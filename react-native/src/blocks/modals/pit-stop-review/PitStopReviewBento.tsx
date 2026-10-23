import React, { memo, useState, type ComponentProps, type ReactNode } from 'react';
import { Platform, Pressable, Text, View, type StyleProp, type TextProps, type TextStyle, type ViewStyle } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { SymbolView } from 'expo-symbols';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { LinearGradient } from 'expo-linear-gradient';
import { ASCENT, bento as B, colors, fonts, rail as RL } from './theme';

/**
 * The pin's lower half — shortcut rail and bento — drawn like the sibling Paddock block (same app): Montserrat labels
 * placed by baseline, SF Symbols (Material icons off iOS), Paddock's tile faces. Copied, not imported, so each
 * block stays self-contained.
 */
type SymbolProps = ComponentProps<typeof SymbolView>;
export type SymbolSpec = { readonly ios: string; readonly fallback: string; readonly fit: number };
const SYM = {
  calendarClock: { ios: 'calendar.badge.clock', fallback: 'calendar-clock-outline', fit: 1 },
  controller: { ios: 'gamecontroller', fallback: 'controller-classic-outline', fit: 1 },
  reflex: { ios: 'gamecontroller.fill', fallback: 'gamepad-variant', fit: 1.1 },
  homeCards: { ios: 'square.grid.2x2.fill', fallback: 'view-grid', fit: 1.05 },
  mic: { ios: 'mic.fill', fallback: 'microphone', fit: 1 },
  arrowUpRight: { ios: 'arrow.up.right', fallback: 'arrow-top-right', fit: 1 },
  podcastListen: { ios: 'headphones.circle.fill', fallback: 'headphones', fit: 1.05 },
  podcastWatch: { ios: 'play.rectangle.fill', fallback: 'play-box', fit: 1.05 },
  podcastFeed: { ios: 'antenna.radiowaves.left.and.right', fallback: 'access-point', fit: 1 },
  document: { ios: 'doc.text.fill', fallback: 'file-document', fit: 1.17 },
} as const;
const COPY = {
  laps: { eyebrow: 'every lap', title: 'counts', label: 'every lap counts' },
  reflex: { eyebrow: 'Race day', title: 'Reflex test', label: 'Race day Reflex test' },
  homeCards: { eyebrow: 'custom', title: 'home cards', label: 'custom home cards' },
  badge: 'TN',
  pitcast: { title: ['pit', 'cast'], subtitle: 'Every Friday at 6pm', names: 'Alex Vale / Sam Ives', label: 'Read the weekend brief, Alex Vale and Sam Ives' },
} as const;
const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });
const PRESS_SCALE = 0.97;

export function Sym({ symbol, size, color, weight, style }: { symbol: SymbolSpec; size: number; color: string; weight?: SymbolProps['weight']; style?: StyleProp<ViewStyle> }) {
  if (Platform.OS === 'ios') {
    return (
      <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }, style]}>
        <SymbolView name={symbol.ios as SymbolProps['name']} size={size * symbol.fit} tintColor={color} weight={weight} />
      </View>
    );
  }
  return <MaterialCommunityIcons name={symbol.fallback as keyof typeof MaterialCommunityIcons.glyphMap} size={size} color={color} style={style as StyleProp<TextStyle>} />;
}
function Txt(props: TextProps) { return <Text allowFontScaling={false} {...props} />; }

