// Flip Focus Badge — the four small icons of the top row, drawn as vectors at the pin's weight (flat, white or orange).
import { useMemo } from 'react';
import Svg, { Circle, Path } from 'react-native-svg';

import { ref } from './ffb-theme';

/** the streak flame: an orange tongue with a lighter core (the pin's #F27B17 body) */
export function FlameIcon({ size = 20 }: { size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path
        d="M12.3 1.6C13.4 5.3 18.4 8 18.4 13.4C18.4 17.7 15.7 21 12.1 21C8.5 21 5.7 18.1 5.7 14.3C5.7 11.8 6.9 10.1 8.3 8.9C8.5 10.5 9.3 11.6 10.4 12.1C10 8.4 10.5 4.6 12.3 1.6Z"
        fill={ref.color.flame}
      />
      <Path
        d="M12.1 12.2C13 13.6 14.6 14.8 14.6 16.8C14.6 18.4 13.5 19.5 12.1 19.5C10.7 19.5 9.6 18.4 9.6 16.9C9.6 15.7 10.4 14.8 11.2 14C11.6 13.4 11.9 12.9 12.1 12.2Z"
        fill="#FFB454"
      />
    </Svg>
  );
}

/** the trophy: a cup, two handles, a stem and a base */
export function TrophyIcon({ size = 20 }: { size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M6.2 3.2H17.8V8.6C17.8 11.9 15.3 14.4 12 14.4C8.7 14.4 6.2 11.9 6.2 8.6V3.2Z" fill="#FFFFFF" />
      <Path
        d="M6.2 5H3.4V7.2C3.4 9.1 4.8 10.6 6.7 10.9M17.8 5H20.6V7.2C20.6 9.1 19.2 10.6 17.3 10.9"
        fill="none"
        stroke="#FFFFFF"
        strokeWidth={1.9}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <Path d="M10.6 14.2H13.4V17.6H10.6Z" fill="#FFFFFF" />
      <Path d="M7.8 19.2C7.8 18.4 8.4 17.8 9.2 17.8H14.8C15.6 17.8 16.2 18.4 16.2 19.2V20.8H7.8V19.2Z" fill="#FFFFFF" />
    </Svg>
  );
}

/** the settings gear: eight rounded teeth round a hole */
export function GearIcon({ size = 22 }: { size?: number }) {
  const d = useMemo(() => {
    const cx = 12;
    const cy = 12;
    const teeth = 8;
    const ro = 10;
    const ri = 7.8;
    const pts: string[] = [];
    for (let i = 0; i < teeth; i++) {
      const a = (i / teeth) * Math.PI * 2;
      const w = 0.2; // half-angle of a tooth's top
      const v = 0.33; // half-angle of its base
      const p = (r: number, ang: number) => `${(cx + r * Math.sin(ang)).toFixed(2)} ${(cy - r * Math.cos(ang)).toFixed(2)}`;
      pts.push(`${i === 0 ? 'M' : 'L'}${p(ri, a - Math.PI / teeth + 0.02)}`);
      pts.push(`L${p(ri, a - v)}`);
      pts.push(`L${p(ro, a - w)}`);
      pts.push(`L${p(ro, a + w)}`);
      pts.push(`L${p(ri, a + v)}`);
    }
    const hole = 3.5;
    return `${pts.join(' ')} Z M${cx + hole} ${cy} A${hole} ${hole} 0 1 0 ${cx - hole} ${cy} A${hole} ${hole} 0 1 0 ${cx + hole} ${cy} Z`;
  }, []);
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} fill="#FFFFFF" fillRule="evenodd" strokeLinejoin="round" stroke="#FFFFFF" strokeWidth={1.2} />
    </Svg>
  );
}

/** the help disc: a white circle with a dark question mark drawn as a path (no font dependency) */
export function HelpIcon({ size = 22 }: { size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Circle cx={12} cy={12} r={11} fill="#FFFFFF" />
      <Path
        d="M8.6 9.4C8.6 7.4 10.1 6.1 12 6.1C13.9 6.1 15.4 7.3 15.4 9.1C15.4 10.4 14.6 11.1 13.7 11.7C12.9 12.2 12.5 12.6 12.5 13.6"
        fill="none"
        stroke="#161616"
        strokeWidth={2.3}
        strokeLinecap="round"
      />
      <Circle cx={12.5} cy={17.4} r={1.35} fill="#161616" />
    </Svg>
  );
}
