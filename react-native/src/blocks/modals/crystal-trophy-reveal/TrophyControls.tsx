// Smoked-crystal controls drawn on the same canvas as the trophy; transparent semantic targets above.
import { Glyphs, Group, Path, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { View } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { CONTROLS_SKSL } from './controls-shader';
import { COPY, TIMING, type TrophyFrame } from './trophy-contract';
import { type TrophyFonts } from './trophy-fonts';
import { uiProgress, type TrophyState } from './trophy-timeline';
import { capBaseline, centredGlyphs } from './TrophyUi';

const glass = Skia.RuntimeEffect.Make(CONTROLS_SKSL);
if (!glass) throw new Error('Crystal Trophy Reveal: controls shader did not compile.');
const checkPath = Skia.PathBuilder.Make().moveTo(0, 0).lineTo(4, 4).lineTo(12, -5).build();

export function TrophyControls({ frame, fonts, state }: { frame: TrophyFrame; fonts: TrophyFonts; state: SharedValue<TrophyState> }) {
  const { share, close } = frame;
  const cx = close.x + close.size / 2; const cy = close.y + close.size / 2;
  const shareGlyphs = useMemo(() => centredGlyphs(fonts.label, COPY.share, 3.5), [fonts.label]);
  const copiedGlyphs = useMemo(() => centredGlyphs(fonts.label, COPY.shared, 3.5), [fonts.label]);
  const copiedWidth = fonts.label.getGlyphWidths(fonts.label.getGlyphIDs(COPY.shared)).reduce((sum, n) => sum + n, 0) + (COPY.shared.length - 1) * 3.5;
  const alpha = useDerivedValue(() => uiProgress(state.get(), TIMING.controls, 0.42));
  const shareTransform = useDerivedValue(() => {
    const s = state.get(); const p = uiProgress(s, TIMING.controls, 0.42);
    return [{ translateY: s.reduced ? 0 : 16 * (1 - p) }, { scale: 1 - 0.03 * s.sharePress }];
  });
  const closeTransform = useDerivedValue(() => {
    const s = state.get();
    return [{ scale: (s.reduced ? 1 : 0.9 + 0.1 * uiProgress(s, TIMING.controls, 0.42)) * (1 - 0.03 * s.closePress) }];
  });
  const shareUniforms = useDerivedValue(() => {
    const s = state.get();
    return { uOrigin: [share.x, share.y], uSize: [share.width, share.height], uRadius: share.radius,
      uYaw: s.yaw, uLamp: s.lamp, uFlash: s.flash, uPress: s.sharePress, uSheen: s.sheen, uGem: 0, uPressPoint: [s.shareX, s.shareY] };
  });
  const closeUniforms = useDerivedValue(() => {
    const s = state.get();
    return { uOrigin: [cx - 18, cy - 18], uSize: [36, 36], uRadius: 18,
      uYaw: s.yaw, uLamp: s.lamp, uFlash: s.flash, uPress: s.closePress, uSheen: -1, uGem: 1, uPressPoint: [s.closeX, s.closeY] };
  });
  const normalAlpha = useDerivedValue(() => 1 - state.get().copied);
  const copiedAlpha = useDerivedValue(() => state.get().copied);
  const normalTransform = useDerivedValue(() => [{ translateY: state.get().reduced ? 0 : -10 * state.get().copied }]);
  const copiedTransform = useDerivedValue(() => [{ translateY: state.get().reduced ? 0 : 10 * (1 - state.get().copied) }]);
  const checkEnd = useDerivedValue(() => state.get().check);
  const baseline = capBaseline(fonts.label, share.y + share.height / 2);
  return <Group opacity={alpha}>
    <Group origin={{ x: frame.width / 2, y: share.y + share.height / 2 }} transform={shareTransform}>
      <Rect x={share.x - 8} y={share.y - 8} width={share.width + 16} height={share.height + 26}><Shader source={glass!} uniforms={shareUniforms} /></Rect>
      <Group opacity={normalAlpha} transform={normalTransform}>
        <Glyphs glyphs={shareGlyphs} x={frame.width / 2} y={baseline} font={fonts.label} color="#F5F8FF" />
      </Group>
      <Group opacity={copiedAlpha} transform={copiedTransform}>
        <Glyphs glyphs={copiedGlyphs} x={frame.width / 2 + 10} y={baseline} font={fonts.label} color="#F5F8FF" />
        <Group transform={[{ translateX: frame.width / 2 - copiedWidth / 2 - 13 }, { translateY: share.y + share.height / 2 }]}>
          <Path path={checkPath} color="#F1F7FF" style="stroke" strokeWidth={1.7} strokeCap="round" strokeJoin="round" start={0} end={checkEnd} />
        </Group>
      </Group>
    </Group>
    <Group origin={{ x: cx, y: cy }} transform={closeTransform}>
      <Rect x={cx - 22} y={cy - 22} width={44} height={44}><Shader source={glass!} uniforms={closeUniforms} /></Rect>
    </Group>
  </Group>;
}
export function TrophyControlTargets({ frame, enabled, activate }: {
  frame: TrophyFrame; enabled: boolean; activate: (target: 'share' | 'close') => void;
}) {
  return <View pointerEvents="box-none" style={{ position: 'absolute', inset: 0 }} accessibilityElementsHidden={!enabled}
    importantForAccessibility={enabled ? 'auto' : 'no-hide-descendants'}>
    <View accessible={enabled} accessibilityRole="button" accessibilityLabel={COPY.shareLabel}
      accessibilityState={{ disabled: !enabled }} onAccessibilityTap={() => activate('share')}
      accessibilityActions={[{ name: 'activate' }]} onAccessibilityAction={() => activate('share')}
      style={{ position: 'absolute', left: frame.share.x, top: frame.share.y, width: frame.share.width, height: frame.share.height }} />
    <View accessible={enabled} accessibilityRole="button" accessibilityLabel={COPY.closeLabel}
      accessibilityState={{ disabled: !enabled }} onAccessibilityTap={() => activate('close')}
      accessibilityActions={[{ name: 'activate' }]} onAccessibilityAction={() => activate('close')}
      style={{ position: 'absolute', left: frame.close.x - 2, top: frame.close.y - 2, width: 44, height: 44 }} />
  </View>;
}
