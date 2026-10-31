import SwiftUI

// Six reusable slots, 256 particles each; the TypeGPU integrator transcribed to the CPU.
final class ClayPromptDust {
  struct Particle {
    var pos=SIMD2<Double>.zero, vel=SIMD2<Double>.zero, goal=SIMD2<Double>.zero
    var life=0.0,age=0.0,hue=0,size=0.0,mode=0,seed=0.0
  }
  var particles=[Particle](repeating:Particle(),count:1536)
  var spawned=0,lastTime:Double?
  static func hash(_ x:Double,_ y:Double)->Double { let v=sin(x*127.1+y*311.7)*43758.5453;return v-floor(v) }
  static func magnitude(_ v:SIMD2<Double>)->Double { sqrt(v.x*v.x+v.y*v.y) }
  func reset() { particles=[Particle](repeating:Particle(),count:1536);lastTime=nil;spawned=0 }
  func spawn(from:CGPoint,to:CGPoint,kind:Int,time:Double,blobR:Double) {
    let slot=spawned%6;spawned += 1
    for i in slot*256..<(slot+1)*256 {
      let fi=Double(i),r1=Self.hash(fi,time),r2=Self.hash(fi+17,time*1.3),r3=Self.hash(fi+41,time*0.7)
      let a=r1*6.2831853,dir=SIMD2(cos(a),sin(a)),disk=dir*sqrt(r2)
      var p=Particle();p.life=1;p.seed=r3;p.size=1.2+r3*r3*2.6
      if kind >= 0 {
        p.hue=kind;p.pos=SIMD2(from.x,from.y)+disk*12;p.goal=SIMD2(to.x,to.y)+disk*14
        let delta=SIMD2(to.x-from.x+0.001,to.y-from.y+0.001)
        let along=delta/Self.magnitude(delta),side=SIMD2(-along.y,along.x)
        p.vel=along*(120+r2*220)+side*((r1-0.5)*520)+SIMD2(0,-140-r3*160)
      } else {
        p.mode=1;p.hue=Int(floor(r3*3.999));p.pos=SIMD2(from.x,from.y)+dir*(blobR*(1.25+r2*0.9)+18)
        p.goal=SIMD2(from.x,from.y);p.vel=SIMD2(-dir.y,dir.x)*(260+r1*200)
      }
      particles[i]=p
    }
  }
  func update(_ time:Double,_ g:Double,_ clear:Double,_ cx:Double,_ cy:Double) {
    let dt=min(0.033,max(0,time-(lastTime ?? time)));lastTime=time
    guard dt>0 else { return }
    let smooth=ClayPromptMotion.smooth
    let blobR=ClayPromptMotion.look(g).blobR
    for i in particles.indices where particles[i].life>0 {
      var p=particles[i];p.age += dt
      if p.mode == 0 {
        let delta=p.goal-p.pos
        let wander=SIMD2(sin(p.age*9+p.seed*20),cos(p.age*7+p.seed*13))*260
        let pull=smooth(0,0.35,p.age)*30
        let acc=delta*pull-p.vel*(2.2+pull*0.18)+wander*(1-smooth(0.1,0.5,p.age))
        p.vel += acc*dt
        let near=smooth(26,4,Self.magnitude(delta))
        p.life=ClayPromptMotion.clamp01(p.life-dt*(0.45+4*near*smooth(0.25,0.4,p.age)))
      } else {
        let off=p.pos-SIMD2(cx,cy),r=max(Self.magnitude(off),0.001),dir=off/r
        let open=smooth(0.48,0.62,g)
        let start=max(blobR,24)*(1.35+0.5*p.seed),end=520*(0.6+p.seed),ringR=start+(end-start)*open
        let tangent=SIMD2(-dir.y,dir.x)
        let swirl=tangent*((520*(1-open*0.7))/(1+r/140))
        let hold=dir*((ringR-r)*(8+open*10))
        p.vel += (swirl+hold-p.vel*2.4)*dt
        p.life=ClayPromptMotion.clamp01(p.life-dt*(0.35+1.4*open))
      }
      p.pos += p.vel*dt;p.life *= 1-clear;particles[i]=p
    }
  }
  // Pre-rendered radial stamps use the reference fragment's two exponential lobes.
  static let stamps:[CGImage] = (0..<4).compactMap { hue in
    let colors=[SIMD3(1.0,0.63,0.35),SIMD3(0.44,0.64,1.0),SIMD3(1.0,0.85,0.42),SIMD3(0.45,0.85,0.51)]
    let n=64;var bytes=[UInt8](repeating:0,count:n*n*4)
    for y in 0..<n { for x in 0..<n {
      let u=(Double(x)+0.5)/Double(n)*8-4,v=(Double(y)+0.5)/Double(n)*8-4,r2=u*u+v*v
      let core=exp(-r2*1.6),glow=exp(-r2*0.22)*0.32
      let hot=colors[hue]+(SIMD3(repeating:1)-colors[hue])*(core*0.65)
      let rgb=hot*(core+glow),alpha=min(1,max(rgb.x,max(rgb.y,rgb.z)))
      let j=(y*n+x)*4
      bytes[j]=UInt8(min(255,max(0,rgb.x*255)));bytes[j+1]=UInt8(min(255,max(0,rgb.y*255)));bytes[j+2]=UInt8(min(255,max(0,rgb.z*255)));bytes[j+3]=UInt8(alpha*255)
    } }
    guard let provider=CGDataProvider(data:Data(bytes) as CFData) else { return nil }
    return CGImage(width:n,height:n,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:n*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedLast.rawValue),provider:provider,decode:nil,shouldInterpolate:true,intent:.defaultIntent)
  }
  func draw(_ context:inout GraphicsContext) {
    context.blendMode = .plusLighter
    for p in particles where p.life>0.004 {
      let r=p.size*(0.55+0.45*p.life)*4
      context.opacity=p.life
      context.draw(Image(decorative:Self.stamps[p.hue],scale:1),in:CGRect(x:p.pos.x-r,y:p.pos.y-r,width:2*r,height:2*r))
    }
  }
}
