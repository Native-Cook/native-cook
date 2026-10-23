import SwiftUI

private typealias Theme = PaddockWidgetBentoTheme
private typealias Motion = PaddockWidgetBentoMotion

enum PaddockWidgetBentoDestination: String, CaseIterable, Identifiable {
    case laps, reflex, homeCards, pitcast, stats, glossary, education, widgetStudio, guide, closeGuide, banner
    case home, calendar, results, circuits, customize
    var id: String { rawValue }
}

/// One line of text placed by its baseline, like the pin's measurements (`x`, `baseline` card-local points).
struct PaddockWidgetBentoLine: View {
    let text: String
    let x: Double
    let baseline: Double
    let size: Double
    let face: PaddockWidgetBentoFont.Face
    let color: Color
    var tracking = 0.0
    var opacity = 1.0
    var serifItalic = false
    let scale: CGFloat

    private var serifFont: UIFont {
        let base = UIFont.systemFont(ofSize: size * scale, weight: .semibold)
        let serif = base.fontDescriptor.withDesign(.serif) ?? base.fontDescriptor
        let italic = serif.withSymbolicTraits(.traitItalic) ?? serif
        return UIFont(descriptor: italic, size: size * scale)
    }

    /// Distance from the top of the text's line box to its baseline.
    private var ascent: CGFloat {
        serifItalic ? serifFont.ascender : CTFontGetAscent(PaddockWidgetBentoFont.ct(face, size: size * scale))
    }

    var body: some View {
        Text(text)
            .font(serifItalic ? Font(serifFont) : PaddockWidgetBentoFont.font(face, size: size * scale))
            .tracking(tracking * scale)
            .foregroundStyle(color)
            .opacity(opacity)
            .lineLimit(1)
            .fixedSize()
            .offset(x: x * scale, y: baseline * scale - ascent)
    }
}

/// A symbol centred at (cx, cy) in a `size` square, drawn like SwiftUI's `.font(.system(size:))` image.
struct PaddockWidgetBentoMark: View {
    let symbol: String
    let cx: Double
    let cy: Double
    let size: Double
    let color: Color
    var weight: Font.Weight = .semibold
    let scale: CGFloat

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * scale, weight: weight))
            .foregroundStyle(color)
            .frame(width: size * scale, height: size * scale)
            .offset(x: (cx - size / 2) * scale, y: (cy - size / 2) * scale)
            .accessibilityHidden(true)
    }
}

/// Eyebrow + title, the pin's two-line tile label (12.5 SemiBold over 16.5 Bold, slightly tightened).
private struct PaddockWidgetBentoTileLabel: View {
    let eyebrow: String
    let title: String
    let x: Double
    let color: Color
    let scale: CGFloat

    var body: some View {
        PaddockWidgetBentoLine(text: eyebrow, x: x, baseline: Theme.Layout.eyebrow, size: Theme.TypeSize.eyebrow, face: .semibold,
                               color: color, tracking: Theme.TypeSize.eyebrowTracking, scale: scale)
        PaddockWidgetBentoLine(text: title, x: x, baseline: Theme.Layout.title, size: Theme.TypeSize.tileTitle, face: .bold,
                               color: color, tracking: Theme.TypeSize.tileTitleTracking, scale: scale)
    }
}

/// Static quick-card content. Reordering, hit testing and lifecycle belong to the parent. The violet card is lettering
/// with a badge, the orange one a label with a controller chip (it gives way to the drag handle while arranging), the
/// yellow one a mark and a label.
struct PaddockWidgetBentoCardFace: View {
    let id: PaddockWidgetBentoCardID
    let scale: CGFloat
    var handles = 0.0

    private var fill: Color { [Theme.Colors.violet, Theme.Colors.orange, Theme.Colors.yellow][id.index] }

