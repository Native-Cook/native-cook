import Foundation

/// Pure film functions. All timings and envelopes are shared with the React Native original.
enum SpikeToStairChartFilm {
    typealias Curves = SpikeToStairChartCurves
    static func clamp(_ n: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { Curves.clamp(n, lo, hi) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { Curves.mix(a, b, p) }

    struct Frame {
        var L = 0.0
        var life = 0.0
        var d = 1
        var end = 1.0
        var headU = 0.0
        var hx = 0.0
        var hy = 0.0
        var angle = 0.0
        var area = 1.0
        var broken = 0.0
        var heat = 0.0
        var heatEnd = 0.0
        var gather = 0.0
        var label = 1.0
        var grid = 1.0
        var thumb = 1.0
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var coreR = 0.0
        var coreG = 0.0
        var coreB = 0.0
        var axisLight = 0.0
        var heatStart = 0.0
        var comet = 0.0
        var gatherTime = 0.0
        var gatherFlash = 0.0
        var glow = 1.0
        var pool = 1.0
        var jolt = 0.0
        var dip = 0.0
        var flash = 0.0
        var glitch = 0.0
        var packet = 0.0
        var packetStart = 0.0
        var packetEnd = 0.0
        var markerU = 0.0
        var markerAlpha = 0.0
        var arrow = 1.0
    }

    struct PiecePose {
        var x: Double
        var y: Double
        var sy: Double
        var angle: Double
        var alpha: Double
    }

    struct ShardPose {
        var x: Double
        var y: Double
        var angle: Double
        var alpha: Double
    }

    static func smooth(_ n: Double) -> Double {
        let p = clamp(n)
        return p * p * (3 - 2 * p)
    }

    static func ease(_ n: Double) -> Double {
        let x = clamp(n)
        var lo = 0.0
        var hi = 1.0
        var t = x
        for _ in 0..<16 {
            t = (lo + hi) / 2
            let q = 1 - t
            let v = 3*q*q*t*0.77 + 3*q*t*t*0.175 + t*t*t
            if v < x { lo = t } else { hi = t }
        }
        return 3 * (1 - t) * t * t + t*t*t
    }

    static func pulse(_ t: Double, _ up: Double, _ down: Double) -> Double {
        t < 0 ? 0 : t < up ? smooth(t / up) : 1 - smooth((t - up) / down)
    }

    static func spring(_ t: Double, _ damping: Double, _ stiffness: Double) -> Double {
        guard t > 0 else { return 0 }
        let a = damping / 2
        let w = sqrt(stiffness - a*a)
        return 1 - exp(-a*t) * (cos(w*t) + a/w * sin(w*t))
    }

    static func motivationU(_ s: Double) -> Double {
        let times = [0.0, 0.7, 0.87, 1.04, 1.2, 1.37, 1.54, 1.7, 1.87, 2.04, 2.2]
        let us = [0.0, 0.27, 0.31, 0.38, 0.52, 0.62, 0.78, 0.85, 0.92, 0.945, 0.957]
        var i = 0
        while i < times.count - 2 && s > times[i + 1] { i += 1 }
        let dt = times[i + 1] - times[i]
        let p = clamp((s - times[i]) / dt)
        let p2 = p*p
        let p3 = p2*p
        let slope = (us[i+1] - us[i]) / dt
        let before = i > 0 ? min(slope, (us[i]-us[i-1])/(times[i]-times[i-1])) : slope
        let after = i < times.count-2 ? min(slope, (us[i+2]-us[i+1])/(times[i+2]-times[i+1])) : 0
        return (2*p3-3*p2+1)*us[i] + (p3-2*p2+p)*dt*before + (-2*p3+3*p2)*us[i+1] + (p3-p2)*dt*after
    }

    static func piecePose(_ L: Double, _ i: Int) -> PiecePose {
        let p = clamp((L - 7.05 - Double(5-i)*0.045) / 0.3)
        let fall = p*p
        let bounce = p > 0.85 ? sin((p-0.85)/0.15 * .pi) * 0.06 : 0
        let settle = smooth((p-0.7)/0.3)
        let flatten = mix(1, 0.03, settle)
        let piece = Curves.pieces[i]
        let angle = (Double(i % 2 == 1 ? 1 : -1) * .pi / 30) * sin(.pi*p)
        let bottomOffset = (piece.bottom-piece.cy)*flatten*cos(angle) + (piece.right-piece.left)/2*abs(sin(angle))
        let y = min(mix(piece.cy,510,fall-bounce), 510-bottomOffset)
        return PiecePose(x: piece.cx, y: y, sy: flatten, angle: angle, alpha: mix(1,0.6,settle))
    }

    static func shardPose(_ L: Double, _ i: Int, still: Bool, life: Double) -> ShardPose {
        let rest = Curves.shards[i]
        var p = 1.0
        var alpha = 1.0
        if L >= 3.6 && L < 9.41 {
            p = 1 - smooth((L-3.6)/0.35)
            alpha = p
        }
        if L >= 9.41 {
            let t = L - 9.41
            p = 1 - exp(-t/0.22)
            alpha = smooth(t/0.08)
        }
        if L > 11.4 { p = mix(p,1,smooth((L-11.4)/0.6)) }
        let bob = still ? 0 : sin(life * .pi / 2 + Double(i)*1.7)*2
        return ShardPose(x: mix(330,rest[0]+bob,p),
                         y: mix(250,rest[1]+(still ? 0 : cos(life * .pi / 2 + Double(i))*2),p),
                         angle: (rest[3]+(1-p)*(40+16*Double(i))+(still ? 0 : 3*sin(life * .pi / 2+Double(i)))) * .pi / 180,
                         alpha: alpha*rest[4])
    }

    static func demoU(_ L: Double) -> Double {
        if L < 1.05 { return 0.8 }
        if L < 2.3 { return mix(0.8,0.16,ease((L-1.05)/1.15)) }
        if L < 3.05 { return mix(0.16,0.62,ease((L-2.3)/0.75)) }
        return mix(0.62,Curves.nearestTread(0.62),spring(L-3.05,22,240))
    }

    static func frame(_ time: Double, still: Bool = false) -> Frame {
        let L = (time.truncatingRemainder(dividingBy: 12) + 12).truncatingRemainder(dividingBy: 12)
        var f = Frame()
        f.L = L
        f.life = L
        var mood = 2.0
        if L >= 3.6 && L < 4.15 {
            f.end = 1 - ease((L-3.6)/0.5)
            mood = L < 4.1 ? mix(2,1,smooth((L-3.6)/0.5)) : mix(1,2,smooth((L-4.1)/0.05))
            f.label = 1 - smooth((L-3.7)/0.45)
            f.grid = 1 - smooth((L-3.6)/0.55)
            f.thumb = 1 - spring(L-3.6,20,260)
        }
        if L >= 4.15 && L < 8.07 {
            f.d = 0
            f.label = 0
            f.grid = 0
            f.thumb = 0
            let s = L - 4.15
            f.end = Curves.atU(0,motivationU(s)).a
            if s < 0.8 { mood = 2 }
            else if s < 1 { mood = 2*(1-smooth((s-0.8)/0.2)) }
            else if s < 1.12 { mood = 0 }
            else if s < 1.3 { mood = 2*smooth((s-1.12)/0.18) }
            else if s < 1.62 { mood = 2 }
            else if s < 1.86 { mood = 2*(1-smooth((s-1.62)/0.24)) }
            else { mood = 0 }
            if L >= 6.95 { f.thumb = spring(L-6.95,20,260) }
            if L >= 7.05 {
                f.broken = smooth((L-7.05)/0.04)
                f.area = 1-smooth((L-7.05)/0.18)
            }
            f.label = smooth((L-7.35)/0.65)
            f.grid = smooth((L-7.35)/0.6)
            if L >= 7.57 {
                f.heat = 1
                f.heatEnd = 1-ease((L-7.57)/0.42)
                f.end = f.heatEnd
                f.broken = 1-smooth((L-7.57)/0.04)
            }
            if L >= 7.99 {
                f.heat = 1
                f.heatEnd = 0
                f.gather = sin(.pi * clamp((L-7.99)/0.08))
            }
        }
        if L >= 8.07 && L < 9.59 {
            mood = L < 8.55 ? 0 : L < 8.72 ? smooth((L-8.55)/0.17) : 1+smooth((L-8.72)/0.23)
            if L < 9.23 {
                let t = L-8.07
                let distance = t < 0.15 ? 0.6*t+0.4*(t*t/(2*0.15)) : t-0.03
                f.end = Curves.stairEnd * clamp(distance/1.13)
            } else {
                let t = clamp((L-9.23)/0.36)
                f.end = mix(Curves.stairEnd,1,(t+t*t)/2)
            }
        }
        let head = Curves.atArc(f.d,f.end)
        f.headU = head.u
        f.hx = head.x
        f.hy = head.y
        f.angle = head.angle
        if L >= 7.05 && L < 7.57 {
            let p = piecePose(L,5)
            let dx = head.x - Curves.pieces[5].cx
            let dy = (head.y - Curves.pieces[5].cy)*p.sy
            f.hx = p.x+dx*cos(p.angle)-dy*sin(p.angle)
            f.hy = p.y+dx*sin(p.angle)+dy*cos(p.angle)
            f.angle = mix(head.angle,.pi,smooth((L-7.05)/0.3))
        }
        if L >= 7.57 && L < 8.07 {
            f.hx = 64+274*0.957*f.heatEnd
            f.hy = 510
            f.angle = mix(.pi,2 * .pi+Curves.atArc(1,0).angle,smooth((L-7.99)/0.08))
        }
        let impact = pulse(L-9.41,0.025,0.3)
        let crash = pulse(L-6.35,0.02,0.4)
        let red = [255.0,45,61]
        let amber = [230.0,198,90]
        let green = [40.0,224,122]
        let channels = (0..<3).map { i in
            mood < 1 ? mix(red[i],amber[i],clamp(mood)) : mix(amber[i],green[i],clamp(mood-1))
        }
        f.r = channels[0]
        f.g = channels[1]
        f.b = channels[2]
        f.coreR = mix(f.r+(255-f.r)*0.55,182,impact*0.6)
        f.coreG = mix(f.g+(255-f.g)*0.55,255,impact*0.6)
        f.coreB = mix(f.b+(255-f.b)*0.55,228,impact*0.6)
        f.heatStart = 1
        for i in 0..<6 {
            let landed = smooth((L-(7.05+Double(5-i)*0.045+0.27))/0.025)
            f.heatStart -= (Curves.pieces[i].right-Curves.pieces[i].left)/(274*0.957)*landed
        }
        f.heatStart = clamp(f.heatStart)
        f.axisLight = smooth((L-7.32)/0.025)*(1-smooth((L-8.07)/0.08))
        if L >= 7.32 && L < 7.57 {
            f.heat = f.axisLight
            f.heatEnd = 1
        }
        f.comet = smooth((L-7.32)/0.25)*(1-smooth((L-8.07)/0.08))
        f.gatherTime = clamp((L-7.99)/0.16)
        f.gatherFlash = smooth((L-7.99)/0.008)*(1-f.gatherTime)
        let idle = ((1-smooth((L-3.5)/0.1))+smooth((L-10)/0.45))*Double(f.d)
        let breathe = still ? 1 : 1+0.06*sin(L * .pi / 2)*idle
        let ember = L >= 6.35 && L < 7.05 ? sin((L-6.35)*53)*sin((L-6.35)*19)*0.1*smooth((L-6.35)/0.1)*(1-smooth((L-6.95)/0.1)) : 0
        f.glow = breathe+ember
        f.pool = max(1+1.5*max(impact,crash),1+f.axisLight)
        f.jolt = still ? 0 : L >= 9.41 ? (1-spring(L-9.41,14,320))*smooth((L-9.41)/0.02) : 0
        f.dip = still ? 0 : L >= 6.35 && L < 7.05 ? 3*pulse(L-6.35,0.035,0.28) : 0
        f.area *= 1+1.2*pulse(L-9.41,0.025,0.35)
        f.flash = impact
        f.glitch = pulse(L-9.41,0.02,0.16)
        let phase = L.truncatingRemainder(dividingBy: 4)/1.6
        f.packet = still ? 0 : idle*smooth(phase/0.08)*(1-smooth((phase-0.92)/0.08))
        f.packetStart = clamp(phase-0.06)
        f.packetEnd = clamp(phase)
        f.markerU = demoU(L)
        f.markerAlpha = still ? 0 : smooth((L-0.9)/0.15)*(1-smooth((L-3.3)/0.2))
        f.arrow = f.d == 1 ? 1-smooth((f.end-0.985)/0.015) : 1
        return f
    }

    static let columnEvents: [[Double]] = Curves.risers.map { u in
        var lo = 8.07
        var hi = 9.59
        for _ in 0..<24 {
            let t = (lo+hi)/2
            if frame(t,still: true).headU < u { lo = t } else { hi = t }
        }
        var lo1 = 1.05
        var hi1 = 2.2
        for _ in 0..<24 {
            let t = (lo1+hi1)/2
            if demoU(t) > u { lo1 = t } else { hi1 = t }
        }
        var lo2 = 2.3
        var hi2 = 3.05
        for _ in 0..<24 {
            let t = (lo2+hi2)/2
            if demoU(t) < u { lo2 = t } else { hi2 = t }
        }
        return [(lo+hi)/2, u <= 0.8 && u >= 0.16 ? (lo1+hi1)/2 : -100,
                u <= 0.62 && u >= 0.16 ? (lo2+hi2)/2 : -100]
    }

    static func columnFlash(_ L: Double, _ i: Int) -> Double {
        columnEvents[i].map { pulse(L-$0,0.12,0.4) }.max() ?? 0
    }

    static func sparkPoint(_ L: Double, _ i: Int) -> CGPoint {
        var t = L-6.35
        var x = 64+0.957*274
        var active = i < 12
        if L >= 7.05 {
            let piece = i/5
            t = L-(7.05+Double(5-piece)*0.045+0.3)
            x = Curves.pieces[piece].cx + Double((i*29)%13-6)*3
            active = i%5 < 3
        }
        let height = Double(10+(i*17)%31)
        let gravity = 1400.0
        let vertical = sqrt(2*gravity*height)
        let horizontal = Double((i*37)%17-8)*7
        guard active && t >= 0 && t <= 2*vertical/gravity else { return CGPoint(x: -100,y: -100) }
        return CGPoint(x: x+horizontal*t, y: 510-vertical*t+0.5*gravity*t*t)
    }
}
