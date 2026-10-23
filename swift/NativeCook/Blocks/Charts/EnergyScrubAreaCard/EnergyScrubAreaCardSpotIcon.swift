import SwiftUI

func EnergyScrubAreaCardDrawSpotIcon(_ context: GraphicsContext, _ origin: CGPoint) {
    let size = 22.0, colors = [Color(hex: 0x7c3aed), Color(hex: 0xe0457b), Color(hex: 0xf97316)]
    var c = context; c.translateBy(x: origin.x, y: origin.y)
    let shading = GraphicsContext.Shading.linearGradient(Gradient(colors: colors), startPoint: CGPoint(x: 2, y: 3), endPoint: CGPoint(x: 20, y: 21))
    c.stroke(Path(ellipseIn: CGRect(x: 3, y: 5.5, width: 13, height: 13)), with: shading, lineWidth: 2.2)
    var handle = Path(); handle.move(to: CGPoint(x: 14.3, y: 16.8)); handle.addLine(to: CGPoint(x: 19, y: size - 0.5))
    c.stroke(handle, with: shading, style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
    var sparkle = Path(); sparkle.move(to: CGPoint(x: 16.5, y: 1.5))
    sparkle.addQuadCurve(to: CGPoint(x: 20.5, y: 5.5), control: CGPoint(x: 17, y: 5))
    sparkle.addQuadCurve(to: CGPoint(x: 16.5, y: 9.5), control: CGPoint(x: 17, y: 6))
    sparkle.addQuadCurve(to: CGPoint(x: 12.5, y: 5.5), control: CGPoint(x: 16, y: 6))
    sparkle.addQuadCurve(to: CGPoint(x: 16.5, y: 1.5), control: CGPoint(x: 16, y: 5)); sparkle.closeSubpath()
    c.fill(sparkle, with: shading)
}
