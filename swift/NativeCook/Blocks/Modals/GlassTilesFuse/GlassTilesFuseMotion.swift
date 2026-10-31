import Foundation

// Direct transcription of gtf-motion.ts. All geometry is in points.
enum GlassTilesFuseMotion {
    static let loop = 9.0
    static let scale = 1.35
    static let gap = 86.0, fusedW = 70.0, fusedG = 30.0
    static let snapDist = 185.0, tension = 0.15
    static let pullX = (snapDist - fusedW - fusedG) / (1 - tension) + 4
    enum T {
        static let press = 1.0, connect0 = 1.2, fused = 2.8, grab = 3.1
        static let snap = 4.2, settled = 5.0, done = 6.5, back0 = 6.75, back1 = 8.2
    }
    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t-a)/(b-a)) }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func easeOut(_ x: Double) -> Double { 1-pow(1-x,3) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a+(b-a)*t }
    static func springTo(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2*Double.pi/response, wd = w*sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    struct Layout {
        var w: Double, h: Double, titleTop: Double, rowsTop: Double, rowH = 63.0
        var sheetTop: Double, tilesY: Double, stageH: Double
        var white = (107*scale).rounded(), glass = (102*scale).rounded()
        var textTop: Double, buttonTop: Double, side = 32.0
        init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) {
            self.w=w; self.h=h; titleTop=top+24; rowsTop=top+126
            buttonTop=h-max(bottom,16)-12-52
            textTop=buttonTop-24-76
            tilesY=textTop-40-0.5*145
            sheetTop=max(top+200,tilesY-0.5*145-56)
            stageH=textTop-12-sheetTop
        }
    }
    struct Poses {
        var wx: Double, wy: Double, wa: Double, gx: Double, gy: Double, ga: Double
        var size = 1.0, fuse = 0.0, seam = -1.0, check = 0.0
    }
    static func rest(_ l: Layout, _ t: Double) -> Poses {
        let bob=sin(t/loop*Double.pi*4), sway=sin(t/loop*Double.pi*2)
        return Poses(wx:l.w/2-22*scale,wy:l.tilesY+12*scale,wa:0.14,
                     gx:l.w/2+36*scale+sway*4,gy:l.tilesY-16*scale+bob*5,ga:-0.12+sway*0.03)
    }
    static func linked(_ l: Layout, _ r: Poses, _ k: Double) -> Poses {
        let a=easeInOut(seg(k,0,0.3)), fuseIn=easeInOut(seg(k,0.18,0.42))
        let part=easeInOut(seg(k,0.56,0.9)), fuseOut=easeInOut(seg(k,0.56,0.84))
        let cx=l.w/2, cy=l.tilesY
        let wobble=sin(seg(k,0.82,1)*Double.pi*3)*(1-seg(k,0.82,1))*6
        return Poses(wx:mix(mix(r.wx,cx-fusedW,a),cx-gap-wobble,part),
                     wy:mix(mix(r.wy,cy,a),cy,part),wa:mix(r.wa,0,a),
                     gx:mix(mix(r.gx,cx+fusedG,a),cx+gap+wobble,part),
                     gy:mix(mix(r.gy,cy,a),cy,part),ga:mix(r.ga,0,a),
                     size:1-0.1*part,fuse:fuseIn*(1-fuseOut),seam:-1+2*seg(k,0.36,0.58),check:easeOut(seg(k,0.78,1)))
    }
    static func linkAt(_ t: Double) -> Double {
        if t<T.connect0 { return 0 }
        if t<T.snap { return 0.5*easeInOut(seg(t,T.connect0,T.fused)) }
        if t<T.back0 { return 0.5+0.5*easeOut(seg(t,T.snap,T.settled)) }
        return 1-easeInOut(seg(t,T.back0,T.back1))
    }
    static func pullAt(_ t: Double) -> Double {
        if t<T.grab { return 0 }
        if t<T.snap { return pullX*easeInOut(seg(t,T.grab,T.snap)) }
        return pullX*(1-easeOut(seg(t,T.snap,T.settled)))
    }
    static func stickAt(_ t: Double) -> Double {
        if t<T.snap { return 1 }
        if t<T.back0 { return 1-easeOut(seg(t,T.snap,T.snap+0.14)) }
        return 1
    }
    static func recoilAt(_ t: Double) -> Double {
        if t<T.grab { return 0 }
        if t<T.snap { return tension*pullAt(t) }
        let x=(t-T.snap)/0.6
        return x>=1 ? 0 : tension*pullX*exp(-4*x)*cos(9*x)*(1-x)
    }
    static func pressAt(_ t: Double, _ at: Double) -> Double {
        easeOut(seg(t,at,at+0.1))*(1-easeOut(seg(t,at+0.16,at+0.36)))
    }
    static func fingerAt(_ t: Double) -> (finger: Double, at: Int) {
        func near(_ a: Double, _ b: Double) -> Double { easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.25))) }
        let onButton=near(T.press,T.press+0.25)+near(T.done,T.done+0.25), onGlass=near(T.grab,T.snap)
        return (clamp01(onButton+onGlass),onGlass>0.001 ? 1 : 0)
    }
}
