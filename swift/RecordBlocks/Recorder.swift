import XCTest

/// Records the self-playing demo of one block for the site's catalog: the app opens on the block
/// and the test does nothing but wait while XCTest records the screen. The video is an attachment
/// of the test; `ci_scripts/ci_post_xcodebuild.sh` exports it and sends it to nativecook.dev,
/// where it is cut to one loop and put in an iPhone frame.
class Recorder: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// Opens the block and stays on it for three loops of its demo, plus the second the recorder
    /// drops at the end. Skipped when the block is not in the run's list (see `Needed`).
    func record(slug: String, loop: TimeInterval) throws {
        try XCTSkipUnless(Needed.includes(slug), "\(slug) already has a video")
        let app = XCUIApplication()
        app.launchArguments = ["-block", slug, "-recording", "1"]
        app.launch()
        // The app opens straight on the block (the stage hides the status bar). This waits on the
        // process state, not on an accessibility snapshot: a block that draws a lot keeps the main
        // thread busy, and on Xcode Cloud's slow machines a snapshot query then times out and fails
        // the test before anything was recorded.
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 60), "\(slug) did not open")
        // What the recording is of, next to it in the result bundle: Xcode Cloud runs the upload
        // script on a machine without the repository, so the manifests are out of reach there.
        let note = XCTAttachment(string: "\(slug) \(loop)")
        note.name = "recording"
        note.lifetime = .keepAlways
        add(note)
        // Three loops: the first after a cold start can be timed differently, and the last second
        // of a recording repeats stale frames. A long loop gets two, so the recording (about
        // 2 MB a second) stays under the 100 MB a request to the media Worker may carry. Two more
        // seconds cover a block that holds its resting frame while it prepares its images before
        // its first loop starts.
        let loops: TimeInterval = loop > 9 ? 2 : 3
        Thread.sleep(forTimeInterval: loops * loop + 4)
    }
}

/// Which blocks this run records. On Xcode Cloud, `ci_post_clone.sh` writes `Needed.swift` with
/// the blocks that have no video on the site, so the list is compiled into the test bundle; a
/// local run names them in the environment variable RECORD_SLUGS (comma-separated, or "all"),
/// passed to xcodebuild as TEST_RUNNER_RECORD_SLUGS.
enum Needed {
    static let slugs: Set<String>? = {
        if let env = ProcessInfo.processInfo.environment["RECORD_SLUGS"], !env.isEmpty {
            return env == "all" ? nil : Set(env.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) })
        }
        return Set(NeededFromBuild.slugs)
    }()

    static func includes(_ slug: String) -> Bool { slugs?.contains(slug) ?? true }
}
