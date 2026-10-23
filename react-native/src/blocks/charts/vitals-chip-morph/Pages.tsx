/**
 * Vitals Chip Morph — what sits under the hero on each screen. Every line is its own layer so the
 * hand-over between two screens can stagger: the leaving screen's lines slide 24 pt towards the
 * side they leave by and fade, the arriving screen's lines come in from the other side, 30 ms apart.
 * Positions are ref px (× k); type sizes are pt (× s).
 */

import type { ReactNode } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, FeGaussianBlur, Filter, G, Line as SvgLine, Path, Rect, Text as SvgText } from 'react-native-svg';

import {
  BedIcon,
  CheckCircle,
  Chevron,
  DotsIcon,
  MiniChartIcon,
  TargetIcon,
} from './icons';
import { easeInOut, easeOut, seg } from './motion';
import { colors, layout, space, type TextSpec, type as typeScale } from './theme';

/** SF Pro: the cap top sits 0.247 em under the top of a Text with no lineHeight. */
const CAP_OFFSET = 0.247;
const ROUNDED = Platform.OS === 'ios' ? 'ui-rounded' : undefined;

export type Blend = {
  lo: SharedValue<number>;
  hi: SharedValue<number>;
  u: SharedValue<number>;
  quiet: SharedValue<boolean>;
};

type Geometry = { k: number; s: number };

/** One line of a screen: its own fade and slide, i = its place in the stagger. */
export function PageLayer({ screen, i, blend, g, children }: { screen: number; i: number; blend: Blend; g: Geometry; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const lo = blend.lo.get();
    const hi = blend.hi.get();
    const u = blend.u.get();
    const quiet = blend.quiet.get();
    let opacity = 0;
    let x = 0;
    if (lo === hi) {
      opacity = lo === screen ? 1 : 0;
    } else if (screen === hi) {
      const a = quiet ? seg(u, 0.5, 1) : easeOut(seg(u, 0.38 + 0.03 * i, 0.86 + 0.03 * i));
      opacity = a;
      x = quiet ? 0 : space.pageSlide * (1 - a);
    } else if (screen === lo) {
      const d = quiet ? seg(u, 0, 0.5) : easeInOut(seg(u, 0.02 * i, 0.4 + 0.02 * i));
      opacity = 1 - d;
      x = quiet ? 0 : -space.pageSlide * d;
    }
    return { opacity, transform: [{ translateX: x * g.k }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, style]}>
      {children}
    </Animated.View>
  );
}

/** A metric label followed by the pin's light chevron, placed by the label's cap top (ref px). */
function MetricLabel({ x, capTop, g, color, children }: { x: number; capTop: number; g: Geometry; color?: string; children: string }) {
  const spec = typeScale.metricLink;
  const size = spec.size * g.s;
  return (
    <View style={{ position: 'absolute', left: x * g.k, top: capTop * g.k - CAP_OFFSET * size, flexDirection: 'row', alignItems: 'center' }}>
      <Text allowFontScaling={false} numberOfLines={1} style={{ fontSize: size, fontWeight: spec.weight, color: color ?? spec.color }}>
        {children}
      </Text>
      <View style={{ marginLeft: 2 * g.s }}>
        <Chevron s={g.s} />
      </View>
    </View>
  );
}

/** A single line of text placed by its cap top (ref px). */
export function Line({
  x,
  capTop,
  spec,
  g,
  children,
  color,
  width,
  align = 'left',
}: {
  x: number;
  capTop: number;
  spec: TextSpec;
  g: Geometry;
  children: ReactNode;
  color?: string;
  width?: number;
  align?: 'left' | 'center';
}) {
  const size = spec.size * g.s;
  const rounded = 'rounded' in spec && spec.rounded;
  return (
    <Text
      allowFontScaling={false}
      numberOfLines={1}
      style={{
        position: 'absolute',
        left: x * g.k,
        top: capTop * g.k - CAP_OFFSET * size,
        width: width === undefined ? undefined : width * g.k,
        fontSize: size,
        fontWeight: spec.weight,
        color: color ?? spec.color,
        letterSpacing: spec.ls * g.s,
        textAlign: align,
        fontFamily: rounded ? ROUNDED : undefined,
        fontVariant: ['tabular-nums'],
      }}
    >
      {children}
    </Text>
  );
}

const L = layout;
const X = space.screenInset;

// ─── Readiness (screen 0) ─────────────────────────────────────────────────────────────────────

const READY_BODY = [
  'You had a good night’s sleep last',
  'night and all your other metrics are',
  'typical. It looks like you’re rested',
  'and ready for today.',
];

