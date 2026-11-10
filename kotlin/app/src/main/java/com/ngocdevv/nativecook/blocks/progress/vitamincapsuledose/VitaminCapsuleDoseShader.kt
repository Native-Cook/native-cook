package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

/** The WGSL hero pass translated to AGSL. 48 outer / 20 transmission steps bound mobile cost. */
internal object VitaminCapsuleDoseShader {
    val source = """
uniform float4 uView;  // width, height, time, unused
uniform float4 uHero;  // centre, unit, shadow distance
uniform float4 uShape; // form A, form B, morph, reform
uniform float4 uOpen;  // separation, twist, hide inner beads, tilt
uniform float4 uFx;    // take, hold, yaw, pitch
float sq(float v) { return v*v; }
float3 lin(float3 c) { return pow(c,float3(2.2)); }
float hash3(float3 p) { return fract(sin(dot(p,float3(127.1,311.7,74.7)))*43758.5453); }
float hash1(float n) { return fract(sin(n*91.3458)*47453.5453); }
float noise3(float3 x) {
 float3 i=floor(x), f=fract(x), u=f*f*(3.-2.*f);
 return mix(mix(mix(hash3(i),hash3(i+float3(1,0,0)),u.x),mix(hash3(i+float3(0,1,0)),hash3(i+float3(1,1,0)),u.x),u.y),
 mix(mix(hash3(i+float3(0,0,1)),hash3(i+float3(1,0,1)),u.x),mix(hash3(i+float3(0,1,1)),hash3(i+float3(1,1,1)),u.x),u.y),u.z);
}
float fbm(float3 p) { return .6*noise3(p)+.3*noise3(p*2.03+7.1)+.1*noise3(p*4.1+3.3); }
float capsuleField(float3 p) { return length(p-float3(clamp(p.x,-.56,.56),0,0))-.44; }
float2 halves(float3 p) {
 float sep=uOpen.x, shell=smoothstep(.22,.4,sep);
 float db=max(capsuleField(p),p.x);
 db=mix(db,max(abs(capsuleField(p))-.035,p.x),shell);
 float3 q=p-float3(sep,0,0); float c=cos(uOpen.y), s=sin(uOpen.y);
 q=float3(q.x,c*q.y-s*q.z,s*q.y+c*q.z);
 float dw=max(capsuleField(q),-q.x);
 dw=mix(dw,max(abs(capsuleField(q))-.035,-q.x),shell);
 dw+=.01*exp(-sq(q.x/.012))*(1.-shell);
 return float2(min(db,dw),dw<db?0.:1.);
}
float tablet(float3 p) {
 float2 q=float2(length(p.xz)-.64,abs(p.y)-.07);
 float d=length(max(q,float2(0)))+min(max(q.x,q.y),0.)-.16;
 return d+.034*exp(-sq(p.x/.03))*smoothstep(.12,.2,p.y);
}
float roundCone(float3 p,float3 a,float3 b,float r1,float r2) {
 float3 ba=b-a; float l2=dot(ba,ba), rr=r1-r2, a2=l2-rr*rr, il2=1./l2;
 float3 pa=p-a; float y=dot(pa,ba), z=y-l2;
 float3 xv=pa*l2-ba*y; float x2=dot(xv,xv), y2=y*y*l2, z2=z*z*l2, k=sign(rr)*rr*rr*x2;
 if(sign(z)*a2*z2>k)return sqrt(x2+z2)*il2-r2;
 if(sign(y)*a2*y2<k)return sqrt(x2+y2)*il2-r1;
 return (sqrt(x2*a2*il2)+y*rr)*il2-r1;
}
float drop(float3 p) { return roundCone(p,float3(0,-.22,0),float3(0,.72,0),.6,.05)+.012*sin(p.y*7.+uView.z*3.); }
float3 pose(float3 p,float shape) {
 float3 q=p;
 if(shape<.5) { float c=cos(-uOpen.w), s=sin(-uOpen.w); q=float3(c*p.x-s*p.y,s*p.x+c*p.y,p.z); }
 else if(shape<1.5) {
  float c=cos(-1.05), s=sin(-1.05); q=float3(p.x,c*p.y-s*p.z,s*p.y+c*p.z);
  float c2=cos(.35), s2=sin(.35); q=float3(c2*q.x-s2*q.z,q.y,s2*q.x+c2*q.z);
 }
 return q;
}
float form(float3 p,float s) { float3 q=pose(p,s); if(s<.5)return halves(q).x; if(s<1.5)return tablet(q); return drop(q); }
float objectField(float3 p) {
 float sc=max(uShape.w,.02); float3 q=p/sc;
 return (mix(form(q,uShape.x),form(q,uShape.y),uShape.z)-.05*sin(uShape.z*3.14159))*sc;
}
float3 normalAt(float3 p) {
 float e=.0015; float3 a=float3(1,-1,-1), b=float3(-1,-1,1), c=float3(-1,1,-1), d=float3(1,1,1);
 return normalize(a*objectField(p+a*e)+b*objectField(p+b*e)+c*objectField(p+c*e)+d*objectField(p+d*e));
}
float2 material(float3 p) {
 float3 q=p/max(uShape.w,.02); float2 w=float2(0);
 for(int i=0;i<2;i++) {
  float s=i==1?uShape.y:uShape.x, wt=i==1?uShape.z:1.-uShape.z;
  float2 m=float2(0); if(s<.5)m=float2(halves(pose(q,0.)).y,0.); else if(s>1.5)m=float2(0,1);
  w+=m*wt;
 }
 return w;
}
float3 toWorld(float3 p) {
 float c=cos(uFx.z), s=sin(uFx.z); float3 q=float3(c*p.x+s*p.z,p.y,-s*p.x+c*p.z);
 c=cos(uFx.w); s=sin(uFx.w); return float3(q.x,c*q.y-s*q.z,s*q.y+c*q.z);
}
float3 toLocal(float3 p) {
 float c=cos(uFx.w), s=sin(uFx.w); float3 q=float3(p.x,c*p.y+s*p.z,-s*p.y+c*p.z);
 c=cos(uFx.z); s=sin(uFx.z); return float3(c*q.x-s*q.z,q.y,s*q.x+c*q.z);
}
float2 toScreen(float2 v) { return uHero.xy+float2(v.x,-v.y)*uHero.z; }
float shadow(float2 s) {
 float2 c=uHero.xy+float2(4,uHero.w), q=(s-c)/(float2(.95,.075)*uHero.z*max(uShape.w,.02));
 return .42*exp(-dot(q,q)*1.6)*(1.-uFx.x);
}
float3 behind(float2 s) { return mix(float3(1),lin(float3(.66,.79,.86)),shadow(s)); }
float3 studio(float3 r) {
 float3 c=mix(lin(float3(.5,.66,.92)),lin(float3(.92,.95,1)),smoothstep(-.6,.8,r.y));
 c+=float3(1,.99,.97)*5.*smoothstep(.9,.97,dot(r,normalize(float3(-.5,.75,.45))));
 c+=float3(.9,.95,1)*1.2*smoothstep(.88,.98,dot(r,normalize(float3(.8,.1,.6))));
 return mix(c,lin(float3(.36,.58,.96)),smoothstep(-.2,-.8,r.y)*.6);
}
float3 encode(float3 c) { return pow(clamp(c/(float3(1)+max(c-float3(.85),float3(0))*.6),float3(0),float3(1)),float3(1./2.2)); }
half4 main(float2 xy) {
 float2 v=float2(xy.x-uHero.x,uHero.y-xy.y)/uHero.z;
 if(dot(v,v)>2.4*2.4)return half4(0);
 float3 ro=toLocal(float3(0,0,12)), rd=toLocal(normalize(float3(v,-12)));
 float t=10.2; bool hit=false;
 for(int i=0;i<48;i++) { float dd=objectField(ro+rd*t); if(dd<.0008){hit=true;break;} t+=dd*.85; if(t>14.)break; }
 float sh=shadow(xy); float3 under=lin(float3(.66,.79,.86))*sh;
 if(!hit)return half4(half3(encode(sh>0.?under/max(sh,.0001):float3(0))*sh),half(sh));
 float3 p=ro+rd*t; float nz=fbm(p*3.2), edge=uFx.x*1.6-.15;
 if(uFx.x>0.&&nz<edge)return half4(half3(encode(under/max(sh,.0001))*sh),half(sh));
 float3 n=normalAt(p), nv=toWorld(n), vv=float3(0,0,1), rdv=toWorld(rd), L=normalize(float3(-.5,.75,.55));
 float2 mat=material(p); float cosv=max(dot(nv,vv),0.), fr=.04+.96*pow(1.-cosv,5.);
 float3 refl=studio(reflect(rdv,nv)); float spec=pow(max(dot(reflect(-L,nv),vv),0.),70.);
 float wrap=clamp((dot(nv,L)+.35)/1.35,0.,1.);
 float chalk=(uShape.y>.5&&uShape.y<1.5?1.:0.)*uShape.z+(uShape.x>.5&&uShape.x<1.5?1.:0.)*(1.-uShape.z);
 float speck=1.-.06*chalk*hash3(floor(p*140.));
 float3 white=lin(mix(float3(.97,.975,.985),float3(.74,.9,.81),chalk))*(.42+.62*wrap)*speck;
 white=mix(white,white*lin(float3(.8,.88,1)),smoothstep(.1,-.6,dot(nv,L))*.6);
 white+=refl*mix(.07,.03,chalk)+float3(1)*spec*mix(.55,.08,chalk);
 float tin=0.; float3 pin=p-n*.003, rin=refract(rd,n,1./mix(1.5,1.33,mat.y));
 for(int i=0;i<20;i++){float dd=-objectField(pin+rin*tin);if(dd<.001)break;tin+=max(dd*.9,.004);if(tin>3.)break;}
 float3 pex=pin+rin*tin, nex=normalAt(pex), rex=refract(rin,-nex,mix(1.5,1.33,mat.y));
 if(dot(rex,rex)<.01)rex=reflect(rin,-nex);
 float3 exv=toWorld(pex), rexv=toWorld(rex);
 float3 seen=behind(toScreen(exv.xy+rexv.xy*(.6+exv.z)/max(-rexv.z,.25)));
 float3 blueAbs=exp(-float3(2.2,.75,.04)*tin*1.7), clearAbs=exp(-float3(1.1,.42,.06)*tin*1.3);
 float3 through=seen*mix(blueAbs,clearAbs,mat.y);
 through=mix(through,through*lin(float3(.3,.6,1))*1.15,smoothstep(.22,.4,uOpen.x)*mat.x);
 if(mat.x>.5&&uOpen.z<.5) {
  float bt=1e5; float3 bn=float3(0); float sc=max(uShape.w,.02);
  for(int i=0;i<9;i++) {
   float fi=float(i); float3 q=float3(-.1-.55*hash1(fi+1.),(hash1(fi+2.)-.5)*.52,(hash1(fi+3.)-.5)*.52);
   float c=cos(uOpen.w), s=sin(uOpen.w); float3 bc=float3(c*q.x-s*q.y,s*q.x+c*q.y,q.z)*sc;
   float br=(.045+.025*hash1(fi+4.))*sc; float3 oc=pin-bc;
   float bb=dot(oc,rin), cc=dot(oc,oc)-br*br, h=bb*bb-cc;
   if(h>0.) {float tt=-bb-sqrt(h); if(tt>0.&&tt<tin&&tt<bt){bt=tt;bn=normalize(pin+rin*tt-bc);}}
  }
  if(bt<1e4) {
   float3 bnv=toWorld(bn); float3 bl=lin(float3(1,.8,.32))*(.5+.7*clamp(dot(bnv,L),0.,1.))+float3(1)*pow(max(dot(reflect(-L,bnv),vv),0.),40.)*.9;
   through=mix(through,bl*mix(float3(1),blueAbs,.35),.95);
  }
 }
 float3 caustic=lin(float3(.85,.95,1))*mat.y*.5*pow(max(dot(nv,normalize(float3(.4,-.7,.6))),0.),5.);
 float3 glass=fr*refl+(1.-fr)*(through+caustic)+float3(1)*spec*1.4;
 glass+=float3(.6,.8,1)*pow(1.-cosv,3.)*.12;
 glass=mix(glass,glass*lin(float3(.55,.72,.92)),mat.y*pow(1.-cosv,1.6)*.8);
 float3 col=mix(white,glass,clamp(mat.x+mat.y,0.,1.));
 if(uFx.x>0.)col+=float3(.7,.9,1)*(1.-smoothstep(0.,.06,nz-edge))*1.6;
 col+=float3(.5,.75,1)*uFx.y*.08*(1.-cosv);
 return half4(half3(encode(col)),1.);
}
""".trimIndent()
}
