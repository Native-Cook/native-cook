import SwiftUI

/// Exact paths from ipc-icons.ts, including the two SVG circular arcs.
struct IslandPrintCameraIcon: Shape {
    enum Kind { case empty, flip, flash }
    var kind: Kind
    func path(in rect: CGRect) -> Path {
        var p = Path()
        func line(_ points: [(Double, Double)], close: Bool = false) {
            guard let first = points.first else { return }
            p.move(to: CGPoint(x: first.0, y: first.1))
            for pt in points.dropFirst() { p.addLine(to: CGPoint(x: pt.0, y: pt.1)) }
            if close { p.closeSubpath() }
        }
        var width = 16.0, height = 16.0
        switch kind {
        case .flash:
            line([(9.15,1.8),(4.4,8.25),(7.75,8.25),(6.85,14.2),(11.6,7.75),(8.25,7.75)], close: true)
            line([(2.1,2.1),(13.9,13.9)])
        case .flip:
            width = 22; height = 22
            p.move(to: CGPoint(x: 18, y: 8.2))
            arc(&p, from: CGPoint(x: 18, y: 8.2), to: CGPoint(x: 5.15, y: 5.1), radius: 7.3)
            p.addLine(to: CGPoint(x: 3.7, y: 6.8))
            line([(3.7,3.4),(3.7,7.2),(7.5,7.2)])
            p.move(to: CGPoint(x: 4, y: 13.8))
            arc(&p, from: CGPoint(x: 4, y: 13.8), to: CGPoint(x: 16.85, y: 16.9), radius: 7.3)
            p.addLine(to: CGPoint(x: 18.3, y: 15.2))
            line([(18.3,18.6),(18.3,14.8),(14.5,14.8)])
        case .empty:
            width = 48; height = 62
            line([(5,49),(5,8),(36,8),(36,49)])
            line([(8,53),(8,11),(39,11),(39,53)])
            p.addRoundedRect(in: CGRect(x: 11, y: 15, width: 31, height: 43), cornerSize: CGSize(width: 1, height: 1))
            line([(15,19),(38,19),(38,44),(15,44)], close: true)
            line([(26.5,27.5),(28.05,30.8),(31.5,31.25),(29,33.65),(29.65,37),(26.5,35.25),(23.35,37),(24,33.65),(21.5,31.25),(24.95,30.8)], close: true)
            line([(33.5,23.2),(34.05,24.4),(35.3,24.55),(34.4,25.4),(34.65,26.65),(33.5,26),(32.35,26.65),(32.6,25.4),(31.7,24.55),(32.95,24.4)], close: true)
        }
        return p.applying(CGAffineTransform(scaleX: rect.width/width, y: rect.height/height))
    }
    private func arc(_ path: inout Path, from: CGPoint, to: CGPoint, radius r: Double) {
        let dx = (from.x-to.x)/2, dy = (from.y-to.y)/2, d2 = dx*dx+dy*dy
        let k = sqrt(max(0, (r*r-d2)/d2))
        let cx = (from.x+to.x)/2-k*dy, cy = (from.y+to.y)/2+k*dx
        let start = atan2(from.y-cy, from.x-cx)
        var delta = atan2(to.y-cy, to.x-cx)-start
        if delta > 0 { delta -= 2 * .pi }
        let count = Int(ceil(abs(delta)/(.pi/2)))
        for i in 0..<count {
            let a = start+delta*Double(i)/Double(count), b = start+delta*Double(i+1)/Double(count)
            let q = 4/3*tan((b-a)/4)
            path.addCurve(to: CGPoint(x: cx+r*cos(b), y: cy+r*sin(b)),
                          control1: CGPoint(x: cx+r*(cos(a)-q*sin(a)), y: cy+r*(sin(a)+q*cos(a))),
                          control2: CGPoint(x: cx+r*(cos(b)+q*sin(b)), y: cy+r*(sin(b)-q*cos(b))))
        }
    }
}
