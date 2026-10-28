import { Canvas, Group, RoundedRect, Shadow, interpolateColors } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { type ProfileSourceGeometry } from './PrismaticGameProfileLayout';
import type { BadgeOrigin } from './PrismaticGameProfileMotion';

/** Only the local art contour changes geometry; native panel content never runs a layout animation. */
export function PrismaticDetailMorph({ progress, object, originColor, origin,sourceGeometry }: { progress: SharedValue<number>; object: boolean; originColor: string; origin: SharedValue<BadgeOrigin>;sourceGeometry:SharedValue<ProfileSourceGeometry> }) {
  const bounds = useDerivedValue(() => {
    const p = progress.get();
    const pose = origin.get();const stats=sourceGeometry.get().statistics;
    const w = object ? pose.size : stats.width; const h = object ? pose.size : stats.height;
    const startX = object ? pose.x-pose.size/2 : stats.left;
    const startY = object ? pose.y-pose.size/2-280 : stats.top-280;
    const radius = (object ? pose.size/2 : 16)*(1-p)+24*p;
    return { rect: { x: startX+(70-startX)*p, y: startY+(76-startY)*p,
      width: w+(253-w)*p, height: h+(178-h)*p }, rx: radius, ry: radius };
  });
  const opacity = useDerivedValue(() => progress.get() > 0 ? 1 : 0);
  const color = useDerivedValue(() => interpolateColors(progress.get(), [0,1], [originColor, '#F4FBFF']));
  return <Canvas style={styles.canvas} accessible={false} pointerEvents="none"><Group opacity={opacity}>
    <RoundedRect rect={bounds} color={color}><Shadow dx={0} dy={7} blur={12} color="#183F5933" /></RoundedRect>
    <RoundedRect rect={bounds} color="#CFDFEC" style="stroke" strokeWidth={1} />
  </Group></Canvas>;
}
const styles = StyleSheet.create({ canvas: { position: 'absolute', left: 0, top: 280, width: 393, height: 280 } });
