import AVFoundation
import CoreText
import UIKit

final class SwipeToOrderOnboardingResources {
  static let shared = SwipeToOrderOnboardingResources()
  let display: String
  let medium: String
  let semibold: String
  var cards: [UIImage] = []
  var ring = UIImage()
  var labels: [String: UIImage] = [:]
  var takeWidth = 0.0
  var ofWidth = 0.0
  var yourWidth = 0.0
  var pillWidth = 0.0
  var players: [String: AVAudioPlayer] = [:]
  init() {
    func register(_ name: String) -> String {
      guard let url = Bundle.main.url(forResource: "SwipeToOrderOnboarding-\(name)", withExtension: "ttf"),
            let provider = CGDataProvider(url: url as CFURL), let face = CGFont(provider) else { return name }
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      return face.postScriptName as String? ?? name
    }
    display = register("Epilogue-400")
    medium = register("Inter-Medium")
    semibold = register("Inter-SemiBold")
    makeArt()
    try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
    for cue in ["snap", "done", "back"] {
      if let url = Bundle.main.url(forResource: "SwipeToOrderOnboarding-sto-\(cue)", withExtension: "wav"),
         let player = try? AVAudioPlayer(contentsOf: url) {
        player.volume = cue == "snap" ? 0.55 : 0.75
        player.prepareToPlay()
        players[cue] = player
      }
    }
  }
  // RN processColor encodes rgba() alpha into an 8-bit colour before UIKit receives it.
  static func alpha(_ value: Double) -> Double { (value*255).rounded()/255 }
  static func ui(_ hex: Int, _ a: Double = 1) -> UIColor {
    UIColor(red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, alpha: a)
  }
  func font(_ name: String, _ size: Double) -> UIFont { UIFont(name: name, size: size) ?? .systemFont(ofSize: size) }
  func attributes(_ face: String, _ size: Double, _ color: UIColor, _ kern: Double = 0) -> [NSAttributedString.Key: Any] {
    var result: [NSAttributedString.Key: Any] = [.font: font(face, size), .foregroundColor: color]
    // An explicit zero disables font kerning in Core Text; RN leaves it unspecified.
    if kern != 0 { result[.kern] = kern }
    return result
  }
  func width(_ text: String, _ face: String, _ size: Double, _ kern: Double = 0) -> Double {
    ceil((text as NSString).size(withAttributes: attributes(face, size, .white, kern)).width*3)/3
  }
  func image(_ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3
    format.preferredRange = .standard
    return UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
  }
  // RN's iOS Text uses the same attributed-string font metrics. Center the font's natural
  // line box inside its explicit lineHeight, without scaling or substituting the font.
  func text(_ text: String, _ x: Double, _ y: Double, _ face: String, _ size: Double,
            _ color: UIColor = .white, _ kern: Double = 0, _ line: Double? = nil, _ maxWidth: Double? = nil) {
    let f = font(face, size)
    let h = ceil(f.lineHeight*3)/3
    let yy = y + ((line ?? h)-h)/2
    let para = NSMutableParagraphStyle()
    para.lineBreakMode = .byTruncatingTail
    var attrs = attributes(face, size, color, kern)
    attrs[.paragraphStyle] = para
    (text as NSString).draw(in: CGRect(x: x, y: yy, width: maxWidth ?? 800, height: h+1), withAttributes: attrs)
  }
  func makeArt() {
    let lime = Self.ui(0xc8f54a), ink = Self.ui(0x1d1a2b)
    takeWidth = width("Take ", display, 50, -1.5)
    ofWidth = width("of ", display, 50, -1.5)
    yourWidth = width(" your", display, 50, -1.5)
    pillWidth = width("control", display, 50, -1.5)+32
    for (key, value, face, size, kern, color, line) in [
      ("brand", "Taskloop", display, 20.0, -0.3, UIColor.white, 0.0),
      ("skip", "Skip →", medium, 15, 0, UIColor.white, 0),
      ("take", "Take ", display, 50, -1.5, UIColor.white, 58),
      ("of", "of ", display, 50, -1.5, UIColor.white, 58),
      ("your", " your", display, 50, -1.5, UIColor.white, 58),
      ("schedule", "schedule", display, 50, -1.5, UIColor.white, 58),
      ("control", "control", display, 50, -1.5, UIColor.white, 58),
      ("controlInk", "control", display, 50, -1.5, ink, 58),
      ("start", "Swipe to get started", semibold, 17, 0, ink, 0),
      ("done", "You're all set", semibold, 17, 0, ink, 0)
    ] {
      let h = line > 0 ? line : ceil(font(face, size).lineHeight*3)/3
      labels[key] = image(CGSize(width: width(value, face, size, kern)+4, height: h)) { _ in
        text(value, 0, 0, face, size, color, kern, h)
      }
    }
    let titles = ["Client Call ABC Corp.", "Focus Block · Code Review", "Leisure Reading"]
    let subs = ["12 Hours Needed", "", "2 Hours Needed"]
    for i in 0..<3 {
      cards.append(image(CGSize(width: 300, height: 86)) { c in
        let rect = CGRect(x: 0, y: 0, width: 300, height: 86)
        let path = UIBezierPath(roundedRect: rect, cornerRadius: 18)
        c.addPath(path.cgPath); c.clip()
        if i != 0 {
          (i == 1 ? lime : Self.ui(0x24202f)).setFill()
          c.fill(rect)
        }
        let color = i == 1 ? ink : UIColor.white
        let titleH = ceil(font(medium, 18).lineHeight*3)/3
        let subH = ceil(font(medium, 13).lineHeight*3)/3
        let tagsH = max(ceil(font(semibold, 10).lineHeight*3)/3+6, subH)
        let stackH = titleH+6+(i == 1 ? tagsH : subH)
        let top = (86-stackH)/2
        text(titles[i], 19, top+(i == 1 ? 1.0/3 : 0), medium, 18, color, -0.2, nil, i == 1 ? 262 : 204)
        if i != 1 {
          text(subs[i], 19, top+titleH+6, medium, 13, .white.withAlphaComponent(Self.alpha(0.7)))
          let ringRect = CGRect(x: 235, y: 20, width: 46, height: 46)
          // CSS border corners interpolate adjacent colours along the diagonals.
          for quadrant in 0..<4 {
            let start = Double(quadrant)*Double.pi/2-Double.pi/4
            c.setStrokeColor(((quadrant == 0 || quadrant == 3) ? UIColor.white.withAlphaComponent(Self.alpha(0.18)) : lime.withAlphaComponent(Self.alpha(i == 0 ? 0.95 : 1))).cgColor)
            c.setLineWidth(4)
            c.addArc(center: CGPoint(x: ringRect.midX, y: ringRect.midY), radius: 21, startAngle: start, endAngle: start+Double.pi/2, clockwise: false)
            c.strokePath()
          }
          let pct = i == 0 ? "24%" : "60%"
          let tw = width(pct, semibold, 12)
          text(pct, 258-tw/2, 43-ceil(font(semibold, 12).lineHeight*3)/6, semibold, 12)
        } else {
          var x = 19.0
          for tag in ["DESIGN", "REVIEW"] {
            let w = width(tag, semibold, 10)+14
            let h = ceil(font(semibold, 10).lineHeight*3)/3+6
            ink.setFill(); UIBezierPath(roundedRect: CGRect(x: x, y: top+titleH+6+(tagsH-h)/2, width: w, height: h), cornerRadius: 9).fill()
            text(tag, x+7, top+titleH+6+(tagsH-h)/2+3+1.0/3, semibold, 10, lime)
            x += w+6
          }
          text("Nov 12", x, top+titleH+6+(tagsH-subH)/2, medium, 13, ink.withAlphaComponent(Self.alpha(0.65)))
        }
        let border = i == 0 ? UIColor.white.withAlphaComponent(Self.alpha(0.28)) : i == 1 ? ink.withAlphaComponent(Self.alpha(0.08)) : UIColor.white.withAlphaComponent(Self.alpha(0.1))
        if i != 0 {
          border.setStroke(); let edge = UIBezierPath(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 17.5)
          edge.lineWidth = 1; edge.stroke()
        }
      })
    }
    ring = image(CGSize(width: 128, height: 128)) { c in
      let f = font(semibold, 9.5) as CTFont
      let chars = Array("PLAN SMARTER • TAKE CONTROL • ".utf16)
      var ids = [CGGlyph](repeating: 0, count: chars.count)
      CTFontGetGlyphsForCharacters(f, chars, &ids, chars.count)
      var widths = [CGSize](repeating: .zero, count: ids.count)
      CTFontGetAdvancesForGlyphs(f, .horizontal, ids, &widths, ids.count)
      let scale = 2*Double.pi*47/widths.reduce(0) { $0+$1.width }
      var s = 0.0
      c.setFillColor(ink.cgColor)
      for i in ids.indices {
        let w = widths[i].width
        let a = (s+w/2)*scale/47-Double.pi/2
        let cs = cos(a+Double.pi/2), sn = sin(a+Double.pi/2)
        let px = 64+cos(a)*47-cs*w/2-sn*3.4
        let py = 64+sin(a)*47-sn*w/2+cs*3.4
        c.saveGState()
        c.translateBy(x: px, y: py)
        c.rotate(by: a+Double.pi/2)
        c.scaleBy(x: 1, y: -1)
        var glyph = ids[i], point = CGPoint.zero
        CTFontDrawGlyphs(f, &glyph, &point, 1, c)
        c.restoreGState()
        s += w
      }
    }
  }
  func play(_ cue: String, _ reduced: Bool) {
    guard !reduced, let p = players[cue] else { return }
    p.currentTime = 0; p.play()
  }
}
