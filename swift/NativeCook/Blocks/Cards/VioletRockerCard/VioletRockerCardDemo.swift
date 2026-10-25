import SwiftUI

struct VioletRockerCardDemo: View {
    private var snapshot: Double? {
        let args=ProcessInfo.processInfo.arguments
        guard let i=args.firstIndex(of:"-snapshot-time"),args.indices.contains(i+1),let time=Double(args[i+1]),time.isFinite else { return nil }
        return max(0,time)
    }
    var body: some View { VioletRockerCard(autoPlay:true,previewTime:snapshot) }
}
