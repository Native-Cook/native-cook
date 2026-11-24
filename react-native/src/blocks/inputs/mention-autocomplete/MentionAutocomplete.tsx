import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Keyboard, type LayoutChangeEvent, Pressable, type StyleProp, StyleSheet, Text, TextInput, type TextStyle, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { AttachMenu } from './AttachMenu';
import { Avatar } from './Avatar';
import {
  type Doc,
  EMPTY_DOC,
  PEOPLE,
  type Person,
  activeQuery,
  applyText,
  insertMention,
  rank,
  runs,
} from './mentionData';
import {
  BREAK_LEN,
  CHIP_START,
  type ChipMode,
  type Dot,
  IDLE,
  LABEL,
  LOOP,
  MORPH_FROM,
  MORPH_LEN,
  POP_PAD,
  type Pose,
  RETURN_LEN,
  ROW_H,
  SCRIPT,
  SELECT_LEN,
  T_STILL,
  autoDot,
  autoPose,
  clamp01,
  easeOutBack,
  seg,
  stepAt,
} from './mentionMotion';
import { MentionPopover } from './MentionPopover';

/*
 Mention Autocomplete — the composer of Huddle, a team chat.

 Typing "@" opens a popover of people that grows out of the caret; each letter filters it, with the
 matched letters in bold and the rows sliding to their new places on springs. Return or a tap turns
 the typed letters into a token chip that grows out of them, followed by a space for the next word.
 Backspace takes that space first; the next one on the chip selects it, and another breaks it back
 into plain text. Sending pushes the conversation up by the new message's height while the message
 rises out of the composer: one translate of the whole thread, measured once per change of its layout.

 The composer is a real TextInput whose text is drawn as runs (plain text, and the chips' names in
 semibold accent); the chips' backgrounds sit behind it, placed by measuring the same runs. The
 loop (mentionMotion.ts) types into the value without focusing it, so no keyboard shows; a touch
 hands everything to the person, and two seconds after the last touch the loop comes back.
*/

type Props = {
  /** Types, mentions and un-mentions someone every LOOP seconds until the screen is touched. */
  autoPlay?: boolean;
  /** The channel the composer posts to. */
  channel?: string;
  /** Called with the message's plain text and its mentions when it is sent. */
  onSend?: (text: string, mentions: Person[]) => void;
};

type Message = { id: string; author: Person | null; time: string; doc: Doc };

const ACCENT = '#2F63D6';
const CHIP_BG = '#E3EAFB';
const CHIP_INK = '#2452B9';
const INK = '#1E1D1A';
const MUTED = '#7A766D';
const PAGE = '#F5F4F0';
const BAR = '#FBFAF7';
const CARD = '#FFFEFB';
const HAIRLINE = 'rgba(30,28,24,0.12)';
const ZW = '​';
const PILL_H = 44;
const PLUS = 36;
const TEXT_PAD = 14;
const SEND = 32;
const CHIP_PAD = 4;
const CHIP_H = 24;
const FONT = 16;
const DOT_SIZE = 44;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
/** How long the thread takes to make room for a sent message (and to give it back). */
const SEND_MS = 300;
/** Space under the last message, inside the measured thread. */
const THREAD_TAIL = 12;

const HISTORY: Message[] = [
  { id: 'h1', author: PEOPLE[0], time: '9:41', doc: { text: 'Hero copy v3 is in the doc. The second line still feels long to me.', chips: [], selected: -1 } },
  { id: 'h2', author: PEOPLE[1], time: '9:44', doc: { text: 'Agreed. It wraps to three lines on the small phones.', chips: [], selected: -1 } },
  { id: 'h3', author: PEOPLE[3], time: '9:46', doc: { text: 'Who signs off on copy before Thursday?', chips: [], selected: -1 } },
];
const YOU: Person = { id: 'you', first: 'Sam', last: 'Ito', role: 'You', colors: ['#8E95A3', '#666D7C', '#4A505E'], online: true };

function makeGeo(W: number, H: number, top: number, bottom: number) {
  const margin = Math.max(12, (W - 460) / 2);
  const composerTop = H - Math.max(bottom, 10) - 8 - PILL_H;
  const pillLeft = margin + PLUS + 8;
  const pillW = W - margin - pillLeft;
  const textLeft = pillLeft + TEXT_PAD;
  const textW = pillW - TEXT_PAD - SEND - 12;
  const popW = Math.min(300, W - 24);
  return { W, H, top, margin, composerTop, pillLeft, pillW, textLeft, textW, popW, headerH: 56, popBottom: composerTop - 8 };
}

type Geo = ReturnType<typeof makeGeo>;

/** Positions measured from the composer's runs, in pt from the start of the text. */
type Measured = { end: number; at: number; from: number; chips: [number, number][]; broke: [number, number] };
const NO_MEASURE: Measured = { end: 0, at: 0, from: 0, chips: [], broke: [0, 0] };

function selectTick() {
  Haptics.selectionAsync().catch(() => {});
}

