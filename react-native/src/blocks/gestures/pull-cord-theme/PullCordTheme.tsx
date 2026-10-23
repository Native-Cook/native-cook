/**
 * Pull Cord Theme: a reading app whose Day/Night switch is the lamp over the page. Pull the pendant's ball chain:
 * past the click the filament cools (or warms) and the page changes theme along the lamp's own light, nearest the
 * bulb first when it comes on, farthest first when it goes off. Tapping the pull, or the switch's accessibility
 * action, plays the same pull.
 *
 * One fixed-step simulation (cord-sim.ts) runs on the UI thread; the page is a shader of the light field
 * (light.ts, shaders.ts) and the type is coloured through a glyph mask (page-text.ts, LampScene.tsx).
 */
import { Canvas, useFonts, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { Air, Chain, Lamp, Page, chainPaths } from './LampScene';
import { BALLS, flicker, initialSim, input, lampPoint, makeGeometry, stepSim, switchFoot, type Input } from './cord-sim';
import { airUniforms, brightness, css, fieldRange, hex, lampColor, mix, pageUniforms, type LampState } from './light';
import { FRONT, HAPTIC, LOOP_MS, TAP } from './motion';
import { buildPage } from './page-text';
import { CHAIN, COPY, DAY, LAMP, SHADE } from './theme';

type Props = { autoPlay?: boolean };

const WHITE = [255, 252, 244];
const BLACK = [0, 0, 0];
const LIT_CENTRE = hex(SHADE.litCentre);
const LIT_EDGE = hex(SHADE.litEdge);

function playHaptic(kind: number) {
  if (kind === HAPTIC.click) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
}

export function PullCordTheme({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const g = useMemo(() => makeGeometry(width, height), [width, height]);
  const range = useMemo(() => fieldRange(g), [g]);
  const fonts = useFonts({ Newsreader: [require('./assets/Newsreader.ttf')] });
  const page = useMemo(() => (fonts ? buildPage(fonts, width, height, g.s) : null), [fonts, width, height, g.s]);
  const [day, setDay] = useState(true);

  // The chain settles under gravity in initialSim: do it once, not on every render.
  const [initial] = useState(() => initialSim(g, reduced, autoPlay));
  const sim = useSharedValue(initial);
  const rope = useSharedValue<number[]>(ropeOf(initial));
  const angle = useSharedValue(0);
  const heat = useSharedValue(1);
  const front = useSharedValue(1);
  const adapt = useSharedValue(1);
  const flick = useSharedValue(1);
  const clock = useSharedValue(0);
  const lightNow = useSharedValue(1);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    let s = sim.get();
    // After a Fast Refresh that changed the chain, the kept state no longer fits: start again from rest.
    if (s.px.length !== BALLS) s = initialSim(g, reduced, autoPlay);
    s.reduced = reduced;
    stepSim(s, g, frame.timeSincePreviousFrame / 1000, autoPlay);
    const pts: number[] = [];
    for (let i = 0; i < BALLS; i++) pts.push(s.px[i], s.py[i]);
    const foot = switchFoot(g, s.th);
    pts.push(foot.x, foot.y);
    rope.set(pts);
    angle.set(s.th);
    heat.set(s.heat);
    front.set(s.front);
    adapt.set(s.adapt);
    flick.set(flicker(s));
    clock.set(reduced ? 0 : s.clock % (LOOP_MS / 1000));
    if (s.light !== lightNow.get()) {
      lightNow.set(s.light);
      scheduleOnRN(setDay, s.light === 1);
    }
    for (let i = 0; i < s.events.length; i++) scheduleOnRN(playHaptic, s.events[i]);
    s.events = [];
    sim.set(s);
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const s = sim.get();
    input(s, g, ev);
    sim.set(s);
  }, [sim, g]);

  const gesture = useMemo(() => Gesture.Simultaneous(
    Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => act({ type: 'down', x: e.x, y: e.y }))
      .onUpdate((e) => act({ type: 'move', x: e.x, y: e.y, vx: e.velocityX, vy: e.velocityY }))
      .onFinalize((e, ok) => act({ type: 'up', vx: e.velocityX, vy: e.velocityY, cancelled: !ok })),
    // A pan that never moves fails, so a tap is its own recognizer; the pan above still holds the pull meanwhile.
    Gesture.Tap()
      .maxDuration(TAP.time * 1000)
      .maxDistance(TAP.slop)
      .onEnd((e, ok) => {
        if (ok) act({ type: 'tap', x: e.x, y: e.y });
      }),
  ), [act]);

  const onToggle = useCallback(() => scheduleOnUI(act, { type: 'toggle' }), [act]);

  // The page.
  const lampState = useDerivedValue<LampState>(() => ({
    angle: angle.get(), heat: heat.get(), front: front.get(), flat: reduced ? 1 : 0,
    adapt: adapt.get(), flicker: flick.get(), time: clock.get(),
  }));
  const uniforms = useDerivedValue(() => pageUniforms(g, range, lampState.get(), FRONT.band, rope.get(), BALLS));
  const air = useDerivedValue(() => airUniforms(g, lampState.get()));

  // The pendant.
  const lampT = useDerivedValue<Transforms3d>(() => [
    { translateX: g.pivotX }, { translateY: g.pivotY }, { rotate: -angle.get() }, { translateY: g.arm }, { scale: g.s },
  ]);
  const inside = useDerivedValue(() => heat.get() * heat.get());
  // The shade, the switch and the chain are lit by the room: the theme front, dimmed with the filament.
  const lit = useDerivedValue(() => front.get() * (0.45 + 0.55 * heat.get()));
  const bloom = useDerivedValue(() => brightness(heat.get()) * flick.get());
  const lamp = useDerivedValue(() => lampColor(heat.get()));
  const lampCss = useDerivedValue(() => css(lamp.get()));
  const insideColors = useDerivedValue(() => [
    css(mix(lamp.get(), LIT_CENTRE, heat.get())),
    css(mix(mix(lamp.get(), BLACK, 0.4), LIT_EDGE, heat.get())),
  ]);
  // A hot white core, the filament's colour toward the glass.
  const bulbColors = useDerivedValue(() => [
    css(mix(lamp.get(), WHITE, heat.get())),
    css(mix(lamp.get(), WHITE, heat.get() * 0.8)),
    css(lamp.get()),
    css(mix(lamp.get(), BLACK, 0.3)),
  ]);

  // The chain.
  const paths = useDerivedValue(() => chainPaths(rope.get(), BALLS, g.s));
  const rims = useDerivedValue(() => paths.get().rims);
  const bodies = useDerivedValue(() => paths.get().bodies);
  const specs = useDerivedValue(() => paths.get().specs);
  const wire = useDerivedValue(() => paths.get().wire);
  const pull = useDerivedValue<Transforms3d>(() => {
    const p = rope.get();
    const n = BALLS - 1;
    const dx = p[n * 2] - p[n * 2 - 2];
    const dy = p[n * 2 + 1] - p[n * 2 - 1];
    return [
      { translateX: p[n * 2] }, { translateY: p[n * 2 + 1] }, { rotate: Math.atan2(-dx, dy) }, { scale: g.s },
      { translateY: CHAIN.ball },
    ];
  });

  // The switch's touch target sits on the pull at rest.
  const pullRest = { x: initial.restX[BALLS - 1], y: initial.restY[BALLS - 1] };
  const bulbRest = lampPoint(g, 0, 0, LAMP.bulbY);

  return (
    <GestureDetector gesture={gesture}>
      <View style={[styles.root, { backgroundColor: DAY.paper }]} collapsable={false}>
        {/* Reading mode: no status bar, as in a book app. */}
        <StatusBar hidden />
        <Canvas style={StyleSheet.absoluteFill}>
          <Page w={width} h={height} page={page} uniforms={uniforms} />
          <Air w={width} h={height} uniforms={air} />
          <Lamp transform={lampT} look={{ front: lit, inside, bloom, insideColors, bulbColors, lampColor: lampCss }} />
          <Chain look={{ rims, bodies, specs, wire, pull, front: lit }} />
        </Canvas>

        <View
          accessible
          accessibilityRole="text"
          accessibilityLabel={page?.text ?? ''}
          style={[styles.pageText, { top: bulbRest.y + LAMP.bulbR * g.s }]}
        />
        <View
          accessible
          accessibilityRole="switch"
          accessibilityLabel={COPY.a11yLabel}
          accessibilityHint={COPY.a11yHint}
          accessibilityValue={{ text: day ? COPY.day : COPY.night }}
          accessibilityState={{ checked: day }}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={onToggle}
          style={[styles.target, {
            left: pullRest.x - (CHAIN.hit * g.s) / 2,
            top: pullRest.y + ((CHAIN.pullHeight - CHAIN.hit) * g.s) / 2,
            width: CHAIN.hit * g.s,
            height: CHAIN.hit * g.s,
          }]}
        />
      </View>
    </GestureDetector>
  );
}

function ropeOf(s: { px: number[]; py: number[] }) {
  const pts: number[] = [];
  for (let i = 0; i < BALLS; i++) pts.push(s.px[i], s.py[i]);
  pts.push(s.px[0], s.py[0]); // the housing's foot: ball 0 at rest
  return pts;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  pageText: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  target: { position: 'absolute' },
});
