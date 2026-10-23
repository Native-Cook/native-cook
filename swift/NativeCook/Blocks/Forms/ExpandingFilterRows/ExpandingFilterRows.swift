import SwiftUI
import UIKit

let ExpandingFilterRowsLoop = 10.5

struct ExpandingFilterRowsFriend: Equatable { var initials: String? = nil; var color: String }
enum ExpandingFilterRowsTime: Equatable { case single(Int), range(Int, Int) }
struct ExpandingFilterRowsFilters: Equatable { var date: String; var friends: Bool; var time: ExpandingFilterRowsTime; var genres: [String]; var price: String? }

private enum ExpandingFilterRowsStyle {
    static let dates = ["18 Dec 2024", "19 Dec 2024", "20 Dec 2024", "21 Dec 2024"]
    static let friends = [ExpandingFilterRowsFriend(initials: "MT", color: "#4d8df0"), ExpandingFilterRowsFriend(color: "#2fa58f"), ExpandingFilterRowsFriend(color: "#d98a4e")]
    static let genres = ["Afrobeat", "Alternative", "Bluegrass", "Blues", "Chant", "Classical", "Country", "Dancehall", "Disco", "EDM", "Electronic", "Experimental", "Folk", "Funk", "Gospel", "Hip-Hop", "House", "Indie", "Jazz", "Latin", "Metal", "Pop", "Reggae", "Soul"]
    static let chosen = ["Afrobeat", "Bluegrass", "Chant", "EDM", "Gospel", "Jazz", "Latin", "Soul"], prices = ["All price", "Free", "Under $30", "Under $60"]
    static let backdrop = Color.black, sheet = Color(hex: 0x121214), row = Color(hex: 0x1c1c1f), rowPressed = Color(hex: 0x26262a), well = Color(hex: 0x161618), ink = Color.white, mute = Color(hex: 0x8e8e93), blue = Color(hex: 0x1f6bff)
    static let side = 20.0, rowHeight = 60.0, rowGap = 16.0, rowRadius = 16.0, wellInset = 6.0, avatar = 26.0, avatarPitch = 34.0, button = 52.0, buttonWidth = 158.0, fade = 44.0, clear = 8.0, rightStart = 3.0
    static let topPad = 8.0, sheetRadius = 30.0, grabberTop = 8.0, grabberWidth = 36.0, grabberHeight = 5.0, grabberRadius = 2.5, grabber = Color(hex: 0x3a3a3e), headerTop = 9.0, header = 44.0, listTop = 30.0
    static let titleText = 17.0, titleLine = 22.0, closeTop = 7.0, close = 30.0, closeRadius = 15.0, closeFill = Color(hex: 0x2a2a2d), closeInk = Color(hex: 0xb8b8be), closeIcon = 15.0, closeHit = 8.0
    static let headPad = 18.0, icon = 18.0, labelGap = 10.0, labelText = 16.0, labelLine = 21.0, valueLeft = 0.4, valueText = 16.0, valueLine = 21.0
    static let friendWidth = 96.0, avatarsLeft = 14.0, initialsText = 10.0, initialsLine = 13.0, headTop = 6.0, head = 9.0, shouldersTop = 17.0, shoulders = 18.0, portraitAlpha = 0.9
    static let bottomMin = 12.0, bottomPad = 8.0, resetHit = 12.0, buttonCount = 11.0, buttonAlpha = 0.62
}

private enum ExpandingFilterRowsMotion {
    static let openStiffness = 115.0, openDamping = 14.5, mass = 1.0, reducedOpen = 0.16, range = 0.38, tickerStiffness = 150.0, tickerDamping = 16.0, tick = ExpandingFilterRowsLoop / 3
    static let press = 0.16, openTime = 0.8, rangeAt = 1.9, turnTime = 2.6, turnTimeBy = -5.0, turnTimeFor = 1.2, openGenres = 4.4, turnGenres = 5.4, turnGenresBy = 5.0, turnGenresFor = 1.0, pick = 6.9, close = 7.9, reset = 8.9
    static let userHold = 3.0, resumeAfter = 0.9, wellRise = -12.0, rowIn = 0.06, rowOut = 0.26, buttonScale = 0.97, buttonPress = 0.12, valueIn = 0.22, valueOut = 0.14, countIn = 0.22, countOut = 0.16, countTravel = 25.0, frameLimit = 0.064, energyThreshold = 6e-9
}

enum ExpandingFilterRowsCurve {
    case quadratic, inOutCubic, outCubic
    func sample(_ t: Double) -> Double { switch self { case .quadratic: return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2; case .inOutCubic: return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2; case .outCubic: return 1 - pow(1 - t, 3) } }
}

enum ExpandingFilterRowsMath {
    static func rounded(_ value: Double) -> Double { guard value.isFinite else { return value }; let lower = floor(value); return value - lower < 0.5 ? lower : lower + 1 }
    static func firstTime(_ value: Double) -> Int { value.isFinite ? Int(min(Double(ExpandingFilterRowsTimes - 1), max(0, rounded(value)))) : 0 }
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func fade(_ value: Double, _ from: Double, _ to: Double) -> Double { clamp((value - from) / (to - from)) }
}

private struct ExpandingFilterRowsScalar {
    private(set) var value: Double
    private var target: Double, from: Double, velocity = 0.0, energy = 0.0, duration = 0.0, stiffness = 0.0, damping = 0.0, mass = 1.0
    private var start = Date.distantPast, last = Date.distantPast
    private var curve = ExpandingFilterRowsCurve.quadratic
    private var mode = 0
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func set(_ value: Double) { self.value = value; target = value; from = value; velocity = 0; mode = 0 }
    mutating func cancel() { set(value) }
    mutating func time(_ next: Double, duration: Double, curve: ExpandingFilterRowsCurve, at date: Date) {
        if value == next { set(next); return }
        if mode != 1 || target != next { from = value; start = date }
        target = next; self.duration = duration; self.curve = curve; velocity = 0; mode = 1; advance(date)
    }
    mutating func spring(_ next: Double, stiffness: Double, damping: Double, mass: Double, at date: Date) {
        if mode == 2 && target == next { advance(date); return }
        if value == next { set(next); return }
        if mode != 2 { last = date; velocity = 0 }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; self.stiffness = stiffness; self.damping = damping; self.mass = mass; energy = 0.5 * stiffness * pow(value - next, 2); mode = 2; advance(date)
    }
    mutating func advance(_ date: Date) {
        if mode == 1 {
            let t = ExpandingFilterRowsMath.clamp(date.timeIntervalSince(start) / duration); value = from + (target - from) * curve.sample(t); if t == 1 { mode = 0 }; return
        }
        guard mode == 2 else { return }
        let dt = min(ExpandingFilterRowsMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(stiffness / mass), decay = damping / (2 * mass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped, wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt))); value = target + attenuation * wave
        } else {
            let b = velocity + omega * x, attenuation = exp(-omega * dt); value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * stiffness * pow(value - target, 2) + 0.5 * mass * velocity * velocity
        if energy == 0 || current / energy <= ExpandingFilterRowsMotion.energyThreshold { value = target; velocity = 0; mode = 0 }
    }
}

