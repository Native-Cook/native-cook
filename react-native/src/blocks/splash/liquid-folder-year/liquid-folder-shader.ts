export const LIQUID_FOLDER_SHADER = `uniform float uTime;
uniform float uRecovery;

float LFY_ease(float u) { u=clamp(u,0.0,1.0);return u*u*(3.0-2.0*u); }
float LFY_roundbox(float2 p,float2 b,float r) { float2 q=abs(p)-b+r;return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-r; }
float LFY_line(float2 p,float2 a,float2 b) { float2 pa=p-a,ba=b-a;return length(pa-ba*clamp(dot(pa,ba)/dot(ba,ba),0.0,1.0)); }
float LFY_ellipse(float2 p,float2 r) { float k0=length(p/r),k1=length(p/(r*r));return k0*(k0-1.0)/max(k1,0.0001); }
float LFY_two(float2 p) {
    float2 upper=p-float2(0.0,-0.35);
    float a=atan(upper.y,upper.x);
    float d=abs(LFY_ellipse(upper,float2(0.32,0.28)));
    if(a>0.8)d=min(length(p-float2(-0.32,-0.35)),length(p-float2(0.223,-0.149)));
    d=min(d,LFY_line(p,float2(0.223,-0.149),float2(-0.30,0.42)));
    float2 lower=p-float2(-0.16,0.42);
    float b=atan(lower.y,lower.x);
    float turn=abs(length(lower)-0.14);
    if(b<1.5707963||b>3.1415927)turn=min(length(p-float2(-0.30,0.42)),length(p-float2(-0.16,0.56)));
    d=min(d,turn);
    d=min(d,LFY_line(p,float2(-0.16,0.56),float2(0.30,0.56)));
    return d-0.125;
}
float LFY_year(float2 p) {
    float d=LFY_two(p-float2(-1.53,0.0));
    d=min(d,abs(LFY_ellipse(p-float2(-0.51,0.0),float2(0.31,0.59)))-0.125);
    d=min(d,LFY_two(p-float2(0.51,0.0)));
    float2 s=p-float2(1.53,0.0);
    float six=abs(LFY_ellipse(s-float2(0.0,0.25),float2(0.30,0.32)))-0.125;
    six=min(six,LFY_line(s,float2(-0.3,0.23),float2(-0.28,-0.31))-0.125);
    six=min(six,LFY_line(s,float2(-0.28,-0.31),float2(-0.11,-0.58))-0.125);
    six=min(six,LFY_line(s,float2(-0.11,-0.58),float2(0.25,-0.61))-0.125);
    return min(d,six);
}
float LFY_smin(float a,float b,float k) {
    float h=max(k-abs(a-b),0.0)/max(k,0.0001);
    return min(a,b)-h*h*k*0.25;
}
float LFY_fluid(float t) { return LFY_ease((t-1.75)/1.35)*(1.0-LFY_ease((t-6.25)/1.25)); }
float LFY_splash(float t) {
    return LFY_ease((t-1.85)/0.65)*(1.0-LFY_ease((t-3.4)/0.9))
        +LFY_ease((t-6.0)/0.45)*(1.0-LFY_ease((t-6.8)/0.65));
}
// y carries coverage, so a detached drop is born and dies with zero opacity.
float2 LFY_field(float2 p,float t,float recovery) {
    float morph=LFY_ease((t-2.65)/1.65)*(1.0-LFY_ease((t-5.9)/1.2))*recovery;
    float splash=LFY_splash(t)*recovery;
    float phase=t/8.8*6.2831853;
    float2 original=p;
    p+=splash*float2(sin(p.y*6.0-phase*3.0)*0.11+sin(p.y*11.0+phase*2.0)*0.045,
                     cos(p.x*5.0-phase*2.0)*0.12+sin(p.x*9.0+phase*3.0)*0.045);
    float2 f=p/float2(1.0+0.52*splash,1.0+0.15*splash);
    float front=LFY_roundbox(f-float2(0.0,0.22),float2(0.90,0.55),0.09);
    float tab=LFY_roundbox(f-float2(-0.43,-0.35),float2(0.47,0.18),0.05);
    float sheet=LFY_roundbox(f-float2(-0.04,-0.23),float2(0.57,0.67),0.035);
    float folder=min(front,min(tab,sheet));
    float d=mix(folder,LFY_year(p),morph)-splash*0.10;
    // Connected lobes pull out of the folder before separating into digit tubes.
    for(int i=0;i<6;i++) {
        float a=float(i)*2.399963+0.25*sin(phase*2.0+float(i));
        float2 center=float2(cos(a)*(0.53+1.02*splash),sin(a)*(0.35+0.46*splash));
        float radius=(0.04+0.27*splash)*splash;
        float lobe=length(p-center)-radius+(1.0-splash)*0.7;
        d=LFY_smin(d,lobe,max(0.0001,0.24*splash));
    }
    // Thin membrane holes create curved fluid folds rather than a flat crossfade.
    for(int i=0;i<3;i++) {
        float a=float(i)*2.094395+phase*0.45;
        float2 center=float2(cos(a)*0.78,sin(a)*0.42);
        float hole=length(p-center)-(0.10+0.08*sin(a*3.0+phase))*splash;
        d=max(d,-hole*splash-(1.0-splash)*2.0);
    }
    float coverage=1.0;
    for(int i=0;i<7;i++) {
        float u=clamp((t-2.15-float(i)*0.045)/1.7,0.0,1.0);
        float life=sin(u*3.14159265);life*=life*recovery;
        float a=float(i)*2.399963;
        float travel=LFY_ease(u);
        float2 center=float2(cos(a)*(0.55+1.75*travel),sin(a)*(0.30+0.83*travel)+0.18*travel*travel);
        float drop=length(original-center)-(0.045+mod(float(i),3.0)*0.018)*life;
        if(drop<d) { d=drop;coverage=LFY_ease(life/0.3); }
    }
    return float2(d,coverage);
}
float3 LFY_scene(float2 coord,float t,float recovery) {
    float2 p=(coord-float2(196.5,405.0))/68.0;
    float3 bg=float3(0.01176,0.01176,0.01961);
    if(abs(p.x)>2.65||abs(p.y)>1.7)return bg;
    float phase=t/8.8*6.2831853;
    float fluid=LFY_fluid(t)*recovery;
    float charge=LFY_ease((t-1.1)/0.9)*(1.0-LFY_ease((t-7.0)/0.8))*recovery;
    float2 field=LFY_field(p,t,recovery);
    float d=field.x;
    float angle=atan(p.y+0.12,p.x);
    float head=-3.14159265+6.2831853*LFY_ease((t-1.1)/1.0);
    float delta=atan(sin(angle-head),cos(angle-head));
    float trace=exp(-delta*delta*16.0)*LFY_ease((t-1.1)/0.18)*(1.0-LFY_ease((t-2.05)/0.35))*recovery;
    float glow=exp(-max(d,0.0)*13.0)*(0.12*charge+0.32*trace)*field.y;
    float3 aura=mix(float3(0.20,0.01,0.65),float3(0.70,0.03,0.95),smoothstep(-1.5,1.5,p.x));
    if(d>0.025)return bg+aura*glow;
    float e=0.005;
    float nx=(LFY_field(p+float2(e,0.0),t,recovery).x-LFY_field(p-float2(e,0.0),t,recovery).x)/(2.0*e);
    float ny=(LFY_field(p+float2(0.0,e),t,recovery).x-LFY_field(p-float2(0.0,e),t,recovery).x)/(2.0*e);
    float edge=1.0-smoothstep(0.0,0.30,max(0.0,-d));
    float3 n=normalize(float3(nx*edge,ny*edge,0.22+sqrt(clamp(-d/0.28,0.0,1.0))*0.95));
    float3 rainbow=mix(float3(0.0,0.90,0.79),float3(0.68,0.015,0.97),smoothstep(-1.25,1.25,p.x+0.24*sin(p.y*3.0-phase)*fluid));
    float3 rest=mix(float3(0.40,0.025,0.91),float3(0.13,0.035,0.20),smoothstep(-0.18,0.35,p.y));
    rest+=float3(0.40,0.0,0.55)*exp(-abs(p.x)*3.0)*smoothstep(-0.1,0.3,p.y);
    float3 base=mix(rest,rainbow,fluid);
    float diffuse=0.28+0.72*max(0.0,dot(n,normalize(float3(-0.6,-0.8,1.3))));
    float highlight=pow(max(0.0,dot(n,normalize(float3(-0.5,-0.7,1.0)))),28.0);
    float reflection=exp(-pow((n.x*0.7+n.y*0.4-0.28+0.16*sin(phase*2.0))*7.0,2.0));
    float3 col=base*diffuse+float3(0.86,0.98,1.0)*highlight*(0.28+0.72*fluid);
    col+=float3(0.55,0.93,1.0)*reflection*fluid*0.28;
    float rim=exp(-abs(d)*100.0);
    col+=rim*(float3(0.75,0.50,0.90)*(0.28+charge*0.35)+float3(1.0,0.91,1.0)*trace*1.3);
    float mask=(1.0-smoothstep(-0.012,0.012,d))*field.y;
    return mix(bg+aura*glow,clamp(col,0.0,1.0),mask);
}

float3 LFY_p3(float3 s) {
    float3 l=mix(s/12.92,pow((s+0.055)/1.055,float3(2.4)),step(float3(0.04045),s));
    l=float3(dot(l,float3(0.822592,0.177534,0.0)),dot(l,float3(0.033199,0.966783,0.0)),dot(l,float3(0.017085,0.072396,0.910302)));
    return mix(l*12.92,1.055*pow(max(l,float3(0.0)),float3(1.0/2.4))-0.055,step(float3(0.0031308),l));
}
half4 main(float2 p) { float3 c=LFY_scene(p,uTime,uRecovery);return half4(LFY_p3(c),1.0); }
`;
