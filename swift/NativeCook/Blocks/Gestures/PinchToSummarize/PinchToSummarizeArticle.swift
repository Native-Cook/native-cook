import SwiftUI
import UIKit

/// The full article beneath the pinch transformation. Geometry and motion are supplied by the root,
/// so this layer never owns a timer or an interaction state.
struct PinchToSummarizeArticle: View {
    let stage: PinchToSummarizeStage
    let insetBottom: CGFloat
    let snapshot: PinchToSummarizeSnapshot
    let scrollLocked: Bool
    let onSummarize: () -> Void
    let onShowArticle: () -> Void

    private var isReading: Bool {
        snapshot.mode == .reading
    }

    var body: some View {
        let breath = PinchToSummarizeGeometry.breathing(
            think: snapshot.think,
            clock: snapshot.clock,
            amplitude: CGFloat(PinchToSummarizeConstants.Timing.breathe),
            period: CGFloat(PinchToSummarizeConstants.Timing.breathePeriod)
        )
        let page = PinchToSummarizeGeometry.pageFrame(
            stage: stage,
            p: snapshot.p,
            lift: snapshot.lift,
            driftX: snapshot.driftX,
            driftY: snapshot.driftY,
            breath: breath
        )

        ZStack(alignment: .topLeading) {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 0) {
                    PinchToSummarizeArticleMasthead()
                        .frame(height: PinchToSummarizeTheme.Layout.mastheadHeight)

                    PinchToSummarizeArticlePanel(width: stage.width - PinchToSummarizeTheme.Layout.panelInset * 2)

                    PinchToSummarizeArticleBody()
                }
                .padding(.top, stage.insetTop)
                .padding(.bottom, PinchToSummarizeTheme.Layout.toolbarHeight + insetBottom + 32)
            }
            .scrollDisabled(scrollLocked)
            .frame(width: stage.width, height: stage.height, alignment: .topLeading)
            .offset(y: -page.trim)

            PinchToSummarizeArticleToolbar(
                insetBottom: insetBottom,
                onSummarize: onSummarize
            )
            .opacity(1 - PinchToSummarizeGeometry.clamp01(snapshot.p / CGFloat(PinchToSummarizeConstants.Timing.toolbarFadeSpan)))
            .allowsHitTesting(isReading)
        }
        .overlay {
            if !isReading {
                Button("Show the full article", action: onShowArticle)
                    .labelStyle(.iconOnly)
                    .accessibilityLabel("Show the full article")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .opacity(0.001)
            }
        }
        .frame(width: stage.width, height: page.clipHeight, alignment: .topLeading)
        .background(PinchToSummarizeTheme.Colors.page)
        .clipShape(.rect(cornerRadius: page.radius / max(page.scale, 0.001)))
        .scaleEffect(page.scale, anchor: .topLeading)
        .offset(x: page.x, y: page.y)
        .opacity(snapshot.pageAlpha)
        .accessibilityElement(children: isReading ? .contain : .ignore)
        .accessibilityAction(named: "Summarize article", onSummarize)
        .contentShape(Rectangle())
    }
}

