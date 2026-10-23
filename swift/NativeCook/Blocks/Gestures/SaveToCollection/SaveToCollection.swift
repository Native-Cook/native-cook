import SwiftUI

struct SaveToCollection: View {
    static let loop: Double = 10

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model: SaveToCollectionModel

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _model = StateObject(wrappedValue: SaveToCollectionModel(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let safeArea = SaveToCollectionSafeArea.current
            let layout = SaveToCollectionLayout(
                size: proxy.size,
                top: max(proxy.safeAreaInsets.top, safeArea.top),
                bottom: max(proxy.safeAreaInsets.bottom, safeArea.bottom)
            )
            ZStack(alignment: .topLeading) {
                SaveToCollectionTokens.Palette.page
                SaveToCollectionStaticFeed(layout: layout)

                TimelineView(.animation(minimumInterval: 1 / 120, paused: !model.demoRunning)) { timeline in
                    let presentation = model.presentation(at: timeline.date)
                    SaveToCollectionDynamicLayer(
                        layout: layout,
                        presentation: presentation,
                        reduceMotion: reduceMotion,
                        autoPlay: autoPlay,
                        model: model
                    )
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .coordinateSpace(name: "SaveToCollectionSpace")
            .clipped()
            .onAppear {
                _ = SaveToCollectionAssets.registerFonts
                model.configure(layout: layout, reduceMotion: reduceMotion)
            }
            .onChange(of: layout) { _, value in
                model.configure(layout: value, reduceMotion: reduceMotion)
            }
            .onChange(of: reduceMotion) { _, value in
                model.configure(layout: layout, reduceMotion: value)
            }
        }
        .background(SaveToCollectionTokens.Palette.page)
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }
}

@MainActor
private enum SaveToCollectionSafeArea {
    static var current: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets ?? .zero
    }
}

private struct SaveToCollectionStaticFeed: View {
    let layout: SaveToCollectionLayout

    var body: some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                Text("Discover")
                    .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.semibold, size: 28))
                    .tracking(-0.6)
                    .foregroundStyle(SaveToCollectionTokens.Palette.ink)
                Spacer(minLength: 0)
                SaveToCollectionGlass(radius: 18) {
                    Text("LK")
                        .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.medium, size: 13))
                        .foregroundStyle(SaveToCollectionTokens.Palette.ink)
                        .frame(width: 36, height: 36)
                }
                .frame(width: 36, height: 36)
            }
            .frame(
                width: layout.size.width - SaveToCollectionTokens.Layout.edge * 2,
                height: SaveToCollectionTokens.Layout.titleHeight
            )
            .position(
                x: layout.size.width / 2,
                y: layout.top + SaveToCollectionTokens.Layout.titleHeight / 2
            )

            HStack(spacing: 8) {
                SaveToCollectionChip(label: "For you", selected: true)
                SaveToCollectionChip(label: "Coast", selected: false)
                SaveToCollectionChip(label: "Mountains", selected: false)
                SaveToCollectionChip(label: "Rooms", selected: false)
            }
            .fixedSize()
            .offset(
                x: SaveToCollectionTokens.Layout.edge,
                y: layout.top + SaveToCollectionTokens.Layout.chipsTop
            )

            ForEach(SaveToCollectionTokens.photos.indices, id: \.self) { index in
                let frame = layout.tiles[index]
                SaveToCollectionAssets.image(index)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFill()
                    .frame(width: frame.width, height: frame.height)
                    .clipShape(RoundedRectangle(cornerRadius: SaveToCollectionTokens.Layout.radius, style: .continuous))
                    .position(x: frame.midX, y: frame.midY)
                    .accessibilityHidden(true)
            }
        }
    }

}

private struct SaveToCollectionChip: View {
    let label: String
    let selected: Bool

    var body: some View {
        Text(label)
            .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.medium, size: 14))
            .tracking(-0.1)
            .foregroundStyle(selected ? SaveToCollectionTokens.Palette.page : SaveToCollectionTokens.Palette.mute)
            .padding(.horizontal, 14)
            .frame(height: SaveToCollectionTokens.Layout.chipHeight)
            .background(selected ? SaveToCollectionTokens.Palette.ink : Color.clear)
            .clipShape(Capsule())
            .overlay {
                if !selected {
                    Capsule().stroke(SaveToCollectionTokens.Palette.hairline, lineWidth: 0.5)
                }
            }
    }
}

