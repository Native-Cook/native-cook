import {
  Canvas,
  Path,
  Points,
  Skia,
  rect,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import {
  GlassView,
  isGlassEffectAPIAvailable,
  isLiquidGlassAvailable,
} from 'expo-glass-effect';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { memo, useEffect, useMemo, type ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import Animated, {
  cubicBezier,
  ReduceMotion,
  type CSSStyle,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const PANEL_MIN_HEIGHT = 224;
const PANEL_CONTENT_HEIGHT = 190;
const PRIMARY_ACTION_SIZE = 106;
const PRIMARY_ACTION_CENTER = PRIMARY_ACTION_SIZE / 2;
const CAPTURE_RING_RADIUS = 45;
const CONTROL_ROW_TOP = 82;
const CONTROL_ROW_MAX_WIDTH = 370;
const DOT_COLUMNS = 20;
const DOT_ROWS = 11;

const PRESS_SPRING = {
  dampingRatio: 1,
  duration: 400,
  reduceMotion: ReduceMotion.System,
};

const PRESS_IN_TIMING = {
  duration: 100,
  reduceMotion: ReduceMotion.System,
};

const ICON_TRANSITION_DURATION = '180ms';
const ICON_TRANSITION_EASING = cubicBezier(0.23, 1, 0.32, 1);
const STATE_ICON_TRANSITION_STYLE = {
  transitionDuration: ICON_TRANSITION_DURATION,
  transitionProperty: ['opacity', 'transform'],
  transitionTimingFunction: ICON_TRANSITION_EASING,
} satisfies CSSStyle<ViewStyle>;

const PANEL_SPRING = {
  dampingRatio: 0.82,
  duration: 520,
  reduceMotion: ReduceMotion.System,
};

const CAPTURE_RING_ARCS = [
  { color: '#F0F885', start: -112, sweep: 50 },
  { color: '#9FD8BC', start: -50, sweep: 58 },
  { color: '#B5ADE4', start: 20, sweep: 78 },
  { color: '#BBDCFC', start: 112, sweep: 52 },
  { color: '#FBC8EC', start: 176, sweep: 54 },
].map(({ color, start, sweep }) => {
  const oval = rect(
    PRIMARY_ACTION_CENTER - CAPTURE_RING_RADIUS,
    PRIMARY_ACTION_CENTER - CAPTURE_RING_RADIUS,
    CAPTURE_RING_RADIUS * 2,
    CAPTURE_RING_RADIUS * 2,
  );
  const path = Skia.PathBuilder.Make()
    .addArc(oval, start, sweep)
    .detach();

  return { color, path };
});

export type ReferenceCameraChromeMode = 'capture' | 'result';

export type ReferenceCameraChromeProps = {
  mode?: ReferenceCameraChromeMode;
  onCapture: () => void;
  instruction?: string;
  captureAccessibilityHint?: string;
  captureAccessibilityLabel?: string;
  captureBusy?: boolean;
  captureDisabled?: boolean;
  onOpenLibrary?: () => void;
  libraryDisabled?: boolean;
  onCrop?: () => void;
  cropDisabled?: boolean;
  onConfirm?: () => void;
  confirmDisabled?: boolean;
  confirmAccessibilityHint?: string;
  confirmAccessibilityLabel?: string;
  onCancel?: () => void;
  cancelDisabled?: boolean;
  cancelAccessibilityHint?: string;
  cancelAccessibilityLabel?: string;
  bottomInset?: number;
  style?: StyleProp<ViewStyle>;
};

type PressScaleProps = {
  accessibilityHint?: string;
  accessibilityLabel: string;
  busy?: boolean;
  children: ReactNode;
  disabled?: boolean;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
};

const PressScale = memo(function PressScale({
  accessibilityHint,
  accessibilityLabel,
  busy = false,
  children,
  disabled = false,
  onPress,
  style,
}: PressScaleProps) {
  const scale = useSharedValue(1);
  const hasHandler = onPress != null;
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.get() }],
  }));

  return (
    <Animated.View
      style={[styles.pressScale, disabled && styles.disabled, animatedStyle, style]}
    >
      <Pressable
        accessibilityHint={hasHandler ? accessibilityHint : undefined}
        accessibilityLabel={accessibilityLabel}
        accessibilityRole={hasHandler ? 'button' : undefined}
        accessibilityState={hasHandler ? { busy, disabled } : undefined}
        accessible={hasHandler}
        disabled={!hasHandler || disabled}
        hitSlop={12}
        onPress={onPress}
        onPressIn={() => scale.set(withTiming(0.92, PRESS_IN_TIMING))}
        onPressOut={() => scale.set(withSpring(1, PRESS_SPRING))}
        pressRetentionOffset={20}
        style={styles.pressableFill}
      >
        {children}
      </Pressable>
    </Animated.View>
  );
});

