import { BlurMask, Canvas, Glyphs, Group, LinearGradient, RoundedRect, type SkFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, type TextInputProps, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  DEBOUNCE_MS,
  DISSOLVE_BLUR,
  DISSOLVE_MS,
  type GhostPose,
  JUMP_BACK,
  JUMP_MS,
  KEYCAP_MS,
  SWEEP_BAND,
  SWEEP_MS,
  SWIPE_ACCEPT,
  SWIPE_DIST,
  WORD_BLUR,
  WORD_GAP,
  WORD_MS,
  clamp01,
  easeOutCubic,
} from './ghostCompleteMotion';
import { EMPTY_LAYOUT, type GhostLayout, KEYCAP_W, MAX_WORDS, cleanText, makeGhostLayout } from './ghostLayout';

/*
 GhostField — a text field that offers the rest of the sentence.

 After a short pause in typing, a suggestion appears behind the caret in a muted tone, word by
 word out of a blur. Swiping right over the field, or tapping the keycap chip next to the
 suggestion, accepts it: ink runs through the grey text from left to right behind a coloured
 front, and the caret jumps to the end with a small overshoot. Typing on dissolves it; typing
 the suggested letters simply walks into it.

 The field draws its own text (one Skia canvas) over an invisible, real TextInput, so typed
 text and suggestion are one paragraph and accepting moves nothing.

   const field = useGhostComplete({ font, width, lineHeight: 25, suggest: (text) => myModel(text) });
   <GhostField field={field} pose={field.pose} layout={field.layout} font={font}
     width={width} height={150} lineHeight={25} placeholder="Write your message" multiline />

 `pose` and `layout` are what is drawn; a demo may pass its own (a function of a clock) and hand
 over to `field.pose` / `field.layout` when the person touches the field.
*/

export const GHOST_COLORS = {
  ink: '#0E121A',
  ghost: '#B7BCC8',
  hint: '#A3A9B6',
  accent: '#D9472B',
  front: '#E0714F',
};

/** Room around the text so a blurred word is not clipped by the canvas. */
const PAD = 10;
const CARET_W = 2;
const KEYCAP_H = 26;
const WORD_SLOTS = Array.from({ length: MAX_WORDS }, (_, i) => i);
const EASE_OUT = Easing.out(Easing.cubic);
const EASE_IN_OUT = Easing.inOut(Easing.cubic);

function lightTap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

// ---- Real input ---------------------------------------------------------------------------------------

type Options = {
  font: SkFont;
  width: number;
  lineHeight: number;
  /** The rest of the sentence for this text, or null. Called after a pause in typing. */
  suggest?: (text: string) => string | null;
  multiline?: boolean;
  /** Keep a line free for the keycap chip under a suggestion that fills its line. */
  keycap?: boolean;
  /** Reduced motion: no blur-in, no sweep, no jump. */
  still?: boolean;
  onChange?: (text: string) => void;
};

export type GhostFieldController = ReturnType<typeof useGhostComplete>;

function kickCaret(caret: SharedValue<number>, on: boolean, still: boolean) {
  'worklet';
  cancelAnimation(caret);
  if (!on) {
    caret.set(withTiming(0, { duration: 100 }));
    return;
  }
  caret.set(1);
  if (!still) caret.set(withRepeat(withSequence(withDelay(500, withTiming(0, { duration: 140 })), withDelay(360, withTiming(1, { duration: 140 }))), -1));
}

