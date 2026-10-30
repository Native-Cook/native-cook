import { BlurMask, Canvas, Circle, Group, Image, LinearGradient, Picture, Rect, RoundedRect, Skia, useImage, vec, type SkCanvas } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A voice assistant with a face (name and words in Instrument Serif italic, the room's gradient
// warming with the mood): a smooth sculpted mask glowing in the dark, coloured by a
// gradient that keeps the sculpture's shading (blend mode "color"), with two lit eye slits.
// Holding the mic, you talk: a waveform dances under the mask and the ring round the mic
// pulses. Let go and Mira answers: the mask warms from calm blue-violet to red and amber, its
// mouth moves with the words and the reply shows below, then it cools back to calm.
//
// Mask artwork: assets/mask.png (generated, prompt in assets/PROMPTS.md), 2:3, drawn 236 pt wide.
// Measured on it: eyes at (0.31, 0.488) and (0.69, 0.488), mouth at (0.49, 0.712).
//
// autoPlay timeline (LOOP = 8 s), t in seconds:
//   0.0-1.0  rest: calm, mouth shut, flat waveform, "Hold to talk"
//   1.0-3.4  listening (fades over 0.3 s at both ends): waveform, mic ring pulse, "Listening…"
//   3.4-4.0  mood 0 -> 1 (ease-in-out cubic): the mask warms, the reply fades in
//   3.8-6.4  speaking (fades 0.3 s): the mouth opens with the words, waveform in amber
//   6.4-7.4  mood 1 -> 0, the reply fades out
//   7.4-8.0  rest, identical to t = 0
// Holding anywhere is listening; releasing jumps to t = 3.4, Mira's answer.

/** Seconds. */
export const LOOP = 8;
const REPLY_AT = 3.4;
const FACE_W = 236;
const FACE_H = FACE_W * 1.5;
const BARS = 28;

const MASK_IMAGE = require('./assets/mask.png');
const SERIF = require('./assets/InstrumentSerif-Italic.ttf');
const SERIF_FAMILY = 'MoodMaskVoice-InstrumentSerifItalic';

const C = {
  background: '#050508',
  text: '#F4F2FF',
  dim: '#7D7A8F',
  calmTop: '#4F5BFF',
  calmBottom: '#B07BFF',
  warmTop: '#FF3B2E',
  warmBottom: '#FFB25C',
  mouth: '#14060F',
  mic: '#16141F',
};

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `rgb(${r}, ${g}, ${bl})`;
}

/** Mood (0 calm, 1 warm), listening and speaking levels, `t` seconds into the loop. */
function voiceAt(t: number) {
  'worklet';
  const mood = easeInOutCubic(seg(t, 3.4, 4.0)) * (1 - easeInOutCubic(seg(t, 6.4, 7.4)));
  const listen = smooth(seg(t, 1.0, 1.3)) * (1 - smooth(seg(t, 3.1, 3.4)));
  const speak = smooth(seg(t, 3.8, 4.1)) * (1 - smooth(seg(t, 6.1, 6.4)));
  return { mood, listen, speak };
}

function drawWave(c: SkCanvas, cx: number, y: number, t: number, listen: number, speak: number, mood: number) {
  'worklet';
  const level = Math.max(listen, speak);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(mixColor('#C9C3FF', C.warmBottom, mood)));
  const gap = 8;
  const left = cx - ((BARS - 1) * gap) / 2;
  for (let k = 0; k < BARS; k++) {
    const edge = Math.sin((Math.PI * (k + 0.5)) / BARS);
    const wave = Math.abs(Math.sin(t * 6.2 + k * 0.7)) * (0.5 + 0.5 * Math.sin(k * 1.3 + t * 3.1));
    const h = 3 + 34 * level * edge * wave;
    paint.setAlphaf(0.35 + 0.55 * level);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(left + k * gap - 1.5, y - h / 2, 3, h), 1.5, 1.5), paint);
  }
}

