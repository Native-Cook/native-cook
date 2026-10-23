/** A person as a monogram: initials in the money face, on ink (or brass for you), the private-banking idiom. */
import { StyleSheet, Text, View } from 'react-native';

import { COLOR, DIDONE_DROP, DIDONE_ROOM, LAYOUT, TYPE } from './theme';
import type { Person } from './transactions';

type Props = { person: Person; size?: number; mini?: boolean; fill?: string; ink?: string; ring?: string };

export function Monogram({ person, size = LAYOUT.mono, mini = false, fill = COLOR.ink, ink = COLOR.ivory, ring }: Props) {
  return (
    <View
      style={[
        styles.disc,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: fill },
        ring ? { boxShadow: `0 0 0 1.5px ${ring}` } : null,
      ]}
    >
      <Text style={[mini ? TYPE.mini : TYPE.mono, styles.text, { color: ink }, mini ? styles.miniRoom : styles.drop]} allowFontScaling={false}>
        {mini ? person.initials[0] : person.initials}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  disc: { alignItems: 'center', justifyContent: 'center' },
  text: { textAlign: 'center' },
  drop: { marginTop: DIDONE_DROP.mono - DIDONE_ROOM.mono },
  miniRoom: { marginTop: -DIDONE_ROOM.mini },
});