    var body: some View {
        let q = Theme.Layout.quick
        let mid = q.height / 2
        Color.clear.frame(width: q.width * scale, height: q.height * scale)
        .background { RoundedRectangle(cornerRadius: Theme.Radius.quickCard * scale, style: .continuous).fill(fill) }
        .overlay(alignment: .topLeading) {
        ZStack(alignment: .topLeading) {
            switch id {
            case .laps:
                let badge = Theme.Layout.violetBadge
                Capsule()
                    .strokeBorder(.white, lineWidth: badge.stroke * scale)
                    .frame(width: badge.width * scale, height: badge.height * scale)
                    .overlay {
                        Text("TN").font(PaddockWidgetBentoFont.font(.black, size: Theme.TypeSize.badge * scale)).tracking(-0.4 * scale).foregroundStyle(.white)
                    }
                    .offset(x: (badge.centerX - badge.width / 2) * scale, y: (mid - badge.height / 2) * scale)
                PaddockWidgetBentoLine(text: "every lap", x: Theme.Layout.violetX, baseline: Theme.Layout.violet[0], size: Theme.TypeSize.violet,
                                       face: .extraBold, color: .white, tracking: Theme.TypeSize.violetTracking, scale: scale)
                PaddockWidgetBentoLine(text: "counts", x: Theme.Layout.violetX, baseline: Theme.Layout.violet[1], size: Theme.TypeSize.violet,
                                       face: .extraBold, color: .white, tracking: Theme.TypeSize.violetTracking, scale: scale)
            case .reflex:
                PaddockWidgetBentoTileLabel(eyebrow: "Race day", title: "Reflex test", x: Theme.Space.tileInset, color: .white, scale: scale)
                let chip = Theme.Layout.orangeChip
                RoundedRectangle(cornerRadius: Theme.Radius.chip * scale, style: .continuous).fill(.white)
                    .frame(width: chip.width * scale, height: chip.height * scale)
                    .overlay {
                        Image(systemName: "gamecontroller.fill").font(.system(size: Theme.TypeSize.chipIcon * scale, weight: .bold)).foregroundStyle(Theme.Colors.orange)
                    }
                    .opacity(1 - handles)
                    .offset(x: (q.width - chip.trailing - chip.width) * scale, y: (mid - chip.height / 2) * scale)
            case .homeCards:
                PaddockWidgetBentoMark(symbol: "square.grid.2x2.fill", cx: Theme.Space.tileMarkCenter, cy: mid, size: Theme.TypeSize.cardIcon,
                                       color: .black, weight: .bold, scale: scale)
                PaddockWidgetBentoTileLabel(eyebrow: "custom", title: "home cards", x: Theme.Space.tileTextAfterMark, color: .black, scale: scale)
            }
            if handles > 0.001 {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: Theme.TypeSize.handle * scale, weight: .bold))
                    .foregroundStyle(id == .homeCards ? Theme.Colors.ink55Black : Theme.Colors.ink75White)
                    .opacity(handles)
                    .frame(height: q.height * scale)
                    .offset(x: (q.width - Theme.Space.handleTrailing - Theme.TypeSize.handle) * scale)
                    .accessibilityHidden(true)
            }
        }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Theme.Copy.cardLabels[id.index])
    }
}

/// Press-in is quick feedback; the release settles a little slower (asymmetric, like a real button).
struct PaddockWidgetBentoPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? 0.10 : 0.18), value: configuration.isPressed)
    }
}

/// A bento tile: its pit-stop pose, and a button over the face. `extras` sit above the button (own touch targets).
private struct PaddockWidgetBentoTile<Face: View, Extras: View>: View {
    let index: Int
    let rect: CGRect
    let corner: Double
    var fill: Color?
    let scale: CGFloat
    let pit: Double
    var shake = 0.0
    let label: String
    var clips = false
    let action: () -> Void
    let face: Face
    let extras: Extras

    init(index: Int, rect: CGRect, corner: Double, fill: Color? = nil, scale: CGFloat, pit: Double, shake: Double = 0, label: String, clips: Bool = false,
         action: @escaping () -> Void, @ViewBuilder face: () -> Face, @ViewBuilder extras: () -> Extras) {
        self.index = index; self.rect = rect; self.corner = corner; self.fill = fill; self.scale = scale; self.pit = pit; self.shake = shake
        self.label = label; self.clips = clips; self.action = action; self.face = face(); self.extras = extras()
    }

    var body: some View {
        let s = scale
        let pose = Motion.pitTile(index, pit)
        let shape = RoundedRectangle(cornerRadius: corner * s, style: .continuous)
        ZStack(alignment: .topLeading) {
            Button(action: action) {
                Color.clear
                    .frame(width: rect.width * s, height: rect.height * s)
                    .background { if let fill { shape.fill(fill) } }
                    .overlay(alignment: .topLeading) {
                        ZStack(alignment: .topLeading) { face }
                    }
                .modifier(PaddockWidgetBentoClip(enabled: clips, corner: corner * s))
                .contentShape(shape)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
            }
            .buttonStyle(PaddockWidgetBentoPressStyle())
            extras
        }
        .frame(width: rect.width * s, height: rect.height * s, alignment: .topLeading)
        .scaleEffect(x: pose.sx, y: pose.sy)
        .rotationEffect(.degrees(pose.rotate))
        .offset(x: (rect.minX + pose.x + shake) * s, y: (rect.minY + pose.y) * s)
    }
}

