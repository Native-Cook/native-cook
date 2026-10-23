// Measured source-frame tracks copied from NeonCardFlightMotion.swift.
export type Point = { x: number; y: number };
export type Card = { points: Point[]; visible: boolean; behindForeground: boolean; intro?: boolean; trail: Point };
export type Piece = { name: string; x: number; y: number; w: number; h: number; roll: number; dx: number; dy: number; turn: number; start: number; end: number; foreground: boolean };
export const PIECES: Piece[] = [
  {
    "name": "orange",
    "x": 286.0,
    "y": 520.0,
    "w": 150.0,
    "h": 160.0,
    "roll": -5.0,
    "dx": -5.0,
    "dy": -30.0,
    "turn": 30.0,
    "start": 2.267,
    "end": 3.12,
    "foreground": false
  },
  {
    "name": "cap",
    "x": 253.0,
    "y": 331.0,
    "w": 120.0,
    "h": 110.0,
    "roll": -3.0,
    "dx": 42.0,
    "dy": 8.0,
    "turn": 19.0,
    "start": 2.267,
    "end": 3.1,
    "foreground": false
  },
  {
    "name": "coin",
    "x": 355.0,
    "y": 383.0,
    "w": 52.0,
    "h": 52.0,
    "roll": -25.0,
    "dx": 0.0,
    "dy": -5.0,
    "turn": -80.0,
    "start": 2.3,
    "end": 3.12,
    "foreground": false
  },
  {
    "name": "coin",
    "x": 341.0,
    "y": 413.0,
    "w": 30.0,
    "h": 30.0,
    "roll": -15.0,
    "dx": 0.0,
    "dy": -5.0,
    "turn": 70.0,
    "start": 2.35,
    "end": 3.18,
    "foreground": false
  },
  {
    "name": "cup",
    "x": 134.0,
    "y": 370.0,
    "w": 174.0,
    "h": 180.0,
    "roll": -6.0,
    "dx": 0.0,
    "dy": -10.0,
    "turn": 25.0,
    "start": 2.233,
    "end": 2.95,
    "foreground": false
  },
  {
    "name": "star",
    "x": 51.0,
    "y": 396.0,
    "w": 110.0,
    "h": 110.0,
    "roll": -8.0,
    "dx": -8.0,
    "dy": 4.0,
    "turn": -48.0,
    "start": 3.017,
    "end": 3.2,
    "foreground": false
  },
  {
    "name": "links",
    "x": 217.0,
    "y": 480.0,
    "w": 230.0,
    "h": 310.0,
    "roll": 0.0,
    "dx": -12.0,
    "dy": 34.0,
    "turn": -8.0,
    "start": 2.25,
    "end": 3.2,
    "foreground": true
  },
  {
    "name": "headphones",
    "x": 92.0,
    "y": 520.0,
    "w": 140.0,
    "h": 150.0,
    "roll": -20.0,
    "dx": -22.0,
    "dy": 12.0,
    "turn": 32.0,
    "start": 2.267,
    "end": 3.12,
    "foreground": true
  },
  {
    "name": "locks",
    "x": 295.0,
    "y": 535.0,
    "w": 125.0,
    "h": 155.0,
    "roll": 0.0,
    "dx": 0.0,
    "dy": -30.0,
    "turn": -28.0,
    "start": 2.267,
    "end": 3.18,
    "foreground": true
  },
  {
    "name": "shield",
    "x": 179.0,
    "y": 577.0,
    "w": 140.0,
    "h": 155.0,
    "roll": -3.0,
    "dx": 5.0,
    "dy": -20.0,
    "turn": 32.0,
    "start": 2.3,
    "end": 3.18,
    "foreground": true
  }
];
export const RUSH: number[][] = [[0, 110, 414, 284, 414, 374, 566, 49, 578], [0.1667, 113, 411, 301, 414, 423, 632, 32, 619], [0.3333, 105, 414, 314, 414, 451, 667, 20, 667], [0.5, 108, 414, 327, 414, 475, 692, 6, 690], [0.6667, 102, 420, 333, 414, 500, 718, -22, 710], [0.8333, 102, 417, 350, 414, 535, 745, -40, 731], [1, 93, 417, 338, 414, 575, 783, -55, 772], [1.1667, 84, 417, 334, 414, 625, 840, -70, 833], [1.3333, 70, 417, 340, 414, 658, 882, -88, 880], [1.5, 47, 414, 350, 415, 690, 920, -110, 915], [1.6667, 25, 409, 357, 409, 725, 960, -135, 955], [1.8833, 2, 391, 339, 391, 765, 1015, -180, 1015]];
export const LEFT: number[][] = [[1.9, 359, 376, 497, 413, 419, 564, 282, 509], [1.9167, 359, 376, 497, 413, 419, 564, 282, 509], [1.9333, 359, 376, 497, 413, 419, 564, 282, 509], [1.95, 285, 347, 423, 394, 354, 544, 218, 480], [1.9833, 246, 338, 383, 373, 325, 524, 189, 477], [2.0167, 244, 338, 380, 374, 320, 524, 189, 477], [2.05, 178, 327, 298, 350, 242, 498, 131, 461], [2.0833, 99, 312, 194, 328, 150, 465, 52, 439], [2.1167, 17, 300, 89, 309, 42, 439, -24, 429], [2.167, -180, 280, -58, 290, -102, 421, -218, 410]];
export const RIGHT: number[][] = [[2.266666667, -100, 312, 39, 389, -70, 459, -209, 382], [2.283333333, -100, 312, 39, 389, -70, 459, -209, 382], [2.3, -70, 320, 66, 397, -20, 499, -156, 422], [2.3333, -70, 320, 66, 397, -20, 499, -156, 422], [2.4167, 26, 376, 127, 420, 83, 508, -20, 464], [2.5, 112, 411, 204, 443, 169, 536, 77, 504], [2.5833, 223, 435, 322, 447, 310, 552, 210, 537], [2.6667, 272, 444, 369, 449, 366, 544, 273, 540], [2.75, 296, 449, 421, 446, 424, 544, 300, 546], [2.867, 435, 449, 563, 446, 566, 544, 439, 546]];
export const RETURNING: number[][] = [[3.45, 455, 444, 190, 126, 8, 12], [3.5, 430, 444, 190, 126, 7, 10], [3.6, 393, 444, 190, 125, 6, 8], [3.7, 365, 444, 190, 123, 5, 6], [3.8, 333, 444, 190, 121, 4, 4], [3.9, 294, 444, 190, 120, 3, 2], [4, 277, 443, 190, 119, 2.6, 1], [4.1, 257, 442, 188, 118, 2, 0], [4.2, 248, 442, 187, 117, 1.8, -1], [4.3, 230, 442, 184, 116, 1.4, -2], [4.4, 223, 442, 181, 115, 1, -3], [4.5, 215, 442, 178, 114, 0.8, -4], [4.6, 211, 443, 176, 113, 0.6, -4], [4.7, 207, 444, 175, 112, 0.5, -4], [4.9, 207, 444, 175, 113, 0.5, -4], [5.1, 207, 445, 176, 114, 0.5, -4], [5.3, 208, 447, 182, 117, 0.5, -4], [5.5, 208, 449, 189, 121, 0.5, -4], [5.583, 208, 449, 189, 121, 0.5, -4]];
export const ACCENT: number[][] = [[2.4, -20, 620, -6], [2.8, 105, 615, -12], [3.1, 180, 600, -20], [3.45, 270, 540, -30], [3.7, 330, 472, -45], [4.1, 354, 400, -52], [4.7, 377, 340, -40], [5.4, 385, 340, -35]];

