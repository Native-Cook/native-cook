import {
  BlurMask, Canvas, Circle, Group, Image as SkiaImage, ImageShader,
  LinearGradient, Path, RadialGradient, Rect, RoundedRect, Shader, Skia,
} from '@shopify/react-native-skia';
import type { SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import Animated, {
  FadeInDown, useAnimatedReaction, useAnimatedStyle, useDerivedValue, type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  CARD_COUNT, CARD_HOLO, CARD_MARKS, CARD_STACK_DEPTH, RARE_INTERACTION_MS, RARE_REVEAL_MS,
  cardProjection,
  commonCardArrival, isRareCard, rareCardPose,
} from './card-reveal-motion';
import { clamp, REFERENCE_LAYOUT } from './model';
import type { CardRevealState } from './use-card-reveal';

// Light lives in card UV space; its placement is driven by the card normal.
// A stationary finger holds the foil. Only the measured reveal sweep uses time.
const CARD_MATERIAL = Skia.RuntimeEffect.Make(`
uniform shader artwork;
uniform float2 size;
uniform float2 tilt;
uniform float holo;
uniform float shine;
uniform float sweep;
uniform float flash;

half4 main(float2 p) {
  half4 base = artwork.eval(p);
  float2 uv = p / size + 0.5;
  float slope = uv.x * 0.72 + uv.y * 0.58;
  float phase = tilt.x * 0.82 + tilt.y * 0.6;
  float3 rainbow = 0.5 + 0.5 * cos(6.28318 *
    (slope * 1.65 - phase + float3(0.0, 0.33, 0.67)));
  float band = pow(max(0.0, 1.0 - abs(slope - 0.63 - phase * 0.8) * 2.6), 2.0);
  float border = 1.0 - smoothstep(0.012, 0.04,
    min(min(uv.x, 1.0-uv.x), min(uv.y, 1.0-uv.y)));
  float movement = min(1.0, abs(tilt.x) * 1.7 + abs(tilt.y));
  float foil = holo * (0.045 + movement * 0.26 + border * 0.18) * (0.28 + band * 0.72);
  float grain = fract(sin(dot(floor(uv * size * 0.8), float2(12.9898, 78.233))) * 43758.5453);
  foil *= 0.88 + grain * 0.24;
  float3 color = mix(base.rgb, min(float3(1.0), base.rgb * 0.85 + rainbow * 0.72), foil);
  float glint = pow(max(0.0, 1.0 - abs(slope - (sweep * 1.9 - 0.25)) * 5.0), 3.0);
  float reflection = pow(max(0.0, 1.0 - abs(slope - 0.25 - phase) * 4.0), 6.0);
  float white = shine * glint * flash * 0.8 + reflection * movement * (0.055 + holo * 0.16);
  color = 1.0 - (1.0 - color) * (1.0 - white);
  color *= 1.0 - min(0.1, abs(tilt.x) * 0.065);
  return half4(color, base.a);
}
`)!;

// The hint and the button under the last card, with a little air below them.
const FOOTER_HEIGHT = 84;

function rareFaceHaptic() {
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => undefined);
}

