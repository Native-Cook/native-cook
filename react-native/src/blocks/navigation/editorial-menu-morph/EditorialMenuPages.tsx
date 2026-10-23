import { Canvas, Group, Image as SkiaImage, Rect, Skia, type SkImage } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

/**
 * The museum's sections: the word in the menu with its small count, the one photo that floats
 * beside the word and then becomes its page's hero, the colour the menu turns to, and the page
 * each one opens on the site underneath.
 */

// Display serif: Iowan Old Style on iOS (sturdy, never hairline); the system serif at Medium on
// Android. Sans: Avenir Next Medium/DemiBold on iOS, sans-serif-medium on Android.
export const DISPLAY = process.env.EXPO_OS === 'ios' ? 'Iowan Old Style' : 'serif';
export const DISPLAY_WEIGHT = process.env.EXPO_OS === 'ios' ? ('400' as const) : ('500' as const);
export const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif-medium';

export const PAPER = '#efe9df';
export const INK = '#16130f';
const MUTED = '#7b6f60';
/** What the hero shows while its photo loads, or if it never does. */
export const HERO_PLACEHOLDER = '#d9d0c2';

/** An Unsplash photo, cropped to `width` × `height` points at 3x. */
export function photo(id: string, width: number, height: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${Math.round(width * 3)}&h=${Math.round(height * 3)}&q=80`;
}

export type Section = {
  word: string;
  /** The small count set after the word in the menu. */
  count: string;
  /** The photo beside the word in the menu and the hero of its page: one and the same. */
  photo: string;
  /** The menu's colour while this word is under the finger. */
  tint: string;
  kicker: string;
  /** The page's headline, already broken into its two lines. */
  title: [string, string];
  caption: string;
  note: string;
};

export const SECTIONS: Section[] = [
  {
    word: 'Exhibitions',
    count: '12',
    photo: 'photo-1705873653125-75312a404bc4',
    tint: '#5b1f1b',
    kicker: 'NOW ON VIEW · HALL C',
    title: ['The Weight', 'of Stillness'],
    caption: 'Marble and plaster, 1880–1930',
    note: 'Until 2 March',
  },
  {
    word: 'Collection',
    count: '4,120',
    photo: 'photo-1705996952322-b070e7982606',
    tint: '#1d3247',
    kicker: 'THE COLLECTION · 4,120 WORKS',
    title: ['Bodies, Busts', 'and Fragments'],
    caption: 'Search by room, maker or century',
    note: 'Open daily',
  },
  {
    word: 'Architecture',
    count: '1911',
    photo: 'photo-1608590766866-ae8d39690bbf',
    tint: '#6a4a1e',
    kicker: 'THE BUILDING · 1911',
    title: ['A Stair Without', 'an End'],
    caption: 'Ninety-one steps by Ada Velde',
    note: 'Tours at 11:00',
  },
  {
    word: 'Visit',
    count: '10–18',
    photo: 'photo-1550773316-169760ebc0d1',
    tint: '#1d4943',
    kicker: 'VISIT · KESTREL QUAY 4',
    title: ['Light Comes', 'Down at Four'],
    caption: 'Tuesday to Sunday, 10:00–18:00',
    note: 'Free on Thursdays',
  },
  {
    word: 'Journal',
    count: 'Nº 12',
    photo: 'photo-1680869839348-4c76c37ce0e0',
    tint: '#393826',
    kicker: 'JOURNAL · ISSUE 12',
    title: ['Notes from the', 'Plaster Room'],
    caption: 'Essays, interviews and casts',
    note: 'New every month',
  },
];

// The page, laid out by hand so the menu's card knows exactly where the hero is: the kicker row
// KICKER_TOP pt below the safe area, two title lines of TITLE_LINE pt, the hero HERO_GAP pt
// under them, 20 pt from the sides, at most 44% of the screen tall and never into the footer.
const KICKER_TOP = 74;
const TITLE_GAP = 28;
export const TITLE_SIZE = 40;
export const TITLE_LINE = 48;
const HERO_GAP = 18;
const SIDE = 20;
const FOOTER_ROOM = 110;

export type PageLayout = {
  kickerY: number;
  titleY: number;
  heroX: number;
  heroY: number;
  heroW: number;
  heroH: number;
  captionY: number;
};

export function pageLayout(width: number, height: number, top: number, bottom: number): PageLayout {
  'worklet';
  const kickerY = top + KICKER_TOP;
  const titleY = kickerY + TITLE_GAP;
  const heroY = titleY + 2 * TITLE_LINE + HERO_GAP;
  const heroW = Math.max(1, width - 2 * SIDE);
  const room = height - bottom - FOOTER_ROOM - heroY;
  const heroH = Math.max(1, Math.min(height * 0.44, heroW * 1.08, room));
  return { kickerY, titleY, heroX: SIDE, heroY, heroW, heroH, captionY: heroY + heroH + 12 };
}

// The page's own entrance, after a hero lands: one timeline from 0 to 1 over PAGE_IN_TIME ms,
// linearly, each part easing inside its slice.
export const PAGE_IN_TIME = 900;
const INDEX_ROLL: [number, number] = [0, 0.45];
const KICKER_IN: [number, number] = [0, 0.4];
const TITLE_FROM = 0.06;
const TITLE_STAGGER = 0.1;
const TITLE_SPAN = 0.5;
const CAPTION_IN: [number, number] = [0.4, 0.85];
const FOOTER_IN: [number, number] = [0.5, 1];
const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
const ROLL = 14;

function slice(t: number, s: [number, number]) {
  'worklet';
  return EASE_OUT(Math.min(1, Math.max(0, (t - s[0]) / (s[1] - s[0]))));
}

/**
 * Loads the section photos once as Skia images, for the menu's card and the page's hero alike,
 * so the card hands off to the hero pixel for pixel. A photo that fails stays null (and the
 * placeholder shows); failures never throw.
 */
export function useSectionImages(uris: string[]) {
  const key = uris.join('|');
  const [images, setImages] = useState<(SkImage | null)[]>([]);
  useEffect(() => {
    let alive = true;
    const list = key.length > 0 ? key.split('|') : [];
    list.forEach((uri, i) => {
      Skia.Data.fromURI(uri)
        .then((data) => {
          try {
            return Skia.Image.MakeImageFromEncoded(data);
          } catch {
            return null;
          }
        })
        .catch(() => null)
        .then((image) => {
          if (!alive || !image) return;
          setImages((prev) => {
            const next = prev.slice();
            next[i] = image;
            return next;
          });
        });
    });
    return () => {
      alive = false;
    };
  }, [key]);
  return images;
}

type PageProps = {
  section: Section;
  index: number;
  /** The section shown before this one, for the index that rolls. */
  previous: number;
  total: number;
  layout: PageLayout;
  image: SkImage | null;
  /** 1 once the hero is on the page (0 while the menu's card is still flying to it). */
  heroShown: SharedValue<number>;
  pageIn: SharedValue<number>;
  bottom: number;
  reduced: boolean;
};

const pad = (n: number) => String(n + 1).padStart(2, '0');

/** The site under the menu: one section's page, as a quiet editorial layout. */
export function MuseumPage({ section, index, previous, total, layout, image, heroShown, pageIn, bottom, reduced }: PageProps) {
  const { kickerY, titleY, heroX, heroY, heroW, heroH, captionY } = layout;
  const kickerStyle = useAnimatedStyle(() => {
    const e = slice(pageIn.get(), KICKER_IN);
    return { opacity: e, transform: [{ translateY: reduced ? 0 : (1 - e) * 10 }] };
  });
  const captionStyle = useAnimatedStyle(() => {
    const e = slice(pageIn.get(), CAPTION_IN);
    return { opacity: e, transform: [{ translateY: reduced ? 0 : (1 - e) * 8 }] };
  });
  const footerStyle = useAnimatedStyle(() => ({ opacity: slice(pageIn.get(), FOOTER_IN) }));
  return (
    <View style={[StyleSheet.absoluteFill, { backgroundColor: PAPER }]}>
      <View style={[styles.kickerRow, { top: kickerY }]}>
        <Animated.Text style={[styles.kicker, kickerStyle]} numberOfLines={1}>
          {section.kicker}
        </Animated.Text>
        <View style={styles.indexRow}>
          <RollingIndex now={pad(index)} before={index === previous ? null : pad(previous)} pageIn={pageIn} reduced={reduced} />
          <Text style={styles.index}>{` / ${String(total).padStart(2, '0')}`}</Text>
        </View>
      </View>
      {section.title.map((line, i) => (
        <TitleLine key={`${section.word}-${i}`} line={line} row={i} top={titleY + i * TITLE_LINE} pageIn={pageIn} reduced={reduced} />
      ))}
      <Canvas style={{ position: 'absolute', left: heroX, top: heroY, width: heroW, height: heroH }} pointerEvents="none">
        <Group opacity={heroShown}>
          <Rect x={0} y={0} width={heroW} height={heroH} color={HERO_PLACEHOLDER} />
          {image && <SkiaImage image={image} x={0} y={0} width={heroW} height={heroH} fit="cover" />}
        </Group>
      </Canvas>
      <Animated.View style={[styles.captionRow, { top: captionY }, captionStyle]}>
        <Text style={styles.caption} numberOfLines={1}>
          {section.caption}
        </Text>
        <Text style={styles.note}>{section.note}</Text>
      </Animated.View>
      <Animated.View style={[styles.footer, { bottom: bottom + 18 }, footerStyle]}>
        <Text style={styles.link}>Plan a visit</Text>
        <Svg width={22} height={12} viewBox="0 0 22 12">
          <Path d="M 1 6 L 20 6 M 15 1 L 20 6 L 15 11" stroke={INK} strokeWidth={1.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </Animated.View>
    </View>
  );
}

type TitleLineProps = { line: string; row: number; top: number; pageIn: SharedValue<number>; reduced: boolean };

/** One headline line, rising out of its own clipped baseline after the one above it. */
function TitleLine({ line, row, top, pageIn, reduced }: TitleLineProps) {
  const style = useAnimatedStyle(() => {
    const from = TITLE_FROM + row * TITLE_STAGGER;
    const e = slice(pageIn.get(), [from, from + TITLE_SPAN]);
    return reduced ? { opacity: e } : { transform: [{ translateY: (1 - e) * TITLE_LINE }, { rotate: `${(1 - e) * 3}deg` }] };
  });
  return (
    <View style={[styles.titleClip, { top }]}>
      <Animated.Text style={[styles.title, style]} numberOfLines={1}>
        {line}
      </Animated.Text>
    </View>
  );
}

type IndexProps = { now: string; before: string | null; pageIn: SharedValue<number>; reduced: boolean };

/** The section number: the old one rolls up and out as the new one rolls in from below. */
function RollingIndex({ now, before, pageIn, reduced }: IndexProps) {
  const outStyle = useAnimatedStyle(() => {
    const e = slice(pageIn.get(), INDEX_ROLL);
    return reduced ? { opacity: 1 - e } : { opacity: 1, transform: [{ translateY: -e * ROLL }] };
  });
  const inStyle = useAnimatedStyle(() => {
    const e = before === null ? 1 : slice(pageIn.get(), INDEX_ROLL);
    return reduced ? { opacity: e } : { transform: [{ translateY: (1 - e) * ROLL }] };
  });
  return (
    <View style={styles.indexClip}>
      {before !== null && <Animated.Text style={[styles.index, styles.indexOld, outStyle]}>{before}</Animated.Text>}
      <Animated.Text style={[styles.index, inStyle]}>{now}</Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  kickerRow: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    height: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  kicker: { flexShrink: 1, fontFamily: SANS, fontSize: 11, fontWeight: '600', letterSpacing: 2, color: MUTED },
  indexRow: { flexDirection: 'row', alignItems: 'center' },
  indexClip: { height: ROLL, overflow: 'hidden', justifyContent: 'center' },
  index: { fontFamily: SANS, fontSize: 11, lineHeight: ROLL, fontWeight: '600', letterSpacing: 1.2, color: INK, fontVariant: ['tabular-nums'] },
  indexOld: { position: 'absolute', left: 0, top: 0 },
  titleClip: { position: 'absolute', left: SIDE, right: SIDE, height: TITLE_LINE, overflow: 'hidden' },
  title: {
    fontFamily: DISPLAY,
    fontWeight: DISPLAY_WEIGHT,
    fontSize: TITLE_SIZE,
    lineHeight: TITLE_LINE,
    color: INK,
    letterSpacing: -0.4,
  },
  captionRow: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    paddingBottom: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(22,19,15,0.35)',
    gap: 12,
  },
  caption: { fontFamily: DISPLAY, fontStyle: 'italic', fontWeight: DISPLAY_WEIGHT, fontSize: 15, color: INK, flexShrink: 1 },
  note: { fontFamily: SANS, fontSize: 12, fontWeight: '600', color: MUTED },
  footer: { position: 'absolute', left: SIDE, flexDirection: 'row', alignItems: 'center', gap: 10 },
  link: { fontFamily: SANS, fontSize: 15, fontWeight: '600', color: INK, letterSpacing: 0.2 },
});
