import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Extrapolation,
  cancelAnimation,
  interpolate,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { ReactionMarks } from './ReactionMarks';
import { LOOP_MS, RESUME_MS, borderAt, dragPointsPerPage, pageAt, projectSeconds, snap } from './motion';
import { colors, fan, layout } from './theme';

const AnimatedImage = Animated.createAnimatedComponent(Image);

function wallTime() {
  'worklet';
  return Date.now();
}

function boundedPage(value: number) {
  'worklet';
  return Math.max(0, Math.min(3, Math.round(value)));
}

function clockForPage(value: number) {
  'worklet';
  return value < 1.5 ? 0 : 1800;
}

function accessibilityValue(index: number) {
  if (index === 0) return 'Dorian Petters, $100 donation, 10 minutes ago, portrait 1 of 4';
  if (index === 3) return 'Emmy Stockley, $200 donation, 11 minutes ago, portrait 4 of 4';
  return `Portrait ${index + 1} of 4`;
}

const cards = [
  { id: 'mateo-left', index: -3, image: require('./assets/mateo.jpg') },
  { id: 'andre-left', index: -2, image: require('./assets/andre.jpg') },
  { id: 'ruth-left', index: -1, image: require('./assets/ruth.jpg') },
  { id: 'noah', index: 0, image: require('./assets/noah.jpg') },
  { id: 'mei', index: 1, image: require('./assets/mei.jpg') },
  { id: 'theo', index: 2, image: require('./assets/theo.jpg') },
  { id: 'clara', index: 3, image: require('./assets/clara.jpg') },
  { id: 'andre-right', index: 4, image: require('./assets/andre.jpg') },
  { id: 'mateo-right', index: 5, image: require('./assets/mateo.jpg') },
  { id: 'ruth-right', index: 6, image: require('./assets/ruth.jpg') },
] as const;

type CardSlotProps = {
  card: (typeof cards)[number];
  page: SharedValue<number>;
  size: number;
  scale: number;
};

function CardSlot({ card, page, size, scale }: CardSlotProps) {
  const animatedStyle = useAnimatedStyle(() => {
    const distance = card.index - page.get();
    const absolute = Math.min(3, Math.abs(distance));
    const direction = distance < 0 ? -1 : 1;
    const x = interpolate(absolute, [0, 1, 2, 3], fan.x, Extrapolation.CLAMP) * direction * scale;
    const y = interpolate(absolute, [0, 1, 2, 3], fan.y, Extrapolation.CLAMP) * scale;
    const cardScale = interpolate(absolute, [0, 1, 2, 3], fan.scale, Extrapolation.CLAMP);
    const rotation = interpolate(absolute, [0, 1, 2, 3], fan.rotate, Extrapolation.CLAMP) * direction;
    const opacity = absolute >= 3 ? Math.max(0, 1 - (absolute - 3) * 4) : 1;

    return {
      opacity,
      zIndex: Math.round(100 - absolute * 20),
      transform: [{ translateX: x }, { translateY: y }, { rotate: `${rotation}deg` }, { scale: cardScale }],
    };
  });

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.card,
        {
          left: layout.deck.centerX * scale - size / 2,
          top: layout.deck.centerY * scale - size / 2,
          width: size,
          height: size,
          borderRadius: layout.deck.radius * scale,
        },
        animatedStyle,
      ]}
    >
      <AnimatedImage source={card.image} contentFit="cover" transition={0} style={styles.image} />
    </Animated.View>
  );
}

type Props = { autoPlay?: boolean; freezeAt?: number };

