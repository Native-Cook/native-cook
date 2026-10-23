// UIKit owns the display clock and transforms inside this SwiftUI boundary.
import SwiftUI

struct AuraWidgetSurface: UIViewRepresentable {
  let machine: AuraMachine
  let words: AuraWords

  func makeUIView(context: Context) -> AuraWidgetView {
    AuraWidgetView(machine: machine, words: words)
  }

  func updateUIView(_ view: AuraWidgetView, context: Context) {}

  static func dismantleUIView(_ view: AuraWidgetView, coordinator: ()) {
    view.machine.stop()
  }
}
