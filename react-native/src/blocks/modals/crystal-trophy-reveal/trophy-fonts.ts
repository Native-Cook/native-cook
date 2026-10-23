// Both renderers load the same local OFL faces; the block has no catalog font dependency.
import { useFont, type SkFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';

import { FONTS, type TrophyFrame } from './trophy-contract';

export const FONT_SOURCES = {
  title: require('./assets/BarlowCondensed-ExtraBoldItalic.ttf'),
  date: require('./assets/BarlowCondensed-SemiBoldItalic.ttf'),
  label: require('./assets/BarlowCondensed-Bold.ttf'),
  hint: require('./assets/BarlowCondensed-Medium.ttf'),
};
const families = {
  [FONTS.title]: FONT_SOURCES.title, [FONTS.date]: FONT_SOURCES.date,
  [FONTS.label]: FONT_SOURCES.label, [FONTS.hint]: FONT_SOURCES.hint,
};
export type TrophyFonts = { title: SkFont; date: SkFont; label: SkFont; hint: SkFont };
export function useTrophyFonts(frame: TrophyFrame): TrophyFonts | null {
  const [loaded, error] = useFonts(families);
  const title = useFont(FONT_SOURCES.title, frame.titleSize);
  const date = useFont(FONT_SOURCES.date, frame.dateSize);
  const label = useFont(FONT_SOURCES.label, 22);
  const hint = useFont(FONT_SOURCES.hint, 13);
  if (error) throw error;
  return loaded && title && date && label && hint ? { title, date, label, hint } : null;
}
