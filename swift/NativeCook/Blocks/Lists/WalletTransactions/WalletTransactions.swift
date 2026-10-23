import QuartzCore
import SwiftUI
import UIKit

/// A pixel-measured personal-finance home ported from the approved React Native block.
struct WalletTransactions: View {
    var autoPlay = false

    static let loop: Double = 9
    static let LOOP_MS = 9_000

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = WalletTransactionsMotion()
    @State private var awake = false
    @State private var resumeToken = 0
    @State private var selectionHaptic = 0
    @State private var lightHaptic = 0

    private var scriptedAutoPlay: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let oy = insets.top > 0 ? insets.top - 62 : -40

            TimelineView(.animation(paused: !scriptedAutoPlay && !awake)) { tick in
                WalletTransactionsScreen(
                    width: width,
                    height: height,
                    offsetY: oy,
                    values: motion.values(at: tick.date, autoPlay: scriptedAutoPlay, reduced: reduceMotion),
                    selectedFilter: motion.selectedFilter,
                    reduced: reduceMotion,
                    onInterrupt: { interrupt(at: tick.date) },
                    onPick: { index in pick(index, at: tick.date) },
                    onHeroBegan: { point in beginHero(at: point, date: tick.date) },
                    onHeroChanged: { point in changeHero(at: point) },
                    onHeroEnded: { endHero(at: tick.date) }
                )
                .frame(width: width, height: height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(WalletTransactionsPalette.band.ignoresSafeArea())
        .preferredColorScheme(.light)
        .sensoryFeedback(.selection, trigger: selectionHaptic)
        .sensoryFeedback(.impact(weight: .light), trigger: lightHaptic)
        .onAppear {
            motion.reset(autoPlay: scriptedAutoPlay, at: .now)
            awake = scriptedAutoPlay
        }
        .onChange(of: autoPlay) { _, enabled in
            motion.reset(autoPlay: enabled && !reduceMotion, at: .now)
            awake = enabled && !reduceMotion
            resumeToken += 1
        }
        .onChange(of: reduceMotion) { _, _ in
            motion.reset(autoPlay: scriptedAutoPlay, at: .now)
            awake = scriptedAutoPlay
            resumeToken += 1
        }
        .task(id: resumeToken) {
            guard resumeToken > 0, !motion.heroActive else { return }
            if scriptedAutoPlay {
                try? await Task.sleep(for: .milliseconds(Int64(WalletTransactionsMotion.resumeAfterMS)))
                guard !Task.isCancelled, scriptedAutoPlay, !motion.heroActive else { return }
                let settleDate = Date.now
                motion.settleHome(at: settleDate, reduced: reduceMotion)
                awake = true
                try? await Task.sleep(for: .milliseconds(500))
                guard !Task.isCancelled, scriptedAutoPlay else { return }
                motion.restartAutoPlay(at: .now)
                awake = true
            } else {
                try? await Task.sleep(for: .milliseconds(700))
                guard !Task.isCancelled else { return }
                awake = motion.heroActive
            }
        }
    }

    private func interrupt(at date: Date) {
        motion.interrupt(at: date, autoPlay: scriptedAutoPlay, reduced: reduceMotion)
        awake = true; resumeToken += 1
    }

    private func pick(_ index: Int, at date: Date) {
        interrupt(at: date)
        motion.pick(index, at: date, reduced: reduceMotion)
        selectionHaptic += 1
    }

    private func beginHero(at point: CGPoint, date: Date) {
        interrupt(at: date)
        motion.beginHero(at: point, date: date, reduced: reduceMotion)
        lightHaptic += 1
    }

    private func changeHero(at point: CGPoint) {
        motion.changeHero(at: point)
    }

    private func endHero(at date: Date) {
        motion.endHero(at: date, reduced: reduceMotion)
        awake = true
        resumeToken += 1
    }
}

// MARK: - Motion

private struct WalletTransactionsValues {
    var chip = 0.0
    var fades = [0.0, 0.0, 0.0, 0.0]
    var lift = 0.0
    var press = 0.0
    var tiltX = 0.35
    var tiltY = -0.2
    var fans = [0.0, 0.0, 0.0, 0.0]
}

private struct WalletTransactionsTransition {
    enum Curve {
        case linear
        case easeOut
        case spring(omega: Double, zeta: Double)
    }

    var from = 0.0
    var to = 0.0
    var start = Date.distantPast
    var duration = 0.0
    var delay = 0.0
    var curve = Curve.linear

    func value(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(start) - delay
        guard elapsed > 0 else { return from }
        let progress: Double
        switch curve {
        case .linear:
            progress = min(max(elapsed / max(duration, 0.0001), 0), 1)
        case .easeOut:
            let x = min(max(elapsed / max(duration, 0.0001), 0), 1)
            progress = 1 - pow(1 - x, 3)
        case let .spring(omega, zeta):
            progress = WalletTransactionsMotion.springStep(elapsed, omega: omega, zeta: zeta)
        }
        return from + (to - from) * progress
    }
}

private final class WalletTransactionsMotion {
    static let resumeAfterMS = 3_500
    static let rowFade = 0.180
    static let rowStagger = 0.030

    var selectedFilter = 0
    var heroActive = false

    private var loopStart = Date.now
    private var manual = false
    private var snapshot = WalletTransactionsValues()
    private var chip = WalletTransactionsTransition()
    private var fades = Array(repeating: WalletTransactionsTransition(), count: 4)
    private var lift = WalletTransactionsTransition()
    private var press = WalletTransactionsTransition()
    private var fans = Array(repeating: WalletTransactionsTransition(), count: 4)
    private var tiltX = 0.35
    private var tiltY = -0.2

    func reset(autoPlay: Bool, at date: Date) {
        loopStart = date
        manual = !autoPlay
        selectedFilter = 0
        heroActive = false
        snapshot = WalletTransactionsValues()
        setTransitions(to: snapshot, at: date)
    }

    func restartAutoPlay(at date: Date) {
        loopStart = date
        manual = false
        selectedFilter = 0
        heroActive = false
    }

    func interrupt(at date: Date, autoPlay: Bool, reduced: Bool) {
        guard !manual else { return }
        snapshot = values(at: date, autoPlay: autoPlay, reduced: reduced)
        manual = true
        selectedFilter = Int(snapshot.fades[0].rounded()).clamped(to: 0 ... 2)
        setTransitions(to: snapshot, at: date)
        tiltX = snapshot.tiltX
        tiltY = snapshot.tiltY
    }

    func pick(_ index: Int, at date: Date, reduced: Bool) {
        let current = values(at: date, autoPlay: false, reduced: reduced)
        selectedFilter = index
        chip = WalletTransactionsTransition(
            from: current.chip,
            to: Double(index),
            start: date,
            duration: reduced ? Self.rowFade : 0,
            curve: reduced ? .linear : .spring(omega: 18.5, zeta: 0.86)
        )
        for row in 0 ..< 4 {
            fades[row] = WalletTransactionsTransition(
                from: current.fades[row],
                to: Double(index),
                start: date,
                duration: Self.rowFade,
                delay: reduced ? 0 : Double(row) * Self.rowStagger,
                curve: .linear
            )
        }
    }