function CheckRow({ g }: { g: Geometry }) {
  const c = L.checks;
  const icons = [<TargetIcon key="t" s={g.s} />, <DotsIcon key="d" s={g.s} />, <BedIcon key="b" s={g.s} />];
  return (
    <>
      {c.groups.map((group, index) => (
        <View key={group.icon}>
          <View style={[styles.centred, { left: group.icon * g.k, top: c.cy * g.k }]}>{icons[index]}</View>
          <View style={[styles.centred, { left: group.check * g.k, top: c.cy * g.k }]}>
            <CheckCircle size={(c.checkD / 3) * g.s} />
          </View>
        </View>
      ))}
      <View style={{ position: 'absolute', left: c.dividers[0] * g.k, top: (c.cy - c.dividerH / 2) * g.k, width: StyleSheet.hairlineWidth * 2, height: c.dividerH * g.k, backgroundColor: colors.hairline }} />
      <View style={{ position: 'absolute', left: c.dividers[1] * g.k, top: (c.cy - c.dividerH / 2) * g.k, height: c.dividerH * g.k, width: 2 }}>
        <Svg width={2} height={c.dividerH * g.k}>
          <SvgLine x1={1} y1={0} x2={1} y2={c.dividerH * g.k} stroke={colors.hairline} strokeWidth={1.4} strokeDasharray="2 2.6" />
        </Svg>
      </View>
    </>
  );
}

function LinkRow({ x, capTop, label, g }: { x: number; capTop: number; label: string; g: Geometry }) {
  const size = typeScale.link.size * g.s;
  const check = 15 * g.s;
  return (
    <>
      <View style={{ position: 'absolute', left: x * g.k, top: capTop * g.k + 0.35 * size - check / 2 }}>
        <CheckCircle size={check} />
      </View>
      <Line x={x + space.linkLabel} capTop={capTop} spec={typeScale.link} g={g}>
        {label}
      </Line>
    </>
  );
}

function MiniChart({ g }: { g: Geometry }) {
  const m = L.miniChart;
  const kinds = ['heart', 'lungs', 'temp', 'oxygen', 'wave'] as const;
  const w = m.w * g.k;
  return (
    <View style={{ position: 'absolute', left: m.x * g.k, top: m.lineTop * g.k, width: w, height: (m.iconsCy + 30 - m.lineTop) * g.k }}>
      <Svg width={w} height={(m.lineBottom - m.lineTop + 12) * g.k}>
        <Rect x={0} y={0} width={w} height={9 * g.k} rx={4.5 * g.k} fill={colors.bandLine} />
        <Rect x={0} y={(m.bandTop - m.lineTop) * g.k} width={w} height={m.bandH * g.k} fill={colors.band} />
        <Rect x={0} y={(m.lineBottom - m.lineTop) * g.k} width={w} height={9 * g.k} rx={4.5 * g.k} fill={colors.bandLine} />
        {m.dots.map(([dx, dy]) => (
          <Circle key={dx} cx={(dx - m.x) * g.k} cy={(dy - m.lineTop) * g.k} r={(m.dotD / 2) * g.k} fill="#FFFFFF" stroke={colors.link} strokeWidth={6 * g.k} />
        ))}
      </Svg>
      {m.dots.map(([dx], index) => (
        <View key={dx} style={[styles.centred, { left: (dx - m.x) * g.k, top: (m.iconsCy - m.lineTop) * g.k }]}>
          <MiniChartIcon kind={kinds[index]} s={g.s} />
        </View>
      ))}
    </View>
  );
}

function SleepScoreRing({ g }: { g: Geometry }) {
  const r = L.sleepRing;
  const size = (r.r * 2 + r.stroke + 24) * g.k;
  const c = size / 2;
  const rr = r.r * g.k;
  const arc = (from: number, to: number) => {
    const a0 = ((from - 90) * Math.PI) / 180;
    const a1 = ((to - 90) * Math.PI) / 180;
    return `M ${c + rr * Math.cos(a0)} ${c + rr * Math.sin(a0)} A ${rr} ${rr} 0 ${to - from > 180 ? 1 : 0} 1 ${c + rr * Math.cos(a1)} ${c + rr * Math.sin(a1)}`;
  };
  return (
    <View style={{ position: 'absolute', left: (r.cx - r.r - r.stroke / 2 - 12) * g.k, top: (r.cy - r.r - r.stroke / 2 - 12) * g.k, width: size, height: size, opacity: 0.8 }}>
      <Svg width={size} height={size}>
        <Filter id="vcmSoftRing" x="-20%" y="-20%" width="140%" height="140%">
          <FeGaussianBlur stdDeviation={(L.underGlassBlur / 2) * g.k} />
        </Filter>
        <G filter="url(#vcmSoftRing)">
          <PathArc d={arc(268, 358)} color={colors.sleepTeal} w={r.stroke * g.k} />
          <PathArc d={arc(4, 58)} color={colors.sleepCoral} w={r.stroke * g.k} />
          <PathArc d={arc(64, 262)} color={colors.sleepBlue} w={r.stroke * g.k} />
          <SvgText x={c} y={c + 7.5 * g.s} fontSize={21 * g.s} fontWeight="700" fill={colors.muted} textAnchor="middle">84</SvgText>
        </G>
      </Svg>
    </View>
  );
}

