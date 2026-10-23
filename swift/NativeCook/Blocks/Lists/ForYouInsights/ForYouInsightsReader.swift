import SwiftUI

/// One article's reader under the hero. All three stay mounted; the frame shows the one whose card is flying.
struct ForYouInsightsReader: View, Equatable {
    typealias T = ForYouInsightsTheme
    typealias M = ForYouInsightsMotion
    var article: Int
    var k: Double
    var width: Double
    var top: Double
    var height: Double
    var bottomInset: Double
    var g: Double
    var dragY: Double
    var actorIndex: Int
    var quiet: Bool
    var live: Bool

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.article == rhs.article && lhs.k == rhs.k && lhs.width == rhs.width && lhs.top == rhs.top
            && lhs.height == rhs.height && lhs.bottomInset == rhs.bottomInset && lhs.g == rhs.g
            && lhs.dragY == rhs.dragY && lhs.actorIndex == rhs.actorIndex && lhs.quiet == rhs.quiet && lhs.live == rhs.live
    }

    var body: some View {
        let selected = actorIndex == article
        ZStack(alignment: .topLeading) {
            animatedArticleContent(selected: selected)
                .offset(y: top)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            if selected && live {
                ScrollView(.vertical, showsIndicators: false) {
                    staticArticleContent
                }
                .frame(width: width, height: max(0, height), alignment: .top)
                .offset(y: top)
                .accessibilityHidden(false)
            }
        }
        .frame(width: width, height: top + max(0, height), alignment: .topLeading)
        .offset(y: dragY)
    }

    private func animatedArticleContent(selected: Bool) -> some View {
        let item = ForYouInsightsContent.articles[article]
        return VStack(alignment: .leading, spacing: 0) {
            ForYouInsightsText(text: item.title, style: T.Typo.readerTitle, color: T.Colors.ink, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                .accessibilityAddTraits(.isHeader)
                .modifier(ForYouInsightsReaderBlock(index: 0, k: k, g: g, quiet: quiet, selected: selected, live: live))
            HStack(spacing: T.Space.xs * k) {
                Image(systemName: "clock.fill").font(.system(size: 13 * k)).foregroundStyle(T.Colors.pink)
                ForYouInsightsText(text: "\(item.readTime.uppercased()) · \(ForYouInsightsContent.label)", style: T.Typo.readerMeta, color: T.Colors.pink, k: k)
            }
            .padding(.top, T.Layout.readerMetaGap * k)
            .modifier(ForYouInsightsReaderBlock(index: 1, k: k, g: g, quiet: quiet, selected: selected, live: live))
            ForYouInsightsText(text: item.summary, style: T.Typo.readerLead, color: T.Colors.secondary, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                .padding(.top, T.Space.lg * k)
                .modifier(ForYouInsightsReaderBlock(index: 2, k: k, g: g, quiet: quiet, selected: selected, live: live))
            ForEach(Array(item.sections.enumerated()), id: \.element.heading) { index, part in
                VStack(alignment: .leading, spacing: T.Layout.readerParagraphGap * k) {
                    ForYouInsightsText(text: part.heading, style: T.Typo.readerHeading, color: T.Colors.ink, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                    ForYouInsightsText(text: part.body, style: T.Typo.readerBody, color: T.Colors.secondary, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                }
                .padding(.top, T.Layout.readerSectionGap * k)
                .modifier(ForYouInsightsReaderBlock(index: index + 3, k: k, g: g, quiet: quiet, selected: selected, live: live))
            }
        }
        .padding(.horizontal, T.Layout.readerTextInset * k)
        .padding(.bottom, T.Layout.readerBottom * k + bottomInset)
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    private var staticArticleContent: some View {
        let item = ForYouInsightsContent.articles[article]
        return VStack(alignment: .leading, spacing: 0) {
            ForYouInsightsText(text: item.title, style: T.Typo.readerTitle, color: T.Colors.ink, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: T.Space.xs * k) {
                Image(systemName: "clock.fill").font(.system(size: 13 * k)).foregroundStyle(T.Colors.pink)
                ForYouInsightsText(text: "\(item.readTime.uppercased()) · \(ForYouInsightsContent.label)", style: T.Typo.readerMeta, color: T.Colors.pink, k: k)
            }
            .padding(.top, T.Layout.readerMetaGap * k)
            ForYouInsightsText(text: item.summary, style: T.Typo.readerLead, color: T.Colors.secondary, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                .padding(.top, T.Space.lg * k)
            ForEach(item.sections, id: \.heading) { part in
                VStack(alignment: .leading, spacing: T.Layout.readerParagraphGap * k) {
                    ForYouInsightsText(text: part.heading, style: T.Typo.readerHeading, color: T.Colors.ink, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                    ForYouInsightsText(text: part.body, style: T.Typo.readerBody, color: T.Colors.secondary, k: k, width: 402 - 2 * T.Layout.readerTextInset)
                }
                .padding(.top, T.Layout.readerSectionGap * k)
            }
        }
        .padding(.horizontal, T.Layout.readerTextInset * k)
        .padding(.bottom, T.Layout.readerBottom * k + bottomInset)
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }
}

/// ReaderBlock.tsx: each block owns its opacity so iOS never caches a transparent reader subtree.
private struct ForYouInsightsReaderBlock: ViewModifier {
    typealias M = ForYouInsightsMotion

    var index: Int
    var k: Double
    var g: Double
    var quiet: Bool
    var selected: Bool
    var live: Bool

    func body(content: Content) -> some View {
        let range = index == 0 ? M.Reader.titleIn : M.Reader.bodyIn
        let delay = index == 0 ? 0 : Double(index - 1) * M.Reader.bodyStagger
        let p = M.seg(g, range.0 + delay, range.1 + delay)
        let opacity = quiet ? M.clamp01(g) : M.smooth(p)
        let rise = index == 0 ? M.Reader.titleRise : M.Reader.bodyRise
        let scale = quiet || index != 0 ? 1 : M.lerp(M.Reader.titleScaleFrom, 1, M.easeOut(p))
        content
            .opacity(selected && !live ? opacity : 0)
            .offset(y: quiet ? 0 : (1 - M.easeOut(p)) * rise * k)
            .scaleEffect(scale, anchor: .center)
            .accessibilityHidden(true)
    }
}