    func beginHero(at point: CGPoint, date: Date, reduced: Bool) {
        let current = values(at: date, autoPlay: false, reduced: reduced)
        heroActive = true
        tiltX = min(max(Double(point.x / 127) * 2 - 1, -1), 1)
        tiltY = min(max(Double(point.y / 133) * 2 - 1, -1), 1)
        press = WalletTransactionsTransition(from: current.press, to: 1, start: date, duration: 0.120, curve: .easeOut)
        lift = WalletTransactionsTransition(
            from: current.lift,
            to: 1,
            start: date,
            duration: 0.370,
            delay: 0.120,
            curve: .spring(omega: 17, zeta: 0.72)
        )
        for index in 0 ..< 4 {
            fans[index] = WalletTransactionsTransition(
                from: current.fans[index],
                to: 1,
                start: date,
                duration: 0.320,
                delay: 0.250 + Double(index) * 0.045,
                curve: .spring(omega: 20, zeta: 0.68)
            )
        }
    }

    func changeHero(at point: CGPoint) {
        let nx = Double(point.x / 127) * 2 - 1
        let ny = Double(point.y / 133) * 2 - 1
        tiltX = nx / (1 + abs(nx) * 0.5) * 1.5
        tiltY = ny / (1 + abs(ny) * 0.5) * 1.5
    }

    func endHero(at date: Date, reduced: Bool) {
        let current = values(at: date, autoPlay: false, reduced: reduced)
        heroActive = false
        press = WalletTransactionsTransition(from: current.press, to: 0, start: date, duration: 0.160, curve: .easeOut)
        lift = WalletTransactionsTransition(
            from: current.lift,
            to: 0,
            start: date,
            duration: 0.400,
            curve: .spring(omega: 15.7, zeta: 1)
        )
        for index in 0 ..< 4 {
            fans[index] = WalletTransactionsTransition(
                from: current.fans[index],
                to: 0,
                start: date,
                duration: 0.350,
                delay: Double(3 - index) * 0.025,
                curve: .spring(omega: 18, zeta: 1)
            )
        }
    }

    func settleHome(at date: Date, reduced: Bool) {
        pick(0, at: date, reduced: reduced)
        endHero(at: date, reduced: reduced)
    }

    func values(at date: Date, autoPlay: Bool, reduced: Bool) -> WalletTransactionsValues {
        if autoPlay, !manual {
            let elapsed = date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: WalletTransactions.loop)
            let time = max(0, elapsed)
            let result = Self.script(at: time, reduced: reduced)
            selectedFilter = Int(result.fades[0].rounded()).clamped(to: 0 ... 2)
            return result
        }

        var result = snapshot
        result.chip = chip.value(at: date)
        for index in 0 ..< 4 {
            result.fades[index] = fades[index].value(at: date)
            result.fans[index] = reduced ? 0 : fans[index].value(at: date)
        }
        result.lift = lift.value(at: date)
        result.press = press.value(at: date)
        result.tiltX = tiltX
        result.tiltY = tiltY
        if reduced {
            result.press = 0
        }
        return result
    }

    private func setTransitions(to values: WalletTransactionsValues, at date: Date) {
        chip = WalletTransactionsTransition(from: values.chip, to: values.chip, start: date)
        lift = WalletTransactionsTransition(from: values.lift, to: values.lift, start: date)
        press = WalletTransactionsTransition(from: values.press, to: values.press, start: date)
        for index in 0 ..< 4 {
            fades[index] = WalletTransactionsTransition(from: values.fades[index], to: values.fades[index], start: date)
            fans[index] = WalletTransactionsTransition(from: values.fans[index], to: values.fans[index], start: date)
        }
    }

    private static func script(at time: Double, reduced: Bool) -> WalletTransactionsValues {
        var result = WalletTransactionsValues()
        result.chip = chipPosition(at: time)
        for index in 0 ..< 4 {
            result.fades[index] = chipFade(at: time, row: index, reduced: reduced)
            result.fans[index] = reduced ? 0 : fan(at: time, index: index)
        }
        result.lift = lift(at: time)
        result.press = reduced ? 0 : press(at: time)
        return result
    }

    private static func lift(at time: Double) -> Double {
        springStep(time - 1.320, omega: 17, zeta: 0.72) - springStep(time - 3.000, omega: 15.7, zeta: 1)
    }

    private static func press(at time: Double) -> Double {
        easeOut((time - 1.200) / 0.120) * (1 - easeOut((time - 1.320) / 0.160))
    }

    private static func fan(at time: Double, index: Int) -> Double {
        springStep(time - 1.450 - Double(index) * 0.045, omega: 20, zeta: 0.68)
            - springStep(time - 2.900 - Double(3 - index) * 0.025, omega: 18, zeta: 1)
    }

    private static func chipPosition(at time: Double) -> Double {
        springStep(time - 3.900, omega: 18.5, zeta: 0.86)
            + springStep(time - 5.500, omega: 18.5, zeta: 0.86)
            - 2 * springStep(time - 7.100, omega: 18.5, zeta: 0.86)
    }

    private static func chipFade(at time: Double, row: Int, reduced: Bool) -> Double {
        let stagger = reduced ? 0 : Double(row) * 0.030
        return clamp((time - 3.900 - stagger) / 0.180)
            + clamp((time - 5.500 - stagger) / 0.180)
            - 2 * clamp((time - 7.100 - stagger) / 0.180)
    }

