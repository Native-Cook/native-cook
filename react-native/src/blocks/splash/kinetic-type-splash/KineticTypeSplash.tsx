import {
  BlurMask,
  Canvas,
  Group,
  Image as SkiaImage,
  LinearGradient,
  matchFont,
  Rect,
  type SkFont,
  type SkImage,
  Skia,
  Text as SkiaText,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Types the lines, rolls the last word, reveals the photo through it and starts over. */
  autoPlay?: boolean;
  /** The two lines that stay, and the words the last line rolls through (the last one opens). */
  lines?: [string, string];
  words?: string[];
  name?: string;
  title?: string;
  caption?: string;
  cta?: string;
  background?: string;
  type?: string;
  accent?: string;
  onGetStarted?: () => void;
};

const LINES: [string, string] = ['FIND', 'YOUR'];
const WORDS = ['CROWD', 'SCENE', 'GLOW'];
const NAME = 'NIGHTJAR';
const TITLE = 'The city after dark,\none tap away.';
const CAPTION = 'Shows, late kitchens and rooftops near you, picked fresh every night.';
const CTA = 'Get started';
const BACKGROUND = '#0a0a0b';
const TYPE = '#f3ede3';
const ACCENT = '#ff3d7f';

const PHOTO = 'https://images.unsplash.com/photo-1581300740943-cfa5f847db2c?auto=format&fit=crop&w=1200&q=80';

// Huge condensed capitals, sized so the widest word fills the width between MARGIN and the edge.
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next Condensed' : 'sans-serif-condensed';
const MARGIN = 22;
const MAX_SIZE = 168;
// Each line is a window as tall as a capital plus PAD of its size above and below; letters rise
// into it from below the baseline. Lines sit LEADING of a capital apart.
const PAD = 0.12;
// The rolling word glows like a neon tube: a copy blurred by GLOW_BLUR pt under it.
const GLOW_BLUR = 9;
const LEADING = 1.2;
// Letters: each rises over LETTER_TIME s, LETTER_LAG s after the one before; lines LINE_LAG apart.
const LETTER_TIME = 0.55;
const LETTER_LAG = 0.045;
const LINE_LAG = 0.38;
const INTRO_END = 2 * LINE_LAG + 5 * LETTER_LAG + LETTER_TIME;
const OUT_END = 4 * LETTER_LAG + LETTER_TIME;
const EASE_OUT = Easing.bezierFn(0.16, 1, 0.3, 1);
const EASE_IN_OUT = Easing.bezierFn(0.76, 0, 0.24, 1);

// The rolling word's columns: the first follows the reel on a lively spring, each next one follows
// its neighbour on a stiffer one, so the letters land one after another like a slot machine.
const LEAD_K = 230;
const LEAD_C = 21;
const CHAIN_K = 420;
const CHAIN_C = 37;
// One column per letter; letters past the last column follow it.
const COLUMNS = 8;
// A flick throws the reel this many seconds ahead before it snaps to a word.
const THROW = 0.12;
// The columns' springs are stepped at most this long (s) at a time, to stay stable in slow frames.
const MAX_STEP = 0.025;

// Reveal: the photo fills the last word, then the word grows from a point in its first stem
// until the photo is the whole screen.
const FILL_MS = 500;
const ZOOM_MS = 1150;
const ZOOM_MAX = 70;
const FINAL_MS = 850;
const EXIT_MS = 650;

// autoPlay script (s).
const LOOP = 9.6;
const INTRO_AT = 0.25;
const ROLL_AT = [2.2, 3.1];
const REVEAL_AT = 3.8;
const PRESS_AT = 8.3;
const EXIT_AT = 8.45;
const USER_HOLD = 4;

function seg(v: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (v - a) / (b - a)));
}

type Word = { text: string; letters: { char: string; x: number }[] };

