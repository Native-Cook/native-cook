import QuartzCore
import SwiftUI

/// Order tracking around a real 3D carton: as the order moves through Packed, Sealed, Labelled and On its way, the box
/// acts it out. Its flaps fall shut, tape runs over the seam, a label drops and sticks, then the box slides off and a
/// fresh one slides in.
///
/// Ported from the React Native block (react-native-filament): the same parcel.glb, converted to USDZ and drawn with
/// SceneKit, the same film. Touch: tap a step to run the film there, drag across the carton to look round it. 2 s after
/// the last touch the film carries on. Reduce Motion: a still, sealed and labelled; taps set the step at once.
struct ParcelPackProgress: View {
  typealias M = ParcelPackProgressMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: ParcelPackProgressController?
  @State private var shown = false
  @State private var dragActive = false
  @State private var dragRefused = false
  @State private var stillTick = 0

  static let gutter: CGFloat = 20
  static let panelR: CGFloat = 28
  static let rowH: CGFloat = 56
  private static let top: CGFloat = 22
  private static let headH: CGFloat = 34
  private static let subH: CGFloat = 22
  private static let cardGap: CGFloat = 16
  private static let bottomGap: CGFloat = 16
  static let panelH: CGFloat = top + headH + subH + cardGap + rowH * 4 + bottomGap
  private static let mark: CGFloat = 28
  private static let rowPad: CGFloat = 14
  private static let linkGap: CGFloat = 4
  private static let linkH: CGFloat = rowH - mark - linkGap * 2
  private static let pillInset: CGFloat = 5
  private static let dot: CGFloat = 46

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let sheetH = Self.panelH + insets.bottom
      let stageH = max(200, H - sheetH + Self.panelR)
      ZStack(alignment: .topLeading) {
        Self.color(M.backdrop)
        if let controller {
          stage(controller, width: W, height: stageH, top: insets.top)
          header(top: insets.top, width: W)
          panel(controller, width: W, height: sheetH)
            .offset(y: H - sheetH)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.backdrop).ignoresSafeArea())
    .preferredColorScheme(.light)
    .onAppear {
      if controller == nil { controller = ParcelPackProgressController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  // MARK: the stage

  private func stage(_ c: ParcelPackProgressController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    ZStack(alignment: .topLeading) {
      ParcelPackProgressStage3D(controller: c, size: CGSize(width: width, height: height)) {
        withAnimation(.easeInOut(duration: 0.32)) { shown = true }
      }
      .frame(width: width, height: height)
      .opacity(shown ? 1 : 0)
      .allowsHitTesting(false)

      // the backdrop's colour over the top of the floor, so the status bar and the shop's name sit on one tone
      LinearGradient(stops: [.init(color: Self.color(M.backdrop), location: 0),
                             .init(color: Self.color(M.backdrop, 0.6), location: 0.55),
                             .init(color: Self.color(M.backdrop, 0), location: 1)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: top + 72)
        .allowsHitTesting(false)

      if !c.reduced {
        TimelineView(.animation) { _ in
          touchDot(c.frame(at: CACurrentMediaTime()), width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
      }
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .clipped()
    .contentShape(Rectangle())
    .gesture(orbitDrag(c, width: width))
    .accessibilityElement()
    .accessibilityLabel("Parcel")
    .accessibilityHint("Drag sideways to look round the parcel")
  }

  /// Sideways after 8 pt; a mostly vertical drag is not the carton's.
  private func orbitDrag(_ c: ParcelPackProgressController, width: CGFloat) -> some Gesture {
    DragGesture(minimumDistance: 8)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if dragRefused { return }
        if !dragActive {
          if abs(v.translation.height) > abs(v.translation.width) {
            dragRefused = true
            return
          }
          dragActive = true
          c.dragBegan(now: now, width: Double(width))
        }
        c.dragMoved(dx: Double(v.translation.width), width: Double(width), now: now)
      }
      .onEnded { v in
        if dragActive { c.dragEnded(velocity: Double(v.velocity.width), width: Double(width), now: CACurrentMediaTime()) }
        dragActive = false
        dragRefused = false
      }
  }

  private func touchDot(_ f: ParcelPackProgressController.Frame, width: CGFloat, height: CGFloat) -> some View {
    let g = M.ghostAt(f.t)
    let dx = CGFloat(-M.orbitFilm / M.orbitSpan) * width * CGFloat(g.along)
    return Circle()
      .fill(Color.white.opacity(0.42))
      .overlay(Circle().strokeBorder(Color.white.opacity(0.85), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .shadow(color: Self.color(0x3B2F1E, 0.28), radius: 8, x: 0, y: 3)
      .scaleEffect(1 - 0.16 * g.pressed)
      .opacity(g.opacity * 0.9 * (f.filming ? 1 : 0))
      .offset(x: width * 0.64 + dx - Self.dot / 2, y: height * 0.62 - Self.dot / 2)
  }

  private func header(top: CGFloat, width: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.shop).font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.color(M.ink))
      Spacer(minLength: 0)
      Text(M.order).font(.system(size: 15)).monospacedDigit().foregroundStyle(Self.color(M.ink, 0.62))
    }
    .frame(width: width - Self.gutter * 2, height: 22)
    .offset(x: Self.gutter, y: top + 10)
    .allowsHitTesting(false)
  }

  // MARK: the sheet

  private func panel(_ c: ParcelPackProgressController, width: CGFloat, height: CGFloat) -> some View {
    let inner = min(width, 560) - Self.gutter * 2
    let sheet = UnevenRoundedRectangle(topLeadingRadius: Self.panelR, topTrailingRadius: Self.panelR)
    return ZStack(alignment: .top) {
      sheet
        .fill(Self.color(M.panel))
        .shadow(color: Self.color(0x3B2F1E, 0.16), radius: 24, x: 0, y: -8)
      sheet
        .stroke(Color.white.opacity(0.95), lineWidth: 0.33)
        .frame(height: Self.panelR)
        .frame(maxHeight: .infinity, alignment: .top)
        .mask(alignment: .top) { Rectangle().frame(height: Self.panelR) }
      VStack(alignment: .leading, spacing: 0) {
        TimelineView(.animation(minimumInterval: nil, paused: c.reduced)) { _ in
          let t = c.frame(at: CACurrentMediaTime()).t
          ZStack(alignment: .leading) {
            ForEach(0..<M.headlines.count, id: \.self) { j in
              let a = M.headlineAlpha(j, t)
              Text(M.headlines[j])
                .font(.system(size: 26, weight: .bold))
                .tracking(-0.4)
                .lineLimit(1)
                .foregroundStyle(Self.color(M.ink))
                .opacity(a)
                .offset(y: (1 - a) * 6)
                .accessibilityHidden(a < 0.5)
            }
          }
          .frame(width: inner, height: Self.headH, alignment: .leading)
        }
        .id(stillTick)
        Text(M.arrives)
          .font(.system(size: 15))
          .monospacedDigit()
          .foregroundStyle(Self.color(M.mute))
          .frame(height: Self.subH)
        stepsCard(c, inner: inner)
          .padding(.top, Self.cardGap)
      }
      .frame(width: inner, alignment: .leading)
      .padding(.top, Self.top)
    }
    .frame(width: width, height: height, alignment: .top)
  }

  private func stepsCard(_ c: ParcelPackProgressController, inner: CGFloat) -> some View {
    let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
    return ZStack(alignment: .topLeading) {
      shape
        .fill(Color.white)
        .shadow(color: Self.color(0x3A2E1E, 0.07), radius: 10, x: 0, y: 3)
      shape.strokeBorder(Self.color(M.ink, 0.08), lineWidth: 0.33)
      TimelineView(.animation(minimumInterval: nil, paused: c.reduced)) { _ in
        steps(t: c.frame(at: CACurrentMediaTime()).t, inner: inner)
      }
      .id(stillTick)
      .allowsHitTesting(false)
      .accessibilityHidden(true) // the buttons below name the steps
      // the rows' touch targets stay still outside the timeline
      VStack(spacing: 0) {
        ForEach(0..<M.stepN, id: \.self) { i in
          Button {
            c.jump(to: i, now: CACurrentMediaTime())
            if c.reduced { stillTick += 1 }
          } label: {
            Color.clear.contentShape(Rectangle())
          }
          .buttonStyle(.plain)
          .frame(height: Self.rowH)
          .accessibilityLabel(M.steps[i].title)
          .accessibilityHint("Shows the parcel at this step")
        }
      }
    }
    .frame(width: inner, height: Self.rowH * 4)
  }

  private func steps(t: Double, inner: CGFloat) -> some View {
    let active = M.activeRow(t)
    let alpha = M.clamp01(M.activeAlpha(t))
    return ZStack(alignment: .topLeading) {
      RoundedRectangle(cornerRadius: 15, style: .continuous)
        .fill(Self.color(M.greenSoft))
        .frame(width: inner - Self.pillInset * 2, height: Self.rowH - Self.pillInset * 2)
        .offset(x: Self.pillInset, y: Self.pillInset + CGFloat(active) * Self.rowH)
        .opacity(alpha)
      ForEach(0..<(M.stepN - 1), id: \.self) { i in
        link(i, t: t)
      }
      VStack(spacing: 0) {
        ForEach(0..<M.stepN, id: \.self) { i in
          row(i, t: t, active: active, alpha: alpha)
        }
      }
    }
    .frame(width: inner, height: Self.rowH * 4, alignment: .topLeading)
  }

  private func link(_ i: Int, t: Double) -> some View {
    ZStack(alignment: .top) {
      Self.color(M.track)
      Self.color(M.green)
        .frame(height: Self.linkH * CGFloat(M.doneAt(i, t)))
        .frame(maxHeight: .infinity, alignment: .top)
    }
    .frame(width: 2, height: Self.linkH)
    .clipShape(RoundedRectangle(cornerRadius: 1))
    .offset(x: Self.rowPad + Self.mark / 2 - 1,
            y: CGFloat(i) * Self.rowH + Self.rowH / 2 + Self.mark / 2 + Self.linkGap)
  }

  private func row(_ i: Int, t: Double, active: Double, alpha: Double) -> some View {
    let near = M.clamp01(1 - abs(active - Double(i))) * alpha
    let done = M.doneAt(i, t)
    return HStack(spacing: 0) {
      ZStack {
        Circle().strokeBorder(Self.color(M.track), lineWidth: 2)
        ZStack {
          Circle().strokeBorder(Self.color(M.green), lineWidth: 2)
          Circle().fill(Self.color(M.green))
            .frame(width: 10, height: 10)
            .scaleEffect(0.72 + 0.28 * M.pulseAt(t))
        }
        .opacity(near * (1 - done))
        ZStack {
          Circle().fill(Self.color(M.green))
          Image(systemName: "checkmark")
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(.white)
        }
        .opacity(done)
        .scaleEffect(M.checkScale(i, t))
      }
      .frame(width: Self.mark, height: Self.mark)
      Text(M.steps[i].title)
        .font(.system(size: 17, weight: .semibold))
        .foregroundStyle(Self.color(M.ink))
        .opacity(0.46 + 0.54 * max(done, near))
        .padding(.leading, 14)
      Spacer(minLength: 8)
      Text(M.steps[i].at)
        .font(.system(size: 15))
        .monospacedDigit()
        .foregroundStyle(Self.color(M.mute))
        .opacity(done)
    }
    .padding(.horizontal, Self.rowPad)
    .frame(height: Self.rowH)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

#Preview {
  ParcelPackProgress()
}
