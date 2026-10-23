import Foundation
import os
import QuartzCore

/// Frame-timing probe (debug builds only; every call compiles to nothing in
/// release). One record per display-link frame: the link's vsync timestamps,
/// the time spent in each phase of `GlowknobUIRuntime.tick`, the main thread's busy
/// time until the run loop next goes idle (which includes SwiftUI's view
/// updates, layout and `Canvas` drawing for that frame), view-body and
/// representable update counts, `Canvas` draw time, the lift layer's SceneKit
/// encode / composite / `nextDrawable` wait and GPU time, and the pick-up face
/// capture. The spans are also os_signpost intervals ("dev.glowknob",
/// PointsOfInterest) for Instruments.
///
/// Off unless the app is launched with `-GKPerf` (or `-GKBench`). Records are
/// then written as JSON lines to `Documents/perf/<launch>-<n>.jsonl` whenever
/// the display link goes idle (the end of an interaction) or 600 frames have
/// accumulated.
@MainActor
enum GlowknobPerf {
  struct Frame: Encodable {
    var seq = 0
    var ts = 0.0          // link.timestamp (s)
    var target = 0.0      // link.targetTimestamp (s)
    var interval = 0.0    // ms since the previous link callback's timestamp
    var budget = 0.0      // ms, targetTimestamp − timestamp
    var start = 0.0       // CACurrentMediaTime at tick entry (s)
    var raf = 0.0, callbacks = 0.0, reactions = 0.0, after = 0.0  // ms
    var busy = 0.0        // ms from tick entry until the run loop waits again
    /// ms from tick entry to each beforeWaiting observer order that ran
    /// (0 = before every system observer, 1999999 = just before Core
    /// Animation's commit at 2000000, max = after it).
    var marks: [String: Double] = [:]
    var render3D = 0.0, composite3D = 0.0, drawableWait = 0.0, gpu3D = 0.0  // ms
    var face = 0.0        // ms
    var canvas: [String: Double] = [:]
    var bodies: [String: Int] = [:]
    var phase = ""
  }

  /// The scripted benchmark's current step (see GlowknobPerfBench).
  static var phase = ""

  #if DEBUG || PERF_PROBE
  static let enabled = ProcessInfo.processInfo.arguments.contains("-GKPerf")
    || ProcessInfo.processInfo.arguments.contains("-GKBench")
  static let signposter = OSSignposter(subsystem: "dev.glowknob", category: .pointsOfInterest)
  private static var current: Frame?
  private static var pending: [Frame] = []
  private static var seq = 0
  private static var lastTs = 0.0
  private static var chunk = 0
  private static let launch = Int(Date().timeIntervalSince1970)
  private static var observer: CFRunLoopObserver?
  private static var gpuLate: [Int: Double] = [:]
  #endif

  static func frameBegan(timestamp: Double, target: Double, event: Bool = false) {
    #if DEBUG || PERF_PROBE
    guard enabled else { return }
    installObserver()
    finish()
    seq += 1
    var f = Frame()
    f.seq = seq
    f.ts = timestamp
    f.target = target
    f.interval = lastTs > 0 && !event ? (timestamp - lastTs) * 1000 : 0
    f.budget = (target - timestamp) * 1000
    f.start = CACurrentMediaTime()
    f.phase = phase
    if event { f.marks["event"] = 1 } else { lastTs = timestamp }
    current = f
    #endif
  }

  /// Times `body` into `key` of the current frame (and as a signpost).
  @discardableResult
  static func measure<T>(_ name: StaticString, _ key: WritableKeyPath<Frame, Double>, _ body: () -> T) -> T {
    #if DEBUG || PERF_PROBE
    guard enabled else { return body() }
    if current == nil {
      let now = CACurrentMediaTime()
      frameBegan(timestamp: now, target: now, event: true)
    }
    let state = signposter.beginInterval(name)
    let t0 = CACurrentMediaTime()
    defer {
      current?[keyPath: key] += (CACurrentMediaTime() - t0) * 1000
      signposter.endInterval(name, state)
    }
    #endif
    return body()
  }

  /// Times a `Canvas` draw (any number per frame, summed by name).
  static func canvas<T>(_ name: String, _ body: () -> T) -> T {
    #if DEBUG || PERF_PROBE
    guard enabled else { return body() }
    let state = signposter.beginInterval("canvas")
    let t0 = CACurrentMediaTime()
    defer {
      current?.canvas[name, default: 0] += (CACurrentMediaTime() - t0) * 1000
      signposter.endInterval("canvas", state)
    }
    #endif
    return body()
  }

  /// A view body / representable update ran.
  static func count(_ name: String) {
    #if DEBUG || PERF_PROBE
    current?.bodies[name, default: 0] += 1
    #endif
  }

