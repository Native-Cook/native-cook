import SwiftUI

@main
struct NativeCookApp: App {
    init() {
        // The RecordBlocks tests ask for the sound with `-recordingSound <folder>` (RecordingSound.swift).
        RecordingSound.startIfAsked()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