/** What a person's typing does to a GhostField: the state behind `pose` and `layout`. */
export function useGhostComplete({ font, width, lineHeight, suggest, multiline = false, keycap = true, still = false, onChange }: Options) {
  const [value, setValue] = useState('');
  const typed = useRef('');
  const ghost = useRef('');
  const focused = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const layout = useSharedValue<GhostLayout>(EMPTY_LAYOUT);
  const n = useSharedValue(0);
  const run = useSharedValue(-1);
  const appear = useSharedValue(0);
  const fade = useSharedValue(1);
  const sweep = useSharedValue(0);
  const caretFrom = useSharedValue(0);
  const caretTo = useSharedValue(0);
  const caretMix = useSharedValue(1);
  const caret = useSharedValue(0);
  const keycapPop = useSharedValue(0);
  const keycapPress = useSharedValue(0);
  const ink = useSharedValue(1);

  const pose = useDerivedValue<GhostPose>(() => {
    const count = n.get();
    const opacity = ink.get();
    return {
      n: count,
      run: run.get(),
      appear: appear.get(),
      fade: fade.get(),
      sweep: sweep.get(),
      caretFrom: caretFrom.get(),
      caretTo: caretTo.get(),
      caretMix: caretMix.get(),
      caret: caret.get(),
      keycap: keycapPop.get(),
      keycapPress: keycapPress.get(),
      ink: opacity,
      hint: count === 0 ? 1 : 1 - opacity,
    };
  });

  const stopTimer = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  }, []);
  useEffect(() => stopTimer, [stopTimer]);

  /** Shows `text` as typed, with `rest` behind it as a suggestion that has not arrived yet. */
  const lay = useCallback(
    (text: string, rest: string) => {
      const ranges: [number, number][] = rest ? [[text.length, text.length + rest.length]] : [];
      const next = makeGhostLayout(font, text + rest, ranges, width, lineHeight, keycap);
      layout.set(next);
      n.set(text.length);
      caretFrom.set(text.length);
      caretTo.set(text.length);
      caretMix.set(1);
      sweep.set(0);
      return next;
    },
    [caretFrom, caretMix, caretTo, font, keycap, layout, lineHeight, n, sweep, width],
  );

  /** Puts a suggestion behind the caret. `quiet` skips the blur-in and the keycap (a picked chip). */
  const offer = useCallback(
    (rest: string, quiet = false) => {
      const text = typed.current;
      const clean = cleanText(rest, multiline);
      if (!clean) return;
      ghost.current = clean;
      const next = lay(text, clean);
      run.set(0);
      fade.set(1);
      if (quiet || still) {
        appear.set(9);
      } else {
        appear.set(0);
        appear.set(withTiming(3, { duration: 3000, easing: Easing.linear }));
      }
      const words = next.runs[0]?.words.length ?? 1;
      keycapPop.set(quiet ? 0 : withDelay(still ? 0 : words * WORD_GAP * 1000 + 120, withTiming(1, { duration: still ? 0 : KEYCAP_MS, easing: Easing.out(Easing.back(1.6)) })));
    },
    [appear, fade, keycapPop, lay, multiline, run, still],
  );

  const propose = useCallback(() => {
    timer.current = null;
    if (!suggest || ghost.current) return;
    const rest = suggest(typed.current);
    if (rest) offer(rest);
  }, [offer, suggest]);

  const arm = useCallback(
    (delay = DEBOUNCE_MS) => {
      stopTimer();
      if (suggest) timer.current = setTimeout(propose, delay);
    },
    [propose, stopTimer, suggest],
  );

  const onChangeText = useCallback(
    (raw: string) => {
      const v = cleanText(raw, multiline);
      const prev = typed.current;
      const rest = ghost.current;
      typed.current = v;
      setValue(v);
      onChange?.(v);
      stopTimer();
      if (focused.current) kickCaret(caret, true, still);

      const added = v.length > prev.length && v.startsWith(prev) ? v.slice(prev.length) : '';
      if (rest && added && rest.startsWith(added)) {
        // Typing the suggested letters: the layout already has them, the ink just advances.
        ghost.current = rest.slice(added.length);
        n.set(v.length);
        caretFrom.set(v.length);
        caretTo.set(v.length);
        caretMix.set(1);
        if (!ghost.current) {
          run.set(-1);
          keycapPop.set(0);
          arm();
        }
        return;
      }
      if (rest) {
        // Anything else rejects it: it dissolves where the text now puts it.
        ghost.current = '';
        lay(v, rest);
        fade.set(withTiming(0, { duration: still ? 0 : DISSOLVE_MS }));
        keycapPop.set(withTiming(0, { duration: 100 }));
      } else {
        lay(v, '');
        run.set(-1);
      }
      arm();
    },
    [arm, caret, caretFrom, caretMix, caretTo, fade, keycapPop, lay, multiline, n, onChange, run, still, stopTimer],
  );

  /**
   Takes the suggestion. `velocity` (pt/s) is the swipe's, and shortens the sweep; `fresh` says the
   suggestion was offered in this same call, so the sweep starts from nothing.
  */
  const accept = useCallback(
    (velocity = 0, fresh = false) => {
      const rest = ghost.current;
      if (!rest) return;
      stopTimer();
      const from = typed.current.length;
      const full = typed.current + rest;
      typed.current = full;
      ghost.current = '';
      setValue(full);
      onChange?.(full);
      // The accepted text stays a fully swept suggestion until the next keystroke lays it out as ink.
      const left = fresh ? 1 : 1 - clamp01(sweep.get());
      const duration = still ? 0 : Math.max(160, (SWEEP_MS * left) / (1 + Math.max(0, velocity) / 1200));
      sweep.set(withTiming(1, { duration, easing: EASE_IN_OUT }));
      caretFrom.set(from);
      caretTo.set(full.length);
      if (still) {
        caretMix.set(1);
      } else {
        caretMix.set(0);
        caretMix.set(withTiming(1, { duration: JUMP_MS, easing: Easing.out(Easing.back(JUMP_BACK)) }));
      }
      keycapPop.set(withTiming(0, { duration: 140 }));
      if (focused.current) kickCaret(caret, true, still);
      lightTap();
      arm(duration + DEBOUNCE_MS);
    },
    [arm, caret, caretFrom, caretMix, caretTo, keycapPop, onChange, still, stopTimer, sweep],
  );

  /** Replaces the text from outside (a demo handing over, or a reset with ''). */
  const adopt = useCallback(
    (text: string, thenSuggest = true) => {
      stopTimer();
      const v = cleanText(text, multiline);
      typed.current = v;
      ghost.current = '';
      setValue(v);
      lay(v, '');
      run.set(-1);
      fade.set(1);
      keycapPop.set(0);
      ink.set(1);
      if (thenSuggest && v) arm();
    },
    [arm, fade, ink, keycapPop, lay, multiline, run, stopTimer],
  );

  const onFocus = useCallback(() => {
    focused.current = true;
    kickCaret(caret, true, still);
  }, [caret, still]);
  const onBlur = useCallback(() => {
    focused.current = false;
    kickCaret(caret, false, still);
  }, [caret, still]);

  return useMemo(
    () => ({ value, pose, layout, ink, run, fade, sweep, keycapPress, onChangeText, onFocus, onBlur, accept, adopt, offer, text: typed, pending: ghost }),
    [accept, adopt, fade, ink, keycapPress, layout, offer, onBlur, onChangeText, onFocus, pose, run, sweep, value],
  );
}

