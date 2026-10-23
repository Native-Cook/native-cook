import SwiftUI

struct PosterGlowCarouselFeedView: View {
    let width: Double
    let height: Double
    let scroll: Double

    private let sections = PosterGlowCarouselContent.sections
    private let layout = PosterGlowCarouselMetrics.feedLayout(PosterGlowCarouselContent.sections)

    var body: some View {
        ZStack(alignment: .topLeading) {
            PosterGlowCarouselHeroView(width: width)
            ForEach(Array(sections.enumerated()), id: \.element.id) { pair in
                let section = pair.element
                let index = pair.offset
                PosterGlowCarouselSectionRow(section: section, width: width)
                    .frame(width: width, height: PosterGlowCarouselMetrics.sectionHeight(section), alignment: .topLeading)
                    .offset(y: layout.tops[index])
                Text(section.label)
                    .font(.custom(PosterGlowCarouselFont.medium, fixedSize: PosterGlowCarouselMetrics.labelSize))
                    .tracking(0)
                    .foregroundStyle(PosterGlowCarouselColor.ink)
                    .lineLimit(1)
                    .frame(width: width - (section.labelX ?? (PosterGlowCarouselMetrics.side + 2)),
                           height: PosterGlowCarouselMetrics.labelLine,
                           alignment: .leading)
                    .offset(x: section.labelX ?? (PosterGlowCarouselMetrics.side + 2),
                            y: layout.labels[index] - PosterGlowCarouselMetrics.labelLine / 2 + PosterGlowCarouselMetrics.labelNudge)
                    .accessibilityAddTraits(.isHeader)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .offset(y: -scroll)
    }
}

struct PosterGlowCarouselSectionRow: View {
    let section: PosterGlowCarouselSection
    let width: Double

    var body: some View {
        switch section.kind {
        case .posters(let items):
            PosterGlowCarouselHorizontalRow(width: width, height: PosterGlowCarouselMetrics.posterH,
                                            gap: PosterGlowCarouselMetrics.posterGap) {
                ForEach(items) { item in PosterGlowCarouselPosterCard(item: item) }
            }
        case .wide(let items):
            PosterGlowCarouselHorizontalRow(width: width, height: PosterGlowCarouselMetrics.wideImage + PosterGlowCarouselMetrics.wideInfo,
                                            gap: PosterGlowCarouselMetrics.wideGap) {
                ForEach(items) { item in PosterGlowCarouselStillCard(item: item) }
            }
        case .tall(let items):
            PosterGlowCarouselHorizontalRow(width: width, height: PosterGlowCarouselMetrics.tallH,
                                            gap: PosterGlowCarouselMetrics.tallGap) {
                ForEach(items) { item in PosterGlowCarouselTallCard(item: item) }
            }
        }
    }
}

private struct PosterGlowCarouselHorizontalRow<Content: View>: View {
    let width: Double
    let height: Double
    let gap: Double
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: gap, content: content)
                .padding(.horizontal, PosterGlowCarouselMetrics.side)
                .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .frame(width: width, height: height, alignment: .topLeading)
        .accessibilityElement(children: .contain)
    }
}

struct PosterGlowCarouselPosterCard: View {
    let item: PosterGlowCarouselPoster

    private var title: String {
        item.style.uppercased ? item.title.uppercased() : item.title
    }

    private var ink: Color {
        PosterGlowCarouselColor.color(item.tint)
    }

    private var darkInk: Bool {
        let red = Double((item.tint >> 16) & 0xFF)
        let green = Double((item.tint >> 8) & 0xFF)
        let blue = Double(item.tint & 0xFF)
        return red * 0.299 + green * 0.587 + blue * 0.114 < 110
    }

