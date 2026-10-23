import {
  Blur,
  Canvas,
  Circle,
  Group,
  ImageShader,
  LinearGradient,
  Paint,
  Points,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  Image as SkImage,
  Text as SkText,
  useFont,
  useImage,
  vec,
  type SkPoint,
  type Uniforms,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useMemo } from 'react';
import { AppState, Image, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type DerivedValue,
  Easing,
  interpolateColor,
  ReduceMotion,
  SensorType,
  type SharedValue,
  useAnimatedSensor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useCalmMotion } from './a11y';
import { Glyph, type IconName, iconBox } from './icons';
import {
  AMBIENT,
  CAM_SPRING,
  clockOf,
  CTA,
  EASE_IN_OUT,
  EASE_NIGHT_TAP,
  EASE_OUT,
  FAN,
  FAN_STAGGER,
  FANNED,
  FIRE,
  fireLight,
  fireOf,
  flareAt,
  flashAt,
  type Frame,
  frameAt,
  flight,
  hash3,
  hourOf,
  LED,
  LED_STILL,
  ledOf,
  LIKE,
  LOOP,
  NIGHT,
  rank,
  REDUCED,
  REST_NAME,
  restFrame,
  RESUME_AFTER,
  RETURN_MS,
  ROLL,
  SHOW_END,
  SLOT_COUNT,
  shellLeft,
  SLOTS,
  SPACE,
  SPARK,
  sparkLaunch,
  sparkOffset,
  SPOT,
  SPRINGS,
  type Spring,
  sunOf,
  TITLE,
  TOWER,
  VIEW,
} from './motion';
import { SCENE_EFFECT } from './scene';
import {
  ASCENT_HEADLINE,
  ASCENT_SF,
  backdrop,
  colors,
  copy,
  fonts,
  layout,
  night as NT,
  radius,
  REF_H,
  REF_INSET,
  REF_PT,
  REF_W,
  scene as SC,
  space,
  TOP_GROUP_MAX,
  type TextStyleSpec,
  type as T,
} from './theme';

type Props = {
  /** Plays the loop by itself; a touch takes over and hands back 2 s after the last one. */
  autoPlay?: boolean;
};

const PHOTO = require('./assets/city.jpg');
/** Spatial layers, baked once from a depth map of the photo (see the notes): nothing runs at app time. */
const FILL = require('./assets/fill.jpg');
const TOWER_MASK = require('./assets/tower.png');
const NEAR_MASK = require('./assets/near.png');
const BACK_DEPTH = require('./assets/backdepth.png');
const DEPTH = require('./assets/depth.png');
/** Grey maps of the night (round 2), the façade's half width per row, the LED font. */
const LIGHT_MAP = require('./assets/lights.png');
const KEY_MAP = require('./assets/key.png');
const BLOOM_MAP = require('./assets/bloom.png');
const BLOOM_KEY_MAP = require('./assets/bloomkey.png');
const FACADE = require('./assets/facade.png');
const GLYPH_ATLAS = require('./assets/glyphs.png');
const PHOTO_W = 850;
const PHOTO_H = 1850;
const GUESTS = [
  require('./assets/guest-1.jpg'),
  require('./assets/guest-2.jpg'),
  require('./assets/guest-3.jpg'),
  require('./assets/guest-4.jpg'),
  require('./assets/guest-5.jpg'),
  require('./assets/guest-6.jpg'),
  require('./assets/guest-7.jpg'),
];
/** The viewer, whose portrait climbs the tower: the guest at the top of the stack. */
const VIEWER = GUESTS[3];
const FONT_FILES = {
  [fonts.headline]: require('./assets/BricolageHeadline.ttf'),
};
const WORDMARK_FONT = require('./assets/ArchivoWordmark.ttf');
const HEADLINE_FONT = require('./assets/BricolageHeadline.ttf');

/** Mitchell cubic: the photo is drawn ×1.42, as smooth as UIImageView draws it. */
const SAMPLING = { B: 1 / 3, C: 1 / 3 } as const;

const rgba = (c: readonly number[], a: number) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;
/** A Gaussian falloff in 7 stops, 0 → 3 sigma. */
const GAUSS = [1, 0.882, 0.607, 0.325, 0.135, 0.044, 0];

/** Burst colours as sRGB 0..1 (shader light) and 0..255 (the light on the UI). */
const hex = (h: string) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const BURST_RGB = FIRE.bursts.map((b) => hex(b.color));
const BURST_LIGHT = BURST_RGB.map((c) => c.map((v) => v / 255));
const SPILL_RGB = SC.spillText.map((v) => Math.round(v * 255));

const EOUT = Easing.bezier(...EASE_OUT);
const EINOUT = Easing.bezier(...EASE_IN_OUT);
const ENIGHT = Easing.bezier(...EASE_NIGHT_TAP);
const NEVER = ReduceMotion.Never;
function ms(s: number) {
  'worklet';
  return Math.round(s * 1000);
}
/** Reanimated's spring with the same {duration, dampingRatio} the closed form uses. */
function rs(s: Spring) {
  'worklet';
  return { duration: Math.round(s.duration * 1000), dampingRatio: s.dampingRatio, reduceMotion: NEVER };
}
function clampTod(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 2 ? 2 : v;
}
/** Past `limit` the finger moves it less and less (rubber band). */
function rubber(v: number, limit: number) {
  'worklet';
  const a = Math.abs(v);
  if (a <= limit) return v;
  const over = a - limit;
  return Math.sign(v) * (limit + over / (1 + over / limit));
}

/** Haze over the sky: stretched value noise, drifting left, faded towards the top and the horizon. */
const MIST = Skia.RuntimeEffect.Make(`
uniform float drift;
uniform float amt;
uniform float3 tint;
float h21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), u.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int k = 0; k < 4; k++) {
    v += a * vnoise(p);
    p = p * 2.03 + float2(17.1, 9.2);
    a *= 0.5;
  }
  return v;
}
half4 main(float2 xy) {
  float2 p = float2(xy.x + drift, xy.y) / float2(560.0, 230.0);
  float n = fbm(p + float2(0.0, drift * 0.0004));
  float m = smoothstep(0.46, 0.78, n);
  float sky = smoothstep(1180.0, 820.0, xy.y) * smoothstep(200.0, 460.0, xy.y);
  float a = amt * m * sky;
  return half4(half3(tint) * a, a);
}
`);

/** Pipeline warm-up: frames, and an opacity no eye can see (the scene at rest equals the photo). */
const WARM_FRAMES = 4;
const WARM_OPACITY = 0.004;
/** Seconds after ignition at which every burst and shell is alive. */
const WARM_FIRE = 0.62;

/* Modes of the clock. */
const AUTO = 0;
const TOUCH = 1;
const RETURN = 2;

/* Tap targets. */
const HIT_NONE = 0;
const HIT_GUESTS = 1;
const HIT_LIKE = 2;
const HIT_VIEW = 3;
const HIT_SKY = 4;

