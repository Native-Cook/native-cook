/**
 * A transaction row with trailing actions. Drag left: the row lifts into a glass slab and leans with
 * the finger's velocity; the actions grow in the uncovered strip; past the full-swipe line Delete takes
 * the whole strip. The finger and the demo script call the same worklets: begin → move → end.
 */
import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { memo, useEffect, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  interpolateColor,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { EASE_OUT, SPRING, SWIPE, TILT, TIMING } from './motion';
import { playSwipeHaptic, type SwipeHaptic } from './swipe-haptics';
import { COLOR, FLAT, LAYOUT, OPEN_X, TYPE } from './theme';
import type { Transaction } from './transactions';

/**
 * The demo's finger as state, not events: several writes can land in one frame and a reaction only
 * sees the last one, so the row compares `down` with its own state to know whether to begin or end.
 */
export type ScriptTouch = { seq: number; key: string; down: boolean; tx: number; vx: number };

type Props = {
  item: Transaction;
  restoring: boolean;
  flagged: boolean;
  width: number;
  reduceMotion: boolean;
  activeKey: SharedValue<string>; // the row that owns the touch; any other open row closes
  script: SharedValue<ScriptTouch>;
  onDelete: (key: string) => void;
  onFlag: (key: string) => void;
};

const NEVER = { reduceMotion: ReduceMotion.Never };

export const SwipeRow = memo(function SwipeRow({ item, restoring, flagged, width, reduceMotion, activeKey, script, onDelete, onFlag }: Props) {
  const { key } = item;
  const x = useSharedValue(restoring ? -width * 1.15 : 0);
  const height = useSharedValue(restoring ? 0 : 1);
  const tilt = useSharedValue(0);
  const lift = useSharedValue(0);
  const full = useSharedValue(0); // Delete has taken the whole strip
  const red = useSharedValue(0); // Delete tile colour, white → pink
  const strip = useSharedValue(restoring ? 0 : 1); // hides the actions while a row slides back in
  const fade = useSharedValue(restoring && reduceMotion ? 0 : 1);
  const start = useSharedValue(0);
  const dragging = useSharedValue(false);
  const removing = useSharedValue(false);
  const opened = useSharedValue(false);

  const fullLine = width * SWIPE.fullAt;
  const limit = width * 0.85;

  useEffect(() => {
    if (!restoring) return;
    if (reduceMotion) {
      height.set(1);
      x.set(0);
      strip.set(1);
      fade.set(withTiming(1, { duration: TIMING.reduced, ...NEVER }));
      return;
    }
    // Undo: the gap opens, then the row slides back in from where it left.
    height.set(withSpring(1, SPRING.restore));
    x.set(withDelay(120, withSpring(0, SPRING.restore, (done) => { if (done) strip.set(1); })));
  }, [fade, height, reduceMotion, restoring, strip, x]);

  const haptic = (kind: SwipeHaptic, silent: boolean) => {
    'worklet';
    if (!silent) scheduleOnRN(playSwipeHaptic, kind);
  };

  const snap = (target: number, velocity: number) => {
    'worklet';
    return reduceMotion
      ? withTiming(target, { duration: TIMING.reduced, ...NEVER })
      : withSpring(target, { ...SPRING.snap, velocity });
  };

  const setFull = (on: boolean, silent: boolean) => {
    'worklet';
    if ((full.get() > 0.5) === on) return;
    full.set(reduceMotion ? withTiming(on ? 1 : 0, { duration: TIMING.reduced, ...NEVER }) : withSpring(on ? 1 : 0, SPRING.tileStretch));
    red.set(withTiming(on ? 1 : 0, { duration: TIMING.colour, ...NEVER }));
    haptic(on ? 'arm' : 'disarm', silent);
  };

  const close = (velocity = 0) => {
    'worklet';
    opened.set(false);
    x.set(snap(0, velocity));
    setFull(false, true);
  };

  const remove = (velocity: number) => {
    'worklet';
    removing.set(true);
    dragging.set(false);
    lift.set(withSpring(0, SPRING.lift));
    const collapse = () => {
      'worklet';
      const done = (finished?: boolean) => {
        'worklet';
        if (finished) scheduleOnRN(onDelete, key);
      };
      height.set(reduceMotion ? withTiming(0, { duration: TIMING.reducedGap, ...NEVER }, done) : withSpring(0, SPRING.collapse, done));
    };
    if (reduceMotion) {
      fade.set(withTiming(0, { duration: TIMING.reduced, ...NEVER }, (finished) => { if (finished) collapse(); }));
      return;
    }
    // Keep leaning into the throw while it leaves.
    tilt.set(withSpring(TILT.max, SPRING.follow));
    x.set(withTiming(-width * 1.15, { duration: TIMING.flyOut, easing: EASE_OUT }, (finished) => {
      if (!finished) return;
      strip.set(0);
      collapse();
    }));
  };

  const begin = () => {
    'worklet';
    if (removing.get()) return;
    cancelAnimation(x);
    start.set(x.get());
    dragging.set(true);
    activeKey.set(key);
    if (!reduceMotion) lift.set(withSpring(1, SPRING.lift));
  };

  const move = (tx: number, vx: number, silent: boolean) => {
    'worklet';
    if (!dragging.get()) return;
    const raw = start.get() + tx;
    let next = raw;
    if (raw > 0) next = raw * SWIPE.rightRubber;
    else if (raw < -limit) next = -limit + (raw + limit) * SWIPE.fullRubber;
    const wasOpen = -x.get() >= SWIPE.openAt;
    x.set(next);
    if (!reduceMotion) {
      const lean = Math.max(-TILT.max, Math.min(TILT.max, -vx * TILT.perVelocity));
      tilt.set(withSpring(lean, SPRING.follow));
    }
    if (!wasOpen && -next >= SWIPE.openAt) haptic('open', silent);
    setFull(-next > fullLine, silent);
  };

  const end = (vx: number) => {
    'worklet';
    if (!dragging.get()) return;
    dragging.set(false);
    lift.set(withSpring(0, SPRING.lift));
    tilt.set(withSpring(0, SPRING.follow));
    const w = -x.get();
    if (full.get() > 0.5 || (vx < SWIPE.deleteFlick && w > width * SWIPE.deleteFlickAt)) {
      remove(vx);
    } else if ((w > SWIPE.openAt || vx < SWIPE.openFlick) && vx < 300) {
      opened.set(true);
      x.set(snap(OPEN_X, vx));
    } else close(vx);
  };

  // Buttons and accessibility take the same path as a full swipe.
  const removeNow = () => scheduleOnUI(() => {
    'worklet';
    if (!removing.get()) remove(0);
  });

  // Another row took the touch: close this one.
  useAnimatedReaction(() => activeKey.get(), (owner) => {
    if (owner !== key && !dragging.get() && !removing.get() && x.get() !== 0) close();
  });

  // The demo's finger.
  useAnimatedReaction(() => script.get().seq, (seq, previous) => {
    if (seq === previous) return;
    const touch = script.get();
    if (touch.key !== key) return;
    if (touch.down) {
      if (!dragging.get()) begin();
      move(touch.tx, touch.vx, true);
    } else if (dragging.get()) {
      move(touch.tx, touch.vx, true);
      end(touch.vx);
    }
  });

  const gesture = useMemo(() => {
    const pan = Gesture.Pan()
      .activeOffsetX([-SWIPE.activeOffsetX, SWIPE.activeOffsetX])
      .failOffsetY([-SWIPE.failOffsetY, SWIPE.failOffsetY])
      .onStart(() => begin())
      .onUpdate((e) => move(e.translationX, e.velocityX, false))
      .onEnd((e) => end(e.velocityX))
      .onFinalize(() => end(0));
    const tap = Gesture.Tap().onEnd(() => {
      if (opened.get()) close();
    });
    return Gesture.Race(pan, tap);
    // The worklets only read shared values and props captured at render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, width, reduceMotion]);

  const shell = useAnimatedStyle(() => ({ height: LAYOUT.row * height.get(), opacity: fade.get() }));
  const content = useAnimatedStyle(() => ({
    transform: [
      { perspective: TILT.perspective },
      { translateX: x.get() },
      { rotateY: `${tilt.get()}deg` },
      { scale: 1 + (TILT.lift - 1) * lift.get() },
    ],
  }));
  const slab = useAnimatedStyle(() => ({ opacity: lift.get() }));
  // Light on the glass slab: a sheen that slides against the row as it moves, brighter as it leans.
  const sheen = useAnimatedStyle(() => ({
    opacity: lift.get() * (0.5 + Math.min(Math.abs(tilt.get()) / TILT.max, 1) * 0.5),
    transform: [{ translateX: 120 + x.get() * TIMING.sheen }, { skewX: '-20deg' }],
  }));
  const stripStyle = useAnimatedStyle(() => ({ width: Math.max(-x.get(), 0), opacity: strip.get() }));

  // Tiles reveal as the strip passes them; at full swipe Delete stretches over the strip.
  const deleteTile = useAnimatedStyle(() => {
    const w = Math.max(-x.get(), 0);
    const p = Math.min(Math.max((w - LAYOUT.gutter) / LAYOUT.tile, 0), 1);
    const stretched = Math.max(w - LAYOUT.gutter - LAYOUT.tileGap, LAYOUT.tile);
    return {
      width: LAYOUT.tile + (stretched - LAYOUT.tile) * full.get(),
      opacity: p,
      transform: [{ scale: 0.6 + 0.4 * p }],
      backgroundColor: interpolateColor(red.get(), [0, 1], [COLOR.white, COLOR.pink]),
    };
  });
  const deleteInk = useAnimatedStyle(() => ({ opacity: 1 - red.get() }));
  const deleteInkOn = useAnimatedStyle(() => ({ opacity: red.get() }));
  const flagTile = useAnimatedStyle(() => {
    const w = Math.max(-x.get(), 0);
    const p = Math.min(Math.max((w - LAYOUT.gutter - LAYOUT.tile - LAYOUT.tileGap) / LAYOUT.tile, 0), 1);
    return { opacity: p * (1 - full.get()), transform: [{ scale: (0.6 + 0.4 * p) * (1 - 0.3 * full.get()) }] };
  });

  return (
    <Animated.View
      style={[styles.shell, shell]}
      accessible
      accessibilityRole="button"
      accessibilityLabel={`${item.title}, ${item.when}, ${item.amount}${flagged ? ', flagged' : ''}`}
      accessibilityHint="Swipe left for actions"
      accessibilityActions={[{ name: 'flag', label: flagged ? 'Unflag' : 'Flag' }, { name: 'delete', label: 'Delete' }]}
      onAccessibilityAction={(e) => {
        if (e.nativeEvent.actionName === 'flag') onFlag(key);
        if (e.nativeEvent.actionName === 'delete') removeNow();
      }}
    >
      <Animated.View style={[styles.strip, stripStyle]}>
        <Animated.View style={[styles.tile, styles.flagTile, flagTile]}>
          <Pressable style={styles.tilePress} onPress={() => { onFlag(key); activeKey.set(''); }} accessibilityLabel={flagged ? 'Unflag' : 'Flag'}>
            <Feather name="flag" size={20} color={COLOR.white} />
            <Text style={[styles.tileText, styles.tileTextLight]}>{flagged ? 'Unflag' : 'Flag'}</Text>
          </Pressable>
        </Animated.View>
        <Animated.View style={[styles.tile, styles.deleteTile, deleteTile]}>
          <Pressable style={styles.tilePress} onPress={removeNow} accessibilityLabel="Delete">
            <View>
              <Animated.View style={deleteInk}><Feather name="trash-2" size={20} color={COLOR.pink} /></Animated.View>
              <Animated.View style={[StyleSheet.absoluteFill, deleteInkOn]}><Feather name="trash-2" size={20} color={COLOR.white} /></Animated.View>
            </View>
            <View>
              <Animated.Text style={[styles.tileText, styles.tileTextPink, deleteInk]}>Delete</Animated.Text>
              <Animated.Text style={[styles.tileText, styles.tileTextLight, styles.overlay, deleteInkOn]}>Delete</Animated.Text>
            </View>
          </Pressable>
        </Animated.View>
      </Animated.View>

      <GestureDetector gesture={gesture}>
        <Animated.View style={[styles.content, content]}>
          <Animated.View style={[styles.slab, slab]}>
            <Animated.View style={[styles.sheen, sheen]}>
              <LinearGradient colors={COLOR.sheen} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
            </Animated.View>
          </Animated.View>
          <View style={styles.circle}>
            <Feather name={item.icon} size={24} color={item.tint} />
          </View>
          <View style={styles.text}>
            <Text style={styles.title} numberOfLines={1}>{item.title}</Text>
            <Text style={styles.sub}>{item.when}</Text>
          </View>
          {flagged && <View style={styles.flagDot} />}
          <Text style={styles.amount}>{item.amount}</Text>
        </Animated.View>
      </GestureDetector>
    </Animated.View>
  );
});

const styles = StyleSheet.create({
  shell: { height: LAYOUT.row, justifyContent: 'center' },
  strip: { position: 'absolute', right: 0, top: 0, bottom: 0, overflow: 'hidden' },
  tile: {
    position: 'absolute',
    top: (LAYOUT.row - LAYOUT.tileHeight) / 2,
    height: LAYOUT.tileHeight,
    width: LAYOUT.tile,
    borderRadius: LAYOUT.tileRadius,
    overflow: 'hidden',
  },
  flagTile: { right: LAYOUT.gutter + LAYOUT.tile + LAYOUT.tileGap, backgroundColor: COLOR.plum },
  deleteTile: { right: LAYOUT.gutter, transformOrigin: 'right' },
  tilePress: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 4 },
  tileText: { ...TYPE.tile },
  tileTextLight: { color: COLOR.white },
  tileTextPink: { color: COLOR.pinkDeep },
  overlay: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  content: { height: LAYOUT.row, flexDirection: 'row', alignItems: 'center', paddingLeft: FLAT.rowLeft, paddingRight: FLAT.rowRight },
  sheen: { position: 'absolute', top: 0, bottom: 0, width: 90 },
  slab: {
    overflow: 'hidden',
    position: 'absolute', left: 8, right: 8, top: 4, bottom: 4,
    borderRadius: LAYOUT.rowRadius, backgroundColor: COLOR.slab,
    borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.slabEdge,
    shadowColor: COLOR.slabShadow, shadowOpacity: 0.25, shadowRadius: 16, shadowOffset: { width: 0, height: 8 },
  },
  // The source's icon sits 7 pt below the row's centre (measured on f017).
  circle: { width: LAYOUT.circle, height: LAYOUT.circle, borderRadius: LAYOUT.circle / 2, transform: [{ translateY: 7 }], backgroundColor: COLOR.plum, alignItems: 'center', justifyContent: 'center' },
  text: { flex: 1, marginLeft: LAYOUT.textLeft - FLAT.rowLeft - LAYOUT.circle },
  title: { ...TYPE.rowTitle, color: COLOR.title },
  sub: { ...TYPE.rowSub, color: COLOR.subtitle },
  flagDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: COLOR.plum, marginRight: 8 },
  // The amount sits on the title's line, not the row's centre (as in the source).
  amount: { ...TYPE.amount, color: COLOR.title, alignSelf: 'flex-start', marginTop: (LAYOUT.row - TYPE.rowTitle.lineHeight - TYPE.rowSub.lineHeight) / 2 - 2.5 },
});
