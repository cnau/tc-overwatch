import { Alert, Badge, Center, Container, Group, Loader, Stack, Title } from '@mantine/core'

import { useMe } from '@/api/auth'
import ApiErrorAlert from '@/components/ApiErrorAlert'
import PingCard from '@/components/PingCard'
import SignInCard from '@/components/SignInCard'
import UserBadge from '@/components/UserBadge'
import { useOAuthTokenBridge } from '@/hooks/useOAuthTokenBridge'

const OAUTH_ERROR_MESSAGES: Record<string, string> = {
  INVITATION_REQUIRED: 'This email is not yet invited.',
  EMAIL_NOT_VERIFIED: 'Your Google account email is not verified.',
  OAUTH_FAILED: 'Google sign-in was cancelled or failed.',
}

export default function App() {
  const oauthError = useOAuthTokenBridge()
  const me = useMe()

  return (
    <Container size="sm" py="xl">
      <Stack gap="lg">
        <Group justify="space-between" align="baseline">
          <Title order={1}>tc-overwatch</Title>
          {me.data ? <UserBadge me={me.data} /> : <Badge color="blue" variant="light">scaffold</Badge>}
        </Group>

        {oauthError && (
          <Alert color="red" title="Sign-in didn't complete">
            {OAUTH_ERROR_MESSAGES[oauthError] ?? `Sign-in error: ${oauthError}`}
          </Alert>
        )}

        {me.isPending && (
          <Center>
            <Loader />
          </Center>
        )}

        {me.isError && <ApiErrorAlert error={me.error} title="Couldn't load session" />}

        {!me.isPending && !me.data && <SignInCard />}

        {me.data && <PingCard />}
      </Stack>
    </Container>
  )
}
