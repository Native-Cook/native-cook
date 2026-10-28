import { Canvas, Fill, ImageShader, Shader, Skia, useImage, type Uniforms } from '@shopify/react-native-skia';
import { memo, useCallback } from 'react';
import { Image, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, useSharedValue, type SharedValue } from 'react-native-reanimated';

import { DiamondGpu } from './gpu/DiamondGpu';
import type { DiamondGpuFrame } from './gpu/types';
import type { PrismaticFrame } from './PrismaticGameProfileMotion';

// Three material responses, compiled once. Original alpha and baked facet detail remain intact.
const PREFIX = `uniform shader artwork; uniform float phase; uniform float power;
  half4 main(float2 p) { half4 src = artwork.eval(p); float a = src.a;
  float3 base = a > 0.001 ? src.rgb / a : float3(0.0); float2 uv = p / float2(114.0,126.0);`;
const GOLD = Skia.RuntimeEffect.Make(PREFIX + `
  float metal = smoothstep(0.05,0.24,base.r-base.b) * smoothstep(0.02,0.18,base.g-base.b);
  float travel=clamp((phase-0.10)/0.55,0.0,1.0);
  float x=0.20+0.65*(1.0-pow(1.0-travel,3.0));
  float y=0.48-0.05*sin(travel*3.141593);
  float2 d=uv-float2(x,y);
  float core=exp(-dot(d/float2(0.034,0.034),d/float2(0.034,0.034))*1.4);
  float strike=exp(-pow((d.x+0.62*d.y)/0.014,2.0))*exp(-pow(d.y/0.12,2.0));
  float cross=exp(-pow((d.x-1.15*d.y)/0.012,2.0))*exp(-pow(d.x/0.095,2.0));
  float shoulder=exp(-dot(d/float2(0.13,0.16),d/float2(0.13,0.16))*1.8);
  float baseCatch=exp(-pow((uv.x-(0.22+0.62*phase))/0.14,2.0)-pow((uv.y-0.745)/0.024,2.0));
  float specular=power*metal*(0.98*core+0.92*strike+0.58*cross);
  float3 gold=mix(base,float3(1.0,0.99,0.80),clamp(specular,0.0,0.98));
  gold+=power*metal*(baseCatch*float3(0.30,0.23,0.08)+shoulder*float3(0.16,0.12,0.035));
  return half4(clamp(gold,0.0,1.0)*a,a); }`);
const ENAMEL = Skia.RuntimeEffect.Make(PREFIX + `
  half4 horizontal=artwork.eval(p+float2(2.3,0.0));
  half4 vertical=artwork.eval(p+float2(0.0,2.3));
  float3 hx=horizontal.rgb/max(horizontal.a,0.001); float3 vy=vertical.rgb/max(vertical.a,0.001);
  float boundary=max(length(base-hx),length(base-vy));
  float bevel=smoothstep(0.025,0.19,boundary);
  float angle = (atan(uv.y-0.49,uv.x-0.5)+3.141593)/6.283186;
  float delta = abs(angle-phase); delta=min(delta,1.0-delta);
  float tracing = exp(-pow(delta/0.15,2.0));
  float teal = smoothstep(0.04,0.22,base.b-base.r) * smoothstep(0.0,0.08,base.g-base.b*0.72);
  float inset = exp(-pow((length((uv-float2(0.50,0.48))*float2(1.0,0.78))-(0.43-0.32*phase))/0.065,2.0));
  float enamel = power*teal*(0.98*tracing*(0.78+0.22*bevel)+0.32*inset);
  float3 color = mix(base,float3(0.84,1.0,0.99),clamp(enamel,0.0,0.96));
  return half4(color*a,a); }`);
const PRISM = Skia.RuntimeEffect.Make(PREFIX + `
  float facet=uv.y<0.355 ? 0.0 : uv.x<0.38 ? 1.0 : uv.x>0.65 ? 2.0 : uv.y<0.45 ? 3.0 : uv.x<0.51 ? 4.0 : 5.0;
  float facing = 0.5+0.5*cos(facet*1.72-phase*6.283186);
  float edge = smoothstep(0.24,0.92,facing)*power;
  float shift = 1.5*edge;
  half4 r = artwork.eval(p+float2(shift,-0.3*shift));
  half4 b = artwork.eval(p-float2(shift,-0.3*shift));
  float3 dispersed = float3(r.r/max(r.a,0.001),base.g,b.b/max(b.a,0.001));
  float3 spectrum = 0.5+0.5*cos(float3(0.0,2.1,4.2)+facet*1.13-phase*4.2);
  float3 color = mix(base,dispersed,0.7*edge);
  color=mix(color,float3(0.15,0.76,1.0)+spectrum*float3(0.52,0.18,0.43),0.42*edge);
  color+=edge*float3(0.08,0.15,0.18);
  return half4(clamp(color,0.0,1.0)*a,a); }`);
