/**
 * Atlas, a photo browser: hold a place until it lifts, carry it onto the saved button and let go; it is pulled in as
 * it gets near and the button swallows it. Tap the button and the collection rises with the new place on top.
 *
 * Layers, back to front: page, header and chips, the grid, the fade under the chrome, the glass tab bar and saved
 * button, the toast, the carried photo, the sheet and its scrim, the demo's finger.
 */
import { BlurView } from 'expo-blur';
import { useFonts } from 'expo-font';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { interpolate, useAnimatedStyle, useReducedMotion, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';

import { SAVE } from './motion';
import { COLLECTION, GLYPH, PHOTOS } from './photos';
import { COLOR, FONT, LAYOUT, TYPE, makeLayout, type Layout } from './theme';
import { useSave } from './use-save';

export type SaveToCollectionProps = { autoPlay?: boolean };

export function SaveToCollection({ autoPlay = false }: SaveToCollectionProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded, fontsFailed] = useFonts({
    [FONT.regular]: require('./assets/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/Inter-SemiBold.ttf'),
  });
  if (!fontsLoaded && !fontsFailed) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  // Geometry and Reduce Motion are fixed per mount; under Reduce Motion there is no demo, a finger still saves.
  return <Screen key={`${width}x${height}-${reduced}`} lay={lay} reduced={reduced} autoPlay={autoPlay && !reduced} />;
}

function Glyph({ d, size = 24, color = COLOR.ink, weight = 1.75, fill }: { d: string; size?: number; color?: string; weight?: number; fill?: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} stroke={color} strokeWidth={weight} strokeLinecap="round" strokeLinejoin="round" fill={fill ?? 'none'} />
    </Svg>
  );
}

/** Glass: the page frosted under it, a thin fill, a hairline rim brighter along the top. */
function Glass({ style, radius, thick, children }: { style?: object; radius: number; thick?: boolean; children?: React.ReactNode }) {
  return (
    <View style={[{ borderRadius: radius, borderCurve: 'continuous', overflow: 'hidden' }, style]}>
      <BlurView intensity={thick ? 80 : 40} tint={thick ? 'systemThickMaterialDark' : 'systemUltraThinMaterialDark'} style={StyleSheet.absoluteFill} />
      <View style={[StyleSheet.absoluteFill, { backgroundColor: thick ? COLOR.sheetFill : COLOR.glassFill }]} />
      <LinearGradient
        colors={['rgba(255,255,255,0.14)', 'rgba(255,255,255,0)']}
        locations={[0, 0.5]}
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
      />
      <View style={[StyleSheet.absoluteFill, { borderRadius: radius, borderCurve: 'continuous', borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.rim }]} pointerEvents="none" />
      {children}
    </View>
  );
}

type Values = ReturnType<typeof useSave>['values'];

/** Under each photo in the sheet: 8 pt, a 16 pt title, a 16 pt place, 8 pt. */
const SHEET_CAPTION = 48;

function Tile({ i, lay, v }: { i: number; lay: Layout; v: Values }) {
  const t = lay.tiles[i];
  const look = useAnimatedStyle(() => {
    const lifted = v.carry.get() === i;
    // Others step back a little while a photo is up; the lifted one leaves a dim place behind.
    const others = v.carry.get() >= 0 && !lifted ? 1 - 0.3 * v.lift.get() : 1;
    return { opacity: lifted ? 0.16 : others };
  });
  const markLook = useAnimatedStyle(() => ({
    opacity: v.marked.get() === i ? v.mark.get() : 0,
    transform: [{ scale: 0.8 + 0.2 * (v.marked.get() === i ? v.mark.get() : 0) }],
  }));
  return (
    <View style={[styles.tile, { left: t.x, top: t.y, width: t.w, height: t.h }]}>
      <Animated.View style={[StyleSheet.absoluteFill, look]}>
        <Image source={PHOTOS[i].src} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
      </Animated.View>
      <Animated.View style={[styles.mark, markLook]}>
        <Glass radius={14} style={StyleSheet.absoluteFill}>
          <View style={styles.center}><Glyph d={GLYPH.bookmark} size={16} weight={1.75} fill={COLOR.ink} /></View>
        </Glass>
      </Animated.View>
    </View>
  );
}

