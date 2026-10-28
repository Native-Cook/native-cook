import SwiftUI
import UIKit

// Port of LeagueCrests.tsx: the crest carousel, the cross-sliding headline, the page tint and
// the focus model (demo peek, drag, snap, return after 3.2 s).

struct EmeraldTierLeague {
    enum State { case done, current, locked }
    let key: String
    let name: String
    let detail: String
    let state: State
    let chip: String
    let tint: [String]
}

enum EmeraldTierLeagues {
    /// Leagues in climbing order; Emerald is the one this person is playing.
    static let all: [EmeraldTierLeague] = [
        EmeraldTierLeague(key: "amber", name: "Amber League", detail: "Completed · you finished 2nd of 8", state: .done, chip: "Promoted Sep 28", tint: ["#E39A4E", "#9A5422", "#3A1E0C"]),
        EmeraldTierLeague(key: "copper", name: "Copper League", detail: "Completed · you finished 1st of 8", state: .done, chip: "Promoted Oct 12", tint: ["#EA7A3C", "#A4401A", "#3A170B"]),
        EmeraldTierLeague(key: "emerald", name: "Emerald League", detail: "Top 40% advance to the next league", state: .current, chip: "3d 23h 15m left", tint: ["#00C88E", "#008C67", "#064B37"]),
        EmeraldTierLeague(key: "sapphire", name: "Sapphire League", detail: "Finish in Emerald’s top 3 to unlock", state: .locked, chip: "Locked", tint: ["#4E86E8", "#22508F", "#0B1F3B"]),
        EmeraldTierLeague(key: "diamond", name: "Diamond League", detail: "Unlocks after Sapphire", state: .locked, chip: "Locked", tint: ["#A9C4D6", "#5C7787", "#18242C"]),
    ]
    static let CURRENT_LEAGUE = 2

    struct Side { let x: [Double]; let y: [Double]; let scale: [Double]; let rotate: [Double]; let ghost: [Double] }
    /// Slot poses of the original composition by distance from focus (left and right differ in the design).
    static let LEFT = Side(x: [0, 104.5, 180.5], y: [0, 4, 18], scale: [1, 0.643, 0.571], rotate: [0, 12, 17], ghost: [1, 0.29, 0.24])
    static let RIGHT = Side(x: [0, 95, 168], y: [0, 7, 18.5], scale: [1, 0.625, 0.545], rotate: [0, 12, 17], ghost: [1, 0.29, 0.24])
    static let SPACING = 95.0
    static let CENTER = 198.0
    static let SPRING_DURATION = 520.0
    static let SPRING_DAMPING = 0.86

    struct SlotPose { var x: Double, y: Double, scale: Double, rotate: Double, ghost: Double, opacity: Double }

    static func slotPose(_ distance: Double) -> SlotPose {
        let d = abs(distance)
        let sign: Double = distance < 0 ? -1 : 1
        let side = distance < 0 ? LEFT : RIGHT
        func at(_ values: [Double]) -> Double {
            if d >= 2 { return values[2] }
            let i = Int(d.rounded(.down))
            return values[i] + (values[i + 1] - values[i]) * (d - Double(i))
        }
        let beyond = max(0, d - 2)
        return SlotPose(x: sign * (at(side.x) + beyond * 70),
                        y: at(side.y) + beyond * 10,
                        scale: at(side.scale) - beyond * 0.08,
                        rotate: sign * at(side.rotate),
                        ghost: at(side.ghost),
                        opacity: 1 - min(1, max(0, d - 2.2) / 0.6))
    }

    /// Demo: after the recast, peek at the next (locked) league and come back.
    static func demoFocus(_ seconds: Double) -> Double {
        func ease(_ x: Double) -> Double { let t = max(0, min(1, x)); return t * t * (3 - 2 * t) }
        return Double(CURRENT_LEAGUE) + ease((seconds - 4.4) / 0.6) - ease((seconds - 6.2) / 0.6)
    }

    /// The standings belong to Emerald; they recede while another league is in focus.
    static func recede(_ focus: Double) -> (opacity: Double, scale: Double) {
        let away = min(1, abs(focus - Double(CURRENT_LEAGUE)))
        return (1 - away * 0.62, 1 - away * 0.02)
    }
}