function lightTap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function useUserValues() {
  const focus = useSharedValue(0);
  const caret = useSharedValue(0);
  const placeholder = useSharedValue(1);
  const send = useSharedValue(0);
  const pop = useSharedValue(0);
  const count = useSharedValue(0);
  const morph = useSharedValue(1);
  const select = useSharedValue(0);
  const breakUp = useSharedValue(0);
  /** The thread's height as drawn (its translate), as laid out, and with only the history in it. */
  const thread = useSharedValue(0);
  const threadFull = useSharedValue(0);
  const threadRest = useSharedValue(0);
  return useMemo(
    () => ({ focus, caret, placeholder, send, pop, count, morph, select, breakUp, thread, threadFull, threadRest }),
    [focus, caret, placeholder, send, pop, count, morph, select, breakUp, thread, threadFull, threadRest],
  );
}

type UserValues = ReturnType<typeof useUserValues>;

function writePose(u: UserValues, p: Pose) {
  'worklet';
  u.focus.set(p.focus);
  u.caret.set(0);
  u.placeholder.set(p.placeholder);
  u.send.set(p.send);
  u.pop.set(p.pop);
  u.count.set(p.count);
  u.morph.set(1);
  u.select.set(p.select);
  u.breakUp.set(0);
}

function easeToRest(u: UserValues, d: number) {
  'worklet';
  const o = { duration: d, easing: EASE_OUT };
  u.focus.set(withTiming(0, o));
  u.pop.set(withTiming(0, o));
  u.send.set(withTiming(0, o));
  u.placeholder.set(withTiming(1, o));
  u.select.set(withTiming(0, o));
  // A message the person sent slides back down into the composer; React drops it once it is out of sight.
  const rest = u.threadRest.get();
  if (rest > 0 && u.thread.get() !== rest) u.thread.set(withTiming(rest, { duration: d, easing: EASE_OUT }));
}

