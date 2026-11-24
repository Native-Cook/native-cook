import SwiftUI
import UIKit

/*
 The hand-over between the loop and a person. While the loop plays every value comes from
 `AddressLookupMapMotion.pose(t)`; a touch copies the frame at that moment into
 `AddressLookupMapValue`s and the person drives from there. Two seconds after the last touch (with
 no field focused) everything eases back to the first frame and the loop starts again. Nothing
 here is written per frame: the TimelineView samples the values against its clock.
*/
final class AddressLookupMapModel: ObservableObject {
    typealias M = AddressLookupMapMotion
    typealias D = AddressLookupMapData
    typealias V = AddressLookupMapValue

    enum Field: Hashable { case query, unit, postcode, city }

    // Changes on keystrokes and picks only.
    @Published private(set) var user = false
    @Published private(set) var uQuery = ""
    @Published private(set) var uSelected: Int?
    @Published var unit = ""
    @Published var postcode = ""
    @Published var city = ""
    /// The switch's state when the screen opens and after the idle return.
    static let leaveAtDoorDefault = true
    @Published var leaveAtDoor = AddressLookupMapModel.leaveAtDoorDefault
    /// Whether each callout is up while a person drives (discrete, for VoiceOver; the fades are the values below).
    @Published private(set) var uCallA = false
    @Published private(set) var uCallB = false
    @Published private(set) var isPaused = false

    /// 0 = the loop, 1 = a person, 2 = easing back to rest.
    private(set) var mode = 0
    private var loopStart = Date().timeIntervalSinceReferenceDate
    private var lastTouch = 0.0
    private var returnAt = 0.0
    /// When a pick's own film (fly, drop, fills) ends: the idle return also waits `idle` after this.
    private var filmEnd = 0.0
    private var editing = false
    private(set) var auto = true
    private(set) var still = false
    private(set) var reduced = false
    private(set) var s0 = 0.33
    private var configured = false
    private var watcher: Task<Void, Never>?

    // The person's values, one per field of the pose.
    private var focus = V(0), text = V(1), placeholder = V(1), list = V(0), count = V(0), choose = V(0)
    private var chosen = 0
    private var camX = V(M.overviewX), camY = V(M.overviewY), camS = V(0.33)
    private var drop = V(0), pin = V(0), ring1 = V(0), ring2 = V(0), lift = V(0)
    private var callA = V(0), callB = V(0), ready = V(0)
    private var fills = [V(0), V(0), V(0)]
    private var rowY = D.addresses.map { _ in V(0) }
    private var rowA = D.addresses.map { _ in V(0) }
    /// How far the screen rides up while a lower field is edited above the keyboard.
    private var raise = V(0)
    private var keyboard: CGFloat = 0
    private var raisedTop: CGFloat = -1
    private var geo: AddressLookupMapGeo?

    // Map gestures.
    private var dragging = false
    private var pinching = false
    private var startX = 0.0, startY = 0.0, startS = 1.0

    private var observers: [NSObjectProtocol] = []

    static var clock: Double { Date().timeIntervalSinceReferenceDate }

    func configure(_ g: AddressLookupMapGeo, autoPlay: Bool, reduced: Bool) {
        geo = g
        s0 = M.overviewScale(Double(g.width))
        guard !configured else { return }
        configured = true
        self.reduced = reduced
        auto = autoPlay && !reduced
        still = autoPlay && reduced
        camS.set(s0)
        loopStart = Self.clock
        isPaused = !auto
        startWatcher()
        let nc = NotificationCenter.default
        observers.append(nc.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { [weak self] n in
            let frame = (n.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect) ?? .zero
            self?.keyboard = frame.height
            self?.applyRaise()
        })
        observers.append(nc.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { [weak self] _ in
            self?.keyboard = 0
            self?.raisedTop = -1
            self?.applyRaise()
        })
    }

    func stop() {
        watcher?.cancel()
        watcher = nil
        observers.forEach(NotificationCenter.default.removeObserver)
        observers = []
        configured = false
    }

    // MARK: Time

    func loopTime(_ now: Double) -> Double {
        if still { return M.tStill }
        if !auto { return 0 }
        let e = now - loopStart
        return e <= 0 ? 0 : e.truncatingRemainder(dividingBy: M.loop)
    }

