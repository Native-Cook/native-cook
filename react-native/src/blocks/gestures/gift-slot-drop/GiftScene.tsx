/** The stage, drawn in Skia: carousel cards in perspective, the floor and slot, the printed offer, confetti. */
import {
  Blur, Canvas, ColorMatrix, Group, Image, Paint, LinearGradient, Oval, Path, RadialGradient, Rect, RoundedRect, Skia, Text, matchFont, useImage,
  vec, type SkFont,
} from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { interpolateColor, useDerivedValue } from 'react-native-reanimated';

import { ACTIVATE, AMBIENT, CHEVRON, CLOSE, DRAG, INSERT, PRINT, REDUCED_MS } from './motion';
import { CARD_ART, MARKS, OFFER_ART } from './gift-assets';
import { COLORS, GIFTS, LAYOUT, type Gift } from './theme';
import { PHASE, milestones, type GiftValues } from './use-gift-slot';

// The source's offer type is a heavy geometric sans; Avenir Next is the closest system face on iOS.
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const markFont = matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: 'bold' });
const bigFont = matchFont({ fontFamily: FAMILY, fontSize: 30, fontWeight: 'bold' });
const lineFont = matchFont({ fontFamily: FAMILY, fontSize: 15, fontWeight: 'bold' });
const offerBig = matchFont({ fontFamily: FAMILY, fontSize: 52, fontWeight: 'bold' });
const offerLine = matchFont({ fontFamily: FAMILY, fontSize: 28, fontWeight: '500' });
const offerSmall = matchFont({ fontFamily: FAMILY, fontSize: 15, fontWeight: '500' });
const offerMark = matchFont({ fontFamily: FAMILY, fontSize: 20, fontWeight: 'bold' });

const W = LAYOUT.cardW;
const H = LAYOUT.cardH;
const OW = LAYOUT.offerW;
const OH = LAYOUT.offerH;
const PERCENT_W = Array.from({ length: 101 }, (_, n) => offerBig.getTextWidth(`${n}%`));
const DEG = Math.PI / 180;
// Built outside the worklets: a callback created inside a worklet fails silently on the UI thread.
const GIFT_INDICES = GIFTS.map((_, i) => i);
const GIFT_COLOURS = GIFTS.map((g) => g.glow);
const centred = (font: SkFont, text: string) => -font.getTextWidth(text) / 2;
// 25 % toward grey, slightly darker: the source's offer card is a muted blue.
const MUTE = (() => {
  const k = 0.25;
  const g = [0.3, 0.59, 0.11];
  const m: number[] = [];
  for (let r = 0; r < 3; r += 1) {
    for (let c = 0; c < 3; c += 1) m.push(((r === c ? 1 : 0) * (1 - k) + g[c] * k) * 0.96);
    m.push(0, 0);
  }
  m.push(0, 0, 0, 1, 0);
  return m;
})();

const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};
const spring = (t: number, stiffness: number, damping: number) => {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness);
  const z = Math.min(0.999, damping / (2 * w));
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
};
const backOut = (t: number) => {
  'worklet';
  const c = clamp01(t);
  const k = 1.6;
  return 1 + (k + 1) * Math.pow(c - 1, 3) + k * Math.pow(c - 1, 2);
};

/** Wordmark: an emblem (generated) beside or under the shop's name, as the source's brands do. */
function Wordmark({ gift, font, y, scale = 1 }: { gift: Gift; font: SkFont; y: number; scale?: number }) {
  const mark = useImage(MARKS[gift.id] ?? null);
  const w = font.getTextWidth(gift.wordmark);
  const m = 16 * scale;
  if (gift.id === 'parcels') {
    // A marketplace wordmark with its swoosh underneath.
    return (
      <Group>
        <Text text={gift.wordmark} x={-w / 2} y={y} font={font} color={gift.ink} />
        {mark && <Image image={mark} x={-w * 0.45} y={y + 1} width={w * 0.9} height={m * 0.9} fit="contain" />}
      </Group>
    );
  }
  const total = (mark ? m + 5 * scale : 0) + w;
  return (
    <Group>
      {mark && <Image image={mark} x={-total / 2} y={y - m + 3 * scale} width={m} height={m} fit="contain" />}
      <Text text={gift.wordmark} x={-total / 2 + (mark ? m + 5 * scale : 0)} y={y} font={font} color={gift.ink} />
    </Group>
  );
}

