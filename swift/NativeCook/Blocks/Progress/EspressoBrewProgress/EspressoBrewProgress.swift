import QuartzCore
import SwiftUI

/// A coffee machine companion app around a real 3D espresso machine: Brew lights the group head, the needle climbs to
/// 9 bar, two streams pour and the cup fills with crema on top, while the sheet counts time, yield and pressure; then
/// the cup is changed for an empty one.
///
/// Ported from the React Native block (react-native-filament + @expo/ui): the same espresso.glb, converted to USDZ and
/// drawn with SceneKit, the same film, the system's segmented control and prominent button. Touch: Single / Double,
/// Brew (plays the shot; Brew again changes the cup first), and a sideways drag looks round the machine. 2 s after the
/// last touch a full cup is changed and the film restarts. Reduce Motion: a poured double, still; Brew shows the result.
struct EspressoBrewProgress: View {
  typealias M = EspressoBrewProgressMotion
  var autoPlay: Bool = true
  /// A finger started a shot: 0 = single, 1 = double.
  var onBrew: ((Int) -> Void)? = nil
  /// A finger's shot is done.
  var onShotReady: (() -> Void)? = nil

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: EspressoBrewProgressController?
  @State private var shown = false
  @State private var doseSel = 0
  @State private var status = 0
  @State private var stillTick = 0
  @State private var dragActive = false
  @State private var dragRefused = false

