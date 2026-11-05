// Aurora Teammates Onboarding — the whole screen, drawn by tgfx (react-native-tgfx, Metal on iOS).
//
// One <Canvas>, bottom to top:
//   - the aurora: one runtime shader whose palette, flare and light position are uniform slots;
//   - the three pages' objects: two acrylic tiles (images), five glass agent cards and six glass
//     integration tiles, each a tgfx <Glass> panel that refracts and frosts the aurora behind it under
//     3D rotation, its rect, lean, dispersion and light angle all slots;
//   - a flare that lights whatever passes through the notch (plusLighter);
//   - the sheet: its outline is SVG path data in a string slot, re-parsed on the render thread as the
//     notch dips and the sheet slides; a vertical gradient and a side vignette;
//     a gradient rim light along its edge;
//   - the title, set by tgfx's own text engine (HarfBuzz, letter spacing): one <Text> per word with a
//     gradient paint whose ends are slots, a live <Blur> for the morph and a plusLighter glint;
//   - the subtitles (centred paragraphs), the page dots (the current one doubles as the autoplay
//     timer: a fill grows inside it) and the Next pill: its outline is a path slot too, so a press
//     ripples its edge like a soft blob; on page 3 it blooms, pops and turns into "You're all set" as
//     the tiles' light streams into it (the finale).
// The phone's tilt reaches the aurora (a uniform) and every glass panel (transform and light slots).
// The scene is compiled once. Each frame one UI-thread reaction writes ~300 SharedValues and two path
// strings that the renderer patches natively: no React render, no re-encode, no JS on the render
// thread. The canvas never takes touches: an invisible Pressable over the pill does (AtoControls).

import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { makeMutable, type SharedValue, useAnimatedReaction } from 'react-native-reanimated';
import {
  Canvas,
  Circle,
  Fill,
  Glass,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  Shader,
  Text,
  Blur,
  Rect,
  TgfxImage,
  measureText,
  useFonts,
  useImage,
} from 'react-native-tgfx';

import { AGENTS, GRADIENT_TOKENS, INTEGRATIONS, TOKENS } from './ato-content';
import {
  dotState,
  glintState,
  layoutTitle,
  pillPathD,
  sheetPathD,
  subtitleState,
  SUBTITLES,
  TEXT_FAMILY,
  TITLE_FAMILY,
  tokenState,
  trackWidth,
} from './ato-sheet';
import { GL, OBJ_COUNT, STRIDE } from './ato-motion';
import { AURORA } from './ato-shaders';
import { C, CARD, DOTS, ICON_RENDER, ICONS, type Layout, RAINBOW, TILE } from './ato-theme';

const FONTS = {
  'ATO Inter': [require('./assets/fonts/Inter-Regular.ttf'), require('./assets/fonts/Inter-SemiBold.ttf')],
  'ATO Display': [require('./assets/fonts/InterDisplay-Bold.ttf')],
};

/** Per object in the slot table: transform (6), panel rect (4), content opacity, lean, dispersion. */
const SLOT = 13;
const NG = 13; // globals: phase, w0, w1, w2, flare, coreX, coreY, flare opacity, flare radius, light angle, light intensity, tilt x, tilt y (pt)
const G0 = OBJ_COUNT * SLOT;
const RIM = G0 + NG; // rim light: opacity, gradient start x, end x
const TK = RIM + 3; // per title token: x, y, opacity, blur, gradient start x/y, end x/y, glint x, glint opacity, glint end x
const TKN = 11;
const SB = TK + TOKENS.length * TKN; // per subtitle: y, opacity, blur
const DT = SB + 3 * 3; // dots: track y, then per dot x, y, width, height, radius, r, g, b
const OV = DT + 1 + DOTS * 8; // the timer's fill: x, y, width, opacity
const BT = OV + 4; // button: centre y, scale, sheen x, sheen opacity, "Next" opacity, its y, "All set" opacity, its y, bloom opacity, bloom scale
const SLOT_COUNT = BT + 10;
/** The tilt's reach on the aurora (pt at full tilt). */
const TILT_BG = 12;
const DOT_DIM = [0x5e / 255, 0x5d / 255, 0x84 / 255];
const DONE_LABEL = "You're all set";
const PERSPECTIVE = 640;

type Props = { scene: SharedValue<number[]>; L: Layout; reduced: boolean; press: SharedValue<number> };

