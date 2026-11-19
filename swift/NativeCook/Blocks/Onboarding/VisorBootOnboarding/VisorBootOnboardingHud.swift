import CoreText
import SwiftUI
import UIKit

/// The three 2D modules that live on the helmet, drawn over the 3D view and projected with the stage's own camera
/// (React Native's VisorHud): the visor HUD (a perspective-correct quad on the visor plate, every point mapped
/// through the homography), the radar round the ear sensor, and the voice module (rings at the mouthpiece and a
/// waveform under the helmet, with the transcript under it as VisorVoiceCaption does).
enum VisorBootOnboardingHud {
  typealias M = VisorBootOnboardingMotion

  static let amberRGB = (r: 255.0 / 255, g: 194.0 / 255, b: 122.0 / 255)
  static let readoutL = "38.4 m"
  static let readoutR = "640 lx"
  static let bars = 30
  static let blips: [(Double, Double)] = [(0.9, 0.62), (2.7, 0.38), (4.5, 0.78)]
  static let transcript = "\u{201C}Mark this trail for later.\u{201D}"

  static func amber(_ a: Double) -> Color {
    Color(.sRGB, red: amberRGB.r, green: amberRGB.g, blue: amberRGB.b, opacity: M.clamp01(a))
  }

  // MARK: the bundled IBM Plex Mono

  static let fontName: String = {
    if let url = Bundle.main.url(forResource: "VisorBootOnboarding-IBMPlexMono-Medium", withExtension: "ttf") {
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      if let provider = CGDataProvider(url: url as CFURL), let f = CGFont(provider), let n = f.postScriptName {
        return n as String
      }
    }
    return "Menlo-Regular"
  }()

  static func mono(_ size: CGFloat) -> Font { .custom(fontName, fixedSize: size) }

  /// Where the baseline sits in a line of the mono font, as a share of the line's height from the top.
  static func baselineShare(_ size: CGFloat) -> CGFloat {
    let f = UIFont(name: fontName, size: size) ?? UIFont.monospacedSystemFont(ofSize: size, weight: .medium)
    return f.ascender / (f.ascender - f.descender)
  }

  /// IBM Plex Mono advances 600/1000 em per glyph.
  static func monoWidth(_ s: String, _ size: Double) -> Double { Double(s.count) * 0.6 * size }

  // MARK: drawing

