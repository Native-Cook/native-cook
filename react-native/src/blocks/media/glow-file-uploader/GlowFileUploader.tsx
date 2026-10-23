import { Canvas, Paragraph, Skia, TextAlign, TileMode, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  interpolateColor,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DropZone, ZONE_HEIGHT } from './DropZone';
import { FileRow } from './FileRow';
import { CHECK, GLYPH_BOX } from './glyphs';
import {
  BUTTON_PRESS,
  DISABLED_ALPHA,
  type Events,
  FILES,
  MAX_ROWS,
  SETTLE,
  USER_HOLD,
  activeCount,
  addRow,
  autoEvents,
  busy,
  cancelOn,
  continueOn,
  leaveAll,
  leaveFor,
  pressScale,
  ready,
  restEvents,
  dropPresses,
  retryCancel,
  retryDown,
  retryUp,
  rowStatus,
  sendAll,
  sheen,
  tidy,
  uploaded,
} from './motion';

export { LOOP } from './motion';

type Props = {
  /** Adds two files, retries the one that fails and sends them, by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Continue was pressed with every file uploaded. */
  onContinue?: (names: string[]) => void;
};

const BACKGROUND = '#0E1013';
const SIDE = 20;
// The title: two lines filled with one gradient, dim at the top and light at the bottom.
const TITLE = 'Upload a file\nto start';
const TITLE_TOP = 28;
const TITLE_SIZE = 50;
const TITLE_LINE = 54;
const TITLE_SPACING = -1.75;
const TITLE_COLORS = ['#4C4F55', '#D3D4D6'];
const FONT = Platform.select({
  ios: ['.AppleSystemUIFont'],
  default: ['sans-serif'],
});
/** Gaps under the title and under the zone. */
const TITLE_GAP = 16;
const SECTION_GAP = 24;
// The buttons at the bottom.
const BUTTON_HEIGHT = 48;
const BUTTON_RADIUS = 22;
const LABEL_SIZE = 12.5;
const BUTTON_GAP = 8;
/** The buttons' bottom sits on the bottom safe area's edge, or this far above the screen's bottom without one. */
const BUTTON_FLOOR = 16;
const CANCEL_WIDTH = 93;
const CANCEL_FILL = '#191C20';
const CANCEL_BORDER = '#3A3D42';
const CANCEL_INK = '#E8E9EA';
const CONTINUE_FILLS = ['#202327', '#2A2E33'];
const CONTINUE_INKS = ['#5E6166', '#F4F4F5'];
const GREEN = '#46C35A';
// The sheen that crosses Continue once when it becomes enabled.
const SHEEN_WIDTH = 60;
const SHEEN_TILT = '20deg';
const SHEEN_COLORS = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.12)', 'rgba(255,255,255,0)'] as const;
/** The check before "Uploaded". */
const LABEL_CHECK = 15;
const LABEL_CHECK_STROKE = 2;

type Shown = { id: number; file: number; status: string };
type Mirror = {
  select: boolean;
  cancel: boolean;
  ready: boolean;
  rows: Shown[];
};
const EMPTY: Mirror = { select: true, cancel: false, ready: false, rows: [] };

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function notify(type: Haptics.NotificationFeedbackType) {
  if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(type).catch(() => {});
}

function titleParagraph(width: number) {
  const paint = Skia.Paint();
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(0, 0),
      vec(0, TITLE_LINE * 2),
      TITLE_COLORS.map((color) => Skia.Color(color)),
      null,
      TileMode.Clamp,
    ),
  );
  const builder = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Left });
  builder.pushStyle(
    {
      color: Skia.Color('#ffffff'),
      fontFamilies: FONT,
      fontSize: TITLE_SIZE,
      fontStyle: { weight: 500 },
      heightMultiplier: TITLE_LINE / TITLE_SIZE,
      halfLeading: true,
      letterSpacing: TITLE_SPACING,
    },
    paint,
  );
  builder.addText(TITLE);
  const paragraph = builder.build();
  paragraph.layout(width);
  return paragraph;
}