    func pose(_ now: Double) -> M.Pose {
        if mode == 0 { return M.pose(loopTime(now), s0) }
        var p = M.Pose()
        p.focus = focus.value(now)
        p.caret = 0
        p.text = text.value(now)
        p.placeholder = placeholder.value(now)
        p.list = list.value(now)
        p.count = count.value(now)
        p.choose = choose.value(now)
        p.chosen = chosen
        p.rowY = rowY.map { $0.value(now) }
        p.rowA = rowA.map { $0.value(now) }
        p.camX = camX.value(now)
        p.camY = camY.value(now)
        p.camS = camS.value(now)
        p.drop = drop.value(now)
        p.pin = pin.value(now)
        p.ring1 = ring1.value(now)
        p.ring2 = ring2.value(now)
        p.lift = lift.value(now)
        p.callA = callA.value(now)
        p.callB = callB.value(now)
        p.fill = fills.map { $0.value(now) }
        p.ready = ready.value(now)
        return p
    }

    func dot(_ now: Double) -> M.Dot {
        mode == 0 && auto ? M.dot(loopTime(now)) : M.Dot()
    }

    func raiseValue(_ now: Double) -> Double { raise.value(now) }

    /// Whether a callout is showing, as a discrete state for accessibility (never a per-frame opacity).
    func calloutShown(_ b: Bool, _ now: Double) -> Bool {
        if mode != 0 { return b ? uCallB : uCallA }
        let t = loopTime(now)
        return b ? (t >= M.callBAt && t < M.resetAt + 0.1) : (t >= M.callAAt && t < M.dragAt + 0.09)
    }

    /// Whether a suggestion row is showing, as a discrete state for accessibility.
    func rowShown(_ i: Int, _ c: Content) -> Bool {
        if user { return tappable(i) }
        return c.listOpen && D.rank(c.query).contains(i)
    }

    /// What the field and the details show: the script's step, or the person's text.
    struct Content {
        var query: String
        var selected: Int?
        var filled: Bool
        var unit: String
        var postcode: String
        var city: String
        var listOpen: Bool
    }

    func content(_ now: Double) -> Content {
        if user {
            return Content(query: uQuery, selected: uSelected, filled: uSelected != nil, unit: unit, postcode: postcode, city: city,
                           listOpen: !uQuery.isEmpty && uSelected == nil)
        }
        let k = mode == 0 ? M.stepAt(loopTime(now)) : 0
        let s = M.script[k]
        let a = s.selected.map { D.addresses[$0] }
        let filled = a != nil && s.filled
        return Content(query: s.query, selected: s.selected, filled: filled,
                       unit: filled ? a!.unit : "", postcode: filled ? a!.postcode : "", city: filled ? a!.city : "",
                       listOpen: k >= 1 && k <= M.typed)
    }

    /// Rows the person can tap.
    func tappable(_ i: Int) -> Bool {
        guard user, !uQuery.isEmpty, uSelected == nil else { return false }
        return D.rank(uQuery).contains(i)
    }

    var ok: Bool { user ? (uSelected != nil && postcode.count == 4 && !city.trimmingCharacters(in: .whitespaces).isEmpty) : true }

    // MARK: Hand-over

    /// Any touch: the loop stops where it is and the person drives from there.
    func takeOver() {
        let now = Self.clock
        if mode == 0 {
            let t = loopTime(now)
            let s = M.script[M.stepAt(t)]
            let a = s.selected.map { D.addresses[$0] }
            uQuery = s.query
            uSelected = s.selected
            unit = s.filled ? (a?.unit ?? "") : ""
            postcode = s.filled ? (a?.postcode ?? "") : ""
            city = s.filled ? (a?.city ?? "") : ""
            let p = M.pose(t, s0)
            write(p)
            // A touch during the reset stretch would keep the field's value faded out (the loop clears it next);
            // under a finger the field always shows what it holds.
            text.set(1)
            placeholder.set(1)
            uCallA = p.callA > 0.5
            uCallB = p.callB > 0.5
            mode = 1
            user = true
        } else if mode == 2 {
            mode = 1
        }
        lastTouch = now
        isPaused = false
    }

    private func touch() {
        lastTouch = Self.clock
        isPaused = false
    }