  static let gutter: CGFloat = 20
  static let panelR: CGFloat = 28
  private static let top: CGFloat = 18
  private static let headH: CGFloat = 30
  private static let gap: CGFloat = 14
  private static let controlsH: CGFloat = 40
  private static let statsH: CGFloat = 104
  private static let bottomGap: CGFloat = 16
  static let panelH: CGFloat = top + headH + gap + controlsH + gap + statsH + bottomGap
  private static let buttonW: CGFloat = 150
  private static let dot: CGFloat = 44

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let sheetH = Self.panelH + insets.bottom
      let stageH = max(260, H - sheetH + Self.panelR)
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
    // a light kitchen: the system controls keep their light look whatever the app's appearance
    .environment(\.colorScheme, .light)
    .onAppear {
      guard controller == nil else { return }
      let c = EspressoBrewProgressController(autoPlay: autoPlay, reduced: reduceMotion)
      controller = c
      sync(c)
    }
  }

  /// The native controls' values follow the film only when they change.
  private func sync(_ c: EspressoBrewProgressController) {
    let f = c.frame(at: CACurrentMediaTime())
    doseSel = f.doseSel
    status = M.statusAt(f.pose.t)
  }

  // MARK: the stage

  private func stage(_ c: EspressoBrewProgressController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    ZStack(alignment: .topLeading) {
      EspressoBrewProgressStage3D(controller: c, size: CGSize(width: width, height: height),
                                  onReady: { withAnimation(.easeInOut(duration: 0.32)) { shown = true } },
                                  onShotReady: onShotReady)
        .frame(width: width, height: height)
        .opacity(shown ? 1 : 0)
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
    .gesture(orbitDrag(c, width: width))
    .accessibilityElement()
    .accessibilityLabel("Espresso machine")
    .accessibilityHint("Drag sideways to look round the machine")
  }

  /// Sideways after 8 pt; a mostly vertical drag is not the machine's.
  private func orbitDrag(_ c: EspressoBrewProgressController, width: CGFloat) -> some Gesture {
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

  private func header(top: CGFloat, width: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.app).font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.color(M.ink))
      Spacer(minLength: 0)
      Text(M.machine).font(.system(size: 15)).foregroundStyle(Self.color(M.mute))
    }
    .frame(width: width - Self.gutter * 2, height: 22)
    .offset(x: Self.gutter, y: top + 10)
    .allowsHitTesting(false)
  }

  // MARK: the sheet

  private func panel(_ c: EspressoBrewProgressController, width: CGFloat, height: CGFloat) -> some View {
    let inner = min(width, 560) - Self.gutter * 2
    let sheet = UnevenRoundedRectangle(topLeadingRadius: Self.panelR, topTrailingRadius: Self.panelR, style: .continuous)
    return ZStack(alignment: .top) {
      sheet
        .fill(Self.color(M.panel))
        .shadow(color: Self.color(0x3C2C18, 0.16), radius: 12, x: 0, y: -8)
      sheet
        .stroke(Color.white.opacity(0.95), lineWidth: 0.33)
        .frame(height: Self.panelR)
        .frame(maxHeight: .infinity, alignment: .top)
        .mask(alignment: .top) { Rectangle().frame(height: Self.panelR) }
      ZStack(alignment: .topLeading) {
        column(c, inner: inner)
        if !c.reduced {
          // the film's finger, over the controls
          TimelineView(.animation) { _ in
            let f = c.frame(at: CACurrentMediaTime())
            ghost(f, inner: inner)
              .onChange(of: f.doseSel) { _, v in doseSel = v }
              .onChange(of: M.statusAt(f.pose.t)) { _, v in status = v }
          }
          .frame(width: inner, height: Self.panelH, alignment: .topLeading)
          .allowsHitTesting(false)
        }
      }
      .frame(width: inner, height: Self.panelH, alignment: .topLeading)
    }
    .frame(width: width, height: height, alignment: .top)
  }

  private func column(_ c: EspressoBrewProgressController, inner: CGFloat) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      HStack {
        Text(M.recipe)
          .font(.system(size: 20, weight: .bold))
          .tracking(-0.3)
          .foregroundStyle(Self.color(M.ink))
        Spacer(minLength: 8)
        Text(M.status[status])
          .font(.system(size: 15, weight: .medium))
          .foregroundStyle(Self.color(status == 2 ? M.accent : M.mute))
      }
      .frame(width: inner, height: Self.headH)
      controls(c, inner: inner)
        .frame(width: inner, height: Self.controlsH)
        .padding(.top, Self.gap)
      stats(c, inner: inner)
        .padding(.top, Self.gap)
    }
    .padding(.top, Self.top)
    .frame(width: inner, alignment: .topLeading)
  }

  private func controls(_ c: EspressoBrewProgressController, inner: CGFloat) -> some View {
    let pickerW = inner - Self.buttonW - 12
    let busy = status == 1 || status == 2 || status == 4
    let label = status == 0 ? "Brew" : status == 3 ? "Brew again" : "Brewing"
    return HStack(spacing: 0) {
      Picker("Dose", selection: doseBinding(c)) {
        ForEach(0..<M.doses.count, id: \.self) { i in Text(M.doses[i]).tag(i) }
      }
      .pickerStyle(.segmented)
      .frame(width: pickerW)
      Spacer(minLength: 0)
      Button {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        onBrew?(doseSel)
        c.brew(now: CACurrentMediaTime())
        refreshIfStill(c)
      } label: {
        Label(label, systemImage: busy ? "drop.fill" : "cup.and.saucer.fill")
          .lineLimit(1)
          .fixedSize()
      }
      .buttonStyle(.borderedProminent)
      .buttonBorderShape(.capsule)
      .controlSize(.regular)
      .tint(Self.color(M.accent))
      .disabled(busy)
      .frame(width: Self.buttonW, alignment: .trailing)
    }
  }

  private func doseBinding(_ c: EspressoBrewProgressController) -> Binding<Int> {
    Binding(
      get: { doseSel },
      set: { v in
        guard v != doseSel else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        doseSel = v
        c.choose(dose: v, now: CACurrentMediaTime())
        refreshIfStill(c)
      }
    )
  }

  /// Reduce Motion has no running timeline: a touch redraws the still at once.
  private func refreshIfStill(_ c: EspressoBrewProgressController) {
    guard c.reduced else { return }
    sync(c)
    stillTick += 1
  }

  private func stats(_ c: EspressoBrewProgressController, inner: CGFloat) -> some View {
    let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
    let g = Int(M.doseG[doseSel])
    return VStack(alignment: .leading, spacing: 0) {
      TimelineView(.animation(minimumInterval: nil, paused: c.reduced)) { _ in
        let p = c.frame(at: CACurrentMediaTime()).pose
        VStack(alignment: .leading, spacing: 0) {
          HStack(spacing: 0) {
            reading("Time", unit: "s", value: M.shotSecondsAt(p.t))
            rule
            reading("Yield", unit: "g", value: M.lerp(M.doseG[0], M.doseG[1], p.dose) * M.fillAt(p.t))
            rule
            reading("Pressure", unit: "bar", value: M.barAt(p.t))
          }
          .frame(height: 52)
          ZStack(alignment: .leading) {
            Self.color(M.accent, 0.14)
            Self.color(M.accent)
              .frame(width: inner - 32)
              .scaleEffect(x: max(0.0001, M.fillAt(p.t)), y: 1, anchor: .leading)
          }
          .frame(width: inner - 32, height: 4)
          .clipShape(Capsule())
          .padding(.top, 10)
        }
      }
      .id(stillTick)
      HStack {
        Text("1 : 2 ratio · \(g / 2) g in")
        Spacer(minLength: 0)
        Text("Target \(g) g")
      }
      .font(.system(size: 12))
      .monospacedDigit()
      .foregroundStyle(Self.color(M.mute))
      .padding(.top, 8)
    }
    .padding(.horizontal, 16)
    .padding(.top, 12)
    .frame(width: inner, height: Self.statsH, alignment: .topLeading)
    .background {
      shape.fill(Self.color(M.card))
      shape.strokeBorder(Self.color(M.ink, 0.09), lineWidth: 0.33)
    }
  }

  private var rule: some View {
    Self.color(M.ink, 0.09).frame(width: 0.33, height: 36).padding(.horizontal, 12)
  }

  private func reading(_ label: String, unit: String, value: Double) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      Text(label)
        .font(.system(size: 13))
        .foregroundStyle(Self.color(M.mute))
        .frame(height: 18)
      HStack(alignment: .firstTextBaseline, spacing: 3) {
        Text(String(format: "%.1f", value))
          .font(.system(size: 22, weight: .bold))
          .monospacedDigit()
          .foregroundStyle(Self.color(M.ink))
          .lineLimit(1)
          .fixedSize()
        Text(unit)
          .font(.system(size: 13))
          .foregroundStyle(Self.color(M.mute))
          .lineLimit(1)
          .fixedSize()
      }
      .frame(height: 30)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .accessibilityElement(children: .combine)
  }

  private func ghost(_ f: EspressoBrewProgressController.Frame, inner: CGFloat) -> some View {
    let g = M.tapGhost(f.pose.t)
    let pickerW = inner - Self.buttonW - 12
    let x = g.target == 2 ? inner - Self.buttonW / 2 : pickerW * (g.target == 0 ? 0.25 : 0.75)
    return Circle()
      .fill(Self.color(M.accent, 0.16))
      .overlay(Circle().strokeBorder(Self.color(M.accent, 0.38), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .scaleEffect(1 - 0.16 * g.pressed)
      .opacity(g.opacity * 0.9 * (f.filming ? 1 : 0))
      .offset(x: x - Self.dot / 2, y: Self.top + Self.headH + Self.gap + Self.controlsH / 2 - Self.dot / 2)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

#Preview {
  EspressoBrewProgress()
}
