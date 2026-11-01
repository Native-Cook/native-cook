import SwiftUI

struct BundlePackBoxArt {
    struct Mark { let path: Path; let hex: Int; var alpha = 1.0; var stroke = 0.0 }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
    }
    static func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path { Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)) }
    static func line(_ points: [CGPoint]) -> Path {
        Path { p in
            guard let first = points.first else { return }
            p.move(to: first)
            for point in points.dropFirst() { p.addLine(to: point) }
        }
    }
    static let items: [[Mark]] = {
        let handle = Path { p in
            // Skia's oval arc starts at 180 degrees and sweeps clockwise through its upper half.
            p.move(to: CGPoint(x: -0.32, y: -0.32))
            p.addCurve(to: CGPoint(x: 0, y: -0.62), control1: CGPoint(x: -0.32, y: -0.485685425), control2: CGPoint(x: -0.17673112, y: -0.62))
            p.addCurve(to: CGPoint(x: 0.32, y: -0.32), control1: CGPoint(x: 0.17673112, y: -0.62), control2: CGPoint(x: 0.32, y: -0.485685425))
        }
        return [
            [Mark(path: oval(-0.5,-0.32,1,1),hex:0x2F3A4A), Mark(path:oval(-0.31,-0.14,0.32,0.32),hex:0x5A6B82,alpha:0.6), Mark(path:handle,hex:0x2F3A4A,stroke:0.14),Mark(path:rr(-0.22,0.05,0.44,0.22,0.06),hex:0xFF8A3D)],
            [Mark(path:rr(-0.5,-0.06,1,0.12,0.05),hex:0xA9B4C2)] + [-1.0,1.0].flatMap { k in [Mark(path:rr(k*0.42-0.12,-0.3,0.24,0.6,0.08),hex:0x3D7BFF),Mark(path:rr(k*0.42-0.12,-0.3,0.08,0.6,0.04),hex:0x7FA9FF,alpha:0.6)] },
            [Mark(path:rr(-0.7,-0.2,1.4,0.4,0.2),hex:0x7CC97E),Mark(path:oval(0.55,-0.2,0.26,0.4),hex:0x5CAE60),Mark(path:oval(0.62,-0.1,0.12,0.2),hex:0x3F8C44),Mark(path:line([CGPoint(x:-0.5,y:-0.2),CGPoint(x:-0.5,y:0.2)]),hex:0xFFFFFF,alpha:0.35,stroke: -2)],
            [Mark(path:rr(-0.2,-0.5,0.4,1,0.14),hex:0xFF6B8B),Mark(path:rr(-0.12,-0.66,0.24,0.2,0.06),hex:0x2F3A4A),Mark(path:rr(-0.2,-0.05,0.4,0.2,0),hex:0xFFFFFF,alpha:0.85)],
            [Mark(path:oval(-0.5,-0.3,1,0.6),hex:0xFFC23D,stroke:0.12),Mark(path:oval(-0.32,-0.18,0.64,0.36),hex:0xFFD978,stroke:0.08)]
        ]
    }()
    struct Fixed {
        let shadow: Path
        let back: [(Path,Int)]
        let front: [(Path,Int)]
        let seam: Path
        let logo: Path
    }
    private static var geometry: [String: Fixed] = [:]
    private static func fixed(_ size: CGSize) -> Fixed {
        let key = "\(size.width):\(size.height)"
        if let cached = geometry[key] { return cached }
        let cx = size.width/2, k = size.width*0.19, by = (size.height*0.5).rounded()
        let w = 0.9, h = 0.62, d = 0.7
        func p(_ x: Double,_ y: Double,_ z: Double) -> CGPoint {
            let a = x*cos(-0.6)+z*sin(-0.6), c = -x*sin(-0.6)+z*cos(-0.6)
            return CGPoint(x:cx+a*k,y:by-(y*cos(0.42)-c*sin(0.42))*k)
        }
        func polygon(_ pts: [CGPoint]) -> Path { var path = line(pts); path.closeSubpath(); return path }
        let lg = p(0,h*0.75,d)
        let value = Fixed(
            shadow:oval(cx-k*1.35,by-k*0.22,k*2.7,k*0.66),
            back:[(polygon([p(-w,0,-d),p(w,0,-d),p(w,h*2,-d),p(-w,h*2,-d)]),0xB9834F),(polygon([p(-w,0,-d),p(-w,0,d),p(-w,h*2,d),p(-w,h*2,-d)]),0xA8733F)],
            front:[(polygon([p(-w,0,d),p(w,0,d),p(w,h*2,d),p(-w,h*2,d)]),0xD9A066),(polygon([p(w,0,d),p(w,0,-d),p(w,h*2,-d),p(w,h*2,d)]),0xC48A50)],
            seam:line([p(-w*0.6,h*1.1,d),p(w*0.6,h*1.1,d)]),
            logo:oval(lg.x-k*0.12,lg.y-k*0.12,k*0.24,k*0.24))
        geometry[key] = value
        return value
    }
    let size: CGSize
    func draw(_ c: GraphicsContext, _ o: BundlePackBoxMotion.Frame, _ reduced: Bool) {
        let W = size.width, H = size.height, cx = W / 2, cy = (H * 0.29).rounded(), k = W * 0.19, u = W * 0.17, spread = W * 0.38, by = (H * 0.5).rounded()
        let w = 0.9, h = 0.62, d = 0.7
        func P(_ x: Double,_ y: Double,_ z: Double) -> CGPoint {
            let a = x * cos(-0.6) + z * sin(-0.6), cc = -x * sin(-0.6) + z * cos(-0.6)
            return CGPoint(x: cx + a * k, y: by - (y * cos(0.42) - cc * sin(0.42)) * k)
        }
        func quad(_ pts: [CGPoint],_ hex: Int,_ alpha: Double = 1) {
            var path = Self.line(pts); path.closeSubpath()
            c.fill(path,with:.color(Self.color(hex,alpha)))
        }
        let fixed = Self.fixed(size)
        c.fill(fixed.shadow,with:.color(Self.color(0x7A4B2A,0.13)))
        for (path,hex) in fixed.back { c.fill(path,with:.color(Self.color(hex))) }
        let positions = [(-0.62,-0.52,-0.3),(0.6,-0.6,0.5),(-0.7,0.02,0.2),(0.68,-0.05,-0.25),(0.02,-0.62,0.1)]
        for pass in 0..<2 {
            for i in 0..<5 {
                let it = positions[i], ki = reduced ? (o.p > 0.5 ? 1.0 : 0.0) : BundlePackBoxMotion.smooth(o.p * 1.45 - Double(i) * 0.11)
                if (pass == 0) != (ki > 0.78) { continue }
                let bob = reduced ? 0 : sin(o.t * 2 * .pi * Double(1+i%2) / 9 + Double(i)) * 6 * (1-ki)
                let ex = cx+it.0*spread, ey = cy+it.1*spread+bob
                let top = P(Double(i-2)*0.22,h*2+0.3,i%2 == 1 ? 0.15 : -0.15)
                let deep = P(Double(i-2)*0.22,h*0.9,i%2 == 1 ? 0.15 : -0.15)
                let f = ki < 0.78 ? ki/0.78 : (ki-0.78)/0.22
                let x = BundlePackBoxMotion.mix(ki < 0.78 ? ex : top.x,ki < 0.78 ? top.x : deep.x,f)
                let y = BundlePackBoxMotion.mix(ki < 0.78 ? ey : top.y,ki < 0.78 ? top.y : deep.y,f) - (ki < 0.78 ? sin(.pi*f)*u*0.9 : 0)
                let rot = it.2*(1-ki)+(o.burst > 0 ? sin(o.burst*9+Double(i))*0.4*(1-o.burst) : 0)
                var cc = c
                cc.translateBy(x:x,y:y); cc.rotate(by:.degrees(rot*57.3))
                let sc = BundlePackBoxMotion.mix(1,0.62,ki)
                cc.scaleBy(x:sc*u,y:sc*u)
                for mark in Self.items[i] {
                    let shade = GraphicsContext.Shading.color(Self.color(mark.hex,mark.alpha))
                    if mark.stroke != 0 { cc.stroke(mark.path,with:shade,style:StrokeStyle(lineWidth:mark.stroke < 0 ? -mark.stroke/u : mark.stroke,lineCap:.round)) }
                    else { cc.fill(mark.path,with:shade) }
                }
            }
            if pass == 0 {
                for (path,hex) in fixed.front { c.fill(path,with:.color(Self.color(hex))) }
                c.stroke(fixed.seam,with:.color(Self.color(0x8A5A30,0.35)),style:StrokeStyle(lineWidth:2,lineCap:.round))
                c.fill(fixed.logo,with:.color(Self.color(0x8A5A30,0.28)))
            }
        }
        let fl = reduced ? (o.p > 0.95 ? 1.0 : 0.0) : BundlePackBoxMotion.smooth((o.p-0.72)/0.22), ang = (1-fl)*2.2
        for f in 0..<2 {
            let z = f == 0 ? d : -d, dir = f == 0 ? 1.0 : -1.0
            let hy = h*2+d*sin(ang), z2 = z-dir*d*cos(ang)
            quad([P(-w,h*2,z),P(w,h*2,z),P(w,hy,z2),P(-w,hy,z2)],f == 0 ? 0xE3AE73 : 0xC9925A)
        }
        for f in 0..<2 {
            let sx = f == 0 ? -w : w, dir = f == 0 ? -1.0 : 1.0
            let hy = h*2+w*0.55*sin(ang), x2 = sx-dir*w*0.55*cos(ang)
            quad([P(sx,h*2,-d),P(sx,h*2,d),P(x2,hy,d),P(x2,hy,-d)],f == 0 ? 0xD29B60 : 0xDDA86C)
        }
        if o.tape > 0 {
            quad([P(-0.12,h*2+0.002,d+0.02),P(-0.12,h*2+0.002,d-2*d*o.tape),P(0.12,h*2+0.002,d-2*d*o.tape),P(0.12,h*2+0.002,d+0.02)],0xF4E2B8,0.92)
            quad([P(-0.12,h*2,d),P(0.12,h*2,d),P(0.12,h*2-0.3*o.tape,d),P(-0.12,h*2-0.3*o.tape,d)],0xF4E2B8,0.92)
        }
    }
}
