import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Easing, type SharedValue, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import {
  Blur,
  Canvas,
  Group,
  ImageShader,
  Paint,
  Paragraph,
  Path,
  Rect,
  Shader,
  Skia,
  type SkParagraph,
  type SkTypefaceFontProvider,
  TextAlign,
  useFonts as useSkiaFonts,
  useImage,
} from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { DIGIT_D, ICON, type IconName } from './fht-icons';
import { clamp01, type Frame, frameAt, groupFocus, groupGeo, LOOP, type Live, liveFrameAt, PAGE_REST_T } from './fht-motion';
import { FRONT_FX, ORANGE_FX, SCENE_FX } from './fht-shaders';
import { BAR_FOOT, C, FONT, G, GROUPS, LIMIT_Y, NGROUPS, PICK_CENTRE_Y, PICK_ROW, PITCH, ROOMS, SHAPES, SPRING } from './fht-theme';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = Platform.OS === 'ios' ? 1 : 0;

const hex3 = (h: string) => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
const SHAPE_RGB = SHAPES.map((s) => hex3(s.color));
const RGB = {
  paper: hex3(C.paper),
  forest: hex3(C.forest),
  orange: hex3(C.orange),
  orangeLight: hex3(C.orangeLight),
  grey: hex3(C.barGrey),
  tile: hex3(C.tile),
};
const BAR_FULL = [0, 1, 2, 3, 4].map((i) => BAR_FOOT - [470, 499, 529, 440, 381][i]);

