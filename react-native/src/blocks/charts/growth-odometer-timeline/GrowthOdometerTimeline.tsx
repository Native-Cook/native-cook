import { useCallback, useMemo, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import { GrowthCanvas } from './GrowthCanvas';
import { GrowthWheelLabels } from './GrowthWheelLabels';
import { COLORS, FULL_MONTHS, metrics, VALUES, type Metrics } from './growth-theme';
import { useGrowthMotion } from './use-growth-motion';
const IDENTITY_OFFSET_PT = 66;
const CAPTION_OFFSET_PT = 34;
const WHEEL_HIT_INSET_PT = 4;
const KNOB_HIT_HALF_PT = 28;
const KNOB_HIT_SIZE_PT = 56;
const IDENTITY_HIT_HEIGHT_PT = 120;
const AVATAR_SIZE_PT = 28;
const NAME_GAP_PT = 8;
const NAME_FONT_PT = 15;
const BADGE_SIZE_PT = 14;
const BADGE_GAP_PT = 4;
const CAPTION_FONT_PT = 13;
const CAPTION_LINE_HEIGHT_PT = 18;

function Instrument({
  m: layout,
  autoPlay
}: {
  m: Metrics;
  autoPlay: boolean;
}) {
  const [canvasReady, setCanvasReady] = useState(false);
  const [avatarReady, setAvatarReady] = useState(false);
  const [badgeReady, setBadgeReady] = useState(false);
  const {
    state,
    presented,
    settledMonth,
    pan,
    tap,
    adjust,
    next
  } = useGrowthMotion(autoPlay, canvasReady && avatarReady && badgeReady);
  const fade = useAnimatedStyle(() => ({
    opacity: presented.get()
  }));
  const followerCount = VALUES[settledMonth].toLocaleString('en-US');
  const description = `${FULL_MONTHS[settledMonth]}, ${followerCount} followers`;
  const ready = useCallback((loaded: boolean) => setCanvasReady(loaded), []);
  return <GestureDetector gesture={pan}>
    <View style={{
      width: layout.width,
      height: layout.height
    }} collapsable={false}>
      <Animated.View
        pointerEvents="none"
        style={[StyleSheet.absoluteFill, fade]}
        accessible={false}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        <GrowthCanvas m={layout} state={state} onReady={ready} />
        <GrowthWheelLabels m={layout} state={state} />
        <View
          style={[styles.identity, {
            left: layout.x0,
            top: layout.cy - IDENTITY_OFFSET_PT
          }]}
        >
          <Image
            source={require('./assets/avatar.png')}
            style={styles.avatar}
            onLoad={() => setAvatarReady(true)}
          />
          <Text allowFontScaling={false} style={styles.name}>Nova Lin</Text>
          <Image
            source={require('./assets/badge.png')}
            style={styles.badge}
            onLoad={() => setBadgeReady(true)}
          />
        </View>
        <Text
          allowFontScaling={false}
          style={[styles.caption, {
            left: layout.x0,
            top: layout.cy + CAPTION_OFFSET_PT
          }]}
        >Followers</Text>
      </Animated.View>
      <View
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          width: layout.railX + WHEEL_HIT_INSET_PT,
          height: layout.height
        }}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Month"
        accessibilityValue={{
          text: description
        }}
        accessibilityActions={[{
          name: 'increment',
          label: 'Next month'
        }, {
          name: 'decrement',
          label: 'Previous month'
        }]}
        onAccessibilityAction={event => adjust(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
      />
      <GestureDetector gesture={tap}>
        <View
          collapsable={false}
          style={{
            position: 'absolute',
            left: layout.knobX - KNOB_HIT_HALF_PT,
            top: layout.cy - KNOB_HIT_HALF_PT,
            width: KNOB_HIT_SIZE_PT,
            height: KNOB_HIT_SIZE_PT
          }}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Next month"
          onAccessibilityTap={next}
        />
      </GestureDetector>
      <View
        pointerEvents="none"
        style={{
          position: 'absolute',
          left: layout.x0,
          top: layout.cy - IDENTITY_OFFSET_PT,
          width: layout.width - layout.x0,
          height: IDENTITY_HIT_HEIGHT_PT
        }}
        accessible
        accessibilityRole="text"
        accessibilityLabel={`Nova Lin. ${description}`}
      />
    </View>
  </GestureDetector>;
}

export function GrowthOdometerTimeline({
  autoPlay = false
}: {
  autoPlay?: boolean;
}) {
  const [size, setSize] = useState({
    width: 0,
    height: 0
  });
  const layout = useMemo(() => metrics(size.width, size.height), [size]);
  return <GestureHandlerRootView style={styles.root}>
    <View
      style={styles.root}
      onLayout={event => {
        const {
          width,
          height
        } = event.nativeEvent.layout;
        setSize(previous => previous.width === width && previous.height === height ? previous : {
          width,
          height
        });
      }}
    >
      {size.width > 0 && size.height > 0 && <Instrument m={layout} autoPlay={autoPlay} />}
    </View>
  </GestureHandlerRootView>;
}
const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: COLORS.page,
    overflow: 'hidden'
  },
  identity: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    height: AVATAR_SIZE_PT
  },
  avatar: {
    width: AVATAR_SIZE_PT,
    height: AVATAR_SIZE_PT
  },
  name: {
    marginLeft: NAME_GAP_PT,
    color: '#FFFFFF',
    fontSize: NAME_FONT_PT,
    fontWeight: '600'
  },
  badge: {
    width: BADGE_SIZE_PT,
    height: BADGE_SIZE_PT,
    marginLeft: BADGE_GAP_PT
  },
  caption: {
    position: 'absolute',
    color: COLORS.caption,
    fontSize: CAPTION_FONT_PT,
    fontWeight: '400',
    lineHeight: CAPTION_LINE_HEIGHT_PT
  }
});
