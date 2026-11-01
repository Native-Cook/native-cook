import CoreText
import SwiftUI
import UIKit

final class StepParadeResources {
  static let shared = StepParadeResources()
  private var fonts: [String: UIFont] = [:]
  private(set) var nameWidths: [Double] = []
  private(set) var switchLabelWidth = 0.0
  private var paths: [String: Path] = [:]
  private var footprintPaths: [String: [(Double, Path)]] = [:]
  init() {
    for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
      if let url = Bundle.main.url(forResource: "StepParade-" + name, withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    for (weight, sizes) in [
      ("Regular", [11.0, 12, 14]), ("SemiBold", [11.0, 12, 13, 15]), ("Bold", [17.0, 56]),
      ("Ionicons", [14.0, 16, 18, 23]),
    ] {
      for size in sizes {
        let name: String
        switch weight {
        case "Ionicons": name = "Ionicons"
        case "SemiBold": name = "Inter-SemiBold"
        default: name = "Inter24pt-" + weight
        }
        fonts["\(weight)-\(size)"] = UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
      }
    }
    switchLabelWidth =
      pixelCeil(
        ("Friends" as NSString).size(withAttributes: [.font: fonts["SemiBold-15.0"]!]).width)
    let nameFont = fonts["SemiBold-11.0"]!
    nameWidths = StepParadeMotion.friends.map {
      pixelCeil(($0.name as NSString).size(withAttributes: [.font: nameFont]).width)
    }
  }
  func path(_ key: String, make: () -> Path) -> Path {
    if let path = paths[key] { return path }
    let path = make()
    paths[key] = path
    return path
  }
  /// Snow prints stay in fixed lanes; a frame only reveals a prefix of this cached trail.
  func footprints(_ scene: CGRect, _ friend: Int) -> [(Double, Path)] {
    let key = "\(scene)-\(friend)"
    if let cached = footprintPaths[key] { return cached }
    let place = StepParadeMotion.place(scene, friend, 0)
    var x = place.x
    var index = 0
    var result: [(Double, Path)] = []
    while x < scene.maxX {
      let odd = index % 2
      let oval = CGRect(
        x: x, y: place.y - 1.5 * place.s + (odd == 1 ? 2.5 : -1) * place.s,
        width: 4.5 * place.s, height: 2.2 * place.s)
      result.append((x, Path(ellipseIn: oval)))
      x += 9 * place.s
      index += 1
    }
    footprintPaths[key] = result
    return result
  }
  func pixelCeil(_ value: Double) -> Double {
    let scale = UIScreen.main.scale
    return ceil(value * scale) / scale
  }
  func lineHeight(_ weight: String, _ size: Double) -> Double {
    pixelCeil(font(weight, size).lineHeight)
  }
  func font(_ weight: String, _ size: Double) -> UIFont { fonts["\(weight)-\(size)"]! }
}
