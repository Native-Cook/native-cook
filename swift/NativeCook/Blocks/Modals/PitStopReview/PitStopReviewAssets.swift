import UIKit

enum PitStopReviewAssets {
    /// The seven Blender pitch frames, −1.5° … +3° in 0.75° steps (index 0 is `pitchFirst`).
    static let pitchFirst = -1.5, pitchStep = 0.75
    private static let frameNames = ["car-pitch-m150", "car-pitch-m075", "car-pitch-000", "car-pitch-p075", "car-pitch-p150", "car-pitch-p225", "car-pitch-p300"]

    static let carFrames: [UIImage] = frameNames.map { load("PitStopReview-\($0)", extension: "webp") }
    static let carShadow: UIImage = load("PitStopReview-car-shadow", extension: "webp")
    static let profile: UIImage = load("PitStopReview-profile", extension: "jpg")

    private static func load(_ name: String, extension fileExtension: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: name, ofType: fileExtension),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
}