function Carried({ lay, v, index }: { lay: Layout; v: Values; index: number }) {
  const t = index >= 0 ? lay.tiles[index] : lay.tiles[0];
  const look = useAnimatedStyle(() => {
    if (v.carry.get() < 0) return { opacity: 0, transform: [{ translateX: -9999 }] };
    const p = v.pull.get() * SAVE.magnetPull;
    const d = v.drop.get();
    let x = v.cx.get() + (lay.save.x - v.cx.get()) * p;
    let y = v.cy.get() + (lay.save.y - v.cy.get()) * p;
    x += (lay.save.x - x) * d;
    y += (lay.save.y - y) * d;
    const scale = (1 + SAVE.liftScale * v.lift.get()) * (1 - SAVE.magnetShrink * v.pull.get()) * (1 - 0.86 * d);
    return {
      opacity: 1 - interpolate(d, [0.55, 1], [0, 1], 'clamp'),
      transform: [
        { translateX: x - t.w / 2 },
        { translateY: y - t.h / 2 - SAVE.liftRise * v.lift.get() },
        { rotate: `${v.lean.get()}deg` },
        { scale },
      ],
      shadowOpacity: 0.55 * v.lift.get(),
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.carried, { width: t.w, height: t.h }, look]}>
      <View style={styles.carriedClip}>
        {index >= 0 && <Image source={PHOTOS[index].src} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />}
      </View>
    </Animated.View>
  );
}

function Bar({ lay, v, count, onSavePress, onSaveTap }: {
  lay: Layout; v: Values; count: number; onSavePress: (down: boolean) => void; onSaveTap: () => void;
}) {
  const saveLook = useAnimatedStyle(() => {
    const pull = v.pull.get();
    return { transform: [{ scale: v.gulp.get() * (1 + 0.12 * pull) * (1 - 0.06 * v.press.get()) }] };
  });
  const halo = useAnimatedStyle(() => ({ opacity: 0.9 * v.pull.get(), transform: [{ scale: 1 + 0.3 * v.pull.get() }] }));
  const badge = useAnimatedStyle(() => ({ opacity: v.roll.get(), transform: [{ translateY: 6 * (1 - v.roll.get()) }] }));
  const tabsW = lay.width - LAYOUT.edge * 2 - LAYOUT.saveSize - 12;
  return (
    <>
      <Glass radius={LAYOUT.barH / 2} style={[styles.tabs, { top: lay.barY, width: tabsW }]}>
        <View style={styles.tabRow}>
          {([['Home', GLYPH.home, true], ['Search', GLYPH.search, false], ['Inbox', GLYPH.inbox, false]] as const).map(([label, d, on]) => (
            <View key={label} style={styles.tab}>
              <Glyph d={d} size={22} color={on ? COLOR.ink : COLOR.mute} weight={on ? 2 : 1.75} />
              <Text style={[styles.tabText, { color: on ? COLOR.ink : COLOR.mute }]}>{label}</Text>
            </View>
          ))}
        </View>
      </Glass>
      <Animated.View style={[styles.halo, { left: lay.save.x - 50, top: lay.save.y - 50 }, halo]} pointerEvents="none" />
      <Animated.View style={[styles.saveWrap, { left: lay.save.x - LAYOUT.saveSize / 2, top: lay.save.y - LAYOUT.saveSize / 2 }, saveLook]}>
        <Pressable onPressIn={() => onSavePress(true)} onPressOut={() => onSavePress(false)} onPress={onSaveTap}
          accessibilityRole="button" accessibilityLabel={`Saved, ${COLLECTION.name}, ${count} places`}>
          <Glass radius={LAYOUT.saveSize / 2} style={{ width: LAYOUT.saveSize, height: LAYOUT.saveSize }}>
            <View style={styles.center}><Glyph d={GLYPH.bookmark} size={24} weight={2} /></View>
          </Glass>
        </Pressable>
        <View style={styles.badge} pointerEvents="none">
          <Animated.Text style={[styles.badgeText, badge]}>{count}</Animated.Text>
        </View>
      </Animated.View>
    </>
  );
}

function Toast({ lay, v, onView }: { lay: Layout; v: Values; onView: () => void }) {
  const look = useAnimatedStyle(() => ({ opacity: v.toast.get(), transform: [{ translateY: 8 * (1 - v.toast.get()) }, { scale: 0.96 + 0.04 * v.toast.get() }] }));
  return (
    <Animated.View style={[styles.toastWrap, { top: lay.barY - 56 }, look]}>
      <Glass radius={22} style={styles.toast}>
        <View style={styles.toastRow}>
          <Text style={styles.toastText}>Saved to <Text style={styles.toastStrong}>{COLLECTION.name}</Text></Text>
          <Pressable onPress={onView} hitSlop={10} accessibilityRole="button"><Text style={styles.toastAction}>View</Text></Pressable>
        </View>
      </Glass>
    </Animated.View>
  );
}

