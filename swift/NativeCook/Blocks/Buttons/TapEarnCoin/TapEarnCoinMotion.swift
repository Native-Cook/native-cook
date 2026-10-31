import Foundation

// Pure transcription of tec-motion.ts. All times are seconds and all geometry is points.
enum TapEarnCoinMotion {
    static let loop = 10.0
    static let taps = 3
    static let base = 438.0
    static let tapAt = [0.9, 1.65, 2.4]
    static let tapPay = [7.0, 6.0, 5.0]
    static let tapLean = [[0.05, -0.04], [-0.06, 0.03], [0.02, 0.05]]
    static let tapPower = [0.0, 0.0, 1.0]
    static let pressIn = 0.09, release = 0.11, burstLife = 1.7, land = 0.62
    static let coins = 9, coinsPerPower = 3, stride = 4
    static let resumeAfter = 2.0, sheetDismiss = 120.0, sheetFlick = 800.0, refillSeconds = 3.0
    enum T {
        static let squash = 3.0, lift0 = 3.16, lift1 = 3.62, morph0 = 3.3, morph1 = 4.42
        static let ringAt = 4.42, travel0 = 4.78, travel1 = 5.52, dim0 = 4.62, dim1 = 5.1
        static let sheet0 = 4.78, rewind0 = 5.7, rewind1 = 6.4, laterIn = 7.0, laterOut = 7.14
        static let close0 = 7.2, back0 = 7.26, back1 = 7.9, unmorph0 = 7.72, unmorph1 = 8.62
        static let drop0 = 8.5, drop1 = 8.95, land = 8.95, refill = 9.3
    }
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func easeOut(_ x: Double) -> Double { let u = 1-x; return 1-u*u*u }
    static func easeIn(_ x: Double) -> Double { x*x*x }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a+(b-a)*t }
    static func springTo(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2*Double.pi/response, wd = w*sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+(zeta*w/wd)*sin(wd*t))
    }
    static func ring(_ t: Double, _ freq: Double, _ decay: Double) -> Double {
        t <= 0 ? 0 : sin(2*Double.pi*freq*t)*exp(-decay*t)
    }
    static func hash(_ n: Double) -> Double { let s = sin(n*12.9898+78.233)*43758.5453; return s-floor(s) }
    struct Scene {
        var press = 0.0, hop = 0.0, morph = 0.0, lift = 0.0, travel = 0.0
        var dim = 0.0, sheet = 0.0, swing = 0.0, amount = base, later = 0.0
        var label = 3, leanX = 0.0, leanZ = 0.0
    }
    static func payBefore(_ t: Double) -> Double {
        var a = 0.0
        for i in 0..<3 { let r = tapAt[i]+release+0.12; a += tapPay[i]*easeOut(seg(t,r,r+0.5)) }
        return a
    }
    static func leanAt(_ t: Double, _ axis: Int) -> Double {
        var l = 0.0
        for i in 0..<3 {
            let a = tapAt[i], v = tapLean[i][axis]
            if t >= a && t < a+release { l = v*easeOut(seg(t,a,a+pressIn)) }
            else if t >= a+release && t < a+release+0.7 { l = v*(1-springTo(t-a-release,0.3,0.45)) }
        }
        return l
    }
    static func pressAt(_ t: Double) -> Double {
        var p = 0.0
        for a in tapAt {
            if t >= a && t < a+pressIn { p = easeOut(seg(t,a,a+pressIn)) }
            else if t >= a+pressIn && t < a+release { p = 1 }
            else if t >= a+release && t < a+release+0.7 { p = 1-springTo(t-a-release,0.32,0.42) }
        }
        return p
    }
    static func sceneAt(_ t: Double, _ b: Double = base) -> Scene {
        let squat = seg(t,T.squash,T.lift0), landT = t-T.land
        let hop = sin(Double.pi*squat)*0.55 + (landT > 0 ? ring(landT,2.6,7)*0.9 : 0)
        let lift = easeOut(seg(t,T.lift0,T.lift1))*(1-easeInOut(seg(t,T.drop0,T.drop1)))
        let morph = easeInOut(seg(t,T.morph0,T.morph1))*(1-easeInOut(seg(t,T.unmorph0,T.unmorph1)))
        let travel = easeInOut(seg(t,T.travel0,T.travel1))*(1-easeInOut(seg(t,T.back0,T.back1)))
        let dim = easeOut(seg(t,T.dim0,T.dim1))*(1-easeInOut(seg(t,T.close0,T.close0+0.5)))
        let up = springTo(t-T.sheet0,0.62,0.82), down = easeInOut(seg(t,T.close0,T.close0+0.42))
        let sheet = t < T.close0 ? up : (1-down)*min(1,up)
        let swing = ring(t-T.ringAt,2.2,3.6)*0.32
        let amount = b+payBefore(t)*(1-easeInOut(seg(t,T.rewind0,T.rewind1)))
        let later = easeOut(seg(t,T.laterIn,T.laterIn+0.08))*(1-easeOut(seg(t,T.laterOut,T.laterOut+0.2)))
        var label = taps
        for i in 0..<3 { if t >= tapAt[i]+release { label = taps-1-i } }
        if t >= T.close0+0.6 { label = 4 }
        if t >= T.refill { label = taps }
        return Scene(press: pressAt(t), hop: hop, morph: morph, lift: lift, travel: travel,
                     dim: dim, sheet: sheet, swing: swing, amount: amount, later: later,
                     label: label, leanX: leanAt(t,0), leanZ: leanAt(t,1))
    }
    static func gleamAt(_ t: Double) -> Double {
        var g = -1.0
        for a in tapAt { let r = a+release; if t >= r && t < r+0.75 { g = -1+2*easeOut(seg(t,r,r+0.75)) } }
        return g
    }
    static func loopBursts(_ t: Double) -> [Double] {
        var out: [Double] = []
        for i in 0..<3 { let age = t-tapAt[i]-release; if age >= 0 && age < burstLife { out += [age,Double(11+i*7),tapPay[i],tapPower[i]] } }
        return out
    }
    static func glowOf(_ bursts: [Double]) -> Double {
        var g = 0.0
        for b in Swift.stride(from: 0, to: bursts.count, by: stride) where b+3 < bursts.count {
            let age = bursts[b]; if age < 0 { continue }
            g = max(g,(1-exp(-age/0.04))*exp(-age/0.42)*(0.8+0.2*bursts[b+3]))
        }
        return g
    }
    static func boxBump(_ bursts: [Double], _ k: Int) -> Double {
        var v = 0.0
        for b in Swift.stride(from: 0, to: bursts.count, by: stride) where b+3 < bursts.count {
            let age = bursts[b]-land; if age < 0 || age > 0.8 { continue }
            for i in 0..<2 where (i+Int(floor(bursts[b+1])))%3 == k {
                v += sin(min(age/0.09,1)*Double.pi*0.5)*exp(-age/0.16)*(1+ring(age,3.2,9)*0.4)
            }
        }
        return v
    }
    static func columnPos(_ amount: Double, _ k: Int) -> Double {
        let unit = pow(10,Double(k)), whole = floor(amount/unit)
        if k == 0 { return amount }
        return whole+clamp01(amount-whole*unit-(unit-1))
    }
    struct CoinPose { var x: Double; var y: Double; var r: Double; var tilt: Double; var spin: Double; var alpha: Double; var lit: Double; var roll: Double }
    static func coinPose(_ age: Double, _ seed: Double, _ i: Int, _ power: Double,
                         _ ox: Double, _ oy: Double, _ R: Double, _ tx: Double, _ ty: Double, _ floorY: Double) -> CoinPose {
        let j = Double(i)
        let h1 = hash(seed*17+j*3.1), h2 = hash(seed*5.3+j*11.7), h3 = hash(seed*9.1+j*1.9), h4 = hash(seed*2.7+j*23.3)
        let r = R*(0.085+0.035*h3), sx = ox+(h1-0.5)*R*0.55, sy = oy-R*0.2
        let emerge = easeOut(seg(age,0,0.08)), roll = age*(7+11*h2)+h4*6.28
        if i < 2 {
            let u = easeIn(seg(age,0,land)), a = 1-u
            let cx = mix(sx,tx,0.5)+(i == 0 ? -1.0 : 1.0)*R*0.5, cy = min(sy,ty)-R*(0.95+0.25*h1)
            let x = a*a*sx+2*a*u*cx+u*u*tx, y = a*a*sy+2*a*u*cy+u*u*ty
            let shrink = 1-easeIn(seg(age,land-0.12,land+0.02))
            return CoinPose(x:x,y:y,r:r*emerge*(0.75+0.25*shrink),tilt:cos(roll),spin:roll*0.3,alpha:shrink,lit:0.5+0.5*sin(roll*1.3),roll:roll)
        }
        let boost = 1+0.18*power, vx = (h1-0.5)*R*4.2*boost, vy = -R*(3.6+2.6*h2)*boost, g = R*13.5
        let disc = vy*vy-2*g*(sy-floorY), tHit = disc > 0 ? (-vy+sqrt(disc))/g : 99
        var x: Double, y: Double, tilt = cos(roll), landed = 0.0
        if age < tHit { x = sx+vx*age; y = sy+vy*age+0.5*g*age*age }
        else {
            let a = age-tHit, up = -(vy+g*tHit)*0.3, tRest = -2*up/g, xHit = sx+vx*tHit
            x = xHit+vx*0.5*min(a,tRest+0.15)
            y = a < tRest ? floorY+up*a+0.5*g*a*a : floorY
            landed = seg(a,tRest,tRest+0.12); tilt = mix(cos(roll),0.22,landed)
        }
        return CoinPose(x:x,y:y-r*0.25*landed,r:r*emerge,tilt:tilt,
                        spin:mix((h3-0.5)*1.4+age*(h1-0.5)*3,0,landed),
                        alpha:1-seg(age,min(burstLife-0.25,tHit+0.55),min(burstLife,tHit+0.8)),
                        lit:0.5+0.5*sin(roll*1.3+h3*4),roll:landed > 0.99 ? Double.pi*0.5 : roll)
    }
    static func pillPose(_ age: Double, _ seed: Double) -> (dx:Double,rise:Double,scale:Double,alpha:Double) {
        (dx:(hash(seed*3.3)-0.5)*0.9,rise:easeOut(seg(age,0,0.9)),scale:0.7+0.3*springTo(age,0.34,0.55),alpha:clamp01(age/0.06)*(1-seg(age,0.75,1)))
    }
}
