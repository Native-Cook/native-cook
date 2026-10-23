import { Canvas, Group, Paint, type SkImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  type AccessibilityActionEvent,
  type GestureResponderEvent,
  type LayoutChangeEvent,
  Pressable,
  StyleSheet,
  View,
  useWindowDimensions,
} from 'react-native';
import {
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { HEART_INDEX, Icon } from './Icon';
import { SCREEN, Scene, cachedPhoto, headingText, loadPhoto } from './Parts';
import {
  COUNT,
  DRAW_ORDER,
  type Events,
  PRELOAD_WAIT,
  SETTLE,
  USER_HOLD,
  anyPressed,
  autoEvents,
  fadeAmount,
  inCard,
  lastEvent,
  layoutFor,
  liked,
  pressed,
  progressAt,
  restEvents,
  restProgress,
  toggled,
} from './motion';

export { LOOP } from './motion';

function photo(id: string) {
  // Sized for the largest it is drawn at (132 × 176 open) at 3×.
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=396&h=528&q=80`;
}

/** Eight photos in grid order: row by row, skipping the middle cell. */
export const DEFAULT_PHOTOS = [
  photo('1433086966358-54859d0ed716'),
  photo('1441974231531-c6227db76b6e'),
  photo('1454496522488-7a8e488e8606'),
  photo('1464822759023-fed622ff2c3b'),
  photo('1472214103451-9374bd1c798e'),
  photo('1506905925346-21bda4d32df4'),
  photo('1526772662000-3f88f10405ff'),
  photo('1531366936337-7c912a4589a7'),
];

const ICON_LABELS = ['Share', 'Like', 'Info', 'Delete'];

type Props = {
  name?: string;
  year?: string;
  /** Eight photo addresses, in grid order (row by row, skipping the middle cell). */
  photos?: string[];
  /** Folds, likes, unlikes and unfolds by itself every LOOP seconds. */
  autoPlay?: boolean;
  onFoldChange?: (folded: boolean) => void;
  onLike?: (liked: boolean) => void;
};

function haptic(strong: boolean) {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * An album screen. Open, its photos lie scattered and tilted over a pale blue and mint gradient that
 * fills the screen, under the album's name; a tap folds it: the gradient shrinks into a square card,
 * the photos fly into a 3×3 grid on it around the name, and a row of icons (share, like, info, delete)
 * rises under it. A tap on the card unfolds it again.
 */
export function ScatterGridAlbum({
  name = 'Highlands',
  year = '2025',
  photos = DEFAULT_PHOTOS,
  autoPlay = false,
  onFoldChange,
  onLike,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;

  const text = useMemo(() => headingText(name, year), [name, year]);
  const layout = useMemo(
    () => layoutFor(width, height, insets.top, insets.bottom, text.nameWidth),
    [width, height, insets.top, insets.bottom, text.nameWidth],
  );
  const ranks = layout.ranks;

  const addresses = photos.slice(0, COUNT);
  const key = addresses.join('|');
  const [images, setImages] = useState<(SkImage | null)[]>(() => addresses.map(cachedPhoto));

  // What the screen shows, mirrored from the clock for touches and screen readers.
  const [view, setView] = useState({ folded: false, liked: false });
  const reported = useRef({ folded: false, liked: false });

  // `tick` follows every frame; `clock` (what everything is drawn from) only while something moves,
  // so a still screen stops drawing. The drift counts from `origin`. The autoPlay's loop starts at
  // `loopStart` (-1 while a touch holds it off), once `ready` (the photos are in, or the wait is over).
  const tick = useSharedValue(0);
  const clock = useSharedValue(0);
  const origin = useSharedValue(-1);
  const events = useSharedValue<Events>(restEvents());
  const loopStart = useSharedValue(-1);
  const touchedAt = useSharedValue(-1);
  const ready = useSharedValue(false);
  const mirrored = useSharedValue(-1);

  useEffect(() => {
    let live = true;
    const list = key.split('|');
    const wait = setTimeout(() => ready.set(true), PRELOAD_WAIT * 1000);
    Promise.all(
      list.map((address, i) =>
        loadPhoto(address).then((image) => {
          if (live) setImages((before) => before.map((old, j) => (j === i ? image : old)));
        }),
      ),
    ).then(() => {
      if (live) ready.set(true);
    });
    return () => {
      live = false;
      clearTimeout(wait);
    };
  }, [key, ready]);

  const report = (folded: boolean, heartOn: boolean, auto: boolean) => {
    const before = reported.current;
    reported.current = { folded, liked: heartOn };
    setView({ folded, liked: heartOn });
    if (before.folded !== folded) onFoldChange?.(folded);
    if (before.liked !== heartOn && !auto) onLike?.(heartOn);
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    tick.set(now);
    if (origin.get() < 0) origin.set(now);
    let start = loopStart.get();
    if (autoPlay && start < 0) {
      const hand = events.get();
      if (touchedAt.get() < 0) {
        // The first loop waits for the photos, so a recording never shows a grey card.
        if (ready.get()) start = now;
      } else if (now - touchedAt.get() >= USER_HOLD && !anyPressed(hand)) {
        // Back to the open album the way a tap on the card goes there, then the loop starts again.
        if (hand.folded) events.set(toggled(hand, false, ranks, now, still));
        else if (now >= hand.at + SETTLE) start = now;
      }
      if (start >= 0) loopStart.set(start);
    }
    if (start >= 0) events.set(autoEvents(now, start));

    const e = events.get();
    const moving = start >= 0 || (!e.folded && !still) || anyPressed(e) || now < lastEvent(e) + SETTLE;
    if (moving || clock.get() === 0) clock.set(now);

    const shown = (e.folded ? 2 : 0) + (e.heartOn ? 1 : 0);
    if (shown !== mirrored.get()) {
      mirrored.set(shown);
      scheduleOnRN(report, e.folded, e.heartOn, start >= 0);
    }
  });

  /** The events on screen now, a touch taking over from the autoPlay (which starts again later). */
  const takeOver = () => {
    'worklet';
    const now = tick.get();
    const start = loopStart.get();
    if (start >= 0) events.set(autoEvents(now, start));
    loopStart.set(-1);
    touchedAt.set(now);
    clock.set(now);
  };
  const toggle = (fold: boolean) => {
    'worklet';
    takeOver();
    const e = events.get();
    if (e.folded === fold) return;
    scheduleOnRN(haptic, false);
    events.set(toggled(e, fold, ranks, tick.get(), still));
  };
  /** Open: a tap anywhere folds. Folded: only a tap on the card unfolds. */
  const tapAt = (x: number, y: number) => {
    'worklet';
    const start = loopStart.get();
    const folded = start >= 0 ? autoEvents(tick.get(), start).folded : events.get().folded;
    if (folded && !inCard(layout, x, y)) return;
    toggle(!folded);
  };
  const like = () => {
    'worklet';
    takeOver();
    const e = liked(events.get(), tick.get());
    if (e.heartOn) scheduleOnRN(haptic, true);
    events.set(e);
  };
  const press = (k: number, down: boolean) => {
    'worklet';
    takeOver();
    events.set(pressed(events.get(), k, down, tick.get()));
  };

  // The scene drawn by the clock; under reduced motion, the two layouts cross-faded (in one canvas:
  // the open one in a layer over the folded one).
  const live = useDerivedValue(() =>
    progressAt(events.get(), ranks, clock.get(), still ? -1 : clock.get() - Math.max(0, origin.get())),
  );
  const openScene = useDerivedValue(() => restProgress(0));
  const foldedScene = useDerivedValue(() => restProgress(1));
  const openFade = useDerivedValue(() => 1 - fadeAmount(events.get(), clock.get()));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };
  const onAlbumPress = (event: GestureResponderEvent) => {
    const { locationX, locationY } = event.nativeEvent;
    scheduleOnUI(tapAt, locationX, locationY);
  };
  const onAlbumAction = (event: AccessibilityActionEvent) => {
    if (event.nativeEvent.actionName === 'activate') scheduleOnUI(toggle, !view.folded);
  };

  const canvas = { width: layout.width, height: layout.height };
  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={onAlbumPress}
        accessibilityRole="button"
        accessibilityLabel={`${name}, ${year}`}
        accessibilityHint={view.folded ? 'Opens the album' : 'Folds the album into a grid'}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={onAlbumAction}
      >
        <Canvas style={[styles.canvas, canvas]}>
          {still ? (
            <>
              <Scene layout={layout} progress={foldedScene} text={text} images={images} order={DRAW_ORDER} />
              <Group layer={<Paint opacity={openFade} />}>
                <Scene layout={layout} progress={openScene} text={text} images={images} order={DRAW_ORDER} />
              </Group>
            </>
          ) : (
            <Scene layout={layout} progress={live} text={text} images={images} order={DRAW_ORDER} />
          )}
        </Canvas>
      </Pressable>
      {ICON_LABELS.map((label, k) => (
        <Icon
          key={label}
          index={k}
          label={label}
          center={layout.icons[k]}
          clock={clock}
          events={events}
          still={still}
          folded={view.folded}
          liked={view.liked}
          onPressIn={(index) => scheduleOnUI(press, index, true)}
          onPressOut={(index) => scheduleOnUI(press, index, false)}
          onPress={(index) => {
            if (index === HEART_INDEX) scheduleOnUI(like);
          }}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: SCREEN,
  },
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});
