import SwiftUI

struct SmokedClocheRevealSurface: UIViewRepresentable {
  var frame: SmokedClocheRevealMotion.Frame
  var clock: SmokedClocheRevealClock
  var pinned: Bool, reduced: Bool
  func makeUIView(context: Context) -> SmokedClocheRevealPage { SmokedClocheRevealPage() }
  func updateUIView(_ view: SmokedClocheRevealPage, context: Context) {
    view.clock=clock;view.pinned=pinned;view.reduced=reduced;view.sample=frame;view.update()
  }
}
