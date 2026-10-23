/** "Latest spending": swipe a row left to flag or delete it; it leans with your finger. */
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { ReduceMotion, useAnimatedStyle, useReducedMotion, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';

import { BalanceCard } from './BalanceCard';
import { CardsView } from './CardsView';
import { EASE_OUT, QA_STATE, SPRING, TIMING } from './motion';
import { playSwipeHaptic } from './swipe-haptics';
import { SwipeRow, type ScriptTouch } from './SwipeRow';
import { TabPanel } from './TabPanel';
import { COLOR, FLAT, FONT, LAYOUT, TILT_VIEW, TYPE } from './theme';
import { SPENDING, type Transaction } from './transactions';
import { UndoPill } from './UndoPill';
import { useSwipeDemo } from './use-swipe-demo';

type Row = Transaction & { restoring?: boolean };

export type TiltSwipeActionsProps = { autoPlay?: boolean };

export function TiltSwipeActions({ autoPlay = false }: TiltSwipeActionsProps) {
  const [fontsLoaded] = useFonts({
    [FONT.medium]: require('./assets/Montserrat-Medium.ttf'),
    [FONT.semibold]: require('./assets/Montserrat-SemiBold.ttf'),
    [FONT.bold]: require('./assets/Montserrat-Bold.ttf'),
  });
  const reduceMotion = useReducedMotion();
  const [rows, setRows] = useState<Row[]>(SPENDING);
  const [flagged, setFlagged] = useState<Set<string>>(() => new Set());
  const [, setLast] = useState<{ item: Transaction; index: number } | null>(null);
  const [pillTitle, setPillTitle] = useState(''); // kept while the pill fades out after Undo
  const activeKey = useSharedValue('');
  const script = useSharedValue<ScriptTouch>({ seq: 0, key: '', down: false, tx: 0, vx: 0 });
  const pill = useSharedValue(0);
  const pillFrom = useSharedValue(0); // the deleted row's height above the pill, for the morph
  const balance = useSharedValue(BALANCE); // rolls as transactions are deleted and restored
  const demoPaused = useRef<{ get: () => boolean } | null>(null);
  const pillTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const rowsRef = useRef(rows);
  useEffect(() => { rowsRef.current = rows; }, [rows]);

  // Tabs: 0 Overview (the cards), 2 Spending (the list). The source cross-fades and slides the content.
  const [tab, setTab] = useState(QA_STATE === 'cards' ? 0 : 2);
  const tabShown = useSharedValue(QA_STATE === 'cards' ? 0 : 1); // 1 = spending
  const selectTab = useCallback((i: number) => {
    if (i === 1) return; // the wallet tab's screen is not part of this block
    setTab(i);
    tabShown.set(reduceMotion
      ? withTiming(i === 2 ? 1 : 0, { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
      : withTiming(i === 2 ? 1 : 0, { duration: TIMING.tab, easing: EASE_OUT }));
  }, [reduceMotion, tabShown]);
  const menuShown = useSharedValue(QA_STATE === 'menu' ? 1 : 0);
  const spendingStyle = useAnimatedStyle(() => ({ opacity: tabShown.get() * (1 - MENU_FADE * Math.min(menuShown.get(), 1)),
    // Behind the open menu the list recedes a little (the source's rows sit ~6 pt higher in the menu frame).
    transform: [{ translateY: (1 - tabShown.get()) * TIMING.tabSlide - MENU_LIFT * menuShown.get() }] }));
  const overviewStyle = useAnimatedStyle(() => ({ opacity: 1 - tabShown.get(), transform: [{ translateY: tabShown.get() * -TIMING.tabSlide }] }));

  // The demo's finger works in the flat app's coordinates.
  const listTop = FLAT.rowsTop;

  const showPill = useCallback((on: boolean) => {
    pill.set(reduceMotion
      ? withTiming(on ? 1 : 0, { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
      : withSpring(on ? 1 : 0, SPRING.pill));
  }, [pill, reduceMotion]);

  const onDelete = useCallback((key: string) => {
    const index = rowsRef.current.findIndex((r) => r.key === key);
    if (index < 0) return;
    const item = rowsRef.current[index];
    setRows((list) => list.filter((r) => r.key !== key));
    setLast({ item, index });
    setPillTitle(item.title);
    const rowCentre = FLAT.rowsTop + LAYOUT.row * (index + 0.5);
    const pillCentre = PLANE_H - PILL_BOTTOM - LAYOUT.pillHeight / 2;
    pillFrom.set(rowCentre - pillCentre);
    pill.set(0);
    showPill(true);
    balance.set(balance.get() + amountOf(item));
    const scripted = autoPlay && demoPaused.current && !demoPaused.current.get();
    if (!scripted) playSwipeHaptic('commit');
    if (pillTimer.current) clearTimeout(pillTimer.current);
    // The demo hides the pill with its own Undo tap, on the loop clock; by hand it times out.
    if (!scripted) pillTimer.current = setTimeout(() => showPill(false), TIMING.undoVisible);
  }, [autoPlay, balance, pill, pillFrom, showPill]);

  const onUndo = useCallback(() => {
    setLast((current) => {
      if (!current) return current;
      balance.set(balance.get() - amountOf(current.item));
      setRows((list) => {
        const next = list.slice();
        next.splice(Math.min(current.index, next.length), 0, { ...current.item, restoring: true });
        return next;
      });
      return null;
    });
    showPill(false);
    if (pillTimer.current) clearTimeout(pillTimer.current);
  }, [balance, showPill]);

  const onUndoPress = useCallback(() => {
    playSwipeHaptic('undo');
    onUndo();
  }, [onUndo]);

  const onFlag = useCallback((key: string) => {
    setFlagged((set) => {
      const next = new Set(set);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  // Demo restart: every row back, nothing flagged, no pill.
  const onReset = useCallback(() => {
    setRows(SPENDING);
    setFlagged(new Set());
    setLast(null);
    balance.set(BALANCE);
    showPill(false);
  }, [balance, showPill]);

  useEffect(() => () => { if (pillTimer.current) clearTimeout(pillTimer.current); }, []);

  const undoPoint = { x: FLAT_WIDTH - LAYOUT.gutter - LAYOUT.fab - 12 - 50, y: FLAT_HEIGHT - PILL_BOTTOM - LAYOUT.pillHeight / 2 };
  const { touchStart, touchEnd, paused, clock, ghost } = useSwipeDemo({ autoPlay, listTop, undoPoint, activeKey, script, onUndo, onReset });
  useEffect(() => { demoPaused.current = paused; }, [paused]);
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghost.ghostO.get(),
    transform: [
      { translateX: ghost.ghostX.get() - LAYOUT.finger / 2 },
      { translateY: ghost.ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 - 0.15 * ghost.ghostPress.get() },
    ],
  }));

  if (!fontsLoaded) return <View style={styles.root} />;
  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <LinearGradient colors={COLOR.desk} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
        {/* The app, laid out flat and seen at the source's angle. */}
        <View style={[styles.plane, { transform: [{ matrix: PLANE }] }]}>
          <LinearGradient colors={COLOR.gradient} locations={COLOR.gradientStops} start={{ x: 0.5, y: 0 }} end={{ x: 0.5, y: 1 }} style={StyleSheet.absoluteFill} />
          <Animated.View style={[StyleSheet.absoluteFill, overviewStyle]} pointerEvents={tab === 0 ? 'box-none' : 'none'}>
            <CardsView balance={balance} reduceMotion={reduceMotion} />
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, spendingStyle]} pointerEvents={tab === 2 ? 'box-none' : 'none'}>
          <View style={styles.card}><BalanceCard balance={balance} reduceMotion={reduceMotion} /></View>
          <Text style={styles.section} accessibilityRole="header">Latest spending</Text>
          <View style={styles.rows}>
            {rows.map((item) => (
              <SwipeRow
                key={item.key}
                item={item}
                restoring={!!item.restoring}
                flagged={flagged.has(item.key)}
                width={FLAT_WIDTH}
                reduceMotion={reduceMotion}
                activeKey={activeKey}
                script={script}
                onDelete={onDelete}
                onFlag={onFlag}
              />
            ))}
          </View>
          </Animated.View>
          <UndoPill shown={pill} fromY={pillFrom} title={pillTitle} bottom={PILL_BOTTOM} reduceMotion={reduceMotion} onUndo={onUndoPress} />
          <TabPanel reduceMotion={reduceMotion} tab={tab} onTab={selectTab} clock={clock} menuShown={menuShown} />
          {autoPlay && <Animated.View style={[styles.ghost, ghostStyle]} pointerEvents="none" />}
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const FLAT_WIDTH = 402;
/** How much the list fades behind the open menu (the source's rows read at ~44 %). */
const MENU_FADE = 0.68;
const MENU_LIFT = 6;
const BALANCE = 1148.9;
/** '−34$' → 34: deleting a spend gives it back to the balance. */
function amountOf(item: Transaction) {
  return Number(item.amount.replace(/[^0-9.]/g, '')) || 0;
}
const FLAT_HEIGHT = 874;
// The source's screen reaches a little past the 402 × 874 design area on the right and at the bottom.
const PLANE_W = 406; // the source's screen edge, measured on f017 at two heights (X ≈ 403–409)
const PLANE_H = 888;
const PILL_BOTTOM = PLANE_H - FLAT.panel.top + 12;
// Column-major 4 × 4 (as CSS matrix3d): an affine map from the flat app to the screen.
const T = TILT_VIEW;
const PLANE = [
  T.scale * T.u[0], T.scale * T.u[1], 0, 0,
  T.scale * T.v[0], T.scale * T.v[1], 0, 0,
  0, 0, 1, 0,
  T.scale * (T.p0[0] - T.cropX - FLAT_HEIGHT * T.v[0]), T.scale * (T.p0[1] - FLAT_HEIGHT * T.v[1]), 0, 1,
];

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.gradient[0] },
  plane: {
    position: 'absolute', left: 0, top: 0, width: PLANE_W, height: PLANE_H,
    borderRadius: TILT_VIEW.screenRadius, overflow: 'hidden', transformOrigin: 'left top',
  },
  card: { position: 'absolute', left: FLAT.card.left, width: FLAT_WIDTH - FLAT.card.left - FLAT.card.right, top: FLAT.card.top },
  section: { ...TYPE.section, color: COLOR.title, position: 'absolute', left: FLAT.section.left, top: FLAT.section.centreY - TYPE.section.lineHeight / 2 },
  // The list ends at the tab panel, as in the source (a third row does not peek under its soft edge).
  rows: { position: 'absolute', left: 0, width: FLAT_WIDTH, top: FLAT.rowsTop, height: FLAT.panel.top - 8 - FLAT.rowsTop, overflow: 'hidden' },
  list: { flex: 1 },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
