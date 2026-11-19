import QuartzCore
import SwiftUI
import UIKit

/// A watch boutique's product page told as a 12-second film around a real 3D lacquered walnut watch case: the clasp
/// lets go, the lid swings open on a damped hinge, the suede pillow lifts the watch and tips its sunburst dial to the
/// lens, the watch turns to catch the light while the camera pushes in to the ticking second hand. The specs count up
/// as it rises, the native segmented picker tries every dial and "Reserve in a boutique" is pressed.
///
/// Ported from the React Native block (react-native-filament + Skia): the same watch.glb, converted to USDZ and drawn
/// with SceneKit (WatchCaseRevealScene3D), the same film (WatchCaseRevealMotion), the same Skia layers in SwiftUI.
/// Touch: drag up on the case to open it and lift the pillow, down to close; sideways once open to turn the watch
/// (rubber band, a detent tick every 12 deg, spring back); tap to open; pick a dial; reserve. 2 s after the last touch
/// the case closes in 2 s and the film restarts. Reduce Motion: open with the pillow up, no film.
struct WatchCaseReveal: View {
  typealias M = WatchCaseRevealMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: WatchCaseRevealController?

  /// The product panel's height (title, specs, picker, button) and the header row's.
  private static let panelH: CGFloat = 236
  private static let headerH: CGFloat = 44
  private static let fade: CGFloat = 48
  private static let topFade: CGFloat = 16
  static let gutter: CGFloat = 20

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let panelTop = H - (insets.bottom + 12 + Self.panelH)
      let stageTop = insets.top + Self.headerH
      let stageH = max(120, panelTop - 8 - stageTop)
      ZStack(alignment: .topLeading) {
        LinearGradient(colors: [Self.color(M.bgTop), Self.color(M.bgBottom)], startPoint: .top, endPoint: .bottom)
        if let controller {
          stage(controller, width: W, height: stageH, screenH: H, top: stageTop)
            .frame(width: W, height: stageH)
            .clipped()
            .offset(y: stageTop)
          Color.clear
            .contentShape(Rectangle())
            .frame(width: W, height: stageH)
            .offset(y: stageTop)
            .gesture(drag(controller))
            .onTapGesture { controller.tap(CACurrentMediaTime()) }
            .accessibilityLabel("Watch case")
            .accessibilityHint("Drag up to open the case, sideways to turn the watch")
          header(top: insets.top)
          // the panel hangs from the bottom edge (React Native: bottom = inset + 12)
          VStack(spacing: 0) {
            Spacer(minLength: 0)
            WatchCaseRevealPanel(controller: controller, width: min(W, 560) - Self.gutter * 2)
          }
          .frame(width: min(W, 560) - Self.gutter * 2, height: H - insets.bottom - 12, alignment: .bottomLeading)
          .offset(x: Self.gutter)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.bgBottom).ignoresSafeArea())
    .preferredColorScheme(.light)
    .statusBarHidden(false)
    .onAppear {
      if controller == nil { controller = WatchCaseRevealController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  private func stage(_ c: WatchCaseRevealController, width: CGFloat, height: CGFloat, screenH: CGFloat, top: CGFloat) -> some View {
    let size = CGSize(width: width, height: height)
    return ZStack(alignment: .topLeading) {
      TimelineView(.animation) { _ in
        WatchCaseRevealBackdrop(pose: c.frame(at: CACurrentMediaTime()).pose)
      }
      WatchCaseRevealStage3D(controller: c, size: size)
        .frame(width: width, height: height)
        .allowsHitTesting(false)
      // the window's edges fade into the paper, so a close shot never runs behind the type
      LinearGradient(colors: [Self.paper(top, screenH, 1), Self.paper(top + Self.topFade, screenH, 0)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: Self.topFade)
      LinearGradient(colors: [Self.paper(top + height - Self.fade, screenH, 0), Self.paper(top + height, screenH, 1)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: Self.fade)
        .offset(y: height - Self.fade)
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .allowsHitTesting(false)
  }

  private func header(top: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.brand).font(.system(size: 22, weight: .bold)).kerning(-0.2).foregroundStyle(Self.color(M.ink))
      Spacer(minLength: 0)
      Text("New arrival").font(.system(size: 13)).foregroundStyle(Self.color(M.mute))
    }
    .padding(.horizontal, Self.gutter)
    .offset(y: top + 8)
    .allowsHitTesting(false)
  }

  private func drag(_ c: WatchCaseRevealController) -> some Gesture {
    DragGesture(minimumDistance: 6)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !c.dragging { c.dragBegan(now) }
        c.dragChanged(now, dx: v.translation.width, dy: v.translation.height)
      }
      .onEnded { v in
        let dt = 0.25
        let vx = (v.predictedEndTranslation.width - v.translation.width) / dt
        let vy = (v.predictedEndTranslation.height - v.translation.height) / dt
        c.dragEnded(CACurrentMediaTime(), vx: vx, vy: vy)
      }
  }

  // MARK: colour

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }

  /// The paper's colour at screen y (the screen gradient runs bgTop to bgBottom), with an alpha.
  static func paper(_ y: CGFloat, _ h: CGFloat, _ a: Double) -> Color {
    let k = Double(min(1, max(0, y / h)))
    func ch(_ s: Int) -> Double {
      let t = Double(M.bgTop >> UInt32(s) & 255), b = Double(M.bgBottom >> UInt32(s) & 255)
      return (t + (b - t) * k).rounded() / 255
    }
    return Color(.sRGB, red: ch(16), green: ch(8), blue: ch(0), opacity: a)
  }
}

/// The watch's name, price, specs (counting up as the pillow rises), the native dial picker and the reserve button.
struct WatchCaseRevealPanel: View {
  typealias M = WatchCaseRevealMotion
  let controller: WatchCaseRevealController
  let width: CGFloat

