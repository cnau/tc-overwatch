import { Button, Card, Stack, Title } from '@mantine/core'

export default function SignInCard() {
  return (
    <Card withBorder radius="md" p="lg">
      <Stack gap="md">
        <Title order={3}>Sign in</Title>
        <Button component="a" href="/oauth2/authorization/google" variant="filled">
          Sign in with Google
        </Button>
      </Stack>
    </Card>
  )
}
