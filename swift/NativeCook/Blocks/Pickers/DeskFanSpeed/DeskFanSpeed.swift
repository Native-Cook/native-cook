import QuartzCore
import SwiftUI

/// A smart-home fan control around a real 3D retro desk fan: a seafoam enamel body, a chrome cage, four brass blades and
/// four piano keys. The system's segmented control sets Off, 1, 2 or 3; the blades spin up and down with inertia, the
/// key for the level goes down on the base, the head oscillates and airflow lines drift across the screen.
///
/// Ported from the React Native block (react-native-filament + @expo/ui): the same fan.glb, converted to USDZ and drawn
/// with SceneKit, the same film (the blade angle is a pure function of the loop time, 20 whole turns a loop), the real
/// SwiftUI segmented `Picker`. Touch: the control sets the level and the motor chases it; a sideways drag on the fan aims
/// the head. 2 s after the last touch the fan goes Off, the blades coast to a stop and the film restarts. Reduce Motion:
/// a still at level 2, nothing spins.
struct DeskFanSpeed: View {
  typealias M = DeskFanSpeedMotion
  var autoPlay: Bool = true
  /// A finger picked a level (0 = off, 1..3).
  var onLevelChange: ((Int) -> Void)? = nil

  init(autoPlay: Bool = true, onLevelChange: ((Int) -> Void)? = nil) {
    self.autoPlay = autoPlay
    self.onLevelChange = onLevelChange
  }

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: DeskFanSpeedController?
  @State private var stageShown = false
  @State private var shown = 0
  @State private var dragActive = false
  @State private var dragRefused = false

