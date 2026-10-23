import { useMemo } from "react";
import { Platform } from "react-native";
import { type SharedValue, useDerivedValue } from "react-native-reanimated";
import {
  BlurMask,
  Box,
  BoxShadow,
  Circle,
  FractalNoise,
  Group,
  Line,
  LinearGradient,
  Path,
  Rect,
  RadialGradient,
  RoundedRect,
  Shadow,
  SweepGradient,
  Text,
  interpolateColors,
  matchFont,
  rect,
  rrect,
  Skia,
  vec,
} from "@shopify/react-native-skia";
import { tactile } from "./tactile-theme";
import { referenceGeometry as g } from "./reference-motion";
import type { TactileControl } from "./use-tactile-control";
import type { Effects } from "./effects";

export type Tilt = { x: number; y: number };

const TOP = 16,
  HEIGHT = 340;
// Specimen box in drawing units, for page-level layers aligned to it.
export const specimen = { top: TOP, height: HEIGHT };
const housing = rrect(rect(1, 1, 998, 282), 141, 141);
const track = rrect(
  rect(g.track.x, g.track.y, g.track.width, g.track.height),
  52,
  52,
);
const ticks = Array.from({ length: 28 }, (_, i) => {
  const a = (i * Math.PI * 2) / 28;
  return {
    a: vec(g.dial.x + Math.sin(a) * 85, g.dial.y - Math.cos(a) * 85),
    b: vec(g.dial.x + Math.sin(a) * 96, g.dial.y - Math.cos(a) * 96),
  };
});
const TRANSFER_AT = tactile.transferAt;

// System face for the Skia-painted text (reflection and lifted object).
function bakedFont(size: number, weight: "400" | "500" = "400") {
  return matchFont({
    fontFamily: Platform.select({
      ios: "Helvetica Neue",
      default: "sans-serif",
    }),
    fontSize: size,
    fontWeight: weight,
  });
}

// The selector glyphs (glyphs.tsx, viewBox 26) as Skia paths, for the baked
// artwork. Sizes match the native glyphs: 50 units for Brightness, 46 for
// Volume, centered in their wells.
const glyphPaths = (() => {
  const sun = Skia.PathBuilder.Make().addCircle(13, 13, 6);
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4;
    sun
      .moveTo(13 + Math.sin(a) * 10, 13 + Math.cos(a) * 10)
      .lineTo(13 + Math.sin(a) * 12, 13 + Math.cos(a) * 12);
  }
  return [
    sun.detach(),
    Skia.Path.MakeFromSVGString("M3 9H9L17 3V23L9 17H3Z M22 10V16")!,
  ];
})();
const glyphSize = [50, 46];

