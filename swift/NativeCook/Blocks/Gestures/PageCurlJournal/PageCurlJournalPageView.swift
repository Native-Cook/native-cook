import SwiftUI

/// One of the book's four white pages, drawn on the 402-pt reference grid and scaled by `size.width / 402`
/// (JournalPages.tsx). The same view is shown at rest and rendered once into the texture the curl samples.
struct PageCurlJournalPageView: View {
    let page: Int
    let size: CGSize
    /// Device pixels per point, for the mark's hairline border.
    let pixelScale: CGFloat
    let twinkle: PageCurlJournalTwinkle

    var body: some View {
        let s = size.width / PageCurlJournalTheme.referenceWidth
        ZStack(alignment: .topLeading) {
            switch page {
            case 0: PageCurlJournalTitlePage(size: size, scale: s, pixelScale: pixelScale, twinkle: twinkle)
            case 1: PageCurlJournalPrefacePage(size: size, scale: s)
            case 2: PageCurlJournalForewordPage(size: size, scale: s)
            default: PageCurlJournalCoverPage(size: size, scale: s)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(PageCurlJournalTheme.Ink.paper)
        .clipped()
    }
}

private typealias Theme = PageCurlJournalTheme

struct PageCurlJournalTitlePage: View {
    let size: CGSize
    let scale: CGFloat
    let pixelScale: CGFloat
    let twinkle: PageCurlJournalTwinkle

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalLines([Theme.Copy.meet], style: Theme.Typography.eyebrow, scale: scale, color: Theme.Ink.eyebrow, width: size.width)
                .offset(y: Theme.Typography.eyebrow.top * scale)
            wordmark
                .offset(y: Theme.Typography.brand.top * scale)
            mark
                .offset(x: Theme.Layout.markLeft * scale, y: Theme.Layout.markTop * scale)
            PageCurlJournalLines(Theme.Copy.intro, style: Theme.Typography.intro, scale: scale, color: Theme.Ink.text, width: size.width)
                .offset(y: Theme.Typography.intro.top * scale)
            hint
                .offset(y: Theme.Typography.hint.top * scale)
        }
    }

    /// "Ritualbook" with its i dotted by a sparkle, as the source's wordmark is.
    private var wordmark: some View {
        let line = Theme.Typography.brand
        let name = Theme.Brand.name
        let head = String(name.prefix(1))
        let tail = String(name.dropFirst(2))
        let tracking = -0.4 * scale
        return HStack(spacing: 0) {
            letters(head, tracking: tracking)
            letters("ı", tracking: tracking)
                .overlay(alignment: .topLeading) {
                    PageCurlJournalTwinkleView(twinkle: twinkle, delay: PageCurlJournalMotion.Twinkle.staggerMs / PageCurlJournalMotion.Twinkle.durationMs) {
                        PageCurlJournalSparkle(size: 7 * scale)
                    }
                    .offset(x: 0.3 * scale, y: 4.6 * scale)
                }
            letters(tail, tracking: tracking)
        }
        .frame(width: size.width, height: line.line * scale, alignment: .center)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(name)
    }

    private func letters(_ text: String, tracking: CGFloat) -> some View {
        let line = Theme.Typography.brand
        return Text(text)
            .font(.system(size: line.size * scale, weight: line.weight))
            .kerning(tracking)
            .foregroundStyle(Theme.Ink.text)
            .fixedSize()
            .frame(height: line.line * scale)
    }

    /// The app mark: a white squircle with a heavy monogram and its bottom-left corner curling up like a page.
    private var mark: some View {
        let side = Theme.Layout.markSize * scale
        let radius = Theme.Layout.markRadius * scale
        let hairline = 1 / pixelScale
        let inner = side - 2 * hairline
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        return ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                monogram("R")
                monogram("ı")
                    .overlay(alignment: .topLeading) {
                        PageCurlJournalTwinkleView(twinkle: twinkle, delay: 0) {
                            PageCurlJournalSparkle(size: 20 * scale)
                        }
                        .offset(x: -3 * scale, y: 4 * scale)
                    }
            }
            .frame(width: inner, height: 74 * scale, alignment: .center)
            .offset(y: 18 * scale)
            PageCurlJournalIcons.MarkFlap(size: inner)
        }
        .frame(width: inner, height: inner, alignment: .topLeading)
        .padding(hairline)
        .background(Theme.Ink.paper)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Theme.Ink.iconLine, lineWidth: hairline))
    }

    private func monogram(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 62 * scale, weight: .black))
            .kerning(-2)
            .foregroundStyle(Theme.Icon.markInk)
            .fixedSize()
            .frame(height: 74 * scale)
    }

    private var arrowSize: CGFloat {
        let box = Theme.Layout.hintArrowSize * scale
        return PageCurlJournalSymbolFit.fontSize("arrow.right", pointSize: box, medium: true, box: box)
    }

    private var hint: some View {
        let style = Theme.Typography.hint
        return HStack(spacing: 0) {
            Text(Theme.Copy.hint)
                .font(.system(size: style.size * scale, weight: style.weight))
                .foregroundStyle(Theme.Ink.mute)
                .fixedSize()
            Image(systemName: "arrow.right")
                .font(.system(size: arrowSize, weight: .medium))
                .foregroundStyle(Theme.Ink.mute)
                .frame(width: Theme.Layout.hintArrowSize * scale, height: Theme.Layout.hintArrowSize * scale)
                .padding(.leading, Theme.Layout.hintArrowGap * scale)
        }
        .frame(width: size.width, height: style.line * scale, alignment: .center)
    }
}

