import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { type ComponentProps, useEffect, useEffectEvent, useRef, useState } from 'react';
import { Keyboard, Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  LinearTransition,
  type SharedValue,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PIECES, POPULAR, RECENT, type Piece, photo, search } from './catalog';
import { type Box, ICON_INSET, LiquidPill, pillAt } from './LiquidPill';

type Props = {
  /** Opens the search, types "air", lets the results settle and closes again, over and over. */
  autoPlay?: boolean;
  /** The words autoPlay types. */
  script?: string;
  onSearch?: (query: string) => void;
};

const PAPER = '#f3f0ea';
const INK = '#17150f';
const MUTED = '#8b8579';
const CREAM = '#f6f1e7';
const CARD = '#ffffff';

// Layout, in pt.
const SIDE = 20;
const TITLE_TOP = 58;
const PILL = { top: 156, width: 214, height: 46 };
const BAR_HEIGHT = 50;
const CANCEL_WIDTH = 74;
const HERO_TOP = 222;
const HERO_HEIGHT = 238;
const GRID_GAP = 12;
const TILE_PHOTO = 196;

// The morph runs on one value, 0 (pill) … 1 (search), at a constant rate; LiquidPill gives each edge
// its own eased share of it. Opening takes OPEN_MS, closing CLOSE_MS.
const OPEN_MS = 760;
const CLOSE_MS = 640;
// The home page sinks back while the search is open: it shrinks by HOME_SINK and blurs behind a
// veil that fades in with the morph.
const HOME_SINK = 0.06;
// Recent searches pop in one after another, CHIP_STAGGER ms apart, once the bar has landed.
const CHIP_STAGGER = 45;
const POP = { duration: 420, dampingRatio: 0.62 };
const TILE_STAGGER = 40;

// autoPlay (seconds): open, type, read the results, close, rest.
const LOOP = 8.6;
const OPEN_AT = 0.8;
const TYPE_AT = 1.95;
const KEY = 0.36;
const CLOSE_AT = 5.9;
const PRESS = 0.16;
// After a touch, autoPlay starts over once the search has been closed and empty this long.
const RESUME_AFTER = 4;

function popIn(delay: number) {
  return () => {
    'worklet';
    return {
      initialValues: { opacity: 0, transform: [{ scale: 0.84 }, { translateY: 10 }] },
      animations: {
        opacity: withDelay(delay, withTiming(1, { duration: 200 })),
        transform: [{ scale: withDelay(delay, withSpring(1, POP)) }, { translateY: withDelay(delay, withSpring(0, POP)) }],
      },
    };
  };
}

const CHIP_IN = RECENT.map((_, i) => popIn(220 + i * CHIP_STAGGER));
const ROW_IN = POPULAR.map((_, i) => popIn(420 + i * CHIP_STAGGER));
const TILE_IN = Array.from({ length: PIECES.length }, (_, i) => popIn(Math.min(i, 6) * TILE_STAGGER));
const FLOW = LinearTransition.springify(520).dampingRatio(0.82);

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function press(sv: SharedValue<number>) {
  'worklet';
  sv.set(withSequence(withTiming(1, { duration: 90 }), withTiming(0, { duration: 220 })));
}

/**
 * A shop's home page with a small ink-black search pill. Tapping it pulls the pill up to the top and
 * across the screen like a drop of ink, over a page that blurs and sinks back; recent searches pop
 * in under it. Typing filters a grid of pieces live, the tiles flowing to their new places as the
 * matches change, and Cancel pours the bar back into the pill.
 */