function haptic(kind: 'light' | 'select' | 'show', delayMs = 0) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'show') {
    // The spire ignites when the portrait reaches the tip; the willow bursts with the tap.
    setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {}), delayMs + ms(LED.fire + FIRE.bursts[0].at));
    return;
  }
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function CityEventHero({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [fontsReady] = useFonts(FONT_FILES);
  const wordFont = useFont(WORDMARK_FONT, layout.wordmark.size);
  // Followed live: Reduce Motion picks the gentle variants and stops the loop; a screen reader stops the loop too.
  const { reduceMotion: reduced, screenReader } = useCalmMotion();
  const looping = autoPlay && !reduced && !screenReader;

  const U = width / REF_W; // ref px → dp
  const FS = width / REF_PT; // pt at 402 → dp
  const ty = (y: number) => (y < TOP_GROUP_MAX ? insets.top + (y - REF_INSET) * U : height - (REF_H - y) * U);
  const X = (x: number) => x * U;
  const titleFont = useFont(HEADLINE_FONT, TITLE.size * FS);
  const suffixFont = useFont(HEADLINE_FONT, TITLE.suffix * FS);

  /* ─────────────── animated state (one shared value per scalar of `Frame`) ─────────────── */
  const fan0 = useSharedValue(0);
  const fan1 = useSharedValue(0);
  const fan2 = useSharedValue(0);
  const fan3 = useSharedValue(0);
  const fan4 = useSharedValue(0);
  const fan5 = useSharedValue(0);
  const fan6 = useSharedValue(0);
  const spot0 = useSharedValue(0);
  const spot1 = useSharedValue(0);
  const spot2 = useSharedValue(0);
  const spot3 = useSharedValue(0);
  const spot4 = useSharedValue(0);
  const spot5 = useSharedValue(0);
  const spot6 = useSharedValue(0);
  const fans = [fan0, fan1, fan2, fan3, fan4, fan5, fan6];
  const spots = [spot0, spot1, spot2, spot3, spot4, spot5, spot6];
  const nameFrom = useSharedValue(REST_NAME);
  const nameTo = useSharedValue(REST_NAME);
  const nameP = useSharedValue(0);
  const like = useSharedValue(0);
  const pop = useSharedValue(1);
  const ring = useSharedValue(0);
  const count = useSharedValue(0);
  const press = useSharedValue(0);
  const chev = useSharedValue(0);
  const sweep = useSharedValue(0);
  const mist = useSharedValue(0);
  const drift = useSharedValue(0);
  const fade = useSharedValue(1); // reduced motion: guests dip to 0 while they change place
  const day = useSharedValue(0);
  const night = useSharedValue(0);
  const show = useSharedValue(-1);
  const camX = useSharedValue(0);
  const camY = useSharedValue(0);
  const camZ = useSharedValue(0);
  const title = useSharedValue(0);
  /** Touch: the hour as one scrubbable value, 0 = 9:00 AM, 1 = 7:00 PM, 2 = 11:45 PM. */
  const tod = useSharedValue(0);
  const panSky = useSharedValue(false);
  const panFrom = useSharedValue(0);
  const camFromX = useSharedValue(0);
  const camFromY = useSharedValue(0);
  /** Reduced motion: the lit tower cross-fades in over the still. */
  const still = useSharedValue(1);
  const stillOn = useSharedValue(false);
  /** Tilt (photo px), high-passed so the view re-centres on however the phone is held. */
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);
  const gravX = useSharedValue(0);
  const gravY = useSharedValue(0);
  const gravReady = useSharedValue(false);
  const gravity = useAnimatedSensor(SensorType.GRAVITY, { interval: 'auto' });

  /* Clock and touch state. */
  const clock = useSharedValue(0); // ms, advanced by the frame callback
  const time = useSharedValue(0); // loop time, s
  const mode = useSharedValue(looping ? AUTO : TOUCH);
  const touchedAt = useSharedValue(-1e9);
  const returnAt = useSharedValue(0);
  const holding = useSharedValue(false);
  const pressing = useSharedValue(HIT_NONE);
  const fanned = useSharedValue(false);
  const spotSlot = useSharedValue(-1);
  const liked = useSharedValue(false);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const warm = useSharedValue(0);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => foreground.set(s === 'active'));
    return () => sub.remove();
  }, [foreground]);

  /* ─────────────── geometry ─────────────── */
  const cardTop = ty(layout.card.y);
  const cardH = ty(layout.card.y + layout.card.h) - cardTop;
  const cardW = layout.card.w * U;

  // Photo, scaled to cover the card, `photoShift` of the overflow cropped from the top.
  const cover = Math.max(cardW / PHOTO_W, cardH / PHOTO_H);
  const photoW = PHOTO_W * cover;
  const photoH = PHOTO_H * cover;
  const photoLeft = (cardW - photoW) / 2;
  const photoTop = -(photoH - cardH) * layout.photoShift;

  const box = (b: { x: number; y: number; w: number; h: number }) => ({
    position: 'absolute' as const,
    left: X(b.x),
    top: ty(b.y),
    width: X(b.w),
    height: b.h * U,
  });

  /** Style of a text line: left aligned at ink x, or centred on `cx`; `base` is the baseline in ref px. `o` = origin of the parent (dp). */
  const textStyle = (s: TextStyleSpec, base: number, at: { x?: number; cx?: number }, bearing = 0, o = { x: 0, y: 0 }) => {
    const size = s.size * FS;
    const top = ty(base) - (s.font ? ASCENT_HEADLINE : ASCENT_SF) * size - o.y;
    const common = {
      color: s.color,
      fontSize: size,
      fontWeight: s.weight,
      letterSpacing: s.ls * FS,
      ...(s.font ? { fontFamily: s.font } : null),
    };
    if (at.cx !== undefined) {
      const BOX = 1100 * U;
      return [styles.abs, common, styles.center, { left: X(at.cx) - BOX / 2 - o.x, width: BOX, top }];
    }
    return [styles.abs, common, { left: X((at.x ?? 0) - bearing) - o.x, top }];
  };

  const label = (text: string, s: TextStyleSpec, base: number, at: { x?: number; cx?: number }, bearing = 0, o?: { x: number; y: number }) => (
    <Text allowFontScaling={false} numberOfLines={1} style={textStyle(s, base, at, bearing, o)}>
      {text}
    </Text>
  );

  const icon = (name: IconName, color?: string, o = { x: 0, y: 0 }): ReactNode => (
    <View style={{ position: 'absolute', left: X(iconBox[name].x) - o.x, top: ty(iconBox[name].y) - o.y }}>
      <Glyph name={name} u={U} color={color} />
    </View>
  );

  const tp = layout.topPills;
  const rowY = layout.row.y;
  const rowH = layout.row.h;
  const thumb = layout.thumb;
  const crop = layout.thumbCrop;
  const thumbK = thumb.w / crop.size; // photo px → ref px

  const knots = backdrop.knots;
  const baseColors = [
    rgba(knots[0].c, 0),
    rgba(knots[0].c, 1),
    ...knots.slice(1).map((k) => rgba(k.c, 1)),
    rgba(knots[knots.length - 1].c, 1),
  ];
  const baseTo = layout.card.y + layout.card.h;
  const baseH = baseTo - backdrop.fadeFrom;
  const basePos = [0, (backdrop.solidFrom - backdrop.fadeFrom) / baseH, ...knots.slice(1).map((k) => (k.y - backdrop.fadeFrom) / baseH), 1];

  /* Hit boxes in dp (all in the bottom-anchored group). */
  const by = (y: number) => height - (REF_H - y) * U;
  const viewBox = { x: layout.view.x, y: rowY, w: layout.view.w, h: rowH };
  const viewO = { x: X(viewBox.x), y: ty(viewBox.y) };
  const likeBox = { x: layout.like.x, y: rowY, w: layout.like.w, h: rowH };
  /** The like count rolls inside the pill's inner band, so the digits never leave the glass. */
  const countBox = { x: layout.likeCount.x - 20, y: rowY + layout.countInset, w: likeBox.x + likeBox.w - layout.likeCount.x, h: rowH - 2 * layout.countInset };
  const countClip = box(countBox);
  const countO = { x: X(countBox.x), y: ty(countBox.y) };
  const hb = {
    /** The open sky between the top bar and the guests: drag scrubs the hour, tap runs the evening. */
    sky: { y0: ty(layout.skyBand.y0), y1: by(layout.guestBand.y0) },
    guests: { y0: by(layout.guestBand.y0), y1: by(layout.guestBand.y1) },
    like: { x0: X(likeBox.x), x1: X(likeBox.x + likeBox.w), y0: by(rowY), y1: by(rowY + rowH) },
    view: { x0: X(viewBox.x), x1: X(viewBox.x + viewBox.w), y0: by(rowY), y1: by(rowY + rowH) },
  };
  const fannedDp = FANNED.map((f, s) => ({ x: X(f.cx), y: by(f.cy), r: (SLOTS[s].d / 2) * U }));

  /* ─────────────── writing a frame, and the touch actions (UI thread) ─────────────── */
  /** Write a value only when it changed: most of a frame's scalars sit still most of the loop. */
  const put = (v: SharedValue<number>, x: number) => {
    'worklet';
    if (v.get() !== x) v.set(x);
  };
  const apply = (f: Frame) => {
    'worklet';
    for (let s = 0; s < SLOT_COUNT; s++) {
      put(fans[s], f.fan[s]);
      put(spots[s], f.spot[s]);
    }
    put(nameFrom, f.nameFrom);
    put(nameTo, f.nameTo);
    put(nameP, f.nameP);
    put(like, f.like);
    put(pop, f.pop);
    put(ring, f.ring);
    put(count, f.count);
    put(press, f.press);
    put(chev, f.chev);
    put(sweep, f.sweep);
    put(mist, f.mist);
    put(drift, f.drift);
    put(day, f.day);
    put(night, f.night);
    put(show, f.show);
    put(camX, f.camX);
    put(camY, f.camY);
    put(camZ, f.camZ);
    put(title, f.title);
  };

  // Reduce Motion or a screen reader turned on mid-loop: back to the pin's still, loop stopped.
  // Turned off again: the loop restarts from its rest frame.
  useEffect(() => {
    scheduleOnUI(() => {
      'worklet';
      if (looping === (mode.get() === AUTO)) return;
      time.set(0);
      mode.set(looping ? AUTO : TOUCH);
      apply(restFrame());
      tod.set(0);
      still.set(1);
      stillOn.set(false);
      fanned.set(false);
      spotSlot.set(-1);
      liked.set(false);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- shared values and `apply` only write; `looping` decides
  }, [looping]);

  /** Move the guests to the stack (0) or the fan (1): springs, staggered from the side that leads. */
  const moveGuests = (to: number) => {
    'worklet';
    for (let s = 0; s < SLOT_COUNT; s++) {
      if (reduced) {
        fans[s].set(withDelay(ms(REDUCED.fade / 2), withTiming(to, { duration: 0, reduceMotion: NEVER })));
      } else {
        const delay = to > 0.5 ? (3 - rank(s)) * FAN_STAGGER : rank(s) * FAN_STAGGER;
        fans[s].set(withDelay(ms(delay), withSpring(to, rs(SPRINGS.fan))));
      }
    }
    if (reduced) {
      const half = ms(REDUCED.fade / 2);
      fade.set(withSequence(withTiming(0, { duration: half, reduceMotion: NEVER }), withTiming(1, { duration: half, reduceMotion: NEVER })));
    }
    fanned.set(to > 0.5);
  };

  /** Roll the guest line to `img`'s name from whichever line shows now. */
  const rollName = (img: number) => {
    'worklet';
    const shown = nameP.get() > 0.5 ? nameTo.get() : nameFrom.get();
    if (shown === img && nameFrom.get() === nameTo.get()) return;
    nameFrom.set(shown);
    nameTo.set(img);
    nameP.set(0);
    nameP.set(withTiming(1, { duration: ms(reduced ? REDUCED.fade : ROLL.name), easing: EINOUT, reduceMotion: NEVER }));
  };

  const setSpot = (slot: number) => {
    'worklet';
    for (let s = 0; s < SLOT_COUNT; s++) {
      const to = s === slot && !reduced ? 1 : 0;
      if (spots[s].get() !== to) spots[s].set(withSpring(to, rs(SPRINGS.spot)));
    }
    spotSlot.set(slot);
    rollName(slot >= 0 ? SLOTS[slot].img : REST_NAME);
  };

  const setLike = (on: boolean) => {
    'worklet';
    liked.set(on);
    const dip = ms(LIKE.dip);
    if (on && !reduced) {
      pop.set(
        withSequence(
          withTiming(1 - LIKE.dipScale, { duration: dip, easing: EOUT, reduceMotion: NEVER }),
          withSpring(1, rs(SPRINGS.pop)),
        ),
      );
      ring.set(0);
      ring.set(withDelay(dip, withTiming(1, { duration: ms(LIKE.ring), easing: EOUT, reduceMotion: NEVER })));
      like.set(withDelay(dip, withTiming(1, { duration: ms(LIKE.fill), easing: EOUT, reduceMotion: NEVER })));
      count.set(withDelay(dip, withTiming(1, { duration: ms(ROLL.count), easing: EINOUT, reduceMotion: NEVER })));
    } else {
      const d = ms(reduced ? REDUCED.fade : LIKE.unfill);
      like.set(withTiming(on ? 1 : 0, { duration: d, easing: EOUT, reduceMotion: NEVER }));
      count.set(withTiming(on ? 1 : 0, { duration: ms(reduced ? REDUCED.fade : ROLL.count), easing: EINOUT, reduceMotion: NEVER }));
    }
  };

  const showRunning = () => {
    'worklet';
    const s = show.get();
    return s >= 0 && s < SHOW_END;
  };

  /** Settle whatever the loop was doing into the nearest still state, so a touch never freezes a move. */
  const takeOver = () => {
    'worklet';
    if (mode.get() === TOUCH) return;
    mode.set(TOUCH);
    // Only what is away from its still state is animated: a spring from 0 to 0 still restyles its
    // view every frame until it settles (14 avatars at once dropped frames on the first touch).
    const settle = (v: SharedValue<number>, to: number, anim: number) => {
      'worklet';
      if (v.get() !== to) v.set(anim);
    };
    let open = false;
    for (let s = 0; s < SLOT_COUNT; s++) if (fans[s].get() > 0.5) open = true;
    for (let s = 0; s < SLOT_COUNT; s++) settle(fans[s], open ? 1 : 0, withSpring(open ? 1 : 0, rs(SPRINGS.fan)));
    fanned.set(open);
    let best = -1;
    for (let s = 0; s < SLOT_COUNT; s++) if (spots[s].get() > 0.5) best = s;
    for (let s = 0; s < SLOT_COUNT; s++) settle(spots[s], s === best ? 1 : 0, withSpring(s === best ? 1 : 0, rs(SPRINGS.spot)));
    spotSlot.set(best);
    const shown = nameP.get() > 0.5 ? nameTo.get() : nameFrom.get();
    nameFrom.set(shown);
    nameTo.set(shown);
    nameP.set(0);
    const on = like.get() > 0.5;
    liked.set(on);
    settle(like, on ? 1 : 0, withTiming(on ? 1 : 0, { duration: 120, reduceMotion: NEVER }));
    settle(count, on ? 1 : 0, withTiming(on ? 1 : 0, { duration: 120, reduceMotion: NEVER }));
    settle(pop, 1, withSpring(1, rs(SPRINGS.ui)));
    if (ring.get() > 0 && ring.get() < 1) ring.set(withTiming(1, { duration: 200, easing: EOUT, reduceMotion: NEVER }));
    if (sweep.get() > 0 && sweep.get() < 1) sweep.set(withTiming(1, { duration: 200, easing: EINOUT, reduceMotion: NEVER }));
    settle(press, 0, withSpring(0, rs(SPRINGS.ui)));
    settle(chev, 0, withSpring(0, rs(SPRINGS.nudge)));
    // The hour becomes one scrubbable value; a show in flight plays on to its end with its camera.
    const n = night.get();
    tod.set(n > 0 ? 1 + n : day.get());
    if (title.get() > 0) title.set(withTiming(0, { duration: ms(TITLE.fade), reduceMotion: NEVER }));
    if (showRunning()) {
      const s = show.get();
      show.set(withTiming(SHOW_END, { duration: ms(SHOW_END - s), easing: Easing.linear, reduceMotion: NEVER }));
    }
  };


  /** Back to the pin's still, then the loop restarts at t = 0. */
  const handBack = () => {
    'worklet';
    moveGuests(0);
    setSpot(-1);
    setLike(false);
    const d = RETURN_MS - 100;
    const glide = { duration: d, easing: EINOUT, reduceMotion: NEVER };
    pop.set(withSpring(1, rs(SPRINGS.ui)));
    press.set(withSpring(0, rs(SPRINGS.ui)));
    chev.set(withSpring(0, rs(SPRINGS.nudge)));
    mist.set(withTiming(0, glide));
    // From the night the dusk is not replayed backwards: the night lifts straight onto the pin.
    if (night.get() > 0) day.set(0);
    else day.set(withTiming(0, glide));
    night.set(withTiming(0, glide));
    tod.set(0);
    camX.set(withTiming(0, glide));
    camY.set(withTiming(0, glide));
    camZ.set(withTiming(0, glide));
    title.set(withTiming(0, glide));
  };

  const hitTest = (x: number, y: number) => {
    'worklet';
    if (y >= hb.sky.y0 && y < hb.sky.y1) return HIT_SKY;
    if (y >= hb.guests.y0 && y <= hb.guests.y1) return HIT_GUESTS;
    if (y >= hb.like.y0 && y <= hb.like.y1 && x >= hb.like.x0 && x <= hb.like.x1) return HIT_LIKE;
    if (y >= hb.view.y0 && y <= hb.view.y1 && x >= hb.view.x0 && x <= hb.view.x1) return HIT_VIEW;
    return HIT_NONE;
  };

  /** The fanned guest under the finger, or -1. */
  const guestAt = (x: number, y: number) => {
    'worklet';
    let hit = -1;
    let best = 1e9;
    for (let s = 0; s < SLOT_COUNT; s++) {
      const g = fannedDp[s];
      const d = Math.hypot(x - g.x, y - g.y);
      if (d <= g.r + 6 && d < best) {
        best = d;
        hit = s;
      }
    }
    return hit;
  };

  const actGuests = (x: number, y: number) => {
    'worklet';
    if (!fanned.get()) {
      moveGuests(1);
      scheduleOnRN(haptic, 'light', 0);
      return;
    }
    const s = guestAt(x, y);
    if (s >= 0 && s !== spotSlot.get()) {
      setSpot(s);
      scheduleOnRN(haptic, 'select', 0);
    } else {
      setSpot(-1);
      moveGuests(0);
      scheduleOnRN(haptic, 'light', 0);
    }
  };

  const actLike = () => {
    'worklet';
    const on = !liked.get();
    setLike(on);
    if (on) scheduleOnRN(haptic, 'light', 0);
  };

  /** Show the hour title while the finger or a toggle drives the sundial, then let it go. */
  const flashTitle = (holdMs: number) => {
    'worklet';
    if (reduced) return;
    title.set(
      withSequence(
        withTiming(1, { duration: 160, easing: EOUT, reduceMotion: NEVER }),
        withDelay(holdMs, withTiming(0, { duration: ms(TITLE.fade), easing: EINOUT, reduceMotion: NEVER })),
      ),
    );
  };

  /** View event: the LED show (night falls first if it has not). Reduced motion: the lit tower fades in. */
  const startShow = () => {
    'worklet';
    if (reduced) {
      const half = { duration: 400, easing: EINOUT, reduceMotion: NEVER };
      if (!stillOn.get()) {
        stillOn.set(true);
        still.set(0);
        tod.set(2);
        show.set(LED_STILL);
        still.set(withTiming(1, half));
      } else {
        stillOn.set(false);
        still.set(
          withTiming(0, half, (done) => {
            'worklet';
            if (!done) return;
            show.set(-1);
            tod.set(0);
            still.set(1);
          }),
        );
      }
      return;
    }
    if (showRunning()) return;
    const wait = tod.get() >= 1.98 ? 0 : ms(CTA.dusk);
    if (wait > 0) tod.set(withTiming(2, { duration: wait, easing: EINOUT, reduceMotion: NEVER }));
    show.set(-1);
    show.set(withDelay(wait, withSequence(withTiming(0, { duration: 0, reduceMotion: NEVER }), withTiming(SHOW_END, { duration: ms(SHOW_END), easing: Easing.linear, reduceMotion: NEVER }))));
    // The camera dollies in and looks up with the climbing portrait, then settles back.
    const sine = Easing.bezier(0.37, 0, 0.63, 1);
    camZ.set(withDelay(wait, withSequence(withTiming(1, { duration: 2550, easing: sine, reduceMotion: NEVER }), withDelay(ms(SHOW_END) - 2550, withTiming(0, { duration: 900, easing: sine, reduceMotion: NEVER })))));
    const up = ms(LED.avatarIn - 0.05);
    const upFor = ms(LED.climbEnd) - up;
    camY.set(withDelay(wait + up, withSequence(withTiming(1, { duration: upFor, easing: sine, reduceMotion: NEVER }), withDelay(ms(SHOW_END) - up - upFor, withTiming(0, { duration: 900, easing: sine, reduceMotion: NEVER })))));
    camX.set(withTiming(0, { duration: 600, easing: sine, reduceMotion: NEVER }));
    scheduleOnRN(haptic, 'show', wait);
  };

  const releaseView = (commit: boolean) => {
    'worklet';
    press.set(withSpring(0, rs(SPRINGS.ui)));
    if (!commit) return;
    if (!reduced) {
      chev.set(
        withSequence(
          withTiming(1, { duration: ms(VIEW.nudgeOut), easing: EOUT, reduceMotion: NEVER }),
          withSpring(0, rs(SPRINGS.nudge)),
        ),
      );
      sweep.set(0);
      sweep.set(withTiming(1, { duration: ms(VIEW.sweep), easing: EINOUT, reduceMotion: NEVER }));
    }
    scheduleOnRN(haptic, 'light', 0);
    startShow();
  };

  /** Tap on the sky: the whole evening runs by itself (or back to the morning). */
  const toggleNight = () => {
    'worklet';
    // The show owns the evening while it runs.
    if (showRunning()) return;
    const to = tod.get() > 1 ? 0 : 2;
    const d = reduced ? REDUCED.fade : NIGHT.toggle;
    tod.set(withTiming(to, { duration: ms(d), easing: reduced ? EINOUT : ENIGHT, reduceMotion: NEVER }));
    flashTitle(ms(d));
  };

  /** Drag on the sky: left or down = later; the camera follows the finger; release settles on the event's start or end. */
  const scrubX = NIGHT.scrubX * cardW;
  const scrubY = NIGHT.scrub * cardH;
  const reach = SPACE.reach;
  const pan = Gesture.Pan()
    .minDistance(10)
    .onBegin((e) => {
      holding.set(true);
      takeOver();
      touchedAt.set(clock.get());
      panSky.set(e.y >= hb.sky.y0 && e.y < hb.sky.y1);
    })
    .onStart(() => {
      if (!panSky.get()) return;
      tod.set(tod.get());
      panFrom.set(tod.get());
      camX.set(camX.get());
      camY.set(camY.get());
      camFromX.set(camX.get());
      camFromY.set(camY.get());
      if (!reduced) title.set(withTiming(1, { duration: 160, easing: EOUT, reduceMotion: NEVER }));
    })
    .onUpdate((e) => {
      if (!panSky.get()) return;
      tod.set(clampTod(panFrom.get() - e.translationX / scrubX + e.translationY / scrubY));
      if (reduced) return;
      camX.set(rubber(camFromX.get() + (e.translationX * SPACE.follow) / SPACE.ampX, reach));
      camY.set(rubber(camFromY.get() + (e.translationY * SPACE.follow) / SPACE.ampY, reach));
    })
    .onEnd((e) => {
      if (!panSky.get()) return;
      const v = -e.velocityX / scrubX + e.velocityY / scrubY;
      const to = v > NIGHT.flick || (v > -NIGHT.flick && tod.get() > 1) ? 2 : 0;
      tod.set(withSpring(to, { duration: 900, dampingRatio: 1, velocity: v, reduceMotion: NEVER }));
      camX.set(withSpring(0, { ...rs(CAM_SPRING), velocity: (e.velocityX * SPACE.follow) / SPACE.ampX }));
      camY.set(withSpring(0, { ...rs(CAM_SPRING), velocity: (e.velocityY * SPACE.follow) / SPACE.ampY }));
      if (!reduced) title.set(withDelay(900 + ms(TITLE.hold), withTiming(0, { duration: ms(TITLE.fade), easing: EINOUT, reduceMotion: NEVER })));
    })
    .onFinalize(() => {
      holding.set(false);
      touchedAt.set(clock.get());
    });

  const tap = Gesture.Tap()
    .maxDuration(800)
    .maxDistance(12)
    .onBegin((e) => {
      holding.set(true);
      takeOver();
      touchedAt.set(clock.get());
      const h = hitTest(e.x, e.y);
      pressing.set(h);
      if (h === HIT_VIEW) press.set(withTiming(1, { duration: ms(VIEW.pressIn), easing: EOUT, reduceMotion: NEVER }));
    })
    .onEnd((e, success) => {
      if (!success) return;
      const h = pressing.get();
      if (h === HIT_GUESTS) actGuests(e.x, e.y);
      else if (h === HIT_SKY) toggleNight();
      else if (h === HIT_LIKE) actLike();
      else if (h === HIT_VIEW) releaseView(true);
    })
    .onFinalize((_e, success) => {
      if (pressing.get() === HIT_VIEW && !success) releaseView(false);
      pressing.set(HIT_NONE);
      holding.set(false);
      touchedAt.set(clock.get());
    });

  const gesture = Gesture.Race(pan, tap);

  /** VoiceOver: the same actions as a tap. */
  const a11y = (kind: number) => () =>
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      touchedAt.set(clock.get());
      if (kind === HIT_GUESTS) actGuests(-1, -1);
      else if (kind === HIT_SKY) toggleNight();
      else if (kind === HIT_LIKE) actLike();
      else releaseView(true);
    });

  useFrameCallback((info) => {
    const dt = info.timeSincePreviousFrame ?? 0;
    const now = clock.get() + dt;
    clock.set(now);
    if (warm.get() > 0) warm.set(warm.get() - 1);
    if (!foreground.get()) return;
    // Tilt: gravity, high-passed (the view drifts back to centre on however the phone is held).
    const g = gravity.sensor.get();
    if (!reduced && (g.x !== 0 || g.y !== 0)) {
      const gx = g.x / 9.81;
      const gy = g.y / 9.81;
      if (!gravReady.get()) {
        gravReady.set(true);
        gravX.set(gx);
        gravY.set(gy);
      }
      const a = Math.min(1, dt / 1000 / SPACE.tiltTau);
      gravX.set(gravX.get() + (gx - gravX.get()) * a);
      gravY.set(gravY.get() + (gy - gravY.get()) * a);
      put(tiltX, (gravX.get() - gx) * SPACE.tilt);
      put(tiltY, (gy - gravY.get()) * SPACE.tilt);
    }
    const m = mode.get();
    if (m === AUTO) {
      if (!looping) return;
      const t = (time.get() + dt / 1000) % LOOP;
      time.set(t);
      apply(frameAt(t));
      return;
    }
    if (m === TOUCH) {
      // The scrubbable hour drives the sundial (0..1) and then the night (1..2).
      const h = tod.get();
      put(day, h < 1 ? h : 1);
      put(night, h > 1 ? h - 1 : 0);
      if (looping && !showRunning() && !holding.get() && now - touchedAt.get() >= RESUME_AFTER) {
        handBack();
        returnAt.set(now);
        mode.set(RETURN);
      }
    } else if (now - returnAt.get() >= RETURN_MS) {
      time.set(0);
      tod.set(0);
      apply(restFrame());
      mode.set(AUTO);
    }
  });


  /* ─────────────── the scene ─────────────── */
  const photo = useImage(PHOTO);
  const fillImg = useImage(FILL);
  const towerImg = useImage(TOWER_MASK);
  const nearImg = useImage(NEAR_MASK);
  const backDepthImg = useImage(BACK_DEPTH);
  const depthImg = useImage(DEPTH);
  const lightMap = useImage(LIGHT_MAP);
  const keyMap = useImage(KEY_MAP);
  const bloomMap = useImage(BLOOM_MAP);
  const bloomKeyMap = useImage(BLOOM_KEY_MAP);
  const facadeImg = useImage(FACADE);
  const glyphImg = useImage(GLYPH_ATLAS);
  const viewerImg = useImage(VIEWER);
  const sceneReady =
    SCENE_EFFECT !== null &&
    [fillImg, towerImg, nearImg, backDepthImg, depthImg, lightMap, keyMap, bloomMap, bloomKeyMap, facadeImg, glyphImg, viewerImg].every((i) => i !== null);
  // Both passes are drawn, all but invisibly, for a few frames once the images are in, so Metal
  // builds their pipelines while the screen rests instead of in the middle of the loop (it took
  // 1.4–2 s the first time each was drawn).
  useEffect(() => {
    if (sceneReady && photo) warm.set(WARM_FRAMES);
  }, [sceneReady, photo, warm]);
  const photoRect = useMemo(() => ({ x: photoLeft, y: photoTop, width: photoW, height: photoH }), [photoLeft, photoTop, photoW, photoH]);
  const glyphRect = useMemo(() => ({ x: 0, y: 0, width: 110, height: 7 }), []);
  const viewerRect = useMemo(() => ({ x: 0, y: 0, width: 180, height: 180 }), []);

  const mistUniforms = useDerivedValue<Uniforms>(() => ({
    drift: drift.get() * AMBIENT.drift,
    // The haze is lit by day only: it fades as the sky grades to night.
    amt: mist.get() * AMBIENT.mist * (1 - Math.min(1, night.get() / NIGHT.grade)),
    tint: colors.mist,
  }));

  /** Camera in photo px (loop or finger, plus tilt) and the layers' zoom. */
  const camera = useDerivedValue(() => {
    const cx = camX.get() * SPACE.ampX + tiltX.get();
    const cy = camY.get() * SPACE.ampY + tiltY.get();
    const zoom = camZ.get() * SPACE.dolly + Math.abs(cx) * SPACE.coverX + Math.abs(cy) * SPACE.coverY;
    return { x: cx, y: cy, z: camZ.get(), zoom };
  });
  /** Where the spire tip shows (card dp), following the tower layer, and that layer's scale. */
  const tip = useDerivedValue(() => {
    const c = camera.get();
    const s = 1 + c.zoom * SPACE.tower;
    const px = SPACE.focusX + (TOWER.tipX - SPACE.focusX) * s + c.x * SPACE.tower;
    const py = SPACE.focusY + (TOWER.tipY - SPACE.focusY) * s + c.y * SPACE.tower;
    return { x: photoLeft + px * cover, y: photoTop + py * cover, s };
  });
  const fire = useDerivedValue(() => fireOf(show.get()));
  /** Fireworks belong to the night: dragged back to dusk mid-show, they fade with it. */
  const fireOpacity = useDerivedValue(() => Math.min(1, night.get() / NIGHT.fireFade));
  /** The sparks see a show in full flight during the warm-up (all but invisible), so their pipelines are built too. */
  const sparkFire = useDerivedValue(() => (warm.get() > 0 ? WARM_FIRE : fire.get()));
  const sparkFade = useDerivedValue(() => (warm.get() > 0 ? WARM_OPACITY : fireOpacity.get()));
  const fireworksTransform = useDerivedValue(() => {
    const p = tip.get();
    return [{ translateX: p.x }, { translateY: p.y }, { scale: U * p.s }];
  });
  /** Everything the scene shader needs, per frame. */
  const sceneUniforms = useDerivedValue<Uniforms>(() => {
    const sh = show.get();
    const fr = fire.get();
    const on = fr >= 0;
    const fd = fireOpacity.get();
    const c = camera.get();
    const p = tip.get();
    const sun = { az: 0, el: 0, amt: 0 };
    sunOf(day.get(), sun);
    const led: number[] = [];
    for (let i = 0; i < 16; i++) led.push(0);
    ledOf(sh, led);
    const fl: number[][] = [];
    const sm: number[] = [];
    for (let b = 0; b < 3; b++) {
      const B = FIRE.bursts[b];
      fl.push([p.x + B.dx * U * p.s, p.y + B.dy * U * p.s, on ? flashAt(B, fr - B.at) * fd : 0]);
      sm.push(on && fr > B.at ? fr - B.at : -1);
    }
    return {
      mode: 0,
      night: night.get(),
      geo: [photoLeft, photoTop, cover],
      cam: [c.x, c.y, c.z],
      sun: [sun.az, sun.el, sun.amt * (1 - Math.min(1, night.get() / NIGHT.grade)), 0],
      f0: fl[0],
      f1: fl[1],
      f2: fl[2],
      c0: BURST_LIGHT[0],
      c1: BURST_LIGHT[1],
      c2: BURST_LIGHT[2],
      smoke: sm,
      flare: [p.x, p.y, on ? flareAt(fr) * fd : 0],
      // The façade belongs to the night too: dragged back to day mid-show, it fades with it.
      ledA: [led[0] * fd, led[1], led[2], led[3]],
      ledB: [led[4], led[5], led[6], led[7]],
      ledC: [led[8], led[9], led[10], led[11]],
      ledD: [led[12], fd, 0, 0],
      drift: drift.get(),
    };
  });
  /** The same, drawing only the tower and the near towers (over the hour title). */
  const towerUniforms = useDerivedValue<Uniforms>(() => ({ ...sceneUniforms.get(), mode: 1 }));
  /** The scene is drawn only while it differs from the photo; it fades in over the first moments. */
  const sceneOpacity = useDerivedValue(() => {
    const c = camera.get();
    const a = Math.max(day.get(), night.get(), Math.abs(c.x) / SPACE.ampX, Math.abs(c.y) / SPACE.ampY, c.z, show.get() >= 0 && show.get() < SHOW_END ? 1 : 0);
    const o = Math.min(1, a / 0.02) * still.get();
    return warm.get() > 0 ? Math.max(o, WARM_OPACITY) : o;
  });
  const towerPassOpacity = useDerivedValue(() => (title.get() > 0.001 ? sceneOpacity.get() : warm.get() > 0 ? WARM_OPACITY : 0));

  /** The fireworks' (and the façade's) light on the headline, the guests' rings and the glass. */
  const uiLight = useDerivedValue(() => {
    const fr = fire.get();
    const fd = fireOpacity.get();
    let r = 0;
    let g = 0;
    let b = 0;
    let w = 0;
    if (fr >= 0) {
      for (let i = 0; i < 3; i++) {
        const k = flashAt(FIRE.bursts[i], fr - FIRE.bursts[i].at) + (i === 0 ? 0.6 * flareAt(fr) : 0);
        r += BURST_RGB[i][0] * k;
        g += BURST_RGB[i][1] * k;
        b += BURST_RGB[i][2] * k;
        w += k;
      }
    }
    const led: number[] = [];
    for (let i = 0; i < 16; i++) led.push(0);
    ledOf(show.get(), led);
    const ls = led[11] * 0.35;
    r += SPILL_RGB[0] * ls;
    g += SPILL_RGB[1] * ls;
    b += SPILL_RGB[2] * ls;
    w += ls;
    // Quantised (1/64 of light, 8 levels per channel) so the 16 views it styles update only when it shows.
    const amt = Math.round(Math.min(1, (fireLight(fr) + ls) * fd) * 64) / 64;
    const q = (v: number) => Math.min(255, Math.round(v / w / 32) * 32);
    const color = w > 0 ? `rgb(${q(r)},${q(g)},${q(b)})` : NT.rim;
    return { amt, color };
  });
  const glow = useDerivedValue(() => uiLight.get().amt);
  // The headline's glow keeps one colour and never quite reaches 0: a text shadow is rasterised again
  // whenever its colour changes or it reappears from 0 (each cost a dropped frame); opacity alone is
  // the compositor's. 0.004 of the glow is below one 8-bit step.
  const headlineGlowAnim = useAnimatedStyle(() => ({ opacity: Math.max(0.004, fireLight(fire.get()) * fireOpacity.get()) }));
  const glassLitAnim = useAnimatedStyle(() => ({ opacity: 0.16 * uiLight.get().amt, backgroundColor: uiLight.get().color }));
  const rimColor = useDerivedValue(() => uiLight.get().color);

  /* The hour title: slots measured once (digits are tabular), laid out per frame. */
  const titleMetrics = useMemo(() => {
    if (!titleFont || !suffixFont) return null;
    const digits = titleFont.getGlyphWidths(titleFont.getGlyphIDs('0123456789'));
    const colon = titleFont.getGlyphWidths(titleFont.getGlyphIDs(':'))[0];
    const am = suffixFont.getGlyphWidths(suffixFont.getGlyphIDs('AM')).reduce((s, v) => s + v, 0);
    const pm = suffixFont.getGlyphWidths(suffixFont.getGlyphIDs('PM')).reduce((s, v) => s + v, 0);
    return { digits, dw: Math.max(...digits), colon, suffix: Math.max(am, pm), gap: 6 * FS };
  }, [titleFont, suffixFont, FS]);
  const titleBase = ty(TITLE.base) - cardTop;
  /** The colon sits behind the tower (it peeks out as the camera moves); hours left of it, minutes right. */
  const titleMid = photoLeft + LED.mid * cover;
  const titleSep = TITLE.sep * FS;
  const titleLayout = useDerivedValue(() => {
    const m = titleMetrics;
    const t = { h0: ' ', h1: ' ', m0: ' ', m1: ' ', ap: ' ', x0: 0, x1: 0, xc: 0, x2: 0, x3: 0, xs: 0 };
    if (!m) return t;
    const c = { h: 9, m: 0, pm: false };
    clockOf(hourOf(day.get(), night.get()), c);
    const digit = (d: number) => (m.dw - m.digits[d]) / 2;
    const h1 = c.h % 10;
    t.h1 = `${h1}`;
    t.x1 = titleMid - titleSep - m.dw + digit(h1);
    if (c.h >= 10) {
      t.h0 = '1';
      t.x0 = titleMid - titleSep - 2 * m.dw + digit(1);
    }
    t.xc = titleMid - m.colon / 2;
    const m0 = Math.floor(c.m / 10);
    const m1 = c.m % 10;
    t.m0 = `${m0}`;
    t.x2 = titleMid + titleSep + digit(m0);
    t.m1 = `${m1}`;
    t.x3 = titleMid + titleSep + m.dw + digit(m1);
    t.ap = c.pm ? 'PM' : 'AM';
    t.xs = titleMid + titleSep + 2 * m.dw + m.gap;
    return t;
  });
  const titleTransform = useDerivedValue(() => {
    const c = camera.get();
    return [{ translateX: c.x * SPACE.title * cover }, { translateY: c.y * SPACE.title * cover }];
  });
  const tH0 = useDerivedValue(() => titleLayout.get().h0);
  const tH1 = useDerivedValue(() => titleLayout.get().h1);
  const tM0 = useDerivedValue(() => titleLayout.get().m0);
  const tM1 = useDerivedValue(() => titleLayout.get().m1);
  const tAp = useDerivedValue(() => titleLayout.get().ap);
  const xH0 = useDerivedValue(() => titleLayout.get().x0);
  const xH1 = useDerivedValue(() => titleLayout.get().x1);
  const xC = useDerivedValue(() => titleLayout.get().xc);
  const xM0 = useDerivedValue(() => titleLayout.get().x2);
  const xM1 = useDerivedValue(() => titleLayout.get().x3);
  const xAp = useDerivedValue(() => titleLayout.get().xs);

  const heartAnim = useAnimatedStyle(() => ({ transform: [{ scale: pop.get() }] }));
  const heartFillAnim = useAnimatedStyle(() => ({ opacity: like.get() }));
  const viewAnim = useAnimatedStyle(() =>
    reduced ? { opacity: 1 - 0.3 * press.get() } : { transform: [{ scale: 1 - VIEW.pressScale * press.get() }] },
  );
  const chevAnim = useAnimatedStyle(() => ({ transform: [{ translateX: VIEW.nudge * U * chev.get() }] }));

  // Highlight band across the View event pill (pill-local dp).
  const pillW = X(viewBox.w);
  const pillH = rowH * U;
  const band = VIEW.band * U;
  const bandSlope = Math.tan((-VIEW.bandSkew * Math.PI) / 180);
  // The gradient's ends are fixed; only a shader translation moves per frame (no Skia point allocated per frame).
  const sweepStart = useMemo(() => vec(0, 0), []);
  const sweepEnd = useMemo(() => vec(band, band * bandSlope), [band, bandSlope]);
  const sweepShift = useDerivedValue(() => [{ translateX: -VIEW.bandLead * band + (pillW + (VIEW.bandLead + 0.6) * band) * sweep.get() }]);
  /** Drawn only while it travels, so the pill at rest is exactly the pin's. */
  const sweepOn = useDerivedValue(() => (sweep.get() > 0 && sweep.get() < 1 ? 1 : 0));

  // Ring burst around the heart (ref px inside its canvas group).
  const heartC = { x: iconBox.heart.x + iconBox.heart.w / 2, y: iconBox.heart.y + iconBox.heart.h / 2 };
  const heartR = iconBox.heart.w / 2;
  const ringR = useDerivedValue(() => heartR * (LIKE.ringFrom + (LIKE.ringTo - LIKE.ringFrom) * ring.get()));
  const ringW = useDerivedValue(() => LIKE.ringStroke * (1 - ring.get()));
  /** The like's burst ring catches the gold of the night (the fireworks' rim colour) as the night falls. */
  const ringColor = useDerivedValue(() => interpolateColor(night.get(), [0, 1], [colors.burst, NT.rim]));
  const ringO = useDerivedValue(() => {
    const r = ring.get();
    return r > 0 && r < 1 ? 0.9 * (1 - r) : 0;
  });

  const burstHalf = layout.burstHalf;

  const mistReady = MIST !== null;
  const nameLines = useMemo(() => copy.names.map((n) => `${n}${copy.moreSuffix}`), []);

  /** Glass that catches the show's light: the same shape, tinted by `glassLitAnim`. */
  const glass = (b: { x: number; y: number; w: number; h: number }, fill: string, r?: number) => {
    const br = (r ?? Math.min(b.w, b.h) / 2) * U;
    return (
      <View style={[box(b), { backgroundColor: fill, borderRadius: br, overflow: 'hidden' }]}>
        <Animated.View style={[StyleSheet.absoluteFill, glassLitAnim]} />
      </View>
    );
  };

  /** Below the backdrop's solid start nothing of the photo shows: the shader stops there. */
  const sceneH = Math.min(cardH, ty(backdrop.solidFrom) - cardTop);
  const towerPassRect = {
    x: photoLeft + 330 * cover,
    y: photoTop + 250 * cover,
    width: 200 * cover,
    height: 800 * cover,
  };

  const sceneChildren = (
    <>
      <ImageShader image={photo} fit="fill" rect={photoRect} sampling={SAMPLING} tx="clamp" ty="clamp" />
      <ImageShader image={fillImg} fit="fill" rect={photoRect} tx="clamp" ty="clamp" />
      <ImageShader image={towerImg} fit="fill" rect={photoRect} tx="decal" ty="decal" />
      <ImageShader image={nearImg} fit="fill" rect={photoRect} tx="decal" ty="decal" />
      <ImageShader image={backDepthImg} fit="fill" rect={photoRect} tx="clamp" ty="clamp" />
      <ImageShader image={depthImg} fit="fill" rect={photoRect} tx="clamp" ty="clamp" />
      <ImageShader image={lightMap} fit="fill" rect={photoRect} tx="decal" ty="decal" />
      <ImageShader image={keyMap} fit="fill" rect={photoRect} tx="decal" ty="decal" />
      <ImageShader image={bloomMap} fit="fill" rect={photoRect} tx="decal" ty="decal" />
      <ImageShader image={bloomKeyMap} fit="fill" rect={photoRect} tx="decal" ty="decal" />
      <ImageShader image={facadeImg} fit="fill" rect={photoRect} tx="clamp" ty="clamp" />
      <ImageShader image={glyphImg} fit="fill" rect={glyphRect} tx="clamp" ty="clamp" />
      <ImageShader image={viewerImg} fit="fill" rect={viewerRect} tx="clamp" ty="clamp" />
    </>
  );

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.screen}>
        {/* Photo card */}
        <View style={[styles.abs, styles.clip, { left: 0, top: cardTop, width: cardW, height: cardH, borderRadius: radius.card * U }]}>
          {/* Shown only until Skia has decoded the photo; then covered by it. */}
          <Image source={PHOTO} style={{ position: 'absolute', width: photoW, height: photoH, left: photoLeft, top: photoTop }} resizeMode="stretch" />
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            {photo ? (
              <>
                <SkImage image={photo} x={photoLeft} y={photoTop} width={photoW} height={photoH} fit="fill" sampling={SAMPLING} />
                {sceneReady && SCENE_EFFECT ? (
                  <Rect x={0} y={0} width={cardW} height={sceneH} opacity={sceneOpacity}>
                    <Shader source={SCENE_EFFECT} uniforms={sceneUniforms}>
                      {sceneChildren}
                    </Shader>
                  </Rect>
                ) : null}
                {/* The hour, between the sky and the tower: drawn over the scene, under the tower drawn again. */}
                {titleFont && suffixFont ? (
                  <Group transform={titleTransform} opacity={title}>
                    <SkText x={xH0} y={titleBase} text={tH0} font={titleFont} color={SC.title} />
                    <SkText x={xH1} y={titleBase} text={tH1} font={titleFont} color={SC.title} />
                    <SkText x={xC} y={titleBase} text=":" font={titleFont} color={SC.title} />
                    <SkText x={xM0} y={titleBase} text={tM0} font={titleFont} color={SC.title} />
                    <SkText x={xM1} y={titleBase} text={tM1} font={titleFont} color={SC.title} />
                    <SkText x={xAp} y={titleBase} text={tAp} font={suffixFont} color={SC.titleSoft} />
                  </Group>
                ) : null}
                {sceneReady && SCENE_EFFECT ? (
                  <Rect {...towerPassRect} opacity={towerPassOpacity}>
                    <Shader source={SCENE_EFFECT} uniforms={towerUniforms}>
                      {sceneChildren}
                    </Shader>
                  </Rect>
                ) : null}
                {/* No opacity on this group: a layer group at opacity 0 broke the rest of the frame
                    (the scrim, backdrop and wordmark fill went missing by day); the fade is per layer. */}
                <Group transform={fireworksTransform}>
                  <Fireworks fire={sparkFire} fade={sparkFade} />
                </Group>
              </>
            ) : null}
            <Group transform={[{ scale: U }, { translateY: -layout.card.y }]}>
              {mistReady ? (
                // A shader-only node with no paint prop of its own writes its shader into the shared
                // paint, which then leaks into every later sibling (the scrim, the backdrop and the
                // wordmark's fill vanished): `opacity` gives each its own paint.
                <Rect x={0} y={layout.card.y} width={REF_W} height={1000} opacity={1}>
                  <Shader source={MIST} uniforms={mistUniforms} />
                </Rect>
              ) : null}
              <Rect x={0} y={layout.card.y} width={REF_W} height={backdrop.topFadeTo - layout.card.y} opacity={1}>
                <LinearGradient
                  start={vec(0, layout.card.y)}
                  end={vec(0, backdrop.topFadeTo)}
                  colors={[colors.scrimTop, colors.scrimTopClear]}
                />
              </Rect>
              <Rect x={0} y={backdrop.fadeFrom} width={REF_W} height={baseH} opacity={1}>
                <LinearGradient start={vec(0, backdrop.fadeFrom)} end={vec(0, baseTo)} colors={baseColors} positions={basePos} />
              </Rect>
              {backdrop.glows.map((g, i) => (
                <Group key={i} transform={[{ translateX: g.cx }, { translateY: g.cy }, { scaleY: g.sy / g.sx }]}>
                  <Circle cx={0} cy={0} r={3 * g.sx} blendMode="plus">
                    <RadialGradient c={vec(0, 0)} r={3 * g.sx} colors={GAUSS.map((w) => rgba(g.c, w))} positions={GAUSS.map((_, k) => k / 6)} />
                  </Circle>
                </Group>
              ))}
              {wordFont ? (
                <>
                  <SkText x={layout.wordmark.x} y={layout.wordmark.base} text={copy.wordmark} font={wordFont}>
                    <Paint color={colors.wordOutline} style="stroke" strokeWidth={layout.wordmark.outline * 2} strokeJoin="round" />
                  </SkText>
                  <SkText x={layout.wordmark.x} y={layout.wordmark.base} text={copy.wordmark} font={wordFont} color={colors.wordFill} />
                </>
              ) : null}
            </Group>
          </Canvas>
        </View>

        {/* Top bar */}
        {glass(tp.gift, colors.glassTop)}
        {glass(tp.byDate, colors.glassTop)}
        {glass(tp.search, colors.glassTop)}
        {icon('gift')}
        {icon('filter')}
        {icon('search')}
        {fontsReady ? label(copy.byDate, T.pill, layout.byDate.base, { x: layout.byDate.x }, layout.bearing.pill) : null}

        {/* Guests (outermost drawn first) */}
        {layout.guests.map((g) => {
          const s = SLOTS.indexOf(g);
          return (
            <GuestAvatar
              key={g.cx}
              slot={s}
              fan={fans[s]}
              spot={spots[s]}
              fade={fade}
              glow={glow}
              rim={rimColor}
              U={U}
              left={X(g.cx - g.d / 2)}
              top={ty(g.cy) - (g.d / 2) * U}
            />
          );
        })}

        {/* Texts */}
        {fontsReady ? (
          <>
            {nameLines.map((line, i) => (
              <RollLine key={line} index={i} from={nameFrom} to={nameTo} p={nameP} rise={reduced ? 0 : ROLL.nameRise * U} style={textStyle(T.mute, layout.mute.base, { cx: layout.mute.cx })}>
                {line}
              </RollLine>
            ))}
            <Animated.Text
              allowFontScaling={false}
              numberOfLines={1}
              style={[
                ...textStyle(T.headline, layout.headline.base, { cx: layout.headline.cx }),
                { textShadowColor: NT.headlineGlow, textShadowRadius: NT.headlineGlowRadius * FS, textShadowOffset: { width: 0, height: 0 } },
                headlineGlowAnim,
              ]}
            >
              {copy.headline}
            </Animated.Text>
            {label(copy.headline, T.headline, layout.headline.base, { cx: layout.headline.cx })}
            {label(copy.date, T.date, layout.date.base, { cx: layout.date.cx })}
          </>
        ) : null}

        {/* Presented by */}
        {glass(layout.presented, colors.glassBottom, radius.glassCard)}
        <View style={[box(thumb), styles.clip, { borderRadius: radius.thumb * U }]}>
          <Image
            source={PHOTO}
            style={{
              position: 'absolute',
              width: PHOTO_W * thumbK * U,
              height: PHOTO_H * thumbK * U,
              left: -crop.x * thumbK * U,
              top: -crop.y * thumbK * U,
            }}
            resizeMode="stretch"
          />
        </View>
        {fontsReady ? (
          <>
            {label(copy.presentedBy, T.label, layout.presentedBy.base, { x: layout.presentedBy.x }, layout.bearing.label)}
            {label(copy.presentedTitle, T.title, layout.presentedTitle.base, { x: layout.presentedTitle.x }, layout.bearing.title)}
          </>
        ) : null}
        {icon('chevCard', colors.chevCard)}

        {/* Action row */}
        {glass(likeBox, colors.glassBottom)}
        {glass({ x: layout.share.x, y: rowY, w: layout.share.w, h: rowH }, colors.glassBottom)}
        {glass({ x: layout.more.x, y: rowY, w: layout.more.w, h: rowH }, colors.glassBottom)}
        <Canvas
          pointerEvents="none"
          style={[styles.abs, { left: X(heartC.x - burstHalf), top: ty(heartC.y - burstHalf), width: 2 * burstHalf * U, height: 2 * burstHalf * U }]}
        >
          <Group transform={[{ scale: U }, { translateX: burstHalf - heartC.x }, { translateY: burstHalf - heartC.y }]}>
            <Circle cx={heartC.x} cy={heartC.y} r={ringR} style="stroke" strokeWidth={ringW} color={ringColor} opacity={ringO} />
          </Group>
        </Canvas>
        <Animated.View style={[styles.abs, { left: X(iconBox.heart.x), top: ty(iconBox.heart.y) }, heartAnim]}>
          <Glyph name="heart" u={U} />
          <Animated.View style={[StyleSheet.absoluteFill, heartFillAnim]}>
            <Glyph name="heartFill" u={U} color={colors.heartFill} />
          </Animated.View>
        </Animated.View>
        {icon('share')}
        {icon('more')}
        {fontsReady ? (
          <>
            <View style={[styles.abs, styles.clip, countClip]}>
              <RollLine index={0} from={null} to={null} p={count} rise={reduced ? 0 : ROLL.countRise * U} style={textStyle(T.pill, layout.likeCount.base, { x: layout.likeCount.x }, layout.bearing.pill, countO)}>
                {copy.likes}
              </RollLine>
              <RollLine index={1} from={null} to={null} p={count} rise={reduced ? 0 : ROLL.countRise * U} style={textStyle(T.pill, layout.likeCount.base, { x: layout.likeCount.x }, layout.bearing.pill, countO)}>
                {copy.likesNext}
              </RollLine>
            </View>
          </>
        ) : null}

        <Animated.View style={[box(viewBox), viewAnim]}>
          <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.glassBottom, borderRadius: (rowH / 2) * U, overflow: 'hidden' }]}>
            <Animated.View style={[StyleSheet.absoluteFill, glassLitAnim]} />
          </View>
          <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
            <RoundedRect x={0} y={0} width={pillW} height={pillH} r={pillH / 2} opacity={sweepOn}>
              <LinearGradient start={sweepStart} end={sweepEnd} transform={sweepShift} colors={[colors.sweepClear, colors.sweepSoft, colors.sweep, colors.sweepSoft, colors.sweepClear]} positions={[0, 0.3, 0.5, 0.7, 1]} />
            </RoundedRect>
          </Canvas>
          {fontsReady ? label(copy.view, T.pillSmall, layout.viewText.base, { x: layout.viewText.x }, layout.bearing.pill, viewO) : null}
          <Animated.View style={chevAnim}>{icon('chevView', colors.chevView, viewO)}</Animated.View>
        </Animated.View>

        {/* Tab bar */}
        {icon('home')}
        {icon('chat')}
        {icon('plus')}
        {icon('bell')}
        {icon('person')}

        {/* VoiceOver targets for the touch actions */}
        <View
          accessible
          accessibilityRole="button"
          accessibilityLabel="City sky. Run the day into the night."
          onAccessibilityTap={a11y(HIT_SKY)}
          style={[styles.abs, { left: 0, width, top: hb.sky.y0, height: hb.sky.y1 - hb.sky.y0 }]}
          pointerEvents="none"
        />
        <View
          accessible
          accessibilityRole="button"
          accessibilityLabel="Guests: Amira and 17 more. Show guests."
          onAccessibilityTap={a11y(HIT_GUESTS)}
          style={[styles.abs, { left: 0, width, top: hb.guests.y0, height: hb.guests.y1 - hb.guests.y0 }]}
          pointerEvents="none"
        />
        <View
          accessible
          accessibilityRole="button"
          accessibilityLabel="Like"
          onAccessibilityTap={a11y(HIT_LIKE)}
          style={box(likeBox)}
          pointerEvents="none"
        />
        <View
          accessible
          accessibilityRole="button"
          accessibilityLabel="View event. Lights up the tower."
          onAccessibilityTap={a11y(HIT_VIEW)}
          style={box(viewBox)}
          pointerEvents="none"
        />
      </View>
    </GestureDetector>
  );
}