/** One line of text placed by its baseline. */
function Line({ text, x, baseline, size, family, color, tracking = 0, s, style }: { text: string; x: number; baseline: number; size: number; family: string; color: string; tracking?: number; s: number; style?: StyleProp<TextStyle> }) {
  return <Txt numberOfLines={1} style={[{ position: 'absolute', left: x * s, top: (baseline - ASCENT * size) * s, fontFamily: family, fontSize: size * s, letterSpacing: tracking * s, color }, style]}>{text}</Txt>;
}
function TileLabel({ eyebrow, title, x, color, s }: { eyebrow: string; title: string; x: number; color: string; s: number }) {
  const b = B.baseline, t = B.type;
  return <>
    <Line text={eyebrow} x={x} baseline={b.eyebrow} size={t.eyebrow} family={fonts.semibold} tracking={t.eyebrowTracking} color={color} s={s} />
    <Line text={title} x={x} baseline={b.title} size={t.title} family={fonts.bold} tracking={t.titleTracking} color={color} s={s} />
  </>;
}
function Tile({ left, top, width, height, corner, fill, label, onPress, s, children }: { left: number; top: number; width: number; height: number; corner: number; fill: string; label: string; onPress: () => void; s: number; children: ReactNode }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} onPressIn={() => setPressed(true)} onPressOut={() => setPressed(false)} style={{ position: 'absolute', left: left * s, top: top * s, width: width * s, height: height * s }}>
      <View style={{ flex: 1, borderRadius: corner * s, borderCurve: 'continuous', backgroundColor: fill, transform: [{ scale: pressed ? PRESS_SCALE : 1 }] }}>{children}</View>
    </Pressable>
  );
}

export type Open = (destination: 'calendar' | 'reflex' | 'customize' | 'pass' | 'laps' | 'pitcast' | 'results') => void;

