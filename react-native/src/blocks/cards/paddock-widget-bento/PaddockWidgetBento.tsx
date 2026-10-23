import React, { useEffect, useState } from 'react';
import { Modal, Platform, Pressable, Text, View, useWindowDimensions } from 'react-native';
import { useFonts } from 'expo-font';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedProps,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  KIND, LONG_PRESS, PEEL, PIT, PIT_DURATION, PULL, SLOT_Y, DEFAULT_ORDER,
  evaluatePlan, jiggle, nextCandidate, peelDemo, peelRelease, pickCard, pitTile, restFrame,
  type Frame, type PeelState, type Plan,
} from './motion';
import { GooLayer, PitTrails } from './FX';
import { LiquidDock } from './LiquidDock';
import { SecondaryPage, SheetContent, sheetTitle, widgetSymbol, type PageState } from './Pages';
import { PaddockRuntime, initialView, reactionButtonTitle, type ViewState } from './runtime';
import { CardFace, StaticGrid, cardFill, type Destination } from './StaticViews';
import { FONT_FILES, REFERENCE_WIDTH, alpha, colors, copy, layout, native, radius, shadow, space, symbols, type } from './theme';
import { Sym, Txt } from './ui';
import { impact, ImpactStyle, useReduceMotion } from './a11y';

/** The demo's loop period in seconds, equal to Swift's `static let loop`. */
export { LOOP } from './motion';

const SLOT_TOP = SLOT_Y[0];
/** A touch's y as a card-top coordinate: the controller compares it with the slots' tops. */
const SHIFT = SLOT_TOP - layout.quickCard.height / 2;
const PICK_RADIUS = layout.touchSurface.pickRadius;
const TOP_SLOP = 4;
const TAP_SLOP = 10;
const HOLD_MS = LONG_PRESS * 1000;
const PEEL_SPOT = { left: 250, top: 46, width: 132, height: 82 };
const CARD_KEYS = ['laps', 'reflex', 'homeCards'] as const;
const CARD_DESTINATION: Destination[] = ['laps', 'reflex', 'homeCards'];
const LINEAR = Easing.linear;
const REST_PEEL: PeelState = { distance: PEEL.hint, angle: PEEL.angle, slap: -1 };
const ANDROID = Platform.OS === 'android';

/** The resting curl: the corner never lies flatter than PEEL.hint, so the sticker always shows it can be peeled. */
function withHint(p: PeelState): PeelState {
  'worklet';
  return p.distance >= PEEL.hint ? p : { distance: PEEL.hint, angle: p.distance > 0.5 ? p.angle : PEEL.angle, slap: p.slap };
}

/** Android has no overscroll: the feed follows a downward pull with resistance (iOS-style rubber band). */
function rubberBand(pull: number, limit: number) {
  'worklet';
  return (1 - 1 / ((pull * 0.55) / limit + 1)) * limit;
}

/** The UI runtime's clock expressed on the controller's: `offset` was measured when the last plan was pushed. */
function controllerTime(offset: SharedValue<number>) {
  'worklet';
  return performance.now() / 1000 + offset.get();
}

const restPlan = (): Plan => ({ kind: KIND.rest, anchor: 0, order: DEFAULT_ORDER.slice(), base: restFrame(DEFAULT_ORDER), tr: null, drag: null });

type CardProps = {
  id: number;
  s: number;
  frame: SharedValue<Frame>;
  pit: SharedValue<number>;
  jiggleTime: SharedValue<number>;
  present: (destination: Destination) => void;
  move: (id: number, offset: number) => void;
  done: () => void;
};

/**
 * One quick card: its slot, lift and shadow come from the frame the UI runtime evaluates, its pit-stop pose from the
 * pit clock, and while the cards are being arranged the cards that are not held jiggle.
 */