/** One guest: travels between its place in the stack and in the fan, lifts in flight and in the spotlight. */
function GuestAvatar({
  slot,
  fan,
  spot,
  fade,
  glow,
  rim,
  U,
  left,
  top,
}: {
  slot: number;
  fan: SharedValue<number>;
  spot: SharedValue<number>;
  fade: SharedValue<number>;
  glow: DerivedValue<number>;
  rim: DerivedValue<string>;
  U: number;
  left: number;
  top: number;
}) {
  const g = SLOTS[slot];
  const f = FANNED[slot];
  const dx = (f.cx - g.cx) * U;
  const dy = (f.cy - g.cy) * U;
  const rimAnim = useAnimatedStyle(() => ({ opacity: 0.85 * glow.get(), borderColor: rim.get() }));
  const style = useAnimatedStyle(() => {
    const p = fan.get();
    const l = flight(p);
    const s = spot.get();
    return {
      opacity: fade.get(),
      transform: [
        { translateX: dx * p },
        { translateY: dy * p - (FAN.flightLift * l + SPOT.lift * s) * U },
        { rotate: `${f.rot * p}deg` },
        { scale: 1 + FAN.flightScale * l + SPOT.scale * s },
      ],
    };
  });
  return (
    <Animated.View
      style={[
        styles.abs,
        styles.clip,
        {
          left,
          top,
          width: g.d * U,
          height: g.d * U,
          borderRadius: (g.d / 2) * U,
          borderWidth: space.ring * U,
          borderColor: colors.ring,
        },
        style,
      ]}
    >
      <Image source={GUESTS[g.img]} style={styles.fill} resizeMode="cover" />
      {/* The fireworks catch the white ring: it warms to gold while they light the scene. */}
      <Animated.View
        style={[
          styles.abs,
          {
            left: -space.ring * U,
            top: -space.ring * U,
            width: g.d * U,
            height: g.d * U,
            borderRadius: (g.d / 2) * U,
            borderWidth: space.ring * U,
          },
          rimAnim,
        ]}
      />
    </Animated.View>
  );
}

