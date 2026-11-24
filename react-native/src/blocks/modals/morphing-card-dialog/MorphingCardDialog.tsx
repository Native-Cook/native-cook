// "Weekend tables": a restaurant guide whose cards open into a dialog by morphing.
// The morph itself is ./MorphingDialog (reusable); this file is the screen, the content and the autoPlay script.
import { Host, Picker, Text as SwiftText } from '@expo/ui/swift-ui';
import { pickerStyle, tag } from '@expo/ui/swift-ui/modifiers';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useState, type ReactNode } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import Animated, { interpolateColor, useAnimatedStyle, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { COPY, PLACES, type Place } from './data';
import {
  CARD_A,
  CARD_B,
  clamp,
  CONTENT_BLUR,
  CONTENT_GATE,
  CONTENT_RISE,
  CONTENT_SPAN,
  CONTENT_START,
  easeOutCubic,
  GHOST_APPEAR,
  GHOST_DRIFT,
  GHOST_PULL,
  ghostPull,
  ghostPullSpeed,
  LOOP,
  mix,
  RADIUS_CARD,
  RADIUS_DIALOG,
  RESUME_AFTER,
  seg,
  T,
  TAP,
  tapGhost,
  tapPress,
} from './motion';
import {
  MorphingDialog,
  MorphingLayer,
  MorphingScene,
  MorphingTrigger,
  TRIGGER_SHADOW,
  useMorphingDialog,
  type MorphingDialogController,
  type Rect,
} from './MorphingDialog';

export { LOOP };
export type MorphingCardDialogProps = {
  /** Plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  places?: Place[];
  onReserve?: (place: Place) => void;
};

const IOS = Platform.OS === 'ios';

// Palette: one warm neutral family, one accent.
const PAPER = '#F3EEE6';
const CARD = '#FFFCF7';
const INK = '#1B1713';
const INK_SOFT = '#857B6F';
const INK_GLASS = 'rgba(27,23,19,0.58)';
const FILL = '#F2ECE3';
const HAIRLINE = 'rgba(27,23,19,0.10)';
const EMBER = '#BF4A26';

// Layout, in points.
const SIDE = 16;
const GAP = 12;
const HEADER = 124;
const CARD_TEXT = 60; // the strip under a card's photo
const CARD_ASPECT = 1.22; // a card is never taller than this × its width
const DIALOG_SIDE = 20;
const DIALOG_MAX = 380;
const PHOTO_RATIO = 0.6; // the dialog photo's height / width
const IMAGE_RATIO = 0.75; // the bundled photos are 4:3
const PAD_CARD = 12;
const PAD_DIALOG = 20;
// Title and subtitle are set once at the dialog's size and scaled down for the card, so both ends match exactly.
const TITLE_SIZE = 26;
const TITLE_LINE = 31;
const TITLE_SCALE = 15 / TITLE_SIZE;
const SUBTITLE_SIZE = 15;
const SUBTITLE_LINE = 20;
const SUBTITLE_SCALE = 13 / SUBTITLE_SIZE;
const TITLE_TOP = { card: 9, dialog: 18 }; // below the photo
const SUBTITLE_TOP = { card: 30, dialog: 52 };
// The "+" that turns into the "×".
const PLUS = 30;
const PLUS_INSET = { card: 40, dialogX: 44, dialogY: 12 };
const CLOSE_SCALE = 32 / PLUS;
const PLUS_TURN = 135; // degrees
// Detail content, measured down from the dialog photo's bottom edge.
const BODY_TOP = 86;
const BODY_LINE = 21;
const FACTS_TOP = 163;
const FACTS_HEIGHT = 54;
const ACTION_TOP = 233;
const ACTION_HEIGHT = 50;
const DETAIL_HEIGHT = 303;
// The ghost finger.
const GHOST = 46;
const GHOST_PRESS = 0.16;
const GHOST_GRAB_Y = 110; // where on the dialog it takes hold, from the top

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

type Layout = {
  cards: Rect[];
  target: Rect;
  photoHeight: number;
};

function makeLayout(width: number, height: number, top: number, bottom: number, count: number): Layout {
  const gridTop = top + HEADER;
  const gridBottom = height - Math.max(bottom, 12) - 4;
  const rows = Math.ceil(count / 2);
  const cardWidth = (width - SIDE * 2 - GAP) / 2;
  const cardHeight = Math.min((gridBottom - gridTop - GAP * (rows - 1)) / rows, cardWidth * CARD_ASPECT);
  const cards: Rect[] = [];
  for (let i = 0; i < count; i += 1) {
    cards.push({
      x: SIDE + (i % 2) * (cardWidth + GAP),
      y: gridTop + Math.floor(i / 2) * (cardHeight + GAP),
      width: cardWidth,
      height: cardHeight,
    });
  }
  const dialogWidth = Math.min(width - DIALOG_SIDE * 2, DIALOG_MAX);
  const photoHeight = Math.round(dialogWidth * PHOTO_RATIO);
  const dialogHeight = photoHeight + DETAIL_HEIGHT;
  const target = {
    x: (width - dialogWidth) / 2,
    y: top + Math.max(8, (height - top - bottom - dialogHeight) / 2),
    width: dialogWidth,
    height: dialogHeight,
  };
  return { cards, target, photoHeight };
}

function impact() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function MorphingCardDialog({ autoPlay = true, places = PLACES, onReserve }: MorphingCardDialogProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotionPreference();
  const [meal, setMeal] = useState(1);
  const { cards, target, photoHeight } = makeLayout(width, height, insets.top, insets.bottom, places.length);
  const dialog = useMorphingDialog({ target, reducedMotion });
  const { active, touches, fingers, dragging, closePress, open, close, pressIn, pressOut, dragBegin, dragMove, dragEnd } = dialog;

  // autoPlay: one clock, and what a touch does to it.
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const idle = useSharedValue(0);
  const seen = useSharedValue(0);
  const scripted = useSharedValue(false);
  const cardA = Math.min(CARD_A, places.length - 1);
  const cardB = Math.min(CARD_B, places.length - 1);
  const playing = autoPlay && !reducedMotion;

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    // A touch takes over: the scripted finger lets go and the clock stops.
    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      if (mode.get() !== MODE_MANUAL) {
        if (scripted.get()) {
          scripted.set(false);
          dragEnd(0, 0);
        }
        closePress.set(0);
        mode.set(MODE_MANUAL);
      }
    }
    if (mode.get() === MODE_MANUAL) {
      if (!playing) return;
      if (fingers.get() === 0 && !dragging.get()) idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        mode.set(MODE_RETURN);
        close(0);
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      // Back at rest: the loop starts again from 0.
      if (active.get() < 0) {
        clock.set(0);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (!playing) return;

    const before = clock.get();
    let t = before + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
    if (t < before) return;

    // Tap card A.
    if (before < T.tapA + TAP.down && t >= T.tapA + TAP.down) pressIn(cardA);
    if (before < T.tapA && t >= T.tapA) {
      pressOut();
      open(cardA, cards[cardA]);
    }
    // Drag the dialog down and let go.
    if (t >= T.dragStart && t < T.dragEnd) {
      if (!scripted.get()) {
        scripted.set(true);
        dragBegin();
      }
      const u = (t - T.dragStart) / (T.dragEnd - T.dragStart);
      dragMove(GHOST_DRIFT * u, GHOST_PULL * ghostPull(u));
    } else if (scripted.get()) {
      scripted.set(false);
      const span = T.dragEnd - T.dragStart;
      dragEnd(GHOST_DRIFT / span, (GHOST_PULL * ghostPullSpeed(1)) / span);
    }
    // Tap card B.
    if (before < T.tapB + TAP.down && t >= T.tapB + TAP.down) pressIn(cardB);
    if (before < T.tapB && t >= T.tapB) {
      pressOut();
      open(cardB, cards[cardB]);
    }
    // Tap the "×".
    if (before < T.tapClose + TAP.down && t >= T.tapClose + TAP.down) closePress.set(1);
    if (before < T.tapClose && t >= T.tapClose) {
      closePress.set(0);
      close(0);
    }
  });

  const reserve = () => {
    impact();
    const index = active.get();
    if (index >= 0 && onReserve) onReserve(places[index]);
  };

  const closeHit = { x: target.width - PLUS_INSET.dialogX - 1, y: PLUS_INSET.dialogY - 1, width: PLUS + 2, height: PLUS + 2 };
  const actionHit = { x: PAD_DIALOG, y: photoHeight + ACTION_TOP, width: target.width - PAD_DIALOG * 2, height: ACTION_HEIGHT };
  const textWidth = target.width - PAD_DIALOG * 2;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <MorphingScene controller={dialog}>
        <View style={[styles.header, { top: insets.top + 8 }]}>
          <View style={styles.brandRow}>
            <Text style={styles.brand}>{COPY.brand}</Text>
            <Text style={styles.count}>{`${places.length} with a table left`}</Text>
          </View>
          <Text style={styles.heading} accessibilityRole="header">
            {COPY.title}
          </Text>
          <MealPicker width={width - SIDE * 2} value={meal} onChange={setMeal} />
        </View>
        {places.map((place, i) => (
          <MorphingTrigger key={place.key} controller={dialog} index={i} rect={cards[i]} onOpen={impact} accessibilityLabel={`${place.name}, ${place.area}`}>
            <CardFace place={place} width={cards[i].width} height={cards[i].height} textWidth={textWidth} />
          </MorphingTrigger>
        ))}
      </MorphingScene>

      <MorphingDialog
        controller={dialog}
        backgroundColor={CARD}
        radius={{ from: RADIUS_CARD, to: RADIUS_DIALOG }}
        closeHit={closeHit}
        actionHit={actionHit}
        onClose={impact}
        onAction={reserve}>
        <DialogFace controller={dialog} places={places} photoHeight={photoHeight} textWidth={textWidth} />
      </MorphingDialog>

      <Ghost
        clock={clock}
        mode={mode}
        show={playing}
        a={cards[cardA]}
        b={cards[cardB]}
        grab={{ x: target.x + target.width / 2, y: target.y + GHOST_GRAB_Y }}
        closeAt={{ x: target.x + closeHit.x + closeHit.width / 2, y: target.y + closeHit.y + closeHit.height / 2 }}
      />
    </View>
  );
}

/** The meals filter: the system's segmented control on iOS. */
function MealPicker({ width, value, onChange }: { width: number; value: number; onChange: (index: number) => void }) {
  if (!IOS) {
    return (
      <View style={[styles.segments, { width }]}>
        {COPY.meals.map((label, i) => (
          <Pressable key={label} onPress={() => onChange(i)} style={[styles.segment, i === value ? styles.segmentOn : null]}>
            <Text style={styles.segmentLabel}>{label}</Text>
          </Pressable>
        ))}
      </View>
    );
  }
  return (
    <Host style={{ width, height: 32 }} colorScheme="light">
      <Picker selection={value} onSelectionChange={(next: number) => onChange(next)} modifiers={[pickerStyle('segmented')]}>
        {COPY.meals.map((label, i) => (
          <SwiftText key={label} modifiers={[tag(i)]}>
            {label}
          </SwiftText>
        ))}
      </Picker>
    </Host>
  );
}

/** A card at rest. It is drawn exactly as the dialog's frame is at progress 0, so the hand-over is invisible. */
function CardFace({ place, width, height, textWidth }: { place: Place; width: number; height: number; textWidth: number }) {
  const photo = height - CARD_TEXT;
  return (
    <>
      <View style={[StyleSheet.absoluteFill, styles.cardShadow]} />
      <View style={[StyleSheet.absoluteFill, styles.cardClip]}>
        <Image source={place.photo} style={{ width, height: photo }} contentFit="cover" transition={0} cachePolicy="memory" />
        <Text
          numberOfLines={1}
          style={[styles.title, { width: textWidth, transform: [{ translateX: PAD_CARD }, { translateY: photo + TITLE_TOP.card }, { scale: TITLE_SCALE }] }]}>
          {place.name}
        </Text>
        <Text
          numberOfLines={1}
          style={[styles.subtitle, { width: textWidth, transform: [{ translateX: PAD_CARD }, { translateY: photo + SUBTITLE_TOP.card }, { scale: SUBTITLE_SCALE }] }]}>
          {place.area}
        </Text>
        <View style={[styles.plus, { transform: [{ translateX: width - PLUS_INSET.card }, { translateY: height - PLUS_INSET.card }] }]}>
          <View style={styles.plusBar} />
          <View style={[styles.plusBar, styles.plusBarUp]} />
        </View>
      </View>
    </>
  );
}

type FaceProps = { controller: MorphingDialogController; places: Place[]; photoHeight: number; textWidth: number };

/** Everything inside the morphing frame. Each part places itself from the controller's shared values. */
function DialogFace({ controller, places, photoHeight, textWidth }: FaceProps) {
  const { target, reducedMotion, progress, titleFollow, subtitleFollow, source, closePress, actionPress } = controller;
  const imageWidth = target.width;
  const imageHeight = target.width * IMAGE_RATIO;

  const photoBox = useAnimatedStyle(() => {
    const q = reducedMotion ? 1 : progress.get();
    return { height: Math.max(1, mix(source.get().height - CARD_TEXT, photoHeight, q)) };
  });
  // The photo always covers its box: scaled about its top-left corner, then centred.
  const photo = useAnimatedStyle(() => {
    const q = reducedMotion ? 1 : progress.get();
    const from = source.get();
    const boxWidth = Math.max(1, mix(from.width, target.width, q));
    const boxHeight = Math.max(1, mix(from.height - CARD_TEXT, photoHeight, q));
    const k = Math.max(boxWidth / imageWidth, boxHeight / imageHeight);
    return {
      transform: [{ translateX: (boxWidth - imageWidth * k) / 2 }, { translateY: (boxHeight - imageHeight * k) / 2 }, { scale: k }],
    };
  });
  const title = useAnimatedStyle(() => {
    const q = reducedMotion ? 1 : progress.get();
    const late = reducedMotion ? 1 : titleFollow.get();
    const edge = mix(source.get().height - CARD_TEXT, photoHeight, q);
    return {
      transform: [
        { translateX: mix(PAD_CARD, PAD_DIALOG, late) },
        { translateY: edge + mix(TITLE_TOP.card, TITLE_TOP.dialog, q) },
        { scale: mix(TITLE_SCALE, 1, late) },
      ],
    };
  });
  const subtitle = useAnimatedStyle(() => {
    const q = reducedMotion ? 1 : progress.get();
    const late = reducedMotion ? 1 : subtitleFollow.get();
    const edge = mix(source.get().height - CARD_TEXT, photoHeight, q);
    return {
      transform: [
        { translateX: mix(PAD_CARD, PAD_DIALOG, late) },
        { translateY: edge + mix(SUBTITLE_TOP.card, SUBTITLE_TOP.dialog, q) },
        { scale: mix(SUBTITLE_SCALE, 1, late) },
      ],
    };
  });
  // "+" rides up the right edge and turns into "×", a beat behind the frame.
  const plus = useAnimatedStyle(() => {
    const q = reducedMotion ? 1 : progress.get();
    const late = reducedMotion ? 1 : titleFollow.get();
    const u = clamp(q, 0, 1);
    const from = source.get();
    const frameWidth = mix(from.width, target.width, q);
    return {
      backgroundColor: interpolateColor(u, [0, 1], [INK, INK_GLASS]),
      transform: [
        { translateX: frameWidth - mix(PLUS_INSET.card, PLUS_INSET.dialogX, q) },
        { translateY: mix(from.height - PLUS_INSET.card, PLUS_INSET.dialogY, q) },
        { rotate: `${PLUS_TURN * late}deg` },
        { scale: mix(1, CLOSE_SCALE, u) * (1 - 0.1 * closePress.get()) },
      ],
    };
  });
  const action = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * actionPress.get() }] }));

  return (
    <>
      <Animated.View style={[styles.photoBox, photoBox]}>
        <Animated.View style={[styles.photo, { width: imageWidth, height: imageHeight }, photo]}>
          {places.map((place, i) => (
            <MorphingLayer key={place.key} controller={controller} index={i} style={StyleSheet.absoluteFill}>
              <Image source={place.photo} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} cachePolicy="memory" />
            </MorphingLayer>
          ))}
        </Animated.View>
      </Animated.View>

      <Animated.View style={[styles.riding, { width: textWidth, height: TITLE_LINE }, title]}>
        {places.map((place, i) => (
          <MorphingLayer key={place.key} controller={controller} index={i} style={StyleSheet.absoluteFill}>
            <Text numberOfLines={1} style={styles.titleText}>
              {place.name}
            </Text>
          </MorphingLayer>
        ))}
      </Animated.View>
      <Animated.View style={[styles.riding, { width: textWidth, height: SUBTITLE_LINE }, subtitle]}>
        {places.map((place, i) => (
          <MorphingLayer key={place.key} controller={controller} index={i} style={StyleSheet.absoluteFill}>
            <Text numberOfLines={1} style={styles.subtitleText}>
              {place.area}
            </Text>
          </MorphingLayer>
        ))}
      </Animated.View>

      <Rise controller={controller} order={0} top={BODY_TOP} photoHeight={photoHeight} width={textWidth} height={BODY_LINE * 3}>
        {places.map((place, i) => (
          <MorphingLayer key={place.key} controller={controller} index={i} style={StyleSheet.absoluteFill}>
            <Text numberOfLines={3} style={styles.body}>
              {place.about}
            </Text>
          </MorphingLayer>
        ))}
      </Rise>
      <Rise controller={controller} order={1} top={FACTS_TOP} photoHeight={photoHeight} width={textWidth} height={FACTS_HEIGHT}>
        <View style={[StyleSheet.absoluteFill, styles.facts]} />
        {places.map((place, i) => (
          <MorphingLayer key={place.key} controller={controller} index={i} style={[StyleSheet.absoluteFill, styles.factsRow]}>
            {place.facts.map((fact, k) => (
              <View key={fact.label} style={[styles.fact, k > 0 ? styles.factDivider : null]}>
                <Text style={styles.factValue}>{fact.value}</Text>
                <Text style={styles.factLabel}>{fact.label}</Text>
              </View>
            ))}
          </MorphingLayer>
        ))}
      </Rise>
      <Rise controller={controller} order={2} top={ACTION_TOP} photoHeight={photoHeight} width={textWidth} height={ACTION_HEIGHT}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.action, action]}>
          <Text style={styles.actionLabel}>{COPY.reserve}</Text>
        </Animated.View>
      </Rise>

      <Animated.View style={[styles.plus, plus]}>
        <View style={styles.plusBar} />
        <View style={[styles.plusBar, styles.plusBarUp]} />
      </Animated.View>
    </>
  );
}

