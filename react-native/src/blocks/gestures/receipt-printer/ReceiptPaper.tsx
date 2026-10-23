/** The printed receipt: thermal paper with grain and a curl at its edges, the print, and a torn bottom edge. */
import { Canvas, FractalNoise, LinearGradient, Rect, vec } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Line, Path } from 'react-native-svg';

import { CAP_TOP, COLOR, FONT, RECEIPT, SLOT, mono, type Row } from './theme';

const logo = require('./assets/logo.png');
const W = SLOT.paperX1 - SLOT.paperX0;
const H = RECEIPT.length;
// Receipt rows are measured in screen points; the paper starts at the slot.
const Y = (screenY: number) => screenY - SLOT.paperTop;
const X = (screenX: number) => screenX - SLOT.paperX0;

function RowText({ row }: { row: Row }) {
  const top = Y(row.cap) - row.size * CAP_TOP;
  const style = [styles.text, { fontSize: row.size, top, fontFamily: mono(row.bold), color: row.color ?? COLOR.ink }];
  if (row.align === 'center') return <Text style={[style, styles.center]}>{row.text}</Text>;
  if (row.align === 'right') return <Text style={[style, { right: W - X(row.x ?? RECEIPT.right) }]}>{row.text}</Text>;
  return <Text style={[style, { left: X(row.x ?? RECEIPT.left) }]}>{row.text}</Text>;
}

/** The scalloped tear at the foot of the roll. */
function edgePath() {
  const { period, depth } = RECEIPT.edge;
  const n = Math.round(W / period);
  const p = W / n;
  let d = `M0 0 H${W} V${H - depth}`;
  for (let i = n - 1; i >= 0; i--) {
    const x0 = (i + 1) * p;
    const x1 = i * p;
    d += ` L${x0} ${H - depth} Q${x0 - p / 2} ${H + depth * 0.6} ${x1} ${H - depth}`;
  }
  return `${d} Z`;
}
const EDGE = edgePath();

function Chart() {
  const c = RECEIPT.chart;
  const left = X(RECEIPT.left);
  const right = X(RECEIPT.right);
  const cols = Array.from({ length: c.columns }, (_, i) => left + ((right - left) * i) / (c.columns - 1));
  const faint = Array.from({ length: (c.columns - 1) * c.dotted }, (_, i) => left + ((right - left) * (i + 0.5)) / ((c.columns - 1) * c.dotted));
  const rows = [0.25, 0.5, 0.75].map((f) => Y(c.top) + (Y(c.base) - Y(c.top)) * f);
  return <>
    {faint.map((x) => <Line key={`f${x}`} x1={x} x2={x} y1={Y(c.top)} y2={Y(c.base)} stroke={COLOR.gridFaint} strokeWidth={0.6} strokeDasharray="1.5 2.5" />)}
    {rows.map((y) => <Line key={`r${y}`} x1={left} x2={right} y1={y} y2={y} stroke={COLOR.gridFaint} strokeWidth={0.6} strokeDasharray="1.5 2.5" />)}
    {cols.map((x) => <Line key={`c${x}`} x1={x} x2={x} y1={Y(c.top)} y2={Y(c.bottom)} stroke={COLOR.grid} strokeWidth={0.8} />)}
    <Line x1={left} x2={right} y1={Y(c.base)} y2={Y(c.base)} stroke={COLOR.axis} strokeWidth={c.baseW} />
  </>;
}

export const ReceiptPaper = memo(function ReceiptPaper() {
  const f = RECEIPT.fast;
  return <View style={styles.paper}>
    {/* Paper body with its torn foot, then grain and the curl's shading baked over it. */}
    <Svg width={W} height={H + RECEIPT.edge.depth} style={StyleSheet.absoluteFill}>
      <Path d={EDGE} fill={COLOR.paper} />
      {RECEIPT.dashes.map((y) => <Line key={y} x1={X(RECEIPT.left)} x2={X(RECEIPT.right)} y1={Y(y) + 0.5} y2={Y(y) + 0.5} stroke={COLOR.dash} strokeWidth={1} strokeDasharray="3 2.4" />)}
      <Chart />
      {RECEIPT.zone.ys.map((y) => <Circle key={y} cx={X(RECEIPT.zone.x)} cy={Y(y)} r={RECEIPT.zone.r} fill={COLOR.zone} />)}
    </Svg>
    <Canvas style={styles.grain} pointerEvents="none">
      <Rect x={0} y={0} width={W} height={H} opacity={0.05}>
        <FractalNoise freqX={0.9} freqY={0.9} octaves={2} />
      </Rect>
      <Rect x={0} y={0} width={W} height={H}>
        <LinearGradient start={vec(0, 0)} end={vec(W, 0)} positions={[0, 0.06, 0.94, 1]}
          colors={[COLOR.paperShade, 'transparent', 'transparent', COLOR.paperShade]} />
      </Rect>
    </Canvas>
    <Image source={logo} style={[styles.logo, { top: Y(RECEIPT.logo.top) }]} contentFit="contain" />
    <View style={[styles.tag, { left: X(f.x), top: Y(f.top), width: f.w, height: f.h, borderRadius: f.radius }]}>
      <Text style={[styles.tagText, { fontSize: f.size }]}>FAST</Text>
    </View>
    {RECEIPT.rows.map((r) => <RowText key={`${r.cap}-${r.text}-${r.x ?? ''}`} row={r} />)}
    {RECEIPT.paragraph.lines.map((t, i) => <RowText key={t} row={{
      cap: RECEIPT.paragraph.cap + i * RECEIPT.paragraph.pitch, text: t, align: 'left', x: RECEIPT.left, size: 11.2, color: COLOR.inkSoft,
    }} />)}
  </View>;
});

const styles = StyleSheet.create({
  paper: { width: W, height: H + RECEIPT.edge.depth },
  grain: { position: 'absolute', left: 0, top: 0, width: W, height: H - RECEIPT.edge.depth },
  text: { position: 'absolute', includeFontPadding: false },
  center: { left: 0, right: 0, textAlign: 'center' },
  logo: { position: 'absolute', left: (W - RECEIPT.logo.w) / 2, width: RECEIPT.logo.w, height: RECEIPT.logo.h },
  tag: { position: 'absolute', backgroundColor: COLOR.tag, alignItems: 'center', justifyContent: 'center' },
  tagText: { fontFamily: FONT.bold, color: COLOR.tagInk },
});