private struct SaveToCollectionDynamicLayer: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    let reduceMotion: Bool
    let autoPlay: Bool
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        ZStack(alignment: .topLeading) {
            SaveToCollectionTileEffects(layout: layout, presentation: presentation, model: model)
            SaveToCollectionGridGesture(layout: layout, presentation: presentation, model: model)
            SaveToCollectionBottomFade(layout: layout)
            SaveToCollectionBar(layout: layout, presentation: presentation, model: model)
            SaveToCollectionToast(layout: layout, presentation: presentation, model: model)
            SaveToCollectionCarried(layout: layout, presentation: presentation)
            SaveToCollectionSheet(
                layout: layout,
                presentation: presentation,
                reduceMotion: reduceMotion,
                model: model
            )
            if autoPlay {
                Circle()
                    .fill(SaveToCollectionTokens.Palette.finger)
                    .overlay {
                        Circle().stroke(SaveToCollectionTokens.Palette.fingerEdge, lineWidth: 1)
                    }
                    .frame(width: SaveToCollectionTokens.Layout.finger, height: SaveToCollectionTokens.Layout.finger)
                    .scaleEffect(1 - 0.12 * presentation.ghostPress)
                    .opacity(presentation.ghostOpacity)
                    .position(presentation.ghostCentre)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }
}

private struct SaveToCollectionTileEffects: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        ForEach(SaveToCollectionTokens.photos.indices, id: \.self) { index in
            let frame = layout.tiles[index]
            let lifted = presentation.carryIndex == index
            let dim = lifted ? 0.84 : (presentation.carryIndex == nil ? 0 : 0.30 * presentation.lift)
            RoundedRectangle(cornerRadius: SaveToCollectionTokens.Layout.radius, style: .continuous)
                .fill(Color.black.opacity(dim))
                .frame(width: frame.width, height: frame.height)
                .position(x: frame.midX, y: frame.midY)
                .allowsHitTesting(false)

            if presentation.markedIndex == index {
                SaveToCollectionGlass(radius: 14) {
                    SaveToCollectionGlyph(.bookmark, size: 16, weight: 1.75, filled: true)
                        .frame(width: 28, height: 28)
                }
                .frame(width: 28, height: 28)
                .scaleEffect(0.8 + 0.2 * presentation.mark)
                .opacity(presentation.mark)
                .position(x: frame.maxX - 22, y: frame.minY + 22)
                .allowsHitTesting(false)
            }
        }
    }
}

private struct SaveToCollectionGridGesture: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: layout.size.width, height: max(layout.barY - 8, 0))
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named("SaveToCollectionSpace"))
                    .onChanged { value in
                        model.gridTouchChanged(value.location, snapshot: presentation)
                    }
                    .onEnded { value in
                        model.gridTouchEnded(value.location)
                    }
            )
            .allowsHitTesting(presentation.sheet < 0.01)
            .accessibilityHidden(true)
    }
}

private struct SaveToCollectionBottomFade: View {
    let layout: SaveToCollectionLayout

