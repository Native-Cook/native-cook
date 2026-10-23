import { Image } from 'expo-image';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { spacing } from '../../../shared/theme';

import { CollectionCardBack } from './collection-card-back';
import { useCardNavigation } from '../navigation';
import type { GalleryItem } from '../models/gallery-item';
import { CARD_HEIGHT_RATIO } from '../theme/card-template';

type CardThumbnailProps = {
  item: GalleryItem;
  width: number;
};

export function CardThumbnail({ item, width }: CardThumbnailProps) {
  const { openCard } = useCardNavigation();

  if (item.kind === 'locked') {
    return (
      <View
        accessibilityLabel={`${item.lockCount} locked cards`}
        accessible
        style={[styles.container, { width }]}
      >
        <CollectionCardBack markerCount={item.lockCount} markerVariant="lock" width={width} />
      </View>
    );
  }

  const height = width * CARD_HEIGHT_RATIO;
  const borderRadius = 10 * (width / 218);

  return (
    <Pressable
      accessibilityHint="Mở màn hình chi tiết và hiệu ứng holographic"
      accessibilityLabel={`${item.name}, ${item.count} thẻ trong bộ sưu tập`}
      accessibilityRole="link"
      onPress={() => openCard(item.cardId)}
      style={({ pressed }) => [styles.container, { opacity: pressed ? 0.72 : 1, width }]}
    >
      <View style={[styles.imageFrame, { borderRadius, height, width }]}>
        <Image
          accessibilityLabel={item.name}
          contentFit="cover"
          source={item.image}
          style={styles.image}
        />
      </View>

      <View style={styles.countBadge}>
        <Text allowFontScaling={false} style={styles.countText}>
          {item.count}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  image: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    transform: [{ scale: 1.035 }],
  },
  imageFrame: {
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  countBadge: {
    alignItems: 'center',
    alignSelf: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.86)',
    borderColor: 'rgba(255, 255, 255, 0.96)',
    borderCurve: 'continuous',
    borderRadius: 999,
    borderWidth: 1,
    height: 22,
    justifyContent: 'center',
    marginTop: 10,
    width: 22,
  },
  countText: {
    color: '#8a94a1',
    fontFamily: 'Georgia',
    fontSize: 10,
    fontVariant: ['tabular-nums'],
    lineHeight: 12,
  },
});
