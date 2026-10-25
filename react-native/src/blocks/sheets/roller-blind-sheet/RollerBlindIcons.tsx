import type { ReactNode } from 'react';
import Svg, { Circle, Path } from 'react-native-svg';

/*
 Line icons for the room's controls and the scenes printed on the blind, drawn on a 24-pt grid
 with round caps (no icon font, no emoji).
 */

type IconProps = { color: string; size?: number };

function Frame({ size = 22, children }: { size?: number; children: ReactNode }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      {children}
    </Svg>
  );
}

export function BulbIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M9 17.5h6M10 20.5h4M8.2 14.2A6 6 0 1 1 15.8 14.2c-.8.7-1.3 1.6-1.3 2.6V17h-5v-.2c0-1-.5-1.9-1.3-2.6Z" stroke={color} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </Frame>
  );
}

export function LampIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M8 3.5h8l3 8H5l3-8ZM12 11.5v8M8 20.5h8" stroke={color} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </Frame>
  );
}

export function ThermoIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M10 4.5a2 2 0 1 1 4 0v9.3a4 4 0 1 1-4 0V4.5ZM12 10v6" stroke={color} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </Frame>
  );
}

export function FanIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Circle cx={12} cy={12} r={1.6} stroke={color} strokeWidth={1.6} />
      <Path
        d="M12 10.4C11 7 12 3.5 14.6 4.2c2.2.6 1 4.6-2.6 6.2ZM13.6 12c3.4-1 6.9 0 6.2 2.6-.6 2.2-4.6 1-6.2-2.6ZM12 13.6c1 3.4 0 6.9-2.6 6.2-2.2-.6-1-4.6 2.6-6.2ZM10.4 12C7 13 3.5 12 4.2 9.4c.6-2.2 4.6-1 6.2 2.6Z"
        stroke={color}
        strokeWidth={1.4}
        strokeLinejoin="round"
      />
    </Frame>
  );
}

export function MoonIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10Z" stroke={color} strokeWidth={1.6} strokeLinejoin="round" />
    </Frame>
  );
}

export function BookIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5ZM12 6.5v13" stroke={color} strokeWidth={1.6} strokeLinejoin="round" />
    </Frame>
  );
}

export function SleepIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M5 8h5l-5 6h5M13 5h6l-6 7h6M14 17h4l-4 4h4" stroke={color} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </Frame>
  );
}

export function SunriseIcon({ color, size }: IconProps) {
  return (
    <Frame size={size}>
      <Path d="M3.5 18h17M7 18a5 5 0 0 1 10 0M12 4.5v4M5.6 9.6l1.8 1.8M18.4 9.6l-1.8 1.8M7 21h10" stroke={color} strokeWidth={1.6} strokeLinecap="round" />
    </Frame>
  );
}
