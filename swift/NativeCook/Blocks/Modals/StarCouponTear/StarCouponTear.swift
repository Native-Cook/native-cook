import SwiftUI

struct StarCouponTear: View {
  static let loop = StarCouponTearMotion.loop
  var autoPlay: Bool
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.displayScale) private var displayScale
  @Environment(\.scenePhase) private var scenePhase
  @State private var driver = StarCouponTearDriver()
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { geo in
        TimelineView(.animation(paused: scenePhase != .active || (reduced && driver.reducedIdle))) {
          _ in
          let t = driver.now
          StarCouponTearScreen(
            size: geo.size, top: safe.safeAreaInsets.top, bottom: safe.safeAreaInsets.bottom,
            frame: driver.frame(t), phase: driver.phase(t), reduced: reduced, scale: displayScale,
            touch: { state, dy, v in driver.touch(state, dy, v) },
            claim: {
              driver.takeOver()
              driver.claim()
            })
        }
      }.ignoresSafeArea()
    }
    .preferredColorScheme(.dark)
    .onAppear {
      driver.autoPlay = autoPlay
      driver.reduced = reduced
    }
    .onDisappear { driver.cancelCompletion() }
    .onChange(of: reduced) { _, v in
      driver.cancelCompletion()
      driver.action = 0
      driver.reducedIdle = true
      driver.phaseOrigin = 0
      driver.reduced = v
      driver.epoch = Date.timeIntervalSinceReferenceDate
      driver.pauseTotal = 0
      driver.pausedAt = nil
      driver.manualStart = nil
    }
    .onChange(of: scenePhase) { _, v in driver.pause(v != .active) }
  }
}