/** A place in the collection: a 2-column grid cell, the photo and its two caption lines under it. */
function SheetTile({ photo, slot, lay, entering }: { photo: number; slot: number; lay: Layout; entering?: SharedValue<number> }) {
  const w = (lay.width - LAYOUT.edge * 2 - LAYOUT.gap) / 2;
  const h = Math.round(w * 1.2);
  const x = (slot % 2) * (w + LAYOUT.gap);
  const y = Math.floor(slot / 2) * (h + SHEET_CAPTION + LAYOUT.gap);
  const look = useAnimatedStyle(() => {
    const e = entering ? entering.get() : 1;
    return { opacity: e, transform: [{ translateY: -14 * (1 - e) }, { scale: 0.94 + 0.06 * e }] };
  });
  const p = PHOTOS[photo];
  return (
    <Animated.View style={[styles.sheetTile, { left: x, top: y, width: w }, look]}>
      <View style={[styles.sheetImage, { height: h }]}>
        <Image source={p.src} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
        {entering && (
          <View style={styles.newTag}><Text style={styles.newText}>JUST SAVED</Text></View>
        )}
      </View>
      <Text style={styles.caption} numberOfLines={1}>{p.title}</Text>
      <Text style={styles.place} numberOfLines={1}>{p.place}</Text>
    </Animated.View>
  );
}

function Sheet({ lay, v, count, open, onClose }: { lay: Layout; v: Values; count: number; open: boolean; onClose: () => void }) {
  const travel = lay.height - lay.sheetY;
  const scrim = useAnimatedStyle(() => ({ opacity: v.sheet.get() }));
  const panel = useAnimatedStyle(() => ({ transform: [{ translateY: (1 - v.sheet.get()) * travel }] }));
  const fresh = count > SAVE.countBefore;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents={open ? 'auto' : 'none'}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.scrim, scrim]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="Close collection" />
      </Animated.View>
      <Animated.View style={[styles.sheet, { top: lay.sheetY, height: travel + 40 }, panel]} accessibilityViewIsModal>
        <Glass radius={LAYOUT.sheetRadius} style={StyleSheet.absoluteFill} thick />
        <View style={styles.grabber} />
        <View style={styles.sheetHead}>
          <View>
            <Text style={styles.sheetTitle} accessibilityRole="header">{COLLECTION.name}</Text>
            <Text style={styles.sheetMeta}>{`${count} places · ${fresh ? 'Updated just now' : 'Updated 2 days ago'}`}</Text>
          </View>
          <Pressable onPress={onClose} hitSlop={8} accessibilityRole="button" accessibilityLabel="Close">
            <Glass radius={18} style={{ width: 36, height: 36 }}>
              <View style={styles.center}><Glyph d={GLYPH.close} size={18} weight={2} /></View>
            </Glass>
          </Pressable>
        </View>
        <View style={styles.sheetGrid}>
          {COLLECTION.before.map((photo, j) => (
            <SheetTile key={photo} photo={photo} slot={fresh ? j + 1 : j} lay={lay} />
          ))}
          {fresh && <SheetTile photo={4} slot={0} lay={lay} entering={v.arrive} />}
        </View>
      </Animated.View>
    </View>
  );
}