// ---- The field -------------------------------------------------------------------------------------------

type GhostFieldProps = Omit<TextInputProps, 'style' | 'value' | 'onChangeText' | 'multiline' | 'placeholder'> & {
  /** What is drawn. Usually `field.pose` and `field.layout`. */
  pose: SharedValue<GhostPose>;
  layout: SharedValue<GhostLayout>;
  /** The real input behind it. */
  field: GhostFieldController;
  font: SkFont;
  width: number;
  height: number;
  lineHeight: number;
  placeholder: string;
  multiline?: boolean;
  /** Show the keycap chip next to a suggestion. */
  keycap?: boolean;
  /** Colour of a suggestion before the ink reaches it; transparent hides it until then. */
  ghostColor?: string;
  /** Called on any touch of the field, before anything else (a demo hands over here). */
  onTouch?: () => void;
};

export function GhostField({
  pose,
  layout,
  field,
  font,
  width,
  height,
  lineHeight,
  placeholder,
  multiline = false,
  keycap = true,
  ghostColor = GHOST_COLORS.ghost,
  onTouch,
  onFocus,
  onBlur,
  ...input
}: GhostFieldProps) {
  const { accept, run, fade, sweep, keycapPress, layout: ownLayout } = field;
  const metrics = useMemo(() => {
    const m = font.getMetrics();
    return { caretH: Math.min(lineHeight, m.descent - m.ascent + 2) };
  }, [font, lineHeight]);
  const sweepColors = useMemo(() => [GHOST_COLORS.ink, GHOST_COLORS.accent, GHOST_COLORS.front, ghostColor], [ghostColor]);

  // ---- Text ---------------------------------------------------------------------------------------------
  const inkGlyphs = useDerivedValue(() => {
    const L = layout.get();
    const count = Math.min(pose.get().n, L.ids.length);
    const out: { id: number; pos: { x: number; y: number } }[] = [];
    for (let i = 0; i < count; i += 1) out.push({ id: L.ids[i], pos: { x: L.xs[i], y: L.ys[i] } });
    return out;
  });
  const inkOpacity = useDerivedValue(() => pose.get().ink);
  const caretX = useDerivedValue(() => {
    const p = pose.get();
    const L = layout.get();
    const last = L.cx.length - 1;
    const a = L.cx[Math.min(last, Math.max(0, p.caretFrom))];
    const b = L.cx[Math.min(last, Math.max(0, p.caretTo))];
    return a + (b - a) * p.caretMix - CARET_W / 2;
  });
  const caretY = useDerivedValue(() => {
    const p = pose.get();
    const L = layout.get();
    const last = L.cy.length - 1;
    const a = L.cy[Math.min(last, Math.max(0, p.caretFrom))];
    const b = L.cy[Math.min(last, Math.max(0, p.caretTo))];
    return a + (b - a) * clamp01(p.caretMix) + (lineHeight - metrics.caretH) / 2;
  });
  const caretOpacity = useDerivedValue(() => {
    const p = pose.get();
    return p.caret * p.ink;
  });

  // ---- Views ----------------------------------------------------------------------------------------------
  const hintStyle = useAnimatedStyle(() => ({ opacity: pose.get().hint }));
  const keycapStyle = useAnimatedStyle(() => {
    const p = pose.get();
    const L = layout.get();
    const r = p.run >= 0 && p.run < L.runs.length ? L.runs[p.run] : null;
    const shown = keycap && r !== null && p.keycap > 0.01;
    return {
      opacity: shown ? clamp01(p.keycap * 2) * p.fade * p.ink : 0,
      transform: [
        { translateX: shown && r ? r.chipX : -4000 },
        { translateY: (r ? r.chipY : 0) + (lineHeight - KEYCAP_H) / 2 },
        { scale: 0.7 + 0.3 * p.keycap },
      ],
    };
  });
  const keycapFaceStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 1.5 * pose.get().keycapPress }] }));
  const keycapShadowStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.7 * pose.get().keycapPress }));

  // ---- Swipe right to accept --------------------------------------------------------------------------------
  const live = useSharedValue(0);
  const reach = useSharedValue(SWIPE_DIST);
  const touched = useCallback(() => onTouch?.(), [onTouch]);
  const pan = Gesture.Pan()
    .activeOffsetX(12)
    .failOffsetY([-14, 14])
    .onBegin(() => {
      scheduleOnRN(touched);
    })
    .onStart(() => {
      const L = ownLayout.get();
      const on = run.get() === 0 && L.runs.length > 0 && fade.get() > 0.5 && sweep.get() < 0.02;
      live.set(on ? 1 : 0);
      if (on) reach.set(Math.max(60, Math.min(SWIPE_DIST, L.runs[0].length)));
    })
    .onUpdate((e) => {
      if (live.get() === 0) return;
      sweep.set(Math.min(0.94, Math.max(0, e.translationX / reach.get())));
    })
    .onEnd((e) => {
      if (live.get() === 0) return;
      live.set(0);
      if (sweep.get() > SWIPE_ACCEPT || e.velocityX > 600) scheduleOnRN(accept, e.velocityX);
      else sweep.set(withSpring(0, { velocity: -e.velocityX / reach.get(), damping: 22, stiffness: 260 }));
    })
    .onFinalize(() => {
      if (live.get() === 1) {
        live.set(0);
        sweep.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
      }
    });

  const press = useCallback(
    (down: boolean) => {
      keycapPress.set(withTiming(down ? 1 : 0, { duration: down ? 60 : 140 }));
    },
    [keycapPress],
  );

  return (
    <GestureDetector gesture={pan}>
      <View style={{ width, height }}>
        <Animated.Text style={[styles.hint, { fontSize: font.getSize(), lineHeight }, hintStyle]} numberOfLines={1} pointerEvents="none">
          {placeholder}
        </Animated.Text>

        <Canvas style={[styles.canvas, { width: width + PAD * 2, height: height + PAD * 2 }]} pointerEvents="none">
          <Group transform={CANVAS_SHIFT}>
            <Group opacity={inkOpacity}>
              <Glyphs font={font} glyphs={inkGlyphs} x={0} y={0} color={GHOST_COLORS.ink} />
              {WORD_SLOTS.map((slot) => (
                <GhostWord key={slot} slot={slot} pose={pose} layout={layout} font={font} colors={sweepColors} />
              ))}
            </Group>
            <RoundedRect x={caretX} y={caretY} width={CARET_W} height={metrics.caretH} r={1} color={GHOST_COLORS.accent} opacity={caretOpacity} />
          </Group>
        </Canvas>

        <TextInput
          {...input}
          value={field.value}
          onChangeText={field.onChangeText}
          onFocus={(e) => {
            onTouch?.();
            field.onFocus();
            onFocus?.(e);
          }}
          onBlur={(e) => {
            field.onBlur();
            onBlur?.(e);
          }}
          multiline={multiline}
          scrollEnabled={false}
          caretHidden
          contextMenuHidden
          autoCorrect={false}
          spellCheck={false}
          selectionColor="transparent"
          accessibilityLabel={placeholder}
          style={[styles.input, { width, height, fontSize: font.getSize(), lineHeight }]}
        />

        {keycap ? (
          <Animated.View style={[styles.keycap, keycapStyle]}>
            <Pressable
              onPress={() => {
                onTouch?.();
                accept();
              }}
              onPressIn={() => press(true)}
              onPressOut={() => press(false)}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel="Accept suggestion"
            >
              <Animated.View style={[styles.keycapShadow, keycapShadowStyle]} />
              <Animated.View style={[styles.keycapFace, keycapFaceStyle]}>
                <Text style={styles.keycapText}>Tab</Text>
                <Text style={styles.keycapArrow}>→</Text>
              </Animated.View>
            </Pressable>
          </Animated.View>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const CANVAS_SHIFT = [{ translateX: PAD }, { translateY: PAD }];

type WordProps = {
  slot: number;
  pose: SharedValue<GhostPose>;
  layout: SharedValue<GhostLayout>;
  font: SkFont;
  colors: string[];
};

/**
 One word of the suggestion. It arrives out of a blur, `slot` × WORD_GAP after the first, and is
 painted with a gradient whose front is the ink sweep: ink behind it, the muted tone ahead.
*/
function GhostWord({ slot, pose, layout, font, colors }: WordProps) {
  const glyphs = useDerivedValue(() => {
    const p = pose.get();
    const L = layout.get();
    const out: { id: number; pos: { x: number; y: number } }[] = [];
    if (p.run < 0 || p.run >= L.runs.length) return out;
    const word = L.runs[p.run].words[slot];
    if (!word) return out;
    for (let i = Math.max(word.a, p.n); i < word.b; i += 1) out.push({ id: L.ids[i], pos: { x: L.xs[i], y: L.ys[i] } });
    return out;
  });
  const opacity = useDerivedValue(() => {
    const p = pose.get();
    return easeOutCubic(clamp01((p.appear - slot * WORD_GAP) / (WORD_MS / 1000))) * p.fade;
  });
  const blur = useDerivedValue(() => {
    const p = pose.get();
    const arrived = easeOutCubic(clamp01((p.appear - slot * WORD_GAP) / (WORD_MS / 1000)));
    return Math.max(0.01, WORD_BLUR * (1 - arrived) + DISSOLVE_BLUR * (1 - p.fade));
  });
  /** x of the ink front on this word's line. */
  const front = useDerivedValue(() => {
    const p = pose.get();
    const L = layout.get();
    if (p.run < 0 || p.run >= L.runs.length) return -SWEEP_BAND;
    const r = L.runs[p.run];
    const word = r.words[slot];
    if (!word) return -SWEEP_BAND;
    return word.x0 + p.sweep * (r.length + SWEEP_BAND) - word.off;
  });
  const start = useDerivedValue(() => ({ x: front.get() - SWEEP_BAND, y: 0 }));
  const end = useDerivedValue(() => ({ x: front.get(), y: 0 }));

  return (
    <Glyphs font={font} glyphs={glyphs} x={0} y={0} opacity={opacity}>
      <LinearGradient start={start} end={end} colors={colors} positions={SWEEP_STOPS} />
      <BlurMask blur={blur} style="normal" />
    </Glyphs>
  );
}

const SWEEP_STOPS = [0, 0.3, 0.72, 1];

const styles = StyleSheet.create({
  hint: { position: 'absolute', left: 0, top: 0, color: GHOST_COLORS.hint, fontWeight: '400' },
  canvas: { position: 'absolute', left: -PAD, top: -PAD },
  input: { position: 'absolute', left: 0, top: 0, padding: 0, paddingTop: 0, color: 'transparent', fontWeight: '400', textAlignVertical: 'top' },
  keycap: { position: 'absolute', left: 0, top: 0, width: KEYCAP_W, height: KEYCAP_H },
  keycapShadow: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 2,
    height: KEYCAP_H,
    borderRadius: 8,
    backgroundColor: '#C9CDD6',
    boxShadow: '0 4px 10px rgba(21,26,36,0.12)',
  },
  keycapFace: {
    width: KEYCAP_W,
    height: KEYCAP_H,
    borderRadius: 8,
    backgroundColor: '#FFFFFF',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#D5D8DF',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,1), inset 0 -1px 0 rgba(21,26,36,0.05)',
  },
  keycapText: { fontSize: 14, fontWeight: '600', color: '#5B6170', letterSpacing: 0.1 },
  keycapArrow: { fontSize: 14, fontWeight: '600', color: GHOST_COLORS.accent, marginTop: -1 },
});