@MainActor
final class ExpandingFilterRowsWheel: ObservableObject {
    @Published private(set) var value: Double
    var byHand = false
    private var scalar: ExpandingFilterRowsScalar
    init(_ value: Double) { self.value = value; scalar = ExpandingFilterRowsScalar(value) }
    private func publish() { if value != scalar.value { value = scalar.value } }
    func set(_ value: Double) { scalar.set(value); publish() }
    func cancel() { scalar.cancel(); publish() }
    func time(_ target: Double, duration: Double, curve: ExpandingFilterRowsCurve = .quadratic, at date: Date = .now) { scalar.time(target, duration: duration, curve: curve, at: date); publish() }
    func spring(_ target: Double, stiffness: Double = 190, damping: Double = 24, mass: Double = 1, at date: Date = .now) { scalar.spring(target, stiffness: stiffness, damping: damping, mass: mass, at: date); publish() }
    func advance(_ date: Date) { scalar.advance(date); publish() }
}

private enum ExpandingFilterRowsExpanded { case time, genres }
private enum ExpandingFilterRowsPressed { case time, genres, reset }
private struct ExpandingFilterRowsReport: Equatable { let date: Int; let friends: Bool; let price: Int; let ranged: Bool; let start: Int; let end: Int; let chosenRevision: Int }

@MainActor
private final class ExpandingFilterRowsState: ObservableObject {
    private typealias S = ExpandingFilterRowsStyle
    private typealias M = ExpandingFilterRowsMotion
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var expanded: ExpandingFilterRowsExpanded?
    private(set) var dateIndex = 0, withFriends = true, priceIndex = 0, ranged = false
    private(set) var startTime: Int, endTime: Int
    private(set) var chosen: [String], moving: String?, fresh: String?
    private(set) var pressed: ExpandingFilterRowsPressed?
    let start: ExpandingFilterRowsWheel, end: ExpandingFilterRowsWheel
    let timeShown = ExpandingFilterRowsWheel(0), genresShown = ExpandingFilterRowsWheel(0), range = ExpandingFilterRowsWheel(0), leftPosition = ExpandingFilterRowsWheel(0), rightPosition = ExpandingFilterRowsWheel(S.rightStart), turn = ExpandingFilterRowsWheel(0)
    var dates = S.dates, genres = S.genres, prices = S.prices, chosenGenres = S.chosen, firstTime: Int
    var still = false
    var onChange: ((ExpandingFilterRowsFilters) -> Void)?, onContinue: ((ExpandingFilterRowsFilters) -> Void)?, onClose: (() -> Void)?
    private var pending: [Task<Void, Never>] = []
    private var chosenRevision = 0, chosenIsInitial = true
    private var reported: ExpandingFilterRowsReport?
    init(firstTime: Int, chosen: [String]) { self.firstTime = firstTime; self.chosen = chosen; chosenGenres = chosen; startTime = firstTime; endTime = firstTime; start = ExpandingFilterRowsWheel(Double(firstTime)); end = ExpandingFilterRowsWheel(Double(firstTime)) }
    var others: [String] { genres.filter { !chosen.contains($0) } }
    var price: String? { priceIndex == 0 ? nil : prices.indices.contains(priceIndex) ? prices[priceIndex] : nil }
    var count: Int { 2 + (withFriends ? 1 : 0) + (chosen.isEmpty ? 0 : 1) + (price?.isEmpty == false ? 1 : 0) }
    var filters: ExpandingFilterRowsFilters { ExpandingFilterRowsFilters(date: dates.indices.contains(dateIndex) ? dates[dateIndex] : "", friends: withFriends, time: ranged ? .range(startTime, endTime) : .single(startTime), genres: chosen, price: price) }
    func report() {
        let key = ExpandingFilterRowsReport(date: dateIndex, friends: withFriends, price: priceIndex, ranged: ranged, start: startTime, end: endTime, chosenRevision: chosenRevision)
        if key != reported { reported = key; onChange?(filters) }
    }
    func touched() { touches += 1 }
    func expand(_ next: ExpandingFilterRowsExpanded?, at date: Date = .now) {
        expanded = next
        for (wheel, to) in [(timeShown, next == .time ? 1.0 : 0.0), (genresShown, next == .genres ? 1.0 : 0.0)] {
            if still { wheel.time(to, duration: M.reducedOpen, at: date) } else { wheel.spring(to, stiffness: M.openStiffness, damping: M.openDamping, mass: M.mass, at: date) }
        }; revision &+= 1
    }
    func toggle(_ row: ExpandingFilterRowsExpanded) { expand(expanded == row ? nil : row) }
    func setMode(_ next: Bool, auto: Bool = false) {
        if auto { start.byHand = false; end.byHand = false }
        ranged = next; if next { end.set(start.value); endTime = Int(ExpandingFilterRowsMath.rounded(start.value)) }
        range.time(next ? 1 : 0, duration: M.range, curve: .outCubic); revision &+= 1; report()
    }
    func onStart(_ half: Int, byHand: Bool) {
        startTime = half; if byHand { UISelectionFeedbackGenerator().selectionChanged() }
        if ranged && Double(half) > ExpandingFilterRowsMath.rounded(end.value) { end.spring(Double(half)) }; revision &+= 1; report()
    }
    func onEnd(_ half: Int, byHand: Bool) {
        endTime = half; if byHand { UISelectionFeedbackGenerator().selectionChanged() }
        if ranged && Double(half) < ExpandingFilterRowsMath.rounded(start.value) { start.spring(Double(half)) }; revision &+= 1; report()
    }
    func nextDate() { if !dates.isEmpty { dateIndex = (dateIndex + 1) % dates.count }; revision &+= 1; report() }
    func toggleFriends() { withFriends.toggle(); revision &+= 1; report() }
    func nextPrice() { if !prices.isEmpty { priceIndex = (priceIndex + 1) % prices.count }; revision &+= 1; report() }
    func move(_ name: String, pick: Bool) {
        guard moving == nil else { return }; moving = name; revision &+= 1
        let captured = chosen
        pending.append(Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: UInt64(ExpandingFilterRowsPickSeconds * 1e9)); try Task.checkCancellation() } catch { return }
            guard let self else { return }
            let raw = min(captured.count, Int(ExpandingFilterRowsMath.rounded(leftPosition.value)) + 1), at = raw < 0 ? max(0, captured.count + raw) : raw
            chosen = pick ? Array(captured.prefix(at)) + [name] + Array(captured.dropFirst(at)) : captured.filter { $0 != name }
            chosenRevision += 1; chosenIsInitial = false; fresh = name; moving = nil; revision &+= 1; report()
        })
    }
    func reset(auto: Bool = false) {
        if auto { start.byHand = false; end.byHand = false }
        expand(nil); dateIndex = 0; withFriends = true; priceIndex = 0; ranged = false; startTime = firstTime; endTime = firstTime
        if !chosenIsInitial || chosen != chosenGenres { chosenRevision += 1 }; chosen = chosenGenres; chosenIsInitial = true; fresh = nil
        range.time(0, duration: M.range); start.set(Double(firstTime)); end.set(Double(firstTime)); leftPosition.set(0); rightPosition.set(S.rightStart); revision &+= 1; report()
    }
    func continueAction() { onContinue?(filters) }
    func close() { onClose?() }
    func advance(_ date: Date) { for wheel in [timeShown, genresShown, start, end, range, leftPosition, rightPosition, turn] { wheel.advance(date) }; revision &+= 1 }
    func stop() { pending.forEach { $0.cancel() }; pending.removeAll() }
    func ticker(count: Int, reduced: Bool) async {
        guard !reduced && count >= 2 else { return }; let origin = Date.now; var step = 0
        do { while !Task.isCancelled { step += 1; let wait = origin.addingTimeInterval(Double(step) * M.tick).timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation(); turn.spring(Double(step), stiffness: M.tickerStiffness, damping: M.tickerDamping, mass: M.mass) } } catch { return }
    }
    func autoplay(enabled: Bool) async {
        defer { pressed = nil; revision &+= 1 }; guard enabled else { return }
        do {
            if touches > 0 { try await Task.sleep(nanoseconds: UInt64(M.userHold * 1e9)); try Task.checkCancellation(); reset(auto: true); try await Task.sleep(nanoseconds: UInt64(M.resumeAfter * 1e9)); try Task.checkCancellation() }
            var origin = Date.now
            while !Task.isCancelled {
                let events: [(Double, Int)] = [(M.openTime, 0), (M.openTime + M.press, 1), (M.rangeAt, 2), (M.turnTime, 3), (M.openGenres, 4), (M.openGenres + M.press, 5), (M.turnGenres, 6), (M.pick, 7), (M.close, 8), (M.close + M.press, 9), (M.reset, 10), (M.reset + M.press, 11)]
                for (at, action) in events {
                    let wait = origin.addingTimeInterval(at).timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation()
                    switch action {
                    case 0: pressed = .time
                    case 1: pressed = nil; toggle(.time)
                    case 2: setMode(true, auto: true)
                    case 3: start.byHand = false; end.byHand = false; start.time(Double(firstTime) + M.turnTimeBy, duration: M.turnTimeFor, curve: .inOutCubic)
                    case 4: pressed = .genres
                    case 5: pressed = nil; toggle(.genres)
                    case 6: rightPosition.time(S.rightStart + M.turnGenresBy, duration: M.turnGenresFor, curve: .inOutCubic)
                    case 7: let at = Int(S.rightStart + M.turnGenresBy); if others.indices.contains(at) { move(others[at], pick: true) }
                    case 8: pressed = .genres
                    case 9: pressed = nil; toggle(.genres)
                    case 10: pressed = .reset
                    default: pressed = nil; reset(auto: true)
                    }; revision &+= 1
                }
                origin = origin.addingTimeInterval(ExpandingFilterRowsLoop); let wait = origin.timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
            }
        } catch { return }
    }
}

