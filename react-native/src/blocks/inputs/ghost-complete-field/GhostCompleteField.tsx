import { matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Keyboard, Platform, Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BODY_FULL,
  BODY_GHOSTS,
  CC_CHIPS,
  CC_DOMAIN,
  CC_FULL,
  CC_GHOSTS,
  CC_TYPED,
  CHIP_GAP,
  CHIP_MS,
  type ChipsPose,
  DEMO_SWIPE,
  type DotPose,
  type GhostPose,
  IDLE,
  LOOP,
  PICK_MS,
  RETURN_LEN,
  type ScreenPose,
  T_FILLED,
  autoBody,
  autoCc,
  autoChips,
  autoDot,
  autoScreen,
  bodyCount,
  ccCount,
  clamp01,
  easeInOutCubic,
  easeOutBack,
  seg,
} from './ghostCompleteMotion';
import { GHOST_COLORS, GhostField, useGhostComplete } from './GhostField';
import { type GhostLayout, KEYCAP_W, makeGhostLayout, suggestDomains, suggestPhrase, textWidth } from './ghostLayout';

/*
 Ghost Complete Field — a reply in Postbox, a mail app, that finishes your sentences.

 The loop types without focusing anything (no keyboard). In Cc it types "theo@", two domain
 chips pop beside the caret and the tapped one stretches into the field. In the body it types
 "Hi Mara, tha": the rest of the phrase appears behind the caret in grey, word by word out of
 a blur, and a tap on the keycap chip fills it with ink from left to right while the caret
 jumps to its end. A few more letters, a second suggestion, and this time a swipe to the right
 accepts it. Then the reply clears.

 A real tap focuses the real fields and the same things happen from what is typed: see
 GhostField.tsx for the field, ghostLayout.ts for the phrases it knows.
*/

type Props = {
  /** Writes the reply every LOOP seconds until the screen is touched. */
  autoPlay?: boolean;
};

const PAGE = '#FBFBFC';
const HAIRLINE = '#E6E7EB';
const INK = GHOST_COLORS.ink;
const MUTED = '#8A90A0';
const ACCENT = GHOST_COLORS.accent;
const FAMILY = Platform.OS === 'ios' ? 'System' : 'sans-serif';
const FONT_SIZE = 17;
const BODY_SIZE = 18;
const LINE = 26;
const ROW_LINE = 22;
const LABEL_W = 36;
const CHIPS_LEFT = 12;
const CHIP_PAD = 10;
const DOT_SIZE = 44;
const REST_DOT: DotPose = { target: 0, alpha: 0, scale: 1, travel: 0 };
const QUOTE_INTRO = "Notes from today's walkthrough are attached. Three things to settle before Friday:";
const NO_CHIPS: ChipsPose = { show: 0, pick: -1, take: 0 };

function makeGeo(W: number, H: number, top: number, bottom: number) {
  const compact = H < 740;
  const margin = Math.max(20, (W - 440) / 2);
  const width = W - margin * 2;
  const navTop = top + (compact ? 2 : 6);
  const rowH = compact ? 42 : 46;
  const rowsTop = navTop + 44 + (compact ? 2 : 8);
  const bodyTop = rowsTop + rowH * 3 + (compact ? 12 : 16);
  const bodyH = LINE * (compact ? 4 : 6);
  const toolbarTop = H - bottom - 50;
  const quoteTop = bodyTop + bodyH + (compact ? 8 : 14);
  return {
    W,
    H,
    compact,
    margin,
    width,
    navTop,
    rowH,
    rowsTop,
    ccTop: rowsTop + rowH,
    ccX: margin + LABEL_W,
    ccWidth: width - LABEL_W,
    bodyTop,
    bodyH,
    toolbarTop,
    quoteTop,
    quoteH: Math.max(0, toolbarTop - 12 - quoteTop),
  };
}

