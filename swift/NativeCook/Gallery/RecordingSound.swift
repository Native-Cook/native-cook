import AVFoundation
import UIKit

/// The sound of a block while the RecordBlocks tests record it.
///
/// XCTest's screen recording has no audio track, so the app writes down what it plays instead:
/// with `-recordingSound <folder>` on the command line, every `AVAudioPlayer` of every block is
/// logged (play, pause, stop, volume) on `CACurrentMediaTime()`, and each sound file it plays is
/// copied into the folder once. The test (`RecordBlocks/SoundMix.swift`) mixes that log into one
/// audio file, and the upload script lays it under the video.
///
/// To place the sound in the video, the app shows one sync mark at launch: a full-screen magenta
/// flash, a few frames long, whose time on the same clock is logged too. The flash is over long
/// before the loop the site keeps.
enum RecordingSound {
    /// Set at launch when the tests ask for the sound; nil otherwise (nothing is swizzled).
    private(set) static var log: RecordingSoundLog?

    static func startIfAsked() {
        guard log == nil, let path = UserDefaults.standard.string(forKey: "recordingSound"), !path.isEmpty else { return }
        guard let log = RecordingSoundLog(folder: URL(fileURLWithPath: path, isDirectory: true)) else { return }
        self.log = log
        AVAudioPlayer.recordingSoundSwizzle()
    }

    /// Shows the sync flash, logs when it reached the screen, and calls `done` when it is over.
    static func flash(_ done: @escaping () -> Void) {
        guard let log else { return done() }
        RecordingSoundFlash.show(shown: { time in log.write(["event": "flash", "t": time]) }, done: done)
    }
}

/// The log: one JSON object per line in `<folder>/events.jsonl`, written as it happens, so a test
/// that ends the app mid-sound still has everything up to that moment.
final class RecordingSoundLog {
    private let folder: URL
    private let handle: FileHandle
    private let queue = DispatchQueue(label: "recording-sound")
    /// Sound files already copied, by their source path.
    private var copied: [String: String] = [:]

    init?(folder: URL) {
        let file = folder.appendingPathComponent("events.jsonl")
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        guard FileManager.default.createFile(atPath: file.path, contents: nil),
              let handle = try? FileHandle(forWritingTo: file) else { return nil }
        self.folder = folder
        self.handle = handle
    }

    func write(_ entry: [String: Any]) {
        queue.async {
            guard var line = try? JSONSerialization.data(withJSONObject: entry, options: [.sortedKeys]) else { return }
            line.append(0x0A)
            self.handle.write(line)
        }
    }

    /// The copy of the player's sound in the folder (a file name), made the first time it plays.
    func file(of player: AVAudioPlayer) -> String? {
        queue.sync {
            if let url = player.url {
                if let name = copied[url.path] { return name }
                let name = "sound-\(copied.count).\(url.pathExtension.isEmpty ? "caf" : url.pathExtension)"
                guard (try? FileManager.default.copyItem(at: url, to: folder.appendingPathComponent(name))) != nil else { return nil }
                copied[url.path] = name
                return name
            }
            // A player made from data: keyed by the data's identity.
            guard let data = player.data else { return nil }
            let key = "data-\(data.count)-\(data.hashValue)"
            if let name = copied[key] { return name }
            let name = "sound-\(copied.count).audio"
            guard (try? data.write(to: folder.appendingPathComponent(name))) != nil else { return nil }
            copied[key] = name
            return name
        }
    }
}

extension AVAudioPlayer {
    private static var swizzled = false

    fileprivate static func recordingSoundSwizzle() {
        guard !swizzled else { return }
        swizzled = true
        // By Objective-C name: `play` alone is ambiguous in Swift (`play()` and `play(atTime:)`).
        let pairs = [
            ("play", "recordingSound_play"),
            ("playAtTime:", "recordingSound_playAtTime:"),
            ("pause", "recordingSound_pause"),
            ("stop", "recordingSound_stop"),
            ("setVolume:fadeDuration:", "recordingSound_setVolume:fadeDuration:"),
            ("setVolume:", "recordingSound_setVolume:"),
        ].map { (NSSelectorFromString($0), NSSelectorFromString($1)) }
        for (original, replacement) in pairs {
            guard let a = class_getInstanceMethod(AVAudioPlayer.self, original),
                  let b = class_getInstanceMethod(AVAudioPlayer.self, replacement) else { continue }
            method_exchangeImplementations(a, b)
        }
    }

    private var recordingSoundID: String { String(UInt(bitPattern: ObjectIdentifier(self).hashValue)) }

