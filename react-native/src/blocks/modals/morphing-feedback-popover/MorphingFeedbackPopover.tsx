// "Help" in an invented notes app, with a Feedback pill that stretches into a popover.
// The stretch itself is ./MorphingPopover (reusable); this file is the page, the popover's content and the autoPlay script.
import { Host, Toggle } from '@expo/ui/swift-ui';
import { labelsHidden, tint } from '@expo/ui/swift-ui/modifiers';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useEffect, useRef, useState, type ComponentProps, type ReactNode } from 'react';
import { Keyboard, Platform, StyleSheet, Switch, Text, TextInput, useWindowDimensions, View } from 'react-native';
import Animated, {
  useAnimatedKeyboard,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  FACE_POP,
  FACE_POP_MS,
  FACE_SPRING,
  GHOST_FACE,
  LOOP,
  PRESS_MS,
  RESUME_AFTER,
  seg,
  T,
  TAP,
  tapGhost,
  tapPress,
} from './motion';
import { MorphingPopover, PopoverRise, useMorphingPopover } from './MorphingPopover';

export { LOOP };
export type Feedback = { text: string; mood: number };
export type MorphingFeedbackPopoverProps = {
  /** Plays the 8 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  /** Called with what was typed and the face that was picked (0 worst … 3 best, -1 none). */
  onSend?: (feedback: Feedback) => void;
};

const IOS = Platform.OS === 'ios';
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);
type SymbolName = ComponentProps<typeof SymbolView>['name'];

const COPY = {
  app: 'Tidemark',
  back: 'Settings',
  title: 'Help',
  label: 'Feedback',
  thanks: 'Thanks!',
  send: 'Send',
  version: 'Tidemark 4.2 (318)',
  // What autoPlay types: a first sentence, a breath, then the rest.
  sample: 'Love the tide view. Could notes open on launch?',
  moods: ['Bad', 'Okay', 'Good', 'Great'],
};
const SAMPLE_PAUSE_AT = 19; // characters typed before the breath
const TYPE_FIRST = 0.36; // share of the typing time for the first sentence
const TYPE_BREATH = 0.12; // share spent not typing

// Palette: a cool paper, one deep sea ink, one mint accent.
const PAPER = '#ECF0EE';
const CARD = '#FBFCFB';
const INK = '#14302D';
const INK_SOFT = '#6C7F7B';
const SEA = '#2E7F74';
const HAIRLINE = 'rgba(20,48,45,0.10)';
const POPOVER = '#17332F';
const ON_DARK = '#F1F7F5';
const ON_DARK_SOFT = 'rgba(241,247,245,0.62)';
const FIELD = 'rgba(241,247,245,0.07)';
const FIELD_EDGE = 'rgba(241,247,245,0.09)';
const MINT = '#9DE6D1';