private struct PaddockWidgetBentoClip: ViewModifier {
    let enabled: Bool
    let corner: Double
    @ViewBuilder func body(content: Content) -> some View {
        if enabled { content.clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous)) } else { content }
    }
}

/// The stationary bento tiles. The three reorderable quick cards are drawn by the parent.
struct PaddockWidgetBentoStaticGrid<Overlay: View>: View {
    let scale: CGFloat
    let guideVisible: Bool
    let pit: Double
    let peel: PaddockWidgetBentoMotion.PeelState
    let onAction: (PaddockWidgetBentoDestination) -> Void
    let heroOverlay: Overlay

    init(scale: CGFloat, guideVisible: Bool, pit: Double, peel: PaddockWidgetBentoMotion.PeelState,
         onAction: @escaping (PaddockWidgetBentoDestination) -> Void, @ViewBuilder heroOverlay: () -> Overlay) {
        self.scale = scale; self.guideVisible = guideVisible; self.pit = pit; self.peel = peel
        self.onAction = onAction; self.heroOverlay = heroOverlay()
    }

    var body: some View {
        let s = scale
        let l = Theme.Layout.self
        ZStack(alignment: .topLeading) {
            pitcast(s)
            stats(s)
            actionTile(index: Motion.Tile.glossary, rect: l.glossary, fill: Theme.Colors.green, destination: .glossary, label: "Race glossary",
                       symbol: "books.vertical.fill", symbolWeight: .bold, ink: .black, titleText: "Glossary", s: s)
            actionTile(index: Motion.Tile.education, rect: l.education, fill: Theme.Colors.blue, destination: .education, label: "Race education",
                       symbol: "circle.lefthalf.filled", symbolWeight: .semibold, ink: .white, titleText: "Education", s: s)
            hero(s)
            if guideVisible { guide(s) }
            banner(s)
        }
        .frame(width: Theme.referenceWidth * s, height: l.gridHeight * s, alignment: .topLeading)
    }

    /// Width in points of "pit" in Montserrat Black 32 with its tracking, where "cast" starts.
    private func pitWidth(_ s: CGFloat) -> Double {
        let font = unsafeBitCast(PaddockWidgetBentoFont.ct(.black, size: Theme.TypeSize.pitcastTitle * s), to: UIFont.self)
        return Double(("pit" as NSString).size(withAttributes: [.font: font, .kern: -0.6 * s]).width / s)
    }

    private func pitcast(_ s: CGFloat) -> some View {
        let l = Theme.Layout.self
        let b = l
        return PaddockWidgetBentoTile(index: Motion.Tile.pitcast, rect: l.pitcast, corner: Theme.Radius.card, fill: Theme.Colors.cream, scale: s, pit: pit,
                                      label: Theme.Copy.pitcastLabel, action: { onAction(.pitcast) }) {
            let inset = Theme.Space.pitcastInset
            RoundedRectangle(cornerRadius: Theme.Radius.mic * s, style: .continuous).fill(Theme.Colors.orange)
                .frame(width: l.mic * s, height: l.mic * s)
                .overlay { Image(systemName: "mic.fill").font(.system(size: Theme.TypeSize.pitcastIcon * s, weight: .semibold)).foregroundStyle(Theme.Colors.micInk) }
                .offset(x: inset * s, y: inset * s)
            Circle().fill(.black).frame(width: l.arrow * s, height: l.arrow * s)
                .overlay { Image(systemName: "arrow.up.right").font(.system(size: Theme.TypeSize.pitcastArrow * s, weight: .heavy)).foregroundStyle(.white) }
                .offset(x: (l.pitcast.width - Theme.Space.pitcastArrowInset - l.arrow) * s, y: Theme.Space.pitcastArrowInset * s)
            PaddockWidgetBentoLine(text: "pit", x: inset, baseline: b.pitcastTitle, size: Theme.TypeSize.pitcastTitle, face: .black, color: .black, tracking: -0.6, scale: s)
            PaddockWidgetBentoLine(text: "cast", x: inset + pitWidth(s), baseline: b.pitcastTitle, size: Theme.TypeSize.pitcastTitle, face: .black, color: .black,
                                   tracking: -0.8, serifItalic: true, scale: s)
            PaddockWidgetBentoLine(text: "Every Friday at 6pm", x: inset, baseline: b.pitcastSubtitle, size: Theme.TypeSize.pitcastSubtitle, face: .medium, color: .black, scale: s)
            PaddockWidgetBentoLine(text: "Alex Vale / Sam Ives", x: inset, baseline: b.pitcastNames, size: Theme.TypeSize.pitcastNames, face: .medium, color: .black,
                                   opacity: Theme.pitcastNamesAlpha, scale: s)
            ForEach(Array(["headphones.circle.fill", "play.rectangle.fill", "antenna.radiowaves.left.and.right"].enumerated()), id: \.offset) { index, symbol in
                PaddockWidgetBentoMark(symbol: symbol, cx: l.glyphX[index], cy: l.glyphRow, size: l.glyph, color: .black, scale: s)
            }
        } extras: { EmptyView() }
    }

