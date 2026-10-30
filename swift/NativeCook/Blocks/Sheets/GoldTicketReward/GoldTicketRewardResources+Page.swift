import CoreImage
import UIKit

extension GoldTicketRewardResources {
  func makePage() -> UIImage {
    typealias P = H.PAGE
    typealias C = H.C
    let format = UIGraphicsImageRendererFormat()
    format.scale = 3
    let image = UIGraphicsImageRenderer(size: CGSize(width: 402, height: 874), format: format).image
    { renderer in
      let c = renderer.cgContext
      func color(_ hex: Int) -> UIColor {
        UIColor(
          red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
          blue: Double(hex & 255) / 255, alpha: 1)
      }
      func fill(_ rect: CGRect, _ ink: Int, _ radius: Double = 0) {
        c.setFillColor(color(ink).cgColor)
        c.addPath(UIBezierPath(roundedRect: rect, cornerRadius: radius).cgPath)
        c.fillPath()
      }
      func stroke(_ path: UIBezierPath, _ ink: Int, _ width: Double) {
        c.setStrokeColor(color(ink).cgColor)
        c.setLineWidth(width)
        c.setLineCap(.round)
        c.setLineJoin(.round)
        c.addPath(path.cgPath)
        c.strokePath()
      }
      func line(_ points: [CGPoint], _ ink: Int, _ width: Double) {
        let path = UIBezierPath()
        if let first = points.first { path.move(to: first) }
        for point in points.dropFirst() { path.addLine(to: point) }
        stroke(path, ink, width)
      }
      func circle(_ x: Double, _ y: Double, _ r: Double, _ ink: Int, _ width: Double = 0) {
        let path = UIBezierPath(ovalIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        if width > 0 {
          stroke(path, ink, width)
        } else {
          c.setFillColor(color(ink).cgColor)
          c.addPath(path.cgPath)
          c.fillPath()
        }
      }
      func text(_ key: String, _ x: Double, _ y: Double, _ ax: Double, _ ink: Int) {
        guard let l = labels[key] else { return }
        l.bitmap.withTintColor(color(ink), renderingMode: .alwaysOriginal).draw(
          in: CGRect(
            x: x - l.width * ax - 12, y: y + l.size * 0.35 - l.baseline, width: l.bitmap.size.width,
            height: l.bitmap.size.height))
      }
      fill(CGRect(x: 0, y: 0, width: 402, height: 874), C.page)
      let bx = P.back.x - 7
      let by = P.back.y
      line(
        [CGPoint(x: bx + 7, y: by - 6), CGPoint(x: bx, y: by), CGPoint(x: bx + 7, y: by + 6)],
        C.ink, 1.8)
      line([CGPoint(x: bx, y: by), CGPoint(x: bx + 15, y: by)], C.ink, 1.8)
      text("title", P.title.x, P.title.y, 0, C.ink)
      let hs = P.help
      let headset = UIBezierPath(
        arcCenter: CGPoint(x: hs.icon, y: hs.y - 0.5), radius: 6.5, startAngle: .pi,
        endAngle: 2 * .pi, clockwise: true)
      stroke(headset, C.ink, 1.6)
      for x in [hs.icon - 6.5, hs.icon + 6.5] {
        line([CGPoint(x: x, y: hs.y), CGPoint(x: x, y: hs.y + 3.5)], C.ink, 1.6)
      }
      text("help", hs.x, hs.y, 1, C.ink)
      let check = P.check
      circle(check.x, check.y, check.r, C.violet, check.stroke)
      line(
        [
          CGPoint(x: check.x - 8, y: check.y + 0.5), CGPoint(x: check.x - 2.5, y: check.y + 6),
          CGPoint(x: check.x + 8.5, y: check.y - 6),
        ], C.violet, check.stroke)
      text("status", P.status.x, P.status.y, 0.5, C.ink)
      for box in [P.card, P.card2] {
        fill(box.rect, C.card, 12)
        stroke(UIBezierPath(roundedRect: box.rect, cornerRadius: 12), C.cardLine, 1)
      }
      let av = P.avatar
      circle(av.x, av.y, av.r + 3, 0xececf3)
      circle(av.x, av.y - 2.6, 3.4, 0x9a99ab)
      let shoulders = UIBezierPath(
        ovalIn: CGRect(x: av.x - 6, y: av.y + 1.5, width: 12, height: 10))
      c.saveGState()
      c.clip(to: CGRect(x: av.x - 6, y: av.y + 1.5, width: 12, height: 5))
      c.setFillColor(color(0x9a99ab).cgColor)
      c.addPath(shoulders.cgPath)
      c.fillPath()
      c.restoreGState()
      text("expert", P.expert.x, P.expert.y, 0, C.ink)
      fill(P.badge.rect, 0xf2c94c, 3)
      text("verified", P.verified.x, P.verified.y, 0, C.mute)
      // Same handset outline as RN, translated to the phone anchor.
      let ph = P.phone
      let handset = UIBezierPath()
      handset.move(to: CGPoint(x: -5.5, y: -7))
      handset.addQuadCurve(to: CGPoint(x: -7, y: -5), controlPoint: CGPoint(x: -7, y: -7))
      handset.addQuadCurve(to: CGPoint(x: 3, y: 7), controlPoint: CGPoint(x: -6, y: 3))
      handset.addQuadCurve(to: CGPoint(x: 6, y: 6), controlPoint: CGPoint(x: 5, y: 7.5))
      handset.addLine(to: CGPoint(x: 7, y: 4))
      handset.addQuadCurve(to: CGPoint(x: 6, y: 2.4), controlPoint: CGPoint(x: 7.2, y: 3))
      handset.addLine(to: CGPoint(x: 3.6, y: 1.4))
      handset.addQuadCurve(to: CGPoint(x: 2, y: 1.9), controlPoint: CGPoint(x: 2.6, y: 1))
      handset.addLine(to: CGPoint(x: 1.3, y: 2.8))
      handset.addQuadCurve(to: CGPoint(x: -4.4, y: -2.8), controlPoint: CGPoint(x: -2.8, y: 1))
      handset.addLine(to: CGPoint(x: -3.4, y: -3.5))
      handset.addQuadCurve(to: CGPoint(x: -2.8, y: -5.2), controlPoint: CGPoint(x: -2.4, y: -4.1))
      handset.addLine(to: CGPoint(x: -3.8, y: -7))
      handset.addQuadCurve(to: CGPoint(x: -5.5, y: -7), controlPoint: CGPoint(x: -4.3, y: -7.6))
      handset.close()
      c.saveGState()
      c.translateBy(x: ph.x, y: ph.y)
      c.setFillColor(color(C.violet).cgColor)
      c.addPath(handset.cgPath)
      c.fillPath()
      c.restoreGState()
      line(
        [
          CGPoint(x: P.card.x + 12, y: P.divider),
          CGPoint(x: P.card.x + P.card.w - 12, y: P.divider),
        ], C.cardLine, 1)
      let ri = P.rowIcon
      circle(ri.x, ri.y, 5.5, C.mute, 1.4)
      line(
        [
          CGPoint(x: ri.x, y: ri.y - 2.8), CGPoint(x: ri.x, y: ri.y),
          CGPoint(x: ri.x + 2.2, y: ri.y + 1.4),
        ], C.mute, 1.4)
      text("progress", P.progress.x, P.progress.y, 0, C.ink)
      let cv = P.chevron
      line(
        [
          CGPoint(x: cv.x - 2.5, y: cv.y - 5), CGPoint(x: cv.x + 2.5, y: cv.y),
          CGPoint(x: cv.x - 2.5, y: cv.y + 5),
        ], C.mute, 1.6)
      text("section", P.section.x, P.section.y, 0, C.mute)
      text("pros", P.pros.x, P.pros.y, 0, C.ink)
      text("prosSub", P.prosSub.x, P.prosSub.y, 0, C.mute)
      let wi = P.waterIcon
      line(
        [
          CGPoint(x: wi.x - 4, y: wi.y - 5), CGPoint(x: wi.x - 3, y: wi.y + 5),
          CGPoint(x: wi.x + 3, y: wi.y + 5), CGPoint(x: wi.x + 4, y: wi.y - 5),
        ], C.violet, 1.4)
      text("water", P.water.x + 6, P.water.y, 0, C.violet)
    }
    guard let cg = image.cgImage else { return image }
    let source = CIImage(cgImage: cg)
    // Match Skia's blur on encoded sRGB channels, without a linear-light conversion.
    let context = CIContext(options: [.workingColorSpace: NSNull(), .outputColorSpace: NSNull()])
    let blurred = source.clampedToExtent().applyingFilter(
      "CIGaussianBlur", parameters: [kCIInputRadiusKey: 1.6 * 3]
    ).cropped(to: source.extent)
    return context.createCGImage(blurred, from: source.extent).map {
      UIImage(cgImage: $0, scale: 3, orientation: .up)
    } ?? image
  }
}
