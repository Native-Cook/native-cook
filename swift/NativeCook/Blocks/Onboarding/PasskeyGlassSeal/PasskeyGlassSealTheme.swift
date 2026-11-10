import SwiftUI

enum PasskeyGlassSealTheme {
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255)/255, green: Double(hex >> 8 & 255)/255, blue: Double(hex & 255)/255, opacity: alpha)
    }
    static let top = color(0xD2668A), mid = color(0x8F5076), bottom = color(0x4D3B5D)
    static let card = color(0x93537A), cardBottom = color(0x784A6C)
    struct Layout {
        let w, h, top, bottom, sx, k: Double
        init(_ size: CGSize, _ top: Double, _ bottom: Double) {
            w = size.width; h = size.height; self.top = max(top,44); self.bottom = max(bottom,20)
            sx = size.width/402; k = (size.height-self.top-self.bottom)/778
        }
        func y(_ v: Double) -> Double { v < 62 ? v*top/62 : top+(v-62)*k }
        var s: Double { min(sx,k) }
        var x: Double { (35+327.0/2)*sx-cw/2 }; var cy: Double { y(276+324.0/2)-ch/2 }
        var cw: Double { 327*s }; var ch: Double { 324*s }
        var px: Double { 165*s }; var py: Double { 161*s }
    }
    static let mark = Path { p in
        // SVG M27 6 A21 21 0 1 0 48 27 (270 degrees counter-clockwise).
        p.addArc(center: CGPoint(x: 27,y: 27), radius: 21, startAngle: .degrees(-90), endAngle: .degrees(-360), clockwise: true)
    }
    static let triangle = Path { p in p.move(to: CGPoint(x:37,y:5)); p.addLine(to: CGPoint(x:49,y:5)); p.addLine(to: CGPoint(x:49,y:17)); p.closeSubpath() }
    static let backCircle = Path(ellipseIn:CGRect(x:2,y:2,width:60,height:60))
    static let check = Path { p in p.move(to: CGPoint(x:20,y:33)); p.addLine(to: CGPoint(x:28,y:41)); p.addLine(to: CGPoint(x:44,y:24)) }
}
