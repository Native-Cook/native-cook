import QuartzCore
import SwiftUI
import UIKit

/// A now playing screen with nothing in the studio but a 12-inch record and its album sleeve, floating upright over
/// soft paper with soft contact shadows. In a 15.8-second film the record slides out of its sleeve, the sleeve drifts
/// back, play spins the record up, it turns a full 360 deg in the air (edge, B side, edge, A side), a finger turns it
/// to scrub the song forward, the volume goes up and back, forward skips 15 s, pause runs it down, back returns to
/// 0:00, and the record slides back into its sleeve.
///
/// Ported from the React Native block (react-native-filament + Skia + @expo/ui): the same vinyl.glb, converted to
/// USDZ and drawn with SceneKit (BoomboxNowPlayingScene3D), the same film (BoomboxNowPlayingMotion), the system's
/// sliders and buttons. Touch: turn the record with a finger to move through the song (10 s a radian, a detent every
/// 15 s), tap it to play or pause, scrub, skip, set the volume; 2 s after the last touch the record stops and the
/// scene eases back into the film. Reduce Motion: the record out in the middle with a working player.
struct BoomboxNowPlaying: View {
  typealias M = BoomboxNowPlayingMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: BoomboxNowPlayingController?

  /// Share of the screen the 3D stage covers, from the top.
  private static let stageShare: CGFloat = 0.63

