import SwiftUI

enum ForYouCardStackCoverKind: String, Hashable {
    case loud
    case seeing
    case objects
    case pages
    case sky
    case arches
    case grid
    case lines
    case shapes
}

enum ForYouCardStackCoverTitles {
    static let values: [ForYouCardStackCoverKind: String] = [
        .loud: "Loud Type",
        .seeing: "The Life of Colour",
        .objects: "The Shape of Everyday Objects",
        .pages: "Pages & Margins",
        .sky: "Blue Hours",
        .arches: "Soft Geometry",
        .grid: "Grid Works",
        .lines: "Flow Lines",
        .shapes: "Small Rooms",
    ]
}

private struct ForYouCardStackSpine: Identifiable {
    let id: Int
    let color: Color
}

struct ForYouCardStackHomeView: View {
    let size: CGSize
    let deck: Double
    let onOpenBook: () -> Void

    private let spines: [ForYouCardStackSpine] = [
        ForYouCardStackSpine(id: 0, color: ForYouCardStackPalette.hex(0xF2C21A)),
        ForYouCardStackSpine(id: 1, color: ForYouCardStackPalette.hex(0xB9C2FE)),
        ForYouCardStackSpine(id: 2, color: ForYouCardStackPalette.hex(0x9FB0F0)),
    ]
    private let rowKinds: [ForYouCardStackCoverKind] = [.objects, .pages]
    private let chartKinds: [ForYouCardStackCoverKind] = [.sky, .arches, .grid]

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForYouCardStackPalette.page

            Text("For you")
                .font(.custom(ForYouCardStackFonts.medium, fixedSize: ForYouCardStackHomeLayout.titleSize))
                .foregroundStyle(ForYouCardStackPalette.ink)
                .frame(width: 180, height: 36, alignment: .leading)
                .position(x: ForYouCardStackHomeLayout.titleLeft + 90, y: ForYouCardStackHomeLayout.titleTop + 18)
                .accessibilityAddTraits(.isHeader)

            Image(uiImage: ForYouCardStackAssets.image(named: "ForYouCardStack-avatar.png"))
                .resizable()
                .scaledToFill()
                .frame(width: ForYouCardStackHomeLayout.avatar, height: ForYouCardStackHomeLayout.avatar)
                .clipShape(Circle())
                .position(
                    x: size.width - ForYouCardStackHomeLayout.avatarRight - ForYouCardStackHomeLayout.avatar / 2,
                    y: ForYouCardStackHomeLayout.avatarTop + ForYouCardStackHomeLayout.avatar / 2
                )
                .accessibilityLabel("Your profile")

            ForYouCardStackSearchIcon()
                .frame(width: ForYouCardStackHomeLayout.searchIcon, height: ForYouCardStackHomeLayout.searchIcon)
                .position(
                    x: ForYouCardStackHomeLayout.searchLeft + ForYouCardStackHomeLayout.searchIcon / 2,
                    y: ForYouCardStackHomeLayout.searchTop + ForYouCardStackHomeLayout.searchIcon / 2
                )

            Text("Book or author")
                .font(.custom(ForYouCardStackFonts.regular, fixedSize: ForYouCardStackHomeLayout.searchText))
                .foregroundStyle(ForYouCardStackPalette.placeholder)
                .frame(width: 160, height: 24, alignment: .leading)
                .position(x: ForYouCardStackHomeLayout.placeholderLeft + 80, y: ForYouCardStackHomeLayout.placeholderTop + 12)

            RoundedRectangle(cornerRadius: ForYouCardStackHomeLayout.cardRadius, style: .continuous)
                .fill(ForYouCardStackPalette.nextCard)
                .frame(width: size.width, height: 200, alignment: .topLeading)
                .position(x: size.width / 2, y: ForYouCardStackHomeLayout.nextCardTop + 100)

            ForYouCardStackDesignCard(
                size: size,
                deck: deck,
                spines: spines,
                rowKinds: rowKinds,
                chartKinds: chartKinds,
                onOpenBook: onOpenBook
            )
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

private struct ForYouCardStackDesignCard: View {
    let size: CGSize
    let deck: Double
    let spines: [ForYouCardStackSpine]
    let rowKinds: [ForYouCardStackCoverKind]
    let chartKinds: [ForYouCardStackCoverKind]
    let onOpenBook: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: ForYouCardStackHomeLayout.cardRadius, style: .continuous)
                .fill(ForYouCardStackPalette.design)
                .frame(width: size.width, height: ForYouCardStackHomeLayout.cardHeight, alignment: .topLeading)
                .position(x: size.width / 2, y: ForYouCardStackHomeLayout.cardTop + ForYouCardStackHomeLayout.cardHeight / 2)

