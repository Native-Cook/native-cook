import { useImage } from '@shopify/react-native-skia';
import { useCallback, useMemo, useState } from 'react';
import { type FlatList, type LayoutChangeEvent, type ListRenderItemInfo, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { scrollTo, useAnimatedReaction, useAnimatedRef, useAnimatedScrollHandler, useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BADGE_ATLAS } from './badge-shaders';
import { BadgeCell } from './BadgeCell';
import { BadgeDetails } from './BadgeDetails';
import { BadgeHero, MorphName } from './BadgeHero';
import { useVaultMotion } from './use-vault-motion';
import { BADGES, type BadgeDef } from './vault-contract';
import { vaultLayout } from './vault-layout';
import { COUNT_WORDS, tone } from './vault-theme';
import { VaultFooter } from './VaultFooter';

const keyExtractor = (item: BadgeDef) => item.id;
export function BadgeVaultGrid({ autoPlay = false }: { autoPlay?: boolean }) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const { width, height } = size;
  const measure = useCallback((event: LayoutChangeEvent) => {
    const next = event.nativeEvent.layout;
    if (next.width > 0 && next.height > 0) {
      setSize(current => current.width === next.width && current.height === next.height ? current :
        { width: next.width, height: next.height });
    }
  }, []);
  const insets = useSafeAreaInsets();
  const layout = useMemo(() => vaultLayout(width, height, insets.top, insets.bottom), [height, insets.bottom, insets.top, width]);
  const image = useImage(BADGE_ATLAS);
  const [listReady, setListReady] = useState(false);
  const [overlayReady, setOverlayReady] = useState(false);
  const ready = !!image && listReady && overlayReady;
  const motion = useVaultMotion(layout, autoPlay, ready);
  const { cells, dispatch, hero, scrollY, scrollCommand, snapshot, selected, reduced } = motion;
  const listRef = useAnimatedRef<FlatList<BadgeDef>>();
  const onScroll = useAnimatedScrollHandler(event => { scrollY.set(event.contentOffset.y); });
  useAnimatedReaction(() => scrollCommand.get(), command => {
    // Animated refs are UI handles; no React ref or measured view crosses into a worklet.
    if (command.serial) scrollTo(listRef, 0, command.y, false);
  });
  const listStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.88 * hero.get().recede,
    transform: [{ scale: reduced ? 1 : 1 - 0.06 * hero.get().recede }] }));
  const listLaidOut = useCallback(() => setListReady(true), []);
  const overlayLaidOut = useCallback(() => setOverlayReady(true), []);
  const renderItem = useCallback(({ item, index }: ListRenderItemInfo<BadgeDef>) => <BadgeCell badge={item} index={index}
    layout={layout} image={image} visual={cells[index]} dispatch={dispatch} have={snapshot[index].have} earned={snapshot[index].earned} />,
  [cells, dispatch, image, layout, snapshot]);
  // FlatList calls getItemLayout with a row index when numColumns is set.
  const getItemLayout = useCallback((_: ArrayLike<BadgeDef> | null | undefined, index: number) =>
    ({ index, length: layout.pitch, offset: layout.headerHeight + index * layout.pitch }), [layout.headerHeight, layout.pitch]);
  const count = snapshot.reduce((sum, cell) => sum + Number(cell.earned), 0);
  const header = useMemo(() => <View style={{ height: layout.headerHeight }}>
    <Text style={[styles.eyebrow, { top: layout.eyebrow.y }]}>COLLECTION</Text>
    <Text style={[styles.heading, { top: layout.heading.y }]}>Badge Vault</Text>
    <Text style={[styles.subtitle, { top: layout.subtitle.y }]}>{COUNT_WORDS[count]} earned. Tap one to hold it.</Text>
  </View>, [count, layout]);
  const footer = useMemo(() => <VaultFooter count={count} layout={layout} motion={motion} />, [count, layout, motion]);
  return <GestureHandlerRootView style={styles.root} onLayout={measure}>
    <View style={[styles.root, { opacity: ready ? 1 : 0 }]} onTouchStart={motion.touched}
      onTouchEnd={motion.touchEnded} onTouchCancel={motion.touchEnded}>
      <Animated.View style={[styles.root, listStyle]} pointerEvents={selected >= 0 ? 'none' : 'auto'}
        accessibilityElementsHidden={selected >= 0} importantForAccessibility={selected >= 0 ? 'no-hide-descendants' : 'auto'}>
        <Animated.FlatList ref={listRef} data={BADGES} numColumns={3} keyExtractor={keyExtractor}
          renderItem={renderItem} getItemLayout={getItemLayout} onScroll={onScroll} scrollEventThrottle={16}
          onLayout={listLaidOut} scrollEnabled={selected < 0} showsVerticalScrollIndicator={false}
          contentInsetAdjustmentBehavior="never" automaticallyAdjustContentInsets={false}
          contentContainerStyle={styles.content} ListHeaderComponent={header} ListFooterComponent={footer}
          initialNumToRender={4} maxToRenderPerBatch={2} windowSize={3} removeClippedSubviews={false} />
      </Animated.View>
      <BadgeHero image={image} hero={hero} layout={layout} onLayout={overlayLaidOut} />
      {selected >= 0 && <BadgeDetails badge={BADGES[selected]} layout={layout} motion={motion} />}
      {BADGES.map((badge, index) => <MorphName key={badge.id} badge={badge} index={index} hero={hero} layout={layout} />)}
    </View>
  </GestureHandlerRootView>;
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tone.page },
  content: { paddingHorizontal: 16 },
  eyebrow: { position: 'absolute', left: 8, fontSize: 12, fontWeight: '600', letterSpacing: 1.2, lineHeight: 16, color: tone.tertiary },
  heading: { position: 'absolute', left: 8, fontSize: 34, fontWeight: '700', letterSpacing: 0.37, lineHeight: 41, color: tone.ink },
  subtitle: { position: 'absolute', left: 8, fontSize: 15, lineHeight: 20, color: tone.secondary },
});
