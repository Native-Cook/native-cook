import { Canvas, Fill, Shader, Skia, type Uniforms } from '@shopify/react-native-skia';
import { memo } from 'react';
import Animated, { useAnimatedReaction, useAnimatedStyle, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { peelFormation, peelSurfaceBounds } from './PrismaticPeelFormation';

export type PlayerCardMaterialFrame = {
  progress: number;
  /** Matching actor tilt in degrees, bounded to +/-6. */
  tiltX: number;
  tiltY: number;
  reduced: boolean;
  active: boolean;
  /** Same-controller finite formation pulse; omitted means standing light only. */
  catchStrength?: number;
  /** Captured original badge-circle centre and diameter in stage coordinates. */
  sourceX: number;
  sourceY: number;
  sourceSize: number;
  /** Actual moving portrait/name/handle union, in the same untransformed stage. */
  peelAvoidance?: { x: number; y: number; width: number; height: number };
};
export type PlayerCardMaterialsProps = {
  frame: SharedValue<PlayerCardMaterialFrame>;
  width?: number;
  height?: number;
  radius?: number;
  foilStart?: number;
};

/** One stateless angular lobe; no pulse, clock, latch or extra shared value. */
export function playerCardOptics(tiltX: number, tiltY: number, openPulse = 0, width = 320, height = 590, reduced = false) {
  'worklet';
  if (reduced) return { openPulse: 0, angleDegrees: 0, angleResponse: 0, opticalStrength: 0 };
  const x=Math.max(-6,Math.min(6,tiltX));
  const y=Math.max(-6,Math.min(6,tiltY));
  const angleDegrees=(x*height*0.072+y*width*0.09)/Math.max(1,height*0.072+width*0.09);
  const t=Math.max(0,Math.min(1,(angleDegrees-0.5)/2));
  const engaged=t*t*(3-2*t);
  const distance=(angleDegrees-4.5)/1.35;
  const angleResponse=engaged*Math.exp(-0.5*distance*distance);
  const pulse=Math.max(0,Math.min(1,openPulse));
  return { openPulse: pulse, angleDegrees, angleResponse,
    opticalStrength: Math.max(pulse,0.75*angleResponse) };
}

const CANVAS_STYLE = { position: 'absolute' as const, left: 0, top: 40, width: 393, height: 690 };
const PLAYER_CARD_SKSL = /* sksl */ `
uniform float4 bounds;
uniform float4 shape;
uniform float2 light;
uniform float catchStrength;
uniform float opticalStrength;
uniform float peelCurl;
uniform float4 peelAvoidance;

float roundedDistance(float2 p, float2 halfSize, float radius) {
  float2 q=abs(p)-halfSize+radius;
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float coverage(float distance) { return 1.0-smoothstep(-0.5,0.5,distance); }
// The only deforming region is a 20 pt blank lip along the top material edge.
// Its projected silhouette recedes as the warm underside rolls toward the face.
float topContour(float x, float width, float radius) {
  float corner=max(0.0,radius-min(x,width-x));
  return radius-sqrt(max(0.0,radius*radius-corner*corner));
}
float curlEnvelope(float x, float width) {
  float u=clamp(x/width,0.0,1.0);
  float envelope=smoothstep(0.0,0.18,u)*(1.0-smoothstep(0.82,1.0,u));
  if (peelAvoidance.z<=0.0 || peelAvoidance.w<=0.0) return envelope;
  float4 avoid=float4(peelAvoidance.xy-bounds.xy,peelAvoidance.zw);
  float top=topContour(x,width,shape.x);
  // The 10 pt guard includes the backing/cast's 4 pt translation, so every
  // additional channel clears rigid actors by at least 6 pt; a 24 pt taper
  // lets the rolled edge flatten gradually without forming a vertical notch.
  float horizontal=smoothstep(avoid.x-34.0,avoid.x-10.0,x)*
    (1.0-smoothstep(avoid.x+avoid.z+10.0,avoid.x+avoid.z+34.0,x));
  float vertical=smoothstep(avoid.y-34.0,avoid.y-10.0,top+26.0)*
    (1.0-smoothstep(avoid.y+avoid.w+10.0,avoid.y+avoid.w+34.0,top));
  return envelope*(1.0-horizontal*vertical);
}
float curlTrim(float x, float width) {
  return 7.0*peelCurl*curlEnvelope(x,width)*(0.72+0.28*clamp(x/width,0.0,1.0));
}
float peeledDistance(float2 p, float2 size, float radius) {
  float intact=roundedDistance(p-size*0.5,size*0.5,radius);
  if (p.y>radius+20.0) return intact;
  float trim=curlTrim(p.x,size.x);
  if (trim<=0.0) return intact;
  return roundedDistance(p-float2(0.0,trim)-size*0.5,size*0.5,radius);
}
// Ray/rounded-rectangle intersection keeps the source continuously on its real edge.
float2 perimeterSource(float2 halfSize, float radius, float angle) {
  float2 direction=float2(cos(angle),sin(angle));
  float2 absolute=abs(direction);
  float extent=min(halfSize.x/max(absolute.x,0.0001),halfSize.y/max(absolute.y,0.0001));
  float2 point=absolute*extent;
  float2 corner=halfSize-radius;
  if (point.x>corner.x && point.y>corner.y) {
    float projection=dot(absolute,corner);
    extent=projection+sqrt(max(0.0,projection*projection-dot(corner,corner)+radius*radius));
  }
  return direction*extent;
}
float sourceWindow(float2 delta, float2 normal, float tangentWidth, float normalWidth) {
  float t=dot(delta,float2(-normal.y,normal.x))/tangentWidth;
  float n=dot(delta,normal)/normalWidth;
  return exp(-0.5*(t*t+n*n));
}
half4 main(float2 pixel) {
  if (shape.z<=0.0) return half4(0.0);
  float2 stage=pixel+float2(0.0,40.0);
  float2 size=bounds.zw;
  float radius=shape.x;
  float2 p=stage-bounds.xy;
  float2 centre=size*0.5;
  float2 halfSize=size*0.5;
  float2 uv=p/size;
  if (p.x < -28.0 || p.x > size.x+28.0 || p.y < -28.0 || p.y > size.y+28.0) return half4(0.0);
  float formation=shape.w>0.5 ? 0.0 : smoothstep(0.80,0.98,shape.z);
  float eclipse=shape.w>0.5 ? 0.0 : smoothstep(0.72,0.98,shape.z);
  float optical=clamp(max(catchStrength,opticalStrength),0.0,1.0)*eclipse;
  float shift=light.y*size.x*0.09+light.x*size.y*0.072;
  float drive=clamp(shift/max(1.0,size.x*0.09+size.y*0.072),-1.0,1.0);
  // A shallow tilt stays on the corner arc; larger tilt explores the raised edge.
  float sourceDrive=drive*abs(drive);
  float frontDistance=roundedDistance(p-centre,halfSize,radius);
  if (peelCurl>0.0) frontDistance=peeledDistance(p,size,radius);
  float frontAlpha=coverage(frontDistance);
  float backDistance=roundedDistance(p-centre-float2(2.3,3.3),halfSize,radius);
  if (peelCurl>0.0) backDistance=peeledDistance(p-float2(2.3,3.3),size,radius);
  float backAlpha=coverage(backDistance);
  float shadowDistance=max(0.0,roundedDistance(p-centre-float2(1.5,4.0),halfSize,radius));
  if (peelCurl>0.0) shadowDistance=max(0.0,peeledDistance(p-float2(1.5,4.0),size,radius));
  float canvasEdge=min(min(pixel.x,393.0-pixel.x),min(pixel.y,690.0-pixel.y));
  float edgeFade=smoothstep(0.0,6.0,canvasEdge);
  float3 outputColor=float3(0.0);
  float outputAlpha=0.0;
  // Emission is behind the opaque card, never a bloom layer over face or text.
  if (frontAlpha<1.0 && eclipse>0.0) {
    float2 source=perimeterSource(halfSize,radius,-2.074+3.14159265*sourceDrive);
    float2 cornerDelta=max(abs(source)-(halfSize-radius),0.0)*sign(source);
    float2 normal=cornerDelta/max(length(cornerDelta),0.0001);
    float2 delta=p-centre-source;
    float outside=max(0.0,frontDistance);
    float aureole=exp(-0.5*outside*outside/81.0)*sourceWindow(delta,normal,58.0,36.0);
    float leak=exp(-outside/1.25)*sourceWindow(delta,normal,70.0,50.0);
    // Elongation follows the edge; the intact front clips it into a crescent.
    float bloom=sourceWindow(delta,normal,22.0,7.0);
    float warmAlpha=clamp(eclipse*(0.13+0.18*optical)*aureole+eclipse*(0.24+0.22*optical)*leak+0.58*optical*bloom,0.0,0.88)*edgeFade;
    float3 warmColor=mix(float3(0.94,0.87,0.99),float3(1.0,0.94,0.73),0.42+0.44*optical);
    float2 oppositeDelta=p-centre+source;
    float reflected=exp(-0.5*outside*outside/36.0)*sourceWindow(oppositeDelta,-normal,30.0,22.0);
    float reflectedAlpha=eclipse*(0.055+0.045*optical)*reflected*edgeFade;
    float3 reflectedColor=mix(float3(0.62,0.91,1.0),float3(0.79,0.72,1.0),0.5+0.5*drive);
    outputColor=reflectedColor*reflectedAlpha+warmColor*warmAlpha*(1.0-reflectedAlpha);
    outputAlpha=reflectedAlpha+warmAlpha*(1.0-reflectedAlpha);
  }
  float shadowAlpha=0.17*exp(-shadowDistance*shadowDistance/18.0)*edgeFade;
  outputColor=float3(0.055,0.065,0.09)*shadowAlpha+outputColor*(1.0-shadowAlpha);
  outputAlpha=shadowAlpha+outputAlpha*(1.0-shadowAlpha);
  float3 backColor=mix(float3(0.52,0.31,0.10),float3(0.86,0.64,0.28),clamp(uv.y,0.0,1.0));
  outputColor=backColor*backAlpha+outputColor*(1.0-backAlpha);
  outputAlpha=backAlpha+outputAlpha*(1.0-backAlpha);
  float opacity=shape.w>0.5 ? shape.z : smoothstep(0.015,0.17,shape.z);
  if (frontAlpha<=0.0) return half4(outputColor*opacity,outputAlpha*opacity);

  float innerDistance=roundedDistance(p-centre,halfSize-3.0,max(1.0,radius-3.0));
  if (peelCurl>0.0 && p.y<radius+20.0) {
    float trim=curlTrim(p.x,size.x);
    if (trim>0.0) innerDistance=roundedDistance(p-float2(0.0,trim)-centre,
      halfSize-3.0,max(1.0,radius-3.0));
  }
  float pearlAlpha=coverage(innerDistance);
  float goldValue=clamp(0.58+0.16*(1.0-uv.y)+0.14*(1.0-uv.x)+0.045*(light.x-light.y),0.0,1.0);
  float3 gold=mix(float3(0.74,0.49,0.17),float3(1.0,0.89,0.63),goldValue);
  float ridge=exp(-pow((frontDistance+1.05)/0.48,2.0));
  gold=mix(gold,float3(1.0,0.98,0.84),0.72*ridge);
  float3 pearl=mix(float3(1.0,0.995,0.979),float3(0.974,0.951,0.908),clamp(uv.y,0.0,1.0));
  pearl+=0.012*(light.x*(uv.x-0.5)-light.y*(uv.y-0.5));
  float lip=exp(-max(0.0,-innerDistance)/1.4);
  pearl*=1.0-0.026*lip;

  // The same tilted light plane crosses the lower pearl and the gold rim.
  float distance=(0.55*p.x+p.y-size.y*0.87-shift)/1.141271;
  float core=exp(-0.5*distance*distance/49.0);
  float shoulder=exp(-0.5*distance*distance/324.0);
  float filmPhase=distance*0.11+0.60*light.y-0.35*light.x;
  float3 spectrum=0.5+0.5*cos(float3(0.0,2.1,4.2)+filmPhase);
  float3 film=0.58+0.42*spectrum;
  float foil=formation*(0.50*core+0.22*shoulder);
  float lower=smoothstep(size.y*shape.y+1.0,size.y*shape.y+17.0,p.y);
  pearl=mix(pearl,film,foil*lower);
  pearl+=float3(0.08+0.035*optical)*formation*core*lower;
  gold=mix(gold,mix(film,float3(1.0),0.45),formation*(0.82*core+0.18*shoulder));
  float3 frontColor=clamp(mix(gold,pearl,pearlAlpha),0.0,1.0);
  if (peelCurl>0.0 && p.y<radius+26.0) {
    float envelope=curlEnvelope(p.x,size.x);
    float depth=20.0*peelCurl*envelope;
    float top=p.y-topContour(p.x,size.x,radius);
    float trim=curlTrim(p.x,size.x);
    float turn=clamp((top-trim)/max(0.5,depth-trim),0.0,1.0);
    // A convex warm reverse face, a narrow reflected rim and an occluded return.
    // Upper-left illumination casts onto the face below/right of the hinge.
    float reverseFace=smoothstep(trim+1.1,trim+2.4,top)*(1.0-smoothstep(depth-1.0,depth+0.5,top));
    float belly=exp(-pow((turn-0.28)/0.28,2.0));
    float returnShade=smoothstep(0.52,1.0,turn);
    float3 underside=mix(float3(0.88,0.79,0.62),float3(1.0,0.96,0.85),belly);
    underside*=1.0-0.19*returnShade;
    underside+=0.018*(1.0-uv.x);
    frontColor=mix(frontColor,underside,reverseFace*peelCurl);
    float reflectedRim=exp(-pow((top-trim-1.15)/0.52,2.0))*peelCurl*envelope;
    frontColor=mix(frontColor,float3(1.0,0.965,0.82),0.72*reflectedRim);
    float castShadow=smoothstep(depth-0.5,depth+0.6,top)*exp(-max(0.0,top-depth)/2.0);
    float contact=exp(-pow((top-depth-0.25)/0.65,2.0));
    float shadowSupport=1.0-smoothstep(depth+2.0,depth+3.0,top);
    float localShadow=(0.15*castShadow+0.13*contact)*peelCurl*envelope*shadowSupport;
    frontColor*=1.0-localShadow;
  }
  return half4((frontColor*frontAlpha+outputColor*(1.0-frontAlpha))*opacity,
    (frontAlpha+outputAlpha*(1.0-frontAlpha))*opacity);
}`;

// One cached effect, with no per-frame paths, blur layers, devices or image uploads.
const PLAYER_CARD_EFFECT = (() => {
  try { return Skia.RuntimeEffect.Make(PLAYER_CARD_SKSL); }
  catch { return null; }
})();

function PearlFallback({ uniforms }: { uniforms: SharedValue<Uniforms> }) {
  const style = useAnimatedStyle(() => {
    const u=uniforms.get(); const b=u.bounds as number[]; const s=u.shape as number[];
    const p=Math.max(0,Math.min(1,(s[2]-0.015)/0.155));
    return { left: b[0], top: b[1], width: b[2], height: b[3], borderRadius: s[0],
      opacity: s[3]>0.5 ? s[2] : p*p*(3-2*p) };
  });
  return <Animated.View pointerEvents="none" accessible={false} style={[{
    position: 'absolute', backgroundColor: '#FFFBF3', borderColor: '#EACD8A', borderWidth: 3,
    shadowColor: '#0E1117', shadowOffset: { width: 1.5, height: 4 }, shadowOpacity: 0.17,
    shadowRadius: 3, elevation: 2,
  },style]} />;
}

/** Parent owns the common world-pivot tilt, actors, visibility and lifecycle clock. */
export const PlayerCardMaterials = memo(function PlayerCardMaterials({
  frame, width = 320, height = 590, radius = 34, foilStart = 348,
}: PlayerCardMaterialsProps) {
  const targetX=(393-width)*0.5;
  const uniforms = useSharedValue<Uniforms>({ bounds: [targetX,85,width,height],
    shape: [radius,foilStart/height,0,0], light: [0,0], catchStrength: 0, opticalStrength: 0,
    peelCurl: 0, peelAvoidance: [0,0,0,0] });
  useAnimatedReaction(() => frame.get(), (f, previous) => {
    // Upgrade retained HMR uniforms once, without adding another state channel.
    const complete=typeof uniforms.get().opticalStrength === 'number' && typeof uniforms.get().peelCurl === 'number' &&
      uniforms.get().peelAvoidance!==undefined;
    // Unrelated parent time/active/source changes cannot redraw an invisible surface.
    if (complete && f.progress<=0 && (!previous || previous.progress<=0)) return;
    if (complete && previous && previous.progress===f.progress && previous.tiltX===f.tiltX && previous.tiltY===f.tiltY &&
      previous.reduced===f.reduced && previous.sourceX===f.sourceX && previous.sourceY===f.sourceY && previous.sourceSize===f.sourceSize && (previous.catchStrength ?? 0)===(f.catchStrength ?? 0) &&
      (previous.peelAvoidance?.x ?? 0)===(f.peelAvoidance?.x ?? 0) && (previous.peelAvoidance?.y ?? 0)===(f.peelAvoidance?.y ?? 0) &&
      (previous.peelAvoidance?.width ?? 0)===(f.peelAvoidance?.width ?? 0) && (previous.peelAvoidance?.height ?? 0)===(f.peelAvoidance?.height ?? 0)) return;
    const p=Math.max(0,Math.min(1,f.progress));
    const b=peelSurfaceBounds(p,f.sourceX,f.sourceY,f.sourceSize,f.reduced,width,height,radius);
    const optics=playerCardOptics(f.tiltX,f.tiltY,f.catchStrength ?? 0,b.width,b.height,f.reduced || p<=0.001);
    uniforms.set({ bounds: [b.x,b.y,b.width,b.height],
      shape: [b.radius,foilStart/height,p,f.reduced ? 1 : 0],
      light: [f.reduced || p<=0.001 ? 0 : Math.max(-1,Math.min(1,f.tiltX/6)),
        f.reduced || p<=0.001 ? 0 : Math.max(-1,Math.min(1,f.tiltY/6))],
      catchStrength: optics.openPulse, opticalStrength: optics.opticalStrength,
      peelCurl: peelFormation(p,f.reduced).curl,
      peelAvoidance: [f.peelAvoidance?.x ?? 0,f.peelAvoidance?.y ?? 0,
        f.peelAvoidance?.width ?? 0,f.peelAvoidance?.height ?? 0] });
  }, [width,height,radius,foilStart,targetX,uniforms]);
  // No clock: active=false preserves the current optical pose instead of clearing it.
  if (!PLAYER_CARD_EFFECT) return <PearlFallback uniforms={uniforms} />;
  return <Canvas pointerEvents="none" accessible={false} style={CANVAS_STYLE}>
    <Fill><Shader source={PLAYER_CARD_EFFECT} uniforms={uniforms} /></Fill>
  </Canvas>;
});
