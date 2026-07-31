import { Button, Card, Stack, Title } from '@mantine/core'

import UnderConstructionBanner from '@/components/UnderConstructionBanner'
import { appConfig } from '@/config'

export default function SignInCard() {
  // OAuth start is a top-level navigation, not a fetch — apiBaseUrl prepending
  // can't ride through http.ts here, so we apply it inline. Empty apiBaseUrl
  // (dev) leaves the path relative for the Vite proxy.
  return (
    <Card withBorder radius="md" p="lg">
      <Stack gap="md">
        <UnderConstructionBanner />
        <Title order={3}>Sign in</Title>
        <Button component="a" href={`${appConfig.apiBaseUrl}/oauth2/authorization/google`} variant="filled">
          Sign in with Google
        </Button>
      </Stack>
    </Card>
  )
}
