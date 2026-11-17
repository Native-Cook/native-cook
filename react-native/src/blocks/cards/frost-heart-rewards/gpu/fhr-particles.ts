// TypeGPU owns the uniform/storage layout; WebGPU intersects the exported Blender facet planes.
import { d } from 'typegpu';
import gem from '../assets/atelier-gem.json';
import { AMETHYST_MATRIX, ART, BEAR_MATRIX } from '../fhr-art';
export const SECTOR_COUNT = gem.pieces.length;
export const DISPLAY_PIECES = 12;
export const COUNT = SECTOR_COUNT + 53;
export const WORKGROUP = 64;
// Rasterized evaluated mesh supplies native-density MSAA coverage independently of optics.
export const coverageVertices: number[] = [];
for (let id = 0; id < gem.pieces.length; id++) {
  const piece = gem.pieces[id];
  for (const face of piece.faces) {
    for (let j = 1; j < face.indices.length - 1; j++) {
      for (const index of [face.indices[0], face.indices[j], face.indices[j + 1]]) {
        coverageVertices.push(...piece.vertices[index], id);
      }
    }
  }
}
export const COVERAGE_VERTICES = coverageVertices.length / 4;
export const Params = d.struct({ view:d.vec4f, placement:d.vec4f, state:d.vec4f, effects:d.vec4f, claims:d.vec4f, manual:d.vec4f, light:d.vec4f, backdrop:d.vec4f, viewport:d.vec4f, crystalViewport:d.vec4f, crystalRaster:d.vec4f, breath:d.vec4f });
export const PARAM_FLOATS = d.sizeOf(Params) / 4;
export const MAX_PLANES = 11;
export const Piece = d.struct({ center:d.vec4f, pivot:d.vec4f, caps:d.vec4u, planes:d.arrayOf(d.vec4f, MAX_PLANES) });
export function pieceValues() {
  return gem.pieces.map((p, index) => {
    const sector = p as typeof p & { displayCentroid?: number[]; exteriorFaceCount?: number };
    const pivot = sector.displayCentroid ?? p.centroid;
    const partner = gem.pieces[index ^ 1];
    const exteriorCount = sector.exteriorFaceCount ?? 3;
    let displayMask = 0;
    p.faces.forEach((face, j) => {
      const shared = j >= exteriorCount && partner.faces.some((other, k) => k >= (partner.exteriorFaceCount ?? 3) &&
        Math.hypot(...face.normal.map((n, axis) => n + other.normal[axis])) < 1e-5 && Math.abs(face.offset + other.offset) < 1e-5);
      if (!shared) displayMask |= 1 << j;
    });
    const radius = Math.max(...p.vertices.map(v=>Math.hypot(v[0]-p.centroid[0],v[1]-p.centroid[1],v[2]-p.centroid[2])));
    if(p.faces.length>MAX_PLANES) throw new Error('Atelier piece exceeds facet budget');
    return {center:d.vec4f(p.centroid[0],p.centroid[1],p.centroid[2],radius),pivot:d.vec4f(pivot[0],pivot[1],pivot[2],exteriorCount),caps:d.vec4u(displayMask,p.faces.length,0,0),planes:Array.from({length:MAX_PLANES},(_,i)=>i<p.faces.length?d.vec4f(...p.faces[i].normal as [number,number,number],p.faces[i].offset):d.vec4f(0,0,0,100))};
  });
}
const PORTALS = Array.from({ length: DISPLAY_PIECES }, (_, group) => {
  const vertices = [...gem.pieces[group * 2].vertices, ...gem.pieces[group * 2 + 1].vertices];
  const outer = vertices.reduce((a, b) => Math.hypot(b[0] - .15, b[1] + .1) > Math.hypot(a[0] - .15, a[1] + .1) ? b : a);
  return `vec2f(${175 + outer[0] * 105},${278 - outer[1] * 105})`;
}).join(',');
export function drawWGSL() { return `
struct Params {view:vec4f,placement:vec4f,state:vec4f,effects:vec4f,claims:vec4f,manual:vec4f,light:vec4f,backdrop:vec4f,viewport:vec4f,crystalViewport:vec4f,crystalRaster:vec4f,breath:vec4f};
struct Piece {center:vec4f,pivot:vec4f,caps:vec4u,planes:array<vec4f,11>};
const SECTORS:u32=${SECTOR_COUNT}u;
@group(0) @binding(0) var<uniform> P:Params;
@group(0) @binding(1) var<storage,read> pieces:array<Piece>;
@group(0) @binding(2) var bearTexture:texture_2d<f32>;
@group(0) @binding(3) var hugTexture:texture_2d<f32>;
@group(0) @binding(4) var pageTexture:texture_2d<f32>;
@group(0) @binding(5) var imageSampler:sampler;
@group(0) @binding(6) var crystalTexture:texture_2d<f32>;
struct Glints {points:array<vec4f,3>};
@group(0) @binding(7) var<storage,read_write> glints:Glints;
@group(0) @binding(8) var skinTexture:texture_2d<f32>;
@group(0) @binding(9) var roundedTexture:texture_2d<f32>;
@group(0) @binding(10) var materialTexture:texture_2d<f32>;
@group(0) @binding(11) var coverageTexture:texture_2d<f32>;
@group(0) @binding(12) var cutTexture:texture_2d<f32>;
const PI:f32=3.141592654;
fn sat(x:f32)->f32{return clamp(x,0.,1.);}
fn phase(t:f32,a:f32,b:f32)->f32{return sat((t-a)/(b-a));}
fn fadePhase(t:f32,a:f32,b:f32)->f32{let u=phase(t,a,b);return u*u*(3.-2.*u);}
fn easing(x:f32)->f32 {let u=sat(x);return select(4.*u*u*u,1.-pow(-2.*u+2.,3.)*.5,u>.5);}
fn rotX(p:vec3f,a:f32)->vec3f{return vec3f(p.x,p.y*cos(a)-p.z*sin(a),p.y*sin(a)+p.z*cos(a));}
fn rotY(p:vec3f,a:f32)->vec3f{return vec3f(p.x*cos(a)+p.z*sin(a),p.y,-p.x*sin(a)+p.z*cos(a));}
fn rotZ(p:vec3f,a:f32)->vec3f{return vec3f(p.x*cos(a)-p.y*sin(a),p.x*sin(a)+p.y*cos(a),p.z);}
fn forward(p:vec3f)->vec3f{return rotY(rotX(p,P.placement.y),P.placement.z);}
fn inverse(p:vec3f)->vec3f{return rotX(rotY(p,-P.placement.z),-P.placement.y);}
fn spread(id:u32)->f32 {
 let t=P.view.w;
 if(t<5.4){return P.state.y;}
 let age=max(0.,t-5.4-f32(id/2u)*.04);
 return exp(-14.*age)*(cos(10.*age)+1.4*sin(10.*age));
}
fn offset(id:u32)->vec3f {
 let c=pieces[id].pivot.xyz;
 let direction=normalize(vec3f(c.xy*.9,c.z*.42+.04));
 return direction*min(.16,max(-.025,spread(id))*.16);
}
fn spin(id:u32)->f32{let group=id/2u;return spread(id)*(.055+f32(group%3u)*.018)*select(-1.,1.,group%2u==0u);}
fn toLocal(p:vec3f,id:u32)->vec3f {let c=pieces[id].pivot.xyz;return rotX(rotZ(rotY(inverse(p)-offset(id)-c,-spin(id)),-spin(id)*.4),-spin(id)*.6)+c;}
fn worldNormal(n:vec3f,id:u32)->vec3f{return forward(rotY(rotZ(rotX(n,spin(id)*.6),spin(id)*.4),spin(id)));}
struct Hit {near:f32,far:f32,normal:vec3f,exitNormal:vec3f,edge:f32,enterFace:u32,exitFace:u32};
fn intersect(ro:vec3f,rd:vec3f,id:u32)->Hit {
 // Conservative exported bounding spheres reject distant sectors before plane clipping.
 // This preserves every facet/ray while avoiding most of the concave-union plane work.
 let relative=ro-pieces[id].center.xyz;let projected=dot(relative,rd);
 let radius=pieces[id].center.w+.002;let outside=dot(relative,relative)-radius*radius;
 if(projected*projected-outside<-.0001 || (projected>0.&&outside>.0001)){
  return Hit(-10000.,-10000.,vec3f(0.,0.,1.),vec3f(0.,0.,-1.),10000.,0u,0u);
 }
 var lo:f32=-10000.;var hi:f32=10000.;var enter=vec3f(0.,0.,1.);var exit=vec3f(0.,0.,-1.);var edge:f32=10000.;var enterFace:u32=0u;var exitFace:u32=0u;
 // Radial caps reject rays outside this narrow wedge before the crown/pavilion.
 // Keep original face IDs so exterior-union and display-cap decisions are unchanged.
 let count=pieces[id].caps.y;
 for(var k:u32=0u;k<11u;k++){
  if(k>=count){break;}
  var j=k-2u;if(k<2u){j=count-2u+k;}
  let plane=pieces[id].planes[j];let den=dot(plane.xyz,rd);let dist=plane.w-dot(plane.xyz,ro);
  if(abs(den)<.00001){if(dist<0.){hi=-10000.;}}else{
   let a=dist/den;
   if(den<0.){if(a>lo){edge=abs(a-lo);lo=a;enter=plane.xyz;enterFace=j;}else{edge=min(edge,abs(a-lo));}}
   else{if(a<hi){hi=a;exit=plane.xyz;exitFace=j;}}
  }
  if(hi<lo){break;}
 }
 return Hit(lo,hi,enter,exit,edge,enterFace,exitFace);
}
fn assembled()->bool{return P.state.y<.004&&(P.view.w<=3.9||P.view.w>=6.35);}
// Exterior planes precede the radial partition planes in each verified sector.
// Skipping radial partition exits traces the closed concave union without artificial glass seams.
fn unionHit(ro:vec3f,rd:vec3f)->Hit {
 var owner:u32=0u;var near:f32=10000.;var far:f32=10000.;var n=vec3f(0.,0.,1.);var en=vec3f(0.,0.,-1.);
 for(var k:u32=0u;k<SECTORS;k++){
  let h=intersect(ro,rd,k);
  if(h.far>=max(0.,h.near)){
   if(h.enterFace<u32(pieces[k].pivot.w)&&h.near>0.&&h.near<near){near=h.near;n=h.normal;owner=k;}
   if(h.exitFace<u32(pieces[k].pivot.w)&&h.far>0.&&h.far<far){far=h.far;en=h.exitNormal;}
  }
 }
 return Hit(near,far,n,en,10000.,owner,0u);
}
// Two convex sectors form one thick display piece. Their shared radial cap is
// transparent to the ray and never becomes an internal mirror or a bevel line.
fn groupHit(ro:vec3f,rd:vec3f,id:u32)->Hit {
 let base=(id/2u)*2u;
 var owner=base;var near:f32=10000.;var far:f32=10000.;
 var normal=vec3f(0.,0.,1.);var exit=vec3f(0.,0.,-1.);
 for(var k=base;k<base+2u;k++){
  let h=intersect(ro,rd,k);
  if(h.far>=max(0.,h.near)){
   let mask=pieces[k].caps.x;
   if((mask&(1u<<h.enterFace))!=0u&&h.near>0.&&h.near<near){near=h.near;normal=h.normal;owner=k;}
   if((mask&(1u<<h.exitFace))!=0u&&h.far>0.&&h.far<far){far=h.far;exit=h.exitNormal;}
  }
 }
 return Hit(near,far,normal,exit,10000.,owner,0u);
}
fn trace(ro:vec3f,rd:vec3f,id:u32)->Hit {if(assembled()){return unionHit(ro,rd);}return groupHit(ro,rd,id);}
fn opticalNormal(n:vec3f,id:u32)->vec3f {if(assembled()){return forward(n);}return worldNormal(n,id);}
// Directional rectangular emitters in a black studio. All emit white light;
// spectral color comes only from the three different optical paths.
fn softbox(r:vec3f,c:vec3f,size:vec2f)->f32 {
 let center=normalize(c);let right=normalize(cross(vec3f(0.,1.,0.),center));let up=cross(center,right);
 let facing=dot(r,center);let uv=vec2f(dot(r,right),dot(r,up))/max(.001,facing);
 let q=abs(uv)-size;
 // Real studio cards have a bright center and feathered falloff. A constant
 // emitter filled neighbouring crown facets with the same clipped white value.
 let aperture=(1.-smoothstep(-.014,.014,max(q.x,q.y)))*step(.01,facing);
 let local=uv/max(size,vec2f(.001));
 return aperture*(.12+.88*exp(-dot(local,local)*2.4));
}
fn pinLight(r:vec3f,c:vec3f)->f32 {
 let center=normalize(c);let right=normalize(cross(vec3f(0.,1.,0.),center));let up=cross(center,right);
 let facing=dot(r,center);let uv=vec2f(dot(r,right),dot(r,up))/max(.001,facing);
 let q=uv/.006;return pow(max(0.,1.-dot(q,q)),2.)*step(.01,facing)*12.;
}
fn environment(ray:vec3f)->vec3f {
 let r=normalize(ray);
 let key=softbox(r,vec3f(-.18,.78,.60),vec2f(.12,.055))*8.;
 // Camera-left crown rim, plus its oblique-turn companion. The black studio
 // remains black between these compact feathered strips.
 let left=softbox(r,vec3f(-.615690,-.554370,.560000),vec2f(.045,.30))*7.
  +softbox(r,vec3f(.17,-.27,.95),vec2f(.08,.25))*7.;
 let right=softbox(r,vec3f(.75,.12,.55),vec2f(.019,.12))*14.;
 let back=softbox(r,vec3f(.15,.65,-.75),vec2f(.10,.035))*7.;
 // A narrow lower reflector catches the pavilion tip. It has directional falloff,
 // so dark facets retain contrast rather than receiving a uniform ambient fill.
 let lower=softbox(r,vec3f(-.47543,-.74502,.46788),vec2f(.055,.055))*.42;
 let lowerRight=softbox(r,vec3f(.7729,-.6340,.0256),vec2f(.050,.028))*.22;
 let bounce=pow(max(0.,dot(r,normalize(vec3f(-.3,-.7,-.6)))),18.)*.025;
 // Low, directional room bounce is still traced light. A black studio does not
 // contain a zero-radiance hemisphere; the old black toe erased these gradients.
 let room=pow(max(0.,dot(r,normalize(vec3f(-.25,.8,.35))))*.5+.5,3.)*.003;
 let wall=pow(max(0.,dot(r,normalize(vec3f(.6,-.3,-.7))))*.5+.5,5.)*.018;
 // These emit white light. Only the separated final-exit IOR paths can make
 // a red/blue fleck; they never paint a spectral pattern onto the gem surface.
 let pins=pinLight(r,vec3f(.278374,-.790470,.545588))+pinLight(r,vec3f(.189659,-.940826,.280847));
 return vec3f(room)*vec3f(.90,.94,1.)+vec3f(left)*vec3f(.72,.86,1.)+vec3f(key+right+back+lower+lowerRight+pins)+mix(vec3f(1.,.12,.55),vec3f(.48,.09,1.),P.effects.z)*(bounce+wall);
}
fn worldPoint(p:vec3f,id:u32)->vec3f {
 if(assembled()){return forward(p);}
 let c=pieces[id].pivot.xyz;
 return forward(rotY(rotZ(rotX(p-c,spin(id)*.6),spin(id)*.4),spin(id))+c+offset(id));
}
fn amethyst(rgb:vec3f)->vec3f {
 return clamp(vec3f(dot(rgb,vec3f(${AMETHYST_MATRIX.slice(0,3).join(',')})),dot(rgb,vec3f(${AMETHYST_MATRIX.slice(5,8).join(',')})),dot(rgb,vec3f(${AMETHYST_MATRIX.slice(10,13).join(',')}))),vec3f(0.),vec3f(1.));
}
fn bearColour(rgb:vec3f)->vec3f {
 return clamp(rgb*vec3f(${BEAR_MATRIX[0]},${BEAR_MATRIX[6]},${BEAR_MATRIX[12]})+vec3f(${BEAR_MATRIX[4]},${BEAR_MATRIX[9]},${BEAR_MATRIX[14]}),vec3f(0.),vec3f(1.));
}
fn backdrop(screen:vec2f)->vec3f {
 let pageUV=clamp(screen/vec2f(402.,440.),vec2f(0.),vec2f(1.));
 var bg=textureSampleLevel(pageTexture,imageSampler,pageUV,0.).rgb;
 let lamp=(screen-vec2f(177.,282.))/vec2f(120.,94.);
 let lampAlpha=exp(-dot(lamp,lamp)*1.5)*sat(P.state.y)*.15;
 bg=mix(bg,vec3f(.92,.79,.87),lampAlpha);
 // Invert the exact hero push/parallax, bear squash and sprite rotation from tgfx.
 let keep=1.-P.state.w;
 var p=(screen-vec2f(175.,278.)-P.backdrop.xy*keep)/(1.+P.effects.x*keep)+vec2f(175.,278.);
 p=(p-vec2f(175.,245.))/vec2f(1.-.03*P.backdrop.z*keep,1.+.015*P.backdrop.z*keep)+vec2f(175.,245.);
 let center=vec2f(${ART.bear.x+ART.bear.width/2},${ART.bear.y+ART.bear.height/2});
 let v=p-center;let angle=-${ART.bear.rotation}.*PI/180.;
 let unrotated=vec2f(v.x*cos(angle)-v.y*sin(angle),v.x*sin(angle)+v.y*cos(angle))+center;
 let uv=(unrotated-vec2f(${ART.bear.x},${ART.bear.y}))/vec2f(${ART.bear.width},${ART.bear.height});
 if(all(uv>=vec2f(0.))&&all(uv<=vec2f(1.))){
  let rest=textureSampleLevel(bearTexture,imageSampler,uv,0.);
  let hug=textureSampleLevel(hugTexture,imageSampler,uv,0.);
  let open=sat(P.state.y)*keep;
  // Straight-alpha sprites, composed in the same order as the two native image draws.
  let a=rest.a*(1.-open)*(1.-smoothstep(292.,332.,p.y));
  let b=hug.a*open*(1.-smoothstep(310.,350.,p.y));
  var radius=mix(-24.,230.,P.effects.z*keep);
  var wave=1.-smoothstep(radius-18.,radius+18.,distance(p,vec2f(175.,278.)));
  if(P.effects.z<=0.){wave=0.;}if(P.effects.z*keep>=1.){wave=1.;}
  let restColour=bearColour(rest.rgb);let hugColour=bearColour(hug.rgb);
  let restRGB=mix(restColour,amethyst(restColour),wave);
  let hugRGB=mix(hugColour,amethyst(hugColour),wave);
  bg=mix(bg,restRGB,a);bg=mix(bg,hugRGB,b);
 }
 return pow(max(bg,vec3f(0.)),vec3f(2.2));
}
// A lit, beveled plaque on the visible table is also part of the room seen by
// reflected rays. Match the native plaque's projection; never tint whole facets.
fn plaqueReflection(wp:vec3f,wr:vec3f)->vec3f {
 if(wr.z<=.015){return vec3f(0.);}
 let travel=(.58-wp.z)/wr.z;
 if(travel<=0.||travel>4.){return vec3f(0.);}
 let q=wp+wr*travel;
 let screen=vec2f(175.,278.)+vec2f(q.x,-q.y)*105.;
 let delta=screen-vec2f(187.5,291.);let c=cos(-.36);let s=sin(-.36);
 let v=vec2f(delta.x*c+delta.y*s,-delta.x*s+delta.y*c);
 let p=abs(v)-vec2f(33.5,3.);
 let sdf=length(max(p,vec2f(0.)))+min(max(p.x,p.y),0.)-38.;
 let coverage=1.-smoothstep(-1.,3.,sdf);
 let albedo=mix(vec3f(.95,.025,.72),vec3f(.33,.004,.31),sat((v.y+41.)/82.));
 let irradiance=environment(normalize(vec3f(-.18,.78,.6)))*.12;
 return albedo*irradiance*coverage*P.state.x;
}
fn exitLight(pos:vec3f,ray:vec3f,id:u32)->vec3f {
 let wp=worldPoint(pos,id);let wr=normalize(opticalNormal(ray,id));
 var light=environment(normalize(wr+wp*vec3f(.16,.13,.08)))+plaqueReflection(wp,wr);
 if(wr.z<-.015){
  let travel=(-.65-wp.z)/wr.z;
  if(travel<0.||travel>10.){return light;}
  let q=wp+wr*travel;
  let screen=vec2f(175.,278.)+vec2f(q.x,-q.y)*105.*(1.+P.effects.x)+P.backdrop.xy*(1.-P.state.w);
  light+=backdrop(screen);
 }
 return light;
}
fn screenTransmission(pos:vec3f,ray:vec3f,n:vec3f,id:u32,ior:f32)->vec3f {
 let wp=worldPoint(pos,id);let wr=opticalNormal(refract(ray,n,1./ior),id);
 if(wr.z>=-.015){return vec3f(0.);}
 let travel=(-.65-wp.z)/wr.z;
 if(travel<0.||travel>10.){return vec3f(0.);}
 let q=wp+wr*travel;
 let point=vec2f(175.,278.)+vec2f(q.x,-q.y)*105.*(1.+P.effects.x)+P.backdrop.xy*(1.-P.state.w);
 // The pavilion forms a reduced virtual image of the nearby bear. Retain
 // its sampled colour at the lower tip instead of tracing past the finite card.
 let virtualImage=vec2f(175.,245.)+(point-vec2f(175.,278.))*.58+vec2f(wr.x,-wr.y)*90.;
 return mix(backdrop(point),backdrop(virtualImage),.65);
}
// Intrinsic transmission is 96% through the volume. Fresnel losses are separate.
// Two internal reflections when settled, three during the turn; total internal reflection is retained.
fn optical(ro:vec3f,rd:vec3f,n:vec3f,id:u32)->vec3f {
 let ior=2.42;
 var ray=refract(rd,n,1./ior);var pos=ro+ray*.0008;var colour=vec3f(0.);var energy:f32=1.;
 // One reflection for small moving pieces, two when settled. The hero's third
 // reflection fades out before hold, avoiding a material pop at exactly 7.4 s.
 let moving=P.view.w>=2.4&&P.view.w<6.35;
 let hero=fadePhase(P.view.w,6.35,6.5)*(1.-fadePhase(P.view.w,7.15,7.4));
 let maxBounces=select(select(3u,4u,hero>0.),2u,moving);
 for(var bounce:u32=0u;bounce<4u;bounce++){
  if(bounce>=maxBounces){break;}
  let h=trace(pos,ray,id);if(h.far<.00001||h.far>9999.){colour+=exitLight(pos,ray,id)*energy;energy=0.;break;}
  let hit=pos+ray*h.far;let outward=h.exitNormal;
  let leave=refract(ray,-outward,ior);
  if(dot(leave,leave)>.001){
   let f0=pow((ior-1.)/(ior+1.),2.);let fres=f0+(1.-f0)*pow(1.-abs(dot(ray,outward)),5.);
   // This transmitted branch terminates at its first successful exit. Split
   // wavelengths here only; all preceding TIR plane tests share the green path.
   let r=refract(ray,-outward,2.40);let b=refract(ray,-outward,2.46);
   var light=vec3f(0.,exitLight(hit,leave,id).g,0.);
   if(dot(r,r)>.001){light.r=exitLight(hit,r,id).r;}
   if(dot(b,b)>.001){light.b=exitLight(hit,b,id).b;}
   let weight=select(1.,hero,bounce==3u);
   colour+=light*energy*(1.-fres)*weight;
   energy*=1.-(1.-fres)*weight;
   break;
  }
  if(bounce+1u>=maxBounces||energy<.012){break;}
  ray=reflect(ray,outward);pos=hit+ray*.001;
 }
 // Budget exhaustion must not convert surviving TIR energy to opaque black.
 // Continue it with the existing traced screen lobe and directional studio tail.
 let tail=screenTransmission(ro,rd,n,id,ior)+environment(normalize(opticalNormal(ray,id)));
 return (colour+tail*energy*.78)*.96;
}
fn filmic(c:vec3f)->vec3f {
 // A strong black toe and a white shoulder; no broad ambient mid-grey fill.
 let x=max(vec3f(0.),c);
 // A continuous shoulder preserves radiance gradients; no broad hard-white clip.
 return (x*(2.51*x+.03))/(x*(2.51*x+.59)+.14);
}
fn facetEdge(pos:vec3f,n:vec3f,id:u32,owner:u32)->f32 {
 let sector=owner;
 let count=select(pieces[sector].caps.y,u32(pieces[sector].pivot.w),assembled());
 var distance:f32=100.;
 for(var j:u32=0u;j<count;j++){
  if(!assembled()&&(pieces[sector].caps.x&(1u<<j))==0u){continue;}
  let plane=pieces[sector].planes[j];let crossLength=length(cross(n,plane.xyz));
  if(crossLength>.05){distance=min(distance,abs(plane.w-dot(plane.xyz,pos))/crossLength);}
 }
 return distance;
}
fn ageFor(i:u32)->f32 {
 if(P.manual.y>.5){if(i==4u){return P.manual.x;}return P.claims[i];}
 if(i==3u){return P.view.w-.6;}if(i==4u){return P.view.w-.95;}return -1000.;
}
struct Out {@builtin(position) pos:vec4f,@location(0) point:vec2f,@location(1) uv:vec2f,@location(2) @interpolate(flat) id:u32,@location(3) @interpolate(flat) alpha:f32};
@vertex fn vertex(@builtin(vertex_index) vi:u32,@builtin(instance_index) id:u32)->Out {
 let corners=array<vec2f,6>(vec2f(-1.,-1.),vec2f(1.,-1.),vec2f(-1.,1.),vec2f(-1.,1.),vec2f(1.,-1.),vec2f(1.,1.));let uv=corners[vi];
 let S=P.view.z;let zoom=1.+P.effects.x;var at=vec2f(175.,278.);var extent=vec2f(0.);var alpha:f32=0.;
 if(id<SECTORS){
  let base=(id/2u)*2u;let pivot=pieces[base].pivot.xyz;
  let c=worldPoint(pivot,id);
  let radius=max(pieces[base].center.w+distance(pieces[base].center.xyz,pivot),pieces[base+1u].center.w+distance(pieces[base+1u].center.xyz,pivot));
  at+=vec2f(c.x,-c.y)*105.*zoom;extent=vec2f(radius*108.*zoom);
  alpha=1.-P.state.w;if(id%2u==1u){alpha=0.;}
  if(assembled()) {
   let center=forward(vec3f(.08446908,-.03181964,.13));
   let yaw=P.placement.z;let pitch=P.placement.y;
   let bound=vec2f(abs(cos(yaw))*1.11478722+abs(sin(yaw)*sin(pitch))*.88462955+abs(sin(yaw)*cos(pitch))*.52,abs(cos(pitch))*.88462955+abs(sin(pitch))*.52);
   at=vec2f(175.,278.)+vec2f(center.x,-center.y)*105.*zoom;
   extent=bound*105.*zoom+vec2f(1.5);if(id>0u){alpha=0.;}
  }
 }else if(id<(SECTORS+40u)){
  let i=(id-SECTORS)/8u;let tail=f32((id-SECTORS)%8u);let age=ageFor(i);let u=easing(phase(age,0.,.65));let q=max(0.,u-tail*.006);
  let start=vec2f(147.5+f32(i)*54.,511.);let control=vec2f(start.x-25.,350.);
  at=(1.-q)*(1.-q)*start+2.*(1.-q)*q*control+q*q*vec2f(175.,278.);
  extent=vec2f((3.8-tail*.35)*1.6);alpha=select(0.,pow(1.-tail/8.,1.6)*fadePhase(age,0.,.04)*(1.-fadePhase(age,.60,.65)),age>0.&&age<.65)*(1.-P.state.w);
 }else if(id==(SECTORS+52u)){
  at=vec2f(184.,354.);extent=vec2f(112.,31.);alpha=P.state.x*P.state.z*(1.-P.state.w);
 }else{
  alpha=0.;extent=vec2f(0.);
 }
 if(P.effects.w>.5 && id>=SECTORS){alpha=0.;}
 // Cull transparent instances before rasterization, including redundant assembled-gem and paired-sector quads.
 if(alpha<.001){extent=vec2f(0.);}
 var point=at+uv*extent;if(id<SECTORS){point+=P.backdrop.xy*(1.-P.state.w);}let pixel=(point-P.viewport.xy)*S+vec2f(0.,P.placement.x);
 var clip=vec2f(pixel.x/P.view.x*2.-1.,1.-pixel.y/P.view.y*2.);
 if(id<SECTORS){let gemPixel=(point-P.crystalViewport.xy)*P.crystalRaster.zw;clip=vec2f(gemPixel.x/P.crystalRaster.x*2.-1.,1.-gemPixel.y/P.crystalRaster.y*2.);}
 return Out(vec4f(clip,.5,1.),point,uv,id,alpha);
}
struct MaterialOut { @location(0) colour:vec4f, @location(1) surface:vec4f };
fn breathDistance(point:vec2f)->f32 {
 let d=point-P.breath.xy;
 // Continuous, stationary noise feathers the wet edge without crawling pixels.
 let ripple=sin(point.x*.31+sin(point.y*.23))*1.3+sin(point.y*.71-point.x*.47)*.65;
 return length(d)+ripple;
}
fn breathAt(point:vec2f)->f32 {
 return (1.-smoothstep(20.,37.,breathDistance(point)))*P.breath.z;
}
fn material(input:Out)->MaterialOut {
 if(input.alpha<.001){discard;}
 if(input.id==(SECTORS+52u)){
  var focus=vec3f(0.);
  for(var j:u32=0u;j<12u;j++){
   let n=forward(pieces[j*2u].planes[1].xyz);
   let red=refract(normalize(vec3f(.12,-.8,-.6)),n,1./2.405);
   let blue=refract(normalize(vec3f(.12,-.8,-.6)),n,1./2.446);
   let at=vec2f(184.+red.x*70.,353.+red.z*17.);
   let q=(input.point-at)/vec2f(17.,3.5);
   let qb=(input.point-at-vec2f((blue.x-red.x)*130.,0.))/vec2f(17.,3.5);
   let a=exp(-dot(q,q)*1.8);let b=exp(-dot(qb,qb)*1.8);
   focus+=vec3f(a,(a+b)*.5,b)*.014;
  }
  let a=min(.13,max(focus.r,max(focus.g,focus.b)))*input.alpha;
  return MaterialOut(vec4f(vec3f(.86,.88,1.)*a,a),vec4f(0.));
 }
 if(input.id>=SECTORS){let r=length(input.uv);let pearl=exp(-r*r*4.8);var a=pearl*input.alpha;
  if(input.id>=(SECTORS+40u)){a=max(pearl,exp(-abs(input.uv.x)*15.-abs(input.uv.y)*2.5)*.45+exp(-abs(input.uv.y)*15.-abs(input.uv.x)*2.5)*.45)*input.alpha;}
  return MaterialOut(vec4f(vec3f(1.,.91,.95)*a,a),vec4f(0.));
 }
 let id=input.id;let zoom=1.+P.effects.x;
 let point=input.point-P.backdrop.xy*(1.-P.state.w);
 let camera=vec3f((point.x-175.)/(105.*zoom),-(point.y-278.)/(105.*zoom),4.);
 let cameraOrigin=vec3f(camera.xy,5.5);let cameraRay=vec3f(0.,0.,-1.);
 var origin=toLocal(cameraOrigin,id);var ray=normalize(toLocal(cameraOrigin+cameraRay,id)-origin);
 if(assembled()){origin=inverse(cameraOrigin);ray=inverse(cameraRay);}
 let hit=trace(origin,ray,id);if(hit.far<max(0.,hit.near)||hit.near>9999.||hit.far>9999.){discard;}
 let pos=origin+ray*hit.near;
 let edgeDistance=facetEdge(pos,hit.normal,id,hit.enterFace);
 // Stable facet identity: the sector and exterior plane, never a frame-dependent random value.
 let planeNoise=fract(sin(dot(hit.normal,vec3f(127.1,311.7,91.3)))*43758.5453);
 let arrival=2.42+fract(atan2(hit.normal.y,hit.normal.x)/(2.*PI)+.5)*1.03+planeNoise*.12;
 let age=phase(P.view.w,arrival,arrival+.18);
 let evaporation=1.-pow(1.-age,3.);
 let grain=fract(sin(dot(floor(pos.xy*180.),vec2f(127.1,311.7)))*43758.5453);
 let threshold=clamp(edgeDistance*12.,0.,.68)+.12+grain*.12;
 var cleared=smoothstep(threshold-.10,threshold+.10,evaporation);
 if(age<=0.){cleared=0.;}if(age>=1.||P.view.w>=3.8||(P.effects.w>.5&&P.state.x>.001)){cleared=1.;}
 // Moisture first softens the frost; only the later part reveals cut facets.
 cleared=max(cleared,smoothstep(.55,.96,breathAt(input.point)));
 let mark=fadePhase(P.view.w,2.1,2.4)*(1.-cleared)*exp(-edgeDistance*230.);
 let laser=fadePhase(P.view.w,2.4,2.42)*exp(-pow((P.view.w-arrival)/.032,2.))*exp(-edgeDistance*190.);
 if(cleared<=0.){return MaterialOut(vec4f(0.),vec4f(0.,mark,laser,1.));}
 let skinPoint=vec2f(175.+pos.x*105.,278.-pos.y*105.);
 let v=skinPoint-vec2f(${ART.heart.x+ART.heart.width/2},${ART.heart.y+ART.heart.height/2});
 let angle=${-ART.heart.rotation}*PI/180.;
 let uv=(vec2f(v.x*cos(angle)-v.y*sin(angle),v.x*sin(angle)+v.y*cos(angle))+vec2f(${ART.heart.width/2},${ART.heart.height/2}))/vec2f(${ART.heart.width},${ART.heart.height});
 let map=textureSampleLevel(roundedTexture,imageSampler,uv,0.).rgb*2.-1.;
 let screenNormal=vec2f(map.x*cos(-angle)-map.y*sin(-angle),map.x*sin(-angle)+map.y*cos(-angle));
 let rounded=normalize(vec3f(screenNormal.x,-screenNormal.y,max(.15,map.z)));
 let normalClear=select(cleared,1.,P.effects.w>.5);
 let n=normalize(mix(rounded,hit.normal,normalClear));let nw=opticalNormal(n,id);
 let fres=.173+.827*pow(1.-abs(dot(nw,-cameraRay)),5.);
 // Each case front carries exactly the joined surface it belonged to. The
 // exposed radial side walls still trace their own crystal volume.
 var carried=vec4f(0.);var carryWeight:f32=0.;
 if(!assembled()&&P.view.w>3.9&&P.view.w<6.35&&hit.normal.z>.15){
  carried=textureSampleLevel(cutTexture,imageSampler,(skinPoint-P.crystalViewport.xy)/P.crystalViewport.zw,0.);
  carryWeight=1.-fadePhase(P.view.w,6.05,6.35);
  if(carryWeight>=1.&&carried.a>.98){
   var capDistance:f32=100.;let sector=hit.enterFace;
   for(var k:u32=0u;k<pieces[sector].caps.y;k++){
    if(k<u32(pieces[sector].pivot.w)||(pieces[sector].caps.x&(1u<<k))==0u){continue;}
    let plane=pieces[sector].planes[k];capDistance=min(capDistance,abs(plane.w-dot(plane.xyz,pos)));
   }
   let rim=exp(-capDistance*105.);
   return MaterialOut(vec4f(carried.rgb/carried.a+vec3f(1.,.86,1.13)*rim*.48,1.),vec4f(1.,0.,0.,1.));
  }
 }
 let volume=optical(pos,ray,n,id);
 let body=mix(vec3f(1.),vec3f(.97,.92,1.),P.effects.z);
 let through=volume*body;
 let reflectedRay=reflect(cameraRay,nw);
 let reflection=environment(normalize(reflectedRay+worldPoint(pos,id)*vec3f(.16,.13,.08)))+plaqueReflection(worldPoint(pos,id),reflectedRay);
 // A screen-space transmission lobe preserves the close hero backdrop where a
 // finite three-bounce studio trace cannot represent the complete room.
 let clear=screenTransmission(pos,ray,n,id,2.42)*body*.96;
 let clearWeight=mix(.52,.72,pow(abs(dot(nw,-cameraRay)),4.));
 var radiance=mix(mix(through,clear,clearWeight),reflection,fres);

 // Exposed cut faces have real thickness; broad white cards are seen through
 // and reflected by the new radial faces, rather than reading as black ink.
 if(!assembled()){
  var capDistance:f32=100.;
  let sector=hit.enterFace;
  for(var k:u32=0u;k<pieces[sector].caps.y;k++){
   if(k<u32(pieces[sector].pivot.w)||(pieces[sector].caps.x&(1u<<k))==0u){continue;}
   let plane=pieces[sector].planes[k];
   capDistance=min(capDistance,abs(plane.w-dot(plane.xyz,pos)));
  }
  let rim=exp(-capDistance*105.);
  let cutFace=1.-smoothstep(.10,.32,abs(hit.normal.z));
  let seen=screenTransmission(pos,ray,n,id,1.52);
  let thick=mix(vec3f(.68,.52,.65),vec3f(1.05,.93,1.12),sat(abs(nw.x)*.65+abs(nw.y)*.45));
  let prism=vec3f(1.,.86,1.13)+vec3f(.15*sin(capDistance*280.),.08,.15*cos(capDistance*280.));
  radiance=mix(radiance,seen*.48+thick,cutFace*.9);
  radiance+=rim*prism*.48;
  if(carryWeight>0.){
   let front=carried.rgb/max(carried.a,.001);
   // Low-density baked alpha is coverage, never black paint on a covered edge.
   let valid=smoothstep(.05,.95,carried.a);
   radiance=mix(radiance,mix(radiance,front,valid)+rim*prism*.48,carryWeight*(1.-cutFace));
   carryWeight=0.;
  }
 }
 let edge=exp(-edgeDistance*420.);
 // A subpixel bevel leaks white key light, without filling the dark facets.
 radiance+=edge*(reflection+environment(normalize(nw+vec3f(-.1,.4,.2))))*.16;
 radiance+=edge*environment(normalize(vec3f(-.18,.78,.60)))*abs(dot(nw,normalize(vec3f(-.18,.78,.60))))*.024;
 let core=exp(-length(input.point-vec2f(185.,289.))/62.)*P.breath.w*.12;
 radiance+=vec3f(1.,.86,.90)*core*cleared;
 let polish=exp(-pow((camera.x+camera.y*.18-P.light.x)/.055,2.))*P.effects.y*.5;
 radiance+=vec3f(polish);
 // Markings lead the laser by 0.3 s. Each facet clears over 180 ms, edges first.
 return MaterialOut(mix(vec4f(radiance,1.),carried,carryWeight),vec4f(cleared,mark,laser,1.));
}
@fragment fn fragment(input:Out)->MaterialOut { return material(input); }
@fragment fn overlayFragment(input:Out)->@location(0) vec4f { return material(input).colour; }
fn foregroundOccludes(screen:vec2f)->bool {
 let keep=1.-P.state.w;let open=sat(P.state.y)*keep;
 let p=(screen-vec2f(175.,278.)-P.backdrop.xy*keep)/(1.+P.effects.x*keep)+vec2f(175.,278.);
 let delta=screen-vec2f(187.5,291.);let c=cos(-.36);let s=sin(-.36);
 let v=vec2f(delta.x*c+delta.y*s,-delta.x*s+delta.y*c);
 let q=abs(v)-vec2f(33.5,3.);
 let sdf=length(max(q,vec2f(0.)))+min(max(q.x,q.y),0.)-38.;
 return sdf<2. || (open<.5&&distance(p,vec2f(286.5,208.5))<23.);
}
var<workgroup> peakScores:array<f32,256>;
var<workgroup> peakPoints:array<vec2f,256>;
@compute @workgroup_size(256) fn findGlints(@builtin(local_invocation_index) lane:u32) {
 let scale=P.crystalRaster.zw;
 let origin=(vec2f(45.,175.)-P.crystalViewport.xy)*scale;
 let size=vec2u(260u,220u);var score:f32=0.;var point=vec2f(0.);
 let hold=fadePhase(P.view.w,7.4,7.65)*(1.-fadePhase(P.view.w,8.65,8.9));
 if(hold<=0.){for(var index=lane;index<size.x*size.y;index+=256u){
  let design=vec2f(f32(index%size.x),f32(index/size.x));
  let px=vec2i(origin+design*scale);
  let c=textureLoad(crystalTexture,px,0);
  let screen=vec2f(px)/scale+P.crystalViewport.xy;
  let sweep=vec2f(118.+55.*sin((P.view.w-7.4)*1.7),225.-15.*cos((P.view.w-7.4)*1.7));
  let crown=exp(-dot((screen-sweep)/vec2f(85.,42.),(screen-sweep)/vec2f(85.,42.)));
  let value=select(max(c.r,max(c.g,c.b))*mix(1.,.55+crown*.85,hold),0.,foregroundOccludes(screen)||(hold>.01&&screen.y>275.));
  if(value>score){score=value;point=screen;}
 }}
 peakScores[lane]=score;peakPoints[lane]=point;workgroupBarrier();
 if(lane==0u){
  var targets:array<vec4f,3>;
  for(var star:u32=0u;star<3u;star++){
   var best:f32=.02;var at=vec2f(0.);
   if(hold<=0.){for(var j:u32=0u;j<256u;j++){
    var separate=true;
    for(var previous:u32=0u;previous<star;previous++){
     if(distance(peakPoints[j],targets[previous].xy)<26.){separate=false;}
    }
    if(separate&&peakScores[j]>best){best=peakScores[j];at=peakPoints[j];}
   }}
   targets[star]=vec4f(at,smoothstep(.02,.35,best)*.85,best);
   // During hold, three crown-mounted pin reflections glide over the fixed gem.
   // Test actual crystal coverage and the inlay mask before drawing a star.
   // They replace these same three slots: no second set can exceed the cap.
   if(hold>0.){
    let age=P.view.w-7.4;let k=f32(star);
    let anchors=array<vec3f,3>(vec3f(.60,.54,-.12),vec3f(-.58,.52,-.12),vec3f(.88,.12,-.10));
    let anchor=anchors[star]+vec3f(.045*sin(age*2.2+k*1.7),.025*sin(age*1.9+k*2.),0.);
    let w=forward(anchor);let pin=vec2f(175.,278.)+vec2f(w.x,-w.y)*105.*(1.+P.effects.x)+P.backdrop.xy*(1.-P.state.w);
    let px=vec2i((pin-P.crystalViewport.xy)*P.crystalRaster.zw);
    let surface=textureLoad(crystalTexture,px,0);
    let visible=select(0.,1.,surface.a>.9&&!foregroundOccludes(pin));
    let breath=.55+.40*pow(sin(age*2.4+k*1.2),2.);
    targets[star]=vec4f(pin,visible*breath*hold,max(surface.r,max(surface.g,surface.b)));
   }
  }
  for(var star:u32=0u;star<3u;star++){
   let nextPeak=targets[star];let old=glints.points[star];
   let blend=1.-exp(-P.backdrop.w/.09);
   if(P.backdrop.w>.5){glints.points[star]=nextPeak;}
   else if(distance(nextPeak.xy,old.xy)>35.&&old.z>.025){
    glints.points[star]=vec4f(old.xy,old.z*(1.-blend),old.w);
   }else{
    glints.points[star]=vec4f(mix(old.xy,nextPeak.xy,blend),mix(old.z,nextPeak.z,blend),nextPeak.w);
   }
  }
 }
}
@vertex fn coverageVertex(@location(0) vertex:vec4f)->@builtin(position) vec4f {
 let w=worldPoint(vertex.xyz,u32(vertex.w));
 let point=vec2f(175.,278.)+vec2f(w.x,-w.y)*105.*(1.+P.effects.x)+P.backdrop.xy*(1.-P.state.w);
 let pixel=(point-P.viewport.xy)*P.view.z;
 return vec4f(pixel.x/P.view.x*2.-1.,1.-pixel.y/P.view.y*2.,.5,1.);
}
@fragment fn coverageFragment()->@location(0) vec4f {return vec4f(1.);}
struct CompositeOut {@builtin(position) pos:vec4f,@location(0) uv:vec2f};
@vertex fn compositeVertex(@builtin(vertex_index) vi:u32)->CompositeOut {
 let corners=array<vec2f,3>(vec2f(-1.,-1.),vec2f(3.,-1.),vec2f(-1.,3.));let p=corners[vi];
 return CompositeOut(vec4f(p,0.,1.),vec2f(p.x*.5+.5,.5-p.y*.5));
}
fn crystalAt(uv:vec2f)->vec4f {
 if(any(uv<vec2f(0.))||any(uv>vec2f(1.))){return vec4f(0.);}
 return textureSampleLevel(crystalTexture,imageSampler,uv,0.);
}
fn reconstructCrystal(crystalUV:vec2f,fullCoverage:f32)->vec3f {
 let source=crystalAt(crystalUV);
 // Reconstruct colour at half native density. Only the native MSAA mask owns alpha.
 let texel=1./P.crystalRaster.xy;
 let neighbors=(crystalAt(crystalUV+vec2f(texel.x,0.))+crystalAt(crystalUV-vec2f(texel.x,0.))+crystalAt(crystalUV+vec2f(0.,texel.y))+crystalAt(crystalUV-vec2f(0.,texel.y)))*.25;
 let detail=clamp(source.rgb-neighbors.rgb,-vec3f(.12),vec3f(.12));
 // Dilate premultiplied colour, including diagonals at the tip. Normalizing by
 // optical alpha extends the interior through its coarse fringe without holes.
 var supported=source;
 if(fullCoverage>0.&&source.a<.999){
  var sum=vec4f(0.);var weight:f32=0.;
  for(var y:i32=-1;y<=1;y++){for(var x:i32=-1;x<=1;x++){
   let q=vec2f(f32(x),f32(y));let w=1./(1.+dot(q,q));
   sum+=crystalAt(crystalUV+q*texel)*w;weight+=w;
  }}
  // A rare diagonal tip can miss the first ring; extend only those empty samples.
  if(sum.a<.001*weight){for(var y:i32=-2;y<=2;y++){for(var x:i32=-2;x<=2;x++){
   if(abs(x)<2&&abs(y)<2){continue;}
   let q=vec2f(f32(x),f32(y));let w=1./(1.+dot(q,q));
   sum+=crystalAt(crystalUV+q*texel)*w;weight+=w;
  }}}
  supported=sum/weight;
 }
 return max(vec3f(0.),supported.rgb+detail*.28*step(.999,source.a))/max(.001,supported.a);
}
// Kept out of the gem/bloom pipeline: only this small scissored pass samples the damp backdrop.
@fragment fn breathFragment(input:CompositeOut)->@location(0) vec4f {
 let point=(input.uv*P.view.xy-vec2f(0.,P.placement.x))/P.view.z+P.viewport.xy;
 let wet=breathAt(point);
 if(wet<=.0001){discard;}
 let crystalUV=(point-P.crystalViewport.xy)/P.crystalViewport.zw;
 let coverage=textureLoad(coverageTexture,vec2i(input.pos.xy),0).a*(1.-P.state.w);
 if(coverage<=0.){discard;}
 let skin=textureSampleLevel(skinTexture,imageSampler,point/vec2f(402.,440.),0.);
 let crystalColour=pow(filmic(reconstructCrystal(crystalUV,coverage)),vec3f(1./2.2));
  let reveal=smoothstep(.55,.96,wet);
  let background=pow(backdrop(point),vec3f(1./2.2));
  let frost=mix(background,skin.rgb,skin.a);
  // A damp veil smooths and brightens the ice before it becomes transparent.
  let soften=vec2f(2.5,0.);
  let softSkin=(textureSampleLevel(skinTexture,imageSampler,(point+soften.xy)/vec2f(402.,440.),0.).rgb+textureSampleLevel(skinTexture,imageSampler,(point-soften.xy)/vec2f(402.,440.),0.).rgb+textureSampleLevel(skinTexture,imageSampler,(point+soften.yx)/vec2f(402.,440.),0.).rgb+textureSampleLevel(skinTexture,imageSampler,(point-soften.yx)/vec2f(402.,440.),0.).rgb)*.25;
  let softened=mix(background,softSkin,skin.a);
  let thin=mix(frost,max(frost,softened),.65*wet);
  let damp=mix(thin,max(thin,vec3f(.96,.97,1.)),.36*sqrt(wet));
  let luma=vec3f(.2126,.7152,.0722);
  let minimum=min(1.,dot(frost,luma)+.018);
  let luminance=dot(crystalColour,luma);
  let lift=sat((minimum-luminance)/max(.001,1.-luminance));
  let clearGlass=mix(crystalColour,vec3f(1.),lift);
  let ring=exp(-pow((breathDistance(point)-30.)/2.2,2.))*P.breath.z*.045;
  let wetColour=mix(damp,clearGlass,reveal)+vec3f(.90,.95,1.)*ring;
  let alpha=max(1.-pow(1.-wet,3.),ring)*coverage;
  return vec4f(wetColour*alpha,alpha);
}
@fragment fn compositeFragment(input:CompositeOut)->@location(0) vec4f {
 let px=input.uv*P.view.xy;let point=(px-vec2f(0.,P.placement.x))/P.view.z+P.viewport.xy;
 let crystalUV=(point-P.crystalViewport.xy)/P.crystalViewport.zw;
 let info=textureSampleLevel(materialTexture,imageSampler,crystalUV,0.);
 let fullCoverage=textureLoad(coverageTexture,vec2i(input.pos.xy),0).a;
 let skin=textureSampleLevel(skinTexture,imageSampler,point/vec2f(402.,440.),0.);
 let cut=select(0.,1.,P.view.w>2.40001||(P.effects.w>.5&&P.state.x>.001));
 var clear=info.r/max(.001,info.a);
 if(P.view.w>=3.8||(P.effects.w>.5&&P.state.x>.001)){clear=1.;}
 if(cut==0.){
  let etch=info.g*.12*fullCoverage;
  return vec4f(vec3f(.65,.69,.77)*etch,etch);
 }
 let crystalColour=pow(filmic(reconstructCrystal(crystalUV,fullCoverage)),vec3f(1./2.2));
 var glow=vec3f(0.);
 if(P.view.w>=2.4&&point.x>25.&&point.x<325.&&point.y>140.&&point.y<405.){
  // Threshold in scene-linear HDR: neither transmitted bear nor dark facets bloom.
  for(var j:u32=0u;j<12u;j++){
   let angle=f32(j)*2.399963;let radius=mix(12.,24.,f32(j%3u)/2.);
   let uv=crystalUV+vec2f(cos(angle),sin(angle))*radius/P.crystalViewport.zw;
   let sample=crystalAt(uv).rgb;
   glow+=min(max(sample-vec3f(2.8),vec3f(0.)),vec3f(8.))*.0045;
  }
 }
 var flare:f32=0.;var halo:f32=0.;
 if(P.effects.w<.5){
  for(var j:u32=0u;j<3u;j++){
   let peak=glints.points[j];let q=abs(point-peak.xy);
   let quiet=mix(.22,1.,fadePhase(P.view.w,6.4,6.65));
   let gate=peak.z*P.state.x*(1.-P.state.w)*quiet;
   let cross=exp(-q.x*3.5-q.y*.35)+exp(-q.y*3.5-q.x*.35);
   flare+=cross*gate*(1.-smoothstep(8.,12.,max(q.x,q.y)));
   halo+=exp(-dot(q,q)/64.)*gate*.035;
  }
 }
 let coverage=fullCoverage*(1.-P.state.w);
 // The skin bypasses tone mapping: these are the original screen-space raster pixels.
 // Only the crystal is tone mapped, then the very same surface sheds its outer frost.
 let skinAlpha=skin.a*(1.-P.state.w);
 // Until case opening the original native raster is the outer skin. Keeping
 // its native sampling path makes the first cut pixel-identical; the advancing
 // crystal coverage is exactly the complementary facet evaporation mask.
 let nativeSkin=P.view.w<3.9;
 let alpha=select(mix(skinAlpha,coverage,clear),coverage*clear,nativeSkin);
 var colour=select(mix(skin.rgb*skinAlpha,crystalColour*coverage,clear),crystalColour*coverage*clear,nativeSkin);
 let etch=info.g*.12*coverage;
 colour=colour*(1.-etch)+vec3f(.65,.69,.77)*etch;
 let laser=info.b*.7*coverage;
 colour+=vec3f(laser*.9,laser*.84,laser*.74);
 var completion:f32=0.;var rays:f32=0.;
 if(P.effects.w<.5){
  // One 120 ms ease-out attack, then a 300 ms ease-out decay, per cut.
  let attack=1.-pow(1.-phase(P.view.w,3.8,3.92),3.);
  let release=pow(1.-phase(P.view.w,3.92,4.22),3.);
  completion=attack*release*coverage*.30;
  let portals=array<vec2f,12>(${PORTALS});
  let rayWindow=fadePhase(P.view.w,2.4,2.5)*(1.-fadePhase(P.view.w,3.8,3.94));
  let core=vec2f(185.,289.);
  if(rayWindow>0.){for(var k:u32=0u;k<12u;k++){
   let portal=portals[k];let direction=normalize(portal-core);
   let opening=mix(core,portal,.88);
   let surface=textureSampleLevel(materialTexture,imageSampler,(opening-P.crystalViewport.xy)/P.crystalViewport.zw,0.);
   let gate=sat(surface.r/max(.001,surface.a)+surface.b*.8)*surface.a;
   let delta=point-portal;let along=dot(delta,direction);let across=abs(delta.x*direction.y-delta.y*direction.x);
   let streak=exp(-across*across/0.21)*exp(-max(0.,along)/12.)*smoothstep(-4.,1.,along)*(1.-smoothstep(19.,27.,along));
   rays+=streak*gate*P.breath.w*rayWindow*.29*(1.-smoothstep(356.,365.,point.y));
  }}
 }
 let signature=vec3f(1.,.88,.93)*(completion+rays*(1.-coverage*.7));
 let extra=clamp((glow+vec3f(flare+halo))*clear+signature,vec3f(0.),vec3f(1.));
 let extraAlpha=max(extra.r,max(extra.g,extra.b));
 colour=colour*(1.-extraAlpha)+extra;
 return vec4f(colour,alpha+(etch+extraAlpha-etch*extraAlpha)*(1.-alpha));
}
`; }
export function entryName(_code:string,stage:'compute'|'vertex'|'fragment'){return stage;}