  static let gutter: CGFloat = 20
  static let panelR: CGFloat = 28
  private static let top: CGFloat = 18
  private static let headH: CGFloat = 30
  private static let gap: CGFloat = 14
  private static let pickerH: CGFloat = 36
  private static let statsH: CGFloat = 74
  private static let bottom: CGFloat = 16
  static let panelH: CGFloat = top + headH + gap + pickerH + gap + statsH + bottom
  private static let hair: CGFloat = 1 / 3
  private static let dot: CGFloat = 44

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let sheetH = Self.panelH + insets.bottom
      let stageH = max(260, H - Self.panelH - insets.bottom + Self.panelR)
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
          // the air: thin currents drifting left to right across the screen, as strong as the blades are fast
          TimelineView(.animation) { _ in
            let f = controller.frame(at: CACurrentMediaTime())
            DeskFanSpeedAirflow(turns: f.pose.turns, speed: f.pose.speed)
          }
          .frame(width: W, height: stageH + 40)
          .clipped()
          .allowsHitTesting(false)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.backdrop).ignoresSafeArea())
    .preferredColorScheme(.light)
    .onAppear {
      guard controller == nil else { return }
      controller = DeskFanSpeedController(autoPlay: autoPlay, reduced: reduceMotion)
      shown = reduceMotion ? 2 : 0
    }
  }

  // MARK: the stage

  private func stage(_ c: DeskFanSpeedController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    ZStack(alignment: .topLeading) {
      DeskFanSpeedStage3D(controller: c, size: CGSize(width: width, height: height)) {
        withAnimation(.easeInOut(duration: 0.32)) { stageShown = true }
      }
      .frame(width: width, height: height)
      .opacity(stageShown ? 1 : 0)
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
    .gesture(aimDrag(c, width: width))
    .accessibilityElement()
    .accessibilityLabel("Desk fan")
    .accessibilityHint("Drag sideways to aim the fan")
  }

  /// Sideways after 8 pt; a mostly vertical drag is not the fan's.
  private func aimDrag(_ c: DeskFanSpeedController, width: CGFloat) -> some Gesture {
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
          c.dragBegan(now: now)
        }
        c.dragMoved(dx: Double(v.translation.width), width: Double(width), now: now)
      }
      .onEnded { v in
        if dragActive { c.dragEnded(velocity: Double(v.velocity.width), width: Double(width), now: CACurrentMediaTime()) }
        dragActive = false
        dragRefused = false
      }
  }

  // MARK: the sheet

  private func panel(_ c: DeskFanSpeedController, width: CGFloat, height: CGFloat) -> some View {
    let inner = min(width, 560) - Self.gutter * 2
    let sheet = UnevenRoundedRectangle(topLeadingRadius: Self.panelR, topTrailingRadius: Self.panelR, style: .continuous)
    return ZStack(alignment: .top) {
      sheet
        .fill(Self.color(M.panel))
        .shadow(color: Color(.sRGB, red: 60 / 255, green: 48 / 255, blue: 24 / 255, opacity: 0.18), radius: 12, x: 0, y: -8)
      sheet
        .stroke(Color.white.opacity(0.95), lineWidth: Self.hair)
        .frame(height: Self.panelR)
        .frame(maxHeight: .infinity, alignment: .top)
        .mask(alignment: .top) { Rectangle().frame(height: Self.panelR) }
      ZStack(alignment: .topLeading) {
        VStack(alignment: .leading, spacing: 0) {
          HStack(spacing: 0) {
            Text(M.room)
              .font(.system(size: 20, weight: .bold))
              .tracking(-0.3)
              .foregroundStyle(Self.color(M.ink))
            Spacer(minLength: 0)
            Text(shown == 0 ? "Off" : "Speed \(shown)")
              .font(.system(size: 15, weight: .medium))
              .monospacedDigit()
              .foregroundStyle(Self.color(M.mute))
          }
          .frame(height: Self.headH)
          Picker("Fan speed", selection: levelBinding(c)) {
            ForEach(0..<M.levels.count, id: \.self) { i in
              Text(M.levels[i]).tag(i)
            }
          }
          .pickerStyle(.segmented)
          .environment(\.colorScheme, .light)
          .frame(width: inner, height: Self.pickerH)
          .padding(.top, Self.gap)
          TimelineView(.animation) { _ in
            let f = c.frame(at: CACurrentMediaTime())
            stats(speed: f.pose.speed)
              .onChange(of: f.level) { _, v in shown = v }
          }
          .frame(width: inner, height: Self.statsH)
          .padding(.top, Self.gap)
        }
        .padding(.top, Self.top)
        if !c.reduced {
          // the film's finger, tapping the control at each change
          TimelineView(.animation) { _ in
            ghost(c.frame(at: CACurrentMediaTime()), inner: inner)
          }
          .frame(width: inner, height: Self.panelH, alignment: .topLeading)
          .allowsHitTesting(false)
        }
      }
      .frame(width: inner, height: Self.panelH, alignment: .topLeading)
      .frame(maxHeight: .infinity, alignment: .top)
    }
    .frame(width: width, height: height, alignment: .top)
  }

  private func levelBinding(_ c: DeskFanSpeedController) -> Binding<Int> {
    Binding(
      get: { shown },
      set: { v in
        guard v != shown else { return }
        shown = v
        UISelectionFeedbackGenerator().selectionChanged()
        onLevelChange?(v)
        c.userLevel(v, now: CACurrentMediaTime())
      }
    )
  }

  private func stats(speed: Double) -> some View {
    let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
    return HStack(spacing: 0) {
      reading("Motor", value: M.rpmAt(speed), unit: "rpm")
      Rectangle().fill(Self.color(M.ink, 0.09)).frame(width: Self.hair, height: 40)
      reading("Power", value: M.wattsAt(speed), unit: "W")
    }
    .frame(height: Self.statsH)
    .background {
      shape.fill(Self.color(M.card))
      shape.strokeBorder(Self.color(M.ink, 0.09), lineWidth: Self.hair)
    }
  }

  private func reading(_ label: String, value: Int, unit: String) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      Text(label)
        .font(.system(size: 13))
        .foregroundStyle(Self.color(M.mute))
        .frame(height: 18)
      HStack(alignment: .firstTextBaseline, spacing: 4) {
        Text(verbatim: "\(value)")
          .font(.system(size: 26, weight: .bold))
          .monospacedDigit()
          .foregroundStyle(Self.color(M.ink))
          .frame(minWidth: 76, alignment: .leading)
        Text(unit)
          .font(.system(size: 14))
          .foregroundStyle(Self.color(M.mute))
      }
      .frame(height: 34)
    }
    .padding(.horizontal, 16)
    .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
    .accessibilityElement(children: .combine)
  }

  private func ghost(_ f: DeskFanSpeedController.Frame, inner: CGFloat) -> some View {
    let g = M.tapGhost(f.t)
    let cell = inner / CGFloat(M.levels.count)
    return Circle()
      .fill(Self.color(M.green, 0.16))
      .overlay(Circle().strokeBorder(Self.color(M.green, 0.34), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .scaleEffect(1 - 0.16 * g.pressed)
      .opacity(g.opacity * 0.9 * (f.filming ? 1 : 0))
      .offset(x: cell * (CGFloat(g.level) + 0.5) - Self.dot / 2, y: Self.top + Self.headH + Self.gap + Self.pickerH / 2 - Self.dot / 2)
      .frame(width: inner, height: Self.panelH, alignment: .topLeading)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

/// The airflow: seven dashed currents across the screen. The dashes move half a pattern per blade turn, so they loop
/// with the blades; their strength follows the speed.
struct DeskFanSpeedAirflow: View {
  typealias M = DeskFanSpeedMotion
  var turns: Double
  var speed: Double

  /// Height of each current as a fraction of the area, its wave's size (pt) and phase, and how strong it is.
  private static let lines: [(y: Double, amp: Double, phase: Double, weight: Double)] = [
    (0.2, 11, 0.4, 0.62), (0.34, 15, 1.9, 1), (0.47, 12, 3.1, 0.8), (0.6, 16, 4.4, 0.95),
    (0.73, 10, 5.5, 0.6), (0.88, 13, 0.9, 0.72), (1.02, 9, 2.5, 0.5),
  ]
  private static let dash = 26.0
  private static let steps = 28
  private static let fullSpeed = 3.2
  private static let lineAlpha = 0.34

  var body: some View {
    let opacity = M.clamp01(speed / Self.fullSpeed) * Self.lineAlpha
    let phase = -((turns * M.flowPerTurn).truncatingRemainder(dividingBy: 1) * M.flowPeriod)
    Canvas { ctx, size in
      guard opacity > 0.001 else { return }
      for l in Self.lines {
        var p = Path()
        let y = Double(size.height) * l.y
        for i in 0...Self.steps {
          let k = Double(i) / Double(Self.steps)
          let px = -20 + (Double(size.width) + 40) * k
          let py = y + l.amp * sin(k * .pi * 2.2 + l.phase) + l.amp * 0.35 * sin(k * .pi * 5.1 + l.phase * 1.7)
          if i == 0 { p.move(to: CGPoint(x: px, y: py)) } else { p.addLine(to: CGPoint(x: px, y: py)) }
        }
        let on = Self.dash * l.weight + 8
        let period = M.flowPeriod
        // Skia's dash phase may be negative; CoreGraphics wants it inside the pattern
        let ph = (phase.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period)
        ctx.stroke(p, with: .color(DeskFanSpeed.color(M.green)),
                   style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [on, period - on], dashPhase: ph))
      }
    }
    .opacity(opacity)
  }
}

#Preview {
  DeskFanSpeed()
}
