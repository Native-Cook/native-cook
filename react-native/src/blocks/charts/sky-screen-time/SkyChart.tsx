import Animated, { type SharedValue, useAnimatedProps } from 'react-native-reanimated';
import Svg, { Circle, ClipPath, Defs, G, Line, Path, Rect, Text as SvgText } from 'react-native-svg';

import type { SkyScreenTimeFrame, SkyScreenTimeHour } from './motion';

const AnimatedG = Animated.createAnimatedComponent(G);
const AnimatedLine = Animated.createAnimatedComponent(Line);
const AnimatedCircle = Animated.createAnimatedComponent(Circle);
const STRIDE = 292 / 24;

function ChartBar({ hour, index, frame }: { hour: SkyScreenTimeHour; index: number; frame: SharedValue<SkyScreenTimeFrame> }) {
  const x = index * STRIDE + 2;
  const height = Math.min(30, hour.minutes) / 30 * 86;
  const top = 86 - height;
  const animatedProps = useAnimatedProps(() => {
    const current = frame.get();
    return { opacity: current.selection > 0.05 && Math.abs(index - current.hour) > 0.65 ? 1 - 0.65 * current.selection : 1 };
  });
  let bottom = 86;
  const segments = [
    { minutes: hour.neutral, color: '#858796' },
    { minutes: hour.productive, color: '#57BF71' },
    { minutes: hour.distracted, color: '#F0B13F' },
  ].map((segment) => {
    const segmentHeight = segment.minutes / 30 * 86;
    bottom -= segmentHeight;
    return { ...segment, y: bottom, height: segmentHeight };
  });
  return (
    <G>
      <Rect x={x} y={0} width={8} height={86} rx={3} fill="#F5F5F7" />
      {height > 0 && (
        <AnimatedG animatedProps={animatedProps} clipPath={`url(#bar-${index})`}>
          {segments.map((segment, offset) => (
            <Rect key={offset} x={x} y={segment.y} width={8} height={segment.height} fill={segment.color} />
          ))}
        </AnimatedG>
      )}
      <Defs><ClipPath id={`bar-${index}`}><Rect x={x} y={top} width={8} height={height} rx={3} /></ClipPath></Defs>
    </G>
  );
}

function Marker({ frame }: { frame: SharedValue<SkyScreenTimeFrame> }) {
  const lineProps = useAnimatedProps(() => {
    const current = frame.get();
    const x = current.hour * STRIDE + 6;
    return { x1: x, x2: x, opacity: current.selection };
  });
  const circleProps = useAnimatedProps(() => {
    const current = frame.get();
    return { cx: current.hour * STRIDE + 6, opacity: current.selection };
  });
  return (
    <>
      <AnimatedLine animatedProps={lineProps} y1={-8} y2={86} stroke="#3988DE" strokeWidth={1.2} strokeDasharray="3 3" />
      <AnimatedCircle animatedProps={circleProps} cy={-9} r={3} fill="#3988DE" />
    </>
  );
}

export function SkyChart({ hours, frame, width, height }: { hours: SkyScreenTimeHour[]; frame: SharedValue<SkyScreenTimeFrame>; width: number; height: number }) {
  return (
    <Svg width={width} height={height} viewBox="0 0 330 126" accessible={false}>
      <G transform="translate(0 14)">
        {hours.map((hour, index) => <ChartBar key={index} hour={hour} index={index} frame={frame} />)}
        {[0, 43, 86].map((y) => <Line key={y} x1={0} x2={292} y1={y} y2={y} stroke="#E0E1E5" strokeWidth={0.65} />)}
        {['30min', '15min', '0s'].map((label, index) => (
          <SvgText key={label} x={297} y={index * 43 + 3} fontSize={10} fill="#8A8C94">{label}</SvgText>
        ))}
        {['12 AM', '6 AM', '12 PM', '6 PM'].map((label, index) => {
          const x = index * 73;
          return (
            <G key={label}>
              <Line x1={x} x2={x} y1={86} y2={102} stroke="#D3D5DC" strokeWidth={0.7} />
              <SvgText x={x + 3} y={107} fontSize={10} fill="#8A8C94">{label}</SvgText>
            </G>
          );
        })}
        <Marker frame={frame} />
      </G>
    </Svg>
  );
}

export function QualityFace({ kind, size }: { kind: 0 | 1 | 2; size: number }) {
  const mouthY = kind === 2 ? 21 : 19;
  const controlY = kind === 0 ? 24 : kind === 1 ? 19 : 15;
  return (
    <Svg width={size} height={size} viewBox="0 0 28 28" accessible={false}>
      <Rect x={0.5} y={0.5} width={27} height={27} rx={8} fill={kind === 0 ? '#60CA6A' : 'rgba(255,255,255,0.04)'} stroke={`rgba(255,255,255,${kind === 0 ? 0.15 : 0.45})`} />
      <Defs><ClipPath id={`face-clip-${kind}`}><Rect x={0.5} y={0.5} width={27} height={27} rx={8} /></ClipPath></Defs>
      {kind !== 0 && <Rect x={0.5} y={26} width={27} height={1.5} clipPath={`url(#face-clip-${kind})`} fill={kind === 1 ? '#8A98A9' : '#F0B13F'} />}
      <Path
        d={`M9 8v2M19 8v2M14 9v5q0 2-2 1M9 ${mouthY}Q14 ${controlY} 19 ${mouthY}`}
        fill="none"
        stroke="#FFFFFF"
        strokeWidth={1.7}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}
