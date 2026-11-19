import QuartzCore
import SwiftUI
import UIKit

/// First-run onboarding for an AR headset app ("Halo"), told as an 18-second product film around a real 3D helmet on
/// a graphite plinth. Each of the three steps swings the camera to a new angle and boots a module on the helmet
/// itself: a perspective-correct HUD on the visor, a radar sweeping round the ear sensor, a voice waveform at the
/// mouthpiece. Then Get Started grows out of the pager's pill while the helmet lifts under a light sweep.
///
/// Ported from the React Native block (react-native-filament + Skia): the same visor.glb, converted to USDZ and drawn
/// with SceneKit (VisorBootOnboardingScene3D), the same film (VisorBootOnboardingMotion), the Skia layers as a Metal
/// backdrop and a SwiftUI Canvas. Touch: drag the helmet to orbit (velocity decay on yaw, rubber-banded pitch, a
/// light haptic every 45 deg); swipe the copy to page; the arrow, dots and Skip page too. Any touch blends the film
/// into the pages in 0.45 s; 2 s after the last touch it eases back to the poster in 1.4 s and the loop restarts.
/// Reduce Motion: the poster page, no loop; pages change with a 0.22 s ease.
struct VisorBootOnboarding: View {
  typealias M = VisorBootOnboardingMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: VisorBootOnboardingController?