    private func stats(_ s: CGFloat) -> some View {
        let l = Theme.Layout.self
        return PaddockWidgetBentoTile(index: Motion.Tile.stats, rect: l.stats, corner: Theme.Radius.card, scale: s, pit: pit,
                                      label: Theme.Copy.statsLabel, clips: true, action: { onAction(.stats) }) {
            LinearGradient(colors: [Theme.Colors.statsGlow, Theme.Colors.statsShade], startPoint: UnitPoint(x: 0.12, y: 0.85), endPoint: UnitPoint(x: 1, y: 0.15))
            Image(uiImage: PaddockWidgetBentoAssets.statsStack).resizable().scaledToFit()
                .frame(width: l.statsArt.width * s, height: l.statsArt.height * s)
                .offset(x: l.statsArt.minX * s, y: l.statsArt.minY * s)
            Image(systemName: "doc.text.fill").font(.system(size: Theme.TypeSize.statsIcon * s)).foregroundStyle(.white)
                .frame(width: l.statsIcon.width * s, height: l.statsIcon.height * s)
                .offset(x: l.statsIcon.minX * s, y: l.statsIcon.minY * s)
            PaddockWidgetBentoLine(text: "Turn Nine", x: Theme.Space.statsTextX, baseline: l.statsEyebrow, size: Theme.TypeSize.statsEyebrow, face: .bold, color: .white, scale: s)
            PaddockWidgetBentoLine(text: "Stats", x: Theme.Space.statsTextX, baseline: l.statsWord, size: Theme.TypeSize.statsWord, face: .bold, color: .white, scale: s)
            PaddockWidgetBentoLine(text: "Stats, records & stories", x: Theme.Space.statsCaptionX, baseline: l.caption[0], size: Theme.TypeSize.caption, face: .medium, color: .white, scale: s)
            PaddockWidgetBentoLine(text: "from Turn Nine", x: Theme.Space.statsCaptionX, baseline: l.caption[1], size: Theme.TypeSize.caption, face: .medium, color: .white, scale: s)
        } extras: { EmptyView() }
    }

    private func actionTile(index: Int, rect: CGRect, fill: Color, destination: PaddockWidgetBentoDestination, label: String, symbol: String,
                            symbolWeight: Font.Weight, ink: Color, titleText: String, s: CGFloat) -> some View {
        PaddockWidgetBentoTile(index: index, rect: rect, corner: Theme.Radius.quickCard, fill: fill, scale: s, pit: pit, label: label, action: { onAction(destination) }) {
            PaddockWidgetBentoMark(symbol: symbol, cx: Theme.Space.tileMarkCenter, cy: rect.height / 2, size: Theme.TypeSize.tileIcon,
                                   color: index == Motion.Tile.education ? Theme.Colors.creamInk : .black, weight: symbolWeight, scale: s)
            PaddockWidgetBentoTileLabel(eyebrow: "Turn Nine", title: titleText, x: Theme.Space.tileTextAfterMark, color: ink, scale: s)
        } extras: { EmptyView() }
    }

