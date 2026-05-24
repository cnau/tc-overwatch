import { Badge, Center, Container, Group, Loader, Stack, Title } from '@mantine/core'

import { useMe } from '@/api/auth'
import ApiErrorAlert from '@/components/ApiErrorAlert'
import OAuthErrorAlert from '@/components/OAuthErrorAlert'
import PingCard from '@/components/PingCard'
import SignInCard from '@/components/SignInCard'
import UserBadge from '@/components/UserBadge'
import { useOAuthTokenBridge } from '@/hooks/useOAuthTokenBridge'

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

        <OAuthErrorAlert error={oauthError} />

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