    var body: some View {
        let top = layout.barY - 72
        LinearGradient(
            stops: [
                .init(color: SaveToCollectionTokens.Palette.page.opacity(0), location: 0),
                .init(color: SaveToCollectionTokens.Palette.page, location: 0.7),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .frame(width: layout.size.width, height: layout.size.height - top)
        .position(x: layout.size.width / 2, y: top + (layout.size.height - top) / 2)
        .allowsHitTesting(false)
    }
}

private struct SaveToCollectionBar: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        let tabsWidth = layout.size.width - SaveToCollectionTokens.Layout.edge * 2
            - SaveToCollectionTokens.Layout.saveSize - 12
        let saveScale = presentation.gulp * (1 + 0.12 * presentation.pull) * (1 - 0.06 * presentation.press)
        ZStack(alignment: .topLeading) {
            SaveToCollectionGlass(radius: SaveToCollectionTokens.Layout.barHeight / 2) {
                HStack(spacing: 0) {
                    SaveToCollectionTab(label: "Home", glyph: .home, selected: true)
                    SaveToCollectionTab(label: "Search", glyph: .search, selected: false)
                    SaveToCollectionTab(label: "Inbox", glyph: .inbox, selected: false)
                }
                .padding(.horizontal, 12)
                .frame(width: tabsWidth, height: SaveToCollectionTokens.Layout.barHeight)
            }
            .frame(width: tabsWidth, height: SaveToCollectionTokens.Layout.barHeight)
            .position(
                x: SaveToCollectionTokens.Layout.edge + tabsWidth / 2,
                y: layout.barY + SaveToCollectionTokens.Layout.barHeight / 2
            )

            Circle()
                .fill(Color.white.opacity(0.08))
                .frame(width: 100, height: 100)
                .scaleEffect(1 + 0.3 * presentation.pull)
                .opacity(0.9 * presentation.pull)
                .position(layout.save)
                .allowsHitTesting(false)

            ZStack(alignment: .topTrailing) {
                SaveToCollectionGlass(radius: SaveToCollectionTokens.Layout.saveSize / 2) {
                    SaveToCollectionGlyph(.bookmark, size: 24, weight: 2)
                        .frame(width: SaveToCollectionTokens.Layout.saveSize, height: SaveToCollectionTokens.Layout.saveSize)
                }
                .frame(width: SaveToCollectionTokens.Layout.saveSize, height: SaveToCollectionTokens.Layout.saveSize)

                Text("\(presentation.count)")
                    .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.semibold, size: 11))
                    .monospacedDigit()
                    .foregroundStyle(SaveToCollectionTokens.Palette.page)
                    .padding(.horizontal, 6)
                    .frame(minWidth: 22, minHeight: 22)
                    .background(SaveToCollectionTokens.Palette.ink)
                    .clipShape(Capsule())
                    .opacity(presentation.roll)
                    .offset(x: 4, y: -4 + 6 * (1 - presentation.roll))
            }
            .frame(width: SaveToCollectionTokens.Layout.saveSize, height: SaveToCollectionTokens.Layout.saveSize)
            .scaleEffect(saveScale)
            .position(layout.save)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named("SaveToCollectionSpace"))
                    .onChanged { _ in model.saveTouchChanged(snapshot: presentation) }
                    .onEnded { _ in model.saveTouchEnded() }
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Saved, \(SaveToCollectionTokens.collectionName), \(presentation.count) places")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { model.saveTouchChanged(snapshot: presentation); model.saveTouchEnded() }
        }
    }
}

private struct SaveToCollectionTab: View {
    let label: String
    let glyph: SaveToCollectionTokens.Glyph
    let selected: Bool

    var body: some View {
        VStack(spacing: 3) {
            SaveToCollectionGlyph(
                glyph,
                size: 22,
                weight: selected ? 2 : 1.75,
                color: selected ? SaveToCollectionTokens.Palette.ink : SaveToCollectionTokens.Palette.mute
            )
            Text(label)
                .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.medium, size: 10))
                .tracking(0.1)
                .foregroundStyle(selected ? SaveToCollectionTokens.Palette.ink : SaveToCollectionTokens.Palette.mute)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct SaveToCollectionToast: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        SaveToCollectionGlass(radius: 22) {
            HStack(spacing: 16) {
                (Text("Saved to ").foregroundStyle(SaveToCollectionTokens.Palette.mute)
                    + Text(SaveToCollectionTokens.collectionName).foregroundStyle(SaveToCollectionTokens.Palette.ink))
                    .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.medium, size: 14))
                Text("View")
                    .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.semibold, size: 14))
                    .foregroundStyle(SaveToCollectionTokens.Palette.ink)
            }
            .padding(.horizontal, 18)
            .frame(height: 44)
        }
        .fixedSize()
        .scaleEffect(0.96 + 0.04 * presentation.toast)
        .opacity(presentation.toast)
        .position(x: layout.size.width / 2, y: layout.barY - 34 + 8 * (1 - presentation.toast))
        .contentShape(Capsule())
        .onTapGesture { model.openFromToast(snapshot: presentation) }
        .allowsHitTesting(presentation.toast > 0.01 && presentation.sheet < 0.01)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