const StateIconTransition = memo(function StateIconTransition({
  active,
  children,
  reducedMotion,
  style,
}: {
  active: boolean;
  children: ReactNode;
  reducedMotion: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Animated.View
      accessibilityElementsHidden={!active}
      importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}
      pointerEvents={active ? 'box-none' : 'none'}
      style={[
        STATE_ICON_TRANSITION_STYLE,
        style,
        active
          ? styles.stateIconVisible
          : reducedMotion
            ? styles.stateIconHiddenReducedMotion
            : styles.stateIconHidden,
      ]}
    >
      {children}
    </Animated.View>
  );
});

const DotTexture = memo(function DotTexture({ height }: { height: number }) {
  const { width } = useWindowDimensions();
  const points = useMemo(
    () =>
      Array.from({ length: DOT_ROWS * DOT_COLUMNS }, (_, index) => ({
        x: ((index % DOT_COLUMNS) + 0.5) * (width / DOT_COLUMNS),
        y:
          (Math.floor(index / DOT_COLUMNS) + 0.5) *
          (height / DOT_ROWS),
      })),
    [height, width],
  );

  return (
    <Canvas
      aria-hidden
      pointerEvents="none"
      style={styles.dotTexture}
    >
      <Points
        color="rgba(105, 94, 105, 0.1)"
        mode="points"
        points={points}
        strokeCap="round"
        strokeWidth={2}
      />
    </Canvas>
  );
});

