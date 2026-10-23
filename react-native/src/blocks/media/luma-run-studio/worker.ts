import { AlphaType, ColorType, Skia, type SkImage } from '@shopify/react-native-skia';
import { key, quantized, type Bitmap, type Params } from './motion';

const W=288,H=330,N=W*H;
// Immediate turns yield to queued intents without paying a display-frame timer per scanline batch.
const pause=()=>new Promise<void>(resolve=>setImmediate(resolve));
type Job={generation:number;params:Params;recovery?:Params[]};
/** One serial, cancellable sorter. Only the latest manual intent is retained. */
export class LumaRunStudioWorker {
  private generation=0;private sequence=0;private running=false;private closed=false;
  private pending:Job|null=null;private priority:Job|null=null;private lastStart=0;
  private timer:ReturnType<typeof setTimeout>|null=null;
  private readonly luminance=new Float64Array(N);
  private readonly mapping=new Int32Array(N);
  private readonly order=new Int32Array(Math.max(W,H));
  private readonly rank=new Int32Array(Math.max(W,H));
  private readonly temp=new Int32Array(Math.max(W,H));
  private readonly cache=new Map<string,Bitmap>();
  onImage:(generation:number,sequence:number,bitmap:Bitmap)=>void=()=>{};
  onRecovery:(generation:number,bitmaps:Bitmap[])=>void=()=>{};
  constructor(private readonly rgba:Uint8Array,private readonly original:SkImage){
    const linear=new Float64Array(256);for(let i=0;i<256;i++){const v=i/255;linear[i]=v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    for(let i=0;i<N;i++)this.luminance[i]=.2126*linear[rgba[i*4]]+.7152*linear[rgba[i*4+1]]+.0722*linear[rgba[i*4+2]];
  }
  invalidate(generation:number){this.generation=generation;this.pending=null;this.priority=null;if(this.timer){clearTimeout(this.timer);this.timer=null;}}
  request(params:Params,generation:number){if(this.closed||generation!==this.generation)return;this.pending={params:quantized(params),generation};this.pump();}
  recovery(params:Params[],generation:number){if(this.closed||generation!==this.generation)return;this.priority={params:params[0],recovery:params,generation};this.pending=null;this.pump();}
  close(){this.closed=true;this.invalidate(this.generation+1);this.cache.clear();}
  private valid(g:number){return !this.closed&&g===this.generation;}
  private pump(){
    if(this.running||this.closed)return;
    const job=this.priority??this.pending;if(!job)return;
    const wait=job.recovery?0:Math.max(0,125-(performance.now()-this.lastStart));
    if(wait>0){if(!this.timer)this.timer=setTimeout(()=>{this.timer=null;this.pump();},wait);return;}
    if(job===this.priority)this.priority=null;else this.pending=null;
    this.running=true;this.lastStart=performance.now();
    void this.run(job).finally(()=>{this.running=false;this.pump();});
  }
  private async run(job:Job){
    if(job.recovery){const out:Bitmap[]=[];for(const p of job.recovery){const b=await this.sort(p,job.generation);if(!b)return;out.push(b);}if(this.valid(job.generation))this.onRecovery(job.generation,out);}
    else{const bitmap=await this.sort(job.params,job.generation);if(bitmap&&this.valid(job.generation))this.onImage(job.generation,++this.sequence,bitmap);}
  }
  private async sort(p:Params,generation:number):Promise<Bitmap|null>{
    if(!this.valid(generation))return null;if(p.q===0)return {image:this.original,params:p};
    const id=key(p),old=this.cache.get(id);if(old){this.cache.delete(id);this.cache.set(id,old);return old;}
    for(let i=0;i<N;i++)this.mapping[i]=i;
    const eligible=(index:number)=>{const x=index%W,y=Math.floor(index/W),sx=29+(x+.5)*335/W,sy=136+(y+.5)*384/H,Y=this.luminance[index];return sx>=59+p.roiX&&sx<=334+p.roiX&&sy>=178+p.roiY&&sy<=478+p.roiY&&Y>=p.low&&Y<=p.high;};
    const merge=(count:number,compare:(a:number,b:number)=>number)=>{for(let width=1;width<count;width*=2){for(let start=0;start<count;start+=width*2){let a=start,b=Math.min(start+width,count),endA=b,endB=Math.min(start+width*2,count),out=start;while(a<endA||b<endB)this.temp[out++]=b>=endB||(a<endA&&compare(this.order[a],this.order[b])<=0)?this.order[a++]:this.order[b++];}for(let i=0;i<count;i++)this.order[i]=this.temp[i];}};
    const process=(indices:number[])=>{let start=0;while(start<indices.length){if(!eligible(indices[start])){start++;continue;}let end=start+1;while(end<indices.length&&eligible(indices[end]))end++;const count=end-start;if(count>=6){for(let i=0;i<count;i++)this.order[i]=i;merge(count,(a,b)=>this.luminance[indices[start+a]]-this.luminance[indices[start+b]]||indices[start+a]-indices[start+b]);for(let i=0;i<count;i++)this.rank[this.order[i]]=i;for(let i=0;i<count;i++)this.order[i]=i;merge(count,(a,b)=>((1-p.q)*a+p.q*this.rank[a])-((1-p.q)*b+p.q*this.rank[b])||indices[start+a]-indices[start+b]);for(let i=0;i<count;i++)this.mapping[indices[start+i]]=indices[start+this.order[i]];}start=end;}};
    const a=p.angle*Math.PI/180;
    if(Math.abs(p.angle)<=45){const shifts=new Int32Array(W);let lo=Infinity,hi=-Infinity;for(let x=0;x<W;x++){shifts[x]=Math.floor(Math.tan(a)*x+.5);lo=Math.min(lo,shifts[x]);hi=Math.max(hi,shifts[x]);}for(let line=-hi;line<H-lo;line++){if(!this.valid(generation))return null;const indices:number[]=[];for(let x=0;x<W;x++){const y=line+shifts[x];if(y>=0&&y<H)indices.push(y*W+x);}process(indices);if((line+hi)%16===0)await pause();}}
    else{const cot=Math.cos(a)/Math.sin(a),shifts=new Int32Array(H);let lo=Infinity,hi=-Infinity;for(let y=0;y<H;y++){shifts[y]=Math.floor(cot*y+.5);lo=Math.min(lo,shifts[y]);hi=Math.max(hi,shifts[y]);}for(let line=-hi;line<W-lo;line++){if(!this.valid(generation))return null;const indices:number[]=[];for(let i=0;i<H;i++){const y=p.angle>=0?i:H-1-i,x=line+shifts[y];if(x>=0&&x<W)indices.push(y*W+x);}process(indices);if((line+hi)%16===0)await pause();}}
    if(!this.valid(generation))return null;
    const bytes=new Uint8Array(this.rgba.length);for(let i=0;i<N;i++){const from=this.mapping[i]*4,to=i*4;bytes[to]=this.rgba[from];bytes[to+1]=this.rgba[from+1];bytes[to+2]=this.rgba[from+2];bytes[to+3]=this.rgba[from+3];}
    const data=Skia.Data.fromBytes(bytes),image=Skia.Image.MakeImage({width:W,height:H,colorType:ColorType.RGBA_8888,alphaType:AlphaType.Premul},data,W*4);data.dispose();if(!image)throw Error('Local pixel preview could not be created');
    const bitmap={image,params:p};this.cache.set(id,bitmap);while(this.cache.size>6)this.cache.delete(this.cache.keys().next().value!);return bitmap;
  }
}
