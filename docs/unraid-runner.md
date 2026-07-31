# Self-hosted GitHub Actions runner (Unraid)

Runbook for the runner that executes the `deploy-unraid-server` and
`deploy-unraid-frontend` jobs in `.github/workflows/ci.yml`. Design rationale
lives in `docs/architecture.md` § Unraid deploy: self-hosted runner; this file
is the ops procedure.

Everything here happens on the Unraid box. The steps are one-time unless marked
otherwise.

## Why a runner instead of a push-based deploy

The Unraid box has no inbound network exposure, and adding one for CI would
undo the reason Cloudflare Tunnel is in front of the app. The runner inverts
the direction: it makes an outbound long-poll to GitHub, claims a job, and runs
it locally. No port forward, no firewall hole, no credentials handed to GitHub.

Because the runner is local, deploy jobs read `/mnt/user/tco/.env` straight off
the host — **CI never sees a database password, the JWT secret, or the tunnel
token.**

## Security constraints (read before setup)

This repo is **public**, and the runner mounts the host Docker socket. Those two
facts together mean a job on this runner is effectively root on the Unraid box.
Three things keep that safe, and all three are load-bearing:

1. **Every self-hosted job is guarded by
   `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`.** Only
   code already merged to `main` reaches the box. A pull request from a fork
   builds on GitHub-hosted runners and never touches this one. Removing that
   guard is the single change that would turn any drive-by fork PR into remote
   code execution on a home network.
2. **Repo Settings → Actions → General → Fork pull request workflows** set to
   *Require approval for all external contributors* (GitHub's default for public
   repos). Defence in depth for the same attack.
3. **The runner's PAT is scoped to this repo only** — see below.

## Setup

### 1. Create the personal access token

A fine-grained PAT, resource owner `cnau`, **only** the `cnau/tc-overwatch`
repository, with one permission:

| Permission | Access |
| --- | --- |
| Repository → Administration | Read and write |

That is what the GitHub API needs to mint runner registration tokens. It does
not grant code push, package publish, or access to any other repo — though
Administration write can still change repo settings, webhooks, and deploy keys,
which is why it gets an expiry rather than living on the box forever.

**Use 90 days, and put a calendar reminder on it.** Note where an expired token
does and doesn't hurt, because the failure is latent: the PAT is read only at
*registration*, when the container's entrypoint exchanges it for a short-lived
registration token. After that the runner polls GitHub with its own credentials,
so steady-state job claiming never touches the PAT. Those credentials live in
the container filesystem, which is not on a volume — a plain restart or an
Unraid reboot keeps them, but any **recreate** (image bump, edit to
`docker-compose.runner.yml`, `down`/`up`) re-registers and needs a live token.

So an expired PAT is invisible for weeks and then surfaces the next time you
touch the runner for an unrelated reason — as a runner that never comes back
and jobs that queue. If tracking the expiry becomes a nuisance, swap the PAT
for a GitHub App (`APP_ID` + `APP_PRIVATE_KEY`, both accepted by this image):
installation tokens auto-renew, so the cliff disappears entirely.

### 2. Write the runner env file

Separate from the app's `.env` — the runner has no business being able to read
DB passwords, and the app containers have no business reading a GitHub token:

```bash
install -m 0600 /dev/null /mnt/user/tco/.env.runner
cat > /mnt/user/tco/.env.runner <<'EOF'
ACCESS_TOKEN=github_pat_...
EOF
```

### 3. Create the work directory

```bash
mkdir -p /mnt/user/tco/runner/_work
```

This path is bind-mounted into the runner **at the same absolute path** it has
on the host, and that is not cosmetic. Bind mounts in a compose file are
resolved by the host's Docker daemon, so `docker-compose.unraid.yml`'s relative
`./scripts/db-init-unraid` mount is interpreted against the host filesystem. If
the checkout lives at a path the host doesn't have, Docker creates an empty
directory there and mounts *that* — a failure that surfaces much later as a
Postgres container coming up with no roles provisioned.

The runner also mounts `/mnt/user/tco/.env` read-only, which exists already from
the app stack. Two different rules are at work and it's worth keeping them
apart, because assuming one covers the other is what makes this fail:

| Read by | Resolved against | Consequence |
| --- | --- | --- |
| Docker daemon — `volumes:` in `docker-compose.unraid.yml` | the **host** filesystem | paths must match what the host sees |
| compose **client** — `--env-file`, `env_file:`, and anything a job's shell opens | the **runner container's** filesystem | the file must be mounted in |

`--env-file` looks like daemon business because it configures containers that
run on the host, but compose parses it client-side before it talks to the
daemon at all. Miss the mount and every deploy job dies at its first compose
command with `couldn't find env file: /mnt/user/tco/.env`.