const MulticolorCaptureButton = memo(function MulticolorCaptureButton({
  accessibilityHint,
  accessibilityLabel,
  busy,
  disabled,
  onPress,
}: {
  accessibilityHint: string;
  accessibilityLabel: string;
  busy: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  return (
    <PressScale
      accessibilityHint={accessibilityHint}
      accessibilityLabel={accessibilityLabel}
      busy={busy}
      disabled={disabled || busy}
      onPress={onPress}
      style={styles.primaryAction}
    >
      <View style={styles.captureDisc}>
        <View
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          pointerEvents="none"
          style={StyleSheet.absoluteFill}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            {CAPTURE_RING_ARCS.map((arc) => (
              <Path
                color={arc.color}
                key={arc.color}
                path={arc.path}
                strokeCap="round"
                strokeWidth={5}
                style="stroke"
              />
            ))}
          </Canvas>
        </View>

        {busy ? <ActivityIndicator color="#817C82" size="small" /> : null}
      </View>
    </PressScale>
  );
});

const SymbolActionButton = memo(function SymbolActionButton({
  accessibilityHint,
  accessibilityLabel,
  disabled = false,
  onPress,
  primary = false,
  symbol,
  symbolColor = '#FFFDFF',
  symbolSize = 24,
  symbolStyle,
  symbolWeight = 'semibold',
}: {
  accessibilityHint?: string;
  accessibilityLabel: string;
  disabled?: boolean;
  onPress?: () => void;
  primary?: boolean;
  symbol: SymbolViewProps['name'];
  symbolColor?: string;
  symbolSize?: number;
  symbolStyle?: StyleProp<ViewStyle>;
  symbolWeight?: 'regular' | 'medium' | 'semibold' | 'bold';
}) {
  return (
    <PressScale
      accessibilityHint={accessibilityHint}
      accessibilityLabel={accessibilityLabel}
      disabled={disabled}
      onPress={onPress}
      style={primary ? styles.primaryAction : styles.secondaryAction}
    >
      <View style={primary ? styles.confirmDisc : styles.secondaryDisc}>
        <SymbolView
          name={symbol}
          resizeMode="scaleAspectFit"
          size={symbolSize}
          style={symbolStyle}
          tintColor={symbolColor}
          weight={symbolWeight}
        />
      </View>
    </PressScale>
  );
});

function canRenderLiquidGlass() {
  return (
    process.env.EXPO_OS === 'ios' &&
    isGlassEffectAPIAvailable() &&
    isLiquidGlassAvailable()
  );
}

export const ReferenceCameraChrome = memo(function ReferenceCameraChrome({
  mode = 'capture',
  onCapture,
  instruction = 'Center your subject in the frame',
  captureAccessibilityHint = 'Take a photo and start the dispersion effect',
  captureAccessibilityLabel,
  captureBusy = false,
  captureDisabled = false,
  onOpenLibrary,
  libraryDisabled = false,
  onCrop,
  cropDisabled = false,
  onConfirm,
  confirmDisabled = false,
  confirmAccessibilityHint = 'Confirm the current result',
  confirmAccessibilityLabel = 'Confirm photo',
  onCancel,
  cancelDisabled = false,
  cancelAccessibilityHint = 'Discard the result and return to the camera',
  cancelAccessibilityLabel = 'Discard photo',
  bottomInset,
  style,
}: ReferenceCameraChromeProps) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const resolvedBottomInset = Math.max(bottomInset ?? insets.bottom, 12);
  const panelHeight = Math.max(
    PANEL_MIN_HEIGHT,
    PANEL_CONTENT_HEIGHT + resolvedBottomInset,
  );
  const panelOffset = useSharedValue(panelHeight);
  const panelAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: panelOffset.get() }],
  }));
  const resolvedCaptureAccessibilityLabel =
    captureAccessibilityLabel ?? (captureBusy ? 'Taking photo' : 'Take photo');

  useEffect(() => {
    panelOffset.set(withSpring(0, PANEL_SPRING));
  }, [panelOffset]);

  const content = (
    <>
      <View pointerEvents="none" style={styles.panelBase} />
      <DotTexture height={panelHeight} />

      {mode === 'capture' ? (
        <View style={styles.instructionSlot}>
          <Text
            accessibilityRole="text"
            adjustsFontSizeToFit
            maxFontSizeMultiplier={1.25}
            minimumFontScale={0.82}
            numberOfLines={2}
            style={styles.instruction}
          >
            {instruction}
          </Text>
        </View>
      ) : null}

      <StateIconTransition
        active={mode === 'result'}
        reducedMotion={reducedMotion}
        style={styles.chevron}
      >
        <View aria-hidden accessibilityElementsHidden style={styles.chevronGlyph}>
          <View style={[styles.chevronStroke, styles.chevronStrokeLeft]} />
          <View style={[styles.chevronStroke, styles.chevronStrokeRight]} />
        </View>
      </StateIconTransition>

      <View style={styles.controlRow}>
        <View style={styles.actionSlot}>
          <StateIconTransition
            active={mode === 'result'}
            reducedMotion={reducedMotion}
            style={styles.actionTransition}
          >
            <SymbolActionButton
              accessibilityHint="Adjust the photo crop"
              accessibilityLabel="Recrop photo"
              disabled={cropDisabled}
              onPress={onCrop}
              symbol={{ ios: 'crop', android: 'crop' }}
              symbolSize={28}
            />
          </StateIconTransition>
        </View>

        <View style={styles.actionSlot}>
          <StateIconTransition
            active={mode === 'capture'}
            reducedMotion={reducedMotion}
            style={styles.actionTransition}
          >
            <MulticolorCaptureButton
              accessibilityHint={captureAccessibilityHint}
              accessibilityLabel={resolvedCaptureAccessibilityLabel}
              busy={captureBusy}
              disabled={captureDisabled}
              onPress={onCapture}
            />
          </StateIconTransition>

          <StateIconTransition
            active={mode === 'result'}
            reducedMotion={reducedMotion}
            style={styles.actionTransition}
          >
            <SymbolActionButton
              accessibilityHint={confirmAccessibilityHint}
              accessibilityLabel={confirmAccessibilityLabel}
              disabled={confirmDisabled}
              onPress={onConfirm}
              primary
              symbol={{ ios: 'checkmark', android: 'check' }}
              symbolColor="#B8B1EB"
              symbolSize={34}
              symbolStyle={styles.confirmSymbol}
              symbolWeight="bold"
            />
          </StateIconTransition>
        </View>

        <View style={styles.actionSlot}>
          <StateIconTransition
            active={mode === 'capture'}
            reducedMotion={reducedMotion}
            style={styles.actionTransition}
          >
            <SymbolActionButton
              accessibilityHint="Open the photo library on your device"
              accessibilityLabel="Choose photo from library"
              disabled={libraryDisabled}
              onPress={onOpenLibrary}
              symbol={{ ios: 'photo', android: 'photo' }}
              symbolSize={32}
            />
          </StateIconTransition>

          <StateIconTransition
            active={mode === 'result'}
            reducedMotion={reducedMotion}
            style={styles.actionTransition}
          >
            <SymbolActionButton
              accessibilityHint={cancelAccessibilityHint}
              accessibilityLabel={cancelAccessibilityLabel}
              disabled={cancelDisabled}
              onPress={onCancel}
              symbol={{ ios: 'xmark', android: 'close' }}
              symbolSize={19}
              symbolWeight="bold"
            />
          </StateIconTransition>
        </View>
      </View>
    </>
  );

  const panelStyle = [styles.panel, { height: panelHeight }];

  return (
    <Animated.View
      pointerEvents="box-none"
      style={[styles.shell, panelAnimatedStyle, style]}
    >
      {canRenderLiquidGlass() ? (
        <GlassView
          colorScheme="light"
          glassEffectStyle="regular"
          style={panelStyle}
          tintColor="rgba(248, 246, 249, 0.96)"
        >
          {content}
        </GlassView>
      ) : (
        <BlurView
          intensity={86}
          style={[panelStyle, styles.materialFallback]}
          tint="systemMaterialLight"
        >
          <View pointerEvents="none" style={styles.materialHighlight} />
          {content}
        </BlurView>
      )}
    </Animated.View>
  );
});