    /// Copies a pose into the person's values.
    private func write(_ p: M.Pose) {
        focus.set(p.focus)
        text.set(p.text)
        placeholder.set(p.placeholder)
        list.set(p.list)
        count.set(p.count)
        choose.set(p.choose)
        chosen = p.chosen
        camX.set(p.camX)
        camY.set(p.camY)
        camS.set(p.camS)
        drop.set(p.drop)
        pin.set(p.pin)
        ring1.set(0)
        ring2.set(0)
        lift.set(0)
        callA.set(p.callA)
        callB.set(p.callB)
        for j in 0 ..< 3 { fills[j].set(p.fill[j]) }
        ready.set(p.ready)
        for i in 0 ..< rowY.count {
            rowY[i].set(p.rowY[i])
            rowA[i].set(p.rowA[i])
        }
    }

    private func easeToRest(_ d: Double) {
        let now = Self.clock
        let rest = M.pose(0, s0)
        let quick = d * 0.4
        uCallA = false
        uCallB = false
        for v in [\AddressLookupMapModel.focus, \.text, \.list, \.callA, \.callB, \.ready, \.pin, \.lift] {
            self[keyPath: v].timing(0, quick, now: now)
        }
        for j in 0 ..< 3 { fills[j].timing(0, quick, now: now) }
        camX.timing(rest.camX, d, now: now, curve: M.easeInOutCubic)
        camY.timing(rest.camY, d, now: now, curve: M.easeInOutCubic)
        camS.timing(rest.camS, d, now: now, curve: M.easeInOutCubic)
    }

    private func resetToAuto() {
        user = false
        uQuery = ""
        uSelected = nil
        unit = ""
        postcode = ""
        city = ""
        leaveAtDoor = Self.leaveAtDoorDefault
        mode = 0
        loopStart = Self.clock
        isPaused = !auto
    }

