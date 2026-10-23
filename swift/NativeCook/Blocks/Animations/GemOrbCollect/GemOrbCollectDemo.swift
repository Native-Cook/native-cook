import CoreText
import SwiftUI
import UIKit

struct GemOrbCollectDemo: View {
    var autoPlay = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model: GemOrbCollectModel

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
        _model = StateObject(wrappedValue: GemOrbCollectModel(autoPlay: autoPlay))
        GemOrbCollectFonts.register()
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                GemOrbCollectMetalSurface(model: model)
                    .ignoresSafeArea()
                if let layout = model.layout {
                    GemOrbCollectCopy(layout: layout, model: model)
                    GemOrbCollectAccessibilityControls(layout: layout, model: model)
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
            .background(.black)
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
        .onAppear {
            model.autoPlay = autoPlay
            model.reduceMotion = reduceMotion
        }
        .onChange(of: autoPlay) { _, value in model.autoPlay = value }
        .onChange(of: reduceMotion) { _, value in model.reduceMotion = value }
    }
}

private struct GemOrbCollectCopy: View {
    let layout: GemOrbCollectLayout
    @ObservedObject var model: GemOrbCollectModel

    private var presentation: GemOrbCollectPresentation { model.presentation }
    private var after: Bool { presentation.copyProgress >= 0.5 }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text("+\(presentation.tally)")
                .font(GemOrbCollectFonts.bold(size: 22 * layout.typeScale))
                .foregroundStyle(Color(red: 0.914, green: 0.894, blue: 1))
                .monospacedDigit()
                .lineLimit(1)
                .opacity(presentation.tallyOpacity)
                .scaleEffect(presentation.tallyScale)
                .frame(width: 112, height: 28 * layout.typeScale)
                .position(x: layout.width / 2, y: layout.tallyY)
                .accessibilityHidden(true)

            GemOrbCollectTitleRoll(layout: layout, progress: presentation.copyProgress,
                                   target: presentation.copyTarget, reduced: model.reduceMotion)
            .frame(width: layout.width, height: 34 * layout.typeScale)
            .position(x: layout.width / 2, y: layout.titleTop + 17 * layout.typeScale)
            .accessibilityElement(children: .ignore)
            .accessibilityAddTraits(.isHeader)
            .accessibilityLabel(after ? "200 Lumens collected" : "200 Lumens ready")

            GemOrbCollectSwapLine(
                before: "Hold the crystal to draw them in.",
                after: "Keep collecting to unlock new crystals.",
                progress: presentation.bodyProgress,
                target: presentation.copyTarget,
                reduced: model.reduceMotion,
                lineHeight: 24 * layout.typeScale,
                font: GemOrbCollectFonts.regular(size: 16 * layout.typeScale),
                color: Color(red: 0.839, green: 0.831, blue: 1).opacity(0.66)
            )
            .frame(width: layout.width, height: 24 * layout.typeScale)
            .position(x: layout.width / 2, y: layout.bodyTop + 12 * layout.typeScale)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(after ? "Keep collecting to unlock new crystals." : "Hold the crystal to draw them in.")

            GemOrbCollectRollingNumber(value: presentation.balanceValue)
                .opacity(presentation.rollFade)
                .scaleEffect(presentation.capsuleScale)
                .frame(width: 60, height: 24, alignment: .leading)
                .position(x: layout.capsule.minX + 74, y: layout.capsule.midY)
                .accessibilityHidden(true)

            GemOrbCollectSwapLine(
                before: "Collect",
                after: "Replay",
                progress: presentation.buttonProgress,
                target: presentation.phase == .before ? 0 : 1,
                reduced: model.reduceMotion,
                lineHeight: 24,
                font: GemOrbCollectFonts.bold(size: 17),
                color: .white
            )
            .opacity(presentation.barOpacity)
            .frame(width: layout.button.width, height: 24)
            .position(x: layout.button.midX, y: layout.button.midY)
            .accessibilityHidden(true)
        }
        .allowsHitTesting(false)
    }
}

private struct GemOrbCollectRollingNumber: View {
    let value: Double
    private let advance = 20.0 * 1353.0 / 2048.0
    private let comma = 20.0 * 582.0 / 2048.0

    var body: some View {
        HStack(spacing: 0) {
            digit(place: 1000)
            Text(",")
                .frame(width: comma, height: 24)
                .font(GemOrbCollectFonts.bold(size: 20))
            digit(place: 100)
            digit(place: 10)
            digit(place: 1)
        }
        .foregroundStyle(.white)
        .frame(width: advance * 4 + comma, height: 24)
    }

    private func digit(place: Double) -> some View {
        let safe = max(0, value)
        let whole = floor(safe / place)
        let position = (whole + max(0, safe - whole * place - (place - 1)))
            .truncatingRemainder(dividingBy: 10)
        return VStack(spacing: 0) {
            ForEach(0...10, id: \.self) { row in
                let distance = abs(Double(row) - position)
                let fadeStart = max(0, 0.5 - 4 / 24)
                let fraction = min(1, max(0, (distance - fadeStart) / (1 - fadeStart)))
                Text(String(row % 10))
                    .font(GemOrbCollectFonts.bold(size: 20))
                    .monospacedDigit()
                    .frame(width: advance, height: 24)
                    .opacity(1 - fraction * fraction * (3 - 2 * fraction))
            }
        }
        .offset(y: -position * 24)
        .frame(width: advance, height: 24, alignment: .top)
        .clipped()
    }
}