private struct SaveToCollectionCarried: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation

    var body: some View {
        if let index = presentation.carryIndex {
            let frame = layout.tiles[index]
            let magnet = presentation.pull * SaveToCollectionTokens.Save.magnetPull
            let magnetX = presentation.centre.x + (layout.save.x - presentation.centre.x) * magnet
            let magnetY = presentation.centre.y + (layout.save.y - presentation.centre.y) * magnet
            let x = magnetX + (layout.save.x - magnetX) * presentation.drop
            let y = magnetY + (layout.save.y - magnetY) * presentation.drop
            let scale = (1 + SaveToCollectionTokens.Save.liftScale * presentation.lift)
                * (1 - SaveToCollectionTokens.Save.magnetShrink * presentation.pull)
                * (1 - 0.86 * presentation.drop)
            let opacity = 1 - SaveToCollectionMath.clamp((presentation.drop - 0.55) / 0.45)

            SaveToCollectionAssets.image(index)
                .resizable()
                .interpolation(.high)
                .scaledToFill()
                .frame(width: frame.width, height: frame.height)
                .clipShape(RoundedRectangle(cornerRadius: SaveToCollectionTokens.Layout.radius, style: .continuous))
                .shadow(color: Color.black.opacity(0.55 * presentation.lift), radius: 24, x: 0, y: 16)
                .rotationEffect(.degrees(presentation.lean))
                .scaleEffect(scale)
                .opacity(opacity)
                .position(x: x, y: y - SaveToCollectionTokens.Save.liftRise * presentation.lift)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}

private struct SaveToCollectionSheet: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    let reduceMotion: Bool
    @ObservedObject var model: SaveToCollectionModel

    var body: some View {
        let travel = layout.size.height - layout.sheetY
        let fresh = presentation.count > SaveToCollectionTokens.Save.countBefore
        ZStack(alignment: .topLeading) {
            SaveToCollectionTokens.Palette.scrim
                .opacity(presentation.sheet)
                .contentShape(Rectangle())
                .onTapGesture { model.closeFromSheet(snapshot: presentation) }

            ZStack(alignment: .topLeading) {
                SaveToCollectionSheetGlass()
                Capsule()
                    .fill(Color.white.opacity(0.30))
                    .frame(width: 36, height: 5)
                    .position(x: layout.size.width / 2, y: 10.5)

                VStack(alignment: .leading, spacing: 2) {
                    Text(SaveToCollectionTokens.collectionName)
                        .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.semibold, size: 22))
                        .tracking(-0.4)
                        .foregroundStyle(SaveToCollectionTokens.Palette.ink)
                    Text("\(presentation.count) places · \(fresh ? "Updated just now" : "Updated 2 days ago")")
                        .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.regular, size: 13))
                        .monospacedDigit()
                        .foregroundStyle(SaveToCollectionTokens.Palette.mute)
                }
                .offset(x: SaveToCollectionTokens.Layout.edge, y: 29)

                SaveToCollectionGlass(radius: 18) {
                    SaveToCollectionGlyph(.close, size: 18, weight: 2)
                        .frame(width: 36, height: 36)
                }
                .frame(width: 36, height: 36)
                .position(x: layout.size.width - SaveToCollectionTokens.Layout.edge - 18, y: 45)
                .contentShape(Circle())
                .onTapGesture { model.closeFromSheet(snapshot: presentation) }
                .accessibilityLabel("Close")
                .accessibilityAddTraits(.isButton)

                SaveToCollectionSheetGrid(layout: layout, presentation: presentation, fresh: fresh)
                    .offset(x: SaveToCollectionTokens.Layout.edge, y: 92)
            }
            .frame(width: layout.size.width, height: travel + 40, alignment: .topLeading)
            .opacity(reduceMotion ? presentation.sheet : 1)
            .offset(y: reduceMotion ? layout.sheetY : layout.sheetY + (1 - presentation.sheet) * travel)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .allowsHitTesting(presentation.sheet > 0.01)
        .accessibilityElement(children: .contain)
    }
}

private struct SaveToCollectionSheetGrid: View {
    let layout: SaveToCollectionLayout
    let presentation: SaveToCollectionPresentation
    let fresh: Bool

