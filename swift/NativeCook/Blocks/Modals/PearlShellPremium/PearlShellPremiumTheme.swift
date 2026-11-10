import CoreText
import SwiftUI
import UIKit

enum PearlShellPremiumTheme {
  static let closePath = Path { p in
    p.move(to:CGPoint(x:17,y:17));p.addLine(to:CGPoint(x:27,y:27))
    p.move(to:CGPoint(x:27,y:17));p.addLine(to:CGPoint(x:17,y:27))
  }
  static let features = ["Every sleep story and soundscape", "Listen offline, anywhere", "New sessions every week"]
  static let ink = Color(.sRGB, red:243/255, green:245/255, blue:251/255, opacity:1)
  static let mute = Color(.sRGB, red:140/255, green:151/255, blue:174/255, opacity:1)
  static let pearl = Color(.sRGB, red:233/255, green:228/255, blue:246/255, opacity:1)
  static let page = Color(.sRGB, red:10/255, green:17/255, blue:33/255, opacity:1)
  struct Layout {
    var w: Double, h: Double, barTop: Double
    var heroX: Double, heroY: Double, heroS: Double
    var titleTop: Double, featTop: Double, plansTop: Double, ctaTop: Double, fineTop: Double
    init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) {
      self.w=w;self.h=h;barTop=top
      fineTop=h-max(bottom,16)-18
      ctaTop=fineTop-8-56
      plansTop=ctaTop-16-64
      featTop=plansTop-24-3*22-2*8
      titleTop=featTop-12-44
      let room0=top+44, room=titleTop-room0
      heroX=w/2;heroY=room0+room*0.55;heroS=min(w*0.33,room*0.42,132)
    }
  }
}

final class PearlShellPremiumResources {
  static let shared = PearlShellPremiumResources()
  private var fonts: [String: Font] = [:]
  private init() {
    for name in ["InstrumentSerif-Regular","Inter-Regular","Inter-Medium","Inter-SemiBold"] {
      if let url=Bundle.main.url(forResource:"PearlShellPremium-\(name)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
      }
    }
    for (name,size) in [("InstrumentSerif-Regular",40.0),("Inter-Regular",15),("Inter-Regular",13),
      ("Inter-Medium",15),("Inter-Medium",14),("Inter-SemiBold",11),("Inter-SemiBold",18),("Inter-SemiBold",17)] {
      let postScript = name == "Inter-Regular" ? "Inter24pt-Regular" : name
      fonts["\(name)-\(size)"] = .custom(postScript,fixedSize:size)
    }
  }
  func font(_ name: String, _ size: Double) -> Font { fonts["\(name)-\(size)"]! }
}

struct PearlShellPremiumPressStyle: ButtonStyle {
  var reduced: Bool
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
      .opacity(configuration.isPressed && reduced ? 0.72 : 1)
      .animation(.timingCurve(0.23,1,0.32,1,duration:0.15), value:configuration.isPressed)
  }
}