/** Fits the type to the screen and lays out every word letter by letter. */
function useType(width: number, lines: string[], words: string[]) {
  return useMemo(() => {
    const probe = matchFont({ fontFamily: FAMILY, fontSize: 100, fontWeight: '900' });
    const widthOf = (text: string) => probe.getGlyphWidths(probe.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
    const widest = Math.max(...[...lines, ...words].map(widthOf));
    const size = Math.min(MAX_SIZE, (100 * (width - MARGIN * 2)) / widest);
    const font = matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: '900' });
    const cap = font.measureText('H').height;
    const layout = (text: string): Word => {
      const widths = font.getGlyphWidths(font.getGlyphIDs(text));
      let x = MARGIN;
      return {
        text,
        letters: [...text].map((char, i) => {
          const at = x;
          x += widths[i] ?? 0;
          return { char, x: at };
        }),
      };
    };
    return { font, size, cap, lines: lines.map(layout), words: words.map(layout) };
  }, [width, lines, words]);
}

/**
 * Kinetic type for a splash. Huge condensed capitals rise line by line out of their own
 * baselines, letter after letter; the last line rolls through its words like a slot machine; the
 * last word fills with a photo, which then grows out of the letters into the whole screen, with a
 * welcome and a button over it. Tap to skip ahead, drag the last line to roll it yourself.
 */