export const Bento = memo(function Bento({ s, cards, onOpen }: { s: number; cards: boolean[]; onOpen: Open }) {
  const onLaps = () => onOpen('laps'), onReflex = () => onOpen('reflex'), onHomeCards = () => onOpen('customize'), onPitcast = () => onOpen('pitcast');
  const T = B.tile, t = B.type, b = B.baseline, C = B.colors, P = B.pitcast;
  const mid = T.height / 2;
  const rows = [0, 1, 2].filter((i) => cards[i]);
  const rowTop = (i: number) => B.top + rows.indexOf(i) * T.pitch;
  return (
    <>
      {cards[0] && <Tile left={B.left} top={rowTop(0)} width={T.width} height={T.height} corner={T.radius} fill={C.violet} label={COPY.laps.label} onPress={onLaps} s={s}>
        <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={{ position: 'absolute', left: (B.violetBadge.centerX - B.violetBadge.width / 2) * s, top: (mid - B.violetBadge.height / 2) * s, width: B.violetBadge.width * s, height: B.violetBadge.height * s, borderRadius: (B.violetBadge.height / 2) * s, borderWidth: B.violetBadge.stroke * s, borderColor: 'white', alignItems: 'center', justifyContent: 'center' }}>
          <Txt style={{ fontFamily: fonts.black, fontSize: t.badge * s, color: 'white', letterSpacing: -0.4 * s }}>{COPY.badge}</Txt>
        </View>
        <Line text={COPY.laps.eyebrow} x={b.violetX} baseline={b.violet[0]} size={t.violet} family={fonts.extraBold} tracking={t.violetTracking} color="white" s={s} />
        <Line text={COPY.laps.title} x={b.violetX} baseline={b.violet[1]} size={t.violet} family={fonts.extraBold} tracking={t.violetTracking} color="white" s={s} />
      </Tile>}
      {cards[1] && <Tile left={B.left} top={rowTop(1)} width={T.width} height={T.height} corner={T.radius} fill={C.orange} label={COPY.reflex.label} onPress={onReflex} s={s}>
        <TileLabel eyebrow={COPY.reflex.eyebrow} title={COPY.reflex.title} x={B.tileInset} color="white" s={s} />
        <View style={{ position: 'absolute', left: (T.width - B.orangeChip.trailing - B.orangeChip.width) * s, top: (mid - B.orangeChip.height / 2) * s, width: B.orangeChip.width * s, height: B.orangeChip.height * s, borderRadius: B.orangeChip.radius * s, borderCurve: 'continuous', backgroundColor: 'white', alignItems: 'center', justifyContent: 'center' }}>
          <Sym symbol={SYM.reflex} size={t.chipIcon * s} color={C.orange} weight="bold" />
        </View>
      </Tile>}
      {cards[2] && <Tile left={B.left} top={rowTop(2)} width={T.width} height={T.height} corner={T.radius} fill={C.yellow} label={COPY.homeCards.label} onPress={onHomeCards} s={s}>
        <View style={{ position: 'absolute', left: (B.tileMarkCenter - t.cardIcon / 2) * s, top: (mid - t.cardIcon / 2) * s }}><Sym symbol={SYM.homeCards} size={t.cardIcon * s} color="black" weight="bold" /></View>
        <TileLabel eyebrow={COPY.homeCards.eyebrow} title={COPY.homeCards.title} x={B.tileTextAfterMark} color="black" s={s} />
      </Tile>}
      <Tile left={P.left} top={B.top} width={P.width} height={P.height} corner={P.radius} fill={C.cream} label={COPY.pitcast.label} onPress={onPitcast} s={s}>
        <View style={{ position: 'absolute', left: P.inset * s, top: P.inset * s, width: P.mic * s, height: P.mic * s, borderRadius: P.micRadius * s, borderCurve: 'continuous', backgroundColor: C.orange, alignItems: 'center', justifyContent: 'center' }}>
          <Sym symbol={SYM.mic} size={t.pitcastIcon * s} color={C.micInk} weight="semibold" />
        </View>
        <View style={{ position: 'absolute', left: (P.width - P.arrowInset - P.arrow) * s, top: P.arrowInset * s, width: P.arrow * s, height: P.arrow * s, borderRadius: (P.arrow / 2) * s, backgroundColor: 'black', alignItems: 'center', justifyContent: 'center' }}>
          <Sym symbol={SYM.arrowUpRight} size={t.pitcastArrow * s} color="white" weight="heavy" />
        </View>
        <View style={{ position: 'absolute', left: P.inset * s, top: (b.pitcastTitle - ASCENT * t.pitcastTitle) * s, flexDirection: 'row', alignItems: 'baseline' }}>
          <Txt style={{ fontFamily: fonts.black, fontSize: t.pitcastTitle * s, letterSpacing: -0.6 * s, color: 'black' }}>{COPY.pitcast.title[0]}</Txt>
          <Txt style={{ fontFamily: SERIF, fontStyle: 'italic', fontWeight: '600', fontSize: t.pitcastTitle * s, letterSpacing: -0.8 * s, color: 'black' }}>{COPY.pitcast.title[1]}</Txt>
        </View>
        <Line text={COPY.pitcast.subtitle} x={P.inset} baseline={b.pitcastSubtitle} size={t.pitcastSubtitle} family={fonts.medium} color="black" s={s} />
        <Line text={COPY.pitcast.names} x={P.inset} baseline={b.pitcastNames} size={t.pitcastNames} family={fonts.medium} color="black" s={s} style={{ opacity: 0.9 }} />
        {[SYM.podcastListen, SYM.podcastWatch, SYM.podcastFeed].map((symbol, i) => (
          <View key={symbol.ios} style={{ position: 'absolute', left: (P.glyphX[i] - P.glyph / 2) * s, top: (P.glyphRow - P.glyph / 2) * s }}><Sym symbol={symbol} size={P.glyph * s} color="black" /></View>
        ))}
      </Tile>
    </>
  );
});

/** The race stats card under the bento (Paddock's stats card face, art left out: it sits under the dock). */
export const StatsCard = memo(function StatsCard({ s, top, onOpen }: { s: number; top: number; onOpen: Open }) {
  const S = B.stats;
  const onPress = () => onOpen('results');
  return (
    <Tile left={B.left} top={top} width={S.width} height={S.height} corner={S.radius} fill="transparent" label="Turn Nine stats" onPress={onPress} s={s}>
      <View style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, borderRadius: S.radius * s, borderCurve: 'continuous', overflow: 'hidden' }}>
        <LinearGradient colors={[S.glow, S.shade]} start={{ x: 0.12, y: 0.85 }} end={{ x: 1, y: 0.15 }} style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 }} />
      </View>
      <View style={{ position: 'absolute', left: S.icon.x * s, top: S.icon.y * s, width: S.icon.width * s, height: S.icon.height * s, alignItems: 'center', justifyContent: 'center' }}>
        <Sym symbol={SYM.document} size={S.iconSize * s} color="white" />
      </View>
      <Line text="Turn Nine" x={S.textX} baseline={S.eyebrowBaseline} size={S.eyebrow} family={fonts.bold} color="white" s={s} />
      <Line text="Stats" x={S.textX} baseline={S.wordBaseline} size={S.word} family={fonts.bold} color="white" s={s} />
      {S.caption.map((line, i) => <Line key={line} text={line} x={S.captionX} baseline={S.captionBaselines[i]} size={S.captionSize} family={fonts.medium} color="white" s={s} />)}
    </Tile>
  );
});

