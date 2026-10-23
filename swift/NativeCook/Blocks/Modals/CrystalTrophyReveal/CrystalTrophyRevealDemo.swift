import SwiftUI

struct CrystalTrophyRevealDemo: View {
  var body: some View {
    CrystalTrophyReveal(autoPlay: true)
  }
}

/// A full-screen theatre. Set autoPlay for the gallery's repeating performance.
struct CrystalTrophyReveal: View {
  var autoPlay = false
  var onClose: (() -> Void)?
  var onShare: (() -> Void)?
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @Environment(\.scenePhase) private var scenePhase

  var body: some View {
    CrystalTrophyRevealSurface(
      autoPlay: autoPlay, reduced: reduceMotion,
      active: scenePhase == .active, onClose: onClose, onShare: onShare
    )
    .ignoresSafeArea()
    .statusBarHidden()
    .persistentSystemOverlays(.hidden)
  }
}

private struct CrystalTrophyRevealSurface: UIViewRepresentable {
  let autoPlay: Bool
  let reduced: Bool
  let active: Bool
  let onClose: (() -> Void)?
  let onShare: (() -> Void)?

  func makeUIView(context: Context) -> CrystalTrophyRevealView {
    CrystalTrophyRevealView(autoPlay: autoPlay, reduced: reduced)
  }

  func updateUIView(_ view: CrystalTrophyRevealView, context: Context) {
    view.motion.reduced = reduced
    view.setActive(active)
    view.onClose = onClose
    view.onShare = onShare
  }

  static func dismantleUIView(_ view: CrystalTrophyRevealView, coordinator: ()) {
    view.setActive(false)
  }
}

#Preview {
  CrystalTrophyRevealDemo()
}
