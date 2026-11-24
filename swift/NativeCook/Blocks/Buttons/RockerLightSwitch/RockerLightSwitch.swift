import QuartzCore
import SwiftUI
import UIKit

/// A smart-home room at dusk around a real 3D rocker switch and a brass wall sconce: a press tips the rocker past
/// its middle, it snaps home on a spring with a click, and 120 ms later the filament warms up: the opal globe glows,
/// the wall takes a warm pool of light and the plate throws its shadow. The camera eases round the wall while the
/// native brightness slider dims the lamp and brings it back, then the switch goes off.
///
/// Ported from the React Native block (react-native-filament + Skia): the same switch.glb, converted to USDZ and
/// drawn with SceneKit (RockerLightSwitchScene3D), the same film (RockerLightSwitchMotion), the Skia light layer as
/// a Canvas. Touch: press either half of the rocker, or drag it through its detent; the slider is the system's.
/// 2 s after the last touch the switch goes off and the film restarts. Reduce Motion: the light on, no film.
struct RockerLightSwitch: View {
  typealias M = RockerLightSwitchMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: RockerLightSwitchController?
  @State private var pressing = false

  static let gutter: CGFloat = 20
  /// The sheet's height above the bottom safe area, and its top corners (it overlaps the stage by as much).
  static let panelH: CGFloat = 252
  static let panelR: CGFloat = 28
  private static let hitPad: CGFloat = 18
  private static let dot: CGFloat = 46

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let panelH = Self.panelH + insets.bottom
      let stageH = max(160, H - panelH + Self.panelR)
      ZStack(alignment: .topLeading) {
        Self.color(M.dusk)
        if let controller {
          stage(controller, width: W, height: stageH, top: insets.top)
          header(top: insets.top, width: W)
          hit(controller, width: W, stageH: stageH)
          RockerLightSwitchPanel(controller: controller, width: W, bottom: insets.bottom)
            .frame(width: W, height: panelH, alignment: .top)
            .offset(y: H - panelH)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.dusk).ignoresSafeArea())
    .preferredColorScheme(.dark)
    .statusBarHidden(false)
    .onAppear {
      if controller == nil { controller = RockerLightSwitchController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  private func stage(_ c: RockerLightSwitchController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    let size = CGSize(width: width, height: height)
    return ZStack(alignment: .topLeading) {
      RockerLightSwitchStage3D(controller: c, size: size)
        .frame(width: width, height: height)
      TimelineView(.animation) { _ in
        let f = c.frame(at: CACurrentMediaTime())
        ZStack(alignment: .topLeading) {
          RockerLightSwitchLight(pose: f.pose)
            .frame(width: width, height: height)
          touchDot(f, width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
      }
      // a little shade under the status bar and the app's name, so both read on the lit wall
      LinearGradient(stops: [.init(color: Color(.sRGB, red: 12 / 255, green: 14 / 255, blue: 22 / 255, opacity: 0.42), location: 0),
                             .init(color: Color(.sRGB, red: 12 / 255, green: 14 / 255, blue: 22 / 255, opacity: 0.14), location: 0.6),
                             .init(color: Color(.sRGB, red: 12 / 255, green: 14 / 255, blue: 22 / 255, opacity: 0), location: 1)],
                     startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: top + 64)
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .clipped()
    .allowsHitTesting(false)
  }

  /// The film's finger: a soft dot on the half being pressed.
  private func touchDot(_ f: RockerLightSwitchController.Frame, width: CGFloat, height: CGFloat) -> some View {
    let p = M.V3(M.rockerPivot.x, M.rockerPivot.y + f.ghost.half * M.rockerHalfH * 0.55, M.rockerPivot.z + M.rockerFaceZ)
    let o = M.project(f.pose.camera, Double(width), Double(height), p)
    return Circle()
      .fill(Color.white.opacity(0.34))
      .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .shadow(color: .black.opacity(0.25), radius: 8, x: 0, y: 3)
      .scaleEffect(1 - 0.16 * f.ghost.pressed)
      .opacity(f.ghost.opacity * 0.9)
      .offset(x: o.x - Self.dot / 2, y: o.y - Self.dot / 2)
  }

  private func header(top: CGFloat, width: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.app).font(.system(size: 17, weight: .semibold)).foregroundStyle(Color(.sRGB, red: 1, green: 252 / 255, blue: 246 / 255, opacity: 0.96))
      Spacer(minLength: 0)
      Text("Evening").font(.system(size: 15)).foregroundStyle(Color(.sRGB, red: 1, green: 252 / 255, blue: 246 / 255, opacity: 0.72))
    }
    .frame(width: width - Self.gutter * 2, height: 22)
    .offset(x: Self.gutter, y: top + 10)
    .allowsHitTesting(false)
  }

  /// The touch target: the plate's outline on screen with the camera at rest, grown a little.
  private func hit(_ c: RockerLightSwitchController, width: CGFloat, stageH: CGFloat) -> some View {
    let o = M.project(M.cameraAt(0, 0), Double(width), Double(stageH), M.V3(M.rockerPivot.x, M.rockerPivot.y, M.rockerFaceZ + M.rockerPivot.z))
    let w = M.plateHalfW * 2 * o.ppm, h = M.plateHalfH * 2 * o.ppm
    return Color.clear
      .contentShape(Rectangle())
      .frame(width: w + Self.hitPad * 2, height: h + Self.hitPad * 2)
      .gesture(
        DragGesture(minimumDistance: 0)
          .onChanged { v in
            let now = CACurrentMediaTime()
            if !pressing {
              pressing = true
              c.pressBegan(now, top: v.startLocation.y < h / 2 + Self.hitPad)
            }
            c.pressMoved(now, dy: v.translation.height)
          }
          .onEnded { v in
            pressing = false
            c.pressEnded(CACurrentMediaTime(), vy: (v.predictedEndTranslation.height - v.translation.height) / 0.25)
          }
      )
      .offset(x: o.x - w / 2 - Self.hitPad, y: o.y - h / 2 - Self.hitPad)
      .accessibilityElement()
      .accessibilityLabel("Light switch")
      .accessibilityHint("Press the top half to turn the light on, the bottom half to turn it off, or drag to tip it")
      .accessibilityAddTraits(.isButton)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

/// The room's sheet: its name, how many lights are on, and the sconce's row with the brightness slider. It reads
/// the controller every frame but only rebuilds its content when what it shows changes.
struct RockerLightSwitchPanel: View {
  typealias M = RockerLightSwitchMotion
  let controller: RockerLightSwitchController
  let width: CGFloat
  let bottom: CGFloat

  var body: some View {
    TimelineView(.animation) { _ in
      let f = controller.frame(at: CACurrentMediaTime())
      RockerLightSwitchSheet(lit: f.lit, value: f.slider, width: width, bottom: bottom,
                             change: { controller.setBright($0, now: CACurrentMediaTime()) },
                             editing: { controller.setSliding($0, now: CACurrentMediaTime()) })
        .equatable()
    }
  }
}

private struct RockerLightSwitchSheet: View, Equatable {
  typealias M = RockerLightSwitchMotion
  let lit: Bool
  let value: Double
  let width: CGFloat
  let bottom: CGFloat
  let change: (Double) -> Void
  let editing: (Bool) -> Void

  static func == (a: Self, b: Self) -> Bool { a.lit == b.lit && a.value == b.value && a.width == b.width && a.bottom == b.bottom }

  var body: some View {
    let R = RockerLightSwitch.self
    let inner = min(width, 560) - R.gutter * 2
    let shape = UnevenRoundedRectangle(topLeadingRadius: R.panelR, topTrailingRadius: R.panelR, style: .circular)
    VStack(alignment: .leading, spacing: 0) {
      Text(M.room).font(.system(size: 28, weight: .bold)).kerning(-0.4).foregroundStyle(R.color(M.ink))
        .frame(height: 34)
      Text("\(M.otherLightsOn + (lit ? 1 : 0)) lights on").font(.system(size: 15)).monospacedDigit().foregroundStyle(R.color(M.mute))
        .frame(height: 20).padding(.top, 2)
      VStack(spacing: 0) {
        HStack(alignment: .firstTextBaseline) {
          Text(M.lightName).font(.system(size: 17, weight: .semibold)).foregroundStyle(R.color(M.ink))
          Spacer(minLength: 0)
          Text(lit ? "On" : "Off").font(.system(size: 15, weight: .medium)).foregroundStyle(lit ? R.color(0xB36F0E) : R.color(M.mute))
        }
        .frame(height: 22)
        HStack(alignment: .firstTextBaseline) {
          Text("Brightness").font(.system(size: 15)).foregroundStyle(R.color(M.mute))
          Spacer(minLength: 0)
          Text("\(Int((value * 100).rounded()))%").font(.system(size: 15, weight: .medium)).monospacedDigit().foregroundStyle(R.color(M.ink))
        }
        .frame(height: 20)
        .padding(.top, 12)
        .padding(.bottom, 4)
        // the system's slider: the film moves it by value; a finger uses it the native way
        Slider(value: Binding(get: { value }, set: { change($0) }), in: M.brightMin...1, onEditingChanged: editing)
          .tint(R.color(M.amber))
          .frame(height: 30)
          .accessibilityLabel("Brightness")
      }
      .padding(.horizontal, 16)
      .padding(.top, 14)
      .padding(.bottom, 12)
      .background(
        RoundedRectangle(cornerRadius: 20, style: .circular)
          .fill(R.color(M.card))
          // the card's tight contact shadow
          .shadow(color: R.color(0x3A2E1E, 0.08), radius: 10, x: 0, y: 3)
      )
      .overlay(RoundedRectangle(cornerRadius: 20, style: .circular).strokeBorder(R.color(M.ink, 0.07), lineWidth: 1 / 3))
      .padding(.top, 18)
    }
    .frame(width: inner, alignment: .leading)
    .padding(.top, 22)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    .background(
      shape.fill(R.color(M.panel))
        // the sheet's long soft shadow up the wall
        .shadow(color: R.color(0x0B0D14, 0.28), radius: 24, x: 0, y: -8)
    )
    .overlay(alignment: .top) {
      shape.strokeBorder(Color.white.opacity(0.9), lineWidth: 1 / 3)
        .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .bottom)
          .frame(height: R.panelR).frame(maxHeight: .infinity, alignment: .top))
    }
    .environment(\.colorScheme, .light)
  }
}
