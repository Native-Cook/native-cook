import SwiftUI
import UIKit

let ExpandingFilterRowsGenrePanel = 236.0
let ExpandingFilterRowsPickSeconds = 0.26

private enum ExpandingFilterRowsGenreStyle {
    static let blue = Color(hex: 0x2f6bff), ink = Color.white, empty = Color(hex: 0x5c5c62)
    static let radius = 280.0, step = 7.5, stepRadians = step * .pi / 180
    static let pitch = radius * sin(stepRadians), line = 30.0, lineWidth = 150.0, leftRim = -24.0, rightRim = 27.0, ring = 12.0
    static let fadeAt = [0.0, 1.2, 2.0, 3.0, 3.6], fade = [1.0, 1.0, 0.82, 0.32, 0.0], swing = 2.5
    static let flick = 0.14, drumStiffness = 190.0, drumDamping = 24.0, mass = 1.0, slotStiffness = 210.0, slotDamping = 20.0
    static let arriving = 0.32, ringTransition = 0.14, ringScale = 1.25, ringStroke = 1.5, ringGap = 9.0, pip = 3.5, pipRadius = 2.0, pipAlpha = 0.7
    static let text = 16.0, textLine = 22.0, emptyText = 14.0, emptyLine = 20.0
    static func brightness(_ distance: Double) -> Double {
        if distance >= fadeAt.last! { return fade.last! }
        for index in 0..<(fadeAt.count - 1) where distance <= fadeAt[index + 1] {
            let t = (distance - fadeAt[index]) / (fadeAt[index + 1] - fadeAt[index]); return fade[index] + (fade[index + 1] - fade[index]) * t
        }
        return 1
    }
}

private struct ExpandingFilterRowsGenreLine: View {
    let name: String
    let index: Int
    let left: Bool
    let position: Double
    let shown: Double
    let moving: Bool
    let activate: () -> Void
    @StateObject private var slot: ExpandingFilterRowsWheel
    @StateObject private var present: ExpandingFilterRowsWheel
    @ScaledMetric(relativeTo: .body) private var textSize = ExpandingFilterRowsGenreStyle.text
    private typealias S = ExpandingFilterRowsGenreStyle
    init(name: String, index: Int, left: Bool, position: Double, shown: Double, moving: Bool, fresh: Bool, activate: @escaping () -> Void) {
        self.name = name; self.index = index; self.left = left; self.position = position; self.shown = shown; self.moving = moving; self.activate = activate
        _slot = StateObject(wrappedValue: ExpandingFilterRowsWheel(Double(index)))
        _present = StateObject(wrappedValue: ExpandingFilterRowsWheel(fresh ? 0 : 1))
    }
    var body: some View {
        TimelineView(.animation) { timeline in
            line(at: timeline.date)
        }.frame(width: S.lineWidth, height: S.line)
            .onAppear { slot.spring(Double(index), stiffness: S.slotStiffness, damping: S.slotDamping); fade() }
            .onChange(of: index) { _, next in slot.spring(Double(next), stiffness: S.slotStiffness, damping: S.slotDamping) }
            .onChange(of: moving) { _, _ in fade() }
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton).accessibilityLabel(name)
            .accessibilityHint(left ? "Chosen. Activate to remove." : "Activate to choose.").accessibilityAction { activate() }
    }
    private func line(at date: Date) -> some View {
        let swing = left ? -S.swing : S.swing
        let away = slot.value - position + swing * (1 - min(1, shown))
        let angle = away * S.stepRadians, out = S.radius * (1 - cos(angle))
        return words.frame(width: S.lineWidth, height: S.line).opacity(S.brightness(abs(away)) * present.value)
            .rotationEffect(.radians(left ? angle : -angle), anchor: left ? .trailing : .leading)
            .offset(x: left ? -out : out, y: S.radius * sin(angle))
            .onChange(of: date) { _, next in slot.advance(next); present.advance(next) }
    }
    private var words: some View {
        let font = UIFont.systemFont(ofSize: textSize, weight: .medium)
        let leading = S.textLine / S.text * textSize - font.lineHeight
        return HStack(spacing: 0) {
            if left { Spacer(minLength: 0) }
            if !left { ring.padding(.trailing, S.ringGap) }
            Text(name).font(Font(font)).foregroundStyle(S.ink).lineLimit(1).padding(.vertical, leading / 2)
                .frame(maxWidth: S.lineWidth, alignment: left ? .trailing : .leading).fixedSize(horizontal: true, vertical: false)
            if !left { Spacer(minLength: 0) }
        }
    }
    private var ring: some View {
        Circle().fill(moving ? S.blue : S.blue.opacity(0)).overlay(Circle().strokeBorder(S.blue, lineWidth: S.ringStroke))
            .overlay { RoundedRectangle(cornerRadius: S.pipRadius, style: .circular).fill(moving ? Color.white : S.blue.opacity(S.pipAlpha)).frame(width: S.pip, height: S.pip).transaction { $0.animation = nil } }
            .frame(width: S.ring, height: S.ring).scaleEffect(moving ? S.ringScale : 1)
            .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: S.ringTransition), value: moving)
    }
    private func fade() { present.time(moving ? 0 : 1, duration: moving ? ExpandingFilterRowsPickSeconds : S.arriving, curve: .quadratic) }
}

