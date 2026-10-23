// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*'],
  },
  {
    // A block is copied out of this app on its own, so it can't lean on the gallery or on another block.
    files: ['src/blocks/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/app/**', '@/gallery/**', '@/blocks/**'],
              message: 'A block imports its own files by relative path, and shared helpers from @/shared.',
            },
          ],
        },
      ],
    },
  },
]);
