import SwiftUI

struct VitaminCapsuleDoseLayout: Equatable {
  var w, h, top, bottom: Double
  private var safeTop: Double { max(top, 44) }
  private var safeBottom: Double { max(bottom, 20) }
  var sx: Double { w / 402 }
  var k: Double { (h - safeTop - safeBottom) / 778 }
  func y(_ v: Double) -> Double { v < 62 ? v * safeTop / 62 : safeTop + (v - 62) * k }
  var chipH: Double { 66 * max(k, 0.9) }
  var chipTop: Double { y(410) + (66 * k - chipH) / 2 }
  var chipPad: Double { (chipH - 66) / 2 }
  var cx: Double { 101 * sx }
  var cy: Double { y(221) }
  var unit: Double { 76 * sx }
  var floor: Double { y(298) }
  var checkX: Double { 351 * sx }
  var rowsY: [Double] { [568,606.5,645].map(y) }
  var logY: Double { y(805) }
}
enum VitaminCapsuleDoseTheme {
  struct Med {
    var name, kind, dose, daily, each: String
    var shape: Double
    var times: [String]
  }
  static let meds = [
    Med(name:"Lumivit",kind:"Dietary supplement",dose:"20mg pills",daily:"60 mg",each:"3x Pills",shape:0,times:["10:00 AM","3:00 PM","6:00 PM"]),
    Med(name:"Calmora",kind:"Antacid suspension",dose:"200ml",daily:"30 ml",each:"3x Spoons",shape:2,times:["8:00 AM","1:00 PM","8:00 PM"]),
    Med(name:"Mentab",kind:"Pain relief",dose:"500mg tablets",daily:"1000 mg",each:"2x Pills",shape:1,times:["9:00 AM","2:00 PM","9:00 PM"])
  ]
  static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
    Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:alpha)
  }
  static let behind=color(0xB8B9C0), ink=color(0x0B0C14), sub=color(0x5B6280), fill=color(0xEEF0F4)
  static let pink=color(0xEC5F7E), pinkText=color(0xEA4D70), pinkDeep=color(0xD9446A)
  static let ring=color(0x474F74), green=color(0x3EAA2D), pill=color(0xEE3A8F), blue=color(0x1D74DB)
}