private struct PinchToSummarizeArticleMasthead: View {
    var body: some View {
        HStack(spacing: 0) {
            HStack(spacing: 8) {
                PinchToSummarizeMeridianMark()
                    .frame(width: 34, height: 22)
                    .accessibilityHidden(true)
                Text(PinchToSummarizeTheme.Copy.wordmark)
                    .pinchToSummarizeText(PinchToSummarizeTheme.Typography.wordmark)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Meridian")

            Spacer(minLength: 0)

            PinchToSummarizeIcon(name: "person", size: 14, color: PinchToSummarizeTheme.Colors.inkSoft)
                .frame(width: 28, height: 28)
                .background(PinchToSummarizeTheme.Colors.toolbar, in: Circle())
                .accessibilityHidden(true)
        }
        .padding(.horizontal, PinchToSummarizeTheme.Layout.gutter)
    }
}

private struct PinchToSummarizeMeridianMark: View {
    var body: some View {
        Canvas { context, size in
            let diameter: CGFloat = 17
            let y = (size.height - diameter) / 2
            let stroke = StrokeStyle(lineWidth: 3.4)
            context.stroke(
                Path(ellipseIn: CGRect(x: 2.5, y: y, width: diameter, height: diameter)),
                with: .color(PinchToSummarizeTheme.Colors.ink),
                style: stroke
            )
            context.stroke(
                Path(ellipseIn: CGRect(x: 14.5, y: y, width: diameter, height: diameter)),
                with: .color(PinchToSummarizeTheme.Colors.ink),
                style: stroke
            )
            var seam = Path()
            seam.move(to: CGPoint(x: 17, y: 4.6))
            seam.addCurve(
                to: CGPoint(x: 17, y: 17.4),
                control1: CGPoint(x: 21.5, y: 7.5),
                control2: CGPoint(x: 21.5, y: 14.5)
            )
            context.stroke(seam, with: .color(PinchToSummarizeTheme.Colors.page), style: StrokeStyle(lineWidth: 1.6))
        }
    }
}

private struct PinchToSummarizeArticlePanel: View {
    let width: CGFloat

    var body: some View {
        VStack(spacing: 0) {
            Text(PinchToSummarizeTheme.Copy.kicker)
                .pinchToSummarizeText(PinchToSummarizeTheme.Typography.pill)
                .frame(height: PinchToSummarizeTheme.Layout.pillHeight)
                .padding(.horizontal, PinchToSummarizeTheme.Layout.pillPadX)
                .background(PinchToSummarizeTheme.Colors.pill, in: .rect(cornerRadius: 8))

            PinchToSummarizeArticleLabel(
                text: PinchToSummarizeTheme.Copy.headline,
                style: PinchToSummarizeTheme.Typography.headline,
                alignment: .center
            )
            .frame(maxWidth: .infinity)
                .padding(.top, PinchToSummarizeTheme.Layout.pillGap)
                .padding(.horizontal, PinchToSummarizeTheme.Layout.panelPadX)

            PinchToSummarizeArticleLabel(
                text: PinchToSummarizeTheme.Copy.dek,
                style: PinchToSummarizeTheme.Typography.dek,
                alignment: .center
            )
            .frame(maxWidth: .infinity)
                .padding(.top, PinchToSummarizeTheme.Layout.dekGap)
                .padding(.horizontal, PinchToSummarizeTheme.Layout.panelPadX)

            PinchToSummarizeArticleImage()
                .frame(width: width, height: width * PinchToSummarizeTheme.Layout.photoAspect)
                .clipped()
                .padding(.top, PinchToSummarizeTheme.Layout.photoGap)
                .accessibilityLabel("A woman in the Halo One visor waves at the camera")

            Circle()
                .fill(PinchToSummarizeTheme.Colors.panelInkSoft)
                .frame(width: 4, height: 4)
                .padding(.top, PinchToSummarizeTheme.Layout.dotGap)
                .padding(.bottom, PinchToSummarizeTheme.Layout.bylineGap)

            Text(PinchToSummarizeTheme.Copy.byline)
                .pinchToSummarizeText(PinchToSummarizeTheme.Typography.byline)

            Text(PinchToSummarizeTheme.Copy.date)
                .pinchToSummarizeText(PinchToSummarizeTheme.Typography.byline)
                .padding(.top, 4)
        }
        .padding(.top, PinchToSummarizeTheme.Layout.panelPadTop)
        .padding(.bottom, PinchToSummarizeTheme.Layout.panelPadBottom)
        .frame(width: width)
        .background(PinchToSummarizeTheme.Colors.panel, in: .rect(cornerRadius: PinchToSummarizeTheme.Layout.panelRadius))
        .clipShape(.rect(cornerRadius: PinchToSummarizeTheme.Layout.panelRadius))
    }
}

private struct PinchToSummarizeArticleBody: View {
    @Environment(\.displayScale) private var displayScale
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Text(PinchToSummarizeTheme.Copy.section)
                    .pinchToSummarizeText(PinchToSummarizeTheme.Typography.section)
                Rectangle()
                    .fill(PinchToSummarizeTheme.Colors.rule)
                    .frame(height: 1 / displayScale)
            }
            .padding(.bottom, 16)