/** Turns the mirror's key back into what React shows. */
function readMirror(key: string): Mirror {
  const [flags, list] = key.split('|');
  return {
    select: flags[0] === '1',
    cancel: flags[1] === '1',
    ready: flags[2] === '1',
    rows: list
      ? list.split(';').map((item) => {
          const [id, file, status] = item.split(':');
          return { id: Number(id), file: Number(file), status };
        })
      : [],
  };
}

/**
 * An upload screen on near-black: a two-line title, a drop zone with a soft white glow inside its rim,
 * a list of files uploading with a ring each (done turns green, failed shows a red pill and Retry),
 * and Cancel and Continue at the bottom; Continue opens once every file is up.
 */
export function GlowFileUploader({ autoPlay = false, onContinue }: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{
    width: number;
    height: number;
  } | null>(null);
  const { width } = laidOut ?? window;
  const content = width - SIDE * 2;

  // What the screen shows, mirrored from the clock for touches and screen readers.
  const [view, setView] = useState<Mirror>(EMPTY);
  const statuses = useRef(new Map<number, string>());

  // The clock in seconds and when it started (the ambient effects run from there); the events it
  // is drawn from; the autoPlay's loop start (-1 while a touch holds it off) and the last touch.
  const clock = useSharedValue(0);
  const origin = useSharedValue(-1);
  const events = useSharedValue<Events>(restEvents());
  const loopStart = useSharedValue(-1);
  const touchedAt = useSharedValue(-1e6);
  const mirrored = useSharedValue('');

  const title = useMemo(() => titleParagraph(content), [content]);

  const report = (key: string, auto: boolean) => {
    const next = readMirror(key);
    if (!auto) {
      for (const row of next.rows) {
        const before = statuses.current.get(row.id);
        if (before === 'u' && row.status === 'd') notify(Haptics.NotificationFeedbackType.Success);
        if (before === 'u' && row.status === 'f') notify(Haptics.NotificationFeedbackType.Error);
      }
    }
    statuses.current = new Map(next.rows.map((row) => [row.id, row.status]));
    setView(next);
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (origin.get() < 0) origin.set(now);
    if (autoPlay) {
      let start = loopStart.get();
      if (start < 0 && now - touchedAt.get() >= USER_HOLD) {
        const e = events.get();
        // Back to rest the way Cancel goes there, then the loop starts again.
        if (!busy(e, now)) {
          if (activeCount(e.rows, now) > 0) events.set(leaveAll(e, now));
          else if (e.rows.length === 0 && now >= e.clearAt + SETTLE) {
            loopStart.set(now);
            start = now;
          }
        }
      }
      if (start >= 0) events.set(autoEvents(now, start));
    }
    if (loopStart.get() < 0) {
      const e = events.get();
      const next = tidy(e, now, still);
      if (next !== e) events.set(next);
    }

    const e = events.get();
    const active = activeCount(e.rows, now);
    let key = `${active < MAX_ROWS ? 1 : 0}${active > 0 ? 1 : 0}${ready(e, now) ? 1 : 0}|`;
    let first = true;
    for (const row of e.rows) {
      if (now >= row.leaveAt + row.leaveDelay + leaveFor(still)) continue;
      key += `${first ? '' : ';'}${row.id}:${row.file}:${rowStatus(row, now)}`;
      first = false;
    }
    if (key !== mirrored.get()) {
      mirrored.set(key);
      scheduleOnRN(report, key, loopStart.get() >= 0);
    }
  });

  /**
   * A touch takes over from the autoPlay, keeping what is on screen but not its press in progress.
   * (The component's worklets hold no inline callbacks: React Compiler would hoist them out of the
   * worklet as plain functions, so list work lives in motion.ts.)
   */
  const takeOver = () => {
    'worklet';
    touchedAt.set(clock.get());
    if (loopStart.get() >= 0) events.set(dropPresses(events.get()));
    loopStart.set(-1);
  };
  /** Select files (0 down, 1 up, 2 pressed; the zone only sends 2). */
  const select = (phase: number) => {
    'worklet';
    takeOver();
    const e = events.get();
    const now = clock.get();
    const room = activeCount(e.rows, now) < MAX_ROWS;
    if (phase === 0 && room) events.set({ ...e, pillDown: now });
    else if (phase === 1 && e.pillDown > e.pillUp) events.set({ ...e, pillUp: now });
    else if (phase === 2) events.set(addRow(e, now));
  };
  /** Retry on a row (0 down, 1 let go without a press, 2 pressed). */
  const retry = (id: number, phase: number) => {
    'worklet';
    takeOver();
    const e = events.get();
    const now = clock.get();
    if (phase === 0) events.set(retryDown(e, id, now));
    else if (phase === 2) events.set(retryUp(e, id, now));
    else events.set(retryCancel(e, id));
  };
  const cancel = (phase: number) => {
    'worklet';
    takeOver();
    const e = events.get();
    const now = clock.get();
    const any = activeCount(e.rows, now) > 0;
    if (phase === 0 && any) events.set({ ...e, cancelDown: now });
    else if (phase === 1 && e.cancelDown > e.cancelUp) events.set({ ...e, cancelUp: now });
    else if (phase === 2 && any) events.set(leaveAll(e, now));
  };
  const proceed = (phase: number) => {
    'worklet';
    takeOver();
    const e = events.get();
    const now = clock.get();
    if (phase === 0 && ready(e, now)) events.set({ ...e, continueDown: now });
    else if (phase === 1 && e.continueDown > e.continueUp) events.set({ ...e, continueUp: now });
    else if (phase === 2) events.set(sendAll(e, now));
  };

  const cancelStyle = useAnimatedStyle(() => {
    const e = events.get();
    return {
      transform: [
        {
          scale: pressScale(e.cancelDown, e.cancelUp, clock.get(), BUTTON_PRESS),
        },
      ],
    };
  });
  const cancelDim = useAnimatedStyle(() => ({
    opacity: DISABLED_ALPHA + (1 - DISABLED_ALPHA) * cancelOn(events.get(), clock.get()),
  }));
  const continueStyle = useAnimatedStyle(() => {
    const e = events.get();
    const now = clock.get();
    return {
      backgroundColor: interpolateColor(continueOn(e, now), [0, 1], CONTINUE_FILLS),
      transform: [{ scale: pressScale(e.continueDown, e.continueUp, now, BUTTON_PRESS) }],
    };
  });
  const continueLabel = useAnimatedStyle(() => {
    const e = events.get();
    const now = clock.get();
    return {
      color: interpolateColor(continueOn(e, now), [0, 1], CONTINUE_INKS),
      opacity: 1 - uploaded(e, now),
    };
  });
  const uploadedLabel = useAnimatedStyle(() => ({
    opacity: uploaded(events.get(), clock.get()),
  }));
  const continueWidth = content - CANCEL_WIDTH - BUTTON_GAP;
  const sheenStyle = useAnimatedStyle(() => {
    const k = sheen(events.get(), clock.get(), still);
    if (k < 0) return { opacity: 0 };
    return {
      opacity: 1,
      transform: [
        {
          translateX: -SHEEN_WIDTH * 2 + (continueWidth + SHEEN_WIDTH * 3) * k,
        },
        { rotate: SHEEN_TILT },
      ],
    };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const titleTop = insets.top + TITLE_TOP;
  const zoneTop = titleTop + TITLE_LINE * 2 + TITLE_GAP;
  const listTop = zoneTop + ZONE_HEIGHT + SECTION_GAP;
  const buttonsBottom = insets.bottom > 0 ? insets.bottom : BUTTON_FLOOR;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View
        style={[styles.title, { top: titleTop, left: SIDE, width: content }]}
        accessible
        accessibilityRole="header"
        accessibilityLabel="Upload a file to start"
      >
        <Canvas style={{ width: content, height: TITLE_LINE * 2 }}>
          <Paragraph paragraph={title} x={0} y={0} width={content} />
        </Canvas>
      </View>

      <View style={{ position: 'absolute', left: SIDE, top: zoneTop }}>
        <DropZone
          width={content}
          clock={clock}
          origin={origin}
          events={events}
          still={still}
          full={!view.select}
          onPillIn={() => {
            if (view.select) tap();
            scheduleOnUI(select, 0);
          }}
          onPillOut={() => scheduleOnUI(select, 1)}
          onSelect={() => {
            if (!view.select) return;
            scheduleOnUI(select, 2);
          }}
        />
      </View>

      <View
        style={{
          position: 'absolute',
          left: SIDE,
          top: listTop,
          width: content,
        }}
      >
        {view.rows.map((row) => (
          <FileRow
            key={row.id}
            id={row.id}
            name={FILES[row.file].name}
            width={content}
            clock={clock}
            events={events}
            still={still}
            failed={row.status === 'f'}
            status={row.status}
            onRetryIn={(id) => {
              tap();
              scheduleOnUI(retry, id, 0);
            }}
            onRetryOut={(id) => scheduleOnUI(retry, id, 1)}
            onRetry={(id) => scheduleOnUI(retry, id, 2)}
          />
        ))}
      </View>

      <View style={[styles.buttons, { left: SIDE, width: content, bottom: buttonsBottom }]}>
        <Animated.View style={[styles.cancel, cancelStyle]}>
          <Pressable
            style={styles.face}
            disabled={!view.cancel}
            onPressIn={() => {
              tap();
              scheduleOnUI(cancel, 0);
            }}
            onPressOut={() => scheduleOnUI(cancel, 1)}
            onPress={() => scheduleOnUI(cancel, 2)}
            accessibilityRole="button"
            accessibilityState={{ disabled: !view.cancel }}
          >
            <Animated.Text style={[styles.cancelLabel, cancelDim]}>Cancel</Animated.Text>
          </Pressable>
        </Animated.View>
        <Animated.View style={[styles.continue, { width: continueWidth }, continueStyle]}>
          <Pressable
            style={styles.face}
            disabled={!view.ready}
            onPressIn={() => {
              tap();
              scheduleOnUI(proceed, 0);
            }}
            onPressOut={() => scheduleOnUI(proceed, 1)}
            onPress={() => {
              scheduleOnUI(proceed, 2);
              onContinue?.(view.rows.map((row) => FILES[row.file].name));
            }}
            accessibilityRole="button"
            accessibilityState={{ disabled: !view.ready }}
          >
            <Animated.View style={[styles.sheen, sheenStyle]} pointerEvents="none">
              <LinearGradient colors={SHEEN_COLORS} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
            </Animated.View>
            <Animated.Text style={[styles.continueLabel, continueLabel]}>Continue</Animated.Text>
            <Animated.View style={[styles.uploaded, uploadedLabel]} pointerEvents="none">
              <Svg width={LABEL_CHECK} height={LABEL_CHECK} viewBox={`0 0 ${GLYPH_BOX} ${GLYPH_BOX}`}>
                <SvgPath
                  d={CHECK}
                  stroke={GREEN}
                  strokeWidth={(LABEL_CHECK_STROKE * GLYPH_BOX) / LABEL_CHECK}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  fill="none"
                />
              </Svg>
              <Text style={styles.uploadedText}>Uploaded</Text>
            </Animated.View>
          </Pressable>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: BACKGROUND,
  },
  title: {
    position: 'absolute',
  },
  buttons: {
    position: 'absolute',
    height: BUTTON_HEIGHT,
    flexDirection: 'row',
    gap: BUTTON_GAP,
  },
  cancel: {
    width: CANCEL_WIDTH,
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_RADIUS,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: CANCEL_BORDER,
    backgroundColor: CANCEL_FILL,
  },
  continue: {
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  face: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelLabel: {
    color: CANCEL_INK,
    fontSize: LABEL_SIZE,
    includeFontPadding: false,
  },
  continueLabel: {
    fontSize: LABEL_SIZE,
    fontWeight: '500',
    includeFontPadding: false,
  },
  sheen: {
    position: 'absolute',
    left: 0,
    top: -BUTTON_HEIGHT / 2,
    width: SHEEN_WIDTH,
    height: BUTTON_HEIGHT * 2,
  },
  uploaded: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  uploadedText: {
    color: '#ffffff',
    fontSize: LABEL_SIZE,
    fontWeight: '500',
    includeFontPadding: false,
  },
});
