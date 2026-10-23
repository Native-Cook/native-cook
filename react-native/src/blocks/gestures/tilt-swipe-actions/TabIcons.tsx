/** The source's three tab glyphs, outlined: a pie, a wallet with a clasp, a person with a badge. */
import Svg, { Circle, G, Path } from 'react-native-svg';

type Props = { kind: 'pie' | 'wallet' | 'person'; color: string; size?: number };

export function TabIcon({ kind, color, size = 26 }: Props) {
  // Traced on the source's tab bar (f017 rectified to the flat app, 8 px/pt): a ring with its slice pulled out,
  // a wallet with a flap and a ring clasp, a person whose body is one curling stroke.
  const stroke = { stroke: color, strokeWidth: 2.5, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      {kind === 'pie' && (
        <>
          <Path d="M20.72 14.38A9 9 0 1 1 10.97 4.63" {...stroke} />
          <Path d="M12.4 13V3.2A9.8 9.8 0 0 1 22.2 13Z" {...stroke} />
        </>
      )}
      {kind === 'wallet' && (
        // The source's wallet sits 3.4 pt higher in its slot than the other two glyphs.
        <G transform="translate(0 -3.4)">
          <Path d="M15.5 4H6.5A3.5 3.5 0 0 0 3 7.5V20.1A3.5 3.5 0 0 0 6.5 23.6H20.7A3.5 3.5 0 0 0 24.2 20.1V11.8" {...stroke} />
          <Path d="M3 11.6C9 14.6 17 14.6 24.2 11.8" {...stroke} />
          <Circle cx={20.5} cy={5.5} r={2.3} {...stroke} />
        </G>
      )}
      {kind === 'person' && (
        <>
          <Circle cx={15.5} cy={7.4} r={3.6} {...stroke} />
          <Path d="M7.6 21.4H23A2.6 2.6 0 0 0 25.6 18.8A3 3 0 0 0 22.6 15.8C21 15.4 19.6 13.6 19 11" {...stroke} />
        </>
      )}
    </Svg>
  );
}
