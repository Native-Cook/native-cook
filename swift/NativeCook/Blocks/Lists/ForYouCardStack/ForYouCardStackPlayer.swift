import SwiftUI

private struct ForYouCardStackPassageWord: Identifiable {
    let id: String
    let text: String
    let progress: Double
}

private struct ForYouCardStackPassageLine: Identifiable {
    let id: Int
    let words: [ForYouCardStackPassageWord]
}

private let forYouCardStackLines: [ForYouCardStackPassageLine] = [
    "Colour is the most restless medium",
    "on paper. Trial, then error.",
    "Our memory for colour is poor",
    "for what is in front of us. We rarely",
    "see a colour alone. Every colour",
    "depends on what stands beside it",
].enumerated().map { lineIndex, text in
    let words = text.split(separator: " ").map(String.init)
    return ForYouCardStackPassageLine(
        id: lineIndex,
        words: words.enumerated().map { wordIndex, word in
            ForYouCardStackPassageWord(
                id: "\(lineIndex)-\(wordIndex)",
                text: word,
                progress: Double(lineIndex) + Double(wordIndex + 1) / Double(words.count)
            )
        }
    )
}

struct ForYouCardStackPlayerView: View {
    let size: CGSize
    let scene: ForYouCardStackScene
    let reducedMotion: Bool
    let onBack: () -> Void
    let onPlay: () -> Void

