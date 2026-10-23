import { useFonts } from 'expo-font';
import MaskedView from '@react-native-masked-view/masked-view';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useState } from 'react';
import { Image, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedReaction, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Defs, Ellipse, Path, RadialGradient, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useCalmMotion } from './a11y';

import { BarsIcon, BookmarkOutline, ChevronDown, ChevronLeft, GridPlus, Globe, LocateArrow, MapBookmark, MapStar, MapThumb, Pin, ShareIcon, Shield } from './icons';
import { type Cluster, CLUSTERS, GLYPH_PATHS, GLYPH_SIZE, type GlyphPath } from './map-markers';
import { BEAM, CLUSTER, CONTROLS_FADE, discAt, easeOut, GRAB, glyphProgress, lerp, LOOP, PRESS, project, RIPPLE, RISE, seg, SHADOW, STAND, standLinear, standOf, TILT, tiltOf } from './motion';
import {
  back,
  badge as badgeBox,
  bubble,
  chips,
  colors,
  copy,
  FONT,
  layout,
  list,
  locate,
  depth,
  depthRank,
  fadedChip,
  mapLabels,
  marker,
  markers,
  METRICS,
  motionColors,
  radius,
  roll as rollBox,
  shield,
  sheet,
  space,
  tick,
  tiles,
  topBlur,
  type as T,
  type TextStyleSpec,
  VISITED_KEY,
} from './theme';
import { HIT, HIT_SLOP, usePlacesMotion } from './use-places-motion';

export { LOOP };

type Props = {
  /**
   * Plays the loop: the sheet rises to its half detent, the loose map glyphs gather into count bubbles,
   * the first café is ticked visited and unticked, the sheet settles back. Touch takes over at once
   * (drag the sheet, tap the checkbox or "you visited", tap a café) and hands back 2 s after the last touch.
   * Stopped under Reduce Motion or a screen reader (followed live).
   */
  autoPlay?: boolean;
};

const FONT_FILES = {
  [FONT.archivo]: require('./assets/Archivo-Regular.ttf'),
  [FONT.archivoMedium]: require('./assets/Archivo-Medium.ttf'),
  [FONT.archivoSemi]: require('./assets/Archivo-SemiBold.ttf'),
  [FONT.archivoBold]: require('./assets/Archivo-ExtraBold.ttf'),
  [FONT.barlow]: require('./assets/Barlow-Regular.ttf'),
  [FONT.barlowSemi]: require('./assets/Barlow-SemiBold.ttf'),
  [FONT.barlowItalic]: require('./assets/Barlow-Italic.ttf'),
};

const IMG = {
  map: require('./assets/map.jpg'),
  cafe: require('./assets/list-cafe.jpg'),
  bar: require('./assets/list-bar.jpg'),
  avatar: require('./assets/avatar-sam.jpg'),
};

const AnimatedPath = Animated.createAnimatedComponent(Path);
const VISITED = markers.findIndex((m) => m.key === VISITED_KEY);
/** Map image height (ref px): the OSM render is 1206 × 1650. */
const MAP_H = 1650;

type PinSpot = {
  m: (typeof markers)[number];
  i: number;
  cx: number;
  cy: number;
  rank: number;
};
/** Each café's ground anchor = its disc centre on the flat map (the pin's still). */
const PINS: PinSpot[] = markers.map((m, i) => ({
  m,
  i,
  cx: m.x - marker.discDx,
  cy: m.base - marker.discDy,
  rank: depthRank[m.key],
}));
const R = marker.disc / 2;
const ACTIVATE = [{ name: 'activate' }];
const SHEET_ACTIONS = [{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }];

