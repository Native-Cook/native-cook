import SwiftUI

struct WovenSpeakerPlayerDemo: View {
    static let loop = WovenSpeakerPlayer.loop
    var body: some View { WovenSpeakerPlayer(autoPlay: true) }
}

#Preview { WovenSpeakerPlayerDemo() }
