// Card Vacuum Seal — a bank's Cards screen where "Protect Card" vacuum-seals the card in clear film.
// Hold the shield tile: a clear sheet falls on the card and the air is drawn out until the film is a creased,
// glossy skin; slide off to hold it half-way, lift early to let the air back, tap to run the whole seal.
// A finger on the film squeegees the air out where it is; on the sealed card it dents the film. The card is a plain
// view wrapped in <VacuumSeal>. Replica of Dribbble 27250589 (FANCY); bank, holder and people invented.
import { useFonts } from 'expo-font';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useState } from 'react';
import { Image, ScrollView, StatusBar, StyleSheet, Text, type TextStyle, useWindowDimensions, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, type SharedValue, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Defs, RadialGradient, Rect as SvgRect, Stop } from 'react-native-svg';

import { useVacuumSeal } from './cvs-controller';
import { BackArrow, CloseGlyph, EyeGlyph, MARK_OPEN_D, MARK_SEALED_D, NutGlyph, ShieldBase, ShieldMark } from './cvs-icons';
import { LOOP, TUG } from './cvs-motion';
import { BARS, comp, COPY, makeLayout, panelShade, ref, sys } from './cvs-theme';
import { VacuumSeal } from './VacuumSeal';

export { LOOP };

const FONT = { medium: 'CvsMedium', semi: 'CvsSemiBold' } as const;
// type sits in fixed boxes measured from the pin: it may grow with Dynamic Type up to this factor, never clip
const MAX_SCALE = 1.3;
// abstract 3D avatars in the pin's style (Codex, from the pin crop; assets/manifest.json)
const AVATARS = [require('./assets/avatar-tidewell.png'), require('./assets/avatar-oren.png')] as const;

// the tile discs: three-stop gradients sampled off the pin (light upper left, deeper lower right)
const DISC = {
  violet: [ref.color.violet, '#B25CF6', ref.color.magenta],
  red: ['#F0525A', ref.color.red, '#B8242F'],
  amber: ['#F2D46A', ref.color.amber, '#B9922A'],
  ice: ['#F2F6FF', '#C3D5F3', ref.color.blueDeep],
} as const;

/** a soft gloss on a disc: a white radial lobe upper left, as the key light would leave on lacquer */
function DiscGloss({ d }: { d: number }) {
  return (
    <Svg width={d} height={d} style={StyleSheet.absoluteFill} pointerEvents="none">
      <Defs>
        <RadialGradient id="cvsDiscGloss" cx={d * 0.32} cy={d * 0.26} rx={d * 0.62} ry={d * 0.55} gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor="#FFFFFF" stopOpacity={0.42} />
          <Stop offset="0.55" stopColor="#FFFFFF" stopOpacity={0.08} />
          <Stop offset="1" stopColor="#FFFFFF" stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Circle cx={d / 2} cy={d / 2} r={d / 2} fill="url(#cvsDiscGloss)" />
    </Svg>
  );
}

/** the card as the app would draw it: a plain view (the film wraps it and draws it from a snapshot) */
function BankCard({ S }: { S: number }) {
  const C = comp.card;
  const T = ref.type;
  const text = (t: { size: number; tracking: number }, base: number, color: string) => ({
    position: 'absolute' as const,
    left: C.padX * S,
    top: (base - t.size) * S,
    fontFamily: FONT.medium,
    fontSize: t.size * S,
    letterSpacing: t.tracking * t.size * S,
    color,
    includeFontPadding: false,
  });
  return (
    <View style={{ width: C.w * S, height: C.h * S, borderRadius: C.r * S, overflow: 'hidden', borderCurve: 'continuous' }}>
      <LinearGradient
        colors={[ref.color.cardLight, ref.color.cardMid, ref.color.cardDark]}
        locations={[0, 0.5, 1]}
        start={{ x: -0.0089, y: 0.6083 }}
        end={{ x: 1.0088, y: 0.3917 }}
        style={StyleSheet.absoluteFill}
      />
      <Text maxFontSizeMultiplier={1} style={text(T.wordmark, C.wordBase, 'rgba(233, 236, 244, 0.92)')} numberOfLines={1}>
        {COPY.bank}
      </Text>
      <Text maxFontSizeMultiplier={1} style={text(T.holder, C.holderBase, 'rgba(233, 236, 244, 0.8)')} numberOfLines={1}>
        {COPY.holder}
      </Text>
    </View>
  );
}

