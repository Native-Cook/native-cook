import SwiftUI

// Only the contours, rope and notes vary. The microphone material is cached at 3x.
enum MicCordOnboardingArt {
    private static let noteHead = Path(ellipseIn:CGRect(x:-7,y:-5,width:14,height:10))
    private static let noteStem: Path = { var p = Path(); p.move(to:CGPoint(x:6,y:-2)); p.addLine(to:CGPoint(x:6,y:-26)); return p }()
    private static let secondStem: Path = { var p = Path(); p.move(to:CGPoint(x:22,y:-6)); p.addLine(to:CGPoint(x:22,y:-30)); return p }()
    private static let noteBeam: Path = { var p = Path(); p.move(to:CGPoint(x:6,y:-25)); p.addLine(to:CGPoint(x:22,y:-29)); return p }()
    private static let noteFlag: Path = {
        var p = Path(); p.move(to:CGPoint(x:6,y:-26)); p.addCurve(to:CGPoint(x:12,y:-10),control1:CGPoint(x:14,y:-22),control2:CGPoint(x:16,y:-16)); return p
    }()
    static func scene(_ ctx: GraphicsContext, _ size: CGSize, _ top: Double, _ time: Double, _ rope: MicCordOnboardingRope) {
        let phase = 2 * Double.pi * time / 8
        for side in 0..<2 {
            for i in 0..<16 {
                var path = Path()
                let base = side == 0 ? top+40+Double(i)*7 : size.height-210+Double(i)*7
                for k in 0...40 {
                    let x = side == 0 ? -20+Double(k)*7 : size.width-260+Double(k)*7
                    let y = base+26*sin(x*0.018+Double(i)*0.16+phase+Double(side)*2) + (side == 0 ? -Double(k)*1.4 : Double(k)*0.6)
                    if k == 0 { path.move(to:CGPoint(x:x,y:y)) } else { path.addLine(to:CGPoint(x:x,y:y)) }
                }
                ctx.stroke(path, with:.color(MicCordOnboardingResources.color(i%2 == 0 ? 0xff4fd8 : 0x8a5cff).opacity(0.22+0.25*(1-Double(i)/16))), lineWidth:1)
            }
        }
        let coil = cable(rope)
        ctx.stroke(coil, with:.color(MicCordOnboardingResources.color(0x7d1172)), style:StrokeStyle(lineWidth:4.5,lineJoin:.round))
        ctx.stroke(coil, with:.color(MicCordOnboardingResources.color(0xff5ad8)), style:StrokeStyle(lineWidth:2.6,lineJoin:.round))
        let angle = atan2(rope.y[17]-rope.y[15],rope.x[17]-rope.x[15])
        var mic = ctx
        mic.translateBy(x:rope.x[17],y:rope.y[17]); mic.rotate(by:.radians(angle - .pi/2)); mic.scaleBy(x:1.4,y:1.4)
        mic.draw(MicCordOnboardingResources.shared.mic, in:CGRect(x:-45,y:-10,width:90,height:198))
        let cols: [UInt32] = [0xff7be6,0xb9a2ff,0x8ff0d0]
        for i in 0..<14 {
            let o = i*6, age = rope.notes[o+4]
            if age > 1.6 { continue }
            let a = min(1,age/0.15)*(1-MicCordOnboardingRope.smooth((age-0.9)/0.7))
            var c = ctx; c.opacity = a
            c.translateBy(x:rope.notes[o],y:rope.notes[o+1]); c.rotate(by:.degrees(sin(age*4+Double(i))*18))
            note(c,Int(rope.notes[o+5]),MicCordOnboardingResources.color(cols[i%3]))
        }
    }
    private static func note(_ c: GraphicsContext, _ kind: Int, _ color: Color) {
        var head = c; head.rotate(by:.degrees(-20))
        head.fill(noteHead, with:.color(color))
        let stem = StrokeStyle(lineWidth:2.4,lineCap:.round)
        c.stroke(noteStem, with:.color(color), style:stem)
        if kind == 1 {
            var second = c; second.translateBy(x:16,y:-4); second.rotate(by:.degrees(-20))
            second.fill(noteHead,with:.color(color))
            c.stroke(secondStem,with:.color(color),style:stem)
            c.stroke(noteBeam,with:.color(color),lineWidth:4.5)
        } else if kind == 0 {
            c.stroke(noteFlag,with:.color(color),style:stem)
        }
    }
    // Measure the RN midpoint-quadratic rope, then offset its arc-length tangent by the coil.
    private static func cable(_ r: MicCordOnboardingRope) -> Path {
        var points = [CGPoint(x:r.x[0],y:r.y[0])]
        var start = points[0]
        for i in 1..<17 {
            let control = CGPoint(x:r.x[i],y:r.y[i])
            let end = CGPoint(x:(r.x[i]+r.x[i+1])/2,y:(r.y[i]+r.y[i+1])/2)
            for j in 1...32 {
                let t = Double(j)/32, u = 1-t
                points.append(CGPoint(x:u*u*start.x+2*u*t*control.x+t*t*end.x,y:u*u*start.y+2*u*t*control.y+t*t*end.y))
            }
            start = end
        }
        points.append(CGPoint(x:r.x[17],y:r.y[17]))
        var distances = [0.0]
        for i in 1..<points.count { distances.append(distances[i-1]+hypot(points[i].x-points[i-1].x,points[i].y-points[i-1].y)) }
        let length = distances.last!
        var path = Path(), index = 1
        for s in stride(from:0.0, through:length, by:1.6) {
            while index < points.count-1 && distances[index] < s { index += 1 }
            let a = points[index-1], b = points[index], d = max(0.000001,distances[index]-distances[index-1])
            let t = (s-distances[index-1])/d
            let x = a.x+(b.x-a.x)*t, y = a.y+(b.y-a.y)*t
            let o = 5.5*sin(s/8.5*2*Double.pi)*min(1,(length-s)/20,s/20+0.2)
            let p = CGPoint(x:x-(b.y-a.y)/d*o,y:y+(b.x-a.x)/d*o)
            if s == 0 { path.move(to:p) } else { path.addLine(to:p) }
        }
        return path
    }
}
