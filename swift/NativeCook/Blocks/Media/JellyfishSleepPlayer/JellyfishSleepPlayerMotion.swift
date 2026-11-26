import Foundation

/// Same clocks and takeover rules as the committed RN reference; all times are seconds.
final class JellyfishSleepPlayerMotion {
    struct Frame {
        var t = 0.0, m = 0.0, c = 0.0
        var a = 0, b = 0
        var k = 0.0, wave = 1.0, lit = 1.0, prog = 0.12
        var playing = true
    }
    var automatic = true, last = -1.0, start = -1.0, waveFrom = 0.0, motesFrom = 0.0
    var still = false, p = 0.0, t = 0.0, m = 0.0, amp = 1.0, speed = 1.0, dim = 0.0
    var playing = true, a = 0, b = 0, age = -1.0, pending = 0, inFor = 0.0, touches = 0, idle = 0.0
    private(set) var out = Frame()
    static func unit(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mod(_ x: Double, _ n: Double) -> Double { x - n * floor(x / n) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    static func pulse(_ p: Double) -> Double {
        let f = mod(p, 2)/2
        return f < 0.22 ? pow(sin(f/0.22 * .pi/2), 2) : pow(cos((f-0.22)/0.78 * .pi/2), 2)
    }
    static func track(_ t: Double) -> (a: Int, b: Int, k: Double, age: Double) {
        for (s, e, a, b) in [(0.0,2.4,0,0),(2.4,3.2,0,1),(3.2,5.0,1,1),(5.0,5.8,1,2),(5.8,7.2,2,2),(7.2,8.0,2,0)] {
            if t >= s && t < e { return (a,b,a == b ? 0 : ease(unit((t-s)/(e-s))),t-s) }
        }
        return (0,0,0,0)
    }
    static func progress(_ k: Double, _ age: Double) -> Double { k > 0 ? 0 : min(1, 0.12+age/30) }
    static func frame(_ time: Double, waveFrom: Double = 0, motesFrom: Double = 0) -> Frame {
        let t = mod(time, 8), tr = track(t), came = [0.0,3.2,5.8]
        return Frame(t: waveFrom+t, m: motesFrom+t, c: pulse(t), a: tr.a, b: tr.b, k: tr.k,
                     prog: progress(tr.k, mod(t-came[tr.a]+8,8)))
    }
    private func takeOver() {
        guard automatic else { return }
        let clock = start < 0 ? 0 : Self.mod(last-start,8), tr = Self.track(clock)
        automatic = false; p = clock; t = waveFrom+clock; m = motesFrom+clock
        a = tr.a; b = tr.b; age = tr.a == tr.b ? -1 : tr.age
        inFor = Self.mod(clock-[0,3.2,5.8][tr.a]+8,8)
    }
    var changeTime: Double { still ? 0.3 : 0.8 }
    /// Returns the requested target, including queued changes, for one user feedback event.
    @discardableResult func changeTrack(_ dir: Int) -> Int {
        takeOver(); idle = 0
        if age >= 0 { pending += dir; return (b+pending%3+3)%3 }
        b = (a+dir+3)%3; age = 0; return b
    }
    func togglePlay() { takeOver(); idle = 0; playing.toggle() }
    func touchDown() { takeOver(); touches += 1; idle = 0 }
    func touchUp() { touches = max(0,touches-1); idle = 0 }
    func setStill(_ value: Bool) {
        guard value != still else { return }; still = value
        if value { takeOver() } else if !automatic { idle = 2 }
    }
    func step(_ now: Double, autoPlay: Bool) {
        let dt = last < 0 ? 0 : min(max(now-last,0),0.05); last = now
        if automatic {
            if autoPlay && !still {
                if start < 0 { start = now }
                out = Self.frame(Self.mod(now-start,8),waveFrom: waveFrom,motesFrom: motesFrom); return
            }
            takeOver()
        }
        let dir = playing ? 1.0 : -1.0
        amp = Self.unit(amp+dir*dt/0.6); speed = Self.unit(speed+dir*dt); dim = Self.unit(dim-dir*dt/0.6)
        let beforeP = p
        if playing || amp > 0 { p += dt }; t += dt*speed; m += dt
        if playing { inFor += dt }
        if age >= 0 {
            age += dt
            if age >= changeTime {
                a = b; age = -1; inFor = 0
                let next = (a+pending%3+3)%3; pending = 0
                if next != a { b = next; age = 0 }
            }
        }
        let k = age >= 0 ? Self.ease(Self.unit(age/changeTime)) : 0
        out = Frame(t: t, m: still ? 0 : m, c: still ? 0 : Self.pulse(p)*amp, a: a, b: age >= 0 ? b : a, k: k,
                    wave: still ? 0 : 1, lit: (1-0.15*dim)*(still ? 1+0.08*sin(2 * .pi*m/4) : 1),
                    prog: Self.progress(k,inFor), playing: playing)
        if touches > 0 { idle = 0; return }; idle += dt
        guard autoPlay && !still && idle >= 2 else { return }
        playing = true
        if age < 0 && a != 0 { b = 0; age = 0 }
        guard age < 0 && a == 0 && amp >= 1 && speed >= 1 && dim <= 0 && floor(p/2) != floor(beforeP/2) else { return }
        let over = Self.mod(p,2)
        automatic = true; start = now-over; waveFrom = t-over; motesFrom = m-over
        out = Self.frame(over,waveFrom: waveFrom,motesFrom: motesFrom)
    }
}