function lightTap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function GhostCompleteField({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  /** Reduced motion with autoPlay: the finished reply, nothing moving. */
  const still = autoPlay && reduced;
  const g = useMemo(() => makeGeo(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);

  const font = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: FONT_SIZE, fontWeight: '400' }), []);
  const bodyFont = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: BODY_SIZE, fontWeight: '400' }), []);
  /** The loop's paragraphs, laid out once: every glyph already where it will end up. */
  const bodyAuto = useMemo(() => makeGhostLayout(bodyFont, BODY_FULL, BODY_GHOSTS, g.width, LINE), [bodyFont, g.width]);
  const ccAuto = useMemo(() => makeGhostLayout(font, CC_FULL, CC_GHOSTS, g.ccWidth, ROW_LINE, false), [font, g.ccWidth]);
  const autoStretch = useMemo(() => textWidth(font, CC_DOMAIN) + 8, [font]);

  // ---- UI-thread state --------------------------------------------------------------------------------
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  /** 0 = the loop (or rest), 1 = a person drives, 2 = clearing before the loop restarts. */
  const mode = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const focused = useSharedValue(0);
  const uShow = useSharedValue(0);
  const uPick = useSharedValue(-1);
  const uTake = useSharedValue(0);
  const uStretch = useSharedValue(80);
  const uSend = useSharedValue(0);

  // ---- JS state: changes on keystrokes and focus only ---------------------------------------------------
  const [chips, setChips] = useState(CC_CHIPS);
  const [formKey, setFormKey] = useState(0);
  const focusCount = useRef(0);
  const entering = useRef(false);
  const picking = useRef(false);
  const chipCount = useRef(CC_CHIPS.length);

  const onBodyChange = useCallback(
    (text: string) => {
      uSend.set(withTiming(text.trim().length > 0 ? 1 : 0, { duration: 220 }));
    },
    [uSend],
  );
  const onCcChange = useCallback(
    (text: string) => {
      if (picking.current) return;
      const next = suggestDomains(text);
      if (next.length > 0 && chipCount.current === 0) {
        uShow.set(0);
        uShow.set(withTiming(2, { duration: 2000, easing: Easing.linear }));
      }
      if (next.length === 0) uShow.set(0);
      chipCount.current = next.length;
      setChips((old) => (old.join() === next.join() ? old : next));
    },
    [uShow],
  );

  const body = useGhostComplete({ font: bodyFont, width: g.width, lineHeight: LINE, suggest: suggestPhrase, multiline: true, still: reduced, onChange: onBodyChange });
  const cc = useGhostComplete({ font, width: g.ccWidth, lineHeight: ROW_LINE, keycap: false, still: reduced, onChange: onCcChange });
  const { pose: bodyUserPose, layout: bodyUserLayout, ink: bodyInk, adopt: adoptBody } = body;
  const { pose: ccUserPose, layout: ccUserLayout, ink: ccInk, adopt: adoptCc, offer: offerCc, accept: acceptCc, text: ccText } = cc;

  const loopTime = useDerivedValue(() => (still ? T_FILLED : clock.get()));
  const bodyPose = useDerivedValue<GhostPose>(() => {
    if (mode.get() !== 0) return bodyUserPose.get();
    const p = autoBody(loopTime.get());
    if (still) p.caret = 0;
    return p;
  });
  const ccPose = useDerivedValue<GhostPose>(() => (mode.get() === 0 ? autoCc(loopTime.get()) : ccUserPose.get()));
  const bodyLayout = useDerivedValue<GhostLayout>(() => (mode.get() === 0 ? bodyAuto : bodyUserLayout.get()));
  const ccLayout = useDerivedValue<GhostLayout>(() => (mode.get() === 0 ? ccAuto : ccUserLayout.get()));
  const chipsPose = useDerivedValue<ChipsPose>(() => {
    if (mode.get() === 0) return auto ? autoChips(clock.get()) : NO_CHIPS;
    return { show: uShow.get(), pick: uPick.get(), take: uTake.get() };
  });
  const stretch = useDerivedValue(() => (mode.get() === 0 ? autoStretch : uStretch.get()));
  const screen = useDerivedValue<ScreenPose>(() => (mode.get() === 0 ? autoScreen(loopTime.get()) : { send: uSend.get() }));
  const dot = useDerivedValue<DotPose>(() => (mode.get() === 0 && auto ? autoDot(clock.get()) : REST_DOT));
  /** Where the chips start: just past the typed part of the address. */
  const chipsX = useDerivedValue(() => {
    const p = ccPose.get();
    const L = ccLayout.get();
    const stop = p.run >= 0 && p.run < L.runs.length ? L.runs[p.run].start : p.n;
    return L.cx[Math.min(L.cx.length - 1, Math.max(0, stop))] + CHIPS_LEFT;
  });

  const resetForm = useCallback(() => {
    adoptBody('', false);
    adoptCc('', false);
    picking.current = false;
    chipCount.current = CC_CHIPS.length;
    setChips(CC_CHIPS);
    setFormKey((k) => k + 1);
  }, [adoptBody, adoptCc]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    if (m === 1) {
      if (focused.get() === 0 && now.get() - lastTouch.get() > IDLE) {
        mode.set(2);
        returnAt.set(now.get());
        const d = reduced ? 0 : RETURN_LEN * 1000 * 0.6;
        bodyInk.set(withTiming(0, { duration: d }));
        ccInk.set(withTiming(0, { duration: d }));
        uSend.set(withTiming(0, { duration: d }));
        uShow.set(0);
      }
      return;
    }
    if (now.get() - returnAt.get() > RETURN_LEN) {
      uPick.set(-1);
      uTake.set(0);
      clock.set(0);
      mode.set(0);
      scheduleOnRN(resetForm);
    }
  });

  const touch = useCallback(() => {
    lastTouch.set(now.get());
  }, [lastTouch, now]);

  /** Hands the loop's current text to the person, so taking over never jumps. */
  const enterUser = useCallback(() => {
    touch();
    if (entering.current) return;
    const m = mode.get();
    if (m === 1) return;
    entering.current = true;
    setTimeout(() => {
      entering.current = false;
    }, 0);
    if (m === 0) {
      const t = still ? T_FILLED : auto ? clock.get() : 0;
      const address = CC_FULL.slice(0, ccCount(t));
      const message = BODY_FULL.slice(0, bodyCount(t));
      adoptCc(address, false);
      adoptBody(message);
      const next = suggestDomains(address);
      chipCount.current = next.length;
      setChips(next);
      uShow.set(next.length > 0 ? 2 : 0);
      uPick.set(-1);
      uTake.set(0);
      uSend.set(message.length > 0 ? 1 : 0);
    } else {
      // Caught while clearing: what was written comes back.
      bodyInk.set(withTiming(1, { duration: 150 }));
      ccInk.set(withTiming(1, { duration: 150 }));
    }
    mode.set(1);
  }, [adoptBody, adoptCc, auto, bodyInk, ccInk, clock, mode, still, touch, uPick, uSend, uShow, uTake]);

  const onFocus = () => {
    enterUser();
    focusCount.current += 1;
    focused.set(1);
  };
  const onBlur = () => {
    focusCount.current = Math.max(0, focusCount.current - 1);
    if (focusCount.current === 0) focused.set(0);
    touch();
  };

  /** A domain chip was tapped: it stretches into the field and its text takes ink there. */
  const pick = (index: number) => {
    enterUser();
    const address = ccText.current;
    const at = address.indexOf('@');
    const label = suggestDomains(address)[index];
    if (at < 0 || !label || picking.current) return;
    const rest = label.slice(address.length - at - 1);
    if (!rest) return;
    picking.current = true;
    uStretch.set(textWidth(font, rest) + 8);
    offerCc(rest, true);
    acceptCc(0, true);
    uPick.set(index);
    uTake.set(0);
    uTake.set(withTiming(1, { duration: reduced ? 0 : PICK_MS, easing: Easing.linear }));
    setTimeout(
      () => {
        picking.current = false;
        chipCount.current = 0;
        setChips([]);
        uShow.set(0);
        uPick.set(-1);
        uTake.set(0);
      },
      (reduced ? 0 : PICK_MS) + 60,
    );
  };

  const onSend = () => {
    enterUser();
    Keyboard.dismiss();
    lightTap();
    // A sent reply clears at once: once the keyboard's blur has passed, let the idle return run.
    setTimeout(() => lastTouch.set(now.get() - IDLE), 120);
  };

  const targets = useMemo(() => {
    const r0 = bodyAuto.runs[0];
    const r1 = bodyAuto.runs[1];
    return [
      [g.ccX + 48, g.ccTop + g.rowH / 2],
      [g.ccX + ccAuto.cx[CC_TYPED.length] + CHIPS_LEFT + 44, g.ccTop + g.rowH / 2],
      [g.margin + 72, g.bodyTop + LINE / 2],
      [g.margin + r0.chipX + KEYCAP_W / 2, g.bodyTop + r0.chipY + LINE / 2],
      // The swipe runs along the suggestion's last line, wherever the wrap put it.
      [g.margin + g.width * 0.2, g.bodyTop + bodyAuto.cy[r1.end] + LINE / 2],
    ];
  }, [bodyAuto, ccAuto, g]);

  const sendStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(screen.get().send, [0, 1], ['#D5D8E0', ACCENT]) }));
  const chipsStyle = useAnimatedStyle(() => {
    const p = chipsPose.get();
    return { transform: [{ translateX: p.show > 0 ? chipsX.get() : -4000 }] };
  });

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <Pressable style={StyleSheet.absoluteFill} onPress={Keyboard.dismiss} accessible={false}>
        {/* Navigation */}
        <View style={[styles.nav, { top: g.navTop, left: g.margin, width: g.width }]}>
          <Text style={styles.cancel}>Cancel</Text>
          <Text style={styles.navTitle}>Reply</Text>
          <Pressable onPress={onSend} hitSlop={10} accessibilityRole="button" accessibilityLabel="Send">
            <Animated.View style={[styles.send, sendStyle]}>
              <SymbolView name={{ ios: 'arrow.up', android: 'arrow_upward', web: 'arrow_upward' }} size={16} weight="bold" tintColor="#FFFFFF" />
            </Animated.View>
          </Pressable>
        </View>

        {/* To */}
        <View style={[styles.row, { top: g.rowsTop, left: g.margin, width: g.width, height: g.rowH }]}>
          <Text style={styles.rowLabel}>To:</Text>
          <View style={styles.token}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>MO</Text>
            </View>
            <Text style={styles.tokenText}>Mara Okafor</Text>
          </View>
        </View>

        {/* Cc: an address field whose domains are offered as chips */}
        <View style={[styles.row, { top: g.ccTop, left: g.margin, width: g.width, height: g.rowH }]}>
          <Text style={styles.rowLabel}>Cc:</Text>
        </View>
        <View style={[styles.abs, { left: g.ccX, top: g.ccTop + (g.rowH - ROW_LINE) / 2 }]}>
          <GhostField
            pose={ccPose}
            layout={ccLayout}
            field={cc}
            font={font}
            width={g.ccWidth}
            height={ROW_LINE}
            lineHeight={ROW_LINE}
            placeholder="Add people"
            keycap={false}
            ghostColor="rgba(217,71,43,0)"
            onTouch={enterUser}
            onFocus={onFocus}
            onBlur={onBlur}
            keyboardType="email-address"
            autoCapitalize="none"
            textContentType="emailAddress"
            returnKeyType="next"
            maxLength={36}
          />
          <Animated.View style={[styles.chips, chipsStyle]}>
            {chips.map((label, index) => (
              <DomainChip key={label} index={index} label={label} pose={chipsPose} stretch={stretch} onPress={() => pick(index)} />
            ))}
          </Animated.View>
        </View>

        {/* Subject: an ordinary field */}
        <View style={[styles.row, { top: g.rowsTop + g.rowH * 2, left: g.margin, width: g.width, height: g.rowH }]}>
          <Text style={styles.rowLabel}>Subject:</Text>
          <TextInput
            key={formKey}
            defaultValue="Re: Tuesday notes"
            onFocus={onFocus}
            onBlur={onBlur}
            onChangeText={touch}
            style={styles.subject}
            selectionColor={ACCENT}
            returnKeyType="next"
            accessibilityLabel="Subject"
          />
        </View>

        {/* Body */}
        <View style={[styles.abs, { left: g.margin, top: g.bodyTop }]}>
          <GhostField
            pose={bodyPose}
            layout={bodyLayout}
            field={body}
            font={bodyFont}
            width={g.width}
            height={g.bodyH}
            lineHeight={LINE}
            placeholder="Write your reply"
            multiline
            onTouch={enterUser}
            onFocus={onFocus}
            onBlur={onBlur}
            maxLength={220}
          />
        </View>

        <Quote top={g.quoteTop} left={g.margin} width={g.width} height={g.quoteH} />

        {/* Toolbar */}
        <View style={[styles.toolbar, { top: g.toolbarTop, left: g.margin, width: g.width }]}>
          <View style={styles.tools}>
            <SymbolView name={{ ios: 'paperclip', android: 'attach_file', web: 'attach_file' }} size={20} tintColor={MUTED} />
            <SymbolView name={{ ios: 'photo', android: 'image', web: 'image' }} size={20} tintColor={MUTED} />
            <SymbolView name={{ ios: 'textformat', android: 'text_format', web: 'text_format' }} size={20} tintColor={MUTED} />
          </View>
          <Text style={styles.tip}>Swipe right or tap Tab to accept</Text>
        </View>
      </Pressable>

      <GhostFinger pose={dot} targets={targets} limit={W - 30} />
    </View>
  );
}

