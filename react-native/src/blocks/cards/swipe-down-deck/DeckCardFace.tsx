import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { AlertIcon, DeckArt, type DeckArtKind, ICON_ADD, PersonIcon, StrokeIcon } from './DeckArt';

export type DeckCard = {
  title: string;
  /** The card's flat colour, as #rrggbb. */
  color: string;
  art: DeckArtKind;
  level: number;
  badge: string;
  tags: string[];
  /** The chips that show this card. */
  filters: string[];
  description: string;
};

const PAD = 16;
const PILL_H = 26;
const ROUND = 32;
const PLATE = 184;
const ART = 150;
const RADIUS = 28;
const WASH = 'rgba(0,0,0,0.18)';

/** The face of a card: badges and the add button, the illustration on its plate, name, tags, text. */
export function DeckCardFace({
  card,
  index,
  width,
  height,
  onAdd,
}: {
  card: DeckCard;
  index: number;
  width: number;
  height: number;
  onAdd: () => void;
}) {
  return (
    <View style={[styles.card, { width, height, backgroundColor: card.color }]}>
      {/* Light from above and a little shade at the foot, so a flat colour reads as a surface. */}
      <LinearGradient
        colors={['rgba(255,255,255,0.16)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.18)']}
        locations={[0, 0.45, 1]}
        style={StyleSheet.absoluteFill}
      />
      <View style={styles.top}>
        <View style={styles.pill}>
          <PersonIcon color="#ffffff" />
          <Text style={styles.pillLabel}>{card.level}</Text>
        </View>
        <View style={styles.pill}>
          <AlertIcon color="#ffffff" ink={card.color} />
          <Text style={styles.pillLabel}>{card.badge}</Text>
        </View>
        <View style={styles.spacer} />
        <Pressable onPress={onAdd} hitSlop={6} accessibilityRole="button" accessibilityLabel={`Add ${card.title} to the deck`}>
          {({ pressed }) => (
            <View style={[styles.round, pressed && styles.pressed]}>
              <StrokeIcon d={ICON_ADD} color="#ffffff" size={16} width={1.8} />
            </View>
          )}
        </Pressable>
      </View>
      <View style={styles.spacer} />
      <DeckArt kind={card.art} plate={PLATE} art={ART} id={`plate-${index}`} />
      <Text style={styles.title} numberOfLines={1}>
        {card.title}
      </Text>
      <View style={styles.tags}>
        {card.tags.map((tag) => (
          <View key={tag} style={styles.tag}>
            <Text style={styles.tagLabel}>{tag}</Text>
          </View>
        ))}
      </View>
      <Text style={styles.description} numberOfLines={3} lineBreakStrategyIOS="standard">
        {card.description}
      </Text>
      <View style={styles.spacer} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    alignItems: 'center',
    paddingTop: PAD,
    paddingHorizontal: PAD,
    paddingBottom: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  top: {
    alignSelf: 'stretch',
    height: ROUND,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  pill: {
    height: PILL_H,
    paddingHorizontal: 10,
    borderRadius: PILL_H / 2,
    backgroundColor: WASH,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  pillLabel: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '600',
    includeFontPadding: false,
  },
  spacer: {
    flex: 1,
  },
  round: {
    width: ROUND,
    height: ROUND,
    borderRadius: ROUND / 2,
    backgroundColor: WASH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    backgroundColor: 'rgba(0,0,0,0.32)',
  },
  title: {
    marginTop: 18,
    color: '#ffffff',
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '400',
    letterSpacing: -0.4,
    includeFontPadding: false,
  },
  tags: {
    marginTop: 10,
    flexDirection: 'row',
    gap: 6,
  },
  tag: {
    height: 24,
    paddingHorizontal: 10,
    borderRadius: 12,
    backgroundColor: WASH,
    justifyContent: 'center',
  },
  tagLabel: {
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
    includeFontPadding: false,
  },
  description: {
    marginTop: 14,
    color: 'rgba(255,255,255,0.82)',
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
    includeFontPadding: false,
  },
});
