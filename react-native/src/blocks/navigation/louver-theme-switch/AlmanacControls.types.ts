import type { LouverTheme } from './LouverThemeSwitch';

export type ThemePickerProps = {
  /** the theme of the screen this control sits on */
  theme: LouverTheme;
  onSelect: (theme: LouverTheme) => void;
  tint: string;
};

export type RowToggleProps = {
  theme: LouverTheme;
  label: string;
  value: boolean;
  onChange: (value: boolean) => void;
  tint: string;
};