export function SavedPlacesMap({ autoPlay = false }: Props) {
  const { width } = useWindowDimensions();
  const [fontsReady] = useFonts(FONT_FILES);
  const { reduceMotion: reduced, screenReader, calm } = useCalmMotion();
  const u = width / 1206; // ref px → dp
  const d = (px: number) => px * u;
  const p = (pt: number) => (pt * width) / 402; // tokens (pt) → dp
  const k = width / 402;

  const { motion, gesture, actions } = usePlacesMotion({ autoPlay, calm, reduced, u, k });
  const { sheet: sp, clus, fill, draw, roll, badge, visitPop, tapIdx, tapPop, press, grab, pressK } = motion;

  /* ---- Screen reader state: the visited tick and the sheet's detent (tracked only while it is on) ---- */
  const [visited, setVisited] = useState(false);
  const [expanded, setExpanded] = useState(false);
  // A reaction runs once on (re)registration with prev = null, so turning the reader on syncs both at once.
  useAnimatedReaction(
    () => fill.get() > 0.5,
    (v, prev) => {
      if (screenReader && v !== prev) scheduleOnRN(setVisited, v);
    },
  );
  useAnimatedReaction(
    () => sp.get() > 0.5,
    (v, prev) => {
      if (screenReader && v !== prev) scheduleOnRN(setExpanded, v);
    },
  );
  const visitedOf = copy.visitedDen.slice(1);
  /** Top of the sheet at the half detent (ref px): markers under it are hidden from the screen reader. */
  const raisedTop = sheet.top * 3 - RISE * 3;

  const t = { u, ready: fontsReady };
  const S = (s: TextStyleSpec) => ({
    size: s.size,
    font: s.font,
    color: s.color,
  });

  /* ---- Animated styles ---- */
  const groundStyle = useAnimatedStyle(() => {
    const q = reduced ? 0 : tiltOf(sp.get());
    return {
      transform: [{ translateY: TILT.lift * q * u }, { perspective: TILT.perspective * u }, { rotateX: `${TILT.pitch * q}deg` }, { scale: 1 + (TILT.scale - 1) * q }],
    };
  });
  const fogStyle = useAnimatedStyle(() => ({
    opacity: reduced ? 0 : easeOut(seg(tiltOf(sp.get()), TILT.fog[0], TILT.fog[1])),
  }));
  const controlsStyle = useAnimatedStyle(() => ({
    opacity: 1 - easeOut(seg(sp.get(), CONTROLS_FADE[0], CONTROLS_FADE[1])),
    transform: [{ translateY: -RISE * k * sp.get() }],
  }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -RISE * k * sp.get() }],
  }));
  const grabStyle = useAnimatedStyle(() => ({
    transform: [{ scaleX: lerp(1, GRAB.scale, grab.get()) }],
  }));
  const grabInkStyle = useAnimatedStyle(() => ({ opacity: grab.get() }));
  const tileStyle = useAnimatedStyle(() => ({
    transform: [
      {
        scale: pressK.get() === HIT.tile ? 1 - (1 - PRESS.tile) * press.get() : 1,
      },
    ],
  }));
  const checkboxStyle = useAnimatedStyle(() => ({
    transform: [
      {
        scale: pressK.get() === HIT.checkbox ? 1 - (1 - PRESS.checkbox) * press.get() : 1,
      },
    ],
  }));
  const fillStyle = useAnimatedStyle(() => ({
    opacity: fill.get(),
    transform: [{ scale: reduced ? 1 : lerp(0.9, 1, fill.get()) }],
  }));
  const tickProps = useAnimatedProps(() => ({
    strokeDashoffset: tick.length * (1 - draw.get()),
  }));
  const rollStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: reduced ? 0 : -roll.get() * rollBox.row * u }],
  }));
  const zeroStyle = useAnimatedStyle(() => ({
    opacity: reduced ? 1 - roll.get() : 1,
  }));
  const oneStyle = useAnimatedStyle(() => ({
    opacity: reduced ? roll.get() : 1,
  }));

  const emoji = (glyph: string, cx: number, cy: number, sizePt: number, key?: string) => (
    <Text
      key={key}
      allowFontScaling={false}
      style={{
        position: 'absolute',
        left: d(cx) - p(30),
        top: d(cy) - p(30),
        width: p(60),
        height: p(60),
        textAlign: 'center',
        textAlignVertical: 'center',
        lineHeight: p(60),
        fontSize: p(sizePt),
      }}>
      {glyph}
    </Text>
  );

  const tileFill = (i: number) =>
    i === 0 ? (
      <View
        style={{
          ...abs(d(tiles.xs[0]), d(tiles.y), d(tiles.w), d(tiles.h)),
          borderRadius: p(radius.tile),
          backgroundColor: colors.tile0,
          borderWidth: d(2),
          borderColor: colors.tile0Border,
        }}
      />
    ) : (
      <View
        style={{
          ...abs(d(tiles.xs[i]), d(tiles.y), d(tiles.w), d(tiles.h)),
          borderRadius: p(radius.tile),
          backgroundColor: colors.tile,
          shadowColor: colors.shadow,
          shadowOpacity: 0.2,
          shadowRadius: d(20),
          shadowOffset: { width: 0, height: d(6) },
          overflow: 'visible',
        }}>
        <LinearGradient colors={['rgba(253,255,255,0)', 'rgba(237,237,245,0.95)']} locations={[0.82, 1]} style={{ ...StyleSheet.absoluteFill, borderRadius: p(radius.tile) }} />
      </View>
    );

  /** A list card; `y` is its top in sheet coordinates (ref px). The first card is the pin's. */
  const card = (y: number, title: string, sub: string, photo: ReactNode, key: string, checkbox?: ReactNode) => {
    const dy = y - layout.card.y;
    return (
      <View key={key} pointerEvents="none" style={StyleSheet.absoluteFill}>
        <View
          style={{
            ...abs(d(layout.card.x), d(y), d(layout.card.w), d(list.cardH)),
            borderRadius: p(radius.card),
            backgroundColor: colors.card,
            shadowColor: colors.shadow,
            shadowOpacity: 0.3,
            shadowRadius: d(18),
            shadowOffset: { width: 0, height: d(4) },
          }}
        />
        {photo}
        <Txt {...t} x={layout.listTitleX} base={layout.listTitleBase + dy} {...S(T.listTitle)}>
          {title}
        </Txt>
        <Txt {...t} x={layout.listSubX} base={layout.listSubBase + dy} {...S(T.listSub)}>
          {sub}
        </Txt>
        {checkbox ?? (
          <View
            style={{
              ...abs(d(layout.checkbox.x), d(layout.checkbox.y + dy), d(layout.checkbox.d), d(layout.checkbox.d)),
              borderRadius: d(7),
              borderWidth: d(3),
              borderColor: colors.checkbox,
            }}
          />
        )}
        <Image
          source={IMG.avatar}
          style={{
            ...abs(d(layout.listAvatar.x), d(layout.listAvatar.y + dy), d(layout.listAvatar.d), d(layout.listAvatar.d)),
            borderRadius: d(layout.listAvatar.d / 2),
          }}
        />
      </View>
    );
  };

  const cb = layout.checkbox;
  const firstCheckbox = (
    <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: [d(cb.x + cb.d / 2), d(cb.y + cb.d / 2), 0] }, checkboxStyle]}>
      <View
        style={{
          ...abs(d(cb.x), d(cb.y), d(cb.d), d(cb.d)),
          borderRadius: d(7),
          borderWidth: d(3),
          borderColor: colors.checkbox,
        }}
      />
      <Animated.View style={[abs(d(cb.x), d(cb.y), d(cb.d), d(cb.d)), { borderRadius: d(7), backgroundColor: motionColors.check }, fillStyle]}>
        <Svg width={d(cb.d)} height={d(cb.d)} viewBox={`0 0 ${cb.d} ${cb.d}`}>
          <AnimatedPath
            d={tick.path}
            stroke={motionColors.checkInk}
            strokeWidth={tick.stroke}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
            strokeDasharray={tick.length}
            animatedProps={tickProps}
          />
        </Svg>
      </Animated.View>
    </Animated.View>
  );

  const zoomedPhoto = (src: number, zoom: number, ox: number, oy: number, y: number) => {
    const s = layout.listPhoto.d;
    return (
      <View
        style={{
          ...abs(d(layout.listPhoto.x), d(y + layout.listPhoto.y - layout.card.y), d(s), d(s)),
          borderRadius: d(layout.listPhoto.r),
          overflow: 'hidden',
        }}>
        <Image source={src} style={abs(d(ox * s * zoom), d(oy * s * zoom), d(s * zoom), d(s * zoom))} />
      </View>
    );
  };

  return (
    <GestureHandlerRootView style={styles.screen}>
      <GestureDetector gesture={gesture}>
        <View style={styles.screen} collapsable={false}>
          {/* ---- Sky: only seen above the horizon when the map pitches into 3D ---- */}
          <LinearGradient
            pointerEvents="none"
            colors={depth.sky}
            style={{
              position: 'absolute',
              left: 0,
              top: 0,
              width,
              height: d(MAP_H),
            }}
          />

          {/* ---- Ground: the map plane (pitches into 3D with the sheet). Its own flattening layer: on iOS, siblings
               with a 3D transform are depth-sorted, so the receding half would otherwise sink behind the sky. ---- */}
          <View pointerEvents="none" collapsable={false} style={StyleSheet.absoluteFill}>
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { transformOrigin: [d(TILT.originX), d(TILT.originY), 0] }, groundStyle]}>
              <Image
                source={IMG.map}
                resizeMode="cover"
                style={{
                  position: 'absolute',
                  left: 0,
                  top: 0,
                  width,
                  height: d(MAP_H),
                }}
              />
              <Txt {...t} x={mapLabels.north.x} base={mapLabels.north.base} size={mapLabels.north.size} font={FONT.barlow} color={mapLabels.north.color} opacity={mapLabels.north.opacity}>
                {mapLabels.north.text}
              </Txt>
              <Txt {...t} x={mapLabels.union.x} base={mapLabels.union.base} size={mapLabels.union.size} font={FONT.barlow} color={mapLabels.union.color}>
                {mapLabels.union.text}
              </Txt>
              <Txt {...t} x={mapLabels.jersey.x} base={mapLabels.jersey.base} size={mapLabels.jersey.size} font={FONT.barlow} color={mapLabels.jersey.color}>
                {mapLabels.jersey.text}
              </Txt>
              <Txt {...t} x={mapLabels.manhattan.x} base={mapLabels.manhattan.base} size={mapLabels.manhattan.size} font={FONT.barlow} color={mapLabels.manhattan.color} ls={mapLabels.manhattan.ls}>
                {mapLabels.manhattan.text}
              </Txt>
              <Txt {...t} x={mapLabels.brooklyn.x} base={mapLabels.brooklyn.base} size={mapLabels.brooklyn.size} font={FONT.barlow} color={mapLabels.brooklyn.color} ls={mapLabels.brooklyn.ls}>
                {mapLabels.brooklyn.text}
              </Txt>
              <Txt {...t} cx={mapLabels.east.cx} base={mapLabels.east.base} size={mapLabels.east.size} font={FONT.barlow} color={mapLabels.east.color} ls={mapLabels.east.ls}>
                {mapLabels.east.text}
              </Txt>
              <Txt {...t} cx={mapLabels.york.cx} base={mapLabels.york.base} size={mapLabels.york.size} font={FONT.barlow} color={mapLabels.york.color} ls={mapLabels.york.ls}>
                {mapLabels.york.text}
              </Txt>
              <Txt {...t} x={mapLabels.upper.x} base={mapLabels.upper.base} size={mapLabels.upper.size} font={FONT.barlowItalic} color={mapLabels.upper.color} ls={mapLabels.upper.ls}>
                {mapLabels.upper.text}
              </Txt>
              <Txt {...t} x={mapLabels.bay.x} base={mapLabels.bay.base} size={mapLabels.bay.size} font={FONT.barlowItalic} color={mapLabels.bay.color} ls={mapLabels.bay.ls}>
                {mapLabels.bay.text}
              </Txt>

              {/* highway shield */}
              <View style={abs(d(shield.x), d(shield.y), d(shield.w), d(shield.h))}>
                <Shield w={d(shield.w)} h={d(shield.h)} blue={colors.shieldBlue} red={colors.shieldRed} />
                <Text
                  allowFontScaling={false}
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    top: d(21),
                    textAlign: 'center',
                    fontFamily: fontsReady ? FONT.barlowSemi : undefined,
                    fontSize: d(shield.size),
                    lineHeight: d(30),
                    color: '#fff',
                  }}>
                  {shield.text}
                </Text>
              </View>

              {/* long contact shadows and landing ripples, painted on the ground */}
              {PINS.map((pin) => (
                <GroundMarks key={pin.m.key} pin={pin} sp={sp} u={u} reduced={reduced} />
              ))}
            </Animated.View>
          </View>

          {/* haze over the horizon */}
          <Animated.View
            pointerEvents="none"
            style={[
              {
                position: 'absolute',
                left: 0,
                top: 0,
                width,
                height: d(depth.fog.h),
              },
              fogStyle,
            ]}>
            <LinearGradient colors={depth.fog.colors} locations={depth.fog.locations} style={StyleSheet.absoluteFill} />
          </Animated.View>

          {/* ---- Billboards: stand upright over their ground spots. Each café carries its screen-reader element in its
               PinGroup (it moves with the pin, no extra animated view); the drawn parts are hidden from the reader. ---- */}
          <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
            <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
            {PINS.map((pin) => (
              <PinRig key={pin.m.key} pin={pin} sp={sp} visitPop={visitPop} u={u} reduced={reduced} />
            ))}
            {GLYPH_PATHS.map((g, i) => (
              <Glyph key={i} g={g} c={clus} sp={sp} u={u} reduced={reduced} />
            ))}
            </View>
            {PINS.map((pin) => {
              const { m, i, cx, cy } = pin;
              const covered = expanded && cy + R > raisedTop;
              return (
                <PinGroup key={m.key} pin={pin} sp={sp} u={u} reduced={reduced}>
                  <View
                    accessible
                    accessibilityRole="button"
                    accessibilityElementsHidden={covered}
                    importantForAccessibility={covered ? 'no-hide-descendants' : 'yes'}
                    accessibilityLabel={`${m.name}, ${m.sub}, ${m.tag}${i === VISITED && visited ? `, ${copy.a11yVisited}` : ''}`}
                    accessibilityHint={copy.a11yMarkerHint}
                    accessibilityActions={ACTIVATE}
                    onAccessibilityAction={(e) => {
                      if (e.nativeEvent.actionName === 'activate') actions.tapMarker(i);
                    }}
                    style={abs(d(cx - R), d(cy - R), d(m.x + marker.hitW - (cx - R)), d(2 * R))}
                  />
                  <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
                  <MarkerDisc i={i} cx={cx} cy={cy} d={d} visitPop={visitPop} tapIdx={tapIdx} tapPop={tapPop} reduced={reduced}>
                    <View
                      style={{
                        ...abs(d(cx - marker.disc / 2), d(cy - marker.disc / 2), d(marker.disc), d(marker.disc)),
                        borderRadius: d(marker.disc / 2),
                        backgroundColor: '#FBFBFA',
                        shadowColor: '#3E5568',
                        shadowOpacity: 0.28,
                        shadowRadius: d(10),
                        shadowOffset: { width: 0, height: d(3) },
                      }}
                    />
                    {emoji(m.emoji, cx, cy, m.emojiSize)}
                    {i === VISITED ? <CheckBadge cx={cx} cy={cy} d={d} badge={badge} reduced={reduced} /> : null}
                  </MarkerDisc>
                  <View style={abs(d(m.x - marker.iconDx - 3), d(m.base - marker.bookmarkDy - 3), d(28), d(32))}>
                    {m.tag === 'saved' ? <MapBookmark w={d(28)} h={d(32)} color={colors.bookmark} /> : <MapThumb w={d(34)} h={d(38)} color={colors.thumb} />}
                  </View>
                  <Txt {...t} x={m.x} base={m.base - marker.savedDy} size={marker.subSize} font={FONT.barlowItalic} color={colors.markerSub}>
                    {m.tag}
                  </Txt>
                  <Txt {...t} x={m.x} base={m.base} size={marker.nameSize} font={FONT.barlowSemi} color={colors.markerInk}>
                    {m.name}
                  </Txt>
                  <Txt {...t} x={m.x} base={m.base + marker.subDy} size={marker.subSize} font={FONT.barlowItalic} color={colors.markerSub}>
                    {m.sub}
                  </Txt>
                  <Bubble cl={CLUSTERS[i]} c={clus} u={u} reduced={reduced} ready={fontsReady} />
                  </View>
                </PinGroup>
              );
            })}
          </View>

          {/* faded top-right control (under the blur, like the pin) */}
          <View
            pointerEvents="none"
            style={{
              ...abs(d(fadedChip.x), d(fadedChip.y), d(fadedChip.w), d(fadedChip.h)),
              borderRadius: d(14),
              backgroundColor: 'rgba(255,255,255,0.45)',
            }}
          />

          {/* ---- The pin's progressive blur: a blurred copy masked by a gradient, and a white fade ---- */}
          <MaskedView
            pointerEvents="none"
            style={{
              position: 'absolute',
              left: 0,
              top: 0,
              width,
              height: d(topBlur.h),
            }}
            maskElement={<LinearGradient colors={['#000', '#000', 'rgba(0,0,0,0)']} locations={[0, topBlur.solid / topBlur.h, 1]} style={{ width, height: d(topBlur.h) }} />}>
            <BlurView intensity={topBlur.intensity} tint="light" style={{ width, height: d(topBlur.h) }} />
          </MaskedView>
          <LinearGradient
            pointerEvents="none"
            colors={topBlur.fade.colors}
            locations={topBlur.fade.locations}
            style={{
              position: 'absolute',
              left: 0,
              top: 0,
              width,
              height: d(topBlur.fade.h),
            }}
          />

          {/* back + badge (sharp, above the blur) */}
          <View pointerEvents="none" style={abs(d(back.chevron.x), d(back.chevron.y), d(back.chevron.w), d(back.chevron.h))}>
            <ChevronLeft w={d(back.chevron.w)} h={d(back.chevron.h)} stroke={2.4} />
          </View>
          <View
            pointerEvents="none"
            style={{
              ...abs(d(back.badge.x), d(back.badge.y), d(back.badge.w), d(back.badge.h)),
              borderRadius: p(radius.badge),
              backgroundColor: colors.badge,
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <View
              style={{
                width: d(38),
                height: d(38),
                borderRadius: d(19),
                backgroundColor: '#F4F4F4',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
              <Text
                allowFontScaling={false}
                style={{
                  fontFamily: fontsReady ? FONT.barlowSemi : undefined,
                  fontSize: d(26),
                  lineHeight: d(30),
                  color: '#222',
                }}>
                3
              </Text>
            </View>
          </View>

          {/* ---- Floating controls: ride the sheet, fade as it rises ---- */}
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, controlsStyle]}>
            <View
              style={{
                ...abs(d(locate.cx - locate.d / 2), d(locate.cy - locate.d / 2), d(locate.d), d(locate.d)),
                borderRadius: d(locate.d / 2),
                backgroundColor: '#FFFFFF',
                shadowColor: '#3E5568',
                shadowOpacity: 0.28,
                shadowRadius: d(14),
                shadowOffset: { width: 0, height: d(5) },
              }}
            />
            <View style={abs(d(locate.arrow.cx - locate.arrow.s / 2), d(locate.arrow.cy - locate.arrow.s / 2), d(locate.arrow.s), d(locate.arrow.s))}>
              <LocateArrow size={d(locate.arrow.s)} color={colors.locate} />
            </View>

            {chips.items.map((c, i) => (
              <View key={i} style={StyleSheet.absoluteFill}>
                <View
                  style={{
                    ...abs(d(c.x), d(chips.y), d(c.w), d(chips.h)),
                    borderRadius: d(chips.h / 2),
                    backgroundColor: colors.chip,
                    shadowColor: '#4B5560',
                    shadowOpacity: 0.28,
                    shadowRadius: d(10),
                    shadowOffset: { width: 0, height: d(5) },
                  }}>
                  <LinearGradient
                    colors={['rgba(253,252,254,0)', 'rgba(235,232,236,0.9)']}
                    locations={[0.75, 1]}
                    style={{
                      ...StyleSheet.absoluteFill,
                      borderRadius: d(chips.h / 2),
                    }}
                  />
                </View>
                {'icon' in c ? (
                  <View style={abs(d(62), d(1463), d(35), d(35))}>
                    <Globe w={d(35)} h={d(35)} stroke={1.5} />
                  </View>
                ) : (
                  emoji(c.emoji, c.emojiX, 1481, 12.6)
                )}
                {c.text ? (
                  <Txt {...t} x={c.textX} base={chips.baseline} {...S(T.chip)}>
                    {c.text}
                  </Txt>
                ) : null}
              </View>
            ))}
            <View style={abs(d(354), d(1474), d(28), d(14))}>
              <ChevronDown w={d(28)} h={d(14)} stroke={2.4} />
            </View>
          </Animated.View>

          {/* ---- Sheet (the screen reader gets the layer at the end) ---- */}
          <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[StyleSheet.absoluteFill, sheetStyle]}>
            <LinearGradient
              colors={['#ECF2F4', '#E8F0F5', '#E2EEF7', '#DDEEF8']}
              locations={[0, 0.25, 0.55, 1]}
              style={{
                position: 'absolute',
                left: 0,
                right: 0,
                top: d(1548),
                bottom: 0,
                borderTopLeftRadius: p(radius.sheet),
                borderTopRightRadius: p(radius.sheet),
                borderTopWidth: Math.max(1, d(3)),
                borderTopColor: colors.highlight,
              }}
            />
            {/* the sheet continues below the screen, so the raised sheet has no bottom edge */}
            <View
              style={{
                position: 'absolute',
                left: 0,
                right: 0,
                top: '100%',
                height: p(RISE) * 2,
                backgroundColor: list.sheetTail,
              }}
            />
            <Animated.View
              style={[
                {
                  ...abs(d(526), d(1572), d(153), d(15)),
                  borderRadius: d(8),
                  backgroundColor: colors.grabber,
                  overflow: 'hidden',
                },
                grabStyle,
              ]}>
              <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: motionColors.grabberTaken }, grabInkStyle]} />
            </Animated.View>

            <Image
              source={IMG.cafe}
              style={{
                ...abs(d(layout.photo.x), d(layout.photo.y), d(layout.photo.w), d(layout.photo.h)),
                borderRadius: d(layout.photo.r),
              }}
            />
            <Txt {...t} x={layout.titleX} base={layout.titleBase} {...S(T.title)}>
              {copy.title}
            </Txt>

            <View style={abs(d(386), d(1808), d(31), d(36))}>
              <Pin w={d(31)} h={d(36)} stroke={1.6} />
            </View>
            <Txt {...t} x={layout.meta.places} base={layout.meta.base} {...S(T.meta)}>
              {copy.places}
            </Txt>
            <View style={abs(d(650), d(1808), d(29), d(35))}>
              <BookmarkOutline w={d(29)} h={d(35)} color={colors.meta} stroke={1.5} />
            </View>
            <Txt {...t} x={layout.meta.saves} base={layout.meta.base} {...S(T.meta)}>
              {copy.saves}
            </Txt>
            <View style={abs(d(894), d(1809), d(37), d(34))}>
              <BarsIcon w={d(37)} h={d(34)} color={colors.meta} stroke={2} />
            </View>
            <Txt {...t} x={layout.meta.views} base={layout.meta.base} {...S(T.meta)}>
              {copy.views}
            </Txt>

            <View
              style={{
                ...abs(d(layout.pill.x), d(layout.pill.y), d(layout.pill.w), d(layout.pill.h)),
                borderRadius: d(layout.pill.h / 2),
                backgroundColor: '#FFFFFF',
                shadowColor: colors.shadow,
                shadowOpacity: 0.2,
                shadowRadius: d(16),
                shadowOffset: { width: 0, height: d(6) },
              }}
            />
            <Image
              source={IMG.avatar}
              style={{
                ...abs(d(layout.avatar.x), d(layout.avatar.y), d(layout.avatar.d), d(layout.avatar.d)),
                borderRadius: d(layout.avatar.d / 2),
              }}
            />
            <Txt {...t} x={layout.handleX} base={layout.handleBase} {...S(T.handle)}>
              {copy.handle}
            </Txt>

            {/* tile 0: "0/59 you visited" (pressable; the digit rolls) */}
            <Animated.View
              style={[
                StyleSheet.absoluteFill,
                {
                  transformOrigin: [d(tiles.xs[0] + tiles.w / 2), d(tiles.y + tiles.h / 2), 0],
                },
                tileStyle,
              ]}>
              {tileFill(0)}
              <View
                style={{
                  ...abs(d(rollBox.x), d(rollBox.y), d(rollBox.w), d(rollBox.h)),
                  overflow: 'hidden',
                }}>
                <Animated.View style={[StyleSheet.absoluteFill, rollStyle]}>
                  <Animated.View style={[StyleSheet.absoluteFill, zeroStyle]}>
                    <Txt {...t} x={layout.tile0.numX - rollBox.x} base={layout.tile0.numBase - rollBox.y} size={T.visitedNum.size} font={T.visitedNum.font} color={colors.ink}>
                      {copy.visitedNum}
                    </Txt>
                  </Animated.View>
                  <Animated.View style={[StyleSheet.absoluteFill, oneStyle]}>
                    <Txt
                      {...t}
                      cx={rollBox.centre - rollBox.x}
                      base={layout.tile0.numBase - rollBox.y + (reduced ? 0 : rollBox.row)}
                      size={T.visitedNum.size}
                      font={T.visitedNum.font}
                      color={colors.ink}>
                      {copy.visitedNext}
                    </Txt>
                  </Animated.View>
                </Animated.View>
              </View>
              <Txt {...t} x={layout.tile0.denX} base={layout.tile0.numBase} size={T.visitedDen.size} font={T.visitedDen.font} color={colors.slash}>
                {copy.visitedDen}
              </Txt>
              <Txt {...t} cx={tiles.centre[0]} base={tiles.baseline[0]} {...S(T.visited)}>
                {copy.visited}
              </Txt>
            </Animated.View>

            {[1, 2, 3].map((i) => (
              <View key={i} style={StyleSheet.absoluteFill}>
                {tileFill(i)}
              </View>
            ))}
            {[copy.save, copy.share, copy.similar].map((label, j) => (
              <Txt {...t} key={label} cx={tiles.centre[j + 1]} base={tiles.baseline[j + 1]} {...S(T.tile)}>
                {label}
              </Txt>
            ))}
            <View style={abs(d(layout.tileIcon[0].x), d(layout.tileIcon[0].y), d(layout.tileIcon[0].w), d(layout.tileIcon[0].h))}>
              <BookmarkOutline w={d(layout.tileIcon[0].w)} h={d(layout.tileIcon[0].h)} stroke={1.65} />
            </View>
            <View style={abs(d(layout.tileIcon[1].x), d(layout.tileIcon[1].y), d(layout.tileIcon[1].w), d(layout.tileIcon[1].h))}>
              <ShareIcon w={d(layout.tileIcon[1].w)} h={d(layout.tileIcon[1].h)} stroke={1.8} />
            </View>
            <View style={abs(d(layout.tileIcon[2].x), d(layout.tileIcon[2].y), d(layout.tileIcon[2].w), d(layout.tileIcon[2].h))}>
              <GridPlus w={d(layout.tileIcon[2].w)} h={d(layout.tileIcon[2].h)} stroke={2.1} />
            </View>

            {/* list */}
            {card(
              layout.card.y,
              copy.listTitle,
              copy.listSub,
              <Image
                source={IMG.bar}
                style={{
                  ...abs(d(layout.listPhoto.x), d(layout.listPhoto.y), d(layout.listPhoto.d), d(layout.listPhoto.d)),
                  borderRadius: d(layout.listPhoto.r),
                }}
              />,
              'first',
              firstCheckbox,
            )}
            {list.more.map((m, i) => {
              const y = layout.card.y + (list.cardH + list.gap) * (i + 1);
              return card(y, m.title, m.sub, zoomedPhoto(m.photo === 'cafe' ? IMG.cafe : IMG.bar, m.zoom, m.ox, m.oy, y), m.title);
            })}
          </Animated.View>

          {/* ---- Screen reader: the sheet as elements with actions, over the drawn sheet (hidden from the reader).
               Transparent; a touch on them still reaches the screen's gesture (they are its descendants). ---- */}
          <Animated.View pointerEvents="box-none" style={[StyleSheet.absoluteFill, sheetStyle]}>
            <View
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel={`${copy.title}, ${copy.places}, ${copy.saves}, ${copy.views}, ${copy.a11yBy} ${copy.handle}`}
              accessibilityValue={{ text: expanded ? copy.a11yExpanded : copy.a11yCollapsed }}
              accessibilityHint={copy.a11ySheetHint}
              accessibilityActions={SHEET_ACTIONS}
              onAccessibilityAction={(e) => {
                const a = e.nativeEvent.actionName;
                if (a === 'increment') actions.sheetTo(1);
                else if (a === 'decrement') actions.sheetTo(0);
                else if (a === 'activate') actions.sheetTo(expanded ? 0 : 1);
              }}
              style={abs(0, d(sheet.top * 3), width, d(tiles.y - sheet.top * 3))}
            />
            <View
              accessible
              accessibilityRole="button"
              accessibilityLabel={copy.visited}
              accessibilityValue={{ text: `${visited ? copy.visitedNext : copy.visitedNum} ${copy.a11yOf} ${visitedOf}` }}
              accessibilityHint={copy.a11yVisitHint}
              accessibilityActions={ACTIVATE}
              onAccessibilityAction={(e) => {
                if (e.nativeEvent.actionName === 'activate') actions.toggleVisit();
              }}
              style={abs(d(tiles.xs[0]), d(tiles.y), d(tiles.w), d(tiles.h))}
            />
            {[copy.save, copy.share, copy.similar].map((label, j) => (
              <View key={label} accessible accessibilityLabel={label} style={abs(d(tiles.xs[j + 1]), d(tiles.y), d(tiles.w), d(tiles.h))} />
            ))}
            <View accessible accessibilityLabel={`${copy.listTitle}, ${copy.listSub}`} style={abs(d(layout.card.x), d(layout.card.y), d(layout.card.w), d(list.cardH))} />
            <View
              accessible
              accessibilityRole="checkbox"
              accessibilityLabel={copy.a11yVisitedBox}
              accessibilityState={{ checked: visited }}
              accessibilityActions={ACTIVATE}
              onAccessibilityAction={(e) => {
                if (e.nativeEvent.actionName === 'activate') actions.toggleVisit();
              }}
              style={abs(d(cb.x + cb.d / 2 - HIT_SLOP), d(cb.y + cb.d / 2 - HIT_SLOP), d(2 * HIT_SLOP), d(2 * HIT_SLOP))}
            />
            {list.more.map((m, i) => (
              <View
                key={m.title}
                accessible
                accessibilityLabel={`${m.title}, ${m.sub}`}
                accessibilityElementsHidden={!expanded}
                importantForAccessibility={expanded ? 'yes' : 'no-hide-descendants'}
                style={abs(d(layout.card.x), d(layout.card.y + (list.cardH + list.gap) * (i + 1)), d(layout.card.w), d(list.cardH))}
              />
            ))}
          </Animated.View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

