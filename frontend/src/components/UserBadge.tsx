import { Button, Group, Text } from '@mantine/core'

import { useLogout } from '@/api/auth'
import type { MeResponse } from '@/api/auth'

type UserBadgeProps = {
  me: MeResponse
}

export default function UserBadge({ me }: UserBadgeProps) {
  const logout = useLogout()
  return (
    <Group gap="sm">
      <Text size="sm" c="dimmed">
        Signed in as{' '}
        <Text component="span" fw={500} c="bright">
          {me.email ?? 'unknown'}
        </Text>
      </Text>
      <Button
        size="xs"
        variant="subtle"
        onClick={() => logout.mutate()}
        loading={logout.isPending}
      >
        Sign out
      </Button>
    </Group>
  )
}