    var body: some View {
        Button {} label: {
            ZStack(alignment: .topLeading) {
                PosterGlowCarouselColor.posterPlaceholder
                PosterGlowCarouselAssets.poster(item.image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: PosterGlowCarouselMetrics.posterW, height: PosterGlowCarouselMetrics.posterH)
                scrim
                VStack(spacing: 0) {
                    Text(title)
                        .font(.custom(item.style.fontName, fixedSize: item.style.fontSize))
                        .tracking(item.style.tracking)
                        .foregroundStyle(ink)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .lineSpacing(item.style.lineHeight - item.style.fontSize)
                        .minimumScaleFactor(0.72)
                    Text(item.credit)
                        .font(.custom(PosterGlowCarouselFont.bebas, fixedSize: 5.5))
                        .tracking(0.9)
                        .foregroundStyle(ink.opacity(0.75))
                        .lineLimit(1)
                        .padding(.top, 3)
                }
                .frame(width: PosterGlowCarouselMetrics.posterW - 12,
                       alignment: .center)
                .frame(height: PosterGlowCarouselMetrics.posterH,
                       alignment: item.top ? .top : .bottom)
                .padding(.horizontal, 6)
                .offset(y: item.top ? 10 : -9)
            }
            .frame(width: PosterGlowCarouselMetrics.posterW, height: PosterGlowCarouselMetrics.posterH)
            .clipShape(RoundedRectangle(cornerRadius: PosterGlowCarouselMetrics.posterRadius, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: PosterGlowCarouselMetrics.posterRadius, style: .continuous))
        }
        .buttonStyle(PosterGlowCarouselPressStyle())
        .accessibilityLabel(item.title)
    }

    private var scrim: some View {
        let strong = darkInk ? Color.white.opacity(0.35) : Color.black.opacity(0.42)
        let clear = Color.clear
        let stops: [Gradient.Stop] = item.top
            ? [.init(color: strong, location: 0), .init(color: clear, location: 0.42)]
            : [.init(color: clear, location: 0.58), .init(color: strong, location: 1)]
        return LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom)
            .frame(width: PosterGlowCarouselMetrics.posterW, height: PosterGlowCarouselMetrics.posterH)
    }
}

struct PosterGlowCarouselStillCard: View {
    let item: PosterGlowCarouselStill

    var body: some View {
        Button {} label: {
            ZStack(alignment: .topLeading) {
                PosterGlowCarouselColor.card
                PosterGlowCarouselAssets.still(item.image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: PosterGlowCarouselMetrics.wideW, height: PosterGlowCarouselMetrics.wideImage)
                    .clipped()
                PosterGlowCarouselIcon(kind: .playCircle, size: 14, color: PosterGlowCarouselColor.ink)
                    .offset(x: 8, y: PosterGlowCarouselMetrics.wideImage - 17 - 14)
                ZStack(alignment: .leading) {
                    PosterGlowCarouselColor.progressTrack
                    PosterGlowCarouselColor.progress
                        .frame(width: (PosterGlowCarouselMetrics.wideW - 2 * PosterGlowCarouselMetrics.progressInset) * item.progress)
                }
                .frame(width: PosterGlowCarouselMetrics.wideW - 2 * PosterGlowCarouselMetrics.progressInset,
                       height: PosterGlowCarouselMetrics.progress)
                .offset(x: PosterGlowCarouselMetrics.progressInset,
                        y: PosterGlowCarouselMetrics.wideImage - PosterGlowCarouselMetrics.progressLift - PosterGlowCarouselMetrics.progress)
                VStack(alignment: .leading, spacing: -PosterGlowCarouselMetrics.wideTitleNudge + 1) {
                    Text(item.title)
                        .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.wideTitleSize))
                        .foregroundStyle(PosterGlowCarouselColor.ink)
                        .lineLimit(1)
                        .frame(height: 24, alignment: .leading)
                    Text(item.meta)
                        .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.wideMetaSize))
                        .foregroundStyle(PosterGlowCarouselColor.inkMute)
                        .lineLimit(1)
                        .frame(height: 16, alignment: .leading)
                }
                .frame(width: PosterGlowCarouselMetrics.wideW - 20,
                       alignment: .leading)
                .offset(x: 10, y: PosterGlowCarouselMetrics.wideImage + 11 + PosterGlowCarouselMetrics.wideTitleNudge)
            }
            .frame(width: PosterGlowCarouselMetrics.wideW,
                   height: PosterGlowCarouselMetrics.wideImage + PosterGlowCarouselMetrics.wideInfo,
                   alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: PosterGlowCarouselMetrics.wideRadius, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: PosterGlowCarouselMetrics.wideRadius, style: .continuous))
        }
        .buttonStyle(PosterGlowCarouselPressStyle())
        .accessibilityLabel("\(item.title), \(item.meta)")
    }
}

