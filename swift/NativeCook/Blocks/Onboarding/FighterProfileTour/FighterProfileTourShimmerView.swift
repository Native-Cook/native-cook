import SwiftUI

/// Port of `CommitShimmer.tsx`: the beam is drawn by `fighterProfileTourShimmer` (Metal).
struct FighterProfileTourShimmerView: View {
    let sweep: Double
    let enabled: Double
    var width: CGFloat = 353
    var height: CGFloat = 52

    var body: some View {
        // The shader replaces the colour of every pixel; the white fill only gives it pixels to run on.
        Rectangle()
            .fill(Color.white)
            .frame(width: width, height: height)
            .colorEffect(ShaderLibrary.fighterProfileTourShimmer(
                .float2(width, height), .float(Float(sweep)), .float(Float(enabled))
            ))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
