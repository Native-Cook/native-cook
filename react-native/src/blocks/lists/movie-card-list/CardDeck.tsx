import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, interpolate, useAnimatedStyle } from 'react-native-reanimated';

import { Star } from './icons';
import { type Movie, MOVIES } from './movies';
import { CAROUSEL, COLORS, DETAIL, DISPLAY, SANS, SERIF_DISPLAY } from './theme';

/** Each film's backdrop is fixed to the screen and revealed through a window that travels with its card. */
export function Backdrops({ page, open, width }: { page: SharedValue<number>; open: SharedValue<number>; width: number }) {
  const dim = useAnimatedStyle(() => ({ opacity: open.get() }));
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {MOVIES.map((movie, i) => <BackdropWindow key={movie.id} movie={movie} index={i} page={page} width={width} />)}
      <LinearGradient colors={['rgba(255,255,255,0)', COLORS.paper]} locations={[CAROUSEL.fadeFrom, 0.93]}
        style={{ position: 'absolute', left: 0, top: 0, width, height: CAROUSEL.backdropH }} />
      {/* Opening the film turns the room dark around the sheet. */}
      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: COLORS.night }, dim]} />
    </View>
  );
}

function BackdropWindow({ movie, index, page, width }: { movie: Movie; index: number; page: SharedValue<number>; width: number }) {
  const windowStyle = useAnimatedStyle(() => {
    const left = (index - page.get()) * width;
    return { transform: [{ translateX: left }] };
  });
  const imageStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -(index - page.get()) * width }] }));
  return (
    <Animated.View style={[styles.window, { width, height: CAROUSEL.backdropH }, windowStyle]}>
      <Animated.View style={[{ width, height: CAROUSEL.backdropH, backgroundColor: movie.backdropColor }, imageStyle]}>
        <Image source={movie.backdrop} style={StyleSheet.absoluteFill} contentFit="cover" contentPosition="top" transition={0} />
        <Lockup movie={movie} width={width} />
      </Animated.View>
    </Animated.View>
  );
}

function Lockup({ movie, width }: { movie: Movie; width: number }) {
  const { lockup } = movie;
  if (lockup.tone === 'buds') {
    return (
      <View style={[styles.lockup, { width, top: 70 }]}>
        {lockup.title.map((line) => <Text key={line} style={styles.budsTitle}>{line}</Text>)}
      </View>
    );
  }
  if (lockup.tone === 'grift') {
    return <View style={[styles.lockup, { width, top: 92 }]}><Text style={styles.griftTitle}>{lockup.title[0]}</Text></View>;
  }
  return (
    <View style={[styles.lockup, { width, top: 70.7 }]}>
      <Text style={styles.kicker}>{lockup.kicker}</Text>
      <Text style={styles.jesterTitle}>{lockup.title[0]}</Text>
      <Text style={styles.tagline}>{lockup.tagline}</Text>
      <Text style={styles.date}>{lockup.date}</Text>
    </View>
  );
}

/** The card's poster carries its own small title, as the printed posters do. */
function PosterTitle({ movie }: { movie: Movie }) {
  if (movie.lockup.tone === 'jester') {
    return (
      <View style={styles.posterJester}>
        <Text style={styles.posterKicker}>MASON VALE</Text>
        <Text style={styles.posterJesterTitle}>DRIFT</Text>
        <Text style={styles.posterKicker}>OCTOBER 4</Text>
      </View>
    );
  }
  if (movie.lockup.tone === 'buds') {
    return <View style={styles.posterTop}>{movie.lockup.title.map((l) => <Text key={l} style={styles.posterBuds}>{l}</Text>)}</View>;
  }
  return <View style={styles.posterTop}><Text style={styles.posterGrift}>{movie.lockup.title[0]}</Text></View>;
}

