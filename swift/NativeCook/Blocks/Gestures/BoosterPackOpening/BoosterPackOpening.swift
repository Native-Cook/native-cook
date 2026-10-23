import SwiftUI

/// A sealed foil pack that is torn open with a swipe across its top. The torn strip folds and
/// curls as the tear advances; when the cut reaches the far edge there is a flash, the strip
/// flies off, the mouth opens and slides down while the first card rises and centres. Then
/// five cards are revealed one by one: drag to tilt, tap, flick or use the arrows to advance.
/// The two epic cards turn through their back with a foil sweep, sparkles and a coloured
/// backdrop. The last card stays until "Open another pack" is pressed.
///
/// The scene is drawn with Metal (meshes and materials) under a SwiftUI canvas (the flash,
/// the trace and the sparks), both from the one clock and geometry of `BoosterPackExperience`.
/// Reduce Motion replaces travel with fades.
///
/// - Parameters:
///   - autoPlay: plays the whole opening by itself in a loop of `BoosterPackExperience.loop`
///     seconds, silently. A touch takes over; 2.5 s after the last touch the loop starts again.
///   - muted: plays no sound.
struct BoosterPackOpening: View {
  var autoPlay = false
  var muted = false

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @Environment(\.scenePhase) private var scenePhase
  @State private var experience = BoosterPackExperience()
  @State private var onScreen = false

  var body: some View {
    ZStack {
      BoosterPackStage(experience: experience)
      BoosterPackEffectsOverlay(experience: experience)
      BoosterPackControls(experience: experience, reduceMotion: reduceMotion)
    }
    .ignoresSafeArea()
    .onAppear {
      onScreen = true
      refresh()
    }
    .onDisappear {
      onScreen = false
      refresh()
    }
    .onChange(of: scenePhase) { refresh() }
    .onChange(of: reduceMotion) { refresh() }
    .onChange(of: autoPlay) { refresh() }
    .onChange(of: muted) { refresh() }
  }

  /// Sounds and motion stop when the app leaves the foreground or the block leaves the screen.
  private func refresh() {
    experience.configure(autoPlay: autoPlay, muted: muted, reduceMotion: reduceMotion)
    experience.setFocused(onScreen && scenePhase == .active)
  }
}

/// The controls over the revealed cards: an arrow at each side of the stack, and the ending
/// under the last card.
struct BoosterPackControls: View {
  let experience: BoosterPackExperience
  let reduceMotion: Bool

  @State private var endingShown = false

  private static let hint = Color(red: 0x7D / 255.0, green: 0x87 / 255.0, blue: 0x98 / 255.0)
  private static let label = Color(red: 0x46 / 255.0, green: 0x57 / 255.0, blue: 0x6E / 255.0)

  var body: some View {
    let interface = experience.interface
    let last = interface.labelIndex == BoosterPackCardRevealMotion.CARD_COUNT - 1
    GeometryReader { proxy in
      let height = proxy.size.height
      ZStack(alignment: .top) {
        if !last {
          HStack {
            arrow("‹", "Reveal next card to the left") { experience.advanceCard(-1) }
            Spacer()
            arrow("›", "Reveal next card") { experience.advanceCard(1) }
          }
          .padding(.horizontal, 3)
          .opacity(interface.arrowsVisible ? 0.65 : 0)
          .offset(y: height / 2 - 24)
        } else {
          // The ending waits for the last card's reveal instead of appearing under it.
          VStack(spacing: 14) {
            Text("Every card revealed")
              .font(.system(size: 12))
              .kerning(0.2)
              .foregroundStyle(Self.hint)
            Button(action: experience.replay) {
              Text("Open another pack")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Self.label)
                .padding(.horizontal, 24)
                .frame(minHeight: 46)
                .background(Color.white.opacity(0xAE / 255.0), in: Capsule())
                .overlay(Capsule().strokeBorder(Color.white.opacity(0xD9 / 255.0), lineWidth: 1))
            }
            .buttonStyle(BoosterPackPressStyle())
          }
          .frame(maxWidth: .infinity)
          .opacity(endingShown ? 1 : 0)
          .offset(y: height * 0.835 + (endingShown ? 0 : 25))
          .onAppear {
            withAnimation(.easeOut(duration: 0.42).delay(experience.footerDelay / 1000)) { endingShown = true }
          }
          .onDisappear { endingShown = false }
        }
      }
      .frame(width: proxy.size.width, height: height, alignment: .top)
    }
    .opacity(interface.opacity)
    .allowsHitTesting(interface.interactive)
    .accessibilityHidden(!interface.interactive)
  }

  private func arrow(_ glyph: String, _ label: String, _ action: @escaping () -> Void) -> some View {
    Button(action: action) {
      Text(glyph)
        .font(.system(size: 38, weight: .light))
        .foregroundStyle(.white)
        .frame(width: 44, height: 48)
        .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityLabel(label)
  }
}

/// Dims its label while pressed.
struct BoosterPackPressStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.opacity(configuration.isPressed ? 0.65 : 1)
  }
}