            Text("Design")
                .font(.custom(ForYouCardStackFonts.medium, fixedSize: ForYouCardStackHomeLayout.headingSize))
                .foregroundStyle(ForYouCardStackPalette.white)
                .frame(width: 150, height: 38, alignment: .leading)
                .position(
                    x: ForYouCardStackHomeLayout.headingLeft + 75,
                    y: ForYouCardStackHomeLayout.headingTop + 19
                )

            Capsule()
                .fill(ForYouCardStackPalette.grabber)
                .frame(width: ForYouCardStackHomeLayout.grabberWidth, height: 2)
                .position(
                    x: size.width - ForYouCardStackHomeLayout.grabberRight - ForYouCardStackHomeLayout.grabberWidth / 2,
                    y: ForYouCardStackHomeLayout.grabberTop + 1
                )

            ForYouCardStackSectionLabel(text: "NEW & TRENDING", x: ForYouCardStackHomeLayout.labelLeft, y: ForYouCardStackHomeLayout.newLabelTop)
            ForYouCardStackSectionLabel(
                text: "SEE ALL",
                x: size.width - ForYouCardStackHomeLayout.labelRight - 100,
                y: ForYouCardStackHomeLayout.newLabelTop,
                alignment: .trailing,
                width: 100
            )

            ForYouCardStackDeckRow(deck: deck, width: size.width, spines: spines, rowKinds: rowKinds, onOpenBook: onOpenBook)

            ForYouCardStackSectionLabel(text: "TOP CHARTS", x: ForYouCardStackHomeLayout.labelLeft, y: ForYouCardStackHomeLayout.chartsLabelTop)

            ForEach(Array(chartKinds.enumerated()), id: \.element) { index, kind in
                ForYouCardStackScaledCover(kind: kind)
                    .frame(
                        width: ForYouCardStackHomeLayout.rowCoverWidth,
                        height: ForYouCardStackHomeLayout.rowCoverHeight,
                        alignment: .topLeading
                    )
                    .position(
                        x: ForYouCardStackHomeLayout.chartsLeft + ForYouCardStackHomeLayout.rowCoverWidth / 2 + ForYouCardStackLayout.rowStep * CGFloat(index),
                        y: ForYouCardStackHomeLayout.chartsTop + ForYouCardStackHomeLayout.rowCoverHeight / 2
                    )
                    .accessibilityLabel(ForYouCardStackCoverTitles.values[kind] ?? "Book cover")
            }
        }
        .frame(width: size.width, height: ForYouCardStackHomeLayout.cardTop + ForYouCardStackHomeLayout.cardHeight, alignment: .topLeading)
        .clipped()
    }
}

private struct ForYouCardStackDeckRow: View {
    let deck: Double
    let width: CGFloat
    let spines: [ForYouCardStackSpine]
    let rowKinds: [ForYouCardStackCoverKind]
    let onOpenBook: () -> Void