type RiseProps = {
  controller: MorphingDialogController;
  /** 0, 1, 2: its place in the stagger. */
  order: number;
  /** Distance below the photo. */
  top: number;
  photoHeight: number;
  width: number;
  height: number;
  children: ReactNode;
};

/** One block of detail content: it comes up out of a blur once the frame is nearly open. */
function Rise({ controller, order, top, photoHeight, width, height, children }: RiseProps) {
  const { reducedMotion, progress, content, source } = controller;
  const rise = useAnimatedStyle(() => {
    const amount = progress.get();
    const q = reducedMotion ? 1 : amount;
    const edge = mix(source.get().height - CARD_TEXT, photoHeight, q);
    const start = CONTENT_START[order];
    const gate = reducedMotion ? clamp(amount, 0, 1) : seg(amount, CONTENT_GATE[0], CONTENT_GATE[1]);
    const u = easeOutCubic(seg(content.get(), start, start + CONTENT_SPAN)) * gate;
    const away = reducedMotion ? 0 : 1 - u;
    return {
      opacity: u,
      transform: [{ translateY: edge + top + away * CONTENT_RISE }],
      filter: [{ blur: away * CONTENT_BLUR }] as unknown as string,
    };
  });
  return <Animated.View style={[styles.rise, { width, height }, rise]}>{children}</Animated.View>;
}

