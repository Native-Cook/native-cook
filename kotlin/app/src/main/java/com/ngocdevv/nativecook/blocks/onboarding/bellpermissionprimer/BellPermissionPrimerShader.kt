package com.ngocdevv.nativecook.blocks.onboarding.bellpermissionprimer

internal object BellPermissionPrimerShader {
    val BLOB = """
uniform float2 center;
uniform float size;
uniform float index;
half4 main(float2 xy) {
 float2 q=(xy-center)/size;
 float g=length(q*float2(1.,1.15))-.62+.08*sin(atan(q.y,q.x)*3.+index);
 float a=(1.-smoothstep(-.04,.04,g))*.55;
 return half4(half3(float3(.75,.86,1.)*a),half(a));
}
""".trimIndent()
    val SOURCE = """
uniform float2 hero;
uniform float unit;
uniform float4 pose;
uniform float2 lift;
uniform float4 island;
uniform float4 slab;
uniform float bob;
float smin(float a,float b,float k) { float h=clamp(.5+.5*(b-a)/k,0.,1.); return mix(b,a,h)-k*h*(1.-h); }
float smax2(float a,float b,float k) { float h=clamp(.5-.5*(b-a)/k,0.,1.); return mix(b,a,h)+k*h*(1.-h); }
float cone(float3 p,float3 a,float3 b,float r1,float r2) {
 float3 ba=b-a; float l2=dot(ba,ba); float rr=r1-r2; float a2=l2-rr*rr; float il2=1./l2;
 float3 pa=p-a; float y=dot(pa,ba); float z=y-l2; float3 xv=pa*l2-ba*y; float x2=dot(xv,xv); float y2=y*y*l2; float z2=z*z*l2; float k=sign(rr)*rr*rr*x2;
 if(sign(z)*a2*z2>k) return sqrt(x2+z2)*il2-r2;
 if(sign(y)*a2*y2<k) return sqrt(x2+y2)*il2-r1;
 return (sqrt(x2*a2*il2)+y*rr)*il2-r1;
}
float segment(float3 p,float3 a,float3 b) { float3 pa=p-a, ba=b-a; return length(pa-ba*clamp(dot(pa,ba)/dot(ba,ba),0.,1.)); }
float2 bell(float3 p) {
 float outer=smin(length(p-float3(0.,-.55,0.))-.34,cone(p,float3(0.,-.58,0.),float3(0.,-1.2,0.),.33,.6),.16);
 float inner=cone(p,float3(0.,-.66,0.),float3(0.,-1.34,0.),.27,.56);
 float body=max(smax2(outer,-inner,.02),-(p.y+1.27)); float2 d=float2(body,(abs(inner)<abs(outer)&&p.y<-.6)?3.:0.);
 float lip=length(float2(length(p.xz)-.585,p.y+1.26))-.045; if(lip<d.x) d=float2(lip,1.);
 float crown=length(float2(length(p.xy-float2(0.,-.11))-.1,p.z))-.035; if(crown<d.x) d=float2(crown,1.);
 float rel=pose.y-pose.x; float3 tip=float3(sin(rel)*.7,-.62-cos(rel)*.7,0.);
 float cl=smin(segment(p,float3(0.,-.62,0.),tip)-.018,length(p-tip)-.11,.03); if(cl<d.x) d=float2(cl,2.); return d;
}
float3 object(float3 p) {
 float2 center=mix(hero+float2(0.,.85*unit+bob)+lift,island.xy+island.zw*.5,pose.z)/unit;
 float sc=mix(1.,.03,pose.z); float3 q=float3(p.x-center.x,-(p.y-center.y),p.z)/sc;
 float ya=-.22*(1.-pose.z), pa=-.12*(1.-pose.z); float cy=cos(ya),sy=sin(ya),cp=cos(pa),sp=sin(pa);
 q=float3(cy*q.x+sp*sy*q.y-cp*sy*q.z,cp*q.y+sp*q.z,sy*q.x-sp*cy*q.y+cp*cy*q.z);
 q.y-=.85; float cs=cos(pose.x*(1.-pose.z)),sn=sin(pose.x*(1.-pose.z)); q.xy=float2(cs*q.x+sn*q.y,-sn*q.x+cs*q.y);
 float2 b=bell(q); float db=b.x*sc; float4 r=mix(island,slab,pose.w); float2 c=(r.xy+r.zw*.5)/unit; float2 h=r.zw*.5/unit; float rad=min(h.y,mix(h.y,34.*(island.z/125.)/unit,pose.w));
 float2 v=abs(p.xy-c)-h+rad; float d2=length(max(v,0.))+min(max(v.x,v.y),0.)-rad; float2 w=float2(d2,abs(p.z)-.05); float di=min(max(w.x,w.y),0.)+length(max(w,0.));
 float crown=(center.y*unit-.95*unit*sc)/unit; float gap=max(crown-(island.y+island.w)/unit,0.); float k=(.55*(1.-smoothstep(.15,1.6,gap))+.4*pose.z)*(1.-pose.w);
 float hmix=k<.002 ? (di<db?0.:1.) : clamp(.5+.5*(di-db)/k,0.,1.);
 return float3(k<.002?min(db,di):mix(di,db,hmix)-k*hmix*(1.-hmix),b.y,1.-hmix);
}
float3 normalAt(float3 p) { float e=.0015; float3 a=float3(1.,-1.,-1.),b=float3(-1.,-1.,1.),c=float3(-1.,1.,-1.),d=float3(1.,1.,1.); return normalize(a*object(p+a*e).x+b*object(p+b*e).x+c*object(p+c*e).x+d*object(p+d*e).x); }
float3 lin(float3 c) { return pow(c,float3(2.2)); }
float3 studio(float3 r) {
 float3 c=mix(lin(float3(.42,.62,.98)),lin(float3(.97,.98,1.)),smoothstep(-.5,.7,r.y));
 float3 k=normalize(float3(-.5,.78,.4));
 c+=float3(1.,.99,.97)*4.*smoothstep(.88,.97,dot(r,k));
 float3 f=normalize(float3(.85,.2,.5));
 c+=float3(.9,.95,1.)*smoothstep(.86,.98,dot(r,f));
 c=mix(c,lin(float3(.25,.42,.98)),(1.-smoothstep(-.7,0.,r.y))*.7);
 return c;
}
float3 encode(float3 c) { return pow(clamp(c/(float3(1.)+max(c-float3(.85),float3(0.))*.6),0.,1.),float3(1./2.2)); }
half4 main(float2 xy) {
 float2 bc=mix(hero+float2(0.,.85*unit+bob)+lift,island.xy+island.zw*.5,pose.z);
 float scale=mix(1.,.03,pose.z); float4 rect=mix(island,slab,pose.w);
 float2 lo=min(bc-float2(1.,1.25)*unit*scale,rect.xy)-unit*.6;
 float2 hi=max(bc+float2(1.,.75)*unit*scale,rect.xy+rect.zw)+unit*.6;
 if(xy.x<lo.x || xy.y<lo.y || xy.x>hi.x || xy.y>hi.y) return half4(0.);
 float3 ro=float3(xy/unit,12.); float t=10.; bool hit=false;
 for(int i=0;i<64;i++) { float d=object(ro+float3(0.,0.,-t)).x; if(d<.0008) {hit=true;break;} t+=d*.85; if(t>14.5) break; }
 float3 shd=object(float3(xy.x/unit-.02,xy.y/unit-.12,0.));
 float sh=.18*(1.-smoothstep(-.05,.4,shd.x))*(1.-shd.z)*(1.-pose.z);
 if(!hit) { float3 shadow=encode(lin(float3(.36,.45,.62))); return half4(half3(shadow*sh),half(sh)); }
 float3 p=ro+float3(0.,0.,-t), ob=object(p), n=normalAt(p); n.y=-n.y;
 float3 rd=float3(0.,0.,-1.), vv=float3(0.,0.,1.);
 float3 light=normalize(float3(-.5,.75,.55));
 float3 refl=studio(reflect(rd,n));
 float spec=pow(max(dot(reflect(-light,n),vv),0.),60.);
 float wrap=clamp((dot(n,light)+.4)/1.4,0.,1.);
 float3 col; float m=ob.y; if(ob.z>.04 && m>.5) m=0.;
 if(m<.5) {
  col=lin(float3(.97,.975,.99))*(.3+.72*wrap);
  col=mix(col,col*lin(float3(.62,.74,1.)),(1.-smoothstep(-.7,.2,dot(n,light)))*.85);
  col+=refl*.08+float3(1.)*spec*.6;
 } else if(m<1.5||m>2.5) {
  float3 gold=lin(float3(.96,.76,.26)); float inside=m>2.5?.55:1.;
  col=gold*(.25+.45*wrap)*inside+refl*gold*.85+float3(1.,.95,.8)*spec*.8;
 } else {
  col=lin(float3(.95,.48,.63))*(.4+.65*wrap)+refl*.06+float3(1.)*spec*.7;
 }
 float sheen=pose.z*(1.-pose.w)*(1.-pose.w);
 float3 black=(refl*.05+float3(1.)*spec*.25)*sheen;
 col=mix(col,black,clamp(ob.z+pose.z*.6,0.,1.));
 return half4(half3(encode(col)),1.);
}
""".trimIndent()
}