/** A carousel card: generated card art, wordmark, the offer in bold type (upper half, above the photo). */
export function CardArt({ gift }: { gift: Gift }) {
  const art = useImage(CARD_ART[gift.id]);
  const clip = { rect: { x: -W / 2, y: -H / 2, width: W, height: H }, rx: LAYOUT.cardR, ry: LAYOUT.cardR };
  const bottom = gift.id === 'percent';
  const top = -H / 2;
  return (
    <Group>
      <Group clip={clip}>
        <Rect x={-W / 2} y={-H / 2} width={W} height={H} color={gift.glow} />
        {art && <Image image={art} x={-W / 2} y={-H / 2} width={W} height={H} fit="cover" />}
        {/* Multiplied by its own hue: darker and richer, as the source's exposure shows the cards. */}
        <Rect x={-W / 2} y={-H / 2} width={W} height={H} color={COLORS.cardShadeInk} opacity={COLORS.cardShade[0]} />
        <Rect x={-W / 2} y={-H / 2} width={W} height={H} color={gift.glow} opacity={COLORS.cardShade[1]} blendMode="multiply" />
      </Group>
      <Wordmark gift={gift} font={markFont} y={top + 37} />
      {bottom ? (
        <Group>
          <Text text={`${gift.headline} ${gift.line}`} x={centred(lineFont, `${gift.headline} ${gift.line}`)} y={H / 2 - 34} font={lineFont} color={gift.ink} />
          <Text text={gift.cap} x={centred(lineFont, gift.cap)} y={H / 2 - 15} font={lineFont} color={gift.ink} />
        </Group>
      ) : (
        <Group>
          <Text text={gift.headline} x={centred(bigFont, gift.headline)} y={top + 94} font={bigFont} color={gift.ink} />
          <Text text={gift.line} x={centred(lineFont, gift.line)} y={top + 111} font={lineFont} color={gift.ink} />
          <Text text={gift.cap} x={centred(lineFont, gift.cap)} y={top + 129} font={lineFont} color={gift.ink} />
        </Group>
      )}
    </Group>
  );
}

/** The activated offer, as it prints out of the slot: larger card, rolling percentage, how to use, expiry. */
function OfferArt({ gift, percent }: { gift: Gift; percent: { get(): number } }) {
  const art = useImage(OFFER_ART[gift.id] ?? CARD_ART[gift.id]);
  const clip = { rect: { x: -OW / 2, y: -OH / 2, width: OW, height: OH }, rx: 14, ry: 14 };
  const text = useDerivedValue(() => `${Math.round(percent.get())}%`);
  const textX = useDerivedValue(() => -PERCENT_W[Math.round(percent.get())] / 2);
  const words = gift.use.split(' ');
  const half = Math.ceil(words.length / 2);
  const use1 = words.slice(0, half).join(' ');
  const use2 = words.slice(half).join(' ');
  const ink = gift.id === 'parcels' ? '#FFFFFF' : gift.ink;
  return (
    <Group>
      <Group clip={clip}>
        <Rect x={-OW / 2} y={-OH / 2} width={OW} height={OH} color={gift.glow} />
        {art && (
          // A touch greyer than the generated art, like the source's muted blue.
          <Group layer={<Paint><ColorMatrix matrix={MUTE} /></Paint>}>
            <Image image={art} x={-OW / 2} y={-OH / 2} width={OW} height={OH} fit="cover" />
          </Group>
        )}
        {art && (
          // Warmed toward the source's grey-cyan (gate 2b swatch).
          <Rect x={-OW / 2} y={-OH / 2} width={OW} height={OH} color={COLORS.offerWarm} />
        )}
      </Group>
      <Wordmark gift={{ ...gift, ink: gift.id === 'parcels' ? '#FFFFFF' : gift.ink }} font={offerMark} y={-OH / 2 + 54} scale={1.4} />
      <Text text={text} x={textX} y={-44} font={offerBig} color={ink} />
      <Text text={gift.line} x={centred(offerLine, gift.line)} y={-2} font={offerLine} color={ink} />
      <Text text={use1} x={centred(offerSmall, use1)} y={62} font={offerSmall} color={ink} opacity={0.9} />
      <Text text={use2} x={centred(offerSmall, use2)} y={82} font={offerSmall} color={ink} opacity={0.9} />
      <Text text={gift.expires} x={centred(offerSmall, gift.expires)} y={OH / 2 - 46} font={offerSmall} color={ink} />
    </Group>
  );
}