    var body: some View {
        let width = (layout.size.width - SaveToCollectionTokens.Layout.edge * 2 - SaveToCollectionTokens.Layout.gap) / 2
        let height = (width * 1.2).rounded()
        ZStack(alignment: .topLeading) {
            ForEach(Array(SaveToCollectionTokens.collectionBefore.enumerated()), id: \.element) { order, photo in
                SaveToCollectionSheetTile(photo: photo, isNew: false, width: width, height: height)
                    .position(cellPosition(slot: fresh ? order + 1 : order, width: width, height: height))
            }
            if fresh {
                SaveToCollectionSheetTile(photo: 4, isNew: true, width: width, height: height)
                    .opacity(presentation.arrive)
                    .scaleEffect(0.94 + 0.06 * presentation.arrive)
                    .offset(y: -14 * (1 - presentation.arrive))
                    .position(cellPosition(slot: 0, width: width, height: height))
            }
        }
    }

    private func cellPosition(slot: Int, width: CGFloat, height: CGFloat) -> CGPoint {
        CGPoint(
            x: CGFloat(slot % 2) * (width + SaveToCollectionTokens.Layout.gap) + width / 2,
            y: CGFloat(slot / 2) * (height + SaveToCollectionTokens.Layout.sheetCaption + SaveToCollectionTokens.Layout.gap)
                + (height + SaveToCollectionTokens.Layout.sheetCaption) / 2
        )
    }
}

private struct SaveToCollectionSheetTile: View {
    let photo: Int
    let isNew: Bool
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SaveToCollectionAssets.image(photo)
                .resizable()
                .interpolation(.high)
                .scaledToFill()
                .frame(width: width, height: height)
                .clipShape(RoundedRectangle(cornerRadius: SaveToCollectionTokens.Layout.radius, style: .continuous))
                .overlay(alignment: .bottomLeading) {
                    if isNew {
                        Text("JUST SAVED")
                            .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.semibold, size: 11))
                            .tracking(0.4)
                            .foregroundStyle(SaveToCollectionTokens.Palette.ink)
                            .padding(.horizontal, 8)
                            .frame(height: 22)
                            .background(SaveToCollectionTokens.Palette.page.opacity(0.62))
                            .clipShape(Capsule())
                            .overlay { Capsule().stroke(SaveToCollectionTokens.Palette.rim, lineWidth: 0.5) }
                            .padding(8)
                    }
                }
            Text(SaveToCollectionTokens.photos[photo].title)
                .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.medium, size: 12))
                .foregroundStyle(SaveToCollectionTokens.Palette.ink)
                .lineLimit(1)
                .frame(height: 16)
                .padding(.top, 8)
            Text(SaveToCollectionTokens.photos[photo].place)
                .font(SaveToCollectionAssets.font(SaveToCollectionTokens.FontName.regular, size: 12))
                .foregroundStyle(SaveToCollectionTokens.Palette.mute)
                .lineLimit(1)
                .frame(height: 16)
        }
        .frame(width: width, height: height + SaveToCollectionTokens.Layout.sheetCaption, alignment: .topLeading)
    }
}

private struct SaveToCollectionGlass<Content: View>: View {
    let radius: CGFloat
    let content: Content

    init(radius: CGFloat, @ViewBuilder content: () -> Content) {
        self.radius = radius
        self.content = content()
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        ZStack {
            shape.fill(.ultraThinMaterial)
            shape.fill(SaveToCollectionTokens.Palette.glassFill)
            shape.fill(
                LinearGradient(
                    stops: [
                        .init(color: Color.white.opacity(0.14), location: 0),
                        .init(color: Color.white.opacity(0), location: 0.5),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            content
            shape.stroke(SaveToCollectionTokens.Palette.rim, lineWidth: 0.5)
        }
        .clipShape(shape)
    }
}

private struct SaveToCollectionSheetGlass: View {
    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: SaveToCollectionTokens.Layout.sheetRadius,
            bottomLeadingRadius: 0,
            bottomTrailingRadius: 0,
            topTrailingRadius: SaveToCollectionTokens.Layout.sheetRadius,
            style: .continuous
        )
        ZStack {
            shape.fill(.regularMaterial)
            shape.fill(SaveToCollectionTokens.Palette.sheetFill)
            shape.fill(
                LinearGradient(
                    colors: [Color.white.opacity(0.14), Color.white.opacity(0)],
                    startPoint: .top,
                    endPoint: .center
                )
            )
            shape.stroke(SaveToCollectionTokens.Palette.rim, lineWidth: 0.5)
        }
    }
}

private struct SaveToCollectionGlyph: View {
    let glyph: SaveToCollectionTokens.Glyph
    let size: CGFloat
    let weight: CGFloat
    let filled: Bool
    let color: Color