export function MentionAutocomplete({ autoPlay = true, channel = '#launch-week', onSend }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const still = autoPlay && reduced;
  const g = useMemo(() => makeGeo(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);

  // ---- UI thread -----------------------------------------------------------------------------------
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  /** 0 = the loop, 1 = a person, 2 = easing back, 3 = waiting for React to reset. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const editing = useSharedValue(0);
  const lift = useSharedValue(0);
  const u = useUserValues();
  const loopTime = useDerivedValue(() => (still ? T_STILL : clock.get()));

  // ---- React: keystrokes, picks, Backspace on chips -----------------------------------------------------
  const [driver, setDriver] = useState<'auto' | 'user'>('auto');
  const [step, setStep] = useState(0);
  const [takeT, setTakeT] = useState(0);
  const [resetTick, setResetTick] = useState(0);
  const [uDoc, setUDoc] = useState<Doc>(EMPTY_DOC);
  const [uMode, setUMode] = useState<ChipMode>('none');
  const [uActive, setUActive] = useState(0);
  const [uQuery, setUQuery] = useState<{ at: number; query: string } | null>(null);
  const [uFrom, setUFrom] = useState(MORPH_FROM);
  const [uBroke, setUBroke] = useState<{ prefix: string; label: string }>({ prefix: '', label: '' });
  const [nonce, setNonce] = useState(false);
  const [messages, setMessages] = useState<Message[]>(HISTORY);
  const [measured, setMeasured] = useState<Measured>(NO_MEASURE);
  const inputRef = useRef<TextInput | null>(null);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const user = driver === 'user';
  const s = SCRIPT[step];
  const doc = user ? uDoc : s.doc;
  const chipMode: ChipMode = user ? uMode : s.chip;
  const activeChip = user ? uActive : 0;
  const query = useMemo(
    () => (user ? uQuery : s.query === null ? null : { at: s.doc.text.lastIndexOf('@'), query: s.query }),
    [s, uQuery, user],
  );
  const morphFrom = user ? uFrom : MORPH_FROM;
  const broke = user ? uBroke : { prefix: s.doc.text.slice(0, CHIP_START), label: LABEL };
  const open = query !== null && rank(query.query).length > 0;
  const slots = useMemo(() => {
    const list = query === null ? [] : rank(query.query);
    return PEOPLE.map((_, i) => list.indexOf(i));
  }, [query]);

  const pose = useDerivedValue<Pose>(() => {
    if (mode.get() === 0) return autoPose(loopTime.get());
    return {
      focus: u.focus.get(),
      caret: u.caret.get(),
      placeholder: u.placeholder.get(),
      send: u.send.get(),
      pop: u.pop.get(),
      count: u.count.get(),
      rowY: [],
      rowA: [],
      morph: u.morph.get(),
      select: u.select.get(),
      breakUp: u.breakUp.get(),
    };
  });
  const dot = useDerivedValue<Dot>(() => (mode.get() === 0 && auto ? autoDot(clock.get()) : { target: 0, alpha: 0, scale: 1 }));

  useAnimatedReaction(
    () => (mode.get() === 0 ? stepAt(loopTime.get()) : -1),
    (k, prev) => {
      if (k >= 0 && k !== prev) scheduleOnRN(setStep, k);
    },
  );

  const resetToAuto = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    setDriver('auto');
    setStep(0);
    setUDoc(EMPTY_DOC);
    setUMode('none');
    setUQuery(null);
    setMessages(HISTORY);
    setResetTick((n) => n + 1);
  }, []);

  const startLoop = useCallback(() => {
    'worklet';
    clock.set(0);
    mode.set(0);
  }, [clock, mode]);

  useEffect(() => {
    if (resetTick > 0) scheduleOnUI(startLoop);
  }, [resetTick, startLoop]);

  useEffect(() => {
    const list = timers.current;
    return () => list.forEach(clearTimeout);
  }, []);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (m === 1) {
      if (editing.get() === 0 && now.get() - lastTouch.get() > IDLE) {
        mode.set(2);
        returnAt.set(now.get());
        easeToRest(u, reduced ? 0 : RETURN_LEN * 1000);
      }
      return;
    }
    if (m === 2 && now.get() - returnAt.get() > RETURN_LEN) {
      mode.set(3);
      scheduleOnRN(resetToAuto);
    }
  });

  // ---- The thread -----------------------------------------------------------------------------------
  // The thread hangs from its top just under the composer's edge and is lifted by its own height, so a new
  // message is laid out out of sight and nothing in the layout moves: only this translate does.
  const threadTo = useCallback(
    (h: number, rest: boolean, fast: boolean) => {
      'worklet';
      const first = u.threadFull.get() === 0;
      u.threadFull.set(h);
      if (rest) u.threadRest.set(h);
      if (first || fast) u.thread.set(h);
      else if (mode.get() !== 2) u.thread.set(withTiming(h, { duration: SEND_MS, easing: EASE_OUT }));
    },
    [mode, u],
  );

  const threadBack = useCallback(
    (fast: boolean) => {
      'worklet';
      const h = u.threadFull.get();
      u.thread.set(fast ? h : withTiming(h, { duration: SEND_MS, easing: EASE_OUT }));
    },
    [u],
  );

  const onThreadLayout = (e: LayoutChangeEvent) => {
    scheduleOnUI(threadTo, e.nativeEvent.layout.height, messages === HISTORY, reduced);
  };

  // ---- Hand-over -----------------------------------------------------------------------------------
  const freeze = useCallback(
    (t: number) => {
      'worklet';
      writePose(u, autoPose(t));
      mode.set(1);
      lastTouch.set(now.get());
    },
    [lastTouch, mode, now, u],
  );

  const takeOver = useCallback(() => {
    const m = mode.get();
    if (m === 0) {
      const t = still ? T_STILL : auto ? clock.get() : 0;
      const st = SCRIPT[stepAt(t)];
      const um = st.chip === 'morph' ? 'chip' : st.chip === 'break' ? 'none' : st.chip;
      setUDoc(um === 'chip' ? spaceAfterChip(st.doc) : st.doc);
      setUMode(um);
      setUActive(0);
      setUQuery(st.query === null ? null : { at: st.doc.text.lastIndexOf('@'), query: st.query });
      setTakeT(t);
      setDriver('user');
      scheduleOnUI(freeze, t);
      return;
    }
    if (m === 2) {
      mode.set(1);
      scheduleOnUI(threadBack, reduced);
    }
    lastTouch.set(now.get());
  }, [auto, clock, freeze, lastTouch, mode, now, reduced, still, threadBack]);

  // ---- The person's typing -----------------------------------------------------------------------------
  const popUI = useCallback(
    (show: boolean, n: number, hasText: boolean, fast: boolean) => {
      'worklet';
      const k = fast ? 0 : 1;
      u.pop.set(withSpring(show ? 1 : 0, { duration: show ? 380 * k : 220 * k, dampingRatio: show ? 0.62 : 1 }));
      if (show) u.count.set(withSpring(n, { duration: 380 * k, dampingRatio: 0.8 }));
      u.send.set(withTiming(hasText ? 1 : 0, { duration: 180 * k }));
      u.placeholder.set(withTiming(hasText ? 0 : 1, { duration: 120 * k }));
      u.select.set(withTiming(0, { duration: 120 * k }));
      lastTouch.set(now.get());
    },
    [lastTouch, now, u],
  );

  const chipUI = useCallback(
    (kind: 'morph' | 'select' | 'break', fast: boolean) => {
      'worklet';
      const k = fast ? 0 : 1;
      if (kind === 'morph') {
        u.morph.set(0);
        u.morph.set(withTiming(1, { duration: MORPH_LEN * 1000 * k, easing: Easing.linear }));
        u.pop.set(withTiming(0, { duration: 220 * k, easing: EASE_OUT }));
        u.select.set(0);
      } else if (kind === 'select') {
        u.select.set(withTiming(1, { duration: SELECT_LEN * 1000 * k, easing: EASE_OUT }));
      } else {
        u.breakUp.set(0);
        u.breakUp.set(withTiming(1, { duration: BREAK_LEN * 1000 * k, easing: Easing.linear }));
        u.select.set(withTiming(0, { duration: BREAK_LEN * 1000 * k }));
      }
      lastTouch.set(now.get());
    },
    [lastTouch, now, u],
  );

  const later = (fn: () => void, ms: number) => {
    timers.current.push(setTimeout(fn, reduced ? 0 : ms));
  };

  const onChangeText = (raw: string) => {
    takeOver();
    const base = user ? uDoc : s.doc;
    let next = raw.split(ZW).join('');
    // The nonce's zero-width space sits at the end of the text, under the caret when the chip is last: a Backspace
    // there removes only that, so the text looks unchanged. It was meant for the character before it.
    if (nonce && !raw.includes(ZW) && next === base.text && next.length > 0) next = next.slice(0, -1);
    const e = applyText(base, next);
    if (e.kind === 'select') {
      setUDoc({ ...base, selected: e.chip });
      setUMode('selected');
      setUActive(e.chip);
      setNonce((n) => !n);
      selectTick();
      scheduleOnUI(chipUI, 'select', reduced);
      return;
    }
    if (e.kind === 'break') {
      const c = base.chips[e.chip];
      setUBroke({ prefix: base.text.slice(0, c.start), label: base.text.slice(c.start, c.start + c.len) });
      setUDoc(e.doc);
      setUMode('break');
      setNonce((n) => !n);
      lightTap();
      scheduleOnUI(chipUI, 'break', reduced);
      later(() => setUMode((m) => (m === 'break' ? 'chip' : m)), BREAK_LEN * 1000 + 40);
      return;
    }
    setUDoc(e.doc);
    if (uMode === 'selected') setUMode('chip');
    const q = activeQuery(e.doc, e.caret);
    setUQuery(q);
    const n = q === null ? 0 : rank(q.query).length;
    scheduleOnUI(popUI, n > 0, n, e.doc.text.length > 0, reduced);
  };

  const pick = (person: number) => {
    takeOver();
    const base = user ? uDoc : s.doc;
    const q = user ? uQuery : query;
    if (q === null) return;
    const r = insertMention(base, q.at, q.at + 1 + q.query.length, person);
    setUFrom(`@${q.query}`);
    setUDoc(r.doc);
    setUActive(r.chip);
    setUMode('morph');
    setUQuery(null);
    selectTick();
    scheduleOnUI(chipUI, 'morph', reduced);
    later(() => setUMode((m) => (m === 'morph' ? 'chip' : m)), MORPH_LEN * 1000 + 60);
  };

  const send = () => {
    takeOver();
    const base = user ? uDoc : s.doc;
    if (base.text.trim().length === 0) return;
    setMessages((list) => [...list, { id: `m${Date.now()}`, author: YOU, time: 'now', doc: base }]);
    onSend?.(base.text.trim(), base.chips.map((c) => PEOPLE[c.person]));
    setUDoc(EMPTY_DOC);
    setUMode('none');
    setUQuery(null);
    setNonce((n) => !n);
    lightTap();
    scheduleOnUI(popUI, false, 0, false, reduced);
  };

  const onSubmit = () => {
    if (query !== null) {
      const first = rank(query.query)[0];
      if (first !== undefined) pick(first);
      return;
    }
    send();
  };

  const focusUI = useCallback(
    (on: boolean) => {
      'worklet';
      editing.set(on ? 1 : 0);
      u.focus.set(withTiming(on ? 1 : 0, { duration: 200 }));
      lastTouch.set(now.get());
    },
    [editing, lastTouch, now, u.focus],
  );

  // Keyboard: the conversation and composer ride up with it.
  useEffect(() => {
    const show = Keyboard.addListener('keyboardWillShow', (e) => {
      const d = reduced ? 0 : e.duration || 250;
      lift.set(withTiming(Math.max(0, e.endCoordinates.height - Math.max(insets.bottom, 10)), { duration: d, easing: Easing.out(Easing.cubic) }));
    });
    const hide = Keyboard.addListener('keyboardWillHide', (e) => {
      lift.set(withTiming(0, { duration: reduced ? 0 : e.duration || 250, easing: Easing.out(Easing.cubic) }));
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [insets.bottom, lift, reduced]);

  // ---- Layout from measurement -------------------------------------------------------------------------
  const atX = measured.at;
  const popLeft = Math.min(Math.max(g.textLeft + atX - 26, g.margin), g.W - g.margin - g.popW);
  const anchor = g.textLeft + atX + 5 - popLeft;

  const bodyStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -lift.get() }] }));
  const threadStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -u.thread.get() }] }));
  const clipH = g.composerTop - 4;
  const pillStyle = useAnimatedStyle(() => ({ borderColor: interpolateColor(pose.get().focus, [0, 1], [HAIRLINE, 'rgba(47,99,214,0.45)']) }));
  const ringStyle = useAnimatedStyle(() => ({ opacity: pose.get().focus }));
  const placeholderStyle = useAnimatedStyle(() => ({ opacity: pose.get().placeholder }));
  const caretStyle = useAnimatedStyle(() => ({ opacity: pose.get().caret }));
  const sendStyle = useAnimatedStyle(() => {
    const v = pose.get().send;
    return { backgroundColor: interpolateColor(v, [0, 1], ['#E2DFD8', ACCENT]), transform: [{ scale: 0.9 + 0.1 * v }] };
  });

  const docRuns = runs(doc);
  const hideSpan = chipMode === 'morph';

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <Animated.View style={[StyleSheet.absoluteFill, bodyStyle]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={Keyboard.dismiss} accessible={false} />

        {/* Conversation, above the composer; a sent message rises out from under the clip's bottom edge. */}
        <View style={[styles.threadClip, { left: g.margin + 4, right: g.margin + 4, height: clipH }]} pointerEvents="none">
          <Animated.View style={[styles.thread, { top: clipH }, threadStyle]} onLayout={onThreadLayout}>
            <View style={styles.divider}>
              <View style={styles.dividerLine} />
              <Text style={styles.dividerText}>Today</Text>
              <View style={styles.dividerLine} />
            </View>
            {messages.map((m) => (
              <MessageRow key={m.id} message={m} />
            ))}
          </Animated.View>
        </View>

        {/* Composer */}
        <View style={[styles.plus, { left: g.margin, top: g.composerTop + (PILL_H - PLUS) / 2 }]}>
          <AttachMenu tint={INK} onTouch={takeOver} onPick={() => lightTap()} />
        </View>
        <View style={[styles.pillWrap, { left: g.pillLeft, top: g.composerTop, width: g.pillW }]}>
          <Animated.View style={[styles.pillRing, ringStyle]} pointerEvents="none" />
          <Animated.View style={[styles.pill, pillStyle]}>
            <View style={[styles.textArea, { left: TEXT_PAD, width: g.textW }]}>
              <ChipLayer measured={measured} doc={doc} mode={chipMode} active={activeChip} morphFrom={morphFrom} broke={broke} pose={pose} />
              {doc.text.length === 0 ? (
                <Animated.View style={[styles.placeholderBox, placeholderStyle]} pointerEvents="none">
                  <Text style={styles.placeholder} numberOfLines={1}>
                    Message {channel}
                  </Text>
                </Animated.View>
              ) : null}
              <TextInput
                ref={inputRef}
                onChangeText={onChangeText}
                onFocus={() => {
                  takeOver();
                  scheduleOnUI(focusUI, true);
                }}
                onBlur={() => scheduleOnUI(focusUI, false)}
                onSubmitEditing={onSubmit}
                submitBehavior="submit"
                returnKeyType="send"
                autoCorrect={false}
                autoCapitalize="sentences"
                selectionColor={ACCENT}
                accessibilityLabel={`Message ${channel}`}
                style={styles.input}
              >
                {docRuns.map((r, i) =>
                  r.chip < 0 ? (
                    <Text key={i}>{r.text}</Text>
                  ) : (
                    <Text key={i} style={[styles.chipText, doc.selected === r.chip && styles.chipTextSelected, hideSpan && r.chip === activeChip && styles.hidden]}>
                      {r.text}
                    </Text>
                  ),
                )}
                {nonce ? <Text>{ZW}</Text> : null}
              </TextInput>
              <Animated.View style={[styles.caret, { transform: [{ translateX: measured.end + 1 }] }, caretStyle]} pointerEvents="none" />
              <Measure doc={doc} at={query?.at ?? -1} morphFrom={morphFrom} broke={broke} onMeasured={setMeasured} />
            </View>
            <Pressable style={styles.sendHit} onPress={send} accessibilityRole="button" accessibilityLabel="Send" hitSlop={6}>
              <Animated.View style={[styles.send, sendStyle]}>
                <SymbolView name={{ ios: 'arrow.up', android: 'arrow_upward', web: 'arrow_upward' }} size={15} weight="bold" tintColor="#FFFDF8" />
              </Animated.View>
            </Pressable>
          </Animated.View>
        </View>

        <MentionPopover
          pose={pose}
          query={query?.query ?? ''}
          slots={slots}
          open={open}
          driver={driver}
          takeT={takeT}
          left={popLeft}
          bottom={g.popBottom}
          width={g.popW}
          anchor={anchor}
          channel={channel}
          accent={ACCENT}
          onPick={pick}
        />
      </Animated.View>

      <Header g={g} channel={channel} />
      <GhostFinger dot={dot} g={g} pose={pose} popLeft={popLeft} />
    </View>
  );
}

