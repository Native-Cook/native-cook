import { StyleSheet } from 'react-native';
import type { Rgb } from './vault-contract';

export const tone = {
  page: '#000000', ink: '#FFFFFF', secondary: 'rgba(235,235,245,0.60)',
  tertiary: 'rgba(235,235,245,0.38)', hairline: 'rgba(255,255,255,0.12)',
};
export function colour(rgb: Rgb, alpha = 1, white = 0) {
  const r = Math.round((rgb[0] + (1 - rgb[0]) * white) * 255);
  const g = Math.round((rgb[1] + (1 - rgb[1]) * white) * 255);
  const b = Math.round((rgb[2] + (1 - rgb[2]) * white) * 255);
  return `rgba(${r},${g},${b},${alpha})`;
}
function linearChannel(channel: number) {
  return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
}
export function enamelLabel(tint: Rgb) {
  const luminance = 0.2126 * linearChannel(tint[0]) + 0.7152 * linearChannel(tint[1]) + 0.0722 * linearChannel(tint[2]);
  return luminance > 0.45 ? '#0B0B0C' : '#FFFFFF';
}
export const textStyles = StyleSheet.create({
  number: { fontVariant: ['tabular-nums'] },
  secondary: { color: tone.secondary },
  tertiary: { color: tone.tertiary },
});
export const COUNT_WORDS = ['Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen'];