/**
 * A line that rolls: with `from`/`to` (guest line) it shows when its index is one of them; without
 * (like count) index 0 is the outgoing line and index 1 the incoming one. Old goes up, new comes from below.
 */
function RollLine({
  index,
  from,
  to,
  p,
  rise,
  style,
  children,
}: {
  index: number;
  from: SharedValue<number> | null;
  to: SharedValue<number> | null;
  p: SharedValue<number>;
  rise: number;
  style: object[];
  children: string;
}) {
  const anim = useAnimatedStyle(() => {
    const k = p.get();
    const a = from ? from.get() : 0;
    const b = to ? to.get() : 1;
    if (a === b) return { opacity: index === a ? 1 : 0, transform: [{ translateY: 0 }] };
    if (index === a) return { opacity: 1 - k, transform: [{ translateY: -rise * k }] };
    if (index === b) return { opacity: k, transform: [{ translateY: rise * (1 - k) }] };
    return { opacity: 0, transform: [{ translateY: 0 }] };
  });
  return (
    <Animated.Text allowFontScaling={false} numberOfLines={1} style={[...style, anim]}>
      {children}
    </Animated.Text>
  );
}

/* ───────────────────────────── fireworks ───────────────────────────── */

/** Where sparks can be (ref px around the spire tip). */
const FIRE_BOUNDS = { x: -640, y: -260, width: 1280, height: 1250 };

