import * as Haptics from 'expo-haptics';
import { memo, type ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

const PRESS_SPRING = {
  damping: 18,
  stiffness: 420,
  mass: 0.42,
};

function runHaptic(kind: 'selection' | 'soft' | 'rigid') {
  const feedback =
    kind === 'selection'
      ? Haptics.selectionAsync()
      : Haptics.impactAsync(
          kind === 'rigid'
            ? Haptics.ImpactFeedbackStyle.Rigid
            : Haptics.ImpactFeedbackStyle.Soft,
        );

  void feedback.catch(() => {
    // Haptics are optional feedback and can be unavailable in Low Power Mode.
  });
}

type CameraControlButtonProps = {
  accessibilityLabel: string;
  icon: string;
  onPress: () => void;
  disabled?: boolean;
  haptic?: 'selection' | 'soft' | 'rigid';
  style?: StyleProp<ViewStyle>;
};

export const CameraControlButton = memo(function CameraControlButton({
  accessibilityLabel,
  icon,
  onPress,
  disabled = false,
  haptic = 'selection',
  style,
}: CameraControlButtonProps) {
  const scale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.get() }],
  }));

  const handlePress = () => {
    runHaptic(haptic);
    onPress();
  };

  return (
    <Animated.View
      style={[styles.roundButtonShadow, disabled && styles.disabled, animatedStyle, style]}
    >
      <Pressable
        accessibilityLabel={accessibilityLabel}
        accessibilityRole="button"
        accessibilityState={{ disabled }}
        disabled={disabled}
        hitSlop={12}
        onPress={handlePress}
        onPressIn={() => scale.set(withSpring(0.9, PRESS_SPRING))}
        onPressOut={() => scale.set(withSpring(1, PRESS_SPRING))}
        style={styles.roundButton}
      >
        <Text accessibilityElementsHidden style={styles.roundButtonIcon}>
          {icon}
        </Text>
      </Pressable>
    </Animated.View>
  );
});

type CaptureButtonProps = {
  onPress: () => void;
  disabled?: boolean;
  busy?: boolean;
};

export const CaptureButton = memo(function CaptureButton({
  onPress,
  disabled = false,
  busy = false,
}: CaptureButtonProps) {
  const scale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.get() }],
  }));

  return (
    <Animated.View style={[styles.captureOuter, disabled && styles.disabled, animatedStyle]}>
      <Pressable
        accessibilityLabel={busy ? 'Taking photo' : 'Take photo'}
        accessibilityRole="button"
        accessibilityState={{ busy, disabled }}
        disabled={disabled}
        hitSlop={14}
        onPress={onPress}
        onPressIn={() => scale.set(withSpring(0.9, PRESS_SPRING))}
        onPressOut={() => scale.set(withSpring(1, PRESS_SPRING))}
        style={styles.capturePressable}
      >
        <View style={styles.captureInner}>
          {busy ? <ActivityIndicator color="#101010" size="small" /> : null}
        </View>
      </Pressable>
    </Animated.View>
  );
});

type CameraActionButtonProps = {
  accessibilityLabel: string;
  children: ReactNode;
  onPress: () => void;
  primary?: boolean;
  disabled?: boolean;
};

export const CameraActionButton = memo(function CameraActionButton({
  accessibilityLabel,
  children,
  onPress,
  primary = false,
  disabled = false,
}: CameraActionButtonProps) {
  const scale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.get() }],
  }));

  const handlePress = () => {
    runHaptic(primary ? 'rigid' : 'selection');
    onPress();
  };

  return (
    <Animated.View style={[disabled && styles.disabled, animatedStyle]}>
      <Pressable
        accessibilityLabel={accessibilityLabel}
        accessibilityRole="button"
        accessibilityState={{ disabled }}
        disabled={disabled}
        onPress={handlePress}
        onPressIn={() => scale.set(withSpring(0.95, PRESS_SPRING))}
        onPressOut={() => scale.set(withSpring(1, PRESS_SPRING))}
        style={[styles.actionButton, primary && styles.primaryActionButton]}
      >
        <Text style={[styles.actionLabel, primary && styles.primaryActionLabel]}>
          {children}
        </Text>
      </Pressable>
    </Animated.View>
  );
});

type CameraStatusPillProps = {
  label: string;
  busy?: boolean;
  tone?: 'neutral' | 'success' | 'error';
};

export const CameraStatusPill = memo(function CameraStatusPill({
  label,
  busy = false,
  tone = 'neutral',
}: CameraStatusPillProps) {
  return (
    <View
      accessibilityLiveRegion="polite"
      accessibilityRole="text"
      style={styles.statusPill}
    >
      {busy ? (
        <ActivityIndicator color="#FFFFFF" size="small" />
      ) : (
        <View
          style={[
            styles.statusDot,
            tone === 'success' && styles.successDot,
            tone === 'error' && styles.errorDot,
          ]}
        />
      )}
      <Text style={styles.statusLabel}>{label}</Text>
    </View>
  );
});

const styles = StyleSheet.create({
  actionButton: {
    alignItems: 'center',
    backgroundColor: 'rgba(16, 16, 16, 0.72)',
    borderColor: 'rgba(255, 255, 255, 0.24)',
    borderRadius: 24,
    borderWidth: StyleSheet.hairlineWidth,
    justifyContent: 'center',
    minHeight: 48,
    minWidth: 112,
    paddingHorizontal: 20,
  },
  actionLabel: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 0.1,
  },
  captureInner: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 29,
    height: 58,
    justifyContent: 'center',
    width: 58,
  },
  captureOuter: {
    alignItems: 'center',
    borderColor: '#FFFFFF',
    borderRadius: 39,
    borderWidth: 3,
    height: 78,
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.28,
    shadowRadius: 10,
    width: 78,
  },
  capturePressable: {
    alignItems: 'center',
    height: '100%',
    justifyContent: 'center',
    width: '100%',
  },
  disabled: {
    opacity: 0.44,
  },
  errorDot: {
    backgroundColor: '#FF625C',
  },
  primaryActionButton: {
    backgroundColor: '#FFFFFF',
    borderColor: '#FFFFFF',
  },
  primaryActionLabel: {
    color: '#111111',
  },
  roundButton: {
    alignItems: 'center',
    backgroundColor: 'rgba(16, 16, 16, 0.68)',
    borderColor: 'rgba(255, 255, 255, 0.22)',
    borderRadius: 24,
    borderWidth: StyleSheet.hairlineWidth,
    height: 48,
    justifyContent: 'center',
    width: 48,
  },
  roundButtonIcon: {
    color: '#FFFFFF',
    fontSize: 23,
    fontWeight: '600',
    lineHeight: 25,
    textAlign: 'center',
  },
  roundButtonShadow: {
    borderRadius: 24,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.24,
    shadowRadius: 8,
  },
  statusDot: {
    backgroundColor: '#FFFFFF',
    borderRadius: 4,
    height: 8,
    width: 8,
  },
  statusLabel: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.15,
  },
  statusPill: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(16, 16, 16, 0.68)',
    borderColor: 'rgba(255, 255, 255, 0.18)',
    borderRadius: 18,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 8,
    minHeight: 36,
    paddingHorizontal: 13,
  },
  successDot: {
    backgroundColor: '#62E59D',
  },
});
