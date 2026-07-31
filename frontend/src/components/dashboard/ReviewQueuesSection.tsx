import { Accordion } from '@mantine/core'

import DashboardSection from '@/components/dashboard/DashboardSection'
import EmptyState from '@/components/EmptyState'

// Collapsed by design: #45 calls these audit surfaces the TC pulls when she
// wants them, not work pushed at her. Her problem is too much to look at.
const QUEUES = [
  {
    value: 'unmatched',
    label: 'Unmatched email',
    empty: 'Nothing unmatched',
    description: 'Transaction-shaped email where no property address could be identified.',
  },
  {
    value: 'auto-promoted',
    label: 'Auto-promoted contacts',
    empty: 'Nothing to review',
    description: 'People added to your known-contacts directory automatically, listed here so you can audit them.',
  },
  {
    value: 'collisions',
    label: 'Label collisions',
    empty: 'No collisions',
    description: 'Two transactions sharing an address. The applied suffix is shown here so you can override it.',
  },
]

export default function ReviewQueuesSection() {
  return (
    <DashboardSection title="Review when you have time">
      <Accordion variant="separated" radius="md">
        {QUEUES.map((queue) => (
          <Accordion.Item key={queue.value} value={queue.value}>
            <Accordion.Control>{queue.label}</Accordion.Control>
            <Accordion.Panel>
              <EmptyState title={queue.empty} description={queue.description} />
            </Accordion.Panel>
          </Accordion.Item>
        ))}
      </Accordion>
    </DashboardSection>
  )
}