function QuickCard({ id, s, frame, pit, jiggleTime, present, move, done }: CardProps) {
  const ink = id === 2 ? colors.ink55Black : colors.ink75White;
  const animated = useAnimatedStyle(() => {
    const f = frame.get();
    const selected = f.selected === id;
    const p = pitTile(id, pit.get());
    const lean = selected ? f.rotation : jiggle(id, jiggleTime.get(), f.handles);
    return {
      opacity: f.opacity[id],
      zIndex: selected ? 5 : 1,
      shadowOpacity: selected ? shadow.lifted.opacity : 0,
      shadowRadius: (selected ? f.shadow : shadow.rest.radius) * s,
      transform: [
        { translateX: p.x * s },
        { translateY: (f.y[id] - SLOT_TOP + p.y) * s },
        { rotate: `${p.rotate + lean}deg` },
        { scale: selected ? f.scale : 1 },
        { scaleX: p.sx },
        { scaleY: p.sy },
      ],
    };
  });
  const handle = useAnimatedStyle(() => ({ opacity: frame.get().handles }));
  const chip = useAnimatedStyle(() => ({ opacity: 1 - frame.get().handles }));
  const destination = CARD_DESTINATION[id];
  return (
    <Animated.View
      accessible
      accessibilityRole="button"
      accessibilityLabel={copy.cards[CARD_KEYS[id]].label}
      accessibilityActions={[{ name: 'activate' }, { name: 'moveUp', label: copy.moveUp }, { name: 'moveDown', label: copy.moveDown }, { name: 'done', label: copy.done.label }]}
      onAccessibilityTap={() => present(destination)}
      onAccessibilityAction={(event) => {
        const name = event.nativeEvent.actionName;
        if (name === 'activate') present(destination);
        else if (name === 'moveUp') move(id, -1);
        else if (name === 'moveDown') move(id, 1);
        else if (name === 'done') done();
      }}
      style={[
        {
          position: 'absolute',
          left: layout.quickCard.x * s,
          top: SLOT_TOP * s,
          width: layout.quickCard.width * s,
          height: layout.quickCard.height * s,
          borderRadius: radius.quickCard * s,
          backgroundColor: cardFill(id),
          shadowColor: colors.shadow,
          shadowOffset: { width: 0, height: shadow.rest.offsetY * s },
        },
        animated,
      ]}
    >
      <CardFace id={id} s={s} chipStyle={chip} />
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[{ position: 'absolute', right: space.handleTrailing * s, top: 0, bottom: 0, justifyContent: 'center' }, handle]}
      >
        <Sym symbol={symbols.handle} size={type.handle * s} color={ink} weight="bold" />
      </Animated.View>
    </Animated.View>
  );
}

