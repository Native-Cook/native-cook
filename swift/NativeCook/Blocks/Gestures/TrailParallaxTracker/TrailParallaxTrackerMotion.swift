import Foundation

struct TrailParallaxTrackerFrame:Codable,Equatable {
    var d=0.0,v=0.0,run=0.0,phase=0.0,pose=14.0,face=1.0,hop=0.0,lean=0.0,squash=1.0,bubS=1.0,bubT=0.0,skidAge = -1.0
    var cardFrom=0.0,cardTo=0.0,cardDir=1.0,cardAge=10.0,time=0.0
}

enum TrailParallaxTrackerMath {
    static func seg(_ t:Double,_ a:Double,_ b:Double)->Double {((t-a)/(b-a)).tptClamp(0,1)}
    static func mix(_ a:Double,_ b:Double,_ k:Double)->Double {a+(b-a)*k}
    static func smooth(_ a:Double,_ b:Double,_ x:Double)->Double {let k=seg(x,a,b);return k*k*(3-2*k)}
    static func out(_ k:Double)->Double {1-pow(1-k,3)}
    static func ease(_ k:Double)->Double {k<0.5 ? 4*k*k*k:1-pow(-2*k+2,3)/2}
    static func spring(_ t:Double,_ from:Double,_ to:Double,_ v:Double=0,_ d:Double=19,_ k:Double=195)->Double {
        if t<=0{return from};let x=from-to,w=sqrt(k),z=d/(2*w)
        if z<1 {let wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))}
        let s=sqrt(max(z*z-1,1e-6)),r1 = -w*(z-s),r2 = -w*(z+s),c2=(v-r1*x)/(r2-r1),c1=x-c2
        return to+c1*exp(r1*t)+c2*exp(r2*t)
    }
    static func ground(_ x:Double,_ base:Double)->Double {let a=2*Double.pi*x/520;return base+17*sin(a+2.3)+6*sin(2*a+1.25)+3*sin(3*a+0.2)}
    static func run(_ v:Double)->Double {smooth(30,45,abs(v))}
    static func cadence(_ v:Double,_ run:Double)->Double {mix(min(abs(v)*24/(52*93/48),2.4),3.1,run)}
    static func pose(_ phase:Double,_ v:Double,_ run:Double,_ wave:Double = -1)->Double {if wave>=0{return wave};if abs(v)<0.3{return 14};let f=phase-floor(phase);return run>0.5 ? 8+floor(f*6).truncatingRemainder(dividingBy:6):floor(f*8).truncatingRemainder(dividingBy:8)}
    static func lean(_ v:Double,_ run:Double,_ skid:Double)->Double {let lean=mix(min(abs(v)*0.3,8),4,run),brake=skid>=0 && skid<0.4 ? sin(Double.pi*min(skid/0.4,1)):0;return lean*(1-brake)-10*brake}
    static func waypoint(_ distance:Double)->Int {var index=0;for i in 1..<TrailParallaxTrackerWorld.waypoints.count {if distance>=TrailParallaxTrackerWorld.waypoints[i].at {index=i}};return index}
    static func day(_ d:Double)->Double {let q=max(d,0)/2.66,f=q-floor(q);return 310-(floor(q)+smooth(0.8,1,f))}
    static func odo(_ value:Double,_ column:Int)->Double {let b=pow(10,Double(column)),base=floor(value/b),digit=((base.truncatingRemainder(dividingBy:10))+10).truncatingRemainder(dividingBy:10);if column==0{return digit+value-floor(value)};let r=(((value.truncatingRemainder(dividingBy:b))+b).truncatingRemainder(dividingBy:b))-(b-1);return digit+max(0,r)}
    static func cardProgress(_ age:Double)->Double {(spring(age,0,1,0,30.5,322.3)/0.98).tptClamp(0,1)}
}

