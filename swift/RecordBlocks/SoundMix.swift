import AVFoundation
import QuartzCore

/// Mixes what the app played during a recording (its log, see `RecordingSound.swift` in the app)
/// into one WAV file the length of the recording, so the upload script can lay it under the video.
/// (WAV, not AAC: an AAC file starts with the encoder's 44 ms of priming, which the passthrough
/// copy in `mux_sound.swift` would play as a delay.)
///
/// Both processes run on the same Mac, so the app's `CACurrentMediaTime()` and this one's are one
/// clock. The file starts `lead` seconds before the app's sync flash; `flash` says where the flash
/// falls in it, which is what the upload script matches with the flash it finds in the video.
enum SoundMix {
    static let rate = 48_000.0
    static let lead = 1.0

    struct Mixed {
        let file: URL
        /// Seconds from the start of the file to the sync flash.
        let flash: Double
    }

    enum Failure: Error { case unreadable(String), unwritable }

    /// Nil when the app logged no flash or played nothing.
    static func mix(folder: URL, endingAt end: CFTimeInterval = CACurrentMediaTime()) throws -> Mixed? {
        let events = readLog(folder.appendingPathComponent("events.jsonl"))
        guard let flash = events.first(where: { $0.event == "flash" })?.t,
              events.contains(where: { $0.event == "play" }) else { return nil }
        let start = flash - lead
        let frames = Int(((end - start) * rate).rounded(.up))
        guard frames > 0 else { return nil }

        var left = [Float](repeating: 0, count: frames)
        var right = [Float](repeating: 0, count: frames)
        var sounds: [String: Sound] = [:]
        for segment in segments(events, until: end) {
            if sounds[segment.file] == nil {
                sounds[segment.file] = try Sound(url: folder.appendingPathComponent(segment.file))
            }
            guard let sound = sounds[segment.file], sound.frames > 0 else { continue }
            segment.render(sound, from: start, into: &left, &right)
        }

        let out = folder.appendingPathComponent("sound.wav")
        try write(left, right, to: out)
        return Mixed(file: out, flash: lead)
    }

    // MARK: The log

    struct Event {
        let event: String
        let t: Double
        let player: String
        let volume: Float
        let fade: Double
        let file: String?
        let offset: Double
        let loops: Int
    }