struct PageCurlJournalPrefacePage: View {
    let size: CGSize
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalLines([Theme.Copy.preface], style: Theme.Typography.eyebrow, scale: scale, color: Theme.Ink.eyebrow, width: size.width)
                .offset(y: Theme.Typography.eyebrow.top * scale)
            PageCurlJournalLines([Theme.Copy.prefaceTitle], style: Theme.Typography.title, scale: scale, color: Theme.Ink.text, width: size.width)
                .offset(y: Theme.Typography.title.top * scale)
            ForEach(Array(Theme.Copy.rows.enumerated()), id: \.offset) { index, row in
                icon(index)
                    .offset(
                        x: (Theme.Layout.rowIconX - Theme.Layout.rowIcon / 2) * scale,
                        y: (Theme.Layout.rowIconY[index] - Theme.Layout.rowIcon / 2) * scale
                    )
                VStack(alignment: .leading, spacing: Theme.Layout.rowGap * scale) {
                    PageCurlJournalLines(
                        [row.title], style: Theme.Typography.rowTitle, scale: scale, color: Theme.Ink.text,
                        width: size.width - Theme.Layout.rowTextLeft * scale, alignment: .leading
                    )
                    PageCurlJournalLines(
                        row.body, style: Theme.Typography.rowBody, scale: scale, color: Theme.Ink.text,
                        width: size.width - Theme.Layout.rowTextLeft * scale, alignment: .leading
                    )
                }
                .offset(x: Theme.Layout.rowTextLeft * scale, y: Theme.Layout.rowTitleTop[index] * scale)
            }
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: shieldSize))
                .foregroundStyle(Theme.Ink.footnote)
                .frame(width: size.width)
                .offset(y: Theme.Layout.shieldTop * scale)
            PageCurlJournalLines(Theme.Copy.footnote, style: Theme.Typography.footnote, scale: scale, color: Theme.Ink.footnote, width: size.width)
                .offset(y: Theme.Typography.footnote.top * scale)
            PageCurlJournalLines(["2"], style: Theme.Typography.folio, scale: scale, color: Theme.Ink.mute, width: size.width)
                .offset(y: Theme.Typography.folio.top * scale)
        }
    }

    private var shieldSize: CGFloat {
        let box = Theme.Layout.shieldSize * scale
        return PageCurlJournalSymbolFit.fontSize("shield.lefthalf.filled", pointSize: box, medium: false, box: box)
    }

    @ViewBuilder
    private func icon(_ index: Int) -> some View {
        let side = Theme.Layout.rowIcon * scale
        if index == 0 {
            PageCurlJournalIcons.CheckBadge(size: side)
        } else {
            PageCurlJournalIcons.BarsIcon(size: side)
        }
    }
}

struct PageCurlJournalForewordPage: View {
    let size: CGSize
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalLines([Theme.Copy.foreword], style: Theme.Typography.eyebrow, scale: scale, color: Theme.Ink.eyebrow, width: size.width)
                .offset(y: Theme.Typography.eyebrow.top * scale)
            PageCurlJournalLines([Theme.Copy.forewordTitle], style: Theme.Typography.title, scale: scale, color: Theme.Ink.text, width: size.width)
                .offset(y: Theme.Typography.title.top * scale)
            PageCurlJournalLines(
                Theme.Copy.body, style: Theme.Typography.body, scale: scale, color: Theme.Ink.text,
                width: size.width - Theme.Layout.bodyLeft * scale, alignment: .leading
            )
            .offset(x: Theme.Layout.bodyLeft * scale, y: Theme.Typography.body.top * scale)
            PageCurlJournalLines(["3"], style: Theme.Typography.folio, scale: scale, color: Theme.Ink.mute, width: size.width)
                .offset(y: Theme.Typography.folio.top * scale)
        }
    }
}

struct PageCurlJournalCoverPage: View {
    let size: CGSize
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            PageCurlJournalSparkle(size: Theme.Layout.sparkleSize * scale, colored: true)
                .offset(
                    x: (Theme.Layout.sparkleCX - Theme.Layout.sparkleSize / 2) * scale,
                    y: (Theme.Layout.sparkleCY - Theme.Layout.sparkleSize / 2) * scale
                )
            PageCurlJournalLines(Theme.Copy.cover, style: Theme.Typography.cover, scale: scale, color: Theme.Ink.text, width: size.width)
                .offset(y: Theme.Typography.cover.top * scale)
            PageCurlJournalLines(
                [Theme.Copy.caption], style: Theme.Typography.caption, scale: scale, color: Theme.Ink.caption,
                width: size.width, tracking: 0.3 * scale
            )
            .offset(y: Theme.Typography.caption.top * scale)
            PageCurlJournalLines([Theme.Copy.author], style: Theme.Typography.author, scale: scale, color: Theme.Ink.author, width: size.width)
                .offset(y: Theme.Typography.author.top * scale)
            PageCurlJournalLines(["4"], style: Theme.Typography.folio, scale: scale, color: Theme.Ink.mute, width: size.width)
                .offset(y: Theme.Typography.folio.top * scale)
        }
    }
}
