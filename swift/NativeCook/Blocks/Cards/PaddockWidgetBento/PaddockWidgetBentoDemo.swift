import SwiftUI

struct PaddockWidgetBentoDemo: View {
    private var autoPlay: Bool {
        #if DEBUG
        !ProcessInfo.processInfo.arguments.contains("-widgets-manual")
        #else
        true
        #endif
    }

    var body: some View { PaddockWidgetBento(autoPlay: autoPlay) }
}
