import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Container, Stack, Group, Title, Text, Button, Card, Badge, Code, Alert } from '@mantine/core'

import { sendPing } from '@/api/ping'

// Temporary hello-world page. Proves the end-to-end path:
//   browser → fetch JSON → Spring MVC @RestController → PingService → PingDao →
//   Postgres → JSON response → render.
//
// This page is throwaway. Real Dashboard / Transaction-details pages replace it
// when the UI epic feature work lands.
export function App() {
  const [counter, setCounter] = useState(0)
  const pingCall = useMutation({ mutationFn: sendPing })

  const handlePing = () => {
    const n = counter + 1
    setCounter(n)
    pingCall.mutate({ message: `Hello from the browser (call #${n})` })
  }

  return (
    <Container size="sm" py="xl">
      <Stack gap="lg">
        <Group justify="space-between" align="baseline">
          <Title order={1}>tc-overwatch</Title>
          <Badge color="blue" variant="light">scaffold</Badge>
        </Group>

        <Text c="dimmed">
          End-to-end smoke test. Click the button to <Code>POST /api/ping</Code> with a JSON
          body. The backend writes a row to <Code>ping_log</Code> and echoes back the server
          timestamp and DB-assigned id.
        </Text>

        <Card withBorder radius="md" p="lg">
          <Stack gap="md">
            <Group justify="space-between">
              <Text fw={500}>Ping</Text>
              <Button onClick={handlePing} loading={pingCall.isPending} disabled={pingCall.isPending}>
                Send ping
              </Button>
            </Group>

            {pingCall.isError && (
              <Alert color="red" variant="light" title="Request failed">
                {pingCall.error.message}
              </Alert>
            )}

            {pingCall.data && (
              <Stack gap="xs">
                <Group gap="xs">
                  <Text size="sm" c="dimmed">echo:</Text>
                  <Code>{pingCall.data.echo}</Code>
                </Group>
                <Group gap="xs">
                  <Text size="sm" c="dimmed">serverReceivedAt:</Text>
                  <Code>{pingCall.data.serverReceivedAt}</Code>
                </Group>
                <Group gap="xs">
                  <Text size="sm" c="dimmed">id:</Text>
                  <Code>{pingCall.data.id}</Code>
                </Group>
              </Stack>
            )}
          </Stack>
        </Card>
      </Stack>
    </Container>
  )
}