/**
 * A loose map glyph: sits on its ground spot (projected while the map pitches) and glides on a clockwise arc
 * into its café's count bubble, which rides the standing pin.
 */
function Glyph({ g, c, sp, u, reduced }: { g: GlyphPath; c: SharedValue<number>; sp: SharedValue<number>; u: number; reduced: boolean }) {
  const z = GLYPH_SIZE[g.kind];
  const pin = PINS[g.cluster];
  const style = useAnimatedStyle(() => {
    const q = glyphProgress(c.get(), g.delay);
    if (reduced) return { opacity: 1 - q };
    const tp = tiltOf(sp.get());
    const from = project(g.x, g.y, tp);
    const disc = discAt(pin.cx, pin.cy, tp, pin.rank, R);
    const tx = disc.x + bubble.dx * disc.m;
    const ty = disc.y + bubble.dy * disc.m;
    const dx = tx - from.x;
    const dy = ty - from.y;
    const bulge = Math.sin(Math.PI * q) * CLUSTER.arc;
    return {
      opacity: 1 - seg(q, CLUSTER.fadeFrom, 1),
      transform: [{ translateX: (from.x - g.x + dx * q - dy * bulge) * u }, { translateY: (from.y - g.y + dy * q + dx * bulge) * u }, { scale: lerp(from.m, CLUSTER.endScale * disc.m, q) }],
    };
  });
  return (
    <Animated.View style={[abs((g.x - z.w / 2) * u, (g.y - z.h / 2) * u, z.w * u, z.h * u), style]}>
      {g.kind === 'bookmark' ? (
        <MapBookmark w={z.w * u} h={z.h * u} color={colors.bookmark} />
      ) : g.kind === 'thumb' ? (
        <MapThumb w={z.w * u} h={z.h * u} color={colors.thumb} />
      ) : (
        <MapStar w={z.w * u} h={z.h * u} color={colors.star} />
      )}
    </Animated.View>
  );
}

