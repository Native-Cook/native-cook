// Aura's page and widget share one origin before the first drawable is presented.
import SwiftUI

struct AuraWalletWidgetDemo: View {
  @State private var machine = AuraMachine()
  @State private var words = AuraWords()
  @State private var pageDrawn = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.accessibilityReduceTransparency) private var opaque

  init() {
    AuraPipelineStore.shared.prepare()
  }

  var body: some View {
    GeometryReader { proxy in
      let width = auraWidth(proxy.size.width)
      let x = floor((proxy.size.width - width) / 2)
      let y = round((proxy.size.height - width) / 2 - AuraDesign.page.lift)
      ZStack(alignment: .topLeading) {
        if AuraPipelineStore.shared.resources != nil {
          AuraPageSurface(width: width, origin: CGPoint(x: x, y: y + proxy.safeAreaInsets.top)) {
            pageDrawn = true
          }
          .frame(
            width: proxy.size.width,
            height: proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom
          )
          .offset(y: -proxy.safeAreaInsets.top)
          if pageDrawn {
            AuraWidgetSurface(machine: machine, words: words)
              .frame(width: width, height: width)
              .offset(x: x, y: y)
            AuraHint(words: words)
              .opacity(words.arrived ? 1 : 0)
              .animation(.easeOut(duration: AuraFlow.enter.reveal / 1000), value: words.arrived)
              .frame(width: width)
              .offset(x: x, y: y + width + AuraDesign.page.hintGap)
          }
        }
      }
    }
    .background(Color(red: 4 / 255, green: 1 / 255, blue: 22 / 255).ignoresSafeArea())
    .preferredColorScheme(.dark)
    .onChange(of: reduced, initial: true) { _, _ in
      machine.preferences(reduced: reduced, opaque: opaque)
    }
    .onChange(of: opaque, initial: true) { _, _ in
      machine.preferences(reduced: reduced, opaque: opaque)
    }
    .onDisappear { machine.stop() }
  }
}

#Preview { AuraWalletWidgetDemo() }
