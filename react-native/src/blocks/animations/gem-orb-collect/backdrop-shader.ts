// Indigo atmosphere: sparse dust, six soft prism flecks and seven widening light shafts.
export const BACKDROP_SKSL = `
uniform float2 uSize;
uniform float2 uGem;
uniform float uRadius;
uniform float uYaw;
uniform float uPitch;
uniform float uCharge;
uniform float uFlare;
uniform float uHush;
uniform float uShock;
uniform float uBeams;
uniform float uPhase;
uniform float uPixelRatio;
uniform float3 uTop;
uniform float3 uMid;
uniform float3 uBloom;
uniform float3 uDust;
const float TAU = 6.28318530718;
float squareB(float v) { return v*v; }
float hashB(float2 p) {
  float3 q = fract(float3(p.x,p.y,p.x)*0.1031);
  q += dot(q,q.yzx+33.33);
  return fract((q.x+q.y)*q.z);
}
float3 spectrumB(float v) {
  float3 cool = mix(float3(0.14,0.94,1.0),float3(0.24,0.35,1.0),smoothstep(-0.85,-0.15,v));
  float3 warm = mix(float3(0.72,0.48,1.0),float3(1.0,0.27,0.65),smoothstep(0.15,0.85,v));
  return mix(cool,warm,smoothstep(-0.15,0.15,v));
}
float3 dustB(float2 p) {
  // Forty cells, each with one mote. The 36% inset keeps every glow inside its cell,
  // including drift/parallax, so there are no neighbour loops or clipped seams.
  float2 pageSize = max(uSize,float2(1.0));
  float2 cellSize = pageSize/float2(5.0,8.0);
  // Independently stagger each column; empty cell margins hide the coordinate seam.
  p.y = mod(p.y+hashB(float2(floor(p.x/cellSize.x),52.0))*cellSize.y,pageSize.y);
  float2 cellId = floor(p/cellSize);
  if (cellId.x<0.0 || cellId.x>=5.0 || cellId.y<0.0 || cellId.y>=8.0) return float3(0.0);
  float seed = hashB(cellId+7.31);
  float seedY = hashB(cellId+29.7);
  float depth = mod(cellId.x+cellId.y*5.0,3.0);
  float scaleB = min(pageSize.x/402.0,pageSize.y/874.0);
  float parallax = depth<0.5 ? 4.0 : (depth<1.5 ? 8.0 : 14.0);
  float2 home = (cellId+0.36+0.28*float2(seed,seedY))*cellSize;
  home += scaleB*(float2(sin(uPhase+seed*TAU),cos(uPhase*2.0+seedY*TAU))*2.4
    - float2(sin(uYaw),sin(uPitch))*parallax);
  float2 delta = (p-home)/max(scaleB,0.01);
  float d2 = dot(delta,delta);
  // Exact finite support also makes the cell lookup safe under the shock's warp.
  if (d2>81.0) return float3(0.0);
  // Five of the thirteen near cells carry a larger defocused mote.
  float nearSoft = depth>1.5 && mod(cellId.x+cellId.y*5.0,9.0)<3.0 ? 1.0 : 0.0;
  float core = 0.50+depth*0.36+nearSoft*1.15;
  float twinkle = 0.62+0.38*squareB(sin(uPhase*(1.0+depth)+seed*TAU));
  float glow = exp(-d2/(core*core))*(0.44-0.23*nearSoft)
    + exp(-d2/(core*core*11.0))*(0.075-0.025*nearSoft);
  float3 tint = mix(uDust,float3(0.30,0.45,1.0),seedY*0.40);
  return tint*glow*twinkle*(0.44+depth*0.24)*(1.0-smoothstep(36.0,81.0,d2));
}
half4 main(float2 xy) {
  float r = max(uRadius,1.0);
  float k = r/104.0;
  float y = xy.y/max(uSize.y,1.0);
  float3 sky = mix(uTop,uMid,smoothstep(0.0,0.45,y));
  sky *= 1.0-smoothstep(0.45,0.62,y);
  sky *= 1.0-0.30*uHush;
  float2 delta = xy-uGem;
  float radial = length(delta);
  float2 radialDir = delta/max(radial,0.001);
  float ringRadius = mix(0.9*r,1.1*length(uSize*0.5),1.0-squareB(1.0-uShock));
  float ringOffset = radial-ringRadius;
  float ringLife = smoothstep(0.0,0.035,uShock)*(1.0-smoothstep(0.45,0.90,uShock));
  float warp = 0.0;
  if (ringLife>0.0) warp = ringLife*9.0*k*exp(-squareB(ringOffset/(12.0*k)));
  float2 warped = xy-radialDir*warp;
  float bloomDist = length(warped-uGem)/r;
  float bloomWidth = 1.10*(1.0-0.24*uHush);
  float3 bloomTint = mix(uBloom,float3(0.35,0.12,0.86),uCharge*0.55);
  // At one radius the envelope is 18% before collection and 45% when charged.
  float bloom = (0.25+0.20*uCharge)*(0.55*exp(-squareB(bloomDist/bloomWidth)*0.65)+0.45*exp(-bloomDist*1.5))*(1.0-0.50*uHush);
  sky += bloomTint*bloom*(1.0-smoothstep(0.48,0.64,y));
  float2 poolPoint = (warped-uGem-float2(0.0,1.75*r))/float2(1.2*r,0.25*r);
  float pool = exp(-dot(poolPoint,poolPoint)*2.0)*0.14*uCharge*(1.0-0.65*uHush);
  sky += bloomTint*pool;
  float2 dustPoint = uGem+(warped-uGem)/max(1.0-0.14*uHush,0.01);
  sky += dustB(dustPoint)*(1.0-0.28*uHush);
  // A 14-point soft wake follows two separated, two-point spectral crests.
  if (ringLife>0.0 && abs(ringOffset)<65.0*k) {
    float ringWhite = exp(-squareB((ringOffset+1.0*k)/max(1.2*k,0.1)));
    float ringRed = exp(-squareB((ringOffset-2.5*k)/max(1.5*k,0.1)));
    float ringBlue = exp(-squareB((ringOffset+2.5*k)/max(1.5*k,0.1)));
    sky += ringLife*(1.0-0.70*uShock)*(float3(0.68,0.75,1.0)*ringWhite*0.19
      + float3(1.0,0.24,0.52)*ringRed*0.19+float3(0.13,0.83,1.0)*ringBlue*0.23
      +float3(0.39,0.29,0.88)*exp(-squareB((ringOffset+6.0*k)/(8.4*k)))*0.15);
  }
  // End caustics above copy: contract does not expose titleTop; the design-size
  // title is 2.27 radii below the gem. Use the stricter of that and the 3.2r mask.
  float copyMask = 1.0-smoothstep(uGem.y+1.82*r,uGem.y+2.15*r,xy.y);
  float farMask = 1.0-smoothstep(uGem.y+3.2*r,uGem.y+3.4*r,xy.y);
  if (uCharge>0.001 && radial>1.30*r && radial<2.35*r && copyMask>0.0) {
    float3 caustic = float3(0.0);
    for (int i=0;i<6;i++) {
      float n = float(i);
      float a = n*2.399963+uYaw*(0.32+0.065*n)+uPitch*sin(n*1.9)*0.65;
      float2 dir = float2(cos(a),sin(a));
      float orbit = (1.60+0.46*(0.5+0.5*sin(n*3.1+uPitch*1.4+uYaw*0.4)))*r;
      float2 q = xy-uGem-dir*orbit;
      float along = dot(q,dir)/(k*(13.0+2.2*n));
      float across = dot(q,float2(-dir.y,dir.x))/(k*(5.0+0.8*n));
      float lens = along*along+across*across;
      float shapeB = exp(-lens*1.8)*(1.0-smoothstep(0.65,1.45,lens));
      float breathe = 0.72+0.28*sin(uPhase+n*1.8+uYaw*(0.6+n*0.09)+uPitch*2.0);
      caustic += spectrumB(along*1.5)*shapeB*breathe;
    }
    sky += caustic*0.16*uCharge*copyMask*farMask;
  }
  if (uBeams>0.001 && radial>0.94*r) {
    float3 beamLight = float3(0.0);
    float lowerMask = mix(1.0,0.12,smoothstep(uGem.y+2.15*r,uGem.y+2.70*r,xy.y));
    // Seven main shafts plus three faint wide shafts between them; ten fixed iterations.
    for (int j=0;j<10;j++) {
      float n = float(j);
      float wide = j>=7 ? 1.0 : 0.0;
      float a = uYaw+(j<7 ? n*TAU/7.0+0.10*sin(n*3.0) : (n-6.5)*TAU/3.0);
      float2 dir = float2(cos(a),sin(a));
      float along = dot(delta,dir);
      float across = dot(delta,float2(-dir.y,dir.x));
      // Compute the distance to the page edge along this shaft, guarding near-axis directions.
      float2 edge = mix(uGem,uSize-uGem,step(float2(0.0),dir))/max(abs(dir),float2(0.001));
      float edgeAlong = min(edge.x,edge.y);
      float spread = clamp((along-r)/max(edgeAlong-r,1.0),0.0,1.0);
      float widthB = mix(3.0*k,k*(16.0+mod(n*7.1,20.0)),spread)*(1.0+2.2*wide);
      float side = across/max(widthB,0.1);
      if (along>0.95*r && abs(side)<2.5) {
        float beamProfile = exp(-side*side*1.3)*(1.0-smoothstep(1.8,2.5,abs(side)));
        float beamCore = exp(-side*side*4.5);
        float reach = length(uSize)*(0.40+0.065*mod(n*1.9,4.0));
        float envelope = smoothstep(0.95*r,1.06*r,along)*exp(-max(along-r,0.0)/max(reach*0.48,1.0));
        float3 beamTint = mix(float3(0.16,0.90,1.0),float3(1.0,0.26,0.70),smoothstep(-0.8,0.8,side));
        beamLight += (beamTint*beamProfile*0.27+float3(0.95,0.89,1.0)*beamCore*0.28)
          *envelope*(0.66+0.34*squareB(cos(n*1.8)))*(1.0-0.94*wide);
      }
    }
    sky += beamLight*uBeams*lowerMask;
  }
  sky += uFlare*(float3(0.40,0.26,0.66)*exp(-squareB(radial/(1.4*r)))*0.33
    +float3(0.07,0.045,0.12)*(1.0-smoothstep(0.30,0.72,y)));
  // Screen-locked triangular dither in physical pixels; no animated grain or lifted black floor.
  float2 pixel = floor(xy*uPixelRatio);
  float grain = (hashB(pixel+0.17)-hashB(pixel+71.3))/255.0;
  float lit = smoothstep(0.0,0.025,max(sky.r,max(sky.g,sky.b)));
  return half4(clamp(sky+grain*lit,0.0,1.0),1.0);
}
`;