// Layout, in points.
const SIDE = 16;
const ROW = 52;
const PILL = { width: 132, height: 44 };
const PILL_BOTTOM = 28; // above the safe area
const POPOVER_MAX = 361;
const POPOVER_HEIGHT = 232;
const PAD = 12;
const FIELD_HEIGHT = 150;
const FIELD_PAD = 14;
const ROW_TOP = 176; // faces and Send
const ROW_HEIGHT = 40;
const FACE_LEFT = 14;
const FACE_STEP = 44;
const FACE = 40;
const SEND_WIDTH = 76;
const GHOST = 46;
const GHOST_PRESS = 0.16;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** How many characters of the sample are typed at u = 0…1 of the typing time. */
function typedCount(u: number, total: number) {
  'worklet';
  const first = Math.min(SAMPLE_PAUSE_AT, total);
  if (u <= TYPE_FIRST) return Math.floor(first * (u / TYPE_FIRST));
  const rest = seg(u, TYPE_FIRST + TYPE_BREATH, 1);
  return first + Math.round((total - first) * rest);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function bump() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function MorphingFeedbackPopover({ autoPlay = true, onSend }: MorphingFeedbackPopoverProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotionPreference();
  const keyboard = useAnimatedKeyboard();
  const [diagnostics, setDiagnostics] = useState(true);
  /** The field is uncontrolled; a new key starts it again from `text`. */
  const [seed, setSeed] = useState({ key: 0, text: '' });
  const written = useRef('');

  const pillBottom = height - Math.max(insets.bottom, 16) - PILL_BOTTOM;
  const anchor = { x: (width - PILL.width) / 2, y: pillBottom - PILL.height, width: PILL.width, height: PILL.height };
  const size = { width: Math.min(width - SIDE * 2, POPOVER_MAX), height: POPOVER_HEIGHT };
  const origin = { x: width / 2 - size.width / 2, y: pillBottom - size.height };
  const sendLeft = size.width - PAD - SEND_WIDTH;

  const popover = useMorphingPopover({ anchor, size, reducedMotion });
  const { goal, tall, thanks, touches, fingers, dragging, press, open, dismiss, confirm } = popover;

  /** The face that is picked, -1 for none. */
  const mood = useSharedValue(-1);
  /** Characters of the sample autoPlay has typed. */
  const typed = useSharedValue(0);
  /** 1 while the real field holds text; 1 while it has the keyboard. */
  const hasText = useSharedValue(0);
  const focused = useSharedValue(0);
  const sendPress = useSharedValue(0);
  // autoPlay: one clock, and what a touch does to it.
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const idle = useSharedValue(0);
  const seen = useSharedValue(0);
  const playing = autoPlay && !reducedMotion;
  const total = COPY.sample.length;

  /** The placeholder steps aside as soon as there is a character. */
  const covered = useDerivedValue<number>(() => (typed.get() > 0 || hasText.get() > 0 ? 1 : 0));
  // The keyboard must not cover the popover.
  const lift = useDerivedValue(() => Math.max(0, keyboard.height.get() - (height - pillBottom) + 12));

  /** A touch took over while autoPlay was typing: the real field carries on from the same text. */
  const adopt = (count: number) => {
    const text = COPY.sample.slice(0, count);
    written.current = text;
    // A new field that starts with the text: an uncontrolled input cannot be handed a value any other way.
    setSeed((last) => ({ key: last.key + 1, text }));
    hasText.set(text.length > 0 ? 1 : 0);
  };

  // Once the new field is on screen, autoPlay's copy of the text steps aside.
  useEffect(() => {
    if (seed.key > 0) typed.set(0);
  }, [seed.key, typed]);

  const clearField = () => {
    if (written.current === '') return;
    written.current = '';
    setSeed((last) => ({ key: last.key + 1, text: '' }));
    hasText.set(0);
  };

  const sent = (picked: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    Keyboard.dismiss();
    onSend?.({ text: written.current, mood: picked });
  };

  const dismissed = () => {
    bump();
    Keyboard.dismiss();
  };

  /** Touches inside the open popover: the faces and Send are hit-tested here, on the UI thread. */
  const touchInside = (x: number, y: number, phase: number) => {
    'worklet';
    if (phase === 2) {
      sendPress.set(withTiming(0, { duration: PRESS_MS }));
      return;
    }
    if (y < ROW_TOP - 4 || y > ROW_TOP + ROW_HEIGHT + 4) return;
    if (x >= sendLeft - 4) {
      const ready = covered.get() > 0 || mood.get() >= 0;
      if (!ready) return;
      if (phase === 0) sendPress.set(withTiming(1, { duration: PRESS_MS }));
      else if (confirm()) scheduleOnRN(sent, mood.get());
      return;
    }
    const index = Math.floor((x - FACE_LEFT + (FACE_STEP - FACE) / 2) / FACE_STEP);
    if (phase === 1 && index >= 0 && index < COPY.moods.length) {
      mood.set(mood.get() === index ? -1 : index);
      scheduleOnRN(tick);
    }
  };

  // Closed again: the next opening starts clean.
  useAnimatedReaction(
    () => goal.get() === 0 && tall.get() <= 0.02,
    (closed, was) => {
      if (closed && was === false) {
        typed.set(0);
        mood.set(-1);
        scheduleOnRN(clearField);
      }
    },
  );

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    // A touch takes over: the clock stops, and what was typed so far stays.
    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      if (mode.get() !== MODE_MANUAL) {
        mode.set(MODE_MANUAL);
        press.set(withTiming(0, { duration: PRESS_MS }));
        sendPress.set(withTiming(0, { duration: PRESS_MS }));
        if (typed.get() > 0 && goal.get() === 1) scheduleOnRN(adopt, typed.get());
      }
    }
    if (mode.get() === MODE_MANUAL) {
      if (!playing) return;
      if (fingers.get() === 0 && !dragging.get() && focused.get() === 0) idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        mode.set(MODE_RETURN);
        dismiss();
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      // Back at rest: the loop starts again from 0.
      if (goal.get() === 0 && tall.get() === 0 && thanks.get() < 0) {
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

    // Tap the pill.
    if (before < T.tapPill + TAP.down && t >= T.tapPill + TAP.down) press.set(withTiming(1, { duration: PRESS_MS }));
    if (before < T.tapPill && t >= T.tapPill) {
      press.set(withTiming(0, { duration: PRESS_MS }));
      open();
    }
    // Type.
    if (t >= T.typeStart && before <= T.typeEnd && goal.get() === 1) {
      typed.set(typedCount(seg(t, T.typeStart, T.typeEnd), total));
    }
    // Pick a face.
    if (before < T.tapFace && t >= T.tapFace) mood.set(GHOST_FACE);
    // Send.
    if (before < T.tapSend + TAP.down && t >= T.tapSend + TAP.down) sendPress.set(withTiming(1, { duration: PRESS_MS }));
    if (before < T.tapSend && t >= T.tapSend) {
      sendPress.set(withTiming(0, { duration: PRESS_MS }));
      confirm();
    }
  });

  const typedProps = useAnimatedProps(() => {
    const text = COPY.sample.slice(0, typed.get());
    return { text, defaultValue: text } as unknown as Record<string, unknown>;
  });
  const sendStyle = useAnimatedStyle(() => {
    const ready = covered.get() > 0 || mood.get() >= 0;
    return {
      opacity: withTiming(ready ? 1 : 0.4, { duration: 160 }),
      transform: [{ scale: 1 - 0.05 * sendPress.get() }],
    };
  });

  const touched = () => touches.set(touches.get() + 1);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.page, { paddingTop: insets.top + 8 }]}>
        <View style={styles.nav}>
          <SymbolView name={{ ios: 'chevron.left', android: 'chevron_left', web: 'chevron_left' }} size={17} weight="semibold" tintColor={SEA} />
          <Text style={styles.back}>{COPY.back}</Text>
        </View>
        <Text style={styles.heading} accessibilityRole="header">
          {COPY.title}
        </Text>
        <View style={styles.group}>
          <Row icon={{ ios: 'book', android: 'menu_book', web: 'menu_book' }} label="Getting started" chevron />
          <Row icon={{ ios: 'keyboard', android: 'keyboard', web: 'keyboard' }} label="Keyboard shortcuts" chevron divider />
          <Row icon={{ ios: 'square.and.arrow.down', android: 'download', web: 'download' }} label="Import notes" value="3 apps" chevron divider />
        </View>
        <View style={styles.group}>
          <Row icon={{ ios: 'arrow.triangle.2.circlepath', android: 'sync', web: 'sync' }} label="Sync" value="Up to date" />
          <Row icon={{ ios: 'waveform.path.ecg', android: 'monitor_heart', web: 'monitor_heart' }} label="Share diagnostics" divider>
            <DiagnosticsToggle value={diagnostics} onChange={setDiagnostics} />
          </Row>
        </View>
        <View style={styles.group}>
          <Row icon={{ ios: 'bell', android: 'notifications', web: 'notifications' }} label="What’s new" value="4.2" chevron />
        </View>
      </View>
      <Text style={[styles.version, { top: anchor.y - 30 }]}>{COPY.version}</Text>

      <MorphingPopover
        controller={popover}
        color={POPOVER}
        label={COPY.label}
        labelColor={ON_DARK}
        labelAt={{ x: PAD + FIELD_PAD, y: PAD + FIELD_PAD }}
        labelHidden={covered}
        thanksLabel={COPY.thanks}
        accent={MINT}
        lift={lift}
        onTouchInside={touchInside}
        onOpen={bump}
        onDismiss={dismissed}>
        <PopoverRise controller={popover} order={0} style={[styles.field, { width: size.width - PAD * 2 }]}>
          <TextInput
            key={seed.key}
            multiline
            scrollEnabled={false}
            defaultValue={seed.text}
            onChangeText={(text) => {
              written.current = text;
              hasText.set(text.length > 0 ? 1 : 0);
            }}
            onFocus={() => {
              focused.set(1);
              touched();
            }}
            onBlur={() => focused.set(0)}
            accessibilityLabel={COPY.label}
            selectionColor={MINT}
            keyboardAppearance="dark"
            style={styles.input}
          />
          {/* What autoPlay types. It is not the real field, so no keyboard comes up. */}
          <AnimatedTextInput
            pointerEvents="none"
            editable={false}
            multiline
            scrollEnabled={false}
            accessible={false}
            animatedProps={typedProps}
            underlineColorAndroid="transparent"
            style={[styles.input, styles.typed]}
          />
        </PopoverRise>
        <PopoverRise controller={popover} order={1} style={styles.faces}>
          {COPY.moods.map((name, i) => (
            <Face key={name} index={i} mood={mood} reducedMotion={reducedMotion} />
          ))}
        </PopoverRise>
        <PopoverRise controller={popover} order={2} style={[styles.sendSlot, { left: sendLeft }]}>
          <Animated.View accessibilityRole="button" accessibilityLabel={COPY.send} style={[styles.send, sendStyle]}>
            <Text style={styles.sendLabel}>{COPY.send}</Text>
          </Animated.View>
        </PopoverRise>
      </MorphingPopover>

      <Ghost
        clock={clock}
        mode={mode}
        show={playing}
        pill={{ x: width / 2, y: pillBottom - PILL.height / 2 }}
        face={{ x: origin.x + FACE_LEFT + FACE_STEP * GHOST_FACE + FACE / 2, y: origin.y + ROW_TOP + ROW_HEIGHT / 2 }}
        send={{ x: origin.x + sendLeft + SEND_WIDTH / 2, y: origin.y + ROW_TOP + ROW_HEIGHT / 2 }}
      />
    </View>
  );
}

