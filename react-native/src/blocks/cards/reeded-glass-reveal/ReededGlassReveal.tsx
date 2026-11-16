// Reeded Glass Reveal — a portrait behind thick reeded glass above a black account capsule and panel (the pin).
// A finger (or the swap button, or the autoplay) combs the glass flat: a front travels across the 22 flutes, each
// flute lies down on its own spring, and the account number resolves exactly where the glass has cleared.
// The glass is <ReededGlass> (ReededGlass.tsx); this file is the account screen on top of it: one more canvas for the
// capsule's glass body and the panel, and ordinary views for the text, buttons and marks.
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { AccessibilityInfo, Alert, Platform, Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import Animated, {
  FadeIn,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  withSpring,
  withTiming,
  Easing,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  BlurMask,
  Canvas,
  FilterMode,
  ImageShader,
  LinearGradient as SkLinearGradient,
  MipmapMode,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  TileMode,
  useImage,
  vec,
} from '@shopify/react-native-skia';

import { press as cuePress } from './rgr-haptics';
import { LoopMark, NetworkMark, PlusGlyph, SableMark, SwapGlyph, VidlyMark } from './rgr-marks';
import { depthAt as depthAtEdges, LOOP, PRESS, type Look } from './rgr-motion';
import { CAPSULE_SKSL, srgbToP3 } from './rgr-shaders';
import { comp, COPY, type Layout, makeLayout, ref, sys } from './rgr-theme';
import { ReededGlass, type ReededReveal, useReduceMotionSetting, useReededReveal } from './ReededGlass';

export { LOOP };
export { ReededGlass, useReededReveal } from './ReededGlass';
export type { ReededGlassProps, ReededReveal } from './ReededGlass';

const capsuleFx = Skia.RuntimeEffect.Make(CAPSULE_SKSL);

const FONT = { light: ref.type.num.family, regular: ref.type.title.family };
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
const ADV = ref.advanceLight;

type Props = {
  autoPlay?: boolean;
  /** Hide the number again this long after it was shown (ms). Not used while autoplay runs: the loop re-masks. */
  autoMaskMs?: number;
  onReveal?: () => void;
  onMask?: () => void;
  onAddPlatform?: () => void;
  /** Glint strength 0..1 (default 1). */
  glints?: number;
  /** Haptics on a real touch (default true; autoplay is always silent). */
  haptics?: boolean;
};

/** The number's glyphs on the reference grid: x of each character, rest and cleared (Urbanist Light advances). */
function numberGlyphs() {
  const size = ref.type.num.size;
  const x0 = comp.num.x;
  const out: { ch: string; x: number; group: 'A' | 'B' | 'C' }[] = [];
  let x = x0;
  const groups: ('A' | 'B' | 'C')[] = ['A', 'B', 'C'];
  COPY.full.forEach((g, gi) => {
    for (const ch of g) {
      out.push({ ch, x, group: groups[gi] });
      x += ADV[ch] * size;
    }
    x += ADV[' '] * size;
  });
  const clearC = out.find((o) => o.group === 'C')!.x;
  const stars: number[] = [];
  let sx = comp.stars.x;
  for (let i = 0; i < 4; i++) {
    stars.push(sx);
    sx += ADV['*'] * size;
  }
  return { digits: out, stars, clearC, restC: comp.num.restX2644 };
}
const GLYPHS = numberGlyphs();

export function ReededGlassReveal({
  autoPlay = false,
  autoMaskMs = 8000,
  onReveal,
  onMask,
  onAddPlatform,
  glints = 1,
  haptics = true,
}: Props) {
  const [fontsLoaded] = useFonts({
    [FONT.light]: require('./assets/fonts/Urbanist-Light.ttf'),
    [FONT.regular]: require('./assets/fonts/Urbanist-Regular.ttf'),
  });
  const portrait = useImage(require('./assets/rgr-portrait.jpg'));
  const { width: W, height: H, fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReduceMotionSetting();
  const reveal = useReededReveal();
  const { look, depthAt } = reveal;
  const held = useSharedValue(1); // the swap button under a finger

  const L = useMemo(() => makeLayout(W, H, insets.bottom), [W, H, insets.bottom]);
  const imageRect = useMemo(() => ({ x: L.img.x, y: L.img.y, width: L.img.w, height: L.img.h }), [L]);
  const focus = useMemo(() => ({ x: W / 2, y: L.img.y + 300 * L.S }), [W, L]);

  const addPlatform = () => {
    if (onAddPlatform) onAddPlatform();
    else Alert.alert('Linked platforms', 'Vidly and Loop are linked in this preview.');
  };
  const onRevealedChange = (r: boolean) => {
    AccessibilityInfo.announceForAccessibility(r ? COPY.a11y.shown : COPY.a11y.hidden);
    if (r) onReveal?.();
    else onMask?.();
  };

  // ---- the capsule's glass and the panel ----
  const cap = useMemo(() => capsuleRect(L), [L]);
  const capStatic = useMemo(
    () => ({
      uImg: [L.img.x, L.img.y, L.img.w, L.img.h],
      uRect: [cap.x, cap.y, cap.w, cap.h],
      uR: comp.capsule.r * L.S,
      uBtn: [L.x(comp.swap.cx), L.yb(comp.swap.cy)],
      uBtnR: (comp.swap.d / 2) * L.S,
      uThumb: [L.x(comp.thumb.x), L.yb(comp.thumb.y), comp.thumb.w * L.S, comp.thumb.h * L.S],
      uTop: srgbToP3(sys.glass.top, Platform.OS === 'ios'),
      uLow: srgbToP3(sys.glass.low, Platform.OS === 'ios'),
      uEnd: srgbToP3(sys.glass.end, Platform.OS === 'ios'),
      uS: L.S,
    }),
    [L, cap],
  );
  const capLeft = cap.x + 6;
  const capRight = cap.x + cap.w - 6;
  // One light across both glasses: the front's specular continues along the capsule's top edge (60 ms late), and the
  // capsule's refracting ends sharpen as the front passes them.
  const capU = useDerivedValue(() => {
    const l = look.get();
    return { ...capStatic, uFront: l.lag, uEnds: [depthAt(capLeft), depthAt(capRight)] };
  });

  const panelY = L.yb(comp.panel.y);
  // The glass ends behind the capsule: below its middle it melts into the dark ground the capsule and the panel stand
  // on (the sweater's own black), so no refracted sliver shows between them or beside the panel.
  const groundTop = cap.y + cap.h * 0.3;
  const groundSolid = cap.y + cap.h - 2 * L.S;
  const glassAreas = useMemo(() => [{ x: 0, y: 0, w: W, h: Math.ceil(groundSolid + 1) }], [W, groundSolid]);
  // The capsule's thick ends refract a hazed copy of the portrait (sharp edges seen through 5 taps band into a fan).
  const haze = useMemo(() => {
    if (!portrait) return null;
    const iw = portrait.width();
    const ih = portrait.height();
    const surface = Skia.Surface.MakeOffscreen(iw, ih, { colorSpace: 'srgb' }) ?? Skia.Surface.Make(iw, ih);
    if (!surface) return null;
    const paint = Skia.Paint();
    const sigma = (comp.capsuleHaze * L.S * iw) / L.img.w;
    const filter = Skia.ImageFilter.MakeBlur(sigma, sigma, TileMode.Clamp);
    paint.setImageFilter(filter);
    surface.getCanvas().drawImage(portrait, 0, 0, paint);
    surface.flush();
    const snap = surface.makeImageSnapshot();
    const image = snap.makeNonTextureImage() ?? snap;
    paint.dispose();
    filter.dispose();
    surface.dispose();
    return image;
  }, [portrait, L.S, L.img.w]);
  const shown = reveal.ready && fontsLoaded && portrait !== null;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      {portrait ? (
        <ReededGlass
          source={portrait}
          reveal={reveal}
          style={StyleSheet.absoluteFill}
          imageRect={imageRect}
          focus={focus}
          drawAreas={glassAreas}
          hitHeight={cap.y}
          autoPlay={autoPlay}
          autoMaskMs={autoMaskMs}
          glints={glints}
          haptics={haptics}
          onRevealedChange={onRevealedChange}
          accessibilityLabel={COPY.a11y.glass}
        />
      ) : null}
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          {portrait !== null && haze !== null && capsuleFx !== null ? (
            <>
              <Rect x={0} y={groundTop} width={W} height={H - groundTop}>
                <SkLinearGradient
                  start={vec(0, groundTop)}
                  end={vec(0, groundSolid)}
                  colors={[sys.surface.groundClear, sys.surface.ground]}
                />
              </Rect>
              <RoundedRect
                x={cap.x + 6 * L.S}
                y={cap.y + comp.shadow.dy * L.S}
                width={cap.w - 12 * L.S}
                height={cap.h}
                r={comp.capsule.r * L.S}
                color={sys.glass.shadow}
                opacity={comp.shadow.alpha}
              >
                <BlurMask blur={comp.shadow.blur * L.S} style="normal" />
              </RoundedRect>
              <RoundedRect
                x={L.x(comp.panel.x)}
                y={panelY}
                width={comp.panel.w * L.S}
                height={H - panelY + 60}
                r={comp.panel.r * L.S}
                color={sys.surface.panel}
              />
              <Rect x={cap.x - 2} y={cap.y - 2} width={cap.w + 4} height={cap.h + 4}>
                <Shader source={capsuleFx} uniforms={capU}>
                  <ImageShader image={haze} fit="fill" rect={imageRect} sampling={SAMPLING} />
                </Shader>
              </Rect>
            </>
          ) : null}
        </Canvas>
      </View>

      {shown ? (
        <Animated.View entering={FadeIn.duration(220)} style={StyleSheet.absoluteFill} pointerEvents="box-none">
          {fontScale > 1.25 ? (
            <AccessibleDetails
              L={L}
              bottom={insets.bottom}
              revealed={reveal.revealed}
              targetRevealed={reveal.targetRevealed}
              onToggle={reveal.toggle}
              onAddPlatform={addPlatform}
              onHold={reveal.hold}
              onRelease={reveal.release}
            />
          ) : (
            <>
              <Capsule
                L={L}
                look={look}
                reveal={reveal}
                held={held}
                reduce={reduce}
                revealed={reveal.revealed}
                targetRevealed={reveal.targetRevealed}
                onToggle={reveal.toggle}
                onHold={reveal.hold}
                onRelease={reveal.release}
              />
              <Panel L={L} reduce={reduce} onAddPlatform={addPlatform} onHold={reveal.hold} onRelease={reveal.release} />
            </>
          )}
        </Animated.View>
      ) : null}
    </View>
  );
}

function capsuleRect(L: Layout) {
  return { x: L.x(comp.capsule.x), y: L.yb(comp.capsule.y), w: comp.capsule.w * L.S, h: comp.capsule.h * L.S };
}

/** Text placed by its baseline on the reference grid (Urbanist: ascent 0.95 em, line box 1.2 em). */
function textBox(L: Layout, x: number, base: number, size: number) {
  const s = size * L.S;
  return {
    position: 'absolute' as const,
    left: L.x(x),
    top: L.yb(base) - ref.ascent * s,
    fontSize: s,
    lineHeight: (ref.ascent + ref.descent) * s,
  };
}

// ---------------------------------------------------------------------------------------------------------------
// The capsule: card thumbnail, "Bank Account", the number that resolves with the glass, the swap button.

type CapsuleProps = {
  L: Layout;
  look: SharedValue<Look>;
  reveal: ReededReveal;
  held: SharedValue<number>;
  reduce: boolean;
  revealed: boolean;
  targetRevealed: boolean;
  onToggle: () => void;
  onHold: () => void;
  onRelease: () => void;
};

function Capsule({ L, look, reveal, held, reduce, revealed, targetRevealed, onToggle, onHold, onRelease }: CapsuleProps) {
  const S = L.S;
  const btn = comp.swap;
  const btnStyle = useAnimatedStyle(() => (reduce ? { opacity: held.get() } : { transform: [{ scale: look.get().press * held.get() }] }));
  const glyphStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${reduce ? 0 : look.get().glyph}deg` }] }));
  return (
    <>
      <View
        style={{
          position: 'absolute',
          left: L.x(comp.capsule.x),
          top: L.yb(comp.capsule.y),
          width: comp.capsule.w * S,
          height: comp.capsule.h * S,
        }}
        accessible
        accessibilityLabel={revealed ? COPY.a11y.shown : COPY.a11y.hidden}
        pointerEvents="none"
      >
        <View style={{ position: 'absolute', left: -L.x(comp.capsule.x), top: -L.yb(comp.capsule.y) }}>
          <Thumb L={L} look={look} />
          <Text
            maxFontSizeMultiplier={1.25}
            style={[
              styles.title,
              textBox(L, comp.title1.x, comp.title1.base, ref.type.title.size),
              { letterSpacing: ref.type.title.tracking * ref.type.title.size * S },
            ]}
          >
            {COPY.title1}
          </Text>
          <NumberLine L={L} look={look} reveal={reveal} reduce={reduce} />
        </View>
      </View>
      <Animated.View
        style={[
          styles.round,
          {
            left: L.x(btn.cx - btn.d / 2),
            top: L.yb(btn.cy) - (btn.d / 2) * S,
            width: btn.d * S,
            height: btn.d * S,
            backgroundColor: ref.color.swap,
          },
          btnStyle,
        ]}
      >
        <Pressable
          style={styles.fill}
          accessibilityRole="button"
          accessibilityLabel={targetRevealed ? COPY.a11y.hide : COPY.a11y.reveal}
          onPressIn={() => {
            onHold();
            held.set(
              withTiming(reduce ? 0.82 : PRESS.finger, {
                duration: 100,
                easing: Easing.bezier(0.23, 1, 0.32, 1),
                reduceMotion: ReduceMotion.Never,
              }),
            );
            cuePress();
          }}
          onPressOut={() => {
            onRelease();
            held.set(
              reduce
                ? withTiming(1, { duration: 160, reduceMotion: ReduceMotion.Never })
                : withSpring(1, { damping: 30.5, stiffness: 322.3, mass: 1 }),
            );
          }}
          onPress={onToggle}
        >
          <Animated.View style={glyphStyle}>
            <SwapGlyph size={S} />
          </Animated.View>
        </Pressable>
      </Animated.View>
    </>
  );
}

// The number rolls glyph by glyph as the glass clears over it, like an odometer: each "*" rolls up and out while its
// digit rolls up from below, so the two never print over each other. "2644" slides right first; the digits that its
// masked place covers ("1730", and the "9") only roll in once it has passed them, and roll out before it slides back.
// Every style reads the shared values itself (a nested worklet's own shared values are not mapper inputs).

/** 0 (masked) .. 1 (clear) for a glyph under glass of depth d; the digits' spring overshoot clamps away. */
function rollOf(d: number) {
  'worklet';
  const v = Math.min(Math.max((0.9 - d) / 0.8, 0), 1);
  return v * v * (3 - 2 * v);
}

function band(a: number, b: number, x: number) {
  'worklet';
  const v = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return v * v * (3 - 2 * v);
}

/** How far "2644" has slid (0 masked place .. 1 its place in the full number), from the glass of rib 5. */
function slideOf(depths: number[], edges: number[], x: number) {
  'worklet';
  return Math.min(1.1, Math.max(-0.1, 1 - depthAtEdges(depths, x, edges)));
}

function NumberLine({ L, look, reveal, reduce }: { L: Layout; look: SharedValue<Look>; reveal: ReededReveal; reduce: boolean }) {
  const size = ref.type.num.size;
  // a digit under the masked "2644" waits until the slide has carried it past (its right edge + 1.5 pt)
  const restL = GLYPHS.restC;
  const dx = GLYPHS.clearC - GLYPHS.restC;
  return (
    <>
      {GLYPHS.stars.map((x, i) => (
        <Star key={`s${i}`} L={L} look={look} edges={reveal.edges} x={x} size={size} reduce={reduce} />
      ))}
      {GLYPHS.digits
        .filter((d) => d.group !== 'C')
        .map((d, i) => {
          const right = d.x + ADV[d.ch] * size;
          const gateAt = right + 1.5 > restL ? (right + 1.5 - restL) / dx : -1;
          return (
            <Digit key={`d${i}`} L={L} look={look} edges={reveal.edges} ch={d.ch} x={d.x} size={size} reduce={reduce} gateAt={gateAt} />
          );
        })}
      <LastFour L={L} look={look} edges={reveal.edges} size={size} reduce={reduce} />
    </>
  );
}

type GlyphProps = {
  L: Layout;
  look: SharedValue<Look>;
  edges: SharedValue<number[]>;
  x: number;
  size: number;
  reduce: boolean;
};

function Star({ L, look, edges, x, size, reduce }: GlyphProps) {
  const at = L.x(x + (ADV['*'] * size) / 2);
  const rise = 10 * L.S;
  // the old glyph rolls up and out as the glass clears above it; gone before its digit is half way in
  const style = useAnimatedStyle(() => {
    const p = rollOf(depthAtEdges(look.get().depths, at, edges.get()));
    return { opacity: 1 - band(0, reduce ? 0.5 : 0.55, p), transform: [{ translateY: reduce ? 0 : -p * rise }] };
  });
  return (
    <Animated.Text
      style={[styles.num, textBox(L, x, comp.num.base + comp.stars.drop, size), style]}
      accessible={false}
      allowFontScaling={false}
    >
      *
    </Animated.Text>
  );
}

function Digit({ L, look, edges, ch, x, size, reduce, gateAt }: GlyphProps & { ch: string; gateAt: number }) {
  const at = L.x(x + (ADV[ch] * size) / 2);
  const slideAt = L.x(110);
  const rise = 12 * L.S;
  // the new glyph rolls up from below once its star has mostly gone; under a peek window it shows at 25 %
  const style = useAnimatedStyle(() => {
    const lk = look.get();
    const e = edges.get();
    let p = rollOf(depthAtEdges(lk.depths, at, e));
    if (gateAt >= 0) p = Math.min(p, band(Math.min(gateAt, 0.9), Math.min(gateAt + 0.1, 0.99), slideOf(lk.depths, e, slideAt)));
    const o = band(reduce ? 0.5 : 0.4, 1, p);
    return { opacity: Math.min(o, 1 - 0.75 * lk.peek), transform: [{ translateY: reduce ? 0 : (1 - p) * rise }] };
  });
  return (
    <Animated.Text style={[styles.num, textBox(L, x, comp.num.base, size), style]} accessible={false} allowFontScaling={false}>
      {ch}
    </Animated.Text>
  );
}

/** "2644" slides from its masked place to its place in the full number, on the glass of rib 5 (just ahead of the digits). */
function LastFour({ L, look, edges, size, reduce }: Omit<GlyphProps, 'x'>) {
  const at = L.x(110);
  const dx = (GLYPHS.clearC - GLYPHS.restC) * L.S;
  const style = useAnimatedStyle(() => {
    const s = slideOf(look.get().depths, edges.get(), at);
    return { opacity: 1, transform: [{ translateX: s * dx }] };
  });
  // Reduce Motion: one string out, then the other in; never both at once
  const maskedStyle = useAnimatedStyle(() => ({ opacity: band(0.5, 1, look.get().mean) }));
  const clearStyle = useAnimatedStyle(() => ({ opacity: 1 - band(0, 0.5, look.get().mean) }));
  if (reduce) {
    return (
      <>
        <Animated.Text
          style={[styles.num, textBox(L, GLYPHS.restC, comp.num.base, size), maskedStyle]}
          accessible={false}
          allowFontScaling={false}
        >
          {COPY.last4}
        </Animated.Text>
        <Animated.Text
          style={[styles.num, textBox(L, GLYPHS.clearC, comp.num.base, size), clearStyle]}
          accessible={false}
          allowFontScaling={false}
        >
          {COPY.last4}
        </Animated.Text>
      </>
    );
  }
  return (
    <Animated.Text style={[styles.num, textBox(L, GLYPHS.restC, comp.num.base, size), style]} accessible={false} allowFontScaling={false}>
      {COPY.last4}
    </Animated.Text>
  );
}

/** The bank card thumbnail inside the capsule (Sable, frosted sage). Decorative. */
function Thumb({ L, look }: { L: Layout; look: SharedValue<Look> }) {
  const S = L.S;
  const t = comp.thumb;
  const tx = (x: number) => x * S;
  // the card's frost drops to 60 % when the glass above it is clear
  const frost = useAnimatedStyle(() => ({ opacity: 0.6 + 0.4 * Math.min(1, Math.max(0, look.get().mean)) }));
  const tt = (base: number, size: number) => ({
    position: 'absolute' as const,
    left: 0,
    top: base * S - ref.ascent * size * S,
    fontSize: size * S,
    lineHeight: 1.2 * size * S,
  });
  return (
    <View
      style={[styles.thumb, { left: L.x(t.x), top: L.yb(t.y), width: t.w * S, height: t.h * S, borderRadius: t.r * S }]}
      accessible={false}
      importantForAccessibility="no-hide-descendants"
    >
      <LinearGradient
        colors={[ref.color.thumbA, ref.color.thumbB, ref.color.thumbC]}
        locations={[0, 0.55, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      <Animated.View style={[StyleSheet.absoluteFill, frost]} pointerEvents="none">
        <LinearGradient
          colors={['rgba(255,255,255,0.22)', 'rgba(255,255,255,0)']}
          start={{ x: 0.1, y: 0 }}
          end={{ x: 0.6, y: 0.7 }}
          style={StyleSheet.absoluteFill}
        />
      </Animated.View>
      <Text allowFontScaling={false} style={[styles.thumbBrand, tt(13.6, 4.2), { left: tx(6) }]}>
        {COPY.thumb.brand}
      </Text>
      <View style={{ position: 'absolute', left: tx(72.6), top: tx(9) }}>
        <SableMark size={S * 1.25} />
      </View>
      <Text allowFontScaling={false} style={[styles.thumbMute, tt(30.4, 4.0), { left: tx(6) }]}>
        {COPY.thumb.groups}
      </Text>
      <Text allowFontScaling={false} style={[styles.thumbNum, tt(30.9, 6.3), { left: tx(37.5) }]}>
        {COPY.thumb.last4}
      </Text>
      <Text allowFontScaling={false} style={[styles.thumbMute, tt(44.4, 3.8), { left: tx(6) }]}>
        {COPY.thumb.expLabel}
      </Text>
      <Text allowFontScaling={false} style={[styles.thumbBrand, tt(49.9, 3.8), { left: tx(6) }]}>
        {COPY.thumb.exp}
      </Text>
      <View style={{ position: 'absolute', left: tx(68.2), top: tx(42.5) }}>
        <NetworkMark size={S * 1.32} />
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------------------------------------------
// The panel: "Authenticated platform" with its + button and the two linked platforms. It never animates.

function Panel({
  L,
  reduce,
  onAddPlatform,
  onHold,
  onRelease,
}: {
  L: Layout;
  reduce: boolean;
  onAddPlatform: () => void;
  onHold: () => void;
  onRelease: () => void;
}) {
  const S = L.S;
  const p = comp.plus;
  const r = comp.row;
  const pressed = useSharedValue(1);
  const plusStyle = useAnimatedStyle(() => (reduce ? { opacity: pressed.get() } : { transform: [{ scale: pressed.get() }] }));
  return (
    <>
      <Text
        style={[
          styles.title,
          textBox(L, comp.title2.x, comp.title2.base, ref.type.title.size),
          { letterSpacing: ref.type.title.tracking * ref.type.title.size * S },
        ]}
        accessibilityRole="header"
      >
        {COPY.title2}
      </Text>
      <Animated.View
        style={[
          styles.round,
          {
            left: L.x(p.cx - p.d / 2),
            top: L.yb(p.cy) - (p.d / 2) * S,
            width: p.d * S,
            height: p.d * S,
            backgroundColor: sys.surface.plusWell,
          },
          plusStyle,
        ]}
      >
        <Pressable
          style={styles.fill}
          accessibilityRole="button"
          accessibilityLabel={COPY.a11y.add}
          onPressIn={() => {
            onHold();
            pressed.set(
              withTiming(reduce ? 0.82 : PRESS.finger, {
                duration: 100,
                easing: Easing.bezier(0.23, 1, 0.32, 1),
                reduceMotion: ReduceMotion.Never,
              }),
            );
            cuePress();
          }}
          onPressOut={() => {
            onRelease();
            pressed.set(
              reduce
                ? withTiming(1, { duration: 160, reduceMotion: ReduceMotion.Never })
                : withSpring(1, { damping: 30.5, stiffness: 322.3, mass: 1 }),
            );
          }}
          onPress={onAddPlatform}
        >
          <PlusGlyph size={S} />
        </Pressable>
      </Animated.View>
      {COPY.rows.map((row, i) => {
        const g = comp.rows[i];
        const rowTop = L.yb(g.cy) - (r.wellD / 2) * S;
        return (
          <View
            key={row.name}
            style={{ position: 'absolute', left: 0, top: rowTop, width: L.W, height: r.wellD * S }}
            pointerEvents="none"
            accessible
            accessibilityLabel={`${row.name}, ${row.followers.replace('  ', ' ')}, ${row.value}`}
          >
            <View style={{ position: 'absolute', left: 0, top: -rowTop, width: L.W, height: L.H }}>
              <View
                style={[
                  styles.round,
                  {
                    left: L.x(r.wellCx - r.wellD / 2),
                    top: L.yb(g.cy) - (r.wellD / 2) * S,
                    width: r.wellD * S,
                    height: r.wellD * S,
                    backgroundColor: sys.surface.well,
                  },
                ]}
              >
                {i === 0 ? <VidlyMark size={S} /> : <LoopMark size={S} />}
              </View>
              <Text maxFontSizeMultiplier={1.15} style={[styles.row, textBox(L, r.name, g.base, ref.type.row.size)]}>
                {row.name}
              </Text>
              <Text maxFontSizeMultiplier={1.15} style={[styles.rowMute, textBox(L, r.followers, g.base, ref.type.row.size)]}>
                {row.followers}
              </Text>
              <Text
                maxFontSizeMultiplier={1.15}
                style={[
                  styles.rowValue,
                  textBox(L, 0, g.base, ref.type.rowValue.size),
                  { left: undefined, right: L.W - L.x(r.valueRight) },
                ]}
              >
                {row.value}
              </Text>
            </View>
          </View>
        );
      })}
    </>
  );
}

/** Reflow only at larger text sizes; the pin's measured composition stays exact at its default size. */
function AccessibleDetails({
  L,
  bottom,
  revealed,
  targetRevealed,
  onToggle,
  onAddPlatform,
  onHold,
  onRelease,
}: {
  L: Layout;
  bottom: number;
  revealed: boolean;
  targetRevealed: boolean;
  onToggle: () => void;
  onAddPlatform: () => void;
  onHold: () => void;
  onRelease: () => void;
}) {
  return (
    <ScrollView
      style={{
        position: 'absolute',
        left: 16,
        right: 16,
        top: Math.min(L.yb(comp.capsule.y), L.H * 0.4),
        bottom: 0,
        backgroundColor: sys.surface.panel,
        borderTopLeftRadius: 28,
        borderTopRightRadius: 28,
      }}
      contentContainerStyle={{ padding: 22, paddingBottom: Math.max(bottom, 24) }}
      showsVerticalScrollIndicator
    >
      <View style={styles.adaptiveHeading}>
        <Text accessibilityRole="header" style={[styles.title, styles.adaptiveTitle]}>
          {COPY.title1}
        </Text>
        <Pressable
          style={[styles.adaptiveControl, { backgroundColor: ref.color.swap }]}
          accessibilityRole="button"
          accessibilityLabel={targetRevealed ? COPY.a11y.hide : COPY.a11y.reveal}
          onPressIn={onHold}
          onPressOut={onRelease}
          onPress={() => {
            cuePress();
            onToggle();
          }}
        >
          <SwapGlyph size={1} />
        </Pressable>
      </View>
      <Text style={[styles.num, styles.adaptiveNumber]} accessibilityLabel={revealed ? COPY.a11y.shown : COPY.a11y.hidden}>
        {revealed ? COPY.full.join(' ') : `${COPY.stars} ${COPY.last4}`}
      </Text>
      <View style={[styles.adaptiveHeading, { marginTop: 28 }]}>
        <Text accessibilityRole="header" style={[styles.title, styles.adaptiveTitle]}>
          {COPY.title2}
        </Text>
        <Pressable
          style={[styles.adaptiveControl, { backgroundColor: sys.surface.plusWell }]}
          accessibilityRole="button"
          accessibilityLabel={COPY.a11y.add}
          onPressIn={onHold}
          onPressOut={onRelease}
          onPress={() => {
            cuePress();
            onAddPlatform();
          }}
        >
          <PlusGlyph size={1} />
        </Pressable>
      </View>
      {COPY.rows.map((row, i) => (
        <View
          key={row.name}
          style={styles.adaptiveRow}
          accessible
          accessibilityLabel={`${row.name}, ${row.followers.replace('  ', ' ')}, ${row.value}`}
        >
          <View style={[styles.adaptiveControl, { backgroundColor: sys.surface.well }]} accessible={false}>
            {i === 0 ? <VidlyMark size={1} /> : <LoopMark size={1} />}
          </View>
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={[styles.row, styles.adaptiveText]}>{row.name}</Text>
            <Text style={[styles.rowMute, styles.adaptiveText]}>{row.followers.replace('  ', ' ')}</Text>
            <Text style={[styles.rowValue, styles.adaptiveText]}>{row.value}</Text>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: ref.color.wall, overflow: 'hidden' },
  glassHit: { position: 'absolute', left: 0, top: 0, right: 0 },
  fill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  round: { position: 'absolute', borderRadius: 999, alignItems: 'center', justifyContent: 'center' },
  title: { fontFamily: FONT.regular, color: sys.ink.primary },
  num: { fontFamily: FONT.light, color: sys.ink.secondary },
  row: { fontFamily: FONT.light, color: sys.ink.primary },
  rowMute: { fontFamily: FONT.light, color: sys.ink.secondary },
  rowValue: { fontFamily: FONT.regular, color: sys.ink.primary },
  thumb: {
    position: 'absolute',
    overflow: 'hidden',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.32)',
    borderCurve: 'continuous',
  },
  thumbBrand: { fontFamily: FONT.regular, color: ref.color.thumbInk },
  thumbMute: { fontFamily: FONT.regular, color: ref.color.thumbMute },
  thumbNum: { fontFamily: FONT.regular, color: ref.color.thumbInk },
  adaptiveHeading: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  adaptiveTitle: { flex: 1, minWidth: 0, fontSize: 17, lineHeight: 23 },
  adaptiveNumber: { fontSize: 17, lineHeight: 24, marginTop: 12 },
  adaptiveControl: { width: 52, height: 52, flexShrink: 0, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  adaptiveRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 16, marginTop: 24 },
  adaptiveText: { fontSize: 16, lineHeight: 24 },
});
