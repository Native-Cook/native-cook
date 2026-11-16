import { Circle, Group, Path, Skia, Text, useFont, type SkFont, type DataSourceParam } from '@shopify/react-native-skia';
import { Easing, useAnimatedReaction, useDerivedValue, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { comp, sys } from './tgt-theme';
import { clamp } from './tgt-motion';
import { type RoutePoint, type RunSample } from './tgt-data';

import { metricText, TEXT_ITEMS, textItemsAt } from './tgt-scene';
function measuredWidth(font: SkFont, text: string) {
  'worklet';
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
}
/** The big figures roll like an odometer: each digit column slides up (or down) into the next while cross-fading, so a
 * changing value never jumps and no glyph is ever cut by a mask. Columns are fixed (tabular), aligned on the decimal point;
 * a leading digit fades in or out while the figure slides half a column to stay left-aligned. */
const ROLL = {
  distance: { decimals: 2, ints: 2 },
  contact: { decimals: 0, ints: 3 },
  oscillation: { decimals: 1, ints: 2 },
} as const;
type RollId = keyof typeof ROLL;
function rollValue(id: RollId, sample: RunSample, units: 'km' | 'mi') {
  'worklet';
  if (id === 'distance') return sample.distance * (units === 'mi' ? 0.621371 : 1);
  if (id === 'contact') return sample.contact;
  return sample.oscillation;
}
const DIGITS = '0123456789';
function RollDigit({
  place,
  lead,
  shown,
  font,
  x,
  y,
  slotW,
  widths,
  travel,
  ink,
}: {
  place: number;
  lead: boolean;
  shown: SharedValue<number>;
  font: SkFont;
  x: number;
  y: number;
  slotW: number;
  widths: number[];
  travel: number;
  ink: string | SharedValue<string>;
}) {
  const unit = Math.pow(10, place);
  // r: how far this column has rolled toward the next digit (0..1); only moves while every lower column passes 9.
  const roll = useDerivedValue(() => {
    const u = Math.max(0, shown.get());
    const base = Math.floor(u / unit);
    const r = Math.min(1, Math.max(0, (u % unit) - (unit - 1)));
    return [base, r];
  });
  const a = useDerivedValue(() => {
    const [base] = roll.get();
    return lead && base === 0 ? '' : DIGITS[base % 10];
  });
  const b = useDerivedValue(() => {
    const [base] = roll.get();
    return DIGITS[(base + 1) % 10];
  });
  const ax = useDerivedValue(() => x + (slotW - (a.get() ? widths[DIGITS.indexOf(a.get())] : 0)) / 2);
  const bx = useDerivedValue(() => x + (slotW - widths[DIGITS.indexOf(b.get())]) / 2);
  const ay = useDerivedValue(() => y - roll.get()[1] * travel);
  const by = useDerivedValue(() => y + (1 - roll.get()[1]) * travel);
  // Sequential, not a cross-fade: the old digit has left (and moved most of a line) before the new one shows, so a column
  // never holds two legible glyphs at once.
  const aOpacity = useDerivedValue(() => {
    const t = Math.min(1, roll.get()[1] / 0.45);
    return 1 - t * t * (3 - 2 * t);
  });
  const bOpacity = useDerivedValue(() => {
    const t = Math.max(0, (roll.get()[1] - 0.55) / 0.45);
    return t * t * (3 - 2 * t);
  });
  return (
    <>
      <Text x={ax} y={ay} text={a} font={font} color={ink} opacity={aOpacity} />
      <Text x={bx} y={by} text={b} font={font} color={ink} opacity={bOpacity} />
    </>
  );
}
function RollingMetric({
  item,
  id,
  sample,
  units,
  fontScale,
  ink,
  fontSource,
  reduced,
}: {
  item: (typeof TEXT_ITEMS)[number];
  id: RollId;
  sample: SharedValue<RunSample>;
  units: 'km' | 'mi';
  fontScale: number;
  ink: string | SharedValue<string>;
  fontSource?: DataSourceParam;
  reduced: boolean;
}) {
  const { decimals, ints } = ROLL[id];
  const size = item.size * Math.min(fontScale, 1.35);
  const font = useFont(fontSource ?? require('./assets/fonts/Urbanist-Light.ttf'), size);
  const scale = Math.pow(10, decimals);
  const target = useDerivedValue(() => Math.round(rollValue(id, sample.get(), units) * scale));
  const shown = useSharedValue(-1);
  useAnimatedReaction(
    () => target.get(),
    (q) => {
      if (reduced || shown.get() < 0) shown.set(q);
      else shown.set(withTiming(q, { duration: 180, easing: Easing.out(Easing.cubic) }));
    },
    [reduced],
  );
  const widths = font ? font.getGlyphWidths(font.getGlyphIDs(DIGITS)) : Array<number>(10).fill(size * 0.55);
  const slotW = Math.max(...widths);
  const dotW = font ? font.getGlyphWidths(font.getGlyphIDs('.'))[0] : size * 0.25;
  const totalW = ints * slotW + (decimals ? dotW + decimals * slotW : 0);
  const fit = Math.min(1, item.width / Math.max(1, totalW));
  // Leading column visibility drives the slide that keeps the figure left-aligned when it gains or loses a digit.
  const leadUnit = Math.pow(10, decimals + ints - 1);
  const slide = useDerivedValue(() => {
    const u = Math.max(0, shown.get());
    const vis = Math.min(1, Math.max(0, u - (leadUnit - 1)));
    return [{ translateX: -(1 - vis) * slotW * fit }];
  });
  const transform = [{ translateX: item.x }, { translateY: item.y }, { scaleX: fit }, { translateX: -item.x }, { translateY: -item.y }];
  if (!font) return null;
  const travel = size * 0.55;
  const slots: { place: number; x: number; lead: boolean }[] = [];
  let cx = item.x;
  for (let i = 0; i < ints; i++) {
    slots.push({ place: decimals + ints - 1 - i, x: cx, lead: i < ints - 1 });
    cx += slotW;
  }
  const dotX = cx;
  cx += dotW;
  for (let i = 0; i < decimals; i++) {
    slots.push({ place: decimals - 1 - i, x: cx, lead: false });
    cx += slotW;
  }
  return (
    <Group transform={slide}>
      <Group transform={transform}>
        {slots.map((sl, i) => (
          <RollDigit
            key={i}
            place={sl.place}
            lead={sl.lead}
            shown={shown}
            font={font}
            x={sl.x}
            y={item.y}
            slotW={slotW}
            widths={widths}
            travel={travel}
            ink={ink}
          />
        ))}
        {decimals ? <Text x={dotX} y={item.y} text="." font={font} color={ink} /> : null}
      </Group>
    </Group>
  );
}
function MetricText({
  item,
  sample,
  units,
  fontScale,
  ink,
  fontSource,
  bpmScale,
}: {
  item: (typeof TEXT_ITEMS)[number];
  sample: SharedValue<RunSample>;
  units: 'km' | 'mi';
  fontScale: number;
  ink: string | SharedValue<string>;
  fontSource?: DataSourceParam;
  bpmScale: SharedValue<number>;
}) {
  // Chart labels are fitted to their measured cells; full-size values remain in the accessibility tree.
  const size = item.size * Math.min(fontScale, item.light ? 1.35 : 1.6);
  const font = useFont(
    fontSource ?? (item.light ? require('./assets/fonts/Urbanist-Light.ttf') : require('./assets/fonts/Urbanist-Regular.ttf')),
    size,
  );
  const text = useDerivedValue(() => metricText(item.id, sample.get(), units));
  const position = useDerivedValue(() => {
    if (!font) return item.x;
    const w = measuredWidth(font, text.get());
    return item.align === 'right' ? item.x - w : item.align === 'center' ? item.x - w / 2 : item.x;
  });
  const fit = useDerivedValue(() => (!font ? 1 : Math.min(1, item.width / Math.max(1, measuredWidth(font, text.get())))));
  const transform = useDerivedValue(() => [
    { translateX: item.x },
    { translateY: item.y },
    { scaleX: fit.get() * (item.id === 'bpm' ? bpmScale.get() : 1) },
    { scaleY: item.id === 'bpm' ? bpmScale.get() : 1 },
    { translateX: -item.x },
    { translateY: -item.y },
  ]);
  if (!font) return null;
  return (
    <Group transform={transform}>
      <Text x={position} y={item.y} text={text} font={font} color={ink} />
    </Group>
  );
}
export function TileContent({
  sample,
  units,
  points,
  progress,
  ring,
  fontScale = 1,
  ink = sys.ink.primary,
  marker = sys.accent.base,
  fontSource,
  bpmScale,
  reduced = false,
}: {
  sample: SharedValue<RunSample>;
  units: 'km' | 'mi';
  points: RoutePoint[];
  progress: SharedValue<number>;
  ring: SharedValue<number>;
  fontScale?: number;
  ink?: string | SharedValue<string>;
  marker?: string;
  fontSource?: DataSourceParam;
  bpmScale: SharedValue<number>;
  reduced?: boolean;
}) {
  const builder = Skia.PathBuilder.Make();
  points.forEach((p, i) => (i === 0 ? builder.moveTo(p.x, p.y) : builder.lineTo(p.x, p.y)));
  const path = builder.detach();
  const markerX = useDerivedValue(() => {
    const i = Math.round(clamp(progress.get()) * (points.length - 1));
    return points[i]?.x ?? 214;
  });
  const markerY = useDerivedValue(() => {
    const i = Math.round(clamp(progress.get()) * (points.length - 1));
    return points[i]?.y ?? 245;
  });
  const trianglePath = Skia.PathBuilder.Make().moveTo(-3, -4).lineTo(3, -4).lineTo(0, 4).close().detach();
  const triangle = useDerivedValue(() => {
    const angle = ((-150 + 120 * clamp((sample.get().oscillation - 8) / 5)) * Math.PI) / 180;
    const x = comp.gauge.verticalX + comp.gauge.verticalR * Math.cos(angle),
      y = comp.gauge.verticalY + comp.gauge.verticalR * Math.sin(angle);
    return [{ translateX: x }, { translateY: y }];
  });
  const arcBuilder = Skia.PathBuilder.Make();
  for (let i = 0; i <= 40; i++) {
    const a = ((-150 + i * 3) * Math.PI) / 180;
    const x = 280 + 54 * Math.cos(a),
      y = 625 + 54 * Math.sin(a);
    if (i === 0) arcBuilder.moveTo(x, y);
    else arcBuilder.lineTo(x, y);
  }
  const arc = arcBuilder.detach();
  return (
    <>
      <Path path={path} color={sys.stroke.hairline} style="stroke" strokeWidth={comp.route.stroke} opacity={0.8} />
      <Circle cx={markerX} cy={markerY} r={ring} color={marker} style="stroke" strokeWidth={0.8} opacity={0.8} />
      <Circle cx={markerX} cy={markerY} r={3} color={marker} />
      <Path path={arc} color={ink} style="stroke" strokeWidth={0.8} opacity={0.4} />
      <Group transform={triangle}>
        <Path path={trianglePath} color={marker} />
      </Group>
      {Array.from({ length: 7 }, (_, i) => (
        <ContactDot key={i} i={i} sample={sample} ink={ink} />
      ))}
      {textItemsAt(fontScale).map((item) =>
        item.id === 'distance' || item.id === 'contact' || item.id === 'oscillation' ? (
          <RollingMetric
            key={item.id}
            item={item}
            id={item.id}
            sample={sample}
            units={units}
            fontScale={fontScale}
            ink={ink}
            fontSource={fontSource}
            reduced={reduced}
          />
        ) : (
          <MetricText
            key={item.id}
            item={item}
            sample={sample}
            units={units}
            fontScale={fontScale}
            ink={ink}
            fontSource={fontSource}
            bpmScale={bpmScale}
          />
        ),
      )}
    </>
  );
}
function ContactDot({ i, sample, ink }: { i: number; sample: SharedValue<RunSample>; ink: string | SharedValue<string> }) {
  const opacity = useDerivedValue(() => (i <= Math.round(clamp((sample.get().contact - 245) / 50) * 6) ? 1 : 0.3));
  return (
    <Circle cx={74 + i * 16} cy={comp.gauge.contactY - 16 * Math.sin((i * Math.PI) / 6)} r={comp.gauge.dot} color={ink} opacity={opacity} />
  );
}
