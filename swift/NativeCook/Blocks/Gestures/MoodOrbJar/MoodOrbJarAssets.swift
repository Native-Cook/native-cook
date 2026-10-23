import SwiftUI
import UIKit

/// The block's artwork, bundled flat as `MoodOrbJar-<file>` (the app has one resource namespace).
/// Loaded once; a missing file trips an assertion in debug builds.
enum MoodOrbJarAssets {
    static let jarPlate = load("MoodOrbJar-jar-plate.png")
    static let lid = load("MoodOrbJar-lid.png")
    /// One per mood, awful to great.
    static let orbs: [UIImage] = MoodOrbJarTokens.moods.map { load($0.assetName) }
    private static let pileAtlas = load("MoodOrbJar-pile-atlas.png")
    /// The atlas contains the accepted reference's 18 resting orbs, one 144 px cell per orb.
    static let pileOrbs: [UIImage] = {
        guard let atlas = pileAtlas.cgImage else {
            assertionFailure("Mood Orb Jar pile atlas has no CGImage")
            return []
        }
        return (0..<MoodOrbJarTokens.pileCount).compactMap { index in
            let source = CGRect(x: index * 144, y: 0, width: 144, height: 144)
            guard let crop = atlas.cropping(to: source) else {
                assertionFailure("Could not crop Mood Orb Jar pile atlas cell \(index)")
                return nil
            }
            return UIImage(cgImage: crop, scale: pileAtlas.scale, orientation: .up)
        }
    }()

    static func assertAllLoaded() {
        #if DEBUG
        let names = ["MoodOrbJar-jar-plate.png", "MoodOrbJar-pile-atlas.png", "MoodOrbJar-lid.png"]
            + MoodOrbJarTokens.moods.map(\.assetName)
        for name in names {
            assert(UIImage(named: name) != nil, "Missing Mood Orb Jar asset: \(name)")
        }
        assert(pileOrbs.count == MoodOrbJarTokens.pileCount, "Mood Orb Jar pile atlas is incomplete")
        #endif
    }

    private static func load(_ name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Mood Orb Jar asset: \(name)")
            return UIImage()
        }
        return image
    }
}