    /// One public call can run another inside it (a fade may set the volume, `play` may call
    /// `play(atTime:)`): only the outermost is logged.
    private static func outermost<T>(_ body: () -> T, log: (T) -> Void) -> T {
        let key = "recordingSound.depth"
        let depth = (Thread.current.threadDictionary[key] as? Int) ?? 0
        Thread.current.threadDictionary[key] = depth + 1
        let result = body()
        Thread.current.threadDictionary[key] = depth
        if depth == 0 { log(result) }
        return result
    }

    private func recordingSoundEvent(_ event: String, at t: CFTimeInterval, _ extra: [String: Any] = [:]) {
        guard let log = RecordingSound.log else { return }
        var entry: [String: Any] = ["event": event, "t": t, "player": recordingSoundID, "volume": volume]
        if event == "play" {
            guard let file = log.file(of: self) else { return }
            entry["file"] = file
            entry["loops"] = numberOfLoops
        }
        entry.merge(extra) { $1 }
        log.write(entry)
    }

    // After an exchange these names call the original methods.

    @objc(recordingSound_play) private func recordingSound_play() -> Bool {
        let now = CACurrentMediaTime()
        // Logged before the call, while `currentTime` still says where playback starts.
        let offset = currentTime
        return Self.outermost({ recordingSound_play() }) { started in
            if started { recordingSoundEvent("play", at: now, ["offset": offset]) }
        }
    }

    @objc(recordingSound_playAtTime:) private func recordingSound_play(atTime time: TimeInterval) -> Bool {
        let now = CACurrentMediaTime()
        let wait = max(0, time - deviceCurrentTime)
        let offset = currentTime
        return Self.outermost({ recordingSound_play(atTime: time) }) { started in
            if started { recordingSoundEvent("play", at: now + wait, ["offset": offset]) }
        }
    }

    @objc(recordingSound_pause) private func recordingSound_pause() {
        Self.outermost({ recordingSound_pause() }) { recordingSoundEvent("pause", at: CACurrentMediaTime()) }
    }

    @objc(recordingSound_stop) private func recordingSound_stop() {
        Self.outermost({ recordingSound_stop() }) { recordingSoundEvent("stop", at: CACurrentMediaTime()) }
    }

    @objc(recordingSound_setVolume:fadeDuration:) private func recordingSound_setVolume(_ value: Float, fadeDuration: TimeInterval) {
        let now = CACurrentMediaTime()
        Self.outermost({ recordingSound_setVolume(value, fadeDuration: fadeDuration) }) {
            recordingSoundEvent("volume", at: now, ["volume": value, "fade": fadeDuration])
        }
    }

    @objc(recordingSound_setVolume:) private func recordingSound_setVolume(_ value: Float) {
        let now = CACurrentMediaTime()
        Self.outermost({ recordingSound_setVolume(value) }) {
            recordingSoundEvent("volume", at: now, ["volume": value, "fade": 0])
        }
    }
}

/// The sync mark: a magenta window over everything for a few frames, cut in and out with no
/// animation. The time logged is when the first frame showing it reaches the screen.
private enum RecordingSoundFlash {
    static let color = UIColor(red: 1, green: 0, blue: 1, alpha: 1)
    static let frames = 6
    /// After the app is active: the system's launch transition fades the whole app in, which would
    /// blur the cut, and it is over well within this.
    static let settle = 1.0

    private static var window: UIWindow?
    private static var link: CADisplayLink?
    private static var shown = 0
    private static var onShown: ((CFTimeInterval) -> Void)?
    private static var onDone: (() -> Void)?

    static func show(shown: @escaping (CFTimeInterval) -> Void, done: @escaping () -> Void) {
        onShown = shown
        onDone = done
        attach()
    }

    /// Waits for the app's window scene to be active, then for the launch transition.
    private static func attach() {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
              scene.activationState == .foregroundActive else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05, execute: attach)
            return
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + settle) { cut(in: scene) }
    }

    private static func cut(in scene: UIWindowScene) {
        let w = UIWindow(windowScene: scene)
        w.windowLevel = .alert + 100
        w.backgroundColor = color
        w.isUserInteractionEnabled = false
        w.isHidden = false
        window = w
        let target = Ticker()
        link = CADisplayLink(target: target, selector: #selector(Ticker.tick(_:)))
        link?.add(to: .main, forMode: .common)
    }

    private final class Ticker: NSObject {
        @objc func tick(_ link: CADisplayLink) {
            shown += 1
            // The window was committed before this callback, so it is on the frame displayed at
            // this callback's target time.
            if shown == 1 { onShown?(link.targetTimestamp); onShown = nil }
            if shown >= RecordingSoundFlash.frames {
                link.invalidate()
                RecordingSoundFlash.link = nil
                RecordingSoundFlash.window?.isHidden = true
                RecordingSoundFlash.window = nil
                RecordingSoundFlash.onDone?()
                RecordingSoundFlash.onDone = nil
            }
        }
    }
}