/// `useLeagueFocus`
final class EmeraldTierLeagueFocus {
    typealias L = EmeraldTierLeagues
    let manualFocus = EmeraldTierAnimator(Double(L.CURRENT_LEAGUE))
    var manual = false
    var dragStart = Double(L.CURRENT_LEAGUE)
    /// `enabled`: previewTime == nil and the Leagues tab is showing.
    var enabled = true
    /// Reanimated's `ReduceMotion.System`.
    var systemReduced = false
    /// Mirrors of the rank model's demo clock, refreshed every tick.
    var demoTime = 0.0
    var demoRunning = true
    private var lastRounded: Int?

    let pan = EmeraldTierPan(axis: .horizontal, activeOffset: 10, failOffset: 14)

    var focus: Double { manual || !demoRunning ? manualFocus.value : L.demoFocus(demoTime) }

    func tick(now: Double) {
        manualFocus.step(now: now)
        let rounded = Int((focus + 0.5).rounded(.down))
        if let previous = lastRounded, rounded != previous, manual { UISelectionFeedbackGenerator().selectionChanged() }
        lastRounded = rounded
    }

    func settle(_ target: Double, velocity: Double = 0) {
        let index = Int(max(0, min(Double(L.all.count - 1), (target + 0.5).rounded(.down))))
        manualFocus.spring(to: Double(index), duration: L.SPRING_DURATION, dampingRatio: L.SPRING_DAMPING, velocity: velocity,
                           reduceMotion: systemReduced) { [weak self] finished in
            guard let self, finished, index != L.CURRENT_LEAGUE else { return }
            // A browsed league always hands the screen back to the one being played.
            self.manualFocus.spring(to: Double(L.CURRENT_LEAGUE), duration: L.SPRING_DURATION, dampingRatio: L.SPRING_DAMPING,
                                    delay: 3.2, reduceMotion: self.systemReduced) { [weak self] done in
                if done { self?.manual = false }
            }
        }
    }

    func select(_ index: Int) {
        manualFocus.cancel()
        manualFocus.set(focus); manual = true
        settle(Double(index))
    }

    // Pan: activeOffsetX +-10, failOffsetY +-14.
    func panBegan() {
        let current = focus
        manualFocus.cancel()
        manualFocus.set(current); dragStart = current; manual = true
    }
    func panUpdated(translationX: Double) {
        let raw = dragStart - translationX / L.SPACING
        let edge = max(0, min(Double(L.all.count - 1), raw))
        let over = raw - edge
        manualFocus.set(edge + over / (1 + abs(over) * 2.5))
    }
    func panEnded(velocityX: Double) {
        let velocity = -velocityX / L.SPACING
        settle(manualFocus.value + max(-1, min(1, velocity * 0.18)), velocity: velocity)
    }
}

// MARK: - Views

private struct EmeraldTierCrestSlot<Content: View>: View {
    let index: Int
    let focus: Double
    let content: Content

    var body: some View {
        let state = EmeraldTierLeagues.all[index].state
        let d = Double(index) - focus
        let pose = EmeraldTierLeagues.slotPose(d)
        let near = max(0, 1 - abs(d))
        // A locked crest is a ghost at the side and a frosted, readable object when focused.
        let material = state == .locked ? pose.ghost * (1 - near) + 0.78 * near : 1
        // Focusing a locked league nudges its lock once, like trying a closed door.
        let shake = state == .locked ? sin(max(0, near - 0.75) * 4 * .pi * 3) * near * 3 : 0
        content
            .frame(width: 100, height: 112)
            .scaleEffect(pose.scale)
            .rotationEffect(.degrees(pose.rotate + shake))
            .offset(x: EmeraldTierLeagues.CENTER - 50 + pose.x, y: pose.y)
            .opacity(pose.opacity * material)
            .zIndex(((10 - abs(d) * 2) + 0.5).rounded(.down))
            .allowsHitTesting(false)
    }
}

/// The crest row: 393 x 118 at y = 57, drag to browse. The touch handling lives in the parent
/// (`EmeraldTierLeaderboard`) so one gesture owner feeds the pan recogniser.
struct EmeraldTierCrestRow<Content: View>: View {
    let focus: Double
    let renderCrest: (EmeraldTierLeague, Int) -> Content

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(EmeraldTierLeagues.all.enumerated()), id: \.offset) { index, league in
                EmeraldTierCrestSlot(index: index, focus: focus, content: renderCrest(league, index))
            }
        }
        .frame(width: 393, height: 118, alignment: .topLeading)
    }
}