            ForEach(Array(PinchToSummarizeTheme.Copy.body.enumerated()), id: \.offset) { index, paragraph in
                PinchToSummarizeArticleParagraph(text: paragraph, hasLeadIn: index == 0)
                    .padding(.bottom, 16)
            }
        }
        .padding(.horizontal, PinchToSummarizeTheme.Layout.panelPadX)
        .padding(.top, PinchToSummarizeTheme.Layout.bodyGap)
    }
}

private struct PinchToSummarizeArticleParagraph: View {
    let text: String
    let hasLeadIn: Bool

    var body: some View {
        PinchToSummarizeArticleLabel(
            text: text,
            style: PinchToSummarizeTheme.Typography.body,
            alignment: .natural,
            leadWords: hasLeadIn ? 3 : 0
        )
    }
}

private struct PinchToSummarizeArticleToolbar: View {
    let insetBottom: CGFloat
    let onSummarize: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                gradient: Gradient(stops: [
                    .init(color: PinchToSummarizeTheme.Colors.pageClear, location: 0),
                    .init(color: PinchToSummarizeTheme.Colors.page, location: 0.45)
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(
                height: PinchToSummarizeTheme.Layout.toolbarHeight
                    + insetBottom
                    + PinchToSummarizeTheme.Layout.toolbarFade
            )

            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(PinchToSummarizeTheme.Colors.toolbarInk.opacity(0.25))
                        .frame(width: 22, height: 22)
                        .rotationEffect(.degrees(8))
                        .offset(x: 3)
                    LinearGradient(
                        colors: PinchToSummarizeTheme.Colors.stackIcon,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .frame(width: 22, height: 22)
                    .clipShape(.rect(cornerRadius: 6))
                    .rotationEffect(.degrees(-8))
                }
                .frame(width: PinchToSummarizeTheme.Layout.toolbarRound, height: PinchToSummarizeTheme.Layout.toolbarRound)
                .accessibilityHidden(true)

                Spacer(minLength: 0)

                Button(action: onSummarize) {
                    PinchToSummarizeIcon(name: "add", size: 20, color: PinchToSummarizeTheme.Colors.toolbarInk)
                }
                    .frame(
                        width: PinchToSummarizeTheme.Layout.toolbarPill.width,
                        height: PinchToSummarizeTheme.Layout.toolbarPill.height
                    )
                    .background(PinchToSummarizeTheme.Colors.toolbar, in: Capsule())
                    .contentShape(Rectangle().inset(by: -8))
                    .buttonStyle(PinchToSummarizePressStyle())
                    .accessibilityLabel("Summarize article")
                    .accessibilityHint("Or pinch the page with two fingers")

                Spacer(minLength: 0)

                PinchToSummarizeIcon(name: "chevron-up", size: 16, color: PinchToSummarizeTheme.Colors.toolbarInk)
                    .frame(width: PinchToSummarizeTheme.Layout.toolbarRound, height: PinchToSummarizeTheme.Layout.toolbarRound)
                    .background(PinchToSummarizeTheme.Colors.toolbar, in: Circle())
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 18)
            .frame(height: PinchToSummarizeTheme.Layout.toolbarHeight)
            .padding(.bottom, PinchToSummarizeTheme.Layout.toolbarCenterFromBottom - PinchToSummarizeTheme.Layout.toolbarHeight / 2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
    }
}

private struct PinchToSummarizeArticleImage: View {
    private static let hero: UIImage = {
        guard let image = UIImage(named: "PinchToSummarize-hero.jpg") else {
            #if DEBUG
            assertionFailure("Missing Pinch to Summarize asset: PinchToSummarize-hero.jpg")
            #endif
            return UIImage()
        }
        return image
    }()

