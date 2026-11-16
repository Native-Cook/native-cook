// Woven Speaker Player — the round display, drawn in Skia inside the dome's canvas, in design pt centred on the dome. `DisplayBase`
// is the glass, the bloom, the arc and the bezel; `NowPlaying` is the shot's content (time, the three glyphs, the cover with its
// pause glyph, the two round buttons, the title and the artist). A host's own display content goes in as children of the
// component and reads the same numbers through `useWovenDisplay()`.
import { createContext, useContext, useMemo } from 'react';
import {
  BlurMask,
  Circle,
  Glyphs,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  SweepGradient,
  vec,
  type SkFont,
} from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import {
  arcPath,
  arcSegmentPath,
  castFoot,
  castPath,
  chevronPath,
  coverArt,
  glyphPath,
  ringPath,
  squirclePath,
  wavePath,
} from './wsp-geometry';
import { clamp01, GESTURE, type Look, mix, timeLabel } from './wsp-motion';
import { ref, type ResolvedTokens, type WovenTrack } from './wsp-theme';
import type { Run } from './wsp-text';

export type DisplayRuns = {
  title: Run[];
  artist: Run[];
  avatar: Run;
  timeFont: SkFont;
  cell: number;
  colon: number;
  /** advance of '0' … '9' and ':' at the time's size, to centre each character in its cell */
  digit: number[];
  /** glyph ids of '0' … '9' and ':' in the time's font */
  digitIds: number[];
};

export type DisplayCtx = {
  look: SharedValue<Look>;
  /** which track is current (an integer while at rest) and how far a swipe has slid it, pt */
  indexL: SharedValue<number>;
  slideL: SharedValue<number>;
  queue: WovenTrack[];
  runs: DisplayRuns | null;
  tokens: ResolvedTokens;
  accent: string;
  accentTail: string;
  duration: number;
  reduce: boolean;
};

export const DisplayContext = createContext<DisplayCtx | null>(null);

/** The numbers of the display for a host's own content: the live `Look`, the current track and the tokens. */
export function useWovenDisplay(): DisplayCtx {
  const c = useContext(DisplayContext);
  if (!c) throw new Error('useWovenDisplay must be used inside a WovenSpeaker');
  return c;
}

const D = ref.display;
const C = ref.color;
const COVER = squirclePath(-ref.cover.size / 2, -ref.cover.size / 2, ref.cover.size, ref.cover.size, ref.cover.r);
const COVER_EDGE = ref.cover.size / 2;
// the cover's print lines: one hairline every 2 pt, a quiet risograph texture over the poster
const SCAN = Array.from({ length: 34 }, (_, k) => -34 + k * 2 + 1);
const CAST = castPath(ref.row.cast);
const CAST_FOOT = castFoot(ref.row.cast);
const WAVE = wavePath(ref.row.wave);
const PREV = chevronPath(-1, ref.disc.glyph);
const NEXT = chevronPath(1, ref.disc.glyph);
const GLASS = ringPath(D.r);

