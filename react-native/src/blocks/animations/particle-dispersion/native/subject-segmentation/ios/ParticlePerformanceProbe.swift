import Foundation
import QuartzCore

/// Diagnostic only: enable with PARTICLE_PERF_LOG=1 before launching the app.
/// Stores scalar timestamps, never images/URIs, and prints summaries off main.
final class ParticlePerformanceProbe: @unchecked Sendable {
  struct Token: Sendable {
    let run: Int
    let frame: Int
  }

  private enum Phase: String {
    case capture = "capture_static", gap = "matte_gap", dust
    case complete = "complete_static", reduced = "reduced_motion"
  }

  private struct Frame {
    let clock: Double
    let progress: Double
    let phase: Phase
    let submittedAt: Double
    let drawableWaitMS: Double
    var encodeMS: Double?
    var gpuMS: Double?
    var gpuDone = false
    var gpuFailed = false
    var presentedAt: Double?
    var presentationCallback = false
  }

  private final class Run {
    let id: Int
    let generation: UInt64
    let startedAt: Double
    var phase: Phase?
    var frames: [Frame] = []
    var sawDust = false
    var closeReason: String?
    var gateSkippedDust = 0
    var unavailableDrawables = 0
    var overflow = 0

    init(id: Int, generation: UInt64) {
      self.id = id
      self.generation = generation
      startedAt = CACurrentMediaTime()
      frames.reserveCapacity(512)
    }
  }

  private static let enabled = ProcessInfo.processInfo.environment["PARTICLE_PERF_LOG"] == "1"
  private static let outputQueue = DispatchQueue(label: "SubjectSegmentation.PerformanceLog", qos: .utility)
  private let lock = NSLock()
  private let viewID = String(UUID().uuidString.prefix(8))
  private let output: ([String: Any]) -> Void
  private var generation: UInt64 = 0
  private var nextRun = 0
  private var currentRun: Int?
  private var runs: [Int: Run] = [:]
  private var lastClock: Double?
  private var lastProgress: Double?

  init?(enabled: Bool = ParticlePerformanceProbe.enabled, output: (([String: Any]) -> Void)? = nil) {
    guard enabled else { return nil }
    self.output = output ?? { event in
      guard let data = try? JSONSerialization.data(withJSONObject: event, options: [.sortedKeys]),
        let text = String(data: data, encoding: .utf8) else { return }
      NSLog("[ParticlePerf] %@", text)
    }
  }

  func beginPreparation(generation: UInt64) {
    lock.lock()
    self.generation = generation
    currentRun = nil
    lastClock = nil
    lastProgress = nil
    emit(["event": "preparation_start", "generation": generation, "hostTime": CACurrentMediaTime()])
    lock.unlock()
  }

  func preparationFinished(generation: UInt64, rendererMS: Double, texturesMS: Double,
    totalMS: Double, status: String) {
    emit(["event": "preparation_end", "generation": generation, "hostTime": CACurrentMediaTime(),
      "rendererMS": rendererMS, "texturesMS": texturesMS, "totalMS": totalMS, "status": status])
  }

