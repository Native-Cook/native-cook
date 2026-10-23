import CoreText
import UIKit

final class PullCordThemePageMasks {
    let ink: UIImage
    let mute: UIImage
    let accent: UIImage

    static let accessibilityText: String = {
        let pieces = [
            PullCordThemeTokens.Book.chapter,
            PullCordThemeTokens.Book.heading,
            PullCordThemeTokens.Book.body.joined(separator: " "),
            "Page \(PullCordThemeTokens.Book.page) of \(PullCordThemeTokens.Book.pages)",
            PullCordThemeTokens.Book.left
        ]
        return pieces.map { text in
            text.range(of: #"[.!?:]$"#, options: .regularExpression) == nil ? text + "." : text
        }.joined(separator: " ")
    }()

    init(ink: UIImage, mute: UIImage, accent: UIImage) {
        self.ink = ink
        self.mute = mute
        self.accent = accent
    }

    static func make(size: CGSize, scale displayScale: CGFloat) -> PullCordThemePageMasks {
        PullCordThemePageFont.register()
        let widthScale = size.width / CGFloat(PullCordThemeTokens.stageWidth)
        let scaled: (Double) -> CGFloat = { CGFloat($0) * widthScale }
        let margin = scaled(PullCordThemeTokens.Typography.margin)
        let measure = size.width - 2 * margin
        let labelTop = size.height
            - scaled(PullCordThemeTokens.Typography.footBottom + PullCordThemeTokens.Typography.footLine)
        let barY = labelTop
            - scaled(PullCordThemeTokens.Typography.barGap + PullCordThemeTokens.Typography.barHeight)
        let bodyTop = scaled(PullCordThemeTokens.Typography.bodyTop)
        let bodyHeight = max(0, barY - scaled(24) - bodyTop)
        let format = UIGraphicsImageRendererFormat()
        format.opaque = false
        format.scale = displayScale

        func renderer(_ draw: @escaping (CGContext) -> Void) -> UIImage {
            UIGraphicsImageRenderer(size: size, format: format).image { context in
                context.cgContext.setFillColor(UIColor.black.cgColor)
                draw(context.cgContext)
            }
        }

        func attributes(
            size: Double,
            lineHeight: Double,
            tracking: Double = 0,
            alignment: NSTextAlignment = .left
        ) -> [NSAttributedString.Key: Any] {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = scaled(lineHeight)
            paragraph.maximumLineHeight = scaled(lineHeight)
            paragraph.alignment = alignment
            paragraph.lineBreakMode = .byWordWrapping
            return [
                .font: PullCordThemePageFont.font(size: scaled(size)),
                .foregroundColor: UIColor.black,
                .kern: scaled(tracking),
                .paragraphStyle: paragraph,
                .baselineOffset: scaled(lineHeight - size) * 0.02
            ]
        }

        let ink = renderer { _ in
            NSAttributedString(
                string: PullCordThemeTokens.Book.heading,
                attributes: attributes(
                    size: PullCordThemeTokens.Typography.titleSize,
                    lineHeight: PullCordThemeTokens.Typography.titleLine
                )
            ).draw(in: CGRect(
                x: margin,
                y: scaled(PullCordThemeTokens.Typography.titleTop),
                width: measure,
                height: scaled(PullCordThemeTokens.Typography.titleLine)
            ))

            let body = PullCordThemeTokens.Book.body.enumerated().map { index, text in
                index == 0 ? text : "\u{2003}\(text)"
            }.joined(separator: "\n")
            NSAttributedString(
                string: body,
                attributes: attributes(
                    size: PullCordThemeTokens.Typography.bodySize,
                    lineHeight: PullCordThemeTokens.Typography.bodyLine
                )
            ).draw(in: CGRect(x: margin, y: bodyTop, width: measure, height: bodyHeight))
        }

        let mute = renderer { context in
            NSAttributedString(
                string: PullCordThemeTokens.Book.chapter,
                attributes: attributes(
                    size: PullCordThemeTokens.Typography.eyebrowSize,
                    lineHeight: PullCordThemeTokens.Typography.eyebrowLine,
                    tracking: PullCordThemeTokens.Typography.eyebrowTracking
                )
            ).draw(in: CGRect(
                x: margin,
                y: scaled(PullCordThemeTokens.Typography.eyebrowTop),
                width: measure,
                height: scaled(PullCordThemeTokens.Typography.eyebrowLine)
            ))
            let footer = attributes(
                size: PullCordThemeTokens.Typography.footSize,
                lineHeight: PullCordThemeTokens.Typography.footLine
            )
            NSAttributedString(
                string: "Page \(PullCordThemeTokens.Book.page) of \(PullCordThemeTokens.Book.pages)",
                attributes: footer
            ).draw(in: CGRect(
                x: margin, y: labelTop, width: measure,
                height: scaled(PullCordThemeTokens.Typography.footLine)
            ))
            NSAttributedString(
                string: PullCordThemeTokens.Book.left,
                attributes: attributes(
                    size: PullCordThemeTokens.Typography.footSize,
                    lineHeight: PullCordThemeTokens.Typography.footLine,
                    alignment: .right
                )
            ).draw(in: CGRect(
                x: margin, y: labelTop, width: measure,
                height: scaled(PullCordThemeTokens.Typography.footLine)
            ))
            context.setAlpha(0.3)
            context.fill(CGRect(
                x: margin,
                y: barY,
                width: measure,
                height: scaled(PullCordThemeTokens.Typography.barHeight)
            ))
        }

        let accent = renderer { context in
            context.fill(CGRect(
                x: margin,
                y: scaled(PullCordThemeTokens.Typography.ruleTop),
                width: scaled(PullCordThemeTokens.Typography.ruleWidth),
                height: scaled(PullCordThemeTokens.Typography.ruleHeight)
            ))
            let progress = Double(PullCordThemeTokens.Book.page) / Double(PullCordThemeTokens.Book.pages)
            context.fill(CGRect(
                x: margin,
                y: barY,
                width: measure * progress,
                height: scaled(PullCordThemeTokens.Typography.barHeight)
            ))
        }
        return PullCordThemePageMasks(ink: ink, mute: mute, accent: accent)
    }
}

private enum PullCordThemePageFont {
    private static var registered = false

    static func register() {
        guard !registered else { return }
        registered = true
        guard let url = Bundle.main.url(forResource: "PullCordTheme-Newsreader", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }

    static func font(size: CGFloat) -> UIFont {
        UIFont(name: PullCordThemeTokens.Typography.family, size: size)
            ?? UIFont(name: "Newsreader", size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .regular)
    }
}
