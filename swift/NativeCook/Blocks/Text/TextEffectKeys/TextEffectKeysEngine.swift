import Foundation

final class TextEffectKeysEngine {
    typealias M=TextEffectKeysMotion
    var geo:M.Geo,autoPlay=true,reduced=false,manual=false,pin:Double?
    var onHaptic:((Int)->Void)?,onChange:((Int)->Void)?
    private(set) var frame:M.Frame
    private var live=M.blankLive(),auto=true,phase=0.0,now=0.0,n0=0.0,lastTouch=0.0
    private var downX=0.0,downY=0.0,travel=0.0
    init(geo:M.Geo){self.geo=geo;frame=M.frameAt(0,geo)
        #if DEBUG
        manual=UserDefaults.standard.bool(forKey:"textEffectKeysManual")
        if let raw=UserDefaults.standard.object(forKey:"textEffectKeysTime"),let t=Double("\(raw)"){pin=t;phase=t;frame=M.frameAt(t,geo)}
        #endif
    }
    func configure(autoPlay:Bool,reduced:Bool){if self.autoPlay != autoPlay || self.reduced != reduced {auto=autoPlay && !reduced && !manual};self.autoPlay=autoPlay;self.reduced=reduced;if manual||reduced{auto=false};ensureLive();publish()}
    private func ensureLive(){guard live.x.isEmpty else{return};let f=M.frameAt(0,geo);load(f,at:0)}
    private func load(_ f:M.Frame,at t:Double){
        let l=M.blankLive();for i in 0..<Int(geo.n){let h=f.hero[i],th=M.THROWS[i];l.x.append(h.x);l.y.append(h.y);l.vx.append(h.vx);l.vy.append(h.vy);l.rot.append(wrap(h.rot+180,360)-180);l.s.append(h.s);l.lime.append(h.lime);l.home.append(h.home);l.font.append(h.font);l.mode.append(t>=th.launch && t<M.RESEAT+0.035*Double(i) ? 2 : t>=3.23 && t<6.05 ? 4 : 0);l.wait.append(0);l.rest.append(0);l.drag.append(0.1)}
        l.target=t>=0.28 && t<6.7 ? 1 : 0;l.rise=f.rise;l.body=f.body;l.depth=f.depth
        l.active = -1;if t>=3.23 && t<6.05 {for k in 0..<8 where f.active[k]>0.5{l.active=Double(k)}};l.sendS=f.sendS;live=l;n0=now-t
    }
    private func wrap(_ t:Double,_ span:Double)->Double{(t.truncatingRemainder(dividingBy:span)+span).truncatingRemainder(dividingBy:span)}
    private func publish(){frame=auto||pin != nil ? M.frameAt(pin ?? phase,geo) : M.liveFrame(live,geo,now,n0,reduced);frame.still=reduced ? 1 : 0}
    func advance(_ dt:Double){now+=dt;guard pin==nil else{return};if auto{phase=wrap(phase+dt,8);publish();return};ensureLive();let previous=live.hapticT;M.stepLive(live,min(dt,1/30),geo,now);if live.hapticT != previous{onHaptic?(0)}
        let busy=live.mode.contains(2)||live.mode.contains(3),staged=live.mode.contains(4)
        if live.finger==0 && now-lastTouch>2 && !busy{
            if live.target != 0 || live.active>=0 || staged{live.active = -1;live.target=0;for i in live.mode.indices where live.mode[i] != 0{live.mode[i]=0;live.wait[i]=0.045*Double(i)};onChange?(-1)}
            var rest=live.rise<0.004 && live.body<0.004 && live.held<0
            for i in live.x.indices{if hypot(live.x[i]-geo.fieldX[i],live.y[i]-M.FIELD_BASE)>0.4 || abs(live.s[i]-1)>0.01 || live.home[i]>0.02{rest=false}}
            let kp=wrap(now-n0,2)/2;if rest && autoPlay && !manual && !reduced && (kp<0.04 || kp>0.975){phase=kp>0.5 ? (kp-1)*2+8 : kp*2;auto=true}
        };publish()
    }
    private func takeOver(){if auto||pin != nil{let t=pin ?? phase;load(M.frameAt(t,geo),at:t);auto=false};pin=nil;lastTouch=now}
    private func keyAt(_ x:Double,_ y:Double)->Int?{(0..<8).first{let xx=[15.0,206][$0%2],yy=[606.0,665,724,783][$0/2];return x>=xx-4 && x<=xx+185 && y>=yy-5 && y<=yy+52}}
    private func isSend(_ x:Double,_ y:Double)->Bool{hypot(x-366.5,y-522.5)<22}
    private func press(_ k:Int){guard live.held != Double(k)else{return};live.held=Double(k);live.active=Double(k);for i in live.mode.indices{live.mode[i]=4;live.wait[i]=0};live.returning=0;onChange?(k)}
    func begin(x:Double,y:Double){takeOver();ensureLive();downX=x;downY=y;travel=0;live.fx=x;live.fy=y;live.fvx=0;live.fvy=0
        if isSend(x,y){live.finger=3;live.sendS=0.88;live.sendV=0}else if let k=keyAt(x,y){live.finger=2;press(k);onHaptic?(1)}else if y<554 && !reduced{live.finger=1;for i in live.mode.indices{live.mode[i]=1;live.wait[i]=0};live.active = -1;live.returning=0};publish()
    }
    func move(x:Double,y:Double,vx:Double,vy:Double){live.fx=x;live.fy=y;live.fvx=vx;live.fvy=vy;travel=max(travel,hypot(x-downX,y-downY));if live.finger==2,let k=keyAt(x,y),live.held != Double(k){press(k);if now-live.hapticT>=0.1{live.hapticT=now;onHaptic?(0)}};lastTouch=now}
    private func burst(){for i in live.x.indices{let h=M.THROWS[i];live.drag[i]=h.ta/M.XK;live.vx[i]=(h.xa-live.x[i])/live.drag[i];live.vy[i] = -h.vy0;live.mode[i]=2;live.rest[i]=0};live.returning=0;onHaptic?(2)}
    func end(x:Double,y:Double,cancelled:Bool=false){
        if live.finger==1{
            if cancelled{for i in live.mode.indices{live.mode[i]=0;live.wait[i]=0.035*Double(i)}}else{let speed=hypot(live.fvx,live.fvy),k=speed>1400 ? 1400/speed : 1
                for i in live.mode.indices{live.mode[i]=2;live.drag[i]=0.3;if i==0{live.vx[i]=live.fvx*k;live.vy[i]=live.fvy*k}else{let v=hypot(live.vx[i],live.vy[i]);if v>1400{live.vx[i]*=1400/v;live.vy[i]*=1400/v}}};if speed>300{onHaptic?(2)}}
        }else if live.finger==3 && !cancelled && travel<=10 && isSend(x,y){if live.target==0{live.target=1;for i in live.mode.indices{live.mode[i]=0;live.wait[i]=0.018*Double(i);live.vx[i]=0;live.vy[i]=0};onHaptic?(3)}else if !reduced{burst()};live.sendV=6}
        live.held = -1;live.finger=0;lastTouch=now;publish()
    }
    func choose(_ k:Int){begin(x:[105.5,296.5][k%2],y:[627.5,686.5,745.5,804.5][k/2]);end(x:0,y:0)}
    func send(){begin(x:366.5,y:522.5);end(x:366.5,y:522.5)}
}