  static func draw(_ ctx: inout GraphicsContext, size: CGSize, state s: VisorBootOnboardingState, limitY: Double) {
    let w = Double(size.width), h = Double(size.height)
    let c = M.cameraAt(s)
    let b = M.basis(c, width: w)
    let lift = s.lift
    let hudVis = M.blend(M.filmHud(s.t), M.pageWeight(s.page, 1), s.m) * M.hudFacing(c, helmetY: M.helmetY + lift)
    let boot = M.blend(M.filmBoot(s.t), 1, s.m)
    let radarVis = M.blend(M.filmRadar(s.t), M.pageWeight(s.page, 2), s.m)
    let voiceVis = M.blend(M.filmVoice(s.t), M.pageWeight(s.page, 3), s.m)
    let run = s.run

    func proj(_ p: M.V3) -> (x: Double, y: Double, z: Double)? { M.project(c, b, w, h, p) }

    // ---- HUD on the visor
    if hudVis > 0.004 {
      var q: [Double] = []
      var ok = true
      for k in 0..<4 {
        if let o = proj(M.helmetPoint(M.hudQuad[k], lift: lift)) { q += [o.x, o.y] } else { ok = false; q += [0, 0] }
      }
      if ok {
        let gx = (q[0] + q[2] + q[4] + q[6]) / 4, gy = (q[1] + q[3] + q[5] + q[7]) / 4
        let gr = max(1, hypot(q[2] - q[0], q[3] - q[1]) * 0.7)
        ctx.fill(Path(ellipseIn: CGRect(x: gx - gr, y: gy - gr, width: 2 * gr, height: 2 * gr)),
                 with: .radialGradient(Gradient(colors: [amber(0.16 * hudVis), amber(0)]),
                                       center: CGPoint(x: gx, y: gy), startRadius: 0, endRadius: gr))
        drawHud(&ctx, Homography(M.rectToQuad(q, M.hudW, M.hudH)), vis: hudVis, boot: boot, run: run)
      }
    }

    // ---- radar round the ear sensor
    if radarVis > 0.004 {
      let grow = 0.72 + 0.28 * M.easeOutCubic(radarVis)
      let cw = M.helmetPoint(M.portCenter, lift: lift)
      func rp(_ ang: Double, _ r: Double) -> M.V3 { cw + M.portAxisA * (cos(ang) * r) + M.portAxisB * (sin(ang) * r) }
      for ring in 1...3 {
        let r = M.radarR * Double(ring) * grow / 3
        var path = Path()
        var first = true
        for k in 0...64 {
          guard let o = proj(rp(Double(k) / 64 * 2 * Double.pi, r)) else { continue }
          if first { path.move(to: CGPoint(x: o.x, y: o.y)) } else { path.addLine(to: CGPoint(x: o.x, y: o.y)) }
          first = false
        }
        ctx.stroke(path, with: .color(amber((ring == 3 ? 0.55 : 0.28) * radarVis)), lineWidth: 1)
      }
      var ticks = Path()
      for k in 0..<12 {
        let ang = Double(k) / 12 * 2 * Double.pi
        if let o = proj(rp(ang, M.radarR * grow)), let o2 = proj(rp(ang, M.radarR * grow * 1.07)) {
          ticks.move(to: CGPoint(x: o.x, y: o.y))
          ticks.addLine(to: CGPoint(x: o2.x, y: o2.y))
        }
      }
      ctx.stroke(ticks, with: .color(amber(0.5 * radarVis)), lineWidth: 1)
      let a = M.radarAngle(run)
      let R = M.radarR * grow
      let oc = proj(cw)
      if let oc {
        let sl = 18
        for k in 0..<sl {
          let a0 = a - 1.1 * (1 - Double(k) / Double(sl)), a1 = a - 1.1 * (1 - Double(k + 1) / Double(sl))
          guard let o1 = proj(rp(a0, R)), let o2 = proj(rp(a1, R)) else { continue }
          var p = Path()
          p.move(to: CGPoint(x: oc.x, y: oc.y))
          p.addLine(to: CGPoint(x: o1.x, y: o1.y))
          p.addLine(to: CGPoint(x: o2.x, y: o2.y))
          p.closeSubpath()
          ctx.fill(p, with: .color(amber(0.22 * pow(Double(k + 1) / Double(sl), 2) * radarVis)))
        }
        if let o = proj(rp(a, R)) {
          var p = Path()
          p.move(to: CGPoint(x: oc.x, y: oc.y))
          p.addLine(to: CGPoint(x: o.x, y: o.y))
          ctx.stroke(p, with: .color(amber(0.9 * radarVis)), lineWidth: 1.4)
        }
      }
      for (ang, rs) in blips {
        var since = (a - ang).truncatingRemainder(dividingBy: 2 * Double.pi)
        if since < 0 { since += 2 * Double.pi }
        let age = since / (2 * Double.pi) * 2.1
        let glow = exp(-age * 2.2)
        guard let o = proj(rp(ang, R * rs)) else { continue }
        let rad = max(1.5, 0.035 * b.focal / o.z)
        ctx.fill(Path(ellipseIn: CGRect(x: o.x - rad, y: o.y - rad, width: 2 * rad, height: 2 * rad)),
                 with: .color(amber((0.25 + 0.75 * glow) * radarVis)))
        let r2 = rad * 3.2
        ctx.fill(Path(ellipseIn: CGRect(x: o.x - r2, y: o.y - r2, width: 2 * r2, height: 2 * r2)),
                 with: .color(amber(0.25 * glow * radarVis)))
      }
      if let o = proj(rp(Double.pi * 0.25, R * 1.12)) {
        text(&ctx, "3 nearby", size: 11, x: o.x + 6, baseline: o.y - 4, color: amber(0.85 * radarVis))
        text(&ctx, "closest 11.6 m", size: 11, x: o.x + 6, baseline: o.y + 10, color: amber(0.6 * radarVis))
      }
    }

    // ---- voice module at the mouthpiece
    if voiceVis > 0.004 {
      let mw = M.helmetPoint(M.mouth, lift: lift)
      let om = proj(mw)
      for k in 0..<3 {
        let ph = (run / 1.6 + Double(k) / 3).truncatingRemainder(dividingBy: 1)
        let r = 0.1 + 0.55 * ph
        var path = Path()
        var first = true
        for sidx in 0...40 {
          let ang = Double(sidx) / 40 * 2 * Double.pi
          let pt = mw + M.mouthAxisA * (cos(ang) * r) + M.mouthAxisB * (sin(ang) * r)
          guard let o = proj(pt) else { continue }
          if first { path.move(to: CGPoint(x: o.x, y: o.y)) } else { path.addLine(to: CGPoint(x: o.x, y: o.y)) }
          first = false
        }
        ctx.stroke(path, with: .color(amber(0.6 * (1 - ph) * voiceVis)), lineWidth: 1.2)
      }
      if let om {
        let ax = om.x, ay = om.y
        let wy = M.voiceWaveY(ay, h, limitY)
        let bw = 3.0, gap = 3.2
        let total = Double(bars) * bw + Double(bars - 1) * gap
        let x0 = w / 2 - total / 2
        let rise = M.easeOutCubic(voiceVis)
        var lead = Path()
        lead.move(to: CGPoint(x: ax, y: ay + 6))
        lead.addLine(to: CGPoint(x: ax, y: ay + 6 + (wy - 26 - ay - 6) * rise))
        ctx.stroke(lead, with: .color(amber(0.45 * voiceVis)), lineWidth: 1)
        ctx.fill(Path(ellipseIn: CGRect(x: ax - 2.6, y: ay - 2.6, width: 5.2, height: 5.2)), with: .color(amber(0.9 * voiceVis)))
        for i in 0..<bars {
          let v = M.voiceBar(i, bars, run)
          let bh = 3 + 30 * v * rise
          let x = x0 + Double(i) * (bw + gap)
          ctx.fill(Path(roundedRect: CGRect(x: x, y: wy - bh / 2, width: bw, height: bh), cornerRadius: 1.5),
                   with: .color(amber((0.45 + 0.5 * v) * voiceVis)))
        }
      }
    }
  }

