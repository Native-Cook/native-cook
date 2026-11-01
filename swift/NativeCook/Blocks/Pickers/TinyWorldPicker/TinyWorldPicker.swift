import SwiftUI

struct TinyWorldPicker: View {
  static let loop = TinyWorldPickerMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var scale
  @StateObject private var clock = TinyWorldPickerClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "tinyWorldPickerTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        let w = full.size.width, h = full.size.height, cy = (h * 0.47).rounded(), s = (w * 0.44).rounded()
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
          let f = clock.sample(timeline.date, autoPlay, reduced, pinned)
          let tint = TinyWorldPickerArt.halo(TinyWorldPickerWorld.all[f.cur].halo, TinyWorldPickerWorld.all[f.nxt].halo, f.m)
          ZStack(alignment: .topLeading) {
            Canvas(opaque: true, colorMode: .nonLinear) { c, size in TinyWorldPickerArt.sky(c, size, f) }
            Rectangle().fill(.white).frame(width: w, height: 520)
              .colorEffect(ShaderLibrary.tinyWorldPickerHalo(.float2(Float(w / 2), 260), .float(Float(w * 0.62)), .float3(tint.x, tint.y, tint.z)))
              .offset(y: cy - 260)
            Canvas(opaque: false, colorMode: .nonLinear) { c, size in TinyWorldPickerArt.dust(c, size, f) }
              .allowsHitTesting(false)
            Rectangle().fill(.white)
              .frame(width: w, height: 500)
              .colorEffect(ShaderLibrary.tinyWorldPickerWorld(
                .float2(Float(w / 2), 250), .float(Float(s)), .float(Float(f.yaw)),
                .float(Float(f.cur)), .float(Float(f.nxt)), .float(Float(min(f.m, 1.03))),
                .float(Float(f.sq)), .float(Float(f.glow)), .float(Float(1 / (s * scale))), .float(Float(f.tm))))
              .offset(y: cy - 250)
            Canvas(opaque: false, colorMode: .nonLinear) { c, size in
              TinyWorldPickerArt.foreground(c, size, top, bottom, scale, f, clock.pressed)
            }.allowsHitTesting(false)
            if pinned == nil {
              TinyWorldPickerTouchView(clock: clock, width: w, reduced: reduced)
                .frame(width: w, height: 340).offset(y: cy - 170)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("World")
                .accessibilityValue("\(TinyWorldPickerWorld.all[f.cur].name), \(TinyWorldPickerWorld.all[f.cur].status.lowercased()), world \(f.cur + 1) of 4")
                .accessibilityHint("Drag sideways or swipe up and down with VoiceOver to pick another world")
                .accessibilityAdjustableAction { direction in
                  let i = f.cur + (direction == .increment ? 1 : -1)
                  if (0..<4).contains(i) { clock.goTo(i, reduced) }
                }
              accessibilityControls(w, h, cy, top, bottom, f)
            }
          }
          .frame(width: w, height: h, alignment: .topLeading)
        }
      }.ignoresSafeArea()
    }
    .background(TinyWorldPickerArt.color(0x0E2A34))
    .preferredColorScheme(.dark)
    .onAppear { clock.active(scenePhase == .active) }
    .onDisappear { clock.active(false) }
    .onChange(of: scenePhase) { _, value in clock.active(value == .active) }
  }
  private func hit(_ label: String, _ x: Double, _ y: Double, _ width: Double, _ height: Double, pressed: Int = -1, action: @escaping () -> Void = {}) -> some View {
    Button(action: action) { Color.clear.contentShape(Rectangle()) }
      .buttonStyle(TinyWorldPickerPressStyle { down in clock.pressed = down ? pressed : -1 }).accessibilityLabel(label)
      .frame(width: width, height: height).position(x: x, y: y)
  }
  private func accessibilityControls(_ w: Double, _ h: Double, _ cy: Double, _ top: Double, _ bottom: Double, _ f: TinyWorldPickerMotion.Frame) -> some View {
    ZStack(alignment: .topLeading) {
      ForEach(0..<4, id: \.self) { i in
        hit("\(TinyWorldPickerWorld.all[i].name), world \(i + 1) of 4", w / 2 - 45 + Double(i) * 30, cy + 274, 30, 44) { clock.goTo(i, reduced) }
      }
      hit("Snapshot", w - 38, cy - 154, 44, 44, pressed: 0)
      hit("Rewards", w - 38, cy - 98, 44, 44, pressed: 1)
      hit("World map", w - 38, cy - 42, 44, 44, pressed: 2)
      hit("World info", 42, h - bottom - 40, 52, 52, pressed: 3)
      hit("Collection, 2 new", w - 42, h - bottom - 40, 52, 52, pressed: 4)
      hit("\(TinyWorldPickerWorld.all[f.cur].cta), \(TinyWorldPickerWorld.all[f.cur].name)", w / 2, h - bottom - 40, w - 160, 56, pressed: 5)
      Color.clear.frame(width: 42, height: 42).position(x: 37, y: top + 27)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("Level 20")
      Color.clear.frame(width: 150, height: 32).position(x: 141, y: top + 27)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("Energy 17,963")
      Color.clear.frame(width: 130, height: 32).position(x: w - 81, y: top + 22)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("13,401,935 coins")
      Color.clear.frame(width: 160, height: 32).position(x: w - 96, y: top + 62)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel("18,313 of 20,000 stars")
      Color.clear.frame(width: w - 48, height: 38).position(x: w / 2, y: cy + 159)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel(TinyWorldPickerWorld.all[f.cur].name).accessibilityAddTraits(.isHeader)
      Color.clear.frame(width: w - 48, height: 28).position(x: w / 2, y: cy + 202)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel(TinyWorldPickerWorld.all[f.cur].status)
      Color.clear.frame(width: w - 48, height: 20).position(x: w / 2, y: cy + 236)
        .allowsHitTesting(false).accessibilityElement().accessibilityLabel(zip(["best run", "tasks", "chests"], TinyWorldPickerWorld.all[f.cur].stats).map { "\($0) \($1)" }.joined(separator: ", "))
    }.frame(width: w, height: h, alignment: .topLeading)
  }
}

private struct TinyWorldPickerPressStyle: ButtonStyle {
  var changed: (Bool) -> Void
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.onChange(of: configuration.isPressed) { _, down in changed(down) }
  }
}