export function seg(t: number, a: number, b: number) { 'worklet'; return Math.min(1, Math.max(0, (t-a)/(b-a))); }
export function ease(p: number) { 'worklet'; return p*p*(3-2*p); }
export function out(p: number) { 'worklet'; return 1-Math.pow(1-p,3); }
export function track(t: number, keys: number[][], column: number): number {
  'worklet'; if(t<=keys[0][0]) return keys[0][column]; if(t>=keys[keys.length-1][0]) return keys[keys.length-1][column];
  let i=0; while(i+1<keys.length && keys[i+1][0]<t) i++;
  const a=keys[i],b=keys[i+1],left=keys[Math.max(0,i-1)],right=keys[Math.min(keys.length-1,i+2)];
  const dt=b[0]-a[0],u=(t-a[0])/dt,m0=(b[column]-left[column])/(b[0]-left[0])*dt,m1=(right[column]-a[column])/(right[0]-a[0])*dt;
  return (2*u*u*u-3*u*u+1)*a[column]+(u*u*u-2*u*u+u)*m0+(-2*u*u*u+3*u*u)*b[column]+(u*u*u-u*u)*m1;
}
function points(s: number, keys: number[][]): Point[] { 'worklet'; return [0,1,2,3].map(i=>({x:track(s,keys,i*2+1),y:track(s,keys,i*2+2)})); }
function quad(cx: number,cy: number,width: number,height: number,roll: number,yaw: number): Point[] {
  'worklet'; const a=roll*Math.PI/180,b=yaw*Math.PI/180;
  return [[-.5,-.5],[.5,-.5],[.5,.5],[-.5,.5]].map(([x,y])=>{
    const lx=x*width,perspective=620/(620+lx*Math.sin(b)),px=lx*Math.cos(b)*perspective,py=y*height*perspective;
    return {x:cx+px*Math.cos(a)-py*Math.sin(a),y:cy+px*Math.sin(a)+py*Math.cos(a)};
  });
}
export function cardPose(t: number): Card {
  'worklet'; const s=Math.max(t-.65,0),zero={x:0,y:0};
  if(s<1.9-1e-9) return {points:points(s,RUSH),intro:true,visible:true,behindForeground:false,trail:zero};
  let keys: number[][],behind: boolean;
  if(s<=2.167){keys=LEFT;behind=false;}
  else if(s>=2.266666667-1e-9&&s<2.867-1e-9){keys=RIGHT;behind=true;}
  else if(s<3.45-1e-9)return {points:[],visible:false,behindForeground:false,trail:zero};
  else return {points:quad(track(s,RETURNING,1),track(s,RETURNING,2),track(s,RETURNING,3),track(s,RETURNING,4),track(s,RETURNING,5),track(s,RETURNING,6)),visible:true,behindForeground:false,trail:zero};
  const p=points(s,keys),previous=points(Math.max(keys[0][0],s-.006),keys);
  let dx=0,dy=0;for(let i=0;i<4;i++){dx+=(p[i].x-previous[i].x)/4;dy+=(p[i].y-previous[i].y)/4;}
  const length=Math.hypot(dx,dy);if(length>5){dx*=5/length;dy*=5/length;}
  return {points:p,visible:true,behindForeground:behind,trail:{x:dx,y:dy}};
}
export function piecePose(piece: Piece,t: number){
  'worklet'; const s=Math.max(t-.65,0),p=out(seg(s,piece.start,piece.end));
  let v=ease(seg(s,2.42,3.18)),sx=1,sy=1,shear=0,axis=0,pivotY=0;
  switch(piece.name){
    case 'links':v=ease(seg(s,2.45,3.15));sx=.28+.72*v;sy=1.14-.14*v;axis=-55;pivotY=-piece.h*.18;shear=-.12*(1-v);break;
    case 'headphones':sx=.4+.6*v;sy=.85+.15*v;shear=.18*(1-v);break;
    case 'locks':sx=1.15-.15*v;sy=.22+.78*v;shear=.35*(1-v);pivotY=-piece.h*.1;break;
    case 'shield':sx=.26+.74*v;sy=1.06-.06*v;shear=.22*(1-v);break;
    case 'cup':sx=.88+.12*v;sy=.8+.2*v;shear=-.2*(1-v);pivotY=-piece.h*.1;break;
    case 'cap':sy=.28+.72*v;shear=.25*(1-v);break;
    case 'star':v=ease(seg(s,3.05,3.18));sx=.14+.86*v;shear=.1*(1-v);break;
    case 'orange':sy=.65+.35*v;shear=-.1*(1-v);break;
    case 'coin':{const angle=s>3.18?(s-3.18)*2*Math.PI/1.45+piece.y/35:1.4*(1-v);sx=.2+.8*Math.abs(Math.cos(angle));break;}
  }
  const angle=axis*Math.PI/180,co=Math.cos(angle),si=Math.sin(angle);
  const a=sx*co*co+sy*si*si,b=(sx-sy)*si*co,c=b+shear,d=sx*si*si+sy*co*co;
  const life=s>3.2?Math.sin((s-3.2)*2*Math.PI/2.4+piece.x/80):0;
  const drift=piece.name==='cup'?track(s,[[3.2,0],[4.7,15],[4.9,15],[5.5,6]],1)*p:0;
  return {x:piece.x+piece.dx*(1-p),y:piece.y+piece.dy*(1-p)+drift,scale:.001+.999*Math.pow(p,1.55),roll:piece.roll+piece.turn*(1-p)+life*.6,alpha:p>0?1:0,a,b,c,d,tx:-c*pivotY,ty:(1-d)*pivotY};
}
