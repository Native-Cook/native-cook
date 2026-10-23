import { useState } from 'react';
import { Text, View } from 'react-native';
import {
  BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, Shader, Skia,
  vec
} from '@shopify/react-native-skia';
import { Pressable } from 'react-native-gesture-handler';
import Animated, {
  Easing, ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue,
  useSharedValue, withSpring, withTiming
} from 'react-native-reanimated';
import { holoEffect, holoUniforms } from './holo-shader';
import { clamp, D, M, mod, T, tone, wrap } from './holo-theme';
import type { HoloValues } from './use-holo-pager';
const ease = Easing.bezier(...T.uiCurve);

export function AgendaButton({ k, top, values, reduced, selected, onPress, onTouchStart,
  onTouchEnd, onReady }: {
    k: number;
    top: number;
    values: HoloValues;
    reduced: boolean;
    selected: boolean;
    onPress: () => void;
    onTouchStart: () => void;
    onTouchEnd: () => void;
    onReady: (part: string) => void;
  }) {
  const { pos, added, demoAdded, buttonScale } = values;
  const morph = useSharedValue(0);
  const labelMix = useSharedValue(0);
  const pressed = useSharedValue(1);

  useAnimatedReaction(() => {
    const index = mod(Math.round(pos.get()));
    return (added.get() & (1 << index)) !== 0 || (index === 2 && demoAdded.get());
  }, (next, prev) => {
    if (next !== prev) {
      labelMix.set(withTiming(next ? 1 : 0, {
        duration: reduced ? T.fade : T.fade * 2,
        easing: Easing.linear, reduceMotion: ReduceMotion.Never
      }));
      morph.set(withTiming(next ? 1 : 0, {
        duration: reduced ? T.fade : T.morph,
        easing: ease, reduceMotion: ReduceMotion.Never
      }));
    }
  });

  const style = useAnimatedStyle(() => ({ transform: [{ scale: pressed.get() * buttonScale.get() }] }));

  const idleLabel = useAnimatedStyle(() => ({
    opacity: clamp(1 - labelMix.get() * 2),
    transform: [{ translateY: -4 * k * labelMix.get() }]
  }));

  const addedLabel = useAnimatedStyle(() => ({
    opacity: clamp(labelMix.get() * 2 - 1),
    transform: [{ translateY: 4 * k * (1 - labelMix.get()) }]
  }));

  const uniforms = useDerivedValue(() => {
    const index = mod(Math.round(pos.get()));
    return holoUniforms(index, [D.buttonWidth, D.buttonHeight], D.buttonRadius, 0, wrap(index
      - pos.get()));
  });

  const iconUniforms = useDerivedValue(() => {
    const index = mod(Math.round(pos.get()));
    return holoUniforms(index, [D.icon, D.icon], 0, 0, wrap(index - pos.get()));
  });

  const iconAlpha = useDerivedValue(() => reduced ? Math.abs(morph.get() * 2 - 1) : 1);

  const path = useDerivedValue(() => {
    const p = reduced ? (morph.get() >= 0.5 ? 1 : 0) : morph.get();
    // Each plus stroke becomes one leg of a check; all four endpoints interpolate.
    return Skia.PathBuilder.Make().moveTo(3, 10).lineTo(17 - 9 * p, 10 + 5 * p)
      .moveTo(10 - 2 * p, 3 + 12 * p).lineTo(10 + 7 * p, 17 - 12 * p).build();
  });
  const [labelWidths, setLabelWidths] = useState([0, 0]);
  const labelWidth = Math.max(...labelWidths);
  const labels = ['Add to agenda', 'In your agenda'];
  const labelStyle = {
    fontSize: D.type.button * k, lineHeight: 22 * k,
    fontWeight: '600' as const, color: tone.ink
  };
  const pad = 12;
  return <Animated.View style={[{
    position: 'absolute', left: D.inset * k, top,
    width: D.buttonWidth * k, height: D.buttonHeight * k
  }, style]}>
    <Pressable onLayout={() => onReady('rim')} onPress={onPress} onPressIn={() => {
      onTouchStart();
      pressed.set(withTiming(M.press, {
        duration: T.press, easing: ease,
        reduceMotion: ReduceMotion.Never
      }));
    }} onPressOut={() => {
      onTouchEnd();
      pressed.set(reduced
        ? withTiming(1, { duration: T.press, reduceMotion: ReduceMotion.Never }) : withSpring(1,
          M.spring));
    }} accessibilityRole="button" accessibilityState={{ selected }}
      accessibilityLabel={selected ? 'In your agenda' : 'Add to agenda'} style={{
        width: D.buttonWidth * k, height: D.buttonHeight * k,
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center'
      }}>
      <Canvas pointerEvents="none" accessible={false} accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants" style={{
          position: 'absolute', left: -pad * k,
          top: -pad * k, width: (D.buttonWidth + pad * 2) * k, height: (D.buttonHeight + pad * 2) * k
        }}>
        <Group transform={[{ scale: k }, { translateX: pad }, { translateY: pad }]}>
          <RoundedRect x={0} y={0} width={D.buttonWidth} height={D.buttonHeight}
            r={D.buttonRadius} style="stroke" strokeWidth={1.5} opacity={morph}>
            <Shader source={holoEffect!} uniforms={uniforms} /><BlurMask blur={5} style="normal" />
          </RoundedRect>
          <RoundedRect x={0} y={0} width={D.buttonWidth} height={D.buttonHeight}
            r={D.buttonRadius} color={tone.glass} />
          <RoundedRect x={0.5} y={0.5} width={D.buttonWidth - 1} height={D.buttonHeight - 1}
            r={D.buttonRadius - 0.5} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, 0)} end={vec(0, D.buttonHeight)} colors={['#FFFFFF24',
              '#FFFFFF0A']} />
          </RoundedRect>
          <RoundedRect x={0.75} y={0.75} width={D.buttonWidth - 1.5} height={D.buttonHeight
            - 1.5} r={D.buttonRadius - 0.75} style="stroke" strokeWidth={1.5} opacity={morph}><Shader
              source={holoEffect!} uniforms={uniforms} /></RoundedRect>
        </Group>
      </Canvas>
      <View pointerEvents="none" accessible={false} accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants" style={{
          flexDirection: 'row', alignItems: 'center', gap: D.buttonGap * k,
          transform: [{ translateX: D.buttonOpticalX * k }], opacity: labelWidth > 0 ? 1 : 0
        }}>
        <View onLayout={() => onReady('icon')} style={{ width: D.icon * k, height: D.icon * k }}>
          <Canvas style={{ width: D.icon * k, height: D.icon * k }}>
            <Group transform={[{ scale: k }]} opacity={iconAlpha}>
              <Path path={path} style="stroke" strokeWidth={D.iconStroke} strokeCap="round"
                strokeJoin="round">
                <Shader source={holoEffect!} uniforms={iconUniforms} />
              </Path>
            </Group>
          </Canvas>
        </View>
        <View style={{ width: labelWidth, height: 22 * k }}>
          <Animated.Text numberOfLines={1} style={[labelStyle, {
            position: 'absolute', left: 0,
            top: 0
          }, idleLabel]}>
            {labels[0]}
          </Animated.Text>
          <Animated.Text numberOfLines={1} style={[labelStyle, {
            position: 'absolute', left: 0,
            top: 0
          }, addedLabel]}>
            {labels[1]}
          </Animated.Text>
        </View>
      </View>
      {/* Intrinsic widths are measured once per font scale, outside the constrained label slot. */}
      <View pointerEvents="none" accessible={false} accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants" style={{
          position: 'absolute', opacity: 0,
          alignItems: 'flex-start'
        }}>
        {labels.map((label, index) => <Text key={label} numberOfLines={1} style={labelStyle}
          onLayout={(event) => {
            const width = event.nativeEvent.layout.width;
            setLabelWidths((previous) => {
              if (previous[index] === width)
                return previous;
              const next = [...previous];
              next[index] = width;
              return next;
            });
          }}>{label}</Text>)}
      </View>
    </Pressable>
  </Animated.View>;
}
