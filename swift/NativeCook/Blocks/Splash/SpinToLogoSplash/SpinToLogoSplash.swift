import QuartzCore
import SwiftUI
import UIKit

/// An onboarding splash for an invented money app, Arca, told as a 14-second film around its 3D mark: three nested
/// arches and a terracotta pearl stand on warm paper and are given a full turn; a track on the floor fills, the circle
/// closes, the arches fold flat into the brand mark, the flat logo takes over with a gleam and rises, "arca" sets letter
/// by letter and the welcome screen fades up.
///
/// Ported from the React Native block (react-native-filament + Skia): the same arca.glb, converted to USDZ and drawn
/// with SceneKit (SpinToLogoSplashScene3D), the same film (SpinToLogoSplashMotion), the Skia floor and mark as SwiftUI
/// Canvases projected with the same camera. Touch: drag sideways to turn the sculpture (0.45 deg per pt, momentum, a
/// light haptic every 45 deg); a full turn completes it; Skip completes it at once. 2 s after the last touch the film
/// resumes. Reduce Motion: the welcome screen with the flat mark, still.
struct SpinToLogoSplash: View {
  typealias M = SpinToLogoSplashMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop
  static let gutter: CGFloat = 24

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: SpinToLogoSplashController?

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      ZStack(alignment: .topLeading) {
        SpinToLogoSplashBackdrop()
        if let c = controller {
          let compact = H < 760
          let finalBase = insets.top + (compact ? 96 : 132)
          let mark = SpinToLogoSplashMark.build(Double(W), Double(H))
          TimelineView(.animation(paused: c.reduced)) { _ in
            SpinToLogoSplashFloor(frame: c.frame(at: CACurrentMediaTime()))
          }
          TimelineView(.animation(paused: c.reduced)) { _ in
            SpinToLogoSplashStage3D(controller: c)
              .opacity(M.clamp01(M.stageVisible(c.frame(at: CACurrentMediaTime()).t)))
          }
          .frame(width: W, height: H)
          .allowsHitTesting(false)
          TimelineView(.animation(paused: c.reduced)) { _ in
            SpinToLogoSplashMarkLayer(frame: c.frame(at: CACurrentMediaTime()), mark: mark, finalBase: Double(finalBase))
          }
          .allowsHitTesting(false)
          Color.clear
            .contentShape(Rectangle())
            .frame(width: W, height: H)
            .gesture(pan(c))
            .accessibilityLabel("Arca sculpture")
            .accessibilityHint("Drag sideways to turn it")
          TimelineView(.animation(paused: c.reduced)) { _ in
            SpinToLogoSplashOverlay(controller: c, frame: c.frame(at: CACurrentMediaTime()), width: W, height: H,
                                    top: insets.top, bottom: insets.bottom, compact: compact, finalBase: finalBase)
          }
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.paperBottom).ignoresSafeArea())
    .preferredColorScheme(.light)
    .onAppear {
      if controller == nil { controller = SpinToLogoSplashController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  @State private var panning = false

  private func pan(_ c: SpinToLogoSplashController) -> some Gesture {
    DragGesture(minimumDistance: 0)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !panning {
          panning = true
          c.touch(now)
        }
        if !c.dragging && (abs(v.translation.width) >= 4 || abs(v.translation.height) >= 4) {
          c.dragBegan(now)
          c.dragChanged(now, dx: 0)
          dragOrigin = v.translation.width
        }
        if c.dragging { c.dragChanged(now, dx: v.translation.width - dragOrigin) }
      }
      .onEnded { v in
        panning = false
        c.dragEnded(CACurrentMediaTime(), vx: v.velocity.width)
      }
  }

  @State private var dragOrigin: CGFloat = 0

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

/// The warm paper: a vertical gradient, a light pool behind the sculpture and a faint vignette (drawn once).
struct SpinToLogoSplashBackdrop: View {
  typealias M = SpinToLogoSplashMotion
  var body: some View {
    Canvas { ctx, size in
      let w = size.width, h = size.height
      let r = CGRect(origin: .zero, size: size)
      ctx.fill(Path(r), with: .linearGradient(Gradient(stops: [
        .init(color: SpinToLogoSplash.color(M.paperTop), location: 0),
        .init(color: SpinToLogoSplash.color(M.paperMid), location: 0.55),
        .init(color: SpinToLogoSplash.color(M.paperBottom), location: 1),
      ]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
      ctx.fill(Path(r), with: .radialGradient(Gradient(colors: [
        Color(.sRGB, red: 1, green: 253 / 255, blue: 249 / 255, opacity: 0.75),
        Color(.sRGB, red: 1, green: 253 / 255, blue: 249 / 255, opacity: 0),
      ]), center: CGPoint(x: w * 0.5, y: h * 0.4), startRadius: 0, endRadius: w * 0.85))
      ctx.fill(Path(r), with: .radialGradient(Gradient(stops: [
        .init(color: Color(.sRGB, red: 60 / 255, green: 48 / 255, blue: 36 / 255, opacity: 0), location: 0.6),
        .init(color: Color(.sRGB, red: 60 / 255, green: 48 / 255, blue: 36 / 255, opacity: 0.07), location: 1),
      ]), center: CGPoint(x: w * 0.5, y: h * 0.45), startRadius: 0, endRadius: max(w, h) * 0.75))
    }
    .allowsHitTesting(false)
  }
}

/// Under the 3D view: the sculpture's floor shadows (one soft spot under the cage, a tight one under each foot, one
/// under the pearl that firms up as it drops) and the progress track on the floor, projected with the stage camera.
struct SpinToLogoSplashFloor: View {
  typealias M = SpinToLogoSplashMotion
  let frame: SpinToLogoSplashController.Frame
  static let steps = 96

  var body: some View {
    Canvas { ctx, size in
      let w = Double(size.width), h = Double(size.height)
      let tt = frame.t, yw = frame.yaw
      let cam = M.cameraAt(tt)
      let b = M.basis(cam, width: w)
      func p(_ x: Double, _ y: Double, _ z: Double) -> CGPoint {
        let q = M.project(cam, b, w, h, M.V3(x, y, z))
        return CGPoint(x: q.x, y: q.y)
      }
      let ui = M.clamp01(M.posterUi(tt))
      let vis = M.clamp01(M.stageVisible(tt))
      let fill = M.trackFill(tt, yw)
      let pop = M.trackPop(tt)

      if vis > 0.01 {
        let o = p(0, 0, 0)
        let rx = abs(p(1.1, 0, 0).x - o.x), ry = abs(p(0, 0, 1.1).y - o.y)
        Self.shadow(ctx, o.x, o.y, rx * 1.05, ry * 1.05, 0.1 * vis)
        let base = yw * Double.pi / 180
        for i in 0..<M.arches.count {
          let ang = base + M.archYaw(tt, i) * Double.pi / 180
          let ca = cos(ang), sa = sin(ang)
          for side in [-1.0, 1.0] {
            let r = M.arches[i].r * side
            let fx = r * ca, fz = -r * sa
            let q = p(fx, 0, fz)
            let sx = abs(p(fx + 0.17, 0, fz).x - q.x)
            let sy = abs(p(fx, 0, fz + 0.17).y - q.y)
            Self.shadow(ctx, q.x, q.y + sy * 0.15, sx, sy + 1.5, 0.42 * vis)
          }
        }
        let py = M.pearlY(tt)
        let k = M.clamp01((M.pearlFloat - py) / (M.pearlFloat - M.pearlR))
        let rr = M.pearlR * (1.9 - 0.9 * k)
        let sx = abs(p(rr, 0, 0).x - o.x), sy = abs(p(0, 0, rr).y - o.y)
        Self.shadow(ctx, o.x, o.y, sx, sy + 1, (0.14 + 0.36 * k) * vis)
      }

      guard ui > 0.01 else { return }
      let ink = SpinToLogoSplash.color(M.ink)
      var ring = Path()
      for s in 0...Self.steps {
        let a = Double(s) / Double(Self.steps) * Double.pi * 2
        let q = p(M.trackR * sin(a), 0, M.trackR * cos(a))
        if s == 0 { ring.move(to: q) } else { ring.addLine(to: q) }
      }
      ctx.stroke(ring, with: .color(SpinToLogoSplash.color(M.ink, 0.13 * ui)), lineWidth: 1.25)
      var ticks = Path()
      for k in 0..<12 {
        let a = Double(k) / 12 * Double.pi * 2
        ticks.move(to: p((M.trackR + 0.03) * sin(a), 0, (M.trackR + 0.03) * cos(a)))
        ticks.addLine(to: p((M.trackR + 0.07) * sin(a), 0, (M.trackR + 0.07) * cos(a)))
      }
      ctx.stroke(ticks, with: .color(SpinToLogoSplash.color(M.ink, 0.2 * ui)), lineWidth: 1.25)
      if fill > 0.002 {
        let dir: Double = yw < 0 ? -1 : 1
        var arc = Path()
        let n = max(2, Int(ceil(Double(Self.steps) * fill)))
        for s in 0...n {
          let a = dir * Double(s) / Double(n) * fill * Double.pi * 2
          let q = p(M.trackR * sin(a), 0, M.trackR * cos(a))
          if s == 0 { arc.move(to: q) } else { arc.addLine(to: q) }
        }
        ctx.stroke(arc, with: .color(ink.opacity(ui)),
                   style: StrokeStyle(lineWidth: 3 + 2 * pop, lineCap: .round, lineJoin: .round))
        let a = dir * fill * Double.pi * 2
        let q = p(M.trackR * sin(a), 0, M.trackR * cos(a))
        let r1 = 7 + 3 * pop, r2 = 4.5 + 2 * pop
        ctx.fill(Path(ellipseIn: CGRect(x: q.x - r1, y: q.y - r1, width: 2 * r1, height: 2 * r1)),
                 with: .color(SpinToLogoSplash.color(M.paperTop, ui)))
        ctx.fill(Path(ellipseIn: CGRect(x: q.x - r2, y: q.y - r2, width: 2 * r2, height: 2 * r2)), with: .color(ink.opacity(ui)))
      }
      if pop > 0.01 {
        let u = M.seg(tt, M.turnEnd - 0.05, M.turnEnd + 0.4)
        let rr = M.trackR + 0.12 * u
        var pr = Path()
        for s in 0...Self.steps {
          let a = Double(s) / Double(Self.steps) * Double.pi * 2
          let q = p(rr * sin(a), 0, rr * cos(a))
          if s == 0 { pr.move(to: q) } else { pr.addLine(to: q) }
        }
        ctx.stroke(pr, with: .color(SpinToLogoSplash.color(M.ink, 0.3 * (1 - u) * ui)), lineWidth: 2)
      }
    }
    .allowsHitTesting(false)
  }

  /// A soft elliptical shadow: a radial gradient squashed vertically.
  static func shadow(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ rx: Double, _ ry: Double, _ alpha: Double) {
    if alpha < 0.004 || rx < 0.5 { return }
    var g = ctx
    g.translateBy(x: x, y: y)
    g.scaleBy(x: 1, y: max(ry, 0.3) / rx)
    let c = { (a: Double) in Color(.sRGB, red: 40 / 255, green: 32 / 255, blue: 24 / 255, opacity: a) }
    g.fill(Path(ellipseIn: CGRect(x: -rx, y: -rx, width: 2 * rx, height: 2 * rx)),
           with: .radialGradient(Gradient(stops: [.init(color: c(alpha), location: 0), .init(color: c(alpha * 0.45), location: 0.45),
                                                  .init(color: c(0), location: 1)]),
                                 center: .zero, startRadius: 0, endRadius: rx))
  }
}

/// The flat mark, traced from the 3D arches through the stage camera at the hand-off, so it lands on them exactly.
struct SpinToLogoSplashMark {
  typealias M = SpinToLogoSplashMotion
  /// Where the mark lands on the welcome screen: the outer arch's radius (pt).
  static let finalR = 40.0
  var paths: [Path]
  var union: Path
  var cx: Double, cy: Double, r: Double
  var left: Double, right: Double, top: Double

  static func build(_ w: Double, _ h: Double) -> SpinToLogoSplashMark {
    let cam = M.handoffCamera
    let b = M.basis(cam, width: w)
    func p(_ x: Double, _ y: Double, _ z: Double) -> M.V3 {
      let q = M.project(cam, b, w, h, M.V3(x, y, z))
      return M.V3(q.x, q.y, q.z)
    }
    var paths: [Path] = []
    var union = Path()
    let n = 72
    var left = w, right = 0.0, top = h
    for arch in M.arches {
      let z = arch.b * 0.35
      var path = Path()
      for s in 0...n {
        let th = Double(s) / Double(n) * Double.pi
        let q = p((arch.r + arch.a) * cos(th), (arch.r + arch.a) * sin(th), z)
        if s == 0 { path.move(to: CGPoint(x: q.x, y: q.y)) } else { path.addLine(to: CGPoint(x: q.x, y: q.y)) }
        left = min(left, q.x); right = max(right, q.x); top = min(top, q.y)
      }
      for s in stride(from: n, through: 0, by: -1) {
        let th = Double(s) / Double(n) * Double.pi
        let q = p((arch.r - arch.a) * cos(th), (arch.r - arch.a) * sin(th), z)
        path.addLine(to: CGPoint(x: q.x, y: q.y))
      }
      path.closeSubpath()
      paths.append(path)
      union.addPath(path)
    }
    let pc = p(0, M.pearlR, 0)
    let pr = M.pearlR / pc.z * b.focal
    let disc = Path(ellipseIn: CGRect(x: pc.x - pr, y: pc.y - pr, width: 2 * pr, height: 2 * pr))
    paths.append(disc)
    union.addPath(disc)
    let c = p(0, 0, M.arches[0].b * 0.35)
    let e = p(M.arches[0].r + M.arches[0].a, 0, M.arches[0].b * 0.35)
    return SpinToLogoSplashMark(paths: paths, union: union, cx: c.x, cy: c.y, r: abs(e.x - c.x), left: left, right: right, top: top)
  }
}

/// Over the 3D view: the ghost finger of the film, and the flat mark that takes over from the sculpture, catches a
/// gleam and rises to its place on the welcome screen.
struct SpinToLogoSplashMarkLayer: View {
  typealias M = SpinToLogoSplashMotion
  let frame: SpinToLogoSplashController.Frame
  let mark: SpinToLogoSplashMark
  let finalBase: Double

  var body: some View {
    Canvas { ctx, size in
      let w = Double(size.width), h = Double(size.height)
      let tt = frame.t
      let vis = M.markVisible(tt)
      let f = M.filmFinger(tt)
      let fv = f.vis * M.clamp01(frame.live)
      let g = M.markGleam(tt)
      let k = M.clamp01(M.markRise(tt))
      if vis > 0.004 {
        let s = M.lerp(1, SpinToLogoSplashMark.finalR / mark.r, k)
        let x = M.lerp(mark.cx, w / 2, k)
        let y = M.lerp(mark.cy, finalBase, k)
        var m = ctx
        m.translateBy(x: x, y: y)
        m.scaleBy(x: s, y: s)
        m.translateBy(x: -mark.cx, y: -mark.cy)
        for i in 0..<mark.paths.count {
          m.fill(mark.paths[i], with: .color(SpinToLogoSplash.color(M.markColors[i], vis)))
        }
        if g.k > 0.01 {
          var gl = m
          gl.clip(to: mark.union)
          gl.blendMode = .screen
          let gx = M.lerp(mark.left, mark.right, g.x)
          let span = (mark.right - mark.left) * 0.22
          let c = { (a: Double) in Color(.sRGB, red: 1, green: 250 / 255, blue: 240 / 255, opacity: a) }
          gl.fill(Path(CGRect(x: mark.left - 4, y: mark.top - 4, width: mark.right - mark.left + 8, height: mark.cy - mark.top + 8)),
                  with: .linearGradient(Gradient(colors: [c(0), c(0.55 * g.k * vis), c(0)]),
                                        startPoint: CGPoint(x: gx - span, y: mark.top), endPoint: CGPoint(x: gx + span, y: mark.cy)))
        }
      }
      if fv > 0.004 {
        let cam = M.cameraAt(0)
        let a = M.project(cam, M.basis(cam, width: w), w, h, M.V3(0, 0.2, 0.6))
        let fx = w / 2 + f.x / M.yawPerPt
        let fy = a.y + 6
        let lift = 1 - f.press
        let r = 22 * (1 + 0.12 * lift)
        let sc = CGPoint(x: fx, y: fy + 3 + 4 * lift)
        let shade = { (o: Double) in Color(.sRGB, red: 40 / 255, green: 32 / 255, blue: 24 / 255, opacity: o) }
        ctx.fill(Path(ellipseIn: CGRect(x: sc.x - r * 1.5, y: sc.y - r * 1.5, width: r * 3, height: r * 3)),
                 with: .radialGradient(Gradient(stops: [.init(color: shade(0.16 * fv), location: 0.4), .init(color: shade(0), location: 1)]),
                                       center: sc, startRadius: 0, endRadius: r * 1.5))
        let body = Path(ellipseIn: CGRect(x: fx - r, y: fy - r, width: 2 * r, height: 2 * r))
        ctx.fill(body, with: .color(.white.opacity(0.62 * fv)))
        let ri = r * 0.62
        ctx.fill(Path(ellipseIn: CGRect(x: fx - ri, y: fy - ri, width: 2 * ri, height: 2 * ri)),
                 with: .color(SpinToLogoSplash.color(M.ink, 0.1 * fv)))
        ctx.stroke(body, with: .color(SpinToLogoSplash.color(M.ink, 0.14 * fv)), lineWidth: 1)
      }
    }
    .allowsHitTesting(false)
  }
}

/// The poster's words (title, hint, degree counter, Skip) and the welcome screen the mark hands over to.
struct SpinToLogoSplashOverlay: View {
  typealias M = SpinToLogoSplashMotion
  let controller: SpinToLogoSplashController
  let frame: SpinToLogoSplashController.Frame
  let width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat
  let compact: Bool
  let finalBase: CGFloat

  private var ink: Color { SpinToLogoSplash.color(M.ink) }
  private var mute: Color { SpinToLogoSplash.color(M.mute) }

  var body: some View {
    let t = frame.t
    let poster = M.clamp01(M.posterUi(t))
    let g = SpinToLogoSplash.gutter
    let cam = M.cameraAt(0)
    let trackFront = M.project(cam, M.basis(cam, width: Double(width)), Double(width), Double(height), M.V3(0, 0, M.trackR)).y
    ZStack(alignment: .topLeading) {
      VStack(alignment: .leading, spacing: 0) {
        title("Give it a full turn.")
        bodyText("Drag the sculpture sideways. Arca opens when the circle closes.")
      }
      .frame(width: width - 2 * g, alignment: .leading)
      .offset(x: g, y: top + (compact ? 20 : 36) + (1 - poster) * -8)
      .opacity(poster)
      .allowsHitTesting(false)

      Text("\(Int((M.trackFill(t, frame.yaw) * 360).rounded()))°")
        .font(.system(size: 15, weight: .semibold)).monospacedDigit()
        .foregroundStyle(ink)
        .frame(width: width)
        .offset(y: trackFront + (compact ? 14 : 22))
        .opacity(poster)
        .allowsHitTesting(false)

      // the wordmark under the mark
      HStack(spacing: 0) {
        ForEach(Array("arca".enumerated()), id: \.offset) { i, ch in
          let k = M.wordLetter(t, i)
          Text(String(ch))
            .font(.system(size: 40, weight: .heavy)).kerning(-1)
            .foregroundStyle(ink)
            .opacity(k)
            .offset(y: (1 - k) * 14)
        }
      }
      .frame(width: width)
      .offset(y: finalBase + 10)
      .allowsHitTesting(false)

      VStack(spacing: 0) {
        Spacer(minLength: 0)
        Button { controller.skip(CACurrentMediaTime()) } label: {
          Text("Skip").font(.system(size: 17, weight: .semibold)).foregroundStyle(mute)
            .padding(.vertical, 10).padding(.horizontal, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .opacity(poster)
        .allowsHitTesting(poster > 0.5)
      }
      .frame(width: width, height: height - bottom - 12)

      welcome(t)
        .frame(width: width - 2 * g, height: height - bottom - 12, alignment: .bottomLeading)
        .offset(x: g)
        .allowsHitTesting(M.welcomeItem(t, 2) > 0.5)
    }
    .frame(width: width, height: height, alignment: .topLeading)
  }

  private func title(_ s: String) -> some View {
    Text(s).font(.system(size: compact ? 30 : 34, weight: .bold)).kerning(-0.6)
      .foregroundStyle(ink)
      .lineSpacing(-1)
      .fixedSize(horizontal: false, vertical: true)
  }

  private func bodyText(_ s: String) -> some View {
    Text(s).font(.system(size: 17)).foregroundStyle(mute)
      .lineSpacing(2.7)
      .frame(maxWidth: 340, alignment: .leading)
      .fixedSize(horizontal: false, vertical: true)
      .padding(.top, 10)
  }

  private func item<V: View>(_ j: Int, _ t: Double, @ViewBuilder _ content: () -> V) -> some View {
    let k = M.welcomeItem(t, j)
    return content().opacity(k).offset(y: (1 - k) * 18)
  }

  private func welcome(_ t: Double) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      item(0, t) { title("Your money,\nin order.") }
      item(1, t) { bodyText("Accounts, bills and savings goals in one calm place. Set up takes two minutes.") }
        .padding(.top, 2)
      item(2, t) {
        Button {
          UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        } label: {
          Text("Get started").font(.system(size: 17, weight: .semibold))
            .foregroundStyle(SpinToLogoSplash.color(M.paperTop))
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Capsule().fill(ink))
        }
        .buttonStyle(SpinToLogoSplashPress(scale: 0.96))
      }
      .padding(.top, compact ? 22 : 32)
      item(3, t) {
        Button {
          UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
          Text("I already have an account").font(.system(size: 17, weight: .semibold))
            .foregroundStyle(ink)
            .frame(maxWidth: .infinity, minHeight: 48)
            .contentShape(Capsule())
        }
        .buttonStyle(SpinToLogoSplashPress(scale: 0.98))
      }
      .padding(.top, 8)
    }
  }
}

/// PressScale: settles to the scale in 150 ms on a strong ease-out and returns on the same curve.
struct SpinToLogoSplashPress: ButtonStyle {
  var scale: CGFloat
  @Environment(\.accessibilityReduceMotion) private var reduce
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .scaleEffect(configuration.isPressed && !reduce ? scale : 1)
      .opacity(reduce && configuration.isPressed ? 0.72 : 1)
      .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
  }
}