/** Layers: per burst, per life bucket, core then glow; then the shells' core and glow. */
const LAYERS = FIRE.bursts.length * 6 + 2;

const OFF = { x: -1e4, y: -1e4 };

function addSparks(fire: number, out: SkPoint[][]) {
  'worklet';
  const a = { x: 0, y: 0 };
  const m = { x: 0, y: 0 };
  const h = { x: 0, y: 0 };
  const l = { angle: 0, speed: 0 };
  for (let b = 0; b < FIRE.bursts.length; b++) {
    const B = FIRE.bursts[b];
    // The shell rising to its burst point.
    const ts = fire - (B.at - FIRE.rise);
    if ((B.launchDx !== 0 || B.launchDy !== 0) && ts > 0 && ts < FIRE.rise) {
      const kh = shellLeft(ts);
      const kc = shellLeft(Math.max(0, ts - SPARK.shellCore));
      const kt = shellLeft(Math.max(0, ts - SPARK.shellTrail));
      const head = { x: B.dx + B.launchDx * kh, y: B.dy + B.launchDy * kh };
      out[LAYERS - 2].push({ x: B.dx + B.launchDx * kc, y: B.dy + B.launchDy * kc }, head);
      out[LAYERS - 1].push({ x: B.dx + B.launchDx * kt, y: B.dy + B.launchDy * kt }, head);
    }
    const tb = fire - B.at;
    if (tb <= 0) continue;
    for (let i = 0; i < B.count; i++) {
      const life = B.life * (SPARK.lifeBase + SPARK.lifeSpread * hash3(b, i, 1));
      if (tb >= life) continue;
      const rem = 1 - tb / life;
      const bucket = rem > 0.5 ? 0 : rem > B.crackle ? 1 : 2;
      if (bucket === 2 && hash3(b, i, 10 + Math.floor(tb * SPARK.flicker)) > SPARK.flickerKeep) continue;
      sparkLaunch(b, i, l);
      sparkOffset(l.speed, l.angle, Math.max(0, tb - B.trail), B.drag, B.gravity, a);
      sparkOffset(l.speed, l.angle, Math.max(0, tb - B.trail * SPARK.coreShare), B.drag, B.gravity, m);
      sparkOffset(l.speed, l.angle, tb, B.drag, B.gravity, h);
      // Stars leave the shell's break a little out from its centre, never all from one point.
      const ox = B.dx + Math.cos(l.angle) * SPARK.breakRadius;
      const oy = B.dy + Math.sin(l.angle) * SPARK.breakRadius;
      const head = { x: ox + h.x, y: oy + h.y };
      const base = b * 6 + bucket * 2;
      out[base].push({ x: ox + m.x, y: oy + m.y }, head);
      out[base + 1].push({ x: ox + a.x, y: oy + a.y }, head);
    }
  }
}