private struct ExpandingFilterRowsLoopKey: Equatable { let enabled: Bool; let touches: Int; let firstTime: Int }
private struct ExpandingFilterRowsTickerKey: Equatable { let reduced: Bool; let count: Int }

struct ExpandingFilterRowsHit: View {
    let width: Double
    let height: Double
    let label: String
    var hint: String? = nil
    var disabled = false
    var selected = false
    var hold: ((Bool) -> Void)? = nil
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var pendingOut: Task<Void, Never>?
    @GestureState private var active = false
    private static let retention = 20.0, bottomRetention = 30.0, minimumPress = 0.13
    init(width: Double, height: Double, label: String, hint: String? = nil, disabled: Bool = false, selected: Bool = false, hold: ((Bool) -> Void)? = nil, activate: @escaping () -> Void) {
        self.width = width; self.height = height; self.label = label; self.hint = hint; self.disabled = disabled; self.selected = selected; self.hold = hold; self.activate = activate
    }
    private func contains(_ point: CGPoint) -> Bool { point.x > -Self.retention && point.x < width + Self.retention && point.y > -Self.retention && point.y < height + Self.bottomRetention }
    private func pressIn() { inside = true; activatedAt = .now; hold?(true) }
    private func pressOut() {
        inside = false; guard let hold else { return }; let wait = max(0, Self.minimumPress - Date.now.timeIntervalSince(activatedAt ?? .now))
        if wait == 0 { hold(false); return }
        pendingOut = Task { @MainActor in do { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)); try Task.checkCancellation(); hold(false) } catch { return } }
    }
    var body: some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).updating($active) { _, active, _ in active = true }
                .onChanged { event in if !began { pendingOut?.cancel(); began = true; pressIn() }; let next = contains(event.location); if next != inside { if next { pressIn() } else { pressOut() } } }
                .onEnded { _ in guard began else { return }; began = false; let perform = inside; if inside { pressOut() }; if perform { activate() } })
            .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { pressOut() } } } } }
            .onDisappear { pendingOut?.cancel(); hold?(false) }.allowsHitTesting(!disabled).disabled(disabled)
            .accessibilityElement(children: .ignore).accessibilityAddTraits(selected ? [.isButton, .isSelected] : [.isButton]).accessibilityLabel(label).accessibilityHint(hint ?? "")
            .accessibilityAction { if !disabled { activate() } }
    }
}

private enum ExpandingFilterRowsValue: Equatable {
    case text(String, String, Bool)
    case friends([ExpandingFilterRowsFriend], String)
    var key: String { switch self { case let .text(text, tail, _): return "text:" + text + tail; case .friends: return "friends" } }
}
private struct ExpandingFilterRowsValueDeparture: Identifiable { let id: Int; let value: ExpandingFilterRowsValue; let start: Date }
private struct ExpandingFilterRowsValueEntry { var value: ExpandingFilterRowsValue; var start: Date?; var departing: [ExpandingFilterRowsValueDeparture] = [] }
private struct ExpandingFilterRowsCountDeparture: Identifiable { let id: Int; let count: Int; let start: Date }