/** A value that has slid under the tab bar's glass: drawn soft, like the pin (an SVG Gaussian blur). */
function SoftText({ x, capTop, g, children }: { x: number; capTop: number; g: Geometry; children: string }) {
  const size = typeScale.value.size * g.s;
  const pad = 12 * g.k;
  const w = 300 * g.k;
  const h = size * 1.4;
  return (
    <Svg style={{ position: 'absolute', left: x * g.k - pad, top: capTop * g.k - CAP_OFFSET * size - pad }} width={w + 2 * pad} height={h + 2 * pad}>
      <Filter id="vcmSoftText" x="-20%" y="-40%" width="140%" height="180%">
        <FeGaussianBlur stdDeviation={(L.underGlassBlur / 2) * g.k} />
      </Filter>
      <SvgText x={pad} y={pad + 0.953 * size} fontSize={size} fontWeight={typeScale.value.weight} fill={colors.ink} fillOpacity={0.72} filter="url(#vcmSoftText)">
        {children}
      </SvgText>
    </Svg>
  );
}

function PathArc({ d, color, w }: { d: string; color: string; w: number }) {
  return <Path d={d} stroke={color} strokeWidth={w} fill="none" />;
}

export function ReadinessPage({ blend, g }: { blend: Blend; g: Geometry }) {
  return (
    <>
      <PageLayer screen={0} i={0} blend={blend} g={g}>
        <CheckRow g={g} />
      </PageLayer>
      <PageLayer screen={0} i={1} blend={blend} g={g}>
        <Line x={X} capTop={L.pageHeadingCapTop} spec={typeScale.heading} g={g}>Ready</Line>
      </PageLayer>
      {READY_BODY.map((text, index) => (
        <PageLayer key={text} screen={0} i={2 + index} blend={blend} g={g}>
          <Line x={X} capTop={L.readyBodyTop + index * space.bodyLine} spec={typeScale.body} g={g}>{text}</Line>
        </PageLayer>
      ))}
      <PageLayer screen={0} i={6} blend={blend} g={g}>
        <Line x={X} capTop={L.raisingCapTop} spec={typeScale.section} g={g}>Raising Your Score</Line>
      </PageLayer>
      <PageLayer screen={0} i={7} blend={blend} g={g}>
        <LinkRow x={X + 3} capTop={L.vitalsLinkCapTop} label="Vitals, Overnight" g={g} />
        <View style={{ position: 'absolute', left: L.vitalsChevronX * g.k, top: L.vitalsLinkCapTop * g.k - 1 * g.s }}>
          <Chevron s={g.s} />
        </View>
        <Line x={X} capTop={L.typicalCapTop} spec={typeScale.value} g={g}>Typical</Line>
        <MiniChart g={g} />
      </PageLayer>
      <PageLayer screen={0} i={8} blend={blend} g={g}>
        <LinkRow x={X + 3} capTop={L.sleepLinkCapTop} label="Sleep Score" g={g} />
        <View style={{ position: 'absolute', left: L.sleepChevronX * g.k, top: L.sleepLinkCapTop * g.k - 1 * g.s }}>
          <Chevron s={g.s} />
        </View>
        <SoftText x={X} capTop={L.highCapTop} g={g}>High</SoftText>
        <SleepScoreRing g={g} />
      </PageLayer>
    </>
  );
}

// ─── Fitness (screen 1) ───────────────────────────────────────────────────────────────────────

const FIT_HEADING = ['Your Exercise Ring', 'Looks Good'];
const FIT_BODY = ['Your Exercise ring is ahead of', 'where it usually is by now.'];
/** Hourly bars of the Move and Exercise rows (heights in ref px), 9 AM onwards. */
const MOVE_BARS = [22, 36, 70, 44, 58, 30, 24, 22, 20];
const EXERCISE_BARS = [14, 26, 66, 60, 18, 22, 30, 16, 14];

