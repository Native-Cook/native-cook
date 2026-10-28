#include <metal_stdlib>
using namespace metal;
struct PrismaticGameProfileVertex { float4 position [[position]]; float2 uv; };
struct PrismaticGameProfileUniforms {
 float4 bounds; float4 shape; float4 light; float4 avoidance;
 float4 optics; float4 canvas; float4 imageFit;
};
vertex PrismaticGameProfileVertex prismaticGameProfileVertex(uint index [[vertex_id]]) {
 float2 p = index==0 ? float2(-1,-1) : index==1 ? float2(3,-1) : float2(-1,3);
 return {float4(p,0,1),float2((p.x+1)*0.5,(1-p.y)*0.5)};
}
struct PrismaticGameProfileCardShader {

float4 bounds;
float4 shape;
float2 light;
float catchStrength;
float opticalStrength;
float peelCurl;
float4 peelAvoidance;

float roundedDistance(float2 p, float2 halfSize, float radius) {
  float2 q=abs(p)-halfSize+radius;
  return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float coverage(float distance) { return 1.0-smoothstep(-0.5,0.5,distance); }
// The only deforming region is a 20 pt blank lip along the top material edge.
// Its projected silhouette recedes as the warm underside rolls toward the face.
float topContour(float x, float width, float radius) {
  float corner=max(0.0,radius-min(x,width-x));
  return radius-sqrt(max(0.0,radius*radius-corner*corner));
}
float curlEnvelope(float x, float width) {
  float u=clamp(x/width,0.0,1.0);
  float envelope=smoothstep(0.0,0.18,u)*(1.0-smoothstep(0.82,1.0,u));
  if (peelAvoidance.z<=0.0 || peelAvoidance.w<=0.0) return envelope;
  float4 avoid=float4(peelAvoidance.xy-bounds.xy,peelAvoidance.zw);
  float top=topContour(x,width,shape.x);
  // The 10 pt guard includes the backing/cast's 4 pt translation, so every
  // additional channel clears rigid actors by at least 6 pt; a 24 pt taper
  // lets the rolled edge flatten gradually without forming a vertical notch.
  float horizontal=smoothstep(avoid.x-34.0,avoid.x-10.0,x)*
    (1.0-smoothstep(avoid.x+avoid.z+10.0,avoid.x+avoid.z+34.0,x));
  float vertical=smoothstep(avoid.y-34.0,avoid.y-10.0,top+26.0)*
    (1.0-smoothstep(avoid.y+avoid.w+10.0,avoid.y+avoid.w+34.0,top));
  return envelope*(1.0-horizontal*vertical);
}
float curlTrim(float x, float width) {
  return 7.0*peelCurl*curlEnvelope(x,width)*(0.72+0.28*clamp(x/width,0.0,1.0));
}
float peeledDistance(float2 p, float2 size, float radius) {
  float intact=roundedDistance(p-size*0.5,size*0.5,radius);
  if (p.y>radius+20.0) return intact;
  float trim=curlTrim(p.x,size.x);
  if (trim<=0.0) return intact;
  return roundedDistance(p-float2(0.0,trim)-size*0.5,size*0.5,radius);
}
// Ray/rounded-rectangle intersection keeps the source continuously on its real edge.
float2 perimeterSource(float2 halfSize, float radius, float angle) {
  float2 direction=float2(cos(angle),sin(angle));
  float2 absolute=abs(direction);
  float extent=min(halfSize.x/max(absolute.x,0.0001),halfSize.y/max(absolute.y,0.0001));
  float2 point=absolute*extent;
  float2 corner=halfSize-radius;
  if (point.x>corner.x && point.y>corner.y) {
    float projection=dot(absolute,corner);
    extent=projection+sqrt(max(0.0,projection*projection-dot(corner,corner)+radius*radius));
  }
  return direction*extent;
}
float sourceWindow(float2 delta, float2 normal, float tangentWidth, float normalWidth) {
  float t=dot(delta,float2(-normal.y,normal.x))/tangentWidth;
  float n=dot(delta,normal)/normalWidth;
  return exp(-0.5*(t*t+n*n));
}
float4 color(float2 pixel) {
  if (shape.z<=0.0) return float4(0.0);
  float2 stage=pixel+float2(0.0,40.0);
  float2 size=bounds.zw;
  float radius=shape.x;
  float2 p=stage-bounds.xy;
  float2 centre=size*0.5;
  float2 halfSize=size*0.5;
  float2 uv=p/size;
  if (p.x < -28.0 || p.x > size.x+28.0 || p.y < -28.0 || p.y > size.y+28.0) return float4(0.0);
  float formation=shape.w>0.5 ? 0.0 : smoothstep(0.80,0.98,shape.z);
  float eclipse=shape.w>0.5 ? 0.0 : smoothstep(0.72,0.98,shape.z);
  float optical=clamp(max(catchStrength,opticalStrength),0.0,1.0)*eclipse;
  float shift=light.y*size.x*0.09+light.x*size.y*0.072;
  float drive=clamp(shift/max(1.0,size.x*0.09+size.y*0.072),-1.0,1.0);
  // A shallow tilt stays on the corner arc; larger tilt explores the raised edge.
  float sourceDrive=drive*abs(drive);
  float frontDistance=roundedDistance(p-centre,halfSize,radius);
  if (peelCurl>0.0) frontDistance=peeledDistance(p,size,radius);
  float frontAlpha=coverage(frontDistance);
  float backDistance=roundedDistance(p-centre-float2(2.3,3.3),halfSize,radius);
  if (peelCurl>0.0) backDistance=peeledDistance(p-float2(2.3,3.3),size,radius);
  float backAlpha=coverage(backDistance);
  float shadowDistance=max(0.0,roundedDistance(p-centre-float2(1.5,4.0),halfSize,radius));
  if (peelCurl>0.0) shadowDistance=max(0.0,peeledDistance(p-float2(1.5,4.0),size,radius));
  float canvasEdge=min(min(pixel.x,393.0-pixel.x),min(pixel.y,690.0-pixel.y));
  float edgeFade=smoothstep(0.0,6.0,canvasEdge);
  float3 outputColor=float3(0.0);
  float outputAlpha=0.0;
  // Emission is behind the opaque card, never a bloom layer over face or text.
  if (frontAlpha<1.0 && eclipse>0.0) {
    float2 source=perimeterSource(halfSize,radius,-2.074+3.14159265*sourceDrive);
    float2 cornerDelta=max(abs(source)-(halfSize-radius),0.0)*sign(source);
    float2 normal=cornerDelta/max(length(cornerDelta),0.0001);
    float2 delta=p-centre-source;
    float outside=max(0.0,frontDistance);
    float aureole=exp(-0.5*outside*outside/81.0)*sourceWindow(delta,normal,58.0,36.0);
    float leak=exp(-outside/1.25)*sourceWindow(delta,normal,70.0,50.0);
    // Elongation follows the edge; the intact front clips it into a crescent.
    float bloom=sourceWindow(delta,normal,22.0,7.0);
    float warmAlpha=clamp(eclipse*(0.13+0.18*optical)*aureole+eclipse*(0.24+0.22*optical)*leak+0.58*optical*bloom,0.0,0.88)*edgeFade;
    float3 warmColor=mix(float3(0.94,0.87,0.99),float3(1.0,0.94,0.73),0.42+0.44*optical);
    float2 oppositeDelta=p-centre+source;
    float reflected=exp(-0.5*outside*outside/36.0)*sourceWindow(oppositeDelta,-normal,30.0,22.0);
    float reflectedAlpha=eclipse*(0.055+0.045*optical)*reflected*edgeFade;
    float3 reflectedColor=mix(float3(0.62,0.91,1.0),float3(0.79,0.72,1.0),0.5+0.5*drive);
    outputColor=reflectedColor*reflectedAlpha+warmColor*warmAlpha*(1.0-reflectedAlpha);
    outputAlpha=reflectedAlpha+warmAlpha*(1.0-reflectedAlpha);
  }
  float shadowAlpha=0.17*exp(-shadowDistance*shadowDistance/18.0)*edgeFade;
  outputColor=float3(0.055,0.065,0.09)*shadowAlpha+outputColor*(1.0-shadowAlpha);
  outputAlpha=shadowAlpha+outputAlpha*(1.0-shadowAlpha);
  float3 backColor=mix(float3(0.52,0.31,0.10),float3(0.86,0.64,0.28),clamp(uv.y,0.0,1.0));
  outputColor=backColor*backAlpha+outputColor*(1.0-backAlpha);
  outputAlpha=backAlpha+outputAlpha*(1.0-backAlpha);
  float opacity=shape.w>0.5 ? shape.z : smoothstep(0.015,0.17,shape.z);
  if (frontAlpha<=0.0) return float4(outputColor*opacity,outputAlpha*opacity);

  float innerDistance=roundedDistance(p-centre,halfSize-3.0,max(1.0,radius-3.0));
  if (peelCurl>0.0 && p.y<radius+20.0) {
    float trim=curlTrim(p.x,size.x);
    if (trim>0.0) innerDistance=roundedDistance(p-float2(0.0,trim)-centre,
      halfSize-3.0,max(1.0,radius-3.0));
  }
  float pearlAlpha=coverage(innerDistance);
  float goldValue=clamp(0.58+0.16*(1.0-uv.y)+0.14*(1.0-uv.x)+0.045*(light.x-light.y),0.0,1.0);
  float3 gold=mix(float3(0.74,0.49,0.17),float3(1.0,0.89,0.63),goldValue);
  float ridge=exp(-pow((frontDistance+1.05)/0.48,2.0));
  gold=mix(gold,float3(1.0,0.98,0.84),0.72*ridge);
  float3 pearl=mix(float3(1.0,0.995,0.979),float3(0.974,0.951,0.908),clamp(uv.y,0.0,1.0));
  pearl+=0.012*(light.x*(uv.x-0.5)-light.y*(uv.y-0.5));
  float lip=exp(-max(0.0,-innerDistance)/1.4);
  pearl*=1.0-0.026*lip;

  // The same tilted light plane crosses the lower pearl and the gold rim.
  float distance=(0.55*p.x+p.y-size.y*0.87-shift)/1.141271;
  float core=exp(-0.5*distance*distance/49.0);
  float shoulder=exp(-0.5*distance*distance/324.0);
  float filmPhase=distance*0.11+0.60*light.y-0.35*light.x;
  float3 spectrum=0.5+0.5*cos(float3(0.0,2.1,4.2)+filmPhase);
  float3 film=0.58+0.42*spectrum;
  float foil=formation*(0.50*core+0.22*shoulder);
  float lower=smoothstep(size.y*shape.y+1.0,size.y*shape.y+17.0,p.y);
  pearl=mix(pearl,film,foil*lower);
  pearl+=float3(0.08+0.035*optical)*formation*core*lower;
  gold=mix(gold,mix(film,float3(1.0),0.45),formation*(0.82*core+0.18*shoulder));
  float3 frontColor=clamp(mix(gold,pearl,pearlAlpha),0.0,1.0);
  if (peelCurl>0.0 && p.y<radius+26.0) {
    float envelope=curlEnvelope(p.x,size.x);
    float depth=20.0*peelCurl*envelope;
    float top=p.y-topContour(p.x,size.x,radius);
    float trim=curlTrim(p.x,size.x);
    float turn=clamp((top-trim)/max(0.5,depth-trim),0.0,1.0);
    // A convex warm reverse face, a narrow reflected rim and an occluded return.
    // Upper-left illumination casts onto the face below/right of the hinge.
    float reverseFace=smoothstep(trim+1.1,trim+2.4,top)*(1.0-smoothstep(depth-1.0,depth+0.5,top));
    float belly=exp(-pow((turn-0.28)/0.28,2.0));
    float returnShade=smoothstep(0.52,1.0,turn);
    float3 underside=mix(float3(0.88,0.79,0.62),float3(1.0,0.96,0.85),belly);
    underside*=1.0-0.19*returnShade;
    underside+=0.018*(1.0-uv.x);
    frontColor=mix(frontColor,underside,reverseFace*peelCurl);
    float reflectedRim=exp(-pow((top-trim-1.15)/0.52,2.0))*peelCurl*envelope;
    frontColor=mix(frontColor,float3(1.0,0.965,0.82),0.72*reflectedRim);
    float castShadow=smoothstep(depth-0.5,depth+0.6,top)*exp(-max(0.0,top-depth)/2.0);
    float contact=exp(-pow((top-depth-0.25)/0.65,2.0));
    float shadowSupport=1.0-smoothstep(depth+2.0,depth+3.0,top);
    float localShadow=(0.15*castShadow+0.13*contact)*peelCurl*envelope*shadowSupport;
    frontColor*=1.0-localShadow;
  }
  return float4((frontColor*frontAlpha+outputColor*(1.0-frontAlpha))*opacity,
    (frontAlpha+outputAlpha*(1.0-frontAlpha))*opacity);
}
};
fragment float4 prismaticGameProfileCard(PrismaticGameProfileVertex in [[stage_in]], constant PrismaticGameProfileUniforms &u [[buffer(0)]]) {
 PrismaticGameProfileCardShader s;
 s.bounds=u.bounds; s.shape=u.shape; s.light=u.light.xy;
 s.catchStrength=u.optics.x; s.opticalStrength=u.optics.y; s.peelCurl=u.optics.z; s.peelAvoidance=u.avoidance;
 return s.color(in.uv*u.canvas.xy);
}

struct PrismaticGameProfileBadgeShader {
 texture2d<float> artwork; sampler pictureSampler;
 float phase; float power; float kind; float4 fit;
 float4 sample(float2 p) {
  float2 uv=(p-fit.xy)/fit.zw;
  if(any(uv<0.0)||any(uv>1.0)) return float4(0.0);
  return float4(artwork.sample(pictureSampler,uv));
 }

