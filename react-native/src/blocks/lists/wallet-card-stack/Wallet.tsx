import {
  Canvas, DashPathEffect, Group, ImageShader, LinearGradient, Path, RadialGradient, RoundedRect,
  Shadow, Skia, useImage, vec,
} from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { LinearGradient as Gradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { type ComposedGesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { BALANCE, CARD_HOLDER } from './data';
import { EyeGlyph, PlusGlyph, SwapGlyph } from './glyphs';
import { HIDE, LIGHT, LOOP, SQUISH, TURN } from './motion';
import { TapTarget } from './TapTarget';
import { CARDS, COLOR, POCKET_UI, WALLET } from './theme';

const HIDE_SPAN = HIDE.charStagger * (BALANCE.amount.length - 1) + HIDE.charDuration;
const W = WALLET.width;
const TOUCH_TOP = -50; // the cards' touch area starts this far above the wallet
const H = WALLET.height;
const PAD = 24; // canvas margin for the shadow and the rim

/** Pocket outline: straight sides, a top edge that dips toward the middle, deep round bottom corners. */
function pocketPath(inset: number) {
  const side = WALLET.pocketSide + inset;
  const dip = WALLET.pocketDip + inset;
  const cr = Math.max(2, WALLET.cornerRadius - inset * 0.6);
  const R = WALLET.pocketRadius - inset;
  const l = inset + WALLET.pocketInset;
  const r = W - inset - WALLET.pocketInset;
  const b = H - inset - WALLET.pocketBottom;
  const p = Skia.PathBuilder.Make();
  p.moveTo(l, side + cr);
  p.quadTo(l, side, l + cr, side);
  p.cubicTo(W * 0.3, side, W * 0.36, dip, W / 2, dip);
  p.cubicTo(W * 0.64, dip, W * 0.7, side, r - cr, side);
  p.quadTo(r, side, r, side + cr);
  p.lineTo(r, b - R);
  p.cubicTo(r, b - R * 0.45, r - R * 0.45, b, r - R, b);
  p.lineTo(l + R, b);
  p.cubicTo(l + R * 0.45, b, l, b - R * 0.45, l, b - R);
  p.close();
  return p.build();
}

/** Only the top edge, for the rim light. */
/**
 * The pin's stitching: one seam a few points under the pocket's lip, following its dip, and one round the
 * bottom of the wallet that runs up the sides only as far as the corners.
 */
function stitchPath() {
  const i = WALLET.stitchInset;
  const side = WALLET.pocketSide + i;
  const dip = WALLET.pocketDip + i;
  const l = WALLET.pocketInset + i + 6;
  const r = W - WALLET.pocketInset - i - 6;
  const p = Skia.PathBuilder.Make();
  p.moveTo(l, side);
  p.cubicTo(W * 0.3, side, W * 0.36, dip, W / 2, dip);
  p.cubicTo(W * 0.64, dip, W * 0.7, side, r, side);
  const bl = WALLET.pocketInset + i;
  const br = W - WALLET.pocketInset - i;
  const b = H - WALLET.pocketBottom - i;
  const R = WALLET.pocketRadius - i;
  p.moveTo(bl, b - R - WALLET.stitchRise);
  p.lineTo(bl, b - R);
  p.cubicTo(bl, b - R * 0.45, bl + R * 0.45, b, bl + R, b);
  p.lineTo(br - R, b);
  p.cubicTo(br - R * 0.45, b, br, b - R * 0.45, br, b - R);
  p.lineTo(br, b - R - WALLET.stitchRise);
  return p.build();
}

function rimPath() {
  const side = WALLET.pocketSide;
  const dip = WALLET.pocketDip;
  const cr = WALLET.cornerRadius;
  const l = WALLET.pocketInset;
  const r = W - WALLET.pocketInset;
  const p = Skia.PathBuilder.Make();
  p.moveTo(l, side + cr);
  p.quadTo(l, side, l + cr, side);
  p.cubicTo(W * 0.3, side, W * 0.36, dip, W / 2, dip);
  p.cubicTo(W * 0.64, dip, W * 0.7, side, r - cr, side);
  p.quadTo(r, side, r, side + cr);
  return p.build();
}

function Leather({ path }: { path: ReturnType<typeof pocketPath> }) {
  const grain = useImage(require('./assets/leather.jpg'));
  if (!grain) return null;
  return (
    <Path path={path} blendMode="softLight" opacity={0.16}>
      <ImageShader image={grain} tx="repeat" ty="repeat" rect={{ x: 0, y: 0, width: 200, height: 200 }} />
    </Path>
  );
}

/** Back of the wallet: only its rim shows, beside and behind the cards. */
function WalletBack() {
  return (
    <Canvas style={styles.canvas} pointerEvents="none">
      <Group transform={[{ translateX: PAD }, { translateY: PAD }]}>
        <RoundedRect x={W * 0.1} y={H - 14} width={W * 0.8} height={18} r={9} color="rgba(60, 30, 110, 0.32)">
          <Shadow dx={0} dy={6} blur={10} color="rgba(60, 30, 110, 0.35)" shadowOnly />
        </RoundedRect>
        <RoundedRect x={0} y={0} width={W} height={H - WALLET.flapShort} r={WALLET.flapRadius}>
          {/* The back panel catches the light on the left and falls into shadow on the right, as in the pin. */}
          <LinearGradient start={vec(0, 0)} end={vec(W, 0)} positions={[...WALLET.flapStops]} colors={[...WALLET.flapColors]} />
        </RoundedRect>
        <RoundedRect x={1} y={1} width={W - 2} height={H - 2 - WALLET.flapShort} r={WALLET.flapRadius - 1}
          style="stroke" strokeWidth={1.5} color={WALLET.rimTop} opacity={0.8}>
          <LinearGradient start={vec(0, 0)} end={vec(0, 50)} colors={[WALLET.rimTop, 'rgba(212, 180, 244, 0)']} />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}

function WalletFront() {
  const pocket = useMemo(() => pocketPath(0), []);
  const stitch = useMemo(() => stitchPath(), []);
  const rim = useMemo(() => rimPath(), []);
  return (
    <Canvas style={styles.canvas} pointerEvents="none">
      <Group transform={[{ translateX: PAD }, { translateY: PAD }]}>
        <Path path={pocket}>
          {/* Sampled across the pin's pocket: a dark seam at the left edge, the light catching ~15 % in,
              falling to a deep violet in the middle and almost navy on the right. */}
          <LinearGradient start={vec(0, 0)} end={vec(W, 0)} positions={[...WALLET.pocketStops]}
            colors={[...WALLET.pocketColors]} />
        </Path>
        <Path path={pocket}>
          <LinearGradient start={vec(0, WALLET.pocketSide)} end={vec(0, H)} positions={[0, 0.55, 1]}
            colors={['rgba(36, 16, 79, 0)', 'rgba(36, 16, 79, 0.12)', 'rgba(36, 16, 79, 0.8)']} />
        </Path>
        {/* The soft light across the lower middle of the pocket (pin, y 300-340). */}
        <Path path={pocket}>
          <RadialGradient c={vec(W * WALLET.pocketGlow.cx, H * WALLET.pocketGlow.cy)} r={W * WALLET.pocketGlow.r}
            colors={[WALLET.pocketGlow.color, 'rgba(255, 255, 255, 0)']} />
        </Path>
        <Leather path={pocket} />
        <Path path={stitch} style="stroke" strokeWidth={1.1} color={WALLET.stitch}>
          <DashPathEffect intervals={[4.5, 3.2]} />
        </Path>
        {/* The lip, as in the pin: a hairline of light on its edge and a darker line just inside it. */}
        <Path path={rim} style="stroke" strokeWidth={1} color="#E2CBFB" opacity={0.7} />
        <Group transform={[{ translateY: 1.4 }]}>
          <Path path={rim} style="stroke" strokeWidth={1.4} color="rgba(48, 24, 100, 0.7)" />
        </Group>
      </Group>
    </Canvas>
  );
}

function Card({ kind }: { kind: 'back' | 'front' }) {
  if (kind === 'back') {
    const c = CARDS.back;
    return (
      <View style={{ width: c.width, height: c.height, borderRadius: c.radius, overflow: 'hidden' }}>
        <Image source={require('./assets/back-field.png')} contentFit="fill" style={StyleSheet.absoluteFill} />
      </View>
    );
  }
  const c = CARDS.front;
  const top = (y: number) => y - c.y; // card-relative from wallet-relative
  return (
    <View style={{ width: c.width, height: c.height, borderRadius: c.radius, overflow: 'hidden' }}>
      {/* The card's light, as a 9 x 7 colour field sampled from the pin (sheen included), stretched smoothly. */}
      <Image source={require('./assets/card-field.png')} contentFit="fill" style={StyleSheet.absoluteFill} />
      <Text style={[styles.holder, { top: top(CARDS.nameY) - 11 }]}>{CARD_HOLDER.name}</Text>
      <Text style={[styles.cardNumber, { top: top(CARDS.numberY) - 8 }]}>
        <Text style={styles.cardDots}>{'**** **** ****  '}</Text>{CARD_HOLDER.last4}
      </Text>
      <Text style={[styles.brand, { top: top(CARDS.brandY) - 12 }]}>{CARD_HOLDER.network}</Text>
      <Text style={[styles.valid, { top: top(CARDS.validY) - 8 }]}>Valid: {CARD_HOLDER.valid}</Text>
    </View>
  );
}

/** One balance character: the digit lifts away as its dot drops in, staggered left to right. */
function BalanceChar({ ch, index, hidden, reduced }: { ch: string; index: number; hidden: SharedValue<number>; reduced: boolean }) {
  const digit = useAnimatedStyle(() => {
    const t = reduced ? hidden.get() : Math.min(1, Math.max(0, (hidden.get() * HIDE_SPAN - index * HIDE.charStagger) / HIDE.charDuration));
    return { opacity: 1 - t, transform: [{ translateY: reduced ? 0 : -HIDE.rise * t }] };
  });
  const dot = useAnimatedStyle(() => {
    const t = reduced ? hidden.get() : Math.min(1, Math.max(0, (hidden.get() * HIDE_SPAN - index * HIDE.charStagger) / HIDE.charDuration));
    return { opacity: t, transform: [{ translateY: reduced ? 0 : HIDE.rise * (1 - t) }, { scale: reduced ? 1 : 0.6 + 0.4 * t }] };
  });
  const isDigit = /[0-9]/.test(ch);
  return (
    <View>
      <Animated.Text style={[styles.balance, digit]}>{ch}</Animated.Text>
      {isDigit && <Animated.Text style={[styles.balance, styles.dot, dot]}>•</Animated.Text>}
    </View>
  );
}

/** Light on the front card: a specular band that crosses it as it turns, stronger the higher it is. */
function Sheen({ lift, turn }: { lift: SharedValue<number>; turn: SharedValue<number> }) {
  const c = CARDS.front;
  const style = useAnimatedStyle(() => {
    const k = Math.min(1, Math.max(0, lift.get() / LOOP.lift));
    const r = turn.get() / LOOP.sway;
    return {
      opacity: LIGHT.sheen * k * (0.35 + 0.65 * Math.min(1, Math.abs(r))),
      transform: [{ translateX: c.width * (0.5 + r * LIGHT.sheenTravel) - SHEEN_W / 2 }, { rotate: '20deg' }],
    };
  });
  return (
    <View style={[StyleSheet.absoluteFill, { borderRadius: c.radius, overflow: 'hidden' }]} pointerEvents="none">
      <Animated.View style={[styles.sheen, style]}>
        <Gradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }}
          style={StyleSheet.absoluteFill} />
      </Animated.View>
    </View>
  );
}

const SHEEN_W = 110;

export function Wallet({ oy, frontLift, backLift, tilt, turn, squish, hidden, swapTurn, reduced, pullGesture, onEye, onSwap, onAdd, hiddenNow }: {
  oy: number; reduced: boolean; hiddenNow: boolean;
  frontLift: SharedValue<number>; backLift: SharedValue<number>; tilt: SharedValue<number>; turn: SharedValue<number>;
  squish: SharedValue<number>; hidden: SharedValue<number>; swapTurn: SharedValue<number>;
  pullGesture: ComposedGesture; onEye: () => void; onSwap: () => void; onAdd: () => void;
}) {
  const body = useAnimatedStyle(() => {
    const s = squish.get();
    return { transform: [{ scaleX: 1 + (SQUISH.scaleX - 1) * s }, { scaleY: 1 + (SQUISH.scaleY - 1) * s }] };
  });
  const front = useAnimatedStyle(() => {
    // Its contact shadow grows, softens and falls further as the card rises off the cards behind it.
    const k = Math.min(1, Math.max(0, frontLift.get() / LOOP.lift));
    return {
      transform: [{ perspective: TURN.perspective }, { rotateY: `${turn.get()}deg` }],
      shadowOpacity: LIGHT.shadow * k,
      shadowRadius: 4 + 10 * k,
      shadowOffset: { width: 0, height: 2 + 8 * k },
    };
  });
  // Lift and lean on a flat parent, so the turned card is flattened into its own plane and never cuts
  // through the card behind it.
  const frontPlace = useAnimatedStyle(() => ({
    transform: [{ translateY: -frontLift.get() }, { rotate: `${tilt.get()}deg` }],
  }));
  const back = useAnimatedStyle(() => ({ transform: [{ translateY: -backLift.get() }] }));
  const swapIcon = useAnimatedStyle(() => ({ transform: [{ rotate: `${swapTurn.get() * 180}deg` }] }));
  const chars = BALANCE.amount.split('');
  return (
    <Animated.View style={[styles.wallet, { top: oy + WALLET.top, transformOrigin: 'bottom' }, body]}>
      <View style={styles.layer}><WalletBack /></View>
      <GestureDetector gesture={pullGesture}>
        <View style={styles.cardsTouch} accessible accessibilityRole="button"
          accessibilityLabel={`${CARD_HOLDER.network} card ending ${CARD_HOLDER.last4}`} accessibilityHint="Pulls the card out of the wallet">
          <Animated.View style={[styles.card, { left: CARDS.back.x, top: CARDS.back.y - TOUCH_TOP }, back]}><Card kind="back" /></Animated.View>
          <Animated.View style={[styles.card, { left: CARDS.front.x, top: CARDS.front.y - TOUCH_TOP }, frontPlace]}>
            <Animated.View style={[styles.card3d, front]}>
              <Card kind="front" />
              <Sheen lift={frontLift} turn={turn} />
            </Animated.View>
          </Animated.View>
        </View>
      </GestureDetector>
      <View style={styles.layer} pointerEvents="none"><WalletFront /></View>
      <Text style={[styles.label, { top: POCKET_UI.labelY - 9 }]}>{BALANCE.label}</Text>
      <View style={[styles.balanceRow, { top: POCKET_UI.balanceY - 20.5 }]}
        accessible accessibilityLabel={hiddenNow ? 'Balance hidden' : `${BALANCE.amount} ${BALANCE.currency}`}>
        {chars.map((ch, i) => <BalanceChar key={i} ch={ch} index={i} hidden={hidden} reduced={reduced} />)}
        <Text style={styles.currency}>{BALANCE.currency}</Text>
      </View>
      <TapTarget onTap={onAdd} label="Add balance" style={styles.addPill}>
        <PlusGlyph color={COLOR.white} size={17} stroke={1.8} />
        <Text style={styles.addText}>Add Balance</Text>
      </TapTarget>
      <TapTarget onTap={onSwap} label="Switch card" style={[styles.iconButton, { left: POCKET_UI.iconXs[0] - POCKET_UI.icon / 2 }]}>
        <Animated.View style={swapIcon}><SwapGlyph color={COLOR.white} size={16} /></Animated.View>
      </TapTarget>
      <TapTarget onTap={onEye} label={hiddenNow ? 'Show balance' : 'Hide balance'} state={{ checked: hiddenNow }}
        style={[styles.iconButton, { left: POCKET_UI.iconXs[1] - POCKET_UI.icon / 2 }]}>
        <EyeGlyph color={COLOR.white} size={17} crossed={!hiddenNow} />
      </TapTarget>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wallet: { position: 'absolute', left: WALLET.left, width: W, height: H },
  layer: { position: 'absolute', left: -PAD, top: -PAD, width: W + PAD * 2, height: H + PAD * 2 },
  canvas: { width: W + PAD * 2, height: H + PAD * 2 },
  cardsTouch: { position: 'absolute', left: 0, top: TOUCH_TOP, width: W, height: -TOUCH_TOP + WALLET.pocketDip + 6 },
  card: { position: 'absolute', shadowColor: '#2A1462', shadowOpacity: 0 },
  card3d: { shadowColor: '#2A1462', shadowOpacity: 0 },
  sheen: { position: 'absolute', top: -60, left: 0, width: SHEEN_W, height: 300 },
  holder: {
    position: 'absolute', left: CARDS.padX, fontSize: 17.5, lineHeight: 24, fontWeight: '700',
    color: 'rgba(255,255,255,0.88)', letterSpacing: 0.4,
  },
  cardNumber: { position: 'absolute', left: CARDS.padX, fontSize: 10, lineHeight: 16, fontWeight: '600', color: 'rgba(255,255,255,0.85)', letterSpacing: 0.4 },
  cardDots: { fontSize: 8, letterSpacing: 0.8 },
  brand: {
    position: 'absolute', right: CARDS.padX, fontSize: 19.5, lineHeight: 24, fontWeight: '900', fontStyle: 'italic',
    color: COLOR.white, letterSpacing: 0,
  },
  valid: { position: 'absolute', right: CARDS.padX, fontSize: 10.5, lineHeight: 16, fontWeight: '600', color: 'rgba(255,255,255,0.92)' },
  label: { position: 'absolute', left: POCKET_UI.pillX, fontSize: 12.2, lineHeight: 18, fontWeight: '500', color: 'rgba(255,255,255,0.9)' },
  balanceRow: { position: 'absolute', left: POCKET_UI.pillX, flexDirection: 'row', alignItems: 'baseline' },
  balance: { fontSize: 24.4, lineHeight: 36, fontWeight: '700', color: COLOR.white, letterSpacing: -0.5 },
  dot: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  currency: { marginLeft: 2, fontSize: 12.3, fontWeight: '600', color: 'rgba(255,255,255,0.8)' },
  addPill: {
    position: 'absolute', left: POCKET_UI.pillX, top: POCKET_UI.pillY, width: POCKET_UI.pillW, height: POCKET_UI.pillH,
    borderRadius: POCKET_UI.pillH / 2, backgroundColor: 'rgba(255,255,255,0.16)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.22)',
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9,
  },
  addText: { fontSize: 13.8, fontWeight: '500', color: COLOR.white },
  iconButton: {
    position: 'absolute', top: POCKET_UI.iconY - POCKET_UI.icon / 2, width: POCKET_UI.icon, height: POCKET_UI.icon,
    borderRadius: POCKET_UI.icon / 2, backgroundColor: 'rgba(255,255,255,0.1)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center', justifyContent: 'center',
  },
});