/** Every spark's segments `fire` seconds after ignition, in ref px around the spire tip. */
function buildSparks(fire: number): SkPoint[][] {
  'worklet';
  const out: SkPoint[][] = [];
  for (let j = 0; j < LAYERS; j++) out.push([]);
  if (fire >= 0 && fire < FIRE.end) addSparks(fire, out);
  // Never hand Skia an empty point list (an empty draw broke the paint state of the rest of the
  // frame: the scrim and the wordmark's fill went missing after a show); park one off screen.
  for (let j = 0; j < LAYERS; j++) if (out[j].length === 0) out[j].push(OFF, OFF);
  return out;
}

/** Fireworks over the spire, drawn in ref px with the tip at the origin. */
function Fireworks({ fire, fade }: { fire: DerivedValue<number>; fade: DerivedValue<number> }) {
  const sparks = useDerivedValue(() => buildSparks(fire.get()));
  /** One colour per burst (hot → burst colour → ember over its life), shared by its six layers. */
  const colours = useDerivedValue(() => {
    const f = fire.get();
    const out: string[] = [];
    for (let b = 0; b < FIRE.bursts.length; b++) {
      const B = FIRE.bursts[b];
      const p = Math.max(0, Math.min(1, (f - B.at) / B.life));
      out.push(interpolateColor(p, [0, 0.25, 1], [B.hot, B.color, B.ember]));
    }
    return out;
  });
  const glows: ReactNode[] = [];
  const cores: ReactNode[] = [];
  for (let b = 0; b < FIRE.bursts.length; b++) {
    for (let k = 0; k < 3; k++) {
      glows.push(<SparkLayer key={`g${b}${k}`} fade={fade} sparks={sparks} colours={colours} index={b * 6 + k * 2 + 1} burst={b} fire={fire} width={SPARK.glowWidth[k]} alpha={SPARK.glowAlpha[k]} blur />);
      cores.push(<SparkLayer key={`c${b}${k}`} fade={fade} sparks={sparks} colours={colours} index={b * 6 + k * 2} burst={b} fire={fire} width={SPARK.coreWidth[k]} alpha={SPARK.coreAlpha[k]} />);
    }
  }
  glows.push(<SparkLayer key="sg" fade={fade} sparks={sparks} colours={colours} index={LAYERS - 1} burst={-1} fire={fire} width={SPARK.glowWidth[0]} alpha={SPARK.glowAlpha[0]} blur />);
  cores.push(<SparkLayer key="sc" fade={fade} sparks={sparks} colours={colours} index={LAYERS - 2} burst={-1} fire={fire} width={SPARK.shellWidth} alpha={1} />);
  // The ten glow layers share one blur: they add (plus) into one layer that is blurred once and added
  // to the photo. Blur is linear, so this equals ten blurred layers, at a tenth of the blur passes
  // (ten full-area blurs per frame dropped frames as the willow opened).
  return (
    <>
      {/* The layer is clipped to where sparks can be, so its blur covers half the card, not all of it. */}
      <Group clip={FIRE_BOUNDS} layer={<Paint blendMode="plus"><Blur blur={SPARK.glowBlur} mode="decal" /></Paint>}>{glows}</Group>
      {cores}
    </>
  );
}

