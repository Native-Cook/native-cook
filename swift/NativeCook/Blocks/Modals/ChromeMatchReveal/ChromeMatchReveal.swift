import SwiftUI

struct ChromeMatchReveal: View {
  static let loop = ChromeMatchRevealMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock = ChromeMatchRevealClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "chromeMatchRevealTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top
      let bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        let size = full.size
        let cy = top + 210
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
          let sample = clock.sample(timeline.date, autoPlay && !reduced, reduced, pinned)
          let f = sample.frame
          ZStack(alignment: .topLeading) {
            ChromeMatchRevealArt.color(0x0b0a0d)
            ZStack(alignment: .topLeading) {
              Text("It's a match!")
                .font(ChromeMatchRevealArt.brush)
                .foregroundStyle(.white)
                .fixedSize()
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.trailing, 34)
                .offset(y: top + 46 + (1 - f.copy) * 12)
                .opacity(f.copy)
                .accessibilityHidden(true)
              Canvas { c, s in ChromeMatchRevealArt.beams(c, s, cy, f, reduced) }
                .allowsHitTesting(false)
              ChromeMatchRevealCard(side: -1, sample: sample, size: size, cy: cy, reduced: reduced)
              ChromeMatchRevealCard(side: 1, sample: sample, size: size, cy: cy, reduced: reduced)
              Canvas { c, s in ChromeMatchRevealArt.overlay(c, s, cy, f, reduced) }
                .allowsHitTesting(false)
              VStack(spacing: 0) {
                (Text("YOU").foregroundColor(ChromeMatchRevealArt.color(0xa58bff))
                 + Text(" AND ").foregroundColor(.white)
                 + Text("PURPLE").foregroundColor(ChromeMatchRevealArt.color(0xff6ad8)))
                  .frame(height: 38)
                Text("LIKED EACH OTHER").foregroundColor(.white).frame(height: 38)
              }
              .font(ChromeMatchRevealArt.hand)
              .tracking(0.5)
              .fixedSize()
              .frame(width: size.width)
              .compositingGroup()
              .offset(y: cy + 118 + 56 - 1 + (1 - f.copy) * 12)
              .opacity(f.copy)
              .accessibilityElement(children: .ignore)
              .accessibilityLabel("It's a match. You and Purple liked each other.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
              .onChanged { value in if pinned == nil { clock.look(value.location) } }
              .onEnded { _ in if pinned == nil { clock.release() } })
            VStack(spacing: 12) {
              Button { clock.message() } label: {
                Text("Send a message").offset(x: -1.0 / 3, y: 1.0 / 3).frame(maxWidth: .infinity).frame(height: 58)
                  .background(ChromeMatchRevealArt.color(0x8b6cf6), in: Capsule())
              }
              .accessibilityLabel("Send a message")
              Button { clock.dislike(reduced) } label: {
                Text("Dislike").offset(x: -1.0 / 3, y: 1.0 / 3).frame(maxWidth: .infinity).frame(height: 58)
                  .background(ChromeMatchRevealArt.color(0x232227), in: Capsule())
              }
              .accessibilityLabel("Dislike")
            }
            .font(ChromeMatchRevealArt.semibold)
            .foregroundStyle(.white)
            .buttonStyle(ChromeMatchRevealButtonStyle())
            .frame(width: size.width - 44)
            .offset(x: 22, y: size.height - bottom - 18 - 128 + (1 - f.buttons) * 18)
            .compositingGroup()
            .opacity(f.buttons)
            .allowsHitTesting(pinned == nil && f.buttons > 0)
          }
          .frame(width: size.width, height: size.height, alignment: .topLeading)
          .clipped()
        }
      }
      .ignoresSafeArea()
    }
    .background(ChromeMatchRevealArt.color(0x0b0a0d))
    .preferredColorScheme(.dark)
    .persistentSystemOverlays(.hidden)
    .onAppear { _ = ChromeMatchRevealArt.fonts; clock.active(true, reduced) }
    .onDisappear { clock.active(false, reduced) }
    .onChange(of: scenePhase) { _, phase in clock.active(phase == .active, reduced) }
    .onChange(of: reduced) { _, value in clock.active(scenePhase == .active, value) }
  }
}

struct ChromeMatchRevealButtonStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
  }
}
