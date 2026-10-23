import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
  type ViewStyle,
} from 'react-native';
import Animated, { cubicBezier, type CSSTransitionProperties } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, radii, spacing, typeScale } from '../../../shared/theme';

import { AwakeningCard } from '../components/awakening-card';
import { CollectionCardBackdrop } from '../components/collection-card-backdrop';
import { getCardById } from '../data/cards';
import { useCardNavigation } from '../navigation';
import { COLLECTION_CARD_BACKDROP } from '../theme/card-template';

const SHEET_RADIUS = 32;
const SHEET_BACKDROP_COLORS = ['#d69aa8', '#e4b7bd', '#d8a6ae'] as const;

const PRESS_TRANSITION = {
  transitionDuration: '110ms',
  transitionProperty: ['backgroundColor', 'transform'],
  transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1),
} satisfies CSSTransitionProperties<ViewStyle>;

export function CardDetailScreen() {
  const { back, cardId: id } = useCardNavigation();
  const insets = useSafeAreaInsets();
  const { height, width } = useWindowDimensions();
  const [isDismissing, setIsDismissing] = useState(false);
  const card = getCardById(id);

  const handleBackPress = useCallback(() => {
    setIsDismissing(true);
  }, []);

  const handleExitReady = useCallback(() => {
    back();
  }, [back]);

  if (!card) {
    return (
      <View style={[styles.screen, styles.missing]}>
        <StatusBar style="light" />
        <Text style={styles.missingTitle}>Không tìm thấy thẻ</Text>
        <Text accessibilityRole="link" onPress={back} style={styles.missingLink}>
          Trở về bộ sưu tập
        </Text>
      </View>
    );
  }

  const cardWidth = Math.min(width - spacing.xxl * 2, 316);
  const sheetTop = Math.max(insets.top + spacing.xs, spacing.xl);
  const sheetHeight = Math.max(height - sheetTop, 1);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <LinearGradient
        colors={SHEET_BACKDROP_COLORS}
        end={{ x: 1, y: 0.8 }}
        start={{ x: 0, y: 0 }}
        style={styles.backdrop}
      />

      <View style={[styles.sheet, { marginTop: sheetTop }]}>
        <CollectionCardBackdrop height={sheetHeight} width={width} />
        <View style={styles.sheetGrabber} />

        <ScrollView
          contentContainerStyle={{
            minHeight: sheetHeight,
            paddingBottom: insets.bottom + spacing.xxl,
            paddingHorizontal: spacing.xl,
            paddingTop: spacing.md,
          }}
          contentInsetAdjustmentBehavior="automatic"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.topBar}>
            <Pressable
              accessibilityHint="Lá bài ổn định lại trước khi trở về danh sách"
              accessibilityLabel="Quay lại bộ sưu tập"
              accessibilityRole="button"
              accessibilityState={{ disabled: isDismissing }}
              disabled={isDismissing}
              hitSlop={12}
              onPress={handleBackPress}
              pressRetentionOffset={16}
              style={styles.backButtonHitArea}
            >
              {({ pressed }) => (
                <Animated.View
                  style={[
                    styles.backButton,
                    PRESS_TRANSITION,
                    pressed && styles.backButtonPressed,
                  ]}
                >
                  <SymbolView
                    name={{ ios: 'chevron.left', android: 'chevron_left', web: 'chevron_left' }}
                    size={14}
                    style={styles.backIcon}
                    tintColor="#667080"
                  />
                </Animated.View>
              )}
            </Pressable>
          </View>

          <View style={styles.cardStage}>
            <AwakeningCard
              exiting={isDismissing}
              onExitReady={handleExitReady}
              width={cardWidth}
            />
          </View>

          <View style={styles.pageMarker}>
            <Text allowFontScaling={false} style={styles.pageMarkerText}>
              1
            </Text>
          </View>

          <View style={styles.details}>
            <Text selectable style={styles.name}>
              The Awakening
            </Text>
            <Text
              adjustsFontSizeToFit
              minimumFontScale={0.82}
              numberOfLines={2}
              selectable
              style={styles.description}
            >
              Congratulations on signing up to mymind. Welcome to your beautiful private garden.
            </Text>
          </View>
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SHEET_BACKDROP_COLORS[0],
  },
  backdrop: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheet: {
    backgroundColor: COLLECTION_CARD_BACKDROP.background,
    borderCurve: 'continuous',
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    flex: 1,
    overflow: 'hidden',
  },
  topBar: {
    alignItems: 'center',
    flexDirection: 'row',
    marginBottom: spacing.xl,
  },
  backButtonHitArea: {
    borderRadius: radii.pill,
    height: 42,
    width: 42,
  },
  backButton: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.62)',
    borderColor: 'rgba(105, 115, 132, 0.14)',
    borderRadius: radii.pill,
    borderWidth: 1,
    height: 42,
    justifyContent: 'center',
    transform: [{ scale: 1 }],
    width: 42,
  },
  backButtonPressed: {
    backgroundColor: 'rgba(103, 113, 124, 0.24)',
    transform: [{ scale: 0.97 }],
  },
  backIcon: {
    height: 14,
    width: 14,
  },
  name: {
    color: '#18212b',
    fontFamily: 'Georgia',
    fontSize: 30,
    fontWeight: '400',
    letterSpacing: -1.2,
    lineHeight: 36,
    textAlign: 'center',
  },
  cardStage: {
    alignItems: 'center',
    alignSelf: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  details: {
    alignItems: 'center',
    alignSelf: 'center',
    gap: spacing.md,
    marginTop: 50,
    maxWidth: 430,
    width: '100%',
  },
  description: {
    color: '#68717d',
    fontSize: 15,
    lineHeight: 22,
    maxWidth: 360,
    textAlign: 'center',
  },
  pageMarker: {
    alignItems: 'center',
    alignSelf: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.84)',
    borderColor: 'rgba(255, 255, 255, 0.94)',
    borderRadius: 15,
    borderWidth: 1,
    boxShadow: '0 3px 11px rgba(91, 103, 119, 0.14)',
    height: 30,
    justifyContent: 'center',
    marginTop: 54,
    width: 30,
  },
  pageMarkerText: {
    color: '#84909d',
    fontFamily: 'Georgia',
    fontSize: typeScale.label,
  },
  sheetGrabber: {
    alignSelf: 'center',
    backgroundColor: 'rgba(103, 113, 124, 0.62)',
    borderRadius: radii.pill,
    height: 5,
    position: 'absolute',
    top: spacing.sm,
    width: 34,
    zIndex: 2,
  },
  missing: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  missingTitle: {
    color: colors.text,
    fontSize: typeScale.title,
    fontWeight: '800',
  },
  missingLink: {
    color: colors.accent,
    marginTop: spacing.md,
  },
});