struct PosterGlowCarouselTallCard: View {
    let item: PosterGlowCarouselTall

    private var word: String {
        item.style == .anton ? item.word : item.word.uppercased()
    }

    var body: some View {
        Button {} label: {
            ZStack(alignment: .topLeading) {
                PosterGlowCarouselColor.posterPlaceholder
                PosterGlowCarouselTallImage(item: item)
                LinearGradient(
                    stops: item.top
                        ? [.init(color: Color.black.opacity(0.35), location: 0), .init(color: .clear, location: 0.3)]
                        : [.init(color: .clear, location: 0.65), .init(color: Color.black.opacity(0.5), location: 1)],
                    startPoint: .top, endPoint: .bottom)
                Text(word)
                    .font(.custom(item.style.fontName,
                                  fixedSize: item.style == .anton ? PosterGlowCarouselMetrics.tallWordSize : 30))
                    .tracking(item.style.tracking)
                    .foregroundStyle(PosterGlowCarouselColor.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
                    .multilineTextAlignment(.center)
                    .frame(width: PosterGlowCarouselMetrics.tallW - 20,
                           height: item.style == .anton ? 44 : 36,
                           alignment: .center)
                    .offset(x: 10,
                            y: item.top ? 18 : PosterGlowCarouselMetrics.tallH -
                                (PosterGlowCarouselMetrics.tallWordFromBottom - 22) -
                                (item.style == .anton ? 44 : 36))
            }
            .frame(width: PosterGlowCarouselMetrics.tallW, height: PosterGlowCarouselMetrics.tallH)
            .clipShape(RoundedRectangle(cornerRadius: PosterGlowCarouselMetrics.tallRadius, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: PosterGlowCarouselMetrics.tallRadius, style: .continuous))
        }
        .buttonStyle(PosterGlowCarouselPressStyle())
        .accessibilityLabel(item.word)
    }
}

private struct PosterGlowCarouselTallImage: View {
    let item: PosterGlowCarouselTall

    var body: some View {
        let image: Image
        if item.image.hasPrefix("game-") {
            image = PosterGlowCarouselAssets.game(item.image)
        } else {
            image = PosterGlowCarouselAssets.mood(item.image)
        }
        return image
            .resizable()
            .scaledToFill()
            .frame(width: PosterGlowCarouselMetrics.tallW, height: PosterGlowCarouselMetrics.tallH)
            .clipped()
    }
}

struct PosterGlowCarouselPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.12), value: configuration.isPressed)
    }
}

struct PosterGlowCarouselHeroView: View {
    let width: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            PosterGlowCarouselAssets.hero()
                .resizable()
                .scaledToFill()
                .frame(width: width, height: PosterGlowCarouselMetrics.heroHeight)
                .clipped()
            LinearGradient(stops: [
                .init(color: Color(.sRGB, red: 43 / 255, green: 41 / 255, blue: 48 / 255, opacity: 0), location: PosterGlowCarouselMetrics.heroFadeFrom),
                .init(color: PosterGlowCarouselColor.background, location: 1),
            ], startPoint: .top, endPoint: .bottom)
            .frame(width: width, height: PosterGlowCarouselMetrics.heroHeight)

