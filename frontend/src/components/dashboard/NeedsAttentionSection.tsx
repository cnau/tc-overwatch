import { SimpleGrid } from '@mantine/core'

import DashboardSection from '@/components/dashboard/DashboardSection'
import EmptyState from '@/components/EmptyState'

// Two adjacent feeds per #44. Referral warnings earn top billing on risk, not
// volume — there will be few, and each one can cost a commission.
export default function NeedsAttentionSection() {
  return (
    <DashboardSection title="Needs you now">
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="lg">
        <EmptyState
          title="No new business"
          description="Inbound enquiries that look like new deals show up here as email is triaged."
        />
        <EmptyState
          title="No referral warnings"
          description="Email suggesting a client is working with another agent shows up here."
        />
      </SimpleGrid>
    </DashboardSection>
  )
}