private struct StarCouponTearScreen: View {
  typealias H = StarCouponTearTheme
  typealias M = StarCouponTearMotion
  let size: CGSize
  let top: Double
  let bottom: Double
  let frame: M.Frame
  let phase: Double
  let reduced: Bool
  let scale: Double
  let touch: (UIGestureRecognizer.State, Double, Double) -> Void
  let claim: () -> Void
  var body: some View {
    let w = Double(size.width)
    let h = Double(size.height)
    let s = (w * 0.3).rounded()
    let cy = top + ((h - top - bottom) * 0.43).rounded()
    let chipX = w - 76
    let chipY = top + 30
    let f = frame.fly
    let arc = sin(Double.pi * f)
    let p = M.backOut(frame.pop)
    let baseY = cy + frame.pull * 36
    let x = w / 2 + (chipX - 30 - w / 2) * f + 30 * arc
    let y = baseY + (chipY - baseY) * f - 40 * arc
    let pulse = frame.chip >= 1 ? 0 : exp(-frame.chip * 5) * sin(frame.chip * 24)
    let turn = reduced ? 0.2 : 0.2 + 0.25 * sin(phase) * (1 - frame.tear) + 2 * Double.pi * f
    let objectSize: Double = s * (1.0 - 0.85 * f) * p
    let objectAlpha: Double = 1.0 - M.smooth(0.85, 1.0, f)
    let obj = SIMD4<Float>(Float(x), Float(y), Float(objectSize), Float(objectAlpha))
    let shape = SIMD4<Float>(Float(frame.tear), Float(turn), 0.1, Float(frame.pull))
    let live = SIMD4<Float>(Float(phase * 3), Float(1 - 0.5 * f), Float(p * (1 - f)), Float(scale))
    let shader = ShaderLibrary.StarCouponTearMain(
      .float4(obj.x, obj.y, obj.z, obj.w), .float4(shape.x, shape.y, shape.z, shape.w),
      .float4(live.x, live.y, live.z, live.w))
    ZStack(alignment: .topLeading) {
      H.page
      HStack(spacing: 10) {
        Text("Flash coupon").font(H.font(13, "SemiBold")).foregroundStyle(H.goldInk)
          .padding(.horizontal, 10).frame(height: 26).background(H.gold, in: Capsule())
        Text("Today only · 3 left").font(H.font(14)).foregroundStyle(H.mute)
      }.offset(x: 20, y: top + 10)
      HStack(spacing: 8) {
        StarCouponTearTicketIcon().fill(H.gold).frame(width: 18, height: 14)
        Text("Coupons").font(H.font(14, "SemiBold")).foregroundStyle(.white)
      }.frame(width: 112, height: 40).background(H.pill, in: Capsule())
        .overlay(Capsule().strokeBorder(H.line, lineWidth: 1))
        .scaleEffect(1 + 0.1 * pulse).offset(x: chipX - 56, y: chipY - 20)
        .accessibilityElement(children: .ignore).accessibilityLabel("My coupons")
      Text("+1").font(H.font(14, "SemiBold")).foregroundStyle(H.gold)
        .opacity(max(0, sin(Double.pi * frame.chip))).offset(
          x: chipX - 20, y: chipY + 26 - 18 * frame.chip
        )
        .accessibilityHidden(true)
      Text("Half price on your next order").font(H.font(28, "SemiBold")).foregroundStyle(.white)
        .lineSpacing(34 - 28 * 1.21).fixedSize(horizontal: false, vertical: true).frame(
          width: w - 48, alignment: .leading
        )
        .offset(x: 24, y: top + 64).accessibilityAddTraits(.isHeader)
      Canvas { context, sz in
        context.fill(Path(CGRect(origin: .zero, size: sz)), with: .shader(shader))
      }.allowsHitTesting(false).accessibilityHidden(true)
      Text("Pull the star down to claim").font(H.font(15)).foregroundStyle(H.mute)
        .frame(width: w).opacity(max(0, 1 - frame.tear)).offset(y: cy + s * 1.5)
      // A transparent UIKit recognizer lets vertical pulls coexist with gallery navigation.
      StarCouponTearTouchView(onEvent: touch)
      Button(action: claim) {
        Text("Claim coupon").font(H.font(17, "SemiBold")).foregroundStyle(H.goldInk)
          .frame(width: w - 40, height: 56).background(H.gold, in: Capsule())
      }.buttonStyle(.plain)
        .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in touch(.began, 0, 0) })
        .offset(x: 20, y: h - max(bottom, 16) - 8 - 56).accessibilityLabel("Claim coupon")
      Circle().fill(.white.opacity(0.35)).overlay(
        Circle().strokeBorder(.white.opacity(0.8), lineWidth: 1.5)
      )
      .frame(width: 44, height: 44).opacity(0.85 * frame.finger)
      .offset(x: w / 2 - 22, y: cy + frame.pull * M.pullPT * 0.85 - 22).allowsHitTesting(false)
      .accessibilityHidden(true)
    }.frame(width: w, height: h, alignment: .topLeading)
  }
}
private struct StarCouponTearTicketIcon: Shape {
  func path(in rect: CGRect) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: 2, y: 1))
    p.addLine(to: CGPoint(x: 16, y: 1))
    p.addQuadCurve(to: CGPoint(x: 17, y: 2), control: CGPoint(x: 17, y: 1))
    p.addLine(to: CGPoint(x: 17, y: 5))
    p.addCurve(
      to: CGPoint(x: 17, y: 9), control1: CGPoint(x: 14.33, y: 5), control2: CGPoint(x: 14.33, y: 9)
    )
    p.addLine(to: CGPoint(x: 17, y: 12))
    p.addQuadCurve(to: CGPoint(x: 16, y: 13), control: CGPoint(x: 17, y: 13))
    p.addLine(to: CGPoint(x: 2, y: 13))
    p.addQuadCurve(to: CGPoint(x: 1, y: 12), control: CGPoint(x: 1, y: 13))
    p.addLine(to: CGPoint(x: 1, y: 9))
    p.addCurve(
      to: CGPoint(x: 1, y: 5), control1: CGPoint(x: 3.67, y: 9), control2: CGPoint(x: 3.67, y: 5))
    p.addLine(to: CGPoint(x: 1, y: 2))
    p.addQuadCurve(to: CGPoint(x: 2, y: 1), control: CGPoint(x: 1, y: 1))
    p.closeSubpath()
    return p.applying(CGAffineTransform(scaleX: rect.width / 18, y: rect.height / 14))
  }
}