const EFFECTS = [GOLD, ENAMEL, PRISM];
const CAUSTIC = Skia.RuntimeEffect.Make(`uniform float phase; uniform float power; uniform float kind;
  half4 main(float2 p) {
    float dx=p.x-79.0;
    float arcY=mix(122.0,115.5,kind)+0.0075*dx*dx;
    float normal=(p.y-arcY)/sqrt(1.0+0.000225*dx*dx);
    float taper=exp(-pow(dx/30.0,4.0)*1.6);
    float band=exp(-0.5*pow(normal/1.4,2.0));
    float shoulder=exp(-0.5*pow(normal/3.2,2.0));
    float3 spectrum=float3(exp(-0.5*pow((normal-1.0)/1.25,2.0)),
      exp(-0.5*pow(normal/1.25,2.0)),exp(-0.5*pow((normal+1.0)/1.25,2.0)));
    spectrum/=max(max(spectrum.r,spectrum.g),max(spectrum.b,0.001));
    float3 color=mix(float3(1.0,0.81,0.30),spectrum,kind);
    float a=power*taper*(0.48*band+0.07*shoulder);
    return half4(color*a,a);
  }`);

export const BadgeMaterial = memo(function BadgeMaterial({ index, asset, frame, renderer, onRenderer }: {
  index: number; asset: number; frame: SharedValue<PrismaticFrame>; renderer: 'auto' | 'skia'; onRenderer: (ready: boolean) => void;
}) {
  const image = useImage(asset);
  const effect = EFFECTS[index];
  const gpuReady = useSharedValue(false);
  const gpuFrame = useDerivedValue<DiamondGpuFrame>(() => {
    const f = frame.get();
    const visible = !f.reduced && f.material === 2 && f.power > 0;
    return { phase: f.time, progress: f.material === 2 ? f.power : 0,
      lightX: -1 + 2*f.progress, lightY: -0.35+0.45*f.progress,
      visible, active: visible && f.active,
      reduced: f.reduced };
  });
  const uniforms = useDerivedValue<Uniforms>(() => {
    const f = frame.get();
    return { phase: f.material === index ? f.progress : 0, power: f.material === index ? f.power : 0 };
  });
  const causticUniforms = useDerivedValue<Uniforms>(() => {
    const f = frame.get();
    return { phase: f.progress, power: f.material === index ? f.power : 0, kind: index === 2 ? 1 : 0 };
  });
  const fallbackStyle = useAnimatedStyle(() => ({ opacity: index === 2 && gpuReady.get() && gpuFrame.get().visible ? 0 : 1 }));
  const gpuStyle = useAnimatedStyle(() => ({ opacity: gpuReady.get() && gpuFrame.get().visible ? 1 : 0 }));
  const handleReady = useCallback((value: boolean) => { gpuReady.set(value); onRenderer(value); }, [gpuReady, onRenderer]);
  const unavailable = useCallback(() => handleReady(false), [handleReady]);
  return <View style={styles.icon} pointerEvents="none" accessible={false}>
    <Animated.View style={[StyleSheet.absoluteFill, fallbackStyle]}>
      {index !== 1 && CAUSTIC ? <Canvas style={styles.caustic} accessible={false}><Fill><Shader source={CAUSTIC} uniforms={causticUniforms} /></Fill></Canvas> : null}
      {image && effect ? <Canvas style={StyleSheet.absoluteFill} accessible={false}><Fill><Shader source={effect} uniforms={uniforms}><ImageShader image={image} fit="contain" rect={{ x: 0, y: 0, width: 114, height: 126 }} /></Shader></Fill></Canvas>
        : <Image source={asset} style={StyleSheet.absoluteFill} resizeMode="contain" resizeMethod="resize" accessible={false} />}
    </Animated.View>
    {index === 2 && renderer === 'auto' ? <Animated.View style={[styles.gpu, gpuStyle]} pointerEvents="none"><DiamondGpu frame={gpuFrame} asset={asset} size={114} onReady={handleReady} onUnavailable={unavailable} /></Animated.View> : null}
  </View>;
});
const styles = StyleSheet.create({ icon: { position: 'absolute', left: 22, top: 16, width: 114, height: 126 }, gpu: { position: 'absolute', left: 0, top: 6, width: 114, height: 114 }, caustic: { position: 'absolute', left: -22, top: -16, width: 158, height: 158 } });