function RevealCard({
  cardIndex, image, back, state, width, height, top, reducedMotion,
}: Readonly<{
  cardIndex: number; image: SkImage | null; back: SkImage | null;
  state: CardRevealState; width: number; height: number;
  top: SharedValue<number>; reducedMotion: boolean;
}>) {
  // Keep mapper inputs local: capturing state subscribes to every shared value.
  const { index, exit, exiting, revealMs, yaw: dragYaw, pitch: dragPitch, exitDirection } = state;
  const cardWidth = width * REFERENCE_LAYOUT.card.width;
  const cardHeight = height * REFERENCE_LAYOUT.card.height;
  const radius = cardWidth * 0.025;
  const pose = useDerivedValue(() => {
    const relative = cardIndex - index.get();
    const progress = exit.get();
    const leaving = relative === 0 && exiting.get() === 1;
    const rare = relative === 0 && isRareCard(cardIndex) && !leaving
      ? rareCardPose(revealMs.get(), reducedMotion) : rareCardPose(2600, true);
    const arrival = relative === 0 && !leaving && !isRareCard(cardIndex)
      ? commonCardArrival(revealMs.get(), reducedMotion).pop : 0;
    const tiltMix = relative > 0 ? 1 - progress : 1;
    const yaw = dragYaw.get() * tiltMix;
    const pitch = dragPitch.get() * tiltMix;
    const depth = Math.max(0, relative) * cardWidth * CARD_STACK_DEPTH;
    return {
      x: width / 2 - Math.sin(yaw) * depth +
        (leaving && !reducedMotion ? exitDirection.get() * width * 1.22 * progress : 0),
      y: top.get() + cardHeight / 2 + Math.sin(pitch) * depth -
        (leaving && !reducedMotion ? Math.sin(progress * Math.PI) * cardWidth * 0.025 : 0),
      yaw: reducedMotion ? 0 : yaw + rare.yaw +
        (leaving ? exitDirection.get() * 0.22 * progress : 0),
      pitch: reducedMotion ? 0 : pitch + rare.pitch,
      roll: reducedMotion ? 0 : rare.roll,
      scale: rare.scale * (1 + arrival * 0.032) *
        (1 - Math.max(0, relative) * 0.003 * (1 - progress)),
    };
  });
  const frontMatrix = useDerivedValue(() => cardProjection(pose.get(), cardWidth));
  const backMatrix = useDerivedValue(() => cardProjection(pose.get(), cardWidth, true));
  const opacity = useDerivedValue(() => {
    const relative = cardIndex - index.get();
    if (relative < 0 || relative >= CARD_COUNT) return 0;
    if (relative === 0) return reducedMotion ? 1 - exit.get() : 1;
    const rare = isRareCard(index.get())
      ? rareCardPose(revealMs.get(), reducedMotion).light : 0;
    return 1 - rare;
  });
  const frontOpacity = useDerivedValue(() => Math.cos(pose.get().yaw) >= 0.002 ? opacity.get() : 0);
  const backOpacity = useDerivedValue(() => Math.cos(pose.get().yaw) < -0.002 ? opacity.get() : 0);
  const artworkOpacity = useDerivedValue(() => {
    const relative = cardIndex - index.get();
    // The reference video shows clean paper edges on unrevealed layers. Bring in the
    // next face as soon as the outgoing card starts uncovering it.
    return relative === 0 || (relative === 1 && exiting.get() === 1) ? 1 : 0;
  });
  const uniforms = useDerivedValue(() => {
    const relative = cardIndex - index.get();
    const rare = isRareCard(cardIndex);
    // Rare cards keep their measured sweep; plain cards get a softer arrival glint.
    const light = relative !== 0 ? rareCardPose(2600, true)
      : rare ? rareCardPose(revealMs.get(), reducedMotion)
        : commonCardArrival(revealMs.get(), reducedMotion);
    return {
      size: [cardWidth, cardHeight],
      tilt: [Math.sin(pose.get().yaw), pose.get().pitch],
      holo: CARD_HOLO[cardIndex], shine: rare ? 1 : 0.7,
      sweep: light.sweep, flash: light.flash,
    };
  });
  const borderGlow = useDerivedValue(() => cardIndex === index.get() && isRareCard(cardIndex)
    ? rareCardPose(revealMs.get(), reducedMotion).flash * 0.82 : 0);
  const rect = useMemo(() => ({
    rect: { x: -cardWidth / 2, y: -cardHeight / 2, width: cardWidth, height: cardHeight },
    rx: radius, ry: radius,
  }), [cardWidth, cardHeight, radius]);

  if (!image || !back) return null;
  return <>
    <Group matrix={frontMatrix} opacity={frontOpacity}>
      <RoundedRect x={-cardWidth / 2 - 0.7} y={-cardHeight / 2 + 1}
        width={cardWidth + 1.4} height={cardHeight + 1.4} r={radius}
        color="#43516d" opacity={0.26}>
        <BlurMask blur={width * 0.012} style="normal" />
      </RoundedRect>
      <RoundedRect x={-cardWidth / 2 - 0.6} y={-cardHeight / 2 - 0.6}
        width={cardWidth + 1.2} height={cardHeight + 1.2} r={radius} color="#f6f7fc" />
      <Group clip={rect} opacity={artworkOpacity}>
        <Rect x={-cardWidth / 2} y={-cardHeight / 2} width={cardWidth} height={cardHeight}>
          <Shader source={CARD_MATERIAL} uniforms={uniforms}>
            <ImageShader image={image} fit="fill" rect={rect.rect} />
          </Shader>
        </Rect>
      </Group>
      <RoundedRect x={-cardWidth / 2} y={-cardHeight / 2} width={cardWidth}
        height={cardHeight} r={radius} style="stroke" strokeWidth={2.2}
        color="#f6feff" opacity={borderGlow}>
        <BlurMask blur={3} style="normal" />
      </RoundedRect>
    </Group>
    <Group matrix={backMatrix} opacity={backOpacity}>
      <RoundedRect x={-cardWidth / 2 - 0.9} y={-cardHeight / 2 - 0.9}
        width={cardWidth + 1.8} height={cardHeight + 1.8} r={radius} color="#e1e4ee" />
      <Group clip={rect}>
        <SkiaImage image={back} x={-cardWidth / 2} y={-cardHeight / 2}
          width={cardWidth} height={cardHeight} fit="fill" />
      </Group>
    </Group>
  </>;
}

