import Foundation
import CoreGraphics

enum GlassPiggySavingsMotion {
    static let loop = 7.0
    static let chips = [1, 5, 20]
    static let scale = 340.0 / 900
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
    static func smooth(_ k: Double) -> Double { let x=clamp(k); return x*x*(3-2*x) }
    static func easeOut(_ k: Double) -> Double { let x=1-clamp(k); return 1-x*x*x }
    static func easeIn(_ k: Double) -> Double { pow(clamp(k),3) }
    static func easeInOut(_ k: Double) -> Double {
        let x=clamp(k); return x<0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2
    }
    static func back(_ k: Double, _ c: Double = 1.7) -> Double {
        let x=clamp(k)-1; return 1+(c+1)*x*x*x+c*x*x
    }
    static func hash(_ i: Double) -> Double {
        let s=sin(i*12.9898+78.233)*43758.5453; return s-floor(s)
    }
    static func total(_ t: Double) -> Double {
        t >= 6.6 ? 1236 : 1236+20*easeInOut(seg(t,2.52,3.3))
    }
    struct Pig {
        var sq=0.0, sv=0.0, wob=0.0, wv=0.0
        mutating func step(_ dt: Double) {
            let n=max(1,Int(ceil(dt/(1.0/240)))); let h=dt/Double(n)
            for _ in 0..<n {
                sv += (-sq*330-sv*9)*h; sq += sv*h
                wv += (-wob*260-wv*7)*h; wob += wv*h
            }
        }
    }
    struct Held {
        var on=false, x=0.0, y=0.0, chip=2, dropAt = -10.0, fromX=0.0, fromY=0.0, backAt = -10.0
    }
    struct Layout {
        var w: Double, h: Double, top: Double, bottom: Double
        var pigX: Double { w/2-170+6 }
        var pigY: Double { top+250 }
        var slotX: Double { pigX+378*scale }
        var slotY: Double { pigY+116*scale }
        var chipY: Double { h-bottom-92 }
        func chipX(_ i: Int) -> Double { w/2+Double(i-1)*112 }
    }
    struct Frame {
        var t=0.0, now=0.0, pig=Pig(), held=Held(), bonus=0, bonusAt = -10.0, plus=20
        var total: Double { GlassPiggySavingsMotion.total(t)+Double(bonus) }
        var dip: Double { sin(seg(t,6.35,6.85)*Double.pi) }
        var floatK: Double {
            let a=t>=2.5 && t<3.6 ? seg(t,2.5,3.6) : -1
            let fb=now-bonusAt; let b=fb>=0 && fb<1.1 ? fb/1.1 : -1
            return max(a,b)
        }
        var receipt: (alpha: Double, y: Double) {
            let a=t>=2.6 && t<4.9 ? t-2.5-0.1 : -1
            let fb=now-bonusAt-0.05; let b=fb>=0 && fb<2.3 ? fb : -1
            let k=max(a,b)
            if k<0 { return (0,90) }
            let out=easeIn((k-1.9)/0.4)
            return (1-out,(1-back(k/0.35))*90+out*40)
        }
        func dot(_ i: Int) -> Double {
            var s=1.0
            if i==2 {
                if t>=0.9 && t<2.5 { s=0 }
                else if t>=2.5 { s=back(seg(t,2.75,3.05)) }
            }
            if held.chip==i {
                if held.on { s=0 }
                else {
                    let since=now-max(held.dropAt+0.23,held.backAt+0.45)
                    if since > -0.5 && since<0.35 { s=since<0 ? 0 : min(s,back(since/0.3)) }
                }
            }
            return max(0,s)
        }
    }
    struct Coin {
        var x=0.0, y=0.0, alpha=0.0, flip=1.0, radius=30.0, clipped=false
    }
    static func coin(_ f: Frame, _ l: Layout) -> Coin {
        let t=f.t, now=f.now, hd=f.held
        var c=Coin()
        if hd.on { c.x=hd.x; c.y=hd.y; c.alpha=1; c.flip=cos(now*9) }
        else if hd.dropAt>0 && now-hd.dropAt<0.3 {
            let k=easeIn(seg(now-hd.dropAt,0,0.23))
            c.x=mix(hd.fromX,l.slotX,min(1,k*1.6)); c.y=mix(hd.fromY,l.slotY+30,k)
            c.radius=mix(30,23,k); c.alpha=1; c.clipped=true
        } else if hd.backAt>0 && now-hd.backAt<0.45 {
            let k=back(seg(now-hd.backAt,0,0.45))
            c.x=mix(hd.fromX,l.chipX(hd.chip),k); c.y=mix(hd.fromY,l.chipY,k)
            c.alpha=1-smooth(seg(now-hd.backAt,0.3,0.45)); c.flip=cos((now-hd.backAt)*14)
        } else if t>=0.9 && t<2.5 {
            let pop=back(seg(t,0.9,1.06)), k=seg(t,1,1.8)
            let rise=easeOut(seg(k,0,0.62)), fall=easeIn(seg(k,0.62,1))
            c.x=mix(l.chipX(2),l.slotX,easeInOut(k))
            c.y=mix(l.chipY-18*pop,l.slotY-118,rise)+fall*78
            c.y += back(seg(t,1.8,2),1.2)*34+easeIn(seg(t,2.32,2.5))*44
            c.radius=mix(30,23,min(1,k*1.3)); c.alpha=min(1,pop*1.4)
            c.flip=t<1.8 ? cos(k*Double.pi*6) : 1; c.clipped=t>=1.8
        }
        return c
    }
    // RN's debug hook pins only the timeline. It does not replay the seat/land
    // impulses; its existing spring settles while the capture waits.
    static func replay(_ time: Double) -> Frame {
        var f=Frame()
        f.t=max(0,time.truncatingRemainder(dividingBy: loop))
        f.now=f.t
        return f
    }
}