function CarouselCard({ k, v, width, reduced }: { k: number; v: GiftValues; width: number; reduced: boolean }) {
  const cx = width / 2;
  const pose = useDerivedValue(() => {
    const rel = k - v.offset.get();
    const now = v.clock.get();
    const at = v.releasedAt.get();
    const isSel = k === v.selected.get();
    let x = cx + rel * LAYOUT.spacing;
    let y: number = LAYOUT.cardY;
    let s = 1 - (1 - LAYOUT.sideScale) * Math.min(1, Math.abs(rel));
    const turn = Math.max(-1, Math.min(1, -rel)) * LAYOUT.turnDeg;
    let opacity = 1;
    let lean = 0;
    let roll = 0;
    const ph = v.phase.get();
    // Ambient: the chosen card floats a few points while it waits to be taken.
    if (isSel && ph === PHASE.choosing && at < 0 && !reduced) y += AMBIENT.floatPt * Math.sin((2 * Math.PI * now) / AMBIENT.floatMs);
    if (isSel && (ph === PHASE.pulling || ph === PHASE.choosing || ph === PHASE.swiping) && at < 0) {
      x += v.pullX.get();
      y += v.pullY.get();
      s *= (1 - (1 - DRAG.minScale) * clamp01(v.pullY.get() / DRAG.full)) * v.lift.get();
      lean = clamp01(v.pullY.get() / DRAG.full) * DRAG.leanDeg;
      // Sideways drag rolls the card a little, like a held object (velocity lean from the finger's offset).
      roll = reduced ? 0 : Math.max(-AMBIENT.rollMax, Math.min(AMBIENT.rollMax, v.pullX.get() * AMBIENT.rollPerPt));
    }
    if (at >= 0) {
      const e = now - at;
      if (isSel) {
        const f = v.releasedFrom.get();
        if (reduced) {
          x = cx + f.x; y = LAYOUT.cardY + f.y; s = f.s;
          opacity = 1 - clamp01(e / REDUCED_MS);
        } else {
          const u = 1 - Math.pow(1 - clamp01(e / INSERT.centreMs), 3);
          // The card stays tipped back while it slides into the pocket; its foreshortened height is what sinks.
          lean = DRAG.leanDeg;
          const tall = H * DRAG.minScale * Math.cos(DRAG.leanDeg * DEG);
          const above = LAYOUT.slotY - tall / 2;
          x = cx + f.x * (1 - u);
          y = LAYOUT.cardY + f.y + (above - LAYOUT.cardY - f.y) * u;
          s = f.s + (DRAG.minScale - f.s) * u;
          const sink = clamp01((e - INSERT.centreMs) / INSERT.sinkMs);
          // On-screen movement: ease-in-out (animate-expo), not a bare ease-in.
          const se = sink < 0.5 ? 4 * sink * sink * sink : 1 - Math.pow(-2 * sink + 2, 3) / 2;
          y += tall * 1.1 * se;
          if (e > INSERT.centreMs + INSERT.sinkMs) opacity = 0;
        }
      } else {
        opacity = 1 - clamp01(e / 250);
      }
      const c0 = v.closeAt.get();
      if (c0 >= 0) {
        // Back from the offer: every card fades and grows into place.
        const r = clamp01((now - c0 - (reduced ? 0 : CLOSE.ms)) / (reduced ? REDUCED_MS : CLOSE.returnMs));
        x = cx + rel * LAYOUT.spacing;
        y = LAYOUT.cardY;
        s = (1 - (1 - LAYOUT.sideScale) * Math.min(1, Math.abs(rel))) * (0.94 + 0.06 * r);
        opacity = r;
      }
    }
    return { x, y, s, turn, opacity, lean, roll };
  });
  const transform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x }, { translateY: p.y }, { perspective: 700 }, { rotateY: p.turn * DEG }, { rotateX: p.lean * DEG }, { rotate: p.roll * DEG }, { scale: p.s }];
  });
  const opacity = useDerivedValue(() => pose.get().opacity);
  const pocketLight = useDerivedValue(() => (Math.abs(k - v.offset.get()) < 0.5 ? clamp01((v.pullY.get() - 50) / 100) : 0));
  // Lifted into the pocket's light, the card brightens (it reads lit, not shaded like the carousel).
  const lift = useDerivedValue(() => pocketLight.get() * COLORS.cardLift);
  const bandT = useDerivedValue(() => {
    const drift = 0.25 * Math.sin((2 * Math.PI * v.clock.get()) / AMBIENT.bandMs + k);
    return [{ translateX: ((k - v.offset.get()) * -0.9 + v.pullX.get() / 120 + drift) * W }];
  });
  const frame = useDerivedValue(() => {
    const ph = v.phase.get();
    if (v.releasedAt.get() >= 0 || (ph !== PHASE.choosing && ph !== PHASE.pulling && ph !== PHASE.swiping)) return 0;
    // The selection frame lets go of the card as it is pulled away.
    return clamp01(1 - Math.abs(k - v.offset.get()) * 2) * (1 - clamp01(v.pullY.get() / 60));
  });
  return (
    <Group transform={transform} opacity={opacity}>
      <Group opacity={frame}>
        <RoundedRect x={-W / 2 - 7} y={-H / 2 - 7} width={W + 14} height={H + 14} r={LAYOUT.cardR + 6} color={COLORS.yellow}
          style="stroke" strokeWidth={6} opacity={0.55}>
          <Blur blur={10} />
        </RoundedRect>
        <RoundedRect x={-W / 2 - 6} y={-H / 2 - 6} width={W + 12} height={H + 12} r={LAYOUT.cardR + 5} color={COLORS.yellow}
          style="stroke" strokeWidth={3} />
      </Group>
      <CardArt gift={GIFTS[k]} />
      {/* Specular band: follows the carousel offset and the pull, with a slow drift (material, not paint). */}
      <Group clip={{ rect: { x: -W / 2, y: -H / 2, width: W, height: H }, rx: LAYOUT.cardR, ry: LAYOUT.cardR }}>
        <Group transform={bandT}>
          <Rect x={-W * 3} y={-H / 2} width={W * 6} height={H} blendMode="softLight" opacity={reduced ? 0 : AMBIENT.band}>
            <LinearGradient start={vec(-W * 0.25, -H / 2)} end={vec(W * 0.15, H / 2)}
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,1)', 'rgba(255,255,255,0)']} />
          </Rect>
        </Group>
        {/* The pocket's light climbs the card's lower half as it goes in (source frame 056). */}
        <Rect x={-W / 2} y={-H / 2} width={W} height={H} color="#FFFFFF" opacity={lift} />
        <Rect x={-W / 2} y={-H / 2} width={W} height={H} opacity={pocketLight}>
          <LinearGradient start={vec(0, -H * 0.6)} end={vec(0, H / 2)} colors={['rgba(255,236,170,0)', COLORS.pocketLight]} />
        </Rect>
      </Group>
    </Group>
  );
}