// The device artwork in drawing units (housing top-left at 0,0). Used by the
// specimen canvas, by the floor reflection (baked, mirrored) so the reflection
// shows the same live light, knob and spill, and as the face of the lifted 3D
// object (baked).
export function DeviceArt({
  control,
  fx,
  tilt,
  baked = false,
}: {
  control: TactileControl;
  fx: Effects;
  tilt: SharedValue<Tilt>;
  /** Also paints the native overlays (title, number, selector glyphs) in
   * Skia: for the floor reflection and the lifted 3D object's face. */
  baked?: boolean;
}) {
  const {
    trackPress,
    brightPress,
    volumePress,
    readout,
    numberInk,
    numberAlpha,
    labelAlpha,
    fill,
    mix,
    lightAlpha,
    lightBlur,
    dialRotation,
    dialPress,
    glowPress,
    active,
    reduced,
    selectorMix,
    slosh,
  } = control;
  const extent = useDerivedValue(() => g.track.width * fill.get());
  // Liquid fill. Below the end cap's width the light rises from the left
  // wall like liquid in the channel: a slice whose right edge rounds as it
  // grows, becoming the full cap at extent = H, then a capsule as before. The
  // shape changes continuously from 0, at full color, so there is no pop and
  // no dead zone. The rect extends H past the left wall so the track clip
  // draws the wall side; only its right corners are ever visible.
  const H = g.track.height;
  // The rect always starts H left of the wall so the track clip draws the
  // left cap; that leaves the right corners free to deform (meniscus).
  const fillX = g.track.x - H;
  const fw = useDerivedValue(() => extent.get() + H);
  const fr = useDerivedValue(() => Math.min(H / 2, extent.get() / 2));
  // Meniscus: the leading edge's horizontal radius follows the slosh spring,
  // scaled down with the slice so a thin light deforms proportionally.
  const radii = useDerivedValue(() => {
    const ry = fr.get();
    if (!fx.meniscus) return vec(ry, ry);
    const rx = ry + slosh.get() * (ry / (H / 2));
    return vec(Math.max(ry * 0.55, Math.min(ry * 1.7, rx, fw.get() / 2)), ry);
  });
  // Fades the last hairline at ~0 instead of leaving a 1px sliver.
  const presence = useDerivedValue(() => Math.min(1, extent.get() / 8));
  // The halo grows with length so a few percent never reads as a blurred blob.
  const reach = useDerivedValue(() => {
    const k = Math.min(1, extent.get() / H);
    return k * k;
  });
  const glowWidth = useDerivedValue(() => Math.max(1, extent.get()));
  const end = useDerivedValue(() => vec(g.track.x + glowWidth.get(), 0));
  const colors = useDerivedValue(() => [
    interpolateColors(mix.get(), TRANSFER_AT, [...tactile.transfer[0]]),
    interpolateColors(mix.get(), TRANSFER_AT, [...tactile.transfer[1]]),
  ]);
  const alpha = useDerivedValue(
    () => lightAlpha.get() * (0.35 + 0.65 * active.get()),
  );
  // Light spill: what the light illuminates around it. Strength grows with
  // length; the knob rim catches it only as the light gets close.
  const tip = useDerivedValue(() => g.track.x + extent.get());
  const cy = g.track.y + H / 2;
  const tipColor = useDerivedValue(() =>
    interpolateColors(mix.get(), TRANSFER_AT, [...tactile.transfer[1]]),
  );
  const spillColors = useDerivedValue(() => [
    interpolateColors(mix.get(), TRANSFER_AT, [...tactile.transfer[1]]),
    "#00000000",
  ]);
  const spillCenter = useDerivedValue(() => vec(tip.get(), cy));
  const spill = useDerivedValue(() =>
    fx.spill
      ? alpha.get() * Math.min(1, extent.get() / 8) * (0.22 + 0.3 * fill.get())
      : 0,
  );
  const knobLight = useDerivedValue(() =>
    fx.spill
      ? spill.get() * Math.max(0, Math.min(1, (tip.get() - 380) / 220)) * 1.6
      : 0,
  );
  const knobLightColors = useDerivedValue(() => [tipColor.get(), "#00000000"]);
  // Tilt: highlight band along the top bevel and the knob sheen angle.
  const bevelX = useDerivedValue(() => 500 + tilt.get().x * 320);
  const bevelColors = ["#FFFFFF00", "#FFFFFF2A", "#FFFFFF00"];
  const bevelStart = useDerivedValue(() => vec(bevelX.get() - 220, 0));
  const bevelEnd = useDerivedValue(() => vec(bevelX.get() + 220, 0));
  const sheenTransform = useDerivedValue(() => [
    { rotate: -0.6 + (fx.tilt ? tilt.get().x * 0.7 : 0) },
  ]);
  // Keep blur filters fixed. Changing sigma per frame caused a held frame in
  // native capture; crossfade cached sharp/soft paints instead.
  const soft = useDerivedValue(() => Math.min(1, lightBlur.get() / 14));
  const softAlpha = useDerivedValue(
    () => alpha.get() * presence.get() * soft.get(),
  );
  const sharpAlpha = useDerivedValue(
    () => alpha.get() * presence.get() * (1 - soft.get()),
  );
  // Colored halo the light casts on the housing. Static under Reduce Motion;
  // only the press swell is motion.
  const glow = useDerivedValue(
    () =>
      alpha.get() *
      presence.get() *
      reach.get() *
      (0.42 + (reduced ? 0 : glowPress.get() * 0.18 + lightBlur.get() / 60)),
  );
  const rotate = useDerivedValue(() => [{ rotate: dialRotation.get() }]);
  // Only a touch on the knob presses the knob; the light answers any touch.
  const compress = useDerivedValue(() => [
    { scale: 1 - dialPress.get() * 0.025 },
  ]);
  // Selector wells follow the light's color handoff, not the React commit.
  const brightnessWell = useDerivedValue(() =>
    interpolateColors(selectorMix.get(), [0, 1], ["#151515", "#222222"]),
  );
  const volumeWell = useDerivedValue(() =>
    interpolateColors(selectorMix.get(), [0, 1], ["#222222", "#151515"]),
  );
  const brightnessRim = useDerivedValue(() => (1 - selectorMix.get()) * 0.75);
  const volumeRim = useDerivedValue(() => selectorMix.get() * 0.75);
  // Grab: a finger on the track wakes the light's tip — a soft highlight just
  // inside the leading edge, so the light reads as the thing being held.
  const grab = useDerivedValue(
    () => (reduced ? 0 : trackPress.get() * 0.34) * alpha.get(),
  );
  const grabCenter = useDerivedValue(() =>
    vec(Math.max(g.track.x + H / 2, tip.get() - H / 2), cy),
  );
  // Selector press: the pressed well darkens as it sinks.
  const brightSink = useDerivedValue(() => brightPress.get() * 0.42);
  const volumeSink = useDerivedValue(() => volumePress.get() * 0.42);
  // Reflection text: the title and percentage painted in Skia so the mirror
  // carries them too (the specimen itself uses native text).
  const font = useMemo(() => (baked ? bakedFont(31) : null), [baked]);
  const numberFont = useMemo(
    () => (baked ? bakedFont(30, "500") : null),
    [baked],
  );
  const brightGlyph = useDerivedValue(() => 1 - selectorMix.get());
  // The pressed glyph sinks with its well, as the native one does.
  const brightGlyphSink = useDerivedValue(() => [
    { scale: 1 - brightPress.get() * 0.08 },
  ]);
  const volumeGlyphSink = useDerivedValue(() => [
    { scale: 1 - volumePress.get() * 0.08 },
  ]);
  const numberText = useDerivedValue(() => `${Math.round(readout.get())}%`);
  const numberX = useDerivedValue(() =>
    numberFont
      ? g.readout.x +
        g.readout.width -
        numberFont.measureText(numberText.get()).width
      : 0,
  );
  const numberColor = useDerivedValue(() =>
    interpolateColors(numberInk.get(), [0, 1], [tactile.text, tactile.ink]),
  );
  return (
    <Group>
      <RoundedRect
        x={4}
        y={12}
        width={992}
        height={280}
        r={140}
        color="#000000"
        opacity={0.36}
      >
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={0} y={0} width={1000} height={284} r={142}>
        <LinearGradient
          start={vec(0, 0)}
          end={vec(0, 284)}
          colors={[...tactile.housing]}
          positions={[0, 0.48, 1]}
        />
      </RoundedRect>
      <Group clip={housing} opacity={0.025} blendMode="multiply">
        <Rect x={0} y={0} width={1000} height={284}>
          <FractalNoise freqX={0.65} freqY={0.65} octaves={1} seed={7} />
        </Rect>
      </Group>
      <RoundedRect
        x={1.2}
        y={1.2}
        width={997.6}
        height={281.6}
        r={140.8}
        style="stroke"
        strokeWidth={2.4}
      >
        <LinearGradient
          start={vec(0, 0)}
          end={vec(0, 284)}
          colors={["#646464", "#343434", "#191919"]}
          positions={[0, 0.3, 1]}
        />
      </RoundedRect>
      {fx.tilt && (
        <Group clip={housing}>
          <Rect x={0} y={0} width={1000} height={18}>
            <LinearGradient
              start={bevelStart}
              end={bevelEnd}
              colors={bevelColors}
            />
          </Rect>
        </Group>
      )}
      {fx.spill && (
        <Group clip={housing} blendMode="screen" opacity={spill}>
          <Circle c={spillCenter} r={330}>
            <RadialGradient c={spillCenter} r={330} colors={spillColors} />
          </Circle>
        </Group>
      )}
      <Box box={track} color={tactile.well}>
        <BoxShadow dx={0} dy={5} blur={7} color="#000000B0" inner />
        <BoxShadow dx={0} dy={-1} blur={1} color="#FFFFFF18" inner />
      </Box>
      <RoundedRect
        x={g.track.x}
        y={g.track.y}
        width={g.track.width}
        height={g.track.height}
        r={52}
        style="stroke"
        strokeWidth={1.2}
      >
        <LinearGradient
          start={vec(0, g.track.y)}
          end={vec(0, g.track.y + g.track.height)}
          colors={["#101010", "#343434", "#565656"]}
          positions={[0, 0.75, 1]}
        />
      </RoundedRect>
      <RoundedRect
        x={g.track.x}
        y={g.track.y}
        width={glowWidth}
        height={H}
        r={fr}
        opacity={glow}
      >
        <LinearGradient start={vec(g.track.x, 0)} end={end} colors={colors} />
        <BlurMask blur={26} style="normal" />
      </RoundedRect>
      <Group clip={track}>
        <RoundedRect
          x={fillX}
          y={g.track.y}
          width={fw}
          height={H}
          r={radii}
          opacity={sharpAlpha}
        >
          <LinearGradient start={vec(g.track.x, 0)} end={end} colors={colors} />
        </RoundedRect>
        <RoundedRect
          x={fillX}
          y={g.track.y}
          width={fw}
          height={H}
          r={radii}
          opacity={softAlpha}
        >
          <LinearGradient start={vec(g.track.x, 0)} end={end} colors={colors} />
          <BlurMask blur={14} style="normal" />
        </RoundedRect>
        <Group blendMode="screen" opacity={grab}>
          <Circle c={grabCenter} r={H * 0.62}>
            <RadialGradient
              c={grabCenter}
              r={H * 0.62}
              colors={["#FFFFFFCC", "#FFFFFF00"]}
            />
          </Circle>
        </Group>
        {fx.glass && (
          <Rect
            x={g.track.x}
            y={g.track.y + 4}
            width={g.track.width}
            height={34}
          >
            <LinearGradient
              start={vec(0, g.track.y + 4)}
              end={vec(0, g.track.y + 38)}
              colors={["#FFFFFF1C", "#FFFFFF00"]}
            />
          </Rect>
        )}
      </Group>
      {([0, 1] as const).map((mode) => {
        const c = mode === 0 ? g.brightness : g.volume;
        return (
          <Box
            key={mode}
            box={rrect(
              rect(c.x - c.radius, c.y - c.radius, c.radius * 2, c.radius * 2),
              c.radius,
              c.radius,
            )}
            color={mode === 0 ? brightnessWell : volumeWell}
          >
            <BoxShadow dx={0} dy={2} blur={2} color="#000000B0" inner />
            <BoxShadow dx={0} dy={-1} blur={1} color="#FFFFFF35" inner />
          </Box>
        );
      })}
      {fx.rim &&
        ([0, 1] as const).map((mode) => {
          const c = mode === 0 ? g.brightness : g.volume;
          return (
            <Circle
              key={`rim${mode}`}
              cx={c.x}
              cy={c.y}
              r={c.radius - 1.5}
              style="stroke"
              strokeWidth={2.2}
              color={mode === 0 ? tactile.warm[1] : tactile.cool[1]}
              opacity={mode === 0 ? brightnessRim : volumeRim}
            />
          );
        })}
      {([0, 1] as const).map((mode) => {
        const c = mode === 0 ? g.brightness : g.volume;
        return (
          <Circle
            key={`sink${mode}`}
            cx={c.x}
            cy={c.y}
            r={c.radius}
            color="#000000"
            opacity={mode === 0 ? brightSink : volumeSink}
          />
        );
      })}
      <Group origin={vec(g.dial.x, g.dial.y)} transform={compress}>
        <Circle
          cx={g.dial.x}
          cy={g.dial.y + 18}
          r={97}
          color="#000000"
          opacity={0.75}
        >
          <BlurMask blur={16} style="normal" />
        </Circle>
        <Circle cx={g.dial.x} cy={g.dial.y} r={100}>
          <LinearGradient
            start={vec(0, g.dial.y - 100)}
            end={vec(0, g.dial.y + 100)}
            colors={[...tactile.bezel]}
            positions={[0, 0.44, 1]}
          />
        </Circle>
        <Circle
          cx={g.dial.x}
          cy={g.dial.y}
          r={99}
          style="stroke"
          strokeWidth={1.6}
        >
          <LinearGradient
            start={vec(0, g.dial.y - 100)}
            end={vec(0, g.dial.y + 100)}
            colors={["#8D8D8D", "#454545", "#171717"]}
            positions={[0, 0.38, 1]}
          />
        </Circle>
        <Group origin={vec(g.dial.x, g.dial.y)} transform={rotate}>
          {ticks.map((tick, i) => (
            <Line
              key={i}
              p1={tick.a}
              p2={tick.b}
              color={tactile.groove}
              strokeWidth={3.3}
              strokeCap="round"
            />
          ))}
        </Group>
        {fx.spill && (
          <Group
            clip={rrect(
              rect(g.dial.x - 100, g.dial.y - 100, 200, 200),
              100,
              100,
            )}
            blendMode="screen"
            opacity={knobLight}
          >
            <Circle cx={g.dial.x} cy={g.dial.y} r={100}>
              <RadialGradient
                c={vec(g.dial.x - 130, g.dial.y)}
                r={150}
                colors={knobLightColors}
              />
            </Circle>
          </Group>
        )}
        {fx.sheen && (
          <Group
            origin={vec(g.dial.x, g.dial.y)}
            transform={sheenTransform}
            blendMode="screen"
          >
            <Circle cx={g.dial.x} cy={g.dial.y} r={99}>
              <SweepGradient
                c={vec(g.dial.x, g.dial.y)}
                colors={[
                  "#FFFFFF00",
                  "#FFFFFF30",
                  "#FFFFFF00",
                  "#FFFFFF00",
                  "#FFFFFF14",
                  "#FFFFFF00",
                  "#FFFFFF00",
                ]}
                positions={[0, 0.08, 0.2, 0.5, 0.58, 0.7, 1]}
              />
            </Circle>
          </Group>
        )}
        <Circle cx={g.dial.x} cy={g.dial.y} r={70}>
          <LinearGradient
            start={vec(0, g.dial.y - 70)}
            end={vec(0, g.dial.y + 70)}
            colors={[...tactile.dial]}
          />
        </Circle>
        <Circle
          cx={g.dial.x}
          cy={g.dial.y}
          r={70}
          style="stroke"
          strokeWidth={1.4}
          color="#4C4C4C"
        />
        <Circle cx={g.dial.x} cy={g.dial.y} r={29} color="#181818">
          <Shadow dx={0} dy={2} blur={3} color="#000000" inner />
        </Circle>
        <Circle
          cx={g.dial.x}
          cy={g.dial.y}
          r={29}
          style="stroke"
          strokeWidth={1}
          color="#363636"
        />
      </Group>
      {baked && font && numberFont && (
        <>
          {([0, 1] as const).map((mode) => {
            const c = mode === 0 ? g.brightness : g.volume;
            const k = glyphSize[mode] / 26;
            return (
              <Group
                key={`glyph${mode}`}
                origin={vec(c.x, c.y)}
                transform={mode === 0 ? brightGlyphSink : volumeGlyphSink}
              >
                <Group
                  transform={[
                    { translateX: c.x - 13 * k },
                    { translateY: c.y - 13 * k },
                    { scale: k },
                  ]}
                >
                  {([tactile.inactive, tactile.text] as const).map((tint) => (
                    <Path
                      key={tint}
                      path={glyphPaths[mode]}
                      style="stroke"
                      strokeWidth={1.6}
                      strokeCap="round"
                      strokeJoin="round"
                      color={tint}
                      opacity={
                        tint === tactile.inactive
                          ? 1
                          : mode === 0
                            ? brightGlyph
                            : selectorMix
                      }
                    />
                  ))}
                </Group>
              </Group>
            );
          })}
          <Text
            x={118}
            y={36 + 31}
            text={control.mode === 0 ? "Brightness" : "Volume"}
            font={font}
            color={tactile.text}
            opacity={labelAlpha}
          />
          <Text
            x={numberX}
            y={g.readout.y + g.readout.line * 0.78}
            text={numberText}
            font={numberFont}
            color={numberColor}
            opacity={numberAlpha}
          />
        </>
      )}
    </Group>
  );
}