/** A café's marker (disc, label, bubble): stands on its ground spot, lifted by the stalk, scaled by depth. */
function PinGroup({ pin, sp, u, reduced, children }: { pin: PinSpot; sp: SharedValue<number>; u: number; reduced: boolean; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    if (reduced)
      return {
        transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 1 }],
      };
    const disc = discAt(pin.cx, pin.cy, tiltOf(sp.get()), pin.rank, R);
    return {
      transform: [{ translateX: (disc.x - pin.cx) * u }, { translateY: (disc.y - pin.cy) * u }, { scale: disc.m }],
    };
  });
  return <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: [pin.cx * u, pin.cy * u, 0] }, style]}>{children}</Animated.View>;
}

/** Stalk, foot and light beam of a standing pin (screen space, behind the markers). */
function PinRig({ pin, sp, visitPop, u, reduced }: { pin: PinSpot; sp: SharedValue<number>; visitPop: SharedValue<number>; u: number; reduced: boolean }) {
  const { cx, cy } = pin;
  const tone = pin.m.tag === 'saved' ? depth.beam.saved : depth.beam.liked;
  const flare = pin.i === VISITED;
  const rig = useAnimatedStyle(() => {
    const tp = reduced ? 0 : tiltOf(sp.get());
    const g = project(cx, cy, tp);
    return {
      opacity: tp > 0 ? 1 : 0,
      transform: [{ translateX: (g.x - cx) * u }, { translateY: (g.y - cy) * u }, { scale: g.m }],
    };
  });
  const stalk = useAnimatedStyle(() => {
    const st = reduced ? 0 : standOf(tiltOf(sp.get()), pin.rank);
    return {
      opacity: seg(st, 0, 0.2),
      transform: [{ scaleY: Math.max(0.001, st) }],
    };
  });
  const foot = useAnimatedStyle(() => ({
    opacity: reduced ? 0 : 0.45 * seg(standOf(tiltOf(sp.get()), pin.rank), 0, 0.5),
  }));
  const beam = useAnimatedStyle(() => {
    const st = reduced ? 0 : standOf(tiltOf(sp.get()), pin.rank);
    const on = seg(st, 0.4, 1);
    const boost = flare ? 1 + BEAM.flare * Math.max(0, visitPop.get() - 1) : 1;
    return {
      opacity: Math.min(1, BEAM.opacity * on * boost),
      transform: [{ scaleX: boost }, { scaleY: lerp(0.3, 1, easeOut(on)) }],
    };
  });
  const sh = STAND.stalk + R;
  return (
    <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: [cx * u, cy * u, 0] }, rig]}>
      <Animated.View style={[abs((cx - BEAM.w * 1.5) * u, (cy - BEAM.h) * u, BEAM.w * 3 * u, BEAM.h * u), { transformOrigin: ['50%', '100%', 0] }, beam]}>
        <LinearGradient colors={[`rgba(${tone},0)`, `rgba(${tone},0.16)`]} style={StyleSheet.absoluteFill} />
        <LinearGradient colors={[`rgba(${tone},0)`, `rgba(${tone},0.34)`]} style={abs(BEAM.w * u, 0, BEAM.w * u, BEAM.h * u)} />
        <LinearGradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.8)']} style={abs((BEAM.w * 1.5 - BEAM.core / 2) * u, 0, BEAM.core * u, BEAM.h * u)} />
      </Animated.View>
      <Animated.View
        style={[abs((cx - depth.foot.d / 2) * u, (cy - depth.foot.d / 4) * u, depth.foot.d * u, (depth.foot.d / 2) * u), { borderRadius: depth.foot.d * u, backgroundColor: depth.foot.color }, foot]}
      />
      <Animated.View style={[abs((cx - depth.stalk.w / 2) * u, (cy - sh) * u, depth.stalk.w * u, sh * u), { transformOrigin: ['50%', '100%', 0] }, stalk]}>
        <LinearGradient colors={depth.stalk.colors} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={[StyleSheet.absoluteFill, { borderRadius: depth.stalk.w * u }]} />
      </Animated.View>
    </Animated.View>
  );
}