private final class ExpandingFilterRowsTextClock {
    private typealias M = ExpandingFilterRowsMotion
    private var values: [Int: ExpandingFilterRowsValueEntry] = [:]
    private var nextID = 0
    private(set) var count: Int?
    private(set) var countStart: Date?
    private(set) var countDeparting: [ExpandingFilterRowsCountDeparture] = []
    func update(_ index: Int, value: ExpandingFilterRowsValue, still: Bool, at date: Date) {
        guard var entry = values[index] else { values[index] = ExpandingFilterRowsValueEntry(value: value, start: nil); return }
        entry.departing.removeAll { date.timeIntervalSince($0.start) >= M.valueOut }
        if entry.value.key != value.key {
            if !still { nextID += 1; entry.departing.append(ExpandingFilterRowsValueDeparture(id: nextID, value: entry.value, start: date)) } else { entry.departing.removeAll() }
            entry.start = still ? nil : date
        }; entry.value = value; values[index] = entry
    }
    func entry(_ index: Int) -> ExpandingFilterRowsValueEntry { values[index] ?? ExpandingFilterRowsValueEntry(value: .text("", "", false), start: nil) }
    func updateCount(_ count: Int, still: Bool, at date: Date) {
        countDeparting.removeAll { date.timeIntervalSince($0.start) >= M.countOut }
        if self.count != count {
            if let old = self.count, !still { nextID += 1; countDeparting.append(ExpandingFilterRowsCountDeparture(id: nextID, count: old, start: date)) }
            if still { countDeparting.removeAll() }; self.count = count; countStart = still ? nil : date
        }
    }
    static func progress(_ start: Date?, date: Date, duration: Double) -> Double { guard let start else { return 1 }; return ExpandingFilterRowsCurve.quadratic.sample(ExpandingFilterRowsMath.clamp(date.timeIntervalSince(start) / duration)) }
}


