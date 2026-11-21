import SwiftUI

/// The reviewed 24-unit vector glyphs. Circular SVG arcs are represented by their centre and sweep.
struct WaveFieldPadGlyph: Shape {
    let index: Int
    func path(in rect: CGRect) -> Path {
        var p = Path()
        func move(_ x: CGFloat, _ y: CGFloat) { p.move(to: CGPoint(x: x, y: y)) }
        func line(_ x: CGFloat, _ y: CGFloat) { p.addLine(to: CGPoint(x: x, y: y)) }
        func arc(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat, _ start: Double, _ end: Double) {
            p.addArc(center: CGPoint(x: x, y: y), radius: radius, startAngle: .degrees(start), endAngle: .degrees(end), clockwise: false)
        }
        switch index {
        case 3:
            move(7,7); line(17,17); move(17,7); line(7,17)
        case 0:
            move(3.5,8.5); line(3.5,6.5); arc(6.5,6.5,3,180,270); line(8.5,3.5)
            move(15.5,3.5); line(17.5,3.5); arc(17.5,6.5,3,270,360); line(20.5,8.5)
            move(20.5,15.5); line(20.5,17.5); arc(17.5,17.5,3,0,90); line(15.5,20.5)
            move(8.5,20.5); line(6.5,20.5); arc(6.5,17.5,3,90,180); line(3.5,15.5)
            p.addEllipse(in: CGRect(x:9.25,y:9.25,width:5.5,height:5.5))
        case 1:
            move(4,9.75); line(7.25,9.75); line(12,5.5); line(12,18.5); line(7.25,14.25); line(4,14.25); p.closeSubpath()
            // SVG radius/endpoints determine the two circular speaker waves.
            move(15.5,9.2); arc(12.504,12,4.1,-43.08,43.08)
            move(18.4,6.6); arc(12.497,12,8,-42.45,42.45)
        default:
            move(14.6,3.6)
            p.addArc(center: CGPoint(x:11.984,y:12.002), radius:8.8, startAngle:.degrees(-72.71), endAngle:.degrees(-3.92), clockwise:true)
            move(12,7.8); line(12,12.3); line(15,14.2)
            move(19.3,2.6)
            p.addQuadCurve(to: CGPoint(x:21.5,y:4.8), control: CGPoint(x:19.6,y:4.5))
            p.addQuadCurve(to: CGPoint(x:19.3,y:7), control: CGPoint(x:19.6,y:5.1))
            p.addQuadCurve(to: CGPoint(x:17.1,y:4.8), control: CGPoint(x:19,y:5.1))
            p.addQuadCurve(to: CGPoint(x:19.3,y:2.6), control: CGPoint(x:19,y:4.5))
            p.closeSubpath()
        }
        return p.applying(CGAffineTransform(scaleX: rect.width/24, y: rect.height/24).concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY)))
    }
}
