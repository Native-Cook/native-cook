import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { forwardRef } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import type { Poster, Section, Still, Tall, TitleStyle } from './content';
import { HERO } from './content';
import { PlayCircleIcon, PlayGlyph, StarIcon } from './icons';
import { LABEL_LINE } from './layout';
import { EASE_OUT, PRESS_MS, PRESS_SCALE } from './motion';
import { COLORS, FONT, HOME, POSTER_TYPE } from './theme';

function face(style: TitleStyle) {
  const t = POSTER_TYPE[style];
  return { fontFamily: t.fontFamily, fontSize: t.fontSize, lineHeight: t.lineHeight, letterSpacing: t.letterSpacing };
}
const caseOf = (style: TitleStyle, text: string) => (POSTER_TYPE[style].upper ? text.toUpperCase() : text);
const isDarkInk = (hex: string) => {
  const n = parseInt(hex.slice(1), 16);
  return ((n >> 16) & 255) * 0.299 + ((n >> 8) & 255) * 0.587 + (n & 255) * 0.114 < 110;
};

/** Press feedback only: the pin has no detail screen, so a card answers the finger and stays put. */
function PressCard({ label, style, children }: { label: string; style: object; children: React.ReactNode }) {
  const press = useSharedValue(0);
  const animated = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS_SCALE) * press.get() }] }));
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} pressRetentionOffset={16}
      onPressIn={() => press.set(withTiming(1, { duration: PRESS_MS, easing: EASE_OUT }))}
      onPressOut={() => press.set(withTiming(0, { duration: PRESS_MS, easing: EASE_OUT }))}>
      <Animated.View style={[style, animated]}>{children}</Animated.View>
    </Pressable>
  );
}

function PosterCard({ item }: { item: Poster }) {
  const { w, h, radius } = HOME.poster;
  const ink = item.tint ?? COLORS.ink;
  const scrim = isDarkInk(ink) ? ['rgba(255,255,255,0.35)', 'rgba(255,255,255,0)'] : ['rgba(0,0,0,0.42)', 'rgba(0,0,0,0)'];
  // A one-sheet: the photographic plate with its title typeset in its own face and a billing line.
  return (
    <PressCard label={item.title} style={[styles.clip, { width: w, height: h, borderRadius: radius }]}>
      <Image source={item.image} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
      <LinearGradient colors={(item.top ? scrim : [scrim[1], scrim[0]]) as [string, string]}
        locations={item.top ? [0, 0.42] : [0.58, 1]} style={StyleSheet.absoluteFill} />
      <View style={[styles.posterType, item.top ? { top: 10 } : { bottom: 9 }]}>
        <Text numberOfLines={2} style={[styles.posterTitle, face(item.style), { color: ink }]}>{caseOf(item.style, item.title)}</Text>
        <Text numberOfLines={1} style={[styles.billing, { color: ink }]}>{item.credit}</Text>
      </View>
    </PressCard>
  );
}

function StillCard({ item }: { item: Still }) {
  const { w, image, info, radius, progress } = HOME.wide;
  return (
    <PressCard label={`${item.title}, ${item.meta}`} style={[styles.clip, styles.stillCard, { width: w, height: image + info, borderRadius: radius }]}>
      <View style={{ width: w, height: image }}>
        <Image source={item.image} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
        <View style={styles.stillPlay}><PlayCircleIcon size={14} color={COLORS.ink} /></View>
        <View style={[styles.track, { height: progress, left: HOME.wide.progressInset, right: HOME.wide.progressInset, bottom: HOME.wide.progressLift }]}>
          <View style={[styles.fill, { width: `${item.progress * 100}%` }]} />
        </View>
      </View>
      <Text style={styles.stillTitle}>{item.title}</Text>
      <Text style={styles.stillMeta}>{item.meta}</Text>
    </PressCard>
  );
}

