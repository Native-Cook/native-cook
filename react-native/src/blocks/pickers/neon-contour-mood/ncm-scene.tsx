import { Platform } from 'react-native';
import {
  BlurMask,
  Circle,
  Group,
  Line,
  LinearGradient,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  Text,
  useFont,
  vec,
  type SkFont,
} from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';
import { clamp, GLOW, LAB, labLetter, labTag, labWord, smooth, type Look } from './ncm-motion';
import { rampAt, THUMB_SKSL, type MoodPack } from './ncm-shaders';
import { comp, ref, type Mood, type NcmColors, type NcmLayout, type NcmTheme } from './ncm-theme';

const thumbFx = Skia.RuntimeEffect.Make(THUMB_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;

const rgbOf = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
/** The ramp colour of the mood under the thumb (sRGB) mixed over `base` by `amount`, as a Skia colour. Runs on the UI runtime. */
function hueMix(pack: MoodPack, mood: number, stop: number, base: number[], amount: number, alpha: number) {
  'worklet';
  const t = [0, 0, 0];
  rampAt(t, 0, pack, mood, stop, false);
  return new Float32Array([
    base[0] + (t[0] - base[0]) * amount,
    base[1] + (t[1] - base[1]) * amount,
    base[2] + (t[2] - base[2]) * amount,
    alpha,
  ]);
}

export function useNcmFonts(S: number, fs: number, heroS: number, theme?: NcmTheme) {
  const text = theme?.textFont;
  return {
    title: useFont(text ?? require('./assets/fonts/Inter-Medium.ttf'), ref.type.title.size * S * fs),
    question: useFont(text ?? require('./assets/fonts/Inter-SemiBold.ttf'), ref.type.question.size * S * fs),
    endpoint: useFont(text ?? require('./assets/fonts/Inter-Regular.ttf'), ref.type.endpoint.size * S * fs),
    button: useFont(text ?? require('./assets/fonts/Inter-SemiBold.ttf'), ref.type.button.size * S * fs),
    tag: useFont(text ?? require('./assets/fonts/Inter-Medium.ttf'), ref.type.tag.size * S * fs),
    display: useFont(theme?.displayFont ?? require('./assets/fonts/Nunito-ExtraBold.ttf'), ref.type.display.size * heroS),
  };
}
export type NcmFonts = ReturnType<typeof useNcmFonts>;
export function textWidth(font: SkFont, text: string, tracking = 0) {
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((a, b) => a + b, 0) + Math.max(0, text.length - 1) * tracking;
}
type Paint = string | Float32Array | DerivedValue<Float32Array<ArrayBuffer>>;
function CenterText({ text, font, x, y, color }: { text: string; font: SkFont; x: number; y: number; color: Paint }) {
  return <Text text={text} font={font} x={x - textWidth(font, text) / 2} y={y} color={color} />;
}
function TrackedText({
  text,
  font,
  x,
  y,
  color,
  tracking,
}: {
  text: string;
  font: SkFont;
  x: number;
  y: number;
  color: string;
  tracking: number;
}) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let cursor = x - textWidth(font, text, tracking) / 2;
  return (
    <>
      {Array.from(text).map((letter, i) => {
        const px = cursor;
        cursor += widths[i] + tracking;
        return <Text key={i} text={letter} font={font} x={px} y={y} color={color} />;
      })}
    </>
  );
}
/** One tube: a soft bloom that opens after the strike, the tight glow of the pin, and the crisp letter. */
function NeonLetter({
  letter,
  glyph,
  index,
  x,
  y,
  font,
  lab,
  color,
}: {
  letter: string;
  glyph: number;
  index: number;
  x: number;
  y: number;
  font: SkFont;
  lab: SharedValue<number[]>;
  color: string;
}) {
  const level = useDerivedValue(() => labLetter(lab.get(), index, glyph, 0, 0));
  const bloom = useDerivedValue(() => labLetter(lab.get(), index, glyph, 1, 0));
  const blur = useDerivedValue(() => labLetter(lab.get(), index, glyph, 2, GLOW.blurTo));
  return (
    <>
      <Text text={letter} x={x} y={y} font={font} color={color} opacity={bloom}>
        <BlurMask blur={blur} style="normal" />
      </Text>
      <Text text={letter} x={x} y={y} font={font} color={color} opacity={level}>
        <BlurMask blur={comp.hero.wordGlow} style="normal" />
      </Text>
      <Text text={letter} x={x} y={y} font={font} color={color} opacity={level} />
    </>
  );
}
function MoodWord({ font, index, mood, L, lab }: { font: SkFont; index: number; mood: Mood; L: NcmLayout; lab: SharedValue<number[]> }) {
  const tracking = ref.type.display.tracking * L.heroS;
  const widths = font.getGlyphWidths(font.getGlyphIDs(mood.word));
  let cursor = L.W / 2 - textWidth(font, mood.word, tracking) / 2;
  const baseline = L.heroY + (ref.type.display.baseline - comp.hero.y) * L.heroS;
  // Scales about the middle of the capitals, so the word settles in place.
  const cy = baseline - font.getSize() * 0.36;
  const zoom = useDerivedValue(() => [
    { translateX: L.W / 2 },
    { translateY: cy },
    { scale: labWord(lab.get(), index) },
    { translateX: -L.W / 2 },
    { translateY: -cy },
  ]);
  return (
    <Group transform={zoom}>
      {Array.from(mood.word).map((letter, glyph) => {
        const px = cursor;
        cursor += widths[glyph] + tracking;
        return <NeonLetter key={glyph} {...{ letter, glyph, index, font, lab }} color={mood.ramp[0]} x={px} y={baseline} />;
      })}
    </Group>
  );
}
function MoodTag({
  index,
  text,
  font,
  L,
  lab,
  color,
}: {
  index: number;
  text: string;
  font: SkFont;
  L: NcmLayout;
  lab: SharedValue<number[]>;
  color: string;
}) {
  const opacity = useDerivedValue(() => labTag(lab.get(), index, 0));
  const lift = L.S * L.fs;
  const cy = L.tagY;
  const shift = useDerivedValue(() => [
    { translateX: L.W / 2 },
    { translateY: cy + labTag(lab.get(), index, 1) * lift },
    { scale: labTag(lab.get(), index, 2) },
    { translateX: -L.W / 2 },
    { translateY: -cy },
  ]);
  return (
    <Group opacity={opacity} transform={shift}>
      <TrackedText text={text} font={font} x={L.W / 2} y={L.tagY + 4.5 * lift} color={color} tracking={ref.type.tag.tracking * L.S} />
    </Group>
  );
}
export function NcmScene({
  L,
  fonts,
  look,
  lab,
  pack,
  moods,
  colors,
  copy,
  press,
  buttonPress,
  closePress,
  edgeOffset,
  reduceFade,
}: {
  L: NcmLayout;
  fonts: NcmFonts;
  look: SharedValue<Look>;
  lab: SharedValue<number[]>;
  pack: MoodPack;
  moods: readonly Mood[];
  colors: NcmColors;
  copy: { next: string; close: string };
  press: SharedValue<number>;
  buttonPress: SharedValue<number>;
  closePress: SharedValue<number>;
  edgeOffset: SharedValue<number>;
  reduceFade: SharedValue<number>;
}) {
  const { S, fs, W, firstX, lastX, sliderY, sliderH } = L;
  const last = Math.max(1, moods.length - 1);
  const track = Skia.RRectXY(Skia.XYWHRect(L.sliderX, sliderY, L.sliderW, sliderH), comp.slider.r * S, comp.slider.r * S);
  const inkRgb = rgbOf(colors.ink);
  const secondaryRgb = rgbOf(colors.secondary);
  const outlineRgb = rgbOf(colors.outline);
  const white = [1, 1, 1];
  const thumbX = useDerivedValue(() => firstX + (clamp(look.get().mood, 0, last) / last) * (lastX - firstX) + edgeOffset.get());
  const thumbY = sliderY + sliderH / 2;
  const thumbR = useDerivedValue(
    () => comp.slider.thumb * S * (1 + (comp.feedback.thumbScale - 1) * Math.max(look.get().press, press.get())),
  );
  const groundRgb = rgbOf(colors.page);
  const thumbUniforms = useDerivedValue(() => {
    const h = [0, 0, 0];
    rampAt(h, 0, pack, look.get().mood, 0, false);
    return { center: [thumbX.get(), thumbY], radius: thumbR.get(), hue: h, ground: groundRgb, press: press.get(), p3: P3 };
  });
  // The neon lights the sheet: every tint below is a function of the mood under the thumb.
  const hue0 = useDerivedValue(() => hueMix(pack, look.get().mood, 0, white, 1, 1));
  const trailEnd = useDerivedValue(() => vec(thumbX.get(), sliderY));
  const trailWidth = useDerivedValue(() => Math.max(0, thumbX.get() - L.sliderX));
  const trailColors = useDerivedValue(() => [colors.track, hue0.get(), colors.track]);
  const poolCenter = useDerivedValue(() => vec(thumbX.get(), thumbY));
  const poolColors = useDerivedValue(() => [
    hueMix(pack, look.get().mood, 0, white, 1, comp.lit.thumbPoolAlpha),
    hueMix(pack, look.get().mood, 0, white, 1, 0),
  ]);
  const questionInk = useDerivedValue(() => hueMix(pack, look.get().mood, 0, inkRgb, comp.lit.inkMix, 1));
  const closeRing = useDerivedValue(() => hueMix(pack, look.get().mood, 0, outlineRgb, comp.lit.closeMix, 1));
  const rimColors = useDerivedValue(() => [
    hueMix(pack, look.get().mood, 1, white, 1, comp.lit.rimAlpha),
    hueMix(pack, look.get().mood, 1, white, 1, 0),
    hueMix(pack, look.get().mood, 1, white, 1, 0),
  ]);
  const leftInk = useDerivedValue(() => {
    const k = clamp(1 - look.get().mood / comp.lit.endReach);
    return new Float32Array([
      secondaryRgb[0] + (inkRgb[0] - secondaryRgb[0]) * k,
      secondaryRgb[1] + (inkRgb[1] - secondaryRgb[1]) * k,
      secondaryRgb[2] + (inkRgb[2] - secondaryRgb[2]) * k,
      1,
    ]);
  });
  const rightInk = useDerivedValue(() => {
    const k = clamp(1 - (last - look.get().mood) / comp.lit.endReach);
    return new Float32Array([
      secondaryRgb[0] + (inkRgb[0] - secondaryRgb[0]) * k,
      secondaryRgb[1] + (inkRgb[1] - secondaryRgb[1]) * k,
      secondaryRgb[2] + (inkRgb[2] - secondaryRgb[2]) * k,
      1,
    ]);
  });
  const buttonTransform = useDerivedValue(() => [
    { translateX: W / 2 },
    { translateY: L.buttonY + L.buttonH / 2 },
    { scale: 1 - (1 - comp.feedback.pressScale) * buttonPress.get() },
    { translateX: -W / 2 },
    { translateY: -L.buttonY - L.buttonH / 2 },
  ]);
  const closeOpacity = useDerivedValue(() => 1 - closePress.get() * 0.4);
  const capsuleWidth = useDerivedValue(() => lab.get()[LAB.width]);
  const capsuleX = useDerivedValue(() => (W - lab.get()[LAB.width]) / 2);
  const capsuleOpacity = useDerivedValue(() => clamp(lab.get()[LAB.width] / (comp.tag.px * 2 * S)));
  const { title, question, display, tag, endpoint, button } = fonts;
  if (!title || !question || !display || !tag || !endpoint || !button) return null;
  const buttonX = comp.button.x * S;
  const buttonW = comp.button.w * S;
  const buttonShape = Skia.RRectXY(Skia.XYWHRect(buttonX, L.buttonY, buttonW, L.buttonH), comp.button.r * S, comp.button.r * S);
  return (
    <>
      <RoundedRect
        x={(W - comp.grabber.w * S) / 2}
        y={L.sheetY + 16 * S}
        width={comp.grabber.w * S}
        height={comp.grabber.h * S}
        r={2 * S}
        color={colors.grabber}
      />
      {L.titleLines.map((line, i) => (
        <CenterText key={i} text={line} font={title} x={W / 2} y={L.titleY + i * ref.type.title.lineHeight * S * fs} color={colors.ink} />
      ))}
      <Group opacity={closeOpacity}>
        <Circle cx={L.closeX + 22 * S} cy={L.closeY + 22 * S} r={22 * S} style="stroke" strokeWidth={0.65 * S} color={closeRing} />
        <Line
          p1={vec(L.closeX + 14 * S, L.closeY + 14 * S)}
          p2={vec(L.closeX + 30 * S, L.closeY + 30 * S)}
          color={colors.close}
          strokeWidth={comp.close.stroke * S}
        />
        <Line
          p1={vec(L.closeX + 30 * S, L.closeY + 14 * S)}
          p2={vec(L.closeX + 14 * S, L.closeY + 30 * S)}
          color={colors.close}
          strokeWidth={comp.close.stroke * S}
        />
      </Group>
      <Group opacity={reduceFade}>
        {moods.map((m, index) => (
          <MoodWord key={m.key} {...{ index, L, lab }} mood={m} font={display} />
        ))}
        <RoundedRect
          x={capsuleX}
          y={L.tagY - (comp.tag.h * S * fs) / 2}
          width={capsuleWidth}
          height={comp.tag.h * S * fs}
          r={comp.tag.r * S * fs}
          color={colors.ink}
          style="stroke"
          strokeWidth={comp.tag.stroke * S * fs}
          opacity={capsuleOpacity}
        />
        {moods.map((m, index) =>
          m.tag ? <MoodTag key={m.key} {...{ index, L, lab }} text={m.tag} font={tag} color={colors.ink} /> : null,
        )}
      </Group>
      {L.questionLines.map((line, i) => (
        <CenterText key={i} text={line} font={question} x={W / 2} y={L.questionY + i * L.questionLine} color={questionInk} />
      ))}
      <RoundedRect x={L.sliderX} y={sliderY} width={L.sliderW} height={sliderH} r={comp.slider.r * S} color={colors.track} />
      <Group clip={track}>
        <Rect x={L.sliderX} y={sliderY} width={trailWidth} height={sliderH} opacity={0.5}>
          <LinearGradient start={vec(L.sliderX, sliderY)} end={trailEnd} colors={trailColors} positions={[0, 0.75, 1]} />
        </Rect>
        <Circle cx={thumbX} cy={thumbY} r={comp.lit.thumbPool * S}>
          <RadialGradient c={poolCenter} r={comp.lit.thumbPool * S} colors={poolColors} />
        </Circle>
        {moods.map((m, k) => (
          <Dot
            key={m.key}
            {...{ k, last, firstX, lastX, thumbX, thumbR, look }}
            cy={thumbY}
            r={comp.slider.dot * S}
            base={colors.dot}
            lit={m.ramp[0]}
            fade={comp.lit.dotClear * S}
          />
        ))}
      </Group>
      {thumbFx && (
        <Circle cx={thumbX} cy={thumbY} r={thumbR}>
          <Shader source={thumbFx} uniforms={thumbUniforms} />
        </Circle>
      )}
      {L.endpointLines[0].map((line, i) => (
        <Text
          key={i}
          text={line}
          font={endpoint}
          x={L.endpointStacked ? (W - textWidth(endpoint, line)) / 2 : 49 * S}
          y={L.endpointY + i * ref.type.endpoint.lineHeight * S * fs}
          color={leftInk}
        />
      ))}
      {L.endpointLines[1].map((line, i) => (
        <Text
          key={i}
          text={line}
          font={endpoint}
          x={L.endpointStacked ? (W - textWidth(endpoint, line)) / 2 : W - 43 * S - textWidth(endpoint, line)}
          y={L.endpointY + (i + (L.endpointStacked ? 1 : 0)) * ref.type.endpoint.lineHeight * S * fs}
          color={rightInk}
        />
      ))}
      <Group transform={buttonTransform}>
        <RoundedRect x={buttonX} y={L.buttonY} width={buttonW} height={L.buttonH} r={comp.button.r * S} color={colors.button} />
        <Group clip={buttonShape}>
          <RoundedRect
            x={buttonX}
            y={L.buttonY}
            width={buttonW}
            height={L.buttonH}
            r={comp.button.r * S}
            style="stroke"
            strokeWidth={comp.lit.rimHeight * 2 * S}
          >
            <LinearGradient
              start={vec(0, L.buttonY)}
              end={vec(0, L.buttonY + L.buttonH)}
              colors={rimColors}
              positions={[0, comp.lit.rimFade, 1]}
            />
          </RoundedRect>
        </Group>
        <CenterText text={copy.next} font={button} x={W / 2} y={L.buttonY + L.buttonH / 2 + 6 * S * fs} color={colors.buttonInk} />
      </Group>
    </>
  );
}
/** A slider stop: the dark dot, the colour it keeps once the thumb has passed it, and a fade near the thumb so none sits half under it. */
function Dot({
  k,
  last,
  firstX,
  lastX,
  thumbX,
  thumbR,
  look,
  cy,
  r,
  base,
  lit,
  fade,
}: {
  k: number;
  last: number;
  firstX: number;
  lastX: number;
  thumbX: SharedValue<number>;
  thumbR: SharedValue<number>;
  look: SharedValue<Look>;
  cy: number;
  r: number;
  base: string;
  lit: string;
  fade: number;
}) {
  const cx = firstX + (k / last) * (lastX - firstX);
  const clear = useDerivedValue(() => smooth((Math.abs(cx - thumbX.get()) - thumbR.get()) / fade));
  const passed = useDerivedValue(() => comp.lit.dotPassed * smooth((look.get().mood - k) / 0.6) * clear.get());
  return (
    <>
      <Circle cx={cx} cy={cy} r={r} color={base} opacity={clear} />
      <Circle cx={cx} cy={cy} r={r} color={lit} opacity={passed} />
    </>
  );
}
