import SwiftUI

struct LiquidChargeCapsuleDemo: View {
    private var snapshot: Double? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-snapshot-time"), args.indices.contains(i+1) else { return nil }
        guard let value = Double(args[i+1]), value.isFinite else { return nil }
        return max(0, value)
    }
    var body: some View { LiquidChargeCapsule(autoPlay: true, previewTime: snapshot) }
}
