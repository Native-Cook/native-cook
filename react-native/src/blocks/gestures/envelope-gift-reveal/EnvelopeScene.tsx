/**
 * Drawn layer: the linen table under the lamp, the envelope (back, liner, folding flap, the card inside, the pocket
 * with "for Sam" in pen, the wax seal), and the card on its way out, landing, and turning over to the note.
 * Paper and linen carry their own grain (two small shaders), so nothing reads as a flat fill.
 */
import {
  Blur, Canvas, Circle, Group, LinearGradient, Oval, Path, RadialGradient, Rect, RoundedRect, Shader, Skia, Text, useFont, vec,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import { AMBIENT, OPEN, REVEAL } from './motion';
import { COLORS, GIFT, LAYOUT, type ScreenLayout } from './theme';
import { finishMs, type EnvelopeValues } from './use-envelope';

const EW = LAYOUT.envW;
const EH = LAYOUT.envH;
const CW = LAYOUT.cardW;
const CH = LAYOUT.cardH;

const SERIF = require('./assets/InstrumentSerif-Regular.ttf');
const HAND = require('./assets/CoveredByYourGrace.ttf');
const UI = require('./assets/Inter-SemiBold.ttf');

/** Linen: a fine weave, slow mottling, a lamp from the upper left and a vignette. */
const LINEN = Skia.RuntimeEffect.Make(`
uniform float2 uSize;
float h(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float n(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(h(i), h(i + float2(1.0, 0.0)), u.x), mix(h(i + float2(0.0, 1.0)), h(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 xy) {
  float2 q = xy / uSize;
  float3 base = float3(0.114, 0.106, 0.094);
  float lamp = exp(-dot(q - float2(0.22, 0.12), q - float2(0.22, 0.12)) * 3.2);
  float vig = smoothstep(1.15, 0.35, length(q - float2(0.45, 0.42)));
  float weave = 0.5 + 0.5 * sin(xy.x * 2.6) * sin(xy.y * 2.6);
  float slub = n(float2(xy.x * 0.05, xy.y * 1.4)) * 0.6 + n(float2(xy.x * 1.4, xy.y * 0.05)) * 0.4;
  float3 c = base * (0.78 + 0.32 * vig) + float3(0.17, 0.13, 0.08) * lamp;
  c *= 0.94 + 0.05 * weave + 0.06 * slub + 0.03 * (h(floor(xy * 2.0)) - 0.5);
  return half4(half3(c), 1.0);
}
`)!;

/** Cotton paper's grain, drawn over paper as a faint warm darkening: tooth, mottling and a few fibres. */
const GRAIN = Skia.RuntimeEffect.Make(`
uniform float uSeed;
float h(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float n(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(h(i), h(i + float2(1.0, 0.0)), u.x), mix(h(i + float2(0.0, 1.0)), h(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 xy) {
  float2 p = xy + uSeed;
  float tooth = h(floor(p * 3.0));
  float mottle = n(p * 0.07) * 0.6 + n(p * 0.21) * 0.4;
  float fine = n(p * 0.6 + 31.0);
  float a = 0.03 * tooth + 0.05 * mottle + 0.025 * fine;
  return half4(0.30, 0.22, 0.10, 1.0) * a;
}
`)!;

const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};
const easeOut = (t: number) => {
  'worklet';
  return 1 - Math.pow(1 - clamp01(t), 3);
};
const easeInOut = (t: number) => {
  'worklet';
  const c = clamp01(t);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
};
const easeOutBack = (t: number, k: number) => {
  'worklet';
  const u = clamp01(t) - 1;
  return 1 + (k + 1) * u * u * u + k * u * u;
};

