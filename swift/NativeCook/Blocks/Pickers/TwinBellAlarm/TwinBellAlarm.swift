import QuartzCore
import SwiftUI

/// An alarm screen around a real 3D twin-bell clock. Its hands sweep to the time on the system's wheel, and when the
/// alarm is switched on it rings: the hammer chatters between the bells, the bells shake, the clock hops and rocks.
///
/// Ported from the React Native block (react-native-filament + @expo/ui): the same alarm.glb, converted to USDZ and
/// drawn with SceneKit, the same film, the real SwiftUI `DatePicker` and `Toggle`. Touch: the wheel and the switch work,
/// and a sideways drag looks round the clock. 2 s after the last touch it goes back to 7:00 and the film restarts.
/// Reduce Motion: a still at 6:30 with the alarm on; the hands jump to a picked time and nothing rings.
struct TwinBellAlarm: View {
  typealias M = TwinBellAlarmMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: TwinBellAlarmController?
  @State private var shown = false
  @State private var minutes = M.restMin
  @State private var on = false
  @State private var dragActive = false
  @State private var dragRefused = false

  static let gutter: CGFloat = 20
  static let panelR: CGFloat = 28
  private static let top: CGFloat = 20
  private static let readH: CGFloat = 46
  private static let subH: CGFloat = 20
  private static let wheelGap: CGFloat = 6
  private static let wheelH: CGFloat = 148
  private static let cardGap: CGFloat = 8
  private static let cardH: CGFloat = 56
  private static let cardPad: CGFloat = 16
  private static let bottomGap: CGFloat = 14
  static let panelH: CGFloat = top + readH + subH + wheelGap + wheelH + cardGap + cardH + bottomGap
  private static let wheelTop: CGFloat = top + readH + subH + wheelGap
  private static let cardTop: CGFloat = wheelTop + wheelH + cardGap
  private static let dragPt: CGFloat = 64
  private static let minutesDX: CGFloat = 8
  private static let switchDX: CGFloat = 26
  private static let dot: CGFloat = 44

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
      guard controller == nil else { return }
      controller = TwinBellAlarmController(autoPlay: autoPlay, reduced: reduceMotion)
      minutes = reduceMotion ? M.filmMin : M.restMin
      on = reduceMotion
    }
  }

  // MARK: the stage

  private func stage(_ c: TwinBellAlarmController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    ZStack(alignment: .topLeading) {
      TwinBellAlarmStage3D(controller: c, size: CGSize(width: width, height: height)) {
        withAnimation(.easeInOut(duration: 0.32)) { shown = true }
      }
      .frame(width: width, height: height)
      .opacity(shown ? 1 : 0)
      .allowsHitTesting(false)

      // the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone
      LinearGradient(stops: [.init(color: Self.color(M.backdrop), location: 0),
                             .init(color: Self.color(M.backdrop, 0.6), location: 0.55),
                             .init(color: Self.color(M.backdrop, 0), location: 1)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: top + 72)
        .allowsHitTesting(false)
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .clipped()
    .contentShape(Rectangle())
    .gesture(orbitDrag(c, width: width))
    .accessibilityElement()
    .accessibilityLabel("Alarm clock")
    .accessibilityHint("Drag sideways to look round the clock")
  }

  /// Sideways after 8 pt; a mostly vertical drag is not the clock's.
  private func orbitDrag(_ c: TwinBellAlarmController, width: CGFloat) -> some Gesture {
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

  private func header(top: CGFloat, width: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.app).font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.color(M.ink))
      Spacer(minLength: 0)
      Text(M.repeats).font(.system(size: 15)).foregroundStyle(Self.color(M.ink, 0.62))
    }
    .frame(width: width - Self.gutter * 2, height: 22)
    .offset(x: Self.gutter, y: top + 10)
    .allowsHitTesting(false)
  }

  // MARK: the sheet

  private func panel(_ c: TwinBellAlarmController, width: CGFloat, height: CGFloat) -> some View {
    let inner = min(width, 560) - Self.gutter * 2
    let sheet = UnevenRoundedRectangle(topLeadingRadius: Self.panelR, topTrailingRadius: Self.panelR)
    return ZStack(alignment: .top) {
      sheet
        .fill(Self.color(M.panel))
        .shadow(color: Self.color(0x1B2238, 0.2), radius: 24, x: 0, y: -8)
      sheet
        .stroke(Color.white.opacity(0.95), lineWidth: 0.33)
        .frame(height: Self.panelR)
        .frame(maxHeight: .infinity, alignment: .top)
        .mask(alignment: .top) { Rectangle().frame(height: Self.panelR) }
      ZStack(alignment: .topLeading) {
        controls(c, inner: inner)
        if !c.reduced {
          // the film's values reach the native controls only when they change; its finger is drawn over them
          TimelineView(.animation) { _ in
            let f = c.frame(at: CACurrentMediaTime())
            ghosts(f, inner: inner)
              .onChange(of: f.wheel) { _, v in minutes = v }
              .onChange(of: f.armed) { _, v in on = v }
          }
          .frame(width: inner, height: Self.panelH, alignment: .topLeading)
          .allowsHitTesting(false)
        }
      }
      .frame(width: inner, height: Self.panelH, alignment: .topLeading)
    }
    .frame(width: width, height: height, alignment: .top)
  }

  private func controls(_ c: TwinBellAlarmController, inner: CGFloat) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      HStack(alignment: .firstTextBaseline, spacing: 6) {
        Text(M.clockText(minutes))
          .font(.system(size: 42, weight: .bold))
          .tracking(-0.8)
          .monospacedDigit()
          .foregroundStyle(Self.color(M.ink))
        Text(minutes % 1440 < 720 ? "AM" : "PM")
          .font(.system(size: 20, weight: .semibold))
          .foregroundStyle(Self.color(M.mute))
      }
      .frame(height: Self.readH)
      Text(on ? M.ringsIn(minutes) : "Alarm is off")
        .font(.system(size: 15))
        .monospacedDigit()
        .foregroundStyle(on ? Self.color(M.onText) : Self.color(M.mute))
        .frame(height: Self.subH)
      DatePicker("Alarm time", selection: dateBinding(c), displayedComponents: .hourAndMinute)
        .datePickerStyle(.wheel)
        .labelsHidden()
        .environment(\.colorScheme, .light)
        .frame(width: inner, height: Self.wheelH)
        .clipped()
        .padding(.top, Self.wheelGap)
      Toggle("Alarm", isOn: toggleBinding(c))
        .tint(Self.color(M.yellow))
        .padding(.horizontal, Self.cardPad)
        .frame(width: inner, height: Self.cardH)
        .background {
          let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
          shape.fill(Color.white)
            .shadow(color: Self.color(0x1B2238, 0.07), radius: 10, x: 0, y: 3)
          shape.strokeBorder(Self.color(M.ink, 0.08), lineWidth: 0.33)
        }
        .padding(.top, Self.cardGap)
    }
    .padding(.top, Self.top)
    .frame(width: inner, alignment: .topLeading)
  }

  private func dateBinding(_ c: TwinBellAlarmController) -> Binding<Date> {
    Binding(
      get: { Self.date(minutes) },
      set: { d in
        let parts = Calendar.current.dateComponents([.hour, .minute], from: d)
        let v = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        guard v != minutes else { return }
        minutes = v
        c.userMinutes(v, now: CACurrentMediaTime())
      }
    )
  }

  private func toggleBinding(_ c: TwinBellAlarmController) -> Binding<Bool> {
    Binding(
      get: { on },
      set: { v in
        guard v != on else { return }
        on = v
        c.userToggle(v, now: CACurrentMediaTime())
      }
    )
  }

  private static func date(_ minutes: Int) -> Date {
    Calendar.current.date(from: DateComponents(year: 2026, month: 1, day: 5, hour: minutes / 60, minute: minutes % 60)) ?? Date()
  }

  private func ghosts(_ f: TwinBellAlarmController.Frame, inner: CGFloat) -> some View {
    let w = M.wheelGhost(f.t), s = M.toggleGhost(f.t), film = f.filming ? 1.0 : 0
    return ZStack(alignment: .topLeading) {
      ghost(pressed: w.pressed, opacity: w.opacity * 0.9 * film)
        .offset(x: inner / 2 + Self.minutesDX - Self.dot / 2,
                y: Self.wheelTop + Self.wheelH / 2 + CGFloat(w.along) * Self.dragPt - Self.dot / 2)
      ghost(pressed: s.pressed, opacity: s.opacity * 0.9 * film)
        .offset(x: inner - Self.cardPad - Self.switchDX - Self.dot / 2,
                y: Self.cardTop + Self.cardH / 2 - Self.dot / 2)
    }
    .frame(width: inner, height: Self.panelH, alignment: .topLeading)
  }

  private func ghost(pressed: Double, opacity: Double) -> some View {
    Circle()
      .fill(Self.color(0x282C3C, 0.16))
      .overlay(Circle().strokeBorder(Self.color(0x282C3C, 0.3), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .scaleEffect(1 - 0.16 * pressed)
      .opacity(opacity)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

#Preview {
  TwinBellAlarm()
}
