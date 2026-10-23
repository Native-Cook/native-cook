import { FilterMode,MipmapMode,Skia,TileMode,useImage } from '@shopify/react-native-skia';
import { useMemo } from 'react';
export const shaderSource=`uniform shader scene; uniform float yaw; uniform float pitch;
half4 main(float2 p){float y=radians(yaw),t=radians(pitch);float3 f=float3(sin(y)*cos(t),sin(t),cos(y)*cos(t));float3 r=float3(cos(y),0,-sin(y));float3 u=float3(-sin(y)*sin(t),cos(t),-cos(y)*sin(t));float3 ray=normalize(f+r*((p.x-196.5)/510.0)-u*((p.y-426.0)/510.0));float2 uv=float2(fract(.5+atan(ray.x,ray.z)/6.28318530718),clamp((1.57079632679-asin(clamp(ray.y,-1.0,1.0)))/2.61799387799,0.0,1.0));return scene.eval(uv*float2(2048,1024));}`;
const effect=Skia.RuntimeEffect.Make(shaderSource);
export function useWorldSunResources(){const image=useImage(require('./assets/scene.png'));return useMemo(()=>{if(!image)return null;if(!effect)throw new Error('World Sun Projection shader failed');return{image,effect,shader:image.makeShaderOptions(TileMode.Clamp,TileMode.Clamp,FilterMode.Linear,MipmapMode.None)};},[image]);}
export type Resources=NonNullable<ReturnType<typeof useWorldSunResources>>;