    var body: some View {
        let look = ForYouCardStackScript.look(open: scene.open, close: scene.close)
        let neighbourWidth = ForYouCardStackLayout.neighbourWidth
        let neighbourHeight = ForYouCardStackLayout.neighbourHeight
        let skipLeft = 201 - ForYouCardStackPlayerLayout.controlsSkipX - ForYouCardStackPlayerLayout.controlsIcon / 2
        let skipRight = 201 + ForYouCardStackPlayerLayout.controlsSkipX - ForYouCardStackPlayerLayout.controlsIcon / 2
        let bookTop = CGFloat(look.centerY) - ForYouCardStackLayout.pageHeight / 2
        let bookOffsetX = CGFloat(look.spineX)
        let bookScale = CGFloat(look.scale)

        return ZStack(alignment: .topLeading) {
            ForYouCardStackWaveShape(
                controlX: ForYouCardStackReadingLayout.waveControlX,
                controlY: ForYouCardStackReadingLayout.waveControlY,
                rightY: ForYouCardStackReadingLayout.waveRight,
                leftY: ForYouCardStackReadingLayout.waveLeft
            )
            .fill(ForYouCardStackPalette.reading)
            .frame(width: size.width, height: ForYouCardStackReadingLayout.waveLeft + 2, alignment: .topLeading)
            .offset(y: -(ForYouCardStackReadingLayout.waveLeft + 40) * CGFloat(1 - look.wave))
            .allowsHitTesting(false)

            ForYouCardStackPassageView(words: scene.words)
                .opacity(look.text)
                .allowsHitTesting(false)

            ForYouCardStackScaledCover(kind: .lines, scale: ForYouCardStackLayout.neighbourWidth / ForYouCardStackLayout.coverWidth)
                .frame(width: neighbourWidth, height: neighbourHeight, alignment: .topLeading)
                .position(
                    x: 22 - neighbourWidth + neighbourWidth / 2,
                    y: ForYouCardStackPlayerLayout.centerY
                )
                .opacity(look.neighbours)
                .accessibilityHidden(true)

            ForYouCardStackScaledCover(kind: .shapes, scale: ForYouCardStackLayout.neighbourWidth / ForYouCardStackLayout.coverWidth)
                .frame(width: neighbourWidth, height: neighbourHeight, alignment: .topLeading)
                .position(
                    x: 348 + neighbourWidth / 2,
                    y: ForYouCardStackPlayerLayout.centerY
                )
                .opacity(look.neighbours)
                .accessibilityHidden(true)

            ForYouCardStackBookView(look: look)
                .frame(width: ForYouCardStackLayout.pageWidth, height: ForYouCardStackLayout.pageHeight, alignment: .topLeading)
                .scaleEffect(bookScale, anchor: UnitPoint(x: 0, y: 0.5))
                .offset(x: bookOffsetX, y: bookTop)
                .accessibilityLabel(ForYouCardStackCoverTitles.values[.seeing] ?? "The Life of Colour")

            Text("Ines Marlowe")
                .font(.custom(ForYouCardStackFonts.regular, fixedSize: ForYouCardStackPlayerLayout.authorSize))
                .foregroundStyle(ForYouCardStackPalette.dim)
                .frame(width: size.width, height: 18, alignment: .top)
                .offset(y: ForYouCardStackPlayerLayout.authorTop + ForYouCardStackReadingLayout.titleShift * CGFloat(look.title))

            Text("Inner Light")
                .font(.custom(ForYouCardStackFonts.medium, fixedSize: ForYouCardStackPlayerLayout.titleSize))
                .multilineTextAlignment(.center)
                .foregroundStyle(ForYouCardStackPalette.white)
                .frame(width: size.width, height: ForYouCardStackPlayerLayout.titleLineHeight, alignment: .top)
                .offset(y: ForYouCardStackPlayerLayout.titleTop + ForYouCardStackReadingLayout.titleShift * CGFloat(look.title))
                .accessibilityLabel("Inner Light of Color")

            Text("of Color")
                .font(.custom(ForYouCardStackFonts.medium, fixedSize: ForYouCardStackPlayerLayout.titleSize))
                .multilineTextAlignment(.center)
                .foregroundStyle(ForYouCardStackPalette.white)
                .frame(width: size.width, height: ForYouCardStackPlayerLayout.titleLineHeight, alignment: .top)
                .offset(
                    y: ForYouCardStackPlayerLayout.titleTop
                        + ForYouCardStackPlayerLayout.titleLineHeight
                        + ForYouCardStackReadingLayout.titleShift * CGFloat(look.title)
                )
                .accessibilityHidden(true)

            Text("Chapter 1 of 25")
                .font(.custom(ForYouCardStackFonts.regular, fixedSize: ForYouCardStackPlayerLayout.chapterSize))
                .foregroundStyle(ForYouCardStackPalette.faint)
                .frame(width: size.width, height: 20, alignment: .top)
                .offset(y: ForYouCardStackPlayerLayout.chapterTop + ForYouCardStackReadingLayout.titleShift * CGFloat(look.title))
                .opacity(look.chapter)

            ForYouCardStackSkipIcon(forward: false)
                .stroke(ForYouCardStackPalette.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                .frame(width: ForYouCardStackPlayerLayout.controlsIcon, height: ForYouCardStackPlayerLayout.controlsIcon)
                .position(x: skipLeft + ForYouCardStackPlayerLayout.controlsIcon / 2, y: ForYouCardStackPlayerLayout.controlsY)
                .accessibilityLabel("Back 15 seconds")

            Button(action: onPlay) {
                ZStack {
                    Circle()
                        .fill(ForYouCardStackPalette.white)
                        .frame(width: ForYouCardStackPlayerLayout.controlsPlay, height: ForYouCardStackPlayerLayout.controlsPlay)
                    ForYouCardStackPlayGlyph()
                        .fill(ForYouCardStackPalette.player)
                        .frame(width: 16, height: 16)
                        .opacity(1 - scene.playing)
                    ForYouCardStackPauseGlyph()
                        .fill(ForYouCardStackPalette.player)
                        .frame(width: 16, height: 16)
                        .opacity(scene.playing)
                }
            }
            .buttonStyle(ForYouCardStackButtonStyle(reduceMotion: reducedMotion))
            .contentShape(Circle())
            .frame(width: ForYouCardStackPlayerLayout.controlsPlay, height: ForYouCardStackPlayerLayout.controlsPlay)
            .position(x: 201, y: ForYouCardStackPlayerLayout.controlsY)
            .accessibilityLabel("Play or pause")

            ForYouCardStackSkipIcon(forward: true)
                .stroke(ForYouCardStackPalette.white, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                .frame(width: ForYouCardStackPlayerLayout.controlsIcon, height: ForYouCardStackPlayerLayout.controlsIcon)
                .position(x: skipRight + ForYouCardStackPlayerLayout.controlsIcon / 2, y: ForYouCardStackPlayerLayout.controlsY)
                .accessibilityLabel("Forward 15 seconds")

            Rectangle()
                .fill(ForYouCardStackPalette.faint)
                .frame(width: max(0, size.width - ForYouCardStackPlayerLayout.progressLeft - ForYouCardStackPlayerLayout.progressRight), height: 1.5)
                .position(
                    x: ForYouCardStackPlayerLayout.progressLeft + max(0, size.width - ForYouCardStackPlayerLayout.progressLeft - ForYouCardStackPlayerLayout.progressRight) / 2,
                    y: ForYouCardStackPlayerLayout.progressY + 0.75
                )

            Rectangle()
                .fill(ForYouCardStackPalette.white)
                .frame(
                    width: max(0, size.width - ForYouCardStackPlayerLayout.progressLeft - ForYouCardStackPlayerLayout.progressRight) * CGFloat(ForYouCardStackPlayerLayout.progressPlayed),
                    height: 1.5,
                    alignment: .leading
                )
                .position(
                    x: ForYouCardStackPlayerLayout.progressLeft + max(0, size.width - ForYouCardStackPlayerLayout.progressLeft - ForYouCardStackPlayerLayout.progressRight) * CGFloat(ForYouCardStackPlayerLayout.progressPlayed) / 2,
                    y: ForYouCardStackPlayerLayout.progressY + 0.75
                )

            Text("00:00:10")
                .font(.custom(ForYouCardStackFonts.regular, fixedSize: ForYouCardStackPlayerLayout.timeSize))
                .foregroundStyle(ForYouCardStackPalette.dim)
                .frame(width: 70, height: 16, alignment: .leading)
                .position(x: ForYouCardStackPlayerLayout.progressLeft + 35, y: ForYouCardStackPlayerLayout.timesTop + 8)
            Text("01:42:21")
                .font(.custom(ForYouCardStackFonts.regular, fixedSize: ForYouCardStackPlayerLayout.timeSize))
                .foregroundStyle(ForYouCardStackPalette.dim)
                .frame(width: 70, height: 16, alignment: .trailing)
                .position(x: size.width - ForYouCardStackPlayerLayout.progressRight - 35, y: ForYouCardStackPlayerLayout.timesTop + 8)

            Button(action: onBack) {
                ForYouCardStackBackIcon()
                    .stroke(ForYouCardStackPalette.white, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                    .frame(width: ForYouCardStackPlayerLayout.backWidth, height: ForYouCardStackPlayerLayout.backHeight)
                    .frame(width: 42, height: 42)
            }
            .buttonStyle(ForYouCardStackButtonStyle(reduceMotion: reducedMotion))
            .contentShape(Rectangle())
            .position(
                x: ForYouCardStackPlayerLayout.backLeft + ForYouCardStackPlayerLayout.backWidth / 2,
                y: ForYouCardStackPlayerLayout.backTop + ForYouCardStackPlayerLayout.backHeight / 2
            )
            .accessibilityLabel("Back")

            ForYouCardStackBookIcon()
                .stroke(ForYouCardStackPalette.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                .frame(width: ForYouCardStackPlayerLayout.bookIconWidth, height: ForYouCardStackPlayerLayout.bookIconHeight)
                .position(
                    x: ForYouCardStackPlayerLayout.bookIconLeft + ForYouCardStackPlayerLayout.bookIconWidth / 2,
                    y: ForYouCardStackPlayerLayout.bookIconTop + ForYouCardStackPlayerLayout.bookIconHeight / 2
                )
                .accessibilityLabel("Book contents")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(ForYouCardStackPalette.player)
        .clipped()
    }
}

private struct ForYouCardStackPassageView: View {
    let words: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(forYouCardStackLines) { line in
                HStack(alignment: .top, spacing: ForYouCardStackReadingLayout.textSize * 0.25) {
                    ForEach(line.words) { word in
                        Text(word.text)
                            .font(.custom(ForYouCardStackFonts.reading, fixedSize: ForYouCardStackReadingLayout.textSize))
                            .foregroundStyle(ForYouCardStackPalette.white)
                            .opacity(word.progress <= words + 0.001 ? 1 : ForYouCardStackReadingLayout.textDim)
                    }
                }
                .frame(height: ForYouCardStackReadingLayout.textLineHeight, alignment: .top)
            }
        }
        .frame(width: 370, alignment: .leading)
        .offset(x: ForYouCardStackReadingLayout.textLeft, y: ForYouCardStackReadingLayout.textTop)
    }
}

private struct ForYouCardStackBookView: View {
    let look: ForYouCardStackLook

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForYouCardStackPageContent(content: AnyView(ForYouCardStackSpreadText()))
            ForYouCardStackLeaf(
                index: 2,
                progress: look.leaf2,
                front: AnyView(ForYouCardStackPageContent(content: AnyView(ForYouCardStackSpreadText()))),
                back: AnyView(ForYouCardStackPageContent(content: AnyView(ForYouCardStackSpreadArt())))
            )
            ForYouCardStackLeaf(
                index: 1,
                progress: look.leaf1,
                front: AnyView(
                    ForYouCardStackHalfImage(
                        image: ForYouCardStackAssets.image(named: "ForYouCardStack-page-landscape.jpg"),
                        offsetX: -ForYouCardStackLayout.coverWidth
                    )
                ),
                back: AnyView(
                    ForYouCardStackPageContent(
                        content: AnyView(
                            Image(uiImage: ForYouCardStackAssets.image(named: "ForYouCardStack-page-portrait.jpg"))
                                .resizable()
                                .scaledToFill()
                                .frame(width: ForYouCardStackLayout.coverWidth, height: ForYouCardStackLayout.coverHeight)
                                .clipped()
                        )
                    )
                )
            )
            ForYouCardStackLeaf(
                index: 0,
                progress: look.leaf0,
                front: AnyView(ForYouCardStackPageContent(content: AnyView(ForYouCardStackCoverView(kind: .seeing)))),
                back: AnyView(
                    ForYouCardStackHalfImage(
                        image: ForYouCardStackAssets.image(named: "ForYouCardStack-page-landscape.jpg"),
                        offsetX: 0
                    )
                )
            )
        }
        .frame(width: ForYouCardStackLayout.pageWidth, height: ForYouCardStackLayout.pageHeight, alignment: .topLeading)
    }
}

private struct ForYouCardStackPageContent: View {
    let content: AnyView

