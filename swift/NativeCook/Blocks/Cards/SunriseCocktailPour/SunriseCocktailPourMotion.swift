import Foundation
import CoreGraphics

enum SunriseCocktailPourMotion {
    static let loop = 10.0
    static let pourStart = 1.0 + 0.1
    static let stirStart = 4.6 + 0.1
    static let speed = Double.pi * 6 / loop
    struct Frame {
        var level = 0.55, red = 0.0, rise = 0.0, mix = 0.0
        var stream = 0.0, swirl = 0.0, shift = 0.0, phase = 0.0
        var step = 0, finger = 0.0, fingerPress = 0.0
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(x)
        func cubic(_ t: Double, _ a: Double, _ b: Double) -> Double {
            3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        }
        var lo=0.0, hi=1.0
        for _ in 0..<24 {
            let mid=(lo+hi)/2
            if cubic(mid,x1,x2)<x { lo=mid } else { hi=mid }
        }
        return cubic((lo+hi)/2,y1,y2)
    }
    static func cssOut(_ x: Double) -> Double { bezier(x,0.23,1,0.32,1) }
    static func defaultEase(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2,2)/2 }
    static func pour(_ tau: Double, _ f: inout Frame) {
        f.stream = tau > 0 && tau < 1.2 ? tau/1.2 : 0
        f.red = easeOut(clamp((tau-0.4)/1.1))
        f.rise = easeInOut(clamp((tau-0.7)/2.2))
        f.level = 0.55+(0.62-0.55)*easeOut(clamp((tau-0.3)/0.9))
    }
    static func stir(_ tau: Double, _ f: inout Frame) {
        f.mix = easeInOut(clamp(tau/1.3))
        f.swirl = 2*Double.pi*1.2*easeOut(clamp(tau/1.6))
    }
    static func at(_ t: Double) -> Frame {
        var f=Frame()
        if t > pourStart && t < 7.6 { pour(t-1.0-0.1,&f); f.step=1 }
        if t > stirStart && t < 7.6 { stir(t-4.6-0.1,&f); f.step=2 }
        if t >= 6.9 && t < 7.6 { f.shift = -3.4*easeInOut(seg(t,6.9,7.6)) }
        else if t >= 7.6 && t < 8.5 { f.shift=3.4*(1-easeOut(seg(t,7.6,8.5))); f.step=0 }
        if t >= 7.6 { f.step=0 }
        for a in [1.0,4.6,6.8] {
            let finger=easeOut(seg(t,a-0.15,a-0.02))*(1-easeOut(seg(t,a+0.14,a+0.3)))
            if finger > f.finger {
                f.finger=finger
                f.fingerPress=easeOut(seg(t,a-0.02,a+0.08))*(1-easeOut(seg(t,a+0.12,a+0.26)))
            }
        }
        f.phase=(speed*t).truncatingRemainder(dividingBy:Double.pi*2)
        return f
    }
    static func note(_ i: Int, _ t: Double) -> (Double,Double) {
        var before=0, after=0, progress=1.0
        for (a,b,c) in [(pourStart,0,1),(stirStart,1,2),(7.6,2,0)] where t>a {
            before=b; after=c; progress=cssOut(seg(t,a,a+0.26))
        }
        let start = before == i ? 1.0 : 0
        let end = after == i ? 1.0 : 0
        let opacity = start+(end-start)*progress
        return (opacity,6*(1-opacity))
    }
}

struct SunriseCocktailPourLayout {
    let w,h,barTop,heroX,heroY,heroS,eyebrowTop,nameTop,recipeTop,noteTop,ctaTop: Double
    init(_ size: CGSize, _ top: Double, _ bottom: Double) {
        w=size.width; h=size.height; barTop=top
        ctaTop=h-max(bottom,16)-8-56
        noteTop=ctaTop-16-20; recipeTop=noteTop-24-20
        nameTop=recipeTop-4-44; eyebrowTop=nameTop-4-16
        let room0=barTop+44+24
        let room=eyebrowTop-24-room0
        heroS=min(w*0.3,room/2.75,118)
        heroX=w/2; heroY=room0+room-heroS*1.32
    }
}