export function AtoStage({ scene, L, reduced, press }: Props) {
  const slots = useMemo(() => Array.from({ length: SLOT_COUNT }, () => makeMutable(0)), []);
  const sheetD = useMemo(() => makeMutable(''), []);
  const rimD = useMemo(() => makeMutable(''), []);
  const pillD = useMemo(() => makeMutable(pillPathD(L.button.w, L.button.h, 0, 0)), [L]);
  const pillAmp = useMemo(() => makeMutable(-1), []);
  const fontsReady = useFonts(FONTS);
  const T = useMemo(() => (fontsReady ? layoutTitle(L) : null), [fontsReady, L]);
  const doneW = useMemo(
    () => (fontsReady ? measureText(DONE_LABEL, { fontFamily: TEXT_FAMILY, fontSize: L.button.label, fontWeight: 600 }).width : 0),
    [fontsReady, L],
  );
  const trackW = trackWidth(L);
  const purple = useImage(require('./assets/ato-icon-purple.png'));
  const slate = useImage(require('./assets/ato-icon-slate.png'));
  const logos = [
    useImage(require('./assets/ato-logo-daybook.png')),
    useImage(require('./assets/ato-logo-huddle.png')),
    useImage(require('./assets/ato-logo-parley.png')),
    useImage(require('./assets/ato-logo-inkwell.png')),
    useImage(require('./assets/ato-logo-postbox.png')),
    useImage(require('./assets/ato-logo-leadflow.png')),
  ];

  useAnimatedReaction(
    () => ({ sc: scene.get(), pr: press.get() }),
    ({ sc, pr }) => {
      for (let i = 0; i < OBJ_COUNT; i++) {
        const o = i * STRIDE;
        const s = i * SLOT;
        slots[s].set(sc[o]);
        slots[s + 1].set(sc[o + 1]);
        slots[s + 2].set(sc[o + 2]);
        slots[s + 3].set(sc[o + 3]);
        slots[s + 4].set(sc[o + 4]);
        slots[s + 5].set(sc[o + 5]);
        slots[s + 6].set(-sc[o + 6]);
        slots[s + 7].set(-sc[o + 7]);
        slots[s + 8].set(2 * sc[o + 6]);
        slots[s + 9].set(2 * sc[o + 7]);
        slots[s + 10].set(sc[o + 5] * sc[o + 8]);
        slots[s + 11].set(sc[o + 9]);
        slots[s + 12].set(sc[o + 10]);
      }
      const g = OBJ_COUNT * SLOT;
      slots[g].set(sc[GL.phase]);
      slots[g + 1].set(sc[GL.w0]);
      slots[g + 2].set(sc[GL.w1]);
      slots[g + 3].set(sc[GL.w2]);
      slots[g + 4].set(sc[GL.flare]);
      slots[g + 5].set(sc[GL.coreX]);
      slots[g + 6].set(sc[GL.coreY]);
      slots[g + 7].set(0.45 * sc[GL.flare]);
      slots[g + 8].set((60 + 70 * sc[GL.flare]) * 1);
      slots[g + 9].set(sc[GL.lightAngle]);
      slots[g + 10].set(sc[GL.lightIntensity]);
      slots[g + 11].set(sc[GL.tiltX] * TILT_BG);
      slots[g + 12].set(sc[GL.tiltY] * TILT_BG * 0.7);

      // the sheet, its notch wherever the light is
      const top = sc[GL.sheetTop];
      sheetD.set(sheetPathD(top, sc[GL.dip], sc[GL.coreX], L, true));
      rimD.set(sheetPathD(top, sc[GL.dip], sc[GL.coreX], L, false));
      slots[RIM].set(0.35 + 0.65 * sc[GL.flare]);
      slots[RIM + 1].set(sc[GL.coreX] - L.cx);
      slots[RIM + 2].set(sc[GL.coreX] - L.cx + L.W);

      // the title's words, their gradient fixed to the title block, and the glint
      if (T != null) {
        const o4 = [0, 0, 0, 0];
        const gx0 = L.cx - 150 * (L.S / 1.4357);
        const gx1 = L.cx + 150 * (L.S / 1.4357);
        const gy0 = Math.min(L.sheetTop[0], L.sheetTop[1]) + L.titleTop - L.titleCap;
        const gy1 = gy0 + 3 * L.titleStep + 40;
        for (let i = 0; i < T.width.length; i++) {
          tokenState(T, i, sc, reduced, o4);
          const b = TK + i * TKN;
          slots[b].set(o4[0]);
          slots[b + 1].set(o4[1]);
          slots[b + 2].set(o4[2]);
          slots[b + 3].set(o4[3]);
          slots[b + 4].set(gx0 - o4[0]);
          slots[b + 5].set(gy0 - o4[1]);
          slots[b + 6].set(gx1 - o4[0]);
          slots[b + 7].set(gy1 - o4[1]);
          glintState(T, i, sc, reduced, o4);
          slots[b + 8].set(o4[0]);
          slots[b + 9].set(0.85 * o4[1] * slots[b + 2].get());
          slots[b + 10].set(o4[0] + 0.4 * T.width[i]);
        }
      }
      const o3 = [0, 0, 0];
      for (let p = 0; p < 3; p++) {
        subtitleState(p, sc, L, reduced, o3);
        slots[SB + p * 3].set(o3[0]);
        slots[SB + p * 3 + 1].set(o3[1]);
        slots[SB + p * 3 + 2].set(o3[2]);
      }

      // the dots: the pill flows from slot to slot while the others make room
      slots[DT].set(sc[GL.dotsY] - L.dots.trackH / 2);
      const d4 = [0, 0, 0, 0];
      // the pill's colour: bright as in the pin, or dim while it is the autoplay timer
      const dim = sc[GL.dotDim];
      const pr0 = 0xae / 255 + (DOT_DIM[0] - 0xae / 255) * dim;
      const pg0 = 0xaf / 255 + (DOT_DIM[1] - 0xaf / 255) * dim;
      const pb0 = 0xcc / 255 + (DOT_DIM[2] - 0xcc / 255) * dim;
      for (let i = 0; i < DOTS; i++) {
        dotState(i, sc, L, d4);
        const b = DT + 1 + i * 8;
        slots[b].set(d4[0]);
        slots[b + 1].set((L.dots.trackH - d4[2]) / 2);
        slots[b + 2].set(d4[1]);
        slots[b + 3].set(d4[2]);
        slots[b + 4].set(d4[2] / 2);
        slots[b + 5].set(0x3a / 255 + (pr0 - 0x3a / 255) * d4[3]);
        slots[b + 6].set(0x39 / 255 + (pg0 - 0x39 / 255) * d4[3]);
        slots[b + 7].set(0x59 / 255 + (pb0 - 0x59 / 255) * d4[3]);
      }
      // the timer's fill, growing from the pill's left end
      dotState(sc[GL.from], sc, L, d4);
      const D = L.dots;
      slots[OV].set(d4[0]);
      slots[OV + 1].set((D.trackH - D.pillH) / 2);
      slots[OV + 2].set(D.pillH + (D.pillW - D.pillH) * sc[GL.dotFill]);
      slots[OV + 3].set(sc[GL.dotFillA]);

      // the button: rides with the sheet, presses in with a soft ripple round its edge, catches the
      // flare as a sheen; in the finale it blooms, pops and changes its words
      const B = L.button;
      slots[BT].set(L.H - sc[GL.buttonBottom] - B.h / 2);
      slots[BT + 1].set(1 - 0.03 * pr + sc[GL.finPop]);
      const amp = reduced ? 0 : Math.round(Math.abs(pr) * 1.1 * L.S * 100) / 100;
      if (amp !== pillAmp.get() || amp > 0) {
        pillAmp.set(amp);
        pillD.set(pillPathD(B.w, B.h, amp, sc[GL.phase] * 7));
      }
      const fs = sc[GL.finSheen];
      if (fs > 0) {
        slots[BT + 2].set(-B.w * 0.7 + B.w * 1.4 * fs);
        slots[BT + 3].set(0.9 * Math.sin(Math.PI * fs));
      } else {
        slots[BT + 2].set(-B.w * 0.7 + B.w * 1.4 * sc[GL.u]);
        slots[BT + 3].set(0.8 * sc[GL.flare]);
      }
      const ck = sc[GL.finCheck];
      slots[BT + 4].set(1 - ck);
      slots[BT + 5].set(-5 * ck);
      slots[BT + 6].set(ck);
      slots[BT + 7].set(5 * (1 - ck));
      slots[BT + 8].set(Math.min(1, sc[GL.finGlow]));
      slots[BT + 9].set(0.85 + 0.35 * Math.min(1, sc[GL.finGlow]));
    },
    [T, reduced, L, pillD, pillAmp],
  );

  const g = OBJ_COUNT * SLOT;
  const S = L.S;
  /** text sizes were measured in points on a 402 pt screen */
  const ts = S / (402 / 280);

  const transformOf = (i: number) => {
    const s = i * SLOT;
    return [
      { translateX: slots[s] },
      { translateY: slots[s + 1] },
      { perspective: PERSPECTIVE },
      { rotateX: slots[s + 3] },
      { rotateY: slots[s + 11] },
      { rotate: slots[s + 2] },
      { scale: slots[s + 4] },
    ];
  };

  // ---------------------------------------------------------------- page 1: the acrylic icons
  const icons = ICONS.map((ic, k) => {
    const img = k === 0 ? purple : slate;
    const size = ic.face * S * ICON_RENDER;
    return (
      <Group key={`icon-${ic.key}`} transform={transformOf(k)} opacity={slots[k * SLOT + 5]}>
        {img != null && <TgfxImage image={img} x={-size / 2} y={-size / 2} width={size} height={size} sampling="linear" />}
      </Group>
    );
  });

  // ---------------------------------------------------------------- page 2: the agent cards
  const cw = CARD.w * S;
  const ch = CARD.h * S;
  const cards = AGENTS.map((a, k) => {
    const i = 2 + k;
    const s = i * SLOT;
    const left = -cw / 2;
    const top = -ch / 2;
    const tile = CARD.tile * S;
    const tileX = left + CARD.tileX * S;
    const tileY = -tile / 2;
    const iconK = (tile * 0.6) / 24;
    const pillW = CARD.pillW * S;
    const pillH = CARD.pillH * S;
    const pillX = cw / 2 - CARD.pillRight * S - pillW;
    return [
      <Group key={`card-${k}`} transform={transformOf(i)} opacity={slots[s + 5]}>
        <Glass
          x={slots[s + 6]}
          y={slots[s + 7]}
          width={slots[s + 8]}
          height={slots[s + 9]}
          r={CARD.r * S}
          refraction={18}
          depth={10}
          frost={22}
          dispersion={slots[s + 12]}
          lightAngle={slots[g + 9]}
          lightIntensity={slots[g + 10]}
        />
        <RoundedRect x={slots[s + 6]} y={slots[s + 7]} width={slots[s + 8]} height={slots[s + 9]} r={CARD.r * S}>
          <LinearGradient
            start={{ x: 0, y: ch / 2 }}
            end={{ x: 0, y: -ch / 2 }}
            colors={['rgba(190,176,255,0.28)', 'rgba(160,146,255,0.17)']}
          />
        </RoundedRect>
        <RoundedRect
          x={slots[s + 6]}
          y={slots[s + 7]}
          width={slots[s + 8]}
          height={slots[s + 9]}
          r={CARD.r * S}
          color="rgba(255,255,255,0.20)"
          style="stroke"
          strokeWidth={0.8}
        />
      </Group>,
      <Group key={`card-c-${k}`} transform={transformOf(i)} opacity={slots[s + 10]}>
        <RoundedRect x={tileX} y={tileY} width={tile} height={tile} r={CARD.tileR * S} color="rgba(255,255,255,0.12)" />
        <RoundedRect
          x={tileX}
          y={tileY}
          width={tile}
          height={tile}
          r={CARD.tileR * S}
          color="rgba(255,255,255,0.16)"
          style="stroke"
          strokeWidth={0.7}
        />
        <Group transform={[{ translate: [tileX + tile / 2 - 12 * iconK, -12 * iconK] }, { scale: iconK }]}>
          <Path path={a.icon} color="#FFFFFF" style="stroke" strokeWidth={1.7} strokeCap="round" strokeJoin="round" />
          <Path path={a.spark} color="#FFFFFF" />
        </Group>
        <Text
          text={a.title}
          x={left + CARD.titleX * S}
          y={top + CARD.titleBase * S}
          fontSize={13.8 * ts}
          fontFamily="ATO Inter"
          fontWeight={600}
          color="#FFFFFF"
        />
        <Text
          text={a.sub}
          x={left + CARD.titleX * S}
          y={top + CARD.subBase * S}
          fontSize={9.8 * ts}
          fontFamily="ATO Inter"
          fontWeight={400}
          color="rgba(255,255,255,0.62)"
        />
        <RoundedRect x={pillX} y={-pillH / 2} width={pillW} height={pillH} r={pillH / 2} color="rgba(255,255,255,0.22)" />
        <Text
          text="Active"
          x={pillX}
          y={3.8 * ts}
          width={pillW}
          textAlign="center"
          fontSize={10.4 * ts}
          fontFamily="ATO Inter"
          fontWeight={600}
          color="rgba(255,255,255,0.92)"
        />
      </Group>,
    ];
  });

  // ---------------------------------------------------------------- page 3: the integration tiles
  const tw = TILE.w * S;
  const th = TILE.h * S;
  const tiles = INTEGRATIONS.map((t, k) => {
    const i = 7 + k;
    const s = i * SLOT;
    const left = -tw / 2;
    const top = -th / 2;
    const logo = TILE.logo * S;
    return [
      <Group key={`tile-${k}`} transform={transformOf(i)} opacity={slots[s + 5]}>
        <Glass
          x={slots[s + 6]}
          y={slots[s + 7]}
          width={slots[s + 8]}
          height={slots[s + 9]}
          r={TILE.r * S}
          refraction={18}
          depth={10}
          frost={22}
          dispersion={slots[s + 12]}
          lightAngle={slots[g + 9]}
          lightIntensity={slots[g + 10]}
        />
        <RoundedRect x={slots[s + 6]} y={slots[s + 7]} width={slots[s + 8]} height={slots[s + 9]} r={TILE.r * S}>
          <LinearGradient
            start={{ x: 0, y: th / 2 }}
            end={{ x: 0, y: -th / 2 }}
            colors={['rgba(200,214,255,0.32)', 'rgba(170,190,255,0.16)']}
          />
        </RoundedRect>
        <RoundedRect
          x={slots[s + 6]}
          y={slots[s + 7]}
          width={slots[s + 8]}
          height={slots[s + 9]}
          r={TILE.r * S}
          color="rgba(255,255,255,0.20)"
          style="stroke"
          strokeWidth={0.8}
        />
      </Group>,
      <Group key={`tile-c-${k}`} transform={transformOf(i)} opacity={slots[s + 10]}>
        {logos[k] != null && (
          <TgfxImage image={logos[k]} x={left + TILE.logoX * S} y={top + TILE.logoY * S} width={logo} height={logo} sampling="mipmap" />
        )}
        <Text
          text={t.name}
          x={left + TILE.logoX * S}
          y={top + TILE.nameBase * S}
          fontSize={13.6 * ts}
          fontFamily="ATO Inter"
          fontWeight={600}
          color="#FFFFFF"
        />
        <Text
          text={t.connected ? `• ${t.status}` : t.status}
          x={left + TILE.logoX * S}
          y={top + TILE.subBase * S}
          fontSize={10 * ts}
          fontFamily="ATO Inter"
          fontWeight={400}
          color="rgba(255,255,255,0.56)"
        />
      </Group>,
    ];
  });

  // ---------------------------------------------------------------- the sheet
  const stops = [0, 380, 420, 440, 460, 480, 500, 520, 580, 600].map((y) => (y * S) / L.H);
  const sheet = [
    <Group key="sheet">
      <Path path={sheetD}>
        <LinearGradient
          start={{ x: 0, y: 0 }}
          end={{ x: 0, y: L.H }}
          colors={['#0E0C12', '#0E0C16', '#0F0D1D', '#0F0E22', '#0F0E28', '#100F2F', '#101037', '#10103E', '#111250', '#12114E']}
          positions={stops}
        />
      </Path>
      <Path path={sheetD}>
        <LinearGradient
          start={{ x: 0, y: 0 }}
          end={{ x: L.W, y: 0 }}
          colors={['rgba(6,5,22,0.34)', 'rgba(6,5,22,0)', 'rgba(6,5,22,0)', 'rgba(6,5,22,0.34)']}
          positions={[0, 0.32, 0.68, 1]}
        />
      </Path>
    </Group>,
    <Path key="rim" path={rimD} style="stroke" strokeWidth={1.1} opacity={slots[RIM]}>
      <LinearGradient
        start={{ x: slots[RIM + 1], y: 0 }}
        end={{ x: slots[RIM + 2], y: 0 }}
        colors={['rgba(78,86,190,0.9)', 'rgba(96,106,214,0.95)', 'rgba(205,215,255,1)', 'rgba(96,106,214,0.95)', 'rgba(78,86,190,0.9)']}
        positions={[0, 0.28, 0.5, 0.72, 1]}
      />
    </Path>,
  ];

  // ---------------------------------------------------------------- the title, word by word
  const title =
    T == null
      ? []
      : TOKENS.map((t, i) => {
          const b = TK + i * TKN;
          const rainbow = GRADIENT_TOKENS.includes(t);
          const w = T.width[i];
          const textProps = {
            text: t,
            x: 0,
            y: 0,
            fontSize: L.titleSize,
            fontFamily: TITLE_FAMILY,
            fontWeight: 700 as const,
            letterSpacing: L.titleTracking,
          };
          return [
            <Group key={`word-${t}`} transform={[{ translateX: slots[b] }, { translateY: slots[b + 1] }]} opacity={slots[b + 2]}>
              <Text {...textProps}>
                {rainbow ? (
                  <LinearGradient start={{ x: 0, y: 0 }} end={{ x: w, y: 0 }} colors={RAINBOW.colors} positions={RAINBOW.positions} />
                ) : (
                  <LinearGradient
                    start={{ x: slots[b + 4], y: slots[b + 5] }}
                    end={{ x: slots[b + 6], y: slots[b + 7] }}
                    colors={[C.title, C.title, C.titleShade]}
                    positions={[0, 0.5, 1]}
                  />
                )}
                <Blur blur={slots[b + 3]} />
              </Text>
            </Group>,
            rainbow ? (
              <Group key={`glint-${t}`} transform={[{ translateX: slots[b] }, { translateY: slots[b + 1] }]} opacity={slots[b + 9]}>
                <Text {...textProps} blendMode="plusLighter">
                  <LinearGradient
                    start={{ x: slots[b + 8], y: 0 }}
                    end={{ x: slots[b + 10], y: 0 }}
                    colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']}
                    positions={[0, 0.5, 1]}
                  />
                </Text>
              </Group>
            ) : null,
          ];
        });

  // ---------------------------------------------------------------- the subtitles
  const subtitles =
    T == null
      ? []
      : SUBTITLES.map((lines, p) => (
          <Group key={`sub-${p}`} transform={[{ translateY: slots[SB + p * 3] }]} opacity={slots[SB + p * 3 + 1]}>
            {lines.map((text, l) => (
              <Text
                key={l}
                text={text}
                x={T.subX[p][l]}
                y={l * L.subtitleStep}
                fontSize={L.subtitleSize}
                fontFamily={TEXT_FAMILY}
                fontWeight={400}
              >
                {/* a flat gradient rather than a colour: the words then take the group's live opacity */}
                <LinearGradient start={{ x: 0, y: 0 }} end={{ x: L.W, y: 0 }} colors={[C.subtitle, C.subtitle]} />
                <Blur blur={slots[SB + p * 3 + 2]} />
              </Text>
            ))}
          </Group>
        ));

  // ---------------------------------------------------------------- the dots
  const dots = (
    <Group key="dots" transform={[{ translateX: L.cx - trackW / 2 }, { translateY: slots[DT] }]}>
      <RoundedRect x={0} y={0} width={trackW} height={L.dots.trackH} r={L.dots.trackH / 2} color={C.dotsTrack} />
      {Array.from({ length: DOTS }, (_, i) => {
        const b = DT + 1 + i * 8;
        return (
          <RoundedRect
            key={i}
            x={slots[b]}
            y={slots[b + 1]}
            width={slots[b + 2]}
            height={slots[b + 3]}
            r={slots[b + 4]}
            color={[slots[b + 5], slots[b + 6], slots[b + 7], 1]}
          />
        );
      })}
      <RoundedRect
        x={slots[OV]}
        y={slots[OV + 1]}
        width={slots[OV + 2]}
        height={L.dots.pillH}
        r={L.dots.pillH / 2}
        color={C.dotActive}
        opacity={slots[OV + 3]}
      />
    </Group>
  );

  // ---------------------------------------------------------------- the Next pill
  const B = L.button;
  const check = B.label * 0.95;
  const doneX = -(doneW + check + B.label * 0.45) / 2;
  const bloom = (
    <Group key="bloom" transform={[{ translateX: L.cx }, { translateY: slots[BT] }, { scale: slots[BT + 9] }]} opacity={slots[BT + 8]}>
      <Group transform={[{ scaleX: (B.w / B.h) * 0.62 }]}>
        <Circle cx={0} cy={0} r={B.h * 1.4} blendMode="plusLighter">
          <RadialGradient
            center={{ x: 0, y: 0 }}
            r={B.h * 1.4}
            colors={['rgba(226,234,255,1)', 'rgba(160,176,255,0.5)', 'rgba(120,110,255,0)']}
            positions={[0, 0.45, 1]}
          />
        </Circle>
      </Group>
    </Group>
  );
  const button = (
    <Group key="button" transform={[{ translateX: L.cx }, { translateY: slots[BT] }, { scale: slots[BT + 1] }]}>
      <Path path={pillD}>
        <LinearGradient
          start={{ x: -B.w * 0.15, y: B.h / 2 }}
          end={{ x: B.w / 2, y: -B.h * 0.4 }}
          colors={[C.buttonLight, C.buttonLight, C.buttonShade]}
          positions={[0, 0.58, 1]}
        />
      </Path>
      <Group clip={{ x: -B.w / 2, y: -B.h / 2, width: B.w, height: B.h, r: B.h / 2 }}>
        <Group transform={[{ translateX: slots[BT + 2] }, { rotate: '18deg' }]} opacity={slots[BT + 3]}>
          <Rect x={-B.h * 0.45} y={-B.h * 1.2} width={B.h * 0.9} height={B.h * 2.4}>
            <LinearGradient
              start={{ x: -B.h * 0.45, y: 0 }}
              end={{ x: B.h * 0.45, y: 0 }}
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.95)', 'rgba(255,255,255,0)']}
              positions={[0, 0.5, 1]}
            />
          </Rect>
        </Group>
      </Group>
      {/* labels in a flat gradient, not a colour, so they take their group's live opacity */}
      <Group transform={[{ translateY: slots[BT + 5] }]} opacity={slots[BT + 4]}>
        <Text
          text="Next →"
          x={-B.w / 2}
          y={B.label * 0.36}
          width={B.w}
          textAlign="center"
          fontSize={B.label}
          fontFamily={TEXT_FAMILY}
          fontWeight={600}
        >
          <LinearGradient start={{ x: 0, y: 0 }} end={{ x: B.w, y: 0 }} colors={[C.buttonText, C.buttonText]} />
        </Text>
      </Group>
      <Group transform={[{ translateY: slots[BT + 7] }]} opacity={slots[BT + 6]}>
        <Path
          path={`M${doneX + check * 0.12} ${-check * 0.02} L${doneX + check * 0.4} ${check * 0.28} L${doneX + check * 0.92} ${-check * 0.36}`}
          style="stroke"
          strokeWidth={B.label * 0.16}
          strokeCap="round"
          strokeJoin="round"
        >
          <LinearGradient start={{ x: 0, y: 0 }} end={{ x: B.w, y: 0 }} colors={[C.buttonText, C.buttonText]} />
        </Path>
        <Text
          text={DONE_LABEL}
          x={doneX + check + B.label * 0.45}
          y={B.label * 0.36}
          fontSize={B.label}
          fontFamily={TEXT_FAMILY}
          fontWeight={600}
        >
          <LinearGradient start={{ x: 0, y: 0 }} end={{ x: B.w, y: 0 }} colors={[C.buttonText, C.buttonText]} />
        </Text>
      </Group>
    </Group>
  );

  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Canvas style={[StyleSheet.absoluteFill, { width: L.W, height: L.H }]} fonts={FONTS}>
        <Fill>
          <Shader
            source={AURORA}
            uniforms={{
              uSize: [L.W, L.H],
              uCore: [slots[g + 5], slots[g + 6]],
              uPhase: slots[g],
              uW: [slots[g + 1], slots[g + 2], slots[g + 3]],
              uFlare: slots[g + 4],
              uTilt: [slots[g + 11], slots[g + 12]],
            }}
          />
        </Fill>
        {icons}
        {cards}
        {tiles}
        <Circle cx={slots[g + 5]} cy={slots[g + 6]} r={slots[g + 8]} opacity={slots[g + 7]} blendMode="plusLighter">
          <RadialGradient
            center={{ x: slots[g + 5], y: slots[g + 6] }}
            r={130}
            colors={['rgba(235,245,255,0.95)', 'rgba(170,190,255,0.35)', 'rgba(120,140,255,0)']}
            positions={[0, 0.45, 1]}
          />
        </Circle>
        {sheet}
        {title}
        {subtitles}
        {dots}
        {bloom}
        {button}
      </Canvas>
    </View>
  );
}
