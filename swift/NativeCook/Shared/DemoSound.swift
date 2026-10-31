import Foundation

/// Whether a block plays its sounds while it plays by itself (`autoPlay`).
///
/// A block is silent until someone touches it: a screen that starts making noise on its own is
/// not polite. The one exception is the site's recording of the demo (README → Recordings →
/// Sound), where the demo app is launched with `-recordingSound <folder>`, so the video carries
/// the sounds a touch would make. Blocks check this beside their own "was it touched" condition.
/// In your own app, leave it out (or make it your own setting).
enum DemoSound {
    static let inAutoPlay = !(UserDefaults.standard.string(forKey: "recordingSound") ?? "").isEmpty
}