    static func springStep(_ elapsed: Double, omega: Double, zeta: Double) -> Double {
        guard elapsed > 0 else { return 0 }
        if zeta >= 1 {
            return 1 - (1 + omega * elapsed) * exp(-omega * elapsed)
        }
        let damped = omega * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * omega * elapsed)
            * (cos(damped * elapsed) + (zeta * omega / damped) * sin(damped * elapsed))
    }

    private static func easeOut(_ value: Double) -> Double {
        let x = clamp(value)
        return 1 - pow(1 - x, 3)
    }

    private static func clamp(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

// MARK: - Layout

private struct WalletTransactionsScreen: View {
    let width: CGFloat
    let height: CGFloat
    let offsetY: CGFloat
    let values: WalletTransactionsValues
    let selectedFilter: Int
    let reduced: Bool
    let onInterrupt: () -> Void
    let onPick: (Int) -> Void
    let onHeroBegan: (CGPoint) -> Void
    let onHeroChanged: (CGPoint) -> Void
    let onHeroEnded: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            WalletTransactionsPalette.band
            content
                .offset(y: offsetY)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var content: some View {
        ZStack(alignment: .topLeading) {
            UnevenRoundedRectangle(
                cornerRadii: .init(bottomLeading: 30, bottomTrailing: 30),
                style: .continuous
            )
            .fill(.white)
            .frame(width: 402, height: 611.3)
            .offset(y: -200)

            header
            balance
            tiles
            actions

            UnevenRoundedRectangle(
                cornerRadii: .init(topLeading: 30, topTrailing: 30),
                style: .continuous
            )
            .fill(.white)
            .frame(width: 402, height: 1_200)
            .offset(y: 489.3)

            Capsule()
                .fill(WalletTransactionsPalette.grabber)
                .frame(width: 38, height: 6)
                .offset(x: 182, y: 499.8)

            WalletTransactionsPlacedText(
                "Transactions",
                x: 15.7,
                capTop: 520.7,
                size: 19,
                weight: .semibold,
                color: WalletTransactionsPalette.ink,
                tracking: -0.2,
                scaleX: 0.9
            )

            WalletTransactionsPressButton(label: "View all transactions", onPress: onInterrupt) {
                ZStack {
                    WalletTransactionsSunk(colors: WalletTransactionsPalette.chipFill, radius: 14.25)
                    HStack(spacing: 4.5) {
                        Text("View all")
                            .font(.system(size: 14.5))
                            .foregroundStyle(WalletTransactionsPalette.meta)
                            .scaleEffect(x: 0.93, anchor: .leading)
                        WalletTransactionsGlyph(kind: .chevron, color: WalletTransactionsPalette.meta)
                            .frame(width: 7, height: 12)
                    }
                    .padding(.leading, 8.6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                }
            }
            .frame(width: 77, height: 28.5)
            .offset(x: 300.7, y: 514.1)

            chips
            transactionRows

            LinearGradient(
                stops: [
                    .init(color: .white.opacity(0), location: 0),
                    .init(color: .white.opacity(0.55), location: 0.5),
                    .init(color: .white.opacity(0.75), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: 402, height: 89)
            .offset(y: 785)
            .allowsHitTesting(false)
        }
        .frame(width: 402, height: 874, alignment: .topLeading).simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in onInterrupt() })
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            WalletTransactionsGlyph(kind: .brand, color: WalletTransactionsPalette.ink)
                .frame(width: 27, height: 29.3)
                .offset(x: 16, y: 72.7)
            WalletTransactionsPlacedText(
                "VEN",
                x: 47,
                capTop: 79,
                size: 23,
                weight: .heavy,
                color: WalletTransactionsPalette.ink,
                tracking: 0.4,
                italic: true
            )
            headerButton(x: 284.9, label: "Insights", glyph: .chart)
            headerButton(x: 342.3, label: "Cards", glyph: .card)
        }
    }

    private func headerButton(x: CGFloat, label: String, glyph: WalletTransactionsGlyphKind) -> some View {
        WalletTransactionsPressButton(label: label, onPress: onInterrupt) {
            ZStack {
                WalletTransactionsSunk(colors: WalletTransactionsPalette.buttonFill, radius: 22, diagonal: true)
                WalletTransactionsGlyph(kind: glyph, color: WalletTransactionsPalette.icon)
                    .frame(width: glyph == .chart ? 22 : 23, height: glyph == .chart ? 22 : 19)
            }
        }
        .frame(width: 44, height: 44)
        .offset(x: x, y: 64.8)
    }

    private var balance: some View {
        ZStack(alignment: .topLeading) {
            WalletTransactionsPlacedText("Balance", x: 14.7, capTop: 148, size: 17, weight: .medium, color: WalletTransactionsPalette.label)
            WalletTransactionsPressButton(label: "Account, Personal", onPress: onInterrupt) {
                HStack(spacing: 6) {
                    Text("Personal")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(.white)
                    WalletTransactionsGlyph(kind: .chevron, color: .white, stroke: 2.1)
                        .frame(width: 7, height: 12)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(WalletTransactionsPalette.pill)
                .clipShape(RoundedRectangle(cornerRadius: 14.2, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14.2, style: .continuous).stroke(WalletTransactionsPalette.pillRim, lineWidth: 0.5))
            }
            .frame(width: 85.7, height: 29)
            .offset(x: 301, y: 138.7)
            WalletTransactionsPlacedText("$", x: 15, capTop: 198, size: 22, weight: .medium, color: WalletTransactionsPalette.grey)
            WalletTransactionsAmount(whole: "25 431", cents: ".30", x: 35.4, capTop: 192.7, size: 44)
        }
    }

    private var tiles: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.white)
                .frame(width: 127, height: 133)
                .shadow(color: .black.opacity(0.55), radius: 9, x: 0, y: 6)
                .opacity(0.2 * min(max(values.lift, 0), 1))
                .scaleEffect(
                    x: CGFloat(0.9 + 0.1 * min(max(values.lift, 0), 1)),
                    y: CGFloat(0.9 + 0.1 * min(max(values.lift, 0), 1))
                )
                .offset(x: 15, y: CGFloat(264 + (reduced ? 2 : 2 + 10 * values.lift)))

            investTile
                .scaleEffect(CGFloat(1 - 0.03 * values.press + 0.04 * (reduced ? 0 : values.lift)))
                .rotationEffect(.degrees(values.tiltX * 1.6 * (reduced ? 0 : values.lift)))
                .offset(
                    x: CGFloat(15 + values.tiltX * 3 * (reduced ? 0 : values.lift)),
                    y: CGFloat(264 + (-5 + values.tiltY * 1.5) * (reduced ? 0 : values.lift))
                )

            WalletTransactionsHeroTouch(
                onBegan: onHeroBegan,
                onChanged: onHeroChanged,
                onEnded: onHeroEnded
            )
            .frame(width: 127, height: 133)
            .offset(x: 15, y: 264)
            .accessibilityLabel("Invest, 70 850 dollars")
            .accessibilityAddTraits(.isButton)

            standardTile(index: 1, width: 143.5, label: "Shared, 1 200.50 dollars") {
                tileIcon(kind: .people, color: WalletTransactionsPalette.shared)
                WalletTransactionsPlacedText("Shared", x: 49, capTop: 21.3, size: 15, color: WalletTransactionsPalette.label)
                WalletTransactionsAmount(whole: "1 200", cents: ".50", x: 25.7, capTop: 96.7, size: 26, dollarX: 13.7, dollarCap: 94.8, dollarSize: 12)
            }

            standardTile(index: 2, width: 73, label: "Loans") {
                WalletTransactionsPlacedText("Loans", x: 14.3, capTop: 16.7, size: 15, color: WalletTransactionsPalette.label)
                WalletTransactionsGlyph(kind: .arrow, color: WalletTransactionsPalette.ink)
                    .frame(width: 18, height: 15)
                    .offset(x: 16, y: 98.3)
            }
        }
    }

    private var investTile: some View {
        ZStack(alignment: .topLeading) {
            WalletTransactionsSunk(colors: WalletTransactionsPalette.tileFill, radius: 22)
            tileIcon(kind: .command, color: WalletTransactionsPalette.invest)
            WalletTransactionsPlacedText("Invest", x: 49, capTop: 21.3, size: 15, color: WalletTransactionsPalette.label)
            ForEach(0 ..< 4, id: \.self) { index in
                let slot = Double(index)
                let fan: Double = values.fans[index]
                let fanX: Double = 13.2 + slot * 21.9 + slot * (27 - 21.9) * fan
                let fanY: Double = 67 - 23.7 / 2 - 3 * fan
                WalletTransactionsHoldingMark(kind: index)
                    .frame(width: 23.7, height: 23.7)
                    .offset(x: CGFloat(fanX), y: CGFloat(fanY))
            }
            WalletTransactionsAmount(whole: "70 850", cents: "", x: 25.7, capTop: 96.7, size: 26, dollarX: 13.7, dollarCap: 94.8, dollarSize: 12)
        }
        .frame(width: 127, height: 133, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func standardTile<Content: View>(index: Int, width: CGFloat, label: String, @ViewBuilder content: () -> Content) -> some View {
        WalletTransactionsPressButton(label: label, onPress: onInterrupt) {
            ZStack(alignment: .topLeading) {
                WalletTransactionsSunk(colors: WalletTransactionsPalette.tileFill, radius: 22)
                content()
            }
        }
        .frame(width: width, height: 133)
        .offset(x: index == 1 ? 152 : 305, y: 264)
    }

    private func tileIcon(kind: WalletTransactionsGlyphKind, color: Color) -> some View {
        ZStack {
            WalletTransactionsSunk(colors: WalletTransactionsPalette.iconSquareFill, radius: 7)
            WalletTransactionsGlyph(kind: kind, color: color)
                .frame(width: kind == .people ? 17 : 16, height: 16)
        }
        .frame(width: 27.4, height: 27.4)
        .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        .offset(x: 13.3, y: 13.5)
    }

    private var actions: some View {
        let items: [(x: CGFloat, width: CGFloat, label: String, glyph: WalletTransactionsGlyphKind?)] = [
            (15.3, 105, "Send", .send),
            (129, 107, "Receive", .down),
            (247.7, 80, "Add", .plus),
            (340, 45, "More", nil),
        ]
        return ZStack(alignment: .topLeading) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                WalletTransactionsPressButton(label: item.label, onPress: onInterrupt) {
                    ZStack(alignment: .topLeading) {
                        Capsule().fill(
                            LinearGradient(
                                stops: [
                                    .init(color: Color(hex: 0x6A6968), location: 0),
                                    .init(color: WalletTransactionsPalette.band, location: 0.4),
                                    .init(color: WalletTransactionsPalette.band, location: 0.6),
                                    .init(color: Color(hex: 0x5A5958), location: 1),
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        Capsule().fill(WalletTransactionsPalette.band).padding(1)
                        if let glyph = item.glyph {
                            ZStack {
                                Circle().fill(
                                    LinearGradient(
                                        stops: [
                                            .init(color: Color(hex: 0x4C4B4A), location: 0),
                                            .init(color: Color(hex: 0x262626), location: 0.4),
                                            .init(color: Color(hex: 0x2B2B2A), location: 0.6),
                                            .init(color: Color(hex: 0x444342), location: 1),
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                Circle().fill(LinearGradient(colors: [Color(hex: 0x2A2A29), Color(hex: 0x383837)], startPoint: .topLeading, endPoint: .bottomTrailing)).padding(1)
                                WalletTransactionsGlyph(kind: glyph, color: .white).frame(width: 18, height: 18)
                            }
                            .frame(width: 30, height: 30)
                            .offset(x: 7, y: 7)
                            WalletTransactionsPlacedText(item.label, x: 45, capTop: 15.4, size: 15, weight: .medium, color: Color(hex: 0xF2F2F2))
                        } else {
                            WalletTransactionsGlyph(kind: .more, color: WalletTransactionsPalette.dots)
                                .frame(width: 20, height: 4)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                }
                .frame(width: item.width, height: 44)
                .offset(x: item.x, y: 427.8)
            }
        }
    }

    private var chips: some View {
        let frames: [(x: CGFloat, width: CGFloat)] = [(15.7, 43.6), (67.3, 91.3), (167.3, 75.7)]
        let labels = ["All", "Recurring", "Friends"]
        let position = min(max(values.chip, 0), 2)
        let lower = min(Int(floor(position)), 1)
        let fraction = CGFloat(position - Double(lower))
        let highlightX = frames[lower].x + (frames[lower + 1].x - frames[lower].x) * fraction
        let highlightWidth = frames[lower].width + (frames[lower + 1].width - frames[lower].width) * fraction

        return ZStack(alignment: .topLeading) {
            ForEach(0 ..< 3, id: \.self) { index in
                WalletTransactionsSunk(colors: WalletTransactionsPalette.chipFill, radius: 18)
                    .frame(width: frames[index].width, height: 36)
                    .offset(x: frames[index].x, y: 551)
            }
            WalletTransactionsSunk(colors: WalletTransactionsPalette.chipActiveFill, radius: 18)
                .frame(width: highlightWidth, height: 36)
                .offset(x: highlightX, y: 551)
            ForEach(0 ..< 3, id: \.self) { index in
                WalletTransactionsPressButton(label: labels[index], selected: selectedFilter == index, onPress: { onPick(index) }) {
                    ZStack {
                        Text(labels[index])
                            .font(.system(size: 14.5))
                            .foregroundStyle(WalletTransactionsPalette.chipText)
                        Text(labels[index])
                            .font(.system(size: 14.5))
                            .foregroundStyle(WalletTransactionsPalette.chipActiveText)
                            .opacity(max(0, 1 - abs(values.fades[0] - Double(index))))
                    }
                }
                .frame(width: frames[index].width, height: 36)
                .offset(x: frames[index].x, y: 551)
            }
        }
    }

    private var transactionRows: some View {
        let lists = [WalletTransactionsData.all, WalletTransactionsData.recurring, WalletTransactionsData.friends]
        return ZStack(alignment: .topLeading) {
            ForEach(0 ..< 3, id: \.self) { listIndex in
                ForEach(Array(lists[listIndex].enumerated()), id: \.element.id) { rowIndex, row in
                    WalletTransactionsRowView(row: row, index: rowIndex)
                        .opacity(max(0, 1 - abs(values.fades[rowIndex] - Double(listIndex)) * (reduced ? 1 : 2)))
                        .accessibilityHidden(selectedFilter != listIndex)
                }
            }
        }
    }
}

private struct WalletTransactionsPlacedText: View {
    let text: String
    let x: CGFloat
    let capTop: CGFloat
    let size: CGFloat
    let weight: Font.Weight
    let color: Color
    let tracking: CGFloat
    let scaleX: CGFloat
    let italic: Bool

    init(
        _ text: String,
        x: CGFloat,
        capTop: CGFloat,
        size: CGFloat,
        weight: Font.Weight = .regular,
        color: Color,
        tracking: CGFloat = 0,
        scaleX: CGFloat = 1,
        italic: Bool = false
    ) {
        self.text = text
        self.x = x
        self.capTop = capTop
        self.size = size
        self.weight = weight
        self.color = color
        self.tracking = tracking
        self.scaleX = scaleX
        self.italic = italic
    }

    var body: some View {
        Text(text)
            .font(italic ? .system(size: size, weight: weight).italic() : .system(size: size, weight: weight))
            .tracking(tracking)
            .foregroundStyle(color)
            .fixedSize()
            .scaleEffect(x: scaleX, anchor: .leading)
            .offset(x: x, y: capTop - size * 0.247)
    }
}

private struct WalletTransactionsAmount: View {
    let whole: String
    let cents: String
    let x: CGFloat
    let capTop: CGFloat
    let size: CGFloat
    var dollarX: CGFloat? = nil
    var dollarCap: CGFloat? = nil
    var dollarSize: CGFloat? = nil

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let dollarX, let dollarCap, let dollarSize {
                WalletTransactionsPlacedText("$", x: dollarX, capTop: dollarCap, size: dollarSize, weight: .medium, color: WalletTransactionsPalette.grey)
            }
            Text(attributed)
                .font(.system(size: size, weight: .medium))
                .monospacedDigit()
                .tracking(-0.6)
                .fixedSize()
                .scaleEffect(x: 0.95, anchor: .leading)
                .offset(x: x, y: capTop - size * 0.247)
        }
    }

    private var attributed: AttributedString {
        var first = AttributedString(whole)
        first.foregroundColor = WalletTransactionsPalette.figure
        var second = AttributedString(cents)
        second.foregroundColor = WalletTransactionsPalette.grey
        first.append(second)
        return first
    }
}

private struct WalletTransactionsRowView: View {
    let row: WalletTransactionsRow
    let index: Int

    var body: some View {
        let top = CGFloat(604.6) + CGFloat(index) * 62.8
        ZStack(alignment: .topLeading) {
            WalletTransactionsRowMark(row: row)
                .frame(width: 49.7, height: 49.7)
                .offset(x: 16, y: top)
            WalletTransactionsPlacedText(row.name, x: 73.8, capTop: top + 7.7, size: 18, color: WalletTransactionsPalette.ink)
            HStack(spacing: 0) {
                Text(row.meta)
                if let date = row.date {
                    Text(" • ")
                    WalletTransactionsGlyph(kind: .repeatMark, color: WalletTransactionsPalette.meta)
                        .frame(width: 12, height: 12)
                        .padding(.trailing, 3)
                    Text(date)
                }
            }
            .font(.system(size: 13))
            .foregroundStyle(WalletTransactionsPalette.meta)
            .fixedSize()
            .offset(x: 73.8, y: top + 30 - 13 * 0.247)
            Text(row.amountAttributed)
                .font(.system(size: 18))
                .monospacedDigit()
                .fixedSize()
                .frame(width: 380.7, alignment: .trailing)
                .offset(y: top + 17.2 - 18 * 0.247)
        }
        .frame(width: 402, height: 874, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibilityLabel)
    }
}

private struct WalletTransactionsRowMark: View {
    let row: WalletTransactionsRow

    var body: some View {
        ZStack {
            if row.icon == .puff {
                WalletTransactionsGlyph(kind: .puff, color: .clear)
            } else {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(row.tint ?? .white)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(row.tint ?? WalletTransactionsPalette.rowIconEdge, lineWidth: 1))
                switch row.icon {
                case .dots:
                    WalletTransactionsGlyph(kind: .dots, color: .clear).frame(width: 47.7, height: 47.7)
                case .sphere:
                    WalletTransactionsGlyph(kind: .sphere, color: .clear).frame(width: 47.7, height: 47.7)
                case .word:
                    Text(row.word ?? "")
                        .font(.system(size: 16))
                        .tracking(-0.3)
                        .foregroundStyle(Color(hex: 0x6E6D6C))
                case .initials:
                    Text(row.word ?? "")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(WalletTransactionsPalette.figure)
                case .puff:
                    EmptyView()
                }
            }
        }
    }
}

// MARK: - Controls and touch

private struct WalletTransactionsPressButton<Content: View>: View {
    let label: String
    var selected: Bool?
    let onPress: () -> Void
    let content: Content

    init(label: String, selected: Bool? = nil, onPress: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.label = label
        self.selected = selected
        self.onPress = onPress
        self.content = content()
    }

    var body: some View {
        Button(action: onPress) { content }
            .buttonStyle(WalletTransactionsPressStyle())
            .accessibilityLabel(label)
            .accessibilityAddTraits(selected == true ? .isSelected : .isButton)
    }
}

private struct WalletTransactionsPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.220, bounce: 0.1), value: configuration.isPressed)
    }
}