/** Everything the scene needs for one frame, from the clock. */
export function frameOf(v: EnvelopeValues, reduced: boolean) {
  'worklet';
  const now = v.clock.get();
  const c = v.commit.get();
  let p = v.p.get();
  let fly = 0;
  let after = -1;
  if (c.at >= 0) {
    const fin = finishMs(c.p0, reduced);
    p = c.p0 + (1 - c.p0) * easeOut((now - c.at) / fin);
    after = now - c.at - fin;
    fly = reduced ? (after >= 0 ? 1 : 0) : REVEAL.flyEase(clamp01(after / REVEAL.flyMs));
  }
  const r = v.resetAt.get();
  const reset = r < 0 ? 0 : clamp01((now - r) / (reduced ? 200 : 420));
  const resetOut = clamp01(reset / 0.4);
  const resetIn = clamp01((reset - 0.43) / (1 - 0.43));
  const page = after < 0 ? 0 : clamp01((after + 100) / (reduced ? 200 : REVEAL.pageMs));
  const drop = after < 0 || reduced ? (after >= 0 ? 1 : 0) : clamp01((after - 60) / REVEAL.envelopeDropMs);
  // The card turns over once it has landed; Reduce Motion shows the note side at once.
  const flipT = after < 0 ? 0 : reduced ? 1 : clamp01((after - REVEAL.flyMs - REVEAL.flipDelay) / REVEAL.flipMs);
  const b = v.breath.get();
  const breath = c.at < 0 && !reduced && b < 700 ? Math.sin((Math.PI * b) / 700) * OPEN.breathDeg : 0;
  return { now, p, fly, after, reset, resetOut, resetIn, page, drop, flipT, breath, tilt0: c.at >= 0 ? c.tilt0 : v.tilt.get() };
}

function flapPath() {
  const tip = EH * LAYOUT.flapTip;
  return Skia.PathBuilder.Make()
    .moveTo(0, 0).lineTo(EW, 0).lineTo(EW / 2 + 26, tip - 12).quadTo(EW / 2, tip + 4, EW / 2 - 26, tip - 12).close().build();
}
const FLAP = flapPath();
const LEFT = Skia.PathBuilder.Make().moveTo(0, 0).lineTo(EW * 0.47, EH * 0.56).lineTo(0, EH).close().build();
const RIGHT = Skia.PathBuilder.Make().moveTo(EW, 0).lineTo(EW * 0.53, EH * 0.56).lineTo(EW, EH).close().build();
const POCKET = Skia.PathBuilder.Make().moveTo(0, EH).quadTo(EW * 0.3, EH * 0.5, EW / 2, EH * 0.44).quadTo(EW * 0.7, EH * 0.5, EW, EH).close().build();

/** An irregular blob of wax: a circle whose radius wanders where the wax spread before it was pressed. */
function waxPath(r: number) {
  const b = Skia.PathBuilder.Make();
  const N = 72;
  for (let k = 0; k <= N; k++) {
    const a = (k / N) * Math.PI * 2;
    const rr = r * (1 + 0.05 * Math.sin(5 * a + 0.6) + 0.035 * Math.sin(9 * a + 2.1) + 0.02 * Math.sin(14 * a + 4));
    if (k === 0) b.moveTo(Math.cos(a) * rr, Math.sin(a) * rr);
    else b.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
  }
  return b.close().build();
}
const WAX = waxPath(LAYOUT.sealD / 2);

function Grain({ w, h, seed }: { w: number; h: number; seed: number }) {
  return (
    <Rect x={0} y={0} width={w} height={h}>
      <Shader source={GRAIN} uniforms={{ uSeed: seed }} />
    </Rect>
  );
}

