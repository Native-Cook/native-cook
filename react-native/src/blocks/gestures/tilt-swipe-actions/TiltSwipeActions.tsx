/**
 * "Latest spending" in a private-banking ledger. Swipe a transaction right and keep pulling: the line breaks into
 * equal shares, one person per detent, yours in ink; let go and your share stays while the others become a request,
 * with Undo. Swipe left for Flag and Delete.
 */
import { useFonts } from 'expo-font';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Image, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing, ReduceMotion, useAnimatedStyle, useReducedMotion, useSharedValue, withDelay, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

import { BalanceCard } from './BalanceCard';
import { Flyers, useFlight } from './Flight';
import { EASE_OUT, EASE_SHEET, QA_HOLD_AT, QA_STATE, SWIPE, TIMING } from './motion';
import { RollText } from './RollText';
import { playSwipeHaptic } from './swipe-haptics';
import { SwipeRow, type RowCommand, type ScriptTouch } from './SwipeRow';
import { TabPanel } from './TabPanel';
import { COLOR, DIDONE_DROP, DIDONE_ROOM, FONT_FILES, LAYOUT, sceneLayout, TYPE } from './theme';
import { LINK_SPAN, Toast, type ToastState } from './Toast';
import { money, shares, SPENDING, weekTotal, type Transaction } from './transactions';
import { useSwipeDemo } from './use-swipe-demo';

type Row = { item: Transaction; leaving?: number; restoring?: boolean }; // leaving: the slot index it folds in

export type TiltSwipeActionsProps = { autoPlay?: boolean };

const NEVER = { reduceMotion: ReduceMotion.Never };
const fresh = (): Row[] => SPENDING.map((item) => ({ item }));

