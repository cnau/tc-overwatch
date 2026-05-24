import { useState } from 'react'
import { Alert } from '@mantine/core'

const OAUTH_ERROR_MESSAGES: Record<string, string> = {
  INVITATION_REQUIRED: 'This email is not yet invited.',
  EMAIL_NOT_VERIFIED: 'Your Google account email is not verified.',
  OAUTH_FAILED: 'Google sign-in was cancelled or failed.',
}

type OAuthErrorAlertProps = {
  error: string | null
}

export default function OAuthErrorAlert({ error }: OAuthErrorAlertProps) {
  const [dismissed, setDismissed] = useState(false)

  if (!error || dismissed) return null

  return (
    <Alert
      color="red"
      title="Sign-in didn't complete"
      withCloseButton
      onClose={() => setDismissed(true)}
    >
      {OAUTH_ERROR_MESSAGES[error] ?? `Sign-in error: ${error}`}
    </Alert>
  )
}
