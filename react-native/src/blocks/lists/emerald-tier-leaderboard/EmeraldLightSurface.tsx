import { Canvas, Fill, ImageFormat, Shader, Skia, type SkImage, type SkPaint, type SkShader, type SkSurface, type Uniforms } from '@shopify/react-native-skia';
import { memo, useEffect, useState } from 'react';
import { Image, PixelRatio } from 'react-native';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import type { EmeraldFrame } from './motion';

/** Inky upper body, a contained lower light reservoir, and a thin refractive edge. */
export const EMERALD_LIGHT_GLASS = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float radius;
uniform float light;
uniform float response;
uniform float phase;
float pool(float2 p) { return exp(-dot(p,p)); }
half4 main(float2 p) {
  float2 uv=p/size;
  float r=min(radius,min(size.x,size.y)*.5);
  float2 q=abs(p-size*.5)-(size*.5-r);
  float edgeDistance=length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
  float coverage=1.0-smoothstep(-.65,.65,edgeDistance);

  // Light diffuses upward from below the lower lip; the upper body stays dark.
  float reservoir=smoothstep(.38,.90,uv.y);
  float rightPool=pool((uv-float2(.90,1.06))/float2(.43,.35));
  float leftPool=pool((uv-float2(.16,1.10))/float2(.55,.32));
  float depth=pool((uv-float2(.12,-.24))/float2(.85,.90));
  float3 color=float3(.018,.035,.037)+float3(.012,.019,.025)*depth;
  float energy=light*(1.0+response*.20);
  color+=reservoir*energy*(float3(.075,.40,.42)*leftPool+float3(.42,.83,.63)*rightPool);

  // Fine monochrome grain stays static instead of crawling with the frame clock.
  float grain=fract(sin(dot(floor(p*1.7),float2(12.9898,78.233)))*43758.5453)-.5;
  color+=grain*.012*(.45+.55*reservoir);

  float rim=1.0-smoothstep(.10,1.0,max(0.0,-edgeDistance));
  float lower=smoothstep(.60,1.0,uv.y);
  float rimPower=.075+.10*(1.0-uv.x)+lower*(.11+.46*rightPool)*light;
  color+=float3(.64,.91,.85)*rim*rimPower;

  // One reflection answers the existing crown contact; no independent VFX clock.
  float band=uv.x+uv.y*.25-mix(-.30,1.55,phase);
  float reflection=exp(-band*band/.008);
  color+=float3(.18,.32,.23)*reflection*response*light*(.25+.75*reservoir);
  float alpha=coverage*.985;
  return half4(clamp(color,0.0,1.0)*alpha,alpha);
}`);

type SurfaceSize = { width: number; height: number; radius: number; light: number };
const STATIC_CACHE_LIMIT = 12;
const staticSurfaces = new Map<string, string>();

function cachedSurface(key: string) {
  const uri = staticSurfaces.get(key);
  if (uri) { staticSurfaces.delete(key); staticSurfaces.set(key, uri); }
  return uri;
}

function renderStaticSurface({ width, height, radius, light }: SurfaceSize, density: number) {
  if (!EMERALD_LIGHT_GLASS || ![width, height, radius, light, density].every(Number.isFinite) || width <= 0 || height <= 0 || density <= 0) return null;
  let surface: SkSurface | null = null;
  let paint: SkPaint | null = null;
  let shader: SkShader | null = null;
  let image: SkImage | null = null;
  try {
    // Match the live canvas's point coordinates, including its static grain, at retina density.
    surface = Skia.Surface.Make(Math.max(1, Math.round(width * density)), Math.max(1, Math.round(height * density)));
    if (!surface) return null;
    shader = EMERALD_LIGHT_GLASS.makeShader([width, height, radius, light, 0, 0]);
    paint = Skia.Paint();
    paint.setShader(shader);
    const canvas = surface.getCanvas();
    canvas.scale(density, density);
    canvas.drawPaint(paint);
    surface.flush();
    image = surface.makeImageSnapshot();
    const png = image.encodeToBase64(ImageFormat.PNG);
    return png ? `data:image/png;base64,${png}` : null;
  } catch { return null; }
  finally {
    // Only the encoded string reaches Image; no view retains these scratch objects.
    image?.dispose(); paint?.dispose(); shader?.dispose(); surface?.dispose();
  }
}

const StaticLightSurface = memo(function StaticLightSurface({ width, height, radius, light }: SurfaceSize) {
  const density = Math.min(3, PixelRatio.get());
  const key = JSON.stringify([width, height, radius, light, density]);
  const [entry, setEntry] = useState<{ key: string; uri: string } | null>(() => {
    const uri = cachedSurface(key);
    return uri ? { key, uri } : null;
  });
  useEffect(() => {
    const task = setTimeout(() => {
      const uri = cachedSurface(key) ?? renderStaticSurface({ width, height, radius, light }, density);
      if (uri) {
        staticSurfaces.set(key, uri);
        while (staticSurfaces.size > STATIC_CACHE_LIMIT) staticSurfaces.delete(staticSurfaces.keys().next().value!);
      }
      setEntry(current => current?.key === key && current.uri === uri ? current : uri ? { key, uri } : null);
    }, 0);
    return () => clearTimeout(task);
  }, [density, height, key, light, radius, width]);
  // The parent ink remains visible during setup, decoding or a failed render.
  if (!entry || entry.key !== key) return null;
  return <Image source={{ uri: entry.uri, scale: density }} style={{ width, height }} resizeMode="stretch" fadeDuration={0} accessible={false}
    onError={() => {
      staticSurfaces.delete(key);
      setEntry(current => current?.key === key ? null : current);
    }} />;
});

function SurfaceCanvas({ width, height, uniforms }: { width: number; height: number; uniforms: Uniforms | SharedValue<Uniforms> }) {
  if (!EMERALD_LIGHT_GLASS) return null;
  return <Canvas style={{ width, height }} pointerEvents="none" accessible={false}>
    <Fill><Shader source={EMERALD_LIGHT_GLASS} uniforms={uniforms} /></Fill>
  </Canvas>;
}

function ReactiveLightSurface({ width, height, radius, light, frame }: SurfaceSize & { frame: SharedValue<EmeraldFrame> }) {
  const uniforms = useDerivedValue<Uniforms>(() => {
    const current = frame.get();
    return { size: [width, height], radius, light, response: current.response, phase: current.responsePhase };
  });
  return <SurfaceCanvas width={width} height={height} uniforms={uniforms} />;
}

export function EmeraldLightSurface({ frame, ...size }: SurfaceSize & { frame?: SharedValue<EmeraldFrame> }) {
  // Only the winning plinth subscribes to rank motion. The other surfaces are static.
  return frame ? <ReactiveLightSurface {...size} frame={frame} />
    : <StaticLightSurface {...size} />;
}