private struct WalletTransactionsHeroTouch: UIViewRepresentable {
    let onBegan: (CGPoint) -> Void
    let onChanged: (CGPoint) -> Void
    let onEnded: () -> Void

    func makeUIView(context: Context) -> WalletTransactionsHeroTouchSurface {
        let view = WalletTransactionsHeroTouchSurface()
        update(view)
        return view
    }

    func updateUIView(_ uiView: WalletTransactionsHeroTouchSurface, context: Context) {
        update(uiView)
    }

    private func update(_ view: WalletTransactionsHeroTouchSurface) {
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

private final class WalletTransactionsHeroTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onBegan: ((CGPoint) -> Void)?
    var onChanged: ((CGPoint) -> Void)?
    var onEnded: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        backgroundColor = .clear
        isOpaque = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        switch recognizer.state {
        case .began:
            onBegan?(point)
        case .changed:
            onChanged?(point)
        case .ended, .cancelled, .failed:
            onEnded?()
        default:
            break
        }
    }
}

private struct WalletTransactionsSunk: View {
    let colors: [Color]
    let radius: CGFloat
    var diagonal = false

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(
                LinearGradient(
                    colors: colors,
                    startPoint: diagonal ? UnitPoint(x: 0.15, y: 0) : .top,
                    endPoint: diagonal ? UnitPoint(x: 0.85, y: 1) : .bottom
                )
            )
    }
}