/** The card's printed face: a bookshop's gift card, letterpressed in green on heavy cotton. */
function CardFront() {
  const shop = useFont(SERIF, 32);
  const amount = useFont(SERIF, 60);
  const small = useFont(UI, 9);
  return (
    <Group>
      <RoundedRect x={-CW / 2} y={-CH / 2} width={CW} height={CH} r={LAYOUT.cardR} color={COLORS.card} />
      <Group clip={{ rect: { x: -CW / 2, y: -CH / 2, width: CW, height: CH }, rx: LAYOUT.cardR, ry: LAYOUT.cardR }}>
        <Group transform={[{ translateX: -CW / 2 }, { translateY: -CH / 2 }]}><Grain w={CW} h={CH} seed={31} /></Group>
      </Group>
      {/* A double rule inset 12 pt, as a printer would set it. */}
      <RoundedRect x={-CW / 2 + 12} y={-CH / 2 + 12} width={CW - 24} height={CH - 24} r={3} color={COLORS.letterpress} style="stroke" strokeWidth={1} opacity={0.7} />
      <RoundedRect x={-CW / 2 + 15} y={-CH / 2 + 15} width={CW - 30} height={CH - 30} r={2} color={COLORS.letterpress} style="stroke" strokeWidth={0.5} opacity={0.5} />
      {shop && <Text text={GIFT.shop} x={-CW / 2 + 28} y={-CH / 2 + 56} font={shop} color={COLORS.letterpress} />}
      {small && <Text text={GIFT.shopLine} x={-CW / 2 + 29} y={-CH / 2 + 74} font={small} color={COLORS.cardMute} />}
      {small && <Text text="GIFT CARD" x={-CW / 2 + 29} y={CH / 2 - 30} font={small} color={COLORS.cardMute} />}
      {/* Letterpress: the type sits in the paper, a hair of light on its lower edge. */}
      {amount && <Text text={GIFT.amount} x={CW / 2 - 28 - amount.getTextWidth(GIFT.amount)} y={CH / 2 - 26 + 0.8} font={amount} color="rgba(255,255,255,0.7)" />}
      {amount && <Text text={GIFT.amount} x={CW / 2 - 28 - amount.getTextWidth(GIFT.amount)} y={CH / 2 - 26} font={amount} color={COLORS.letterpress} />}
      <RoundedRect x={-CW / 2 + 0.5} y={-CH / 2 + 0.5} width={CW - 1} height={CH - 1} r={LAYOUT.cardR} color={COLORS.cardEdge} style="stroke" strokeWidth={1} />
    </Group>
  );
}

/** The card's back: Nora's note in blue ink, and the amount small in the corner. */
function CardBack() {
  const hand = useFont(HAND, 25);
  const small = useFont(UI, 9);
  const amount = useFont(SERIF, 22);
  return (
    <Group>
      <RoundedRect x={-CW / 2} y={-CH / 2} width={CW} height={CH} r={LAYOUT.cardR} color={COLORS.card} />
      <Group clip={{ rect: { x: -CW / 2, y: -CH / 2, width: CW, height: CH }, rx: LAYOUT.cardR, ry: LAYOUT.cardR }}>
        <Group transform={[{ translateX: -CW / 2 }, { translateY: -CH / 2 }]}><Grain w={CW} h={CH} seed={77} /></Group>
      </Group>
      {hand && GIFT.note.map((line, i) => (
        <Text key={i} text={line} x={-CW / 2 + 26 + (i === GIFT.note.length - 1 ? 150 : 0) + (i % 2) * 2} y={-CH / 2 + 44 + i * 32 + (i === GIFT.note.length - 1 ? 4 : 0)}
          font={hand} color={COLORS.pen} opacity={0.92} />
      ))}
      {amount && <Text text={GIFT.amount} x={CW / 2 - 22 - amount.getTextWidth(GIFT.amount)} y={-CH / 2 + 34} font={amount} color={COLORS.letterpress} />}
      {small && <Text text="FABLE & FERN" x={CW / 2 - 22 - small.getTextWidth('FABLE & FERN')} y={-CH / 2 + 48} font={small} color={COLORS.cardMute} />}
      <RoundedRect x={-CW / 2 + 0.5} y={-CH / 2 + 0.5} width={CW - 1} height={CH - 1} r={LAYOUT.cardR} color={COLORS.cardEdge} style="stroke" strokeWidth={1} />
    </Group>
  );
}