  var body: some View {
    TimelineView(.animation) { _ in
      let f = controller.frame(at: CACurrentMediaTime())
      VStack(alignment: .leading, spacing: 0) {
        HStack(alignment: .firstTextBaseline) {
          Text(M.model).font(.system(size: 32, weight: .bold)).kerning(-0.5)
          Spacer(minLength: 0)
          Text(M.price).font(.system(size: 19, weight: .semibold)).monospacedDigit()
        }
        .foregroundStyle(WatchCaseReveal.color(M.ink))
        Text(M.line).font(.system(size: 15)).foregroundStyle(WatchCaseReveal.color(M.mute)).padding(.top, 2)
        HStack(alignment: .top, spacing: 0) {
          ForEach(0..<M.specs.count, id: \.self) { i in spec(i, open: f.pose.open) }
        }
        .padding(.top, 16)
        .padding(.bottom, 18)
        VStack(spacing: 14) {
          WatchCaseRevealDialPicker(selection: f.pickerDial) { i in
            controller.pickDial(i, now: CACurrentMediaTime())
            UISelectionFeedbackGenerator().selectionChanged()
          }
          .equatable()
          Button {
            controller.reserve(CACurrentMediaTime())
            UINotificationFeedbackGenerator().notificationOccurred(.success)
          } label: {
            Label(f.reserved ? "Reserved" : "Reserve in a boutique", systemImage: f.reserved ? "checkmark" : "bag")
          }
          .buttonStyle(.borderedProminent)
          .buttonBorderShape(.capsule)
          .controlSize(.large)
          .tint(WatchCaseReveal.color(M.ink))
          .frame(maxWidth: .infinity)
        }
      }
      .frame(width: width, alignment: .leading)
    }
  }

  private func spec(_ i: Int, open: Double) -> some View {
    let s = M.specs[i]
    let k = M.clamp01((M.specsOf(open) - Double(i) * 0.08) / 0.84)
    return VStack(alignment: .leading, spacing: 1) {
      HStack(alignment: .firstTextBaseline, spacing: 2) {
        Text("\(Int((Double(s.value) * k).rounded()))")
          .font(.system(size: 24, weight: .semibold)).monospacedDigit()
          .frame(width: CGFloat(String(s.value).count * 17 + 6), alignment: .leading)
        Text(s.unit).font(.system(size: 15, weight: .medium))
      }
      .foregroundStyle(WatchCaseReveal.color(M.ink))
      Text(s.label).font(.system(size: 13)).foregroundStyle(WatchCaseReveal.color(M.mute))
    }
    .opacity(0.35 + 0.65 * k)
    .frame(maxWidth: .infinity, alignment: .leading)
  }
}

/// The panel's TimelineView rebuilds its content every frame, and a UISegmentedControl updated mid-touch drops the
/// touch, so the dial picker was dead to the finger. As an Equatable view SwiftUI only updates it when the selection
/// changes.
private struct WatchCaseRevealDialPicker: View, Equatable {
  typealias M = WatchCaseRevealMotion
  let selection: Int
  let pick: (Int) -> Void

  static func == (a: Self, b: Self) -> Bool { a.selection == b.selection }

  var body: some View {
    Picker("Dial", selection: Binding(get: { selection }, set: { i in
      guard i != selection else { return }
      pick(i)
    })) {
      ForEach(0..<M.dials.count, id: \.self) { i in Text(M.dials[i].name).tag(i) }
    }
    .pickerStyle(.segmented)
  }
}
