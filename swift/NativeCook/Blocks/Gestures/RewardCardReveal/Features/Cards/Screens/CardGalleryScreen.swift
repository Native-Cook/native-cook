import SwiftUI

private let collectionButtonHeight: CGFloat = 42
private let mobileColumnGap: CGFloat = 10
private let sheetRadius: CGFloat = 32
private let backdropColors = ["#d69aa8", "#e4b7bd", "#d8a6ae"]
private let footerFadeColors = [
    "rgba(233, 237, 243, 0)",
    "rgba(233, 237, 243, 0.72)",
    "rgba(233, 237, 243, 0.97)",
]

struct CardGalleryScreen: View {
    var safeAreaInsets = EdgeInsets()
    let onSelectCard: (String) -> Void

    var body: some View {
        GeometryReader { geo in
            let insets = safeAreaInsets
            let width = geo.size.width
            let horizontalPadding = Spacing.lg
            let columnGap = width >= 720 ? Spacing.lg : mobileColumnGap
            let cardWidth = min((width - horizontalPadding * 2 - columnGap) / 2, 218)
            let cardHeight = cardWidth * cardHeightRatio
            let gridWidth = cardWidth * 2 + columnGap
            let sheetTop = max(insets.top + Spacing.xs, Spacing.xl)
            let footerBottomPadding = max(insets.bottom, Spacing.lg)

            ZStack(alignment: .top) {
                LinearGradient(
                    gradient: Gradient(css: backdropColors),
                    startPoint: UnitPoint(x: 0, y: 0),
                    endPoint: UnitPoint(x: 1, y: 0.8)
                )

                ZStack(alignment: .top) {
                    CollectionGalleryPalette.background

                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 0) {
                            VStack(spacing: 0) {
                                Text("Achievements and")
                                    .frame(height: 26)
                                Text("record of a growing mind.")
                                    .frame(height: 26)
                            }
                            .font(.custom("Georgia", fixedSize: 25))
                            .tracking(-1.1)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(CollectionGalleryPalette.text)
                            .frame(maxWidth: 450)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("Achievements and\nrecord of a growing mind.")
                            .padding(.bottom, Spacing.xxl)

                            LazyVGrid(
                                columns: [
                                    GridItem(.fixed(cardWidth), spacing: columnGap, alignment: .top),
                                    GridItem(.fixed(cardWidth), spacing: 0, alignment: .top),
                                ],
                                alignment: .center,
                                spacing: Spacing.lg
                            ) {
                                ForEach(galleryItems) { item in
                                    CardThumbnail(item: item, width: cardWidth, onSelect: onSelectCard)
                                }
                            }
                            .padding(.bottom, Spacing.lg)

                            // Ghost footer hinting at more cards below.
                            HStack(spacing: columnGap) {
                                ForEach(0..<2, id: \.self) { _ in
                                    RoundedRectangle(cornerRadius: 10 * (cardWidth / 218), style: .continuous)
                                        .fill(Color(css: "rgba(255, 255, 255, 0.56)"))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10 * (cardWidth / 218), style: .continuous)
                                                .strokeBorder(Color(css: "rgba(255, 255, 255, 0.84)"), lineWidth: 1)
                                        )
                                        .frame(width: cardWidth, height: cardHeight)
                                }
                            }
                            .opacity(0.45)
                            .frame(width: gridWidth, height: cardHeight * 0.58, alignment: .top)
                            .clipped()
                        }
                        .padding(.top, Spacing.huge)
                        .padding(.horizontal, horizontalPadding)
                        .padding(.bottom, collectionButtonHeight + Spacing.huge + insets.bottom)
                        .frame(maxWidth: .infinity)
                    }

                    Capsule()
                        .fill(Color(css: "rgba(103, 113, 124, 0.58)"))
                        .frame(width: 34, height: 5)
                        .padding(.top, Spacing.sm)

                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        ZStack(alignment: .bottom) {
                            Rectangle()
                                .fill(.ultraThinMaterial)
                                .opacity(0.72)
                                .padding(.top, Spacing.sm)
                            LinearGradient(
                                gradient: Gradient(css: footerFadeColors, positions: [0, 0.46, 1]),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            Text("My Collection")
                                .font(.system(size: 15))
                                .foregroundStyle(CollectionGalleryPalette.text)
                                .frame(width: min(gridWidth * 0.63, 284), height: collectionButtonHeight)
                                .background(Capsule().fill(Color(css: "rgba(238, 242, 247, 0.7)")))
                                .overlay(Capsule().strokeBorder(Color(css: "rgba(69, 79, 92, 0.44)"), lineWidth: 1))
                                .padding(.bottom, footerBottomPadding)
                        }
                        .frame(height: Spacing.huge + collectionButtonHeight + footerBottomPadding)
                    }
                    .allowsHitTesting(false)
                }
                .clipShape(
                    UnevenRoundedRectangle(
                        topLeadingRadius: sheetRadius,
                        bottomLeadingRadius: 0,
                        bottomTrailingRadius: 0,
                        topTrailingRadius: sheetRadius,
                        style: .continuous
                    )
                )
                .padding(.top, sheetTop)
            }
        }
        .background(Color(css: backdropColors[0]))
    }
}

#Preview {
    CardGalleryScreen { _ in }
}