/** A closed outline around a centre: four corners make a facet, ten a five-pointed mark. */
function markPath(x: number, y: number, size: number, facet = false) {
  const builder = Skia.PathBuilder.Make();
  const points = facet ? 4 : 10;
  for (let i = 0; i < points; i++) {
    const angle = -Math.PI / 2 + i * Math.PI * 2 / points;
    const radius = facet ? size * (i % 2 ? 0.65 : 1) : size * (i % 2 ? 0.46 : 1);
    const px = x + Math.cos(angle) * radius;
    const py = y + Math.sin(angle) * radius;
    if (i === 0) builder.moveTo(px, py); else builder.lineTo(px, py);
  }
  return builder.close().build();
}

function RarityMark({ slot, state, width, height }: Readonly<{
  slot: number; state: CardRevealState; width: number; height: number;
}>) {
  const { index, opacity: sceneOpacity, exit, revealMs, ready } = state;
  const size = width * 0.025;
  const x = width * (1 - REFERENCE_LAYOUT.card.width) / 2 + size + slot * size * 2.5;
  const y = height * (1 + REFERENCE_LAYOUT.card.height) / 2 + width * 0.065;
  const small = useMemo(() => markPath(x, y, size * 0.78, true), [x, y, size]);
  const large = useMemo(() => markPath(x, y, size), [x, y, size]);
  const path = useDerivedValue(() => isRareCard(index.get()) ? large : small);
  const opacity = useDerivedValue(() => {
    if (slot >= CARD_MARKS[index.get()]) return 0;
    // The first card's reveal clock starts when the stack becomes interactive.
    if (index.get() === 0 && !ready.get()) return 0;
    const delay = isRareCard(index.get()) ? 2100 : 120;
    return sceneOpacity.get() * (1 - exit.get()) *
      clamp((revealMs.get() - delay - slot * 90) / 180, 0, 1);
  });
  const colors = useDerivedValue(() => isRareCard(index.get())
    ? ['#fff9c8', '#ffdf61', '#c18c24'] : ['#fafcff', '#dde9ee', '#8da1b3']);
  return <Group opacity={opacity}>
    <Path path={path} color="#263442" style="stroke" strokeWidth={size * 0.34} strokeJoin="round" />
    <Path path={path}>
      <LinearGradient start={{ x, y: y - size }} end={{ x, y: y + size }} colors={colors} />
    </Path>
  </Group>;
}

function RevealSparkle({ id, state, width, height, reducedMotion }: Readonly<{
  id: number; state: CardRevealState; width: number; height: number; reducedMotion: boolean;
}>) {
  const { index, revealMs } = state;
  const x = width * (0.06 + ((id * 37 + 17) % 89) / 100);
  const y = height * (0.17 + ((id * 29 + 3) % 63) / 100);
  const size = width * (id % 3 === 0 ? 0.015 : 0.007);
  const path = useMemo(() => markPath(x, y, size, true), [x, y, size]);
  const opacity = useDerivedValue(() => {
    if (reducedMotion || !isRareCard(index.get())) return 0;
    const t = revealMs.get();
    return rareCardPose(t).light * Math.pow(Math.max(0, Math.sin(t / 170 + id * 2.1)), 4) * 0.9;
  });
  return <Group opacity={opacity}>
    <Circle cx={x} cy={y} r={size * 1.4} color="#fff4bb" opacity={0.55}>
      <BlurMask blur={size * 1.5} style="normal" />
    </Circle>
    <Path path={path} color="#fffef1" />
  </Group>;
}

