import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

const ROWS = ['QWERTYUIOP', 'ASDFGHJKL', 'ZXCVBNM'];
const WORDS = ['I', 'The', 'I’m'];

const BED = '#d3d6dc';
const KEY = '#ffffff';
const SPECIAL = '#aeb3bd';
const INK = '#111111';
const SIDE = 3;
const GAP = 6;
const KEY_HEIGHT = 43;
const ROW_GAP = 11;
const SUGGESTIONS = 44;

/** Height of the drawn keyboard above the home indicator's strip. */
export const KEYBOARD_HEIGHT = SUGGESTIONS + KEY_HEIGHT * 4 + ROW_GAP * 3 + 8 + 50;

/**
 * A drawing of the system's light keyboard, for the page behind the sheet. It is not a keyboard:
 * it takes no input, and the block never opens the real one.
 */
export function Keyboard({ width, bottomInset }: { width: number; bottomInset: number }) {
  const key = (width - SIDE * 2 - GAP * 9) / 10;
  return (
    <View style={[styles.bed, { width, paddingBottom: Math.max(bottomInset - 20, 0) }]} pointerEvents="none">
      <View style={styles.suggestions}>
        {WORDS.map((word, index) => (
          <View key={word} style={styles.suggestion}>
            {index > 0 ? <View style={styles.divider} /> : null}
            <Text style={[styles.word, index === 1 && styles.wordStrong]}>{word}</Text>
          </View>
        ))}
      </View>
      <View style={styles.keys}>
        <View style={styles.row}>
          {ROWS[0].split('').map((letter) => (
            <Key key={letter} label={letter} width={key} />
          ))}
        </View>
        <View style={[styles.row, { paddingHorizontal: (key + GAP) / 2 }]}>
          {ROWS[1].split('').map((letter) => (
            <Key key={letter} label={letter} width={key} />
          ))}
        </View>
        <View style={[styles.row, styles.spread]}>
          <View style={[styles.key, styles.special, { width: key * 1.3 }]}>
            <Ionicons name="arrow-up-outline" size={21} color={INK} />
          </View>
          <View style={styles.middle}>
            {ROWS[2].split('').map((letter) => (
              <Key key={letter} label={letter} width={key} />
            ))}
          </View>
          <View style={[styles.key, styles.special, { width: key * 1.3 }]}>
            <Ionicons name="backspace-outline" size={21} color={INK} />
          </View>
        </View>
        <View style={styles.row}>
          <View style={[styles.key, styles.special, { width: key * 1.3 + GAP + key * 0.9 }]}>
            <Text style={styles.small}>123</Text>
          </View>
          <View style={[styles.key, styles.space]}>
            <Text style={styles.small}>space</Text>
          </View>
          <View style={[styles.key, styles.special, { width: key * 2.3 + GAP }]}>
            <Text style={styles.small}>return</Text>
          </View>
        </View>
      </View>
      <View style={styles.bottom}>
        <Ionicons name="happy-outline" size={26} color="#4d5057" />
        <Ionicons name="mic-outline" size={25} color="#4d5057" />
      </View>
    </View>
  );
}

function Key({ label, width }: { label: string; width: number }) {
  return (
    <View style={[styles.key, { width }]}>
      <Text style={styles.letter}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  bed: { backgroundColor: BED, paddingTop: 0 },
  suggestions: { height: SUGGESTIONS, flexDirection: 'row', alignItems: 'center' },
  suggestion: { flex: 1, height: SUGGESTIONS, alignItems: 'center', justifyContent: 'center' },
  divider: { position: 'absolute', left: 0, top: 12, bottom: 12, width: StyleSheet.hairlineWidth * 2, backgroundColor: 'rgba(60,60,67,0.25)' },
  word: { fontSize: 16, lineHeight: 20, color: INK },
  wordStrong: { fontWeight: '500' },
  keys: { paddingHorizontal: SIDE, gap: ROW_GAP },
  row: { flexDirection: 'row', gap: GAP },
  spread: { justifyContent: 'space-between' },
  middle: { flexDirection: 'row', gap: GAP },
  key: {
    height: KEY_HEIGHT,
    borderRadius: 8,
    backgroundColor: KEY,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOpacity: 0.3,
    shadowRadius: 0,
    shadowOffset: { width: 0, height: 1 },
  },
  special: { backgroundColor: SPECIAL },
  space: { flex: 1 },
  letter: { fontSize: 23, lineHeight: 28, color: INK },
  small: { fontSize: 16, lineHeight: 20, color: INK },
  bottom: { height: 50, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 22, marginTop: 8 },
});