    var body: some View {
        Image(uiImage: Self.hero)
            .resizable()
            .scaledToFill()
    }
}

private struct PinchToSummarizePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? CGFloat(PinchToSummarizeConstants.Timing.pressScale) : 1)
    }
}

/// UIKit's paragraph engine enforces the React Native line boxes exactly; SwiftUI's `lineSpacing`
/// is additive and cannot reduce Georgia's intrinsic 36.4 pt line height down to the 36 pt source.
private struct PinchToSummarizeArticleLabel: UIViewRepresentable {
    let text: String
    let style: PinchToSummarizeTheme.Typography.Style
    let alignment: NSTextAlignment
    let leadWords: Int

    init(
        text: String,
        style: PinchToSummarizeTheme.Typography.Style,
        alignment: NSTextAlignment,
        leadWords: Int = 0
    ) {
        self.text = text
        self.style = style
        self.alignment = alignment
        self.leadWords = leadWords
    }

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.numberOfLines = 0
        label.lineBreakMode = .byWordWrapping
        label.backgroundColor = .clear
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        label.attributedText = attributedText()
        label.textAlignment = alignment
        label.accessibilityLabel = text
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView label: UILabel,
        context: Context
    ) -> CGSize? {
        guard let width = proposal.width, width > 0 else { return nil }
        label.attributedText = attributedText()
        label.preferredMaxLayoutWidth = width
        return label.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
    }

    private func attributedText() -> NSAttributedString {
        let font = PinchToSummarizeArticleFont.uiFont(for: style)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byWordWrapping
        if style.lineHeight > 0 {
            paragraph.minimumLineHeight = style.lineHeight
            paragraph.maximumLineHeight = style.lineHeight
        }
        let result = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: font,
                .foregroundColor: UIColor(style.color),
                .kern: style.tracking,
                .paragraphStyle: paragraph
            ]
        )
        if leadWords > 0 {
            let words = text.split(separator: " ", omittingEmptySubsequences: true)
            let lead = words.prefix(leadWords).joined(separator: " ").uppercased()
            let range = (text as NSString).range(of: words.prefix(leadWords).joined(separator: " "))
            result.replaceCharacters(in: range, with: lead)
            result.addAttributes([
                .font: UIFont(name: "Georgia-Bold", size: style.size) ?? .boldSystemFont(ofSize: style.size),
                .kern: 0.6
            ], range: NSRange(location: 0, length: (lead as NSString).length))
        }
        return result
    }
}

private extension View {
    func pinchToSummarizeText(_ style: PinchToSummarizeTheme.Typography.Style) -> some View {
        font(style.family.map { .custom($0, size: style.size).weight(style.weight) } ?? .system(size: style.size, weight: style.weight))
            .tracking(style.tracking)
            .lineSpacing(PinchToSummarizeArticleFont.additionalLeading(for: style))
            .foregroundStyle(style.color)
    }
}

private enum PinchToSummarizeArticleFont {
    static func uiFont(for style: PinchToSummarizeTheme.Typography.Style) -> UIFont {
        if style.family == "Georgia" {
            let name: String
            switch style.weight {
            case .bold, .heavy, .black:
                name = "Georgia-Bold"
            default:
                name = "Georgia"
            }
            return UIFont(name: name, size: style.size) ?? .systemFont(ofSize: style.size)
        }
        return .systemFont(ofSize: style.size)
    }

    static func additionalLeading(for style: PinchToSummarizeTheme.Typography.Style) -> CGFloat {
        guard style.lineHeight > 0 else { return 0 }
        return max(0, style.lineHeight - uiFont(for: style).lineHeight)
    }
}