    var body: some View {
        let step = ForYouCardStackHomeLayout.rowCoverWidth + 11.3
        let seeingX = ForYouCardStackHomeLayout.rowDeckLeft + interpolate(deck, from: -ForYouCardStackHomeLayout.deckPeek, to: 0)
        let seeingScale = interpolate(deck, from: (ForYouCardStackHomeLayout.rowCoverHeight - ForYouCardStackHomeLayout.deckInset * 2) / ForYouCardStackHomeLayout.rowCoverHeight, to: 1)
        return ZStack(alignment: .topLeading) {
            ForEach(spines) { spine in
                Rectangle()
                    .fill(spine.color)
                    .frame(
                        width: ForYouCardStackHomeLayout.rowCoverWidth,
                        height: ForYouCardStackHomeLayout.rowCoverHeight - ForYouCardStackHomeLayout.deckInset * 2 * CGFloat(spine.id + 2)
                    )
                    .position(
                        x: ForYouCardStackHomeLayout.rowDeckLeft - ForYouCardStackHomeLayout.deckPeek * CGFloat(spine.id + 2) + ForYouCardStackHomeLayout.rowCoverWidth / 2,
                        y: ForYouCardStackHomeLayout.deckInset * CGFloat(spine.id + 2) + (ForYouCardStackHomeLayout.rowCoverHeight - ForYouCardStackHomeLayout.deckInset * 2 * CGFloat(spine.id + 2)) / 2
                    )
                    .zIndex(Double(-spine.id - 3))
            }

            Button(action: onOpenBook) {
                ForYouCardStackScaledCover(kind: .seeing)
                    .scaleEffect(seeingScale)
            }
            .buttonStyle(.plain)
            .frame(width: ForYouCardStackHomeLayout.rowCoverWidth, height: ForYouCardStackHomeLayout.rowCoverHeight, alignment: .topLeading)
            .position(x: seeingX + ForYouCardStackHomeLayout.rowCoverWidth / 2, y: ForYouCardStackHomeLayout.rowCoverHeight / 2)
            .zIndex(1)
            .allowsHitTesting(deck > 0.5)
            .accessibilityLabel(ForYouCardStackCoverTitles.values[.seeing] ?? "Book cover")

            ForYouCardStackScaledCover(kind: .loud)
                .frame(width: ForYouCardStackHomeLayout.rowCoverWidth, height: ForYouCardStackHomeLayout.rowCoverHeight, alignment: .topLeading)
                .position(x: ForYouCardStackHomeLayout.rowDeckLeft + step * CGFloat(deck) + ForYouCardStackHomeLayout.rowCoverWidth / 2, y: ForYouCardStackHomeLayout.rowCoverHeight / 2)
                .zIndex(2)

            ForEach(Array(rowKinds.enumerated()), id: \.element) { index, kind in
                ForYouCardStackScaledCover(kind: kind)
                    .frame(width: ForYouCardStackHomeLayout.rowCoverWidth, height: ForYouCardStackHomeLayout.rowCoverHeight, alignment: .topLeading)
                    .position(
                        x: ForYouCardStackHomeLayout.rowDeckLeft + step * CGFloat(index + 1) + step * CGFloat(deck) + ForYouCardStackHomeLayout.rowCoverWidth / 2,
                        y: ForYouCardStackHomeLayout.rowCoverHeight / 2
                    )
                    .zIndex(Double(3 - index))
                    .accessibilityLabel(ForYouCardStackCoverTitles.values[kind] ?? "Book cover")
            }
        }
        .frame(width: width, height: ForYouCardStackHomeLayout.rowCoverHeight, alignment: .topLeading)
        .position(x: width / 2, y: ForYouCardStackHomeLayout.rowTop + ForYouCardStackHomeLayout.rowCoverHeight / 2)
    }

    private func interpolate(_ value: Double, from: CGFloat, to: CGFloat) -> CGFloat {
        from + (to - from) * CGFloat(min(max(value, 0), 1))
    }
}

private struct ForYouCardStackSectionLabel: View {
    let text: String
    let x: CGFloat
    let y: CGFloat
    var alignment: TextAlignment = .leading
    var width: CGFloat = 180

    var body: some View {
        Text(text)
            .font(.custom(ForYouCardStackFonts.heavy, fixedSize: ForYouCardStackHomeLayout.labelSize))
            .tracking(0)
            .foregroundStyle(ForYouCardStackPalette.white)
            .multilineTextAlignment(alignment)
            .frame(width: width, height: 20, alignment: alignment == .trailing ? .trailing : .leading)
            .position(x: x + width / 2, y: y + 10)
    }
}

struct ForYouCardStackScaledCover: View {
    let kind: ForYouCardStackCoverKind
    let scale: CGFloat

    init(kind: ForYouCardStackCoverKind, scale: CGFloat = ForYouCardStackLayout.rowScale) {
        self.kind = kind
        self.scale = scale
    }

    var body: some View {
        ForYouCardStackCoverView(kind: kind)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: ForYouCardStackLayout.coverWidth * scale, height: ForYouCardStackLayout.coverHeight * scale, alignment: .topLeading)
            .clipped()
    }
}

private struct ForYouCardStackSearchIcon: View {
    var body: some View {
        Canvas { context, _ in
            var circle = Path()
            circle.addEllipse(in: CGRect(x: 1.2, y: 1.2, width: 15.2, height: 15.2))
            context.stroke(circle, with: .color(ForYouCardStackPalette.ink), lineWidth: 1.9)
            var handle = Path()
            handle.move(to: CGPoint(x: 14.4, y: 14.4))
            handle.addLine(to: CGPoint(x: 20, y: 20))
            context.stroke(handle, with: .color(ForYouCardStackPalette.ink), style: StrokeStyle(lineWidth: 1.9, lineCap: .round))
        }
    }
}
