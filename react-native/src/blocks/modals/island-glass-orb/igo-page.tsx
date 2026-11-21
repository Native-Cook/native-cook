// Island Glass Orb: the home screen under the glass (wallpaper, two frosted widgets, their text). It is drawn once
// into an image so the island's shader can refract it; the parts that animate (the calendar row, the status bar) are
// drawn live above it by the component.

import {
  Blur,
  Group,
  ImageShader,
  Path,
  ImageSVG,
  LinearGradient,
  Paragraph,
  Paint,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  type SkImage,
  type SkParagraph,
  type SkSVG,
  type SkTypefaceFontProvider,
  vec,
} from '@shopify/react-native-skia';

import { ICON_CAMERA_SVG, ICON_VOICE_BARS_SVG, NORI_MARK_SVG } from './igo-icons';
import { GRAIN_FX, WALLPAPER_FX } from './igo-shaders';
import { artworkAt, C, COPY, dockTopAt, FAMILY, FAMILY_DISPLAY, G } from './igo-theme';

type Run = { text: string; size: number; weight: number; alpha?: number; track?: number; tnum?: boolean; display?: boolean };

/** One line of Inter, laid out wide; read its width with `getLongestLine()`. */
export const para = (fonts: SkTypefaceFontProvider, runs: Run[]): SkParagraph => {
  const b = Skia.ParagraphBuilder.Make({}, fonts);
  for (const r of runs) {
    const a = r.alpha ?? 1;
    b.pushStyle({
      fontFamilies: [r.display ? FAMILY_DISPLAY : FAMILY],
      fontSize: r.size,
      fontStyle: { weight: r.weight },
      color: Skia.Color(`rgba(247,247,245,${a})`),
      letterSpacing: (r.track ?? 0) * r.size,
      fontFeatures: r.tnum ? [{ name: 'tnum', value: 1 }] : [],
    });
    b.addText(r.text);
    b.pop();
  }
  const p = b.build();
  p.layout(1000);
  return p;
};

export type PageText = ReturnType<typeof makePageText>;

export const makePageText = (fonts: SkTypefaceFontProvider) => ({
  ask: para(fonts, [{ text: COPY.ask, size: 24, weight: 400, alpha: 0.85, track: -0.013 }]),
  day: para(fonts, [{ text: COPY.day, size: 13, weight: 600, alpha: 0.85, track: 0.06 }]),
  date: para(fonts, [{ text: COPY.date, size: 32, weight: 300, track: -0.021, tnum: true, display: true }]),
  tomorrow: para(fonts, [{ text: COPY.tomorrow, size: 13, weight: 600, alpha: 0.6, track: 0.06 }]),
  nori: para(fonts, [{ text: COPY.assistant, size: 13, weight: 500 }]),
  calendar: para(fonts, [{ text: COPY.calendar, size: 13, weight: 500 }]),
  events: para(fonts, [{ text: COPY.events, size: 15, weight: 600, track: -0.01 }]),
  range: para(fonts, [
    { text: COPY.range, size: 13, weight: 400, tnum: true },
    { text: 'PM', size: 13, weight: 500, track: 0.02 },
  ]),
  time: para(fonts, [{ text: COPY.time, size: 17, weight: 600, track: -0.013, tnum: true }]),
});

export type PageSvgs = { mark: SkSVG | null; camera: SkSVG | null; bars: SkSVG | null };
export const makeSvgs = (): PageSvgs => ({
  mark: Skia.SVG.MakeFromString(NORI_MARK_SVG),
  camera: Skia.SVG.MakeFromString(ICON_CAMERA_SVG),
  bars: Skia.SVG.MakeFromString(ICON_VOICE_BARS_SVG),
});

/** Top of a paragraph whose line is centred on y. */
export const topFor = (p: SkParagraph, cy: number) => cy - p.getHeight() / 2;

type W = { x: number; y: number; w: number; h: number; r: number };

/** One transform for the page, widget frost and dock: the sculpture stays registered under every lens. */
export function Wallpaper({ wall, H }: { wall: SkImage; H: number }) {
  const { scale, x, y } = artworkAt(H);
  const { s, ox, oy } = G.wallpaper;
  return (
    <Rect x={0} y={0} width={402} height={H}>
      <Shader source={WALLPAPER_FX} uniforms={{ uP3: 1, uMap: [s * scale, ox * scale + x, oy * scale + y] }}>
        <ImageShader image={wall} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" />
      </Shader>
    </Rect>
  );
}

/** Frosted glass: white 16 %, a 2 pt rim lit to white 55 % at the top. */
function Frosted({ wall, w, H }: { wall: SkImage; w: W; H: number }) {
  const clip = Skia.RRectXY(Skia.XYWHRect(w.x, w.y, w.w, w.h), w.r, w.r);
  return (
    <>
      <Group clip={clip}>
        <Group layer={<Paint><Blur blur={12} mode="clamp" /></Paint>}>
          <Wallpaper wall={wall} H={H} />
        </Group>
        <Rect x={w.x} y={w.y} width={w.w} height={w.h} color="rgba(255,255,255,0.16)" />
      </Group>
      <RoundedRect x={w.x + 1} y={w.y + 1} width={w.w - 2} height={w.h - 2} r={w.r - 1} style="stroke" strokeWidth={2}>
        <LinearGradient start={vec(0, w.y)} end={vec(0, w.y + w.h)} colors={['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.16)']} />
      </RoundedRect>
    </>
  );
}

