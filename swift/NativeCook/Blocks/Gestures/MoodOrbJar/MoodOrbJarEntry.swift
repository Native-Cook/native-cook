import Foundation

/// One row of the day's list.
struct MoodOrbJarEntry: Identifiable, Equatable {
    let id: Int
    let mood: Int
    let tags: String
    let time: String
    /// The newest entry, which a swipe can undo.
    var isFresh: Bool
}
