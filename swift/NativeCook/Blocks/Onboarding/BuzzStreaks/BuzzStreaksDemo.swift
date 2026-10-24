import SwiftUI

struct BuzzStreaksDemo: View {
    static let loop = 12.0
    private let recording = UserDefaults.standard.bool(forKey: "recording")
    var body: some View { BuzzStreaks(autoPlay:true,motionProfile:.signature,recordingLoop:recording ? Self.loop : nil) }
}

#Preview { BuzzStreaksDemo() }