private struct ExpandingFilterRowsGenreColumn: View {
    let left: Bool
    let names: [String]
    @ObservedObject var position: ExpandingFilterRowsWheel
    let shown: Double
    let moving: String?
    let fresh: String?
    let onTap: (String) -> Void
    let onTouch: () -> Void
    @State private var grabbed = 0.0
    private typealias S = ExpandingFilterRowsGenreStyle
    private var last: Int { max(0, names.count - 1) }
    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                ForEach(Array(names.enumerated()), id: \.element) { index, name in
                    ExpandingFilterRowsGenreLine(name: name, index: index, left: left, position: position.value, shown: shown, moving: name == moving, fresh: name == fresh) { onTap(name) }
                        .offset(x: left ? proxy.size.width + S.leftRim - S.lineWidth : S.rightRim, y: (ExpandingFilterRowsGenrePanel - S.line) / 2)
                }
                ExpandingFilterRowsPanSurface(begin: {
                    position.cancel(); grabbed = position.value; onTouch()
                }, update: { translation in
                    let to = grabbed - translation / S.pitch, over = to < 0 ? to : to > Double(last) ? to - Double(last) : 0
                    position.set(to - over + over / (1 + abs(over)) / 2)
                }, finalize: { velocity in
                    let carried = position.value - velocity * S.flick / S.pitch
                    position.spring(min(Double(last), max(0, ExpandingFilterRowsMath.rounded(carried))), stiffness: S.drumStiffness, damping: S.drumDamping, mass: S.mass)
                }, tap: { point in
                    let height = min(1, max(-1, (point.y - ExpandingFilterRowsGenrePanel / 2) / S.radius)), at = Int(ExpandingFilterRowsMath.rounded(position.value + asin(height) / S.stepRadians))
                    if names.indices.contains(at) { onTap(names[at]) }
                }).frame(width: proxy.size.width, height: ExpandingFilterRowsGenrePanel)
            }
        }.frame(height: ExpandingFilterRowsGenrePanel).onAppear(perform: pullBack).onChange(of: last) { _, _ in pullBack() }
    }
    private func pullBack() { if position.value > Double(last) { position.spring(Double(last), stiffness: S.drumStiffness, damping: S.drumDamping, mass: S.mass) } }
}

/// Each name turns about the point where its wheel's rim meets the middle row.
struct ExpandingFilterRowsGenreDrum: View {
    let chosen: [String]
    let others: [String]
    let leftPosition: ExpandingFilterRowsWheel
    let rightPosition: ExpandingFilterRowsWheel
    let shown: Double
    let moving: String?
    let fresh: String?
    let emptyLabel: String
    let onPick: (String) -> Void
    let onDrop: (String) -> Void
    let onTouch: () -> Void
    @ScaledMetric(relativeTo: .body) private var emptySize = ExpandingFilterRowsGenreStyle.emptyText
    private typealias S = ExpandingFilterRowsGenreStyle
    var body: some View {
        GeometryReader { proxy in
            let half = proxy.size.width / 2
            ZStack(alignment: .topLeading) {
                if chosen.isEmpty {
                    let font = UIFont.systemFont(ofSize: emptySize, weight: .regular), line = S.emptyLine / S.emptyText * emptySize
                    Text(emptyLabel).font(Font(font)).foregroundStyle(S.empty).multilineTextAlignment(.trailing).lineSpacing(line - font.lineHeight).padding(.vertical, (line - font.lineHeight) / 2)
                        .frame(width: half + S.leftRim, alignment: .trailing).offset(y: (ExpandingFilterRowsGenrePanel - S.emptyLine) / 2)
                }
                ExpandingFilterRowsGenreColumn(left: true, names: chosen, position: leftPosition, shown: shown, moving: moving, fresh: fresh, onTap: onDrop, onTouch: onTouch).frame(width: half)
                ExpandingFilterRowsGenreColumn(left: false, names: others, position: rightPosition, shown: shown, moving: moving, fresh: fresh, onTap: onPick, onTouch: onTouch).frame(width: half).offset(x: half)
            }.frame(width: proxy.size.width, height: ExpandingFilterRowsGenrePanel, alignment: .topLeading).clipped()
        }.frame(height: ExpandingFilterRowsGenrePanel)
    }
}