private enum ExpandingFilterRowsCSS {
    private static let names: [String: UInt32] = [
        "transparent": 0x00000000,
        "aliceblue": 0xf0f8ffff,
        "antiquewhite": 0xfaebd7ff,
        "aqua": 0x00ffffff,
        "aquamarine": 0x7fffd4ff,
        "azure": 0xf0ffffff,
        "beige": 0xf5f5dcff,
        "bisque": 0xffe4c4ff,
        "black": 0x000000ff,
        "blanchedalmond": 0xffebcdff,
        "blue": 0x0000ffff,
        "blueviolet": 0x8a2be2ff,
        "brown": 0xa52a2aff,
        "burlywood": 0xdeb887ff,
        "burntsienna": 0xea7e5dff,
        "cadetblue": 0x5f9ea0ff,
        "chartreuse": 0x7fff00ff,
        "chocolate": 0xd2691eff,
        "coral": 0xff7f50ff,
        "cornflowerblue": 0x6495edff,
        "cornsilk": 0xfff8dcff,
        "crimson": 0xdc143cff,
        "cyan": 0x00ffffff,
        "darkblue": 0x00008bff,
        "darkcyan": 0x008b8bff,
        "darkgoldenrod": 0xb8860bff,
        "darkgray": 0xa9a9a9ff,
        "darkgreen": 0x006400ff,
        "darkgrey": 0xa9a9a9ff,
        "darkkhaki": 0xbdb76bff,
        "darkmagenta": 0x8b008bff,
        "darkolivegreen": 0x556b2fff,
        "darkorange": 0xff8c00ff,
        "darkorchid": 0x9932ccff,
        "darkred": 0x8b0000ff,
        "darksalmon": 0xe9967aff,
        "darkseagreen": 0x8fbc8fff,
        "darkslateblue": 0x483d8bff,
        "darkslategray": 0x2f4f4fff,
        "darkslategrey": 0x2f4f4fff,
        "darkturquoise": 0x00ced1ff,
        "darkviolet": 0x9400d3ff,
        "deeppink": 0xff1493ff,
        "deepskyblue": 0x00bfffff,
        "dimgray": 0x696969ff,
        "dimgrey": 0x696969ff,
        "dodgerblue": 0x1e90ffff,
        "firebrick": 0xb22222ff,
        "floralwhite": 0xfffaf0ff,
        "forestgreen": 0x228b22ff,
        "fuchsia": 0xff00ffff,
        "gainsboro": 0xdcdcdcff,
        "ghostwhite": 0xf8f8ffff,
        "gold": 0xffd700ff,
        "goldenrod": 0xdaa520ff,
        "gray": 0x808080ff,
        "green": 0x008000ff,
        "greenyellow": 0xadff2fff,
        "grey": 0x808080ff,
        "honeydew": 0xf0fff0ff,
        "hotpink": 0xff69b4ff,
        "indianred": 0xcd5c5cff,
        "indigo": 0x4b0082ff,
        "ivory": 0xfffff0ff,
        "khaki": 0xf0e68cff,
        "lavender": 0xe6e6faff,
        "lavenderblush": 0xfff0f5ff,
        "lawngreen": 0x7cfc00ff,
        "lemonchiffon": 0xfffacdff,
        "lightblue": 0xadd8e6ff,
        "lightcoral": 0xf08080ff,
        "lightcyan": 0xe0ffffff,
        "lightgoldenrodyellow": 0xfafad2ff,
        "lightgray": 0xd3d3d3ff,
        "lightgreen": 0x90ee90ff,
        "lightgrey": 0xd3d3d3ff,
        "lightpink": 0xffb6c1ff,
        "lightsalmon": 0xffa07aff,
        "lightseagreen": 0x20b2aaff,
        "lightskyblue": 0x87cefaff,
        "lightslategray": 0x778899ff,
        "lightslategrey": 0x778899ff,
        "lightsteelblue": 0xb0c4deff,
        "lightyellow": 0xffffe0ff,
        "lime": 0x00ff00ff,
        "limegreen": 0x32cd32ff,
        "linen": 0xfaf0e6ff,
        "magenta": 0xff00ffff,
        "maroon": 0x800000ff,
        "mediumaquamarine": 0x66cdaaff,
        "mediumblue": 0x0000cdff,
        "mediumorchid": 0xba55d3ff,
        "mediumpurple": 0x9370dbff,
        "mediumseagreen": 0x3cb371ff,
        "mediumslateblue": 0x7b68eeff,
        "mediumspringgreen": 0x00fa9aff,
        "mediumturquoise": 0x48d1ccff,
        "mediumvioletred": 0xc71585ff,
        "midnightblue": 0x191970ff,
        "mintcream": 0xf5fffaff,
        "mistyrose": 0xffe4e1ff,
        "moccasin": 0xffe4b5ff,
        "navajowhite": 0xffdeadff,
        "navy": 0x000080ff,
        "oldlace": 0xfdf5e6ff,
        "olive": 0x808000ff,
        "olivedrab": 0x6b8e23ff,
        "orange": 0xffa500ff,
        "orangered": 0xff4500ff,
        "orchid": 0xda70d6ff,
        "palegoldenrod": 0xeee8aaff,
        "palegreen": 0x98fb98ff,
        "paleturquoise": 0xafeeeeff,
        "palevioletred": 0xdb7093ff,
        "papayawhip": 0xffefd5ff,
        "peachpuff": 0xffdab9ff,
        "peru": 0xcd853fff,
        "pink": 0xffc0cbff,
        "plum": 0xdda0ddff,
        "powderblue": 0xb0e0e6ff,
        "purple": 0x800080ff,
        "rebeccapurple": 0x663399ff,
        "red": 0xff0000ff,
        "rosybrown": 0xbc8f8fff,
        "royalblue": 0x4169e1ff,
        "saddlebrown": 0x8b4513ff,
        "salmon": 0xfa8072ff,
        "sandybrown": 0xf4a460ff,
        "seagreen": 0x2e8b57ff,
        "seashell": 0xfff5eeff,
        "sienna": 0xa0522dff,
        "silver": 0xc0c0c0ff,
        "skyblue": 0x87ceebff,
        "slateblue": 0x6a5acdff,
        "slategray": 0x708090ff,
        "slategrey": 0x708090ff,
        "snow": 0xfffafaff,
        "springgreen": 0x00ff7fff,
        "steelblue": 0x4682b4ff,
        "tan": 0xd2b48cff,
        "teal": 0x008080ff,
        "thistle": 0xd8bfd8ff,
        "tomato": 0xff6347ff,
        "turquoise": 0x40e0d0ff,
        "violet": 0xee82eeff,
        "wheat": 0xf5deb3ff,
        "white": 0xffffffff,
        "whitesmoke": 0xf5f5f5ff,
        "yellow": 0xffff00ff,
        "yellowgreen": 0x9acd32ff
    ]
    private static let hex = try! NSRegularExpression(pattern: #"^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"#)
    private static let formats: [(String, Bool, NSRegularExpression)] = [
        ("rgb", false, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("rgb", true, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("rgb", true, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hsl", false, try! NSRegularExpression(pattern: #"hsl\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*\)"#)),
        ("hsl", true, try! NSRegularExpression(pattern: #"hsla\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hsl", true, try! NSRegularExpression(pattern: #"hsla\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hwb", false, try! NSRegularExpression(pattern: #"hwb\(\s*([-+]?\d*\.?\d+)\s*([-+]?\d*\.?\d+%)\s*([-+]?\d*\.?\d+%)\s*\)"#)),
        ("hwb", true, try! NSRegularExpression(pattern: #"hwb\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#))
    ]
    private static func clamp(_ value: Double, _ high: Double = 1) -> Double { min(high, max(0, value)) }
    private static func byte(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        let low = floor(value)
        return value - low < 0.5 ? low : low + 1
    }
    private static func hue(_ p: Double, _ q: Double, _ input: Double) -> Double {
        var t = input
        if t < 0 { t += 1 }; if t > 1 { t -= 1 }
        if t < 1 / 6 { return p + (q - p) * 6 * t }
        if t < 1 / 2 { return q }
        if t < 2 / 3 { return p + (q - p) * (2 / 3 - t) * 6 }
        return p
    }
    static func color(_ source: String) -> Color {
        if let value = names[source] { return Color(hex: value >> 8, opacity: Double(value & 255) / 255) }
        let ns = source as NSString, whole = NSRange(location: 0, length: (source as NSString).length)
        if hex.firstMatch(in: source, range: whole) != nil {
            var digits = String(source.dropFirst())
            if digits.count == 3 || digits.count == 4 { digits = digits.map { "\($0)\($0)" }.joined() }
            if digits.count == 6 { digits += "ff" }
            if let value = UInt32(digits, radix: 16) { return Color(hex: value >> 8, opacity: Double(value & 255) / 255) }
        }
        for (kind, alpha, pattern) in formats {
            guard let match = pattern.firstMatch(in: source, range: whole) else { continue }
            let values = (1..<match.numberOfRanges).map { Double(ns.substring(with: match.range(at: $0)).replacingOccurrences(of: "%", with: "")) ?? 0 }
            let opacity = alpha ? byte(clamp(values[3]) * 255) / 255 : 1
            if kind == "rgb" {
                return .rgba(clamp(values[0], 255).rounded(.towardZero), clamp(values[1], 255).rounded(.towardZero), clamp(values[2], 255).rounded(.towardZero), opacity)
            }
            let h = ((values[0].truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)) / 360
            let second = clamp(values[1] / 100), third = clamp(values[2] / 100)
            let channels: [Double]
            if kind == "hwb" {
                if second + third >= 1 { channels = Array(repeating: second / (second + third), count: 3) }
                else { channels = [h + 1 / 3, h, h - 1 / 3].map { hue(0, 1, $0) * (1 - second - third) + second } }
            } else {
                let q = third < 0.5 ? third * (1 + second) : third + second - third * second
                let p = 2 * third - q
                channels = [h + 1 / 3, h, h - 1 / 3].map { hue(p, q, $0) }
            }
            return .rgba(byte(channels[0] * 255), byte(channels[1] * 255), byte(channels[2] * 255), opacity)
        }
        return .clear
    }
}


private enum ExpandingFilterRowsText {
    static func width(_ value: String, size: Double, weight: UIFont.Weight, pixel: Double) -> Double { let raw = (value as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: size, weight: weight)]).width; return ceil(raw * pixel) / pixel }
    static func color(_ value: String) -> Color { ExpandingFilterRowsCSS.color(value) }
}

private struct ExpandingFilterRowsRow<Content: View, Value: View>: View {
    let icon: String
    let label: String
    let hint: String?
    let pressed: Bool
    let extra: Double
    let shown: Double
    let expanded: Bool
    let activate: () -> Void
    @ViewBuilder var value: () -> Value
    @ViewBuilder var content: () -> Content
    @State private var held = false
    @ScaledMetric(relativeTo: .body) private var labelSize = ExpandingFilterRowsStyle.labelText
    private typealias S = ExpandingFilterRowsStyle
    private typealias M = ExpandingFilterRowsMotion
    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width, font = UIFont.systemFont(ofSize: labelSize, weight: .regular), line = S.labelLine / S.labelText * labelSize
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .leading) {
                    Rectangle().fill(S.rowPressed).opacity(held || pressed ? 1 : 0).animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: held || pressed ? M.rowIn : M.rowOut), value: held || pressed)
                    HStack(spacing: S.labelGap) {
                        Image(systemName: icon).font(.system(size: S.icon)).foregroundStyle(S.mute).frame(width: S.icon, height: S.icon)
                        Text(label).font(Font(font)).foregroundStyle(S.mute).lineLimit(1).padding(.vertical, (line - font.lineHeight) / 2).frame(width: width - S.headPad * 2 - S.icon - S.labelGap, alignment: .leading)
                    }.padding(.horizontal, S.headPad).accessibilityHidden(true)
                    value().frame(width: width * (1 - S.valueLeft) - S.headPad, height: S.rowHeight, alignment: .trailing).offset(x: width * S.valueLeft)
                    ExpandingFilterRowsHit(width: width, height: S.rowHeight, label: label, hint: hint, hold: { held = $0 }, activate: activate)
                        .accessibilityValue(extra == 0 ? "" : expanded ? "Expanded" : "Collapsed")
                }.frame(width: width, height: S.rowHeight)
                if extra > 0 {
                    content().frame(width: width - S.wellInset * 2, height: extra - S.wellInset, alignment: .topLeading)
                        .background(S.well).clipShape(RoundedRectangle(cornerRadius: S.rowRadius - 4, style: .continuous))
                        .opacity(ExpandingFilterRowsMath.fade(shown, 0.1, 0.7)).offset(x: S.wellInset, y: S.rowHeight + (1 - min(1, shown)) * M.wellRise)
                        .allowsHitTesting(expanded).accessibilityHidden(!expanded)
                }
            }.frame(width: width, height: S.rowHeight + extra * max(0, shown), alignment: .topLeading)
                .background(S.row).clipShape(RoundedRectangle(cornerRadius: S.rowRadius, style: .continuous)).contentShape(Rectangle())
        }.frame(height: S.rowHeight + extra * max(0, shown))
    }
}

