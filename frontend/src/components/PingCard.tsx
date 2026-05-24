import { Button, Card, Code, Group, Stack, Text } from '@mantine/core'

import { useSendPing } from '@/api/ping'
import ApiErrorAlert from '@/components/ApiErrorAlert'

export default function PingCard() {
  const ping = useSendPing()

  return (
    <>
      <Text c="dimmed">
        End-to-end smoke test. Click the button to <Code>POST /api/ping</Code> with a JSON
        body. The backend writes a row to <Code>ping_log</Code> and echoes back the server
        timestamp and DB-assigned id.
      </Text>

      <Card withBorder radius="md" p="lg">
        <Stack gap="md">
          <Group justify="space-between">
            <Text fw={500}>Ping</Text>
            <Button onClick={() => ping.mutate({ message: 'hello' })} loading={ping.isPending}>
              Send ping
            </Button>
          </Group>

          {ping.isError && <ApiErrorAlert error={ping.error} />}

          {ping.data && (
            <Stack gap="xs">
              <Group gap="xs">
                <Text size="sm" c="dimmed">echo:</Text>
                <Code>{ping.data.echo}</Code>
              </Group>
              <Group gap="xs">
                <Text size="sm" c="dimmed">serverReceivedAt:</Text>
                <Code>{ping.data.serverReceivedAt}</Code>
              </Group>
              <Group gap="xs">
                <Text size="sm" c="dimmed">id:</Text>
                <Code>{ping.data.id}</Code>
              </Group>
            </Stack>
          )}
        </Stack>
      </Card>
    </>
  )
}
