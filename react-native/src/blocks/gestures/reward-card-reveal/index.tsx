import { useEffect, useMemo, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import Animated, { FadeIn, FadeOut } from 'react-native-reanimated';

import {
  CardDetailScreen,
  CardGalleryScreen,
  CardNavigationContext,
  type CardNavigation,
} from './features/cards';
import { colors } from './shared/theme';

// The original stack pushed `card/[id]` over `index` with `animation: 'fade'` and
// `animationDuration: 240`. Here the detail screen fades in over the gallery the same way.
const SCREEN_FADE_DURATION = 240;

export default function RewardCardRevealDemo() {
  const [cardId, setCardId] = useState<string>();
  const isDetailOpen = cardId !== undefined;

  const navigation = useMemo<CardNavigation>(
    () => ({
      back: () => setCardId(undefined),
      cardId,
      openCard: setCardId,
    }),
    [cardId],
  );

  // Android's back button popped the detail route; without this it would leave the block.
  useEffect(() => {
    if (!isDetailOpen) {
      return;
    }

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      setCardId(undefined);
      return true;
    });

    return () => subscription.remove();
  }, [isDetailOpen]);

  return (
    <CardNavigationContext value={navigation}>
      <View style={styles.root}>
        <View
          accessibilityElementsHidden={isDetailOpen}
          importantForAccessibility={isDetailOpen ? 'no-hide-descendants' : 'auto'}
          style={styles.screen}
        >
          <CardGalleryScreen />
        </View>

        {isDetailOpen ? (
          <Animated.View
            entering={FadeIn.duration(SCREEN_FADE_DURATION)}
            exiting={FadeOut.duration(SCREEN_FADE_DURATION)}
            style={styles.overlay}
          >
            <CardDetailScreen />
          </Animated.View>
        ) : null}
      </View>
    </CardNavigationContext>
  );
}

const styles = StyleSheet.create({
  root: {
    backgroundColor: colors.background,
    flex: 1,
  },
  screen: {
    flex: 1,
  },
  overlay: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
});
