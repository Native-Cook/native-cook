#if DEBUG
import Foundation

/// Opt-in callback and story-time evidence. The frame path only writes preallocated scalars.
@MainActor
final class RaffleDrumSpinPerformance {
    private let enabled = UserDefaults.standard.bool(forKey: "rafflePerf")
    private var first: TimeInterval?
    private var firstStory = 0.0
    private var start: TimeInterval?
    private var last: TimeInterval?
    private var count = 0
    private var maxGap = 0.0
    private var gapsAbove25ms = 0
    private var gapsAbove50ms = 0
    private var finished = false
    private var trace = [Double](repeating: 0, count: 4096 * 3)
    private var traceCount = 0
    private var phaseCount = [Int](repeating: 0, count: 20)
    private var phaseDuration = [Double](repeating: 0, count: 20)
    private var phaseMaxGap = [Double](repeating: 0, count: 20)

    func record(at timestamp: TimeInterval, storyTime: Double, mode: Int) {
        guard enabled, !finished else { return }
        if first == nil { first = timestamp; firstStory = storyTime }
        if traceCount == 0 || timestamp > trace[(traceCount-1)*3] {
            if traceCount < 4096 {
                trace[traceCount*3] = timestamp
                trace[traceCount*3+1] = storyTime
                trace[traceCount*3+2] = Double(mode)
                traceCount += 1
            }
        }
        guard timestamp - (first ?? timestamp) >= 3 else { return }
        guard let start, let last else { self.start = timestamp; self.last = timestamp; return }
        let gap = timestamp - last
        guard gap > 0 else { return }
        self.last = timestamp
        count += 1
        maxGap = max(maxGap, gap)
        if gap > 0.025 { gapsAbove25ms += 1 }
        if gap > 0.05 { gapsAbove50ms += 1 }
        if mode == 0 {
            let phase = min(19, max(0, Int(storyTime * 2)))
            phaseCount[phase] += 1
            phaseDuration[phase] += gap
            phaseMaxGap[phase] = max(phaseMaxGap[phase], gap)
        }
        let elapsed = timestamp - start
        guard elapsed >= 45 else { return }
        finished = true
        let captured = trace, capturedCount = traceCount, origin = first ?? timestamp, initialStory = firstStory
        let counts = phaseCount, durations = phaseDuration, maxima = phaseMaxGap
        let intervals = count, worstGap = maxGap, late25 = gapsAbove25ms, late50 = gapsAbove50ms
        let output = UserDefaults.standard.string(forKey: "rafflePerfOutput")
        // Serialization and disk I/O occur once, outside the render callback's main-thread work.
        DispatchQueue.global(qos: .utility).async {
            let frames: [[String: Any]] = (0..<capturedCount).map { i in
                ["wall": captured[i*3] - origin, "story": captured[i*3+1], "mode": Int(captured[i*3+2])]
            }
            let phases: [[String: Any]] = (0..<20).map { i in
                ["startStory": Double(i)*0.5, "intervals": counts[i], "duration": durations[i], "maxGap": maxima[i], "callbacksPerSecond": durations[i] > 0 ? Double(counts[i])/durations[i] : 0]
            }
            let result: [String: Any] = [
                "measurement": "TimelineView callbacks after a three-second warm-up; unaltered stepDriver story times",
                "durationSeconds": elapsed, "intervals": intervals, "callbacksPerSecond": Double(intervals)/elapsed,
                "maxGapSeconds": worstGap, "gapsAbove25ms": late25, "gapsAbove50ms": late50,
                "renderFrames": RaffleDrumSpinRenderTrace.shared.rows(),
                "initialStoryTime": initialStory, "originEpoch": Date(timeIntervalSinceReferenceDate:origin).timeIntervalSince1970, "phases": phases, "frames": frames
            ]
            do {
                let data = try JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted, .sortedKeys])
                if let output, (try? data.write(to: URL(fileURLWithPath: output), options: .atomic)) != nil { return }
                let fallback = FileManager.default.temporaryDirectory.appendingPathComponent("RaffleDrumSpin-performance.json")
                try data.write(to: fallback, options: .atomic)
                print("RaffleDrumSpin timing evidence: \(fallback.path)")
            } catch { print("RaffleDrumSpin timing evidence failed: \(error)") }
        }
    }
}
#endif