export default function PaddockWidgetBento({ autoPlay = false }: { autoPlay?: boolean }) {
  const [fontsLoaded] = useFonts(FONT_FILES);
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReduceMotion();
  const s = width / REFERENCE_WIDTH;

  const plan = useSharedValue<Plan>(restPlan());
  const clock = useSharedValue(0);
  const offset = useSharedValue(0);
  const ticking = useSharedValue(false);
  const dragY = useSharedValue(0);
  const touchId = useSharedValue(-1);
  const tapCard = useSharedValue(-1);
  const touchOwner = useSharedValue(0);
  const ownerSeq = useSharedValue(0);
  const held = useSharedValue(false);
  const candidate = useSharedValue(0);
  const grab = useSharedValue(0);
  const scrollLocked = useSharedValue(false);
  const feedTop = useSharedValue(true);
  // Touch-driven effects: the pull-to-refresh pit stop, the finger on the sticker, the jiggle clock while arranging.
  const livePit = useSharedValue(-1);
  const peelHeld = useSharedValue(false);
  const peelDistance = useSharedValue(0);
  const peelAngle = useSharedValue<number>(PEEL.angle);
  const peelSince = useSharedValue(-1);
  const jiggleClock = useSharedValue(0);
  // The pull that runs the pit stop: the feed's offset (iOS overscrolls below 0), and Android's own rubber band.
  const scrollY = useSharedValue(0);
  const pullFromTop = useSharedValue(false);
  const pullY = useSharedValue(0);

  const [view, setView] = useState<ViewState>(initialView);
  const [runtime] = useState(() => new PaddockRuntime({ plan, clock, offset, ticking, dragY }, setView, autoPlay));
  const [guideVisible, setGuideVisible] = useState(true);
  const [pinned, setPinned] = useState<string[]>([]);
  const [selectedWidget, setSelectedWidget] = useState('Lap count');
  const [lapCount, setLapCount] = useState(3);
  const [savedWeekends, setSavedWeekends] = useState<string[]>([]);
  const [selectedCircuit, setSelectedCircuit] = useState('Harbor');
  const [latestStats, setLatestStats] = useState(true);
  const [expandedLesson, setExpandedLesson] = useState('Find the apex');
  const [searchQuery, setSearchQuery] = useState('');
  const [lastDestination, setLastDestination] = useState<Destination>('laps');
  if (view.destination !== null && view.destination !== lastDestination) setLastDestination(view.destination);

  useEffect(() => runtime.mount(), [runtime]);
  useEffect(() => {
    runtime.setAutoPlay(autoPlay);
  }, [autoPlay, runtime]);

  // The controller wakes the clock only while something moves; the frame worklet advances it on the UI runtime.
  const frameCallback = useFrameCallback(() => {
    'worklet';
    if (ticking.get()) clock.set(controllerTime(offset));
  }, false);
  useEffect(() => {
    frameCallback.setActive(view.running);
    return () => frameCallback.setActive(false);
  }, [frameCallback, view.running]);

  // While the cards are being arranged by hand, a clock for their jiggle.
  const jiggleCallback = useFrameCallback((info) => {
    'worklet';
    jiggleClock.set(info.timeSinceFirstFrame / 1000);
  }, false);
  useEffect(() => {
    jiggleCallback.setActive(view.editing && !reduced);
    return () => jiggleCallback.setActive(false);
  }, [jiggleCallback, view.editing, reduced]);

  const frame = useDerivedValue<Frame>(() => {
    'worklet';
    return evaluatePlan(plan.get(), clock.get(), dragY.get());
  });
  /** Seconds into the pit stop: a pull-to-refresh, or the demo's loop. -1 when none runs. */
  const pit = useDerivedValue(() => {
    'worklet';
    const live = livePit.get();
    if (live >= 0) return live;
    const t = frame.get().loopTime;
    return t >= 0 ? t - PIT.loopStart : -1;
  });
  /** The sticker: the finger, a released finger falling back, or the demo. */
  const peel = useDerivedValue<PeelState>(() => {
    'worklet';
    if (peelHeld.get()) return withHint({ distance: peelDistance.get(), angle: peelAngle.get(), slap: -1 });
    const since = peelSince.get();
    if (since >= 0) return withHint(peelRelease(peelDistance.get(), peelAngle.get(), since));
    if (peelDistance.get() > 0.5) return withHint({ distance: peelDistance.get(), angle: peelAngle.get(), slap: -1 });
    const t = frame.get().loopTime;
    return t >= 0 ? withHint(peelDemo(t)) : REST_PEEL;
  });
  const jiggleTime = useDerivedValue(() => {
    'worklet';
    const t = frame.get().loopTime;
    return t >= 0 ? t : jiggleClock.get();
  });

  const { touchDown, touchHold, candidateMoved, touchUp, touchCancel, tap, feedTopChanged, holdDemo } = runtime;

  // One hold-then-drag recogniser over the three cards. The hold is native (320 ms, 10 pt of slack), so a quick
  // tap opens a card and a vertical swipe that starts on a card still scrolls the feed.
  const pan = Gesture.Pan()
    .maxPointers(1)
    .activateAfterLongPress(HOLD_MS)
    .onBegin((event) => {
      const referenceY = event.y / s + SHIFT;
      const id = pickCard(frame.get().y, referenceY, PICK_RADIUS);
      touchId.set(id);
      tapCard.set(id);
      held.set(false);
      if (id < 0) return;
      const owner = ownerSeq.get() + 1;
      ownerSeq.set(owner);
      touchOwner.set(owner);
      dragY.set(referenceY);
      scheduleOnRN(touchDown, owner, id, referenceY, controllerTime(offset));
    })
    .onStart((event) => {
      const id = touchId.get();
      if (id < 0) return;
      const referenceY = event.y / s + SHIFT;
      const visibleY = frame.get().y[id];
      dragY.set(referenceY);
      grab.set(referenceY - visibleY);
      candidate.set(Math.max(0, plan.get().base.order.indexOf(id)));
      held.set(true);
      scrollLocked.set(true);
      scheduleOnRN(touchHold, touchOwner.get(), id, referenceY, controllerTime(offset), visibleY);
    })
    .onUpdate((event) => {
      if (!held.get()) return;
      const referenceY = event.y / s + SHIFT;
      dragY.set(referenceY);
      const current = candidate.get();
      const next = nextCandidate(current, referenceY - grab.get());
      if (next === current) return;
      candidate.set(next);
      scheduleOnRN(candidateMoved, touchOwner.get(), referenceY, controllerTime(offset));
    })
    .onFinalize((event, success) => {
      const id = touchId.get();
      if (id < 0) return;
      const owner = touchOwner.get();
      const wasHeld = held.get();
      const referenceY = event.y / s + SHIFT;
      const time = controllerTime(offset);
      held.set(false);
      scrollLocked.set(false);
      touchId.set(-1);
      if (wasHeld && !success) scheduleOnRN(touchCancel, owner, dragY.get(), time);
      else scheduleOnRN(touchUp, owner, referenceY, time);
    });
  const quickTap = Gesture.Tap()
    .maxDuration(HOLD_MS)
    .maxDistance(TAP_SLOP)
    .onEnd((_event, success) => {
      if (success) scheduleOnRN(tap, tapCard.get());
    });

  const quickGesture = Gesture.Race(pan, quickTap);

  const peelImpact = () => {
    holdDemo(PEEL.release + PEEL.slapEnd);
    if (!reduced) setTimeout(() => impact(ImpactStyle.Medium), PEEL.release * 1000);
  };
  // Drag the sticker's corner: the corner follows the finger, the fold is halfway between. Letting go drops it back.
  const peelGesture = Gesture.Pan()
    .maxPointers(1)
    .minDistance(4)
    .onBegin(() => {
      scrollLocked.set(true);
    })
    .onStart(() => {
      peelSince.set(-1);
      peelHeld.set(true);
      scheduleOnRN(holdDemo, 30);
    })
    .onUpdate((event) => {
      const dx = event.translationX / s;
      const dy = event.translationY / s;
      peelDistance.set(Math.min(PEEL.touchMaxDistance, Math.hypot(dx, dy)));
      peelAngle.set((Math.atan2(dy, dx) * 180) / Math.PI);
    })
    .onFinalize(() => {
      scrollLocked.set(false);
      if (!peelHeld.get()) return;
      peelHeld.set(false);
      if (reduced) {
        peelDistance.set(withTiming(0, { duration: 200, easing: LINEAR }));
        scheduleOnRN(holdDemo, 0.2);
        return;
      }
      const total = PEEL.release + PEEL.slapEnd;
      peelSince.set(0);
      peelSince.set(
        withTiming(total, { duration: total * 1000, easing: LINEAR }, (finished) => {
          if (!finished) return;
          peelSince.set(-1);
          peelDistance.set(0);
        }),
      );
      scheduleOnRN(peelImpact);
    });

  const startPit = () => {
    holdDemo(PIT_DURATION);
    impact();
  };
  const scrollHandler = useAnimatedScrollHandler({
    onScroll: (event) => {
      scrollY.set(event.contentOffset.y);
      const top = event.contentOffset.y <= TOP_SLOP * s;
      if (top === feedTop.get()) return;
      feedTop.set(top);
      scheduleOnRN(feedTopChanged, top);
    },
  });
  const scrollProps = useAnimatedProps(() => ({ scrollEnabled: !scrollLocked.get() }));

  // Pull the feed past its top and let go: the pit crew rebuilds the bento. A Gesture Handler pan next to the scroll
  // view's own gesture, so it works on Android (no negative offset there) as on iOS (where the feed overscrolls).
  const feedNative = Gesture.Native();
  const pullGesture = Gesture.Pan()
    .enabled(view.tab === 'home')
    .activeOffsetY(10)
    .failOffsetX([-16, 16])
    .simultaneousWithExternalGesture(feedNative)
    .onStart(() => {
      pullFromTop.set(scrollY.get() <= TOP_SLOP * s && !scrollLocked.get());
    })
    .onUpdate((event) => {
      if (!pullFromTop.get() || !ANDROID) return;
      pullY.set(rubberBand(Math.max(0, event.translationY), PULL.rubber * s));
    })
    .onEnd((event) => {
      if (!pullFromTop.get()) return;
      const pulled = ANDROID ? pullY.get() : Math.max(0, -scrollY.get());
      const far = pulled >= PULL.trigger * s;
      const flick = event.velocityY >= PULL.flick * s && pulled >= PULL.trigger * PULL.flickShare * s;
      if (reduced || !(far || flick) || livePit.get() >= 0) return;
      livePit.set(0);
      livePit.set(
        withTiming(PIT_DURATION, { duration: PIT_DURATION * 1000, easing: LINEAR }, (finished) => {
          if (finished) livePit.set(-1);
        }),
      );
      scheduleOnRN(startPit);
    })
    .onFinalize(() => {
      pullFromTop.set(false);
      if (pullY.get() !== 0) pullY.set(withSpring(0, PULL.back));
    });
  const feedGesture = Gesture.Simultaneous(feedNative, pullGesture);
  const pullStyle = useAnimatedStyle(() => ({ transform: [{ translateY: pullY.get() }] }));

  const onAction = (action: Destination) => {
    if (action === 'closeGuide') setGuideVisible(false);
    else runtime.present(action === 'banner' ? 'widgetStudio' : action);
  };

  const pageState: PageState = {
    order: view.order,
    editing: view.editing,
    savedWeekends,
    toggleWeekend: (venue, saved) => setSavedWeekends((current) => (saved ? [...current, venue] : current.filter((value) => value !== venue))),
    selectedCircuit,
    setSelectedCircuit,
    lapCount,
    logLap: () => setLapCount((count) => count + 1),
    reactionText: view.reaction,
    reactionTitle: reactionButtonTitle(view.reaction),
    reactionAction: runtime.reactionAction,
    latestStats,
    setLatestStats,
    searchQuery,
    setSearchQuery,
    expandedLesson,
    setExpandedLesson,
    selectedWidget,
    setSelectedWidget,
    pinned,
    pin: () => setPinned((current) => (current.includes(selectedWidget) ? current : [...current, selectedWidget])),
    moveCard: runtime.accessibleMove,
    toggleEditing: runtime.toggleEditing,
    restoreOrder: runtime.restoreOrder,
    restoreGuide: () => setGuideVisible(true),
  };

  if (!fontsLoaded) return <View style={{ flex: 1, backgroundColor: colors.background }} />;

  const peelSpot = (
    <GestureDetector gesture={peelGesture}>
      <View
        collapsable={false}
        accessible={false}
        importantForAccessibility="no"
        style={{ position: 'absolute', left: PEEL_SPOT.left * s, top: PEEL_SPOT.top * s, width: PEEL_SPOT.width * s, height: PEEL_SPOT.height * s }}
      />
    </GestureDetector>
  );

  const home = (
    <View style={{ width, height: (layout.content.height + pinned.length * layout.pinned.rowPitch) * s }}>
      <PitTrails s={s} pit={pit} frame={frame} />
      <StaticGrid s={s} guideVisible={guideVisible} pit={pit} peel={peel} onAction={onAction} heroOverlay={peelSpot} />
      <GooLayer s={s} frame={frame} time={jiggleTime} />
      {[0, 1, 2].map((id) => (
        <QuickCard key={id} id={id} s={s} frame={frame} pit={pit} jiggleTime={jiggleTime} present={runtime.present} move={runtime.accessibleMove} done={runtime.doneEditing} />
      ))}
      <GestureDetector gesture={quickGesture}>
        <View
          collapsable={false}
          accessible={false}
          importantForAccessibility="no"
          style={{ position: 'absolute', left: layout.touchSurface.x * s, top: SLOT_TOP * s, width: layout.touchSurface.width * s, height: layout.touchSurface.height * s, zIndex: 7 }}
        />
      </GestureDetector>
      {view.editing ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={copy.done.label}
          accessibilityHint={copy.done.hint}
          onPress={runtime.doneEditing}
          style={({ pressed }) => ({
            position: 'absolute',
            left: layout.done.centerX * s - (layout.done.width * s) / 2,
            top: layout.done.centerY * s - layout.unscaled.doneHeight / 2,
            width: layout.done.width * s,
            height: layout.unscaled.doneHeight,
            borderRadius: layout.unscaled.doneHeight / 2,
            backgroundColor: colors.white,
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 8,
            opacity: pressed ? alpha.pressed : 1,
          })}
        >
          <Txt style={{ fontWeight: '700', fontSize: type.done * s, color: colors.black }}>{copy.done.label}</Txt>
        </Pressable>
      ) : null}
      <View style={{ position: 'absolute', left: layout.pinned.left * s, top: layout.pinned.top * s, gap: space.pinnedGap * s }}>
        {pinned.map((widget) => (
          <View
            key={widget}
            style={{
              width: layout.pinned.width * s,
              height: layout.pinned.rowHeight * s,
              borderRadius: radius.pinned * s,
              backgroundColor: colors.panel10,
              paddingHorizontal: space.pinnedInset * s,
              flexDirection: 'row',
              alignItems: 'center',
              gap: native.stackSpacing,
            }}
          >
            <Sym symbol={widgetSymbol(widget)} size={type.body} color={colors.white} />
            <Text style={{ flex: 1, fontSize: type.body, fontWeight: '700', color: colors.white }}>{widget}</Text>
            <Text style={{ fontSize: type.body, color: colors.pinnedLabel }}>{copy.pinned}</Text>
          </View>
        ))}
      </View>
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <GestureDetector gesture={feedGesture}>
      <Animated.ScrollView
        key={view.tab}
        animatedProps={scrollProps}
        onScroll={view.tab === 'home' ? scrollHandler : undefined}
        scrollEventThrottle={16}
        contentInsetAdjustmentBehavior="never"
        showsVerticalScrollIndicator={false}
        // The pin's grid starts 54.5 pt from the top of the screen, under the status bar's row like the pin's.
        contentContainerStyle={{ paddingTop: view.tab === 'home' ? 0 : insets.top, paddingBottom: insets.bottom + layout.content.bottomClearance * s }}
      >
        <Animated.View style={[{ width, minHeight: layout.content.minHeight * s }, pullStyle]}>
          {view.tab === 'home' ? home : <SecondaryPage tab={view.tab} s={s} state={pageState} />}
        </Animated.View>
      </Animated.ScrollView>
      </GestureDetector>

      <LiquidDock s={s} tab={view.tab} quiet={reduced} onSelect={runtime.selectTab} />

      <Modal visible={view.destination !== null} animationType="slide" presentationStyle="pageSheet" onRequestClose={runtime.closeDestination} onDismiss={runtime.closeDestination}>
        <View style={{ flex: 1, backgroundColor: colors.sheet }}>
          <View style={{ height: native.headerHeight, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }}>
            <Text accessibilityRole="header" style={{ fontSize: type.headline, fontWeight: '600', color: colors.white }}>
              {sheetTitle(view.destination ?? lastDestination)}
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={copy.done.label}
              onPress={runtime.closeDestination}
              style={{ position: 'absolute', right: native.listInset - native.stackSpacing, minWidth: layout.unscaled.tapTarget, height: layout.unscaled.tapTarget, alignItems: 'center', justifyContent: 'center', paddingHorizontal: native.stackSpacing }}
            >
              <Text style={{ fontSize: type.body, color: colors.accent, fontWeight: '600' }}>{copy.done.label}</Text>
            </Pressable>
          </View>
          <SheetContent destination={view.destination ?? lastDestination} state={pageState} />
        </View>
      </Modal>
    </View>
  );
}
