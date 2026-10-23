import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { FlatList, Platform, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { radii, spacing } from '../../../shared/theme';

import { CardThumbnail } from '../components/card-thumbnail';
import { galleryItems } from '../data/gallery-items';
import { CARD_HEIGHT_RATIO, COLLECTION_GALLERY } from '../theme/card-template';

const COLLECTION_BUTTON_HEIGHT = 42;
const MOBILE_COLUMN_GAP = 10;
const SHEET_RADIUS = 32;
const BACKDROP_COLORS = ['#d69aa8', '#e4b7bd', '#d8a6ae'] as const;
const FOOTER_FADE_COLORS = [
  'rgba(233, 237, 243, 0)',
  'rgba(233, 237, 243, 0.72)',
  'rgba(233, 237, 243, 0.97)',
] as const;

export function CardGalleryScreen() {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const horizontalPadding = spacing.lg;
  const columnGap = width >= 720 ? spacing.lg : MOBILE_COLUMN_GAP;
  const cardWidth = Math.min((width - horizontalPadding * 2 - columnGap) / 2, 218);
  const cardHeight = cardWidth * CARD_HEIGHT_RATIO;
  const gridWidth = cardWidth * 2 + columnGap;
  const sheetTop = Math.max(insets.top + spacing.xs, spacing.xl);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <LinearGradient
        colors={BACKDROP_COLORS}
        end={{ x: 1, y: 0.8 }}
        start={{ x: 0, y: 0 }}
        style={styles.backdrop}
      />

      <View style={[styles.sheet, { marginTop: sheetTop }]}>
        <View style={styles.sheetGrabber} />

        <FlatList
          columnWrapperStyle={[styles.row, { gap: columnGap }]}
          contentContainerStyle={{
            paddingBottom: COLLECTION_BUTTON_HEIGHT + spacing.huge,
            paddingHorizontal: horizontalPadding,
            paddingTop: spacing.huge,
          }}
          contentInsetAdjustmentBehavior="automatic"
          data={galleryItems}
          keyExtractor={(item) => item.id}
          ListHeaderComponent={
            <View style={styles.header}>
              <Text style={styles.title}>
                Achievements and{`\n`}record of a growing mind.
              </Text>
            </View>
          }
          ListFooterComponent={
            <View style={[styles.ghostFooter, { height: cardHeight * 0.58, width: gridWidth }]}>
              <View style={[styles.ghostRow, { gap: columnGap }]}>
                {[0, 1].map((ghost) => (
                  <View
                    key={ghost}
                    style={[
                      styles.ghostCard,
                      {
                        borderRadius: 10 * (cardWidth / 218),
                        height: cardHeight,
                        width: cardWidth,
                      },
                    ]}
                  />
                ))}
              </View>
            </View>
          }
          numColumns={2}
          renderItem={({ item }) => <CardThumbnail item={item} width={cardWidth} />}
          showsVerticalScrollIndicator={false}
          style={styles.list}
        />

        <View style={[styles.fixedFooter, { paddingBottom: Math.max(insets.bottom, spacing.lg) }]}>
          <BlurView intensity={28} style={styles.footerBlur} tint="light" />
          <LinearGradient
            colors={FOOTER_FADE_COLORS}
            locations={[0, 0.46, 1]}
            style={styles.footerFade}
          />

          <View style={[styles.collectionButton, { width: Math.min(gridWidth * 0.63, 284) }]}>
            <Text allowFontScaling={false} style={styles.collectionButtonText}>
              My Collection
            </Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKDROP_COLORS[0],
  },
  backdrop: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheet: {
    backgroundColor: COLLECTION_GALLERY.background,
    borderCurve: 'continuous',
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    flex: 1,
    overflow: 'hidden',
  },
  list: {
    flex: 1,
  },
  header: {
    alignItems: 'center',
    alignSelf: 'center',
    marginBottom: spacing.xxl,
    maxWidth: 450,
    width: '100%',
  },
  title: {
    color: COLLECTION_GALLERY.text,
    fontFamily: Platform.select({
      android: 'serif',
      default: 'Georgia',
      web: 'Georgia, serif',
    }),
    fontSize: 25,
    fontWeight: '400',
    letterSpacing: -1.1,
    lineHeight: 26,
    textAlign: 'center',
  },
  row: {
    alignSelf: 'center',
    justifyContent: 'center',
  },
  sheetGrabber: {
    alignSelf: 'center',
    backgroundColor: 'rgba(103, 113, 124, 0.58)',
    borderRadius: radii.pill,
    height: 5,
    position: 'absolute',
    top: spacing.sm,
    width: 34,
    zIndex: 2,
  },
  ghostFooter: {
    alignItems: 'center',
    alignSelf: 'center',
    overflow: 'hidden',
    position: 'relative',
  },
  ghostRow: {
    flexDirection: 'row',
    opacity: 0.45,
    position: 'absolute',
    top: 0,
  },
  ghostCard: {
    backgroundColor: 'rgba(255, 255, 255, 0.56)',
    borderColor: 'rgba(255, 255, 255, 0.84)',
    borderCurve: 'continuous',
    borderWidth: 1,
  },
  fixedFooter: {
    alignItems: 'center',
    bottom: 0,
    left: 0,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.huge,
    pointerEvents: 'box-none',
    position: 'absolute',
    right: 0,
    zIndex: 3,
  },
  footerBlur: {
    bottom: 0,
    left: 0,
    opacity: 0.72,
    pointerEvents: 'none',
    position: 'absolute',
    right: 0,
    top: spacing.sm,
  },
  footerFade: {
    bottom: 0,
    left: 0,
    pointerEvents: 'none',
    position: 'absolute',
    right: 0,
    top: 0,
  },
  collectionButton: {
    alignItems: 'center',
    backgroundColor: 'rgba(238, 242, 247, 0.7)',
    borderColor: 'rgba(69, 79, 92, 0.44)',
    borderCurve: 'continuous',
    borderRadius: radii.pill,
    borderWidth: 1,
    height: COLLECTION_BUTTON_HEIGHT,
    justifyContent: 'center',
  },
  collectionButtonText: {
    color: COLLECTION_GALLERY.text,
    fontSize: 15,
    fontWeight: '400',
  },
});
