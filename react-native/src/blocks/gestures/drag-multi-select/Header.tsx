/**
 * The top of the screen: back, Select / Cancel, the flight card (in the manner of a flight tracker: the two airports
 * set large, the route between them, the times) and the list's section label. Opaque, so the timeline scrolls under it.
 *
 * The route draws on the card: dotted while nothing is planned, then filled from SFO with the share of the flight the
 * timeline covers (orange into violet, like the segments), and the plane flies it while the episodes download.
 */
import { Canvas, Circle, DashPathEffect, Group, LinearGradient, Path, Skia, vec } from '@shopify/react-native-skia';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing, cubicBezier, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue, withTiming, type SharedValue,
} from 'react-native-reanimated';

import { FLIGHT, formatMinutes } from './episodes';
import { GLYPH } from './glyph-paths';
import { Stroke } from './Glyphs';
import { type Model } from './model';
import { EASE_OUT, PLANE, TIMING } from './motion';
import { coverage, planeTarget, routeAt, routePath } from './route';
import { COLOR, LAYOUT, ROUTE, TYPE, type Layout } from './theme';

const CSS_EASE_OUT = cubicBezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const easeOut = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const PLANE_PATH = Skia.Path.MakeFromSVGString(GLYPH.plane)!;
const ROUTE_PATH = routePath();

type Props = {
  lay: Layout;
  selecting: boolean;
  busy: boolean;
  model: SharedValue<Model>;
  scrollY: SharedValue<number>;
  reduced: boolean;
  onSelect: () => void;
};

export function Header({ lay, selecting, busy, model, scrollY, reduced, onSelect }: Props) {
  const [pressed, setPressed] = useState(false);
  const edge = useAnimatedStyle(() => ({ opacity: Math.min(Math.max(scrollY.get() / 12, 0), 1) }));
  // Select / Cancel dims at once under the finger and eases back on release; it fades out while a download runs.
  const selectLook = {
    opacity: busy ? 0 : pressed ? 0.55 : 1,
    transitionProperty: 'opacity',
    transitionDuration: pressed && !busy ? 0 : TIMING.state,
    transitionTimingFunction: CSS_EASE_OUT,
  } as const;
  const height = lay.listTop - LAYOUT.sectionAfter;
  return (
    <View style={[styles.header, { height }]}>
      <View style={[styles.nav, { marginTop: lay.safeTop }]}>
        <Pressable style={styles.back} hitSlop={8} accessibilityRole="button" accessibilityLabel="Back">
          <Stroke d={GLYPH.back} size={26} color={COLOR.ink} weight={2} />
        </Pressable>
        <Pressable onPress={onSelect} onPressIn={() => setPressed(true)} onPressOut={() => setPressed(false)} hitSlop={8}
          disabled={busy} style={styles.select} accessibilityRole="button" accessibilityLabel={selecting ? 'Cancel selection' : 'Select episodes'}>
          <Animated.View style={selectLook}>
            <Text style={styles.selectText}>{selecting || busy ? 'Cancel' : 'Select'}</Text>
          </Animated.View>
        </Pressable>
      </View>
      <FlightCard lay={lay} model={model} reduced={reduced} />
      <View style={[styles.section, { top: lay.sectionTop }]}>
        <Text style={styles.sectionText} accessibilityRole="header">IN-FLIGHT LISTENING</Text>
        <Text style={styles.sectionMeta}>Second Draft · 7 new</Text>
      </View>
      <Animated.View style={[styles.hair, edge]} />
    </View>
  );
}

