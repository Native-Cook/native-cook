import SwiftUI

/// Persistent glyph rows reproduce Reanimated's independent entering and exiting layout tracks.
struct PullLeverRefreshCaption: View {
    let text: String
    let reduceMotion: Bool

    @State private var rows: [Row]
    @State private var currentText: String
    @State private var revision: Int
    @State private var animating: Bool
    @State private var nextRowID: Int

    init(text: String, reduceMotion: Bool) {
        self.text = text
        self.reduceMotion = reduceMotion
        let now = Date().timeIntervalSinceReferenceDate
        let initial = text.isEmpty ? [] : [Row(id: 0, text: text, enteredAt: now, reduced: reduceMotion)]
        _rows = State(initialValue: initial)
        _currentText = State(initialValue: text)
        _revision = State(initialValue: 0)
        _animating = State(initialValue: !text.isEmpty)
        _nextRowID = State(initialValue: text.isEmpty ? 0 : 1)
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: !animating)) { timeline in
            let now = animating ? timeline.date.timeIntervalSinceReferenceDate : motionEnd
            ZStack(alignment: .trailing) {
                ForEach(rows) { row in
                    glyphRow(row, at: now)
                }
            }
        }
        .frame(height: PullLeverRefreshTheme.Typography.caption.lineHeight, alignment: .trailing)
        .accessibilityHidden(true)
        .onChange(of: text) { _, value in transition(to: value) }
        .task(id: revision) {
            guard animating else { return }
            let wait = max(0, motionEnd - Date().timeIntervalSinceReferenceDate)
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard !Task.isCancelled else { return }
            rows.removeAll { $0.exitedAt != nil }
            animating = false
        }
    }

    private func glyphRow(_ row: Row, at now: Double) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(row.text.enumerated()), id: \.offset) { index, character in
                let state = glyphState(row, index: index, at: now)
                Text(character == " " ? " " : String(character))
                    .font(.system(
                        size: PullLeverRefreshTheme.Typography.caption.fontSize,
                        weight: .medium
                    ))
                    .kerning(PullLeverRefreshTheme.Typography.caption.letterSpacing)
                    .foregroundStyle(PullLeverRefreshTheme.ColorPalette.mute)
                    .frame(height: PullLeverRefreshTheme.Typography.caption.lineHeight)
                    .opacity(state.opacity)
                    .offset(y: state.y)
                    .id("\(row.text)-\(index)")
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }

    private func transition(to next: String) {
        guard next != currentText else { return }
        let now = Date().timeIntervalSinceReferenceDate
        for index in rows.indices where rows[index].exitedAt == nil {
            let bases = rows[index].text.indices.enumerated().map { glyphIndex, _ in
                entryState(rows[index], index: glyphIndex, at: now)
            }
            rows[index].exitOpacity = bases.map(\.opacity)
            rows[index].exitY = bases.map(\.y)
            rows[index].exitedAt = now
        }
        if !next.isEmpty {
            rows.append(Row(id: nextRowID, text: next, enteredAt: now, reduced: reduceMotion))
            nextRowID += 1
        }
        currentText = next
        revision += 1
        animating = !rows.isEmpty
    }

    private var motionEnd: Double {
        rows.reduce(0) { end, row in
            if let exitedAt = row.exitedAt {
                let lastDelay = row.reduced ? 0 : Double(max(0, row.text.count - 1)) * exitStagger
                let duration = row.reduced ? reducedDuration : exitDuration
                return max(end, exitedAt + lastDelay + duration)
            }
            let lastDelay = row.reduced ? 0 : Double(max(0, row.text.count - 1)) * enterStagger
            let duration = row.reduced ? reducedDuration : Self.springDuration
            return max(end, row.enteredAt + lastDelay + duration)
        }
    }

    private func glyphState(_ row: Row, index: Int, at now: Double) -> GlyphState {
        guard let exitedAt = row.exitedAt else { return entryState(row, index: index, at: now) }
        let delay = row.reduced ? 0 : Double(index) * exitStagger
        let duration = row.reduced ? reducedDuration : exitDuration
        let progress = Self.quadInOut((now - exitedAt - delay) / duration)
        let opacity = row.exitOpacity[index]
        let y = row.exitY[index]
        return GlyphState(
            opacity: max(0, min(1, opacity * (1 - progress))),
            y: row.reduced ? 0 : y + (-PullLeverRefreshMotion.Caption.travel - y) * progress
        )
    }

    private func entryState(_ row: Row, index: Int, at now: Double) -> GlyphState {
        let delay = row.reduced ? 0 : Double(index) * enterStagger
        let elapsed = now - row.enteredAt - delay
        if row.reduced {
            let progress = Self.quadInOut(elapsed / reducedDuration)
            return GlyphState(opacity: progress, y: 0)
        }
        let sample = Self.spring(elapsed)
        return GlyphState(
            opacity: max(0, min(1, 1 - sample.position)),
            y: PullLeverRefreshMotion.Caption.travel * sample.position
        )
    }

    private var enterStagger: Double { PullLeverRefreshMotion.Timing.charStagger / 1_000 }
    private var exitStagger: Double { PullLeverRefreshMotion.Timing.charStagger / 2 / 1_000 }
    private var exitDuration: Double { PullLeverRefreshMotion.Timing.charOut / 1_000 }
    private var reducedDuration: Double { PullLeverRefreshMotion.Timing.reduced / 1_000 }

    private static let springDuration: Double = {
        var time = 0.0
        while spring(time).energyRatio > PullLeverRefreshMotion.Clock.springEnergyThreshold {
            time += PullLeverRefreshMotion.Clock.nominalDelta
        }
        return time
    }()

    private static func spring(_ elapsed: Double) -> (position: Double, energyRatio: Double) {
        guard elapsed > 0 else { return (1, 1) }
        let mass = PullLeverRefreshMotion.Caption.springMass
        let stiffness = PullLeverRefreshMotion.Caption.springStiffness
        let damping = PullLeverRefreshMotion.Caption.springDamping
        let omega0 = sqrt(stiffness / mass)
        let zeta = damping / (2 * sqrt(stiffness * mass))
        let omega1 = omega0 * sqrt(1 - zeta * zeta)
        let envelope = exp(-zeta * omega0 * elapsed)
        let sine = sin(omega1 * elapsed)
        let cosine = cos(omega1 * elapsed)
        let position = envelope * (sine * zeta * omega0 / omega1 + cosine)
        let velocity = -zeta * omega0 * position
            + envelope * (cosine * zeta * omega0 - omega1 * sine)
        let initialEnergy = 0.5 * stiffness
        let energy = 0.5 * stiffness * position * position + 0.5 * mass * velocity * velocity
        let ratio = energy / initialEnergy
        return ratio <= PullLeverRefreshMotion.Clock.springEnergyThreshold ? (0, 0) : (position, ratio)
    }

    private static func quadInOut(_ raw: Double) -> Double {
        let value = min(1, max(0, raw))
        return value < 0.5 ? 2 * value * value : 1 - pow(-2 * value + 2, 2) / 2
    }

    private struct GlyphState {
        let opacity: Double
        let y: Double
    }

    private struct Row: Identifiable {
        let id: Int
        let text: String
        let enteredAt: Double
        let reduced: Bool
        var exitedAt: Double?
        var exitOpacity: [Double] = []
        var exitY: [Double] = []
    }
}