    private func startWatcher() {
        watcher?.cancel()
        watcher = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 100_000_000)
                self?.tick()
            }
        }
    }

    private func tick() {
        let now = Self.clock
        if mode == 1 {
            if !editing && !dragging && !pinching && now - lastTouch > M.idle && now - filmEnd > M.idle {
                mode = 2
                returnAt = now
                easeToRest(reduced ? 0 : M.returnLen)
                isPaused = false
            } else if isPaused == false && !dragging && !pinching && !moving(now) && keyboard == 0 {
                isPaused = true
            }
        } else if mode == 2 && now - returnAt > M.returnLen {
            resetToAuto()
        }
    }

    private func moving(_ now: Double) -> Bool {
        let all = [focus, text, placeholder, list, count, choose, camX, camY, camS, drop, pin, ring1, ring2, lift, callA, callB, ready, raise] + fills + rowY + rowA
        return all.contains { $0.isMoving(now) }
    }

    // MARK: The person's actions

    private func listUI(open: Bool, n: Int, d: Double) {
        let now = Self.clock
        list.timing(open ? 1 : 0, d * 0.3, now: now, curve: { Ease.out.value(at: $0) })
        count.spring(Double(n), d, damping: 0.82, now: now)
        choose.set(0)
        text.set(1)
        placeholder.set(1)
    }

    /// Rows follow the person's query on springs (re-ranked, entering ones fade in).
    private func updateRows() {
        let now = Self.clock
        let open = !uQuery.isEmpty && uSelected == nil
        let ranked = D.rank(open ? uQuery : "")
        for i in 0 ..< rowY.count {
            let slot = ranked.firstIndex(of: i) ?? -1
            let picked = uSelected == i
            let visible = (open && slot >= 0) || picked
            if slot >= 0 { rowY[i].spring(Double(slot), reduced ? 0 : 0.42, damping: 0.78, now: now) }
            rowA[i].timing(visible ? 1 : 0, reduced ? 0 : (visible ? 0.26 : 0.16), now: now)
        }
    }

    func setQuery(_ v: String) {
        // A pick writes the street into the field; the field echoing that same text back (as focus ends) is no edit,
        // and must not undo the pick (it would reopen the list with the one matching row).
        if user, v == uQuery { return }
        takeOver()
        uQuery = v
        if uSelected != nil { uSelected = nil }
        listUI(open: !v.isEmpty, n: D.rank(v).count, d: reduced ? 0 : 0.42)
        updateRows()
    }

    func choose(_ i: Int) {
        takeOver()
        let a = D.addresses[i]
        uQuery = a.street
        uSelected = i
        unit = a.unit
        postcode = a.postcode
        city = a.city
        UISelectionFeedbackGenerator().selectionChanged()
        updateRows()
        let now = Self.clock
        let k = reduced ? 0.0 : 1.0
        chosen = i
        choose.set(0)
        choose.timing(1, M.chooseLen * k, now: now, curve: M.easeInOutCubic)
        focus.timing(0, 0.2 * k, now: now)
        text.set(0)
        text.timing(1, 0.23 * k, now: now, delay: 0.2 * k)
        camX.timing(a.x, M.flyLen * k, now: now, curve: M.easeInOutCubic)
        camY.timing(a.y, M.flyLen * k, now: now, curve: M.easeInOutCubic)
        camS.timing(M.near, M.flyLen * k, now: now, curve: M.easeInOutCubic)
        let land = (M.flyLen - 0.1) * k
        pin.set(0)
        drop.set(0)
        pin.timing(1, 0.08 * k, now: now, delay: land)
        drop.timing(1, M.dropLen * k, now: now, delay: land)
        ring1.set(0)
        ring2.set(0)
        ring1.timing(1, M.ringLen * k, now: now, delay: land + 0.33 * k)
        ring2.timing(1, M.ringLen * k, now: now, delay: land + 0.75 * k)
        callB.timing(0, 0.15 * k, now: now)
        uCallA = true
        uCallB = false
        callA.set(0)
        callA.timing(1, 0.3 * k, now: now, delay: land + 0.55 * k, curve: { Ease.out.value(at: $0) })
        for j in 0 ..< 3 {
            fills[j].set(0)
            fills[j].timing(1, M.fillLen * k, now: now, delay: land + (0.6 + Double(j) * M.fillGap) * k, curve: { Ease.out.value(at: $0) })
        }
        ready.timing(ok ? 1 : 0, 0.4 * k, now: now, delay: land + 1.05 * k)
        // The idle return counts from the end of the pick's film (the form filled, Continue ready), not from the
        // tap, or it would ease the screen back while the fields are still filling in.
        filmEnd = now + land + 1.45 * k
        if !reduced {
            DispatchQueue.main.asyncAfter(deadline: .now() + (M.flyLen - 0.1 + M.dropLen * 0.5)) {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
    }

    func submit() {
        if let first = D.rank(uQuery).first, uSelected == nil { choose(first) }
    }

    func clearQuery() {
        takeOver()
        uQuery = ""
        uSelected = nil
        listUI(open: false, n: 0, d: reduced ? 0 : 0.3)
        updateRows()
    }

    func recenter() {
        takeOver()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let now = Self.clock
        let (x, y, s) = uSelected.map { (D.addresses[$0].x, D.addresses[$0].y, M.near) } ?? (M.overviewX, M.overviewY, s0)
        camX.timing(x, 0.7, now: now, curve: M.easeInOutCubic)
        camY.timing(y, 0.7, now: now, curve: M.easeInOutCubic)
        camS.timing(s, 0.7, now: now, curve: M.easeInOutCubic)
    }

    func setDetail(_ j: Int, _ v: String) {
        takeOver()
        switch j {
        case 0: unit = v
        case 1: postcode = String(v.filter(\.isNumber).prefix(4))
        default: city = v
        }
        fills[j].set(1)
        readyFollows()
    }

    func setLeaveAtDoor(_ v: Bool) {
        takeOver()
        leaveAtDoor = v
    }

    private func readyFollows() {
        guard user else { return }
        ready.timing(ok ? 1 : 0, reduced ? 0 : 0.3, now: Self.clock)
    }

    func continuePressed() {
        takeOver()
        if !ok || uSelected == nil {
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
            return
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    // MARK: Focus and the keyboard

    func focusChanged(_ field: Field?) {
        let now = Self.clock
        if let field {
            takeOver()
            editing = true
            if field == .query {
                focus.timing(1, 0.2, now: now)
                raisedTop = -1
            } else {
                raisedTop = field == .city ? (geo?.row2 ?? -1) : (geo?.row1 ?? -1)
            }
            applyRaise()
        } else {
            editing = false
            focus.timing(0, 0.2, now: now)
            lastTouch = now
        }
        isPaused = false
    }

    private func applyRaise() {
        guard let g = geo else { return }
        let need = (raisedTop < 0 || keyboard == 0) ? 0 : raisedTop + g.boxH + 16 - (g.H - keyboard)
        raise.timing(max(0, Double(need)), reduced ? 0 : 0.28, now: Self.clock, curve: M.easeOutCubic)
        isPaused = false
    }

    // MARK: Map gestures

    private static func rubber(_ x: Double, _ lo: Double, _ hi: Double) -> Double {
        if x < lo { return lo - (lo - x) / (1 + (lo - x) / 160) }
        if x > hi { return hi + (x - hi) / (1 + (x - hi) / 160) }
        return x
    }

    /// How far the camera's centre may go on one axis at scale s: far enough that the (turned) map window stays
    /// on the drawn town, so a pan, a fling or a pinch never shows the blank paper past its edge.
    private func cameraBound(_ s: Double, _ axis: Int) -> (Double, Double) {
        let w = Double(geo?.width ?? 353), h = Double(geo?.mapH ?? 400)
        let c = abs(cos(M.mapRot)), n = abs(sin(M.mapRot))
        let half = (axis == 0 ? w * c + h * n : w * n + h * c) / 2 / s
        let lo = min(D.world / 2, half)
        return (lo, D.world - lo)
    }

    func dragChanged(_ translation: CGSize) {
        let now = Self.clock
        if !dragging {
            dragging = true
            takeOver()
            startX = camX.value(now)
            startY = camY.value(now)
            lift.spring(1, reduced ? 0 : 0.3, damping: 0.8, now: now)
            callA.timing(0, 0.15, now: now)
            callB.timing(0, 0.15, now: now)
            uCallA = false
            uCallB = false
        }
        let sc = camS.value(now)
        let o = M.dragToWorld(translation.width, translation.height, sc)
        let bx = cameraBound(sc, 0), by = cameraBound(sc, 1)
        camX.set(Self.rubber(startX + o.0, bx.0, bx.1))
        camY.set(Self.rubber(startY + o.1, by.0, by.1))
        touch()
    }

    func dragEnded(velocity: CGSize) {
        guard dragging else { return }
        dragging = false
        let now = Self.clock
        let sc = camS.value(now)
        let v = M.dragToWorld(velocity.width, velocity.height, sc)
        let bx = cameraBound(sc, 0), by = cameraBound(sc, 1)
        camX.decay(velocity: v.0, lo: bx.0, hi: bx.1, now: now)
        camY.decay(velocity: v.1, lo: by.0, hi: by.1, now: now)
        lift.spring(0, reduced ? 0 : 0.45, damping: 0.55, now: now)
        if pin.value(now) > 0.5 {
            callB.timing(1, 0.28, now: now, delay: 0.26)
            uCallB = true
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
        touch()
    }

    func pinchChanged(_ scale: CGFloat) {
        let now = Self.clock
        if !pinching {
            pinching = true
            takeOver()
            startS = camS.value(now)
        }
        camS.set(Self.rubber(startS * scale, s0 * M.minScaleK, M.maxScale))
        touch()
    }

    func pinchEnded() {
        guard pinching else { return }
        pinching = false
        let now = Self.clock
        let s = camS.value(now)
        let target = min(M.maxScale, max(s0 * M.minScaleK, s))
        camS.spring(target, reduced ? 0 : 0.4, damping: 0.85, now: now)
        // Zooming out widens the window: bring the centre back inside the town at the scale it settles on.
        let bx = cameraBound(target, 0), by = cameraBound(target, 1)
        let cx = camX.value(now), cy = camY.value(now)
        if cx < bx.0 || cx > bx.1 { camX.spring(min(bx.1, max(bx.0, cx)), reduced ? 0 : 0.4, damping: 0.85, now: now) }
        if cy < by.0 || cy > by.1 { camY.spring(min(by.1, max(by.0, cy)), reduced ? 0 : 0.4, damping: 0.85, now: now) }
        touch()
    }
}
