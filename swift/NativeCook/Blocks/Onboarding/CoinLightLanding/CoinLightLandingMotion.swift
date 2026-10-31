import Foundation
import CoreGraphics

enum CoinLightLandingMotion {
    static let loop = 8.0
    static let base = 733000.0
    static let gain = 48.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func easeInOut(_ k: Double) -> Double {
        let x = clamp(k)
        return x < 0.5 ? 4*x*x*x : 1 - pow(-2*x+2, 3)/2
    }
    static func easeOut(_ k: Double) -> Double { 1 - pow(1-clamp(k), 3) }
    static func easeOutBack(_ k: Double) -> Double {
        let x = clamp(k)-1
        return 1 + 2.5*x*x*x + 1.5*x*x
    }
    struct Frame {
        var x = 0.0, y = 0.0, a = 1.0, s = 1.0, yaw = 0.0, tx = 0.0
        var pay = base, button = 0.0, t = 0.0
        var dip: Double { t >= 7.2 && t < 7.7 ? sin((t-7.2)/0.5 * .pi) : 0 }
    }
    static func at(_ t: Double, _ w: Double, _ h: Double, _ top: Double) -> Frame {
        let restX = w/2, restY = h*0.74, row = top+168, by = top+354
        let bob = sin(2 * .pi * 2*t/loop)*4
        var f = Frame(x: restX, y: restY+bob, yaw: 0.12*sin(2 * .pi*t/loop), t: t)
        if t >= 1 && t < 3.3 {
            let p = [CGPoint(x: restX,y: restY), CGPoint(x: w*0.3,y: row+104), CGPoint(x: w*0.72,y: row+24), CGPoint(x: w*0.55,y: row-10), CGPoint(x:104,y:by)]
            let k = easeInOut((t-1)/2.3)*4
            let i = min(3, Int(floor(k))), u = k-Double(min(3,Int(floor(k))))
            let p0=p[max(0,i-1)], p1=p[i], p2=p[i+1], p3=p[min(4,i+2)]
            func cat(_ a: Double,_ b: Double,_ c: Double,_ d: Double) -> Double {
                0.5*(2*b+(-a+c)*u+(2*a-5*b+4*c-d)*u*u+(-a+3*b-3*c+d)*u*u*u)
            }
            f.x=cat(p0.x,p1.x,p2.x,p3.x); f.y=cat(p0.y,p1.y,p2.y,p3.y)
            f.tx=(p2.x-p1.x)*0.002
        } else if t >= 3.3 && t < 4.6 {
            let k=easeOut((t-3.3)/0.3)
            f.x=104; f.y=by; f.s=mix(1,0.3,k); f.a=1-k
        } else if t >= 4.6 && t < 5.4 {
            f.y=mix(h+140,restY+bob,easeOutBack((t-4.6)/0.8))
        } else if t >= 5.8 && t < 7.2 {
            f.yaw += 4 * .pi * easeOut((t-5.8)/1.4)
        }
        f.pay = t >= 3.4 && t < 7.45 ? base+gain*easeInOut(t-3.4) : base
        f.button = t >= 3.3 ? exp(-max(0,t-3.4)*1.6)*(t < 3.4 ? (t-3.3)/0.1 : 1) : 0
        return f
    }
}
