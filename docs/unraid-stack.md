# Unraid app stack

Runbook for `docker-compose.unraid.yml` — Postgres, migrate, backend, frontend, and the Cloudflare
Tunnel connector. Design rationale is in `docs/architecture.md` § Alternate deployment: private
network (Unraid); the runner that deploys this stack from CI is in `docs/unraid-runner.md`.

## Bringing it up

```bash
cd /mnt/user/tco/repo
docker compose -f docker-compose.unraid.yml --env-file /mnt/user/tco/.env up -d
```

The same `.env` file is used two independent ways, and both are needed:

- `--env-file` feeds compose's `${VAR}` interpolation in the compose file itself.
- The `env_file:` directives pass variables through to each container.

Forgetting `--env-file` fails fast with a "missing X" error from the `${VAR:?...}` guards rather than
silently substituting empty strings. The file lives at `/mnt/user/tco/.env`, mode `0600`, outside the
repo; `.env.unraid.example` lists the required keys.

## Cloudflare Tunnel

Configured out-of-band in the Zero Trust dashboard, not in this repo:

1. Networks → Tunnels: create a tunnel and copy its token into `TUNNEL_TOKEN` in
   `/mnt/user/tco/.env`.
2. Add two public hostnames inside that tunnel:
   - `app.<domain>` → HTTP → `frontend:80`
   - `api.<domain>` → HTTP → `backend:8080`

## Image tags

Tags default to `:main`, which is what a hand-run `up -d` gets. The `deploy-unraid-*` CI jobs
override only the service they own with that commit's immutable `sha-<short>` tag
(`TCO_SERVER_TAG`, `TCO_MIGRATE_TAG`, `TCO_FRONTEND_TAG`), so a deploy ships exactly the commit that
triggered it and never races the moving `main` tag.

The catch: a bare `up -d` with no tag variables puts every service back on `:main`, discarding a
pinned rollback. Deploy one service at a time. Rollback itself is in `docs/unraid-runner.md`
§ Operations.
