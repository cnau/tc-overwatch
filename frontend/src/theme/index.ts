import { createTheme } from '@mantine/core'

// Mantine theme. Colors, spacing, breakpoints, and component defaults live here.
// Components reference these via theme tokens — never hardcode in components.
// See docs/claude/react.md § Mantine.
export const theme = createTheme({
  primaryColor: 'blue',
  defaultRadius: 'md',
  fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
})