function OfferCard({ index, v, width, reduced }: { index: number; v: GiftValues; width: number; reduced: boolean }) {
  const cx = width / 2;
  const gift = GIFTS[index];
  const pose = useDerivedValue(() => {
    const at = v.releasedAt.get();
    if (at < 0) return { y: 0, s: 1, opacity: 0, roll: 0 };
    const m = milestones(reduced);
    const e = v.clock.get() - at - m.printStart;
    if (e < 0) return { y: 0, s: 1, opacity: 0, roll: 0 };
    let y: number;
    let s: number;
    let opacity = 1;
    let roll: number;
    if (reduced) {
      y = LAYOUT.offerY; s = 1; opacity = clamp01(e / REDUCED_MS); roll = 1;
    } else {
      const s0 = PRINT.startScale;
      let fed = 0;
      for (let i = 0; i < PRINT.feeds; i += 1) fed += PRINT.feedShare * backOut((e - i * (PRINT.feedMs + PRINT.feedGap)) / PRINT.feedMs);
      const feedEnd = PRINT.feeds * (PRINT.feedMs + PRINT.feedGap);
      const fedY = LAYOUT.slotY - fed * OH * s0 + (OH * s0) / 2;
      const r = spring((e - feedEnd) / 1000, 170, 20);
      y = fedY + (LAYOUT.offerY - fedY) * r;
      s = s0 + (1 - s0) * r;
      const p = clamp01((e - feedEnd + 100) / PRINT.rollMs);
      roll = 1 - Math.pow(1 - p, 3);
    }
    const c0 = v.closeAt.get();
    if (c0 >= 0) {
      const c = clamp01((v.clock.get() - c0) / (reduced ? REDUCED_MS : CLOSE.ms));
      opacity *= 1 - c;
      if (!reduced) { s *= 1 - 0.06 * c * c; y -= 24 * c * c; }
    }
    return { y, s, opacity, roll };
  });
  const transform = useDerivedValue(() => [{ translateX: cx }, { translateY: pose.get().y }, { scale: pose.get().s }]);
  const opacity = useDerivedValue(() => pose.get().opacity);
  const percent = useDerivedValue(() => parseInt(gift.headline, 10) * pose.get().roll);
  return (
    <Group transform={transform} opacity={opacity}>
      <OfferArt gift={gift} percent={percent} />
    </Group>
  );
}

