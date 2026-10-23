import { BlendColor, Blur, BlurMask, Canvas, Circle, Group, Paint, Paragraph, Path, RoundedRect, Skia, rect, rrect } from '@shopify/react-native-skia';
import { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import Animated, { Easing, useDerivedValue, useSharedValue, withTiming } from 'react-native-reanimated';

type Props = {
  value: string;
  onChangeText: (value: string) => void;
  /** The characters are readable and the field is white. */
  shown: boolean;
  /** Called by a press on the eye. */
  onToggle: () => void;
  /** The field's width; its height is FIELD. */
  width: number;
  /** The eye looks pressed: autoPlay's finger. */
  held?: boolean;
  /** Called when a finger lands on the field or on the eye. */
  onTouch?: () => void;
  maxLength?: number;
  /** Shown while the field is empty. */
  placeholder?: string;
  placeholderColor?: string;
  /** The field hidden, and the wipe that floods it. */
  fill?: string;
  wipe?: string;
  dotColor?: string;
  textColor?: string;
  eyeColor?: string;
  eyeOpenColor?: string;
};

export const FIELD = 64;
export const RADIUS = 14;
/** The wipe from the disc to the whole field and back, in milliseconds, and its curve. */
export const WIPE = 340;
const WIPE_CURVE = Easing.bezier(0.55, 0, 0.15, 1);
const PRESS = 90;

const DISC = 33;
// From the field's right edge to the middle of the disc, where the eye stays.
const EYE_INSET = 43;
const LOCK_X = 33;
const LOCK = 21;
const DOTS_FROM = 64;
const PITCH = 12.5;
const DOT = 5;
const FONT_SIZE = 17;
// The canvas is larger than the field by this much, for the shadow.
const BLEED = 28;
// The dots are gone and the characters sharp by this share of the wipe; both are blurred on the way.
const TEXT_FROM = 0.1;
const TEXT_TO = 0.6;
const TEXT_BLUR = 3.5;
const EYE_FROM = 0.15;
const EYE_TO = 0.85;
// The eye: half its width, how far a lid bows, and its lashes.
const EYE = 9.5;
const LID = 5.5;
const LASH_GAP = 2.6;
const LASH = 3.6;
const LASHES = [
  [0.2, -0.5],
  [0.5, 0],
  [0.8, 0.5],
];
const PUPIL = 2.9;
const STROKE = 2.1;

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
// A padlock in a 24 × 24 box.
const ICON_LOCK =
  'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function channels(color: string) {
  const [r, g, b] = Array.from(Skia.Color(color));
  return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
}

/** `from` on its way to `to`, at `alpha`: text fades through the colour its layer is filled with. */
function mix(from: number[], to: number[], t: number, alpha: number) {
  'worklet';
  const u = clamp01(t);
  return `rgba(${Math.round(from[0] + (to[0] - from[0]) * u)},${Math.round(from[1] + (to[1] - from[1]) * u)},${Math.round(from[2] + (to[2] - from[2]) * u)},${alpha})`;
}

/** The eye, `open` of the way from shut to open: two lids, and three lashes that flip from under to over. */
function eyePath(open: number) {
  'worklet';
  const path = Skia.PathBuilder.Make();
  // A lid is a curve whose middle point bows by LID: the lower one down, the upper one from down to up.
  const bow = 2 * LID * (1 - 2 * open);
  path.moveTo(-EYE, 0).quadTo(0, 2 * LID, EYE, 0);
  path.moveTo(-EYE, 0).quadTo(0, bow, EYE, 0);
  // The lashes grow out of the upper lid, away from the eye; they vanish as it passes the middle.
  const side = 1 - 2 * open;
  const reach = Math.sqrt(Math.abs(side));
  for (let i = 0; i < LASHES.length; i++) {
    const t = LASHES[i][0];
    const x = -EYE + 2 * EYE * t;
    const y = bow * 2 * t * (1 - t);
    const dx = LASHES[i][1];
    const dy = side > 0 ? 1 : -1;
    const length = Math.hypot(dx, dy);
    path.moveTo(x + (dx / length) * LASH_GAP * reach, y + (dy / length) * LASH_GAP * reach);
    path.lineTo(x + (dx / length) * (LASH_GAP + LASH) * reach, y + (dy / length) * (LASH_GAP + LASH) * reach);
  }
  return path.build();
}