/// The list slides as one piece so an opened wheel remains above the bottom fade.
struct ExpandingFilterRows: View {
    var title: String
    var dates: [String]
    var friends: [ExpandingFilterRowsFriend]
    var friendsCount: String
    var time: Double
    var genres: [String]
    var chosenGenres: [String]
    var prices: [String]
    var datesLabel: String
    var friendsLabel: String
    var anyFriendsLabel: String
    var timeLabel: String
    var singleTimeLabel: String
    var timeRangeLabel: String
    var genresLabel: String
    var allGenresLabel: String
    var noGenresLabel: String
    var priceLabel: String
    var resetLabel: String
    var continueLabel: String
    var withLabel: String
    var autoPlay: Bool
    var onChange: ((ExpandingFilterRowsFilters) -> Void)?
    var onContinue: ((ExpandingFilterRowsFilters) -> Void)?
    var onClose: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @StateObject private var state: ExpandingFilterRowsState
    @State private var textClock = ExpandingFilterRowsTextClock()
    @State private var resetHeld = false
    @State private var continueHeld = false
    @State private var touchBegan = false
    @GestureState private var touchActive = false
    private typealias S = ExpandingFilterRowsStyle
    private typealias M = ExpandingFilterRowsMotion
    private typealias T = ExpandingFilterRowsText
    init(title: String = "Filters", dates: [String] = ExpandingFilterRowsStyle.dates, friends: [ExpandingFilterRowsFriend] = ExpandingFilterRowsStyle.friends, friendsCount: String = "12+", time: Double = 38, genres: [String] = ExpandingFilterRowsStyle.genres, chosenGenres: [String] = ExpandingFilterRowsStyle.chosen, prices: [String] = ExpandingFilterRowsStyle.prices, datesLabel: String = "Dates", friendsLabel: String = "Friends attend", anyFriendsLabel: String = "Anyone", timeLabel: String = "Time", singleTimeLabel: String = "Single time", timeRangeLabel: String = "Time range", genresLabel: String = "Genres", allGenresLabel: String = "All genres", noGenresLabel: String = "Tap a genre", priceLabel: String = "Price", resetLabel: String = "Reset all", continueLabel: String = "Continue", withLabel: String = "with", autoPlay: Bool = false, onChange: ((ExpandingFilterRowsFilters) -> Void)? = nil, onContinue: ((ExpandingFilterRowsFilters) -> Void)? = nil, onClose: (() -> Void)? = nil) {
        self.title = title; self.dates = dates; self.friends = friends; self.friendsCount = friendsCount; self.time = time; self.genres = genres; self.chosenGenres = chosenGenres; self.prices = prices; self.datesLabel = datesLabel; self.friendsLabel = friendsLabel; self.anyFriendsLabel = anyFriendsLabel; self.timeLabel = timeLabel; self.singleTimeLabel = singleTimeLabel; self.timeRangeLabel = timeRangeLabel; self.genresLabel = genresLabel; self.allGenresLabel = allGenresLabel; self.noGenresLabel = noGenresLabel; self.priceLabel = priceLabel; self.resetLabel = resetLabel; self.continueLabel = continueLabel; self.withLabel = withLabel; self.autoPlay = autoPlay; self.onChange = onChange; self.onContinue = onContinue; self.onClose = onClose
        _state = StateObject(wrappedValue: ExpandingFilterRowsState(firstTime: ExpandingFilterRowsMath.firstTime(time), chosen: chosenGenres))
    }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets, width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let top = insets.top + S.topPad, sheetHeight = height - top, listTop = S.grabberTop + S.grabberHeight + S.headerTop + S.header + S.listTop, room = sheetHeight - listTop, bottom = max(insets.bottom, S.bottomMin) + S.bottomPad
            TimelineView(.animation) { timeline in
                let _ = configure(date: .now)
                ZStack(alignment: .topLeading) {
                    S.backdrop
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: S.grabberRadius, style: .circular).fill(S.grabber).frame(width: S.grabberWidth, height: S.grabberHeight).offset(x: (width - S.grabberWidth) / 2, y: S.grabberTop)
                        header(width: width).offset(y: S.grabberTop + S.grabberHeight + S.headerTop)
                        rows(width: width, room: room, bottom: bottom, date: timeline.date).frame(width: width, height: room, alignment: .topLeading).clipped().contentShape(Rectangle()).offset(y: listTop)
                        bar(width: width, bottom: bottom, date: timeline.date).offset(y: sheetHeight - S.fade - S.button - bottom)
                    }.frame(width: width, height: sheetHeight, alignment: .topLeading).background(S.sheet)
                        .clipShape(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous)).offset(y: top)
                }.frame(width: width, height: height, alignment: .topLeading).contentShape(Rectangle())
                    .simultaneousGesture(DragGesture(minimumDistance: 0).updating($touchActive) { _, active, _ in active = true }
                        .onChanged { _ in if !touchBegan { touchBegan = true; state.touched() } }.onEnded { _ in touchBegan = false })
                    .onChange(of: touchActive) { _, active in if !active { touchBegan = false } }
                    .offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
            .onAppear { state.report() }.onDisappear { state.stop() }
        }.background(S.backdrop.ignoresSafeArea()).preferredColorScheme(.dark)
            .task(id: ExpandingFilterRowsLoopKey(enabled: autoPlay, touches: state.touches, firstTime: ExpandingFilterRowsMath.firstTime(time))) { await state.autoplay(enabled: autoPlay) }
            .task(id: ExpandingFilterRowsTickerKey(reduced: reduceMotion, count: friends.count)) { await state.ticker(count: friends.count, reduced: reduceMotion) }
    }
    private func configure(date: Date) {
        state.dates = dates; state.genres = genres; state.prices = prices; state.chosenGenres = chosenGenres; state.firstTime = ExpandingFilterRowsMath.firstTime(time); state.still = reduceMotion
        state.onChange = onChange; state.onContinue = onContinue; state.onClose = onClose
        let timeValue = state.ranged && state.endTime != state.startTime ? "\(ExpandingFilterRowsTimeLabel(state.startTime)) – \(ExpandingFilterRowsTimeLabel(state.endTime))" : ExpandingFilterRowsTimeLabel(state.startTime)
        let genreValue = state.chosen.isEmpty ? allGenresLabel : state.chosen.prefix(2).joined(separator: ", "), tail = state.chosen.count > 2 ? " +\(state.chosen.count - 2)" : ""
        textClock.update(0, value: .text(state.filters.date, "", false), still: reduceMotion, at: date)
        textClock.update(1, value: state.withFriends ? .friends(friends, friendsCount) : .text(anyFriendsLabel, "", true), still: reduceMotion, at: date)
        textClock.update(2, value: .text(timeValue, "", false), still: reduceMotion, at: date)
        textClock.update(3, value: .text(genreValue, tail, state.chosen.isEmpty), still: reduceMotion, at: date)
        textClock.update(4, value: .text(prices.indices.contains(state.priceIndex) ? prices[state.priceIndex] : "", "", state.priceIndex == 0), still: reduceMotion, at: date)
        textClock.updateCount(state.count, still: reduceMotion, at: date)
    }
    private func manual(_ action: () -> Void) { state.touched(); UIImpactFeedbackGenerator(style: .light).impactOccurred(); action() }
    private func pick(_ name: String, chosen: Bool) { state.touched(); UISelectionFeedbackGenerator().selectionChanged(); state.move(name, pick: chosen) }
    private func text(_ value: String, size: Double, line: Double, weight: UIFont.Weight = .regular, color: Color = S.ink, width: Double? = nil, oneLine: Bool = false, alignment: Alignment = .leading) -> some View {
        let font = UIFont.systemFont(ofSize: size * fontScale, weight: weight), line = line * fontScale
        let proposed = width ?? T.width(value, size: size * fontScale, weight: weight, pixel: displayScale)
        return Text(value).font(Font(font)).foregroundStyle(color).lineSpacing(line - font.lineHeight).lineLimit(oneLine ? 1 : nil).padding(.vertical, (line - font.lineHeight) / 2)
            .frame(width: proposed, alignment: alignment)
    }
    private func header(width: Double) -> some View {
        ZStack {
            text(title, size: S.titleText, line: S.titleLine, weight: .semibold, width: min(width, T.width(title, size: S.titleText * fontScale, weight: .semibold, pixel: displayScale)), oneLine: true).accessibilityAddTraits(.isHeader)
            ZStack {
                Circle().fill(S.closeFill)
                Image(systemName: "xmark").font(.system(size: S.closeIcon)).foregroundStyle(S.closeInk).accessibilityHidden(true)
                ExpandingFilterRowsHit(width: S.close + S.closeHit * 2, height: S.close + S.closeHit * 2, label: "Close") { state.touched(); state.close() }
            }.frame(width: S.close, height: S.close).offset(x: width / 2 - S.side - S.close / 2)
        }.frame(width: width, height: S.header)
    }
    private func rows(width: Double, room: Double, bottom: Double, date: Date) -> some View {
        let timeExtra = ExpandingFilterRowsTimePanel + S.wellInset, genreExtra = ExpandingFilterRowsGenrePanel + S.wellInset
        let timeEnd = 2 * (S.rowHeight + S.rowGap) + S.rowHeight + timeExtra * max(0, state.timeShown.value), genresEnd = timeEnd + S.rowGap + S.rowHeight + genreExtra * max(0, state.genresShown.value)
        let limit = room - bottom - S.button - S.fade - S.clear, shift = room > 0 ? -max(0, timeEnd - limit, genresEnd - limit) : 0
        return VStack(spacing: S.rowGap) {
            ExpandingFilterRowsRow(icon: "calendar", label: datesLabel, hint: "Shows the next date", pressed: false, extra: 0, shown: 0, expanded: false, activate: { manual(state.nextDate) }, value: { value(0, date: date) }, content: { EmptyView() })
            ExpandingFilterRowsRow(icon: "person.2", label: friendsLabel, hint: "Turns the filter on or off", pressed: false, extra: 0, shown: 0, expanded: false, activate: { manual(state.toggleFriends) }, value: { value(1, date: date) }, content: { EmptyView() })
            ExpandingFilterRowsRow(icon: "clock", label: timeLabel, hint: nil, pressed: state.pressed == .time, extra: timeExtra, shown: state.timeShown.value, expanded: state.expanded == .time, activate: { manual { state.toggle(.time) } }, value: { value(2, date: date) }, content: {
                ExpandingFilterRowsTimeDial(start: state.start, end: state.end, range: state.range.value, ranged: state.ranged, singleLabel: singleTimeLabel, rangeLabel: timeRangeLabel, onMode: { state.setMode($0) }, onStart: { state.onStart($0, byHand: $1) }, onEnd: { state.onEnd($0, byHand: $1) }, onTouch: state.touched, shown: state.timeShown.value)
            })
            ExpandingFilterRowsRow(icon: "music.note", label: genresLabel, hint: nil, pressed: state.pressed == .genres, extra: genreExtra, shown: state.genresShown.value, expanded: state.expanded == .genres, activate: { manual { state.toggle(.genres) } }, value: { value(3, date: date) }, content: {
                ExpandingFilterRowsGenreDrum(chosen: state.chosen, others: state.others, leftPosition: state.leftPosition, rightPosition: state.rightPosition, shown: state.genresShown.value, moving: state.moving, fresh: state.fresh, emptyLabel: noGenresLabel, onPick: { pick($0, chosen: true) }, onDrop: { pick($0, chosen: false) }, onTouch: state.touched)
            })
            ExpandingFilterRowsRow(icon: "dollarsign", label: priceLabel, hint: "Shows the next price", pressed: false, extra: 0, shown: 0, expanded: false, activate: { manual(state.nextPrice) }, value: { value(4, date: date) }, content: { EmptyView() })
        }.padding(.horizontal, S.side).frame(width: width, alignment: .top).offset(y: shift)
    }
    private func value(_ index: Int, date: Date) -> some View {
        let entry = textClock.entry(index)
        return ZStack(alignment: .trailing) {
            ForEach(entry.departing) { old in payload(old.value).opacity(1 - ExpandingFilterRowsTextClock.progress(old.start, date: date, duration: M.valueOut)).accessibilityHidden(true) }
            payload(entry.value).opacity(ExpandingFilterRowsTextClock.progress(entry.start, date: date, duration: M.valueIn))
        }
    }
    @ViewBuilder private func payload(_ value: ExpandingFilterRowsValue) -> some View {
        switch value {
        case let .text(string, tail, dim):
            let font = UIFont.systemFont(ofSize: S.valueText * fontScale, weight: dim ? .regular : .semibold), leading = S.valueLine * fontScale - font.lineHeight
            (Text(string).foregroundStyle(dim ? S.mute : S.ink).font(Font(font)) + Text(tail).foregroundStyle(S.mute).font(.system(size: S.valueText * fontScale, weight: .medium)))
                .monospacedDigit().lineLimit(1).multilineTextAlignment(.trailing).padding(.vertical, leading / 2).frame(maxWidth: .infinity, alignment: .trailing)
        case let .friends(list, count):
            friendsValue(list, count: count)
        }
    }
    private func friendsValue(_ list: [ExpandingFilterRowsFriend], count: String) -> some View {
        ZStack(alignment: .trailing) {
            ForEach(Array(list.enumerated()), id: \.offset) { index, friend in
                let raw = Double(index) - state.turn.value + Double(list.count) / 2, count = Double(list.count)
                let away = ((raw.truncatingRemainder(dividingBy: count) + count).truncatingRemainder(dividingBy: count)) - count / 2
                ZStack(alignment: .top) {
                    Circle().fill(T.color(friend.color))
                    if let initials = friend.initials, !initials.isEmpty {
                        text(initials, size: S.initialsText, line: S.initialsLine, weight: .bold, width: min(S.avatar, T.width(initials, size: S.initialsText * fontScale, weight: .bold, pixel: displayScale)), alignment: .center).frame(height: S.avatar)
                    } else {
                        Circle().fill(S.ink.opacity(S.portraitAlpha)).frame(width: S.head, height: S.head).offset(y: S.headTop)
                        Circle().fill(S.ink.opacity(S.portraitAlpha)).frame(width: S.shoulders, height: S.shoulders).offset(y: S.shouldersTop)
                    }
                }.frame(width: S.avatar, height: S.avatar).clipShape(Circle()).offset(x: -S.friendWidth + S.avatarsLeft + S.avatar, y: away * S.avatarPitch).accessibilityHidden(true)
            }
            text(count, size: S.valueText, line: S.valueLine, weight: .semibold, width: S.friendWidth, alignment: .trailing).multilineTextAlignment(.trailing).monospacedDigit()
        }.frame(width: S.friendWidth, height: S.rowHeight)
    }
    private func bar(width: Double, bottom: Double, date: Date) -> some View {
        let resetWidth = min(width - S.side * 2, T.width(resetLabel, size: S.labelText * fontScale, weight: .regular, pixel: displayScale))
        return VStack(spacing: 0) {
            LinearGradient(colors: [S.sheet.opacity(0), S.sheet], startPoint: .top, endPoint: .bottom).frame(height: S.fade).allowsHitTesting(false).accessibilityHidden(true)
            HStack(spacing: 0) {
                ZStack {
                    text(resetLabel, size: S.labelText, line: S.labelLine, color: resetHeld || state.pressed == .reset ? S.ink : S.mute, width: resetWidth).accessibilityHidden(true)
                    ExpandingFilterRowsHit(width: resetWidth + S.resetHit * 2, height: S.labelLine * fontScale + S.resetHit * 2, label: resetLabel, hold: { resetHeld = $0 }) { manual { state.reset() } }
                }.frame(width: resetWidth, height: S.labelLine * fontScale)
                Spacer(minLength: 0)
                ZStack {
                    RoundedRectangle(cornerRadius: S.button / 2, style: .circular).fill(S.blue).overlay {
                        HStack(spacing: 0) {
                            text(continueLabel, size: S.labelText, line: S.labelLine, weight: .semibold, width: min(S.buttonWidth, T.width(continueLabel, size: S.labelText * fontScale, weight: .semibold, pixel: displayScale)))
                            text(" \(withLabel) ", size: S.labelText, line: S.labelLine, weight: .medium, color: S.ink.opacity(S.buttonAlpha), width: min(S.buttonWidth, T.width(" \(withLabel) ", size: S.labelText * fontScale, weight: .medium, pixel: displayScale)))
                            count(date: date)
                        }.transaction { $0.animation = nil }
                    }.scaleEffect(continueHeld ? M.buttonScale : 1).animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.buttonPress), value: continueHeld).accessibilityHidden(true)
                    ExpandingFilterRowsHit(width: S.buttonWidth, height: S.button, label: "\(continueLabel) \(withLabel) \(state.count)", hold: { continueHeld = $0 }) { manual(state.continueAction) }
                }.frame(width: S.buttonWidth, height: S.button)
            }.padding(.horizontal, S.side).frame(width: width, height: S.button).padding(.bottom, bottom).background(S.sheet)
        }.frame(width: width)
    }
    private func count(date: Date) -> some View {
        let progress = ExpandingFilterRowsTextClock.progress(textClock.countStart, date: date, duration: M.countIn)
        return ZStack {
            ForEach(textClock.countDeparting) { old in
                let gone = ExpandingFilterRowsTextClock.progress(old.start, date: date, duration: M.countOut)
                text(String(old.count), size: S.labelText, line: S.labelLine, weight: .medium, color: S.ink.opacity(S.buttonAlpha), width: S.buttonCount, oneLine: true, alignment: .center).monospacedDigit().opacity(1 - gone).offset(y: -M.countTravel * gone)
            }
            text(String(state.count), size: S.labelText, line: S.labelLine, weight: .medium, color: S.ink.opacity(S.buttonAlpha), width: S.buttonCount, oneLine: true, alignment: .center).monospacedDigit().opacity(progress).offset(y: M.countTravel * (1 - progress))
        }.frame(width: S.buttonCount, height: S.labelLine)
    }
}
