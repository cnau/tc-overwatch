import { Stack, Text } from '@mantine/core'
import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  description?: string
  children?: ReactNode
}

export default function EmptyState({ title, description, children }: EmptyStateProps) {
  return (
    <Stack gap="xs" align="center" py="lg">
      <Text fw={600}>{title}</Text>
      {description && (
        <Text size="sm" c="dimmed" ta="center" maw="42ch">
          {description}
        </Text>
      )}
      {children}
    </Stack>
  )
}