function Confetti({ v, index }: { v: GiftValues; index: number }) {
  const colours = [COLORS.yellow, '#FFFFFF', GIFTS[index].glow];
  const paths = colours.map((_, ci) => ci);
  return (
    <>
      {paths.map((ci) => <ConfettiLayer key={ci} v={v} colour={colours[ci]} which={ci} />)}
    </>
  );
}
function ConfettiLayer({ v, colour, which }: { v: GiftValues; colour: string; which: number }) {
  const path = useDerivedValue(() => {
    const c = v.confetti.get();
    const b = Skia.PathBuilder.Make();
    for (let o = 0; o < c.length; o += 8) {
      if (c[o + 6] <= 0 || c[o + 7] !== which) continue;
      const a = c[o + 4];
      const fade = Math.min(1, c[o + 6] / 0.4);
      const hw = 5 * fade;
      const hh = 2.6 * fade * Math.abs(Math.cos(a * 1.7)) + 0.5;
      const ca = Math.cos(a);
      const sa = Math.sin(a);
      b.moveTo(c[o] + ca * hw - sa * hh, c[o + 1] + sa * hw + ca * hh);
      b.lineTo(c[o] - ca * hw - sa * hh, c[o + 1] - sa * hw + ca * hh);
      b.lineTo(c[o] - ca * hw + sa * hh, c[o + 1] - sa * hw - ca * hh);
      b.lineTo(c[o] + ca * hw + sa * hh, c[o + 1] + sa * hw - ca * hh);
      b.close();
    }
    return b.build();
  });
  return <Path path={path} color={colour} />;
}