private struct EmeraldTierHeadlineLayer: View {
    let index: Int
    let focus: Double
    let solid: Bool

    var body: some View {
        let league = EmeraldTierLeagues.all[index]
        let d = Double(index) - focus
        let near = max(0, 1 - abs(d) * 1.8)
        let icon: String = league.state == .current ? "clock" : league.state == .done ? "checkmark.circle.fill" : "lock.fill"
        let tone = league.state == .locked ? "#B9C9DA" : league.state == .done ? "#FFD9A8" : "#72F598"
        EmeraldTierHeadlineBody(title: league.name, subtitle: league.detail, chip: league.chip, icon: icon,
                                iconSize: league.state == .current ? 18 : 15, tone: tone, solid: solid)
            .opacity(near)
            .offset(x: d * -22)
            .accessibilityHidden(index != EmeraldTierLeagues.CURRENT_LEAGUE)
            .allowsHitTesting(false)
    }
}

struct EmeraldTierHeadlineBody: View {
    let title: String
    let subtitle: String
    let chip: String
    let icon: String
    let iconSize: Double
    let tone: String
    let solid: Bool

    var body: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(.system(size: 23, weight: .bold))
                .tracking(-0.65)
                .foregroundStyle(.white)
                .frame(width: 393, height: 28)
            Text(subtitle)
                .font(.system(size: 13))
                .foregroundStyle(EmeraldTierPalette.color("#a6c7b9"))
                .frame(width: 393, height: 18)
                .padding(.top, 4)
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: iconSize)).foregroundStyle(EmeraldTierPalette.color(tone))
                Text(chip)
                    .font(.system(size: 12, weight: .bold).monospacedDigit())
                    .foregroundStyle(EmeraldTierPalette.color(tone))
            }
            .padding(.horizontal, 16)
            .frame(minWidth: 161, minHeight: 32, maxHeight: 32)
            .background { EmeraldTierGlass(kind: .green, radius: 20, solid: solid) }
            .padding(.top, 10)
        }
        .frame(width: 393, height: 92, alignment: .top)
        .frame(width: 393, height: 92)
    }
}

/// Title, subtitle and countdown of the focused league, cross-sliding with the crests.
struct EmeraldTierLeagueHeadline: View {
    let focus: Double
    let solid: Bool
    let challenges: Bool

    var body: some View {
        ZStack(alignment: .top) {
            if challenges {
                EmeraldTierHeadlineBody(title: "Emerald Challenges", subtitle: "Earn XP to climb the league", chip: "3d 23h 15m left",
                                        icon: "clock", iconSize: 18, tone: "#72F598", solid: solid)
                    .allowsHitTesting(false)
            } else {
                ForEach(Array(EmeraldTierLeagues.all.enumerated()), id: \.offset) { index, _ in
                    EmeraldTierHeadlineLayer(index: index, focus: focus, solid: solid)
                }
            }
        }
        .frame(width: 393, height: 92, alignment: .top)
        .offset(y: 168)
        .frame(width: 393, height: 260, alignment: .topLeading)
        .allowsHitTesting(false)
    }
}

/// The page's light takes the colour of the league in focus. Emerald is the base gradient itself.
struct EmeraldTierLeagueTint: View {
    let focus: Double

    var body: some View {
        ZStack {
            ForEach(Array(EmeraldTierLeagues.all.enumerated()), id: \.offset) { index, league in
                if index != EmeraldTierLeagues.CURRENT_LEAGUE {
                    LinearGradient(stops: [
                        .init(color: EmeraldTierPalette.color(league.tint[0]), location: 0),
                        .init(color: EmeraldTierPalette.color(league.tint[1]), location: 0.23),
                        .init(color: EmeraldTierPalette.color(league.tint[2]), location: 0.40),
                        .init(color: EmeraldTierPalette.color("#071e14"), location: 0.58),
                        .init(color: EmeraldTierPalette.color("#080a09"), location: 0.82),
                    ], startPoint: .top, endPoint: .bottom)
                    .opacity(max(0, 1 - abs(Double(index) - focus)))
                }
            }
        }
        .allowsHitTesting(false)
    }
}