            Text(PosterGlowCarouselContent.hero.badge)
                .font(.custom(PosterGlowCarouselFont.bebas, fixedSize: 8))
                .tracking(0.8)
                .foregroundStyle(PosterGlowCarouselColor.ink)
                .frame(width: 52, height: 10)
                .background(PosterGlowCarouselColor.badge)
                .offset(x: width / 2 - 26, y: PosterGlowCarouselMetrics.badgeY - 5)
            Text(PosterGlowCarouselContent.hero.subtitle)
                .font(.custom(PosterGlowCarouselFont.bebas, fixedSize: 14))
                .tracking(1.6)
                .foregroundStyle(PosterGlowCarouselColor.color(0xF2F2F2))
                .shadow(color: .black.opacity(0.6), radius: 4)
                .frame(width: width, height: 16)
                .offset(y: PosterGlowCarouselMetrics.subtitleY - 8)
            Text(PosterGlowCarouselContent.hero.lockup)
                .font(.custom(PosterGlowCarouselFont.marcellus, fixedSize: PosterGlowCarouselMetrics.lockupSize - 3))
                .tracking(1.2)
                .foregroundStyle(PosterGlowCarouselColor.color(0xE9EEF2))
                .shadow(color: Color(.sRGB, red: 10 / 255, green: 20 / 255, blue: 30 / 255, opacity: 0.85), radius: 6, y: 1)
                .frame(width: width, height: 40)
                .offset(y: PosterGlowCarouselMetrics.lockupY - 20)
            Text(PosterGlowCarouselContent.hero.title)
                .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.titleSize))
                .foregroundStyle(PosterGlowCarouselColor.ink)
                .frame(width: width - PosterGlowCarouselMetrics.side, height: 26, alignment: .leading)
                .offset(x: PosterGlowCarouselMetrics.side, y: PosterGlowCarouselMetrics.titleY - 13)
            Text(PosterGlowCarouselContent.hero.genres.joined(separator: " • "))
                .font(.custom(PosterGlowCarouselFont.regular, fixedSize: PosterGlowCarouselMetrics.genreSize))
                .foregroundStyle(PosterGlowCarouselColor.inkSoft)
                .frame(width: width - PosterGlowCarouselMetrics.genreX, height: 18, alignment: .leading)
                .offset(x: PosterGlowCarouselMetrics.genreX, y: PosterGlowCarouselMetrics.genreY - 9)
            HStack(spacing: PosterGlowCarouselMetrics.starPitch - PosterGlowCarouselMetrics.star) {
                ForEach(0..<5, id: \.self) { index in
                    PosterGlowCarouselStar(fill: max(0, min(1, PosterGlowCarouselMetrics.rating - Double(index))), size: PosterGlowCarouselMetrics.star)
                }
            }
            .frame(height: PosterGlowCarouselMetrics.star, alignment: .leading)
            .offset(x: PosterGlowCarouselMetrics.starsX, y: PosterGlowCarouselMetrics.starsY - PosterGlowCarouselMetrics.star / 2)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Rated \(PosterGlowCarouselMetrics.rating, specifier: "%.1f") out of 5")
            Button {} label: {
                ZStack {
                    Circle().fill(PosterGlowCarouselColor.ink)
                    PosterGlowCarouselIcon(kind: .play, size: 26, color: PosterGlowCarouselColor.color(0x111114))
                        .offset(x: 1.5)
                }
                .frame(width: PosterGlowCarouselMetrics.play, height: PosterGlowCarouselMetrics.play)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Play \(PosterGlowCarouselContent.hero.title)")
            .offset(x: PosterGlowCarouselMetrics.playX - PosterGlowCarouselMetrics.play / 2,
                    y: PosterGlowCarouselMetrics.playY - PosterGlowCarouselMetrics.play / 2)
        }
        .frame(width: width, height: PosterGlowCarouselMetrics.heroHeight, alignment: .topLeading)
    }
}

private struct PosterGlowCarouselStar: View {
    let fill: Double
    let size: Double

    var body: some View {
        Canvas { context, _ in
            // STAR is authored in the SVG's 24 × 24 viewBox. Scaling the context also
            // scales the foreground clip, so a 16.5 pt star has the same geometry as RN.
            context.scaleBy(x: size / 24, y: size / 24)
            var star = Path()
            // The same nine-point path as the SVG source (rather than an approximated star).
            let points = [CGPoint(x: 12, y: 1.6), CGPoint(x: 15.1, y: 8.2), CGPoint(x: 22.3, y: 9.1),
                          CGPoint(x: 17, y: 14), CGPoint(x: 18.4, y: 21.1), CGPoint(x: 12, y: 17.6),
                          CGPoint(x: 5.6, y: 21.1), CGPoint(x: 7, y: 14), CGPoint(x: 1.7, y: 9.1),
                          CGPoint(x: 8.9, y: 8.2)]
            star.move(to: points[0])
            for point in points.dropFirst() { star.addLine(to: point) }
            star.closeSubpath()
            context.fill(star, with: .color(PosterGlowCarouselColor.starOff))
            var foreground = context
            foreground.clip(to: Path(CGRect(x: 0, y: 0, width: 24 * fill, height: 24)))
            foreground.fill(star, with: .color(PosterGlowCarouselColor.star))
        }
        .frame(width: size, height: size)
    }
}

