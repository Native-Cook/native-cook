import Foundation
import CoreGraphics
import Combine

private final class LumaRunStudioCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false
    func cancel() { lock.lock(); value = true; lock.unlock() }
    func isCancelled() -> Bool { lock.lock(); defer { lock.unlock() }; return value }
}

/// One serial sort owner. Mutable pixel/index scratch is never touched from the UI thread.
private final class LumaRunStudioWorker: @unchecked Sendable {
    let queue = DispatchQueue(label: "LumaRunStudio.sort", qos: .userInitiated)
    private let source: LumaRunStudioSource
    private lazy var sorter = LumaRunStudioSorter(source: source)
    init(source: LumaRunStudioSource) { self.source = source }
    func generate(_ params: [LumaRunStudioParams], token: LumaRunStudioCancellation,
                  completion: @escaping @Sendable ([LumaRunStudioBitmap]?) -> Void) {
        queue.async { [self] in
            var result: [LumaRunStudioBitmap] = []
            var aliases: [LumaRunStudioParams: CGImage] = [:]
            for p in params {
                if token.isCancelled() { completion(nil); return }
                if let image = aliases[p] { result.append(.init(image: image, params: p)); continue }
                guard let bitmap = sorter.render(params: p, cancelled: token.isCancelled) else { completion(nil); return }
                aliases[p] = bitmap.image; result.append(bitmap)
            }
            completion(token.isCancelled() ? nil : result)
        }
    }
}

/// Atomic image/complete-tuple publications. Main owns the displayed pair and gesture/event stamps.
@MainActor
final class LumaRunStudioCache: ObservableObject {
    @Published private(set) var publication: LumaRunStudioPublication?
    @Published private(set) var recovery: LumaRunStudioRecoveryPublication?
    private let worker: LumaRunStudioWorker
    private let source: LumaRunStudioSource
    private var generation = 0
    private var sequence = 0
    private var acceptedSequence = -1
    private var loop: [LumaRunStudioBitmap]?
    private var loopLookup: [LumaRunStudioParams: CGImage] = [:]
    private var preparingLoop = false
    private var pendingLoop: [LumaRunStudioKnot]?
    private var loopWaiters: [CheckedContinuation<[LumaRunStudioBitmap], Never>] = []
    private var lru: [LumaRunStudioBitmap] = []
    private var busy = false
    private var currentKind = 0 // 0 none, 1 startup, 2 manual, 3 recovery
    private var currentToken: LumaRunStudioCancellation?
    private var runningManual: Manual?
    private var pendingManual: Manual?
    private var pendingRecovery: Recovery?
    private var recoverySequence = 0
    private var lastManualStart = -Double.infinity
    private var wake: Task<Void, Never>?
    private var latestRequested: LumaRunStudioParams?

    private struct Manual: Sendable { let params: LumaRunStudioParams; let generation: Int; let sequence: Int }
    private struct Recovery: Sendable { let params: [LumaRunStudioParams]; let generation: Int; let sequence: Int }

    init(source: LumaRunStudioSource) { self.source = source; worker = .init(source: source) }

    /// The full 64 authored knots are prepared once before Main starts its epoch.
    func prepareLoop(knots: [LumaRunStudioKnot]) async -> [LumaRunStudioBitmap] {
        precondition(knots.count == 64)
        if let loop { return loop }
        return await withCheckedContinuation { continuation in
            loopWaiters.append(continuation)
            guard !preparingLoop else { return }
            preparingLoop = true; pendingLoop = knots; pump()
        }
    }

    static func quantized(_ p: LumaRunStudioParams) -> LumaRunStudioParams {
        func nearest(_ x: Double, _ step: Double) -> Double { floor(x / step + 0.5) * step }
        let low = min(168, max(16, LumaRunStudioSorter.roundHalfUp(p.low * 200)))
        let high = min(184, max(low + 16, LumaRunStudioSorter.roundHalfUp(p.high * 200)))
        return .init(q: min(1, max(0, nearest(p.q, 1.0 / 64))),
            angle: min(90, max(-90, nearest(p.angle, 1))), low: Double(low) / 200, high: Double(high) / 200,
            roiX: min(30, max(-30, nearest(p.roiX, 1))), roiY: min(42, max(-42, nearest(p.roiY, 1))))
    }

    /// Leaving the screen cancels startup as well as ordinary input work.
    /// A future appearance may prepare again; cancelled waiters receive no partial cache.
    func cancelPreparation() {
        guard loop == nil else { return }
        pendingLoop = nil
        if currentKind == 1 { currentToken?.cancel() }
        else if preparingLoop {
            preparingLoop = false
            let waiters = loopWaiters; loopWaiters.removeAll()
            for waiter in waiters { waiter.resume(returning: []) }
        }
    }

