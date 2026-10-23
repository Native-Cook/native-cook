import SwiftUI

/// The numbers and the timeline of Glow File Uploader, the same in the three stacks.
enum GlowFileUploaderMotion {
    /// One loop of the autoPlay, in seconds. Every ambient period divides it, so the loop has no jump.
    static let loop = 12.0
    // The autoPlay's timeline, in seconds from the start of a loop: "Select files" twice (the second
    // file fails), "Retry" on it, then "Continue" once both are done.
    static let selectPresses = [0.8, 1.75]
    static let retryPress = 4.7
    static let continuePress = 8.3
    static let pressFor = 0.12
    /// The row the autoPlay retries: the second one.
    static let autoRetryRow = 1
    /// Seconds after the last touch before the rows leave and the autoPlay starts again.
    static let userHold = 4.0
    /// The list holds at most this many rows.
    static let maxRows = 3

    struct File {
        let name: String
        /// Where its first upload stops and fails, as a share of the file; nil when it goes through.
        let failsAt: Double?
    }

    /// The files "Select files" adds, in order, starting again after the last. A retry always succeeds.
    static let files: [File] = [
        File(name: "Website_styles.pdf", failsAt: nil),
        File(name: "profile_styles.css", failsAt: 0.64),
        File(name: "brand_guide.fig", failsAt: nil),
        File(name: "hero_photo.png", failsAt: 0.4),
    ]

    struct Spring {
        let mass: Double
        let stiffness: Double
        let damping: Double
    }

    /// A pressed button coming back up.
    static let release = Spring(mass: 1, stiffness: 380, damping: 20)
    /// The clip badge's shake after "Select files": a couple of wobbles.
    static let shake = Spring(mass: 1, stiffness: 300, damping: 9)
    /// A new row opening its place in the list.
    static let arrive = Spring(mass: 1, stiffness: 260, damping: 24)
    /// The green disc of a finished upload, overshooting a little.
    static let doneDisc = Spring(mass: 1, stiffness: 320, damping: 15)
    /// The red "!" disc of a failed upload popping in.
    static let alertPop = Spring(mass: 1, stiffness: 400, damping: 14)

    // Ambient, always running (also at rest): the zone's glow breathes, its hairlines drift right by one
    // spacing, and the halo behind the clip badge pulses.
    static let breathePeriod = 4.0
    static let breatheLow = 0.82
    static let breatheHigh = 1.0
    static let driftPeriod = 6.0
    static let haloPeriod = 3.0
    static let haloScaleLow = 0.92
    static let haloScaleHigh = 1.08
    static let haloOpacityLow = 0.22
    static let haloOpacityHigh = 0.34

    // Presses: down over `pressIn`, back up on `release`.
    static let pressIn = 0.1
    static let selectPressScale = 0.95
    static let buttonPressScale = 0.97
    static let retryPressScale = 0.94

    // A file added: the zone flares (more glow and a touch bigger) and settles, the badge shakes, and a
    // ring leaves it.
    static let flareGain = 0.45
    static let flareScale = 0.012
    static let flareFor = 0.7
    static let shakeAngle = -18.0
    static let pulseFor = 0.7
    static let pulseGrow = 2.6
    static let pulseOpacity = 0.5
    // The new row's content drops in.
    static let contentDelay = 0.04
    static let contentFor = 0.25
    static let contentDrop = -14.0
    static let contentScale = 0.94

    // An upload, from 0 to 100%.
    static let uploadFor = 1.6
    static let uploadCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
    // Done: the ring gives way to the green disc, the check draws, the row's fill fades.
    static let discFrom = 0.6
    static let discFadeFor = 0.1
    static let ringOutFor = 0.1
    static let checkDelay = 0.06
    static let checkFor = 0.28
    static let fillFadeFor = 0.3