export function PageTree({ wall, text, svgs, H }: { wall: SkImage; text: PageText; svgs: PageSvgs; H: number }) {
  const ask = G.ask;
  const markSize = 28;
  const iconSize = 26;
  return (
    <Group>
      <Rect x={0} y={0} width={402} height={H} color={C.sage} />
      <Wallpaper wall={wall} H={H} />
      <Rect x={0} y={0} width={402} height={H}>
        <Shader source={GRAIN_FX} uniforms={{ uA: 0.03 }} />
      </Rect>

      <GlassDock wall={wall} H={H} />

      {/* left widget: the assistant */}
      <Frosted wall={wall} w={G.wL} H={H} />
      <RoundedRect x={ask.x} y={ask.y} width={ask.w} height={ask.h} r={ask.r} color="rgba(255,255,255,0.14)" />
      <ImageSVG svg={svgs.mark} x={60} y={ask.y + ask.h / 2 - markSize / 2} width={markSize} height={markSize} />
      <Paragraph paragraph={text.ask} x={98} y={topFor(text.ask, ask.y + ask.h / 2)} width={1000} />
      {G.circles.map((c, i) => (
        <Group key={i}>
          <RoundedRect x={c.x - G.circleD / 2} y={c.y - G.circleD / 2} width={G.circleD} height={G.circleD} r={G.circleD / 2} color="rgba(255,255,255,0.16)" />
          <Group opacity={0.85}>
            <ImageSVG svg={i === 0 ? svgs.camera : svgs.bars} x={c.x - iconSize / 2} y={c.y - iconSize / 2} width={iconSize} height={iconSize} />
          </Group>
        </Group>
      ))}

      {/* right widget: the calendar (its event row is drawn live) */}
      <Frosted wall={wall} w={G.wR} H={H} />
      <Paragraph paragraph={text.day} x={G.textX} y={topFor(text.day, 104)} width={1000} />
      <Paragraph paragraph={text.date} x={G.textX - 1.5} y={topFor(text.date, 128)} width={1000} />
      <Paragraph paragraph={text.tomorrow} x={G.textX} y={topFor(text.tomorrow, 160)} width={1000} />

      {/* app labels */}
      <Paragraph paragraph={text.nori} x={109 - text.nori.getLongestLine() / 2} y={topFor(text.nori, G.labelsY)} width={1000} />
      <Paragraph paragraph={text.calendar} x={293 - text.calendar.getLongestLine() / 2} y={topFor(text.calendar, G.labelsY)} width={1000} />
    </Group>
  );
}

// Invented assistant tools: open weave, split aperture, thought tiles and a pinned route.
// Every mark is drawn in code, with a 2.5 pt stroke; none uses an OS application symbol.
const DOCK_GLYPHS = [
  'M27 18 C13 10 12 35 27 31 M33 29 C47 37 48 12 33 16 M22 25 L38 25',
  'M20 17 L31 13 L43 23 L38 39 L23 43 L15 30 Z M23 22 L35 22 L38 32 L27 36 L21 29 Z',
  'M17 18 Q17 15 20 15 L36 15 Q39 15 39 18 L39 30 L30 30 L24 36 L24 30 L17 30 Z M31 37 L43 37 L43 24',
  'M18 39 L18 30 Q18 25 24 25 L36 25 Q42 25 42 19 M14 39 L22 39 M38 19 L42 15 L46 19 M29 22 L29 28',
].map((p) => Skia.Path.MakeFromSVGString(p));

function GlassDock({ wall, H }: { wall: SkImage; H: number }) {
  // Keep the dock above the bottom inset on shorter screens, with the reviewed 762–846 frame at 874 pt.
  const dock = { ...G.dock, y: dockTopAt(H) };
  const dy = dock.y - G.dock.y;
  return (
    <Group>
      <RoundedRect x={dock.x} y={dock.y + 5} width={dock.w} height={dock.h} r={dock.r} color="rgba(35,49,36,0.18)">
        <Blur blur={12} />
      </RoundedRect>
      <Frosted wall={wall} w={dock} H={H} />
      {DOCK_GLYPHS.map((glyph, i) => (
        <Group key={i} transform={[{ translateX: 36 + i * 90 }, { translateY: 774 + dy }]}>
          <RoundedRect x={0} y={3} width={60} height={60} r={19} color="rgba(29,46,30,0.20)">
            <Blur blur={4} />
          </RoundedRect>
          <RoundedRect x={0} y={0} width={60} height={60} r={19}>
            <LinearGradient start={vec(6, 0)} end={vec(48, 60)} colors={['#D3E5CB80', '#A8C0AF55', '#4D6C5A55']} />
          </RoundedRect>
          <RoundedRect x={1} y={1} width={58} height={58} r={18} style="stroke" strokeWidth={2}>
            <LinearGradient start={vec(12, 0)} end={vec(44, 60)} colors={['#FFFFFFB0', '#FFFFFF20', '#E3F4DB70']} />
          </RoundedRect>
          {glyph && <Path path={glyph} color="#F7F7F5" style="stroke" strokeWidth={2.5} strokeCap="round" strokeJoin="round" />}
        </Group>
      ))}
    </Group>
  );
}