export function CardRevealScene({
  state, width, height, top, opacity, reducedMotion, onReplay, scripted, bottomInset = 0,
}: Readonly<{
  state: CardRevealState; width: number; height: number; top: SharedValue<number>;
  opacity: SharedValue<number>; reducedMotion: boolean; onReplay: () => void;
  /** 1 while the autoPlay script drives the cards: a turned face gives no haptic. */
  scripted: SharedValue<number>;
  /** The screen's bottom safe-area inset; the ending stays above it. */
  bottomInset?: number;
}>) {
  const { index, ready, busy, revealMs, exit, opacity: sceneOpacity, dragging } = state;
  const [labelIndex, setLabelIndex] = useState(0);
  const [interactive, setInteractive] = useState(false);
  useAnimatedReaction(() => index.get(), (next, previous) => {
    if (next !== previous) scheduleOnRN(setLabelIndex, next);
  });
  useAnimatedReaction(() => ready.get() && !busy.get(), (next, previous) => {
    if (next !== previous) scheduleOnRN(setInteractive, next);
  });
  // The rare face lands with its foil flash.
  useAnimatedReaction(() => ready.get() && isRareCard(index.get()) &&
    revealMs.get() >= 1250 && revealMs.get() < RARE_REVEAL_MS, (landed, previous) => {
    if (landed && previous === false && scripted.get() === 0) scheduleOnRN(rareFaceHaptic);
  }, [scripted]);
  const aura = useDerivedValue(() => isRareCard(index.get())
    ? rareCardPose(revealMs.get(), reducedMotion).light * (1 - exit.get()) : 0);
  const uiStyle = useAnimatedStyle(() => ({
    opacity: sceneOpacity.get(), pointerEvents: interactive ? 'auto' : 'none',
  }));
  const arrowStyle = useAnimatedStyle(() => ({
    opacity: dragging.get() || busy.get() || index.get() === CARD_COUNT - 1 ? 0 : 0.65,
  }));
  const last = labelIndex === CARD_COUNT - 1;
  return <>
    <Canvas style={styles.overlay} colorSpace="srgb">
      <Group opacity={opacity}>
        <Group opacity={aura}>
          <Rect x={0} y={0} width={width} height={height}>
            <LinearGradient start={{ x: 0, y: 0 }} end={{ x: width, y: height }}
              colors={['#f8ad72', '#e055ab', '#cf4be3', '#777af1', '#67d8e8']} />
          </Rect>
          <Rect x={0} y={0} width={width} height={height}>
            <RadialGradient c={{ x: width * 0.5, y: height * 0.46 }} r={height * 0.58}
              colors={['#fffbe98c', '#fff8ef00']} />
          </Rect>
        </Group>
        {Array.from({ length: 18 }, (_, id) => <RevealSparkle key={id} id={id} state={state}
          width={width} height={height} reducedMotion={reducedMotion} />)}
        {[4, 3, 2, 1, 0].map((cardIndex) => <RevealCard key={cardIndex} cardIndex={cardIndex}
          image={state.images[cardIndex]} back={state.back} state={state} width={width}
          height={height} top={top} reducedMotion={reducedMotion} />)}
        {[0, 1, 2].map((slot) => <RarityMark key={slot} slot={slot} state={state}
          width={width} height={height} />)}
      </Group>
    </Canvas>
    <Animated.View style={[styles.overlay, uiStyle]}
      accessibilityElementsHidden={!interactive}
      aria-hidden={!interactive}
      importantForAccessibility={interactive ? 'auto' : 'no-hide-descendants'}>
      {!last && <Animated.View style={[styles.arrows, { top: height / 2 - 24 }, arrowStyle]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Reveal next card to the left"
          disabled={!interactive} onPress={() => state.advance(-1)} style={styles.arrowButton}>
          <Text style={styles.chevron}>‹</Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Reveal next card"
          disabled={!interactive} onPress={() => state.advance(1)} style={styles.arrowButton}>
          <Text style={styles.chevron}>›</Text>
        </Pressable>
      </Animated.View>}
      {/* The ending waits for the last card's reveal instead of appearing under it. */}
      {last && <Animated.View
        style={[styles.footer, { top: Math.min(height * 0.835, height - bottomInset - FOOTER_HEIGHT) }]}
        entering={FadeInDown.delay(reducedMotion ? 0 : RARE_INTERACTION_MS).duration(420)}>
        <Text style={styles.hint}>Every card revealed</Text>
        <Pressable accessibilityRole="button" disabled={!interactive} onPress={onReplay}
          style={({ pressed }) => [styles.replay, { opacity: pressed ? 0.65 : 1 }]}>
          <Text style={styles.replayText}>Open another pack</Text>
        </Pressable>
      </Animated.View>}
    </Animated.View>
  </>;
}

const styles = StyleSheet.create({
  overlay: { position: 'absolute', top: 0, bottom: 0, left: 0, right: 0, pointerEvents: 'none' },
  arrows: { position: 'absolute', left: 3, right: 3, flexDirection: 'row', justifyContent: 'space-between', pointerEvents: 'box-none' },
  arrowButton: { width: 44, height: 48, alignItems: 'center', justifyContent: 'center' },
  chevron: { fontSize: 38, lineHeight: 42, color: '#ffffff', fontWeight: '300' },
  footer: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 14, pointerEvents: 'box-none' },
  hint: { fontSize: 12, color: '#7d8798', letterSpacing: 0.2 },
  replay: { minHeight: 46, paddingHorizontal: 24, justifyContent: 'center', borderRadius: 24, backgroundColor: '#ffffffae', borderWidth: 1, borderColor: '#ffffffd9' },
  replayText: { color: '#46576e', fontSize: 14, fontWeight: '500' },
});