    var body: some View {
        content
            .scaleEffect(ForYouCardStackLayout.pageScale, anchor: .topLeading)
            .frame(width: ForYouCardStackLayout.pageWidth, height: ForYouCardStackLayout.pageHeight, alignment: .topLeading)
            .clipped()
    }
}

private struct ForYouCardStackLeaf: View {
    let index: Int
    let progress: Double
    let front: AnyView
    let back: AnyView

    var body: some View {
        let moving = progress > 0 && progress < 1
        return ZStack(alignment: .topLeading) {
            front
                .opacity(progress < 0.5 ? 1 : 0)
            back
                .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0), anchor: .leading, perspective: 0)
                .opacity(progress >= 0.5 ? 1 : 0)
        }
        .frame(width: ForYouCardStackLayout.pageWidth, height: ForYouCardStackLayout.pageHeight, alignment: .topLeading)
        .clipped()
        .rotation3DEffect(
            .degrees(-180 * progress),
            axis: (x: 0, y: 1, z: 0),
            anchor: .leading,
            anchorZ: 0,
            perspective: 1 / 1200
        )
        .zIndex(moving ? 30 : progress >= 0.5 ? Double(10 + index) : Double(10 - index))
    }
}

private struct ForYouCardStackHalfImage: View {
    let image: UIImage
    let offsetX: CGFloat

