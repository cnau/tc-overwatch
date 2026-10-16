import { createBrowserRouter } from 'react-router'

import RootLayout from '@/components/RootLayout'
import DashboardPage from '@/pages/DashboardPage'
import ScaffoldPage from '@/pages/ScaffoldPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'scaffold', element: <ScaffoldPage /> },
    ],
  },
])