  var body: some View {
    GeometryReader { geo in
      let insets = geo.safeAreaInsets
      let W = geo.size.width
      let H = geo.size.height + insets.top + insets.bottom
      let scale = min(1.1, max(0.9, W / 393))
      let column = min(W - 48 * scale, 460)
      let stageH = (H * Self.stageShare).rounded()
      ZStack(alignment: .topLeading) {
        LinearGradient(stops: [.init(color: Self.color(M.paperTop), location: 0), .init(color: Self.color(M.paperBottom), location: 0.55),
                               .init(color: Self.color(M.paperBottom), location: 1)],
                       startPoint: .top, endPoint: .bottom)
        if let controller {
          stage(controller, width: W, height: stageH, scale: scale)
          header(top: insets.top, scale: scale)
            .frame(width: W)
          VStack(spacing: 0) {
            Spacer(minLength: 0)
            BoomboxNowPlayingPlayer(controller: controller, column: column, scale: scale)
          }
          .frame(width: column, height: H - insets.bottom - 14 * scale, alignment: .bottomLeading)
          .offset(x: (W - column) / 2)
        }
      }
      .frame(width: W, height: H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.paperTop).ignoresSafeArea())
    .preferredColorScheme(.light)
    .statusBarHidden(false)
    .onAppear {
      if controller == nil { controller = BoomboxNowPlayingController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  private func stage(_ c: BoomboxNowPlayingController, width: CGFloat, height: CGFloat, scale: CGFloat) -> some View {
    let fade = 56 * scale
    return ZStack(alignment: .topLeading) {
      BoomboxNowPlayingShadows(controller: c)
        .frame(width: width, height: height)
      BoomboxNowPlayingStage3D(controller: c, size: CGSize(width: width, height: height))
        .frame(width: width, height: height)
        .allowsHitTesting(false)
      // the stage ends above the player: a close shot fades into the paper instead of running behind the type
      LinearGradient(colors: [Self.color(M.paperBottom, 0), Self.color(M.paperBottom)], startPoint: .top, endPoint: .bottom)
        .frame(width: width, height: fade)
        .offset(y: height - fade)
        .allowsHitTesting(false)
      Color.clear
        .contentShape(Rectangle())
        .frame(width: width, height: height)
        .gesture(rotary(c, width: width, height: height))
        .onTapGesture {
          UIImpactFeedbackGenerator(style: .light).impactOccurred()
          c.toggle(CACurrentMediaTime())
        }
        .accessibilityElement()
        .accessibilityLabel("A vinyl record and its sleeve floating in a photo studio")
        .accessibilityHint("Turn the record with a finger to move through the song, tap to play or pause")
    }
    .frame(width: width, height: height, alignment: .topLeading)
    .clipped()
  }

  private func header(top: CGFloat, scale: CGFloat) -> some View {
    VStack(spacing: 0) {
      Capsule().fill(Self.color(M.ink, 0.18)).frame(width: 36 * scale, height: 5 * scale)
      Text("Playing from Night Drive")
        .font(.system(size: 13 * scale, weight: .semibold))
        .foregroundStyle(Self.color(M.secondary))
        .padding(.top, 10 * scale)
    }
    .padding(.top, top + 6 * scale)
    .allowsHitTesting(false)
  }

  /// Rotary scrub: a finger on the record turns it about its centre, a minute of song a turn, a detent every 15 s.
  private func rotary(_ c: BoomboxNowPlayingController, width: CGFloat, height: CGFloat) -> some Gesture {
    let tick = UISelectionFeedbackGenerator()
    return DragGesture(minimumDistance: 6)
      .onChanged { v in
        let now = CACurrentMediaTime()
        if !c.dragging {
          c.dragBegan(now, x: Double(v.location.x), y: Double(v.location.y), width: Double(width), height: Double(height))
          tick.prepare()
          return
        }
        let before = c.frame(at: now).song
        let after = c.dragChanged(now, x: Double(v.location.x), y: Double(v.location.y))
        if floor(before / 15) != floor(after / 15) { tick.selectionChanged() }
      }
      .onEnded { _ in c.dragEnded(CACurrentMediaTime()) }
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}

/// The title, the artist with the now playing bars, and the system's controls: the song slider with its times, back,
/// play/pause and forward, the volume slider between two speakers. The film and the record drive their values.
struct BoomboxNowPlayingPlayer: View {
  typealias M = BoomboxNowPlayingMotion
  let controller: BoomboxNowPlayingController
  let column: CGFloat
  let scale: CGFloat

  var body: some View {
    TimelineView(.animation) { _ in
      let f = controller.frame(at: CACurrentMediaTime())
      VStack(alignment: .leading, spacing: 0) {
        Text("Overpass Lights")
          .font(.system(size: 28 * scale, weight: .bold))
          .kerning(-0.4)
          .lineLimit(1)
          .minimumScaleFactor(0.75)
          .foregroundStyle(BoomboxNowPlaying.color(M.ink))
          .frame(height: 34 * scale)
        HStack(spacing: 8 * scale) {
          Text("Juno Vale")
            .font(.system(size: 20 * scale))
            .lineLimit(1)
            .foregroundStyle(BoomboxNowPlaying.color(M.secondary))
            .frame(height: 25 * scale)
          HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<3, id: \.self) { k in
              RoundedRectangle(cornerRadius: 1.5)
                .fill(BoomboxNowPlaying.color(M.barColor))
                .frame(width: 3, height: 13)
                .scaleEffect(x: 1, y: max(0.15, M.barAt(k, f.song, f.level) * (0.4 + 0.6 * f.volume)), anchor: .bottom)
            }
          }
          .frame(height: 13)
        }
        .padding(.top, 2 * scale)
        VStack(spacing: 4 * scale) {
          Slider(value: Binding(get: { f.song / M.trackSeconds },
                                set: { controller.scrub(CACurrentMediaTime(), fraction: $0) }),
                 in: 0...1,
                 onEditingChanged: { controller.setScrubbing($0, now: CACurrentMediaTime()) })
            .tint(BoomboxNowPlaying.color(M.ink))
            .frame(width: column, height: 34)
            .accessibilityLabel("Song position")
          HStack {
            Text(M.clock(f.song))
            Spacer(minLength: 0)
            Text("-" + M.clock(M.trackSeconds - f.song))
          }
          .font(.system(size: 12, weight: .semibold).monospacedDigit())
          .foregroundStyle(BoomboxNowPlaying.color(M.secondary))
        }
        .padding(.top, 14 * scale)
        transport(playing: f.playing)
          .frame(width: column)
          .padding(.top, 8 * scale)
        volume(f.volume)
          .frame(width: column)
          .padding(.top, 8 * scale)
      }
      .frame(width: column, alignment: .leading)
    }
  }

  private func transport(playing: Bool) -> some View {
    HStack(spacing: 44 * scale) {
      button("backward.fill", size: 30, frame: 56, label: "Back to the start") {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        controller.seek(CACurrentMediaTime(), forward: false)
      }
      button(playing ? "pause.fill" : "play.fill", size: 40, frame: 64, label: playing ? "Pause" : "Play") {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        controller.toggle(CACurrentMediaTime())
      }
      button("forward.fill", size: 30, frame: 56, label: "Forward") {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        controller.seek(CACurrentMediaTime(), forward: true)
      }
    }
  }

  private func button(_ symbol: String, size: CGFloat, frame: CGFloat, label: String, action: @escaping () -> Void) -> some View {
    Button(action: action) {
      Image(systemName: symbol)
        .font(.system(size: size * scale))
        .foregroundStyle(BoomboxNowPlaying.color(M.ink))
    }
    .buttonStyle(.plain)
    .frame(width: frame * scale, height: frame * scale)
    .accessibilityLabel(label)
  }

  private func volume(_ v: Double) -> some View {
    let icon = 18 * scale
    return HStack(spacing: 0) {
      Image(systemName: "speaker.fill").font(.system(size: icon * 0.8))
      Slider(value: Binding(get: { v }, set: { controller.setVolume($0, now: CACurrentMediaTime()) }), in: 0...1)
        .tint(BoomboxNowPlaying.color(M.ink))
        .frame(width: column - 2 * icon - 20 * scale, height: 34)
        .padding(.horizontal, 10 * scale)
        .accessibilityLabel("Volume")
      Image(systemName: "speaker.wave.3.fill").font(.system(size: icon * 0.8))
    }
    .foregroundStyle(BoomboxNowPlaying.color(M.secondary))
  }
}