function TallCard({ item }: { item: Tall }) {
  const { w, h, radius } = HOME.tall;
  return (
    <PressCard label={item.word} style={[styles.clip, { width: w, height: h, borderRadius: radius }]}>
      <Image source={item.image} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
      <LinearGradient colors={item.top ? ['rgba(0,0,0,0.35)', 'rgba(0,0,0,0)'] : ['rgba(0,0,0,0)', 'rgba(0,0,0,0.5)']}
        locations={item.top ? [0, 0.3] : [0.65, 1]} style={StyleSheet.absoluteFill} />
      <Text numberOfLines={1} adjustsFontSizeToFit style={[styles.tallWord, face(item.style),
        item.style === 'anton' ? styles.tallAnton : styles.tallGame,
        item.top ? { top: 18 } : { bottom: HOME.tall.wordFromBottom - 22 }]}>
        {item.style === 'anton' ? item.word : caseOf(item.style, item.word)}
      </Text>
    </PressCard>
  );
}

export const SectionRow = forwardRef<ScrollView, { section: Section; first: boolean }>(function SectionRow({ section, first }, ref) {
  const pitch = section.kind === 'posters' ? HOME.poster.w + HOME.poster.gap
    : section.kind === 'wide' ? HOME.wide.w + HOME.wide.gap : HOME.tall.w + HOME.tall.gap;
  const gap = section.kind === 'posters' ? HOME.poster.gap : section.kind === 'wide' ? HOME.wide.gap : HOME.tall.gap;
  return (
    <View style={{ marginTop: first ? 0 : section.gap - LABEL_LINE / 2 }}>
      <Text style={[styles.label, section.labelX !== undefined && { marginLeft: section.labelX }]} accessibilityRole="header">{section.label}</Text>
      <ScrollView ref={ref} horizontal showsHorizontalScrollIndicator={false} decelerationRate="fast"
        snapToInterval={pitch} snapToAlignment="start"
        style={{ marginTop: (section.toContent ?? HOME.labelToContent) - LABEL_LINE / 2 }}
        contentContainerStyle={{ paddingHorizontal: HOME.side, gap }}>
        {section.kind === 'posters' && section.items.map((item) => <PosterCard key={item.title} item={item} />)}
        {section.kind === 'wide' && section.items.map((item) => <StillCard key={item.title} item={item} />)}
        {section.kind === 'tall' && section.items.map((item) => <TallCard key={item.word} item={item} />)}
      </ScrollView>
    </View>
  );
});