enum TrailParallaxTrackerScore {
    static let loop=12.0
    private static let keys:[[Double]]=[[0,0],[0.06,-3.33],[0.12,0],[1.2,21],[1.95,22],[2.55,3],[2.75,1.3],[3,1.3],[3.6,24.4],[4.75,24.4],[5.2,1.4],[5.7,1.2],[5.95,108.4],[6.5,40],[7,14],[7.3,3],[7.4,1.6],[8,2.3],[8.3,0]]
    private static let distances:[Double]={var out=[0.0];for i in 1..<keys.count {out.append(out[i-1]+(keys[i-1][1]+keys[i][1])/2*(keys[i][0]-keys[i-1][0]))};return out}()
    static func distance(_ t:Double)->(d:Double,v:Double) {
        if t<=8.3 {var i=0;while i<keys.count-2 && t>keys[i+1][0] {i += 1};let t0=keys[i][0],dt=(t-t0).tptClamp(0,keys[i+1][0]-t0),a=(keys[i+1][1]-keys[i][1])/(keys[i+1][0]-t0);return(distances[i]+keys[i][1]*dt+0.5*a*dt*dt,keys[i][1]+a*dt)}
        if t>=11.4{return(0,0)}
        let s=t-8.3,T=11.4-8.3,r=0.514,speed=distances.last!/(T-r)
        func ramp(_ s:Double)->Double {let k=(s/r).tptClamp(0,1);return r*(k*k*k-pow(k,4)/2)}
        let covered:Double,sp:Double
        if s<r {covered=ramp(s);sp=TrailParallaxTrackerMath.smooth(0,r,s)}else if s<T-r {covered=r/2+s-r;sp=1}else{let u=T-s;covered=T-r-ramp(u);sp=TrailParallaxTrackerMath.smooth(0,r,u)}
        return (distances.last!-speed*covered,-speed*sp)
    }
    private static let phases:[Double]={var out=[0.0],p=0.0;for i in 1...1200 {let t=(Double(i)-0.5)/100,v=distance(t).v;p += TrailParallaxTrackerMath.cadence(v,TrailParallaxTrackerMath.run(v))/100;out.append(p)};return out}()
    private struct Event {let t:Double,from:Int,to:Int,dir:Int}
    private static let events:([Event],[Double])={
        var cards=[Event](),skids=[Double](),previousW=0,previousV=0.0,t=0.002
        while t<=12 {let(d,v)=distance(t),w=TrailParallaxTrackerMath.waypoint(d);if w != previousW {cards.append(Event(t:t,from:previousW,to:w,dir:w>previousW ? 1:-1));previousW=w};if abs(previousV)>=14 && abs(v)<14 {skids.append(t)};previousV=v;t += 0.002};return(cards,skids)
    }()
    static func frame(_ raw:Double)->TrailParallaxTrackerFrame {
        let M=TrailParallaxTrackerMath.self,t=((raw.truncatingRemainder(dividingBy:12))+12).truncatingRemainder(dividingBy:12),(d,v)=distance(t)
        var f=TrailParallaxTrackerFrame();f.time=t;f.d=d;f.v=v;f.run=M.run(v)
        let x=t*100,i=min(Int(x),1199);f.phase=M.mix(phases[i],phases[i+1],x-Double(i));f.pose=M.pose(f.phase,v,f.run)
        f.face=t>=8.3 && t<11.48 ? -1:1
        func hop(_ start:Double)->Double {let k=M.seg(t,start,start+0.16);return k>0 && k<1 ? sin(Double.pi*k):0}
        f.hop=max(hop(8.22),hop(11.4));for s in events.1 where s<=t {f.skidAge=t-s}
        let antic=t<0.12 ? sin(Double.pi*M.seg(t,0,0.12)):0
        f.lean=M.lean(v,f.run,f.skidAge)-4*antic
        let crouch=t>=5.6 && t<5.7 ? pow(M.seg(t,5.6,5.7),3):t>=5.7 ? max(0,M.spring(t-5.7,1,0)):0
        f.squash=1-0.08*crouch-0.1*f.hop+0.015*sin(2*Double.pi*6*t/12)
        if t>=0.2 && t<11.55 {let k=M.seg(t,0.2,0.48);f.bubS=1-pow(k,3);f.bubT=M.ease(k)}else if t>=11.55 {f.bubS=M.mix(max(0,M.spring(t-11.55,0,1)),1,M.seg(t,11.85,12));f.bubT=1-M.out(M.seg(t,11.55,11.8))}
        var e=events.0.last!,age=t+12-e.t;for event in events.0 where event.t<=t {e=event;age=t-event.t}
        f.cardFrom=Double(e.from);f.cardTo=Double(e.to);f.cardDir=Double(e.dir);f.cardAge=age
        return f
    }
}

