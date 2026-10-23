import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';

// Invented card networks, told apart by the first digit of the number. Each has its own gradient
// (from the top left to the bottom right of the card) and a glow colour for the light on it.
export type Network = 'none' | 'kestrel' | 'vela' | 'orrery' | 'nimbus';

export type Palette = [start: string, end: string, glow: string];

export const PALETTES: Record<Network, Palette> = {
  none: ['#25262d', '#454852', '#9ea3b5'],
  kestrel: ['#0b4f4a', '#13a77c', '#7cf0c4'],
  vela: ['#1c2580', '#6338e6', '#a993ff'],
  orrery: ['#b8330f', '#b0124f', '#ffad5c'],
  nimbus: ['#0a3d63', '#0b98d6', '#8fdcff'],
};

export const NETWORK_NAMES: Record<Network, string> = {
  none: '',
  kestrel: 'Kestrel',
  vela: 'Vela',
  orrery: 'Orrery',
  nimbus: 'Nimbus',
};

export function networkOf(digits: string): Network {
  switch (digits[0]) {
    case '3':
      return 'kestrel';
    case '4':
      return 'vela';
    case '5':
      return 'orrery';
    case '6':
      return 'nimbus';
    default:
      return 'none';
  }
}

const INK = 'rgba(255, 255, 255, 0.95)';

/** The network's mark: a small drawn emblem and its name, white on the card. */
export function NetworkMark({ network }: { network: Network }) {
  if (network === 'none') {
    // Before a digit is typed: an empty slot where the mark will go.
    return <View style={styles.empty} />;
  }
  return (
    <View style={styles.mark}>
      <Svg width={26} height={24} viewBox="0 0 26 24">
        {network === 'vela' && <Path d="M3 21 L13 2 L13 21 Z M15.5 21 L15.5 8 L24 21 Z" fill={INK} />}
        {network === 'kestrel' && (
          <Path d="M2 7 L13 17 L24 7 M6 3 L13 10 L20 3" stroke={INK} strokeWidth={2.6} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        )}
        {network === 'orrery' && (
          <>
            <Circle cx={12} cy={12} r={8.5} stroke={INK} strokeWidth={2.2} fill="none" />
            <Circle cx={12} cy={12} r={3} fill={INK} />
            <Circle cx={19.6} cy={6.2} r={2.6} fill={INK} />
          </>
        )}
        {network === 'nimbus' && (
          <Path
            d="M3 18 A5 5 0 0 1 8 11 A6.5 6.5 0 0 1 20 10 A4.5 4.5 0 0 1 23 18 Z"
            stroke={INK}
            strokeWidth={2.2}
            strokeLinejoin="round"
            fill="none"
          />
        )}
      </Svg>
      <Text allowFontScaling={false} style={styles.name}>
        {NETWORK_NAMES[network].toUpperCase()}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  mark: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  name: {
    color: INK,
    fontSize: 14,
    fontWeight: '800',
    fontStyle: 'italic',
    letterSpacing: 1.6,
  },
  empty: {
    width: 64,
    height: 22,
    borderRadius: 11,
    borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: 'rgba(255, 255, 255, 0.22)',
    borderStyle: 'dashed',
  },
});
