import Svg, { Path } from 'react-native-svg';

export function BackGlyph() {
  return (
    <Svg width={11.3} height={19.7} viewBox="1.2 0.8 11.6 20.4">
      <Path d="M11.5 2 L2.5 11 L11.5 20" stroke="#ffffff" strokeWidth={2.6} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

// a filled curved "forward" arrow, as on the pin's share button
export function ShareGlyph() {
  return (
    <Svg width={19} height={15} viewBox="0.5 0.8 25 18.4">
      <Path d="M15 1.5 L25 9 L15 16.5 V12 C9 12 4.5 13.8 1.5 18.5 C2.3 11.6 6.5 6.7 15 6 Z" fill="#ffffff" />
    </Svg>
  );
}

// the Prime plan's flag
export function FlagGlyph({ size = 13 }: { size?: number }) {
  return (
    <Svg width={size * 1.1} height={size} viewBox="0 0 14 13">
      <Path d="M0 0 H14 L10 6.5 L14 13 H0 Z" fill="#64ff52" />
    </Svg>
  );
}

export function CheckGlyph() {
  return (
    <Svg width={30} height={24} viewBox="0 0 30 24">
      <Path d="M3 12.5 L11 20 L27 3.5" stroke="#64ff52" strokeWidth={5} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}
