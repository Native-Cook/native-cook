import Foundation
import simd

enum PasskeyGlassSealPrint {
    static let count = 6144
    typealias Pt = SIMD2<Double>
    struct Ridge { var pts: [Pt]; var k: Int }
    static func rand(_ n: Double) -> Double { let s = sin(n*91.3458)*47453.5453; return s-floor(s) }
    static func ridges() -> [Ridge] {
        var out: [Ridge] = []
        for k in 0..<8 {
            let kd = Double(k), a = 5.5+6.9*kd, b = 7+7.5*kd, cy = -2.0
            let legL = 54-cy-(k > 5 ? (kd-5)*14 : 0)-3*sin(kd*1.7)
            var pts: [Pt] = []
            for i in 0...24 { let y = cy+legL*(1-Double(i)/24), d = y-cy; pts.append(Pt(-(a+0.05*d-0.0016*d*d)+0.0012*d*d,y)) }
            for i in 1..<60 { let th = Double.pi*(1-Double(i)/60); pts.append(Pt(a*cos(th)*(1+0.04*sin(th*2)),cy-b*sin(th))) }
            let legR = legL*(0.82+0.12*rand(kd+3))
            for i in 0...24 { let y = cy+legR*Double(i)/24, d = y-cy; pts.append(Pt(a+0.05*d-0.0016*d*d+0.0012*d*d,y)) }
            var pieces = [pts]
            let cuts = k < 2 ? 0 : k < 5 ? 1 : 2
            for c in 0..<cuts {
                var index = 0
                for j in pieces.indices where pieces[j].count > pieces[index].count { index = j }
                let longest = pieces[index]
                let at = Int(floor(Double(longest.count)*(0.25+0.5*rand(kd*7+Double(c)))))
                pieces.replaceSubrange(index...index,with:[Array(longest.prefix(at)),Array(longest.dropFirst(at+3))])
            }
            for piece in pieces where piece.count > 2 { out.append(Ridge(pts:piece,k:k)) }
        }
        out.insert(Ridge(pts:(0..<16).map { Pt(0,-2+Double($0)*1.6) },k:0),at:0)
        return out
    }
    static func keyLines() -> [[Pt]] {
        func ring(_ r: Double) -> [Pt] { (0...80).map { let t = Double($0)/80 * .pi*2; return Pt(-50+r*cos(t),r*sin(t)) } }
        let top = stride(from:-26.0,through:66.0,by:2).map { Pt($0,-6.5) }
        let teeth: [Pt] = [Pt(66,6.5),Pt(62,6.5),Pt(62,16),Pt(54,16),Pt(54,6.5),Pt(48,6.5),Pt(48,13),Pt(40,13),Pt(40,6.5),Pt(34,6.5),Pt(34,18),Pt(27,18),Pt(27,6.5),Pt(-26,6.5)]
        var under: [Pt] = []
        for i in 0..<teeth.count-1 {
            let a = teeth[i], b = teeth[i+1], n = max(2,Int((simd_length(b-a)/2).rounded()))
            for j in 0..<n { under.append(a+(b-a)*Double(j)/Double(n)) }
        }
        return [ring(24),ring(11),top,(0..<8).map { Pt(66,-6.5+13*Double($0)/7) },under]
    }
    static func sample(_ lines: [[Pt]]) -> [SIMD3<Float>] {
        struct Segment { var a: Pt; var b: Pt; var len: Double; var line: Int }
        var segments: [Segment] = []
        for (line,pts) in lines.enumerated() {
            for i in 0..<pts.count-1 { segments.append(Segment(a:pts[i],b:pts[i+1],len:simd_length(pts[i+1]-pts[i]),line:line)) }
        }
        let total = segments.reduce(0) { $0+$1.len }
        var index = 0, acc = 0.0
        return (0..<count).map { i in
            let s = (Double(i)+0.5)/Double(count)*total
            while index < segments.count-1 && acc+segments[index].len < s { acc += segments[index].len; index += 1 }
            let g = segments[index], f = g.len > 0 ? (s-acc)/g.len : 0
            let pt = g.a+(g.b-g.a)*f
            return SIMD3(Float(pt.x),Float(pt.y),Float(g.line))
        }
    }
    static let points: [SIMD4<Float>] = {
        let ridges = Self.ridges(), a = sample(ridges.map(\.pts)), b = sample(keyLines())
        var out: [SIMD4<Float>] = []; out.reserveCapacity(count*2)
        for i in 0..<count {
            out.append(SIMD4(a[i].x,a[i].y,b[i].x,b[i].y))
            out.append(SIMD4(Float(ridges[Int(a[i].z)].k)/7,Float(min(1,hypot(Double(a[i].x),Double(a[i].y)+2)/62)),Float(rand(Double(i)+11)),0))
        }
        return out
    }()
}