    // Failed: the fill turns red and drains, the ring shrinks away, the row shakes, the "Upload failed"
    // pill opens and "Retry" slides in.
    static let failTintFor = 0.12
    static let drainFor = 0.35
    static let ringShrinkFor = 0.15
    static let rowShakeFor = 0.6
    static let rowShakeAmplitude = 7.0
    static let rowShakeDecay = 7.0
    static let rowShakeFrequency = 6.0
    static let pillOpenFor = 0.32
    static let retryDelay = 0.1
    static let retryFor = 0.25
    static let retrySlide = 10.0

    // "Retry" released: its arrows spin a turn, the pill closes, and the upload starts again.
    static let spinFor = 0.55
    static let pillCloseFor = 0.2
    static let restartDelay = 0.15

    // "Continue": lights up when every file is done, with one sheen across it; pressed, it shows
    // "Uploaded" for a while and the rows leave.
    static let enableFor = 0.3
    static let sheenDelay = 0.1
    static let sheenFor = 0.8
    static let sheenWidth = 60.0
    static let labelFor = 0.2
    static let uploadedFor = 0.9

    // Rows leaving, the bottom one first.
    static let leaveStagger = 0.07
    static let leaveFadeFor = 0.22
    static let leaveLift = -10.0
    static let leaveScale = 0.96
    static let collapseFor = 0.3

