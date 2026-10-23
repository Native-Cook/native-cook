import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View, type TextStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Line, Polygon } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useCalmMotion } from './a11y';
import { buttonShown, labelState, pressScale, sheetRadius, type Geo } from './fold';
import { avatarsShown, gooColors, type GooGeo } from './goo';
import {
  CUP,
  ease,
  EASE_OUT,
  FLIGHT,
  FOLD,
  GOO,
  GUST,
  INVITE,
  INVITE_END,
  kick,
  LOOP,
  REDUCED,
  RESUME_AFTER,
  SCAN,
  seg,
  START,
} from './motion';
import { Overlay, type DropImg } from './Overlay';
import { ASCENT, box, colors, copy, DESCENT, FONT, radius, REF_TOP, REF_W, space, type as T } from './theme';

type Props = {
  /** Loops the showcase (7.8 s); touch takes over and hands back 2 s after it has played out. Off under Reduce Motion or a screen reader. */
  autoPlay?: boolean;
};

const FONT_FILES = {
  [FONT.medium]: require('./assets/Anybody-Medium.ttf'),
  [FONT.semi]: require('./assets/Anybody-SemiBold.ttf'),
  [FONT.bold]: require('./assets/Anybody-Bold.ttf'),
  [FONT.extra]: require('./assets/Anybody-ExtraBold.ttf'),
};

const IMG = {
  cup: require('./assets/cup.png'),
  h1: require('./assets/head-1.png'),
  h2: require('./assets/head-2.png'),
  h3: require('./assets/head-3.png'),
  h5: require('./assets/head-5.png'),
};

type TextSpec = { size: number; font: string; ls: number; color: string; lineHeight?: number };

/** Style of a one-line Text whose baseline sits at `baseline` (dp from the top of its parent). */
function lineStyle(spec: TextSpec, baseline: number): TextStyle {
  const lh = spec.lineHeight ?? Math.round(spec.size * 1.3);
  const top = baseline - ((lh - (ASCENT + DESCENT) * spec.size) / 2 + ASCENT * spec.size);
  return {
    position: 'absolute',
    top,
    height: lh,
    fontFamily: spec.font,
    fontSize: spec.size,
    lineHeight: lh,
    letterSpacing: spec.ls,
    color: spec.color,
    includeFontPadding: false,
  };
}

/** Clock modes. */
const AUTO = 0;
const MANUAL = 1;

/** The flight starts where the dart was folded: the right half of the button, at its middle. */
const PATH_START = FLIGHT.points[0];

