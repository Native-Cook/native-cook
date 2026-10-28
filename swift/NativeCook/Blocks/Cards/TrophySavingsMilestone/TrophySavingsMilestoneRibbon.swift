import SwiftUI
import UIKit

@MainActor
struct TrophySavingsMilestoneRibbon: View {
    typealias Geometry = TrophySavingsMilestoneStreamerGeometry
    let frame: Geometry.Frame
    let foreground: Bool
    private static let front: [UInt32] = [0xFBA4BA,0xF57D9E,0xF57D9E,0xD9507C]
    private static let reverse: [UInt32] = [0xD25D82,0xA92C56,0xA92C56,0x8E2048]
    private static let edge: [UInt32] = [0xD25078,0xC34C70,0xC34C70,0xB13E64]
    private static let seam: [UInt32] = [0xD46C88,0xD56D89,0xED7A99,0xEE7B9A]
    private static let roll: UIImage = {
        guard let url = Bundle.main.url(forResource:"TrophySavingsMilestone-open-roll-right",withExtension:"png"), let image = UIImage(contentsOfFile:url.path) else { preconditionFailure("Missing approved trophy roll PNG") }
        return image.preparingForDisplay() ?? image
    }()
    var body: some View {
        Canvas { context,_ in
            if !foreground && frame.coilOpacity > 0 { drawCoil(context:&context) }
            let outline = silhouette()
            // The contact shadow belongs beneath the complete paper, never between adjacent spans.
            if foreground && frame.meshOpacity > 0 {
                var shadow = context
                shadow.clip(to:TrophySavingsMilestonePaths.bowl)
                shadow.translateBy(x:0,y:1.2)
                shadow.fill(outline,with:.color(Color(red:92/255,green:41/255,blue:31/255).opacity(0.13*frame.meshOpacity)))
            }
            var paper = context
            paper.opacity *= frame.meshOpacity
            // Only the actual outside rail gets coverage AA; shared interior edges are opaque.
            paper.clip(to:outline,style:FillStyle(antialiased:true))
            paper.drawLayer { surface in
                for span in frame.spans where span.foreground == foreground {
                    let left = (span.a.left+span.b.left)*0.5, right = (span.a.right+span.b.right)*0.5
                    let a = palette(span.a), b = palette(span.b)
                    let edgeWidth = min(0.08,0.65/max(0.8,(span.a.width+span.b.width)*0.5))
                    let locations = [0.0,edgeWidth,1-edgeWidth,1.0]
                    let stops = (0..<4).map { i in Gradient.Stop(color:color(a[i],b[i],0.5),location:locations[i]) }
                    surface.fill(quad(span),with:.linearGradient(Gradient(stops:stops),startPoint:left.point,endPoint:right.point),style:FillStyle(antialiased:false))
                }
            }
            let roll = frame.roll, alpha = roll.opacity*frame.meshOpacity*TrophySavingsMilestoneMotion.smooth((roll.width-3)/6)
            if roll.foreground == foreground && alpha > 0 {
                var tip = context; tip.opacity *= alpha; tip.translateBy(x:roll.center.x,y:roll.center.y)
                tip.rotate(by:.radians(atan2(roll.tangent.y,roll.tangent.x))); tip.scaleBy(x:frame.tipLength/36,y:roll.width/34)
                tip.draw(Image(uiImage:Self.roll),in:CGRect(x:-16,y:-25,width:64,height:64))
            }
        }.frame(width:393,height:350).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func palette(_ station: Geometry.Station)->[SIMD3<Double>] {
        let source = abs(station.facing) < 0.09 ? Self.edge : station.facing >= 0 ? Self.front : Self.reverse
        return (0..<4).map { i in let a = rgb(source[i]), b = rgb(Self.seam[i]); return a+(b-a)*station.joinBlend }
    }
    private func rgb(_ value: UInt32)->SIMD3<Double> { SIMD3(Double((value>>16)&255),Double((value>>8)&255),Double(value&255)) }
    private func color(_ a: SIMD3<Double>,_ b: SIMD3<Double>,_ q: Double)->Color { let v = a+(b-a)*q; return Color(red:v.x/255,green:v.y/255,blue:v.z/255) }
    private func silhouette()->Path {
        var outline = Path()
        var run: [Geometry.Span] = []
        func closeRun() {
            guard let first = run.first, let last = run.last else { return }
            outline.move(to:first.a.left.point)
            for span in run { outline.addLine(to:span.b.left.point) }
            outline.addLine(to:last.b.right.point)
            for span in run.reversed() { outline.addLine(to:span.a.right.point) }
            outline.closeSubpath()
            run.removeAll(keepingCapacity:true)
        }
        // Separate runs preserve rear/front occlusion; they never bridge hidden guide sections.
        for span in frame.spans {
            if span.foreground == foreground { run.append(span) } else { closeRun() }
        }
        closeRun()
        return outline
    }
    private func quad(_ span: Geometry.Span)->Path {
        var p = Path()
        p.move(to:span.a.left.point); p.addLine(to:span.b.left.point)
        p.addLine(to:span.b.right.point); p.addLine(to:span.a.right.point)
        p.closeSubpath()
        return p
    }
    private func drawCoil(context: inout GraphicsContext) {
        func point(_ x: Double,_ y: Double)->CGPoint { (frame.coilJoint+(Geometry.Vector(x:x,y:y)-Geometry.feedJoint)*frame.coilScale).point }
        var p = Path(); p.move(to:point(44.08,138.8)); p.addQuadCurve(to:point(40,154),control:point(44.76,172.05)); p.addQuadCurve(to:frame.coilJoint.point,control:point(37.28,141.65))
        context.stroke(p,with:.color(Color(hex:0xFF7F8C).opacity(frame.coilOpacity)),style:StrokeStyle(lineWidth:frame.coilWidth,lineCap:.round))
    }
}