 float4 samplePicture(float2 uv) {
  if(any(uv<0.0)||any(uv>1.0)) return float4(0.0);
  float4 value=artwork.sample(pictureSampler,uv);
  return float4(value.a>0.001 ? value.rgb/value.a:float3(0.0),value.a);
 }
float cross2(float2 a,float2 b) { return a.x * b.y - a.y * b.x; }
float triangleMask(float2 p,float2 a,float2 b,float2 c) {
  auto ab = b-a; auto bc = c-b; auto ca = a-c;
  auto distance = min(min(cross2(ab,p-a)/length(ab),cross2(bc,p-b)/length(bc)),cross2(ca,p-c)/length(ca));
  return smoothstep(-0.002,0.002,distance);
}
float4 diamond(float2 nativeUV) {
  float power = clamp(this->power,0.0,1.0);
  auto uv = (nativeUV-float2(0.5))*float2(1.0,512.0/408.0)+float2(0.5);
  auto base = samplePicture(uv);
  // The actual 512x408 artwork's table/girdle/pavilion junctions, in source UV.
  auto sourceSize = float2(512.0,408.0);
  auto leftTip = float2(80.0,169.0)/sourceSize;
  auto leftCrown = float2(145.0,105.0)/sourceSize;
  auto tableLeft = float2(197.0,120.0)/sourceSize;
  auto tableRight = float2(316.0,120.0)/sourceSize;
  auto rightCrown = float2(366.0,103.0)/sourceSize;
  auto rightTip = float2(431.0,169.0)/sourceSize;
  auto girdleLeft = float2(174.0,189.0)/sourceSize;
  auto girdleCentre = float2(256.0,189.0)/sourceSize;
  auto girdleRight = float2(338.0,189.0)/sourceSize;
  auto tip = float2(256.0,341.0)/sourceSize;
  auto upperLeft = min(1.0,triangleMask(uv,leftTip,leftCrown,tableLeft)+triangleMask(uv,leftTip,tableLeft,girdleLeft));
  auto upperCentre = min(1.0,triangleMask(uv,tableLeft,tableRight,girdleRight)+triangleMask(uv,tableLeft,girdleRight,girdleLeft));
  auto upperRight = min(1.0,triangleMask(uv,tableRight,rightCrown,rightTip)+triangleMask(uv,tableRight,rightTip,girdleRight));
  auto lowerLeft = triangleMask(uv,leftTip,girdleLeft,tip);
  auto centreLeft = triangleMask(uv,girdleLeft,girdleCentre,tip);
  auto centreRight = triangleMask(uv,girdleCentre,girdleRight,tip);
  auto lowerRight = triangleMask(uv,girdleRight,rightTip,tip);
  auto sum = upperLeft+upperCentre+upperRight+lowerLeft+centreLeft+centreRight+lowerRight;
  auto table = max(0.0,1.0-sum);
  auto total = max(0.001,table+sum);
  auto facet = (upperLeft+2.0*upperRight+3.0*upperCentre+4.0*(lowerLeft+centreLeft)+5.0*(centreRight+lowerRight))/total;
  auto plane = normalize((table*float3(0.0,-0.8,0.6)+upperLeft*float3(-0.65,-0.35,0.67)+
    upperCentre*float3(0.0,-0.4,0.91)+upperRight*float3(0.72,-0.3,0.62)+
    lowerLeft*float3(-0.72,0.48,0.5)+centreLeft*float3(-0.35,0.65,0.67)+
    centreRight*float3(0.36,0.65,0.67)+lowerRight*float3(0.72,0.48,0.5))/total);
  auto light = normalize(float3((-1.0+2.0*phase)*0.65+0.25,(-0.35+0.45*phase)*0.65-0.25,1.0));
  // Parent maps its material sweep to lightX=-1..1, including direct finger scrubbing.
  auto sweep = clamp(((-1.0+2.0*phase)+1.0)*0.5,0.0,1.0);
  auto facing = 0.5+0.5*cos(facet*1.72-sweep*6.2831853);
  auto response = smoothstep(0.24,0.92,facing)*power*(0.75+0.25*max(0.0,dot(plane,light)));
  auto shift = float2(1.5,-0.45)*response/max(1.0,114.0)*float2(1.0,512.0/408.0);
  auto red = samplePicture(uv+shift);
  auto blue = samplePicture(uv-shift);
  auto dispersed = float3(select(base.r,red.r,red.a>0.001),base.g,select(base.b,blue.b,blue.a>0.001));
  auto spectrum = float3(0.5)+0.5*cos(float3(0.0,2.1,4.2)+facet*1.13-sweep*4.2);
  auto tint = float3(0.15,0.76,1.0)+spectrum*float3(0.52,0.18,0.43);
  auto rgb = mix(base.rgb,dispersed,0.7*response);
  rgb = mix(rgb,tint,0.46*response);
  rgb += response*float3(0.08,0.15,0.18);
  rgb = clamp(rgb,float3(0.0),float3(1.0));
  // One contiguous, curved spectral fan below the actual tip. It is not an alpha-edge halo.
  auto point = nativeUV*114.0;
  auto dx = point.x-114.0*0.5;
  auto arcY = 114.0*(93.5/114.0)+0.0075*dx*dx;
  auto normal = (point.y-arcY)/sqrt(1.0+0.000225*dx*dx);
  auto q = dx/30.0;
  auto taper = exp(-q*q*q*q*1.6);
  auto band = exp(-0.5*normal*normal/(1.4*1.4));
  auto shoulder = exp(-0.5*normal*normal/(3.2*3.2));
  auto spectrumFan = exp(-0.5*pow((float3(normal)-float3(1.0,0.0,-1.0))/1.25,float3(2.0)));
  auto fanColour = mix(float3(0.30,0.85,1.0),spectrumFan/max(max(spectrumFan.r,spectrumFan.g),max(spectrumFan.b,0.001)),0.8);
  auto fanAlpha = power*taper*(0.52*band+0.07*shoulder)*(1.0-base.a);
  return float4(rgb*base.a+fanColour*fanAlpha,base.a+fanAlpha);
}
float4 gold(float2 p) {
 float4 src = sample(p); float a = src.a;
  float3 base = a > 0.001 ? float3(src.rgb) / a : float3(0.0); float2 uv = p / float2(114.0,126.0);
  float metal = smoothstep(0.05,0.24,base.r-base.b) * smoothstep(0.02,0.18,base.g-base.b);
  float travel=clamp((phase-0.10)/0.55,0.0,1.0);
  float x=0.20+0.65*(1.0-pow(1.0-travel,3.0));
  float y=0.48-0.05*sin(travel*3.141593);
  float2 d=uv-float2(x,y);
  float core=exp(-dot(d/float2(0.034,0.034),d/float2(0.034,0.034))*1.4);
  float strike=exp(-pow((d.x+0.62*d.y)/0.014,2.0))*exp(-pow(d.y/0.12,2.0));
  float cross=exp(-pow((d.x-1.15*d.y)/0.012,2.0))*exp(-pow(d.x/0.095,2.0));
  float shoulder=exp(-dot(d/float2(0.13,0.16),d/float2(0.13,0.16))*1.8);
  float baseCatch=exp(-pow((uv.x-(0.22+0.62*phase))/0.14,2.0)-pow((uv.y-0.745)/0.024,2.0));
  float specular=power*metal*(0.98*core+0.92*strike+0.58*cross);
  float3 gold=mix(base,float3(1.0,0.99,0.80),clamp(specular,0.0,0.98));
  gold+=power*metal*(baseCatch*float3(0.30,0.23,0.08)+shoulder*float3(0.16,0.12,0.035));
  return float4(clamp(gold,0.0,1.0)*a,a); }
float4 enamel(float2 p) {
 float4 src = sample(p); float a = src.a;
  float3 base = a > 0.001 ? float3(src.rgb) / a : float3(0.0); float2 uv = p / float2(114.0,126.0);
  float4 horizontal=sample(p+float2(2.3,0.0));
  float4 vertical=sample(p+float2(0.0,2.3));
  float3 hx=horizontal.rgb/max(horizontal.a,0.001); float3 vy=vertical.rgb/max(vertical.a,0.001);
  float boundary=max(length(base-hx),length(base-vy));
  float bevel=smoothstep(0.025,0.19,boundary);
  float angle = (atan2(uv.y-0.49,uv.x-0.5)+3.141593)/6.283186;
  float delta = abs(angle-phase); delta=min(delta,1.0-delta);
  float tracing = exp(-pow(delta/0.15,2.0));
  float teal = smoothstep(0.04,0.22,base.b-base.r) * smoothstep(0.0,0.08,base.g-base.b*0.72);
  float inset = exp(-pow((length((uv-float2(0.50,0.48))*float2(1.0,0.78))-(0.43-0.32*phase))/0.065,2.0));
  float enamel = power*teal*(0.98*tracing*(0.78+0.22*bevel)+0.32*inset);
  float3 color = mix(base,float3(0.84,1.0,0.99),clamp(enamel,0.0,0.96));
  return float4(color*a,a); }
float4 prism(float2 p) {
 float4 src = sample(p); float a = src.a;
  float3 base = a > 0.001 ? float3(src.rgb) / a : float3(0.0); float2 uv = p / float2(114.0,126.0);
  float facet=uv.y<0.355 ? 0.0 : uv.x<0.38 ? 1.0 : uv.x>0.65 ? 2.0 : uv.y<0.45 ? 3.0 : uv.x<0.51 ? 4.0 : 5.0;
  float facing = 0.5+0.5*cos(facet*1.72-phase*6.283186);
  float edge = smoothstep(0.24,0.92,facing)*power;
  float shift = 1.5*edge;
  float4 r = sample(p+float2(shift,-0.3*shift));
  float4 b = sample(p-float2(shift,-0.3*shift));
  float3 dispersed = float3(r.r/max(r.a,0.001),base.g,b.b/max(b.a,0.001));
  float3 spectrum = 0.5+0.5*cos(float3(0.0,2.1,4.2)+facet*1.13-phase*4.2);
  float3 color = mix(base,dispersed,0.7*edge);
  color=mix(color,float3(0.15,0.76,1.0)+spectrum*float3(0.52,0.18,0.43),0.42*edge);
  color+=edge*float3(0.08,0.15,0.18);
  return float4(clamp(color,0.0,1.0)*a,a); }

