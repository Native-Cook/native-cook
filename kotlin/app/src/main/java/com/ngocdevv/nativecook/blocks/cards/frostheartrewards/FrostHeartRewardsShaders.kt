package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import java.util.Locale
import kotlin.math.sqrt

/**
 * AGSL twin of gpu/fhr-particles.ts, simplified: the same 24 Blender sector planes traced in one
 * pass per pixel (no MSAA mask, no half-density target, no baked case fronts, no glint search).
 * Two internal bounces, dispersion as three IORs on the last exit, the same studio softboxes and
 * filmic shoulder. Array indices are loop indices only, as AGSL (GLSL ES 1.0) requires.
 */
internal object FrostHeartRewardsShaders {
    private fun f(x: Double) = String.format(Locale.US, "%.6f", x)

    /** Softbox frames are constant, so their right/up vectors are computed here, not per pixel. */
    private fun frame(cx: Double, cy: Double, cz: Double): String {
        val l = sqrt(cx * cx + cy * cy + cz * cz)
        val c = doubleArrayOf(cx / l, cy / l, cz / l)
        // right = normalize(cross((0,1,0), c)) = normalize((c.z, 0, -c.x))
        val rl = sqrt(c[2] * c[2] + c[0] * c[0])
        val r = doubleArrayOf(c[2] / rl, 0.0, -c[0] / rl)
        // up = cross(c, right)
        val u = doubleArrayOf(c[1] * r[2] - c[2] * r[1], c[2] * r[0] - c[0] * r[2], c[0] * r[1] - c[1] * r[0])
        return "float3(${f(c[0])},${f(c[1])},${f(c[2])}),float3(${f(r[0])},${f(r[1])},${f(r[2])}),float3(${f(u[0])},${f(u[1])},${f(u[2])})"
    }
    private fun box(cx: Double, cy: Double, cz: Double, sx: Double, sy: Double) = "softbox(r,${frame(cx, cy, cz)},float2(${f(sx)},${f(sy)}))"
    private fun pin(cx: Double, cy: Double, cz: Double) = "pinLight(r,${frame(cx, cy, cz)})"
    private fun n3(x: Double, y: Double, z: Double): String { val l = sqrt(x * x + y * y + z * z); return "float3(${f(x / l)},${f(y / l)},${f(z / l)})" }

    /** The nine planes of sector [k]: table, six exterior facets, its own radial and the next one negated. */
    private fun planes(k: String, table: Double) = listOf(
        "float3(0.0,0.0,1.0)" to f(table),
        "ext[$k*6+0].xyz" to "ext[$k*6+0].w",
        "ext[$k*6+1].xyz" to "ext[$k*6+1].w",
        "ext[$k*6+2].xyz" to "ext[$k*6+2].w",
        "ext[$k*6+3].xyz" to "ext[$k*6+3].w",
        "ext[$k*6+4].xyz" to "ext[$k*6+4].w",
        "ext[$k*6+5].xyz" to "ext[$k*6+5].w",
        "rad[$k].xyz" to "rad[$k].w",
        "(-rad[$k+1].xyz)" to "(-rad[$k+1].w)",
    )

    /** Convex slab clipping. With [gradient], also the screen gradients of the entry/exit distances. */
    private fun clip(k: String, table: Double, gradient: Boolean) = buildString {
        planes(k, table).forEachIndexed { id, (n, w) ->
            append("{float3 pn=$n;float pw=$w;float den=dot(pn,d);float dist=pw-dot(pn,o);")
            if (gradient) {
                append("if(abs(den)<0.0001){float gs=length(float2(dot(pn,ux),dot(pn,uy)));float sl=dist/max(gs,0.000001);")
                // Within a closed union the radial planes are seams, not edges: never antialias them.
                append(if (id >= 7) "if($id==seamId||assembled){sl+=seam;}" else "")
                append("slack=min(slack,sl);}")
                append("else{float a=dist/den;float2 ga=-float2(dot(pn,ux),dot(pn,uy))/den;")
                append("if(den<0.0){if(a>lo){lo=a;nIn=pn;fI=$id;gLo=ga;}}else{if(a<hi){hi=a;fO=$id;gHi=ga;}}}}")
            } else {
                append("if(abs(den)<0.0001){if(dist<0.0){miss=true;}}")
                append("else{float a=dist/den;if(den<0.0){if(a>lo){lo=a;}}else{if(a<hi){hi=a;nOut=pn;fO=$id;}}}}")
            }
        }
    }

