import QuartzCore
import SwiftUI

/// A password vault's unlock screen around a real 3D wall safe: a bottle-green housing, a round brushed-steel door, a
/// numbered dial, a T handle and eight bolts. The dial turns to the three numbers of the code, which fill the sheet's
/// slots; on the third the handle swings, the bolts draw in and the door opens towards you with warm light in the gap.
///
/// Ported from the React Native block (react-native-filament + @expo/ui): the same safe.glb, converted to USDZ and drawn
/// with SceneKit, the same film, the real SwiftUI button. Touch: drag round the dial anywhere on the stage to turn it
/// (a tick per number); on release its speed carries it on and a spring lands it on a number, which is entered. The
/// right code opens the safe, a wrong one shakes it and clears the slots; Lock shuts it again. 2 s after the last touch
/// the door shuts, the dial goes home and the film restarts. Reduce Motion: a still of the open safe with the code in.
struct CombinationSafeDial: View {
  typealias M = CombinationSafeDialMotion
  var code: (Int, Int, Int) = M.demoCode
  var autoPlay: Bool = true
  /// A finger entered the right combination.
  var onUnlock: (() -> Void)? = nil

  init(code: (Int, Int, Int) = M.demoCode, autoPlay: Bool = true, onUnlock: (() -> Void)? = nil) {
    self.code = code
    self.autoPlay = autoPlay
    self.onUnlock = onUnlock
  }

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: CombinationSafeDialController?
  @State private var shown = false
  @State private var lockable = false
  @State private var dragActive = false

  static let gutter: CGFloat = 20
  static let panelR: CGFloat = 28
  private static let top: CGFloat = 16
  private static let headH: CGFloat = 32
  private static let gap: CGFloat = 12
  private static let slotH: CGFloat = 64
  private static let rowH: CGFloat = 38
  private static let bottom: CGFloat = 14
  static let panelH: CGFloat = top + headH + gap + slotH + gap + rowH * CGFloat(M.rows.count) + bottom
  private static let hair: CGFloat = 1 / 3
  private static let ghostRadius = 0.066
  private static let ghostStart = 58.0
  private static let dot: CGFloat = 44
  private static let glowW: CGFloat = 240
  private static let glowH: CGFloat = 360

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let sheetH = Self.panelH + insets.bottom
      let stageH = max(240, H - Self.panelH - insets.bottom + Self.panelR)
      ZStack(alignment: .topLeading) {
        Self.color(M.backdrop)
        if let controller {
          stage(controller, width: W, height: stageH, top: insets.top)
          Text(M.app)
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(Self.color(M.ink))
            .frame(height: 22)
            .offset(x: Self.gutter, y: insets.top + 10)
            .allowsHitTesting(false)
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
      guard controller == nil else { return }
      let c = CombinationSafeDialController(autoPlay: autoPlay, reduced: reduceMotion, code: code)
      c.onUnlock = onUnlock
      controller = c
      lockable = reduceMotion
    }
  }

  // MARK: the stage