    func invalidate(generation: Int) {
        self.generation = generation; acceptedSequence = sequence
        pendingManual = nil; pendingRecovery = nil; latestRequested = nil
        if currentKind != 1 { currentToken?.cancel() }
        wake?.cancel(); wake = nil
        publication = nil; recovery = nil; recoverySequence += 1
    }

    func requestManual(params: LumaRunStudioParams, generation: Int) {
        if generation != self.generation { invalidate(generation: generation) }
        let p = Self.quantized(params)
        guard p != latestRequested else { return }
        latestRequested = p; sequence += 1
        let request = Manual(params: p, generation: generation, sequence: sequence)
        pendingRecovery = nil; recovery = nil; recoverySequence += 1
        if currentKind == 3 { currentToken?.cancel() }
        if let cached = lookup(p) {
            // Returning to an already accepted key must also obsolete an in-flight other key.
            pendingManual = nil; currentToken?.cancel(); acceptedSequence = sequence
            if publication?.generation != generation || publication?.bitmap.params != p {
                publication = .init(generation: generation, sequence: sequence, bitmap: cached)
            }
            return
        }
        if let runningManual, runningManual.generation == generation, runningManual.params == p,
           currentToken?.isCancelled() == false {
            pendingManual = nil; return
        }
        pendingManual = request // Exactly one latest pending tuple replaces all older pending work.
        pump()
    }

    func prepareRecovery(params: [LumaRunStudioParams], generation: Int) {
        precondition(params.count == 6)
        if generation != self.generation { invalidate(generation: generation) }
        sequence += 1; recoverySequence = sequence
        pendingManual = nil; latestRequested = nil; wake?.cancel(); wake = nil
        recovery = nil; currentToken?.cancel()
        pendingRecovery = .init(params: params, generation: generation, sequence: recoverySequence)
        pump()
    }

    private func lookup(_ p: LumaRunStudioParams) -> LumaRunStudioBitmap? {
        if p.q == 0 { return .init(image: source.canonical, params: p) }
        if let image = loopLookup[p] { return .init(image: image, params: p) }
        if let i = lru.firstIndex(where: { $0.params == p }) {
            let result = lru.remove(at: i); lru.append(result); return result
        }
        return nil
    }
    private func remember(_ bitmap: LumaRunStudioBitmap) {
        lru.removeAll { $0.params == bitmap.params }; lru.append(bitmap)
        if lru.count > 6 { lru.removeFirst(lru.count - 6) }
    }

    private func pump() {
        guard !busy else { return }
        if let knots = pendingLoop {
            pendingLoop = nil; busy = true; currentKind = 1
            // Startup cannot be superseded by an input generation; it publishes no manual state.
            let token = LumaRunStudioCancellation(); currentToken = token
            worker.generate(knots.map(\.params), token: token) { [self] result in
                Task { @MainActor [self] in
                    let bitmaps = result ?? []
                    if bitmaps.count == 64 {
                        self.loop = bitmaps
                        for b in bitmaps { self.loopLookup[b.params] = b.image }
                    }
                    self.preparingLoop = false; self.busy = false; self.currentKind = 0; self.currentToken = nil
                    let waiters = self.loopWaiters; self.loopWaiters.removeAll()
                    for waiter in waiters { waiter.resume(returning: bitmaps) }
                    self.pump()
                }
            }
            return
        }
        guard !preparingLoop else { return }
        if let request = pendingRecovery {
            pendingRecovery = nil; busy = true; currentKind = 3
            let token = LumaRunStudioCancellation(); currentToken = token
            worker.generate(request.params, token: token) { [weak self] result in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    self.busy = false; self.currentKind = 0; self.currentToken = nil
                    if request.generation == self.generation, request.sequence == self.recoverySequence,
                       !token.isCancelled(), let result, result.count == 6 {
                        for bitmap in result { self.remember(bitmap) }
                        self.recovery = .init(generation: request.generation, bitmaps: result)
                    }
                    self.pump()
                }
            }
            return
        }
        guard let request = pendingManual else { return }
        let now = ProcessInfo.processInfo.systemUptime
        let remaining = 0.125 - (now - lastManualStart)
        if remaining > 0 {
            if wake == nil {
                wake = Task { @MainActor [weak self] in
                    do { try await Task.sleep(for: .seconds(remaining)) } catch { return }
                    guard let self else { return }; self.wake = nil; self.pump()
                }
            }
            return
        }
        pendingManual = nil; runningManual = request; busy = true; currentKind = 2
        lastManualStart = now
        let token = LumaRunStudioCancellation(); currentToken = token
        worker.generate([request.params], token: token) { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.busy = false; self.currentKind = 0; self.currentToken = nil; self.runningManual = nil
                if request.generation == self.generation, request.sequence > self.acceptedSequence,
                   !token.isCancelled(), let bitmap = result?.first {
                    self.remember(bitmap); self.acceptedSequence = request.sequence
                    self.publication = .init(generation: request.generation, sequence: request.sequence, bitmap: bitmap)
                }
                self.pump()
            }
        }
    }
}