// ---- Chips -----------------------------------------------------------------------------------------

type ChipLayerProps = {
  measured: Measured;
  doc: Doc;
  mode: ChipMode;
  active: number;
  morphFrom: string;
  broke: { prefix: string; label: string };
  pose: SharedValue<Pose>;
};

/** Chip backgrounds behind the input, and the letters of the chip being born or broken. */
function ChipLayer({ measured, doc, mode, active, morphFrom, broke, pose }: ChipLayerProps) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {doc.chips.map((c, i) => {
        const m = measured.chips[i];
        if (!m) return null;
        if (i === active && (mode === 'morph' || mode === 'selected' || mode === 'chip')) {
          return <LiveChip key={`${c.start}-${c.person}`} x={m[0]} w={m[1]} fromW={measured.from} label={doc.text.slice(c.start, c.start + c.len)} from={morphFrom} mode={mode} pose={pose} />;
        }
        return <View key={`${c.start}-${c.person}`} style={[styles.chipBg, { left: m[0] - CHIP_PAD, width: m[1] + CHIP_PAD * 2 }]} />;
      })}
      {mode === 'break' ? <BreakingChip x={measured.broke[0]} w={measured.broke[1]} label={broke.label} pose={pose} /> : null}
    </View>
  );
}

type LiveChipProps = { x: number; w: number; fromW: number; label: string; from: string; mode: ChipMode; pose: SharedValue<Pose> };

