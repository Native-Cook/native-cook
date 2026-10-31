import AVFoundation
import CoreText
import UIKit

final class WavyFrameAuctionResources {
  static let shared = WavyFrameAuctionResources()
  let title: UIFont
  let piece: UIFont
  let medium: UIFont
  let semibold: UIFont
  let bid: UIFont
  let intro: UIImage
  private var players: [String: AVAudioPlayer] = [:]
  private var countdowns: [String: UIImage] = [:]
  init() {
    var names: [String: String] = [:]
    for file in ["Outfit-Medium", "Inter-Medium", "Inter-SemiBold"] {
      if let url = Bundle.main.url(forResource: "WavyFrameAuction-\(file)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor], let d = descriptors.first {
          names[file] = CTFontCopyPostScriptName(CTFontCreateWithFontDescriptor(d, 12, nil)) as String
        }
      }
    }
    func font(_ name: String, _ size: Double) -> UIFont { UIFont(name: names[name] ?? name, size: size) ?? .systemFont(ofSize: size) }
    title = font("Outfit-Medium", 32); piece = font("Outfit-Medium", 22)
    medium = font("Inter-Medium", 13); semibold = font("Inter-SemiBold", 14); bid = font("Inter-SemiBold", 17)
    let face = title
    let format = UIGraphicsImageRendererFormat(); format.scale = 3
    intro = UIGraphicsImageRenderer(size: CGSize(width: 354, height: 120), format: format).image { r in
      for (i, string) in [" Perhaps The", "Most Convenient", "NFT Auction"].enumerated() {
        let text = NSMutableAttributedString(string: string, attributes: [.font: face, .foregroundColor: UIColor.white])
        if i == 0 { text.addAttributes([.backgroundColor: Self.color(0xc8f560), .foregroundColor: Self.color(0x21251f)], range: NSRange(location: 0, length: 5)) }
        if i == 1 { text.addAttribute(.foregroundColor, value: Self.color(0xc8f560), range: NSRange(location: 5, length: 10)) }
        let width = text.size().width
        text.draw(at: CGPoint(x: (354 - width) / 2, y: Double(i) * 40 + (40 - face.lineHeight) / 2))
      }
    }
    try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
    for name in ["unfold", "fold", "tick"] {
      if let url = Bundle.main.url(forResource: "WavyFrameAuction-wfa-\(name)", withExtension: "wav"), let p = try? AVAudioPlayer(contentsOf: url) {
        p.volume = name == "tick" ? 0.45 : 0.7; p.prepareToPlay(); players[name] = p
      }
    }
  }
  static func color(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
  }
  func cue(_ name: String, _ reduced: Bool) {
    guard !reduced, let p = players[name] else { return }
    p.currentTime = 0; p.play()
  }
  func stop() { for p in players.values { p.stop() } }
  func details(_ seconds: Int, _ width: Double) -> UIImage {
    let key = "\(seconds)-\(width)"
    if let image = countdowns[key] { return image }
    let format = UIGraphicsImageRendererFormat(); format.scale = 3
    let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: 100), format: format).image { _ in
      let c = UIGraphicsGetCurrentContext()!
      let creatorHeight = max(42, ceil(piece.lineHeight) + 2 + ceil(medium.lineHeight))
      let avatarY = (creatorHeight - 42) / 2
      c.setFillColor(Self.color(0x7862f8).cgColor)
      c.fillEllipse(in: CGRect(x: 0, y: avatarY, width: 42, height: 42))
      c.setStrokeColor(Self.color(0xc8f560).cgColor); c.setLineWidth(2)
      c.strokeEllipse(in: CGRect(x: 1, y: avatarY + 1, width: 40, height: 40))
      func label(_ text: String, _ font: UIFont, _ x: Double, _ y: Double, _ color: UIColor) {
        (text as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: [.font: font, .foregroundColor: color])
      }
      label("Lumen Tide #042", piece, 54, (creatorHeight - ceil(piece.lineHeight) - 2 - ceil(medium.lineHeight)) / 2, .white)
      label("@nadia.studio", medium, 54, (creatorHeight - ceil(piece.lineHeight) - 2 - ceil(medium.lineHeight)) / 2 + ceil(piece.lineHeight) + 2, .white.withAlphaComponent(0.55))
      var x = 0.0
      for (text, ink, bg) in [("0.34 ETH", 0x21251f, 0xc8f560), ("$1,284", 0xffffff, 0x4e515c)] {
        let width = ceil((text as NSString).size(withAttributes: [.font: semibold]).width) + 24
        c.setFillColor(Self.color(bg).cgColor)
        c.addPath(UIBezierPath(roundedRect: CGRect(x: x, y: creatorHeight + 16, width: width, height: 32), cornerRadius: 16).cgPath); c.fillPath()
        label(text, semibold, x + 12, creatorHeight + 16 + (32 - ceil(semibold.lineHeight)) / 2 - 0.5, Self.color(ink)); x += width + 8
      }
      let text = String(format: "Ends in %02d : %02d : %02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
      let desc = medium.fontDescriptor.addingAttributes([.featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType, UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]])
      let nums = UIFont(descriptor: desc, size: 13)
      let textWidth = (text as NSString).size(withAttributes: [.font: nums]).width
      label(text, nums, width - textWidth, creatorHeight + 16 + (32 - ceil(nums.lineHeight)) / 2, .white.withAlphaComponent(0.75))
    }
    if countdowns.count > 10 { countdowns.removeAll() }
    countdowns[key] = image
    return image
  }
}