    init(
        _ glyph: SaveToCollectionTokens.Glyph,
        size: CGFloat,
        weight: CGFloat,
        filled: Bool = false,
        color: Color = SaveToCollectionTokens.Palette.ink
    ) {
        self.glyph = glyph
        self.size = size
        self.weight = weight
        self.filled = filled
        self.color = color
    }

    var body: some View {
        SaveToCollectionGlyphShape(glyph: glyph)
            .fill(filled ? color : Color.clear)
            .overlay {
                SaveToCollectionGlyphShape(glyph: glyph)
                    .stroke(
                        color,
                        style: StrokeStyle(lineWidth: weight, lineCap: .round, lineJoin: .round)
                    )
            }
            .frame(width: size, height: size)
    }
}

private struct SaveToCollectionGlyphShape: Shape {
    let glyph: SaveToCollectionTokens.Glyph

    func path(in rect: CGRect) -> Path {
        var path = Path()
        switch glyph {
        case .home:
            path.move(to: CGPoint(x: 4, y: 10.5))
            path.addLine(to: CGPoint(x: 12, y: 4))
            path.addLine(to: CGPoint(x: 20, y: 10.5))
            path.addLine(to: CGPoint(x: 20, y: 19))
            path.addLine(to: CGPoint(x: 14.5, y: 19))
            path.addLine(to: CGPoint(x: 14.5, y: 14.5))
            path.addLine(to: CGPoint(x: 9.5, y: 14.5))
            path.addLine(to: CGPoint(x: 9.5, y: 19))
            path.addLine(to: CGPoint(x: 4, y: 19))
            path.closeSubpath()
        case .search:
            path.addEllipse(in: CGRect(x: 4, y: 4, width: 13, height: 13))
            path.move(to: CGPoint(x: 15.2, y: 15.2))
            path.addLine(to: CGPoint(x: 20, y: 20))
        case .inbox:
            path.move(to: CGPoint(x: 4, y: 13.5))
            path.addLine(to: CGPoint(x: 6.2, y: 6))
            path.addQuadCurve(to: CGPoint(x: 7.6, y: 5), control: CGPoint(x: 6.6, y: 5))
            path.addLine(to: CGPoint(x: 16.4, y: 5))
            path.addQuadCurve(to: CGPoint(x: 17.8, y: 6), control: CGPoint(x: 17.4, y: 5))
            path.addLine(to: CGPoint(x: 20, y: 13.5))
            path.addLine(to: CGPoint(x: 20, y: 19))
            path.addLine(to: CGPoint(x: 4, y: 19))
            path.closeSubpath()
            path.move(to: CGPoint(x: 4, y: 13.5))
            path.addLine(to: CGPoint(x: 8.5, y: 13.5))
            path.addLine(to: CGPoint(x: 9.7, y: 15.5))
            path.addLine(to: CGPoint(x: 14.3, y: 15.5))
            path.addLine(to: CGPoint(x: 15.5, y: 13.5))
            path.addLine(to: CGPoint(x: 20, y: 13.5))
        case .bookmark:
            path.move(to: CGPoint(x: 7, y: 4.5))
            path.addLine(to: CGPoint(x: 17, y: 4.5))
            path.addLine(to: CGPoint(x: 18, y: 5.5))
            path.addLine(to: CGPoint(x: 18, y: 20))
            path.addLine(to: CGPoint(x: 12, y: 15.8))
            path.addLine(to: CGPoint(x: 6, y: 20))
            path.addLine(to: CGPoint(x: 6, y: 5.5))
            path.closeSubpath()
        case .close:
            path.move(to: CGPoint(x: 7, y: 7))
            path.addLine(to: CGPoint(x: 17, y: 17))
            path.move(to: CGPoint(x: 17, y: 7))
            path.addLine(to: CGPoint(x: 7, y: 17))
        }
        return path.applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24))
    }
}

#Preview {
    SaveToCollection(autoPlay: true)
        .preferredColorScheme(.dark)
}