  /// Text in the mono font with its baseline at y (Skia's drawText).
  static func text(_ ctx: inout GraphicsContext, _ s: String, size: CGFloat, x: Double, baseline y: Double, color: Color) {
    let r = ctx.resolve(Text(s).font(mono(size)).foregroundColor(color))
    ctx.draw(r, at: CGPoint(x: x, y: y), anchor: UnitPoint(x: 0, y: baselineShare(size)))
  }

  // MARK: the HUD under the visor homography

  struct Homography {
    let m: [Double]
    init(_ m: [Double]) { self.m = m }
    func callAsFunction(_ x: Double, _ y: Double) -> CGPoint {
      let w = m[6] * x + m[7] * y + m[8]
      return CGPoint(x: (m[0] * x + m[1] * y + m[2]) / w, y: (m[3] * x + m[4] * y + m[5]) / w)
    }
    /// The affine map that matches the homography at (x, y) (its value and first derivatives).
    func local(_ x: Double, _ y: Double) -> CGAffineTransform {
      let w = m[6] * x + m[7] * y + m[8]
      let u = (m[0] * x + m[1] * y + m[2]) / w, v = (m[3] * x + m[4] * y + m[5]) / w
      let a = (m[0] - u * m[6]) / w, bb = (m[3] - v * m[6]) / w
      let c = (m[1] - u * m[7]) / w, d = (m[4] - v * m[7]) / w
      return CGAffineTransform(a: a, b: bb, c: c, d: d, tx: u - a * x - c * y, ty: v - bb * x - d * y)
    }
  }