function FlightCard({ lay, model, reduced }: { lay: Layout; model: SharedValue<Model>; reduced: boolean }) {
  const cover = useDerivedValue(() => coverage(model.get()));
  // The plane's place on the route. When the loop resets it does not fly home: it fades out at JFK and back in at SFO.
  const plane = useSharedValue(0);
  const planeA = useSharedValue(1);
  useAnimatedReaction(
    () => planeTarget(model.get()),
    (next, prev) => {
      if (prev !== null && next < prev - 0.5) {
        planeA.set(0);
        plane.set(next);
        planeA.set(withTiming(1, { duration: PLANE.back, easing: easeOut }));
      } else plane.set(next);
    },
  );
  const planeT = useDerivedValue(() => {
    const p = routeAt(plane.get());
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: reduced ? 0 : p.a }, { scale: PLANE.scale }];
  });
  const planeO = useDerivedValue(() => planeA.get());
  const label = `United 1514, Thursday 23 October, San Francisco 9:05 AM to New York 5:30 PM, ${formatMinutes(FLIGHT.minutes)}`;
  return (
    <View style={[styles.card, { top: lay.cardTop, width: lay.cardWidth }]} accessible accessibilityLabel={label}>
      <Canvas style={[StyleSheet.absoluteFill, { width: lay.cardWidth, height: LAYOUT.card }]} pointerEvents="none">
        <Path path={ROUTE_PATH} style="stroke" strokeWidth={1.5} strokeCap="round" color={COLOR.dot}>
          <DashPathEffect intervals={[1, 5]} />
        </Path>
        <Path path={ROUTE_PATH} style="stroke" strokeWidth={2.5} strokeCap="round" start={0} end={cover}>
          <LinearGradient start={vec(ROUTE.x0, 0)} end={vec(ROUTE.x1, 0)} colors={[COLOR.orange, COLOR.violet]} />
        </Path>
        <Circle cx={ROUTE.x0} cy={ROUTE.y} r={3.5} color={COLOR.ink} />
        <Circle cx={ROUTE.x1} cy={ROUTE.y} r={3.5} color={COLOR.card} />
        <Circle cx={ROUTE.x1} cy={ROUTE.y} r={3.5} color={COLOR.ink} style="stroke" strokeWidth={1.5} />
        <Group transform={planeT} opacity={planeO}>
          <Circle cx={0} cy={0} r={10 / PLANE.scale} color={COLOR.card} />
          <Group transform={[{ translateX: -12 }, { translateY: -12 }]}>
            <Path path={PLANE_PATH} color={COLOR.ink} />
          </Group>
        </Group>
      </Canvas>
      <Text style={styles.meta}><Text style={styles.metaStrong}>UA 1514</Text>{'  ·  Thu 23 Oct  ·  5h 25m'}</Text>
      <Text style={[styles.code, styles.left]}>SFO</Text>
      <Text style={[styles.code, styles.right]}>JFK</Text>
      <Text style={[styles.city, styles.left]}>San Francisco</Text>
      <Text style={[styles.city, styles.right]}>New York</Text>
      <Text style={[styles.time, styles.left]}>9:05<Text style={styles.ap}> AM</Text></Text>
      <Text style={[styles.time, styles.right]}>5:30<Text style={styles.ap}> PM</Text></Text>
    </View>
  );
}

const PAD = LAYOUT.cardPad;

const styles = StyleSheet.create({
  header: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: COLOR.page },
  nav: { height: LAYOUT.nav, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: LAYOUT.edge - 8 },
  back: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  select: { height: 44, paddingHorizontal: 8, justifyContent: 'center' },
  selectText: { ...TYPE.nav, color: COLOR.violet },
  card: {
    position: 'absolute', left: LAYOUT.edge, height: LAYOUT.card, borderRadius: LAYOUT.cardRadius, borderCurve: 'continuous',
    backgroundColor: COLOR.card, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.line, overflow: 'hidden',
  },
  // 14 · meta 16 · 2 · codes 48 · cities 16 · 14 · times 22 · 16 = 148.
  meta: { ...TYPE.cardMeta, color: COLOR.mute, position: 'absolute', left: PAD, top: 14 },
  metaStrong: { ...TYPE.cardMetaStrong, color: COLOR.ink },
  code: { ...TYPE.code, color: COLOR.ink, position: 'absolute', top: 32 },
  city: { ...TYPE.city, color: COLOR.mute, position: 'absolute', top: 80 },
  time: { ...TYPE.time, color: COLOR.ink, position: 'absolute', top: 110 },
  ap: { ...TYPE.timeAp, color: COLOR.mute },
  left: { left: PAD },
  right: { right: PAD, textAlign: 'right' },
  section: { position: 'absolute', left: LAYOUT.edge, right: LAYOUT.edge, height: LAYOUT.section, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sectionText: { ...TYPE.section, color: COLOR.mute },
  sectionMeta: { ...TYPE.sectionMeta, color: COLOR.mute },
  hair: { position: 'absolute', left: 0, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: COLOR.line },
});
