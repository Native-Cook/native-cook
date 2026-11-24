import QuartzCore
import SwiftUI
import simd

/// A podcast recording app around a real 3D four-channel mixing desk: brushed aluminium on a charcoal wedge between
/// walnut cheeks, a fader, an eight-LED meter, a lit mute button and a gain knob per channel. The film rides two faders
/// (Host settles on 0 dB), mutes SFX, recalls the Interview preset (every fader and knob glides there) and then Intro.
///
/// Ported from the React Native block (react-native-filament + @expo/ui): the same mixer.glb, converted to USDZ and
/// drawn with SceneKit, the same film, the system's segmented control for presets. Touch: drag any fader (it snaps
/// lightly at 0 dB), tap a mute button, pick a preset. 2 s after the last touch Intro is recalled and the film
/// restarts. Reduce Motion: the Interview mix with the meters standing still; touches change the pose at once.
struct MixingDeskFader: View {
  typealias M = MixingDeskFaderMotion
  var autoPlay: Bool = true
  /// A finger moved a fader (channel, 0..1 of its travel; 0.75 is 0 dB).
  var onFader: ((Int, Double) -> Void)? = nil
  /// A finger muted or unmuted a channel.
  var onMute: ((Int, Bool) -> Void)? = nil

  static let loop: Double = M.loop

  private enum Touch { case none, pending, fader, refused }

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: MixingDeskFaderController?
  @State private var shown = false
  @State private var preset = 0
  @State private var stillTick = 0
  @State private var touch = Touch.none