function Screen({ lay, reduced, autoPlay }: { lay: Layout; reduced: boolean; autoPlay: boolean }) {
  const { count, sheetOpen, carried, gesture, touchStart, touchEnd, onSavePress, onSaveTap, onClose, values: v } = useSave({ lay, autoPlay, reduced });
  const ghost = useAnimatedStyle(() => ({
    opacity: v.ghostO.get(),
    transform: [
      { translateX: v.ghostX.get() - LAYOUT.finger / 2 },
      { translateY: v.ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 - 0.12 * v.ghostP.get() },
    ],
  }));
  const fadeTop = lay.barY - 72;
  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <View style={[styles.header, { top: lay.top }]}>
          <Text style={styles.title} accessibilityRole="header">Discover</Text>
          <Glass radius={18} style={{ width: 36, height: 36 }}>
            <View style={styles.center}><Text style={styles.initials}>LK</Text></View>
          </Glass>
        </View>
        <View style={[styles.chips, { top: lay.top + LAYOUT.chipsTop }]}>
          {['For you', 'Coast', 'Mountains', 'Rooms'].map((c, k) => (
            <View key={c} style={[styles.chip, k === 0 ? styles.chipOn : styles.chipOff]}>
              <Text style={[styles.chipText, { color: k === 0 ? COLOR.chipOnInk : COLOR.mute }]}>{c}</Text>
            </View>
          ))}
        </View>
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            {lay.tiles.map((_, i) => <Tile key={PHOTOS[i].id} i={i} lay={lay} v={v} />)}
          </View>
        </GestureDetector>
        <LinearGradient pointerEvents="none" colors={[COLOR.pageClear, COLOR.page]} locations={[0, 0.7]}
          style={[styles.fade, { top: fadeTop, height: lay.height - fadeTop }]} />
        <Bar lay={lay} v={v} count={count} onSavePress={onSavePress} onSaveTap={onSaveTap} />
        <Toast lay={lay} v={v} onView={onSaveTap} />
        <Carried lay={lay} v={v} index={carried} />
        <Sheet lay={lay} v={v} count={count} open={sheetOpen} onClose={onClose} />
        {autoPlay && <Animated.View style={[styles.ghost, ghost]} pointerEvents="none" />}
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.page },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { position: 'absolute', left: LAYOUT.edge, right: LAYOUT.edge, height: LAYOUT.titleH, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { ...TYPE.title, color: COLOR.ink },
  initials: { ...TYPE.chip, color: COLOR.ink, fontSize: 13 },
  chips: { position: 'absolute', left: LAYOUT.edge, right: 0, height: LAYOUT.chipH, flexDirection: 'row', gap: 8 },
  chip: { height: LAYOUT.chipH, borderRadius: LAYOUT.chipH / 2, paddingHorizontal: 14, justifyContent: 'center' },
  chipOn: { backgroundColor: COLOR.chipOn },
  chipOff: { borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.hairline },
  chipText: { ...TYPE.chip },
  tile: { position: 'absolute', borderRadius: LAYOUT.radius, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: COLOR.tile },
  mark: { position: 'absolute', right: 8, top: 8, width: 28, height: 28 },
  carried: {
    position: 'absolute', left: 0, top: 0, borderRadius: LAYOUT.radius, borderCurve: 'continuous',
    shadowColor: '#000', shadowRadius: 24, shadowOffset: { width: 0, height: 16 },
  },
  carriedClip: { flex: 1, borderRadius: LAYOUT.radius, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: COLOR.tile },
  fade: { position: 'absolute', left: 0, right: 0 },
  tabs: { position: 'absolute', left: LAYOUT.edge, height: LAYOUT.barH },
  tabRow: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around', paddingHorizontal: 12 },
  tab: { alignItems: 'center', gap: 3, width: 64 },
  tabText: { ...TYPE.tab },
  halo: { position: 'absolute', width: 100, height: 100, borderRadius: 50, backgroundColor: 'rgba(255,255,255,0.08)' },
  saveWrap: { position: 'absolute', width: LAYOUT.saveSize, height: LAYOUT.saveSize },
  badge: {
    position: 'absolute', right: -4, top: -4, minWidth: 22, height: 22, borderRadius: 11, paddingHorizontal: 6,
    backgroundColor: COLOR.badge, alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
  },
  badgeText: { ...TYPE.badge, color: COLOR.badgeInk },
  toastWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  toast: { height: 44 },
  toastRow: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 16, paddingHorizontal: 18 },
  toastText: { ...TYPE.toast, color: COLOR.mute },
  toastStrong: { ...TYPE.toast, color: COLOR.ink },
  toastAction: { ...TYPE.toastAction, color: COLOR.ink },
  scrim: { backgroundColor: COLOR.scrim },
  sheet: { position: 'absolute', left: 0, right: 0, borderTopLeftRadius: LAYOUT.sheetRadius, borderTopRightRadius: LAYOUT.sheetRadius, overflow: 'hidden' },
  grabber: { alignSelf: 'center', marginTop: 8, width: 36, height: 5, borderRadius: 2.5, backgroundColor: 'rgba(255,255,255,0.3)' },
  sheetHead: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', paddingHorizontal: LAYOUT.edge, paddingTop: 16 },
  sheetTitle: { ...TYPE.sheetTitle, color: COLOR.ink },
  sheetMeta: { ...TYPE.sheetMeta, color: COLOR.mute, marginTop: 2 },
  sheetGrid: { marginTop: 20, marginHorizontal: LAYOUT.edge },
  sheetTile: { position: 'absolute' },
  sheetImage: { borderRadius: LAYOUT.radius, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: COLOR.tile },
  caption: { ...TYPE.caption, color: COLOR.ink, marginTop: 8, height: 16 },
  place: { ...TYPE.caption, color: COLOR.mute, fontFamily: FONT.regular, height: 16 },
  newTag: {
    position: 'absolute', left: 8, bottom: 8, height: 22, borderRadius: 11, paddingHorizontal: 8, justifyContent: 'center',
    backgroundColor: 'rgba(10,10,11,0.62)', borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.rim,
  },
  newText: { ...TYPE.new, color: COLOR.ink },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
