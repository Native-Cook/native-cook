import SwiftUI

/// The approved 64 pt list row. A fresh row grows horizontally out of its avatar and can be swiped
/// left to reveal Undo; older rows remain completely static.
struct MoodOrbJarEntryRow: View {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Palette = MoodOrbJarTokens.Palette

    let controller: MoodOrbJarController
    let entry: MoodOrbJarEntry
    let width: Double

    @Environment(\.displayScale) private var displayScale

    private var mood: MoodOrbJarTokens.Mood { MoodOrbJarTokens.moods[entry.mood] }

    var body: some View {
        let swipe = entry.isFresh ? controller.rowX : 0
        let grow = entry.isFresh ? controller.grow : 1
        let content = entry.isFresh ? max(0, (grow - 0.45) / 0.55) : 1

        ZStack(alignment: .topLeading) {
            if entry.isFresh { undoLayer(opacity: min(1, -swipe / 60)) }
            card(grow: grow, content: content)
                .offset(x: swipe)
        }
        .frame(width: width, height: Layout.entryHeight, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(mood.name), \(entry.tags), \(entry.time)")
        .accessibilityActions {
            if entry.isFresh {
                Button(MoodOrbJarTokens.Copy.undo) { controller.undo() }
            }
        }
    }

    private func undoLayer(opacity: Double) -> some View {
        RoundedRectangle(cornerRadius: Layout.cardRadius)
            .fill(Palette.danger)
            .overlay(alignment: .trailing) {
                Text(MoodOrbJarTokens.Copy.undo)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.dangerInk)
                    .padding(.trailing, 20)
            }
            .frame(width: width, height: Layout.entryHeight)
            .opacity(opacity)
    }

    private func card(grow: Double, content: Double) -> some View {
        let rowPadding = MoodOrbJarTokens.Space.cardPadding
        let avatarBox = Layout.entryOrb * 2 + rowPadding * 2
        let eased = 1 - pow(1 - grow, 3)
        let surfaceScale = (avatarBox + (width - avatarBox) * eased) / width

        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Layout.cardRadius)
                .fill(surfaceColor(grow))
                .overlay {
                    RoundedRectangle(cornerRadius: Layout.cardRadius)
                        .stroke(Palette.cardEdge, lineWidth: 1 / displayScale)
                }
                .frame(width: width, height: Layout.entryHeight)
                .scaleEffect(x: surfaceScale, y: 1, anchor: .leading)
                .opacity(entry.isFresh ? min(1, grow * 4) : 1)

            Canvas { context, _ in
                var bead = context
                bead.translateBy(x: 20, y: 20)
                MoodOrbJarBeadPainter.draw(in: bead, radius: Layout.entryOrb, mood: entry.mood)
            }
            .frame(width: 40, height: 40)
            .offset(x: rowPadding - 2, y: (Layout.entryHeight - 40) / 2)
            .opacity(entry.isFresh && grow <= 0 ? 0 : 1)

            VStack(alignment: .leading, spacing: 0) {
                Text(mood.name)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Palette.ink)
                    .frame(height: 20, alignment: .topLeading)
                Text("\(entry.tags)  ·  \(entry.time)")
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.secondary)
                    .lineLimit(1)
                    .frame(height: 18, alignment: .topLeading)
                    .padding(.top, 2)
            }
            .frame(width: width - 60 - 44, alignment: .leading)
            .offset(x: 60, y: 11)
            .opacity(content)

            HStack(spacing: 2.5) {
                ForEach(0..<3, id: \.self) { _ in
                    Circle()
                        .fill(Palette.dotsInk)
                        .frame(width: 3, height: 3)
                }
            }
            .frame(width: 28, height: 28)
            .background(Circle().fill(Palette.dotsBackground))
            .offset(x: width - 40, y: 18)
            .opacity(content)
        }
        .frame(width: width, height: Layout.entryHeight, alignment: .topLeading)
    }

    private func surfaceColor(_ grow: Double) -> Color {
        guard entry.isFresh else { return Palette.card }
        let t = grow < 0.6 ? 0 : (grow - 0.6) / 0.4
        func channel(_ shift: UInt32) -> Double {
            let from = Double((mood.light >> shift) & 0xff)
            let to = Double((Palette.cardHex >> shift) & 0xff)
            return (from + (to - from) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
}