function FitnessChart({ g }: { g: Geometry }) {
  const c = L.fitChart;
  const w = c.w * g.k;
  const h = (c.labelsTop + 50 - c.rowsCy[0] + 60) * g.k;
  const top = c.rowsCy[0] - 60;
  const bar = (x: number, bottom: number, height: number, color: string, key: string) => (
    <Rect key={key} x={x * g.k} y={(bottom - height - top) * g.k} width={c.barW * g.k} height={height * g.k} rx={(c.barW / 2) * g.k} fill={color} />
  );
  const labels = ['12 AM', '6 AM', '12 PM', '6 PM'];
  return (
    <View style={{ position: 'absolute', left: c.x * g.k, top: top * g.k, width: w, height: h }}>
      <Svg width={w} height={h}>
        {[c.rowsCy[0] + c.base, c.rowsCy[1] + c.base].map((y) => (
          <Rect key={y} x={0} y={(y - top) * g.k} width={w * c.gridW} height={g.k * 1.5} fill={colors.hairline} opacity={0.6} />
        ))}
        {MOVE_BARS.map((height, i) => bar(c.barX + i * c.barPitch, c.rowsCy[0] + c.base, height, colors.movePink, `m${i}`))}
        {EXERCISE_BARS.map((height, i) => bar(c.barX + i * c.barPitch, c.rowsCy[1] + c.base, height, colors.exerciseText, `e${i}`))}
        {[0, 1, 2, 3, 4].map((i) => (
          <Circle key={`s${i}`} cx={(c.dotX + i * c.dotPitch) * g.k} cy={(c.rowsCy[2] - top) * g.k} r={c.dotR * g.k} fill={colors.standCyan} opacity={i === 4 ? 0.3 : 1} />
        ))}
      </Svg>
      {labels.map((label, i) => (
        <Text key={label} allowFontScaling={false} style={{ position: 'absolute', left: i * c.labelPitch * g.k, top: (c.labelsTop - top) * g.k, fontSize: typeScale.axis.size * g.s, color: colors.faint }}>
          {label}
        </Text>
      ))}
    </View>
  );
}

function Metric({ x, title, value, sub, g, titleColor }: { x: number; title: string; value: ReactNode; sub: string; g: Geometry; titleColor: string }) {
  const top = L.fitMetricsCapTop;
  return (
    <>
      <MetricLabel x={x} capTop={top} g={g} color={titleColor}>{title}</MetricLabel>
      <Line x={x} capTop={top + space.metricValue} spec={typeScale.metricValue} g={g}>{value}</Line>
      <Line x={x} capTop={top + space.metricSub} spec={typeScale.metricSub} g={g}>{sub}</Line>
    </>
  );
}

export function FitnessPage({ blend, g }: { blend: Blend; g: Geometry }) {
  const statColours = [colors.movePink, colors.exerciseText, colors.standCyan];
  const stats = ['375/500 CAL', '19/30 MIN', '4/12 HRS'];
  return (
    <>
      {FIT_HEADING.map((text, index) => (
        <PageLayer key={text} screen={1} i={1 + index} blend={blend} g={g}>
          <Line x={X} capTop={L.pageHeadingCapTop + index * space.headingLine} spec={typeScale.heading} g={g}>{text}</Line>
        </PageLayer>
      ))}
      {FIT_BODY.map((text, index) => (
        <PageLayer key={text} screen={1} i={3 + index} blend={blend} g={g}>
          <Line x={X} capTop={L.fitBodyTop + index * space.bodyLine} spec={typeScale.body} g={g}>{text}</Line>
        </PageLayer>
      ))}
      <PageLayer screen={1} i={5} blend={blend} g={g}>
        <FitnessChart g={g} />
        {stats.map((text, index) => (
          <Line key={text} x={L.fitStats.x} capTop={L.fitStats.capTops[index]} spec={typeScale.stat} g={g} color={statColours[index]}>{text}</Line>
        ))}
      </PageLayer>
      <PageLayer screen={1} i={6} blend={blend} g={g}>
        <Metric x={X} title="Steps" value="5,253" sub="Avg: 5,053" g={g} titleColor={colors.heartDark} />
        <Metric x={L.fitStats.x} title="Cardio Fitness" value="38 VO₂ max" sub="High" g={g} titleColor={colors.heartDark} />
      </PageLayer>
    </>
  );
}

// ─── Heart (screen 2) — values copied from pulse-heart-card ───────────────────────────────────