    /// "Select files" and "Cancel" going to and from their disabled look.
    static let disableFor = 0.2
    /// The disabled look: label (and border) at this opacity.
    static let disabledOpacity = 0.4
    /// Reduced motion: every change is a fade of this length, in place.
    static let fade = 0.2
    /// Seconds after the last event until nothing moves but the ambient.
    static let settle = 1.2

    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let easeIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.55, y: 0), endControlPoint: UnitPoint(x: 1, y: 0.45))
    static let sheenCurve = UnitCurve.easeInOut

    static let never = -1e6

    struct Press: Equatable {
        var downAt = never
        var upAt = never
        var isDown: Bool { downAt > upAt }
    }

    struct Row: Equatable, Identifiable {
        let id: Int
        /// Index into `files`.
        let file: Int
        /// When it was added; its first upload starts at once.
        let addedAt: Double
        /// The press on "Retry", and when its upload was sent again (`never` if not).
        var retry = Press()
        var retriedAt = never
        /// When its "Cancel" or "Continue" batch starts to leave (the row counts until then), and when
        /// this row itself starts to go.
        var leaveBatch = Double.infinity
        var leaveAt = Double.infinity

        var retried: Bool { retriedAt != never }
    }

    /// Everything that happened, as times on the clock in seconds.
    struct Events: Equatable {
        var rows: [Row] = []
        var select = Press()
        var cancel = Press()
        var proceed = Press()
        /// When "Continue" was released and its label turned to "Uploaded".
        var uploadedAt = never
        var nextFile = 0
        var nextID = 0
    }

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    /// A spring let go from 0 towards 1 at rest, `t` seconds later (the closed form, so every stack agrees).
    static func spring(_ t: Double, _ s: Spring) -> Double {
        guard t > 0 else { return 0 }
        let w0 = (s.stiffness / s.mass).squareRoot()
        let zeta = s.damping / (2 * (s.stiffness * s.mass).squareRoot())
        if zeta >= 1 { return 1 - exp(-w0 * t) * (1 + w0 * t) }
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
    }

    // MARK: Ambient

    /// The zone's glow, 0.82 at the start of a loop, 1 half a period later.
    static func breathe(_ t: Double, still: Bool) -> Double {
        if still { return breatheHigh }
        let c = cos(2 * .pi * t / breathePeriod)
        return (breatheLow + breatheHigh) / 2 - (breatheHigh - breatheLow) / 2 * c
    }

    /// How far the hairlines have drifted, as a share of their spacing.
    static func drift(_ t: Double, still: Bool) -> Double {
        if still { return 0 }
        let k = t / driftPeriod
        return k - floor(k)
    }

    /// The halo's scale and opacity.
    static func halo(_ t: Double, still: Bool) -> (scale: Double, opacity: Double) {
        let c = still ? 0 : cos(2 * .pi * t / haloPeriod)
        return (
            (haloScaleLow + haloScaleHigh) / 2 - (haloScaleHigh - haloScaleLow) / 2 * c,
            (haloOpacityLow + haloOpacityHigh) / 2 - (haloOpacityHigh - haloOpacityLow) / 2 * c
        )
    }

    // MARK: Uploads

    /// Seconds the upload curve takes to reach `share` of the file.
    static func timeToReach(_ share: Double) -> Double {
        var lo = 0.0
        var hi = 1.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            if uploadCurve.value(at: mid) < share { lo = mid } else { hi = mid }
        }
        return uploadFor * (lo + hi) / 2
    }

    struct Upload {
        /// When the current attempt starts and ends (finishes or fails).
        let start: Double
        let end: Double
        /// Where the current attempt stops, if it fails.
        let stopsAt: Double?
        /// When the first attempt failed, or `never` for a file that goes through.
        let failedAt: Double
    }

    static func upload(_ r: Row) -> Upload {
        let point = files[r.file].failsAt
        let failedAt = point.map { r.addedAt + timeToReach($0) } ?? never
        if r.retried {
            let start = r.retriedAt + restartDelay
            return Upload(start: start, end: start + uploadFor, stopsAt: nil, failedAt: failedAt)
        }
        return Upload(start: r.addedAt, end: point == nil ? r.addedAt + uploadFor : failedAt, stopsAt: point, failedAt: failedAt)
    }

    enum Status: Int { case uploading, done, failed }

    static func status(_ r: Row, at t: Double) -> Status {
        let u = upload(r)
        if t >= u.end { return u.stopsAt == nil ? .done : .failed }
        // Between a "Retry" and its restart the row still reads as failed.
        return t < u.start ? .failed : .uploading
    }

    /// The share of the file sent at `t`.
    static func progress(_ u: Upload, at t: Double) -> Double {
        guard t >= u.start else { return 0 }
        let v = uploadCurve.value(at: clamp01((t - u.start) / uploadFor))
        return u.stopsAt.map { min(v, $0) } ?? v
    }

    // MARK: The list

    /// The rows that count at `t`: added, and not yet sent away by "Cancel" or "Continue".
    static func present(_ e: Events, at t: Double) -> [Row] {
        e.rows.filter { $0.addedAt <= t && t < $0.leaveBatch }
    }

    /// Sends the rows that count away, the bottom one first, starting at `batch`.
    static func leave(_ e: Events, batch: Double, now: Double) -> Events {
        var e = e
        var k = 0.0
        for i in e.rows.indices.reversed() where e.rows[i].addedAt <= now && now < e.rows[i].leaveBatch {
            e.rows[i].leaveBatch = min(e.rows[i].leaveBatch, batch)
            e.rows[i].leaveAt = e.rows[i].leaveBatch + k * leaveStagger
            k += 1
        }
        return e
    }

    /// When the rows sent away have all gone.
    static func goneAt(_ e: Events) -> Double {
        e.rows.map(\.leaveAt).filter(\.isFinite).map { $0 + collapseFor }.max() ?? never
    }

    /// One code per counted row, its id and its status, so a change can be felt (haptics).
    static func statusKey(_ e: Events, at t: Double) -> [Int] {
        present(e, at: t).map { $0.id * 4 + status($0, at: t).rawValue }
    }

    /// The latest time a counted row is still uploading or waiting to leave, or `never`.
    static func busyUntil(_ e: Events, now: Double) -> Double {
        var until = never
        for r in present(e, at: now) {
            let u = upload(r)
            if u.end > now { until = max(until, u.end) }
            if r.leaveBatch.isFinite { until = max(until, r.leaveBatch) }
        }
        return until
    }

    static func anyPressed(_ e: Events) -> Bool {
        e.select.isDown || e.cancel.isDown || e.proceed.isDown || e.rows.contains { $0.retry.isDown }
    }

    /// When nothing will move any more but the ambient (infinity while a finger is down).
    static func quietAt(_ e: Events) -> Double {
        if anyPressed(e) { return .infinity }
        var t = max(e.select.upAt, e.cancel.upAt, e.proceed.upAt, e.uploadedAt + uploadedFor) + settle
        for r in e.rows {
            let u = upload(r)
            t = max(t, r.addedAt + settle, u.end + settle, u.failedAt + settle, r.retriedAt + settle)
            if r.leaveAt.isFinite { t = max(t, r.leaveAt + settle) }
        }
        return t
    }

    // MARK: The autoPlay

    /// The events of the autoPlay at `now` for loops that started at `start`.
    static func autoEvents(now: Double, start: Double) -> Events {
        let elapsed = max(0, now - start)
        let round = floor(elapsed / loop)
        let tau = elapsed - round * loop
        let base = now - tau
        var e = Events()
        e.nextID = Int(round) * 10
        for at in selectPresses where tau >= at {
            e.select.downAt = base + at
            guard tau >= at + pressFor else { continue }
            e.select.upAt = base + at + pressFor
            e.rows.append(Row(id: e.nextID, file: e.nextFile, addedAt: e.select.upAt))
            e.nextID += 1
            e.nextFile = (e.nextFile + 1) % files.count
        }
        if tau >= retryPress, autoRetryRow < e.rows.count {
            e.rows[autoRetryRow].retry.downAt = base + retryPress
            if tau >= retryPress + pressFor {
                e.rows[autoRetryRow].retry.upAt = base + retryPress + pressFor
                e.rows[autoRetryRow].retriedAt = base + retryPress + pressFor
            }
        }
        if tau >= continuePress {
            e.proceed.downAt = base + continuePress
            if tau >= continuePress + pressFor {
                let up = base + continuePress + pressFor
                e.proceed.upAt = up
                e.uploadedAt = up
                e = leave(e, batch: up + uploadedFor, now: up)
            }
        }
        e.rows.removeAll { now >= $0.leaveAt + collapseFor }
        return e
    }

    // MARK: Poses

    /// How far a press has the button down: 0 to 1, springing back after release.
    static func pressAmount(_ p: Press, now: Double) -> Double {
        if p.isDown { return easeOut.value(at: clamp01((now - p.downAt) / pressIn)) }
        return easeOut.value(at: clamp01((p.upAt - p.downAt) / pressIn)) * (1 - spring(now - p.upAt, release))
    }

    static func pressScale(_ p: Press, now: Double, scale: Double) -> Double {
        1 - (1 - scale) * pressAmount(p, now: now)
    }

    /// A 0-1-0 swell after the latest file was added, for the zone's flare.
    static func flare(_ e: Events, now: Double, still: Bool) -> Double {
        guard !still, let added = e.rows.map(\.addedAt).max() else { return 0 }
        let t = now - added
        guard t >= 0, t < flareFor else { return 0 }
        return sin(.pi * easeOut.value(at: t / flareFor))
    }

    /// The clip badge's turn, in degrees.
    static func badgeTurn(_ e: Events, now: Double, still: Bool) -> Double {
        guard !still, let added = e.rows.map(\.addedAt).max(), now >= added else { return 0 }
        return shakeAngle * (1 - spring(now - added, shake))
    }

    /// The ring leaving the badge: its scale and opacity, or nil when there is none.
    static func pulse(_ e: Events, now: Double, still: Bool) -> (scale: Double, opacity: Double)? {
        guard !still, let added = e.rows.map(\.addedAt).max() else { return nil }
        let t = now - added
        guard t >= 0, t < pulseFor else { return nil }
        let k = easeOut.value(at: t / pulseFor)
        return (1 + (pulseGrow - 1) * k, pulseOpacity * (1 - k))
    }

    /// A level that moves linearly towards 1 or 0 for `duration` after each change, and when it last
    /// turned on.
    static func level(changes: [Double], now: Double, duration: Double, on: (Double) -> Bool) -> (value: Double, rose: Double) {
        var value = 0.0
        var target = 0.0
        var last = never
        var rose = never
        for t in changes.filter({ $0 <= now }).sorted() {
            value = approach(value, target, (t - last) / duration)
            let next = on(t) ? 1.0 : 0.0
            if next == 1, target == 0 { rose = t }
            target = next
            last = t
        }
        return (approach(value, target, (now - last) / duration), rose)
    }

    private static func approach(_ value: Double, _ target: Double, _ step: Double) -> Double {
        target > value ? min(target, value + step) : max(target, value - step)
    }

    /// How disabled "Select files" looks (the list is full): 0 to 1.
    static func selectFull(_ e: Events, now: Double) -> Double {
        let changes = e.rows.flatMap { [$0.addedAt, $0.leaveBatch] }
        return level(changes: changes, now: now, duration: disableFor) { present(e, at: $0).count >= maxRows }.value
    }

    /// How enabled "Cancel" looks (the list is not empty): 0 to 1.
    static func cancelOn(_ e: Events, now: Double) -> Double {
        let changes = e.rows.flatMap { [$0.addedAt, $0.leaveBatch] }
        return level(changes: changes, now: now, duration: disableFor) { !present(e, at: $0).isEmpty }.value
    }

    struct Proceed {
        /// How lit "Continue" looks, 0 to 1.
        var on = 0.0
        /// Where the sheen is across the button, 0 to 1, or nil.
        var sheen: Double?
        /// How much of the label is "Uploaded", 0 to 1.
        var uploaded = 0.0
        /// Whether a tap does anything.
        var enabled = false
    }

    static func proceed(_ e: Events, now: Double, still: Bool) -> Proceed {
        let lit: (Double) -> Bool = { t in
            let rows = present(e, at: t)
            return !rows.isEmpty && rows.allSatisfy { status($0, at: t) == .done }
        }
        let changes = e.rows.flatMap { [$0.addedAt, upload($0).end, $0.leaveBatch] }.filter(\.isFinite)
        let level = level(changes: changes, now: now, duration: enableFor, on: lit)
        var p = Proceed(on: level.value)
        if !still, lit(now) {
            let k = (now - level.rose - sheenDelay) / sheenFor
            if k >= 0, k <= 1 { p.sheen = sheenCurve.value(at: k) }
        }
        let shown = clamp01((now - e.uploadedAt) / labelFor)
        let hidden = clamp01((now - e.uploadedAt - uploadedFor) / labelFor)
        p.uploaded = shown * (1 - hidden)
        p.enabled = lit(now) && now >= e.uploadedAt + uploadedFor
        return p
    }

    struct RowPose {
        /// How much of its place (row and gap) the row takes, 0 to 1.
        var slot = 0.0
        var y = 0.0
        var scale = 1.0
        var opacity = 1.0
        var shake = 0.0
        /// The progress fill: its width as a share of the row, opacity, and how red it has turned.
        var fill = 0.0
        var fillOpacity = 0.0
        var fillRed = 0.0
        /// The bright leading edge of the fill.
        var edge = 0.0
        var ring = 0.0
        var ringScale = 1.0
        var ringOpacity = 0.0
        var disc = 0.0
        var discOpacity = 0.0
        var check = 0.0
        /// The "Upload failed" pill: how far it is open, its opacity and its "!" disc's scale.
        var pill = 0.0
        var pillOpacity = 0.0
        var alert = 0.0
        var retry = 0.0
        var retryX = 0.0
        var spin = 0.0
        var canRetry = false
        var leaving = false
    }

    static func rowPose(_ r: Row, now: Double, still: Bool) -> RowPose {
        var p = RowPose()
        let since = now - r.addedAt
        if still {
            p.slot = easeInOut.value(at: clamp01(since / fade))
            p.opacity = clamp01(since / fade)
        } else {
            p.slot = spring(since, arrive)
            let k = easeOut.value(at: clamp01((since - contentDelay) / contentFor))
            p.y = contentDrop * (1 - k)
            p.scale = contentScale + (1 - contentScale) * k
            p.opacity = k
        }
        if now >= r.leaveAt {
            let t = now - r.leaveAt
            p.leaving = true
            if still {
                let k = clamp01(t / fade)
                p.opacity *= 1 - k
                p.slot *= 1 - easeInOut.value(at: k)
            } else {
                let k = easeOut.value(at: clamp01(t / leaveFadeFor))
                p.opacity *= 1 - k
                p.y += leaveLift * k
                p.scale *= 1 - (1 - leaveScale) * k
                p.slot *= 1 - easeInOut.value(at: clamp01(t / collapseFor))
            }
        }

        let u = upload(r)
        if now >= u.start && now < u.end {
            let v = progress(u, at: now)
            p.fill = v
            p.fillOpacity = 1
            p.edge = 1
            p.ring = v
            p.ringOpacity = 1
            // After a retry the ring grows back in.
            if r.retried {
                let k = clamp01((now - u.start) / ringShrinkFor)
                if still { p.ringOpacity = k } else { p.ringScale = easeOut.value(at: k) }
            }
        } else if now >= u.end && u.stopsAt == nil {
            let t = now - u.end
            p.fill = 1
            p.fillOpacity = 1 - clamp01(t / fillFadeFor)
            p.edge = p.fillOpacity
            p.ring = 1
            p.ringOpacity = 1 - clamp01(t / ringOutFor)
            if still {
                p.disc = 1
                p.discOpacity = clamp01(t / fade)
                p.check = 1
            } else {
                p.disc = discFrom + (1 - discFrom) * spring(t, doneDisc)
                p.discOpacity = clamp01(t / discFadeFor)
                p.check = easeOut.value(at: clamp01((t - checkDelay) / checkFor))
            }
        }

        if u.failedAt != never, now >= u.failedAt, let point = files[r.file].failsAt {
            let t = now - u.failedAt
            if now < u.start || !r.retried {
                p.fill = point * (1 - easeInOut.value(at: clamp01(t / drainFor)))
                p.fillOpacity = 1
                p.fillRed = clamp01(t / failTintFor)
                p.edge = 1 - clamp01(t / ringShrinkFor)
                p.ring = point
                if still {
                    p.ringOpacity = 1 - clamp01(t / fade)
                } else {
                    p.ringOpacity = 1
                    p.ringScale = 1 - easeIn.value(at: clamp01(t / ringShrinkFor))
                }
            }
            if still {
                p.pill = 1
                p.pillOpacity = clamp01(t / fade)
                p.alert = 1
                p.retry = clamp01(t / fade)
            } else {
                if t < rowShakeFor {
                    p.shake = rowShakeAmplitude * exp(-t * rowShakeDecay) * sin(2 * .pi * t * rowShakeFrequency)
                }
                p.pill = easeOut.value(at: clamp01(t / pillOpenFor))
                p.pillOpacity = 1
                p.alert = spring(t, alertPop)
                let k = easeOut.value(at: clamp01((t - retryDelay) / retryFor))
                p.retry = k
                p.retryX = retrySlide * (1 - k)
            }
            if r.retried {
                let t = now - r.retriedAt
                let c = clamp01(t / pillCloseFor)
                p.pillOpacity *= 1 - c
                p.retry *= 1 - c
                if !still {
                    p.pill *= 1 - easeOut.value(at: c)
                    p.spin = 360 * easeOut.value(at: clamp01(t / spinFor))
                }
            } else {
                p.canRetry = !p.leaving
            }
        }
        return p
    }
}
