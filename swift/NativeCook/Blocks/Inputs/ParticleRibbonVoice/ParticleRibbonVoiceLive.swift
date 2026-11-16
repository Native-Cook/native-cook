import Foundation

extension ParticleRibbonVoiceMotion {
    struct Live {
        var t = 0.0, modeT = 0.0
        var mode = 0, talk = 0
        var talkY0 = 0.0, talkLevel = 0.0, hold = 0.0, level = 0.0
        var base = 26.0, baseV = 0.0, think = 0.0, thinkV = 0.0
        var waveP = -0.07, idleP = 1.0, acc = 0.0
        var pul = Array(repeating:0.0,count:64), win = Array(repeating:0.0,count:22)
        var bump: [Double] = [0,0,0,80,99,1,0,0,0,80,99,1]
        var talkX = 0.0, talkY = 0.0, talkT = 0.0, talkVy = 0.0
        var sg = [0,0,0,0], cross = 0, words = 0
        var cap = ParticleRibbonVoiceMotion.captionAt(0)
        var dots = 0, quiet = 0.0, gainIn = 0.0, gain = 0.0, fromMode = 0
        var wordT: [Double] = [-1,-1,-1,-1,-1,-1,-1]
    }
    static func liveFromLoop(_ s: inout Live, _ input: Double) {
        let t = wrap(input), f = frameAt(t)
        s.t = t; s.mode = stateAt(t); s.modeT = 0; s.level = f.sheet[1]
        s.base = baseWidth(t); s.baseV = 0; s.think = f.think[0]; s.thinkV = 0
        s.waveP = f.line[3]; s.idleP = f.twA[2]; s.acc = 0; s.talk = 0; s.hold = 0; s.words = 0
        s.wordT = Array(repeating:-1,count:7); s.fromMode = s.mode; s.gain = 0
        for i in 0..<8 {
            let a = f.pul[i*4+1], on = abs(a)>0.01, o = i*8
            s.pul[o] = f.pul[i*4]; s.pul[o+1] = f.pul[i*4+3]; s.pul[o+2] = a; s.pul[o+3] = f.pul[i*4+2]
            s.pul[o+4] = on ? 0.2:9; s.pul[o+5] = 0.9; s.pul[o+6] = 1.5; s.pul[o+7] = f.pul[i*4+3]<0 ? 1:0
        }
        s.win = Array(repeating:0,count:22)
        for k in 0..<2 {
            let h = f.bump[k*4+1], on = abs(h)>0.05, o = k*6
            s.bump[o] = f.bump[k*4]; s.bump[o+1] = f.bump[k*4+3]; s.bump[o+2] = h; s.bump[o+3] = f.bump[k*4+2]
            s.bump[o+4] = on ? 0.3:99; s.bump[o+5] = 1.4
        }
        s.talkVy = 0; s.sg = [0,0,0,0]; s.cap = captionAt(t); s.dots = 0; s.quiet = 0; s.gainIn = 0
    }
    static func emit(_ s: inout Live, _ x: Double, _ v: Double, _ a: Double, _ w: Double,
                     _ tau: Double, _ life: Double, _ speaking: Double) {
        var k = 0, oldest = -1.0
        for i in 0..<8 {
            let age = s.pul[i*8+4]
            if age >= s.pul[i*8+6] { k = i; break }
            if age > oldest { oldest = age; k = i }
        }
        s.pul.replaceSubrange(k*8..<k*8+8,with:[x,v,a,w,0,tau,life,speaking])
    }
    static func emitBump(_ s: inout Live, _ x: Double, _ v: Double, _ h: Double, _ w: Double) {
        var k = 0, oldest = -1.0
        for i in 0..<2 {
            let age = s.bump[i*6+4]
            if age >= s.bump[i*6+5] { k = i; break }
            if age > oldest { oldest = age; k = i }
        }
        s.bump.replaceSubrange(k*6..<k*6+6,with:[x,v,h,w,0,1.4])
    }
    static func liveMode(_ s: inout Live, _ mode: Int) {
        if s.mode == mode { return }
        s.fromMode = s.mode; s.mode = mode; s.modeT = 0; s.acc = 0.55
        if mode == 3 { s.words = 0; s.wordT = Array(repeating:-1,count:7) }
        if mode == 2 { s.dots = 0 }
    }
    static func liveDown(_ s: inout Live, _ id: Int, _ x: Double, _ y: Double, _ c: Double,
                         _ parts: Bool, _ twist: Double) -> Int {
        s.quiet = 0
        if parts && abs(y-c)<=70 && x>4 && x<398 {
            for k in 0..<2 where s.win[k*11] == 0 {
                let o = k*11
                s.win.replaceSubrange(o..<o+11,with:[1,Double(id),x,x,0,0,faceOn(twist),y,0,0,s.t])
                return 2
            }
        }
        if s.talk == 0 {
            s.talk = id+1; s.talkY0 = y; s.talkX = x; s.talkY = y; s.talkT = s.t; s.talkVy = 0
            s.talkLevel = 0.25; s.hold = 0; s.gainIn = 1
            emit(&s,-42,260,0.9*Double.pi,64,0.9,1.5,0); liveMode(&s,1)
            return 1
        }
        return 0
    }
    static func liveMove(_ s: inout Live, _ id: Int, _ x: Double, _ y: Double, _ c: Double, _ vx: Double = 0) {
        s.quiet = 0
        for k in 0..<2 {
            let o = k*11
            if s.win[o] == 1 && s.win[o+1] == Double(id) {
                let nx = min(max(x,20),382), dtv = s.t-s.win[o+10]
                if dtv > 0.004 { s.win[o+9] += ((nx-s.win[o+3])/dtv-s.win[o+9])*0.35; s.win[o+10] = s.t }
                else if vx != 0 { s.win[o+9] = vx }
                s.win[o+3] = nx; s.win[o+7] = y; s.win[o+8] = 26*clamp01(abs(y-c)/90)
                return
            }
        }
        if s.talk == id+1 {
            s.talkLevel = clamp01((s.talkY0-y)/220+0.25); s.talkX = x
            let dtv = s.t-s.talkT
            if dtv > 0.004 { s.talkVy += ((y-s.talkY)/dtv-s.talkVy)*0.35; s.talkY = y; s.talkT = s.t }
        }
    }
    @discardableResult static func liveUp(_ s: inout Live, _ id: Int, _ demoFlow: Bool) -> Int {
        s.quiet = 0
        for k in 0..<2 {
            let o = k*11
            if s.win[o] == 1 && s.win[o+1] == Double(id) {
                s.win[o] = 2
                let xc = s.win[o+2], vx = s.win[o+9]
                emit(&s,xc-70,-260+0.4*vx,0.7*Double.pi,40,0.45,1.2,0)
                emit(&s,xc+70,260+0.4*vx,0.7*Double.pi,40,0.45,1.2,0)
                return 0
            }
        }
        if s.talk == id+1 {
            s.talk = 0; s.gainIn = 0
            if s.hold < 0.25 { liveMode(&s,0); return 0 }
            let h = max(-22,min(22,s.talkVy/40))
            if abs(h)>=1.5 { emitBump(&s,s.talkX,260,h,80) }
            s.talkVy = 0
            if demoFlow { liveMode(&s,2) }
            return 1
        }
        return 0
    }
    static func liveStep(_ s: inout Live, _ dtIn: Double, _ demoFlow: Bool, _ ext: Double = -1, _ speed: Double = 260) {
        let dt = min(max(dtIn,0),0.05), prevT = s.modeT
        s.t += dt; s.modeT += dt
        s.quiet += s.talk == 0 && s.win[0] != 1 && s.win[11] != 1 ? dt:0
        if s.talk > 0 { s.hold += dt; s.talkVy *= exp(-dt/0.12) }
        if demoFlow {
            if s.mode == 2 && s.modeT >= 1.4 { liveMode(&s,3) }
            if s.mode == 3 && s.modeT >= 2.1+0.9 { liveMode(&s,0) }
        }
        var target = 0.0
        if s.mode == 1 { target = ext>=0 ? ext:s.talkLevel }
        else if s.mode == 3 { target = ext>=0 ? ext:replyLevel(s.modeT+reply[0][0]) }
        let tau = target>s.level ? 0.06:0.32
        s.level += (target-s.level)*(1-exp(-dt/tau))
        if s.mode == 1 {
            s.acc += (0.7+2.6*s.level)*dt
            if s.acc>=1 { s.acc -= 1; emit(&s,-42,speed,Double.pi*(0.3+0.55*s.level),64,0.9,1.5,0) }
        } else if s.mode == 3 {
            if ext>=0 {
                s.acc += (0.7+2.6*s.level)*dt
                if s.acc>=1 { s.acc -= 1; emit(&s,444,-speed,Double.pi*(0.3+0.55*s.level),64,0.9,1.3,1) }
            } else {
                for r in reply {
                    let at = r[0]-reply[0][0]
                    if prevT<=at && s.modeT>at { emit(&s,444,-speed,Double.pi*(0.3+0.55*r[1]),64,0.9,1.3,1) }
                }
            }
        }
        let bt = s.mode == 2 ? 6.0:s.mode == 3 ? 7:26, tt = s.mode == 2 ? 1.0:0
        let n = max(1,Int(ceil(dt/0.004))), h = dt/Double(n)
        for _ in 0..<n {
            s.baseV += (120*(bt-s.base)-20*s.baseV)*h; s.base += s.baseV*h
            s.thinkV += (157.9*(tt-s.think)-25.1*s.thinkV)*h; s.think += s.thinkV*h
        }
        s.waveP += 3*dt/loop; s.idleP += 2*Double.pi*dt/loop
        for i in 0..<8 {
            let o = i*8
            if s.pul[o+4]>=s.pul[o+6] { s.pul[o+2] = 0; continue }
            let x0 = s.pul[o]
            s.pul[o] += s.pul[o+1]*dt; s.pul[o+4] += dt; s.pul[o+2] *= exp(-dt/s.pul[o+5])
            if s.pul[o+7] == 1 && x0>201 && s.pul[o]<=201 {
                if s.words<7 { s.wordT[s.words] = s.t }
                s.words += 1
            }
        }
        for k in 0..<2 {
            let o = k*11, st = s.win[k*11]
            if st == 0 { continue }
            s.win[o+2] += (s.win[o+3]-s.win[o+2])*(1-exp(-dt/0.03))
            let held = st == 1
            if held { s.win[o+9] *= exp(-dt/0.12) }
            let response = held ? 0.28:0.5, zeta = held ? 0.9:1, kk = pow(2*Double.pi/response,2)
            let cc = 2*zeta*sqrt(kk)
            for _ in 0..<n {
                s.win[o+5] += (kk*((held ? 1:0)-s.win[o+4])-cc*s.win[o+5])*h
                s.win[o+4] += s.win[o+5]*h
            }
            if !held && s.win[o+4]<0.002 { s.win[o] = 0; s.win[o+4] = 0 }
        }
        for k in 0..<2 {
            let o = k*6
            if s.bump[o+4]>=s.bump[o+5] { continue }
            s.bump[o] += s.bump[o+1]*dt; s.bump[o+4] += dt
        }
        if s.win[0] == 1 && s.win[11] == 1 {
            let xa = s.win[2], xb = s.win[13]
            for i in 0..<4 {
                let v = i == 0 ? xb-(xa-70):i == 1 ? xb-(xa+70):i == 2 ? xa-(xb-70):xa-(xb+70)
                let sign = v>0 ? 1:-1
                if s.sg[i] != 0 && s.sg[i] != sign { s.cross += 1 }
                s.sg[i] = sign
            }
        } else { s.sg = [0,0,0,0] }
        let kOut = 1-exp(-dt/0.035), kIn = 1-exp(-dt/0.05)
        s.dots = s.mode == 2 ? 1+(s.modeT>=0.25/thinkF ? 1:0)+(s.modeT>=0.75/thinkF ? 1:0):0
        let holdReply = s.mode == 0 && s.fromMode == 3 && s.modeT<0.6
        let want0 = s.mode == 0 && !holdReply ? 1.0:0, want1 = s.mode == 1 ? 1.0:0
        let want2 = s.mode == 2 ? 1.0:0, want3 = s.mode == 3 || holdReply ? 1.0:0
        let g0 = s.cap[0], g1 = s.cap[1], g2 = max(s.cap[2],max(s.cap[3],s.cap[4])), g3 = s.cap[5..<12].max() ?? 0
        let o0 = g1<0.25 && g2<0.25 && g3<0.25 ? 1.0:0, o1 = g0<0.25 && g2<0.25 && g3<0.25 ? 1.0:0
        let o2 = g0<0.25 && g1<0.25 && g3<0.25 ? 1.0:0, o3 = g0<0.25 && g1<0.25 && g2<0.25 ? 1.0:0
        s.cap[0] += (want0*o0-s.cap[0])*(want0*o0>s.cap[0] ? kIn:kOut)
        s.cap[1] += (want1*o1-s.cap[1])*(want1*o1>s.cap[1] ? kIn:kOut)
        for i in 0..<3 {
            let tg = want2*o2*(s.dots>i ? 1.0:0)
            s.cap[2+i] += (tg-s.cap[2+i])*(tg>s.cap[2+i] ? kIn:kOut)
        }
        for i in 0..<7 {
            let tg = want3*o3*(s.words>i ? 1.0:0)
            s.cap[5+i] += (tg-s.cap[5+i])*(tg>s.cap[5+i] ? 1-exp(-dt/0.02):kOut)
            let age = s.wordT[i]<0 ? 9:s.t-s.wordT[i]
            s.cap[12+i] = age<0.24 ? s.cap[5+i]*(1-smooth(0,0.24,age)):0
        }
        s.gain += ((s.gainIn>0 ? 1:0)-s.gain)*(1-exp(-dt/(s.gainIn>0 ? 0.06:0.32)))
    }
    static func liveFrame(_ s: Live) -> Frame {
        let iw = clamp01((s.base-6)/20)
        var pul = emptyPulses(), slot = 0, win = emptyWin(), winB = Array(repeating:0.0,count:8), bump = emptyBump()
        for i in 0..<8 {
            let o = i*8
            if s.pul[o+4]>=s.pul[o+6] { continue }
            let gate = 1-smooth(s.pul[o+6]-0.5,s.pul[o+6],s.pul[o+4])
            pul.replaceSubrange(slot*4..<slot*4+4,with:[s.pul[o],s.pul[o+2]*gate,s.pul[o+3],s.pul[o+1]])
            slot += 1
        }
        for k in 0..<2 {
            let o = k*11
            if s.win[o] == 0 { continue }
            win.replaceSubrange(k*4..<k*4+4,with:[s.win[o+2],clamp01(s.win[o+4]),s.win[o+6],70])
            winB.replaceSubrange(k*4..<k*4+4,with:[s.win[o+7],s.win[o+8],0.3,0])
        }
        for k in 0..<2 {
            let o = k*6
            if s.bump[o+4]>=s.bump[o+5] { continue }
            bump.replaceSubrange(k*4..<k*4+4,with:[s.bump[o],s.bump[o+2]*bumpGate(s.bump[o+4],s.bump[o+5]),s.bump[o+3],s.bump[o+1]])
        }
        return buildFrame(phase:(s.t/loop).truncatingRemainder(dividingBy:1),waveP:s.waveP,amp:24+4.5*s.level,
                          wd:s.base+4*sin(s.idleP*2)*iw+20*s.level,level:s.level,idleW:iw,idleP:s.idleP,
                          think:clamp01(s.think),thinkU0:201+60*sin(2*Double.pi*thinkF*s.modeT),pul:pul,win:win,winB:winB,bump:bump,
                          dir:0,loose:0.3+0.7*s.level,gain:1+0.2*s.gain)
    }
}
