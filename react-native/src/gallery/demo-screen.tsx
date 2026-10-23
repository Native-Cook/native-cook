import { Suspense } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { blocks } from './registry';
import { colors } from './theme';

/** One block on the whole screen. Leave it with the system's back gesture or button. */
export function DemoScreen({ slug }: { slug: string }) {
  const block = blocks.find((b) => b.slug === slug);
  if (!block) {
    return (
      <View style={[styles.screen, styles.missing]}>
        <Text style={styles.missingText}>No block is named “{slug}”.</Text>
      </View>
    );
  }
  return (
    <View style={styles.screen}>
      <Suspense fallback={null}>
        <block.Demo />
      </Suspense>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  missing: { alignItems: 'center', justifyContent: 'center', padding: 24 },
  missingText: { color: colors.textMuted, fontSize: 15, textAlign: 'center' },
});
