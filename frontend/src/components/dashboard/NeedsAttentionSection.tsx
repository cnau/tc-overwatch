import { SimpleGrid } from '@mantine/core'

import DashboardSection from '@/components/dashboard/DashboardSection'
import EmptyState from '@/components/EmptyState'

// Two adjacent feeds, one priority tier: a referral warning is the heads-up that a
// new-business email is on its way.
export default function NeedsAttentionSection() {
  return (
    <DashboardSection title="Needs you now">
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="lg">
        <EmptyState
          title="No new business"
          description="Inbound inquiries that look like new deals show up here as email is triaged."
        />
        <EmptyState
          title="No referral warnings"
          description="Heads-ups from people who've passed your name along show up here, so you know who to expect."
        />
      </SimpleGrid>
    </DashboardSection>
  )
}
