import SwiftUI

struct GlassPlaylistFolder: View {
  static let loop = GlassPlaylistFolderMotion.loop
  var autoPlay = false
  var previewTime: Double? = nil
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var phase
  @StateObject private var clock = GlassPlaylistFolderClock()
  private var pinned: Double? {
    #if DEBUG
      if let v = UserDefaults.standard.object(forKey: "glassPlaylistFolderTime") {
        return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
      }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top
      let bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        TimelineView(
          .animation(
            paused: pinned != nil || phase != .active || (reduced && !clock.running)
              || ((!autoPlay || previewTime != nil) && !clock.running))
        ) { timeline in
          let f = clock.sample(
            timeline.date, autoPlay && !reduced && previewTime == nil, reduced, pinned, previewTime)
          ZStack(alignment: .topLeading) {
            GlassPlaylistFolderScene(frame: f, size: full.size, top: top)
              .overlay {
                if pinned == nil && previewTime == nil {
                  GlassPlaylistFolderTouch { state, p, d, v in
                    clock.touch(state, p, d, v, full.size.width / 2, top + 318, reduced)
                  }.accessibilityHidden(true)
                }
              }
            HStack(alignment: .firstTextBaseline) {
              Text("Library").font(.system(size: 30, weight: .bold)).tracking(-0.6).foregroundStyle(
                .white)
              Spacer()
              Text("Folders").font(.system(size: 15, weight: .medium)).foregroundStyle(
                GlassPlaylistFolderArt.color(0xc8ffd7, 0.6))
            }.padding(.horizontal, 20).padding(.top, top + 12).allowsHitTesting(false)
            VStack(alignment: .leading, spacing: 0) {
              Text("FOLDER · 4 PLAYLISTS").font(.system(size: 12, weight: .semibold)).tracking(1.6)
                .foregroundStyle(GlassPlaylistFolderArt.color(0x96ffb4, 0.7))
              Text("Late Night Drive").font(.system(size: 30, weight: .bold)).tracking(-0.6)
                .foregroundStyle(.white).padding(.top, 6)
              Text("96 songs · 5 h 12 min · updated today").font(.system(size: 15))
                .foregroundStyle(.white.opacity(0.55)).padding(.top, 4)
              Text("▶  Play all").font(.system(size: 16, weight: .bold))
                .foregroundStyle(GlassPlaylistFolderArt.color(0x04170a))
                .padding(.horizontal, 26).frame(height: 50)
                .background(GlassPlaylistFolderArt.color(0x4ce07a), in: Capsule())
                .scaleEffect(1 - f.press * 0.04)
                .overlay {
                  if pinned == nil {
                    GlassPlaylistFolderTouch(tapOnly: true) { state, p, _, _ in
                      if state == .began { clock.playBegin(reduced) }
                      if state == .ended || state == .cancelled || state == .failed {
                        clock.playEnd(state == .ended && p.x >= 0 && p.y >= 0 && p.y <= 50, reduced)
                      }
                    }.accessibilityHidden(true)
                  }
                }
                .accessibilityElement(children: .ignore).accessibilityLabel("Play")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction {
                  if pinned == nil {
                    clock.playBegin(reduced)
                    clock.playEnd(true, reduced)
                  }
                }
                .padding(.top, 20)
            }.fixedSize(horizontal: false, vertical: true)
              .compositingGroup()
              .opacity(1 - GlassPlaylistFolderMotion.clamp(f.s.open) * 0.15)
              .offset(y: GlassPlaylistFolderMotion.clamp(f.s.open) * 6)
              .padding(.horizontal, 24).padding(.bottom, bottom + 28)
              .frame(width: full.size.width, height: full.size.height, alignment: .bottomLeading)
          }.frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
        }
      }.ignoresSafeArea()
    }.background(GlassPlaylistFolderArt.color(0x050806).ignoresSafeArea())
      .preferredColorScheme(.dark).dynamicTypeSize(.medium)
      .onAppear { clock.reset(reduced) }
      .onChange(of: reduced) { _, v in clock.reset(v) }
      .onChange(of: phase) { _, _ in clock.active() }
      .onChange(of: autoPlay) { _, _ in clock.active() }
  }
}