/** On the ground plane (pitched with it): a long soft shadow away from the light, and a ripple where the pin lands. */
function GroundMarks({ pin, sp, u, reduced }: { pin: PinSpot; sp: SharedValue<number>; u: number; reduced: boolean }) {
  const { cx, cy } = pin;
  const shadow = useAnimatedStyle(() => {
    const st = reduced ? 0 : standOf(tiltOf(sp.get()), pin.rank);
    return {
      opacity: SHADOW.opacity * Math.min(1, st),
      transform: [{ rotate: `${SHADOW.angle}deg` }, { scaleX: Math.max(0.001, st) }],
    };
  });
  const ripple = useAnimatedStyle(() => {
    const q = reduced ? 0 : standLinear(tiltOf(sp.get()), pin.rank);
    const e = seg(q, RIPPLE.at, 1);
    return {
      opacity: 0.8 * seg(q, RIPPLE.at, RIPPLE.at + 0.05) * (1 - e),
      transform: [{ scale: lerp(1, RIPPLE.grow, easeOut(e)) }],
    };
  });
  const id = `sh-${pin.m.key}`;
  return (
    <>
      <Animated.View style={[abs(cx * u, (cy - SHADOW.w / 2) * u, SHADOW.len * u, SHADOW.w * u), { transformOrigin: [0, '50%', 0] }, shadow]}>
        <Svg width={SHADOW.len * u} height={SHADOW.w * u}>
          <Defs>
            <RadialGradient id={id} cx="0.1" cy="0.5" rx="0.9" ry="0.5" fx="0.05" fy="0.5" gradientUnits="objectBoundingBox">
              <Stop offset="0" stopColor={depth.shadow} stopOpacity={1} />
              <Stop offset="0.45" stopColor={depth.shadow} stopOpacity={0.45} />
              <Stop offset="1" stopColor={depth.shadow} stopOpacity={0} />
            </RadialGradient>
          </Defs>
          <Ellipse cx={(SHADOW.len / 2) * u} cy={(SHADOW.w / 2) * u} rx={(SHADOW.len / 2) * u} ry={(SHADOW.w / 2) * u} fill={`url(#${id})`} />
        </Svg>
      </Animated.View>
      <Animated.View
        style={[
          abs((cx - RIPPLE.d / 2) * u, (cy - RIPPLE.d / 2) * u, RIPPLE.d * u, RIPPLE.d * u),
          {
            borderRadius: RIPPLE.d * u,
            borderWidth: depth.ripple.ring * u,
            borderColor: depth.ripple.color,
          },
          ripple,
        ]}
      />
    </>
  );
}

