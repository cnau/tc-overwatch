import {
  Badge,
  Button,
  Card,
  Center,
  Code,
  Container,
  Group,
  Loader,
  Stack,
  Text,
  Title,
} from '@mantine/core'

import { useMe } from '@/api/auth'
import { useSendPing } from '@/api/ping'
import ApiErrorAlert from '@/components/ApiErrorAlert'
import DevLoginCard from '@/components/DevLoginCard'
import UserBadge from '@/components/UserBadge'

export default function App() {
  const me = useMe()

  return (
    <Container size="sm" py="xl">
      <Stack gap="lg">
        <Group justify="space-between" align="baseline">
          <Title order={1}>tc-overwatch</Title>
          {me.data ? <UserBadge me={me.data} /> : <Badge color="blue" variant="light">scaffold</Badge>}
        </Group>

        {me.isPending && (
          <Center>
            <Loader />
          </Center>
        )}

        {me.isError && <ApiErrorAlert error={me.error} title="Couldn't load session" />}

        {!me.isPending && !me.data && <DevLoginCard />}

        {me.data && <PingCard />}
      </Stack>
    </Container>
  )
}

function PingCard() {
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