/** 0 below a, 1 above b, smooth between */
function band(a: number, b: number, x: number) {
  'worklet';
  const v = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return v * v * (3 - 2 * v);
}

/** how many leading characters two labels share ("Protect Card" / "Protected" → "Protect") */
function stemOf(a: string, b: string) {
  let n = 0;
  while (n < a.length && n < b.length && a[n] === b[n]) n++;
  return n;
}

/** "Protect Card" ↔ "Protected" as ONE word that changes its ending: the shared stem stays and slides to stay centred,
 *  the old ending drops out before the new one rises in, so no frame ever shows two labels (k: 0 `from` … 1 `to`) */
function MorphLabel({
  from,
  to,
  k,
  width,
  lift,
  still,
  style,
}: {
  from: string;
  to: string;
  k: SharedValue<number>;
  width: number;
  lift: number;
  still: boolean;
  style: TextStyle;
}) {
  const n = stemOf(from, to);
  const parts = [from.slice(0, n), from.slice(n), to.slice(n)] as const;
  const [w, setW] = useState([0, 0, 0]);
  // the typeset width of each piece (its line)
  const measure = (i: number) => (e: { nativeEvent: { lines: { width: number }[] } }) => {
    const v = e.nativeEvent.lines.reduce((m, l) => Math.max(m, l.width), 0);
    setW((o) => (Math.abs(o[i] - v) < 0.1 ? o : o.map((x, j) => (j === i ? v : x))));
  };
  const ws = w[0];
  const wa = w[1];
  const wb = w[2];
  const dy = still ? 0 : lift;
  const left = (x: number) => {
    'worklet';
    return (width - ws - wa + (wa - wb) * band(0.1, 0.9, x)) / 2;
  };
  const stemStyle = useAnimatedStyle(() => ({ transform: [{ translateX: left(k.get()) }] }));
  const outStyle = useAnimatedStyle(() => {
    const o = band(0, 0.45, k.get());
    return { opacity: 1 - o, transform: [{ translateX: left(k.get()) + ws }, { translateY: dy * o }] };
  });
  const inStyle = useAnimatedStyle(() => {
    const i = band(0.5, 1, k.get());
    return { opacity: i, transform: [{ translateX: left(k.get()) + ws }, { translateY: -dy * (1 - i) }] };
  });
  const st = [style, styles.morphPart];
  return (
    <>
      <Animated.Text maxFontSizeMultiplier={MAX_SCALE} style={[st, stemStyle]} numberOfLines={1} onTextLayout={measure(0)}>
        {parts[0]}
      </Animated.Text>
      <Animated.Text maxFontSizeMultiplier={MAX_SCALE} style={[st, outStyle]} numberOfLines={1} onTextLayout={measure(1)}>
        {parts[1]}
      </Animated.Text>
      <Animated.Text maxFontSizeMultiplier={MAX_SCALE} style={[st, inStyle]} numberOfLines={1} onTextLayout={measure(2)}>
        {parts[2]}
      </Animated.Text>
    </>
  );
}

