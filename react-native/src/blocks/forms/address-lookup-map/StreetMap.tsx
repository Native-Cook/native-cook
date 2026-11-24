import {
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  Text,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import { memo, useMemo } from 'react';
import { Platform } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { CRESCENT, DISTRICTS, MAIN_ROAD, PARKS, RIVER, STREETS, houses } from './addressLookupData';

/*
 StreetMap — a town drawn in code with Skia: river, parks, house footprints, streets with casings,
 one main road and labels. Nothing comes from a map service. A camera (centre + scale, in the
 town's world units) looks at it; the pin is drawn in screen space at the middle of the map, so a
 drag moves the town under it, the way delivery apps let you fine-tune a drop-off point.
*/

export type Camera = { x: number; y: number; s: number };
export type PinPose = {
  /** Height above the ground, pt (negative = up). */
  y: number;
  /** Touch-down squash 0 ..1. */
  squash: number;
  alpha: number;
  /** 0 ..1 while the map is held: the pin floats off the ground. */
  lift: number;
  ring1: number;
  ring2: number;
};

type Props = {
  width: number;
  height: number;
  camera: SharedValue<Camera>;
  pin: SharedValue<PinPose>;
  /** Rotation of the town on screen, radians. */
  rotation: number;
  accent?: string;
};

export const MAP_COLORS = {
  land: '#ECE9E1',
  yard: '#E6E2D7',
  house: '#DAD5C8',
  houseShade: '#CDC6B6',
  casing: '#D6D0C1',
  street: '#FBFAF6',
  main: '#F7DFA6',
  mainCasing: '#E2C27E',
  water: '#BCD5E4',
  waterEdge: '#A6C4D8',
  park: '#D2E2BE',
  parkPath: '#E4ECD6',
  label: '#7E776A',
  waterLabel: '#5F86A1',
  district: '#6E675B',
};

const PIN_HEAD_Y = -36;
const PIN_R = 14;
const LIFT_PT = 14;
const FONT_FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

function poly(points: [number, number][]) {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0]} ${p[1]}`).join(' ');
}

/** Smooth path through points (Catmull-Rom as cubic Béziers). */
function smooth(points: [number, number][]) {
  let d = `M${points[0][0]} ${points[0][1]}`;
  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[Math.max(0, i - 1)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(points.length - 1, i + 2)];
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += ` C${c1[0]} ${c1[1]} ${c2[0]} ${c2[1]} ${p2[0]} ${p2[1]}`;
  }
  return d;
}

function useTown() {
  return useMemo(() => {
    const make = (d: string) => Skia.Path.MakeFromSVGString(d) ?? Skia.Path.Make();
    const footprint = houses();
    const houseD = footprint.map(([x, y, w, h]) => `M${x} ${y} h${w} v${h} h${-w} Z`).join(' ');
    const shadeD = footprint.map(([x, y, w, h]) => `M${x + 1.6} ${y + 2.2} h${w} v${h} h${-w} Z`).join(' ');
    const streetsD = STREETS.map((r) => poly(r.points)).join(' ');
    const crescentD = `M${CRESCENT.from[0]} ${CRESCENT.from[1]} Q${CRESCENT.ctrl[0]} ${CRESCENT.ctrl[1]} ${CRESCENT.to[0]} ${CRESCENT.to[1]}`;
    const north = smooth(RIVER.north);
    const southRev = smooth([...RIVER.south].reverse()).replace(/^M/, 'L');
    const riverD = `${north} ${southRev} Z`;
    const parkPathD = PARKS.map(([x0, y0, x1, y1]) => {
      const cx = (x0 + x1) / 2;
      const cy = (y0 + y1) / 2;
      return `M${x0 + 14} ${y1 - 18} Q${cx} ${cy - 10} ${x1 - 14} ${y0 + 20} M${x0 + 20} ${y0 + 16} Q${cx - 10} ${cy + 20} ${x1 - 18} ${y1 - 14}`;
    }).join(' ');
    return {
      houses: make(houseD),
      shade: make(shadeD),
      streets: make(`${streetsD} ${crescentD}`),
      main: make(poly(MAIN_ROAD.points)),
      river: make(riverD),
      riverEdge: make(`${north} ${smooth(RIVER.south)}`),
      parkPaths: make(parkPathD),
    };
  }, []);
}

function useFonts() {
  return useMemo(
    () => ({
      street: matchFont({ fontFamily: FONT_FAMILY, fontSize: 12, fontWeight: '500' }),
      park: matchFont({ fontFamily: FONT_FAMILY, fontSize: 12, fontWeight: '600' }),
      district: matchFont({ fontFamily: FONT_FAMILY, fontSize: 40, fontWeight: '700' }),
      main: matchFont({ fontFamily: FONT_FAMILY, fontSize: 13, fontWeight: '600' }),
    }),
    [],
  );
}

type Label = { text: string; x: number; y: number; angle: number; w: number };

function StreetMapImpl({ width, height, camera, pin, rotation, accent = '#E0562E' }: Props) {
  const town = useTown();
  const fonts = useFonts();

  const labels = useMemo<Label[]>(
    () =>
      STREETS.map((r) => ({ text: r.name, x: r.label[0], y: r.label[1], angle: (r.angle * Math.PI) / 180, w: fonts.street.measureText(r.name).width })).concat([
        { text: 'Lark Rise', x: 805, y: 872, angle: 0, w: fonts.street.measureText('Lark Rise').width },
      ]),
    [fonts],
  );
  const mainLabel = useMemo(() => ({ w: fonts.main.measureText(MAIN_ROAD.name).width }), [fonts]);
  const districts = useMemo(() => DISTRICTS.map((d) => ({ ...d, w: fonts.district.measureText(d.name).width })), [fonts]);
  const parkLabels = useMemo(() => PARKS.map((p) => ({ text: p[4], x: (p[0] + p[2]) / 2, y: (p[1] + p[3]) / 2 + 4, w: fonts.park.measureText(p[4]).width })), [fonts]);
  const riverW = useMemo(() => fonts.street.measureText('Wendle Water').width, [fonts]);

  const cx = width / 2;
  const cy = height / 2;
  const transform = useDerivedValue(() => {
    const c = camera.get();
    return [{ translateX: cx }, { translateY: cy }, { rotate: rotation }, { scale: c.s }, { translateX: -c.x }, { translateY: -c.y }];
  });
  const streetLabels = useDerivedValue(() => {
    const s = camera.get().s;
    const k = (s - 0.46) / 0.24;
    return k < 0 ? 0 : k > 1 ? 1 : k;
  });
  const districtAlpha = useDerivedValue(() => {
    const s = camera.get().s;
    const k = (s - 0.4) / 0.22;
    return 1 - (k < 0 ? 0 : k > 1 ? 1 : k);
  });

  // Pin, in screen space.
  const pinTransform = useDerivedValue(() => {
    const p = pin.get();
    const sq = p.squash;
    return [{ translateX: cx }, { translateY: cy + p.y - LIFT_PT * p.lift }, { scaleX: 1 + 0.1 * sq }, { scaleY: 1 - 0.14 * sq }];
  });
  const pinAlpha = useDerivedValue(() => pin.get().alpha);
  const shadowRect = useDerivedValue(() => {
    const p = pin.get();
    const fall = Math.min(1, -p.y / 84);
    const k = 1 - 0.55 * Math.max(fall, p.lift * 0.6);
    const w = 22 * k;
    const h = 7 * k;
    return { x: cx - w / 2, y: cy - h / 2, width: w, height: h };
  });
  const shadowAlpha = useDerivedValue(() => {
    const p = pin.get();
    const fall = Math.min(1, -p.y / 84);
    return p.alpha * (0.32 - 0.2 * Math.max(fall, p.lift * 0.6));
  });
  const ring1R = useDerivedValue(() => 6 + 34 * (1 - (1 - pin.get().ring1) * (1 - pin.get().ring1)));
  const ring2R = useDerivedValue(() => 6 + 34 * (1 - (1 - pin.get().ring2) * (1 - pin.get().ring2)));
  const ring1A = useDerivedValue(() => {
    const r = pin.get().ring1;
    return r <= 0 || r >= 1 ? 0 : 0.5 * (1 - r) * pin.get().alpha;
  });
  const ring2A = useDerivedValue(() => {
    const r = pin.get().ring2;
    return r <= 0 || r >= 1 ? 0 : 0.38 * (1 - r) * pin.get().alpha;
  });

  const pinPath = useMemo(
    () =>
      Skia.Path.MakeFromSVGString(
        `M0 0 C-2 -6 ${-PIN_R} ${PIN_HEAD_Y + 14} ${-PIN_R} ${PIN_HEAD_Y} A${PIN_R} ${PIN_R} 0 1 1 ${PIN_R} ${PIN_HEAD_Y} C${PIN_R} ${PIN_HEAD_Y + 14} 2 -6 0 0 Z`,
      ) ?? Skia.Path.Make(),
    [],
  );

  return (
    <Canvas style={{ width, height }}>
      <Rect x={0} y={0} width={width} height={height} color={MAP_COLORS.land} />
      <Group transform={transform}>
        <Rect x={-400} y={-400} width={2000} height={2000} color={MAP_COLORS.land} />
        <Path path={town.river} color={MAP_COLORS.water} />
        <Path path={town.riverEdge} style="stroke" strokeWidth={2} color={MAP_COLORS.waterEdge} />
        {PARKS.map(([x0, y0, x1, y1], i) => (
          <RoundedRect key={i} x={x0 + 9} y={y0 + 9} width={x1 - x0 - 18} height={y1 - y0 - 18} r={10} color={MAP_COLORS.park} />
        ))}
        <Path path={town.parkPaths} style="stroke" strokeWidth={3} strokeCap="round" color={MAP_COLORS.parkPath} />
        <Path path={town.shade} color={MAP_COLORS.houseShade} />
        <Path path={town.houses} color={MAP_COLORS.house} />
        <Path path={town.streets} style="stroke" strokeWidth={15} strokeCap="round" color={MAP_COLORS.casing} />
        <Path path={town.main} style="stroke" strokeWidth={28} strokeCap="butt" color={MAP_COLORS.mainCasing} />
        <Path path={town.streets} style="stroke" strokeWidth={12} strokeCap="round" color={MAP_COLORS.street} />
        <Path path={town.main} style="stroke" strokeWidth={24} strokeCap="butt" color={MAP_COLORS.main} />

        <Group opacity={streetLabels}>
          {labels.map((l) => (
            <Group key={l.text} transform={[{ translateX: l.x }, { translateY: l.y }, { rotate: l.angle }]}>
              <Text x={-l.w / 2} y={4} text={l.text} font={fonts.street} color={MAP_COLORS.street} style="stroke" strokeWidth={3} />
              <Text x={-l.w / 2} y={4} text={l.text} font={fonts.street} color={MAP_COLORS.label} />
            </Group>
          ))}
          <Group transform={[{ translateX: MAIN_ROAD.label[0] }, { translateY: MAIN_ROAD.label[1] }, { rotate: (MAIN_ROAD.angle * Math.PI) / 180 }]}>
            <Text x={-mainLabel.w / 2} y={4.5} text={MAIN_ROAD.name} font={fonts.main} color="#8C6A22" />
          </Group>
          {parkLabels.map((l) => (
            <Text key={l.text} x={l.x - l.w / 2} y={l.y} text={l.text} font={fonts.park} color="#6C8A55" />
          ))}
          <Text x={RIVER.label[0] - riverW / 2} y={RIVER.label[1] + 4} text="Wendle Water" font={fonts.street} color={MAP_COLORS.waterLabel} />
        </Group>
        <Group opacity={districtAlpha}>
          {districts.map((d) => (
            <Group key={d.name}>
              <Text x={d.x - d.w / 2} y={d.y} text={d.name} font={fonts.district} color={MAP_COLORS.land} style="stroke" strokeWidth={8} />
              <Text x={d.x - d.w / 2} y={d.y} text={d.name} font={fonts.district} color={MAP_COLORS.district} opacity={0.62} />
            </Group>
          ))}
        </Group>
      </Group>

      {/* A soft light from the top-left and a vignette, so the drawing sits in the card like a lit surface. */}
      <Rect x={0} y={0} width={width} height={height}>
        <RadialGradient c={vec(width * 0.3, height * 0.2)} r={Math.max(width, height) * 0.95} colors={['rgba(255,255,255,0.18)', 'rgba(255,255,255,0)', 'rgba(60,52,40,0.10)']} positions={[0, 0.55, 1]} />
      </Rect>

      <Circle cx={cx} cy={cy} r={ring1R} color={accent} opacity={ring1A} style="stroke" strokeWidth={2} />
      <Circle cx={cx} cy={cy} r={ring2R} color={accent} opacity={ring2A} style="stroke" strokeWidth={1.5} />
      <Oval rect={shadowRect} color="#2A2015" opacity={shadowAlpha} />
      <Group opacity={pinAlpha}>
        <Group transform={pinTransform}>
          <Path path={pinPath} color="#000" opacity={0.18} transform={[{ translateY: 1.5 }]} />
          <Path path={pinPath}>
            <LinearGradient start={vec(-PIN_R, PIN_HEAD_Y - PIN_R)} end={vec(PIN_R, 0)} colors={[lighten(accent), accent, '#B23D1C']} positions={[0, 0.55, 1]} />
          </Path>
          <Circle cx={0} cy={PIN_HEAD_Y} r={5.2} color="#FFF8F1" />
          <Circle cx={-5} cy={PIN_HEAD_Y - 6} r={3.4} color="rgba(255,255,255,0.32)" />
        </Group>
      </Group>
    </Canvas>
  );
}

function lighten(hex: string) {
  const n = parseInt(hex.slice(1), 16);
  const r = Math.min(255, ((n >> 16) & 255) + 38);
  const g = Math.min(255, ((n >> 8) & 255) + 38);
  const b = Math.min(255, (n & 255) + 30);
  return `rgb(${r},${g},${b})`;
}

export const StreetMap = memo(StreetMapImpl);