type Props = {
  name?: string;
  idle?: string;
  listening?: string;
  reply?: string;
  /** Talks with Mira by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function MoodMaskVoice({
  name = 'Mira',
  idle = 'Hold to talk',
  listening = 'Listening…',
  reply = 'That’s wonderful news. Tell me everything!',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const mask = useImage(MASK_IMAGE);
  const [fontsLoaded] = useFonts({ [SERIF_FAMILY]: SERIF });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const faceX = cx - FACE_W / 2;
  const faceY = insets.top + 64;
  const waveY = faceY + FACE_H + 40;
  const micY = height - insets.bottom - 70;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const osc = useSharedValue(0);
  const mood = useSharedValue(0);
  const listen = useSharedValue(0);
  const speak = useSharedValue(0);
  const touching = useSharedValue(false);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      osc.set(now);
      mood.set(Math.max(0, mood.get() - dt * 2));
      listen.set(Math.min(1, listen.get() + dt * 4));
      speak.set(Math.max(0, speak.get() - dt * 4));
      return;
    }
    if (!autoPlay) return;
    const t = (now - origin.get()) % LOOP;
    const v = voiceAt(t);
    osc.set(t);
    mood.set(v.mood);
    listen.set(v.listen);
    speak.set(v.speak);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const top = useDerivedValue(() => mixColor(C.calmTop, C.warmTop, mood.get()));
  const bottom = useDerivedValue(() => mixColor(C.calmBottom, C.warmBottom, mood.get()));
  const colors = useDerivedValue(() => [top.get(), bottom.get()]);
  const glowColor = useDerivedValue(() => mixColor(C.calmBottom, C.warmTop, mood.get()));
  // The room itself warms a little with the mood.
  const sky = useDerivedValue(() => [mixColor('#0B0920', '#1E0A0C', mood.get()), C.background]);
  const glowOpacity = useDerivedValue(() => 0.32 + 0.18 * mood.get() + 0.1 * speak.get());
  // The mask leans in a little while it listens and talks.
  const faceTransform = useDerivedValue(() => {
    const s = 1 + 0.02 * listen.get() + 0.015 * speak.get() * Math.sin(osc.get() * 5);
    return [{ translateX: cx }, { translateY: faceY + FACE_H / 2 }, { scale: s }, { translateX: -cx }, { translateY: -(faceY + FACE_H / 2) }];
  });
  const mouthOpen = useDerivedValue(() => {
    const t = osc.get();
    return speak.get() * (0.25 + 0.75 * Math.abs(Math.sin(t * 9)) * (0.6 + 0.4 * Math.sin(t * 3.1)));
  });
  const mouthH = useDerivedValue(() => 1 + 16 * mouthOpen.get());
  const mouthW = useDerivedValue(() => 30 + 8 * mouthOpen.get());
  const mouthX = useDerivedValue(() => faceX + FACE_W * 0.49 - mouthW.get() / 2);
  const mouthY = useDerivedValue(() => faceY + FACE_H * 0.712 - mouthH.get() / 2);
  const mouthOpacity = useDerivedValue(() => smooth(mouthOpen.get() * 6));
  const eyeOpacity = useDerivedValue(() => 0.75 + 0.25 * Math.max(listen.get(), speak.get()));
  const ring = useDerivedValue(() => 40 + 16 * listen.get() * (0.5 + 0.5 * Math.sin(osc.get() * 7)));
  const ringOpacity = useDerivedValue(() => 0.12 + 0.3 * listen.get());

  const wave = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawWave(canvas, cx, waveY, osc.get(), listen.get(), speak.get(), mood.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, waveY]);

  const idleStyle = useAnimatedStyle(() => {
    const r = smooth((mood.get() - 0.3) / 0.4);
    return { opacity: clamp01(1 - Math.max(listen.get(), r)) };
  });
  const listenStyle = useAnimatedStyle(() => ({ opacity: listen.get() }));
  const replyStyle = useAnimatedStyle(() => ({ opacity: smooth((mood.get() - 0.3) / 0.4) }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const hold = Gesture.LongPress()
    .enabled(!reduced)
    .minDuration(0)
    .maxDistance(2000)
    .onBegin(() => {
      touching.set(true);
      scheduleOnRN(haptic);
    })
    .onFinalize(() => {
      touching.set(false);
      // Mira answers: the loop carries on from the reply.
      origin.set(elapsed.get() - REPLY_AT);
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  const eyeW = 34;
  const eyeY = faceY + FACE_H * 0.488 - 2.5;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={hold}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel={`${name}, voice assistant. Hold to talk.`}>
            <Rect x={0} y={0} width={width} height={height}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={sky} />
            </Rect>
            <Group opacity={glowOpacity}>
              <Circle cx={cx} cy={faceY + FACE_H * 0.5} r={150} color={glowColor}>
                <BlurMask blur={70} style="normal" />
              </Circle>
            </Group>
            <Group transform={faceTransform}>
              {mask ? (
                <Group layer>
                  <Image image={mask} x={faceX} y={faceY} width={FACE_W} height={FACE_H} fit="fill" />
                  <Rect x={faceX} y={faceY} width={FACE_W} height={FACE_H} blendMode="color">
                    <LinearGradient start={vec(cx, faceY)} end={vec(cx, faceY + FACE_H)} colors={colors} />
                  </Rect>
                  <Image image={mask} x={faceX} y={faceY} width={FACE_W} height={FACE_H} fit="fill" blendMode="dstIn" />
                </Group>
              ) : null}
              <Group opacity={eyeOpacity}>
                <RoundedRect x={faceX + FACE_W * 0.31 - eyeW / 2} y={eyeY} width={eyeW} height={5} r={2.5} color="#E9E4FF">
                  <BlurMask blur={4} style="solid" />
                </RoundedRect>
                <RoundedRect x={faceX + FACE_W * 0.69 - eyeW / 2} y={eyeY} width={eyeW} height={5} r={2.5} color="#E9E4FF">
                  <BlurMask blur={4} style="solid" />
                </RoundedRect>
              </Group>
              <Group opacity={mouthOpacity}>
                <RoundedRect x={mouthX} y={mouthY} width={mouthW} height={mouthH} r={8} color={C.mouth}>
                  <BlurMask blur={2} style="normal" />
                </RoundedRect>
              </Group>
            </Group>
            <Picture picture={wave} />
            <Circle cx={cx} cy={micY} r={ring} color="#FFFFFF" opacity={ringOpacity} style="stroke" strokeWidth={1.5} />
            <Circle cx={cx} cy={micY} r={34} color={C.mic} />
            <RoundedRect x={cx - 6} y={micY - 14} width={12} height={20} r={6} color={C.text} />
            <Rect x={cx - 1} y={micY + 9} width={2} height={6} color={C.text} />
          </Canvas>

          <View style={[styles.header, { top: insets.top + 12 }]} pointerEvents="none">
            <View style={styles.status} />
            <Text style={styles.name}>{name}</Text>
          </View>

          <View style={[styles.captions, { top: waveY + 36 }]} pointerEvents="none">
            <Animated.Text style={[styles.caption, styles.idle, idleStyle]}>{idle}</Animated.Text>
            <Animated.Text style={[styles.caption, listenStyle]}>{listening}</Animated.Text>
            <Animated.Text style={[styles.caption, replyStyle]}>{reply}</Animated.Text>
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  header: { position: 'absolute', left: 0, right: 0, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  status: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#7CF0B0', marginRight: 8 },
  name: { fontFamily: SERIF_FAMILY, fontSize: 28, lineHeight: 32, color: C.text },
  captions: { position: 'absolute', left: 28, right: 28, height: 76, alignItems: 'center' },
  caption: { position: 'absolute', left: 0, right: 0, fontFamily: SERIF_FAMILY, fontSize: 30, lineHeight: 36, color: C.text, textAlign: 'center' },
  idle: { color: C.dim },
});
