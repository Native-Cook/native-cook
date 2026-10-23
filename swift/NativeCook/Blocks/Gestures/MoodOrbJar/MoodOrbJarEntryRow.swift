import SwiftUI

/// One 106 pt source card: the mood's orb, name, tags, time and a "..." button. The newest row
/// follows the finger's swipe (revealing Undo) and grows out of the flying orb's landing.
struct MoodOrbJarEntryRow: View {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Palette = MoodOrbJarTokens.Palette

    let controller: MoodOrbJarController
    let entry: MoodOrbJarEntry
    let width: Double

    @Environment(\.displayScale) private var displayScale

    private var mood: MoodOrbJarTokens.Mood {
        MoodOrbJarTokens.moods[entry.mood]
    }

    var body: some View {
        // Only the newest row reads the per-frame swipe and growth, so the others stay still.
        let swipe = entry.isFresh ? controller.rowX : 0
        let grow = entry.isFresh ? controller.grow : 1
        // Container morph: the card's surface grows out of the avatar, then the text fades in.
        let content = entry.isFresh ? max(0, (grow - 0.45) / 0.55) : 1
        ZStack(alignment: .topLeading) {
            if entry.isFresh {
                undoLayer(opacity: min(1, -swipe / 60))
            }
            card(grow: grow, content: content)
                .offset(x: swipe)
        }
        .frame(width: width, height: Layout.entryHeight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(mood.name), \(entry.tags), \(entry.time)")
        .accessibilityActions {
            if entry.isFresh {
                Button(MoodOrbJarTokens.Copy.undo) {
                    controller.undo()
                }
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
                    .padding(.trailing, 24)
            }
            .frame(width: width, height: Layout.entryHeight)
            .opacity(opacity)
    }

    private func card(grow: Double, content: Double) -> some View {
        let avatarBox = Layout.entryOrb * 2 + 32
        let eased = 1 - pow(1 - grow, 3)
        let surfaceScale = (avatarBox + (width - avatarBox) * eased) / width
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Layout.cardRadius)
                .fill(surfaceColor(grow))
                .frame(width: width, height: Layout.entryHeight)
                .scaleEffect(x: surfaceScale, y: 1, anchor: .leading)
                .opacity(entry.isFresh ? min(1, grow * 4) : 1)

            Image(uiImage: MoodOrbJarAssets.orbs[entry.mood])
                .resizable()
                .frame(width: Layout.entryOrb * 2, height: Layout.entryOrb * 2)
                .offset(x: MoodOrbJarTokens.Space.cardPadding, y: MoodOrbJarTokens.Space.avatarTop)
                .opacity(entry.isFresh && grow <= 0 ? 0 : 1)

            text
                .opacity(content)
                .offset(
                    x: MoodOrbJarTokens.Space.cardPadding + Layout.entryOrb * 2 + 16.3,
                    y: 27.4
                )

            dots
                .opacity(content)
                .offset(x: width - 11.3 - 27, y: 14.3)
        }
        .frame(width: width, height: Layout.entryHeight, alignment: .topLeading)
    }

    private var text: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(mood.name)
                .font(.system(size: 16, weight: .bold))
                .tracking(-0.2)
                .foregroundStyle(Palette.ink)
                .frame(height: 20, alignment: .topLeading)
            Text(entry.tags)
                .font(.system(size: 14))
                .foregroundStyle(Palette.secondary)
                .lineLimit(1)
                .frame(height: 18, alignment: .topLeading)
                .padding(.top, 4.1)
            Text(entry.time)
                .font(.system(size: 14))
                .foregroundStyle(Palette.secondary)
                .frame(height: 18, alignment: .topLeading)
                .padding(.top, 4.1)
        }
    }

    private var dots: some View {
        HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { _ in
                Circle()
                    .fill(Color(hex: 0x2A2A2E))
                    .frame(width: 3, height: 3)
            }
        }
        .frame(width: 27, height: 27)
        .background {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white)
                .stroke(Palette.sheetLine, lineWidth: 2 / displayScale)
        }
    }

    /// The grouped list is white, so the morphing surface is the mood's tint, which then settles to white.
    private func surfaceColor(_ grow: Double) -> Color {
        guard entry.isFresh else { return Palette.card }
        let t = grow < 0.6 ? 0 : (grow - 0.6) / 0.4
        func channel(_ shift: UInt32) -> Double {
            let from = Double((mood.light >> shift) & 0xff)
            let to = Double((MoodOrbJarTokens.Palette.cardHex >> shift) & 0xff)
            return (from + (to - from) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
}