    /** Distances from a facet point to its neighbouring facets (bevel and laser line), and to the cut cap. */
    private fun edges(k: String, table: Double) = buildString {
        append("edge=100.0;cap=100.0;float3 hp=o+d*lo;")
        planes(k, table).forEachIndexed { id, (n, w) ->
            append("{float3 pn=$n;float pw=$w;")
            if (id >= 7) append("if($id!=seamId){cap=min(cap,abs(pw-dot(pn,hp)));}")
            append("if($id!=fI")
            if (id >= 7) append("&&!assembled&&$id!=seamId")
            append("){float cl=length(cross(nIn,pn));if(cl>0.05){edge=min(edge,abs(pw-dot(pn,hp))/cl);}}}")
        }
    }

    fun gem(table: Double, amethyst: FloatArray, bear: FloatArray): String = """
uniform shader page;
uniform shader bear;
uniform shader hug;
uniform shader skin;
uniform float4 ext[144];
uniform float4 rad[25];
uniform float4 grp[12];
uniform float4 xf[36];
uniform float4 uA; // t, gem, display, spotlight
uniform float4 uB; // reset, polish, hue, reduced
uniform float4 uC; // pitch, yaw, tiltX, tiltY
uniform float4 uD; // polishX, breath, breathX, breathY
uniform float4 uE; // core, assembled, device pixels per point, completion flash
const float PI=3.14159265;
float sat(float x){return clamp(x,0.0,1.0);}
float phase(float t,float a,float b){return sat((t-a)/(b-a));}
float fadePhase(float t,float a,float b){float u=phase(t,a,b);return u*u*(3.0-2.0*u);}
float3 rotX(float3 p,float a){float c=cos(a);float s=sin(a);return float3(p.x,p.y*c-p.z*s,p.y*s+p.z*c);}
float3 rotY(float3 p,float a){float c=cos(a);float s=sin(a);return float3(p.x*c+p.z*s,p.y,-p.x*s+p.z*c);}
float3 fwd(float3 p){return rotY(rotX(p,uC.x),uC.y);}
float3 toWorld(float3 v,float4 R0,float4 R1,float4 R2){return R0.xyz*v.x+R1.xyz*v.y+R2.xyz*v.z;}
float3 worldPoint(float3 p,float4 R0,float4 R1,float4 R2){return toWorld(p-float3(R0.w,R1.w,R2.w),R0,R1,R2);}
float softbox(float3 r,float3 c,float3 rt,float3 up,float2 size){
 float facing=dot(r,c);float2 uv=float2(dot(r,rt),dot(r,up))/max(0.001,facing);
 float2 q=abs(uv)-size;
 float ap=(1.0-smoothstep(-0.014,0.014,max(q.x,q.y)))*step(0.01,facing);
 float2 l=uv/max(size,float2(0.001));
 return ap*(0.12+0.88*exp(-dot(l,l)*2.4));
}
float pinLight(float3 r,float3 c,float3 rt,float3 up){
 float facing=dot(r,c);float2 uv=float2(dot(r,rt),dot(r,up))/max(0.001,facing);
 float2 q=uv/0.006;float k=max(0.0,1.0-dot(q,q));return k*k*step(0.01,facing)*12.0;
}
float3 environment(float3 ray){
 float3 r=normalize(ray);
 float key=${box(-.18, .78, .60, .12, .055)}*8.0;
 float left=${box(-.615690, -.554370, .560000, .045, .30)}*7.0+${box(.17, -.27, .95, .08, .25)}*7.0;
 float right=${box(.75, .12, .55, .019, .12)}*14.0;
 float back=${box(.15, .65, -.75, .10, .035)}*7.0;
 float lower=${box(-.47543, -.74502, .46788, .055, .055)}*0.42;
 float lowerRight=${box(.7729, -.6340, .0256, .050, .028)}*0.22;
 float bounce=pow(max(0.0,dot(r,${n3(-.3, -.7, -.6)})),18.0)*0.025;
 float room=pow(max(0.0,dot(r,${n3(-.25, .8, .35)}))*0.5+0.5,3.0)*0.003;
 float wall=pow(max(0.0,dot(r,${n3(.6, -.3, -.7)}))*0.5+0.5,5.0)*0.018;
 float pins=${pin(.278374, -.790470, .545588)}+${pin(.189659, -.940826, .280847)};
 return float3(room)*float3(0.90,0.94,1.0)+float3(left)*float3(0.72,0.86,1.0)+float3(key+right+back+lower+lowerRight+pins)+mix(float3(1.0,0.12,0.55),float3(0.48,0.09,1.0),uB.z)*(bounce+wall);
}
float3 amethyst(float3 c){return clamp(float3(dot(c,float3(${amethyst[0]},${amethyst[1]},${amethyst[2]})),dot(c,float3(${amethyst[5]},${amethyst[6]},${amethyst[7]})),dot(c,float3(${amethyst[10]},${amethyst[11]},${amethyst[12]}))),0.0,1.0);}
float3 bearColour(float3 c){return clamp(c*float3(${bear[0]},${bear[6]},${bear[12]})+float3(${bear[4]},${bear[9]},${bear[14]}),0.0,1.0);}
float3 backdrop(float2 screen){
 float3 bg=float3(page.eval(screen).rgb);
 float2 lamp=(screen-float2(177.0,282.0))/float2(120.0,94.0);
 bg=mix(bg,float3(0.92,0.79,0.87),exp(-dot(lamp,lamp)*1.5)*sat(uA.z)*0.15);
 float keep=1.0-uB.x;
 float2 p=screen-uC.zw*keep;
 float4 rest=float4(bear.eval(p));float4 hg=float4(hug.eval(p));
 float open=sat(uA.z)*keep;
 float a=rest.a*(1.0-open)*(1.0-smoothstep(292.0,332.0,p.y));
 float b=hg.a*open*(1.0-smoothstep(310.0,350.0,p.y));
 float radius=mix(-24.0,230.0,uB.z*keep);
 float wave=1.0-smoothstep(radius-18.0,radius+18.0,distance(p,float2(175.0,278.0)));
 if(uB.z<=0.0){wave=0.0;} if(uB.z*keep>=1.0){wave=1.0;}
 float3 rc=bearColour(rest.rgb/max(rest.a,0.001));float3 hc=bearColour(hg.rgb/max(hg.a,0.001));
 rc=mix(rc,amethyst(rc),wave);hc=mix(hc,amethyst(hc),wave);
 bg=mix(bg,rc,a);bg=mix(bg,hc,b);
 return pow(max(bg,float3(0.0)),float3(2.2));
}
float3 plaque(float3 wp,float3 wr){
 if(wr.z<=0.015){return float3(0.0);}
 float travel=(0.58-wp.z)/wr.z;if(travel<=0.0||travel>4.0){return float3(0.0);}
 float3 q=wp+wr*travel;float2 sc=float2(175.0,278.0)+float2(q.x,-q.y)*105.0;
 float2 dl=sc-float2(187.5,291.0);float c=cos(-0.36);float s=sin(-0.36);
 float2 v=float2(dl.x*c+dl.y*s,-dl.x*s+dl.y*c);
 float2 pp=abs(v)-float2(33.5,3.0);float sdf=length(max(pp,float2(0.0)))+min(max(pp.x,pp.y),0.0)-38.0;
 float3 albedo=mix(float3(0.95,0.025,0.72),float3(0.33,0.004,0.31),sat((v.y+41.0)/82.0));
 return albedo*0.97*(1.0-smoothstep(-1.0,3.0,sdf))*uA.y;
}
float3 exitLight(float3 pos,float3 ray,float4 R0,float4 R1,float4 R2){
 float3 wp=worldPoint(pos,R0,R1,R2);float3 wr=normalize(toWorld(ray,R0,R1,R2));
 float3 light=environment(normalize(wr+wp*float3(0.16,0.13,0.08)))+plaque(wp,wr);
 if(wr.z<-0.015){
  float travel=(-0.65-wp.z)/wr.z;
  if(travel>=0.0&&travel<=10.0){float3 q=wp+wr*travel;light+=backdrop(float2(175.0,278.0)+float2(q.x,-q.y)*105.0+uC.zw*(1.0-uB.x));}
 }
 return light;
}
float3 screenTx(float3 pos,float3 ray,float3 n,float ior,float4 R0,float4 R1,float4 R2){
 float3 wp=worldPoint(pos,R0,R1,R2);float3 wr=toWorld(refract(ray,n,1.0/ior),R0,R1,R2);
 if(wr.z>=-0.015){return float3(0.0);}
 float travel=(-0.65-wp.z)/wr.z;if(travel<0.0||travel>10.0){return float3(0.0);}
 float3 q=wp+wr*travel;
 float2 pt=float2(175.0,278.0)+float2(q.x,-q.y)*105.0+uC.zw*(1.0-uB.x);
 float2 vi=float2(175.0,245.0)+(pt-float2(175.0,278.0))*0.58+float2(wr.x,-wr.y)*90.0;
 return mix(backdrop(pt),backdrop(vi),0.65);
}
// Inside the closed union (g < 0) only exterior facets end a path; inside one display piece its
// open radial cut does too, and the seam shared by its two sectors never does.
float4 traceExit(float3 o,float3 d,int g){
 float best=10000.0;float3 bn=float3(0.0,0.0,-1.0);
 for(int k=0;k<24;k++){
  if(g>=0&&(k/2)!=g){continue;}
  float3 rel=o-grp[k/2].xyz;float pr=dot(rel,d);float rr=grp[k/2].w+0.01;float outside=dot(rel,rel)-rr*rr;
  if(pr*pr-outside<0.0||(pr>0.0&&outside>0.0)){continue;}
  float lo=-10000.0;float hi=10000.0;float3 nOut=float3(0.0,0.0,-1.0);int fO=0;bool miss=false;
  ${clip("k", table, false)}
  if(miss||hi<max(0.0,lo)||hi<=0.00001){continue;}
  int seamId=((k-(k/2)*2)==0)?8:7;
  bool ok=(g<0)?(fO<7):(fO!=seamId);
  if(ok&&hi<best){best=hi;bn=nOut;}
 }
 return float4(bn,best);
}
float3 filmic(float3 c){float3 x=max(float3(0.0),c);return (x*(2.51*x+0.03))/(x*(2.51*x+0.59)+0.14);}
float breathDistance(float2 pt){float2 d=pt-uD.zw;float ripple=sin(pt.x*0.31+sin(pt.y*0.23))*1.3+sin(pt.y*0.71-pt.x*0.47)*0.65;return length(d)+ripple;}
float breathAt(float2 pt){return (1.0-smoothstep(20.0,37.0,breathDistance(pt)))*uD.y;}
half4 main(float2 p){
 float t=uA.x;float keep=1.0-uB.x;bool assembled=uE.y>0.5;float seam=1.5/uE.z;
 float2 pt=p-uC.zw*keep;
 float3 camO=float3((pt.x-175.0)/105.0,-(pt.y-278.0)/105.0,5.5);
 float cov=0.0;float bestT=10000.0;int bestK=-1;float3 bN=float3(0.0,0.0,1.0);float3 bO=float3(0.0);float3 bD=float3(0.0,0.0,-1.0);
 float4 b0=float4(1.0,0.0,0.0,0.0);float4 b1=float4(0.0,1.0,0.0,0.0);float4 b2=float4(0.0,0.0,1.0,0.0);float bEdge=100.0;float bCap=100.0;
 float fringe=0.0;bool bHit=false;float bLo=0.0;
 for(int k=0;k<24;k++){
  float4 R0=xf[(k/2)*3];float4 R1=xf[(k/2)*3+1];float4 R2=xf[(k/2)*3+2];
  float3 o=float3(dot(R0.xyz,camO)+R0.w,dot(R1.xyz,camO)+R1.w,dot(R2.xyz,camO)+R2.w);
  float3 d=-float3(R0.z,R1.z,R2.z);
  float3 ux=float3(R0.x,R1.x,R2.x)/105.0;float3 uy=-float3(R0.y,R1.y,R2.y)/105.0;
  float3 rel=o-grp[k/2].xyz;float pr=dot(rel,d);float rr=grp[k/2].w+0.03;
  if(pr*pr-(dot(rel,rel)-rr*rr)<0.0){continue;}
  int seamId=((k-(k/2)*2)==0)?8:7;
  float lo=-10000.0;float hi=10000.0;float3 nIn=float3(0.0,0.0,1.0);int fI=0;int fO=0;float2 gLo=float2(0.0);float2 gHi=float2(0.0);float slack=10000.0;
  ${clip("k", table, true)}
  float gap=hi-lo;float gl=length(gHi-gLo);
  float sd=min(gl>0.000001?gap/gl:(gap>=0.0?10000.0:-10000.0),slack);
  float ck=sat(sd*uE.z+0.5);
  if(hi<=0.0){ck=0.0;}
  // The sectors partition the heart, so their pixel coverages add up across every seam.
  cov+=ck;
  bool hit=gap>=0.0&&slack>=0.0&&lo>0.0;
  if((hit&&lo<bestT)||(!hit&&!bHit&&ck>fringe)){
   if(!hit){fringe=ck;}
   float edge=100.0;float cap=100.0;
   ${edges("k", table)}
   bestT=hit?lo:bestT;bLo=lo;bestK=k;bN=nIn;bO=o;bD=d;b0=R0;b1=R1;b2=R2;bEdge=edge;bCap=cap;
   if(hit){bHit=true;}
  }
 }
 cov=min(1.0,cov)*keep;
 if(bestK<0||cov<=0.0){return half4(0.0);}
 float3 n=bN;float3 ray=bD;
 float3 pos=bO+bD*bLo;
 float3 nw=normalize(toWorld(n,b0,b1,b2));
 float3 camD=float3(0.0,0.0,-1.0);
 // Facet clearing: the laser reaches each plane in turn and its frost evaporates edges first.
 float planeNoise=fract(sin(dot(n,float3(127.1,311.7,91.3)))*43758.5453);
 float arrival=2.42+fract(atan(n.y,n.x)/(2.0*PI)+0.5)*1.03+planeNoise*0.12;
 float age=phase(t,arrival,arrival+0.18);
 float evaporation=1.0-pow(1.0-age,3.0);
 float grain=fract(sin(dot(floor(pos.xy*180.0),float2(127.1,311.7)))*43758.5453);
 float threshold=clamp(bEdge*12.0,0.0,0.68)+0.12+grain*0.12;
 float cleared=smoothstep(threshold-0.10,threshold+0.10,evaporation);
 if(age<=0.0){cleared=0.0;}
 if(age>=1.0||t>=3.8||(uB.w>0.5&&uA.y>0.001)){cleared=1.0;}
 float wet=t<2.4?breathAt(p):0.0;
 cleared=max(cleared,smoothstep(0.55,0.96,wet));
 float mark=fadePhase(t,2.1,2.4)*(1.0-cleared)*exp(-bEdge*230.0);
 float la=(t-arrival)/0.032;float laser=fadePhase(t,2.4,2.42)*exp(-la*la)*exp(-bEdge*190.0);
 float etch=mark*0.12*cov;
 bool cutting=t>2.40001||(uB.w>0.5&&uA.y>0.001)||uD.y>0.0001;
 if(!cutting||(t<2.4&&wet<=0.0001)){return half4(float3(0.65,0.69,0.77)*etch,etch);}
 // Clear optics: refract in, up to two internal paths, three wavelengths on the way out.
 int g=assembled?-1:(bestK/2);
 float ior=2.42;
 float3 r=refract(ray,n,1.0/ior);float3 q=pos+r*0.0008;float3 colour=float3(0.0);float energy=1.0;
 for(int bounce=0;bounce<2;bounce++){
  float4 h=traceExit(q,r,g);
  if(h.w>9999.0){colour+=exitLight(q,r,b0,b1,b2)*energy;energy=0.0;break;}
  float3 hit=q+r*h.w;float3 outward=h.xyz;
  float3 leave=refract(r,-outward,ior);
  if(dot(leave,leave)>0.001){
   float f0=pow((ior-1.0)/(ior+1.0),2.0);float fres=f0+(1.0-f0)*pow(1.0-abs(dot(r,outward)),5.0);
   float3 rr=refract(r,-outward,2.40);float3 bb=refract(r,-outward,2.46);
   float3 light=exitLight(hit,leave,b0,b1,b2);
   if(dot(rr,rr)>0.001){light.r=exitLight(hit,rr,b0,b1,b2).r;}
   if(dot(bb,bb)>0.001){light.b=exitLight(hit,bb,b0,b1,b2).b;}
   colour+=light*energy*(1.0-fres);energy*=fres;
   break;
  }
  r=reflect(r,outward);q=hit+r*0.001;
 }
 float3 body=mix(float3(1.0),float3(0.97,0.92,1.0),uB.z);
 float3 clear=screenTx(pos,ray,n,2.42,b0,b1,b2)*body*0.96;
 float3 tail=clear+environment(normalize(toWorld(r,b0,b1,b2)));
 float3 through=(colour+tail*energy*0.78)*0.96*body;
 float fres=0.173+0.827*pow(1.0-abs(dot(nw,-camD)),5.0);
 float3 wp=worldPoint(pos,b0,b1,b2);
 float3 reflected=reflect(camD,nw);
 float3 reflection=environment(normalize(reflected+wp*float3(0.16,0.13,0.08)))+plaque(wp,reflected);
 float clearWeight=mix(0.52,0.72,pow(abs(dot(nw,-camD)),4.0));
 float3 radiance=mix(mix(through,clear,clearWeight),reflection,fres);
 if(!assembled){
  // Exposed cut faces have thickness: seen-through, lit, with a prismatic rim along the cut.
  float rim=exp(-bCap*105.0);
  float cutFace=1.0-smoothstep(0.10,0.32,abs(n.z));
  float3 thick=mix(float3(0.68,0.52,0.65),float3(1.05,0.93,1.12),sat(abs(nw.x)*0.65+abs(nw.y)*0.45));
  float3 prism=float3(1.0,0.86,1.13)+float3(0.15*sin(bCap*280.0),0.08,0.15*cos(bCap*280.0));
  radiance=mix(radiance,clear*0.48+thick,cutFace*0.9);
  radiance+=rim*prism*0.48;
 }
 float edgeGlow=exp(-bEdge*420.0);
 radiance+=edgeGlow*(reflection+environment(normalize(nw+float3(-0.1,0.4,0.2))))*0.16;
 float core=exp(-length(p-float2(185.0,289.0))/62.0)*uE.x*0.12;
 radiance+=float3(1.0,0.86,0.90)*core*cleared;
 float2 cam=camO.xy;
 float pb=(cam.x+cam.y*0.18-uD.x)/0.055;radiance+=float3(exp(-pb*pb)*uB.y*0.5);
 float3 crystal=pow(filmic(radiance),float3(1.0/2.2));
 if(t<2.4&&uD.y>0.0001){
  // Breath on frost: a damp veil softens and brightens the ice before it turns to clear glass.
  float reveal=smoothstep(0.55,0.96,wet);
  float floorFade=1.0-smoothstep(306.0,368.0,p.y);
  float4 sk=float4(skin.eval(p))*floorFade;
  float3 background=pow(backdrop(p),float3(1.0/2.2));
  float3 frost=background*(1.0-sk.a)+sk.rgb;
  float3 soft=(float3(skin.eval(p+float2(2.5,0.0)).rgb)+float3(skin.eval(p-float2(2.5,0.0)).rgb)+float3(skin.eval(p+float2(0.0,2.5)).rgb)+float3(skin.eval(p-float2(0.0,2.5)).rgb))*0.25*floorFade;
  float3 softened=background*(1.0-sk.a)+soft;
  float3 thin=mix(frost,max(frost,softened),0.65*wet);
  float3 damp=mix(thin,max(thin,float3(0.96,0.97,1.0)),0.36*sqrt(wet));
  float3 luma=float3(0.2126,0.7152,0.0722);
  float minimum=min(1.0,dot(frost,luma)+0.018);
  float lum=dot(crystal,luma);
  float3 clearGlass=mix(crystal,float3(1.0),sat((minimum-lum)/max(0.001,1.0-lum)));
  float rd=(breathDistance(p)-30.0)/2.2;float ring=exp(-rd*rd)*uD.y*0.045;
  float3 wetColour=mix(damp,clearGlass,reveal)+float3(0.90,0.95,1.0)*ring;
  float alpha=max(1.0-pow(1.0-wet,3.0),ring)*cov;
  return half4(wetColour*alpha,alpha);
 }
 float alpha=t<3.9?cov*cleared:cov;
 float3 outc=crystal*alpha;
 outc=outc*(1.0-etch)+float3(0.65,0.69,0.77)*etch;
 float lz=laser*0.7*cov;
 outc+=float3(lz*0.9,lz*0.84,lz*0.74);
 float flash=uE.w*cov;
 float3 extra=clamp(float3(1.0,0.88,0.93)*flash,0.0,1.0);
 float ea=max(extra.r,max(extra.g,extra.b));
 outc=outc*(1.0-ea)+extra;
 float lzA=min(1.0,lz);
 return half4(outc,min(1.0,alpha+(etch+ea+lzA-etch*ea)*(1.0-alpha)));
}
"""

    /** Soft refracted light under the cutting edges (CAUSTIC_SKSL), added with BlendMode.PLUS. */
    const val CAUSTIC = """
uniform float4 uA; // time, light, display, polish
half4 main(float2 p){
 float2 q=(p-float2(177.0,332.0))/float2(124.0,39.0);
 float mask=exp(-dot(q,q)*1.8);
 float a=sin(p.x*0.053+sin(p.y*0.095+uA.x*0.6283185)*2.0);
 float b=cos(p.y*0.108-p.x*0.024-uA.x*1.256637);
 float line=pow(max(0.0,1.0-abs(a+b)*0.58),12.0);
 float alpha=mask*line*uA.y*(0.07+uA.z*0.04+uA.w*0.055);
 return half4(float3(0.78,0.74,1.0)*alpha,alpha);
}
"""
}
