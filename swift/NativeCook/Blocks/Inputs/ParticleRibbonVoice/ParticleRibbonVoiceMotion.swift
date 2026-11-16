import Foundation

/// Value-for-value port of prv-motion.ts at a360cb096e20. All times are seconds and lengths design points.
enum ParticleRibbonVoiceMotion {
    static let loop = 9.0
    static let press = 0.55, release = 3.0, speak = 4.4, idleBack = 6.5, captionBack = 7.1
    static let grab = 6.6, letGo = 7.4, thinkIn = 3.05, thinkOut = 4.4
    static let thinkF = 7.0 / loop, pitch = 6 * Double.pi / 402
    static let maxWords = 7, captionLayers = 19
    static let user: [[Double]] = [[0.78,0.34],[1.02,0.42],[1.3,0.55],[1.62,0.74],[1.92,0.9],[2.2,1],[2.5,0.8],[2.78,0.58]]
    static let reply: [[Double]] = [[4.42,0.55],[4.82,0.7],[5.17,0.6],[5.52,0.5]]
    static let wordAt = reply.map { $0[0] + 243 / 260 }

    struct Frame {
        var phase: Double
        var line, sheet, twA, twB, think, pul, win, winB, bump, opt, extra: [Double]
    }
    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b-a)*t }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x-a)/(b-a)); return t*t*(3-2*t)
    }
    // JS Math.round resolves exact negative halves toward +infinity.
    static func jsRound(_ x: Double) -> Double { floor(x + 0.5) }
    static func wrap(_ t: Double) -> Double { (t.truncatingRemainder(dividingBy: loop)+loop).truncatingRemainder(dividingBy: loop) }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w*t)*(1+w*t) }
        let wd = w * sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    static func swell(_ tau: Double, _ rise: Double, _ fall: Double) -> Double {
        tau < 0 ? exp(-pow(tau/rise,2)) : exp(-tau/fall)
    }
    static func userLevel(_ t: Double) -> Double { 1-user.reduce(1.0) { $0*(1-$1[1]*swell(t-$1[0],0.07,0.3)) } }
    static func replyLevel(_ t: Double) -> Double { 1-reply.reduce(1.0) { $0*(1-$1[1]*swell(t-($1[0]+0.05),0.07,0.3)) } }
    static func emptyPulses() -> [Double] { Array(repeating: [0.0,0,40,0], count: 8).flatMap { $0 } }
    static func emptyWin() -> [Double] { [0,0,0,70,0,0,0,70] }
    static func emptyBump() -> [Double] { [0,0,80,0,0,0,80,0] }
    static func buildFrame(phase: Double, waveP: Double, amp: Double, wd: Double, level: Double,
                           idleW: Double, idleP: Double, think: Double, thinkU0: Double,
                           pul: [Double], win: [Double], winB: [Double], bump: [Double],
                           dir: Double, loose: Double, gain: Double) -> Frame {
        Frame(phase: phase, line: [476,amp,402,waveP], sheet: [wd,level,gain,0.7],
              twA: [0.55*idleW,300,idleP,0.4*idleW], twB: [-1.4*idleW,0,55,0],
              think: [think,thinkU0,pitch,0], pul: pul, win: win, winB: winB, bump: bump,
              opt: [1,loose,0,0], extra: [dir,0,0,0])
    }
    static func lineY(_ x: Double, _ f: Frame) -> Double {
        var y = f.line[0]+f.line[1]*cos(2*Double.pi*(x/f.line[2]-f.line[3]))
        for k in 0..<2 {
            let d = (x-f.bump[k*4])/max(f.bump[k*4+2],1)
            y += f.bump[k*4+1]*exp(-d*d)
        }
        return y
    }
    static func twistBase(_ u: Double, _ f: Frame) -> Double {
        var a = f.twA[0]*sin(2*Double.pi*u/f.twA[1]+f.twA[2])+f.twA[3]*((u-200)/402)
        a += f.twB[0]*exp(-pow(u/f.twB[2],2))+f.twB[1]*exp(-pow((402-u)/f.twB[2],2))
        a += f.think[0]*(u-f.think[1])*f.think[2]
        for i in 0..<8 {
            let x = (u-f.pul[i*4])/max(f.pul[i*4+2],1)
            a += f.pul[i*4+1]*exp(-x*x)
        }
        return a
    }
    static func faceOn(_ a: Double) -> Double { jsRound(a/Double.pi)*Double.pi }
    struct Pulse {
        var t0, x0, v, a0, w, tau, life, rise: Double
    }
    static let auto: [Pulse] = {
        var p = [Pulse(t0:press,x0:-42,v:260,a0:0.9*Double.pi,w:64,tau:0.9,life:1.5,rise:0)]
        for u in user { p.append(Pulse(t0:u[0]-0.18,x0:-42,v:260,a0:Double.pi*(0.3+0.55*u[1]),w:64,tau:0.9,life:1.5,rise:0)) }
        for r in reply { p.append(Pulse(t0:r[0],x0:444,v:-260,a0:Double.pi*(0.3+0.55*r[1]),w:64,tau:0.9,life:1.3,rise:0)) }
        for v in [-260.0,260] { p.append(Pulse(t0:letGo,x0:175+(v<0 ? -70:70),v:v,a0:0.7*Double.pi,w:40,tau:0.45,life:1.2,rise:0.12)) }
        return p
    }()
    static func bumpGate(_ age: Double, _ life: Double) -> Double { smooth(0,0.08,age)*(1-smooth(life-0.5,life,age)) }
    static func bumpsAt(_ t: Double) -> [Double] {
        let age = t-release
        if age < 0 || age > 1.4 { return emptyBump() }
        return [200+260*age,-14*bumpGate(age,1.4),80,260,0,0,80,0]
    }
    static func pulsesAt(_ t: Double) -> [Double] {
        var out = emptyPulses(), slot = 0
        for p in auto.reversed() {
            let age = t-p.t0
            if age < 0 || age > p.life { continue }
            if slot >= 8 { break }
            let gate = smooth(0,max(p.rise,0.001),age)*(1-smooth(p.life-0.5,p.life,age))
            out[slot*4] = p.x0+p.v*age; out[slot*4+1] = p.a0*exp(-age/p.tau)*gate
            out[slot*4+2] = p.w; out[slot*4+3] = p.v; slot += 1
        }
        return out
    }
    static func baseWidth(_ t: Double) -> Double {
        26+(6-26)*springAt(t-release,0.574,0.913)+(7-6)*springAt(t-speak,0.574,0.913)+(26-7)*springAt(t-idleBack,0.574,0.913)
    }
    static func frameNoWindow(_ input: Double) -> Frame {
        let t = wrap(input), lu = userLevel(t), lr = replyLevel(t), level = 1-(1-lu)*(1-lr)
        let base = baseWidth(t), iw = clamp01((base-6)/20)
        let wd = base+4*sin(2*Double.pi*2*t/loop)*iw+20*(lu+lr)
        let lag = 1-(1-userLevel(t-0.2))*(1-replyLevel(t-0.2))
        let think = springAt(t-thinkIn,0.5,1)-springAt(t-thinkOut,0.5,1)
        return buildFrame(phase:t/loop,waveP:-0.07+3*t/loop,amp:24+4.5*lag,wd:wd,level:level,idleW:iw,
                          idleP:1+2*Double.pi*t/loop,think:clamp01(think),thinkU0:201+60*sin(2*Double.pi*thinkF*(t-release)),
                          pul:pulsesAt(t),win:emptyWin(),winB:Array(repeating:0,count:8),bump:bumpsAt(t),dir:0,loose:0.3+0.7*level,gain:1)
    }
    static let grabFaceOn = faceOn(twistBase(300,frameNoWindow(grab)))
    static func frameAt(_ input: Double) -> Frame {
        let t = wrap(input), k = smooth(6.75,7.3,t), e = k*k*(3-2*k)
        let x = 300+(175-300)*e, dy = -70*smooth(6.7,7.05,t)+50*smooth(7.1,7.38,t)
        let strength = clamp01(springAt(t-grab,0.28,0.9)-springAt(t-letGo,0.5,1))
        var f = frameNoWindow(t)
        if strength > 0.001 {
            f.win = [x,strength,grabFaceOn,70,0,0,0,70]
            f.winB = [lineY(x,f)+dy,26*clamp01(abs(dy)/90),0.3,0,0,0,0,0]
        }
        return f
    }
    static func outAt(_ t: Double, _ a: Double) -> Double { smooth(a,a+0.1,t) }
    static func inAt(_ t: Double, _ a: Double) -> Double { smooth(a+0.07,a+0.07+0.14,t) }
    static func captionAt(_ input: Double) -> [Double] {
        let t = wrap(input)
        var out = Array(repeating:0.0,count:19)
        out[0] = clamp01(1-outAt(t,press)+inAt(t,captionBack))
        out[1] = inAt(t,press)*(1-outAt(t,release))
        let dots = inAt(t,release)*(1-outAt(t,speak))
        out[2] = dots*smooth(release+0.12,release+0.2,t)
        out[3] = dots*smooth(release+0.25/thinkF,release+0.25/thinkF+0.08,t)
        out[4] = dots*smooth(release+0.75/thinkF,release+0.75/thinkF+0.08,t)
        for i in wordAt.indices {
            out[5+i] = (1-outAt(t,captionBack))*smooth(wordAt[i],wordAt[i]+0.06,t)
            out[12+i] = t >= wordAt[i] ? out[5+i]*(1-smooth(wordAt[i],wordAt[i]+0.24,t)):0
        }
        return out
    }
    static func askAt(_ input: Double) -> [Double] {
        let t = wrap(input), gone = 1-smooth(captionBack,captionBack+0.2,t)
        let syllables = [1,1,2,1,1,1,1]
        var out = Array(repeating:0.0,count:8), heard = 0
        for i in syllables.indices {
            let at = 0.78+Double(heard)*(release-0.78)/8
            out[i] = smooth(at,at+0.12,t)*gone; heard += syllables[i]
        }
        out[7] = smooth(press-0.06,press+0.06,t)*(1-smooth(release-0.06,release+0.06,t))
        return out
    }
    static func stateAt(_ input: Double) -> Int {
        let t = wrap(input)
        if t >= press && t < release { return 1 }
        if t >= release && t < speak { return 2 }
        if t >= speak && t < idleBack { return 3 }
        return 0
    }
    static func reducedFrame(_ mode: Int, _ level: Double, _ pulse: Double) -> Frame {
        buildFrame(phase:0,waveP:-0.07,amp:24,wd:(mode == 2 ? 8:mode == 3 ? 7:26)+20*level,
                   level:mode == 2 ? 0:level,idleW:mode == 2 ? 0:1,idleP:1,think:0,thinkU0:201,
                   pul:emptyPulses(),win:emptyWin(),winB:Array(repeating:0,count:8),bump:emptyBump(),
                   dir:mode == 1 ? 1:mode == 3 ? -1:0,loose:0,gain:mode == 2 ? 0.75+0.25*pulse:1)
    }
    static func lerpArr(_ x: [Double], _ y: [Double], _ w: Double) -> [Double] { zip(x,y).map { mix($0.0,$0.1,w) } }
    static func blendFrame(_ a: Frame, _ b: Frame, _ w: Double) -> Frame {
        var f = a
        let wb = b.line[3]+jsRound(a.line[3]-b.line[3])
        let ib = b.twA[2]+2*Double.pi*jsRound((a.twA[2]-b.twA[2])/(2*Double.pi))
        f.phase = b.phase
        f.line = [mix(a.line[0],b.line[0],w),mix(a.line[1],b.line[1],w),b.line[2],mix(a.line[3],wb,w)]
        f.sheet = lerpArr(a.sheet,b.sheet,w)
        f.twA = [mix(a.twA[0],b.twA[0],w),b.twA[1],mix(a.twA[2],ib,w),mix(a.twA[3],b.twA[3],w)]
        f.twB = lerpArr(a.twB,b.twB,w)
        f.think = [mix(a.think[0],b.think[0],w),mix(a.think[1],b.think[1],w),a.think[2],0]
        for i in 0..<8 { f.pul[i*4+1] *= 1-w }
        for i in 0..<2 { f.win[i*4+1] *= 1-w; f.bump[i*4+1] *= 1-w }
        f.opt = lerpArr(a.opt,b.opt,w); f.extra = lerpArr(a.extra,b.extra,w)
        return f
    }
}
