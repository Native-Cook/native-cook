import { Canvas, Fill, Shader, Skia, type Uniforms } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import type { EmeraldFrame } from './motion';

// One contact response follows the crown seating. No separate airborne trail or effect clock.
const CONTACT = Skia.RuntimeEffect.Make(`uniform float phase; uniform float power;
half4 main(float2 p) { float x=p.x-197.; float center=-45.+phase*90.; float dx=x-center;
float lip=165.5+.0014*x*x; float dy=p.y-lip;
float span=(1.-smoothstep(50.,62.,abs(x))); float envelope=exp(-pow(dx/22.,2.))*span;
float line=exp(-pow(dy/1.15,2.)); float shoulder=exp(-pow(dy/4.8,2.));
float alpha=power*envelope*(line*.76+shoulder*.085);
float3 color=mix(float3(.66,1.,.80),float3(1.,.92,.63),clamp(phase+.2,0.,1.));
return half4(color*alpha,alpha); }`);
export function EmeraldEffects({ frame }: { frame: SharedValue<EmeraldFrame> }) {
  const uniforms = useDerivedValue<Uniforms>(() => ({ phase: frame.get().responsePhase, power: frame.get().response }));
  return <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessible={false}>
    {CONTACT ? <Fill><Shader source={CONTACT} uniforms={uniforms} /></Fill> : null}
  </Canvas>;
}
