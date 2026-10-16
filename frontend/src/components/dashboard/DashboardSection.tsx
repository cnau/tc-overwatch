import { Card, Group, Text, Title } from '@mantine/core'
import type { ReactNode } from 'react'

type DashboardSectionProps = {
  title: string
  hint?: string
  children: ReactNode
}

export default function DashboardSection({ title, hint, children }: DashboardSectionProps) {
  return (
    <Card withBorder radius="md" p="lg" component="section">
      <Group justify="space-between" align="baseline" mb="sm">
        <Title order={3} size="h5" tt="uppercase">
          {title}
        </Title>
        {hint && (
          <Text size="xs" c="dimmed">
            {hint}
          </Text>
        )}
      </Group>
      {children}
    </Card>
  )
}