  func observe(time: Double, progress: Double, dustStart: Double, dustDuration: Double,
    reduced: Bool, generation: UInt64) {
    guard time.isFinite, progress.isFinite else { return }
    lock.lock()
    defer { lock.unlock() }
    guard self.generation == generation else { return }
    let reset = (lastClock.map { time < $0 - 0.05 } ?? false) ||
      (reduced && (lastProgress.map { progress < $0 - 0.05 } ?? false))
    if reset, let id = currentRun { close(id, reason: "clock_reset") }
    if currentRun == nil || reset {
      // Repeated rapid seeks cannot retain an unbounded callback backlog.
      if runs.count >= 4, let oldest = runs.keys.min() { summarize(oldest, reason: "run_capacity") }
      nextRun += 1
      let run = Run(id: nextRun, generation: generation)
      runs[run.id] = run
      currentRun = run.id
      let id = run.id
      Self.outputQueue.asyncAfter(deadline: .now() + 10) { [weak self] in
        self?.flush(id, reason: "window_timeout")
      }
    }
    lastClock = time
    lastProgress = progress
    guard let id = currentRun, let run = runs[id] else { return }
    let phase = Self.phase(time: time, dustStart: dustStart, dustDuration: dustDuration, reduced: reduced)
    if run.phase != phase {
      run.phase = phase
      emit(["event": "phase", "run": id, "generation": generation,
        "phase": phase.rawValue, "clock": time, "progress": progress, "hostTime": CACurrentMediaTime()])
    }
    if phase == .dust { run.sawDust = true }
    if run.sawDust && phase == .complete { close(id, reason: "dust_window_ended") }
    if reduced && progress >= 1 { close(id, reason: "reduced_motion_ended") }
  }

  func submitted(clock: Double, progress: Double, drawableWaitMS: Double) -> Token? {
    lock.lock()
    defer { lock.unlock() }
    guard let id = currentRun, let run = runs[id], let phase = run.phase else { return nil }
    guard run.frames.count < 1_024 else { run.overflow += 1; return nil }
    let token = Token(run: id, frame: run.frames.count)
    run.frames.append(Frame(clock: clock, progress: progress, phase: phase,
      submittedAt: CACurrentMediaTime(), drawableWaitMS: drawableWaitMS))
    return token
  }

  func encoded(_ token: Token, milliseconds: Double) {
    update(token) { $0.encodeMS = milliseconds }
  }

  func completed(_ token: Token, start: Double, end: Double, failed: Bool) {
    update(token) {
      $0.gpuDone = true
      $0.gpuFailed = failed
      if start.isFinite, end.isFinite, start > 0, end >= start { $0.gpuMS = (end - start) * 1_000 }
    }
  }

  func presented(_ token: Token, at time: Double) {
    update(token) {
      $0.presentationCallback = true
      // Metal documents zero for skipped/unpresented drawables. Never substitute
      // callback arrival time, which can be delayed or arrive out of order.
      if time.isFinite, time > 0 { $0.presentedAt = time }
    }
  }

  func skipped(gate: Bool) {
    lock.lock()
    defer { lock.unlock() }
    guard let id = currentRun, let run = runs[id] else { return }
    if gate && run.phase == .dust { run.gateSkippedDust += 1 }
    if !gate { run.unavailableDrawables += 1 }
  }

  func finish(reason: String) {
    lock.lock()
    for id in runs.keys.sorted() { summarize(id, reason: reason) }
    currentRun = nil
    lock.unlock()
  }

  private static func phase(time: Double, dustStart: Double, dustDuration: Double, reduced: Bool) -> Phase {
    if reduced { return .reduced }
    let clock = Float(time)
    if clock < 0.400 { return .capture }
    let age = clock - Float(dustStart)
    if age < 0 { return .gap }
    return age < Float(dustDuration) ? .dust : .complete
  }

  private func update(_ token: Token, _ body: (inout Frame) -> Void) {
    lock.lock()
    defer { lock.unlock() }
    guard let run = runs[token.run], run.frames.indices.contains(token.frame) else { return }
    body(&run.frames[token.frame])
  }

  private func close(_ id: Int, reason: String) {
    guard let run = runs[id], run.closeReason == nil else { return }
    run.closeReason = reason
    // GPU completion does not imply presentation. Keep a bounded grace period
    // for both callbacks; missing callbacks are reported explicitly at expiry.
    Self.outputQueue.asyncAfter(deadline: .now() + 1) { [weak self] in
      self?.flush(id, reason: reason)
    }
  }

  private func flush(_ id: Int, reason: String) {
    lock.lock()
    summarize(id, reason: reason)
    lock.unlock()
  }

