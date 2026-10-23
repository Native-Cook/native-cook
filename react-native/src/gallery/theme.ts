import { Platform } from 'react-native';

// The gallery's own look. Blocks bring their colours with them and don't read these.
export const colors = {
  background: '#121210',
  surface: '#1c1c19',
  surfacePressed: '#262622',
  border: 'rgba(244, 243, 236, 0.1)',
  text: '#f4f3ec',
  textMuted: '#9a998f',
  accent: '#fff48d',
} as const;

export const mono = Platform.select({ ios: 'Menlo', default: 'monospace' });
