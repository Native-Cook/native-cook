import SwiftUI

struct SparkSphereScoreDemo: View {
    static let loop = SparkSphereScore.loop

    var body: some View {
        SparkSphereScore(autoPlay: true)
    }
}

#Preview {
    SparkSphereScoreDemo()
}