const hapticLight = () => {
  if (Platform.OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
};
const hapticSuccess = () => {
  if (Platform.OS === 'web') return;
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
};

/** 0 → 1 → 0 over [0, 1] (reduced-motion dips). */
function tri(u: number) {
  'worklet';
  if (u <= 0 || u >= 1) return 0;
  return u < 0.5 ? ease(EASE_OUT, u * 2) : 1 - ease(EASE_OUT, (u - 0.5) * 2);
}

function PlaneGlyph({ fill, fold, size }: { fill: string; fold: string; size: number }) {
  return (
    <Svg width={size + 2} height={size + 2} viewBox="-1 -1 58 57">
      <Polygon points="56,0 0,22 24,31" fill={fill} stroke={fill} strokeWidth={1.5} strokeLinejoin="round" />
      <Polygon points="56,0 24,31 33,55" fill={fill} stroke={fill} strokeWidth={1.5} strokeLinejoin="round" />
      <Line x1={55} y1={1} x2={25} y2={30} stroke={fold} strokeWidth={1.6} strokeLinecap="round" />
    </Svg>
  );
}

type AvatarSpec = (typeof box.avatars)[number];

/** A resting avatar. While the contacts are liquid, the Skia overlay draws it instead. */
function Avatar({ a, scan, left, top, reduced }: { a: AvatarSpec; scan: SharedValue<number>; left: number; top: number; reduced: boolean }) {
  const img = a.d * a.head;
  const style = useAnimatedStyle(() => ({ opacity: reduced || avatarsShown(scan.get()) ? 1 : 0 }));
  return (
    <Animated.View
      style={[
        { position: 'absolute', left, top, width: a.d, height: a.d, borderRadius: a.d / 2, backgroundColor: a.bg, overflow: 'hidden' },
        style,
      ]}
    >
      <Image
        source={IMG[a.src as keyof typeof IMG]}
        contentFit="cover"
        style={{ position: 'absolute', width: img, height: img, left: (a.d - img) / 2, top: (a.d - img) / 2 + a.dy * a.d }}
      />
    </Animated.View>
  );
}

export function FindFriendsInvite({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [fontsReady] = useFonts(FONT_FILES);
  // Followed live: Reduce Motion turns the presses into dips; it or a screen reader stops the loop.
  const { reduceMotion: reduced, calm } = useCalmMotion();
  const dy = insets.top - REF_TOP; // pin y → screen y
  const dx = width / 2 - REF_W / 2; // centred content
  const y = (v: number) => v + dy;

  const cardW = width - space.cardMargin * 2;
  const pill = box.pill;

  const stops = colors.pageStops;
  const pageColors = stops.map((s) => s[1]) as [string, string, ...string[]];
  const pageLocs = stops.map((s) => Math.min(1, (s[0] + dy * (s[0] / 874)) / Math.max(height, 874))) as [number, number, ...number[]];

  // Button boxes (screen dp).
  const inv = { left: space.cardMargin + space.cardPadInvite, top: y(box.invite.y), w: cardW - space.cardPadInvite * 2, h: box.invite.h };
  const chk = { left: space.cardMargin + space.cardPadCheck, top: y(box.check.y), w: cardW - space.cardPadCheck * 2, h: box.check.h };
  const plane = { left: box.plane.x - 1 + dx, top: y(box.plane.y) - 1, size: box.plane.size };
  const cupBox = { w: box.cup.w, h: box.cup.h, left: box.cup.cx + dx - box.cup.w / 2, top: y(box.cup.cy) - box.cup.h / 2 };

  /* ---------- overlay geometry (paper and liquid) ---------- */
  const geo: Geo = useMemo(
    () => ({
      W: inv.w,
      H: inv.h,
      left: inv.left,
      top: inv.top,
      camX: FLIGHT.cameraAt[0] + dx,
      camY: FLIGHT.cameraAt[1] + dy,
      offX: inv.left + (3 * inv.w) / 4 - PATH_START[0],
      offY: inv.top + inv.h / 2 - PATH_START[1],
    }),
    [inv.w, inv.h, inv.left, inv.top, dx, dy],
  );
  const gooGeo: GooGeo = useMemo(() => {
    const rest = box.avatars.map((a) => ({ x: a.cx + dx, y: a.cy + dy, r: a.d / 2 }));
    let ax = 0;
    let ay = 0;
    let aw = 0;
    for (const b of rest) {
      ax += b.x * b.r * b.r;
      ay += b.y * b.r * b.r;
      aw += b.r * b.r;
    }
    return { rest, cx: ax / aw, cy: ay / aw, width };
  }, [dx, dy, width]);
  const gooCols = useMemo(() => gooColors(box.avatars.map((a) => a.bg)), []);
  const faces = useMemo(
    () => box.avatars.map((a) => ({ image: IMG[a.src as keyof typeof IMG], cx: a.cx + dx, cy: a.cy + dy, d: a.d, head: a.head, dy: a.dy })),
    [dx, dy],
  );

  // The droplets' faces: each head drawn as it is in its resting avatar (scale and offset in the circle).
  const dropFaces: DropImg[] = useMemo(
    () =>
      GOO.drops.map((dr, j) => {
        const src = GOO.dropFaces.heads[j];
        const a = box.avatars.find((v) => v.src === src) ?? box.avatars[0];
        return { image: IMG[src], head: a.head, dy: a.dy, mirror: GOO.dropFaces.mirror[j], r: dr.r };
      }),
    [],
  );

  /* ---------- clocks ---------- */
  const looping = autoPlay && !calm;
  const invEnd = reduced ? REDUCED.inviteEnd : INVITE_END;
  const scanEnd = reduced ? REDUCED.scanEnd : SCAN.end;
  const launchAt = INVITE.release + FOLD.launch;
  const tInv = useSharedValue(0);
  const tScan = useSharedValue(0);
  const tCup = useSharedValue<number>(CUP.end);
  const tCupTap = useSharedValue<number>(CUP.end);
  const loopT = useSharedValue(0);
  const mode = useSharedValue(looping ? AUTO : MANUAL);
  const invHold = useSharedValue(false);
  const scanHold = useSharedValue(false);
  const lastTs = useSharedValue(-1);
  const idleSince = useSharedValue(-1);

  useFrameCallback((info) => {
    const now = info.timestamp;
    const before = lastTs.get();
    lastTs.set(now);
    const dt = before < 0 ? 0 : Math.min((now - before) / 1000, 0.1);

    if (mode.get() === AUTO) {
      if (!looping) {
        mode.set(MANUAL);
        return;
      }
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      tInv.set(Math.min(Math.max(t - START.invite, 0), INVITE_END));
      tScan.set(Math.min(Math.max(t - START.scan, 0), SCAN.end));
      tCup.set(Math.min(Math.max(t - START.cup, 0), CUP.end));
      tCupTap.set(CUP.end);
      return;
    }

    // Manual: each running clock plays to its end; a held finger stops a press before its release.
    if (!looping && tInv.get() <= 0 && tScan.get() <= 0 && tCup.get() >= CUP.end && tCupTap.get() >= CUP.end) return;
    let busy = false;
    const i0 = tInv.get();
    if (i0 > 0 && i0 < invEnd) {
      let i1 = Math.min(i0 + dt, invEnd);
      if (!reduced && invHold.get() && i1 > INVITE.release - 0.001) i1 = Math.max(i0, INVITE.release - 0.001);
      const launch = reduced ? REDUCED.inviteEnd / 2 : launchAt;
      if (i0 < launch && i1 >= launch) scheduleOnRN(hapticLight);
      if (!reduced && i0 < GUST && i1 >= GUST) tCup.set(0.0001);
      tInv.set(i1);
      busy = true;
    }
    const s0 = tScan.get();
    if (s0 > 0 && s0 < scanEnd) {
      let s1 = Math.min(s0 + dt, scanEnd);
      if (!reduced && scanHold.get() && s1 > SCAN.release - 0.001) s1 = Math.max(s0, SCAN.release - 0.001);
      const done = reduced ? REDUCED.scanEnd / 2 : SCAN.fillStart + SCAN.fill;
      if (s0 < done && s1 >= done) scheduleOnRN(hapticSuccess);
      tScan.set(s1);
      busy = true;
    }
    const c0 = tCup.get();
    if (c0 > 0 && c0 < CUP.end) {
      tCup.set(Math.min(c0 + dt, CUP.end));
      busy = true;
    }
    const k0 = tCupTap.get();
    if (k0 < CUP.end) {
      tCupTap.set(Math.min(k0 + dt, CUP.end));
      busy = true;
    }
    if (busy || invHold.get() || scanHold.get() || !looping) {
      idleSince.set(-1);
      return;
    }
    if (idleSince.get() < 0) idleSince.set(now);
    else if (now - idleSince.get() >= RESUME_AFTER) {
      idleSince.set(-1);
      loopT.set(0);
      tInv.set(0);
      tScan.set(0);
      tCup.set(CUP.end);
      tCupTap.set(CUP.end);
      mode.set(AUTO);
    }
  });

  const isRest = (v: number, end: number) => v <= 0 || v >= end;

  const onInviteIn = () => {
    mode.set(MANUAL);
    if (!isRest(tInv.get(), invEnd)) return; // the paper is still out
    tInv.set(0.0001);
    invHold.set(true);
  };
  const onInviteOut = () => invHold.set(false);
  const onCheckIn = () => {
    mode.set(MANUAL);
    if (!isRest(tScan.get(), scanEnd)) return;
    tScan.set(0.0001);
    scanHold.set(true);
  };
  const onCheckOut = () => scanHold.set(false);
  const onCup = () => {
    mode.set(MANUAL);
    if (reduced || tCupTap.get() < CUP.tapRearm) return;
    tCupTap.set(0);
    hapticLight();
  };

  /* ---------- animated styles ---------- */
  const inviteGroup = useAnimatedStyle(() => {
    const tau = tInv.get();
    if (reduced) return { opacity: 1, transform: [{ scale: 1 }] };
    return {
      opacity: buttonShown(tau) ? 1 : 0,
      transform: [{ scale: pressScale(tau, INVITE.pressIn, INVITE.release, INVITE.pressScale) }],
    };
  });
  const inviteShape = useAnimatedStyle(() => ({ borderRadius: reduced ? radius.invite : sheetRadius(tInv.get(), radius.invite) }));
  const inviteLabel = useAnimatedStyle(() => {
    if (reduced) return { opacity: 1, transform: [{ scale: 1 }] };
    const l = labelState(tInv.get());
    return { opacity: l[0], transform: [{ scale: l[1] }] };
  });
  const slotPlane = useAnimatedStyle(() => {
    const tau = tInv.get();
    if (reduced) return { opacity: 1 - (1 - REDUCED.planeDip) * tri(tau / REDUCED.inviteEnd) };
    return { opacity: 1 };
  });

  const cupStyle = useAnimatedStyle(() => {
    if (reduced) return { transform: [{ translateX: 0 }, { rotate: '0deg' }] };
    const a = CUP.amp * kick(tCup.get(), CUP.period, CUP.decay, CUP.fadeFrom, CUP.end) + CUP.tapAmp * kick(tCupTap.get(), CUP.period, CUP.decay, CUP.fadeFrom, CUP.end);
    const sx = CUP.shiftX * kick(tCup.get() - CUP.shiftLag, CUP.period, CUP.decay, CUP.fadeFrom, CUP.end);
    return { transform: [{ translateX: sx }, { rotate: `${a}deg` }] };
  });

  const checkGroup = useAnimatedStyle(() => ({
    transform: [{ scale: reduced ? 1 : pressScale(tScan.get(), SCAN.pressIn, SCAN.release, SCAN.pressScale) }],
  }));

  const fill = useAnimatedStyle(() => {
    const tau = tScan.get();
    if (reduced) return { opacity: SCAN.fillOpacity * tri(tau / REDUCED.scanEnd), transform: [{ translateX: 0 }] };
    const fillEnd = SCAN.fillStart + SCAN.fill;
    const p = seg(tau, SCAN.fillStart, fillEnd);
    const o = tau <= SCAN.fillStart ? 0 : SCAN.fillOpacity * (1 - ease(EASE_OUT, seg(tau, fillEnd, fillEnd + SCAN.fadeDur)));
    return { opacity: o, transform: [{ translateX: -chk.w * (1 - p) }] };
  });

  // The label and the icon scale about the button's centre.
  const labelBox = { position: 'absolute' as const, left: 0, top: 0, width: inv.w, height: inv.h };

  return (
    <View style={styles.root}>
      <LinearGradient colors={pageColors} locations={pageLocs} style={StyleSheet.absoluteFill} />

      {/* search field */}
      <View
        style={[
          styles.pill,
          {
            left: pill.x,
            top: y(pill.y),
            width: pill.w,
            height: pill.h,
            borderRadius: radius.pill,
            shadowOpacity: box.pillShadow.opacity,
            shadowRadius: box.pillShadow.blur,
            shadowOffset: { width: 0, height: box.pillShadow.dy },
          },
        ]}
      />
      {fontsReady ? (
        <Text style={[lineStyle(T.placeholder, y(box.placeholder.baseline)), { left: box.placeholder.x }]}>{copy.placeholder}</Text>
      ) : null}
      <Svg
        width={box.close.size + 8}
        height={box.close.size + 8}
        style={{ position: 'absolute', left: box.close.cx - box.close.size / 2 - 4, top: y(box.close.cy) - box.close.size / 2 - 4 }}
      >
        <Line x1={4} y1={4} x2={4 + box.close.size} y2={4 + box.close.size} stroke={colors.ink} strokeWidth={box.close.stroke} strokeLinecap="round" />
        <Line x1={4 + box.close.size} y1={4} x2={4} y2={4 + box.close.size} stroke={colors.ink} strokeWidth={box.close.stroke} strokeLinecap="round" />
      </Svg>

      {/* card 1 — invite */}
      <View style={[styles.card, { left: space.cardMargin, top: y(box.card1.y), width: cardW, height: box.card1.h, backgroundColor: colors.card1 }]} />
      <Animated.View style={[{ position: 'absolute', left: cupBox.left, top: cupBox.top, width: cupBox.w, height: cupBox.h }, cupStyle]}>
        <Image source={IMG.cup} contentFit="contain" style={{ width: cupBox.w, height: cupBox.h }} />
      </Animated.View>
      {fontsReady ? <Text style={[lineStyle(T.h1, y(box.h1.baseline)), styles.centered]}>{copy.h1}</Text> : null}
      <Animated.View style={[{ position: 'absolute', left: inv.left, top: inv.top, width: inv.w, height: inv.h }, inviteGroup]}>
        <Animated.View style={[StyleSheet.absoluteFill, { overflow: 'hidden' }, inviteShape]}>
          <LinearGradient
            colors={colors.inviteStops.map((s) => s[1]) as [string, string, ...string[]]}
            locations={colors.inviteStops.map((s) => s[0]) as [number, number, ...number[]]}
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>
        <Animated.View style={[labelBox, inviteLabel]}>
          <Animated.View style={[{ position: 'absolute', left: plane.left - inv.left, top: plane.top - inv.top }, slotPlane]}>
            <PlaneGlyph fill={colors.white} fold={colors.planeFold} size={plane.size} />
          </Animated.View>
          {fontsReady ? (
            <Text style={[lineStyle(T.invite, y(box.inviteLabel.baseline) - inv.top), { left: box.inviteLabel.x + dx - inv.left }]}>{copy.invite}</Text>
          ) : null}
        </Animated.View>
      </Animated.View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={copy.invite}
        onPressIn={onInviteIn}
        onPressOut={onInviteOut}
        style={{ position: 'absolute', left: inv.left, top: inv.top, width: inv.w, height: inv.h }}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Coffee cup"
        onPress={onCup}
        style={{ position: 'absolute', left: cupBox.left, top: cupBox.top, width: cupBox.w, height: cupBox.h }}
      />

      {/* card 2 — contacts */}
      <View style={[styles.card, { left: space.cardMargin, top: y(box.card2.y), width: cardW, height: box.card2.h, backgroundColor: colors.card2 }]} />
      {box.avatars.map((a) => (
        <Avatar key={a.src} a={a} scan={tScan} left={a.cx + dx - a.d / 2} top={y(a.cy) - a.d / 2} reduced={reduced} />
      ))}
      {fontsReady ? (
        <>
          <Text style={[lineStyle(T.h2, y(box.h2.baseline)), styles.centered]}>{copy.h2}</Text>
          {copy.body.map((line, i) => (
            <Text key={i} style={[lineStyle(T.body, y(box.bodyBaselines[i])), styles.centered]}>
              {line.map(([text, strong], j) => (
                <Text key={j} style={strong ? { fontFamily: T.bodyStrong.font, letterSpacing: T.bodyStrong.ls } : null}>
                  {text}
                </Text>
              ))}
              {i === 2 ? <Text style={{ letterSpacing: 0 }}>{copy.lock}</Text> : null}
            </Text>
          ))}
        </>
      ) : null}
      <Animated.View style={[{ position: 'absolute', left: chk.left, top: chk.top, width: chk.w, height: chk.h }, checkGroup]}>
        <View style={[StyleSheet.absoluteFill, { borderRadius: radius.check, backgroundColor: colors.buttonBlack, overflow: 'hidden' }]}>
          <Animated.View style={[StyleSheet.absoluteFill, { borderRadius: radius.check, backgroundColor: colors.white }, fill]} />
        </View>
        {fontsReady ? <Text style={[lineStyle(T.check, y(box.checkLabel.baseline) - chk.top), styles.fullWidth]}>{copy.check}</Text> : null}
      </Animated.View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={copy.check}
        onPressIn={onCheckIn}
        onPressOut={onCheckOut}
        style={{ position: 'absolute', left: chk.left, top: chk.top, width: chk.w, height: chk.h }}
      />

      {/* the folded paper in flight and the liquid contacts, above everything */}
      {reduced ? null : (
        <Overlay
          tInv={tInv}
          tScan={tScan}
          reduced={reduced}
          geo={geo}
          pill={radius.invite}
          goo={gooGeo}
          gooColors={gooCols}
          faces={faces}
          drops={dropFaces}
          width={width}
          height={height}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.pageStops[0][1] },
  pill: {
    position: 'absolute',
    backgroundColor: colors.ink,
    shadowColor: '#000000',
  },
  card: { position: 'absolute', borderRadius: radius.card },
  centered: { left: 0, right: 0, textAlign: 'center' },
  fullWidth: { left: 0, right: 0, textAlign: 'center' },
});
