import { Stack } from '@mantine/core'

import ActiveTransactionsSection from '@/components/dashboard/ActiveTransactionsSection'
import NeedsAttentionSection from '@/components/dashboard/NeedsAttentionSection'
import ReviewQueuesSection from '@/components/dashboard/ReviewQueuesSection'
import UnderConstructionBanner from '@/components/UnderConstructionBanner'

// Order is the priority contract, not a layout preference: push (money and
// risk) → the working surface → pull (audit surfaces). See docs/task-inventory.md §9.
export default function DashboardPage() {
  return (
    <Stack gap="lg">
      <UnderConstructionBanner />
      <NeedsAttentionSection />
      <ActiveTransactionsSection />
      <ReviewQueuesSection />
    </Stack>
  )
}