/** The wax seal: an oxblood blob pressed with an N. As the flap lifts it swells, then cracks along the flap's line. */
function Seal({ split }: { split: { get(): number } }) {
  const r = LAYOUT.sealD / 2;
  const mono = useFont(SERIF, 30);
  const w = mono ? mono.getTextWidth('N') : 0;
  const face = (
    <>
      <Path path={WAX}>
        <RadialGradient c={vec(-r * 0.35, -r * 0.4)} r={r * 1.6} colors={[COLORS.waxHigh, COLORS.wax, COLORS.waxLow]} positions={[0, 0.45, 1]} />
      </Path>
      {/* The die's ring, pressed in: dark on its upper inner edge, lit on its lower. */}
      <Circle cx={0} cy={0} r={r * 0.7} color={COLORS.waxLow} style="stroke" strokeWidth={1.6} opacity={0.75} />
      <Circle cx={0.6} cy={0.8} r={r * 0.7} color={COLORS.waxHigh} style="stroke" strokeWidth={0.8} opacity={0.6} />
      {mono && <Text text="N" x={-w / 2 + 0.7} y={10.8} font={mono} color={COLORS.waxHigh} opacity={0.7} />}
      {mono && <Text text="N" x={-w / 2} y={10} font={mono} color={COLORS.waxLow} />}
      <Oval x={-r * 0.62} y={-r * 0.7} width={r * 0.5} height={r * 0.26} color="rgba(255,235,225,0.32)"><Blur blur={1.5} /></Oval>
    </>
  );
  const pop = useDerivedValue(() => [{ scale: 1 + 0.08 * Math.sin(Math.PI * clamp01(split.get() * 2.5)) }]);
  const leftT = useDerivedValue(() => [{ translateX: -12 * split.get() }, { translateY: 10 * split.get() * split.get() }, { rotate: -0.3 * split.get() }]);
  const rightT = useDerivedValue(() => [{ translateX: 12 * split.get() }, { translateY: 12 * split.get() * split.get() }, { rotate: 0.36 * split.get() }]);
  const halves = useDerivedValue(() => 1 - clamp01((split.get() - 0.55) / 0.45));
  return (
    <Group transform={[{ translateX: EW / 2 }, { translateY: EH * LAYOUT.sealY }]}>
      <Path path={WAX} color="rgba(40,10,10,0.35)" transform={[{ translateY: 2.5 }]} opacity={halves}><Blur blur={3} /></Path>
      <Group transform={pop} opacity={halves}>
        <Group transform={leftT} clip={Skia.XYWHRect(-r - 4, -r - 4, r + 4.5, r * 2 + 8)}>{face}</Group>
        <Group transform={rightT} clip={Skia.XYWHRect(0, -r - 4, r + 4, r * 2 + 8)}>{face}</Group>
      </Group>
    </Group>
  );
}