// MARK: - Data and palette

private enum WalletTransactionsRowIcon {
    case puff, dots, sphere, word, initials
}

private struct WalletTransactionsRow: Identifiable {
    let id: String
    let name: String
    let meta: String
    let date: String?
    let amount: String
    let cents: String
    let icon: WalletTransactionsRowIcon
    let word: String?
    let tint: Color?

    var amountAttributed: AttributedString {
        var first = AttributedString(amount)
        first.foregroundColor = WalletTransactionsPalette.figure
        var second = AttributedString(cents)
        second.foregroundColor = WalletTransactionsPalette.grey
        first.append(second)
        return first
    }

    var accessibilityLabel: String {
        "\(name), \(meta)\(date.map { ", repeats, \($0)" } ?? ""), \(amount)\(cents)"
    }
}

private enum WalletTransactionsData {
    static let all = [
        WalletTransactionsRow(id: "aven-59", name: "Aven", meta: "Online", date: "Nov 12, ‘26", amount: "-$59", cents: ".99", icon: .puff, word: nil, tint: nil),
        WalletTransactionsRow(id: "aven-109", name: "Aven", meta: "Online", date: "Nov 14, ‘26", amount: "-$109", cents: ".99", icon: .dots, word: nil, tint: nil),
        WalletTransactionsRow(id: "sphere", name: "Studio Sphere", meta: "Salary", date: nil, amount: "$4,950", cents: ".00", icon: .sphere, word: nil, tint: nil),
        WalletTransactionsRow(id: "rivo", name: "Rivo Drive", meta: "Online", date: nil, amount: "-$24", cents: ".23", icon: .word, word: "Rivo", tint: nil),
    ]
    static let recurring = Array(all.prefix(2))
    static let friends = [
        WalletTransactionsRow(id: "nadia", name: "Nadia Rahman", meta: "Friends", date: nil, amount: "+$45", cents: ".00", icon: .initials, word: "NR", tint: Color(hex: 0xE8F1E4)),
        WalletTransactionsRow(id: "jonas", name: "Jonas Weber", meta: "Friends", date: nil, amount: "-$18", cents: ".50", icon: .initials, word: "JW", tint: Color(hex: 0xE9ECFB)),
        WalletTransactionsRow(id: "aiko", name: "Aiko Tanaka", meta: "Friends", date: nil, amount: "-$64", cents: ".00", icon: .initials, word: "AT", tint: Color(hex: 0xFCEDE4)),
    ]
}