type RowProps = { icon: SymbolName; label: string; value?: string; chevron?: boolean; divider?: boolean; children?: ReactNode };

function Row({ icon, label, value, chevron, divider, children }: RowProps) {
  return (
    <View style={styles.row}>
      {divider ? <View style={styles.divider} /> : null}
      <View style={styles.tile}>
        <SymbolView name={icon} size={16} weight="medium" tintColor={SEA} />
      </View>
      <Text style={styles.rowLabel}>{label}</Text>
      {value ? <Text style={styles.rowValue}>{value}</Text> : null}
      {children}
      {chevron ? <SymbolView name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }} size={12} weight="semibold" tintColor="rgba(20,48,45,0.28)" /> : null}
    </View>
  );
}

/** The system's switch. */
function DiagnosticsToggle({ value, onChange }: { value: boolean; onChange: (next: boolean) => void }) {
  if (!IOS) return <Switch value={value} onValueChange={onChange} trackColor={{ true: SEA }} />;
  return (
    <Host style={styles.toggle} colorScheme="light">
      <Toggle isOn={value} onIsOnChange={onChange} modifiers={[labelsHidden(), tint(SEA)]} />
    </Host>
  );
}

// Mouths for the four moods, in a 28 pt box.
const MOUTHS = ['M9.5 19.2 Q14 14.8 18.5 19.2', 'M10 18 L18 18', 'M9.5 16.6 Q14 20.6 18.5 16.6', 'M9 15.6 Q14 23 19 15.6 Z'];