  float4 caustic(float2 p) {
    float dx=p.x-79.0;
    float arcY=mix(122.0,115.5,kind)+0.0075*dx*dx;
    float normal=(p.y-arcY)/sqrt(1.0+0.000225*dx*dx);
    float taper=exp(-pow(dx/30.0,4.0)*1.6);
    float band=exp(-0.5*pow(normal/1.4,2.0));
    float shoulder=exp(-0.5*pow(normal/3.2,2.0));
    float3 spectrum=float3(exp(-0.5*pow((normal-1.0)/1.25,2.0)),
      exp(-0.5*pow(normal/1.25,2.0)),exp(-0.5*pow((normal+1.0)/1.25,2.0)));
    spectrum/=max(max(spectrum.r,spectrum.g),max(spectrum.b,0.001));
    float3 color=mix(float3(1.0,0.81,0.30),spectrum,kind);
    float a=power*taper*(0.48*band+0.07*shoulder);
    return float4(color*a,a);
  }
};
fragment float4 prismaticGameProfileBadge(PrismaticGameProfileVertex in [[stage_in]], constant PrismaticGameProfileUniforms &u [[buffer(0)]], texture2d<float> image [[texture(0)]]) {
 constexpr sampler s(address::clamp_to_zero,filter::linear);
 PrismaticGameProfileBadgeShader b{image,s,u.optics.x,u.optics.y,u.optics.z,u.imageFit};
 float2 p=in.uv*u.canvas.xy;
 if(u.optics.w>1.5) return b.diamond(in.uv);
 if(u.optics.w>0.5) return b.caustic(p);
 return u.optics.z<0.5 ? b.gold(p) : u.optics.z<1.5 ? b.enamel(p) : b.prism(p);
}