type Point = { x: number; y: number };
type GhostProps = { clock: SharedValue<number>; mode: SharedValue<number>; show: boolean; a: Rect; b: Rect; grab: Point; closeAt: Point };

/** The finger autoPlay pretends to have: a soft dot that taps, drags and lets go. */
function Ghost({ clock, mode, show, a, b, grab, closeAt }: GhostProps) {
  const dot = useAnimatedStyle(() => {
    const t = clock.get();
    const onA = tapGhost(t, T.tapA);
    const onB = tapGhost(t, T.tapB);
    const onClose = tapGhost(t, T.tapClose);
    const landed = T.dragStart - GHOST_APPEAR;
    const onDrag = seg(t, landed, landed + 0.18) * (1 - seg(t, T.dragEnd, T.dragEnd + 0.2));
    let x = a.x + a.width / 2;
    let y = a.y + a.height * 0.42;
    let press = tapPress(t, T.tapA);
    let opacity = onA;
    if (onDrag > 0) {
      const u = seg(t, T.dragStart, T.dragEnd);
      x = grab.x + GHOST_DRIFT * u;
      y = grab.y + GHOST_PULL * ghostPull(u);
      press = seg(t, T.dragStart - 0.1, T.dragStart) * (1 - seg(t, T.dragEnd, T.dragEnd + 0.1));
      opacity = onDrag;
    } else if (onB > 0) {
      x = b.x + b.width / 2;
      y = b.y + b.height * 0.42;
      press = tapPress(t, T.tapB);
      opacity = onB;
    } else if (onClose > 0) {
      x = closeAt.x;
      y = closeAt.y;
      press = tapPress(t, T.tapClose);
      opacity = onClose;
    }
    return {
      opacity: show && mode.get() === MODE_AUTO ? opacity : 0,
      transform: [{ translateX: x - GHOST / 2 }, { translateY: y - GHOST / 2 }, { scale: 1 - GHOST_PRESS * press }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.ghost, dot]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER, overflow: 'hidden' },
  header: { position: 'absolute', left: SIDE, right: SIDE, gap: 2 },
  brandRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  brand: { color: EMBER, fontSize: 15, fontWeight: '700' },
  count: { color: INK_SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  heading: { color: INK, fontSize: 32, lineHeight: 38, fontWeight: '800', letterSpacing: -0.4, marginBottom: 10 },
  segments: { height: 32, flexDirection: 'row', borderRadius: 9, backgroundColor: 'rgba(27,23,19,0.07)', padding: 2 },
  segment: { flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 7 },
  segmentOn: { backgroundColor: CARD, boxShadow: '0 1px 3px rgba(27,23,19,0.14)' },
  segmentLabel: { color: INK, fontSize: 13, fontWeight: '600' },

  cardShadow: { borderRadius: RADIUS_CARD, borderCurve: 'continuous', backgroundColor: CARD, boxShadow: TRIGGER_SHADOW },
  cardClip: { borderRadius: RADIUS_CARD, borderCurve: 'continuous', backgroundColor: CARD, overflow: 'hidden' },
  title: { position: 'absolute', left: 0, top: 0, height: TITLE_LINE, transformOrigin: 'top left', color: INK, fontSize: TITLE_SIZE, lineHeight: TITLE_LINE, fontWeight: '700', letterSpacing: -0.3 },
  subtitle: { position: 'absolute', left: 0, top: 0, height: SUBTITLE_LINE, transformOrigin: 'top left', color: INK_SOFT, fontSize: SUBTITLE_SIZE, lineHeight: SUBTITLE_LINE, fontWeight: '500' },
  plus: { position: 'absolute', left: 0, top: 0, width: PLUS, height: PLUS, borderRadius: PLUS / 2, backgroundColor: INK, alignItems: 'center', justifyContent: 'center', boxShadow: '0 1px 0 rgba(255,255,255,0.14) inset, 0 2px 6px rgba(27,23,19,0.22)' },
  plusBar: { position: 'absolute', width: 12, height: 1.8, borderRadius: 1, backgroundColor: '#FBF7F0' },
  plusBarUp: { transform: [{ rotate: '90deg' }] },

  photoBox: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden', backgroundColor: '#2A221B' },
  photo: { position: 'absolute', left: 0, top: 0, transformOrigin: 'top left' },
  riding: { position: 'absolute', left: 0, top: 0, transformOrigin: 'top left' },
  titleText: { color: INK, fontSize: TITLE_SIZE, lineHeight: TITLE_LINE, fontWeight: '700', letterSpacing: -0.3 },
  subtitleText: { color: INK_SOFT, fontSize: SUBTITLE_SIZE, lineHeight: SUBTITLE_LINE, fontWeight: '500' },
  rise: { position: 'absolute', left: PAD_DIALOG, top: 0 },
  body: { color: '#4A423A', fontSize: 15, lineHeight: BODY_LINE },
  facts: { borderRadius: 16, borderCurve: 'continuous', backgroundColor: FILL },
  factsRow: { flexDirection: 'row', alignItems: 'center' },
  fact: { flex: 1, alignItems: 'center', gap: 1 },
  factDivider: { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: HAIRLINE },
  factValue: { color: INK, fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'] },
  factLabel: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  action: { borderRadius: ACTION_HEIGHT / 2, backgroundColor: EMBER, alignItems: 'center', justifyContent: 'center', boxShadow: '0 1px 0 rgba(255,255,255,0.22) inset, 0 1px 2px rgba(120,40,14,0.30), 0 10px 22px rgba(191,74,38,0.28)' },
  actionLabel: { color: '#FFF8F1', fontSize: 17, fontWeight: '600' },

  ghost: { position: 'absolute', left: 0, top: 0, width: GHOST, height: GHOST, borderRadius: GHOST / 2, backgroundColor: 'rgba(27,23,19,0.20)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.75)', boxShadow: '0 2px 10px rgba(27,23,19,0.18)' },
});