export function Rating({ movie, size = 13 }: { movie: Movie; size?: number }) {
  return (
    <View style={styles.rating} accessible accessibilityLabel={`Rated ${movie.rating.toFixed(1)}`}>
      <Text style={styles.ratingText}>{movie.rating.toFixed(1)}</Text>
      {[0, 1, 2, 3, 4].map((i) => <Star key={i} size={size} color={i < movie.stars ? COLORS.star : COLORS.starOff} filled={i < movie.stars} />)}
    </View>
  );
}

export function Chips({ genres }: { genres: string[] }) {
  return (
    <View style={styles.chips}>
      {genres.map((g) => <View key={g} style={styles.chip}><Text style={styles.chipText}>{g}</Text></View>)}
    </View>
  );
}

function Card({ movie, index, page, open, width }: {
  movie: Movie; index: number; page: SharedValue<number>; open: SharedValue<number>; width: number;
}) {
  const { cardW, cardTop, cardH, pitch, sideScale } = CAROUSEL;
  const cx = width / 2;
  const style = useAnimatedStyle(() => {
    const d = index - page.get();
    const o = open.get();
    const near = Math.min(1, Math.abs(d));
    const s = 1 - (1 - interpolate(o, [0, 1], [sideScale, DETAIL.sideScale])) * near;
    // Opening lifts the posters to a row at the top, behind the sheet.
    // Scale is about the card's centre, so the lift puts the scaled poster's top edge on DETAIL.posterTop.
    const lift = interpolate(o, [0, 1], [0, DETAIL.posterTop - cardTop - cardH / 2 + (cardH / 2 - CAROUSEL.posterTop) * DETAIL.posterScale]);
    const shrink = interpolate(o, [0, 1], [1, DETAIL.posterScale]);
    return {
      transform: [{ translateX: d * pitch * interpolate(o, [0, 1], [1, DETAIL.pitch]) }, { translateY: lift }, { scale: s * shrink }],
      zIndex: Math.round(10 - Math.abs(d) * 2),
    };
  });
  const frameStyle = useAnimatedStyle(() => ({ opacity: 1 - open.get() }));
  const washStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.abs(index - page.get())) * CAROUSEL.sideWash * (1 - open.get()),
  }));
  return (
    <Animated.View pointerEvents="none" style={[styles.card, { left: cx - cardW / 2, top: cardTop, width: cardW, height: cardH }, style]}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.frame, frameStyle]} />
      <View style={styles.poster}>
        <Image source={movie.poster} contentFit="cover" transition={0}
          style={[StyleSheet.absoluteFill, movie.posterZoom && { transform: [{ scale: movie.posterZoom.scale }, { translateX: movie.posterZoom.x }, { translateY: movie.posterZoom.y }] }]} />
        <PosterTitle movie={movie} />
      </View>
      <Animated.View style={[StyleSheet.absoluteFill, styles.wash, washStyle]} />
    </Animated.View>
  );
}

export function CardDeck({ page, open, width }: { page: SharedValue<number>; open: SharedValue<number>; width: number }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {MOVIES.map((movie, i) => <Card key={movie.id} movie={movie} index={i} page={page} open={open} width={width} />)}
    </View>
  );
}

/** Title, chips and rating under the centre card; they cross-fade between films as the page moves. */
export function CardInfo({ page, open, width }: { page: SharedValue<number>; open: SharedValue<number>; width: number }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {MOVIES.map((movie, i) => <InfoBlock key={movie.id} movie={movie} index={i} page={page} open={open} width={width} />)}
    </View>
  );
}