export function LiquidSearchMorph({ autoPlay = false, script = 'air', onSearch }: Props) {
  const insets = useSafeAreaInsets();
  const { width: screenW, height: screenH } = useWindowDimensions();
  const reducedMotion = useReducedMotion();

  const closed: Box = { x: SIDE, y: insets.top + PILL.top, w: PILL.width, h: PILL.height };
  const open: Box = { x: 16, y: insets.top + 8, w: screenW - 32 - CANCEL_WIDTH, h: BAR_HEIGHT };
  const canvasH = closed.y + closed.h + 70;
  const tileW = (screenW - SIDE * 2 - GRID_GAP) / 2;

  const [opened, setOpened] = useState(false);
  const [query, setQuery] = useState('');
  const [focused, setFocused] = useState(false);
  const [playing, setPlaying] = useState(autoPlay);
  const results = search(query);
  const input = useRef<TextInput>(null);
  // The keyboard's delayed focus and the close's delayed reset: cleared when they are overtaken and on
  // unmount, so neither lands on a bar that is already closing or a screen that has gone.
  const focusTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closing = useRef(false);
  useEffect(
    () => () => {
      if (focusTimer.current) clearTimeout(focusTimer.current);
      if (closeTimer.current) clearTimeout(closeTimer.current);
    },
    [],
  );

  const t = useSharedValue(0);
  const pillPress = useSharedValue(0);
  const cancelPress = useSharedValue(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const openUp = (byUser: boolean) => {
    if (opened) return;
    setOpened(true);
    if (byUser) tick();
    t.set(withTiming(1, { duration: OPEN_MS, easing: Easing.linear }));
    // The keyboard comes up once the bar has landed, so it does not race the morph.
    if (byUser) {
      if (focusTimer.current) clearTimeout(focusTimer.current);
      focusTimer.current = setTimeout(() => {
        focusTimer.current = null;
        input.current?.focus();
      }, OPEN_MS * 0.75);
    }
  };

  const close = (byUser: boolean) => {
    // A second Cancel while the bar pours back is ignored (no second haptic or reset timer).
    if (!opened || closing.current) return;
    closing.current = true;
    if (focusTimer.current) {
      clearTimeout(focusTimer.current);
      focusTimer.current = null;
    }
    if (byUser) tick();
    Keyboard.dismiss();
    input.current?.blur();
    t.set(withTiming(0, { duration: CLOSE_MS, easing: Easing.linear }));
    // What the search showed goes once the pill is whole again.
    closeTimer.current = setTimeout(() => {
      closeTimer.current = null;
      closing.current = false;
      setOpened(false);
      setQuery('');
    }, CLOSE_MS);
  };

  const type = (text: string) => {
    setQuery(text);
    onSearch?.(text);
  };

  // autoPlay: every step is timed from the start of its loop, so the period never drifts.
  const perform = useEffectEvent((step: number) => {
    if (step === 0) press(pillPress);
    else if (step === 1) openUp(false);
    else if (step < 2 + script.length) type(script.slice(0, step - 1));
    else if (step === 2 + script.length) press(cancelPress);
    else close(false);
  });
  useEffect(() => {
    if (!playing) return;
    const times = [OPEN_AT - PRESS, OPEN_AT];
    for (let i = 0; i < script.length; i += 1) times.push(TYPE_AT + i * KEY);
    times.push(CLOSE_AT - PRESS, CLOSE_AT);
    let origin = Date.now();
    let index = 0;
    let timer: ReturnType<typeof setTimeout>;
    const wait = () => {
      const at = index < times.length ? times[index] : LOOP;
      timer = setTimeout(
        () => {
          if (index < times.length) {
            perform(index);
            index += 1;
          } else {
            origin += LOOP * 1000;
            index = 0;
          }
          wait();
        },
        Math.max(0, origin + at * 1000 - Date.now()),
      );
    };
    wait();
    return () => clearTimeout(timer);
  }, [playing, script]);

  // A touch takes over; once the search is closed and left alone, the loop starts over.
  const takeOver = () => {
    if (playing) setPlaying(false);
  };
  const resume = useEffectEvent(() => setPlaying(true));
  useEffect(() => {
    if (!autoPlay || playing || opened) return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, playing, opened]);

  const homeStyle = useAnimatedStyle(() => {
    const k = clamp01(t.get() * 1.3);
    return { transform: [{ scale: reducedMotion ? 1 : 1 - HOME_SINK * k }] };
  });
  const veilStyle = useAnimatedStyle(() => ({ opacity: clamp01(t.get() * 1.6) }));
  // The pill's own words ride its left edge and fade before it stretches.
  const labelStyle = useAnimatedStyle(() => {
    const p = pillAt(t.get(), closed, open, reducedMotion);
    return {
      opacity: clamp01(1 - t.get() * 5) * (1 - pillPress.get() * 0.2),
      transform: [{ translateX: p.left - closed.x }, { translateY: (p.top + p.bottom) / 2 - (closed.y + closed.h / 2) }],
    };
  });
  // The magnifier rides its own drop.
  const iconStyle = useAnimatedStyle(() => {
    const p = pillAt(t.get(), closed, open, reducedMotion);
    return {
      opacity: reducedMotion ? Math.abs(1 - 2 * t.get()) : 1,
      transform: [{ translateX: p.dropX - 10 }, { translateY: p.dropY - 10 }],
    };
  });
  const fieldStyle = useAnimatedStyle(() => ({ opacity: clamp01((t.get() - 0.72) / 0.28) }));
  const cancelStyle = useAnimatedStyle(() => {
    const k = clamp01((t.get() - 0.45) / 0.55);
    return {
      opacity: k,
      transform: [{ translateX: reducedMotion ? 0 : (1 - k) * 24 }, { scale: 1 - cancelPress.get() * 0.08 }],
    };
  });
  const sheetStyle = useAnimatedStyle(() => {
    const k = clamp01((t.get() - 0.5) / 0.5);
    return { opacity: k, transform: [{ translateY: reducedMotion ? 0 : (1 - k) * 18 }] };
  });

  return (
    <View style={styles.screen}>
      {/* The home page. */}
      <Animated.View style={[StyleSheet.absoluteFill, homeStyle]} pointerEvents={opened ? 'none' : 'auto'}>
        <View style={[styles.topBar, { top: insets.top + 12 }]}>
          <Text style={styles.wordmark}>ATELIER NORD</Text>
          <View style={styles.bag}>
            <SymbolView name="bag" size={18} tintColor={INK} weight="medium" />
            <View style={styles.badge}>
              <Text style={styles.badgeText}>2</Text>
            </View>
          </View>
        </View>
        <Text style={[styles.title, { top: insets.top + TITLE_TOP }]}>{'Walk soft,\nsmell of rain.'}</Text>
        <View style={[styles.hero, { top: insets.top + HERO_TOP, height: HERO_HEIGHT }]}>
          <Image source={photo(PIECES[4].photo, 900)} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
          <LinearGradient colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.5)']} style={styles.heroShade} />
          <View style={styles.heroText}>
            <Text style={styles.heroKicker}>NEW THIS WEEK</Text>
            <View style={styles.heroRow}>
              <Text style={styles.heroName}>{PIECES[4].name}</Text>
              <Text style={styles.heroPrice}>{PIECES[4].price}</Text>
            </View>
          </View>
        </View>
        <View style={[styles.section, { top: insets.top + HERO_TOP + HERO_HEIGHT + 24 }]}>
          <Text style={styles.sectionTitle}>Trending now</Text>
          <Text style={styles.sectionLink}>See all</Text>
        </View>
        <View style={[styles.trending, { top: insets.top + HERO_TOP + HERO_HEIGHT + 60, height: screenH - (insets.top + HERO_TOP + HERO_HEIGHT + 60) }]}>
          {[PIECES[0], PIECES[8]].map((piece) => (
            <View key={piece.id} style={{ width: tileW }}>
              <Image source={photo(piece.photo, 500)} style={styles.trendingPhoto} contentFit="cover" transition={200} />
              <Text style={styles.tileName} numberOfLines={1}>{piece.name}</Text>
              <Text style={styles.tileMeta}>{piece.price}</Text>
            </View>
          ))}
        </View>
      </Animated.View>

      {/* The blur and veil the search opens over. */}
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, veilStyle]}>
        <BlurView style={StyleSheet.absoluteFill} intensity={34} tint="light" />
        <View style={[StyleSheet.absoluteFill, styles.veil]} />
      </Animated.View>

      {/* Under the bar: recent searches, or the results. */}
      {opened && (
        <Animated.View style={[styles.sheet, { top: open.y + open.h + 10 }, sheetStyle]}>
          {query.length === 0 ? (
            <Animated.View style={styles.suggestions} exiting={FadeOut.duration(150)}>
              <Text style={styles.label}>RECENT</Text>
              <View style={styles.chips}>
                {RECENT.map((word, i) => (
                  <Animated.View key={word} entering={reducedMotion ? FadeIn : CHIP_IN[i]}>
                    <Pressable
                      style={({ pressed }) => [styles.chip, pressed && styles.chipPressed]}
                      onPress={() => {
                        takeOver();
                        type(word);
                      }}
                      accessibilityRole="button"
                    >
                      <SymbolView name="clock.arrow.circlepath" size={13} tintColor={MUTED} />
                      <Text style={styles.chipText}>{word}</Text>
                    </Pressable>
                  </Animated.View>
                ))}
              </View>
              <Text style={[styles.label, styles.labelGap]}>POPULAR</Text>
              {POPULAR.map((name, i) => (
                <Animated.View key={name} entering={reducedMotion ? FadeIn : ROW_IN[i]}>
                  <Pressable
                    style={({ pressed }) => [styles.popular, pressed && styles.popularPressed]}
                    onPress={() => {
                      takeOver();
                      type(name.split(' ')[0]);
                    }}
                    accessibilityRole="button"
                  >
                    <Text style={styles.rank}>{String(i + 1).padStart(2, '0')}</Text>
                    <Text style={styles.popularName}>{name}</Text>
                    <SymbolView name="arrow.up.left" size={13} tintColor={MUTED} />
                  </Pressable>
                </Animated.View>
              ))}
            </Animated.View>
          ) : (
            <ScrollView
              style={styles.results}
              contentContainerStyle={{ paddingBottom: insets.bottom + 40 }}
              keyboardShouldPersistTaps="handled"
              keyboardDismissMode="on-drag"
              showsVerticalScrollIndicator={false}
            >
              <Animated.Text layout={FLOW} style={styles.count}>
                {results.length === 0 ? `Nothing for “${query}”` : `${results.length} ${results.length === 1 ? 'piece' : 'pieces'} for “${query}”`}
              </Animated.Text>
              {results.length === 0 && (
                <Animated.Text entering={FadeIn.duration(200)} style={styles.empty}>
                  Try a shape, a scent or a colour.
                </Animated.Text>
              )}
              <View style={styles.grid}>
                {results.map((piece, i) => (
                  <Tile key={piece.id} piece={piece} width={tileW} entering={reducedMotion ? FadeIn : TILE_IN[i]} still={reducedMotion} />
                ))}
              </View>
            </ScrollView>
          )}
        </Animated.View>
      )}

      {/* The ink pill, and what rides on it. */}
      <LiquidPill width={screenW} height={canvasH} closed={closed} open={open} t={t} press={pillPress} still={reducedMotion} color={INK} />
      <Animated.View pointerEvents="none" style={[styles.icon, iconStyle]}>
        <SymbolView name="magnifyingglass" size={18} tintColor={CREAM} weight="semibold" />
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.pillLabel, { left: closed.x + ICON_INSET + 20, top: closed.y, height: closed.h }, labelStyle]}>
        <Text style={styles.pillText}>{`Search ${PIECES.length} pieces`}</Text>
      </Animated.View>
      {!opened && (
        <Pressable
          style={[styles.hit, { left: closed.x, top: closed.y, width: closed.w, height: closed.h }]}
          onPressIn={() => pillPress.set(withTiming(1, { duration: 90 }))}
          onPressOut={() => pillPress.set(withTiming(0, { duration: 200 }))}
          onPress={() => {
            takeOver();
            openUp(true);
          }}
          accessibilityRole="search"
          accessibilityLabel={`Search ${PIECES.length} pieces`}
        />
      )}
      {opened && (
        <Animated.View style={[styles.field, { left: open.x + ICON_INSET + 20, top: open.y, width: open.w - ICON_INSET - 20 - 10, height: open.h }, fieldStyle]}>
          <TextInput
            ref={input}
            style={styles.input}
            value={query}
            onChangeText={(text) => {
              takeOver();
              type(text);
            }}
            onFocus={() => {
              takeOver();
              setFocused(true);
            }}
            onBlur={() => setFocused(false)}
            placeholder="Sneakers, scents, sizes"
            placeholderTextColor="rgba(246, 241, 231, 0.45)"
            selectionColor={CREAM}
            keyboardAppearance="dark"
            returnKeyType="search"
            autoCorrect={false}
            autoCapitalize="none"
            accessibilityLabel="Search the shop"
          />
          {playing && !focused && (
            <View pointerEvents="none" style={styles.mirror}>
              <Text style={[styles.input, styles.ghost]}>{query}</Text>
              <Animated.View
                style={[
                  styles.caret,
                  {
                    animationName: { '0%': { opacity: 1 }, '45%': { opacity: 1 }, '55%': { opacity: 0 }, '100%': { opacity: 0 } },
                    animationDuration: '1s',
                    animationIterationCount: 'infinite',
                  },
                ]}
              />
            </View>
          )}
          {query.length > 0 && (
            <Pressable
              style={styles.clear}
              hitSlop={10}
              onPress={() => {
                takeOver();
                type('');
              }}
              accessibilityRole="button"
              accessibilityLabel="Clear the search"
            >
              <SymbolView name="xmark.circle.fill" size={17} tintColor="rgba(246, 241, 231, 0.55)" />
            </Pressable>
          )}
        </Animated.View>
      )}
      {opened && (
        <Animated.View style={[styles.cancel, { top: open.y, height: open.h, width: CANCEL_WIDTH }, cancelStyle]}>
          <Pressable
            style={styles.cancelHit}
            onPress={() => {
              takeOver();
              close(true);
            }}
            accessibilityRole="button"
          >
            <Text style={styles.cancelText}>Cancel</Text>
          </Pressable>
        </Animated.View>
      )}
    </View>
  );
}

