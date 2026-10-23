import { type ReactNode } from 'react';
import Svg, { Circle, Ellipse, G, Path, Rect } from 'react-native-svg';

type IconProps = { w: number; h: number; color?: string; stroke?: number };

/** Each icon is drawn in its own tight viewBox and stretched to the measured pixel box (w × h). */
function Box({ w, h, vb, children }: { w: number; h: number; vb: string; children: ReactNode }) {
  return (
    <Svg width={w} height={h} viewBox={vb} preserveAspectRatio="none" fill="none">
      {children}
    </Svg>
  );
}

export function ChevronLeft({ w, h, color = '#000', stroke = 2.6 }: IconProps) {
  return (
    <Box w={w} h={h} vb="5.7 1.7 11.6 20.6">
      <Path d="M16 3 L7 12 L16 21" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" />
    </Box>
  );
}

export function ChevronDown({ w, h, color = '#000', stroke = 2.2 }: IconProps) {
  return (
    <Box w={w} h={h} vb="2.9 6.9 18.2 10.2">
      <Path d="M4 8 L12 16 L20 8" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" />
    </Box>
  );
}

export function Globe({ w, h, color = '#000', stroke = 1.7 }: IconProps) {
  return (
    <Box w={w} h={h} vb="1.85 1.85 20.3 20.3">
      <G stroke={color} strokeWidth={stroke} strokeLinecap="round">
        <Circle cx="12" cy="12" r="9.3" />
        <Ellipse cx="12" cy="12" rx="4.1" ry="9.3" />
        <Path d="M2.7 12 H21.3 M4.2 7.2 H19.8 M4.2 16.8 H19.8" />
      </G>
    </Box>
  );
}

export function Pin({ w, h, color = '#6E7479', stroke = 1.9 }: IconProps) {
  return (
    <Box w={w} h={h} vb="4.25 2.45 15.5 20">
      <G stroke={color} strokeWidth={stroke} strokeLinejoin="round">
        <Path d="M12 22 C8 17.6 5.2 14.6 5.2 10.2 a6.8 6.8 0 0 1 13.6 0 C18.8 14.6 16 17.6 12 22 Z" />
        <Circle cx="12" cy="10.2" r="2.6" />
      </G>
    </Box>
  );
}

export function BookmarkOutline({ w, h, color = '#000', stroke = 2 }: IconProps) {
  return (
    <Box w={w} h={h} vb="5.25 2.05 13.5 19.9">
      <Path d="M6.2 3 H17.8 V21 L12 16.6 L6.2 21 Z" stroke={color} strokeWidth={stroke} strokeLinejoin="round" />
    </Box>
  );
}

export function BarsIcon({ w, h, color = '#6E7479', stroke = 2.4 }: IconProps) {
  return (
    <Box w={w} h={h} vb="3.3 2.8 18.9 18.4">
      <Path d="M4.5 20 V10 M10 20 V4 M15.5 20 V12 M21 20 V15" stroke={color} strokeWidth={stroke} strokeLinecap="round" />
    </Box>
  );
}

export function ShareIcon({ w, h, color = '#000', stroke = 2.2 }: IconProps) {
  return (
    <Box w={w} h={h} vb="2.9 2.1 18.2 19.2">
      <G stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
        <Path d="M12 15 V3.5 M7.6 7.6 L12 3.2 L16.4 7.6" />
        <Path d="M8 10.5 H6.2 a2.2 2.2 0 0 0 -2.2 2.2 V18 a2.2 2.2 0 0 0 2.2 2.2 H17.8 a2.2 2.2 0 0 0 2.2 -2.2 V12.7 a2.2 2.2 0 0 0 -2.2 -2.2 H16" />
      </G>
    </Box>
  );
}

export function GridPlus({ w, h, color = '#000', stroke = 2.4 }: IconProps) {
  return (
    <Box w={w} h={h} vb="1.8 1.8 20.4 20.4">
      <G stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
        <Rect x="3" y="3" width="7.6" height="7.6" rx="1.6" />
        <Rect x="3" y="13.4" width="7.6" height="7.6" rx="1.6" />
        <Rect x="13.4" y="13.4" width="7.6" height="7.6" rx="1.6" />
        <Path d="M17.2 3 V10.6 M13.4 6.8 H21" />
      </G>
    </Box>
  );
}

/** Navigation arrow (points up-right), filled. */
export function LocateArrow({ size, color = '#0263FF' }: { size: number; color?: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M21.2 2.8 L3.4 10.6 Q2.6 11 3.5 11.5 L10 13.9 L12.5 20.5 Q12.9 21.4 13.4 20.6 Z" fill={color} />
    </Svg>
  );
}

/** Map bookmark: blue ribbon with a white outline. */
export function MapBookmark({ w, h, color = '#0E77EA' }: { w: number; h: number; color?: string }) {
  return (
    <Svg width={w} height={h} viewBox="0 0 28 32">
      <Path d="M3 3 H25 V29 L14 22 L3 29 Z" fill={color} stroke="#fff" strokeWidth={2.4} strokeLinejoin="round" />
    </Svg>
  );
}

export function MapThumb({ w, h, color = '#8FCAFC' }: { w: number; h: number; color?: string }) {
  return (
    <Svg width={w} height={h} viewBox="0 0 16 18">
      <Path
        d="M1 7.4 H4 V16.6 H1 Z M5.2 7.6 L8.2 2.2 Q9 0.6 10.4 1.6 Q11.4 2.4 10.9 4 L10.2 6.2 H14.2 Q15.8 6.4 15.4 8.2 L14.4 14 Q14.1 15.8 12.4 16 L5.6 16.6 Z"
        fill={color}
        stroke="#fff"
        strokeWidth={0.9}
        strokeLinejoin="round"
      />
    </Svg>
  );
}

export function MapStar({ w, h, color = '#FFBD20' }: { w: number; h: number; color?: string }) {
  return (
    <Svg width={w} height={h} viewBox="0 0 24 23">
      <Path
        d="M12 1.2 L15.2 8 L22.6 8.9 L17.1 14 L18.6 21.3 L12 17.7 L5.4 21.3 L6.9 14 L1.4 8.9 L8.8 8 Z"
        fill={color}
        stroke="#fff"
        strokeWidth={1.2}
        strokeLinejoin="round"
      />
    </Svg>
  );
}

/** Highway shield: red cap, blue body, white numerals drawn by the caller. */
export function Shield({ w, h, blue, red }: { w: number; h: number; blue: string; red: string }) {
  return (
    <Svg width={w} height={h} viewBox="0 0 74 57">
      <G>
        <Path d="M37 1.5 C22 1.5 11 6.5 2 11 L2 22 L72 22 L72 11 C63 6.5 52 1.5 37 1.5 Z" fill={red} stroke="#fff" strokeWidth={3} strokeLinejoin="round" />
        <Path d="M2 22 L72 22 L72 38 C72 48 56 54 37 55.5 C18 54 2 48 2 38 Z" fill={blue} stroke="#fff" strokeWidth={3} strokeLinejoin="round" />
      </G>
    </Svg>
  );
}