  static func drawHud(_ ctx: inout GraphicsContext, _ H: Homography, vis: Double, boot: Double, run: Double) {
    let W = M.hudW, Hh = M.hudH
    func quad(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
      var p = Path()
      p.move(to: H(x, y)); p.addLine(to: H(x + w, y)); p.addLine(to: H(x + w, y + h)); p.addLine(to: H(x, y + h))
      p.closeSubpath()
      return p
    }
    func line(_ p: inout Path, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) {
      p.move(to: H(x0, y0)); p.addLine(to: H(x1, y1))
    }
    /// A stroke whose width follows the homography's local scale (Skia strokes in canonical units).
    func scale(_ x: Double, _ y: Double) -> Double {
      let t = H.local(x, y)
      return sqrt(abs(t.a * t.d - t.b * t.c))
    }
    let s0 = scale(W / 2, Hh / 2)

    ctx.fill(quad(0, 0, W, Hh), with: .color(amber(0.045 * vis)))
    // scanlines
    let scanA = (0.05 + 0.08 * (1 - M.seg(boot, 0.25, 0.45))) * vis
    var scan = Path()
    var y = 2.0
    while y < Hh { line(&scan, 0, y, W, y); y += 3 }
    ctx.stroke(scan, with: .color(amber(scanA)), lineWidth: 0.6 * s0)
    // the boot sweep
    let sweep = M.seg(boot, 0, 0.3)
    if sweep > 0 && sweep < 1 {
      let yy = -12 + sweep * (Hh + 24)
      for k in 0..<6 {
        ctx.fill(quad(0, yy - 12 + Double(k) * 2, W, 2), with: .color(amber((0.08 + 0.1 * Double(k)) * vis)))
      }
      ctx.fill(quad(0, yy, W, 1.2), with: .color(amber(0.9 * vis)))
    }
    // corner brackets
    let br = 14 * M.easeOutCubic(M.seg(boot, 0.1, 0.35))
    if br > 0.5 {
      let i = 4.0
      var p = Path()
      line(&p, i, i, i + br, i); line(&p, i, i, i, i + br * 0.7)
      line(&p, W - i, i, W - i - br, i); line(&p, W - i, i, W - i, i + br * 0.7)
      line(&p, i, Hh - i, i + br, Hh - i); line(&p, i, Hh - i, i, Hh - i - br * 0.7)
      line(&p, W - i, Hh - i, W - i - br, Hh - i); line(&p, W - i, Hh - i, W - i, Hh - i - br * 0.7)
      ctx.stroke(p, with: .color(amber(0.85 * vis)), style: StrokeStyle(lineWidth: 1.4 * s0, lineCap: .square))
    }
    // compass ribbon
    let reveal = M.easeOutCubic(M.seg(boot, 0.3, 0.55))
    let cx = W / 2
    if reveal > 0.01 {
      let half = 96 * reveal
      var clip = ctx
      clip.clip(to: quad(cx - half, 6, half * 2, 26))
      let hd = M.heading(run)
      let ppd = 2.6
      var deg = floor((hd - 42) / 5) * 5
      while deg <= hd + 42 {
        let x = cx + (deg - hd) * ppd
        let di = Int(deg)
        let major = ((di % 15) + 15) % 15 == 0
        let edge = 1 - abs(x - cx) / 100
        var p = Path()
        line(&p, x, 12, x, 12 + (major ? 6 : 3))
        clip.stroke(p, with: .color(amber((major ? 0.9 : 0.5) * vis * M.clamp01(edge * 1.6))), lineWidth: s0)
        let d360 = ((di % 360) + 360) % 360
        if d360 % 30 == 0 {
          let label = d360 == 0 ? "N" : d360 == 90 ? "E" : d360 == 180 ? "S" : d360 == 270 ? "W" : (d360 < 100 ? "0" : "") + "\(d360)"
          hudText(&clip, H, label, size: 8.5, x: x - monoWidth(label, 8.5) / 2, baseline: 28,
                  color: amber((major ? 0.9 : 0.5) * vis * M.clamp01(edge * 1.6)))
        }
        deg += 5
      }
      var caret = Path()
      caret.move(to: H(cx, 10)); caret.addLine(to: H(cx - 3.5, 5)); caret.addLine(to: H(cx + 3.5, 5)); caret.closeSubpath()
      ctx.fill(caret, with: .color(amber(0.95 * vis * reveal)))
    }
    // reticle
    let ret = M.easeOutCubic(M.seg(boot, 0.25, 0.5))
    if ret > 0.01 {
      let ry = 60.0
      let rr = 7 + 4 * (1 - ret)
      var circle = Path()
      for k in 0...40 {
        let a = Double(k) / 40 * 2 * Double.pi
        let pt = H(cx + cos(a) * rr, ry + sin(a) * rr)
        if k == 0 { circle.move(to: pt) } else { circle.addLine(to: pt) }
      }
      line(&circle, cx - rr - 9, ry, cx - rr - 3, ry)
      line(&circle, cx + rr + 3, ry, cx + rr + 9, ry)
      line(&circle, cx, ry + rr + 3, cx, ry + rr + 7)
      ctx.stroke(circle, with: .color(amber(0.7 * vis * ret)), lineWidth: s0)
      var horizon = Path()
      line(&horizon, cx - 70, ry, cx - 36, ry)
      line(&horizon, cx + 36, ry, cx + 70, ry)
      ctx.stroke(horizon, with: .color(amber(0.35 * vis * ret)), lineWidth: s0)
    }
    // two readouts typing in
    let kl = M.seg(boot, 0.55, 0.78), kr = M.seg(boot, 0.72, 0.95)
    if kl > 0 {
      let n = Int(ceil(kl * Double(readoutL.count)))
      let s = String(readoutL.prefix(n))
      hudText(&ctx, H, s, size: 13, x: 12, baseline: 100, color: amber(0.95 * vis))
      if kl < 1 { ctx.fill(quad(13 + monoWidth(s, 13), 90, 6, 11), with: .color(amber(0.95 * vis))) }
    }
    if kr > 0 {
      let x1 = W - 12
      let n = Int(ceil(kr * Double(readoutR.count)))
      let s = String(readoutR.prefix(n))
      let full = monoWidth(readoutR, 13)
      hudText(&ctx, H, s, size: 13, x: x1 - full, baseline: 100, color: amber(0.95 * vis))
      if kr < 1 { ctx.fill(quad(x1 - full + monoWidth(s, 13) + 1, 90, 6, 11), with: .color(amber(0.95 * vis))) }
    }
  }

  /// Text in canonical HUD units, under the homography's local affine map at the text's baseline origin.
  static func hudText(_ ctx: inout GraphicsContext, _ H: Homography, _ s: String, size: CGFloat, x: Double, baseline y: Double, color: Color) {
    if s.isEmpty { return }
    let midX = x + monoWidth(s, Double(size)) / 2
    var g = ctx
    g.concatenate(H.local(midX, y - Double(size) * 0.35))
    text(&g, s, size: size, x: x, baseline: y, color: color)
  }
}