private enum WalletTransactionsPalette {
    static let ink = Color(hex: 0x1F1F1E)
    static let figure = Color(hex: 0x272726)
    static let label = Color(hex: 0x737272)
    static let meta = Color(hex: 0x787777)
    static let grey = Color(hex: 0xA6A6A5)
    static let icon = Color(hex: 0x6F6E6D)
    static let pill = Color(hex: 0x8FA1FD)
    static let pillRim = Color(hex: 0x8394F2)
    static let band = Color(hex: 0x1F1F1F)
    static let dots = Color(hex: 0x7D7D7C)
    static let grabber = Color(hex: 0xF1F1F0)
    static let chipText = Color(hex: 0x737372)
    static let chipActiveText = Color(hex: 0xEF60D9)
    static let invest = Color(hex: 0xF25EEA)
    static let shared = Color(hex: 0xF8B000)
    static let rowIconEdge = Color(hex: 0xEFEFEF)
    static let tileEdge = Color(hex: 0xF2F2F1)
    static let rowGrey = [Color(hex: 0xB4B4B3), Color(hex: 0xBEBEBD)]
    static let systemCapHeightRatio = 0.705
    static let tileFill = [Color(hex: 0xEFF0EF), Color(hex: 0xF7F7F6), Color(hex: 0xFAFAF9)]
    static let buttonFill = [Color(hex: 0xEEEEEE), Color(hex: 0xFDFDFD)]
    static let iconSquareFill = [Color(hex: 0xD8D8D7), Color(hex: 0xE9E9E8)]
    static let chipFill = [Color(hex: 0xEFEFEF), Color(hex: 0xF8F8F7), Color(hex: 0xFAFAF9)]
    static let chipActiveFill = [Color(hex: 0xEEE6EC), Color(hex: 0xF6E8F3), Color(hex: 0xF6ECF4)]
}

private extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

// MARK: - Vector marks

private enum WalletTransactionsGlyphKind: Equatable {
    case brand, chart, card, chevron, arrow, send, down, plus, more, repeatMark, command, people, puff, dots, sphere
}

private struct WalletTransactionsGlyph: View {
    let kind: WalletTransactionsGlyphKind
    let color: Color
    var stroke: CGFloat = 2

    var body: some View {
        Canvas { context, size in
            let sx = size.width / baseSize.width
            let sy = size.height / baseSize.height
            context.scaleBy(x: sx, y: sy)
            draw(in: &context)
        }
    }

    private var baseSize: CGSize {
        switch kind {
        case .brand: CGSize(width: 27, height: 29.3)
        case .chart: CGSize(width: 22, height: 22)
        case .card: CGSize(width: 23, height: 19)
        case .chevron: CGSize(width: 7, height: 12)
        case .arrow: CGSize(width: 18, height: 15)
        case .send, .down, .plus: CGSize(width: 18, height: 18)
        case .more: CGSize(width: 20, height: 4)
        case .repeatMark: CGSize(width: 12, height: 12)
        case .command: CGSize(width: 16, height: 16)
        case .people: CGSize(width: 17, height: 16)
        case .puff, .dots, .sphere: CGSize(width: 49.7, height: 49.7)
        }
    }

    private func draw(in context: inout GraphicsContext) {
        switch kind {
        case .brand: drawBrand(in: &context)
        case .chart: drawChart(in: &context)
        case .card: drawCard(in: &context)
        case .chevron: stroke(points: [CGPoint(x: 1.2, y: 1.2), CGPoint(x: 5.6, y: 6), CGPoint(x: 1.2, y: 10.8)], width: stroke, in: &context)
        case .arrow: stroke(points: [CGPoint(x: 1, y: 7.5), CGPoint(x: 16.8, y: 7.5), CGPoint(x: 10.6, y: 1.2), CGPoint(x: 16.9, y: 7.5), CGPoint(x: 10.6, y: 13.8)], width: 2, in: &context)
        case .send: drawSend(in: &context)
        case .down: stroke(points: [CGPoint(x: 9, y: 1.2), CGPoint(x: 9, y: 16.4), CGPoint(x: 2.6, y: 10.2), CGPoint(x: 9, y: 16.6), CGPoint(x: 15.4, y: 10.2)], width: 1.9, in: &context)
        case .plus: stroke(points: [CGPoint(x: 9, y: 1.2), CGPoint(x: 9, y: 16.8), CGPoint(x: 1.2, y: 9), CGPoint(x: 16.8, y: 9)], width: 1.9, in: &context)
        case .more:
            for x in [2.0, 10.0, 18.0] { context.fill(Path(ellipseIn: CGRect(x: x - 1.75, y: 0.25, width: 3.5, height: 3.5)), with: .color(color)) }
        case .repeatMark: drawRepeat(in: &context)
        case .command: drawCommand(in: &context)
        case .people: drawPeople(in: &context)
        case .puff: drawPuff(in: &context)
        case .dots: drawDots(in: &context)
        case .sphere: drawSphere(in: &context)
        }
    }

    private func stroke(points: [CGPoint], width: CGFloat, in context: inout GraphicsContext) {
        var path = Path()
        for (index, point) in points.enumerated() {
            if index == 0 || (kind == .plus && index == 2) || (kind == .down && index == 2) || (kind == .arrow && index == 2) {
                path.move(to: point)
            } else {
                path.addLine(to: point)
            }
        }
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }

    private func drawBrand(in context: inout GraphicsContext) {
        let petals: [(CGFloat, CGFloat, CGFloat)] = [(9.6, 4.6, -62), (4.8, 12.4, 8), (5.7, 21.4, 52), (12.6, 25, 92), (20.6, 20, 146)]
        for (x, y, angle) in petals {
            var path = Path(ellipseIn: CGRect(x: x - 4.6, y: y - 2, width: 9.2, height: 4))
            let transform = CGAffineTransform(translationX: x, y: y)
                .rotated(by: angle * .pi / 180)
                .translatedBy(x: -x, y: -y)
            path = path.applying(transform)
            context.fill(path, with: .color(color))
        }
        context.fill(Path(ellipseIn: CGRect(x: 15.8, y: 6.8, width: 9.2, height: 9.2)), with: .color(color))
    }