function character(text: string) {
  const paragraph = Skia.ParagraphBuilder.Make({})
    .pushStyle({ color: Skia.Color('#ffffff'), fontFamilies: [MONO], fontSize: FONT_SIZE, fontStyle: { weight: 500 } })
    .addText(text)
    .build();
  paragraph.layout(FONT_SIZE * 2);
  return { paragraph, width: paragraph.getLongestLine(), height: paragraph.getHeight() };
}

/**
 * A password field with an eye. Hidden, it is dark, with a dot for every character and a white
 * disc that holds a shut eye. A press on the eye stretches the disc into a pill that grows across
 * the field as a white wipe; under it the dots blur into the characters, the padlock turns dark
 * where the wipe has passed, and the eye opens. A second press wipes back.
 */
export function WipeField({
  value,
  onChangeText,
  shown,
  onToggle,
  width,
  held = false,
  onTouch,
  maxLength = 14,
  placeholder = 'Password',
  placeholderColor = '#5d648f',
  fill = '#121829',
  wipe = '#ffffff',
  dotColor = '#5b5fe0',
  textColor = '#5f5f9e',
  eyeColor = '#3b3690',
  eyeOpenColor = '#4b3fb5',
}: Props) {
  const [focused, setFocused] = useState(false);
  // 0 hidden, 1 shown: how far the wipe has gone.
  const progress = useSharedValue(shown ? 1 : 0);
  const pressed = useSharedValue(0);

  useEffect(() => {
    progress.set(withTiming(shown ? 1 : 0, { duration: WIPE, easing: WIPE_CURVE }));
  }, [shown, progress]);

  useEffect(() => {
    pressed.set(withTiming(held ? 1 : 0, { duration: PRESS }));
  }, [held, pressed]);

  const characters = useMemo(() => value.split('').map(character), [value]);
  const colors = useMemo(
    () => ({ dot: channels(dotColor), text: channels(textColor), eye: channels(eyeColor), eyeOpen: channels(eyeOpenColor) }),
    [dotColor, textColor, eyeColor, eyeOpenColor],
  );

  // The wipe is the disc's frame on its way to the field's frame; its ends stay round until it is nearly there.
  const flood = useDerivedValue(() => {
    const p = progress.get();
    const size = DISC * (1 - 0.08 * pressed.get() * (1 - p));
    const left = (width - EYE_INSET - size / 2) * (1 - p);
    const right = width - (EYE_INSET - size / 2) * (1 - p);
    const height = size + (FIELD - size) * p;
    const radius = height / 2 + (RADIUS - height / 2) * smooth(p, 0.75, 1);
    return rrect(rect(left, (FIELD - height) / 2, right - left, height), radius, radius);
  });
  const shadowOpacity = useDerivedValue(() => smooth(progress.get(), 0.5, 1));
  const resolved = useDerivedValue(() => smooth(progress.get(), TEXT_FROM, TEXT_TO));
  const dotBlur = useDerivedValue(() => TEXT_BLUR * resolved.get());
  const dotTint = useDerivedValue(() => mix(colors.dot, colors.dot, 0, 1 - resolved.get()));
  const textBlur = useDerivedValue(() => TEXT_BLUR * (1 - resolved.get()));
  const textTint = useDerivedValue(() => mix(colors.text, colors.text, 0, resolved.get()));
  const open = useDerivedValue(() => smooth(progress.get(), EYE_FROM, EYE_TO));
  const eye = useDerivedValue(() => eyePath(open.get()));
  const eyeTint = useDerivedValue(() => mix(colors.eye, colors.eyeOpen, open.get(), 1));
  // Shut, the eye hangs a little under the middle of the disc.
  const eyeTransform = useDerivedValue(() => [
    { translateX: width - EYE_INSET },
    { translateY: FIELD / 2 - 2.5 * (1 - open.get()) },
    { scale: 1 - 0.08 * pressed.get() },
  ]);
  const pupil = useDerivedValue(() => PUPIL * smooth(open.get(), 0.45, 1));

  const lock = [{ translateX: LOCK_X - LOCK / 2 }, { translateY: FIELD / 2 - LOCK / 2 - 1 }, { scale: LOCK / 24 }];
  const count = characters.length;

  return (
    <View style={{ width, height: FIELD }}>
      <Canvas style={[styles.canvas, { width: width + BLEED * 2, height: FIELD + BLEED * 2 }]}>
        <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
          <RoundedRect x={4} y={10} width={width - 8} height={FIELD - 6} r={RADIUS} color="rgba(70,60,190,0.3)" opacity={shadowOpacity}>
            <BlurMask blur={12} style="normal" />
          </RoundedRect>
          <RoundedRect x={0} y={0} width={width} height={FIELD} r={RADIUS} color={fill} />
          <Path path={ICON_LOCK} color="#ffffff" transform={lock} />
          <RoundedRect rect={flood} color={wipe} />
          {/* The padlock again, dark, where the wipe has passed. */}
          <Group clip={flood}>
            <Path path={ICON_LOCK} color={fill} transform={lock} />
          </Group>

          <Group
            layer={
              <Paint>
                <Blur blur={dotBlur} />
                <BlendColor color={dotTint} mode="srcIn" />
              </Paint>
            }
          >
            {characters.map((_, index) => (
              <Circle key={index} cx={DOTS_FROM + index * PITCH} cy={FIELD / 2} r={DOT / 2} color="#ffffff" />
            ))}
          </Group>
          <Group
            layer={
              <Paint>
                <Blur blur={textBlur} />
                <BlendColor color={textTint} mode="srcIn" />
              </Paint>
            }
          >
            {characters.map((one, index) => (
              <Paragraph
                key={index}
                paragraph={one.paragraph}
                x={DOTS_FROM + index * PITCH - one.width / 2}
                y={FIELD / 2 - one.height / 2}
                width={FONT_SIZE * 2}
              />
            ))}
          </Group>

          <Group transform={eyeTransform}>
            <Path path={eye} style="stroke" strokeWidth={STROKE} strokeCap="round" strokeJoin="round" color={eyeTint} />
            <Circle cx={0} cy={0} r={pupil} color={eyeTint} />
          </Group>
        </Group>
      </Canvas>

      {/* The real field: it takes the keyboard, and what it holds is drawn by the canvas under it. */}
      <TextInput
        value={value}
        onChangeText={onChangeText}
        maxLength={maxLength}
        placeholder={placeholder}
        placeholderTextColor={placeholderColor}
        style={styles.input}
        autoCapitalize="none"
        autoCorrect={false}
        spellCheck={false}
        caretHidden
        contextMenuHidden
        textContentType="none"
        accessibilityLabel={placeholder}
        onPressIn={onTouch}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
      {focused && (
        <Animated.View
          pointerEvents="none"
          style={[
            styles.caret,
            {
              left: DOTS_FROM + count * PITCH - PITCH / 2 - 1,
              backgroundColor: dotColor,
              animationName: { '0%': { opacity: 1 }, '50%': { opacity: 1 }, '56%': { opacity: 0 }, '94%': { opacity: 0 }, '100%': { opacity: 1 } },
              animationDuration: '1s',
              animationIterationCount: 'infinite',
              animationTimingFunction: 'linear',
            },
          ]}
        />
      )}
      <Pressable
        style={styles.eye}
        accessibilityRole="button"
        accessibilityLabel={shown ? 'Hide password' : 'Show password'}
        onPressIn={() => {
          onTouch?.();
          pressed.set(withTiming(1, { duration: PRESS }));
        }}
        onPressOut={() => pressed.set(withTiming(0, { duration: PRESS * 2 }))}
        onPress={onToggle}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  input: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: EYE_INSET * 2 - 16,
    height: FIELD,
    paddingLeft: DOTS_FROM,
    color: 'transparent',
    fontSize: FONT_SIZE,
  },
  caret: {
    position: 'absolute',
    top: FIELD / 2 - 11,
    width: 2,
    height: 22,
    borderRadius: 1,
  },
  eye: {
    position: 'absolute',
    right: 0,
    top: 0,
    width: EYE_INSET * 2 - 16,
    height: FIELD,
  },
});
