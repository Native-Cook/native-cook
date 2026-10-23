import { useRouter } from 'expo-router';
import { Pressable, SectionList, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { blocks, tier, type Block } from './registry';
import { colors, mono } from './theme';

// The registry is sorted by category, so each category's blocks are already together.
const sections = blocks.reduce<{ title: string; data: Block[] }[]>((list, block) => {
  const last = list.at(-1);
  if (last?.title === block.category) last.data.push(block);
  else list.push({ title: block.category, data: [block] });
  return list;
}, []);

const count = `${blocks.length} ${blocks.length === 1 ? 'block' : 'blocks'}`;

/** Every block in this app, by category. A row opens the block on its own screen. */
export function GalleryScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  return (
    <SectionList
      style={styles.screen}
      contentContainerStyle={{
        paddingTop: insets.top + 24,
        paddingBottom: insets.bottom + 32,
        paddingHorizontal: 16,
      }}
      sections={sections}
      keyExtractor={(block) => block.slug}
      stickySectionHeadersEnabled={false}
      ListHeaderComponent={
        <View style={styles.header}>
          <Text style={styles.title} accessibilityRole="header">
            Native Cook
          </Text>
          <Text style={styles.meta}>
            {tier} · React Native · {count}
          </Text>
        </View>
      }
      ListEmptyComponent={
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>No blocks yet</Text>
          <Text style={styles.emptyText}>
            Add the first one from the repository root, then reload:
          </Text>
          <Text style={styles.command}>npm run new -- &lt;category&gt; &lt;slug&gt;</Text>
        </View>
      }
      renderSectionHeader={({ section }) => <Text style={styles.category}>{section.title}</Text>}
      renderItem={({ item }) => (
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push({ pathname: '/[slug]', params: { slug: item.slug } })}
          style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
        >
          <View style={styles.rowText}>
            <Text style={styles.name}>{item.name}</Text>
            {item.spec ? (
              <Text style={styles.spec} numberOfLines={2}>
                {item.spec}
              </Text>
            ) : null}
          </View>
          <Text style={styles.chevron}>›</Text>
        </Pressable>
      )}
    />
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  header: { gap: 8, paddingHorizontal: 8, paddingBottom: 8 },
  title: { color: colors.text, fontSize: 32, fontWeight: '700', letterSpacing: -0.5 },
  meta: { color: colors.textMuted, fontFamily: mono, fontSize: 12, textTransform: 'uppercase' },
  category: {
    color: colors.accent,
    fontFamily: mono,
    fontSize: 12,
    paddingHorizontal: 8,
    paddingTop: 24,
    paddingBottom: 8,
    textTransform: 'uppercase',
  },
  row: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 12,
    marginBottom: 8,
    minHeight: 56,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  rowPressed: { backgroundColor: colors.surfacePressed },
  rowText: { flex: 1, gap: 4 },
  name: { color: colors.text, fontSize: 17, fontWeight: '600' },
  spec: { color: colors.textMuted, fontSize: 14, lineHeight: 20 },
  chevron: { color: colors.textMuted, fontSize: 22 },
  empty: { gap: 8, paddingHorizontal: 8, paddingTop: 32 },
  emptyTitle: { color: colors.text, fontSize: 17, fontWeight: '600' },
  emptyText: { color: colors.textMuted, fontSize: 15, lineHeight: 22 },
  command: { color: colors.accent, fontFamily: mono, fontSize: 13, paddingTop: 4 },
});
