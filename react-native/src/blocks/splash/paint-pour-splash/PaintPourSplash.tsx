import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Fill, FilterMode, Glyphs, Image, ImageShader, MipmapMode, Shader, Skia, useFont, useImage } from '@shopify/react-native-skia';
import type { SkFont } from '@shopify/react-native-skia';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { StatusBar } from 'expo-status-bar';

import { PAINT_POUR_SKSL } from './paintPourShader';

/*
 Paint Pour Splash — the launch screen of an invented paint brand, Hue. The approved still fills the
 screen: rainbow paint pours from a tilted can into a splash crown on a pale floor, with its
 reflection below. One full-screen shader makes the still flow: the paint keeps sliding down the
 stream and the pool ripples outward; the wordmark and tagline sit on top, still.

 Loop (LOOP = 4 s): the stream flows steadily (two flow phases of 2 s each, cross-fading every
 second) and the pool ripples once a second. No touch (a splash screen). Reduce Motion: the shader is
 not run; the image is drawn still.
*/

/** One loop, in seconds. */
export const LOOP = 4.0;

const PAINT = require('./assets/paint.webp');
const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const INTER_MEDIUM = require('./assets/Inter-Medium.ttf');

const EFFECT = Skia.RuntimeEffect.Make(PAINT_POUR_SKSL);
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
/**
 No sRGB → Display P3 conversion here, on purpose. The repo converts shader output on iOS because
 colours computed from constants are written to a Display P3 layer as they are; but this shader's
 colours come from the image, which Skia already converts into the P3 destination before the shader
 samples it, so converting again dulls the paint. Measured on the iPhone 18 Pro simulator against the
 designer's stills: off, every static sample within 1 level (orange 249,120,3 vs 250,120,4); on,
 233,127,49 (see PR).
*/
const P3 = 0;

/** paint.webp is 708 × 1536 px, fitted to the screen's height and centred. */
const IMG_W = 708;
const IMG_H = 1536;

type GlyphRun = { glyphs: { id: number; pos: { x: number; y: number } }[] };

/** Glyphs set with tracking after every letter, right-aligned so the advance ends at `right`. */
function setRight(font: SkFont, text: string, right: number, baseline: number, tracking: number): GlyphRun {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i] + tracking;
  // Like the reference (canvas letterSpacing), the last letter's tracking counts in the width.
  let x = right - total;
  const glyphs = [];
  for (let i = 0; i < ids.length; i++) {
    glyphs.push({ id: ids[i], pos: { x, y: baseline } });
    x += widths[i] + tracking;
  }
  return { glyphs };
}

type Props = {
  /** Plays the flow on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function PaintPourSplash({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const paint = useImage(PAINT);
  const archivo = useFont(ARCHIVO, 120);
  const inter = useFont(INTER_MEDIUM, 16);
  const fy = height / 852;
  const fitW = (height * IMG_W) / IMG_H;
  const fitX = (width - fitW) / 2;

  // Right edges kept from the right side: the wordmark's advance (with its trailing tracking) at
  // 24 pt, i.e. its last letter's advance at 16 pt (x 377 on 393), the tagline at 28 pt.
  const text = useMemo(() => {
    if (!archivo || !inter) return null;
    return {
      hue: setRight(archivo, 'Hue', width - 24, 400 * fy, -8),
      line1: setRight(inter, 'Colour for', width - 28, 432 * fy, 0),
      line2: setRight(inter, 'every wall.', width - 28, 452 * fy, 0),
    };
  }, [archivo, inter, width, fy]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    clock.set(clockBase.get() + (info.timestamp - startedAt.get()) / 1000);
  }, false);
  const running = active && autoPlay && !reduced;
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const uniforms = useDerivedValue(() => ({
    fit: [fitX, 0, fitW, height],
    img: [IMG_W, IMG_H],
    time: ((clock.get() % LOOP) + LOOP) % LOOP,
    p3: P3,
  }));

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill}>
        {paint && EFFECT && !reduced ? (
          <Fill>
            <Shader source={EFFECT} uniforms={uniforms}>
              <ImageShader image={paint} fit="none" tx="clamp" ty="clamp" sampling={SAMPLING} />
            </Shader>
          </Fill>
        ) : null}
        {paint && reduced ? <Image image={paint} x={fitX} y={0} width={fitW} height={height} fit="fill" /> : null}
        {text && archivo ? <Glyphs font={archivo} glyphs={text.hue.glyphs} color="#16161A" /> : null}
        {text && inter ? <Glyphs font={inter} glyphs={text.line1.glyphs} color="#55555E" /> : null}
        {text && inter ? <Glyphs font={inter} glyphs={text.line2.glyphs} color="#55555E" /> : null}
      </Canvas>
      <View
        accessible
        accessibilityRole="header"
        accessibilityLabel="Hue. Colour for every wall."
        style={StyleSheet.absoluteFill}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#E9E9EC' },
});