export function DonationFanCarousel({ autoPlay = false, freezeAt }: Props) {
  'use no memo';

  const { height, width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const stageWidth = width - 16;
  const stageHeight = (462 / 748) * stageWidth;
  const scale = stageWidth / 402;
  const size = layout.deck.card * scale;
  const frozen = freezeAt !== undefined;

  const clock = useSharedValue(frozen ? freezeAt : 0);
  const page = useSharedValue(frozen ? pageAt(freezeAt) : 0);
  const loopStart = useSharedValue(-1);
  const playing = useSharedValue(autoPlay && !frozen && !reduced);
  const gestureStart = useSharedValue(0);
  const resumeAt = useSharedValue(0);
  const touching = useSharedValue(false);
  const contactCount = useSharedValue(0);
  const panWasActive = useSharedValue(false);
  const panCancelled = useSharedValue(false);
  const initialAccessibilityIndex = frozen ? boundedPage(pageAt(freezeAt)) : 0;
  const [accessibleIndex, setAccessibleIndex] = useState(initialAccessibilityIndex);
  const accessibleIndexRef = useRef(initialAccessibilityIndex);

  const frameStep = useCallback((frame: { timestamp: number }) => {
    'worklet';
    if (!playing.get()) {
      if (!touching.get() && autoPlay && !frozen && !reduced && resumeAt.get() > 0 && Date.now() >= resumeAt.get()) {
        cancelAnimation(page);
        clock.set(0);
        page.set(0);
        loopStart.set(-1);
        resumeAt.set(0);
        playing.set(true);
      }
      return;
    }
    if (loopStart.get() < 0) loopStart.set(frame.timestamp);
    const time = (frame.timestamp - loopStart.get()) % LOOP_MS;
    clock.set(time);
    page.set(pageAt(time));
  }, [autoPlay, clock, frozen, loopStart, page, playing, reduced, resumeAt, touching]);
  const ticker = useFrameCallback(frameStep, false);

  const reportAccessibleIndex = useCallback((index: number) => {
    accessibleIndexRef.current = index;
    setAccessibleIndex(index);
  }, []);

  useAnimatedReaction(
    () => boundedPage(page.get()),
    (index, previous) => {
      if (index !== previous) scheduleOnRN(reportAccessibleIndex, index);
    },
    [reportAccessibleIndex],
  );

  useEffect(() => {
    ticker.setActive(autoPlay && !frozen && !reduced);
    cancelAnimation(page);
    resumeAt.set(0);
    touching.set(false);
    contactCount.set(0);
    panWasActive.set(false);
    panCancelled.set(false);
    if (frozen) {
      clock.set(freezeAt);
      page.set(pageAt(freezeAt));
      playing.set(false);
      return;
    }
    if (autoPlay && !reduced) {
      loopStart.set(-1);
      playing.set(true);
    } else {
      playing.set(false);
      clock.set(0);
      page.set(0);
    }
    return () => ticker.setActive(false);
  }, [
    autoPlay,
    clock,
    contactCount,
    freezeAt,
    frozen,
    loopStart,
    page,
    panCancelled,
    panWasActive,
    playing,
    reduced,
    resumeAt,
    ticker,
    touching,
  ]);

  const contact = useMemo(
    () =>
      Gesture.Manual()
        .enabled(!frozen && !reduced)
        .shouldCancelWhenOutside(false)
        .onTouchesDown((event, manager) => {
          const previous = contactCount.get();
          const next = previous + event.changedTouches.length;
          contactCount.set(next);
          if (previous !== 0) return;

          manager.activate();
          touching.set(true);
          cancelAnimation(page);
          playing.set(false);
          gestureStart.set(page.get());
          resumeAt.set(0);
          panWasActive.set(false);
          panCancelled.set(false);
        })
        .onTouchesUp((event, manager) => {
          const next = Math.max(0, contactCount.get() - event.changedTouches.length);
          contactCount.set(next);
          if (next !== 0) return;

          if (panCancelled.get()) {
            const target = boundedPage(page.get());
            cancelAnimation(page);
            page.set(withSpring(target, { ...snap, velocity: 0 }));
            clock.set(clockForPage(target));
          }
          touching.set(false);
          panCancelled.set(false);
          if (autoPlay) resumeAt.set(wallTime() + RESUME_MS);
          manager.end();
        })
        .onTouchesCancelled((event, manager) => {
          const next = Math.max(0, contactCount.get() - event.changedTouches.length);
          contactCount.set(next);
          if (next !== 0) return;

          const target = boundedPage(page.get());
          cancelAnimation(page);
          page.set(withSpring(target, { ...snap, velocity: 0 }));
          clock.set(clockForPage(target));
          touching.set(false);
          panCancelled.set(false);
          if (autoPlay) resumeAt.set(wallTime() + RESUME_MS);
          manager.fail();
        }),
    [
      autoPlay,
      clock,
      contactCount,
      frozen,
      gestureStart,
      page,
      panCancelled,
      panWasActive,
      playing,
      reduced,
      resumeAt,
      touching,
    ],
  );

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .enabled(!frozen && !reduced)
        .maxPointers(1)
        .activeOffsetX([-8, 8])
        .failOffsetY([-18, 18])
        .onStart(() => {
          panWasActive.set(true);
        })
        .onUpdate((event) => {
          let value = gestureStart.get() - event.translationX / (dragPointsPerPage * scale);
          if (value < 0) value *= 0.28;
          if (value > 3) value = 3 + (value - 3) * 0.28;
          page.set(value);
          clock.set(value < 1.5 ? 0 : 1800);
        })
        .onEnd((event, success) => {
          if (!success) {
            if (panWasActive.get()) panCancelled.set(true);
            return;
          }
          const velocity = -event.velocityX / (dragPointsPerPage * scale);
          const target = Math.max(0, Math.min(3, Math.round(page.get() + velocity * projectSeconds)));
          page.set(withSpring(target, { ...snap, velocity }));
          panCancelled.set(false);
        }),
    [clock, frozen, gestureStart, page, panCancelled, panWasActive, reduced, scale],
  );
  const gesture = useMemo(() => Gesture.Simultaneous(contact, pan), [contact, pan]);

  const adjustAccessibility = useCallback(
    (delta: number) => {
      if (frozen) return;
      const target = Math.max(0, Math.min(3, accessibleIndexRef.current + delta));
      if (target === accessibleIndexRef.current) return;

      reportAccessibleIndex(target);
      cancelAnimation(page);
      playing.set(false);
      touching.set(false);
      contactCount.set(0);
      panWasActive.set(false);
      panCancelled.set(false);
      resumeAt.set(0);
      clock.set(clockForPage(target));
      page.set(reduced ? target : withSpring(target, { ...snap, velocity: 0 }));
      if (autoPlay && !reduced) resumeAt.set(Date.now() + RESUME_MS);
    },
    [
      autoPlay,
      clock,
      contactCount,
      frozen,
      page,
      panCancelled,
      panWasActive,
      playing,
      reduced,
      reportAccessibleIndex,
      resumeAt,
      touching,
    ],
  );

  const firstMeta = useAnimatedStyle(() => ({
    opacity: interpolate(page.get(), [0, 0.55, 1.1], [1, 0.35, 0], Extrapolation.CLAMP),
  }));
  const secondMeta = useAnimatedStyle(() => ({
    opacity: interpolate(page.get(), [1.9, 2.45, 3], [0, 0.35, 1], Extrapolation.CLAMP),
  }));
  const borderStyle = useAnimatedStyle(() => ({ opacity: borderAt(clock.get()) }));

  return (
    <GestureDetector gesture={gesture}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Donation portraits"
        accessibilityValue={{ text: accessibilityValue(accessibleIndex) }}
        accessibilityState={{ disabled: frozen }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          if (event.nativeEvent.actionName === 'increment') adjustAccessibility(1);
          if (event.nativeEvent.actionName === 'decrement') adjustAccessibility(-1);
        }}
      >
        <StatusBar style="dark" />
        <View
          style={[
            styles.stage,
            {
              left: (width - stageWidth) / 2,
              top: (height - stageHeight) / 2,
              width: stageWidth,
              height: stageHeight,
            },
          ]}
        >
          <View style={[styles.header, { top: layout.header.top * scale, left: layout.header.side * scale, right: layout.header.side * scale }]}>
            <Text style={[styles.heading, { fontSize: layout.header.fontSize * scale, lineHeight: layout.header.lineHeight * scale }]}>Latest donations</Text>
            <Text style={[styles.counter, { fontSize: layout.header.counterSize * scale, lineHeight: layout.header.lineHeight * scale }]}>04/24</Text>
          </View>

          <View style={StyleSheet.absoluteFill}>
            {cards.map((card) => (
              <CardSlot key={card.id} card={card} page={page} size={size} scale={scale} />
            ))}
            <View
              pointerEvents="none"
              style={[
                styles.activeBorder,
                {
                  left: layout.deck.centerX * scale - size / 2,
                  top: layout.deck.centerY * scale - size / 2,
                  width: size,
                  height: size,
                  borderRadius: layout.deck.radius * scale,
                },
              ]}
            >
              <Animated.View
                style={[
                  styles.activeKeyline,
                  { borderRadius: layout.deck.radius * scale, borderWidth: layout.deck.border * scale },
                  borderStyle,
                ]}
              />
              <ReactionMarks clock={clock} page={page} scale={scale} />
            </View>
          </View>

          <Animated.View style={[styles.metadata, { top: layout.metadata.top * scale }, firstMeta]}>
            <Text style={[styles.name, { fontSize: layout.metadata.nameSize * scale, lineHeight: layout.metadata.nameLine * scale }]}>Dorian Petters</Text>
            <Text style={[styles.detail, { fontSize: layout.metadata.detailSize * scale, lineHeight: layout.metadata.detailLine * scale }]}>
              <Text style={styles.detailAmount}>$100 donation • </Text>
              <Text style={styles.detailTime}>10m ago</Text>
            </Text>
          </Animated.View>
          <Animated.View style={[styles.metadata, { top: layout.metadata.top * scale }, secondMeta]}>
            <Text style={[styles.name, { fontSize: layout.metadata.nameSize * scale, lineHeight: layout.metadata.nameLine * scale }]}>Emmy Stockley</Text>
            <Text style={[styles.detail, { fontSize: layout.metadata.detailSize * scale, lineHeight: layout.metadata.detailLine * scale }]}>
              <Text style={styles.detailAmount}>$200 donation • </Text>
              <Text style={styles.detailTime}>11m ago</Text>
            </Text>
          </Animated.View>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background, overflow: 'hidden' },
  stage: { position: 'absolute', backgroundColor: colors.background },
  header: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  heading: { color: colors.ink, fontFamily: 'Avenir Next', fontWeight: '500', letterSpacing: -0.35 },
  counter: { top: -1.3, color: colors.ink, fontFamily: 'Avenir Next', fontWeight: '500', letterSpacing: -0.25 },
  card: {
    position: 'absolute',
    overflow: 'hidden',
    backgroundColor: '#E8E8E8',
    shadowColor: '#171717',
    shadowOpacity: 0.13,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
  },
  image: { width: '100%', height: '100%' },
  activeBorder: {
    position: 'absolute',
    zIndex: 130,
    alignItems: 'center',
    justifyContent: 'center',
  },
  activeKeyline: { position: 'absolute', inset: 0, borderColor: colors.accent },
  metadata: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  name: { color: colors.ink, fontFamily: 'Avenir Next', fontWeight: '600', letterSpacing: -0.1 },
  detail: { fontFamily: 'Avenir Next', fontWeight: '400', letterSpacing: -0.08 },
  detailAmount: { color: colors.detailAmount },
  detailTime: { color: colors.secondary },
});