// ---- Domain chips ----------------------------------------------------------------------------------------

type ChipProps = {
  index: number;
  label: string;
  pose: SharedValue<ChipsPose>;
  /** Width the picked chip's pill stretches to: the text it becomes in the field. */
  stretch: SharedValue<number>;
  onPress: () => void;
};

/**
 A suggested mail domain. It pops in beside the caret; when picked it slides to the caret and its
 pill stretches to the width the domain takes in the field, then lets go of it, while the field
 inks the same letters in underneath.
*/
function DomainChip({ index, label, pose, stretch, onPress }: ChipProps) {
  const x = useSharedValue(index * 96);
  const w = useSharedValue(88);
  const wrapStyle = useAnimatedStyle(() => {
    const p = pose.get();
    const pop = easeOutBack(clamp01((p.show - index * CHIP_GAP) / (CHIP_MS / 1000)), 1.5);
    const picked = p.pick === index;
    const other = p.pick >= 0 && !picked;
    const leave = other ? 1 - clamp01(p.take * 3) : 1 - seg(p.take, 0.6, 0.92);
    return {
      opacity: p.show <= 0 ? 0 : clamp01(pop * 2) * leave,
      transform: [
        { translateX: picked ? -(x.get() + CHIPS_LEFT - 2) * easeInOutCubic(seg(p.take, 0, 0.55)) : other ? 10 * p.take : 0 },
        { scale: 0.8 + 0.2 * pop },
      ],
    };
  });
  const pillStyle = useAnimatedStyle(() => {
    const p = pose.get();
    if (p.pick !== index) return { transform: [{ scaleX: 1 }, { scaleY: 1 }] };
    const s = easeOutBack(seg(p.take, 0.05, 0.6), 1.4);
    return { transform: [{ scaleX: 1 + (stretch.get() / Math.max(1, w.get()) - 1) * s }, { scaleY: 1 - 0.14 * s }] };
  });
  const labelStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.pick === index ? 1 - seg(p.take, 0.08, 0.38) : 1 };
  });

  return (
    <Animated.View
      style={wrapStyle}
      onLayout={(e) => {
        x.set(e.nativeEvent.layout.x);
        w.set(e.nativeEvent.layout.width);
      }}
    >
      <Pressable onPress={onPress} hitSlop={6} accessibilityRole="button" accessibilityLabel={`Use ${label}`}>
        <View style={styles.chip}>
          <Animated.View style={[styles.chipPill, pillStyle]} />
          <Animated.Text style={[styles.chipText, labelStyle]} numberOfLines={1}>
            {label}
          </Animated.Text>
        </View>
      </Pressable>
    </Animated.View>
  );
}