const styles = StyleSheet.create({
  actionTransition: {
    alignItems: 'center',
    bottom: 0,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  actionSlot: {
    alignItems: 'center',
    height: PRIMARY_ACTION_SIZE,
    justifyContent: 'center',
    width: PRIMARY_ACTION_SIZE,
  },
  captureDisc: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 253, 255, 0.94)',
    borderColor: 'rgba(255, 255, 255, 0.84)',
    borderCurve: 'continuous',
    borderRadius: PRIMARY_ACTION_SIZE / 2,
    borderWidth: StyleSheet.hairlineWidth,
    boxShadow: '0 3px 13px rgba(72, 58, 63, 0.1)',
    height: PRIMARY_ACTION_SIZE,
    justifyContent: 'center',
    width: PRIMARY_ACTION_SIZE,
  },
  chevron: {
    alignSelf: 'center',
    height: 9,
    position: 'absolute',
    top: 15,
    width: 23,
  },
  chevronGlyph: {
    height: 9,
    width: 23,
  },
  chevronStroke: {
    backgroundColor: 'rgba(127, 120, 126, 0.2)',
    borderRadius: 2,
    height: 3,
    position: 'absolute',
    top: 3,
    width: 13,
  },
  chevronStrokeLeft: {
    left: 0,
    transform: [{ rotate: '-25deg' }],
  },
  chevronStrokeRight: {
    right: 0,
    transform: [{ rotate: '25deg' }],
  },
  confirmDisc: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 253, 255, 0.96)',
    borderColor: 'rgba(255, 255, 255, 0.88)',
    borderCurve: 'continuous',
    borderRadius: PRIMARY_ACTION_SIZE / 2,
    borderWidth: StyleSheet.hairlineWidth,
    boxShadow: '0 3px 13px rgba(72, 58, 63, 0.1)',
    height: PRIMARY_ACTION_SIZE,
    justifyContent: 'center',
    width: PRIMARY_ACTION_SIZE,
  },
  confirmSymbol: {
    transform: [{ scaleX: 1.07 }, { scaleY: 0.82 }],
  },
  controlRow: {
    alignItems: 'center',
    alignSelf: 'center',
    flexDirection: 'row',
    height: PRIMARY_ACTION_SIZE,
    justifyContent: 'space-between',
    maxWidth: CONTROL_ROW_MAX_WIDTH,
    position: 'absolute',
    top: CONTROL_ROW_TOP,
    width: '100%',
  },
  disabled: {
    opacity: 0.46,
  },
  dotTexture: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  instruction: {
    color: '#898789',
    fontSize: 17,
    fontWeight: '400',
    letterSpacing: 0,
    lineHeight: 21,
    paddingHorizontal: 28,
    textAlign: 'center',
    width: '100%',
  },
  instructionSlot: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    left: 24,
    position: 'absolute',
    right: 24,
    top: 25,
  },
  materialFallback: {
    backgroundColor: 'rgba(246, 244, 247, 0.91)',
    borderColor: 'rgba(255, 255, 255, 0.72)',
    borderWidth: StyleSheet.hairlineWidth,
  },
  materialHighlight: {
    backgroundColor: 'rgba(255, 255, 255, 0.18)',
    height: '48%',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  panel: {
    borderCurve: 'continuous',
    borderTopLeftRadius: 52,
    borderTopRightRadius: 52,
    boxShadow: '0 -3px 24px rgba(45, 32, 38, 0.08)',
    overflow: 'hidden',
    width: '100%',
  },
  panelBase: {
    backgroundColor: 'rgba(246, 244, 247, 0.98)',
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  pressScale: {
    borderCurve: 'continuous',
  },
  pressableFill: {
    alignItems: 'center',
    height: '100%',
    justifyContent: 'center',
    width: '100%',
  },
  primaryAction: {
    borderRadius: PRIMARY_ACTION_SIZE / 2,
    height: PRIMARY_ACTION_SIZE,
    width: PRIMARY_ACTION_SIZE,
  },
  secondaryAction: {
    borderRadius: 27,
    height: 54,
    width: 54,
  },
  secondaryDisc: {
    alignItems: 'center',
    backgroundColor: '#DEDCDE',
    borderColor: 'rgba(255, 255, 255, 0.36)',
    borderCurve: 'continuous',
    borderRadius: 27,
    borderWidth: StyleSheet.hairlineWidth,
    boxShadow: '0 3px 10px rgba(65, 52, 60, 0.08)',
    height: 54,
    justifyContent: 'center',
    width: 54,
  },
  shell: {
    width: '100%',
  },
  stateIconHidden: {
    opacity: 0,
    transform: [{ scale: 0.92 }],
  },
  stateIconHiddenReducedMotion: {
    opacity: 0,
    transform: [{ scale: 1 }],
  },
  stateIconVisible: {
    opacity: 1,
    transform: [{ scale: 1 }],
  },
});

export default ReferenceCameraChrome;
