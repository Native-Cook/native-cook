import SwiftUI

struct GlassTilesFuseRowIcon: View {
    let index: Int
    var body: some View {
        Canvas { c,_ in
            func fill(_ p: Path,_ hex: UInt32) { c.fill(p,with:.color(GlassTilesFuseTheme.color(hex))) }
            func rect(_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ r: Double,_ hex: UInt32) { fill(Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:r),hex) }
            func path(_ points: [CGPoint],_ hex: UInt32,_ stroke: Double=0) {
                var p=Path(); p.addLines(points)
                if stroke>0 { c.stroke(p,with:.color(GlassTilesFuseTheme.color(hex)),style:StrokeStyle(lineWidth:stroke,lineCap:.round,lineJoin:.round)) }
                else { p.closeSubpath(); fill(p,hex) }
            }
            switch index {
            case 0:
                rect(0,0,36,36,9,0x8E8E93); fill(Path(ellipseIn:CGRect(x:12.5,y:8.5,width:11,height:11)),0xF2F2F2)
                var p=Path(); p.move(to:CGPoint(x:8,y:29)); p.addCurve(to:CGPoint(x:18,y:21),control1:CGPoint(x:9.5,y:23.5),control2:CGPoint(x:13.5,y:21)); p.addCurve(to:CGPoint(x:28,y:29),control1:CGPoint(x:22.5,y:21),control2:CGPoint(x:26.5,y:23.5)); fill(p,0xF2F2F2)
            case 1:
                rect(0,0,36,36,9,0x2F6BFF); rect(8,11,20,14,2.5,0xFFFFFF)
                path([CGPoint(x:8.8,y:12),CGPoint(x:18,y:19),CGPoint(x:27.2,y:12)],0x2F6BFF,1.8)
            case 2:
                rect(0,0,36,36,9,0xFFFFFF); rect(0,0,36,10,0,0xF2554C)
                var p=Path(); p.move(to:CGPoint(x:13,y:17)); p.addLine(to:CGPoint(x:17,y:17)); p.addLine(to:CGPoint(x:14.5,y:20.2)); p.addCurve(to:CGPoint(x:17.2,y:22.6),control1:CGPoint(x:16.1,y:20.2),control2:CGPoint(x:17.2,y:21.2)); p.addCurve(to:CGPoint(x:14.4,y:25.2),control1:CGPoint(x:17.2,y:24.1),control2:CGPoint(x:16,y:25.2)); p.addCurve(to:CGPoint(x:12,y:23.9),control1:CGPoint(x:13.3,y:25.2),control2:CGPoint(x:12.4,y:24.7)); p.move(to:CGPoint(x:21,y:18.5)); p.addLine(to:CGPoint(x:23.4,y:17)); p.addLine(to:CGPoint(x:23.4,y:25)); c.stroke(p,with:.color(GlassTilesFuseTheme.color(0x1C1C1E)),style:StrokeStyle(lineWidth:1.7,lineCap:.round,lineJoin:.round))
            default:
                rect(0,0,36,36,9,0x1F1F21)
                path([CGPoint(x:18,y:9),CGPoint(x:27,y:24.5),CGPoint(x:9,y:24.5)],0x4BC27D)
                path([CGPoint(x:18,y:9),CGPoint(x:22.5,y:16.8),CGPoint(x:13.5,y:16.8)],0xF4C443)
                path([CGPoint(x:9,y:24.5),CGPoint(x:27,y:24.5),CGPoint(x:24.4,y:28),CGPoint(x:11.6,y:28)],0x3D8BFF)
            }
        }.accessibilityHidden(true)
    }
}
