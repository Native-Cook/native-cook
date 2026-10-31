import SwiftUI

struct PuffySticker: View {
  static let loop = PuffyStickerMotion.loop
  var autoPlay: Bool
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var pixelRatio
  @StateObject private var driver = PuffyStickerDriver()
  @State private var switchX = 0.0
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  var body: some View {
    GeometryReader { outer in
      GeometryReader { geo in
        let l = PuffyStickerTheme.Layout(
          w: geo.size.width, h: geo.size.height, top: outer.safeAreaInsets.top,
          bottom: outer.safeAreaInsets.bottom)
        TimelineView(
          .animation(
            paused: scenePhase != .active || !driver.active || (!driver.manual && driver.pin != nil)
          )
        ) { timeline in
          content(l)
            .onChange(of: timeline.date, initial: true) { _, date in driver.tick(date) }
        }
      }
      .ignoresSafeArea()
    }
    .preferredColorScheme(.dark)
    .onAppear {
      driver.configure(autoPlay: autoPlay, reduced: reduced)
      driver.tick(Date())
    }
    .onChange(of: reduced) { _, v in driver.configure(autoPlay: autoPlay, reduced: v) }
    .onChange(of: autoPlay) { _, _ in driver.configure(autoPlay: autoPlay, reduced: reduced) }
    .onChange(of: scenePhase) { _, v in if v != .active { driver.audio.stopAll() } }
    .onDisappear { driver.audio.stopAll() }
  }
  private func content(_ l: PuffyStickerTheme.Layout) -> some View {
    let f = driver.frame
    return ZStack(alignment: .topLeading) {
      PuffyStickerTheme.page
      bar(l)
      Text(driver.puffy ? "Drag it, or pick another shape" : "Hold the sticker to puff it up")
        .font(PuffyStickerTheme.font(13)).foregroundStyle(PuffyStickerTheme.mute)
        .frame(width: l.w).offset(y: l.hintTop)
      control(l)
      HStack(spacing: 16) {
        ForEach(0..<4) { i in
          let press = f.tapAt == i ? f.tapPress : 0
          PuffyStickerThumbButton(
            selected: driver.selected == i, scriptedPress: press, action: { driver.goToShape(i) }
          )
          .frame(width: 64, height: 64)
          .accessibilityLabel(PuffyStickerTheme.labels[i])
          .accessibilityAddTraits(driver.selected == i ? .isSelected : [])
        }
      }
      .frame(width: 304, height: 64)
      .contentShape(Rectangle())
      .simultaneousGesture(
        DragGesture(minimumDistance: 6).onChanged { e in
          if abs(e.translation.width) > 6 { driver.scrub((e.location.x - 32) / 80) }
        }.onEnded { e in if abs(e.translation.width) > 6 { driver.endScrub() } }
      )
      .offset(x: l.trayLeft, y: l.trayTop)
      // The reference draws thumbnails above the tray backgrounds, then the full-screen hero.
      ForEach(0..<4) { i in
        stickerLayer(
          width: 64, height: 64, center: CGPoint(x: 32, y: 32), radius: 64 * 0.34, puff: 0.85,
          shape: Double(i), thumbnail: true, squish: 0, axis: 0, light: .zero
        )
        .offset(x: l.trayLeft + Double(i) * 80, y: l.trayTop)
      }
      stickerLayer(
        width: l.w, height: l.h, center: CGPoint(x: l.stageX + driver.x, y: l.stageY + driver.y),
        radius: l.sticker, puff: f.puff, shape: f.shape, squish: f.squish, axis: driver.axis,
        light: CGPoint(x: -driver.x / 900, y: driver.y / 900))
      Circle().fill(.white.opacity(f.touch * 0.14))
        .overlay(Circle().stroke(.white.opacity(f.touch * 0.75), lineWidth: 2))
        .frame(width: 60 * (0.8 + 0.2 * f.touch), height: 60 * (0.8 + 0.2 * f.touch))
        .position(x: l.stageX, y: l.stageY + l.sticker * 0.15)
        .allowsHitTesting(false)
      PuffyStickerTouchView { state, _, translation, velocity in
        driver.stickerEvent(state, translation, velocity)
      }
      .frame(width: l.sticker * 2.2, height: l.sticker * 2.2)
      .offset(x: l.stageX - l.sticker * 1.1, y: l.stageY - l.sticker * 1.1)
      .accessibilityElement()
      .accessibilityLabel(
        "\(PuffyStickerTheme.labels[driver.selected]) sticker, \(driver.puffy ? "puffy":"flat")"
      )
      .accessibilityHint("Hold to puff it up, drag to move it.")
      .accessibilityAdjustableAction { direction in driver.setPuff(direction == .increment) }
      .accessibilityAction { driver.setPuff(!driver.puffy) }
    }
    .frame(width: l.w, height: l.h, alignment: .topLeading)
    .clipped()
  }
  private func stickerLayer(
    width: Double, height: Double, center: CGPoint, radius: Double, puff: Double, shape: Double,
    thumbnail: Bool = false, squish: Double, axis: Double, light: CGPoint
  ) -> some View {
    let s = min(3, max(0, shape))
    let a = thumbnail ? s : min(floor(s), 2)
    let shader = ShaderLibrary.PuffyStickerMain(
      .float2(center), .float(radius), .float(puff), .float(a), .float(thumbnail ? a : a + 1),
      .float(thumbnail ? 0 : s - a),
      .float2(Float(1 + squish), Float(1 / (1 + squish))), .float(axis), .float2(light),
      .float(pixelRatio))
    return Rectangle().fill(.white).frame(width: width, height: height)
      .colorEffect(shader).allowsHitTesting(false).accessibilityHidden(true)
  }
  private func bar(_ l: PuffyStickerTheme.Layout) -> some View {
    ZStack {
      Text("New sticker").font(PuffyStickerTheme.font(17, "SemiBold")).accessibilityAddTraits(
        .isHeader)
      HStack {
        Button("Cancel") { driver.cancel() }.font(PuffyStickerTheme.font(16, "Regular"))
        Spacer()
        Button {
          driver.done()
        } label: {
          Text("Done").font(PuffyStickerTheme.font(15, "SemiBold"))
            .foregroundStyle(PuffyStickerTheme.accentInk)
            .padding(.horizontal, 16).frame(height: 32).background(
              PuffyStickerTheme.accent, in: Capsule())
        }
      }
    }
    .foregroundStyle(PuffyStickerTheme.ink).buttonStyle(.plain)
    .frame(width: l.w - 32, height: 44).offset(x: 16, y: l.barTop)
  }
  private func control(_ l: PuffyStickerTheme.Layout) -> some View {
    ZStack(alignment: .topLeading) {
      Capsule().fill(PuffyStickerTheme.surface)
      Capsule().fill(PuffyStickerTheme.surfaceOn).frame(width: 96, height: 32)
        .scaleEffect(1 - 0.04 * driver.frame.flatPress)
        .offset(x: 4 + switchX, y: 4)
      HStack(spacing: 0) {
        ForEach(0..<2) { i in
          Button {
            driver.setPuff(i == 1)
          } label: {
            Text(i == 0 ? "Flat" : "Puffy").font(PuffyStickerTheme.font(14))
              .foregroundStyle(
                (i == 1) == driver.puffy ? PuffyStickerTheme.ink : PuffyStickerTheme.mute
              )
              .frame(width: 96, height: 32)
          }
          .accessibilityAddTraits((i == 1) == driver.puffy ? .isSelected : [])
        }
      }.padding(4)
    }
    .buttonStyle(.plain).frame(width: 200, height: 40)
    .offset(x: l.w / 2 - 100, y: l.switchTop)
    .onChange(of: driver.puffy, initial: true) { _, on in
      withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.2)) { switchX = on ? 96 : 0 }
    }

  }
}

private struct PuffyStickerThumbButton: View {
  var selected: Bool, scriptedPress: Double
  var action: () -> Void
  var body: some View {
    Button(action: action) {
      RoundedRectangle(cornerRadius: 18).fill(PuffyStickerTheme.surface)
        .overlay(
          RoundedRectangle(cornerRadius: 18).strokeBorder(
            selected ? PuffyStickerTheme.ink : .clear, lineWidth: 2))
    }
    .buttonStyle(PuffyStickerThumbStyle(scriptedPress: scriptedPress))
  }
}
private struct PuffyStickerThumbStyle: ButtonStyle {
  var scriptedPress: Double
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.scaleEffect(configuration.isPressed ? 0.96 : 1 - 0.04 * scriptedPress)
      .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.12), value: configuration.isPressed)
  }
}