/** Count bubble: appears with the first landing, swells a little per landing, its count rolls up. */
function Bubble({ cl, c, u, reduced, ready }: { cl: Cluster; c: SharedValue<number>; u: number; reduced: boolean; ready: boolean }) {
  const w = Math.max(bubble.h, String(cl.count).length * bubble.digit + bubble.padX * 2);
  const row = bubble.h - bubble.ring * 2;
  const style = useAnimatedStyle(() => {
    let landed = 0;
    for (let i = 0; i < cl.delays.length; i++) landed += seg(glyphProgress(c.get(), cl.delays[i]), CLUSTER.landFrom, 1);
    const show = reduced ? seg(c.get(), 0, 1) : seg(landed, 0, 0.5);
    const gulp = !reduced && landed > 0 && landed < cl.count ? CLUSTER.gulp * Math.sin(Math.PI * (landed % 1)) : 0;
    return {
      opacity: show,
      transform: [{ scale: reduced ? 1 : lerp(CLUSTER.bubbleFrom, 1, show) + gulp }],
    };
  });
  const strip = useAnimatedStyle(() => {
    let landed = 0;
    for (let i = 0; i < cl.delays.length; i++) landed += seg(glyphProgress(c.get(), cl.delays[i]), CLUSTER.landFrom, 1);
    const r = reduced ? cl.count : Math.min(cl.count, Math.max(1, landed));
    return { transform: [{ translateY: -(r - 1) * row * u }] };
  });
  return (
    <Animated.View
      style={[
        abs((cl.bx - w / 2) * u, (cl.by - bubble.h / 2) * u, w * u, bubble.h * u),
        {
          borderRadius: (bubble.h / 2) * u,
          backgroundColor: motionColors.bubble,
          borderWidth: bubble.ring * u,
          borderColor: motionColors.bubbleInk,
          overflow: 'hidden',
        },
        style,
      ]}>
      <Animated.View style={strip}>
        {Array.from({ length: cl.count }, (_, i) => (
          <Text
            key={i}
            allowFontScaling={false}
            style={{
              height: row * u,
              lineHeight: row * u,
              textAlign: 'center',
              fontFamily: ready ? FONT.barlowSemi : undefined,
              fontSize: bubble.size * u,
              fontVariant: ['tabular-nums'],
              color: motionColors.bubbleInk,
            }}>
            {i + 1}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** Disc + emoji of one café: pops when tapped, and (the visited café) when ticked. */
function MarkerDisc({
  i,
  cx,
  cy,
  d,
  visitPop,
  tapIdx,
  tapPop,
  reduced,
  children,
}: {
  i: number;
  cx: number;
  cy: number;
  d: (px: number) => number;
  visitPop: SharedValue<number>;
  tapIdx: SharedValue<number>;
  tapPop: SharedValue<number>;
  reduced: boolean;
  children: ReactNode;
}) {
  const style = useAnimatedStyle(() => {
    if (reduced) return { transform: [{ scale: 1 }] };
    const v = i === VISITED ? visitPop.get() : 1;
    const tp = tapIdx.get() === i ? tapPop.get() : 1;
    return { transform: [{ scale: v * tp }] };
  });
  return <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: [d(cx), d(cy), 0] }, style]}>{children}</Animated.View>;
}

function CheckBadge({ cx, cy, d, badge, reduced }: { cx: number; cy: number; d: (px: number) => number; badge: SharedValue<number>; reduced: boolean }) {
  const s = badgeBox.d;
  const style = useAnimatedStyle(() => ({
    opacity: badge.get(),
    transform: [{ scale: reduced ? 1 : lerp(0.9, 1, badge.get()) }],
  }));
  return (
    <Animated.View
      style={[
        abs(d(cx + badgeBox.dx - s / 2), d(cy + badgeBox.dy - s / 2), d(s), d(s)),
        {
          borderRadius: d(s / 2),
          backgroundColor: motionColors.check,
          borderWidth: d(badgeBox.ring),
          borderColor: motionColors.checkInk,
          alignItems: 'center',
          justifyContent: 'center',
        },
        style,
      ]}>
      <Svg width={d(s * 0.62)} height={d(s * 0.62)} viewBox="0 0 29 29">
        <Path d={tick.path} stroke={motionColors.checkInk} strokeWidth={tick.stroke * 1.3} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </Svg>
    </Animated.View>
  );
}

/** Text placed by baseline (`top = baseline − ascent · size`); left = ink left, or centred on `cx`. */
function Txt({
  u,
  ready,
  x,
  cx,
  base,
  size,
  font,
  color,
  ls = 0,
  opacity,
  children,
}: {
  u: number;
  ready: boolean;
  x?: number;
  cx?: number;
  base: number;
  size: number;
  font: string;
  color: string;
  ls?: number;
  opacity?: number;
  children: ReactNode;
}) {
  const m = METRICS[font];
  const fs = size * u;
  const centred = cx !== undefined;
  return (
    <Text
      numberOfLines={1}
      allowFontScaling={false}
      style={{
        position: 'absolute',
        top: base * u - m.asc * fs,
        left: centred ? (cx - 500) * u : (x ?? 0) * u,
        width: 1000 * u,
        textAlign: centred ? 'center' : 'left',
        fontFamily: ready ? font : undefined,
        fontSize: fs,
        lineHeight: (m.asc + m.desc) * fs,
        letterSpacing: ls * u,
        color,
        opacity,
      }}>
      {children}
    </Text>
  );
}

function abs(left: number, top: number, width: number, height: number) {
  return { position: 'absolute' as const, left, top, width, height };
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#DDEEF8', overflow: 'hidden' },
});

export { space, sheet };
