import { Image } from 'react-native';
import { AlphaType, ColorType, Skia, type SkImage } from '@shopify/react-native-skia';
import { lumaRunStudioAssets as assets } from './assets';
import { knots, type Bitmap } from './motion';

export type BaseResources = { canonical:SkImage; frame:SkImage; mask:SkImage };
export type Resources = BaseResources & { loop:Bitmap[]; rgba:Uint8Array };
let loading:Promise<Resources>|undefined;
let base:BaseResources|undefined;
const listeners:((base:BaseResources)=>void)[]=[];
export function resources(onBase?:(base:BaseResources)=>void):Promise<Resources> {
  if(onBase){if(base)onBase(base);else listeners.push(onBase);}
  if(loading)return loading;
  loading=(async()=>{
    const decoded=new Map<number,SkImage>();
    const load=async(id:number)=>{const old=decoded.get(id);if(old)return old;const data=await Skia.Data.fromURI(Image.resolveAssetSource(id).uri);const image=Skia.Image.MakeImageFromEncoded(data);data.dispose();if(!image)throw Error('Local canyon image could not be decoded');decoded.set(id,image);return image;};
    const canonical=await load(assets.canonical),frame=await load(assets.frame),mask=await load(assets.mask);
    base={canonical,frame,mask};for(const listener of listeners)listener(base);listeners.length=0;
    const loop:Bitmap[]=[];for(let i=0;i<assets.loop.length;i++)loop.push({image:await load(assets.loop[i]),params:knots[i].params});
    const pixels=canonical.readPixels(0,0,{width:288,height:330,colorType:ColorType.RGBA_8888,alphaType:AlphaType.Unpremul});
    if(!(pixels instanceof Uint8Array)||pixels.length!==288*330*4)throw Error('Local canyon pixels are unavailable');
    return {canonical,frame,mask,loop,rgba:pixels};
  })();return loading;
}