export function Hero({ width, onPlay }: { width: number; onPlay?: () => void }) {
  const h = HOME.heroHeight;
  return (
    <View style={{ width, height: HOME.firstLabelY - LABEL_LINE / 2 }}>
      <Image source={HERO.image} style={{ position: 'absolute', left: 0, top: 0, width, height: h }} contentFit="cover" transition={0} />
      <LinearGradient colors={['rgba(43,41,48,0)', COLORS.bg]} locations={[HOME.heroFadeFrom, 1]}
        style={{ position: 'absolute', left: 0, top: 0, width, height: h }} />
      {/* The poster's own lockup, typeset as a one-sheet would be. */}
      <View style={[styles.badge, { top: HOME.badgeY - 5, left: width / 2 - 26 }]}>
        <Text style={styles.badgeText}>{HERO.badge}</Text>
      </View>
      <Text style={[styles.lockupSub, { top: HOME.subtitleY - 8, width }]}>{HERO.subtitle}</Text>
      <Text style={[styles.lockup, { top: HOME.lockupY - 20, width }]}>{HERO.lockup}</Text>

      <Text style={[styles.heroTitle, { top: HOME.titleY - 13 }]}>{HERO.title}</Text>
      <Text style={[styles.heroGenre, { top: HOME.genreY - 9 }]}>{HERO.genres.join(' • ')}</Text>
      <View style={[styles.stars, { top: HOME.starsY - HOME.star / 2 }]} accessible accessibilityLabel={`Rated ${HOME.rating} out of 5`}>
        {[0, 1, 2, 3, 4].map((i) => (
          <StarIcon key={i} size={HOME.star} color={COLORS.star} off={COLORS.starOff} fill={Math.max(0, Math.min(1, HOME.rating - i))} />
        ))}
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel={`Play ${HERO.title}`} onPress={onPlay} hitSlop={4}
        style={[styles.play, { left: HOME.playX - HOME.play / 2, top: HOME.playY - HOME.play / 2 }]}>
        <PlayGlyph size={26} color="#111114" />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  clip: { overflow: 'hidden', backgroundColor: COLORS.posterPlaceholder },
  posterType: { position: 'absolute', left: 6, right: 6, alignItems: 'center' },
  posterTitle: { textAlign: 'center' },
  billing: { marginTop: 3, fontFamily: FONT.bebas, fontSize: 5.5, lineHeight: 7, letterSpacing: 0.9, opacity: 0.75, textAlign: 'center' },
  stillCard: { backgroundColor: COLORS.card },
  stillPlay: { position: 'absolute', left: 8, bottom: 17 },
  track: { position: 'absolute', backgroundColor: COLORS.progressTrack },
  fill: { height: '100%', backgroundColor: COLORS.progress },
  stillTitle: { marginTop: 11 + HOME.wide.titleNudge, marginLeft: 10, fontFamily: FONT.regular, fontSize: HOME.wide.titleSize, lineHeight: 24, color: COLORS.ink },
  stillMeta: { marginTop: 1 - HOME.wide.titleNudge, marginLeft: 10, fontFamily: FONT.regular, fontSize: HOME.wide.metaSize, lineHeight: 16, color: COLORS.inkMute },
  tallWord: { position: 'absolute', left: 10, right: 10, textAlign: 'center', color: COLORS.ink },
  tallAnton: { fontSize: HOME.tall.wordSize, lineHeight: 44 },
  tallGame: { fontSize: 30, lineHeight: 36 },
  label: { marginLeft: HOME.side + 2, fontFamily: FONT.medium, fontSize: HOME.labelSize, lineHeight: LABEL_LINE, color: COLORS.ink, transform: [{ translateY: HOME.labelNudge }] },
  badge: { position: 'absolute', width: 52, height: 10, backgroundColor: COLORS.badge, alignItems: 'center', justifyContent: 'center' },
  badgeText: { fontFamily: FONT.bebas, fontSize: 8, lineHeight: 9, color: COLORS.ink, letterSpacing: 0.8 },
  lockupSub: { position: 'absolute', left: 0, textAlign: 'center', fontFamily: FONT.bebas, fontSize: 14, lineHeight: 16, color: '#F2F2F2', letterSpacing: 1.6,
    textShadowColor: 'rgba(0,0,0,0.6)', textShadowRadius: 4 },
  lockup: { position: 'absolute', left: 0, textAlign: 'center', fontFamily: FONT.marcellus, fontSize: HOME.lockupSize - 3, lineHeight: 40, color: '#E9EEF2', letterSpacing: 1.2,
    textShadowColor: 'rgba(10,20,30,0.85)', textShadowRadius: 6, textShadowOffset: { width: 0, height: 1 } },
  heroTitle: { position: 'absolute', left: HOME.side, fontFamily: FONT.regular, fontSize: HOME.titleSize, lineHeight: 26, color: COLORS.ink },
  heroGenre: { position: 'absolute', left: HOME.genreX, fontFamily: FONT.regular, fontSize: HOME.genreSize, lineHeight: 18, color: COLORS.inkSoft },
  stars: { position: 'absolute', left: HOME.starsX, flexDirection: 'row', gap: HOME.starPitch - HOME.star },
  play: { position: 'absolute', width: HOME.play, height: HOME.play, borderRadius: HOME.play / 2, backgroundColor: COLORS.ink,
    alignItems: 'center', justifyContent: 'center', paddingLeft: 3 },
});