export function KineticTypeSplash({
  autoPlay = false,
  lines = LINES,
  words = WORDS,
  name = NAME,
  title = TITLE,
  caption = CAPTION,
  cta = CTA,
  background = BACKGROUND,
  type = TYPE,
  accent = ACCENT,
  onGetStarted,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const W = size.width;
  const H = size.height;
  const photo = useImage(PHOTO);
  const typeSet = useType(W, lines, words);
  const { font, cap } = typeSet;
  const pad = typeSet.size * PAD;
  const windowH = cap + pad * 2;
  const pitch = cap * LEADING + pad;
  // Baselines of the three lines, the block centred a little above the middle.
  const blockTop = H * 0.45 - (pitch * 2 + cap) / 2;
  const baselines = [0, 1, 2].map((i) => blockTop + cap + i * pitch);
  const last = words.length - 1;
  // The point of the last word the reveal grows from: the middle of its first letter's stem.
  const focus = { x: MARGIN + cap * 0.12, y: baselines[2] - cap * 0.45 };
  const ctaTop = H - Math.max(insets.bottom, 16) - 8 - 56;

  // Seconds into the lines' entrance and exit, the reel (in words), the reveal's three steps,
  // and the way out.
  const intro = useSharedValue(0);
  const outro = useSharedValue(0);
  const reel = useSharedValue(0);
  const cols = useSharedValue<number[]>(new Array(COLUMNS).fill(0));
  const colVel = useSharedValue<number[]>(new Array(COLUMNS).fill(0));
  const fill = useSharedValue(0);
  const zoom = useSharedValue(0);
  const final = useSharedValue(0);
  const exit = useSharedValue(0);
  const press = useSharedValue(0);
  // 0 lines, 1 rolling, 2 revealing, 3 welcome, 4 leaving.
  const stage = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const started = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onGetStarted?.();
  };

  function startIntro(delay: number, fast: boolean) {
    'worklet';
    const left = Math.max(0, INTRO_END - intro.get());
    intro.set(withDelay(delay, withTiming(INTRO_END, { duration: fast ? 260 : left * 1000, easing: Easing.linear })));
  }

  function roll(to: number) {
    'worklet';
    if (stage.get() > 1) return;
    stage.set(1);
    reel.set(Math.max(0, Math.min(last, to)));
  }

  function reveal(delay: number) {
    'worklet';
    if (stage.get() > 1) return;
    stage.set(2);
    reel.set(last);
    fill.set(withDelay(delay, withTiming(1, { duration: FILL_MS, easing: Easing.linear })));
    outro.set(withDelay(delay, withTiming(OUT_END, { duration: OUT_END * 1000, easing: Easing.linear })));
    zoom.set(
      withDelay(
        delay + FILL_MS + 200,
        withTiming(1, { duration: reducedMotion ? 500 : ZOOM_MS, easing: Easing.linear }, (done) => {
          if (!done) return;
          stage.set(3);
          final.set(withTiming(1, { duration: FINAL_MS, easing: Easing.linear }));
        }),
      ),
    );
  }

  function reset(done?: boolean) {
    'worklet';
    if (!done) return;
    intro.set(0);
    outro.set(0);
    reel.set(0);
    cols.set(new Array(COLUMNS).fill(0));
    colVel.set(new Array(COLUMNS).fill(0));
    fill.set(0);
    zoom.set(0);
    final.set(0);
    exit.set(0);
    stage.set(0);
    if (!looping.get() || hold.get() > 0) startIntro(400, false);
  }

  function leave(user: boolean) {
    'worklet';
    if (stage.get() !== 3 || final.get() < 0.9) return;
    stage.set(4);
    if (user) scheduleOnRN(started);
    exit.set(withTiming(1, { duration: reducedMotion ? 350 : EXIT_MS, easing: Easing.bezier(0.76, 0, 0.24, 1) }, reset));
  }

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
    if (!auto) startIntro(INTRO_AT * 1000, false);
    // startIntro is a worklet over shared values; it only needs to run when the mode changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto, clock, hold, looping]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    time.set(time.get() + dt);

    // The reel's columns: the first chases the reel (or sits under the finger), the rest chase it.
    const c = cols.get();
    const v = colVel.get();
    const target = reel.get();
    const steps = Math.max(1, Math.ceil(dt / MAX_STEP));
    const h = dt / steps;
    for (let j = 0; j < c.length; j += 1) {
      if (reducedMotion) {
        c[j] += (target - c[j]) * Math.min(1, dt * 14);
        v[j] = 0;
        continue;
      }
      if (j === 0 && dragging.get()) {
        v[0] = (target - c[0]) / dt;
        c[0] = target;
        continue;
      }
      const lead = j === 0 ? target : c[j - 1];
      const k = j === 0 ? LEAD_K : CHAIN_K;
      const damp = j === 0 ? LEAD_C : CHAIN_C;
      // The stiff chain blows up when stepped in long frames, so a slow frame is split in steps.
      for (let n = 0; n < steps; n += 1) {
        v[j] += (k * (lead - c[j]) - damp * v[j]) * h;
        c[j] += v[j] * h;
      }
      if (!Number.isFinite(c[j]) || !Number.isFinite(v[j])) {
        c[j] = target;
        v[j] = 0;
      }
    }
    cols.set([...c]);
    colVel.set([...v]);

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        const s = stage.get();
        clock.set(s <= 1 ? (intro.get() > 0 ? INTRO_END + INTRO_AT - 0.2 : 0) : s <= 3 ? PRESS_AT - 0.5 : EXIT_AT + 0.01);
      }
      return;
    }
    if (!looping.get()) return;
    const t0 = clock.get();
    const t = (t0 + dt) % LOOP;
    clock.set(t);
    const passed = (at: number) => (t0 < at && t >= at) || (t < t0 && (at > t0 || at <= t));
    if (passed(INTRO_AT) && stage.get() === 0 && intro.get() === 0) startIntro(0, false);
    for (let i = 0; i < ROLL_AT.length; i += 1) {
      if (passed(ROLL_AT[i]) && stage.get() <= 1 && reel.get() < last) roll(Math.round(reel.get()) + 1);
    }
    if (passed(REVEAL_AT)) reveal(0);
    if (passed(PRESS_AT) && stage.get() === 3) {
      press.set(withSequence(withTiming(1, { duration: 110 }), withTiming(0, { duration: 200 })));
    }
    if (passed(EXIT_AT)) leave(false);
  });

  // A tick each time a word reaches the window under the finger.
  useAnimatedReaction(
    () => Math.round(cols.get()[0]),
    (now, before) => {
      if (before !== null && now !== before && dragging.get()) scheduleOnRN(tick);
    },
  );

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
  }

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      const s = stage.get();
      if (s <= 1 && intro.get() < INTRO_END - 0.05) {
        startIntro(0, true);
        scheduleOnRN(tick);
      } else if (s <= 1 && reel.get() < last) {
        roll(Math.round(reel.get()) + 1);
        scheduleOnRN(tick);
      } else if (s <= 1) {
        reveal(0);
        scheduleOnRN(tick);
      } else if (s === 3 && event.y > ctaTop - 8 && event.y < ctaTop + 64) {
        press.set(withSequence(withTiming(1, { duration: 90 }), withTiming(0, { duration: 180 })));
        leave(true);
      }
    });

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .onStart(() => {
      touched();
      if (stage.get() > 1 || intro.get() < INTRO_END - 0.3 || reducedMotion) return;
      stage.set(1);
      dragFrom.set(cols.get()[0]);
      dragging.set(true);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      const raw = dragFrom.get() - event.translationY / windowH;
      // Past the first or last word the reel gives less and less.
      const over = raw < 0 ? raw : raw > last ? raw - last : 0;
      reel.set(raw - over + over / (1 + Math.abs(over) * 2.5));
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      dragging.set(false);
      const speed = -event.velocityY / windowH;
      colVel.modify((a) => {
        'worklet';
        a[0] = speed;
        return a;
      });
      const to = Math.max(0, Math.min(last, Math.round(reel.get() + speed * THROW)));
      reel.set(to);
      if (to === last) reveal(700);
    })
    .onFinalize(() => {
      dragging.set(false);
    });

  // The last word grows about its first stem until the photo fills the screen.
  const zoomTransform = useDerivedValue(() => {
    const z = reducedMotion ? 1 : Math.pow(ZOOM_MAX, EASE_IN_OUT(zoom.get()));
    return [{ translateX: focus.x }, { translateY: focus.y }, { scale: z }, { translateX: -focus.x }, { translateY: -focus.y }];
  });
  const photoInType = useDerivedValue(() => fill.get() * (reducedMotion ? 1 - zoom.get() : 1));
  const photoFull = useDerivedValue(() => (reducedMotion ? zoom.get() : seg(zoom.get(), 0.9, 1)));
  // The photo drifts in a little as the welcome settles.
  const photoTransform = useDerivedValue(() => [{ scale: 1.08 - 0.08 * EASE_OUT(seg(zoom.get(), 0.3, 1) * 0.6 + final.get() * 0.4) }]);
  const shade = useDerivedValue(() => final.get());
  const reelOpacity = useDerivedValue(() => 1 - fill.get());
  const haloOpacity = useDerivedValue(() => 0.7 * fill.get() * (1 - seg(zoom.get(), 0, 0.25)));
  // The way out: a panel of the background rises over everything (fades in, under reduced motion).
  const exitY = useDerivedValue(() => (reducedMotion ? (exit.get() > 0 ? 0 : H) : H * (1 - EASE_IN_OUT(exit.get()))));
  const exitOpacity = useDerivedValue(() => (reducedMotion ? exit.get() : 1));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    if (width !== W || height !== H) setSize({ width, height });
  };

  const lastWord = typeSet.words[last];
  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: background }]}
        onLayout={onLayout}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${lines.join(' ')} ${words[last]}. ${title.replace('\n', ' ')} ${cta}`}
      >
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {typeSet.lines.map((word, line) => (
            <Group key={line} clip={Skia.XYWHRect(0, baselines[line] - cap - pad, W, windowH)}>
              {word.letters.map((letter, index) => (
                <StayLetter
                  key={index}
                  char={letter.char}
                  x={letter.x}
                  baseline={baselines[line]}
                  line={line}
                  index={index}
                  rise={windowH}
                  intro={intro}
                  outro={outro}
                  font={font}
                  color={type}
                  reducedMotion={reducedMotion}
                />
              ))}
            </Group>
          ))}
          <Group clip={Skia.XYWHRect(0, baselines[2] - cap - pad, W, windowH)} opacity={reelOpacity}>
            {typeSet.words.map((word, wordIndex) =>
              word.letters.map((letter, index) => (
                <ReelLetter
                  key={`${wordIndex}-${index}`}
                  char={letter.char}
                  x={letter.x}
                  baseline={baselines[2]}
                  word={wordIndex}
                  index={index}
                  rise={windowH}
                  intro={intro}
                  cols={cols}
                  font={font}
                  color={accent}
                  reducedMotion={reducedMotion}
                />
              )),
            )}
          </Group>
          {/* The halo stays under the photo-filled word until it starts to grow. */}
          <Group opacity={haloOpacity}>
            {lastWord.letters.map((letter, index) => (
              <SkiaText key={index} x={letter.x} y={baselines[2]} text={letter.char} font={font} color={accent}>
                <BlurMask blur={GLOW_BLUR * 1.4} style="normal" />
              </SkiaText>
            ))}
          </Group>
          {/* The last word as a window onto the photo, growing until it is the screen. */}
          <Group layer opacity={photoInType}>
            <Group transform={zoomTransform}>
              {lastWord.letters.map((letter, index) => (
                <SkiaText key={index} x={letter.x} y={baselines[2]} text={letter.char} font={font} color="#ffffff" />
              ))}
            </Group>
            <Photo image={photo} W={W} H={H} transform={photoTransform} blendMode="srcIn" accent={accent} />
          </Group>
          <Group opacity={photoFull}>
            <Photo image={photo} W={W} H={H} transform={photoTransform} accent={accent} />
          </Group>
          <Rect x={0} y={0} width={W} height={H} opacity={shade}>
            <LinearGradient
              start={vec(0, H * 0.4)}
              end={vec(0, H)}
              colors={['rgba(8,6,10,0)', 'rgba(8,6,10,0.72)', 'rgba(8,6,10,0.95)']}
              positions={[0, 0.42, 1]}
            />
          </Rect>
        </Canvas>

        <Welcome
          final={final}
          exit={exit}
          press={press}
          name={name}
          title={title}
          caption={caption}
          cta={cta}
          top={insets.top}
          ctaTop={ctaTop}
          copyBottom={H - ctaTop + 30}
          type={type}
          reducedMotion={reducedMotion}
        />

        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Rect x={0} y={exitY} width={W} height={H} color={background} opacity={exitOpacity} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

type PhotoProps = {
  image: SkImage | null;
  W: number;
  H: number;
  transform: SharedValue<{ scale: number }[]>;
  accent: string;
  blendMode?: 'srcIn';
};

/** The photo, screen-sized; a gradient in the accent stands in until it has loaded. */
function Photo({ image, W, H, transform, accent, blendMode }: PhotoProps) {
  if (!image) {
    return (
      <Rect x={0} y={0} width={W} height={H} blendMode={blendMode}>
        <LinearGradient start={vec(0, 0)} end={vec(W, H)} colors={[accent, '#5b2bff']} />
      </Rect>
    );
  }
  return (
    <Group transform={transform} origin={vec(W / 2, H / 2)} blendMode={blendMode}>
      <SkiaImage image={image} x={0} y={0} width={W} height={H} fit="cover" />
    </Group>
  );
}

type StayLetterProps = {
  char: string;
  x: number;
  baseline: number;
  line: number;
  index: number;
  rise: number;
  intro: SharedValue<number>;
  outro: SharedValue<number>;
  font: SkFont;
  color: string;
  reducedMotion: boolean;
};

/** A letter of a line that stays: it rises out of the baseline, and leaves upwards for the reveal. */
function StayLetter({ char, x, baseline, line, index, rise, intro, outro, font, color, reducedMotion }: StayLetterProps) {
  const enter = useDerivedValue(() => seg(intro.get(), line * LINE_LAG + index * LETTER_LAG, line * LINE_LAG + index * LETTER_LAG + LETTER_TIME));
  const away = useDerivedValue(() => seg(outro.get(), index * LETTER_LAG, index * LETTER_LAG + LETTER_TIME));
  const transform = useDerivedValue(() => [
    { translateY: reducedMotion ? 0 : (1 - EASE_OUT(enter.get())) * rise - EASE_IN_OUT(away.get()) * rise },
  ]);
  const opacity = useDerivedValue(() => (reducedMotion ? enter.get() * (1 - away.get()) : 1));
  return (
    <Group transform={transform} opacity={opacity}>
      <SkiaText x={x} y={baseline} text={char} font={font} color={color} />
    </Group>
  );
}

type ReelLetterProps = {
  char: string;
  x: number;
  baseline: number;
  word: number;
  index: number;
  rise: number;
  intro: SharedValue<number>;
  cols: SharedValue<number[]>;
  font: SkFont;
  color: string;
  reducedMotion: boolean;
};

/** A letter of a word on the reel, on its own column, so the word rolls letter after letter. */
function ReelLetter({ char, x, baseline, word, index, rise, intro, cols, font, color, reducedMotion }: ReelLetterProps) {
  const transform = useDerivedValue(() => {
    const from = 2 * LINE_LAG + index * LETTER_LAG;
    const enter = EASE_OUT(seg(intro.get(), from, from + LETTER_TIME));
    const offset = word - cols.get()[Math.min(index, COLUMNS - 1)];
    return [{ translateY: reducedMotion ? 0 : offset * rise + (1 - enter) * rise }];
  });
  const opacity = useDerivedValue(() => {
    const offset = Math.abs(word - cols.get()[Math.min(index, COLUMNS - 1)]);
    if (reducedMotion) {
      const from = 2 * LINE_LAG + index * LETTER_LAG;
      return Math.max(0, 1 - offset) * seg(intro.get(), from, from + LETTER_TIME);
    }
    return offset > 1.05 ? 0 : 1;
  });
  return (
    <Group transform={transform} opacity={opacity}>
      {/* A neon halo under the letter. */}
      <SkiaText x={x} y={baseline} text={char} font={font} color={color} opacity={0.45}>
        <BlurMask blur={GLOW_BLUR} style="normal" />
      </SkiaText>
      <SkiaText x={x} y={baseline} text={char} font={font} color={color} />
    </Group>
  );
}

type WelcomeProps = {
  final: SharedValue<number>;
  exit: SharedValue<number>;
  press: SharedValue<number>;
  name: string;
  title: string;
  caption: string;
  cta: string;
  top: number;
  ctaTop: number;
  copyBottom: number;
  type: string;
  reducedMotion: boolean;
};

/** A welcome element's style: it fades and rises in from `from` of the welcome's progress, and
 * fades out in the first 40% of the way out. `press` shrinks it a little. */
function riseIn(final: number, exit: number, from: number, distance: number, still: boolean, press: number) {
  'worklet';
  const p = EASE_OUT(seg(final, from, from + 0.6));
  return {
    opacity: seg(final, from, from + 0.4) * (1 - seg(exit, 0, 0.4)),
    transform: [{ translateY: still ? 0 : (1 - p) * distance }, { scale: 1 - press * 0.03 }],
  };
}

/** What sits over the photo once it fills the screen. */
function Welcome({ final, exit, press, name, title, caption, cta, top, ctaTop, copyBottom, type, reducedMotion }: WelcomeProps) {
  const nameStyle = useAnimatedStyle(() => riseIn(final.get(), exit.get(), 0, 10, reducedMotion, 0));
  const titleStyle = useAnimatedStyle(() => riseIn(final.get(), exit.get(), 0.1, 30, reducedMotion, 0));
  const captionStyle = useAnimatedStyle(() => riseIn(final.get(), exit.get(), 0.22, 24, reducedMotion, 0));
  const ctaStyle = useAnimatedStyle(() => riseIn(final.get(), exit.get(), 0.32, 50, reducedMotion, press.get()));
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.Text style={[styles.name, { top: top + 16, color: type }, nameStyle]}>{name}</Animated.Text>
      <View style={[styles.copy, { bottom: copyBottom }]}>
        <Animated.Text style={[styles.title, { color: type }, titleStyle]}>{title}</Animated.Text>
        <Animated.Text style={[styles.caption, { color: `${type}c0` }, captionStyle]}>{caption}</Animated.Text>
      </View>
      <Animated.View style={[styles.cta, { top: ctaTop, backgroundColor: type }, ctaStyle]}>
        <Text style={styles.ctaLabel}>{cta}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  name: {
    position: 'absolute',
    left: MARGIN,
    fontFamily: FAMILY,
    fontSize: 17,
    fontWeight: '800',
    letterSpacing: 3,
  },
  copy: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    gap: 12,
  },
  title: {
    fontFamily: FAMILY,
    fontSize: 38,
    lineHeight: 40,
    fontWeight: '800',
    letterSpacing: -0.3,
  },
  caption: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
    maxWidth: 320,
  },
  cta: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ctaLabel: {
    fontSize: 17,
    fontWeight: '700',
    color: '#0a0a0b',
  },
});
