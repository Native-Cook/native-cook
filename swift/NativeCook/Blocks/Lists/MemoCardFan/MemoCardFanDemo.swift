import SwiftUI

struct MemoCardFanDemo: View {
    var body: some View {
        MemoCardFan(
            autoPlay: freezeAt == nil,
            freezeAt: freezeAt
        )
    }

    private var freezeAt: Double? {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard let flag = arguments.firstIndex(of: "-freezeAt"),
              arguments.indices.contains(flag + 1) else {
            return nil
        }
        return Double(arguments[flag + 1])
        #else
        return nil
        #endif
    }
}

#Preview {
    MemoCardFanDemo()
}
