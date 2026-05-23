import { Alert, Code, Group, Stack, Text } from '@mantine/core'

import { ApiError } from '@/api/http'

type ApiErrorAlertProps = {
  error: unknown
  title?: string
}

export default function ApiErrorAlert({ error, title = 'Request failed' }: ApiErrorAlertProps) {
  const code = error instanceof ApiError ? error.code : null
  const message = error instanceof Error ? error.message : String(error)

  return (
    <Alert color="red" variant="light" title={title}>
      <Stack gap={4}>
        {code && (
          <Group gap="xs">
            <Text size="sm" c="dimmed">code:</Text>
            <Code>{code}</Code>
          </Group>
        )}
        <Text size="sm">{message}</Text>
      </Stack>
    </Alert>
  )
}
