import { Image } from 'expo-image';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type AnimatedRef, type SharedValue, interpolate, useAnimatedStyle, useSharedValue, withSpring,
} from 'react-native-reanimated';

import { Chips, Rating } from './CardDeck';
import { PRESS_SCALE } from './motion';
import type { Movie } from './movies';
import { CAROUSEL, COLORS, DETAIL, SANS } from './theme';

/** The centre card's white body widens into the sheet; its content rises in after it. */
export function DetailSheet({ movie, open, width, height, castRef, active }: {
  movie: Movie; open: SharedValue<number>; width: number; height: number;
  castRef: AnimatedRef<Animated.ScrollView>; active: boolean;
}) {
  const fromX = (width - CAROUSEL.cardW) / 2;
  const sheetStyle = useAnimatedStyle(() => {
    const o = open.get();
    return {
      opacity: o > 0.001 ? 1 : 0,
      left: interpolate(o, [0, 1], [fromX, DETAIL.sheetInset]),
      width: interpolate(o, [0, 1], [CAROUSEL.cardW, width - DETAIL.sheetInset * 2]),
      top: interpolate(o, [0, 1], [CAROUSEL.cardTop, DETAIL.sheetTop]),
      borderRadius: interpolate(o, [0, 1], [CAROUSEL.cardRadius, DETAIL.sheetRadius]),
    };
  });
  const head = useAnimatedStyle(() => {
    const p = interpolate(open.get(), [0.35, 0.85], [0, 1], 'clamp');
    return { opacity: p, transform: [{ translateY: (1 - p) * 18 }] };
  });
  const body = useAnimatedStyle(() => {
    const p = interpolate(open.get(), [0.55, 1], [0, 1], 'clamp');
    return { opacity: p, transform: [{ translateY: (1 - p) * 26 }] };
  });
  const inner = width - DETAIL.sheetInset * 2;
  return (
    <Animated.View style={[styles.sheet, { height: height - DETAIL.sheetTop + 40 }, sheetStyle]}
      pointerEvents={active ? 'box-none' : 'none'}
      accessibilityElementsHidden={!active}
      importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}>
      <Animated.View style={[{ width: inner }, head]}>
        <Text style={[styles.title, { top: DETAIL.titleY - 18 }]}>{movie.title}</Text>
        <View style={[styles.row, { top: DETAIL.chipsY - 10 }]}><Chips genres={movie.genres} /></View>
        <View style={[styles.row, { top: DETAIL.ratingY - 8 }]}><Rating movie={movie} /></View>
        <Text style={[styles.director, { top: DETAIL.directorY - 8 }]}>{movie.director}</Text>
      </Animated.View>
      <Animated.View style={[{ position: 'absolute', left: 0, top: 0, width: inner }, body]}>
        <Text style={[styles.label, { top: DETAIL.actorsLabelY - 10 }]} accessibilityRole="header">Actors</Text>
        <Animated.ScrollView ref={castRef} horizontal showsHorizontalScrollIndicator={false}
          style={{ position: 'absolute', top: DETAIL.actorsTop, left: 0, width: inner }}
          contentContainerStyle={{ paddingHorizontal: DETAIL.contentX, gap: DETAIL.actorGap }}>
          {movie.cast.map((person) => (
            <View key={person.name} style={{ width: DETAIL.actorW }}>
              <Image source={person.photo} style={styles.actor} contentFit="cover" transition={0} accessibilityLabel={person.name} />
              <Text style={styles.actorName} numberOfLines={1}>{person.name}</Text>
            </View>
          ))}
        </Animated.ScrollView>
        <Text style={[styles.label, { top: DETAIL.introLabelY - 10 }]} accessibilityRole="header">Introduction</Text>
        <Text style={[styles.intro, { top: DETAIL.introTop }]} numberOfLines={3}>{movie.intro}</Text>
      </Animated.View>
    </Animated.View>
  );
}

/** One button for both screens: BUY TICKET grows from the card's width to the sheet's. */
export function BuyButton({ open, width, onPress, hidden, enabled }: {
  open: SharedValue<number>; width: number; onPress: () => void; hidden: SharedValue<number>; enabled: boolean;
}) {
  const press = useSharedValue(0);
  const style = useAnimatedStyle(() => {
    const o = open.get();
    const w = interpolate(o, [0, 1], [CAROUSEL.buttonW, DETAIL.buttonW]);
    const cy = interpolate(o, [0, 1], [CAROUSEL.buttonY, DETAIL.sheetTop + DETAIL.buttonY]);
    const h = interpolate(o, [0, 1], [CAROUSEL.buttonH, DETAIL.buttonH]);
    return {
      left: (width - w) / 2, width: w, top: cy - h / 2, height: h,
      opacity: 1 - hidden.get(),
      transform: [{ scale: 1 - (1 - PRESS_SCALE) * press.get() }],
    };
  });
  return (
    <Animated.View style={[styles.button, style]} pointerEvents={enabled ? 'auto' : 'none'}
      accessibilityElementsHidden={!enabled}
      importantForAccessibility={enabled ? 'auto' : 'no-hide-descendants'}>
      <Pressable accessibilityRole="button" accessibilityLabel="Buy ticket" style={styles.buttonHit} onPress={onPress} disabled={!enabled}
        onPressIn={() => press.set(withSpring(1, { damping: 18, stiffness: 320 }))}
        onPressOut={() => press.set(withSpring(0, { damping: 18, stiffness: 320 }))}>
        <Text style={styles.buttonText}>BUY TICKET</Text>
      </Pressable>
    </Animated.View>
  );
}


const styles = StyleSheet.create({
  sheet: { position: 'absolute', backgroundColor: COLORS.paper, overflow: 'hidden' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: SANS.semibold, fontSize: CAROUSEL.titleSize, lineHeight: 36, color: COLORS.ink },
  row: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  director: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: SANS.regular, fontSize: 10, lineHeight: 16, color: COLORS.inkSoft },
  label: { position: 'absolute', left: DETAIL.contentX, fontFamily: SANS.semibold, fontSize: 13.5, lineHeight: 20, color: COLORS.ink },
  actor: { width: DETAIL.actorW, height: DETAIL.actorH, borderRadius: DETAIL.actorRadius, backgroundColor: '#D9D9DC' },
  actorName: { marginTop: 8, textAlign: 'center', fontFamily: SANS.regular, fontSize: 9.5, color: COLORS.inkSoft },
  intro: { position: 'absolute', left: DETAIL.contentX, right: DETAIL.contentX, fontFamily: SANS.regular, fontSize: 10.5, lineHeight: 16, color: COLORS.inkMute },
  button: { position: 'absolute', height: CAROUSEL.buttonH, borderRadius: 4, backgroundColor: COLORS.button, overflow: 'hidden' },
  buttonHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontFamily: SANS.semibold, fontSize: 16.3, letterSpacing: 0.4, color: COLORS.paper, marginTop: 3 },
});