  /// The probe is recording (debug / PERF_PROBE build launched with -GKPerf).
  static var isOn: Bool {
    #if DEBUG || PERF_PROBE
    return enabled
    #else
    return false
    #endif
  }

  static var frameSeq: Int {
    #if DEBUG || PERF_PROBE
    return current?.seq ?? 0
    #else
    return 0
    #endif
  }

  /// GPU time of work committed in frame `seq` (reported asynchronously).
  nonisolated static func gpu(_ ms: Double, frame: Int) {
    #if DEBUG || PERF_PROBE
    Task { @MainActor in
      if let i = pending.firstIndex(where: { $0.seq == frame }) { pending[i].gpu3D += ms }
      else if current?.seq == frame { current?.gpu3D += ms }
      else { gpuLate[frame, default: 0] += ms }
    }
    #endif
  }

  /// The link paused: the interaction is over, write what we have.
  static func idle() {
    #if DEBUG || PERF_PROBE
    guard enabled else { return }
    finish()
    flush()
    #endif
  }

  #if DEBUG || PERF_PROBE
  private static func finish() {
    guard var f = current else { return }
    current = nil
    if f.busy == 0 { f.busy = (CACurrentMediaTime() - f.start) * 1000 }
    if let g = gpuLate.removeValue(forKey: f.seq) { f.gpu3D += g }
    pending.append(f)
    if pending.count >= 600 { flush() }
  }

  private static func installObserver() {
    guard observer == nil else { return }
    for (name, order) in [("o0", 0), ("o1m", 1_000_000), ("o1999999", 1_999_999), ("o2000001", 2_000_001)] {
      let obs = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.beforeWaiting.rawValue, true, order) { _, _ in
        MainActor.assumeIsolated {
          guard let f = current, f.busy == 0, f.marks[name] == nil else { return }
          current?.marks[name] = (CACurrentMediaTime() - f.start) * 1000
        }
      }
      CFRunLoopAddObserver(CFRunLoopGetMain(), obs, .commonModes)
    }
    let obs = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.beforeWaiting.rawValue, true, Int.max) { _, _ in
      MainActor.assumeIsolated {
        guard let f = current, f.busy == 0 else { return }
        current?.busy = (CACurrentMediaTime() - f.start) * 1000
      }
    }
    CFRunLoopAddObserver(CFRunLoopGetMain(), obs, .commonModes)
    observer = obs
  }

  private static func flush() {
    guard !pending.isEmpty else { return }
    let frames = pending
    pending = []
    chunk += 1
    let name = "\(launch)-\(String(format: "%04d", chunk)).jsonl"
    DispatchQueue.global(qos: .utility).async {
      let enc = JSONEncoder()
      enc.outputFormatting = .sortedKeys
      var data = Data()
      for f in frames {
        if let line = try? enc.encode(f) { data.append(line); data.append(0x0A) }
      }
      guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
      let dir = docs.appendingPathComponent("perf", isDirectory: true)
      try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
      try? data.write(to: dir.appendingPathComponent(name))
    }
  }
  #endif
}

/// A repeatable interaction script for measurements (launch argument
/// `-GKBench`; debug / PERF_PROBE builds only): knob drags and flicks, then
/// pick-up, turn-over and set-down, driven through the same entry points the
/// gesture recognizers call, with `GlowknobPerf.phase` naming each step.
@MainActor
enum GlowknobPerfBench {
  static var requested: Bool {
    #if DEBUG || PERF_PROBE
    return ProcessInfo.processInfo.arguments.contains("-GKBench")
    #else
    return false
    #endif
  }

  static func run(control: GlowknobTactileControl, lift: GlowknobConsoleLift) async {
    func wait(_ s: Double) async { try? await Task.sleep(for: .milliseconds(Int(s * 1000))) }
    await wait(5)
    for round in 0..<2 {
      for i in 0..<3 {
        let dir = i % 2 == 0 ? 1.0 : -1.0
        GlowknobPerf.phase = "knobDrag"
        control.dialPanBegan()
        control.dialPanStarted()
        for step in 0..<60 {
          control.dialPanChanged(translationX: dir * Double(step) * 2)
          await wait(1.0 / 60)
        }
        GlowknobPerf.phase = "knobFlick"
        control.dialPanEnded(velocityX: dir * 1800)
        control.dialPanFinalized()
        await wait(2.5)
      }
      GlowknobPerf.phase = "pickUp"
      lift.pickUpStarted()
      lift.pickUpEnded(velocity: .zero)
      await wait(2)
      GlowknobPerf.phase = "turn"
      lift.turnStarted()
      lift.turnEnded(velocity: CGPoint(x: 1500, y: 0))
      await wait(2.5)
      GlowknobPerf.phase = "setDown"
      lift.putDown()
      await wait(3)
      GlowknobPerf.phase = "idle\(round)"
      await wait(1)
    }
    GlowknobPerf.phase = "done"
    GlowknobPerf.idle()
  }
}
