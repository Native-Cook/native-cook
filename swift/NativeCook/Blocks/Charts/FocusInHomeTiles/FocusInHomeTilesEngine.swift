import Foundation

final class FocusInHomeTilesEngine {
    typealias M=FocusInHomeTilesMotion
    private struct Channel {
        var value:Double;private var from=0.0,target=0.0,start=0.0,duration=0.0,velocity=0.0,cubic=true
        private var damping=0.0,stiffness=0.0;private(set) var busy=false
        mutating func set(_ x:Double){value=x;busy=false}
        mutating func timing(_ to:Double,_ now:Double,_ duration:Double,_ reduced:Bool,cubic:Bool=true){if reduced{set(to);return};from=value;target=to;start=now;self.duration=duration;self.cubic=cubic;busy=true}
        mutating func spring(_ to:Double,_ now:Double,_ v:Double,_ reduced:Bool){if reduced{set(to);return};from=value;target=to;start=now;duration=0;velocity=v;damping=30.5;stiffness=322.3;busy=true}
        mutating func advance(_ now:Double){guard busy else{return};let t=now-start
            if duration>0{let k=M.seg(t,0,duration),e=cubic ? M.easeInOut(k) : k<0.5 ? 2*k*k : 1-pow(-2*k+2,2)/2;value=M.mix(from,target,e);if k>=1{set(target)}}else{value=M.springAt(t,from,target,velocity,damping/4,stiffness/4);if t>3 && abs(value-target)<0.001{set(target)}}
        }
    }
    var autoPlay=true,reduced=false,manual=false,pin:Double?,viewportHeight=874.0
    var onHaptic:((Int)->Void)?
    private(set) var frame=M.frameAt(0,874),clock=0.0
    private var auto=true,now=0.0,phase=0.0,touching=false,lastTouch=0.0,mode=0,moved=false,downAt=0.0
    private var s=Channel(value:0),fp=Channel(value:2),budget=Channel(value:120),alert=Channel(value:1),dark=Channel(value:0),lx=Channel(value:292),ly=Channel(value:690)
    private var alertOn=true,startS=0.0,startB=120.0,downX=0.0,downY=0.0
    init(){
        #if DEBUG
        manual=UserDefaults.standard.bool(forKey:"focusInHomeTilesManual")
        if let raw=UserDefaults.standard.object(forKey:"focusInHomeTilesTime"),let t=Double("\(raw)"){pin=t;phase=t;frame=M.frameAt(t,viewportHeight)}
        #endif
    }
    func configure(autoPlay:Bool,reduced:Bool){if self.autoPlay != autoPlay || self.reduced != reduced{auto=autoPlay && !reduced && !manual};self.autoPlay=autoPlay;self.reduced=reduced;if manual||reduced{auto=false};publish()}
    func viewport(_ height:Double){viewportHeight=height;if !touching && dark.value==0{ly.set(690-max(0,874-height))};publish()}
    private func publish(){clock=auto||pin != nil ? pin ?? phase : now;frame=auto||pin != nil ? M.frameAt(pin ?? phase,viewportHeight) : M.liveFrameAt(M.Live(s:s.value,fp:fp.value,budget:budget.value,alert:alert.value,dark:dark.value,lx:lx.value,ly:ly.value,sharp:reduced ? 1 : 0))}
    func advance(_ dt:Double){now+=dt;guard pin==nil else{return};if auto{phase=(phase+dt).truncatingRemainder(dividingBy:10);publish();return}
        s.advance(now);fp.advance(now);budget.advance(now);alert.advance(now);dark.advance(now);lx.advance(now);ly.advance(now)
        if !reduced && autoPlay && !manual && !touching && now-lastTouch>2{let page=min(max(Int(floor(s.value/780+0.5)),0),2),focus=[2.0,1,2][page]
            let settled=abs(s.value-Double(page)*780)<0.5 && abs(fp.value-focus)<0.02 && abs(budget.value-120)<0.5 && dark.value<0.002
            if !settled{s.timing(Double(page)*780,now,0.6,false);fp.timing(focus,now,0.6,false);budget.timing(120,now,0.6,false);dark.timing(0,now,0.6,false);alertOn=true;alert.timing(1,now,0.2,false,cubic:false);lastTouch=now-1.3}else{phase=M.PAGE_REST_T[page];auto=true}
        };publish()
    }
    private func takeOver(){if auto||pin != nil{let f=frame;s.set(f.s);fp.set(f.fp);budget.set(f.budget);alert.set(f.alert);alertOn=f.alert>0.5;lx.set(f.ox);ly.set(f.oy);let d=f.front<1.9 ? f.dir>0 ? M.clamp01(1-(f.front+0.22)/1.54) : M.clamp01((f.front+0.22)/1.54) : 0;dark.set(d);if d>0{dark.timing(0,now,0.9,false,cubic:false)};auto=false};pin=nil}
    func begin(x:Double,y:Double){touching=true;takeOver();moved=false;downAt=now;downX=x;downY=y;startS=s.value;startB=budget.value;mode=0;lastTouch=now;guard !reduced else{return}
        let st=84-s.value;if x>=318 && x<=378 && y>=st-8 && y<=st+97{mode=2;lx.set(frame.ox);ly.set(frame.oy);dark.timing(1,now,1,false);lx.timing(x,now,0.7,false,cubic:false);ly.timing(y,now,0.7,false,cubic:false);onHaptic?(0);return}
        if s.value>780*1.9 && abs(x-frame.ox)<frame.ow/2+12 && abs(y-frame.oy)<frame.oh/2+16{mode=1}
    }
    private func setBudget(_ b:Double){let v=min(max(b,40),120);budget.set(v);if alertOn && v<99{alertOn=false;alert.timing(0,now,0.2,false,cubic:false);onHaptic?(1)}else if !alertOn && v>101{alertOn=true;alert.timing(1,now,0.2,false,cubic:false);onHaptic?(1)}}
    func move(x:Double,y:Double){let ty=y-downY;if abs(ty)>6 || abs(x-downX)>6{moved=true};if mode==2{lx.set(x);ly.set(y);return};if mode==1{setBudget(startB-ty*120/222);return};guard moved else{return};var value=startS-ty;if value<0{value = -60*(1-exp(value/60))}else if value>1560{value=1560+60*(1-exp(-(value-1560)/60))};s.set(value);publish()}
    func end(x:Double,y:Double,velocityY:Double,cancelled:Bool=false){touching=false;lastTouch=now
        if mode==2{dark.timing(0,now,1.1,false);return};if mode==1{return}
        if !moved && now-downAt<0.35{if !cancelled && !reduced{for g in 0..<M.GROUPS.count{let group=M.GROUPS[g],r=group.rect,top=r[1]+group.page*780-s.value;if x>=r[0] && x<=r[0]+r[2] && y>=top && y<=top+r[3]{fp.timing(group.plane,now,0.55,false);onHaptic?(0);break}}};return}
        let v=cancelled ? 0 : -velocityY,target=min(max(Int(floor((s.value+v*0.18)/780+0.5)),0),2),old=Int(floor(startS/780+0.5));if old != target{onHaptic?(2)};s.spring(Double(target)*780,now,v,reduced)
        if !reduced && old != target{fp.timing([2.0,1,2][target],now,0.55,false)}
    }
    func page(_ delta:Int){takeOver();let p=min(max(Int(floor(s.value/780+0.5))+delta,0),2);s.timing(Double(p)*780,now,0.6,reduced);fp.timing([2.0,1,2][p],now,0.55,reduced);lastTouch=now}
    func focus(_ g:Int){takeOver();fp.timing(M.GROUPS[g].plane,now,0.55,reduced);lastTouch=now}
}