### 4. Migrate the app stack to the pinned compose project name

`docker-compose.unraid.yml` now pins `name: tco`. The stack deployed by hand
before this change took its project name from whatever directory it was run
from, so the first CI deploy would try to create `tco-backend` while a
container of that name already exists under the old project, and fail on the
name collision.

Check what the running stack calls itself:

```bash
docker inspect tco-backend --format '{{index .Config.Labels "com.docker.compose.project"}}'
```

If that prints anything other than `tco`, re-create the stack once under the
pinned name (~30s of downtime; the Postgres data is a host bind mount at
`/mnt/user/tco/postgres` and is not touched):

```bash
cd /mnt/user/tco/repo && git pull
docker compose -p <old-name> -f docker-compose.unraid.yml --env-file /mnt/user/tco/.env down
docker compose -f docker-compose.unraid.yml --env-file /mnt/user/tco/.env up -d
```

### 5. Start the runner

```bash
cd /mnt/user/tco/repo
docker compose -f docker-compose.runner.yml --env-file /mnt/user/tco/.env.runner up -d
docker logs -f tco-runner
```

Expect `√ Connected to GitHub` followed by `Listening for Jobs`. Confirm at
Repo Settings → Actions → Runners: a runner named `unraid`, idle, labelled
`self-hosted`, `Linux`, `X64`, `unraid`.

### 6. Verify end to end

Push a trivial commit to `main` (or re-run the latest CI run) and watch
`deploy-unraid-server` and `deploy-unraid-frontend` claim the runner. A green
run means the images pulled, migrations applied, containers reported healthy,
and `scripts/smoke.sh` got the expected responses back through the tunnel.

## Operations

**Rollback.** Deploys pin the immutable `sha-<short>` tag, so rolling back is
redeploying an older one. On the box:

```bash
cd /mnt/user/tco/repo
TCO_SERVER_TAG=sha-abc1234 docker compose -f docker-compose.unraid.yml \
  --env-file /mnt/user/tco/.env up -d backend
```

Same shape with `TCO_FRONTEND_TAG` / `frontend`. Note this does **not** roll the
schema back — a rollback across a migration needs a forward-fixing changeset.
Re-running CI on the older commit works too and is the more traceable option.

Careful: a bare `docker compose up -d` with no tag variables set puts every
service back on `:main` and quietly discards a pinned rollback. Deploy the one
service you mean to.

**Logs.** `docker logs -f tco-runner` for the runner; the failing deploy job's
own log already carries the last 200 lines of the app container it restarted.

**Upgrading the runner.** `docker-compose.runner.yml` pins an image digest.
Pull the new tag, copy the printed digest into the file, commit, then
`up -d` — same convention as `cloudflared` and the Liquibase base image.

**Disk.** Every deploy leaves the previous image on the box. GHCR is pruned to
5 versions per package by CI, but the Unraid side is not. Periodically:
`docker image prune -a --filter "until=336h"`.

**Deregistering.** `docker compose -f docker-compose.runner.yml down` stops the
runner but leaves it registered; remove it from Repo Settings → Actions →
Runners as well, otherwise jobs queue against a runner that is never coming
back.

## Troubleshooting first-run failures

**`permission denied while trying to connect to the Docker daemon socket`** — the runner
process isn't in a group that can read `/var/run/docker.sock`. The image runs as
root by default, which works; if `RUN_AS_ROOT=false` was set, either drop it or add
the runner user to the socket's group.

**`couldn't find env file: /mnt/user/tco/.env`** — the runner container lacks
the read-only `.env` mount, or predates it. Recreate it:
`docker compose -f docker-compose.runner.yml --env-file /mnt/user/tco/.env.runner up -d --force-recreate`.
A recreate re-registers the runner, so the PAT has to still be valid.

**`Conflict. The container name "/tco-backend" is already in use`** — step 4 was
skipped. The stack is still under its old project name.

**Jobs sit queued forever** — no runner with both `self-hosted` and `unraid`
labels is online. Check Repo Settings → Actions → Runners and `docker logs
tco-runner`. If this appeared right after recreating the runner container, and
the log shows a registration failure rather than `Listening for Jobs`, the PAT
expired — see step 1.

**Postgres comes up with no roles, or `tco_app` doesn't exist** — the
`./scripts/db-init-unraid` bind mount resolved to a path the host doesn't have,
so Docker mounted an empty directory. Re-check step 3: the work directory must
be mounted at an identical path on both sides.

## Fallback

If the runner turns into a maintenance burden, `docs/architecture.md` records
Watchtower as the substitute: it polls GHCR for the `main` tag and restarts
changed containers. It costs the migration ordering, the health gate, and the
smoke test — the whole point of this pipeline — so it's a retreat, not a
refactor.
