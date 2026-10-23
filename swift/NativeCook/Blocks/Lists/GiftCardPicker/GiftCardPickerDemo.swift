import SwiftUI

struct GiftCardPickerDemo: View {
    var body: some View {
        GiftCardPicker(autoPlay: !manual, freezeAt: freezeAt)
    }

    private var manual: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-manual")
        #else
        false
        #endif
    }

    private var freezeAt: Double {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-freezeAt"),
           arguments.indices.contains(index + 1),
           let value = Double(arguments[index + 1]) {
            return max(0, value)
        }
        #endif
        return -1
    }
}

#Preview {
    GiftCardPickerDemo()
}