export function TiltSwipeActions({ autoPlay = false }: TiltSwipeActionsProps) {
  const [fontsLoaded] = useFonts(FONT_FILES);
  const reduceMotion = useReducedMotion();
  const { width, height } = useWindowDimensions();
  const { top, bottom } = useSafeAreaInsets();
  const scene = useMemo(() => sceneLayout(width, height, { top, bottom }), [width, height, top, bottom]);

  const [rows, setRows] = useState<Row[]>(fresh);
  const [splits, setSplits] = useState<Record<string, number>>({});
  const [flagged, setFlagged] = useState<Set<string>>(() => new Set());
  const [toast, setToast] = useState<ToastState | null>(null);
  const [toastLive, setToastLive] = useState(false);
  const [instant, setInstant] = useState(false); // the demo's restart: rows jump to their slots
  useEffect(() => {
    if (!instant) return undefined;
    const id = setTimeout(() => setInstant(false), 0);
    return () => clearTimeout(id);
  }, [instant]);
  const activeKey = useSharedValue('');
  const dim = useSharedValue(0);
  const script = useSharedValue<ScriptTouch>({ seq: 0, key: '', kind: 'up', dx: 0, vx: 0, x: 0 });
  const command = useSharedValue<RowCommand>({ seq: 0, key: '', kind: 'close', n: 0 });
  const toastShown = useSharedValue(0);
  const countdown = useSharedValue(0);
  const flight = useFlight(scene.toastTop, reduceMotion);
  const toastRef = useRef<ToastState | null>(null);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => { toastRef.current = toast; }, [toast]);

  const later = useCallback((ms: number, run: () => void) => {
    timers.current.push(setTimeout(run, ms));
  }, []);

  const menuShown = useSharedValue(QA_STATE === 'menu' ? 1 : 0);
  // Behind the FAB's open menu the ledger recedes a little.
  const pageStyle = useAnimatedStyle(() => ({ opacity: 1 - MENU_FADE * Math.min(menuShown.get(), 1) }));
  // The toast covers the link (the mock's third frame); the link steps aside as the toast comes, so they never overlap.
  const linkStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(toastShown.get() / LINK_SPAN, 1) }));

  const live = rows.filter((r) => r.leaving === undefined);
  const shownCount = Math.min(live.length, toast ? scene.capacityUnderToast : scene.capacity);
  const sheetHeight = LAYOUT.listPad + Math.max(shownCount, 1) * LAYOUT.row + 3; // 440 for six rows, rules included
  const sheetH = useSharedValue(sheetHeight);
  useEffect(() => {
    const to = withTiming(sheetHeight, { duration: reduceMotion ? TIMING.reduced : TIMING.sheet, easing: EASE_OUT, ...NEVER });
    // Shrinking waits for the row it drops to fade, so no row is ever drawn outside the sheet.
    sheetH.set(sheetHeight < sheetH.get() && !reduceMotion ? withDelay(TIMING.toastOut * 0.7, to) : to);
  }, [reduceMotion, sheetH, sheetHeight]);
  const sheetStyle = useAnimatedStyle(() => ({ height: sheetH.get() }));

  const amountOf = useCallback((item: Transaction) => (splits[item.key] ? -shares(item.amount, splits[item.key])[0] : item.amount), [splits]);
  const total = weekTotal(live.map((r) => amountOf(r.item)));
  const totalMoney = money(total);

  const hideToast = useCallback(() => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToastLive(false);
    setToast(null);
    toastShown.set(withTiming(0, { duration: TIMING.toastOut, easing: EASE_OUT, ...NEVER }));
  }, [toastShown]);

  const showToast = useCallback((next: ToastState, delay: number) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToast(next);
    setToastLive(true);
    const rise = reduceMotion
      ? withTiming(1, { duration: TIMING.reduced, ...NEVER })
      : withTiming(1, { duration: TIMING.toastIn, easing: EASE_SHEET });
    toastShown.set(0);
    toastShown.set(withDelay(delay, rise));
    countdown.set(1);
    countdown.set(withDelay(delay, withTiming(0, { duration: TIMING.undoVisible, easing: Easing.linear, ...NEVER })));
    if (QA_HOLD_AT !== null) return; // QA stills: the toast stays while the clock is frozen
    toastTimer.current = setTimeout(() => {
      hideToast();
      scheduleOnUI(flight.fade);
    }, TIMING.undoVisible + delay);
  }, [countdown, flight, hideToast, reduceMotion, toastShown]);

  const onSplit = useCallback((key: string, n: number) => {
    const item = SPENDING.find((s) => s.key === key);
    if (!item) return;
    setSplits((s) => ({ ...s, [key]: n }));
    showToast({ kind: 'split', key, n, amount: item.amount }, reduceMotion ? 0 : TIMING.flightDelay);
  }, [reduceMotion, showToast]);

  const onUnsplit = useCallback((key: string) => {
    setSplits((s) => {
      const next = { ...s };
      delete next[key];
      return next;
    });
  }, []);

  const onDelete = useCallback((key: string) => {
    setRows((list) => {
      const liveNow = list.filter((r) => r.leaving === undefined);
      const index = liveNow.findIndex((r) => r.item.key === key);
      if (index < 0) return list;
      showToast({ kind: 'delete', key, item: liveNow[index].item, index }, 0);
      return list.map((r) => (r.item.key === key ? { item: r.item, leaving: index } : r));
    });
    later(SWIPE.fold + 40, () => setRows((list) => list.filter((r) => r.item.key !== key || r.leaving === undefined)));
  }, [later, showToast]);

  const onFlag = useCallback((key: string) => {
    setFlagged((set) => {
      const next = new Set(set);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  const onUndo = useCallback(() => {
    const current = toastRef.current;
    if (!current) return;
    hideToast();
    if (current.kind === 'split') {
      // The settled row stays one row: its amount, subtitle and "of" line roll and cross-fade back (RollText), the
      // week total rolls back, and the toast leaves with the monograms it holds. Nothing re-opens.
      onUnsplit(current.key);
      scheduleOnUI(flight.fade);
      return;
    }
    const { item, index } = current;
    setRows((list) => {
      const others = list.filter((r) => r.item.key !== item.key);
      const liveOthers = others.filter((r) => r.leaving === undefined);
      const anchor = liveOthers[index];
      const at = anchor ? others.indexOf(anchor) : others.length;
      const next = others.slice();
      next.splice(at, 0, { item, restoring: true });
      return next;
    });
    later(TIMING.toastIn + 100, () => setRows((list) => list.map((r) => (r.item.key === item.key ? { item: r.item } : r))));
  }, [flight, hideToast, later, onUnsplit]);

  const onUndoPress = useCallback(() => {
    playSwipeHaptic('undo');
    onUndo();
  }, [onUndo]);

  const onUndoSplit = useCallback((key: string) => {
    if (toastRef.current?.kind === 'split' && toastRef.current.key === key) onUndo();
    else onUnsplit(key);
  }, [onUndo, onUnsplit]);

  // Demo restart: every row back, nothing split or flagged, no toast.
  const onReset = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    hideToast();
    setInstant(true);
    setRows(fresh());
    setSplits({});
    setFlagged(new Set());
    scheduleOnUI(() => {
      'worklet';
      activeKey.set('');
      dim.set(0);
      command.set({ seq: command.get().seq + 1, key: '*', kind: 'reset', n: 0 });
    });
    scheduleOnUI(flight.reset);
  }, [activeKey, command, dim, flight, hideToast]);

  useEffect(() => () => {
    timers.current.forEach(clearTimeout);
    if (toastTimer.current) clearTimeout(toastTimer.current);
  }, []);

  const undoPoint = { x: LAYOUT.gutter + scene.listWidth - 8 - UNDO_HALF, y: scene.toastTop + LAYOUT.toast / 2 };
  // The script waits for the rows: its clock starts once the fonts have loaded and the list is on screen.
  const { touchStart, touchEnd, ghost } = useSwipeDemo({
    autoPlay, ready: fontsLoaded, rowsTop: scene.rowsTop, rowsLeft: LAYOUT.gutter, undoPoint, script, onUndo, onReset,
  });
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghost.ghostO.get(),
    transform: [
      { translateX: ghost.ghostX.get() - LAYOUT.finger / 2 },
      { translateY: ghost.ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 - 0.15 * ghost.ghostPress.get() },
    ],
  }));
  if (!fontsLoaded) return <View style={styles.root} />;

  let liveIndex = 0;
  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        {/* Laid paper: one 240 pt tile of grain drawn once (the mock's 16 % multiply noise), laid edge to edge. */}
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          {paperTiles(width, height).map((t) => <Image key={t.key} source={PAPER} style={[styles.tile, { left: t.x, top: t.y }]} />)}
        </View>
        <Animated.View style={[StyleSheet.absoluteFill, pageStyle]} pointerEvents="box-none">
          <View style={[styles.card, { top: scene.cardTop }]}>
            <BalanceCard width={scene.listWidth} />
          </View>
          <View style={[styles.section, { top: scene.sectionTop }]}>
            <Text style={styles.sectionTitle} accessibilityRole="header">Latest spending</Text>
            <RollText
              id={String(total)}
              reduceMotion={reduceMotion}
              direction={Math.abs(total) < 332.6 ? 'down' : 'up'}
              style={styles.sectionMeta}
            >
              <Text style={styles.sectionMetaText} accessibilityLabel={`Spent ${totalMoney.whole.replace('\u2013', '')}${totalMoney.cents} this week`}>
                {`${totalMoney.whole}${totalMoney.cents} this week`}
              </Text>
            </RollText>
          </View>
          <View style={[styles.sheetSlot, { top: scene.listTop, width: scene.listWidth }]}>
            <Animated.View style={[styles.sheet, sheetStyle]} pointerEvents="none" />
            {rows.map((r) => {
              const index = r.leaving ?? liveIndex++;
              const slot = LAYOUT.listPad + index * LAYOUT.row;
              return (
                <SwipeRow
                  key={r.item.key}
                  item={r.item}
                  top={slot}
                  screenTop={scene.listTop + slot}
                  rowLeft={LAYOUT.gutter}
                  split={splits[r.item.key] ?? 0}
                  flagged={flagged.has(r.item.key)}
                  hidden={r.leaving === undefined && index >= shownCount}
                  first={index === 0}
                  leaving={r.leaving !== undefined}
                  restoring={!!r.restoring}
                  hudBelow={index === 0}
                  instant={instant}
                  width={scene.listWidth}
                  reduceMotion={reduceMotion}
                  activeKey={activeKey}
                  dim={dim}
                  script={script}
                  command={command}
                  flight={flight}
                  onSplit={onSplit}
                  onDelete={onDelete}
                  onFlag={onFlag}
                  onUndoSplit={onUndoSplit}
                />
              );
            })}
          </View>
          <Animated.Text style={[styles.link, { top: scene.linkTop }, linkStyle]} accessibilityRole="link">ALL TRANSACTIONS</Animated.Text>
        </Animated.View>
        <Toast
          toast={toast}
          live={toastLive}
          shown={toastShown}
          countdown={countdown}
          top={scene.toastTop}
          width={scene.listWidth}
          onUndo={onUndoPress}
          reduceMotion={reduceMotion}
        />
        <Flyers flight={flight} />
        <TabPanel scene={scene} reduceMotion={reduceMotion} menuShown={menuShown} />
        {autoPlay && <Animated.View style={[styles.ghost, ghostStyle]} pointerEvents="none" />}
      </View>
    </GestureHandlerRootView>
  );
}