struct PosterGlowCarouselIcon: View {
    enum Kind {
        case back, gear, play, playCircle, home, search, seat, download, user
    }

    let kind: Kind
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: size / 24, y: size / 24)
            switch kind {
            case .back:
                var path = Path()
                path.move(to: CGPoint(x: 20, y: 12)); path.addLine(to: CGPoint(x: 5, y: 12))
                path.move(to: CGPoint(x: 11, y: 5)); path.addLine(to: CGPoint(x: 4, y: 12)); path.addLine(to: CGPoint(x: 11, y: 19))
                context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            case .gear:
                var path = Path()
                for index in 0..<(8 * 4) {
                    let angle = Double(index) / 32 * .pi * 2 - .pi / 2 + .pi / 32
                    let radius = index.isMultiple(of: 4) || index % 4 == 1 ? 11 : 8.4
                    let point = CGPoint(x: 12 + cos(angle) * radius, y: 12 + sin(angle) * radius)
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
                path.closeSubpath()
                context.fill(path, with: .color(color))
                context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
                context.fill(Path(ellipseIn: CGRect(x: 8.6, y: 8.6, width: 6.8, height: 6.8)), with: .color(PosterGlowCarouselColor.color(0x4A4F57)))
            case .play:
                var path = Path()
                path.move(to: CGPoint(x: 7, y: 4.6)); path.addCurve(to: CGPoint(x: 9.2, y: 3.4), control1: CGPoint(x: 7, y: 3.5), control2: CGPoint(x: 8.2, y: 2.8))
                path.addLine(to: CGPoint(x: 20.2, y: 10.3)); path.addCurve(to: CGPoint(x: 20.2, y: 12.8), control1: CGPoint(x: 21.1, y: 10.9), control2: CGPoint(x: 21.1, y: 12.2))
                path.addLine(to: CGPoint(x: 9.2, y: 19.7)); path.addCurve(to: CGPoint(x: 7, y: 18.6), control1: CGPoint(x: 8.2, y: 20.4), control2: CGPoint(x: 7, y: 19.7)); path.closeSubpath()
                context.fill(path, with: .color(color))
            case .playCircle:
                context.stroke(Path(ellipseIn: CGRect(x: 2, y: 2, width: 20, height: 20)), with: .color(color), style: StrokeStyle(lineWidth: 1.8))
                var path = Path(); path.move(to: CGPoint(x: 10, y: 8.2)); path.addLine(to: CGPoint(x: 16, y: 12)); path.addLine(to: CGPoint(x: 10, y: 15.8)); path.closeSubpath()
                context.fill(path, with: .color(color))
            case .home:
                var path = Path(); path.move(to: CGPoint(x: 3, y: 10.6)); path.addCurve(to: CGPoint(x: 3.8, y: 9), control1: CGPoint(x: 3, y: 10), control2: CGPoint(x: 3.3, y: 9.4)); path.addLine(to: CGPoint(x: 10.8, y: 3.4)); path.addCurve(to: CGPoint(x: 13.2, y: 3.4), control1: CGPoint(x: 11.4, y: 2.9), control2: CGPoint(x: 12.6, y: 2.9)); path.addLine(to: CGPoint(x: 20.2, y: 9)); path.addCurve(to: CGPoint(x: 21, y: 10.6), control1: CGPoint(x: 20.7, y: 9.4), control2: CGPoint(x: 21, y: 10)); path.addLine(to: CGPoint(x: 21, y: 19)); path.addCurve(to: CGPoint(x: 19, y: 21), control1: CGPoint(x: 21, y: 21), control2: CGPoint(x: 20, y: 21)); path.addLine(to: CGPoint(x: 15.4, y: 21)); path.addLine(to: CGPoint(x: 15.4, y: 15.6)); path.addCurve(to: CGPoint(x: 14, y: 14.2), control1: CGPoint(x: 15.4, y: 14.2), control2: CGPoint(x: 15.4, y: 14.2)); path.addLine(to: CGPoint(x: 12, y: 14.2)); path.addCurve(to: CGPoint(x: 10.6, y: 15.6), control1: CGPoint(x: 10.6, y: 14.2), control2: CGPoint(x: 10.6, y: 14.2)); path.addLine(to: CGPoint(x: 10.6, y: 21)); path.addLine(to: CGPoint(x: 5, y: 21)); path.addCurve(to: CGPoint(x: 3, y: 19), control1: CGPoint(x: 4, y: 21), control2: CGPoint(x: 3, y: 21)); path.closeSubpath(); context.fill(path, with: .color(color))
            case .search:
                context.stroke(Path(ellipseIn: CGRect(x: 3.4, y: 3.4, width: 14.4, height: 14.4)), with: .color(color), style: StrokeStyle(lineWidth: 2))
                var path = Path(); path.move(to: CGPoint(x: 16, y: 16)); path.addLine(to: CGPoint(x: 21, y: 21)); context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            case .seat:
                var path = Path(); path.move(to: CGPoint(x: 6, y: 11)); path.addLine(to: CGPoint(x: 6, y: 6.5)); path.addCurve(to: CGPoint(x: 8.5, y: 4), control1: CGPoint(x: 6, y: 5.4), control2: CGPoint(x: 7.1, y: 4)); path.addLine(to: CGPoint(x: 15.5, y: 4)); path.addCurve(to: CGPoint(x: 18, y: 6.5), control1: CGPoint(x: 16.9, y: 4), control2: CGPoint(x: 18, y: 5.4)); path.addLine(to: CGPoint(x: 18, y: 11)); path.move(to: CGPoint(x: 3.5, y: 11)); path.addLine(to: CGPoint(x: 6.5, y: 11)); path.addLine(to: CGPoint(x: 6.5, y: 15.5)); path.addLine(to: CGPoint(x: 17.5, y: 15.5)); path.addLine(to: CGPoint(x: 17.5, y: 11)); path.addLine(to: CGPoint(x: 20.5, y: 11)); path.addLine(to: CGPoint(x: 20.5, y: 17.5)); path.addLine(to: CGPoint(x: 3.5, y: 17.5)); path.closeSubpath(); context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round)); var legs = Path(); legs.move(to: CGPoint(x: 6, y: 17.5)); legs.addLine(to: CGPoint(x: 6, y: 20)); legs.move(to: CGPoint(x: 18, y: 17.5)); legs.addLine(to: CGPoint(x: 18, y: 20)); legs.move(to: CGPoint(x: 9, y: 13)); legs.addLine(to: CGPoint(x: 15, y: 13)); context.stroke(legs, with: .color(color), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            case .download:
                var path = Path(); path.move(to: CGPoint(x: 12, y: 3.5)); path.addLine(to: CGPoint(x: 12, y: 15.5)); path.move(to: CGPoint(x: 6.8, y: 10.5)); path.addLine(to: CGPoint(x: 12, y: 15.7)); path.addLine(to: CGPoint(x: 17.2, y: 10.5)); path.move(to: CGPoint(x: 5, y: 20.5)); path.addLine(to: CGPoint(x: 19, y: 20.5)); context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            case .user:
                context.fill(Path(ellipseIn: CGRect(x: 6.8, y: 2, width: 10.4, height: 10.4)), with: .color(color))
                var path = Path(); path.move(to: CGPoint(x: 2.5, y: 20.2)); path.addCurve(to: CGPoint(x: 12, y: 14.6), control1: CGPoint(x: 2.5, y: 16.9), control2: CGPoint(x: 6.8, y: 14.6)); path.addCurve(to: CGPoint(x: 21.5, y: 20.2), control1: CGPoint(x: 17.2, y: 14.6), control2: CGPoint(x: 21.5, y: 16.9)); path.addCurve(to: CGPoint(x: 19.7, y: 22), control1: CGPoint(x: 21.5, y: 22), control2: CGPoint(x: 20.7, y: 22)); path.addLine(to: CGPoint(x: 4.3, y: 22)); path.addCurve(to: CGPoint(x: 2.5, y: 20.2), control1: CGPoint(x: 3.3, y: 22), control2: CGPoint(x: 2.5, y: 22)); path.closeSubpath(); context.fill(path, with: .color(color))
            }
        }
        .frame(width: size, height: size)
    }
}