    private func drawChart(in context: inout GraphicsContext) {
        let box = Path(roundedRect: CGRect(x: 1, y: 1, width: 20, height: 20), cornerRadius: 6)
        context.stroke(box, with: .color(color), lineWidth: 1.9)
        for (x, top) in [(7.0, 11.3), (11, 6.6), (15, 9.6)] {
            var path = Path(); path.move(to: CGPoint(x: x, y: top)); path.addLine(to: CGPoint(x: x, y: 16))
            context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
    }

    private func drawCard(in context: inout GraphicsContext) {
        context.stroke(Path(roundedRect: CGRect(x: 1, y: 1, width: 21, height: 17), cornerRadius: 4.6), with: .color(color), lineWidth: 1.9)
        stroke(points: [CGPoint(x: 1.5, y: 6.6), CGPoint(x: 21.5, y: 6.6)], width: 1.9, in: &context)
        stroke(points: [CGPoint(x: 12.4, y: 12.6), CGPoint(x: 14.4, y: 12.6)], width: 2.6, in: &context)
        stroke(points: [CGPoint(x: 16.9, y: 12.6), CGPoint(x: 18.4, y: 12.6)], width: 2.6, in: &context)
    }

    private func drawSend(in context: inout GraphicsContext) {
        var path = Path(); path.move(to: CGPoint(x: 1.6, y: 9)); path.addLine(to: CGPoint(x: 16.4, y: 1.6)); path.addLine(to: CGPoint(x: 12, y: 9.4)); path.addLine(to: CGPoint(x: 15.2, y: 16.6)); path.closeSubpath()
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.9, lineJoin: .round))
    }

    private func drawRepeat(in context: inout GraphicsContext) {
        var arc = Path(); arc.addArc(center: CGPoint(x: 6.5, y: 6), radius: 4.6, startAngle: .degrees(-85), endAngle: .degrees(140), clockwise: true)
        context.stroke(arc, with: .color(color), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        stroke(points: [CGPoint(x: 1.2, y: 7.6), CGPoint(x: 2.6, y: 9.6), CGPoint(x: 4.8, y: 8.6)], width: 1.3, in: &context)
        for point in [CGPoint(x: 8.8, y: 1.9), CGPoint(x: 10.4, y: 3.6), CGPoint(x: 11, y: 5.9), CGPoint(x: 10.4, y: 8.2), CGPoint(x: 8.9, y: 9.9)] {
            context.fill(Path(ellipseIn: CGRect(x: point.x - 0.65, y: point.y - 0.65, width: 1.3, height: 1.3)), with: .color(color))
        }
    }

    private func drawCommand(in context: inout GraphicsContext) {
        var path = Path()
        path.move(to: CGPoint(x: 5.6, y: 5.6)); path.addLine(to: CGPoint(x: 10.4, y: 5.6)); path.addLine(to: CGPoint(x: 10.4, y: 10.4)); path.addLine(to: CGPoint(x: 5.6, y: 10.4)); path.closeSubpath()
        path.move(to: CGPoint(x: 5.6, y: 5.6)); path.addLine(to: CGPoint(x: 5.6, y: 4.2)); path.addCurve(to: CGPoint(x: 3, y: 1.6), control1: CGPoint(x: 5.6, y: 2.76), control2: CGPoint(x: 4.44, y: 1.6)); path.addCurve(to: CGPoint(x: 0.4, y: 4.2), control1: CGPoint(x: 1.56, y: 1.6), control2: CGPoint(x: 0.4, y: 2.76)); path.addCurve(to: CGPoint(x: 3, y: 6.8), control1: CGPoint(x: 0.4, y: 5.64), control2: CGPoint(x: 1.56, y: 6.8)); path.addLine(to: CGPoint(x: 5.6, y: 6.8))
        path.move(to: CGPoint(x: 10.4, y: 5.6)); path.addLine(to: CGPoint(x: 10.4, y: 4.2)); path.addCurve(to: CGPoint(x: 13, y: 1.6), control1: CGPoint(x: 10.4, y: 2.76), control2: CGPoint(x: 11.56, y: 1.6)); path.addCurve(to: CGPoint(x: 15.6, y: 4.2), control1: CGPoint(x: 14.44, y: 1.6), control2: CGPoint(x: 15.6, y: 2.76)); path.addCurve(to: CGPoint(x: 13, y: 6.8), control1: CGPoint(x: 15.6, y: 5.64), control2: CGPoint(x: 14.44, y: 6.8)); path.addLine(to: CGPoint(x: 10.4, y: 6.8))
        path.move(to: CGPoint(x: 10.4, y: 10.4)); path.addLine(to: CGPoint(x: 10.4, y: 11.8)); path.addCurve(to: CGPoint(x: 13, y: 14.4), control1: CGPoint(x: 10.4, y: 13.24), control2: CGPoint(x: 11.56, y: 14.4)); path.addCurve(to: CGPoint(x: 15.6, y: 11.8), control1: CGPoint(x: 14.44, y: 14.4), control2: CGPoint(x: 15.6, y: 13.24)); path.addCurve(to: CGPoint(x: 13, y: 9.2), control1: CGPoint(x: 15.6, y: 10.36), control2: CGPoint(x: 14.44, y: 9.2)); path.addLine(to: CGPoint(x: 10.4, y: 9.2))
        path.move(to: CGPoint(x: 5.6, y: 10.4)); path.addLine(to: CGPoint(x: 5.6, y: 11.8)); path.addCurve(to: CGPoint(x: 3, y: 14.4), control1: CGPoint(x: 5.6, y: 13.24), control2: CGPoint(x: 4.44, y: 14.4)); path.addCurve(to: CGPoint(x: 0.4, y: 11.8), control1: CGPoint(x: 1.56, y: 14.4), control2: CGPoint(x: 0.4, y: 13.24)); path.addCurve(to: CGPoint(x: 3, y: 9.2), control1: CGPoint(x: 0.4, y: 10.36), control2: CGPoint(x: 1.56, y: 9.2)); path.addLine(to: CGPoint(x: 5.6, y: 9.2))
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
    }

    private func drawPeople(in context: inout GraphicsContext) {
        context.fill(Path(ellipseIn: CGRect(x: 9.4, y: 1.2, width: 6, height: 6)), with: .color(color.opacity(0.32)))
        var back = Path(); back.move(to: CGPoint(x: 10.8, y: 8.6)); back.addCurve(to: CGPoint(x: 16.6, y: 13.2), control1: CGPoint(x: 14.4, y: 8.6), control2: CGPoint(x: 16.6, y: 10.6)); back.addCurve(to: CGPoint(x: 14.4, y: 15.4), control1: CGPoint(x: 16.6, y: 14.6), control2: CGPoint(x: 15.8, y: 15.4)); back.addLine(to: CGPoint(x: 11.6, y: 15.4)); back.closeSubpath(); context.fill(back, with: .color(color.opacity(0.32)))
        context.fill(Path(ellipseIn: CGRect(x: 2.8, y: 0.4, width: 7.2, height: 7.2)), with: .color(color))
        var front = Path(); front.move(to: CGPoint(x: 0.6, y: 13.4)); front.addCurve(to: CGPoint(x: 6.4, y: 8.6), control1: CGPoint(x: 0.6, y: 10.4), control2: CGPoint(x: 3.2, y: 8.6)); front.addCurve(to: CGPoint(x: 12.2, y: 13.4), control1: CGPoint(x: 9.6, y: 8.6), control2: CGPoint(x: 12.2, y: 10.4)); front.addCurve(to: CGPoint(x: 10, y: 15.6), control1: CGPoint(x: 12.2, y: 14.8), control2: CGPoint(x: 11.4, y: 15.6)); front.addLine(to: CGPoint(x: 2.8, y: 15.6)); front.addCurve(to: CGPoint(x: 0.6, y: 13.4), control1: CGPoint(x: 1.4, y: 15.6), control2: CGPoint(x: 0.6, y: 14.8)); front.closeSubpath(); context.fill(front, with: .color(color))
    }

    private func drawPuff(in context: inout GraphicsContext) {
        context.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 49.7, height: 49.7), cornerRadius: 12), with: .linearGradient(Gradient(colors: [Color(hex: 0xB7B6B6), Color(hex: 0xC0BFBF)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 49.7)))
        let pillow = CGRect(x: 9.94, y: 9.44, width: 29.82, height: 30.81)
        context.fill(Path(roundedRect: pillow.offsetBy(dx: 0, dy: 1.6), cornerRadius: 11.9), with: .color(.black.opacity(0.08)))
        context.fill(Path(roundedRect: pillow, cornerRadius: 11.9), with: .radialGradient(Gradient(colors: [.white, Color(hex: 0xF6F6F6), Color(hex: 0xE2E2E2)]), center: CGPoint(x: 20.9, y: 17.9), startRadius: 0, endRadius: 34.8))
        context.fill(Path(roundedRect: CGRect(x: 22.37, y: 18.39, width: 4.97, height: 12.92), cornerRadius: 2.48), with: .color(Color(hex: 0xA9A9A8)))
    }

    private func drawDots(in context: inout GraphicsContext) {
        let c = 24.85
        context.fill(Path(roundedRect: CGRect(x: c - 8.4, y: c - 8.4, width: 7.6, height: 3.4), cornerRadius: 1.7), with: .color(Color(hex: 0x36C5F0)))
        context.fill(Path(ellipseIn: CGRect(x: c - 7.6, y: c + 0.4, width: 6.8, height: 6.8)), with: .color(Color(hex: 0xE01E5A)))
        context.fill(Path(roundedRect: CGRect(x: c + 1.4, y: c - 9, width: 3.4, height: 7.6), cornerRadius: 1.7), with: .color(Color(hex: 0x2EB67D)))
        context.fill(Path(ellipseIn: CGRect(x: c + 1.2, y: c + 1, width: 6.8, height: 6.8)), with: .color(Color(hex: 0xECB22E)))
    }

    private func drawSphere(in context: inout GraphicsContext) {
        let c = 24.85
        context.clip(to: Path(ellipseIn: CGRect(x: c - 12.6, y: c - 12.6, width: 25.2, height: 25.2)))
        for i in 0 ..< 5 {
            for j in 0 ..< 5 {
                let u = (Double(i) + 0.5) / 5 * 2 - 1
                let v = (Double(j) + 0.5) / 5 * 2 - 1
                let x = c + sin(u * 1.2) / sin(1.2) * 12.6 * 0.86
                let y = c + sin(v * 1.2) / sin(1.2) * 12.6 * 0.86
                let width = 4.6 * cos(u * 1.05)
                let height = 4.6 * cos(v * 1.05)
                context.fill(Path(roundedRect: CGRect(x: x - width / 2, y: y - height / 2, width: width, height: height), cornerRadius: min(width, height) * 0.4), with: .color(Color(hex: 0x2B2A2A)))
            }
        }
    }
}