    private func hero(_ s: CGFloat) -> some View {
        let l = Theme.Layout.self
        return PaddockWidgetBentoTile(index: Motion.Tile.hero, rect: l.hero, corner: Theme.Radius.card, fill: Theme.Colors.red, scale: s, pit: pit,
                                      shake: Motion.slapPose(peel.slap).shake, label: Theme.Copy.heroLabel, action: { onAction(.widgetStudio) }) {
            PaddockWidgetBentoSticker(scale: s, peel: peel)
            Circle().fill(.white)
                .overlay { Circle().stroke(Theme.Colors.sealStroke, lineWidth: 1) }
                .frame(width: l.sealSize * s, height: l.sealSize * s)
                .overlay { Text("TN").font(PaddockWidgetBentoFont.font(.black, size: Theme.TypeSize.heroSeal * s)).tracking(-0.4 * s).foregroundStyle(Theme.Colors.red) }
                .offset(x: (l.sealCenterX - l.sealSize / 2) * s, y: (l.sealCenterY - l.sealSize / 2) * s)
        } extras: {
            heroOverlay
                .frame(width: l.peelSpot.width * s, height: l.peelSpot.height * s)
                .offset(x: l.peelSpot.minX * s, y: l.peelSpot.minY * s)
        }
    }

    private func guide(_ s: CGFloat) -> some View {
        let l = Theme.Layout.self
        return PaddockWidgetBentoTile(index: Motion.Tile.guide, rect: l.guide, corner: Theme.Radius.card, fill: Theme.Colors.guideYellow, scale: s, pit: pit,
                                      label: "\(Theme.Copy.guideTitle) \(Theme.Copy.guideHelper.joined(separator: " "))", action: { onAction(.guide) }) {
            PaddockWidgetBentoLine(text: Theme.Copy.guideTitle, x: Theme.Space.guideInset, baseline: l.guideTitle, size: Theme.TypeSize.guideTitle, face: .extraBold, color: .black, scale: s)
            PaddockWidgetBentoLine(text: Theme.Copy.guideHelper[0], x: Theme.Space.guideInset, baseline: l.guideHelper[0], size: Theme.TypeSize.guideHelper, face: .medium, color: .black, scale: s)
            PaddockWidgetBentoLine(text: Theme.Copy.guideHelper[1], x: Theme.Space.guideInset, baseline: l.guideHelper[1], size: Theme.TypeSize.guideHelper, face: .medium, color: .black, scale: s)
        } extras: {
            Button { onAction(.closeGuide) } label: {
                Circle().fill(Theme.Colors.closeFill).frame(width: l.closeSize * s, height: l.closeSize * s)
                    .overlay { Image(systemName: "xmark").font(.system(size: Theme.TypeSize.guideClose * s, weight: .black)).foregroundStyle(Theme.Colors.guideYellow) }
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close arrangement guide")
            .offset(x: (l.guide.width - Theme.Space.guideCloseInset - l.closeSize / 2) * s - 22, y: l.closeCenterY * s - 22)
        }
    }

    private func banner(_ s: CGFloat) -> some View {
        let l = Theme.Layout.self
        return PaddockWidgetBentoTile(index: Motion.Tile.banner, rect: l.banner, corner: Theme.Radius.card, scale: s, pit: pit,
                                      label: Theme.Copy.bannerLabel, clips: true, action: { onAction(.banner) }) {
            Image(uiImage: PaddockWidgetBentoAssets.bannerPhoto).resizable().scaledToFill()
                .frame(width: l.banner.width * s, height: l.banner.height * s).clipped()
            LinearGradient(colors: [Theme.Colors.bannerShade, Theme.Colors.bannerGlow, Theme.Colors.bannerClear],
                           startPoint: UnitPoint(x: 0, y: 1), endPoint: UnitPoint(x: 1, y: 0))
            VStack(alignment: .leading, spacing: Theme.Space.bannerGap * s) {
                Text("Your race-day space").font(PaddockWidgetBentoFont.font(.extraBold, size: Theme.TypeSize.bannerTitle * s)).tracking(-0.6 * s).lineLimit(2)
                Text("Build a dashboard that feels like yours.").font(PaddockWidgetBentoFont.font(.medium, size: Theme.TypeSize.bannerSubtitle * s)).lineLimit(2)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, Theme.Space.bannerPad * s)
            .frame(width: l.banner.width * s, alignment: .leading)
            .offset(y: Theme.Space.bannerTop * s)
        } extras: { EmptyView() }
    }
}