function InfoBlock({ movie, index, page, open, width }: { movie: Movie; index: number; page: SharedValue<number>; open: SharedValue<number>; width: number }) {
  const style = useAnimatedStyle(() => {
    const d = index - page.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.6) * (1 - Math.min(1, open.get() * 2.5)), transform: [{ translateX: d * 60 }] };
  });
  return (
    <Animated.View style={[styles.info, { width }, style]}>
      {/* On the pin the title and the chips sit 4 pt right of the card's centre line. */}
      <Text style={[styles.title, { top: CAROUSEL.titleY - 18, left: 8 }]}>{movie.title}</Text>
      <View style={[styles.row, { top: CAROUSEL.chipsY - CAROUSEL.chipH / 2, left: 8 }]}><Chips genres={movie.genres} /></View>
      <View style={[styles.row, { top: CAROUSEL.ratingY - 8 }]}><Rating movie={movie} /></View>
      <Text style={[styles.dots, { top: CAROUSEL.dotsY - 10 }]}>•••</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  window: { position: 'absolute', left: 0, top: 0, overflow: 'hidden' },
  lockup: { position: 'absolute', left: 0, alignItems: 'center' },
  kicker: { fontFamily: SANS.semibold, fontSize: 11, letterSpacing: 2.6, color: '#C9CDCB' },
  // The pin's title is a tall condensed face (73 pt cap height, 144 pt wide): Anton at 78 stretched 1.1 vertically.
  jesterTitle: { fontFamily: DISPLAY, fontSize: 78, lineHeight: 104, color: '#F2F2EE', letterSpacing: -0.5, marginTop: -3, marginBottom: -18.4, transform: [{ scaleY: 1.1 }] },
  tagline: { fontFamily: SANS.semibold, fontSize: 11.3, letterSpacing: 1.6, color: '#F2F2EE', marginTop: 2 },
  date: { fontFamily: SANS.semibold, fontSize: 11.3, letterSpacing: 1.7, color: '#B9BFBC', marginTop: 4.4 },
  budsTitle: { fontFamily: DISPLAY, fontSize: 64, lineHeight: 64, color: '#FFFFFF', letterSpacing: 0.5 },
  griftTitle: { fontFamily: SERIF_DISPLAY, fontSize: 42, letterSpacing: 6, color: '#FFF7DC',
    textShadowColor: 'rgba(80,50,0,0.45)', textShadowRadius: 8 },
  card: { position: 'absolute' },
  frame: { backgroundColor: COLORS.paper, borderRadius: CAROUSEL.cardRadius,
    shadowColor: '#000', shadowOpacity: 0.12, shadowRadius: 18, shadowOffset: { width: 0, height: 6 } },
  poster: { position: 'absolute', left: CAROUSEL.posterInset, right: CAROUSEL.posterInset, top: CAROUSEL.posterTop,
    height: CAROUSEL.posterH, borderRadius: CAROUSEL.posterRadius, overflow: 'hidden', backgroundColor: '#20302C' },
  posterJester: { position: 'absolute', right: 12, bottom: 18, alignItems: 'flex-end' },
  posterKicker: { fontFamily: SANS.semibold, fontSize: 6.5, letterSpacing: 1.4, color: '#E6E6E2' },
  posterJesterTitle: { fontFamily: DISPLAY, fontSize: 34, lineHeight: 38, color: '#F4F4F0' },
  posterTop: { position: 'absolute', left: 0, right: 0, top: 22, alignItems: 'center' },
  posterBuds: { fontFamily: DISPLAY, fontSize: 40, lineHeight: 40, color: '#FFFFFF' },
  posterGrift: { fontFamily: SERIF_DISPLAY, fontSize: 22, letterSpacing: 3, color: '#FFF7DC' },
  wash: { backgroundColor: COLORS.paper, borderRadius: CAROUSEL.cardRadius },
  info: { position: 'absolute', left: 0, top: 0, height: 760 },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: SANS.medium, fontSize: CAROUSEL.titleSize, lineHeight: 36, color: COLORS.ink },
  row: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  chips: { flexDirection: 'row', gap: 6.5 },
  chip: { height: CAROUSEL.chipH, paddingHorizontal: 12, borderRadius: CAROUSEL.chipH / 2, borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: COLORS.chip, justifyContent: 'center' },
  chipText: { fontFamily: SANS.regular, fontSize: CAROUSEL.chipSize, color: COLORS.inkSoft },
  rating: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  ratingText: { fontFamily: SANS.regular, fontSize: 15, color: COLORS.inkSoft, marginRight: 7 },
  dots: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 17, letterSpacing: 2, color: COLORS.ink },
});