/** The chip that grows out of the typed letters (morph), sits (chip), or is selected by Backspace. */
const LiveChip = memo(function LiveChip({ x, w, fromW, label, from, mode, pose }: LiveChipProps) {
  const morphing = mode === 'morph';
  const box = useAnimatedStyle(() => {
    const p = pose.get();
    const m = morphing ? p.morph : 1;
    const grow = easeOutBack(m);
    const pad = CHIP_PAD * clamp01(m * 3);
    const width = fromW + (w - fromW) * grow + pad * 2;
    return {
      left: x - pad,
      width,
      backgroundColor: interpolateColor(p.select, [0, 1], [CHIP_BG, ACCENT]),
      opacity: morphing ? seg(m, 0, 0.25) * 0.6 + 0.4 * clamp01(m * 4) : 1,
      transform: [{ scale: 1 + 0.04 * Math.sin(Math.PI * seg(p.select, 0, 1)) * (1 - p.select) }],
    };
  });
  const typed = useAnimatedStyle(() => {
    const m = morphing ? pose.get().morph : 1;
    return { opacity: morphing ? 1 - seg(m, 0.08, 0.45) : 0, transform: [{ translateY: -2 * seg(m, 0, 0.45) }] };
  });
  const name = useAnimatedStyle(() => {
    const m = morphing ? pose.get().morph : 1;
    return { opacity: morphing ? seg(m, 0.12, 0.55) : 0, transform: [{ translateY: 3 * (1 - seg(m, 0.12, 0.6)) }] };
  });
  const inner = useAnimatedStyle(() => {
    const m = morphing ? pose.get().morph : 1;
    return { left: CHIP_PAD * clamp01(m * 3) };
  });
  return (
    <Animated.View style={[styles.chipBox, box]}>
      <Animated.View style={[styles.chipInner, inner]}>
        <Animated.View style={[styles.layer, typed]}>
          <Text style={styles.overlayText} numberOfLines={1}>
            {from}
          </Text>
        </Animated.View>
        <Animated.View style={[styles.layer, name]}>
          <Text style={[styles.overlayText, styles.chipText]} numberOfLines={1}>
            {label}
          </Text>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
});

/** A selected chip coming apart: its background dissolves and its name relaxes into plain text. */
function BreakingChip({ x, w, label, pose }: { x: number; w: number; label: string; pose: SharedValue<Pose> }) {
  const box = useAnimatedStyle(() => {
    const b = pose.get().breakUp;
    return {
      left: x - CHIP_PAD,
      width: w + CHIP_PAD * 2,
      opacity: 1 - seg(b, 0.05, 0.75),
      backgroundColor: interpolateColor(seg(b, 0, 0.5), [0, 1], [ACCENT, CHIP_BG]),
      transform: [{ scaleX: 1 + 0.06 * b }, { scaleY: 1 + 0.14 * b }],
    };
  });
  const text = useAnimatedStyle(() => {
    const b = pose.get().breakUp;
    return {
      opacity: 1 - seg(b, 0.1, 0.65),
      color: interpolateColor(seg(b, 0, 0.4), [0, 1], ['#FFFDF8', CHIP_INK]),
      transform: [{ translateY: 2 * b }],
    };
  });
  return (
    <>
      <Animated.View style={[styles.chipBox, box]} />
      <View style={[styles.chipInnerFixed, { left: x }]}>
        <Animated.Text style={[styles.overlayText, styles.chipText, text]} numberOfLines={1}>
          {label}
        </Animated.Text>
      </View>
    </>
  );
}

// ---- Measuring the runs ---------------------------------------------------------------------------------

type MeasureProps = {
  doc: Doc;
  /** Index of the "@" being typed, or -1. */
  at: number;
  morphFrom: string;
  broke: { prefix: string; label: string };
  onMeasured: (m: Measured) => void;
};

/**
 Hidden copies of the composer's runs, laid out with the same type, give the x of every chip, the
 caret and the "@". A trailing bar keeps a trailing space from being trimmed; its width comes off.
*/
function Measure({ doc, at, morphFrom, broke, onMeasured }: MeasureProps) {
  const got = useRef<Record<string, number>>({});
  const last = useRef('');
  const chipCount = doc.chips.length;

  const onWidth = useCallback(
    (k: string, width: number) => {
      const v = got.current;
      v[k] = width;
      const bar = v.bar;
      if (bar === undefined || v.end === undefined || v.from === undefined || v.at === undefined || v.bx === undefined || v.bw === undefined) return;
      const chips: [number, number][] = [];
      for (let i = 0; i < chipCount; i += 1) {
        const x = v[`x${i}`];
        const w = v[`w${i}`];
        if (x === undefined || w === undefined) return;
        chips.push([Math.max(0, x - bar), w]);
      }
      const m: Measured = { end: Math.max(0, v.end - bar), at: Math.max(0, v.at - bar), from: v.from, chips, broke: [Math.max(0, v.bx - bar), v.bw] };
      const key = JSON.stringify(m);
      if (key !== last.current) {
        last.current = key;
        onMeasured(m);
      }
    },
    [chipCount, onMeasured],
  );

  const all = runs(doc);
  const before = (n: number) => runs({ text: doc.text.slice(0, n), chips: doc.chips.filter((c) => c.start + c.len <= n), selected: -1 });
  const draw = (list: ReturnType<typeof runs>) => list.map((r, i) => (r.chip < 0 ? <Text key={i}>{r.text}</Text> : <Text key={i} style={styles.chipText}>{r.text}</Text>));

  return (
    <View style={styles.measure} pointerEvents="none" accessible={false} importantForAccessibility="no-hide-descendants">
      <MText id="bar" style={styles.measureText} onWidth={onWidth}>
        |
      </MText>
      <MText key={`end-${doc.text}-${chipCount}`} style={styles.measureText} id={'end'} onWidth={onWidth}>
        {draw(all)}|
      </MText>
      <MText key={`at-${at}-${doc.text}`} style={styles.measureText} id={'at'} onWidth={onWidth}>
        {draw(before(Math.max(0, at)))}|
      </MText>
      <MText key={`from-${morphFrom}`} style={styles.measureText} id={'from'} onWidth={onWidth}>
        {morphFrom}
      </MText>
      <MText key={`bx-${broke.prefix}`} style={styles.measureText} id={'bx'} onWidth={onWidth}>
        {broke.prefix}|
      </MText>
      <MText key={`bw-${broke.label}`} style={[styles.measureText, styles.chipText]} id={'bw'} onWidth={onWidth}>
        {broke.label}
      </MText>
      {doc.chips.map((c, i) => (
        <MText key={`x${i}-${c.start}-${doc.text.slice(0, c.start)}`} style={styles.measureText} id={`x${i}`} onWidth={onWidth}>
          {draw(before(c.start))}|
        </MText>
      ))}
      {doc.chips.map((c, i) => (
        <MText key={`w${i}-${c.start}-${c.len}`} style={[styles.measureText, styles.chipText]} id={`w${i}`} onWidth={onWidth}>
          {doc.text.slice(c.start, c.start + c.len)}
        </MText>
      ))}
    </View>
  );
}

type MTextProps = { id: string; style: StyleProp<TextStyle>; onWidth: (id: string, width: number) => void; children: React.ReactNode };

/** A hidden Text that reports its width under an id. */
function MText({ id, style, onWidth, children }: MTextProps) {
  return (
    <Text style={style} numberOfLines={1} onLayout={(e) => onWidth(id, e.nativeEvent.layout.width)}>
      {children}
    </Text>
  );
}

// ---- Conversation and chrome ------------------------------------------------------------------------------

const MessageRow = memo(function MessageRow({ message }: { message: Message }) {
  const a = message.author ?? YOU;
  return (
    <View style={styles.message}>
      <Avatar initials={`${a.first[0]}${a.last[0]}`} colors={a.colors} size={36} online={a.online} ring={PAGE} />
      <View style={styles.messageBody}>
        <View style={styles.messageHead}>
          <Text style={styles.author}>{a === YOU ? 'You' : `${a.first} ${a.last}`}</Text>
          <Text style={styles.time}>{message.time}</Text>
        </View>
        <Text style={styles.messageText}>
          {runs(message.doc).map((r, i) =>
            r.chip < 0 ? (
              <Text key={i}>{r.text}</Text>
            ) : (
              <Text key={i} style={styles.inlineMention}>
                {r.text}
              </Text>
            ),
          )}
        </Text>
      </View>
    </View>
  );
});

function Header({ g, channel }: { g: Geo; channel: string }) {
  return (
    <View style={[styles.header, { paddingTop: g.top, height: g.top + g.headerH }]}>
      <View style={[styles.headerRow, { paddingHorizontal: g.margin + 2 }]}>
        <SymbolView name={{ ios: 'chevron.left', android: 'arrow_back', web: 'arrow_back' }} size={19} tintColor={INK} />
        <View style={styles.headerMid}>
          <Text style={styles.channel}>{channel}</Text>
          <Text style={styles.members}>14 members · 5 online</Text>
        </View>
        <View style={styles.stack}>
          {[PEOPLE[2], PEOPLE[0], PEOPLE[1]].map((p, i) => (
            <View key={p.id} style={[styles.stackItem, { marginLeft: i === 0 ? 0 : -9, zIndex: 3 - i }]}>
              <Avatar initials={`${p.first[0]}${p.last[0]}`} colors={p.colors} size={26} />
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

function GhostFinger({ dot, g, pose, popLeft }: { dot: SharedValue<Dot>; g: Geo; pose: SharedValue<Pose>; popLeft: number }) {
  const style = useAnimatedStyle(() => {
    const d = dot.get();
    let x = g.pillLeft + g.pillW * 0.42;
    let y = g.composerTop + PILL_H / 2;
    if (d.target === 1) {
      const n = Math.max(1, pose.get().count);
      x = popLeft + g.popW * 0.4;
      y = g.popBottom - POP_PAD - (n - 1) * ROW_H - ROW_H / 2;
    }
    return {
      opacity: d.alpha,
      transform: [{ translateX: x - DOT_SIZE / 2 }, { translateY: y - DOT_SIZE / 2 + (1 - d.alpha) * 8 }, { scale: d.scale }],
    };
  });
  return <Animated.View style={[styles.finger, style]} pointerEvents="none" />;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    backgroundColor: BAR,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: HAIRLINE,
  },
  headerRow: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12 },
  headerMid: { flex: 1, gap: 1 },
  channel: { fontSize: 17, fontWeight: '600', color: INK, letterSpacing: -0.3 },
  members: { fontSize: 12.5, color: MUTED },
  stack: { flexDirection: 'row' },
  stackItem: { borderRadius: 15, borderWidth: 2, borderColor: BAR },
  threadClip: { position: 'absolute', top: 0, overflow: 'hidden' },
  thread: { position: 'absolute', left: 0, right: 0, gap: 16, paddingBottom: THREAD_TAIL },
  divider: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 2 },
  dividerLine: { flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: HAIRLINE },
  dividerText: { fontSize: 12, fontWeight: '500', color: MUTED },
  message: { flexDirection: 'row', gap: 11 },
  messageBody: { flex: 1, gap: 2 },
  messageHead: { flexDirection: 'row', alignItems: 'baseline', gap: 7 },
  author: { fontSize: 15, fontWeight: '600', color: INK, letterSpacing: -0.2 },
  time: { fontSize: 12, color: MUTED, fontVariant: ['tabular-nums'] },
  messageText: { fontSize: 16, lineHeight: 21, color: '#34312C', letterSpacing: -0.15 },
  inlineMention: { color: CHIP_INK, fontWeight: '600', backgroundColor: CHIP_BG },
  plus: {
    position: 'absolute',
    width: PLUS,
    height: PLUS,
    borderRadius: PLUS / 2,
    backgroundColor: CARD,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIRLINE,
    boxShadow: '0 1px 2px rgba(30,26,20,0.08)',
  },
  pillWrap: { position: 'absolute', height: PILL_H },
  pillRing: { position: 'absolute', left: -4, right: -4, top: -4, bottom: -4, borderRadius: PILL_H / 2 + 4, backgroundColor: 'rgba(47,99,214,0.10)' },
  pill: {
    flex: 1,
    borderRadius: PILL_H / 2,
    borderWidth: 1,
    backgroundColor: CARD,
    boxShadow: '0 1px 2px rgba(30,26,20,0.08), 0 8px 20px rgba(30,26,20,0.07)',
  },
  textArea: { position: 'absolute', top: 0, height: PILL_H, overflow: 'hidden' },
  placeholderBox: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, justifyContent: 'center' },
  placeholder: { fontSize: FONT, color: '#A7A297' },
  input: { position: 'absolute', left: 0, right: 0, top: 0, height: PILL_H, paddingVertical: 0, paddingHorizontal: 0, fontSize: FONT, color: INK },
  chipText: { color: CHIP_INK, fontWeight: '600' },
  chipTextSelected: { color: '#FFFDF8' },
  hidden: { color: 'transparent' },
  caret: { position: 'absolute', left: 0, top: (PILL_H - 22) / 2, width: 2, height: 22, borderRadius: 1, backgroundColor: ACCENT },
  chipBg: { position: 'absolute', top: (PILL_H - CHIP_H) / 2, height: CHIP_H, borderRadius: 7, borderCurve: 'continuous', backgroundColor: CHIP_BG },
  chipBox: { position: 'absolute', top: (PILL_H - CHIP_H) / 2, height: CHIP_H, borderRadius: 7, borderCurve: 'continuous', overflow: 'hidden' },
  chipInner: { position: 'absolute', top: -(PILL_H - CHIP_H) / 2, height: PILL_H, justifyContent: 'center' },
  chipInnerFixed: { position: 'absolute', top: 0, height: PILL_H, justifyContent: 'center' },
  layer: { position: 'absolute', left: 0, top: 0, bottom: 0, justifyContent: 'center' },
  overlayText: { fontSize: FONT, color: INK },
  sendHit: { position: 'absolute', right: 6, top: (PILL_H - SEND) / 2 },
  send: { width: SEND, height: SEND, borderRadius: SEND / 2, alignItems: 'center', justifyContent: 'center' },
  measure: { position: 'absolute', left: 0, top: 0, width: 2000, opacity: 0 },
  measureText: { position: 'absolute', left: 0, top: 0, fontSize: FONT },
  finger: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: 'rgba(30,26,20,0.16)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.75)',
    boxShadow: '0 4px 12px rgba(30,26,20,0.14)',
  },
});

// Taken over with the caret right after a chip (the film's Backspace had taken its space): give the space back,
// so what the person types next never runs into the name.
function spaceAfterChip(doc: Doc): Doc {
  const last = doc.chips[doc.chips.length - 1];
  return last && last.start + last.len === doc.text.length ? { ...doc, text: doc.text + ' ' } : doc;
}