/** "+ ● / ■ ×" shapes glyph of the pin's third rail icon, in a 24-unit box. */
function ShapesIcon({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessible={false}>
      <Path d="M5.5 1.5 V9.5 M1.5 5.5 H9.5" stroke={colors.ink} strokeWidth={2.6} strokeLinecap="round" />
      <Circle cx={18.5} cy={5.5} r={4.3} fill={colors.ink} />
      <Rect x={1.3} y={14.3} width={8.4} height={8.4} rx={2.2} fill={colors.ink} />
      <Path d="M15.2 15.2 L21.8 21.8 M21.8 15.2 L15.2 21.8" stroke={colors.ink} strokeWidth={2.6} strokeLinecap="round" />
    </Svg>
  );
}
/** Circle with a four-point sparkle, the pin's fourth rail icon. */
function SparkleIcon({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessible={false}>
      <Circle cx={12} cy={12} r={10.6} stroke={colors.ink} strokeWidth={2} fill="none" />
      <Path d="M12 5.4 Q12.9 11.1 18.6 12 Q12.9 12.9 12 18.6 Q11.1 12.9 5.4 12 Q11.1 11.1 12 5.4 Z" fill={colors.ink} />
    </Svg>
  );
}

/** The shortcut rail: four buttons, glyphs and hairline dividers at the pin's centres. */
const RAIL_ACTIONS = [['Race weekends', 'calendar'], ['Reaction test', 'reflex'], ['Customize home cards', 'customize'], ['Club Pass', 'pass']] as const;
export const Rail = memo(function Rail({ s, width, onOpen }: { s: number; width: number; onOpen: Open }) {
  const actions = RAIL_ACTIONS.map(([label, d]) => ({ label, onPress: () => onOpen(d) }));
  const left = (width / s - RL.width) / 2;
  const icons = [
    <Sym key="c" symbol={SYM.calendarClock} size={RL.calendar * s} color={colors.ink} weight="medium" />,
    <Sym key="g" symbol={SYM.controller} size={RL.controller * s} color={colors.ink} weight="medium" />,
    <ShapesIcon key="s" size={RL.shapes * s} />,
    <SparkleIcon key="k" size={RL.sparkle * s} />,
  ];
  return (
    <View style={{ position: 'absolute', left: left * s, top: RL.top * s, width: RL.width * s, height: RL.height * s, borderRadius: (RL.height / 2) * s, backgroundColor: RL.fill, borderWidth: 1, borderColor: RL.stroke }}>
      {actions.map((a, i) => {
        const cx = RL.iconX[i] - left;
        return (
          <Pressable key={a.label} accessibilityRole="button" accessibilityLabel={a.label} onPress={a.onPress} style={{ position: 'absolute', left: (cx - 30) * s, top: 0, width: 60 * s, height: RL.height * s, alignItems: 'center', justifyContent: 'center' }}>
            {icons[i]}
          </Pressable>
        );
      })}
      {[0, 1, 2].map((i) => {
        const x = (RL.iconX[i] + RL.iconX[i + 1]) / 2 - left;
        return <View key={i} pointerEvents="none" style={{ position: 'absolute', left: x * s, top: ((RL.height - RL.divider.height) / 2) * s, width: 1, height: RL.divider.height * s, backgroundColor: RL.divider.color }} />;
      })}
    </View>
  );
});
