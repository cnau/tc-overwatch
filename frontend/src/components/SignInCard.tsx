import { useState } from 'react'
import { Button, Card, Divider, Stack, Text, TextInput, Title } from '@mantine/core'

import { useDevLogin } from '@/api/auth'
import ApiErrorAlert from '@/components/ApiErrorAlert'

export default function SignInCard() {
  const [email, setEmail] = useState('')
  const login = useDevLogin()

  return (
    <Card withBorder radius="md" p="lg">
      <Stack gap="md">
        <Title order={3}>Sign in</Title>
        <Button component="a" href="/oauth2/authorization/google" variant="filled">
          Sign in with Google
        </Button>

        <Divider label="or, in local dev" labelPosition="center" />

        <Text size="sm" c="dimmed">
          Dev-only stub — any well-formed email is accepted. Not available outside the local profile.
        </Text>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            login.mutate({ email })
          }}
        >
          <Stack gap="sm">
            <TextInput
              label="Email"
              placeholder="alice@example.com"
              value={email}
              onChange={(e) => setEmail(e.currentTarget.value)}
              required
              type="email"
            />
            <Button type="submit" variant="default" loading={login.isPending}>
              Sign in (dev)
            </Button>
          </Stack>
        </form>
        {login.isError && <ApiErrorAlert error={login.error} title="Sign-in failed" />}
      </Stack>
    </Card>
  )
}