final class TrailParallaxTrackerEngine {
    var f=TrailParallaxTrackerScore.frame(0),automatic=true,held=false
    private var origin=0.0,prior=0.0,lastTouch=0.0,startDistance=0.0,anchor=0.0,previousD=0.0
    private var mode=0,target=0.0,releaseVelocity=0.0,homeFrom=0.0,homeTime=0.0,faceTarget=1.0,faceTime = -99.0,braceTime = -99.0,skidTime = -99.0,waveTime = -99.0,bubbleTime = -99.0,cardTime = -99.0,bubbleV=0.0
    func frame(now:Double,autoPlay:Bool,pin:Double?,manual:Bool,reduced:Bool)->TrailParallaxTrackerFrame {
        if origin==0 {origin=now;prior=now;automatic=autoPlay && !manual && !reduced}
        let dt=(now-prior).tptClamp(0,0.05);prior=now
        if automatic {f=TrailParallaxTrackerScore.frame(pin ?? now-origin);return f}
        let M=TrailParallaxTrackerMath.self,oldV=f.v
        if !held {
            if mode==2 {let duration=(abs(homeFrom)/58+0.7).tptClamp(0.8,3.1),p=min((now-homeTime)/duration,1);f.d=homeFrom*(1-M.ease(p));if p>=1 {f.d=0;mode=3;homeTime=now;if faceTarget != 1 {faceTarget=1;faceTime=now}}}
            else if mode==3 {f.d=0;if now-homeTime>0.45 {automatic=true;origin=now;mode=0;f=TrailParallaxTrackerScore.frame(0);return f}}
            else if f.d<0 || f.d>150 {let b=f.d<0 ? 0.0:150;releaseVelocity += (-157.9*(f.d-b)-22.1*releaseVelocity)*dt;f.d += releaseVelocity*dt;mode=0;target=b}
            else if mode==1 {
                let decay = -1000*log(0.9972),old=releaseVelocity,new=old*exp(-decay*dt)
                releaseVelocity=new;f.d += (old-new)/decay
                if abs(new)<0.6 {mode=0;target=(f.d*10).rounded()/10}
            } else {releaseVelocity += (-157.9*(f.d-target)-22.1*releaseVelocity)*dt;f.d += releaseVelocity*dt}
            if autoPlay && !manual && pin==nil && !reduced && mode==0 && now-lastTouch>2 && abs(f.d-target)<0.02 && abs(releaseVelocity)<0.08 {mode=2;homeFrom=f.d;homeTime=now}
        }
        let measured=dt>0 ? (f.d-previousD)/dt:0;f.v=M.mix(oldV,measured,0.35);previousD=f.d
        f.run=reduced ? 0:M.run(f.v);if !reduced {f.phase += M.cadence(f.v,f.run)*dt}
        if !held && mode != 3 && abs(f.v)>0.6 && (f.v<0 ? -1.0:1) != faceTarget {faceTarget=f.v<0 ? -1:1;faceTime=now}
        if abs(oldV)>=14 && abs(f.v)<14 && now-skidTime>0.5 {skidTime=now}
        let way=M.waypoint(f.d)
        if way != Int(f.cardTo) {f.cardFrom=f.cardTo;f.cardDir=Double(way)>f.cardTo ? 1:-1;f.cardTo=Double(way);cardTime=now}
        f.cardAge=now-cardTime;f.skidAge=now-skidTime<0.5 ? now-skidTime:-1
        let turn=M.seg(now,faceTime,faceTime+0.16);f.hop=reduced ? 0:(turn>0 && turn<1 ? sin(Double.pi*turn):0);f.face=turn<0.5 && f.hop>0 ? -faceTarget:faceTarget
        let age=now-braceTime,brace=age>=0 && age<0.6 ? (held && age<0.08 ? age/0.08:max(0,M.spring(age-0.08,1,0))):0
        let waveAge=now-waveTime,wave=waveAge>=0 && waveAge<0.5 ? Double([16,17,18,17,16][min(4,Int(waveAge/0.1))]):-1
        f.pose=M.pose(f.phase,f.v,f.run,wave);f.lean=reduced ? 0:M.lean(f.v,f.run,f.skidAge);f.squash=reduced ? 1:1-0.1*f.hop-0.06*brace+0.015*sin(Double.pi*now)
        let show=(abs(f.d)<0.6 && abs(f.v)<1.5 && mode != 2) || now-bubbleTime<2 ? 1.0:0
        bubbleV += (-195*(f.bubS-show)-19*bubbleV)*dt;f.bubS=max(0,f.bubS+bubbleV*dt);f.bubT=M.mix(f.bubT,show==1 ? 0:1,min(1,dt*10));f.time=now-origin
        return f
    }
    func begin(now:Double){automatic=false;held=true;mode=0;releaseVelocity=0;target=f.d;startDistance=f.d;anchor=f.d;previousD=f.d;faceTarget=f.face;braceTime=now;cardTime=now-f.cardAge;lastTouch=now;if abs(f.v)>14 {skidTime=now}}
    func move(_ translation:Double,now:Double){
        var d=startDistance-translation/24
        if d<0 {let over = -d*24;d = -over/(1+over/120)/24}else if d>150 {let over=(d-150)*24;d=150+over/(1+over/120)/24}
        f.d=d
        if (d-anchor)*faceTarget>0 {anchor=d}else if (anchor-d)*faceTarget>4/24.0 {faceTarget = -faceTarget;faceTime=now;anchor=d}
        lastTouch=now
    }
    func end(_ velocity:Double,now:Double,cancelled:Bool){held=false;lastTouch=now;let v=cancelled ? 0: -velocity/24;releaseVelocity=v;if f.d>=0 && f.d<=150 && abs(v)>=0.6 {mode=1}else{mode=0;target=(f.d.tptClamp(0,150)*10).rounded()/10}}
    func tap(x:Double,y:Double,base:Double,now:Double){let foot=TrailParallaxTrackerMath.ground(200+f.d*24,base);if abs(x-200)<36 && y>foot-107 && y<foot+10 {waveTime=now;bubbleTime=now};lastTouch=now}
    func adjust(_ d:Double,now:Double){begin(now:now);f.d=d.tptClamp(0,150);previousD=f.d;end(0,now:now,cancelled:true)}
}

extension Double {func tptClamp(_ a:Double,_ b:Double)->Double {Swift.min(Swift.max(self,a),b)}}