    var body: some View {
        Image(uiImage: image)
            .resizable()
            .scaledToFill()
            .frame(width: ForYouCardStackLayout.coverWidth * 2, height: ForYouCardStackLayout.coverHeight)
            .offset(x: offsetX)
            .frame(width: ForYouCardStackLayout.coverWidth, height: ForYouCardStackLayout.coverHeight, alignment: .topLeading)
            .clipped()
            .scaleEffect(ForYouCardStackLayout.pageScale, anchor: .topLeading)
            .frame(width: ForYouCardStackLayout.pageWidth, height: ForYouCardStackLayout.pageHeight, alignment: .topLeading)
    }
}

private struct ForYouCardStackWaveShape: Shape {
    let controlX: CGFloat
    let controlY: CGFloat
    let rightY: CGFloat
    let leftY: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: rightY))
        path.addQuadCurve(to: CGPoint(x: 0, y: leftY), control: CGPoint(x: controlX, y: controlY))
        path.closeSubpath()
        return path
    }
}

private struct ForYouCardStackSkipIcon: Shape {
    let forward: Bool

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let scaleX = rect.width / 24
        let scaleY = rect.height / 24
        let center = CGPoint(x: 12 * scaleX, y: 12 * scaleY)
        let radius = 6.5 * min(scaleX, scaleY)
        if forward {
            path.addArc(center: center, radius: radius, startAngle: .degrees(0), endAngle: .degrees(313.75), clockwise: false)
            path.move(to: CGPoint(x: 16.7 * scaleX, y: 3.8 * scaleY))
            path.addLine(to: CGPoint(x: 16.7 * scaleX, y: 7.4 * scaleY))
            path.addLine(to: CGPoint(x: 13.1 * scaleX, y: 7.4 * scaleY))
        } else {
            path.addArc(center: center, radius: radius, startAngle: .degrees(180), endAngle: .degrees(-133.75), clockwise: true)
            path.move(to: CGPoint(x: 7.3 * scaleX, y: 3.8 * scaleY))
            path.addLine(to: CGPoint(x: 7.3 * scaleX, y: 7.4 * scaleY))
            path.addLine(to: CGPoint(x: 10.9 * scaleX, y: 7.4 * scaleY))
        }
        return path
    }
}

