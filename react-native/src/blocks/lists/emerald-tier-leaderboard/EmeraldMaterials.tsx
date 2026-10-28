import { Canvas, Fill, ImageShader, Shader, Skia, useImage, type Uniforms } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { Image, StyleSheet, View } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import type { EmeraldFrame } from './motion';
import { EMERALD_LIGHT_GLASS, EmeraldLightSurface } from './EmeraldLightSurface';

const ART = Skia.RuntimeEffect.Make(`uniform shader artwork; uniform float2 size; uniform float phase; uniform float power;
half4 main(float2 p) { half4 s=artwork.eval(p); float a=s.a; float3 base=s.rgb/max(a,0.001); float2 uv=p/size;
float line=uv.x+uv.y*.32-mix(-.3,1.5,phase); float shine=exp(-pow(line/.055,2.0));
float facets=smoothstep(.18,.7,max(base.r,max(base.g,base.b))); float3 c=base+float3(.70,.61,.30)*shine*power*(.32+.68*facets);
return half4(clamp(c,0.0,1.0)*a,a); }`);
export function ArtMaterial({ asset, width, height, frame, onReady, reactToRank = false }: { asset: number; width: number; height: number; frame: SharedValue<EmeraldFrame>; onReady?: (ready: boolean) => void; reactToRank?: boolean }) {
  const image = useImage(asset);
  const uniforms = useDerivedValue<Uniforms>(() => { const f = frame.get(); return { size: [width, height], phase: f.responsePhase, power: reactToRank ? f.response * .50 : 0 }; });
  useEffect(() => { onReady?.(!!image && !!ART && !!EMERALD_LIGHT_GLASS); }, [image, onReady]);
  return <View style={{ width, height }} pointerEvents="none" accessible={false}>{image && ART ? <Canvas style={StyleSheet.absoluteFill}><Fill><Shader source={ART} uniforms={uniforms}><ImageShader image={image} fit="contain" rect={{ x: 0, y: 0, width, height }} /></Shader></Fill></Canvas> : <Image source={asset} resizeMode="contain" style={StyleSheet.absoluteFill} />}</View>;
}
export function PodiumMaterial({ width, height, frame, highlighted = true, solid = false }: { width: number; height: number; frame: SharedValue<EmeraldFrame>; highlighted?: boolean; solid?: boolean }) {
  return EMERALD_LIGHT_GLASS && !solid
    ? <EmeraldLightSurface width={width} height={height} radius={4} light={highlighted ? .76 : .31} frame={highlighted ? frame : undefined} />
    : <View style={{ width, height, backgroundColor: '#0A1B18', borderWidth: .7, borderColor: '#c5e6c72e' }} />;
}
