import { Group, Text } from '@mantine/core'

import classes from './UnderConstructionBanner.module.css'

export default function UnderConstructionBanner() {
  return (
    <div className={classes.banner}>
      <div className={classes.stripes} />
      <Group className={classes.plaque} gap="sm" justify="center" wrap="nowrap">
        <svg className={classes.sign} viewBox="0 0 40 36" aria-hidden="true" focusable="false">
          <path d="M20 2 L38 33 H2 Z" fill="#f5c518" stroke="#111" strokeWidth="3" strokeLinejoin="round" />
          <circle cx="15" cy="15" r="2.6" fill="#111" />
          <path d="M15 18 v6" stroke="#111" strokeWidth="2.4" strokeLinecap="round" />
          <path d="M15 24 l-3.5 5 M15 24 l4.5 4.5" stroke="#111" strokeWidth="2.2" strokeLinecap="round" />
          <g className={classes.arm}>
            <path d="M15 20 l8 3.5" stroke="#111" strokeWidth="2.2" strokeLinecap="round" />
            <path d="M22 22 l6 2.5 l-2 4.5 l-6 -2.5 z" fill="#111" />
          </g>
        </svg>
        <Text className={classes.text}>Under construction</Text>
      </Group>
      <div className={classes.stripes} />
    </div>
  )
}
