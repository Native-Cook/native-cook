import SwiftUI

enum ForYouInsightsSection: Int, CaseIterable {
    case insights, longevity, browse
}

/// Static pieces of the feed (no per-frame input), so the timeline does not rebuild them.
struct ForYouInsightsHeading: View, Equatable {
    typealias T = ForYouInsightsTheme
    var k: Double, y: (Double) -> Double, title: String
    static func == (l: Self, r: Self) -> Bool { l.k == r.k && l.title == r.title && l.y(62) == r.y(62) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForYouInsightsText(text: title, style: T.Typo.heading, color: T.Colors.ink, k: k)
                .accessibilityAddTraits(.isHeader)
                .offset(x: T.Layout.column * k, y: y(T.Layout.headingTop))
            T.Colors.separator
                .frame(width: T.Layout.columnWidth * k, height: T.Layout.divider)
                .offset(x: T.Layout.column * k, y: y(T.Layout.headingDivider))
        }
    }
}

struct ForYouInsightsHabits: View, Equatable {
    typealias T = ForYouInsightsTheme
    var k: Double, y: (Double) -> Double
    static func == (l: Self, r: Self) -> Bool { l.k == r.k && l.y(62) == r.y(62) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(ForYouInsightsContent.habits.enumerated()), id: \.element.title) { i, habit in
                HStack(alignment: .top, spacing: T.Space.md * k) {
                    ZStack {
                        RoundedRectangle(cornerRadius: T.Radius.habitIcon * k, style: .circular).fill(T.Colors.white)
                        Image(systemName: habit.symbol)
                            .font(.system(size: 22 * k))
                            .foregroundStyle(Color(hex: habit.color))
                    }
                    .frame(width: T.Layout.habitIcon * k, height: T.Layout.habitIcon * k)
                    VStack(alignment: .leading, spacing: T.Space.xs * k) {
                        ForYouInsightsText(text: habit.title, style: T.Typo.habitTitle, color: T.Colors.ink, k: k)
                        ForYouInsightsText(text: habit.body, style: T.Typo.habitBody, color: T.Colors.secondary, k: k, lines: 3)
                            .frame(maxWidth: .infinity, alignment: .topLeading)
                    }
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                }
                .padding(.top, T.Space.md * k)
                .frame(width: T.Layout.columnWidth * k, height: T.Layout.habitPitch * k, alignment: .topLeading)
                .overlay(alignment: .bottom) { T.Colors.separator.frame(height: T.Layout.divider) }
                .offset(x: T.Layout.column * k, y: y(T.Layout.habitTop + T.Layout.habitPitch * Double(i)))
            }
        }
    }
}

struct ForYouInsightsRowButtonStyle: ButtonStyle {
    var onChange: (Bool) -> Void
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in onChange(pressed) }
    }
}

/// One article row: title, summary, read time, the flat illustration and its divider.
struct ForYouInsightsRow: View {
    typealias T = ForYouInsightsTheme
    typealias M = ForYouInsightsMotion
    var index: Int
    var top: Double
    var k: Double
    var y: (Double) -> Double
    var frame: ForYouInsightsFrame
    var onPress: (Bool) -> Void
    var onOpen: () -> Void

    var body: some View {
        let item = ForYouInsightsContent.articles[index]
        let mine = frame.actorIndex == index
        let titleP = mine ? M.smooth(M.seg(frame.g, M.Reader.rowTitleOut.0, M.Reader.rowTitleOut.1)) : 0
        let imagePress = mine ? 1 - (1 - M.pressScale) * frame.press * (1 - M.smooth(M.seg(frame.g, 0, 0.5))) : 1
        let size = T.Layout.rowImageSize * k
        ZStack(alignment: .topLeading) {
            ForYouInsightsText(text: item.title, style: T.Typo.rowTitle, color: T.Colors.ink, k: k, width: T.Layout.rowTextWidth, lines: 3)
                .opacity(1 - titleP)
                .offset(x: T.Layout.column * k, y: y(top) - M.Reader.rowTitleRise * k * titleP)
            ForYouInsightsText(text: item.summary, style: T.Typo.rowSummary, color: T.Colors.tertiary, k: k, width: T.Layout.rowTextWidth, lines: 3)
                .offset(x: T.Layout.column * k, y: y(top + T.Layout.rowSummaryOffset))
            HStack(spacing: T.Layout.rowMetaGap * k) {
                Image(systemName: "clock.fill")
                    .font(.system(size: T.Layout.rowMetaIcon * k))
                ForYouInsightsText(text: item.readTime, style: T.Typo.rowMeta, color: T.Colors.meta, k: k)
            }
            .foregroundStyle(T.Colors.meta)
            .offset(x: T.Layout.column * k, y: y(top + T.Layout.rowMetaOffset))
            ForYouInsightsAssets.flats[index]
                .resizable()
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: T.Radius.image * k, style: .circular))
                .scaleEffect(imagePress)
                .opacity(mine && frame.g > M.Reader.handOff ? 0 : 1)
                .accessibilityHidden(true)
                .offset(x: T.Layout.rowImageX * k, y: y(top))
            Button(action: onOpen) {
                Color.clear
                    .frame(width: T.Layout.columnWidth * k, height: T.Layout.rowPitch * k)
                    .contentShape(Rectangle())
            }
            .buttonStyle(ForYouInsightsRowButtonStyle(onChange: onPress))
            .accessibilityLabel("\(item.title). \(item.summary) \(item.readTime).")
            .offset(x: T.Layout.column * k, y: y(top - T.Space.md))
            T.Colors.separator
                .frame(width: T.Layout.columnWidth * k, height: T.Layout.divider)
                .offset(x: T.Layout.column * k, y: y(top + T.Layout.rowDividerOffset))
        }
    }
}