export function EnvelopeScene({ v, width, height, reduced, lay }: { v: EnvelopeValues; width: number; height: number; reduced: boolean; lay: ScreenLayout }) {
  const hand = useFont(HAND, 30);
  const envTop = lay.envTop;
  const finalTop = lay.cardTop;
  const ex = (width - EW) / 2;
  const frame = useDerivedValue(() => frameOf(v, reduced));
  // Ambient: the sealed envelope rises and settles 2 pt on the table's air; still while it is being opened.
  const float = useDerivedValue(() => {
    const f = frame.get();
    if (reduced) return 0;
    const still = 1 - clamp01(f.p / 0.05);
    return AMBIENT.floatPt * Math.sin((2 * Math.PI * f.now) / AMBIENT.floatMs) * still;
  });
  const envTransform = useDerivedValue(() => {
    const f = frame.get();
    const d = f.reset > 0 ? 0 : f.drop;
    const settle = f.reset > 0 ? 0 : 16 * clamp01(f.p / OPEN.flapShare);
    return [{ translateX: ex }, { translateY: envTop + settle + d * d * 520 + float.get() }, { scale: f.reset > 0 ? 0.96 + 0.04 * f.resetIn : 1 }];
  });
  const envOpacity = useDerivedValue(() => {
    const f = frame.get();
    return f.reset > 0 ? f.resetIn : 1 - f.drop;
  });
  const angle = useDerivedValue(() => {
    const f = frame.get();
    if (f.reset > 0) return 0;
    return clamp01(f.p / OPEN.flapShare) * 180 + f.breath;
  });
  const split = useDerivedValue(() => {
    const f = frame.get();
    const lifted = f.reset > 0 ? 0 : clamp01(f.p / OPEN.flapShare) * 180;
    return clamp01(lifted / (reduced ? 30 : OPEN.sealSplitDeg));
  });
  // Paper: the flap's lower part lags the fold, so it curls as it lifts.
  const fold = EH * LAYOUT.flapTip * 0.55;
  const tipT = useDerivedValue(() => {
    const lag = reduced ? 0 : OPEN.curlDeg * Math.sin((Math.PI * Math.min(180, angle.get())) / 180);
    return [{ translateY: fold }, { perspective: 700 }, { rotateX: (lag * Math.PI) / 180 }, { translateY: -fold }];
  });
  const flapTransform = useDerivedValue(() => [{ perspective: 900 }, { rotateX: (-angle.get() * Math.PI) / 180 }]);
  const flapFront = useDerivedValue(() => (angle.get() < 90 ? 1 : 0));
  // Past 90° the inside of the flap shows, its liner up, standing above the envelope.
  const flapBack = useDerivedValue(() => (angle.get() >= 90 ? 1 : 0));
  const cardPose = useDerivedValue(() => {
    const f = frame.get();
    const out = f.reset > 0 ? 0 : clamp01((f.p - OPEN.flapShare) / (1 - OPEN.flapShare));
    const insideY = envTop + 16 * clamp01(f.p / OPEN.flapShare) * (f.reset > 0 ? 0 : 1) + 18 + (CH * LAYOUT.cardInScale) / 2 - out * OPEN.cardRise;
    const finalY = finalTop + CH / 2;
    const k = f.fly;
    const arc = -36 * Math.sin(Math.PI * k);
    const turn = easeOutBack(f.flipT, REVEAL.flipBack);
    return {
      y: insideY + (finalY - insideY) * k + arc,
      s: (LAYOUT.cardInScale + (1 - LAYOUT.cardInScale) * easeInOut(k)) * (1 + REVEAL.flipLift * Math.sin(Math.PI * clamp01(f.flipT))),
      rot: f.tilt0 * (1 - k),
      flip: turn * Math.PI,
      front: f.after >= 0 && f.reset === 0,
    };
  });
  const cardTransform = useDerivedValue(() => [
    { translateX: width / 2 }, { translateY: cardPose.get().y }, { rotate: (cardPose.get().rot * Math.PI) / 180 }, { scale: cardPose.get().s },
    { perspective: 1100 }, { rotateY: cardPose.get().flip },
  ]);
  const insideCardTransform = useDerivedValue(() => [
    { translateX: EW / 2 }, { translateY: cardPose.get().y - envTop }, { rotate: (cardPose.get().rot * Math.PI) / 180 }, { scale: cardPose.get().s },
  ]);
  const insideOpacity = useDerivedValue(() => (cardPose.get().front ? 0 : 1));
  const frontOpacity = useDerivedValue(() => (cardPose.get().front ? 1 - frame.get().resetOut : 0));
  const faceUp = useDerivedValue(() => (Math.cos(cardPose.get().flip) >= 0 ? 1 : 0));
  const faceDown = useDerivedValue(() => 1 - faceUp.get());
  // Its shadow on the table spreads as the card lifts to turn over.
  const shadowT = useDerivedValue(() => {
    const pose = cardPose.get();
    const lift = Math.sin(Math.PI * clamp01(frame.get().flipT));
    return [{ translateX: width / 2 + 4 }, { translateY: pose.y + 14 + 10 * lift }, { scaleX: Math.max(0.15, Math.abs(Math.cos(pose.flip))) * pose.s }, { scaleY: pose.s }];
  });
  const shadowO = useDerivedValue(() => frontOpacity.get() * (1 - 0.35 * Math.sin(Math.PI * clamp01(frame.get().flipT))));

  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Rect x={0} y={0} width={width} height={height}>
        <Shader source={LINEN} uniforms={{ uSize: vec(width, height) }} />
      </Rect>
      <Group transform={envTransform} opacity={envOpacity}>
        {/* Its shadow on the linen: soft, down and right of the lamp. */}
        <RoundedRect x={10} y={22} width={EW - 8} height={EH - 8} r={6} color={COLORS.shadow}><Blur blur={18} /></RoundedRect>
        {/* The inside: darker paper behind the card. */}
        <RoundedRect x={0} y={0} width={EW} height={EH} r={4} color={COLORS.paperInside} />
        <Group opacity={flapBack} transform={flapTransform}>
          <Path path={FLAP} color={COLORS.paperLow} />
          {/* The liner: deep green tissue with a fine gold pattern. */}
          <Group clip={FLAP} transform={[{ translateX: 6 }, { translateY: 5 }, { scale: 0.96 }]}>
            <Rect x={0} y={0} width={EW} height={EH} color={COLORS.letterpress} />
            {Array.from({ length: 22 }, (_, i) => (
              <Path key={i} path={Skia.PathBuilder.Make().moveTo(i * 18 - 120, 0).lineTo(i * 18 + 60, EH).build()} color="rgba(214,184,120,0.28)" style="stroke" strokeWidth={0.6} />
            ))}
          </Group>
        </Group>
        <Group opacity={insideOpacity} transform={insideCardTransform}><CardFront /></Group>
        <Group clip={{ rect: { x: 0, y: 0, width: EW, height: EH }, rx: 4, ry: 4 }}>
          <Path path={LEFT}>
            <LinearGradient start={vec(0, 0)} end={vec(EW * 0.47, EH * 0.56)} colors={[COLORS.paperHigh, COLORS.paper]} />
          </Path>
          <Path path={RIGHT}>
            <LinearGradient start={vec(EW, 0)} end={vec(EW * 0.53, EH * 0.56)} colors={[COLORS.paper, COLORS.paperLow]} />
          </Path>
          {/* Folds: a soft shade along each edge, never a drawn outline; the pocket lies over the side flaps. */}
          <Path path={LEFT} color={COLORS.paperShade} style="stroke" strokeWidth={1.2}><Blur blur={1.2} /></Path>
          <Path path={POCKET}>
            <LinearGradient start={vec(0, EH * 0.45)} end={vec(EW * 0.4, EH)} colors={[COLORS.paperHigh, COLORS.paper]} />
          </Path>
          <Path path={POCKET} color={COLORS.crease} style="stroke" strokeWidth={2}><Blur blur={1.6} /></Path>
          <Grain w={EW} h={EH} seed={3} />
          {hand && <Text text={GIFT.to} x={EW - 24 - hand.getTextWidth(GIFT.to)} y={EH - 28} font={hand} color={COLORS.pen} opacity={0.9} />}
        </Group>
        <Group opacity={flapFront} transform={flapTransform}>
          <Path path={FLAP} color="rgba(60,40,10,0.22)" transform={[{ translateY: 3 }]}><Blur blur={4} /></Path>
          <Group clip={Skia.XYWHRect(0, 0, EW, fold)}>
            <Path path={FLAP}>
              <LinearGradient start={vec(0, 0)} end={vec(EW * 0.6, EH * LAYOUT.flapTip)} colors={[COLORS.paperHigh, COLORS.paper]} />
            </Path>
            <Group clip={FLAP}><Grain w={EW} h={EH} seed={11} /></Group>
          </Group>
          <Group transform={tipT}>
            <Group clip={Skia.XYWHRect(0, fold, EW, EH)}>
              <Path path={FLAP}>
                <LinearGradient start={vec(0, 0)} end={vec(EW * 0.6, EH * LAYOUT.flapTip)} colors={[COLORS.paperHigh, COLORS.paper]} />
              </Path>
              <Group clip={FLAP}><Grain w={EW} h={EH} seed={11} /></Group>
            </Group>
            <Seal split={split} />
          </Group>
        </Group>
      </Group>
      <Group opacity={shadowO} transform={shadowT}>
        <RoundedRect x={-CW / 2 + 8} y={-CH / 2} width={CW - 16} height={CH - 4} r={10} color={COLORS.shadow}><Blur blur={20} /></RoundedRect>
      </Group>
      <Group opacity={frontOpacity} transform={cardTransform}>
        <Group opacity={faceUp}><CardFront /></Group>
        <Group opacity={faceDown} transform={[{ scaleX: -1 }]}><CardBack /></Group>
      </Group>
    </Canvas>
  );
}
