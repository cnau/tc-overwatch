// PostCSS config for Mantine.
// `postcss-preset-mantine` provides the rem(), mantine-light-dark(), and mixin helpers
// referenced in Mantine's component CSS. `postcss-simple-vars` is required by the preset.
module.exports = {
  plugins: {
    'postcss-preset-mantine': {},
    'postcss-simple-vars': {
      variables: {
        'mantine-breakpoint-xs': '36em',
        'mantine-breakpoint-sm': '48em',
        'mantine-breakpoint-md': '62em',
        'mantine-breakpoint-lg': '75em',
        'mantine-breakpoint-xl': '88em',
      },
    },
  },
}