type Props = {
  /** Plays the 10 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

// ---- text ----

type TextSpec = {
  g: number;
  text: string;
  x: number;
  base: number; // baseline (page-local pt)
  size: number;
  weight: 400 | 500;
  color: string;
  track?: number; // em
  w?: number; // box width for centred text
};

const TEXT: TextSpec[] = [
  {
    g: G.header,
    text: 'Maren’s',
    x: 40,
    base: 121,
    size: 34,
    weight: 400,
    color: C.tile,
    track: -0.02,
  },
  {
    g: G.header,
    text: 'Home',
    x: 40,
    base: 158,
    size: 34,
    weight: 400,
    color: C.tile,
    track: -0.02,
  },
  {
    g: G.bedroom,
    text: 'HOME',
    x: 40,
    base: 242,
    size: 13,
    weight: 500,
    color: C.label,
    track: 0.08,
  },
  {
    g: G.bedroom,
    text: 'Bedroom',
    x: 40,
    base: 303,
    size: 38,
    weight: 400,
    color: C.ink,
  },
  {
    g: G.chat,
    text: 'Live Chat',
    x: 40,
    base: 597,
    size: 38,
    weight: 400,
    color: C.ink,
  },
  {
    g: G.chat,
    text: 'Calling',
    x: 70,
    base: 676.5,
    size: 18,
    weight: 500,
    color: C.tile,
  },
  {
    g: G.chat,
    text: '3 mins',
    x: 264,
    base: 676.5,
    size: 18,
    weight: 500,
    color: C.ink,
    w: 107,
  },
  {
    g: G.chart,
    text: 'Your average',
    x: 46,
    base: 279,
    size: 20,
    weight: 500,
    color: C.ink,
  },
  {
    g: G.chart,
    text: 'usage this month',
    x: 46,
    base: 301,
    size: 20,
    weight: 500,
    color: C.ink,
  },
  {
    g: G.chart,
    text: 'is 104 kWh',
    x: 46,
    base: 323,
    size: 20,
    weight: 500,
    color: C.ink,
  },
  ...['100', '75', '50', '25', '0'].map((t, i) => ({
    g: G.chart,
    text: t,
    x: 46,
    base: 395 + 74 * i + 5.6,
    size: 16,
    weight: 400 as const,
    color: C.label,
  })),
  {
    g: G.alert,
    text: 'ALERT',
    x: 40,
    base: 510,
    size: 13,
    weight: 500,
    color: C.label,
    track: 0.08,
  },
  {
    g: G.alert,
    text: 'Your daily',
    x: 40,
    base: 546,
    size: 20,
    weight: 500,
    color: C.ink,
  },
  {
    g: G.alert,
    text: 'budget has just',
    x: 40,
    base: 568,
    size: 20,
    weight: 500,
    color: C.ink,
  },
  {
    g: G.alert,
    text: 'been reached',
    x: 40,
    base: 590,
    size: 20,
    weight: 500,
    color: C.ink,
  },
  {
    g: G.alert,
    text: 'Ignore',
    x: 149,
    base: 675.5,
    size: 18,
    weight: 500,
    color: C.ink,
    w: 104,
  },
];

type Glyph = {
  g: number;
  icon: IconName;
  cx: number;
  cy: number;
  size: number;
  color: string;
};

const GLYPHS: Glyph[] = [
  {
    g: G.header,
    icon: 'chevronUp',
    cx: 348,
    cy: 107,
    size: 24,
    color: C.forest,
  },
  {
    g: G.header,
    icon: 'chevronDown',
    cx: 348,
    cy: 150,
    size: 24,
    color: C.forest,
  },
  { g: G.bedroom, icon: 'auto', cx: 323, cy: 290, size: 24, color: C.ink },
  { g: G.bedroom, icon: 'timer', cx: 355, cy: 290, size: 24, color: C.ink },
  { g: G.bedroom, icon: 'power', cx: 56, cy: 477, size: 22, color: C.tile },
  { g: G.bedroom, icon: 'panel', cx: 110, cy: 477, size: 22, color: C.ink },
  { g: G.chat, icon: 'headset', cx: 54, cy: 670, size: 19, color: C.tile },
  { g: G.chat, icon: 'hourglass', cx: 225, cy: 670, size: 22, color: C.ink },
  { g: G.strip, icon: 'timer', cx: 55, cy: 105, size: 21, color: C.tile },
  { g: G.strip, icon: 'lock', cx: 55, cy: 153, size: 21, color: C.tile },
  { g: G.chart, icon: 'calculator', cx: 349, cy: 269, size: 22, color: C.ink },
  { g: G.mode, icon: 'power', cx: 85, cy: 130, size: 22, color: C.ink },
  { g: G.mode, icon: 'ecoLeaf', cx: 161, cy: 130, size: 22, color: C.ink },
  { g: G.mode, icon: 'timer', cx: 239, cy: 130, size: 22, color: C.tile },
  { g: G.mode, icon: 'frost', cx: 316, cy: 130, size: 22, color: C.ink },
  { g: G.alert, icon: 'bell', cx: 231, cy: 506, size: 22, color: C.ink },
  { g: G.alert, icon: 'power', cx: 53, cy: 669, size: 20, color: C.ink },
];

type Para = { p: SkParagraph; x: number; y: number; w: number };

const buildPara = (fonts: SkTypefaceFontProvider, t: Omit<TextSpec, 'g'>): Para => {
  const w = t.w ?? 360;
  const p = Skia.ParagraphBuilder.Make({ textAlign: t.w ? TextAlign.Center : TextAlign.Left }, fonts)
    .pushStyle({
      fontFamilies: [FONT.text],
      fontSize: t.size,
      fontStyle: { weight: t.weight },
      color: Skia.Color(t.color),
      letterSpacing: (t.track ?? 0) * t.size,
    })
    .addText(t.text)
    .pop()
    .build();
  p.layout(w);
  const m = p.getLineMetrics()[0];
  return { p, x: t.x, y: t.base - (m ? m.baseline : t.size * 0.95), w };
};

// ---- figures: 48 × 76 path boxes scaled 0.85 (cap 47 pt, as measured on the reference) ----

const FIG = 0.85;
const FIG_ADV = 42.8;
/** Box x of the degree ring and of each figure for a right edge at `right`. */
const figLayout = (right: number) => {
  const deg = right - 22.8;
  const units = deg - 42.8;
  return { deg, units, tens: units - FIG_ADV };
};
const digitOf = (v: number, pos: number) => {
  'worklet';
  return pos === 0 ? Math.floor(v / 10) % 10 : Math.round(v) % 10;
};

/** A tile's text and glyphs: one focus group, defocused by a gaussian (text only, never the tile itself). */
function TileText({ g, F, children }: { g: number; F: SharedValue<Frame>; children: ReactNode }) {
  const grp = GROUPS[g];
  const tf = useDerivedValue(() => {
    const f = F.get();
    const q = groupGeo(g, f.s, f.fp);
    return [{ translateX: q.cx }, { translateY: q.cy + q.dy }, { scale: q.scale }, { translateX: -q.cx }, { translateY: -q.cy }];
  });
  const blur = useDerivedValue(() => groupFocus(g, F.get()).r * 0.5);
  const op = useDerivedValue(() => {
    const f = F.get();
    const q = groupGeo(g, f.s, f.fp);
    const top = grp.rect[1] + q.dy;
    if (top > 940 || top + grp.rect[3] < -60) return 0;
    const a = groupFocus(g, f).a;
    return g === G.alert ? a * (0.6 + 0.4 * f.alert) : a;
  });
  const pad = 44;
  return (
    <Group transform={tf}>
      <Group
        clip={{
          x: grp.rect[0] - pad,
          y: grp.rect[1] - pad,
          width: grp.rect[2] + 2 * pad,
          height: grp.rect[3] + 2 * pad,
        }}
        layer={
          <Paint opacity={op}>
            <Blur blur={blur} mode="decal" />
          </Paint>
        }
      >
        {children}
      </Group>
    </Group>
  );
}

function Glyphs({ list }: { list: Glyph[] }) {
  return (
    <>
      {list.map((q, i) => {
        const k = q.size / 24;
        return (
          <Group key={i} transform={[{ translateX: q.cx - q.size / 2 }, { translateY: q.cy - q.size / 2 }, { scale: k }]}>
            <Path path={ICON[q.icon]} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={q.color} />
          </Group>
        );
      })}
    </>
  );
}

/** A static figure group (Bedroom's 24°). */
function Figures({ value, right, base }: { value: number; right: number; base: number }) {
  const L = figLayout(right);
  const y = base - 65 * FIG;
  const tens = Math.floor(value / 10);
  const units = value % 10;
  return (
    <>
      <Group transform={[{ translateX: L.tens }, { translateY: y }, { scale: FIG }]}>
        <Path path={DIGIT_D[tens]} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={C.ink} />
      </Group>
      <Group transform={[{ translateX: L.units }, { translateY: y }, { scale: FIG }]}>
        <Path path={DIGIT_D[units]} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={C.ink} />
      </Group>
      <Group transform={[{ translateX: L.deg }, { translateY: y }, { scale: FIG }]}>
        <Path path={DIGIT_D[10]} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={C.ink} />
      </Group>
    </>
  );
}

/** One figure position of the strip, changing by focus: the old figure drops 6 pt out of focus, the new one resolves from 8 pt above. */
function FigureStep({ F, pos, incoming, x, y }: { F: SharedValue<Frame>; pos: number; incoming: boolean; x: number; y: number }) {
  const path = useDerivedValue(() => {
    const f = F.get();
    return DIGIT_D[digitOf(incoming ? f.degB : f.degA, pos)];
  });
  const changing = (f: Frame) => {
    'worklet';
    return digitOf(f.degA, pos) !== digitOf(f.degB, pos) && f.degK > 0 && f.degK < 1;
  };
  const tf = useDerivedValue(() => {
    const f = F.get();
    let dy = 0;
    if (changing(f)) dy = incoming ? -8 * (1 - f.degK) : 6 * f.degK;
    return [{ translateX: x }, { translateY: y + dy }, { scale: FIG }];
  });
  const blur = useDerivedValue(() => {
    const f = F.get();
    if (!changing(f)) return 0;
    return 4 * (incoming ? 1 - f.degK : f.degK);
  });
  const op = useDerivedValue(() => {
    const f = F.get();
    const settled = digitOf(f.degA, pos) === digitOf(f.degB, pos) || f.degK >= 1 || f.degK <= 0;
    if (settled) {
      const showIn = f.degK >= 1;
      return incoming === showIn ? 1 : 0;
    }
    return incoming ? f.degK : 1 - f.degK;
  });
  return (
    <Group
      layer={
        <Paint opacity={op}>
          <Blur blur={blur} mode="decal" />
        </Paint>
      }
    >
      <Group transform={tf}>
        <Path path={path} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={C.ink} />
      </Group>
    </Group>
  );
}

function StripFigures({ F }: { F: SharedValue<Frame> }) {
  const L = figLayout(363);
  const y = 156 - 65 * FIG;
  return (
    <>
      <FigureStep F={F} pos={0} incoming={false} x={L.tens} y={y} />
      <FigureStep F={F} pos={0} incoming x={L.tens} y={y} />
      <FigureStep F={F} pos={1} incoming={false} x={L.units} y={y} />
      <FigureStep F={F} pos={1} incoming x={L.units} y={y} />
      <Group transform={[{ translateX: L.deg }, { translateY: y }, { scale: FIG }]}>
        <Path path={DIGIT_D[10]} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={C.ink} />
      </Group>
    </>
  );
}

/** One picker row: its name turns ink at the centre, label grey beside it; its value rolls with the wheel. */
function PickerRow({ F, i, ink, grey, value }: { F: SharedValue<Frame>; i: number; ink: Para; grey: Para; value: Para }) {
  const room = ROOMS[i];
  // a wheel: rows turn away past the neighbours (compress towards the axis and darken out)
  const tf = useDerivedValue(() => {
    const u = i - F.get().pick;
    const sy = Math.cos(Math.min(Math.abs(u), 1.6) * 0.62);
    return [{ translateY: PICK_CENTRE_Y + u * PICK_ROW }, { scaleY: sy }, { translateY: -PICK_CENTRE_Y }];
  });
  const rowOp = useDerivedValue(() => clamp01(1.6 - Math.abs(i - F.get().pick)));
  const inkOp = useDerivedValue(() => clamp01(1 - Math.abs(i - F.get().pick)));
  const greyOp = useDerivedValue(() => 1 - clamp01(1 - Math.abs(i - F.get().pick)));
  const vblur = useDerivedValue(() => {
    const p = F.get().pick;
    return 3 * clamp01(Math.abs(p - Math.round(p)) * 4);
  });
  return (
    <Group transform={tf}>
      {/* paragraphs ignore a group's paint opacity: every fade here is a (row-sized) layer */}
      <Group clip={{ x: 16, y: PICK_CENTRE_Y - 26, width: 370, height: 52 }} layer={<Paint opacity={rowOp} />}>
        <Group layer={<Paint opacity={inkOp} />}>
          <Paragraph paragraph={ink.p} x={ink.x} y={ink.y} width={ink.w} />
        </Group>
        <Group layer={<Paint opacity={greyOp} />}>
          <Paragraph paragraph={grey.p} x={grey.x} y={grey.y} width={grey.w} />
        </Group>
        <Group
          layer={
            <Paint>
              <Blur blur={vblur} mode="decal" />
            </Paint>
          }
        >
          <Group transform={[{ translateX: 237 - 8 }, { translateY: PICK_CENTRE_Y - 8 }, { scale: 16 / 24 }]}>
            <Path path={ICON[room.icon]} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color={C.ink} />
          </Group>
          <Paragraph paragraph={value.p} x={value.x} y={value.y} width={value.w} />
        </Group>
      </Group>
    </Group>
  );
}

/** The dotted limit line (value 90): 2 pt dots every 6 pt; it flinches as the orange crosses it. */
const LIMIT_DOTS = (() => {
  const b = Skia.PathBuilder.Make();
  for (let x = 47; x <= 310; x += 6) b.addCircle(x, LIMIT_Y, 1);
  return b.detach();
})();

/**
 * A smart-home dashboard filmed with a real lens. Depth is focus: every tile sits on a plane and the focus racks from
 * tile to tile following the action; defocused tiles are drawn as true bokeh from their shapes. One orange light never
 * dies: the lamp in the dark, the live dot, bar 5 of the usage chart, the budget bar, and the lamp again.
 */
export function FocusInHomeTiles({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const fonts = useSkiaFonts({
    [FONT.text]: [require('./assets/Figtree-Regular.ttf'), require('./assets/Figtree-Medium.ttf')],
  });
  const grain = useImage(require('./assets/paper-grain.png'));

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  const HC = H / S; // screen height in canvas pt
  const dpr = PixelRatio.get();
  const px = 1 / (S * dpr);

  // ---- clocks and live state ----
  const T = useSharedValue<number>(PAGE_REST_T[0]);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const ls = useSharedValue(0);
  const lfp = useSharedValue(2);
  const lbudget = useSharedValue(120);
  const lalert = useSharedValue(1);
  const alertOn = useSharedValue(1);
  const ldark = useSharedValue(0);
  const llx = useSharedValue(292);
  const lly = useSharedValue(690);
  // gesture
  const mode = useSharedValue(0); // 0 scroll / tap, 1 budget bar, 2 stepper held
  const startS = useSharedValue(0);
  const startB = useSharedValue(0);
  const moved = useSharedValue(0);
  const downAt = useSharedValue(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
    if (autoPlay && !reduced) T.set(0);
  }, [autoPlay, reduced, auto, T]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __fht?: unknown };
    g.__fht = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        T.set(t);
      },
      play: () => {
        pinned.set(0);
        auto.set(1);
      },
      rate: (r: number) => {
        rate.set(r);
      },
      /** Pins a live (finger) state: { s, fp, budget, dark, lx, ly }. */
      live: (
        v: Partial<{
          s: number;
          fp: number;
          budget: number;
          dark: number;
          lx: number;
          ly: number;
        }>,
      ) => {
        pinned.set(1);
        auto.set(0);
        if (v.s !== undefined) ls.set(v.s);
        if (v.fp !== undefined) lfp.set(v.fp);
        if (v.budget !== undefined) {
          lbudget.set(v.budget);
          const on = v.budget >= 100 ? 1 : 0;
          alertOn.set(on);
          lalert.set(on);
        }
        if (v.dark !== undefined) ldark.set(v.dark);
        if (v.lx !== undefined) llx.set(v.lx);
        if (v.ly !== undefined) lly.set(v.ly);
      },
      state: () => ({
        T: T.get(),
        auto: auto.get(),
        pinned: pinned.get(),
        s: ls.get(),
        fp: lfp.get(),
        budget: lbudget.get(),
        dark: ldark.get(),
      }),
    };
    return () => {
      delete g.__fht;
    };
  }, [T, auto, pinned, rate, ls, lfp, lbudget, lalert, alertOn, ldark, llx, lly]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium;
      Haptics.impactAsync(style).catch(() => {});
    }
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    // 2 s after the last touch, settle on the page's rest and hand the loop back from that page
    if (!reduced && autoPlay && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      const page = Math.min(Math.max(Math.round(ls.get() / PITCH), 0), 2);
      const fpRest = [2, 1, 2][page];
      const settled =
        Math.abs(ls.get() - page * PITCH) < 0.5 && Math.abs(lfp.get() - fpRest) < 0.02 && Math.abs(lbudget.get() - 120) < 0.5 && ldark.get() < 0.002;
      if (!settled) {
        const e = { duration: 600, easing: Easing.inOut(Easing.cubic) };
        ls.set(withTiming(page * PITCH, e));
        lfp.set(withTiming(fpRest, e));
        lbudget.set(withTiming(120, e));
        ldark.set(withTiming(0, e));
        alertOn.set(1);
        lalert.set(withTiming(1, { duration: 200 }));
        lastTouch.set(now.get() - 1.3);
      } else {
        T.set(PAGE_REST_T[page]);
        auto.set(1);
      }
    }
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) return frameAt(T.get(), HC);
    const L: Live = {
      s: ls.get(),
      fp: lfp.get(),
      budget: lbudget.get(),
      alert: lalert.get(),
      dark: ldark.get(),
      lx: llx.get(),
      ly: lly.get(),
      sharp: reduced ? 1 : 0,
    };
    return liveFrameAt(L);
  });
  const clock = useDerivedValue(() => (auto.get() === 1 ? T.get() : now.get()));

  const sceneU = useDerivedValue(() => {
    const f = F.get();
    const rects: number[] = [];
    const pars: number[] = [];
    const cols: number[] = [];
    const geo = [];
    const foc = [];
    for (let g = 0; g < NGROUPS; g++) {
      geo.push(groupGeo(g, f.s, f.fp));
      foc.push(groupFocus(g, f));
    }
    for (let i = 0; i < SHAPES.length; i++) {
      const sh = SHAPES[i];
      const q = geo[sh.g];
      let y = sh.rect[1];
      let h = sh.rect[3];
      if (sh.bar !== undefined) {
        h = 44 + (BAR_FULL[sh.bar] - 44) * f.grow[sh.bar];
        y = BAR_FOOT - h;
      }
      const X = q.cx + (sh.rect[0] - q.cx) * q.scale;
      const Y = q.cy + (y - q.cy) * q.scale + q.dy;
      const w = sh.rect[2] * q.scale;
      const hh = h * q.scale;
      const vis = Y + hh > -60 && Y < HC + 60;
      rects.push(X, Y, w, hh);
      pars.push(sh.r * q.scale, foc[sh.g].r, vis ? foc[sh.g].a : 0, sh.kind);
      const rgb = SHAPE_RGB[i];
      cols.push(rgb[0], rgb[1], rgb[2], GROUPS[sh.g].plane < f.fp ? -1 : 1);
    }
    return {
      uP3: P3,
      uPx: px,
      uGrainK: S * dpr,
      uPaper: RGB.paper,
      uRect: rects,
      uPar: pars,
      uCol: cols,
    };
  });

  const frontU = useDerivedValue(() => {
    const f = F.get();
    return {
      uP3: P3,
      uFront: f.front,
      uDir: f.dir,
      uT: clock.get(),
      uForest: RGB.forest,
    };
  });
  const frontOp = useDerivedValue(() => (F.get().front >= 1.9 ? 0 : 1));

  const orangeU = useDerivedValue(() => {
    const f = F.get();
    const t = clock.get();
    return {
      uP3: P3,
      uPx: px,
      uBox: [f.ox, f.oy, Math.max(f.ow, 0.5), Math.max(f.oh, 0.5)],
      uRad: f.or,
      uTheta: f.oth,
      uFlood: f.flood,
      uLamp: f.olamp,
      uBreath: 3 * Math.sin(2 * Math.PI * 0.3 * t),
      uBlink: 0.5 + 0.5 * Math.cos(2 * Math.PI * t),
      uOr: RGB.orange,
      uOrL: RGB.orangeLight,
      uGrey: RGB.grey,
      uTile: RGB.tile,
    };
  });
  const orangeRect = useDerivedValue(() => {
    const f = F.get();
    const pad = 60;
    return {
      x: f.ox - f.ow / 2 - pad,
      y: f.oy - f.oh / 2 - pad,
      width: f.ow + 2 * pad,
      height: f.oh + 2 * pad,
    };
  });
  // the budget bar's power glyph, 23 pt above its foot, drawn in as the orange lands
  const glyphTf = useDerivedValue(() => {
    const f = F.get();
    return [{ translateX: f.ox - 9 }, { translateY: f.oy + f.oh / 2 - 23 - 9 }, { scale: 18 / 24 }];
  });
  const glyphEnd = useDerivedValue(() => F.get().glyph);
  const dipTf = useDerivedValue(() => [{ translateY: F.get().dip }]);

  // ---- paragraphs ----
  const paras = useMemo(() => {
    if (!fonts) return null;
    const text = TEXT.map((t) => ({ g: t.g, ...buildPara(fonts, t) }));
    const rows = ROOMS.map((r) => {
      const name = {
        text: r.name,
        x: 127 - 90,
        base: PICK_CENTRE_Y + 7.5,
        size: 22,
        weight: 400 as const,
        w: 180,
      };
      return {
        ink: buildPara(fonts, { ...name, color: C.ink }),
        grey: buildPara(fonts, { ...name, color: C.label }),
        value: buildPara(fonts, {
          text: r.value,
          x: 258,
          base: PICK_CENTRE_Y + 5.7,
          size: 16,
          weight: 500,
          color: C.ink,
          track: 0.04,
        }),
      };
    });
    return { text, rows };
  }, [fonts]);

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    if (auto.get() === 0) return;
    const f = F.get();
    ls.set(f.s);
    lfp.set(f.fp);
    lbudget.set(f.budget);
    lalert.set(f.alert);
    alertOn.set(f.alert > 0.5 ? 1 : 0);
    llx.set(f.ox);
    lly.set(f.oy);
    let dark = 0;
    if (f.front < 1.9) dark = f.dir > 0 ? clamp01(1 - (f.front + 0.22) / 1.54) : clamp01((f.front + 0.22) / 1.54);
    ldark.set(dark);
    if (dark > 0) ldark.set(withTiming(0, { duration: 900 }));
    auto.set(0);
    pinned.set(0);
  };

  const pageTop = (g: number) => {
    'worklet';
    return GROUPS[g].page * PITCH - ls.get();
  };
  const hitGroup = (x: number, y: number) => {
    'worklet';
    for (let g = 0; g < NGROUPS; g++) {
      const r = GROUPS[g].rect;
      const top = r[1] + pageTop(g);
      if (x >= r[0] && x <= r[0] + r[2] && y >= top && y <= top + r[3]) return g;
    }
    return -1;
  };
  const setBudget = (b: number) => {
    'worklet';
    const v = Math.min(Math.max(b, 40), 120);
    lbudget.set(v);
    // hysteresis ±1 %: no flicker when held on the line
    if (alertOn.get() === 1 && v < 99) {
      alertOn.set(0);
      lalert.set(withTiming(0, { duration: 200 }));
      scheduleOnRN(buzz, 'light');
    } else if (alertOn.get() === 0 && v > 101) {
      alertOn.set(1);
      lalert.set(withTiming(1, { duration: 200 }));
      scheduleOnRN(buzz, 'light');
    }
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      touching.set(1);
      takeOver();
      const x = e.x / S;
      const y = e.y / S;
      moved.set(0);
      downAt.set(now.get());
      startS.set(ls.get());
      startB.set(lbudget.get());
      mode.set(0);
      if (reduced) return;
      const f = F.get();
      // the stepper on the header: hold for the dark, the lamp under the finger
      const stTop = 84 - ls.get();
      if (x >= 318 && x <= 378 && y >= stTop - 8 && y <= stTop + 97) {
        mode.set(2);
        llx.set(f.ox);
        lly.set(f.oy);
        ldark.set(withTiming(1, { duration: 1000, easing: Easing.inOut(Easing.cubic) }));
        llx.set(withTiming(x, { duration: 700 }));
        lly.set(withTiming(y, { duration: 700 }));
        scheduleOnRN(buzz, 'soft');
        return;
      }
      // the budget bar (page 3)
      if (ls.get() > PITCH * 1.9 && Math.abs(x - f.ox) < f.ow / 2 + 12 && Math.abs(y - f.oy) < f.oh / 2 + 16) mode.set(1);
    })
    .onUpdate((e) => {
      const ty = e.translationY / S;
      if (Math.abs(ty) > 6 || Math.abs(e.translationX / S) > 6) moved.set(1);
      if (mode.get() === 2) {
        llx.set(e.x / S);
        lly.set(e.y / S);
        return;
      }
      if (mode.get() === 1) {
        setBudget(startB.get() - (ty * 120) / 222);
        return;
      }
      if (moved.get() === 0) return;
      // scroll, rubber-banded past the ends
      let s = startS.get() - ty;
      const max = 2 * PITCH;
      if (s < 0) s = -60 * (1 - Math.exp(s / 60));
      else if (s > max) s = max + 60 * (1 - Math.exp(-(s - max) / 60));
      ls.set(s);
    })
    .onFinalize((e, success) => {
      touching.set(0);
      lastTouch.set(now.get());
      if (mode.get() === 2) {
        ldark.set(
          withTiming(0, { duration: 1100, easing: Easing.inOut(Easing.cubic) }, (done) => {
            if (done) scheduleOnRN(buzz, 'soft');
          }),
        );
        return;
      }
      if (mode.get() === 1) return;
      if (moved.get() === 0 && now.get() - downAt.get() < 0.35) return;
      const v = success ? -e.velocityY / S : 0;
      const target = Math.min(Math.max(Math.round((ls.get() + v * 0.18) / PITCH), 0), 2);
      if (Math.round(startS.get() / PITCH) !== target) scheduleOnRN(buzz, 'sel');
      ls.set(
        withSpring(target * PITCH, {
          damping: SPRING.snap.d,
          stiffness: SPRING.snap.k,
          velocity: v,
        }),
      );
      // the lens follows: the page's first tile comes into focus
      if (!reduced && Math.round(startS.get() / PITCH) !== target)
        lfp.set(
          withTiming([2, 1, 2][target], {
            duration: 550,
            easing: Easing.inOut(Easing.cubic),
          }),
        );
    });
  const tap = Gesture.Tap()
    .maxDuration(350)
    .maxDistance(6 * S)
    .onEnd((e, success) => {
      if (!success || reduced || mode.get() !== 0 || moved.get() !== 0) return;
      const g = hitGroup(e.x / S, e.y / S);
      if (g >= 0) {
        lfp.set(
          withTiming(GROUPS[g].plane, { duration: 550, easing: Easing.inOut(Easing.cubic) }, (done) => {
            if (done) scheduleOnRN(buzz, 'soft');
          }),
        );
      }
    });

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  const byGroup = (g: number) => (paras ? paras.text.filter((t) => t.g === g) : []);
  const glyphs = (g: number) => GLYPHS.filter((q) => q.g === g);
  const texts = (g: number) => byGroup(g).map((t, i) => <Paragraph key={i} paragraph={t.p} x={t.x} y={t.y} width={t.w} />);

  return (
    <View style={styles.root} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={Gesture.Simultaneous(pan, tap)}>
        <View style={StyleSheet.absoluteFill}>
          {size && grain && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Group transform={[{ scale: S }]}>
                <Rect x={0} y={0} width={402} height={HC}>
                  <Shader source={SCENE_FX} uniforms={sceneU}>
                    <ImageShader image={grain} tx="repeat" ty="repeat" />
                  </Shader>
                </Rect>
                {paras && (
                  <>
                    <TileText g={G.header} F={F}>
                      {texts(G.header)}
                      <Glyphs list={glyphs(G.header)} />
                    </TileText>
                    <TileText g={G.bedroom} F={F}>
                      {texts(G.bedroom)}
                      <Glyphs list={glyphs(G.bedroom)} />
                      <Figures value={24} right={366} base={500} />
                    </TileText>
                    <TileText g={G.chat} F={F}>
                      {texts(G.chat)}
                      <Glyphs list={glyphs(G.chat)} />
                    </TileText>
                    <TileText g={G.strip} F={F}>
                      <Glyphs list={glyphs(G.strip)} />
                      <StripFigures F={F} />
                    </TileText>
                    <TileText g={G.chart} F={F}>
                      {texts(G.chart)}
                      <Glyphs list={glyphs(G.chart)} />
                      <Group transform={dipTf}>
                        <Path path={LIMIT_DOTS} color={C.label} />
                      </Group>
                    </TileText>
                    <TileText g={G.mode} F={F}>
                      <Glyphs list={glyphs(G.mode)} />
                    </TileText>
                    <TileText g={G.picker} F={F}>
                      <Group clip={Skia.RRectXY(Skia.XYWHRect(16, 218, 370, 222), 40, 40)}>
                        {paras.rows.map((r, i) => (
                          <PickerRow key={i} F={F} i={i} ink={r.ink} grey={r.grey} value={r.value} />
                        ))}
                      </Group>
                    </TileText>
                    <TileText g={G.alert} F={F}>
                      {texts(G.alert)}
                      <Glyphs list={glyphs(G.alert)} />
                    </TileText>
                  </>
                )}
                <Rect x={0} y={0} width={402} height={HC} opacity={frontOp}>
                  <Shader source={FRONT_FX} uniforms={frontU} />
                </Rect>
                <Rect rect={orangeRect}>
                  <Shader source={ORANGE_FX} uniforms={orangeU} />
                </Rect>
                <Group transform={glyphTf}>
                  <Path path={ICON.power} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color={C.forest} start={0} end={glyphEnd} />
                </Group>
              </Group>
            </Canvas>
          )}
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.paper },
});
