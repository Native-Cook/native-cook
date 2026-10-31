import SwiftUI
import CoreText

enum LiveLikeBubbleTheme {
  static let page = Color(red: 233/255, green: 233/255, blue: 238/255)
  static let ink = Color(red: 22/255, green: 22/255, blue: 27/255)
  static let mute = Color(red: 111/255, green: 111/255, blue: 122/255)
  static let live = Color(red: 1, green: 46/255, blue: 85/255)
  static let line = Color(red: 217/255, green: 217/255, blue: 224/255)
  static let finger = Color(red: 30/255, green: 30/255, blue: 60/255)
  static let fonts: Void = {
    for weight in ["Regular", "Medium", "SemiBold"] {
      if let url = Bundle.main.url(forResource: "LiveLikeBubble-Inter-\(weight)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
  }()
  static func font(_ size: Double, _ weight: String = "Medium") -> Font {
    _ = fonts
    return .custom(weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)", fixedSize: size)
  }
}

struct LiveLikeBubbleHeart: Shape {
  func path(in rect: CGRect) -> Path {
    var p = Path()
    p.move(to: CGPoint(x:12,y:21))
    p.addCurve(to: CGPoint(x:1.4,y:10.6), control1: CGPoint(x:12,y:21), control2: CGPoint(x:3.5,y:15.7))
    p.addCurve(to: CGPoint(x:7.1,y:1.5), control1: CGPoint(x:-0.1,y:6), control2: CGPoint(x:3,y:1.5))
    p.addCurve(to: CGPoint(x:12,y:4.3), control1: CGPoint(x:9.3,y:1.5), control2: CGPoint(x:10.9,y:2.7))
    p.addCurve(to: CGPoint(x:16.9,y:1.5), control1: CGPoint(x:13.1,y:2.7), control2: CGPoint(x:14.7,y:1.5))
    p.addCurve(to: CGPoint(x:22.6,y:10.6), control1: CGPoint(x:21,y:1.5), control2: CGPoint(x:24.1,y:6))
    p.addCurve(to: CGPoint(x:12,y:21), control1: CGPoint(x:20.5,y:15.7), control2: CGPoint(x:12,y:21))
    p.closeSubpath()
    // SVG's default xMidYMid meet preserves the 24:22 viewBox aspect ratio.
    let scale = min(rect.width/24,rect.height/22)
    return p.applying(CGAffineTransform(a:scale,b:0,c:0,d:scale,tx:rect.midX-12*scale,ty:rect.midY-11*scale))
  }
}