  static let gutter: CGFloat = 20
  static let panelR: CGFloat = 28
  private static let top: CGFloat = 18
  private static let headH: CGFloat = 30
  private static let gap: CGFloat = 14
  private static let pickerH: CGFloat = 36
  private static let stripsH: CGFloat = 62
  private static let bottomGap: CGFloat = 16
  static let panelH: CGFloat = top + headH + gap + pickerH + gap + stripsH + bottomGap
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
    // a dark studio: the system controls take their dark look whatever the app's appearance
    .environment(\.colorScheme, .dark)
    .onAppear {
      guard controller == nil else { return }
      let c = MixingDeskFaderController(autoPlay: autoPlay, reduced: reduceMotion)
      controller = c
      preset = c.frame(at: CACurrentMediaTime()).preset
    }
  }

  // MARK: the stage

  private func stage(_ c: MixingDeskFaderController, width: CGFloat, height: CGFloat, top: CGFloat) -> some View {
    ZStack(alignment: .topLeading) {
      MixingDeskFaderStage3D(controller: c, size: CGSize(width: width, height: height)) {
        withAnimation(.easeInOut(duration: 0.32)) { shown = true }
      }
      .frame(width: width, height: height)
      .opacity(shown ? 1 : 0)
      .allowsHitTesting(false)

      if !c.reduced {
        // the film's finger on the desk, on the cap it rides or the button it taps
        TimelineView(.animation) { _ in
          deskGhost(c.frame(at: CACurrentMediaTime()), width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
      }

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
    .gesture(deskGesture(c, width: width, height: height))
    .accessibilityElement()
    .accessibilityLabel("Mixing desk")
    .accessibilityHint("Drag a fader up or down; tap a mute button")
  }

  /// One gesture for the desk: a mostly vertical drag (after 6 pt, refused past 40 pt sideways) takes the fader whose
  /// track is nearest where the finger came down; a touch that stays within 10 pt is a tap on a mute button.
  private func deskGesture(_ c: MixingDeskFaderController, width: CGFloat, height: CGFloat) -> some Gesture {
    DragGesture(minimumDistance: 0, coordinateSpace: .local)
      .onChanged { v in
        let now = CACurrentMediaTime()
        let tr = SIMD2(Double(v.translation.width), Double(v.translation.height))
        switch touch {
        case .none:
          touch = .pending
          _ = c.pick(at: SIMD2(Double(v.startLocation.x), Double(v.startLocation.y)), width: Double(width), height: Double(height), now: now)
        case .pending:
          if abs(tr.x) > 40 {
            touch = .refused
          } else if abs(tr.y) > 6 {
            touch = c.dragBegan(now: now) ? .fader : .refused
          }
        case .fader:
          if c.dragMoved(translation: tr, now: now) { UISelectionFeedbackGenerator().selectionChanged() }
        case .refused:
          break
        }
      }
      .onEnded { v in
        let now = CACurrentMediaTime()
        let tr = SIMD2(Double(v.translation.width), Double(v.translation.height))
        switch touch {
        case .fader:
          if let (ch, to) = c.dragEnded(velocity: SIMD2(Double(v.velocity.width), Double(v.velocity.height)), now: now) {
            onFader?(ch, to)
          }
        case .pending where simd_length(tr) <= 10:
          c.dragCancelled(now: now)
          if let (ch, muted) = c.tap(at: SIMD2(Double(v.location.x), Double(v.location.y)), width: Double(width), height: Double(height), now: now) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onMute?(ch, muted)
          }
        default:
          c.dragCancelled(now: now)
        }
        touch = .none
        if c.reduced { stillTick += 1 }
      }
  }

  private func deskGhost(_ f: MixingDeskFaderController.Frame, width: CGFloat, height: CGFloat) -> some View {
    let g = M.deskGhost(f.t)
    let k = f.pose.lean
    let s = g.mute
      ? M.muteOnScreen(g.channel, k: k, width: Double(width), height: Double(height))
      : M.capOnScreen(g.channel, f.pose.p[g.channel], k: k, width: Double(width), height: Double(height))
    return Circle()
      .fill(Self.color(M.ink, 0.18))
      .overlay(Circle().strokeBorder(Self.color(M.ink, 0.6), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .scaleEffect(1 - 0.16 * g.pressed)
      .opacity(f.filming ? g.opacity * 0.9 : 0)
      .offset(x: CGFloat(s.x) - Self.dot / 2, y: CGFloat(s.y) - Self.dot / 2)
  }

  private func header(top: CGFloat, width: CGFloat) -> some View {
    HStack(alignment: .firstTextBaseline) {
      Text(M.app).font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.color(M.ink))
      Spacer(minLength: 0)
      Text(M.studio).font(.system(size: 15)).foregroundStyle(Self.color(M.mute))
    }
    .frame(width: width - Self.gutter * 2, height: 22)
    .offset(x: Self.gutter, y: top + 10)
    .allowsHitTesting(false)
  }

  // MARK: the sheet

  private func panel(_ c: MixingDeskFaderController, width: CGFloat, height: CGFloat) -> some View {
    let inner = min(width, 560) - Self.gutter * 2
    let sheet = UnevenRoundedRectangle(topLeadingRadius: Self.panelR, topTrailingRadius: Self.panelR, style: .continuous)
    return ZStack(alignment: .top) {
      sheet
        .fill(Self.color(M.panel))
        .shadow(color: Color.black.opacity(0.45), radius: 14, x: 0, y: -8)
      sheet
        .stroke(Color.white.opacity(0.14), lineWidth: 0.33)
        .frame(height: Self.panelR)
        .frame(maxHeight: .infinity, alignment: .top)
        .mask(alignment: .top) { Rectangle().frame(height: Self.panelR) }
      ZStack(alignment: .topLeading) {
        column(c, inner: inner)
        if !c.reduced {
          // the film's finger on the presets; the film's preset reaches the control only when it changes
          TimelineView(.animation) { _ in
            let f = c.frame(at: CACurrentMediaTime())
            presetGhost(f, inner: inner)
              .onChange(of: f.preset) { _, v in preset = v }
          }
          .frame(width: inner, height: Self.panelH, alignment: .topLeading)
          .allowsHitTesting(false)
        }
      }
      .frame(width: inner, height: Self.panelH, alignment: .topLeading)
    }
    .frame(width: width, height: height, alignment: .top)
  }

  private func column(_ c: MixingDeskFaderController, inner: CGFloat) -> some View {
    VStack(alignment: .leading, spacing: 0) {
      HStack {
        Text(M.episode)
          .font(.system(size: 20, weight: .bold))
          .tracking(-0.3)
          .foregroundStyle(Self.color(M.ink))
        Spacer(minLength: 8)
        recPill(c)
      }
      .frame(width: inner, height: Self.headH)
      Picker("Preset", selection: presetBinding(c)) {
        ForEach(0..<M.presetNames.count, id: \.self) { i in Text(M.presetNames[i]).tag(i) }
      }
      .pickerStyle(.segmented)
      .frame(width: inner, height: Self.pickerH)
      .padding(.top, Self.gap)
      strips(c, inner: inner)
        .padding(.top, Self.gap)
    }
    .padding(.top, Self.top)
    .frame(width: inner, alignment: .topLeading)
  }

  private func presetBinding(_ c: MixingDeskFaderController) -> Binding<Int> {
    Binding(
      get: { preset },
      set: { v in
        guard v != preset else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        preset = v
        c.choose(preset: v, now: CACurrentMediaTime())
        if c.reduced { stillTick += 1 }
      }
    )
  }

  private func recPill(_ c: MixingDeskFaderController) -> some View {
    HStack(spacing: 6) {
      TimelineView(.animation(minimumInterval: nil, paused: c.reduced)) { _ in
        let t = c.frame(at: CACurrentMediaTime()).t
        Circle()
          .fill(Self.color(M.rec))
          .frame(width: 8, height: 8)
          .opacity(0.45 + 0.55 * (0.5 + 0.5 * cos(2 * Double.pi * M.recBreaths * t / M.loop)))
      }
      .frame(width: 8, height: 8)
      Text("REC")
        .font(.system(size: 13, weight: .bold))
        .tracking(0.4)
        .foregroundStyle(Self.color(M.rec))
    }
    .padding(.horizontal, 10)
    .frame(height: 26)
    .background(Capsule().fill(Self.color(M.rec, 0.14)))
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("Recording")
  }

  private func strips(_ c: MixingDeskFaderController, inner: CGFloat) -> some View {
    let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
    return TimelineView(.animation(minimumInterval: nil, paused: c.reduced)) { _ in
      let p = c.frame(at: CACurrentMediaTime()).pose.p
      HStack(spacing: 0) {
        ForEach(0..<M.channels, id: \.self) { ch in
          HStack(spacing: 0) {
            if ch > 0 { Self.color(M.ink, 0.09).frame(width: 0.33, height: 34) }
            VStack(alignment: .leading, spacing: 2) {
              HStack(spacing: 6) {
                Circle().fill(Self.color(M.channelColours[ch])).frame(width: 7, height: 7)
                Text(M.names[ch])
                  .font(.system(size: 13))
                  .foregroundStyle(Self.color(M.mute))
                  .frame(height: 18)
              }
              Text(M.dbLabel(p[ch], muted: p[8 + ch]))
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .foregroundStyle(Self.color(M.ink))
                .frame(height: 20)
            }
            .padding(.leading, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
          }
          .frame(maxWidth: .infinity)
        }
      }
    }
    .id(stillTick)
    .frame(width: inner, height: Self.stripsH)
    .background {
      shape.fill(Self.color(M.card))
      shape.strokeBorder(Self.color(M.ink, 0.09), lineWidth: 0.33)
    }
  }

  private func presetGhost(_ f: MixingDeskFaderController.Frame, inner: CGFloat) -> some View {
    let g = M.presetGhost(f.t)
    let cell = inner / CGFloat(M.presetNames.count)
    return Circle()
      .fill(Self.color(M.ink, 0.14))
      .overlay(Circle().strokeBorder(Self.color(M.ink, 0.5), lineWidth: 1.5))
      .frame(width: Self.dot, height: Self.dot)
      .scaleEffect(1 - 0.16 * g.pressed)
      .opacity(g.opacity * 0.9 * (f.filming ? 1 : 0))
      .offset(x: cell * (CGFloat(g.preset) + 0.5) - Self.dot / 2,
              y: Self.top + Self.headH + Self.gap + Self.pickerH / 2 - Self.dot / 2)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

#Preview {
  MixingDeskFader()
}
