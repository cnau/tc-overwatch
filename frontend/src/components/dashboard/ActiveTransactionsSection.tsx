import DashboardSection from '@/components/dashboard/DashboardSection'
import EmptyState from '@/components/EmptyState'

export default function ActiveTransactionsSection() {
  return (
    <DashboardSection title="Active transactions" hint="Sorted by what needs you first">
      <EmptyState
        title="No transactions yet"
        description="Each property address becomes a transaction once tc-overwatch recognises it in your mail."
      />
    </DashboardSection>
  )
}