/** The glass, the bloom behind the cover, the arc, the handle, the bezel and its red reflection. */
export function DisplayBase() {
  const c = useWovenDisplay();
  const { look } = c;
  const arc = useDerivedValue(() => arcPath(D.arcR, (look.get().pos / c.duration) * 360));
  const bloomR = useDerivedValue(() => look.get().bloomR);
  const bloomOp = useDerivedValue(() => look.get().bloom);
  const handleT = useDerivedValue(() => {
    const a = -Math.PI / 2 + (look.get().pos / c.duration) * 2 * Math.PI;
    return [{ translateX: D.arcR * Math.cos(a) }, { translateY: D.arcR * Math.sin(a) }, { scale: 1 + 0.6 * look.get().head }];
  });
  const handleOp = useDerivedValue(() => look.get().handle);
  const refl = useDerivedValue(() => {
    const deg = -90 + (look.get().pos / c.duration) * 360;
    return arcSegmentPath(D.bezelRing, deg, 30);
  });
  const reflOp = useDerivedValue(() => look.get().refl * 0.85);

  return (
    <>
      <Circle r={D.bezelOuter} color={C.bezelDark} />
      <Group clip={GLASS}>
        <Circle r={D.r}>
          <LinearGradient start={vec(0, -D.r)} end={vec(0, D.r)} colors={[C.glassTop, C.glassBottom]} />
        </Circle>
        {/* the bloom: two soft lobes behind the cover, the lamp coming up before the mesh answers */}
        <Group opacity={bloomOp} blendMode="plus">
          <Circle cx={-ref.bloom.lobeDx} cy={ref.bloom.lobeDy} r={bloomR}>
            <RadialGradient
              c={vec(-ref.bloom.lobeDx, ref.bloom.lobeDy)}
              r={bloomR}
              colors={[`${C.bloom}B0`, `${C.bloom}44`, `${C.bloom}00`]}
              positions={[0, 0.55, 1]}
            />
          </Circle>
          <Circle cx={ref.bloom.lobeDx} cy={ref.bloom.lobeDy} r={bloomR}>
            <RadialGradient
              c={vec(ref.bloom.lobeDx, ref.bloom.lobeDy)}
              r={bloomR}
              colors={[`${C.bloom}B0`, `${C.bloom}44`, `${C.bloom}00`]}
              positions={[0, 0.55, 1]}
            />
          </Circle>
        </Group>
        {/* the arc rides a faint track just inside the glass */}
        <Circle r={D.arcR} style="stroke" strokeWidth={1} color="#FFFFFF" opacity={0.07} />
        <Group transform={[{ rotate: -Math.PI / 2 }]}>
          <Path path={arc} style="stroke" strokeWidth={D.arcW} strokeCap="round">
            <SweepGradient c={vec(0, 0)} colors={[c.accent, c.accentTail, c.accentTail]} positions={[0, 0.58, 1]} />
          </Path>
        </Group>
        <Group transform={handleT} opacity={handleOp}>
          <Circle r={3} color={c.accentTail} />
          <Circle r={3} color="#FFFFFF" opacity={0.28} />
        </Group>
      </Group>
      {/* the bezel: a thin metal ring that catches the key light on the upper left, and the lamp's red reflection */}
      <Circle r={D.bezelRing} style="stroke" strokeWidth={D.bezelW}>
        <SweepGradient
          c={vec(0, 0)}
          colors={[C.bezelLo, '#46474C', C.bezelHi, '#46474C', C.bezelLo]}
          positions={[0, 0.3, 0.625, 0.85, 1]}
        />
      </Circle>
      <Circle r={D.r + 0.4} style="stroke" strokeWidth={0.6} color="#000000" opacity={0.6} />
      <Path path={refl} style="stroke" strokeWidth={1.5} strokeCap="round" color={c.accent} opacity={reflOp}>
        <BlurMask blur={1.2} style="normal" />
      </Path>
    </>
  );
}

