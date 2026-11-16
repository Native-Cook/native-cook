import type { ReactNode } from 'react';
import type { SharedValue } from 'react-native-reanimated';
import type { SkPath } from '@shopify/react-native-skia';
export type ExtrusionPage = {
  /** Coordinates are normalized to the path bounds at mount. */
  glyph: SkPath | string;
  field: [string, string];
  face: string;
  direction?: [number, number];
  headline: string;
  caption?: string;
  stat?: { value: number; label?: string };
  render?: (active: SharedValue<number>) => ReactNode;
};
export type ExtrusionStoryProps = {
  pages: ExtrusionPage[];
  index?: number;
  defaultIndex?: number;
  progress?: SharedValue<number>;
  onIndexChange?: (index: number) => void;
  onComplete?: () => void;
  autoAdvanceMs?: number | null;
  loft?: boolean;
  steer?: boolean;
  header?: { brand?: ReactNode; onClose?: () => void; showCount?: boolean };
  tokens?: { displayFont?: string; captionFont?: string; headline?: string; radius?: number; peek?: number };
  autoPlay?: boolean;
};