private struct ForYouCardStackPlayGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 4.8, y: 2.8))
        path.addLine(to: CGPoint(x: 4.8, y: 13.2))
        path.addLine(to: CGPoint(x: 13, y: 8))
        path.closeSubpath()
        return path
    }
}

private struct ForYouCardStackPauseGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRoundedRect(in: CGRect(x: 4.4, y: 3, width: 2, height: 10), cornerSize: CGSize(width: 1, height: 1))
        path.addRoundedRect(in: CGRect(x: 9.6, y: 3, width: 2, height: 10), cornerSize: CGSize(width: 1, height: 1))
        return path
    }
}

private struct ForYouCardStackBackIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.maxX - 0.8, y: rect.midY))
        path.addLine(to: CGPoint(x: 1.2, y: rect.midY))
        path.move(to: CGPoint(x: 6.4, y: 1))
        path.addLine(to: CGPoint(x: 1, y: rect.midY))
        path.addLine(to: CGPoint(x: 6.4, y: rect.maxY - 1))
        return path
    }
}

private struct ForYouCardStackBookIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let midX = rect.midX
        path.move(to: CGPoint(x: midX, y: 2.4))
        path.addCurve(to: CGPoint(x: 0.8, y: 1.1), control1: CGPoint(x: 6.6, y: 1.1), control2: CGPoint(x: 4, y: 0.8))
        path.addLine(to: CGPoint(x: 0.8, y: 12.7))
        path.addCurve(to: CGPoint(x: midX, y: 14), control1: CGPoint(x: 4, y: 12.7), control2: CGPoint(x: 6.6, y: 12.7))
        path.addCurve(to: CGPoint(x: rect.maxX - 0.8, y: 12.7), control1: CGPoint(x: rect.maxX - 4, y: 12.7), control2: CGPoint(x: rect.maxX - 6.6, y: 12.7))
        path.addLine(to: CGPoint(x: rect.maxX - 0.8, y: 1.1))
        path.addCurve(to: CGPoint(x: midX, y: 2.4), control1: CGPoint(x: rect.maxX - 4, y: 0.8), control2: CGPoint(x: rect.maxX - 6.6, y: 1.1))
        path.move(to: CGPoint(x: midX, y: 2.4))
        path.addLine(to: CGPoint(x: midX, y: 14))
        return path
    }
}