const HEART_HEADING = ['Cardio Fitness', 'Trending Up'];
const HEART_BODY = ['On average, your cardio fitness', 'went up over the last month. This is', 'great for your long-term health.'];
/** pulse-heart-card's measured bars, in screen pt (x, width, y0, y1). */
const BAR_BOUNDS = [
  [34, 678, 697], [41.25, 670, 688], [48.5, 670, 694], [55.75, 665, 695], [63, 670, 697], [70.25, 677, 698],
  [77.5, 685, 699], [84.75, 676, 699], [92, 687, 699], [99.25, 686, 703], [106.5, 687, 703], [113.75, 696, 703],
  [121, 696, 703], [128.25, 660, 698], [135.5, 670, 694], [142.75, 681, 695], [150, 680, 701], [157.25, 687, 700],
  [164.5, 680, 700], [171.75, 681, 703],
] as const;

function HeartChart({ g }: { g: Geometry }) {
  const top = 650;
  const s = g.s;
  const w = 338 * s;
  const last = BAR_BOUNDS[BAR_BOUNDS.length - 1];
  return (
    <View style={{ position: 'absolute', left: 32 * s, top: top * s, width: w, height: 80 * s }}>
      <Svg width={w} height={80 * s}>
        {BAR_BOUNDS.map(([x, y0, y1]) => (
          <Rect key={x} x={(x - 32) * s} y={(y0 - top) * s} width={3.8 * s} height={(y1 - y0) * s} rx={1.9 * s} fill={colors.chartPink} fillOpacity={0.82} />
        ))}
        {Array.from({ length: 33 }, (_, i) => (
          <Circle key={i} cx={(2.7 + i * 10.35) * s} cy={(711 - top) * s} r={0.7 * s} fill={colors.faint} fillOpacity={0.55} />
        ))}
        <Rect x={2 * s} y={(707 - top) * s} width={s} height={7 * s} fill={colors.faint} opacity={0.7} />
        <Rect x={334 * s} y={(707 - top) * s} width={s} height={7 * s} fill={colors.faint} opacity={0.7} />
        <Circle cx={(last[0] - 32 + 1.9) * s} cy={(last[1] - 3 - top) * s} r={3.3 * s} fill="#FFFFFF" stroke={colors.heart} strokeWidth={2.4 * s} />
      </Svg>
    </View>
  );
}

export function HeartPage({ blend, g }: { blend: Blend; g: Geometry }) {
  return (
    <>
      {HEART_HEADING.map((text, index) => (
        <PageLayer key={text} screen={2} i={1 + index} blend={blend} g={g}>
          <Line x={X} capTop={L.pageHeadingCapTop + index * space.headingLine} spec={typeScale.heading} g={g}>{text}</Line>
        </PageLayer>
      ))}
      {HEART_BODY.map((text, index) => (
        <PageLayer key={text} screen={2} i={3 + index} blend={blend} g={g}>
          <Line x={X} capTop={L.heartBodyTop + index * space.bodyLine} spec={typeScale.body} g={g}>{text}</Line>
        </PageLayer>
      ))}
      <PageLayer screen={2} i={6} blend={blend} g={g}>
        <Line x={X} capTop={L.heartChart.titleTop} spec={typeScale.chartTitle} g={g}>43–120 BPM</Line>
        <HeartChart g={g} />
        <Line x={X} capTop={L.heartChart.axisCapTop} spec={typeScale.axis} g={g}>12AM</Line>
        <Line x={L.heartChart.axisRightX} capTop={L.heartChart.axisCapTop} spec={typeScale.axis} g={g}>12AM</Line>
      </PageLayer>
      <PageLayer screen={2} i={7} blend={blend} g={g}>
        <MetricLabel x={X} capTop={L.heartMetricsTop} g={g}>Resting Heart Rate</MetricLabel>
        <Line x={X} capTop={L.heartMetricsTop + space.metricValue} spec={typeScale.metricValue} g={g} color={colors.supportValue}>55 BPM</Line>
        <MetricLabel x={L.heartRightColumn} capTop={L.heartMetricsTop} g={g}>Recovery HRV</MetricLabel>
        <Line x={L.heartRightColumn} capTop={L.heartMetricsTop + space.metricValue} spec={typeScale.metricValue} g={g} color={colors.supportValue}>54 MS</Line>
      </PageLayer>
    </>
  );
}

// ─── hero numbers ─────────────────────────────────────────────────────────────────────────────

export { ROUNDED, CAP_OFFSET };

const styles = StyleSheet.create({
  centred: { position: 'absolute', width: 0, height: 0, alignItems: 'center', justifyContent: 'center', overflow: 'visible' },
});