private struct GemOrbCollectSwapLine: View {
    let before: String
    let after: String
    let progress: Double
    let target: Double
    let reduced: Bool
    let lineHeight: Double
    let font: Font
    let color: Color
    var alignLeft = false
    var tracking = 0.0

    var body: some View {
        let beforeOpacity = max(0, 1 - progress)
        let afterOpacity = max(0, progress)
        let direction = target >= 0.5 ? 1.0 : -1.0
        ZStack(alignment: alignLeft ? .leading : .center) {
            Text(before)
                .offset(y: reduced ? 0 : -direction * (1 - beforeOpacity) * lineHeight)
                .opacity(reduced ? beforeOpacity : beforeOpacity <= 0 ? 0 : 1)
            Text(after)
                .offset(y: reduced ? 0 : direction * (1 - afterOpacity) * lineHeight)
                .opacity(reduced ? afterOpacity : afterOpacity <= 0 ? 0 : 1)
        }
        .font(font)
        .foregroundStyle(color)
        .tracking(tracking)
        .lineLimit(1)
        .minimumScaleFactor(0.9)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignLeft ? .leading : .center)
        .clipped()
    }
}

private struct GemOrbCollectTitleRoll: View {
    let layout: GemOrbCollectLayout
    let progress: Double
    let target: Double
    let reduced: Bool

    var body: some View {
        let size = 28 * layout.typeScale
        let line = 34 * layout.typeScale
        let nativeFont = UIFont(name: "Inter24pt-Bold", size: size) ?? .boldSystemFont(ofSize: size)
        let attributes: [NSAttributedString.Key: Any] = [.font: nativeFont, .kern: -0.4]
        let prefixWidth = ("200 Lumens " as NSString).size(withAttributes: attributes).width
        let readyWidth = ("ready" as NSString).size(withAttributes: attributes).width
        let collectedWidth = ("collected" as NSString).size(withAttributes: attributes).width
        let startX = (layout.width - prefixWidth - readyWidth
                      - (collectedWidth - readyWidth) * progress) / 2
        let titleColor = Color(red: 0.949, green: 0.945, blue: 1)
        Group {
            if reduced {
                GemOrbCollectSwapLine(before: "200 Lumens ready", after: "200 Lumens collected",
                                       progress: progress, target: target, reduced: true,
                                       lineHeight: line, font: GemOrbCollectFonts.bold(size: size),
                                       color: titleColor, tracking: -0.4)
            } else {
                ZStack(alignment: .topLeading) {
                    Text("200 Lumens ")
                        .offset(x: startX)
                    GemOrbCollectSwapLine(before: "ready", after: "collected",
                                           progress: progress, target: target, reduced: false,
                                           lineHeight: line, font: GemOrbCollectFonts.bold(size: size),
                                           color: titleColor, alignLeft: true, tracking: -0.4)
                        .frame(width: collectedWidth + 3, height: line)
                        .offset(x: startX + prefixWidth)
                }
                .font(GemOrbCollectFonts.bold(size: size))
                .foregroundStyle(titleColor)
                .tracking(-0.4)
                .frame(width: layout.width, height: line, alignment: .topLeading)
            }
        }
    }
}

private struct GemOrbCollectAccessibilityControls: View {
    let layout: GemOrbCollectLayout
    @ObservedObject var model: GemOrbCollectModel

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button("Crystal") { model.accessibilityActivateGem() }
                .buttonStyle(.plain)
                .foregroundStyle(.clear)
                .frame(width: layout.gemRadius * 2.5, height: layout.gemRadius * 2.5)
                .contentShape(Circle())
                .position(x: layout.gemX, y: layout.gemY)
                .accessibilityLabel("Crystal")
                .accessibilityHint("Hold to collect the Lumens. Drag to turn it.")

            Button(model.presentation.phase == .after ? "Replay" : "Collect") {
                model.accessibilityActivateBar()
            }
            .buttonStyle(.plain)
            .foregroundStyle(.clear)
            .frame(width: layout.button.width, height: layout.button.height)
            .contentShape(Capsule())
            .position(x: layout.button.midX, y: layout.button.midY)
            .disabled(model.presentation.phase != .before && model.presentation.phase != .after)

            Color.clear
                .frame(width: layout.capsule.width, height: layout.capsule.height)
                .position(x: layout.capsule.midX, y: layout.capsule.midY)
                .accessibilityElement()
                .accessibilityLabel("Balance: \(model.presentation.balance.formatted(.number.grouping(.automatic))) Lumens")
        }
        .allowsHitTesting(false)
        .accessibilityElement(children: .contain)
    }
}

enum GemOrbCollectFonts {
    private static let registered: Void = {
        for file in ["GemInter-Regular", "GemInter-Bold"] {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("\(file).ttf is missing from the app bundle")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() { _ = registered }
    static func regular(size: Double) -> Font { .custom("Inter24pt-Regular", fixedSize: size) }
    static func bold(size: Double) -> Font { .custom("Inter24pt-Bold", fixedSize: size) }
}

#Preview {
    GemOrbCollectDemo()
}