/** One layer of segments; its colour runs hot → burst colour → ember over the burst's life. */
function SparkLayer({
  fade,
  sparks,
  colours,
  index,
  burst,
  fire,
  width,
  alpha,
  blur = false,
}: {
  fade: DerivedValue<number>;
  sparks: DerivedValue<SkPoint[][]>;
  colours: DerivedValue<string[]>;
  index: number;
  burst: number;
  fire: DerivedValue<number>;
  width: number;
  alpha: number;
  blur?: boolean;
}) {
  const points = useDerivedValue(() => sparks.get()[index]);
  const color = useDerivedValue(() => (burst < 0 ? FIRE.bursts[0].hot : colours.get()[burst]));
  const opacity = useDerivedValue(() => {
    const k = fade.get();
    if (burst < 0) return alpha * k;
    const tb = fire.get() - FIRE.bursts[burst].at;
    if (!blur) return k * alpha * Math.max(0, Math.min(1, tb / SPARK.coreIn));
    return k * alpha * Math.max(0, Math.min(1, (tb - SPARK.glowDelay) / SPARK.glowIn));
  });
  return (
    <Points points={points} mode="lines" color={color} strokeWidth={width} strokeCap="round" style="stroke" opacity={opacity} blendMode="plus" />
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.page },
  abs: { position: 'absolute' },
  clip: { overflow: 'hidden' },
  fill: { width: '100%', height: '100%' },
  center: { textAlign: 'center' },
});