function TrackCover({ i, variant, size }: { i: number; variant: number; size: number }) {
  const c = useWovenDisplay();
  const { look, indexL, slideL } = c;
  const dist = useDerivedValue(() => (i - indexL.get()) * GESTURE.slideSpan + slideL.get());
  const t = useDerivedValue(() => [{ translateX: dist.get() * GESTURE.coverFollow }]);
  const op = useDerivedValue(() => clamp01(1 - Math.abs(dist.get()) / (GESTURE.slideSpan * 0.9)));
  const dim = useDerivedValue(() => 0.2 * clamp01(look.get().turn));
  const art = useMemo(() => coverArt(variant), [variant]);
  const half = size / 2;
  const { sun, rim } = art;
  return (
    <Group transform={t} opacity={op}>
      <Group clip={COVER}>
        <RoundedRect x={-half} y={-half} width={size} height={size} r={ref.cover.r}>
          <LinearGradient start={vec(-half, -half)} end={vec(half * 0.5, half)} colors={[C.coverTop, C.coverBottom]} />
        </RoundedRect>
        <Group transform={[{ scale: size / 68 }]}>
          {/* the low sun and its glow */}
          <Circle cx={sun.x} cy={sun.y} r={sun.r * 1.9}>
            <RadialGradient c={vec(sun.x, sun.y)} r={sun.r * 1.9} colors={[`${C.sunGlow}66`, `${C.sunGlow}00`]} />
          </Circle>
          <Circle cx={sun.x} cy={sun.y} r={sun.r}>
            <LinearGradient start={vec(sun.x, sun.y - sun.r)} end={vec(sun.x, sun.y + sun.r)} colors={[C.sunGlow, C.sun]} />
          </Circle>
          {/* the silhouette, with a rim of light on its sun side */}
          <Group transform={[{ translateX: rim.dx }, { translateY: rim.dy }]}>
            <Path path={art.figure} color={C.rim} />
          </Group>
          <Path path={art.figure}>
            <LinearGradient start={vec(0, -26)} end={vec(0, 34)} colors={[C.figure, C.figureLo]} />
          </Path>
          {SCAN.map((y) => (
            <Rect key={y} x={-34} y={y} width={68} height={0.45} color="#000000" opacity={0.09} />
          ))}
        </Group>
        <Group opacity={dim}>
          <RoundedRect x={-half} y={-half} width={size} height={size} r={ref.cover.r} color="#000000" />
        </Group>
      </Group>
      <Path path={COVER} style="stroke" strokeWidth={0.5} color="#FFFFFF" opacity={0.12} />
    </Group>
  );
}

function TrackText({ i }: { i: number }) {
  const c = useWovenDisplay();
  const { indexL, slideL } = c;
  const dist = useDerivedValue(() => (i - indexL.get()) * GESTURE.slideSpan + slideL.get());
  const t = useDerivedValue(() => [{ translateX: dist.get() * 0.6 }]);
  const op = useDerivedValue(() => clamp01(1 - Math.abs(dist.get()) / (GESTURE.slideSpan * 0.7)));
  const run = c.runs;
  if (!run) return null;
  const title = run.title[i];
  const artist = run.artist[i];
  return (
    <Group transform={t} opacity={op}>
      <Glyphs font={title.font} x={-title.width / 2} y={ref.track.y + 0.364 * ref.track.size} glyphs={title.glyphs} color={c.tokens.ink} />
      <Glyphs
        font={artist.font}
        x={-artist.width / 2}
        y={ref.track.artistY + 0.364 * ref.track.artistSize}
        glyphs={artist.glyphs}
        color={C.displayMute}
      />
    </Group>
  );
}

