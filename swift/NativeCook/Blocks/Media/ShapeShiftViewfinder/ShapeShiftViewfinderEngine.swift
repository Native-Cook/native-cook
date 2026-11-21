import Foundation

/// Live state owns targets separately from presentation: repeated taps/flips can reverse before the midpoint.
final class ShapeShiftViewfinderEngine {
    var f=ShapeShiftViewfinderScore.frame(0),automatic=true,target=0,held=false,reduced=false
    var xGoal=0.0,yGoal=0.0,kGoal=1.0,rotGoal=0.0,sheetGoal=0.0,flipTarget=0.0
    var rowScroll=0.0,selectedSwatch=0,solidTarget=false
    private var origin=0.0,prior=0.0,lastTouch=0.0,returning=false
    private var velocity=[Double](repeating:0,count:432),pending=[Int](repeating:0,count:12),targets=[Int](repeating:0,count:12),activate=[Double](repeating:0,count:12)
    private var vx=0.0,vy=0.0,vk=0.0,vr=0.0,vs=0.0,previousX=0.0,previousY=0.0
    private var flipFrom=0.0,flipTime = -99.0,tapTime = -99.0,irisTime = -99.0,floodTime = -99.0
    private var oldOutline:[Double]?,fadeTime = -99.0
    var previousFrame:ShapeShiftViewfinderFrame? {
        guard let w=oldOutline else{return nil};var p=f;p.w=w;return p
    }
    var crossfade:Double {min(max((prior-fadeTime)/0.2,0),1)}
    func frame(now:Double,autoPlay:Bool,pin:Double?,manual:Bool,reduced:Bool)->ShapeShiftViewfinderFrame {
        self.reduced=reduced
        if origin==0 {origin=now;prior=now;automatic=autoPlay && !manual && !reduced}
        let dt=(now-prior).ssvClamp(0,0.05);prior=now
        if automatic {f=ShapeShiftViewfinderScore.frame(pin ?? now-origin);target=ShapeShiftViewfinderScore.shape(f.phase);return f}
        let M=ShapeShiftViewfinderMath.self
        for q in 0..<12 {
            if now>=activate[q] {targets[q]=pending[q]}
            for b in 0..<6 {let w=2*Double.pi/(0.42/(1+0.35*Double(b)));for s in 0..<6 {let i=q*36+b*6+s,goal=s==targets[q] ? 1.0:0,r=M.step(f.w[i],velocity[i],goal,dt,w,w*w);f.w[i]=reduced ? goal:r.0;velocity[i]=reduced ? 0:r.1}}
        }
        if !held {
            let a=M.step(f.x,vx,xGoal,dt,22.1,157.9),b=M.step(f.y,vy,yGoal,dt,22.1,157.9),c=M.step(f.k,vk,kGoal,dt,22.1,157.9),d=M.step(f.rot,vr,rotGoal,dt,22.1,157.9)
            f.x=reduced ? xGoal:a.0;vx=a.1;f.y=reduced ? yGoal:b.0;vy=b.1;f.k=reduced ? kGoal:c.0;vk=c.1;f.rot=reduced ? rotGoal:d.0;vr=d.1
        }
        let sh=M.step(f.sheet,vs,sheetGoal,dt,22.1,157.9);f.sheet=reduced ? sheetGoal:sh.0;vs=sh.1
        let fp=M.seg(now,flipTime,flipTime+0.35);f.flip=reduced ? flipTarget:M.mix(flipFrom,flipTarget,fp)
        let age=now-tapTime;f.dent=reduced ? 0:10*max(0,M.spring(age,1,0,0,30.5,322.3));f.press=reduced ? 1:1-M.spring(age,0.04,0)
        f.rings=[reduced ? 0:0.45*(1-M.seg(age,0,0.24)),f.tapA,1,0,0,1]
        let speedX=dt>0 ? (f.x-previousX)/dt:0,speedY=dt>0 ? (f.y-previousY)/dt:0,m=hypot(speedX,speedY)
        previousX=f.x;previousY=f.y
        if m>1 {f.dx=speedX/m;f.dy=speedY/m}
        let tx=reduced ? 0:(speedX/5600).ssvClamp(-0.16,0.16),ty=reduced ? 0:(speedY/5600).ssvClamp(-0.16,0.16)
        f.jx += (tx-f.jx)*(1-exp(-dt*18));f.jy += (ty-f.jy)*(1-exp(-dt*18))
        f.lx=reduced ? 0: -f.dx*min(m*0.06,10);f.ly=reduced ? 0: -f.dy*min(m*0.06,10)
        f.lift += ((held ? 2.0:0)-f.lift)*(1-exp(-dt*12))
        f.sx *= exp(-dt*8);f.sy *= exp(-dt*8);f.sr *= exp(-dt*8)
        let ir=M.iris(now-irisTime);f.iris=reduced ? 1:ir[0];f.twist=reduced ? 0:ir[1];f.flash=ir[2];f.dim=reduced ? 1:ir[3];f.disc=reduced ? 1:ir[4];f.blinkD=reduced ? 0:ir[5];f.blinkA=reduced ? 0:ir[6]
        if floodTime>=0 {let u=(now-floodTime)/0.58;f.f1u=u;f.dot=M.color(f.base,f.f1c,M.seg(u,0.34,0.69));if u>=1 {f.base=f.f1c;f.f1u = -1;floodTime = -99}}
        f.segment=solidTarget ? 1:0
        if autoPlay && !manual && pin==nil && !reduced && !held && now-lastTouch>2 && now-irisTime>0.62 {
            if !returning {returning=true;home(now:now)}
            if now-lastTouch>3.1 && abs(f.x)<0.02 && abs(f.y)<0.02 && abs(f.k-1)<0.002 && abs(f.sheet)<0.002 {automatic=true;origin=now;returning=false;f=ShapeShiftViewfinderScore.frame(0)}
        }
        return f
    }
    func takeOver(now:Double) {
        lastTouch=now;returning=false
        if !automatic {return}
        let a=ShapeShiftViewfinderScore.frame(f.phase+0.0005),b=ShapeShiftViewfinderScore.frame(f.phase-0.0005)
        velocity=(0..<432).map{(a.w[$0]-b.w[$0])/0.001}
        target=ShapeShiftViewfinderScore.shape(f.phase)
        for q in 0..<12 {targets[q]=ShapeShiftViewfinderScore.shape(f.phase-Double(q)*0.2/11);pending[q]=target;activate[q]=now}
        xGoal=f.x;yGoal=f.y;kGoal=f.k;rotGoal=f.rot;sheetGoal=f.sheet>0.5 ? 1:0;flipTarget=f.flip>=0.5 ? 1:0;flipFrom=f.flip;flipTime=now
        previousX=f.x;previousY=f.y;solidTarget=f.segment>0.5
        if f.f1u>=0 {floodTime=now-f.f1u*0.58}
        if f.phase>=8.3 && f.phase<8.92 {irisTime=now-(f.phase-8.3)}
        automatic=false
    }
    func morph(now:Double,angle:Double) {
        takeOver(now:now);target=(target+1)%6
        if reduced {oldOutline=f.w;fadeTime=now}
        for q in 0..<12 {pending[q]=target;activate[q]=reduced ? now:now+Double(q)*0.2/11}
        tapTime=now;f.tapA=angle;f.hint=[1,0,0]
    }
    func flip(now:Double) {takeOver(now:now);flipFrom=f.flip;flipTarget=1-flipTarget;flipTime=now}
    func capture(now:Double) {takeOver(now:now);if now-irisTime>0.62 {irisTime=now}}
    func sheet(now:Double,open:Bool?=nil) {takeOver(now:now);sheetGoal=(open ?? (sheetGoal<0.5)) ? 1:0}
    func color(_ color:UInt32,index:Int,now:Double) {takeOver(now:now);f.base=f.dot;f.f1c=color;f.f1u=0;floodTime=now;selectedSwatch=index;if solidTarget {f.ring=Double(index)}else{f.dring=Double(index)};if reduced {f.base=color;f.dot=color;f.f1u = -1;floodTime = -99}}
    func release(now:Double,cancelled:Bool,velocityX:Double=0,velocityY:Double=0,height:Double) {
        held=false;lastTouch=now
        xGoal=(f.x+(cancelled ? 0:velocityX*0.12)).ssvClamp(-175,175)
        yGoal=(f.y+(cancelled ? 0:velocityY*0.12)).ssvClamp(-height/2+20,height/2-20)
        vx=cancelled ? 0:velocityX;vy=cancelled ? 0:velocityY;kGoal=f.k.ssvClamp(0.45,1.3)
        let near=(f.rot/90).rounded()*90;rotGoal=abs(f.rot-near)<=4 ? near:f.rot
    }
    func home(now:Double) {
        xGoal=0;yGoal=0;kGoal=1;rotGoal=0;sheetGoal=0;flipFrom=f.flip;flipTarget=0;flipTime=now
        target=0;for q in 0..<12 {pending[q]=0;activate[q]=now+Double(q)*0.2/11}
        f.base=0x118169;f.dot=0x118169;f.f1u = -1;floodTime = -99;solidTarget=false
    }
}
