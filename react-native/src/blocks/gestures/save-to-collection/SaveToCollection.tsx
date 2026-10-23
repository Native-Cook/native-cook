/** A pile of saved-for-later cards on black: carry one onto the bookmark, tap one, or tap the bookmark to save them all. */
import { Image } from 'expo-image';
import { useMemo } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';

import { Bookmark } from './Bookmark';
import { CardTile } from './CardTile';
import { Flight } from './Flight';
import { CARDS, DESIGN } from './gallery';
import { Stream } from './Stream';
import { COLOR, GLOW, LAYOUT } from './theme';
import { useSave } from './use-save';

export type SaveToCollectionProps = { autoPlay?: boolean };

export function SaveToCollection({ autoPlay = false }: SaveToCollectionProps) {
  const { width, height } = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  // The pile is measured on a 402 × 874 screen; scale it by width and keep it centred vertically.
  const k = width / DESIGN.width;
  const dy = (height - DESIGN.height * k) / 2;
  const rects = useMemo(() => CARDS.map((c) => ({ x: c.x * k, y: c.y * k + dy, w: c.w * k, h: c.h * k })), [dy, k]);
  const bookmark = useMemo(() => ({ x: width / 2, y: LAYOUT.bookmarkY * k + dy }), [dy, k, width]);
  const {
    gesture, touchStart, touchEnd, onLand, onFlightStart, visible, flights, dragCard, saved, deal, pileCount, rush, values,
  } = useSave({ autoPlay, reduceMotion, rects, bookmark });
  const { press, pressCard, lifted, dragX, dragY, dragScale, dragOn, open, bounce, rise, ring, tilt, clock, ghostX, ghostY, ghostO, ghostPress } = values;

  const drag = dragCard >= 0 ? rects[dragCard] : null;
  const dragStyle = useAnimatedStyle(() => ({
    opacity: dragOn.get(),
    transform: [
      { translateX: dragX.get() - (drag?.w ?? 0) / 2 },
      { translateY: dragY.get() - (drag?.h ?? 0) / 2 },
      { rotate: `${tilt.get()}deg` },
      { scale: dragScale.get() },
    ],
  }));
  const underGlow = useAnimatedStyle(() => ({
    opacity: dragOn.get() * GLOW.underOpacity,
    transform: [
      { translateX: dragX.get() - (drag?.w ?? 0) / 2 },
      { translateY: dragY.get() - (drag?.h ?? 0) / 2 + GLOW.underDrop },
      { scale: dragScale.get() * 1.05 },
    ],
  }));
  const ghost = useAnimatedStyle(() => ({
    opacity: ghostO.get(),
    transform: [
      { translateX: ghostX.get() - LAYOUT.finger / 2 },
      { translateY: ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 - 0.18 * ghostPress.get() },
    ],
  }));

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            {visible.map((i, order) => (
              <View
                key={`${CARDS[i].id}-${deal}`}
                style={StyleSheet.absoluteFill}
                pointerEvents="box-none"
                accessible
                accessibilityRole="button"
                accessibilityLabel={`${CARDS[i].label}. Double tap to save`}
              >
                <CardTile card={CARDS[i]} index={i} rect={rects[i]} order={order} lifted={lifted} press={press} pressCard={pressCard} clock={clock} reduceMotion={reduceMotion} />
              </View>
            ))}
          </View>
        </GestureDetector>
        {rush > 0 && (
          <View style={StyleSheet.absoluteFill} pointerEvents="none">
            <Stream key={rush} clock={clock} to={bookmark} riseY={LAYOUT.bookmarkRiseY * k + dy} rise={rise} k={k} />
          </View>
        )}
        <Bookmark press={press} pressCard={pressCard} bounce={bounce} open={open} rise={rise} ring={ring} pileCount={pileCount} centre={bookmark} screenMidY={LAYOUT.bookmarkRiseY * k + dy} saved={saved} reduceMotion={reduceMotion} />
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          {flights.map((f) => (
            <Flight key={f.id} id={f.id} card={f.card} onStart={onFlightStart} art={CARDS[f.card].art} from={f.from} to={bookmark} delay={f.delay} duration={f.duration} onLand={onLand} clock={clock} />
          ))}
          {drag && (
            // A hue glow under the lifted card: its own art, blurred, a little below it.
            <Animated.View style={[styles.drag, styles.underGlow, { width: drag.w, height: drag.h }, underGlow]}>
              <Image source={CARDS[dragCard].art} style={StyleSheet.absoluteFill} contentFit="cover" blurRadius={GLOW.under} transition={0} />
            </Animated.View>
          )}
          {drag && (
            <Animated.View style={[styles.drag, { width: drag.w, height: drag.h }, dragStyle]}>
              <Image source={CARDS[dragCard].art} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
            </Animated.View>
          )}
        </View>
        {autoPlay && <Animated.View style={[styles.ghost, ghost]} pointerEvents="none" />}
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.background },
  underGlow: { borderRadius: 40, overflow: 'hidden' },
  drag: {
    position: 'absolute', left: 0, top: 0, borderRadius: LAYOUT.cardRadius, overflow: 'hidden',
    shadowColor: '#000000', shadowOpacity: 0.5, shadowRadius: 24, shadowOffset: { width: 0, height: 16 },
  },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