  static let gutter: CGFloat = 24
  static let rowH: CGFloat = 56

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let size = CGSize(width: W, height: H)
      let compact = H < 760
      let copyHeight: CGFloat = compact ? 168 : 206
      let rowBottom = insets.bottom + 20
      let copyTop = H - (rowBottom + Self.rowH + 22 + copyHeight)
      ZStack(alignment: .topLeading) {
        Self.color(M.bgEdge)
        if let c = controller {
          TimelineView(.animation) { _ in
            VisorBootOnboardingBackdrop(state: c.state(at: CACurrentMediaTime()), size: size)
          }
          .frame(width: W, height: H)
          VisorBootOnboardingStage3D(controller: c, size: size)
            .frame(width: W, height: H)
            .allowsHitTesting(false)
          TimelineView(.animation) { _ in
            let s = c.state(at: CACurrentMediaTime())
            ZStack(alignment: .topLeading) {
              Canvas { ctx, sz in
                VisorBootOnboardingHud.draw(&ctx, size: sz, state: s, limitY: Double(copyTop))
              }
              .frame(width: W, height: H)
              caption(s, size: size, limitY: copyTop)
            }
            .frame(width: W, height: H, alignment: .topLeading)
          }
          .allowsHitTesting(false)
          Color.clear
            .contentShape(Rectangle())
            .frame(width: W, height: max(0, copyTop))
            .gesture(orbit(c))
            .accessibilityLabel("Helmet")
            .accessibilityHint("Drag to turn the helmet")
          Color.clear
            .contentShape(Rectangle())
            .frame(width: W, height: max(0, H - copyTop))
            .offset(y: copyTop)
            .gesture(pager(c, width: W))
          VisorBootOnboardingOverlay(controller: c, width: W, height: H, top: insets.top, bottom: rowBottom,
                                     compact: compact, copyHeight: copyHeight)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.bgEdge).ignoresSafeArea())
    .preferredColorScheme(.dark)
    .onAppear {
      if controller == nil { controller = VisorBootOnboardingController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  /// What Halo heard, typed in under the voice waveform (VisorVoiceCaption).
  @ViewBuilder
  private func caption(_ s: VisorBootOnboardingState, size: CGSize, limitY: CGFloat) -> some View {
    let vis = M.blend(M.filmVoice(s.t), M.pageWeight(s.page, 3), s.m)
    if vis > 0.004 {
      let w = Double(size.width), h = Double(size.height)
      let c = M.cameraAt(s)
      let b = M.basis(c, width: w)
      if let o = M.project(c, b, w, h, M.helmetPoint(M.mouth, lift: s.lift)) {
        let typed = M.blend(M.filmTranscript(s.t), 1, s.m)
        let n = Int(ceil(M.clamp01(typed) * Double(VisorBootOnboardingHud.transcript.count)))
        Text(String(VisorBootOnboardingHud.transcript.prefix(n)))
          .font(.system(size: 17, weight: .medium))
          .foregroundStyle(Self.color(M.ink))
          .lineLimit(1)
          .frame(width: size.width - 48, alignment: .center)
          .offset(x: 24, y: CGFloat(M.voiceWaveY(o.y, h, Double(limitY)) + 24))
          .opacity(M.clamp01(vis))
      }
    }
  }

  private func orbit(_ c: VisorBootOnboardingController) -> some Gesture {
    DragGesture(minimumDistance: 6)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !c.dragging { c.orbitBegan(now) }
        c.orbitChanged(now, dx: v.translation.width, dy: v.translation.height)
      }
      .onEnded { v in
        let dt = 0.25
        c.orbitEnded(CACurrentMediaTime(), vx: (v.predictedEndTranslation.width - v.translation.width) / dt,
                     vy: (v.predictedEndTranslation.height - v.translation.height) / dt)
      }
  }

  private func pager(_ c: VisorBootOnboardingController, width: CGFloat) -> some Gesture {
    DragGesture(minimumDistance: 10)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !c.dragging {
          // a mostly vertical move is not a page swipe
          if abs(v.translation.height) > abs(v.translation.width) { return }
          c.pagerBegan(now)
        }
        c.pagerChanged(now, dx: v.translation.width, width: width)
      }
      .onEnded { v in
        guard c.dragging else { return }
        let dt = 0.25
        c.pagerEnded(CACurrentMediaTime(), dx: v.translation.width,
                     vx: (v.predictedEndTranslation.width - v.translation.width) / dt, width: width)
      }
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

/// Header, copy, pager and the Get Started button that grows out of the pager's pill (VisorOverlay).
struct VisorBootOnboardingOverlay: View {
  typealias M = VisorBootOnboardingMotion
  let controller: VisorBootOnboardingController
  let width: CGFloat
  let height: CGFloat
  let top: CGFloat
  let bottom: CGFloat
  let compact: Bool
  let copyHeight: CGFloat

  static let copy: [(label: String, title: String, body: String)] = [
    ("", "Meet Halo", "Your visor shows what matters, right where you look. Setup takes about a minute."),
    ("Step 1 of 3", "See more at a glance", "Directions, distance and light appear on the visor only when you look up."),
    ("Step 2 of 3", "Stay aware", "Sensors on each side notice what\u{2019}s behind you and give one gentle pulse."),
    ("Step 3 of 3", "Hands-free", "Ask Halo anything. Answers appear on the visor, not out loud."),
    ("", "You\u{2019}re all set", "Next, a quick fit check. It takes about 40 seconds."),
  ]

  static let dot: CGFloat = 6
  static let dotStretch: CGFloat = 18
  static let dotGap: CGFloat = 8

  @State private var pressed = false

  var body: some View {
    let g = VisorBootOnboarding.gutter
    let rowW = min(width, 520) - g * 2
    let rowH = VisorBootOnboarding.rowH
    let rowY = height - (bottom + 20) - rowH
    let copyY = height - (bottom + 20 + rowH + 22) - copyHeight
    ZStack(alignment: .topLeading) {
      // header
      HStack {
        Text("Halo").font(.system(size: 17, weight: .bold)).foregroundStyle(VisorBootOnboarding.color(M.ink))
        Spacer(minLength: 0)
        Button {
          UIImpactFeedbackGenerator(style: .light).impactOccurred()
          controller.skip(now: CACurrentMediaTime())
        } label: {
          Text("Skip").font(.system(size: 17)).foregroundStyle(VisorBootOnboarding.color(M.mute))
            .padding(14).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(-14)
      }
      .frame(width: width - g * 2, height: 32)
      .offset(x: g, y: top + 10)

      TimelineView(.animation) { _ in
        let s = controller.state(at: CACurrentMediaTime())
        ZStack(alignment: .topLeading) {
          // copy
          ZStack(alignment: .bottomLeading) {
            ForEach(0..<Self.copy.count, id: \.self) { i in copyBlock(i, s) }
          }
          .frame(width: rowW, height: copyHeight, alignment: .bottomLeading)
          .offset(x: g, y: copyY)
          .allowsHitTesting(false)
          // pager row
          row(s, rowW: rowW)
            .frame(width: rowW, height: rowH, alignment: .topLeading)
            .offset(x: g, y: rowY)
        }
        .frame(width: width, height: height, alignment: .topLeading)
      }
    }
    .frame(width: width, height: height, alignment: .topLeading)
  }

  private func copyBlock(_ i: Int, _ s: VisorBootOnboardingState) -> some View {
    let a = M.filmCopy(i, s.t), b = M.pageCopy(i, s.page)
    let o = M.blend(a.0, b.0, s.m), dy = M.blend(a.1, b.1, s.m)
    let c = Self.copy[i]
    let size: CGFloat = compact ? 30 : 34
    return VStack(alignment: .leading, spacing: 0) {
      if !c.label.isEmpty {
        Text(c.label).font(.system(size: 13, weight: .semibold)).foregroundStyle(VisorBootOnboarding.color(M.amber))
          .padding(.bottom, 8)
      }
      Text(c.title).font(.system(size: size, weight: .bold)).kerning(-0.4)
        .foregroundStyle(VisorBootOnboarding.color(M.ink))
        .fixedSize(horizontal: false, vertical: true)
      VisorBootOnboardingBody(text: c.body, size: compact ? 15 : 17, lineHeight: compact ? 20 : 23)
        .frame(maxWidth: 340, alignment: .leading)
        .padding(.top, compact ? 6 : 10)
    }
    .opacity(o)
    .offset(y: dy)
    .accessibilityHidden(o < 0.5)
  }

  private func dotK(_ i: Int, _ s: VisorBootOnboardingState) -> Double {
    M.blend(M.filmDot(i, s.t), M.pageDot(i, s.page), s.m)
  }

  private func ctaK(_ s: VisorBootOnboardingState) -> Double {
    M.blend(M.filmCta(s.t), M.pageCta(s.page), s.m)
  }

  private func dotLeft(_ i: Int, _ s: VisorBootOnboardingState) -> CGFloat {
    var x: CGFloat = 0
    for j in 0..<i { x += Self.dot + Self.dotStretch * CGFloat(dotK(j, s)) + Self.dotGap }
    return x
  }

  private func row(_ s: VisorBootOnboardingState, rowW: CGFloat) -> some View {
    let rowH = VisorBootOnboarding.rowH
    let g = ctaK(s)
    let fade = 1 - M.clamp01(g * 3)
    let mm = M.clamp01(s.m)
    let anchor = M.filmCtaAnchor(s.t) * (1 - mm) + 3 * mm
    let left0 = dotLeft(Int(anchor.rounded()), s)
    let bh = CGFloat(M.lerp(Double(Self.dot), Double(rowH), g))
    let bw = CGFloat(M.lerp(Double(Self.dot + Self.dotStretch), Double(rowW), g))
    let bx = CGFloat(M.lerp(Double(left0), 0, min(1, g)))
    let labelK = M.seg(g, 0.6, 1)
    let nextK = M.clamp01(g * 2.5)
    let ink = VisorBootOnboarding.color(M.ink)
    return ZStack(alignment: .topLeading) {
      // dots
      HStack(spacing: Self.dotGap) {
        ForEach(0..<4, id: \.self) { i in
          let k = dotK(i, s)
          Capsule().fill(ink)
            .frame(width: Self.dot + Self.dotStretch * CGFloat(k), height: Self.dot)
            .opacity((0.28 + 0.72 * k) * fade)
            .padding(.vertical, 18)
            .contentShape(Rectangle())
            .onTapGesture {
              UIImpactFeedbackGenerator(style: .light).impactOccurred()
              controller.goPage(i, now: CACurrentMediaTime())
            }
            .accessibilityLabel("Step \(i)")
            .accessibilityAddTraits(.isButton)
        }
      }
      .frame(height: rowH)
      // next
      Button {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        controller.next(now: CACurrentMediaTime())
      } label: {
        ZStack {
          Circle().strokeBorder(Color(.sRGB, red: 238 / 255, green: 240 / 255, blue: 242 / 255, opacity: 0.28), lineWidth: 2 / 3)
          VisorBootOnboardingArrow().stroke(ink, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
            .frame(width: 18, height: 18)
        }
        .frame(width: rowH, height: rowH)
        .contentShape(Circle())
      }
      .buttonStyle(VisorBootOnboardingPress(scale: 0.94))
      .accessibilityLabel("Next")
      .opacity(1 - nextK)
      .scaleEffect(1 - 0.1 * nextK)
      .offset(x: rowW - rowH)
      .allowsHitTesting(g < 0.5)
      // Get Started
      Button {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        controller.start(now: CACurrentMediaTime())
      } label: {
        ZStack(alignment: .topLeading) {
          Capsule().fill(ink)
            .frame(width: bw, height: bh)
            .offset(x: bx, y: (rowH - bh) / 2)
            .opacity(g > 0.002 ? 1 : 0)
          HStack(spacing: 10) {
            Text("Get Started").font(.system(size: 17, weight: .semibold)).foregroundStyle(VisorBootOnboarding.color(M.bgEdge))
            VisorBootOnboardingArrow().stroke(VisorBootOnboarding.color(M.bgEdge),
                                              style: StrokeStyle(lineWidth: 1.8 * 16 / 18, lineCap: .round, lineJoin: .round))
              .frame(width: 16, height: 16)
          }
          .frame(width: rowW, height: rowH)
          .opacity(labelK)
          .offset(y: (1 - labelK) * 6)
        }
        .frame(width: rowW, height: rowH, alignment: .topLeading)
        .contentShape(Rectangle())
      }
      .buttonStyle(VisorBootOnboardingPress(scale: 0.97))
      .accessibilityLabel("Get Started")
      .allowsHitTesting(g > 0.5)
    }
  }
}

/// React Native's arrow: M2 9h13M10 3.5 15.5 9 10 14.5 in an 18 x 18 box.
struct VisorBootOnboardingArrow: Shape {
  func path(in r: CGRect) -> Path {
    let k = r.width / 18
    var p = Path()
    p.move(to: CGPoint(x: r.minX + 2 * k, y: r.minY + 9 * k))
    p.addLine(to: CGPoint(x: r.minX + 15 * k, y: r.minY + 9 * k))
    p.move(to: CGPoint(x: r.minX + 10 * k, y: r.minY + 3.5 * k))
    p.addLine(to: CGPoint(x: r.minX + 15.5 * k, y: r.minY + 9 * k))
    p.addLine(to: CGPoint(x: r.minX + 10 * k, y: r.minY + 14.5 * k))
    return p
  }
}

/// The shared PressScale: a spring to `scale` while held.
struct VisorBootOnboardingPress: ButtonStyle {
  let scale: CGFloat
  @Environment(\.accessibilityReduceMotion) private var reduce
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .scaleEffect(configuration.isPressed && !reduce ? scale : 1)
      .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
  }
}

/// The copy's body as React Native lays it out: a fixed line height and plain greedy line breaks (SwiftUI's Text
/// avoids a single word on the last line, which wraps "about 40 seconds" differently from the reference).
struct VisorBootOnboardingBody: UIViewRepresentable {
  let text: String
  let size: CGFloat
  let lineHeight: CGFloat

  private var attributed: NSAttributedString {
    let p = NSMutableParagraphStyle()
    p.minimumLineHeight = lineHeight
    p.maximumLineHeight = lineHeight
    p.lineBreakStrategy = []
    let font = UIFont.systemFont(ofSize: size)
    return NSAttributedString(string: text, attributes: [
      .font: font, .paragraphStyle: p,
      .foregroundColor: UIColor(red: 0x99 / 255.0, green: 0xA1 / 255.0, blue: 0xAB / 255.0, alpha: 1),
      .baselineOffset: (lineHeight - font.lineHeight) / 4,
    ])
  }

  func makeUIView(context: Context) -> UILabel {
    let l = UILabel()
    l.numberOfLines = 0
    l.lineBreakStrategy = []
    l.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
    return l
  }

  func updateUIView(_ l: UILabel, context: Context) {
    if l.attributedText?.string != text { l.attributedText = attributed }
  }

  func sizeThatFits(_ proposal: ProposedViewSize, uiView l: UILabel, context: Context) -> CGSize? {
    let w = proposal.width ?? 340
    if l.attributedText?.string != text { l.attributedText = attributed }
    let r = attributed.boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude),
                                    options: [.usesLineFragmentOrigin], context: nil)
    return CGSize(width: w, height: ceil(r.height))
  }
}
