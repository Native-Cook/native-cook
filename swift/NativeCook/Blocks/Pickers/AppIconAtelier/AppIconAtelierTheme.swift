import SwiftUI

// Screen coordinates are points from the full-screen origin, exactly as in makeLayout.
enum AppIconAtelierTheme {
  struct Glass {
    let name, note: String
    let tint: SIMD3<Double>
    let density, frost, film, glow: Double
    let light, deep: Int
  }
  static let glasses: [Glass] = [
    .init(name: "Lilac Glass", note: "The original. Clear, cool, a touch of violet.", tint: .init(0.58,0.4,0.98), density: 1.1, frost: 0, film: 0.55, glow: 0, light: 0xE4DAFB, deep: 0x9B7BEA),
    .init(name: "Pearl", note: "Milky glass with a mother-of-pearl sheen.", tint: .init(0.97,0.95,0.99), density: 0.6, frost: 0.75, film: 1, glow: 0, light: 0xFFFFFF, deep: 0xD9D3E6),
    .init(name: "Mint Frost", note: "Sandblasted glass the colour of sea mist.", tint: .init(0.6,0.92,0.8), density: 1, frost: 0.6, film: 0.3, glow: 0, light: 0xE2F7EE, deep: 0x7FCDB0),
    .init(name: "Amber", note: "Warm honey glass, poured thick.", tint: .init(1,0.72,0.32), density: 1.5, frost: 0.05, film: 0.25, glow: 0, light: 0xFCE6C4, deep: 0xE39A3B),
    .init(name: "Smoke", note: "Dark glass, with the heart lit from within.", tint: .init(0.36,0.33,0.46), density: 2, frost: 0.1, film: 0.6, glow: 0.8, light: 0x8C849E, deep: 0x2B2638),
  ]
  static let ink = color(0x1C1630), mute = color(0x8A8399)
  static func color(_ value: Int, _ opacity: Double = 1) -> Color {
    Color(.sRGB, red: Double((value >> 16)&255)/255, green: Double((value >> 8)&255)/255, blue: Double(value&255)/255, opacity: opacity)
  }
  struct Layout: Equatable {
    let w,h,barTop,heroX,heroY,heroS,eyebrowTop,nameTop,noteTop,chipsTop,chipsLeft,homeLabelTop,cardX,cardY,cardW,appsY,ctaTop: Double
    let appX: [Double]
    init(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) {
      self.w=w; self.h=h; barTop=top
      ctaTop=h-max(bottom,16)-8-56
      cardY=ctaTop-24-168; homeLabelTop=cardY-12-18
      chipsTop=homeLabelTop-32-52; noteTop=chipsTop-24-20
      nameTop=noteTop-4-42; eyebrowTop=nameTop-4-16
      let room0=top+44, room=eyebrowTop-room0
      heroX=w/2; heroS=min(w*0.25, room*0.36,104); heroY=room0+room*0.44
      cardX=16; cardW=w-32
      let gap=(cardW-56-240)/3
      appX=(0..<4).map { 16+28+30+Double($0)*(60+gap) }
      appsY=cardY+30+26+30; chipsLeft=(w-324)/2
    }
    func chip(_ i: Double) -> Double { chipsLeft+26+i*68 }
    func flight(_ p: Double) -> CGPoint {
      let u=1-p
      return CGPoint(x: u*u*heroX+2*u*p*(heroX+w*0.3)+p*p*appX[2], y: u*u*heroY+2*u*p*(heroY+appsY)*0.5+p*p*appsY)
    }
  }
}