// ---- The message being answered -----------------------------------------------------------------------------

function Quote({ top, left, width, height }: { top: number; left: number; width: number; height: number }) {
  return (
    <View style={[styles.quote, { top, left, width, height }]} pointerEvents="none">
      <Text style={styles.quoteHead} numberOfLines={1}>
        On Tue 12 May at 16:42, Mara Okafor wrote:
      </Text>
      <View style={styles.quoteBody}>
        <View style={styles.quoteBar} />
        <View style={styles.quoteText}>
          <View style={styles.file}>
            <View style={styles.fileIcon}>
              <View style={styles.fileFold} />
              <View style={[styles.fileLine, { width: 14 }]} />
              <View style={[styles.fileLine, { width: 18 }]} />
              <View style={[styles.fileLine, { width: 10 }]} />
            </View>
            <View>
              <Text style={styles.fileName}>Tuesday notes.pdf</Text>
              <Text style={styles.fileMeta}>3 pages · 240 KB</Text>
            </View>
          </View>
          <Text style={styles.quoteLine}>Hi Ines,</Text>
          <Text style={styles.quoteLine}>{QUOTE_INTRO}</Text>
          <Text style={styles.quoteLine}>1. Move the launch review to Thursday</Text>
          <Text style={styles.quoteLine}>2. Pick one of the two onboarding flows</Text>
          <Text style={styles.quoteLine}>3. Decide who owns the pricing page</Text>
          <Text style={styles.quoteLine}>Shout if I missed anything.</Text>
          <Text style={styles.quoteLine}>Mara</Text>
        </View>
      </View>
      <LinearGradient colors={['rgba(251,251,252,0)', PAGE]} style={styles.quoteFade} />
    </View>
  );
}

