import React, { useState, type ComponentProps, type ReactNode } from 'react';
import { Pressable, View, type StyleProp, type TextStyle } from 'react-native';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, { cubicBezier, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import { StickerWordmark } from './FX';
import { TILE, pitTile, slapPose, type PeelState } from './motion';
import { ASCENT, alpha, colors, copy, fonts, layout, radius, space, symbols, type } from './theme';
import { Sym, Txt, type SymbolSpec } from './ui';

const STATS_ART = require('./assets/PaddockWidgetBento-stats-stack.png');
const BANNER_PHOTO = require('./assets/PaddockWidgetBento-banner-photo.jpg');
const PRESS_SCALE = 0.97;
/** Press-in is quick feedback; the release settles a little slower (asymmetric, like a real button). */
const PRESS_MS = 100;
const RELEASE_MS = 180;
const EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

export type Destination =
  | 'laps' | 'reflex' | 'homeCards' | 'pitcast' | 'stats' | 'glossary' | 'education' | 'widgetStudio' | 'guide' | 'closeGuide' | 'banner'
  | 'home' | 'calendar' | 'results' | 'circuits' | 'customize';

const CARD_FILL = [colors.violet, colors.orange, colors.yellow];
const CARD_COPY = [copy.cards.laps, copy.cards.reflex, copy.cards.homeCards];

/** Card background, so a lifted card can cast a shadow of its own capsule. */
export const cardFill = (id: number) => CARD_FILL[id];

/** One line of text placed by its baseline, like the pin's measurements: its top is `baseline - ASCENT * size`. */
export function Line({ text, x, baseline, size, family, color, tracking = 0, s, style }: {
  text: string; x: number; baseline: number; size: number; family: string | undefined; color: string; tracking?: number; s: number; style?: StyleProp<TextStyle>;
}) {
  return (
    <Txt
      numberOfLines={1}
      style={[{ position: 'absolute', left: x * s, top: (baseline - ASCENT * size) * s, fontFamily: family, fontSize: size * s, letterSpacing: tracking * s, color }, style]}
    >
      {text}
    </Txt>
  );
}

/** A centred mark (symbol) in a tile, `size` square around (cx, cy). */
function Mark({ symbol, cx, cy, size, color, s, weight = 'semibold' }: { symbol: SymbolSpec; cx: number; cy: number; size: number; color: string; s: number; weight?: 'medium' | 'semibold' | 'bold' }) {
  return (
    <View style={{ position: 'absolute', left: (cx - size / 2) * s, top: (cy - size / 2) * s }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Sym symbol={symbol} size={size * s} color={color} weight={weight} />
    </View>
  );
}

/** Eyebrow + title, the pin's two-line tile label (12.5 SemiBold over 16.5 Bold, slightly tightened). */
function TileLabel({ eyebrow, title, x, color, s }: { eyebrow: string; title: string; x: number; color: string; s: number }) {
  const b = layout.baseline;
  return (
    <>
      <Line text={eyebrow} x={x} baseline={b.eyebrow} size={type.eyebrow} family={fonts.semibold} tracking={type.eyebrowTracking} color={color} s={s} />
      <Line text={title} x={x} baseline={b.title} size={type.tileTitle} family={fonts.bold} tracking={type.tileTitleTracking} color={color} s={s} />
    </>
  );
}

/**
 * Static quick-card content. The violet card is lettering with a badge, the orange one a label with a controller
 * chip on the right (it gives way to the drag handle while arranging), the yellow one a mark and a label.
 */
export function CardFace({ id, s, chipStyle }: { id: number; s: number; chipStyle?: ComponentProps<typeof Animated.View>['style'] }) {
  const q = layout.quickCard;
  const mid = q.height / 2;
  if (id === 0) {
    const badge = layout.violetBadge;
    const b = layout.baseline;
    return (
      <View style={{ flex: 1 }}>
        <View
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={{
            position: 'absolute', left: (badge.centerX - badge.width / 2) * s, top: (mid - badge.height / 2) * s, width: badge.width * s, height: badge.height * s,
            borderRadius: (badge.height / 2) * s, borderWidth: badge.stroke * s, borderColor: colors.white, alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Txt style={{ fontFamily: fonts.black, fontSize: type.badge * s, color: colors.white, letterSpacing: -0.4 * s }}>{copy.badge}</Txt>
        </View>
        <Line text={CARD_COPY[0].eyebrow} x={b.violetX} baseline={b.violet[0]} size={type.violet} family={fonts.extraBold} tracking={type.violetTracking} color={colors.white} s={s} />
        <Line text={CARD_COPY[0].title} x={b.violetX} baseline={b.violet[1]} size={type.violet} family={fonts.extraBold} tracking={type.violetTracking} color={colors.white} s={s} />
      </View>
    );
  }
  if (id === 1) {
    const chip = layout.orangeChip;
    return (
      <View style={{ flex: 1 }}>
        <TileLabel eyebrow={CARD_COPY[1].eyebrow} title={CARD_COPY[1].title} x={space.tileInset} color={colors.white} s={s} />
        <Animated.View
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={[
            {
              position: 'absolute', left: (q.width - chip.trailing - chip.width) * s, top: (mid - chip.height / 2) * s, width: chip.width * s, height: chip.height * s,
              borderRadius: radius.chip * s, borderCurve: 'continuous', backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center',
            },
            chipStyle,
          ]}
        >
          <Sym symbol={symbols.reflex} size={type.chipIcon * s} color={colors.orange} weight="bold" />
        </Animated.View>
      </View>
    );
  }
  return (
    <View style={{ flex: 1 }}>
      <Mark symbol={symbols.homeCards} cx={space.tileMarkCenter} cy={mid} size={type.cardIcon} color={colors.black} s={s} weight="bold" />
      <TileLabel eyebrow={CARD_COPY[2].eyebrow} title={CARD_COPY[2].title} x={space.tileTextAfterMark} color={colors.black} s={s} />
    </View>
  );
}

/** The pit-stop pose of tile `index` as a transform; `shake` (points) adds the sticker slap to the hero card. */
function usePitStyle(index: number, pit: SharedValue<number>, s: number, peel?: SharedValue<PeelState>) {
  return useAnimatedStyle(() => {
    const p = pitTile(index, pit.get());
    const shake = peel ? slapPose(peel.get().slap).shake : 0;
    return {
      transform: [{ translateX: (p.x + shake) * s }, { translateY: p.y * s }, { rotate: `${p.rotate}deg` }, { scaleX: p.sx }, { scaleY: p.sy }],
    };
  });
}

type TileProps = {
  index: number; left: number; top: number; width: number; height: number; corner: number; s: number; pit: SharedValue<number>; peel?: SharedValue<PeelState>;
  label: string; onPress: () => void; fill?: string; children: ReactNode; clip?: boolean;
};

/** A bento tile: its pit-stop pose on the UI thread, and a 0.97 press (100 ms in, 180 ms out, ease-out) that commits on release. */
function Tile({ index, left, top, width, height, corner, s, pit, peel, label, onPress, fill, children, clip }: TileProps) {
  const pose = usePitStyle(index, pit, s, peel);
  const [pressed, setPressed] = useState(false);
  return (
    <Animated.View style={[{ position: 'absolute', left: left * s, top: top * s, width: width * s, height: height * s }, pose]}>
      <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} onPressIn={() => setPressed(true)} onPressOut={() => setPressed(false)} style={{ flex: 1 }}>
        <Animated.View
          style={{
            flex: 1, borderRadius: corner * s, borderCurve: 'continuous', backgroundColor: fill, overflow: clip ? 'hidden' : 'visible',
            transform: [{ scale: pressed ? PRESS_SCALE : 1 }], transitionProperty: 'transform', transitionDuration: pressed ? PRESS_MS : RELEASE_MS, transitionTimingFunction: EASE_OUT,
          }}
        >
          {children}
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

type Props = { s: number; guideVisible: boolean; pit: SharedValue<number>; peel: SharedValue<PeelState>; onAction: (destination: Destination) => void; heroOverlay?: ReactNode };

/** The stationary bento rows. The three reorderable quick cards are drawn by the parent. */
export function StaticGrid({ s, guideVisible, pit, peel, onAction, heroOverlay }: Props) {
  const l = layout;
  const b = l.baseline;
  const a = l.actionTile;
  const mid = a.height / 2;
  const P = l.pitcast;
  return (
    <View style={{ position: 'absolute', left: 0, top: 0, width: l.grid.width * s, height: l.grid.height * s }} pointerEvents="box-none">
      <Tile index={TILE.pitcast} left={P.left} top={P.top} width={P.width} height={P.height} corner={radius.card} fill={colors.cream} s={s} pit={pit} label={copy.pitcast.label} onPress={() => onAction('pitcast')}>
        <View style={{ position: 'absolute', left: space.pitcastInset * s, top: space.pitcastInset * s, width: P.mic * s, height: P.mic * s, borderRadius: radius.mic * s, borderCurve: 'continuous', backgroundColor: colors.orange, alignItems: 'center', justifyContent: 'center' }}>
          <Sym symbol={symbols.mic} size={type.pitcastIcon * s} color={colors.micInk} weight="semibold" />
        </View>
        <View style={{ position: 'absolute', left: (P.width - space.pitcastArrowInset - P.arrow) * s, top: space.pitcastArrowInset * s, width: P.arrow * s, height: P.arrow * s, borderRadius: (P.arrow / 2) * s, backgroundColor: colors.black, alignItems: 'center', justifyContent: 'center' }}>
          <Sym symbol={symbols.arrowUpRight} size={type.pitcastArrow * s} color={colors.white} weight="heavy" />
        </View>
        <View style={{ position: 'absolute', left: space.pitcastInset * s, top: (b.pitcastTitle - ASCENT * type.pitcastTitle) * s, flexDirection: 'row', alignItems: 'baseline' }}>
          <Txt style={{ fontFamily: fonts.black, fontSize: type.pitcastTitle * s, letterSpacing: -0.6 * s, color: colors.black }}>{copy.pitcast.title[0]}</Txt>
          <Txt style={{ fontFamily: fonts.serif, fontStyle: 'italic', fontWeight: '600', fontSize: type.pitcastTitle * s, letterSpacing: -0.8 * s, color: colors.black }}>{copy.pitcast.title[1]}</Txt>
        </View>
        <Line text={copy.pitcast.subtitle} x={space.pitcastInset} baseline={b.pitcastSubtitle} size={type.pitcastSubtitle} family={fonts.medium} color={colors.black} s={s} />
        <Line text={copy.pitcast.names} x={space.pitcastInset} baseline={b.pitcastNames} size={type.pitcastNames} family={fonts.medium} color={colors.black} s={s} style={{ opacity: alpha.pitcastNames }} />
        {[symbols.podcastListen, symbols.podcastWatch, symbols.podcastFeed].map((symbol, index) => (
          <Mark key={symbol.ios} symbol={symbol} cx={P.glyphX[index]} cy={P.glyphRow} size={P.glyph} color={colors.black} s={s} />
        ))}
      </Tile>

      <Tile index={TILE.stats} left={l.stats.left} top={l.stats.top} width={l.stats.width} height={l.stats.height} corner={radius.card} s={s} pit={pit} label={copy.stats.label} onPress={() => onAction('stats')} clip>
        <LinearGradient colors={[colors.statsGlow, colors.statsShade]} start={{ x: 0.12, y: 0.85 }} end={{ x: 1, y: 0.15 }} style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 }} />
        <Image source={STATS_ART} contentFit="contain" accessible={false} style={{ position: 'absolute', left: l.stats.art.x * s, top: l.stats.art.y * s, width: l.stats.art.width * s, height: l.stats.art.height * s }} />
        <View style={{ position: 'absolute', left: l.stats.icon.x * s, top: l.stats.icon.y * s, width: l.stats.icon.width * s, height: l.stats.icon.height * s, alignItems: 'center', justifyContent: 'center' }}>
          <Sym symbol={symbols.document} size={type.statsIcon * s} color={colors.white} />
        </View>
        <Line text={copy.stats.race} x={space.statsTextX} baseline={b.statsEyebrow} size={type.statsEyebrow} family={fonts.bold} color={colors.white} s={s} />
        <Line text={copy.stats.word} x={space.statsTextX} baseline={b.statsWord} size={type.statsWord} family={fonts.bold} color={colors.white} s={s} />
        {copy.stats.caption.map((line, index) => (
          <Line key={line} text={line} x={space.statsCaptionX} baseline={b.caption[index]} size={type.caption} family={fonts.medium} color={colors.white} s={s} style={{ opacity: alpha.captionInk }} />
        ))}
      </Tile>

      <Tile index={TILE.glossary} left={l.glossary.left} top={l.glossary.top} width={a.width} height={a.height} corner={radius.quickCard} fill={colors.green} s={s} pit={pit} label={copy.glossary} onPress={() => onAction('glossary')}>
        <Mark symbol={symbols.glossary} cx={space.tileMarkCenter} cy={mid} size={type.tileIcon} color={colors.black} s={s} weight="bold" />
        <TileLabel eyebrow={copy.brand} title={copy.glossaryTitle} x={space.tileTextAfterMark} color={colors.black} s={s} />
      </Tile>
      <Tile index={TILE.education} left={l.education.left} top={l.education.top} width={a.width} height={a.height} corner={radius.quickCard} fill={colors.blue} s={s} pit={pit} label={copy.education} onPress={() => onAction('education')}>
        <Mark symbol={symbols.education} cx={space.tileMarkCenter} cy={mid} size={type.tileIcon} color={colors.creamInk} s={s} />
        <TileLabel eyebrow={copy.brand} title={copy.educationTitle} x={space.tileTextAfterMark} color={colors.white} s={s} />
      </Tile>

      <Tile index={TILE.hero} left={l.hero.left} top={l.hero.top} width={l.hero.width} height={l.hero.height} corner={radius.card} fill={colors.red} s={s} pit={pit} peel={peel} label={copy.hero.label} onPress={() => onAction('widgetStudio')}>
        <StickerWordmark s={s} peel={peel} />
        <View
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={{
            position: 'absolute', left: (l.hero.seal.centerX - l.hero.seal.size / 2) * s, top: (l.hero.seal.centerY - l.hero.seal.size / 2) * s,
            width: l.hero.seal.size * s, height: l.hero.seal.size * s, borderRadius: (l.hero.seal.size / 2) * s,
            backgroundColor: colors.white, borderWidth: 1, borderColor: colors.sealStroke, alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Txt style={{ fontFamily: fonts.black, fontSize: type.heroSeal * s, letterSpacing: -0.4 * s, color: colors.red }}>{copy.hero.seal}</Txt>
        </View>
        {heroOverlay}
      </Tile>

      {guideVisible ? (
        <Tile index={TILE.guide} left={l.guide.left} top={l.guide.top} width={l.guide.width} height={l.guide.height} corner={radius.card} fill={colors.guideYellow} s={s} pit={pit} label={`${copy.guide.title} ${copy.guide.helper.join(' ')}`} onPress={() => onAction('guide')}>
          <Line text={copy.guide.title} x={space.guideInset} baseline={b.guideTitle} size={type.guideTitle} family={fonts.extraBold} color={colors.black} s={s} />
          {copy.guide.helper.map((line, index) => (
            <Line key={line} text={line} x={space.guideInset} baseline={b.guideHelper[index]} size={type.guideHelper} family={fonts.medium} color={colors.black} s={s} />
          ))}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={copy.guide.close}
            onPress={() => onAction('closeGuide')}
            hitSlop={(layout.unscaled.closeButton - l.guide.close.size * s) / 2}
            style={{
              position: 'absolute', left: (l.guide.width - space.guideCloseInset - l.guide.close.size) * s, top: (l.guide.close.centerY - l.guide.close.size / 2) * s,
              width: l.guide.close.size * s, height: l.guide.close.size * s, borderRadius: (l.guide.close.size / 2) * s, backgroundColor: colors.closeFill, alignItems: 'center', justifyContent: 'center',
            }}
          >
            <Sym symbol={symbols.close} size={type.guideClose * s} color={colors.guideYellow} weight="black" />
          </Pressable>
        </Tile>
      ) : null}

      <Tile index={TILE.banner} left={l.banner.left} top={l.banner.top} width={l.banner.width} height={l.banner.height} corner={radius.card} s={s} pit={pit} label={copy.banner.label} onPress={() => onAction('banner')} clip>
        <Image source={BANNER_PHOTO} contentFit="cover" accessible={false} style={{ width: l.banner.width * s, height: l.banner.height * s }} />
        <LinearGradient colors={[colors.bannerShade, colors.bannerGlow, colors.bannerClear]} start={{ x: 0, y: 1 }} end={{ x: 1, y: 0 }} style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 }} />
        <View style={{ position: 'absolute', left: 0, top: space.bannerTop * s, paddingHorizontal: space.bannerPad * s, gap: space.bannerGap * s }}>
          <Txt numberOfLines={2} style={{ fontFamily: fonts.extraBold, fontSize: type.bannerTitle * s, letterSpacing: -0.6 * s, color: colors.white }}>{copy.banner.title}</Txt>
          <Txt numberOfLines={2} style={{ fontFamily: fonts.medium, fontSize: type.bannerSubtitle * s, color: colors.white }}>{copy.banner.subtitle}</Txt>
        </View>
      </Tile>
    </View>
  );
}