    static func readLog(_ url: URL) -> [Event] {
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        let events = text.split(separator: "\n").compactMap { line -> Event? in
            guard let o = try? JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any],
                  let event = o["event"] as? String, let t = (o["t"] as? NSNumber)?.doubleValue else { return nil }
            return Event(
                event: event, t: t,
                player: o["player"] as? String ?? "",
                volume: (o["volume"] as? NSNumber)?.floatValue ?? 1,
                fade: (o["fade"] as? NSNumber)?.doubleValue ?? 0,
                file: o["file"] as? String,
                offset: (o["offset"] as? NSNumber)?.doubleValue ?? 0,
                loops: (o["loops"] as? NSNumber)?.intValue ?? 0)
        }
        // Lines are written in order, but a play(atTime:) is logged at the time it will start.
        return events.enumerated().sorted { ($0.element.t, $0.offset) < ($1.element.t, $1.offset) }.map(\.element)
    }

    /// One stretch of one player playing one file.
    struct Segment {
        let file: String
        let begin: Double
        let end: Double
        /// Where in the file playback started, in seconds.
        let offset: Double
        /// AVAudioPlayer's numberOfLoops: 0 plays once, n plays n more times, negative forever.
        let loops: Int
        /// The player's volume over time: (time, value, ramp seconds to reach it).
        let volume: [(t: Double, value: Float, fade: Double)]

        func gain(at t: Double) -> Float {
            var g = volume.first?.value ?? 1
            for step in volume.dropFirst() where step.t <= t {
                if step.fade > 0, t < step.t + step.fade {
                    let k = Float((t - step.t) / step.fade)
                    return g + (step.value - g) * k
                }
                g = step.value
            }
            return g
        }

        func render(_ sound: Sound, from start: Double, into left: inout [Float], _ right: inout [Float]) {
            let first = Int(((begin - start) * SoundMix.rate).rounded())
            let last = min(left.count, Int(((end - start) * SoundMix.rate).rounded()))
            var position = Int((offset * SoundMix.rate).rounded())
            var plays = 0
            var frame = first
            while frame < last {
                if position >= sound.frames {
                    plays += 1
                    if loops >= 0 && plays > loops { break }
                    position = 0
                }
                if frame >= 0 {
                    let g = gain(at: start + Double(frame) / SoundMix.rate)
                    left[frame] += sound.left[position] * g
                    right[frame] += sound.right[position] * g
                }
                frame += 1
                position += 1
            }
        }
    }

    static func segments(_ events: [Event], until end: Double) -> [Segment] {
        struct Open { let play: Event; let volumeFrom: Int }
        var open: [String: Open] = [:]
        var volumes: [String: [(t: Double, value: Float, fade: Double)]] = [:]
        var out: [Segment] = []

        func close(_ player: String, at t: Double) {
            guard let o = open.removeValue(forKey: player), let file = o.play.file else { return }
            let steps = Array(volumes[player, default: []][o.volumeFrom...])
            out.append(Segment(
                file: file, begin: o.play.t, end: t, offset: o.play.offset, loops: o.play.loops,
                volume: [(o.play.t, o.play.volume, 0)] + steps))
        }

        for e in events where e.event != "flash" {
            switch e.event {
            case "play":
                close(e.player, at: e.t)
                open[e.player] = Open(play: e, volumeFrom: volumes[e.player, default: []].count)
            case "pause", "stop":
                close(e.player, at: e.t)
            case "volume":
                volumes[e.player, default: []].append((e.t, e.volume, e.fade))
            default:
                break
            }
        }
        for player in Array(open.keys) { close(player, at: end) }
        return out
    }

    // MARK: Audio files

    /// A sound file decoded to two channels at the mix's rate.
    struct Sound {
        let left: [Float]
        let right: [Float]
        var frames: Int { left.count }

        init(url: URL) throws {
            let file = try AVAudioFile(forReading: url)
            let source = file.processingFormat
            guard let input = AVAudioPCMBuffer(pcmFormat: source, frameCapacity: AVAudioFrameCount(file.length)) else {
                throw Failure.unreadable(url.lastPathComponent)
            }
            try file.read(into: input)
            let channels = Int(source.channelCount)
            guard let target = AVAudioFormat(standardFormatWithSampleRate: SoundMix.rate, channels: AVAudioChannelCount(channels)) else {
                throw Failure.unreadable(url.lastPathComponent)
            }
            var buffer = input
            if source.sampleRate != SoundMix.rate || source.commonFormat != .pcmFormatFloat32 || source.isInterleaved {
                guard let converter = AVAudioConverter(from: source, to: target),
                      let output = AVAudioPCMBuffer(
                        pcmFormat: target,
                        frameCapacity: AVAudioFrameCount(Double(input.frameLength) * SoundMix.rate / source.sampleRate) + 1024) else {
                    throw Failure.unreadable(url.lastPathComponent)
                }
                var fed = false
                var error: NSError?
                converter.convert(to: output, error: &error) { _, status in
                    if fed { status.pointee = .endOfStream; return nil }
                    fed = true
                    status.pointee = .haveData
                    return input
                }
                if let error { throw error }
                buffer = output
            }
            let n = Int(buffer.frameLength)
            guard let data = buffer.floatChannelData else { throw Failure.unreadable(url.lastPathComponent) }
            left = Array(UnsafeBufferPointer(start: data[0], count: n))
            right = channels > 1 ? Array(UnsafeBufferPointer(start: data[1], count: n)) : left
        }
    }

    static func write(_ left: [Float], _ right: [Float], to url: URL) throws {
        try? FileManager.default.removeItem(at: url)
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: rate,
            AVNumberOfChannelsKey: 2,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false,
        ]
        let file = try AVAudioFile(forWriting: url, settings: settings, commonFormat: .pcmFormatFloat32, interleaved: false)
        let chunk = 4096
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(chunk)),
              let data = buffer.floatChannelData else { throw Failure.unwritable }
        var at = 0
        while at < left.count {
            let n = min(chunk, left.count - at)
            for i in 0..<n {
                // A phone's output clips past full scale; so does the mix.
                data[0][i] = max(-1, min(1, left[at + i]))
                data[1][i] = max(-1, min(1, right[at + i]))
            }
            buffer.frameLength = AVAudioFrameCount(n)
            try file.write(from: buffer)
            at += n
        }
    }
}