function FaceArt({ index, on }: { index: number; on: boolean }) {
  const line = on ? INK : ON_DARK_SOFT;
  return (
    <Svg width={30} height={30} viewBox="0 0 28 28">
      <Circle cx={14} cy={14} r={12} fill={on ? MINT : 'none'} stroke={on ? MINT : ON_DARK_SOFT} strokeWidth={1.6} />
      <Circle cx={10} cy={11.4} r={1.35} fill={line} />
      <Circle cx={18} cy={11.4} r={1.35} fill={line} />
      <Path d={MOUTHS[index]} fill={index === 3 ? line : 'none'} stroke={line} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

/** A mood face: outlined, or filled once it is picked, with a pop. */
function Face({ index, mood, reducedMotion }: { index: number; mood: SharedValue<number>; reducedMotion: boolean }) {
  const pop = useSharedValue(1);
  useAnimatedReaction(
    () => mood.get() === index,
    (on, was) => {
      if (on && was === false && !reducedMotion) {
        pop.set(withSequence(withTiming(FACE_POP, { duration: FACE_POP_MS }), withSpring(1, FACE_SPRING)));
      }
    },
  );
  const scale = useAnimatedStyle(() => ({ transform: [{ scale: pop.get() }] }));
  const picked = useAnimatedStyle(() => ({ opacity: withTiming(mood.get() === index ? 1 : 0, { duration: 140 }) }));
  return (
    <Animated.View accessibilityRole="button" accessibilityLabel={COPY.moods[index]} style={[styles.face, scale]}>
      <FaceArt index={index} on={false} />
      <Animated.View style={[StyleSheet.absoluteFill, styles.faceOn, picked]}>
        <FaceArt index={index} on />
      </Animated.View>
    </Animated.View>
  );
}

type Point = { x: number; y: number };
type GhostProps = { clock: SharedValue<number>; mode: SharedValue<number>; show: boolean; pill: Point; face: Point; send: Point };

/** The finger autoPlay pretends to have: a soft dot that taps the pill, a face and Send. */
function Ghost({ clock, mode, show, pill, face, send }: GhostProps) {
  const dot = useAnimatedStyle(() => {
    const t = clock.get();
    const onPill = tapGhost(t, T.tapPill);
    const onFace = tapGhost(t, T.tapFace);
    const onSend = tapGhost(t, T.tapSend);
    let x = pill.x;
    let y = pill.y;
    let pressed = tapPress(t, T.tapPill);
    let opacity = onPill;
    if (onFace > 0) {
      x = face.x;
      y = face.y;
      pressed = tapPress(t, T.tapFace);
      opacity = onFace;
    } else if (onSend > 0) {
      x = send.x;
      y = send.y;
      pressed = tapPress(t, T.tapSend);
      opacity = onSend;
    }
    return {
      opacity: show && mode.get() === MODE_AUTO ? opacity : 0,
      transform: [{ translateX: x - GHOST / 2 }, { translateY: y - GHOST / 2 }, { scale: 1 - GHOST_PRESS * pressed }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.ghost, dot]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER, overflow: 'hidden' },
  page: { paddingHorizontal: SIDE, gap: 20 },
  nav: { flexDirection: 'row', alignItems: 'center', gap: 4, height: 24, marginBottom: -12 },
  back: { color: SEA, fontSize: 17 },
  heading: { color: INK, fontSize: 32, lineHeight: 38, fontWeight: '800', letterSpacing: -0.4, marginBottom: -4 },
  group: { borderRadius: 18, borderCurve: 'continuous', backgroundColor: CARD, boxShadow: '0 1px 2px rgba(20,48,45,0.06), 0 6px 18px rgba(20,48,45,0.05)' },
  row: { height: ROW, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14 },
  divider: { position: 'absolute', left: 56, right: 0, top: 0, height: StyleSheet.hairlineWidth, backgroundColor: HAIRLINE },
  tile: { width: 30, height: 30, borderRadius: 9, borderCurve: 'continuous', backgroundColor: 'rgba(46,127,116,0.12)', alignItems: 'center', justifyContent: 'center' },
  rowLabel: { flex: 1, color: INK, fontSize: 16 },
  rowValue: { color: INK_SOFT, fontSize: 15, fontVariant: ['tabular-nums'] },
  toggle: { width: 52, height: 32 },
  version: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: INK_SOFT, fontSize: 12, fontVariant: ['tabular-nums'] },

  field: { left: PAD, top: PAD, height: FIELD_HEIGHT, borderRadius: 16, borderCurve: 'continuous', backgroundColor: FIELD, borderWidth: StyleSheet.hairlineWidth, borderColor: FIELD_EDGE },
  input: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, paddingHorizontal: FIELD_PAD, paddingTop: FIELD_PAD, paddingBottom: FIELD_PAD, color: ON_DARK, fontSize: 16, lineHeight: 20, textAlignVertical: 'top' },
  typed: { backgroundColor: 'transparent' },
  faces: { left: FACE_LEFT, top: ROW_TOP, height: ROW_HEIGHT, flexDirection: 'row', gap: FACE_STEP - FACE },
  face: { width: FACE, height: FACE, alignItems: 'center', justifyContent: 'center' },
  faceOn: { alignItems: 'center', justifyContent: 'center' },
  sendSlot: { top: ROW_TOP, width: SEND_WIDTH, height: ROW_HEIGHT },
  send: { flex: 1, borderRadius: ROW_HEIGHT / 2, backgroundColor: MINT, alignItems: 'center', justifyContent: 'center', boxShadow: '0 1px 0 rgba(255,255,255,0.45) inset, 0 4px 12px rgba(6,22,20,0.30)' },
  sendLabel: { color: INK, fontSize: 16, fontWeight: '600' },

  ghost: { position: 'absolute', left: 0, top: 0, width: GHOST, height: GHOST, borderRadius: GHOST / 2, backgroundColor: 'rgba(255,255,255,0.26)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)', boxShadow: '0 2px 10px rgba(10,30,28,0.25)' },
});