type TileProps = {
  piece: Piece;
  width: number;
  entering: ComponentProps<typeof Animated.View>['entering'];
  still: boolean;
};

/** One result: it pops in, flows to its new place when the matches change, and fades when it no longer matches. */
function Tile({ piece, width, entering, still }: TileProps) {
  return (
    <Animated.View style={{ width }} entering={entering} exiting={FadeOut.duration(160)} layout={still ? undefined : FLOW}>
      <Pressable style={({ pressed }) => [styles.tile, { transform: [{ scale: pressed ? 0.97 : 1 }] }]} accessibilityRole="button" accessibilityLabel={`${piece.name}, ${piece.price}`}>
        <Image source={photo(piece.photo, 500)} style={[styles.tilePhoto, { height: TILE_PHOTO }]} contentFit="cover" transition={160} />
        <Text style={styles.tileName} numberOfLines={1}>
          {piece.name}
        </Text>
        <View style={styles.tileRow}>
          <Text style={styles.tileMeta} numberOfLines={1}>
            {piece.kind}
          </Text>
          <Text style={styles.tilePrice}>{piece.price}</Text>
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAPER,
    overflow: 'hidden',
  },
  topBar: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  wordmark: {
    color: INK,
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 3.2,
  },
  bag: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: CARD,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 8px rgba(23, 21, 15, 0.08)',
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
    minWidth: 15,
    height: 15,
    borderRadius: 7.5,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: {
    color: CREAM,
    fontSize: 9,
    fontWeight: '700',
  },
  title: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    color: INK,
    fontSize: 36,
    lineHeight: 40,
    fontWeight: '700',
    letterSpacing: -1.2,
  },
  hero: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    borderRadius: 26,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#e5dfd4',
  },
  heroShade: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: '55%',
  },
  heroText: {
    position: 'absolute',
    left: 18,
    right: 18,
    bottom: 16,
    gap: 3,
  },
  heroKicker: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 10.5,
    fontWeight: '700',
    letterSpacing: 1.8,
  },
  heroRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  heroName: {
    color: '#ffffff',
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.4,
  },
  heroPrice: {
    color: '#ffffff',
    fontSize: 17,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  section: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  sectionTitle: {
    color: INK,
    fontSize: 19,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  sectionLink: {
    color: MUTED,
    fontSize: 14,
    fontWeight: '500',
  },
  trending: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    flexDirection: 'row',
    gap: GRID_GAP,
    overflow: 'hidden',
  },
  trendingPhoto: {
    width: '100%',
    height: 172,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: '#e5dfd4',
  },
  veil: {
    backgroundColor: 'rgba(243, 240, 234, 0.66)',
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  suggestions: {
    paddingHorizontal: SIDE,
    paddingTop: 12,
  },
  label: {
    color: MUTED,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.6,
    marginBottom: 12,
  },
  labelGap: {
    marginTop: 30,
    marginBottom: 4,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 36,
    paddingHorizontal: 14,
    borderRadius: 18,
    backgroundColor: CARD,
    boxShadow: '0 1px 3px rgba(23, 21, 15, 0.08)',
  },
  chipPressed: {
    backgroundColor: '#ebe6dc',
  },
  chipText: {
    color: INK,
    fontSize: 15,
    fontWeight: '500',
  },
  popular: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    height: 52,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(23, 21, 15, 0.12)',
  },
  popularPressed: {
    backgroundColor: 'rgba(23, 21, 15, 0.04)',
  },
  rank: {
    color: MUTED,
    fontSize: 13,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    width: 22,
  },
  popularName: {
    flex: 1,
    color: INK,
    fontSize: 17,
    fontWeight: '500',
  },
  results: {
    flex: 1,
    paddingHorizontal: SIDE,
  },
  count: {
    color: MUTED,
    fontSize: 13,
    fontWeight: '600',
    marginTop: 12,
    marginBottom: 14,
  },
  empty: {
    color: INK,
    fontSize: 17,
    fontWeight: '500',
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    columnGap: GRID_GAP,
    rowGap: 20,
  },
  tile: {
    gap: 2,
  },
  tilePhoto: {
    width: '100%',
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: '#e5dfd4',
    marginBottom: 8,
  },
  tileName: {
    color: INK,
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: -0.2,
    marginTop: 8,
  },
  tileRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  tileMeta: {
    flexShrink: 1,
    color: MUTED,
    fontSize: 13,
  },
  tilePrice: {
    color: INK,
    fontSize: 13,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  icon: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 20,
    height: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillLabel: {
    position: 'absolute',
    justifyContent: 'center',
  },
  pillText: {
    color: CREAM,
    fontSize: 15,
    fontWeight: '600',
  },
  hit: {
    position: 'absolute',
  },
  field: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
  },
  input: {
    flex: 1,
    height: '100%',
    color: CREAM,
    fontSize: 17,
    fontWeight: '500',
  },
  mirror: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
  },
  ghost: {
    flex: 0,
    height: undefined,
    opacity: 0,
  },
  caret: {
    width: 2,
    height: 21,
    borderRadius: 1,
    marginLeft: 1,
    backgroundColor: CREAM,
  },
  clear: {
    paddingLeft: 8,
  },
  cancel: {
    position: 'absolute',
    right: 16,
  },
  cancelHit: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelText: {
    color: INK,
    fontSize: 17,
    fontWeight: '600',
  },
});