export function GiftScene({ v, width, height, index, reduced }: { v: GiftValues; width: number; height: number; index: number; reduced: boolean }) {
  const cx = width / 2;
  const warm = useDerivedValue(() => COLORS.stageWarm * clamp01(v.pullY.get() / DRAG.full));
  const glowColour = useDerivedValue(() => interpolateColor(v.offset.get(), GIFT_INDICES, GIFT_COLOURS));
  const slotGlow = useDerivedValue(() => {
    const at = v.releasedAt.get();
    const now = v.clock.get();
    // The pocket breathes softly while it waits, and brightens as the card is pulled toward it.
    if (at < 0) return 0.5 + 0.5 * clamp01(v.pullY.get() / DRAG.commit) + (reduced ? 0 : 0.08 * Math.sin((2 * Math.PI * now) / AMBIENT.slotBreathMs));
    const m = milestones(reduced);
    const e = now - at;
    if (e < m.sunk) return 1;
    if (e < m.sunk + INSERT.flashMs) return 1 + 0.6 * (1 - (e - m.sunk) / INSERT.flashMs);
    if (e < m.printStart) return 0.85 + 0.25 * Math.sin(((e - m.sunk) / ACTIVATE.dotMs) * Math.PI);
    if (v.closeAt.get() >= 0) return 0.5 * clamp01((now - v.closeAt.get()) / CLOSE.returnMs);
    // Once the offer has risen the pocket goes dark behind the details, as in the source.
    return 1 - 0.9 * clamp01((e - m.printed) / 600);
  });
  const slotTransform = useDerivedValue(() => [{ translateX: cx }, { translateY: LAYOUT.slotY }, { scaleX: 0.9 + 0.12 * Math.min(1.2, slotGlow.get()) }]);
  const slotOpacity = useDerivedValue(() => Math.min(1, slotGlow.get()));
  const placeholder = useDerivedValue(() => {
    const at = v.releasedAt.get();
    if (at >= 0) return v.phase.get() === PHASE.inserting || v.phase.get() === PHASE.activating ? 1 : 0;
    return clamp01(v.pullY.get() / 40);
  });
  const chevrons = useDerivedValue(() => {
    const visible = v.releasedAt.get() < 0 ? 1 : 0;
    const base = LAYOUT.chevronY + (reduced ? 0 : CHEVRON.bobPt * Math.sin((v.clock.get() / CHEVRON.bobMs) * Math.PI * 2));
    // The chevrons stay put under the card. While the pulled card passes over them they are hidden under it;
    // once its top edge has come down to them they ride just above it (source frame a: 18 pt above the edge).
    const py = v.pullY.get();
    const k = clamp01(py / DRAG.full);
    const half = (H * (1 - (1 - DRAG.minScale) * k) * Math.cos(k * DRAG.leanDeg * DEG)) / 2;
    const top = LAYOUT.cardY + py - half;
    const bottom = LAYOUT.cardY + py + half;
    let y = base;
    let shown = 1;
    if (bottom > base - CHEVRON.gap) {
      if (top < base - CHEVRON.gap) shown = 0;
      else y = top - CHEVRON.gap;
    }
    return [{ translateX: cx }, { translateY: y }, { scale: visible * shown }];
  });
  // The pocket's mouth hides the card as it goes in, while pulling and after the release (source frame a).
  const clip = useDerivedValue(() => Skia.XYWHRect(0, 0, width, LAYOUT.slotY));
  const pocketX = cx - LAYOUT.pocketW / 2;
  const pocket = Skia.PathBuilder.Make().addRRect({
    rect: { x: pocketX, y: LAYOUT.pocketTop, width: LAYOUT.pocketW, height: LAYOUT.floorY - LAYOUT.pocketTop + LAYOUT.pocketDepth }, rx: 16, ry: 16,
  }).build();
  // The glow is drawn relative to the slot centre; it stays inside the pocket and above the floor.
  // The glow fills the pocket down to its rounded bottom, as in the source.
  const pocketClip = Skia.RRectXY(Skia.XYWHRect(-LAYOUT.pocketW / 2, LAYOUT.pocketTop - LAYOUT.slotY, LAYOUT.pocketW, LAYOUT.floorY + LAYOUT.pocketDepth - LAYOUT.pocketTop), 16, 16);
  const chevron = Skia.PathBuilder.Make().moveTo(-11.5, -7.5).lineTo(0, 1).lineTo(11.5, -7.5).moveTo(-11.5, 2.5).lineTo(0, 11).lineTo(11.5, 2.5).build();

  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      {/* Dark olive stage with a soft vignette, a tint of the centred card, then the floor. */}
      <Rect x={0} y={0} width={width} height={height}>
        {/* Light from above the screen, falling off toward the floor (source footage). */}
        <RadialGradient c={vec(cx, height * 0.08)} r={height * 0.85} colors={[COLORS.stage, COLORS.stageEdge]} />
      </Rect>
      {/* The pocket's light warms the whole stage as the card goes in (source frame 056). */}
      <Rect x={0} y={0} width={width} height={LAYOUT.floorY} color={COLORS.stageWarmInk} opacity={warm} blendMode="multiply" />
      <Oval x={cx - 200} y={LAYOUT.cardY - 220} width={400} height={440} color={glowColour} opacity={COLORS.hueTint}>
        <Blur blur={60} />
      </Oval>
      <Rect x={0} y={LAYOUT.floorY} width={width} height={height - LAYOUT.floorY}>
        <LinearGradient start={vec(0, LAYOUT.floorY)} end={vec(0, height)} colors={[COLORS.floor, COLORS.floorLow]} />
      </Rect>
      <Rect x={0} y={LAYOUT.floorY} width={width} height={1} color={COLORS.floorLine} />
      {/* The lit pocket: a rounded opening under the card, glowing yellow where it meets the floor. */}
      <Path path={pocket}>
        <LinearGradient start={vec(0, LAYOUT.pocketTop)} end={vec(0, LAYOUT.floorY)} colors={[COLORS.pocketFade, COLORS.pocketFade, COLORS.pocketLow]} positions={[0, 0.35, 1]} />
      </Path>
      <Group transform={slotTransform} opacity={slotOpacity}>
        <Oval x={-LAYOUT.slotW / 2 - 24} y={-LAYOUT.slotH / 2 - 10 + LAYOUT.glowDrop} width={LAYOUT.slotW + 48} height={LAYOUT.slotH + 26} color={COLORS.yellowDeep} opacity={0.2}>
          <Blur blur={20} />
        </Oval>
        <Group clip={pocketClip}>
          <Oval x={-LAYOUT.pocketW * 0.6} y={LAYOUT.glowTop} width={LAYOUT.pocketW * 1.2} height={LAYOUT.glowH} opacity={0.72}>
            <RadialGradient c={vec(0, 16)} r={LAYOUT.pocketW * 0.45} colors={[COLORS.glowCore, COLORS.yellowDeep, 'rgba(242,169,0,0)']} />
            <Blur blur={6} />
          </Oval>
        </Group>
      </Group>
      <Group transform={chevrons}>
        <Path path={chevron} color={COLORS.mute} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" />
      </Group>
      <RoundedRect x={cx - W / 2 - 7} y={LAYOUT.cardY - H / 2 - 7} width={W + 14} height={H + 14} r={LAYOUT.cardR + 6} color={COLORS.outline}
        style="stroke" strokeWidth={1.2} opacity={placeholder} />
      <Group clip={clip}>
        {GIFTS.map((_, k) => <CarouselCard key={k} k={k} v={v} width={width} reduced={reduced} />)}
        <OfferCard index={index} v={v} width={width} reduced={reduced} />
      </Group>
      <Confetti v={v} index={index} />
    </Canvas>
  );
}