const PAPER = require('./assets/paper.png');
const TILE = 240;

function paperTiles(width: number, height: number) {
  const tiles: { key: string; x: number; y: number }[] = [];
  for (let y = 0; y < height; y += TILE) for (let x = 0; x < width; x += TILE) tiles.push({ key: `${x}-${y}`, x, y });
  return tiles;
}
const SECTION_META_TOP = 12.33;
/** How much the ledger fades behind the open menu. */
const MENU_FADE = 0.5;
/** Half the Undo button's width (12 + 16 glyph + 6 + "Undo" + 12). */
const UNDO_HALF = 40;

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.paper },
  tile: { position: 'absolute', width: TILE, height: TILE },
  card: { position: 'absolute', left: LAYOUT.gutter },
  section: {
    position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, height: LAYOUT.sectionTitle,
  },
  sectionTitle: { ...TYPE.section, color: COLOR.ink, marginTop: DIDONE_DROP.title + 4 / 3 - DIDONE_ROOM.title },
  // On the title's baseline (CSS baseline alignment, measured on the mock).
  sectionMeta: { position: 'absolute', right: 0, top: SECTION_META_TOP, width: 200, height: 16 },
  sectionMetaText: { ...TYPE.sectionMeta, color: COLOR.ink2 },
  sheetSlot: { position: 'absolute', left: LAYOUT.gutter, height: LAYOUT.listPad + SPENDING.length * LAYOUT.row + 3 },
  // The ledger: a full-ink rule over it, a softer one closing it; the rows hang on the paper itself.
  sheet: { position: 'absolute', left: 0, right: 0, top: 0, borderTopWidth: 1, borderTopColor: COLOR.ink, borderBottomWidth: 1, borderBottomColor: COLOR.ruleBottom },
  link: { ...TYPE.link, color: COLOR.brassPaper, position: 'absolute', left: 0, right: 0, height: 24, textAlign: 'center' },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