private struct WalletTransactionsHoldingMark: View {
    let kind: Int

    var body: some View {
        Canvas { context, size in
            let r = size.width / 2
            let body = r - 1.3
            context.fill(Path(ellipseIn: CGRect(origin: .zero, size: size)), with: .color(Color(hex: 0xF7F7F6)))
            context.fill(Path(ellipseIn: CGRect(x: 1.3, y: 1.3, width: body * 2, height: body * 2)), with: .color(kind == 2 ? Color(hex: 0x168FBD) : kind == 3 ? Color(hex: 0x050505) : .white))
            switch kind {
            case 0:
                var leaf = Path(); leaf.move(to: CGPoint(x: r + 4.7, y: r - 6.7)); leaf.addCurve(to: CGPoint(x: r - 4.8, y: r + 4.8), control1: CGPoint(x: r - 3.8, y: r - 6.1), control2: CGPoint(x: r - 7.1, y: r - 1.1)); leaf.addCurve(to: CGPoint(x: r + 4.7, y: r - 6.7), control1: CGPoint(x: r - 1.4, y: r + 8.1), control2: CGPoint(x: r + 4.5, y: r + 4.8)); leaf.closeSubpath(); context.fill(leaf, with: .color(Color(hex: 0x050505)))
                var vein = Path(); vein.move(to: CGPoint(x: r - 4.3, y: r + 4.6)); vein.addQuadCurve(to: CGPoint(x: r + 4.4, y: r - 6.3), control: CGPoint(x: r - 0.5, y: r + 0.5)); context.stroke(vein, with: .color(.white.opacity(0.72)), style: StrokeStyle(lineWidth: 1.15, lineCap: .round))
            case 1:
                var wave = Path(); wave.move(to: CGPoint(x: r - 8, y: r + 2.9)); wave.addCurve(to: CGPoint(x: r - 1.2, y: r - 2.7), control1: CGPoint(x: r - 5.9, y: r - 0.7), control2: CGPoint(x: r - 3.7, y: r - 2.7)); wave.addCurve(to: CGPoint(x: r + 5, y: r + 2.6), control1: CGPoint(x: r + 1.6, y: r - 2.7), control2: CGPoint(x: r + 2.1, y: r + 2.6)); wave.addCurve(to: CGPoint(x: r + 8, y: r + 0.1), control1: CGPoint(x: r + 6.4, y: r + 2.6), control2: CGPoint(x: r + 7.3, y: r + 1.5)); context.stroke(wave, with: .color(Color(hex: 0x065FD6)), style: StrokeStyle(lineWidth: 2.45, lineCap: .round, lineJoin: .round))
            case 2:
                var mark = Path(); mark.move(to: CGPoint(x: r, y: r - 7)); mark.addCurve(to: CGPoint(x: r + 6.6, y: r - 0.5), control1: CGPoint(x: r + 4.6, y: r - 7), control2: CGPoint(x: r + 7.2, y: r - 4.2)); mark.addCurve(to: CGPoint(x: r, y: r), control1: CGPoint(x: r + 3.3, y: r - 2.2), control2: CGPoint(x: r + 0.8, y: r - 2.1)); mark.addCurve(to: CGPoint(x: r - 6.6, y: r - 0.5), control1: CGPoint(x: r - 0.8, y: r - 2.1), control2: CGPoint(x: r - 3.3, y: r - 2.2)); mark.addCurve(to: CGPoint(x: r, y: r - 7), control1: CGPoint(x: r - 7.2, y: r - 4.2), control2: CGPoint(x: r - 4.6, y: r - 7)); mark.closeSubpath(); mark.move(to: CGPoint(x: r, y: r)); mark.addCurve(to: CGPoint(x: r + 1.8, y: r + 6.7), control1: CGPoint(x: r + 2.5, y: r + 1.1), control2: CGPoint(x: r + 3.3, y: r + 3.4)); mark.addCurve(to: CGPoint(x: r + 6.6, y: r - 0.5), control1: CGPoint(x: r + 5.7, y: r + 6.1), control2: CGPoint(x: r + 7.4, y: r + 2.9)); mark.addCurve(to: CGPoint(x: r, y: r), control1: CGPoint(x: r + 3.2, y: r + 0.3), control2: CGPoint(x: r + 1.2, y: r - 0.1)); mark.closeSubpath(); mark.move(to: CGPoint(x: r, y: r)); mark.addCurve(to: CGPoint(x: r - 1.8, y: r + 6.7), control1: CGPoint(x: r - 2.5, y: r + 1.1), control2: CGPoint(x: r - 3.3, y: r + 3.4)); mark.addCurve(to: CGPoint(x: r - 6.6, y: r - 0.5), control1: CGPoint(x: r - 5.7, y: r + 6.1), control2: CGPoint(x: r - 7.4, y: r + 2.9)); mark.addCurve(to: CGPoint(x: r, y: r), control1: CGPoint(x: r - 3.2, y: r + 0.3), control2: CGPoint(x: r - 1.2, y: r - 0.1)); mark.closeSubpath(); context.fill(mark, with: .color(.white))
                context.fill(Path(ellipseIn: CGRect(x: r - 1.3, y: r - 1.3, width: 2.6, height: 2.6)), with: .color(Color(hex: 0x168FBD)))
            default:
                let bars: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [(-6.4, -5.7, -18, 8.6), (-2.7, -1.4, 18, 9.2), (-6.1, 3, -18, 8.2)]
                for (index, barData) in bars.enumerated() {
                    let (x, y, angle, width) = barData
                    var bar = Path(roundedRect: CGRect(x: r + x, y: r + y, width: width, height: 2.8), cornerRadius: 1.4); bar = bar.applying(CGAffineTransform(translationX: r, y: r).rotated(by: angle * .pi / 180).translatedBy(x: -r, y: -r)); context.fill(bar, with: .linearGradient(Gradient(colors: [Color(hex: 0x4FE3C1), Color(hex: 0x9B5CF0)]), startPoint: CGPoint(x: r - 6 + CGFloat(index) * 0.1, y: r), endPoint: CGPoint(x: r + 7, y: r)))
                }
            }
        }
    }
}