/** The shot's display content. */
export function NowPlaying() {
  const c = useWovenDisplay();
  const { look, runs } = c;
  const cover = ref.cover.size;
  const glyphP = useDerivedValue(() => glyphPath(look.get().glyph));
  const glyphT = useDerivedValue(() => [{ scale: look.get().press }]);
  const timeT = useDerivedValue(() => [
    { translateY: ref.time.y },
    { scale: mix(1, ref.time.big / ref.time.size, Math.min(look.get().turn, 1.08)) },
  ]);
  const w0 = runs?.cell ?? 7.7;
  const wc = runs?.colon ?? 3.7;
  const xm = -(3 * w0 + wc) / 2;
  const cells = [xm - w0, xm, xm + w0, xm + w0 + wc, xm + 2 * w0 + wc];
  const cellW = [w0, w0, wc, w0, w0];
  const digit = runs?.digit ?? [];
  const ids = runs?.digitIds ?? [];
  const timeY = 0.364 * ref.time.size;
  // One glyph run for "m:ss", each character centred in a tabular cell, so the label counts without wobbling.
  const timeGlyphs = useDerivedValue(() => {
    const s = timeLabel(look.get().pos);
    const p = s.length < 5 ? ` ${s}` : s;
    const out: { id: number; pos: { x: number; y: number } }[] = [];
    for (let i = 0; i < 5; i++) {
      const ch = p.charAt(i);
      if (ch === ' ' || ch === '') continue;
      const k = ch === ':' ? 10 : ch.charCodeAt(0) - 48;
      const w = digit[k] ?? (k === 10 ? wc : w0);
      out.push({ id: ids[k] ?? 0, pos: { x: cells[i] + (cellW[i] - w) / 2, y: timeY } });
    }
    return out;
  });
  const dimCover = useDerivedValue(() => 1 - 0.2 * clamp01(look.get().turn));
  const tracks = c.queue;

  return (
    <>
      {runs ? (
        <Group transform={timeT} opacity={0.92}>
          <Glyphs font={runs.timeFont} glyphs={timeGlyphs} color={C.time} />
        </Group>
      ) : null}

      {/* the row: cast, the avatar, the waveform */}
      <Group transform={[{ translateY: ref.row.y }]}>
        <Group transform={[{ translateX: -ref.row.dx }]} opacity={0.82}>
          <Path path={CAST} style="stroke" strokeWidth={1.2} strokeCap="round" color={C.glyph} />
          <Path path={CAST_FOOT} color={C.glyph} />
        </Group>
        <Circle r={ref.row.avatar / 2} color={C.avatar} />
        {runs ? (
          <Glyphs
            font={runs.avatar.font}
            x={-runs.avatar.width / 2}
            y={0.364 * runs.avatar.size}
            glyphs={runs.avatar.glyphs}
            color={C.avatarInk}
          />
        ) : null}
        <Group transform={[{ translateX: ref.row.dx }]} opacity={0.82}>
          <Path path={WAVE} style="stroke" strokeWidth={1.2} strokeCap="round" color={C.glyph} />
        </Group>
      </Group>

      {tracks.map((tr, i) => (
        <TrackCover key={`c${i}`} i={i} variant={tr.cover ?? i} size={cover} />
      ))}

      {/* the amber line across the cover (static, as in the shot) and the centre control */}
      <Group opacity={dimCover}>
        <RoundedRect x={-COVER_EDGE} y={-0.3} width={ref.cover.size} height={0.6} r={0.3}>
          <LinearGradient
            start={vec(-COVER_EDGE, 0)}
            end={vec(COVER_EDGE, 0)}
            colors={[`${C.amber}00`, C.amber, C.amber, `${C.amber}00`]}
            positions={[0, 0.18, 0.82, 1]}
          />
        </RoundedRect>
        <Group transform={glyphT}>
          <Path path={glyphP} style="stroke" strokeWidth={1.4} strokeJoin="round" color={C.glyph} opacity={0.9} />
          <Path path={glyphP}>
            <LinearGradient start={vec(0, -8)} end={vec(0, 8)} colors={['#F4F2FB', C.glyphLo]} />
          </Path>
        </Group>
      </Group>

      {/* previous and next */}
      {[-1, 1].map((d) => (
        <Group key={`d${d}`} transform={[{ translateX: d * ref.disc.dx }]}>
          <Circle r={ref.disc.size / 2} color={C.discFill} opacity={0.12} />
          <Circle r={ref.disc.size / 2 - 0.3} style="stroke" strokeWidth={0.6} color={C.discFill} opacity={0.32} />
          <Group transform={[{ translateX: d * 0.8 }]} opacity={0.88}>
            <Path path={d < 0 ? PREV : NEXT} color={C.glyph} />
          </Group>
        </Group>
      ))}

      {runs ? tracks.map((_, i) => <TrackText key={`t${i}`} i={i} />) : null}
    </>
  );
}
