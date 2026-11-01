import CoreText
import SwiftUI
import UIKit

final class PopUpQuestCardResources {
  static let shared = PopUpQuestCardResources()
  struct Quest {
    var title: String; var price: String; var bg: String; var tint: String
    var cats: [[String]]; var acts: [[String: String]]; var hinges: [Double]
    var art: [UIImage]
  }
  var quests: [Quest] = []
  var icons: [String: Int] = [:]
  private var pages: [String: UIImage] = [:]
  static func color(_ hex: String, _ alpha: Double = 1) -> UIColor {
    let n = UInt32(hex.dropFirst(), radix: 16) ?? 0
    return UIColor(red: Double((n >> 16) & 255) / 255, green: Double((n >> 8) & 255) / 255, blue: Double(n & 255) / 255, alpha: alpha)
  }
  static func image(_ size: CGSize, scale: Double = 3, _ draw: (CGContext) -> Void) -> UIImage {
    let f = UIGraphicsImageRendererFormat(); f.scale = scale; f.preferredRange = .standard
    return UIGraphicsImageRenderer(size: size, format: f).image { draw($0.cgContext) }
  }
  init() {
    for name in ["Nunito-ExtraBold", "Nunito-Black", "Ionicons"] {
      if let url = Bundle.main.url(forResource: "PopUpQuestCard-\(name)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      }
    }
    guard let url = Bundle.main.url(forResource: "PopUpQuestCard-art", withExtension: "json"),
      let bytes = try? Data(contentsOf: url), let data = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
      let qs = data["quests"] as? [[String: Any]] else { return }
    icons = data["icons"] as? [String: Int] ?? [:]
    for q in qs {
      let layers = q["layers"] as! [[String: Any]]
      let art = layers.map { layer in
        Self.image(CGSize(width: 1200, height: 900), scale: 1.5) { cv in
          cv.translateBy(x: 100, y: 100)
          for command in layer["pic"] as! [[String: Any]] { Self.draw(command, cv) }
        }
      }
      quests.append(Quest(title: q["title"] as! String, price: q["price"] as! String, bg: q["bg"] as! String, tint: q["tint"] as! String, cats: q["cats"] as! [[String]], acts: q["acts"] as! [[String: String]], hinges: layers.map { $0["hinge"] as! Double }, art: art))
    }
  }
  static func draw(_ command: [String: Any], _ cv: CGContext) {
    let op = command["op"] as! String, args = command["args"] as! [Any]
    let paint = args.last as! [String: Any]
    let path = CGMutablePath()
    func n(_ i: Int) -> Double { (args[i] as! NSNumber).doubleValue }
    switch op {
    case "drawRect", "drawRRect", "drawOval":
      let a = args[0] as! [Double], r = CGRect(x: a[0], y: a[1], width: a[2], height: a[3])
      if op == "drawRect" { path.addRect(r) }
      else if op == "drawOval" { path.addEllipse(in: r) }
      else { path.addRoundedRect(in: r, cornerWidth: a[4], cornerHeight: a[5]) }
    case "drawCircle": path.addEllipse(in: CGRect(x: n(0) - n(2), y: n(1) - n(2), width: n(2) * 2, height: n(2) * 2))
    case "drawLine": path.move(to: CGPoint(x: n(0), y: n(1))); path.addLine(to: CGPoint(x: n(2), y: n(3)))
    case "drawPath":
      for a in args[0] as! [[Any]] {
        func d(_ i: Int) -> Double { (a[i] as! NSNumber).doubleValue }
        switch a[0] as! String {
        case "m": path.move(to: CGPoint(x: d(1), y: d(2)))
        case "l": path.addLine(to: CGPoint(x: d(1), y: d(2)))
        case "q": path.addQuadCurve(to: CGPoint(x: d(3), y: d(4)), control: CGPoint(x: d(1), y: d(2)))
        default: path.closeSubpath()
        }
      }
    default: return
    }
    cv.saveGState()
    cv.setAlpha(paint["alpha"] as? Double ?? 1)
    if let g = paint["gradient"] as? [String: Any] {
      cv.addPath(path); cv.clip()
      let a = g["a"] as! [String: Double], b = g["b"] as! [String: Double]
      let colors = (g["colors"] as! [String]).map { color($0).cgColor }
      let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors as CFArray, locations: [0, 1])!
      cv.drawLinearGradient(gradient, start: CGPoint(x: a["x"]!, y: a["y"]!), end: CGPoint(x: b["x"]!, y: b["y"]!), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    } else {
      let c = color(paint["color"] as? String ?? "#000000").cgColor
      cv.addPath(path)
      if (paint["style"] as? Int) == 1 {
        cv.setStrokeColor(c); cv.setLineWidth(paint["width"] as? Double ?? 1); cv.setLineCap(.round); cv.strokePath()
      } else { cv.setFillColor(c); cv.fillPath() }
    }
    cv.restoreGState()
  }
  func font(_ size: Double, _ black: Bool = false) -> UIFont {
    UIFont(name: black ? "Nunito-Black" : "Nunito-ExtraBold", size: size) ?? .systemFont(ofSize: size, weight: black ? .black : .heavy)
  }
  func line(_ size: Double, _ black: Bool = false) -> Double {
    let scale = UIScreen.main.scale
    return ceil(font(size, black).lineHeight * scale) / scale
  }
  @discardableResult func text(_ s: String, _ x: Double, _ y: Double, _ size: Double, _ black: Bool = false, _ color: String = "#2A2633", width: Double? = nil, center: Bool = false) -> Double {
    let f = font(size, black), p = NSMutableParagraphStyle(); p.lineBreakMode = .byTruncatingTail
    let a: [NSAttributedString.Key: Any] = [.font: f, .foregroundColor: Self.color(color), .paragraphStyle: p]
    let scale = UIScreen.main.scale
    let measured = ceil((s as NSString).size(withAttributes: a).width * scale) / scale
    let w = center ? measured : (width ?? measured)
    let left = center ? x + ((width ?? measured) - measured) / 2 : x
    // Yoga floors text-node positions while rounding their measured dimensions up.
    (s as NSString).draw(in: CGRect(x: floor(left * scale + 0.0001) / scale, y: floor(y * scale + 0.0001) / scale, width: w, height: line(size, black) + 1), withAttributes: a)
    return line(size, black)
  }
  func icon(_ name: String, _ x: Double, _ y: Double, _ size: Double, _ color: String) {
    guard let cp = icons[name], let scalar = UnicodeScalar(cp) else { return }
    let f = UIFont(name: "Ionicons", size: size) ?? UIFont.systemFont(ofSize: size)
    let a: [NSAttributedString.Key: Any] = [.font: f, .foregroundColor: Self.color(color)]
    let s = String(scalar) as NSString
    let scale = UIScreen.main.scale
    let height = ceil(f.lineHeight * scale) / scale
    s.draw(at: CGPoint(x: floor(x * scale + 0.0001) / scale, y: floor((y - (height - size) / 2) * scale + 0.0001) / scale), withAttributes: a)
  }
  func rounded(_ cv: CGContext, _ r: CGRect, _ radius: Double, _ c: String, _ alpha: Double = 1) {
    let scale = UIScreen.main.scale
    let minX = (r.minX * scale).rounded() / scale, minY = (r.minY * scale).rounded() / scale
    let rect = CGRect(x: minX, y: minY, width: (r.maxX * scale).rounded() / scale - minX, height: (r.maxY * scale).rounded() / scale - minY)
    cv.setFillColor(Self.color(c, alpha).cgColor); cv.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)); cv.fillPath()
  }
  func cached(_ key: String, _ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
    if let image = pages[key] { return image }
    let image = Self.image(size, draw); pages[key] = image; return image
  }
}