  private func stage(_ c: CombinationSafeDialController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    ZStack(alignment: .topLeading) {
      CombinationSafeDialStage3D(controller: c, size: CGSize(width: width, height: height)) {
        withAnimation(.easeInOut(duration: 0.32)) { shown = true }
      }
      .frame(width: width, height: height)
      .opacity(shown ? 1 : 0)
      .allowsHitTesting(false)

      TimelineView(.animation) { _ in
        let f = c.frame(at: CACurrentMediaTime())
        overlays(f, width: width, height: height)
      }
      .frame(width: width, height: height, alignment: .topLeading)
      .allowsHitTesting(false)

      // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
      LinearGradient(stops: [.init(color: Self.color(M.backdrop), location: 0),
                             .init(color: Self.color(M.backdrop, 0.6), location: 0.55),
                             .init(color: Self.color(M.backdrop, 0), location: 1)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: top + 64)
        .allowsHitTesting(false)
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .clipped()
    .contentShape(Rectangle())
    .gesture(dialDrag(c, size: CGSize(width: width, height: height)))
    .accessibilityElement()
    .accessibilityLabel("Safe dial")
    .accessibilityHint("Drag round the dial to turn it")
  }

  private func dialDrag(_ c: CombinationSafeDialController, size: CGSize) -> some Gesture {
    DragGesture(minimumDistance: 2)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !dragActive {
          dragActive = true
          c.dragBegan(at: v.location, stage: size, now: now)
        }
        c.dragMoved(to: v.location, now: now)
      }
      .onEnded { v in
        c.dragEnded(at: v.location, velocity: v.velocity, now: CACurrentMediaTime())
        dragActive = false
      }
  }

  /// The warm light from inside the safe, standing in the gap between the door's free edge and the bore, and the
  /// film's finger circling the dial with it.
  private func overlays(_ f: CombinationSafeDialController.Frame, width: CGFloat, height: CGFloat) -> some View {
    let W = Double(width), H = Double(height)
    let open = M.doorAt(f.pose.unlock)
    let a = open * M.doorDeg * Double.pi / 180
    let reach = M.doorCentre.x + M.doorRadius
    let h = M.doorHinge
    let edge = M.project(M.V3(h.x + reach * cos(a), h.y, h.z + reach * sin(a)), open: open, width: W, height: H)
    let bore = M.project(M.V3(M.boreRadius, h.y, M.frontZ), open: open, width: W, height: H)
    let topP = M.project(M.V3(M.boreRadius, h.y + M.boreRadius, M.frontZ), open: open, width: W, height: H)
    let span = abs(bore.y - topP.y) * 2.5
    let gw = max(40, abs(edge.x - bore.x) * 2.1)

    let g = M.dialGhost(f.t)
    let c = M.project(M.V3(0, M.dialWorldY, M.dialFaceZ), open: 0, width: W, height: H)
    let e = M.project(M.V3(Self.ghostRadius, M.dialWorldY, M.dialFaceZ), open: 0, width: W, height: H)
    let r = abs(e.x - c.x)
    let th = (Self.ghostStart - f.pose.dial * M.degPerNumber) * Double.pi / 180

    return ZStack(alignment: .topLeading) {
      Ellipse()
        .fill(EllipticalGradient(stops: [.init(color: Self.color(M.glow, 0.85), location: 0),
                                         .init(color: Self.color(M.glow, 0.34), location: 0.38),
                                         .init(color: Self.color(M.glow, 0.08), location: 0.72),
                                         .init(color: Self.color(M.glow, 0), location: 1)],
                                 center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5))
        .frame(width: gw, height: span)
        .position(x: (edge.x + bore.x) / 2, y: bore.y)
        .opacity(M.seg(open, 0.05, 0.42) * 0.82)
      Circle()
        .fill(Color(.sRGB, red: 250 / 255, green: 246 / 255, blue: 236 / 255, opacity: 0.3))
        .overlay(Circle().strokeBorder(Color(.sRGB, red: 250 / 255, green: 246 / 255, blue: 236 / 255, opacity: 0.75), lineWidth: 1.5))
        .frame(width: Self.dot, height: Self.dot)
        .scaleEffect(1 - 0.16 * g.pressed)
        .position(x: c.x + r * sin(th), y: c.y - r * cos(th))
        .opacity(f.filming ? g.opacity * 0.9 : 0)
    }
    .frame(width: width, height: height, alignment: .topLeading)
  }

  // MARK: the sheet

  private func panel(_ c: CombinationSafeDialController, width: CGFloat, height: CGFloat) -> some View {
    let inner = min(width, 560) - Self.gutter * 2
    let sheet = UnevenRoundedRectangle(topLeadingRadius: Self.panelR, topTrailingRadius: Self.panelR, style: .continuous)
    return ZStack(alignment: .top) {
      sheet
        .fill(Self.color(M.panel))
        .shadow(color: Color(.sRGB, red: 38 / 255, green: 32 / 255, blue: 20 / 255, opacity: 0.2), radius: 12, x: 0, y: -8)
      sheet
        .stroke(Color.white.opacity(0.95), lineWidth: Self.hair)
        .frame(height: Self.panelR)
        .frame(maxHeight: .infinity, alignment: .top)
        .mask(alignment: .top) { Rectangle().frame(height: Self.panelR) }
      VStack(alignment: .leading, spacing: 0) {
        HStack(spacing: 0) {
          Text(M.vault)
            .font(.system(size: 20, weight: .bold))
            .tracking(-0.3)
            .foregroundStyle(Self.color(M.ink))
          Spacer(minLength: 0)
          if lockable {
            Button {
              UIImpactFeedbackGenerator(style: .medium).impactOccurred()
              c.relock(now: CACurrentMediaTime())
            } label: {
              Label("Lock", systemImage: "lock.fill")
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .controlSize(.small)
            .tint(Self.color(M.green))
            .accessibilityLabel("Lock the vault")
          } else {
            TimelineView(.animation) { _ in
              status(c.frame(at: CACurrentMediaTime()))
            }
            .frame(width: 120, height: 22)
          }
        }
        .frame(height: Self.headH)
        TimelineView(.animation) { _ in
          let f = c.frame(at: CACurrentMediaTime())
          slots(f)
            .onChange(of: f.canLock) { _, v in lockable = v }
        }
        .frame(height: Self.slotH)
        .padding(.top, Self.gap)
        rows(c)
          .padding(.top, Self.gap)
      }
      .padding(.top, Self.top)
      .frame(width: inner, alignment: .topLeading)
      .frame(maxHeight: .infinity, alignment: .top)
    }
    .frame(width: width, height: height, alignment: .top)
  }

  private func status(_ f: CombinationSafeDialController.Frame) -> some View {
    let open = M.doorAt(f.pose.unlock)
    return ZStack(alignment: .trailing) {
      Text("Locked")
        .accessibilityHidden(true)
        .font(.system(size: 15, weight: .medium))
        .foregroundStyle(Self.color(M.mute))
        .opacity((1 - M.seg(open, 0.1, 0.5)) * (1 - f.wrong))
      Text("Unlocked")
        .accessibilityHidden(true)
        .font(.system(size: 15, weight: .semibold))
        .foregroundStyle(Self.color(M.green))
        .opacity(M.seg(open, 0.3, 0.8))
      Text("Wrong code")
        .accessibilityHidden(true)
        .font(.system(size: 15, weight: .semibold))
        .foregroundStyle(Self.color(M.red))
        .opacity(f.wrong)
    }
    .frame(width: 120, height: 22, alignment: .trailing)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(f.wrong > 0.5 ? "Wrong code" : open > 0.5 ? "Unlocked" : "Locked")
  }

  private func slots(_ f: CombinationSafeDialController.Frame) -> some View {
    HStack(spacing: 8) {
      ForEach(0..<3, id: \.self) { i in
        let text = i < f.count ? M.twoDigits(f.entries[i]) : i == f.count ? M.twoDigits(M.dialNumber(f.pose.dial)) : "--"
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        ZStack {
          shape.fill(Self.color(M.card))
          shape.fill(Color(.sRGB, red: 31 / 255, green: 66 / 255, blue: 56 / 255, opacity: 0.07)).opacity(i < f.count ? 1 : 0)
          shape.strokeBorder(Self.color(M.ink, 0.09), lineWidth: Self.hair)
          shape.strokeBorder(Self.color(M.brass), lineWidth: 2).opacity(f.count == i ? 1 : 0)
          shape.fill(Color(.sRGB, red: 179 / 255, green: 58 / 255, blue: 43 / 255, opacity: 0.14)).opacity(f.wrong)
          Text(text)
            .font(.system(size: 32, weight: .bold))
            .monospacedDigit()
            .foregroundStyle(Self.color(M.ink))
            .accessibilityLabel("Number \(i + 1), \(text)")
        }
        .frame(minWidth: 0, maxWidth: .infinity)
        .frame(height: Self.slotH)
      }
    }
  }

  private func rows(_ c: CombinationSafeDialController) -> some View {
    VStack(spacing: 0) {
      ForEach(Array(M.rows.enumerated()), id: \.offset) { i, r in
        HStack(spacing: 0) {
          Text(r.label)
            .font(.system(size: 15))
            .foregroundStyle(Self.color(M.ink))
          Spacer(minLength: 0)
          TimelineView(.animation) { _ in
            let open = M.doorAt(c.frame(at: CACurrentMediaTime()).pose.unlock)
            ZStack(alignment: .trailing) {
              Text("••••")
                .font(.system(size: 15))
                .tracking(1)
                .foregroundStyle(Self.color(M.mute))
                .opacity(1 - M.seg(open, 0.1, 0.55))
                .accessibilityHidden(open > 0.5)
                .accessibilityLabel("Hidden")
              Text(r.value)
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Self.color(M.ink))
                .opacity(M.seg(open, 0.35, 0.9))
                .accessibilityHidden(open <= 0.5)
            }
            .frame(width: 64, height: 20, alignment: .trailing)
          }
          .frame(width: 64, height: 20)
        }
        .frame(height: Self.rowH - Self.hair)
        .overlay(alignment: .bottom) {
          if i < M.rows.count - 1 {
            Rectangle().fill(Self.color(M.ink, 0.09)).frame(height: Self.hair)
          }
        }
      }
    }
    .padding(.horizontal, 16)
    .background {
      let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
      shape.fill(Self.color(M.card))
      shape.strokeBorder(Self.color(M.ink, 0.09), lineWidth: Self.hair)
    }
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

#Preview {
  CombinationSafeDial()
}
