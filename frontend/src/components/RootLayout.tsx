import { Badge, Center, Container, Group, Loader, Stack, Title } from '@mantine/core'
import { Outlet } from 'react-router'

import { useMe } from '@/api/auth'
import ApiErrorAlert from '@/components/ApiErrorAlert'
import OAuthErrorAlert from '@/components/OAuthErrorAlert'
import SignInCard from '@/components/SignInCard'
import UserBadge from '@/components/UserBadge'
import { useOAuthTokenBridge } from '@/hooks/useOAuthTokenBridge'

export default function RootLayout() {
  const oauthError = useOAuthTokenBridge()
  const me = useMe()

  return (
    <Container size="md" py="xl">
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

        {/* The gate is here rather than in a route guard: with two routes, a
            redirect loop costs more than it buys. Revisit at the third route. */}
        {!me.isPending && !me.data && <SignInCard />}

        {me.data && <Outlet />}
      </Stack>
    </Container>
  )
}
