import CoreText
import SwiftUI
import UIKit

final class EvolveHoldMorphArt {
  static let shared = EvolveHoldMorphArt()
  private var faces: [String: String] = [:]
  private var fontCache: [String: UIFont] = [:]
  private var images: [String: Image] = [:]
  private var labels: [String: EvolveHoldMorphLabel] = [:]
  private struct EvolveHoldMorphLabel {
    let storage: NSTextStorage
    let manager: NSLayoutManager
    let container: NSTextContainer
    let size: CGSize
  }
  private var dimensions = CGSize.zero
  private init() {
    for file in ["Nunito-ExtraBold", "Nunito-Black", "TitanOne-Regular", "Ionicons"] {
      if let url = Bundle.main.url(forResource: "EvolveHoldMorph-\(file)", withExtension: "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor]
        if let d = descriptors?.first { faces[file] = CTFontDescriptorCopyAttribute(d, kCTFontNameAttribute) as? String }
      }
    }
  }
  static func color(_ hex: Int) -> Color {
    Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: 1)
  }
  private func ui(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, alpha: alpha)
  }
  private func font(_ face: String, _ size: Double) -> UIFont {
    let key = "\(face)-\(size)"
    if let f = fontCache[key] { return f }
    let f = UIFont(name: faces[face] ?? face, size: size) ?? .systemFont(ofSize: size)
    fontCache[key] = f
    return f
  }
  // Match RN's iOS text layout, then cache the glyph layout with the static chrome.
  private func label(_ text: String, _ face: String, _ size: Double, _ hex: Int) -> EvolveHoldMorphLabel {
    let key = "\(text)-\(face)-\(size)-\(hex)"
    if let label = labels[key] { return label }
    let storage = NSTextStorage(string: text, attributes: [.font: font(face, size), .foregroundColor: ui(hex)])
    let manager = NSLayoutManager(); manager.usesFontLeading = false
    let container = NSTextContainer(size: CGSize(width: 1000, height: 1000))
    container.lineFragmentPadding = 0; container.lineBreakMode = .byClipping
    manager.addTextContainer(container); storage.addLayoutManager(manager)
    manager.ensureLayout(for: container)
    let measured = manager.usedRect(for: container).size
    let scale = UIScreen.main.scale
    let label = EvolveHoldMorphLabel(storage: storage, manager: manager, container: container,
      size: CGSize(width: ceil(measured.width*scale)/scale, height: ceil(measured.height*scale)/scale))
    labels[key] = label
    return label
  }
  private func textSize(_ text: String, _ face: String, _ size: Double) -> CGSize {
    label(text, face, size, 0).size
  }
  private func text(_ text: String, _ face: String, _ size: Double, _ hex: Int, _ x: Double, _ y: Double) {
    let label = label(text, face, size, hex)
    let scale = UIScreen.main.scale
    let origin = CGPoint(x: (x*scale).rounded()/scale, y: (y*scale).rounded()/scale)
    let range = label.manager.glyphRange(for: label.container)
    label.manager.drawGlyphs(forGlyphRange: range, at: origin)
  }
  private func round(_ context: CGContext, _ rect: CGRect, _ r: Double, _ hex: Int) {
    context.setFillColor(ui(hex).cgColor)
    context.addPath(CGPath(roundedRect: rect, cornerWidth: r, cornerHeight: r, transform: nil)); context.fillPath()
  }
  func gradient(_ size: CGSize) -> Image {
    if dimensions != size { images.removeAll(); dimensions = size }
    if let image = images["gradient"] { return image }
    let scale = UIScreen.main.scale
    let height = Int((size.height*scale).rounded())
    let space = CGColorSpace(name: CGColorSpace.sRGB)!
    func decode(_ x: Float) -> Float { x <= 0.04045 ? x/12.92 : pow((x+0.055)/1.055, 2.4) }
    func encode(_ x: Float) -> Float { x <= 0.0031308 ? x*12.92 : 1.055*pow(max(0,x), 1/2.4)-0.055 }
    let srgbStops: [[Float]] = [[221/255,243/255,232/255],[243/255,250/255,242/255],[1,247/255,234/255]]
    let stops = srgbStops.map { (c: [Float]) -> [Float] in
      let r = decode(c[0])
      let g = decode(c[1])
      let b = decode(c[2])
      let pr: Float = 0.8225929*r+0.1775340*g
      let pg: Float = 0.0331995*r+0.9667835*g
      let pb: Float = 0.0170854*r+0.0723957*g+0.9103015*b
      return [encode(pr), encode(pg), encode(pb)]
    }
    var bytes = [UInt8](repeating: 255, count: height*4)
    for y in 0..<height {
      let t = (Float(y)+0.5)/Float(height)*2
      let index = min(1, Int(t)), f = t-Float(index)
      var p3 = [Float](repeating: 0, count: 3)
      for channel in 0..<3 {
        let c = stops[index][channel]*(1-f)+stops[index+1][channel]*f
        p3[channel] = decode((c*255).rounded()/255)
      }
      let rgb = [1.2247453*p3[0]-0.2249044*p3[1], -0.0420581*p3[0]+1.0420810*p3[1],
        -0.0196423*p3[0]-0.0786549*p3[1]+1.0985372*p3[2]]
      for channel in 0..<3 {
        bytes[y*4+channel] = UInt8(max(0, min(255, (encode(rgb[channel])*255).rounded())))
      }
    }
    // Match Skia's eight-bit P3 interpolation, then tag the cached pixels as sRGB.
    // This avoids ColorSync profile differences and Core Graphics gradient dithering.
    let provider = CGDataProvider(data: Data(bytes) as CFData)!
    let cg = CGImage(width: 1, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
      bytesPerRow: 4, space: space, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
      provider: provider, decode: nil, shouldInterpolate: false, intent: .relativeColorimetric)!
    let image = Image(decorative: cg, scale: scale).interpolation(.none)
    images["gradient"] = image
    return image
  }
  func background(_ size: CGSize) -> Image {
    cached("background", size) { ctx in
      let w = size.width, h = size.height, cy = (h * 0.43).rounded(), s = (w * 0.42).rounded()
      let y = cy + s * 0.62
      ctx.setFillColor(ui(0x2C5A44, 0.14).cgColor)
      ctx.fillEllipse(in: CGRect(x: w/2-s*0.62, y: y-s*0.05, width: s*1.24, height: s*0.22))
      ctx.setLineWidth(5); ctx.setLineCap(.round)
      for i in 0..<9 {
        let a = -0.15 + Double(i) * 0.04
        let rect = CGRect(x: w/2-s*(0.56-a*0.2), y: y-s*0.12+Double(i)*1.6, width: s*(1.12-a*0.4), height: s*0.26)
        ctx.setStrokeColor(ui(i % 2 == 1 ? 0xC98E52 : 0xB57A40).cgColor)
        ctx.saveGState(); ctx.translateBy(x: rect.midX, y: rect.midY)
        ctx.scaleBy(x: 1, y: rect.height/rect.width)
        ctx.addArc(center: .zero, radius: rect.width/2, startAngle: Double(10+i*4) * .pi/180, endAngle: Double(170-i*2) * .pi/180, clockwise: false)
        // Restore before stroking: Skia's oval arcs keep a uniform five-point stroke.
        ctx.restoreGState(); ctx.strokePath()
      }
    }
  }
  private func cached(_ key: String, _ size: CGSize, draw: (CGContext) -> Void) -> Image {
    if dimensions != size { images.removeAll(); dimensions = size }
    if let image = images[key] { return image }
    let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale; format.opaque = false
    let bitmap = UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
    let image = Image(uiImage: bitmap); images[key] = image
    return image
  }
  func chrome(_ size: CGSize, top: Double, bottom: Double, stage: Int) -> Image {
    cached("chrome-\(stage)-\(top)-\(bottom)", size) { ctx in
      let w = size.width, h = size.height, cy = (h*0.43).rounded(), s = (w*0.42).rounded()
      let bold = "Nunito-ExtraBold", black = "Nunito-Black", title = "TitanOne-Regular"
      let names = EvolveHoldMorphMotion.names
      let smallH = textSize("Your pet", bold, 14).height
      let nameH = textSize("Pip the \(names[stage])", title, 26).height
      text("Your pet", bold, 14, 0x6E8A7C, 20, top+8)
      text("Pip the \(names[stage])", title, 26, 0x22362C, 20, top+8+smallH+2)
      let level = "Lv \(4+stage)", levelSize = textSize(level, black, 14)
      let levelW = 24+14+6+levelSize.width, levelY = top+8+(smallH+2+nameH-34)/2
      round(ctx, CGRect(x: w-20-levelW, y: levelY, width: levelW, height: 34), 17, 0xFFFFFF)
      text("\u{F3B2}", "Ionicons", 14, 0x2F9E62, w-20-levelW+12, levelY+(34-textSize("\u{F3B2}", "Ionicons", 14).height)/2)
      text(level, black, 14, 0x22362C, w-20-levelW+32, levelY+(34-levelSize.height)/2)
      let widths = names.map { textSize($0, bold, 13).width }
      var x = (w-widths.reduce(0,+)-56)/2
      for i in 0..<3 {
        let y = cy+s*0.9
        ctx.setFillColor(ui(i <= stage ? 0x2F9E62 : 0xC9DCD1).cgColor)
        ctx.fillEllipse(in: CGRect(x: x+widths[i]/2-5, y: y, width: 10, height: 10))
        text(names[i], bold, 13, i == stage ? 0x22362C : 0x8AA396, x, y+16)
        x += widths[i]+28
      }
      let cardY = h-bottom-164
      round(ctx, CGRect(x: 20, y: cardY, width: w-40, height: 64), 20, 0xFFFFFF)
      round(ctx, CGRect(x: 34, y: cardY+14, width: 36, height: 36), 12, 0xFFF2D6)
      let flash = "\u{F316}", flashSize = textSize(flash, "Ionicons", 18)
      text(flash, "Ionicons", 18, 0xF2A516, 52-flashSize.width/2, cardY+32-flashSize.height/2)
      let titleH = textSize("Evolution energy", black, 14).height
      text("Evolution energy", black, 14, 0x22362C, 82, cardY+(64-titleH-12)/2)
      let num = textSize("120/120", black, 14)
      round(ctx, CGRect(x: 82, y: cardY+(64-titleH-12)/2+titleH+6, width: w-34-num.width-12-82, height: 6), 3, 0xF2B63A)
      text("120/120", black, 14, 0x8A6A1E, w-34-num.width, cardY+(64-num.height)/2)
    }
  }
  func button(_ size: CGSize, bottom: Double, stage: Int, charge: Double, hold: Bool, context: GraphicsContext) {
    let y = size.height-bottom-80, w = size.width-40
    var c = context
    if hold {
      c.translateBy(x: size.width/2, y: y+30); c.scaleBy(x: 0.97, y: 0.97)
      c.translateBy(x: -size.width/2, y: -y-30)
    }
    let rect = CGRect(x: 20, y: y, width: w, height: 60)
    c.clip(to: Path(roundedRect: rect, cornerRadius: 30))
    c.fill(Path(rect), with: .color(Self.color(0xFFE29A)))
    c.fill(Path(CGRect(x: 20, y: y, width: w*charge, height: 56)), with: .color(Self.color(0xFFC93D)))
    c.fill(Path(CGRect(x: 20, y: y+56, width: w, height: 4)), with: .color(Self.color(0xE8B94A)))
    let copy = cached("button-\(stage)-\(bottom)", size) { _ in
      let label = EvolveHoldMorphMotion.cta[stage], icon = "\u{F58C}"
      let ts = textSize(label, "Nunito-Black", 18), isz = textSize(icon, "Ionicons", 18)
      let scale = UIScreen.main.scale
      // Yoga floors the local position of directly centered text nodes.
      let left = floor((size.width-ts.width-18-8)/2*scale+0.00001)/scale
      text(icon, "Ionicons", 18, 0x3B2A06, left, y+(56-isz.height)/2)
      text(label, "Nunito-Black", 18, 0x3B2A06, left+26, y+(56-ts.height)/2)
    }
    c.draw(copy, in: CGRect(origin: .zero, size: size))
  }
  func effects(_ f: EvolveHoldMorphFrame, _ size: CGSize, _ context: GraphicsContext) {
    let w = size.width, cy = (size.height*0.43).rounded(), s = (w*0.42).rounded()
    if f.c > 0.001 {
      let r = s*0.82
      var ring = Path()
      ring.addArc(center: CGPoint(x: w/2, y: cy-4), radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90+360*f.c), clockwise: false)
      var c = context; c.opacity = 0.25+0.75*EvolveHoldMorphMotion.clamp(f.c*3)
      c.stroke(ring, with: .color(Self.color(0xFFB53B)), style: StrokeStyle(lineWidth: 6, lineCap: .round))
    }
    if f.burst >= 0 && f.burst < 0.9 {
      let k = f.burst/0.9
      var c = context; c.opacity = 1-k
      for i in 0..<14 {
        let a = Double(i)/14 * .pi*2+0.3, d = s*(0.45+0.75*(1-pow(1-k,3)))
        let r = (i % 3 == 0 ? 7.0 : 4.5)*(1-k*0.6)
        c.fill(Path(ellipseIn: CGRect(x: w/2+cos(a)*d-r, y: cy-4+sin(a)*d*0.9-r, width: r*2, height: r*2)), with: .color(Self.color(i % 2 == 1 ? 0xFFD34D : 0xFF8FB1)))
      }
    }
  }
}