  private func summarize(_ id: Int, reason: String) {
    guard let run = runs.removeValue(forKey: id) else { return }
    let dust = run.frames.filter { $0.phase == .dust }
    // Sort timestamp records, not callback arrival order. Static phases are
    // excluded by phase; retain EVERY positive interval within active dust.
    let shown = run.frames.enumerated().filter { $0.element.presentedAt != nil }.sorted {
      let a = $0.element.presentedAt!, b = $1.element.presentedAt!
      return a == b ? $0.offset < $1.offset : a < b
    }
    var intervals: [Double] = []
    var reordered = 0
    for (a, b) in zip(shown, shown.dropFirst()) {
      if b.offset < a.offset { reordered += 1 }
      if a.element.phase == .dust && (b.element.phase == .dust || b.element.phase == .complete) {
        let interval = (b.element.presentedAt! - a.element.presentedAt!) * 1_000
        if interval > 0 { intervals.append(interval) }
      }
    }
    let elapsedMS = intervals.reduce(0, +)
    var event: [String: Any] = [
      "event": "run_summary", "run": id, "generation": run.generation, "reason": reason,
      "hostTime": CACurrentMediaTime(), "wallMS": (CACurrentMediaTime() - run.startedAt) * 1_000,
      "submittedFrames": run.frames.count, "presentedFrames": shown.count,
      "dustSubmitted": dust.count, "dustPresented": dust.filter { $0.presentedAt != nil }.count,
      "dustIntervals": intervals.count, "dustPresentationFPS": elapsedMS > 0 ? Double(intervals.count) * 1_000 / elapsedMS : 0,
      "dustIntervalMS": Self.distribution(intervals),
      "dustGPUms": Self.distribution(dust.compactMap { $0.gpuMS }),
      "dustEncodeMS": Self.distribution(dust.compactMap { $0.encodeMS }),
      "dustDrawableWaitMS": Self.distribution(dust.map { $0.drawableWaitMS }),
      "dustIntervalsOver25MS": intervals.filter { $0 > 25 }.count,
      "dustIntervalsOver50MS": intervals.filter { $0 > 50 }.count,
      "missingGPUCallbacks": run.frames.filter { !$0.gpuDone }.count,
      "missingPresentedCallbacks": run.frames.filter { !$0.presentationCallback }.count,
      "skippedPresentations": run.frames.filter { $0.presentationCallback && $0.presentedAt == nil }.count,
      "gpuFailures": run.frames.filter { $0.gpuFailed }.count,
      "gateSkippedDust": run.gateSkippedDust, "unavailableDrawables": run.unavailableDrawables,
      "presentationReordered": reordered, "recordOverflow": run.overflow,
      "phaseCounts": Dictionary(grouping: run.frames, by: { $0.phase.rawValue }).mapValues { $0.count },
    ]
    if let first = dust.first {
      event["firstDustClock"] = first.clock
      event["firstDustProgress"] = first.progress
      event["firstDustGPUms"] = first.gpuMS
      event["firstDustEncodeMS"] = first.encodeMS
      event["firstDustDrawableWaitMS"] = first.drawableWaitMS
      if let shownAt = first.presentedAt { event["firstDustSubmitToPresentMS"] = (shownAt - first.submittedAt) * 1_000 }
    }
    if let last = dust.last { event["lastDustClock"] = last.clock }
    emit(event)
  }

  private static func distribution(_ values: [Double]) -> [String: Double] {
    let values = values.filter { $0.isFinite && $0 >= 0 }.sorted()
    guard !values.isEmpty else { return ["samples": 0] }
    return ["samples": Double(values.count), "median": values[values.count / 2],
      "p95": values[min(values.count - 1, Int(ceil(Double(values.count) * 0.95)) - 1)], "max": values.last!]
  }

  private func emit(_ event: [String: Any]) {
    var payload = event
    payload["view"] = viewID
    let output = self.output
    Self.outputQueue.async { output(payload) }
  }
}