export function CardVacuumSeal({ autoPlay = false }: { autoPlay?: boolean }) {
  const [fontsLoaded] = useFonts({
    [FONT.medium]: require('./assets/fonts/HankenGrotesk-Medium.ttf'),
    [FONT.semi]: require('./assets/fonts/HankenGrotesk-SemiBold.ttf'),
  });

  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const L = useMemo(() => makeLayout(W, H, Math.max(insets.top, 20), insets.bottom), [W, H, insets.top, insets.bottom]);
  const S = L.S;
  const tileW = comp.tiles.w * S;
  const tileH = comp.tiles.h * S;

  const [filmReady, setFilmReady] = useState(false);
  const ready = fontsLoaded && filmReady;

  // the film's controller: one state value `p`, the loop, the finger on the tile and on the film, haptics, sound
  const seal = useVacuumSeal({ autoPlay, disabled: !ready, sound: true, devHook: true, holdSize: { w: tileW, h: tileH } });
  const { look, reduce, sealed, label: discK } = seal;

  // show the screen once the card has been drawn (the pin fades its screen in too)
  const shown = useSharedValue(0);
  useEffect(() => {
    if (ready) shown.set(withTiming(1, { duration: 420, easing: Easing.bezier(0.2, 0.8, 0.2, 1) }));
  }, [ready, shown]);

  // ---- outside the film: the disc, the shield's mark and the label follow the film's state (`seal.label`, a timed
  // morph at the lock and when air gets back in); the tiles are tugged at the lock
  const tugPt = -TUG.pt * S;
  const tileStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: tugPt * look.get().tug }, { scale: 1 - 0.04 * look.get().press }],
  }));
  const sideTugStyle = useAnimatedStyle(() => ({ transform: [{ translateY: tugPt * look.get().tugSide }] }));
  const amberStyle = useAnimatedStyle(() => ({ transform: [{ scale: Math.max(discK.get(), 0.001) }] }));
  const flashStyle = useAnimatedStyle(() => ({ opacity: 0.75 * Math.min(1, look.get().flash) }));
  const discGlintStyle = useAnimatedStyle(() => ({ opacity: 0.5 * Math.min(1, look.get().discGlint) }));
  // the mark: the bar folds away, then the check grows in (never both)
  const markOpenStyle = useAnimatedStyle(() => {
    const o = band(0, 0.45, discK.get());
    return { opacity: 1 - o, transform: [{ scaleY: 1 - 0.6 * o }] };
  });
  const markSealedStyle = useAnimatedStyle(() => {
    const k = band(0.5, 1, discK.get());
    return { opacity: k, transform: [{ scale: 0.75 + 0.25 * k }] };
  });
  const screenStyle = useAnimatedStyle(() => ({ opacity: shown.get() }));

  // ---- layout
  const T = ref.type;
  const font = (t: { family: string; size: number; tracking: number }, color: string = sys.ink.primary) => ({
    fontFamily: t.family,
    fontSize: t.size * S,
    letterSpacing: t.tracking * t.size * S,
    color,
    includeFontPadding: false,
  });
  const C = comp.card;
  const TL = comp.tiles;
  const RV = comp.revenue;
  const LS = comp.list;
  const contentH = Math.max(H, L.yt(LS.y + LS.rowY + 2 * LS.rowH + 12) + L.bottom);
  const tileX = (i: number) => (C.x + i * (TL.w + TL.gap)) * S;
  const discD = TL.discD * S;
  const box = (x: number, y: number, w: number, h: number) => ({
    position: 'absolute' as const,
    left: x * S,
    top: L.yt(y),
    width: w * S,
    height: h * S,
  });

  const tile = (i: number, label: React.ReactNode, disc: React.ReactNode, a11y?: object) => (
    <Animated.View
      style={[
        styles.tile,
        { left: tileX(i), top: L.yt(TL.y), width: tileW, height: tileH, borderRadius: ref.radius.tile * S },
        sideTugStyle,
      ]}
      {...a11y}
    >
      <View style={[styles.disc, { top: TL.discTop * S, width: discD, height: discD, borderRadius: discD / 2, left: (tileW - discD) / 2 }]}>
        {disc}
      </View>
      <View style={[styles.labelRow, { top: (TL.labelBase - T.tile.size) * S }]}>{label}</View>
    </Animated.View>
  );

  return (
    <View style={styles.root}>
      <StatusBar barStyle="light-content" />
      <Animated.View style={[StyleSheet.absoluteFill, screenStyle]}>
        <ScrollView
          bounces={false}
          scrollEnabled={contentH > H}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ height: contentH }}
        >
          {/* the violet light the room throws on the screen's upper left */}
          <Svg style={StyleSheet.absoluteFill} width={W} height={contentH} pointerEvents="none">
            <Defs>
              <RadialGradient id="cvsBloom" cx={0} cy={0} rx={W * 0.95} ry={W * 1.15} gradientUnits="userSpaceOnUse">
                <Stop offset="0" stopColor={ref.color.bloom} stopOpacity={0.2} />
                <Stop offset="0.45" stopColor={ref.color.bloom} stopOpacity={0.06} />
                <Stop offset="1" stopColor={ref.color.bloom} stopOpacity={0} />
              </RadialGradient>
            </Defs>
            <SvgRect x={0} y={0} width={W} height={contentH} fill="url(#cvsBloom)" />
          </Svg>

          {/* header */}
          <View
            style={[styles.iconBtn, { left: (comp.header.arrowX - 11) * S, top: L.yt(comp.header.cy) - 22 }]}
            accessible
            accessibilityRole="button"
            accessibilityLabel={COPY.a11y.back}
          >
            <BackArrow size={comp.header.arrowSize * S} />
          </View>
          <Text
            maxFontSizeMultiplier={MAX_SCALE}
            style={[font(T.title), styles.title, { top: L.yt(comp.header.cy) - T.title.size * S * 0.62, width: W }]}
            accessibilityRole="header"
          >
            {COPY.title}
          </Text>
          <View
            style={[styles.iconBtn, { left: (comp.header.closeX + comp.header.closeD / 2) * S - 22, top: L.yt(comp.header.cy) - 22 }]}
            accessible
            accessibilityRole="button"
            accessibilityLabel={COPY.a11y.close}
          >
            <LinearGradient
              colors={[ref.color.lilacLight, ref.color.lilac, ref.color.lilac]}
              start={{ x: 0.2, y: 0 }}
              end={{ x: 0.8, y: 1 }}
              style={{
                width: comp.header.closeD * S,
                height: comp.header.closeD * S,
                borderRadius: (comp.header.closeD * S) / 2,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <CloseGlyph size={18 * S} />
            </LinearGradient>
          </View>

          {/* tiles */}
          {tile(
            0,
            <Text maxFontSizeMultiplier={MAX_SCALE} style={font(T.tile)} numberOfLines={1}>
              {COPY.tiles.details}
            </Text>,
            <LinearGradient colors={DISC.violet} start={{ x: 0.15, y: 0.1 }} end={{ x: 0.85, y: 0.95 }} style={styles.fill}>
              <DiscGloss d={discD} />
              <EyeGlyph size={24 * S} iris={'#B25CF6'} />
            </LinearGradient>,
            { accessible: true, accessibilityRole: 'button', accessibilityLabel: COPY.tiles.details },
          )}
          <GestureDetector gesture={seal.hold}>
            <Animated.View
              style={[styles.abs, { left: tileX(1), top: L.yt(TL.y), width: tileW, height: tileH }, tileStyle]}
              accessible
              accessibilityRole="switch"
              accessibilityLabel={COPY.a11y.protect}
              accessibilityHint={COPY.a11y.protectHint}
              accessibilityState={{ checked: sealed }}
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityAction={() => seal.toggle(!sealed)}
            >
              <View style={[styles.tile, { left: 0, top: 0, width: tileW, height: tileH, borderRadius: ref.radius.tile * S }]}>
                <View
                  style={[
                    styles.disc,
                    { top: TL.discTop * S, width: discD, height: discD, borderRadius: discD / 2, left: (tileW - discD) / 2 },
                  ]}
                >
                  <LinearGradient colors={DISC.red} start={{ x: 0.2, y: 0.05 }} end={{ x: 0.8, y: 1 }} style={styles.fill} />
                  <Animated.View style={[styles.fill, { borderRadius: discD / 2, overflow: 'hidden' }, amberStyle]}>
                    <LinearGradient colors={DISC.amber} start={{ x: 0.2, y: 0.05 }} end={{ x: 0.8, y: 1 }} style={styles.fill} />
                  </Animated.View>
                  <Animated.View style={[styles.fill, { backgroundColor: ref.color.redFlash }, flashStyle]} />
                  <Animated.View style={[styles.fill, { backgroundColor: '#FFFFFF' }, discGlintStyle]} />
                  <DiscGloss d={discD} />
                  <View style={styles.center}>
                    <ShieldBase size={26 * S} />
                  </View>
                  <Animated.View style={[styles.center, markOpenStyle]}>
                    <ShieldMark size={26 * S} d={MARK_OPEN_D} color={ref.color.red} />
                  </Animated.View>
                  <Animated.View style={[styles.center, markSealedStyle]}>
                    <ShieldMark size={26 * S} d={MARK_SEALED_D} color={ref.color.amberInk} />
                  </Animated.View>
                </View>
                <View
                  style={[styles.labelRow, { top: (TL.labelBase - T.tile.size) * S }]}
                  accessibilityElementsHidden
                  importantForAccessibility="no-hide-descendants"
                >
                  {/* measured only once the font is registered: the text layout cache keeps the first measurement of a
                      string, and a fallback-font width left a gap after the stem */}
                  {fontsLoaded && (
                    <MorphLabel
                      from={COPY.tiles.protect}
                      to={COPY.tiles.protected}
                      k={discK}
                      width={tileW}
                      lift={5 * S}
                      still={reduce}
                      style={font(T.tile)}
                    />
                  )}
                </View>
              </View>
            </Animated.View>
          </GestureDetector>
          {tile(
            2,
            <Text maxFontSizeMultiplier={MAX_SCALE} style={font(T.tile)} numberOfLines={1}>
              {COPY.tiles.settings}
            </Text>,
            <LinearGradient colors={DISC.ice} start={{ x: 0.2, y: 0.05 }} end={{ x: 0.8, y: 1 }} style={styles.fill}>
              <DiscGloss d={discD} />
              <NutGlyph size={26 * S} hole={ref.color.blue} />
            </LinearGradient>,
            { accessible: true, accessibilityRole: 'button', accessibilityLabel: COPY.tiles.settings },
          )}

          {/* revenue */}
          <View
            style={[styles.panel, box(C.x, RV.y, C.w, RV.h), { borderRadius: ref.radius.tile * S }]}
            accessible
            accessibilityLabel={`${COPY.revenue} ${COPY.revenueValue}`}
          >
            <LinearGradient colors={panelShade(RV.y, RV.y + RV.h)} style={StyleSheet.absoluteFill} pointerEvents="none" />
            <View
              style={[
                styles.badge,
                { left: RV.padX * S, top: RV.badgeY * S, width: RV.badge * S, height: RV.badge * S, borderRadius: ref.radius.badge * S },
              ]}
            >
              <Text maxFontSizeMultiplier={MAX_SCALE} style={[font(T.small, '#2A2208'), { fontFamily: FONT.semi }]}>
                $
              </Text>
            </View>
            <Text
              maxFontSizeMultiplier={MAX_SCALE}
              style={[
                font(T.label),
                styles.abs,
                { left: (RV.padX + RV.badge + 9) * S, top: (RV.badgeY + RV.badge / 2) * S - T.label.size * S * 0.62 },
              ]}
            >
              {COPY.revenue}
            </Text>
            <Text
              maxFontSizeMultiplier={MAX_SCALE}
              style={[
                font(T.figure),
                styles.abs,
                { left: (RV.padX - 2) * S, top: (RV.figureBase - T.figure.size * 0.92) * S, fontVariant: ['tabular-nums'] },
              ]}
            >
              {COPY.revenueValue}
            </Text>
            <Svg style={styles.abs} width={C.w * S} height={RV.h * S} pointerEvents="none">
              {BARS.map((h, i) => {
                const x = (RV.bars.x0 - C.x + ((RV.bars.x1 - RV.bars.x0) * i) / (BARS.length - 1)) * S;
                const base = RV.bars.base * S;
                return h <= 3 ? (
                  <Circle key={i} cx={x} cy={base - 1.5 * S} r={1.6 * S} fill={sys.ink.primary} />
                ) : (
                  <SvgRect key={i} x={x - 1.5 * S} y={base - h * S} width={3 * S} height={h * S} rx={1.5 * S} fill={sys.ink.primary} />
                );
              })}
            </Svg>
          </View>

          {/* recent transactions (the card runs on past the screen's bottom, like the pin's) */}
          <View style={[styles.panel, box(C.x, LS.y, C.w, (contentH - L.yt(LS.y)) / S + 40), { borderRadius: ref.radius.tile * S }]}>
            <LinearGradient
              colors={panelShade(LS.y, LS.y + (contentH - L.yt(LS.y)) / S + 40)}
              style={StyleSheet.absoluteFill}
              pointerEvents="none"
            />
            <View
              style={[
                styles.listHeader,
                { left: LS.padX * S, right: LS.padX * S, top: (LS.headBase - T.heading.size * 0.95) * S, gap: LS.textGap },
              ]}
            >
              <Text
                maxFontSizeMultiplier={MAX_SCALE}
                style={[font(T.heading), styles.listTitle]}
                numberOfLines={1}
                ellipsizeMode="tail"
                adjustsFontSizeToFit
                minimumFontScale={0.85}
                accessibilityRole="header"
              >
                {COPY.list}
              </Text>
              <Text maxFontSizeMultiplier={MAX_SCALE} style={[font(T.label), styles.seeAll]} numberOfLines={1} accessibilityRole="button">
                {COPY.seeAll}
              </Text>
            </View>
            {COPY.rows.map((row, i) => {
              const y = (LS.rowY + i * LS.rowH) * S;
              const a = LS.avatar * S;
              return (
                <View
                  key={row.name}
                  style={[styles.abs, { left: 0, right: 0, top: y, height: a }]}
                  accessible
                  accessibilityLabel={`${row.name}, ${row.note}, ${row.amount}, ${row.status}`}
                >
                  <Image
                    source={AVATARS[i]}
                    style={[styles.abs, { left: LS.padX * S, top: 0, width: a, height: a, borderRadius: a / 2 }]}
                    accessibilityIgnoresInvertColors
                  />
                  <Text
                    maxFontSizeMultiplier={MAX_SCALE}
                    style={[
                      font(T.name),
                      styles.abs,
                      { left: (LS.padX + LS.avatar + 12) * S, right: (LS.padX + LS.amountW) * S + LS.textGap, top: 2 * S },
                    ]}
                    numberOfLines={1}
                  >
                    {row.name}
                  </Text>
                  <Text
                    maxFontSizeMultiplier={MAX_SCALE}
                    style={[
                      font(T.small, sys.ink.secondary),
                      styles.abs,
                      { left: (LS.padX + LS.avatar + 12) * S, right: (LS.padX + LS.amountW) * S + LS.textGap, top: 24 * S },
                    ]}
                    numberOfLines={1}
                  >
                    {row.note}
                  </Text>
                  <Text
                    maxFontSizeMultiplier={MAX_SCALE}
                    style={[
                      font(T.amount),
                      styles.abs,
                      { right: LS.padX * S, width: LS.amountW * S, textAlign: 'right', top: 2 * S, fontVariant: ['tabular-nums'] },
                    ]}
                  >
                    {row.amount}
                  </Text>
                  <Text
                    maxFontSizeMultiplier={MAX_SCALE}
                    style={[
                      font(T.small, sys.ink.success),
                      styles.abs,
                      { right: LS.padX * S, width: LS.amountW * S, textAlign: 'right', top: 24 * S },
                    ]}
                  >
                    {row.status}
                  </Text>
                </View>
              );
            })}
          </View>

          {/* the card and its film (drawn over the header's bottom and the tiles' top for the falling sheet) */}
          <VacuumSeal
            seal={seal}
            autoPlay={autoPlay}
            contentKey={fontsLoaded ? 1 : 0}
            radius={ref.radius.card * S}
            accessibilityLabel={COPY.a11y.card(sealed)}
            onReady={() => setFilmReady(true)}
            style={[styles.abs, { left: C.x * S, top: L.yt(C.y), width: C.w * S, height: C.h * S }]}
          >
            <BankCard S={S} />
          </VacuumSeal>
        </ScrollView>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: sys.surface.page },
  abs: { position: 'absolute' },
  fill: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  center: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  title: { position: 'absolute', left: 0, textAlign: 'center' },
  iconBtn: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  tile: { position: 'absolute', backgroundColor: sys.surface.tile, borderCurve: 'continuous', overflow: 'hidden' },
  disc: { position: 'absolute', overflow: 'hidden' },
  labelRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center' },
  morphPart: { position: 'absolute', left: 0, top: 0 },
  listHeader: { position: 'absolute', flexDirection: 'row', alignItems: 'baseline' },
  listTitle: { flex: 1, minWidth: 0 },
  seeAll: { flexShrink: 0 },
  panel: { backgroundColor: sys.surface.tile, borderCurve: 'continuous', overflow: 'hidden' },
  badge: { position: 'absolute', backgroundColor: ref.color.gold, alignItems: 'center', justifyContent: 'center' },
});
