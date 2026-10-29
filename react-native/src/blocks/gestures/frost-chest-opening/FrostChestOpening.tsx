import { Canvas, Picture, Skia, type SkFont, type SkTypeface, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { FcoShardBurst } from './FcoShardBurst';
import { drawScene, type Geo, type Label, type Res } from './fco-draw';
import { canClaim, claimTap, type Driver, frameAt, holdBegin, holdEnd, LOOP, makeDriver, stepDriver, T, touched } from './fco-motion';
import { LIGHT_SKSL, ORB_SKSL, RAYLIGHT_SKSL, RAYS_SKSL } from './fco-shaders';
import { COPY, L, REF_H, REF_W } from './fco-theme';

/*
 Frost Chest Opening — the reward screen of a winter event in an invented game.

 Look: a pale blue sky with soft rays turning behind a glossy cyan chest, "A BIG SURPRISE AWAITS!",
 a glass pill "1X RARE DROP" and "HOLD TO OPEN CHEST!". Holding the pill pops a glass orb out of it
 that fills with glowing blue frost while ice dashes stream into it and the chest lights up; when it
 is full the orb flies into the chest, the lid blows open in a white flash and a card spins out of
 the light: the Winter Special Costume, Super Rare, over a frosted backdrop, then "Other Rewards"
 and Claim rise in. Claim sends the card back into the chest and the screen to its start.

 autoPlay: LOOP = 9.6 s (timeline in fco-motion.ts); frame 9.6 s equals frame 0.
 Touch: hold the pill to charge (release early and it drains back); the reveal then plays by itself
 and waits; Claim closes it. 2 s after the last touch the loop resumes from where it is.
 Reduced motion: no loop; the revealed reward shows still.
*/

const raysEffect = Skia.RuntimeEffect.Make(RAYS_SKSL);
const orbEffect = Skia.RuntimeEffect.Make(ORB_SKSL);
const rayLightEffect = Skia.RuntimeEffect.Make(RAYLIGHT_SKSL);
const lightEffect = Skia.RuntimeEffect.Make(LIGHT_SKSL);

const ARROW = 'M19 12H5 M12 19l-7-7 7-7';
const HAND =
  'M10 9.5V4a2 2 0 0 0-4 0v10 M14 10V9a2 2 0 0 0-4 0v1 M18 11v-1a2 2 0 0 0-4 0 M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15';
// A four-point sparkle, 20 units across, centred on 0.
const STAR = 'M0 -10 Q1.8 -1.8 10 0 Q1.8 1.8 0 10 Q-1.8 1.8 -10 0 Q-1.8 -1.8 0 -10 Z';

/** previewTime pins the timeline at one moment (seconds), for still captures and debugging. */
type Props = { autoPlay?: boolean; previewTime?: number };

function widthOf(font: SkFont, str: string) {
  const ids = font.getGlyphIDs(str);
  return font.getGlyphWidths(ids).reduce((a, b) => a + b, 0);
}

/** A font of `face` sized so `str` is `target` points wide. */
function fit(face: SkTypeface, str: string, target: number): Label {
  const probe = Skia.Font(face, 100);
  const size = (100 * target) / widthOf(probe, str);
  const font = Skia.Font(face, size);
  return { font, width: widthOf(font, str) };
}

export function FrostChestOpening({ autoPlay = false, previewTime }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const chestClosed = useImage(require('./assets/chest-closed.webp'));
  const chestOpen = useImage(require('./assets/chest-open.webp'));
  const jacket = useImage(require('./assets/jacket.webp'));
  const xp = useImage(require('./assets/xp-boost.webp'));
  const boots = useImage(require('./assets/winter-boots.webp'));
  const shrine = useImage(require('./assets/frozen-shrine.webp'));
  const coin = useImage(require('./assets/coin.webp'));
  const crystal = useImage(require('./assets/crystal.webp'));
  const backdrop = useImage(require('./assets/backdrop.jpg'));
  const floor = useImage(require('./assets/floor.webp'));
  const cardFront = useImage(require('./assets/card-front.webp'));
  const cardBack = useImage(require('./assets/card-back.webp'));
  const black = useFont(require('./assets/fonts/Poppins-Black.ttf'), 100);
  const extra = useFont(require('./assets/fonts/Poppins-ExtraBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Poppins-Bold.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Poppins-SemiBold.ttf'), 100);
  const regular = useFont(require('./assets/fonts/Poppins-Regular.ttf'), 100);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => ({ w, h, top: insets.top, bottom: insets.bottom, sx: w / REF_W, sy: h / REF_H }), [w, h, insets.top, insets.bottom]);

  const res = useMemo<Res | null>(() => {
    if (!chestClosed || !chestOpen || !jacket || !xp || !boots || !shrine || !coin || !crystal || !backdrop || !floor || !cardFront || !cardBack) return null;
    if (!black || !extra || !bold || !semi || !regular || !raysEffect || !orbEffect || !rayLightEffect || !lightEffect) return null;
    const faces = [black, extra, bold, semi, regular].map((f) => f.getTypeface());
    const [fBlack, fExtra, fBold, fSemi, fRegular] = faces;
    if (!fBlack || !fExtra || !fBold || !fSemi || !fRegular) return null;
    const s = geo.sx;
    const arrow = Skia.Path.MakeFromSVGString(ARROW);
    const hand = Skia.Path.MakeFromSVGString(HAND);
    const star = Skia.Path.MakeFromSVGString(STAR);
    if (!arrow || !hand || !star) return null;
    return {
      chestClosed, chestOpen, jacket, items: [xp, boots, shrine], coin, crystal, backdrop, floor, cardFront, cardBack,
      rays: raysEffect, rayLight: rayLightEffect, light: lightEffect, orb: orbEffect, arrow, hand, star,
      text: {
        balance: fit(fBold, COPY.balance, L.balanceW * s),
        eyebrow: fit(fBlack, COPY.eyebrow, L.eyebrowW * s),
        title: fit(fBlack, COPY.title, L.titleW * s),
        drop: fit(fExtra, COPY.drop, L.dropW * s),
        hint1: fit(fBold, COPY.hint1, L.hintW * s),
        hint2: fit(fBold, COPY.hint2, L.hintW2 * s),
        reward1: fit(fExtra, COPY.reward1, L.rewardW * s),
        reward2: fit(fExtra, COPY.reward2, L.rewardW2 * s),
        rarity: fit(fBold, COPY.rarity, L.rarityTextW * s),
        others: fit(fSemi, COPY.others, L.othersW * s),
        items: [fit(fRegular, COPY.items[0], L.itemW[0] * s), fit(fRegular, COPY.items[1], L.itemW[1] * s), fit(fRegular, COPY.items[2], L.itemW[2] * s)],
        claim: fit(fExtra, COPY.claim, L.claimTextW * s),
      },
    };
  }, [chestClosed, chestOpen, jacket, xp, boots, shrine, coin, crystal, backdrop, floor, cardFront, cardBack, black, extra, bold, semi, regular, geo.sx]);

  // The burst's ice chips are drawn in 3D with WebGPU when the native module is there; Skia draws
  // flat ones otherwise.
  const [gpuChips, setGpuChips] = useState(false);

  const driver = useSharedValue<Driver>(makeDriver());
  const clock = useSharedValue(0);
  const time = useSharedValue(reduced ? T.settled : 0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver());
    clock.set(previewTime ?? 0);
    time.set(previewTime ?? (reduced ? T.settled : 0));
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // Debug (development builds only): `globalThis.__frostChest.at(2.7)` pins the timeline at a
  // moment, `.play()` lets it run again; used to capture frames next to the reference video.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __frostChest?: unknown };
    g.__frostChest = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1) };
    return () => {
      delete g.__frostChest;
    };
  }, [debugT]);

  const frameCb = useFrameCallback((info) => {
    if (debugT.get() >= 0) {
      time.set(debugT.get());
      clock.set(debugT.get());
      return;
    }
    const dt = (info.timeSincePreviousFrame ?? 16) / 1000;
    const now = clock.get() + Math.min(dt, 0.05);
    clock.set(now);
    const d = driver.get();
    const before = d.t;
    const t = stepDriver(d, dt, now, auto);
    time.set(t);
    // Haptics only answer a finger: autoPlay stays silent.
    if (d.mode !== 0) {
      if (before < T.press + 0.02 && t >= T.press + 0.02 && d.mode === 1) scheduleOnRN(buzz, 0);
      for (const q of [0.25, 0.5, 0.75]) {
        const at = T.fillKnee + (T.fillEnd - T.fillKnee) * q;
        if (before < at && t >= at && d.mode === 1) scheduleOnRN(buzz, 0);
      }
      if (before < T.impact && t >= T.impact) scheduleOnRN(buzz, 2);
      if (before < 3.68 && t >= 3.68) scheduleOnRN(buzz, 0); // the card faces you
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, previewTime, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) {
      const t = time.get();
      const idle = auto && previewTime === undefined ? clock.get() % LOOP : t % LOOP;
      drawScene(c, res, geo, frameAt(t, idle), gpuChips);
    } else c.drawColor(Skia.Color('#c3d7f6'));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, auto, previewTime, gpuChips]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // Touch areas, in screen points.
  const pill = { x: L.pillX * geo.sx - 16, y: L.pillY * geo.sy - 30, w: L.pillW * geo.sx + 32, h: (L.hintBase2 - L.pillY) * geo.sy + 46 };
  const claim = { x: L.claimX * geo.sx, y: L.claimY * geo.sy - 6, w: L.claimW * geo.sx, h: L.claimH * geo.sy + 12 };
  const holding = useSharedValue(false);
  const claiming = useSharedValue(false);

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(!reduced && previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const inPill = e.x >= pill.x && e.x <= pill.x + pill.w && e.y >= pill.y && e.y <= pill.y + pill.h;
      const inClaim = e.x >= claim.x && e.x <= claim.x + claim.w && e.y >= claim.y && e.y <= claim.y + claim.h;
      touched(d, now);
      holding.set(inPill && holdBegin(d, now));
      claiming.set(inClaim && canClaim(d));
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      if (holding.get()) holdEnd(d, now);
      if (claiming.get() && Math.hypot(e.translationX, e.translationY) < 12 && claimTap(d, now)) scheduleOnRN(buzz, 0);
      holding.set(false);
      claiming.set(false);
      d.last = now;
    });

  const openForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      if (!claimTap(d, now)) {
        d.t = T.fillEnd;
        d.mode = 3;
        d.stop = T.settled;
        d.last = now;
      }
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="A big surprise awaits. Rare chest with one rare drop."
          accessibilityHint="Hold to open the chest. Then claim the Winter Special Costume and other rewards."
          onAccessibilityTap={openForAccessibility}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      {size && !reduced ? (
        <FcoShardBurst time={time} width={geo.w} height={geo.h} origin={{ x: L.openCX * geo.sx, y: 395 * geo.sy }} unit={geo.sx} onAvailable={setGpuChips} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#c3d7f6' },
});
