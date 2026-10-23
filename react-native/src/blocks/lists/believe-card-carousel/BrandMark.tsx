import Svg, { Path } from 'react-native-svg';

// Invented mark standing in for the pin's app logo (a letter with an arrow): a honeycomb cell for
// "Beehive", drawn with the same chunky rounded stroke so it fills the icon the way the pin's does.
export function BrandMark({ size, color, opacity = 1 }: { size: number; color: string; opacity?: number }) {
  return (
    <Svg width={size} height={size * 1.17} viewBox="0 0 90 105" opacity={opacity}>
      <Path
        d="M45 14 L74 31 L74 69 L45 86 L16 69 L16 31 Z"
        stroke={color}
        strokeWidth={13}
        strokeLinejoin="round"
        fill="none"
      />
      <Path d="M45 40 L58 47.5 L58 62.5 L45 70 L32 62.5 L32 47.5 Z" fill={color} />
    </Svg>
  );
}