/** A soft touch dot: it taps the Cc field, a chip, the body and the keycap, then swipes right. */
function GhostFinger({ pose, targets, limit }: { pose: SharedValue<DotPose>; targets: number[][]; limit: number }) {
  const style = useAnimatedStyle(() => {
    const p = pose.get();
    const at = targets[p.target] ?? targets[0];
    const x = Math.min(limit, at[0] + p.travel * DEMO_SWIPE);
    return {
      opacity: p.alpha,
      transform: [{ translateX: x - DOT_SIZE / 2 }, { translateY: at[1] - DOT_SIZE / 2 + (p.target === 4 ? 0 : (1 - p.alpha) * 8) }, { scale: p.scale }],
    };
  });
  return <Animated.View style={[styles.finger, style]} pointerEvents="none" />;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAGE, overflow: 'hidden' },
  abs: { position: 'absolute' },
  nav: { position: 'absolute', height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cancel: { fontSize: FONT_SIZE, color: ACCENT, width: 70 },
  navTitle: { fontSize: FONT_SIZE, fontWeight: '600', color: INK, letterSpacing: -0.2 },
  send: {
    width: 32,
    height: 32,
    borderRadius: 16,
    marginLeft: 38,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(21,26,36,0.14), inset 0 1px 0 rgba(255,255,255,0.25)',
  },
  row: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: HAIRLINE,
  },
  rowLabel: { fontSize: 15, color: MUTED, marginRight: 8 },
  token: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 28,
    paddingLeft: 4,
    paddingRight: 10,
    borderRadius: 14,
    backgroundColor: '#EEF0F4',
  },
  avatar: { width: 20, height: 20, borderRadius: 10, backgroundColor: '#3E4A63', alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontSize: 9, fontWeight: '700', color: '#F4F5F8' },
  tokenText: { fontSize: 15, fontWeight: '500', color: INK },
  subject: { flex: 1, fontSize: FONT_SIZE, fontWeight: '600', color: INK, padding: 0, letterSpacing: -0.2 },
  chips: { position: 'absolute', left: 0, top: (ROW_LINE - 26) / 2, height: 26, flexDirection: 'row', gap: 8 },
  chip: { height: 26, paddingHorizontal: CHIP_PAD, justifyContent: 'center' },
  chipPill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: 13,
    backgroundColor: '#FCE9E3',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#F3C9BC',
    transformOrigin: 'left center',
  },
  chipText: { fontSize: 13.5, fontWeight: '500', color: '#B43A22' },
  quote: { position: 'absolute', overflow: 'hidden' },
  quoteHead: { fontSize: 13, lineHeight: 18, color: MUTED, marginBottom: 8 },
  quoteBody: { flexDirection: 'row', gap: 12 },
  quoteBar: { width: 3, borderRadius: 1.5, backgroundColor: '#E1E4EA' },
  quoteText: { flex: 1, gap: 7, paddingVertical: 2 },
  quoteLine: { fontSize: 15, lineHeight: 21, color: '#5B6170' },
  file: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    height: 56,
    paddingHorizontal: 10,
    marginBottom: 4,
    borderRadius: 14,
    backgroundColor: '#FFFFFF',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIRLINE,
    boxShadow: '0 1px 2px rgba(21,26,36,0.05), 0 8px 20px rgba(21,26,36,0.05)',
  },
  fileIcon: { width: 30, height: 36, borderRadius: 6, backgroundColor: '#FCE9E3', paddingLeft: 6, paddingTop: 13, gap: 3 },
  fileFold: { position: 'absolute', right: 0, top: 0, width: 10, height: 10, borderBottomLeftRadius: 4, borderTopRightRadius: 6, backgroundColor: '#F3C9BC' },
  fileLine: { height: 2, borderRadius: 1, backgroundColor: '#D9472B', opacity: 0.55 },
  fileName: { fontSize: 15, fontWeight: '600', color: INK, letterSpacing: -0.1 },
  fileMeta: { fontSize: 13, color: MUTED, marginTop: 1, fontVariant: ['tabular-nums'] },
  quoteFade: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 44 },
  toolbar: {
    position: 'absolute',
    height: 50,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: HAIRLINE,
  },
  tools: { flexDirection: 'row', alignItems: 'center', gap: 24 },
  tip: { fontSize: 13, color: MUTED },
  finger: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT_SIZE,
    height: DOT_SIZE,
    borderRadius: DOT_SIZE / 2,
    backgroundColor: 'rgba(21,26,36,0.14)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
    boxShadow: '0 4px 12px rgba(21,26,36,0.14)',
  },
});
