import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import { Platform, StyleSheet, View } from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

/** iOS 26 Liquid Glass when the system has it; otherwise a blur (iOS) or a tinted fill (Android). */
export const LIQUID_GLASS = isLiquidGlassAvailable();

type Props = {
  width: number;
  height: number;
  radius: number;
  /** 'clear' = the dark chrome (close, link); 'prominent' = the milky primary button. */
  kind: 'clear' | 'prominent';
  /** Reduce Transparency: an opaque surface with the same rim. */
  solid?: boolean;
};

const TINT = {
  clear: 'rgba(24,26,30,0.28)',
  prominent: 'rgba(255,255,255,0.9)',
} as const;
const FALLBACK = {
  clear: 'rgba(30,32,36,0.46)',
  prominent: 'rgba(250,250,252,0.94)',
} as const;
const SOLID = {
  clear: '#2E2D30',
  prominent: '#FAFAFA',
} as const;
/**
 * The specular rim, a 1 pt stroke: bright where the light comes from (top left), fading along the
 * sides, and a cool-to-warm hint of dispersion on the far edge.
 */
const RIM = {
  clear: [['#FFFFFF', 0.5], ['#AEEBFF', 0.08], ['#FFC2E6', 0.22]],
  prominent: [['#FFFFFF', 1], ['#BDEFFF', 0.35], ['#FFD2EA', 0.7]],
} as const;

/**
 * A glass surface drawn behind a control's content (absolute fill; give the parent the size and
 * radius). Native glass samples the light field behind it, so it picks up the colour by itself.
 */
export function GlassSurface({ width, height, radius, kind, solid = false }: Props) {
  const id = `nc-eps-rim-${kind}`;
  if (solid) {
    return (
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius, backgroundColor: SOLID[kind], borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.22)' }]} />
    );
  }
  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius, overflow: 'hidden' }]}>
      {LIQUID_GLASS ? (
        <GlassView
          glassEffectStyle={kind === 'prominent' ? 'regular' : 'clear'}
          colorScheme={kind === 'prominent' ? 'light' : 'dark'}
          tintColor={TINT[kind]}
          style={[StyleSheet.absoluteFill, { borderRadius: radius }]}
        />
      ) : (
        <>
          {Platform.OS === 'ios' ? <BlurView tint="dark" intensity={kind === 'prominent' ? 30 : 40} style={StyleSheet.absoluteFill} /> : null}
          <View style={[StyleSheet.absoluteFill, { backgroundColor: FALLBACK[kind] }]} />
        </>
      )}
      <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
        <Defs>
          <LinearGradient id={id} x1="0" y1="0" x2="1" y2="1">
            {RIM[kind].map(([color, opacity], index) => (
              <Stop key={color} offset={index === 0 ? 0 : index === 1 ? 0.55 : 1} stopColor={color} stopOpacity={LIQUID_GLASS ? opacity * 0.6 : opacity} />
            ))}
          </LinearGradient>
        </Defs>
        <Rect x={0.5} y={0.5} width={width - 1} height={height - 1} rx={radius - 0.5} ry={radius - 0.5} fill="none" stroke={`url(#${id})`} strokeWidth={1} />
      </Svg>
    </View>
  );
}
