import SwiftUI
import UIKit

private typealias M = HabitatSpecimenCarouselMotion
private typealias Specimen = HabitatSpecimenCarouselSpecimen

enum HabitatSpecimenCarouselTab: Int { case about = 0, stats, evolution }

/// A mounted-for-the-view clock that runs 0 to 1 in 900 ms (linear) each time the panel changes;
/// the stat bars fill and the evolution chain draws from it.
struct HabitatSpecimenCarouselReveal<Content: View>: View {
    let key: Int
    let reduced: Bool
    @ViewBuilder let content: (Double) -> Content

    @State private var start = Date()

    var body: some View {
        TimelineView(.animation(paused: reduced)) { context in
            let t = reduced ? 1 : min(1, max(0, context.date.timeIntervalSince(start) / 0.9))
            content(t)
        }
        .onChange(of: key) { start = Date() }
    }
}

/// Element tints in carousel order, interpolated the way Reanimated's interpolateColor does (RGB, gamma 2.2).
private func elementTint(_ position: Double) -> Color {
    let colors = Specimen.elementColors
    let p = max(0, min(3, position))
    let lower = min(2, Int(p.rounded(.down)))
    let f = p - Double(lower)
    func channel(_ hex: UInt32, _ shift: UInt32) -> Double { pow(Double((hex >> shift) & 0xff) / 255, 2.2) }
    func mix(_ shift: UInt32) -> Double { pow(channel(colors[lower], shift) + (channel(colors[lower + 1], shift) - channel(colors[lower], shift)) * f, 1 / 2.2) }
    return Color(.sRGB, red: mix(16), green: mix(8), blue: mix(0), opacity: 1)
}

/// Value of one base stat at a continuous carousel position: it morphs with the swipe itself.
private func statAt(_ stat: Int, _ position: Double) -> Double {
    let p = M.wrapPosition(position)
    let from = Int(p.rounded(.down)) % 3
    let to = (from + 1) % 3
    let f = p - p.rounded(.down)
    let eased = f * f * (3 - 2 * f)
    let a = Double(Specimen.all[from].stats[stat]), b = Double(Specimen.all[to].stats[stat])
    return a + (b - a) * eased
}

private let tabCurve = Animation.timingCurve(0.2, 0, 0, 1, duration: 0.16)
private let tabLabels = ["About", "Base stats", "Evolution"]
private let statLabels = ["HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"]

private struct StatRow: View {
    let model: HabitatSpecimenCarouselModel
    let index: Int
    let reveal: Double
    let scale: CGFloat
    let height: CGFloat
    let selectedIndex: Int

    var body: some View {
        let line = 15 * scale
        HStack(spacing: 0) {
            Text(statLabels[index])
                .font(.system(size: 9.5 * scale))
                .tracking(0.3)
                .foregroundStyle(Color(habitatSC: 0x9FAE9E))
                .frame(width: 44 * scale, alignment: .leading)
            HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: line, selectedIndex: selectedIndex, alignment: .topTrailing) { specimen in
                Text("\(Specimen.all[specimen].stats[index])")
                    .font(.system(size: 11.5 * scale, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Color(habitatSC: 0xF1F4E5))
                    .frame(height: line)
            }
            .frame(width: 26 * scale)
            .padding(.trailing, 10)
            // The bar fills from the left; its colour follows the habitat front.
            TimelineView(.animation) { _ in
                let local: Double = model.reduced ? 1 : {
                    let t = max(0, min(1, (reveal - Double(index) * 0.075) / 0.6))
                    return 1 - pow(1 - t, 3)
                }()
                let fill = min(1, statAt(index, model.position) / 150) * local
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Color(.sRGB, red: 1, green: 1, blue: 1, opacity: 0x14 / 255.0)
                        Rectangle().fill(elementTint(M.wrapPosition(model.position)))
                            .frame(width: proxy.size.width * fill)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 2 * scale, style: .continuous))
                }
            }
            .frame(height: 4 * scale)
        }
        .frame(height: height)
    }
}

private struct EvolutionLink: View {
    let order: Int
    let reached: Bool
    let level: Int?
    let from: CGFloat
    let to: CGFloat
    let top: CGFloat
    let reveal: Double
    let scale: CGFloat

    var body: some View {
        let width = max(0, to - from)
        let t = max(0, min(1, (reveal - 0.12 - Double(order) * 0.16) / 0.45))
        ZStack(alignment: .leading) {
            Color(habitatSC: 0xD4E4DA, 0x22 / 255.0)
            Color(habitatSC: 0xE0EACC, 0x88 / 255.0)
                .frame(width: width * (reached ? 1 - pow(1 - t, 3) : 0))
        }
        .frame(width: width, height: 1)
        .overlay(alignment: .top) {
            if let level {
                Text("Lv \(level)")
                    .font(.system(size: 8.5 * scale, weight: .semibold))
                    .tracking(0.4)
                    .foregroundStyle(Color(habitatSC: 0xC9D4C3))
                    .padding(.horizontal, 6 * scale)
                    .frame(height: 18 * scale)
                    .background(Color(habitatSC: 0x18241F), in: RoundedRectangle(cornerRadius: 9 * scale, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 9 * scale, style: .continuous).stroke(Color(habitatSC: 0xD4E4DA, 0x2A / 255.0), lineWidth: 1 / 3))
                    .fixedSize()
                    .offset(y: -9 * scale - 0.5)
            }
        }
        .offset(x: from, y: top - 0.5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct EvolutionStageView: View {
    let stage: HabitatSpecimenCarouselStage
    let active: Bool
    let order: Int
    let asset: String
    let reveal: Double
    let reduced: Bool
    let scale: CGFloat
    let node: CGFloat
    let left: CGFloat
    let width: CGFloat

    var body: some View {
        let t = reduced ? 1 : max(0, min(1, (reveal - Double(order) * 0.16) / 0.5))
        let eased = reduced ? 1 : 1 - pow(1 - t, 3)
        VStack(spacing: 0) {
            ZStack {
                Circle().fill(active ? Color(habitatSC: 0xFFFFFF, 0x12 / 255.0) : Color(habitatSC: 0xFFFFFF, 0x08 / 255.0))
                Circle().stroke(active ? Color(habitatSC: 0xE9EFD8, 0xAA / 255.0) : Color(habitatSC: 0xD4E4DA, 0x33 / 255.0), lineWidth: active ? 1 : 1 / 3)
                if active {
                    HabitatSpecimenCarouselImage(asset: asset).frame(width: node * 0.92, height: node * 0.92)
                } else {
                    Text("#\(stage.number)")
                        .font(.system(size: 9.5 * scale, weight: .semibold))
                        .tracking(0.6)
                        .monospacedDigit()
                        .foregroundStyle(Color(habitatSC: 0xA5B49D))
                }
            }
            .frame(width: node, height: node)
            Text(stage.name)
                .font(.system(size: 10 * scale, weight: active ? .semibold : .medium))
                .foregroundStyle(active ? Color(habitatSC: 0xF1F4E5) : Color(habitatSC: 0x9FAE9E))
                .lineLimit(1)
                .padding(.top, 6 * scale)
        }
        .frame(width: width)
        .opacity(eased)
        .scaleEffect(0.92 + 0.08 * eased)
        .offset(x: left)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct EvolutionView: View {
    let spec: HabitatSpecimenCarouselSpecimen
    let reduced: Bool
    let reveal: Double
    let scale: CGFloat
    let width: CGFloat

    var body: some View {
        let current = spec.evolution.firstIndex { $0.number == spec.number } ?? -1
        let node = 40 * scale
        let column = 74 * scale
        let centers = [column / 2, width / 2, width - column / 2]
        ZStack(alignment: .topLeading) {
            ForEach(0..<2, id: \.self) { link in
                EvolutionLink(
                    order: link, reached: link < current, level: spec.evolution[link + 1].level,
                    from: centers[link] + node / 2 + 6 * scale, to: centers[link + 1] - node / 2 - 6 * scale, top: node / 2,
                    reveal: reduced ? 1 : reveal, scale: scale
                )
            }
            ForEach(Array(spec.evolution.enumerated()), id: \.offset) { index, stage in
                EvolutionStageView(
                    stage: stage, active: index == current, order: index, asset: spec.asset, reveal: reveal, reduced: reduced,
                    scale: scale, node: node, left: centers[index] - column / 2, width: column
                )
            }
        }
        .frame(width: width, alignment: .topLeading)
    }
}

/// The three panels under the stage and the tabs that choose between them.
struct HabitatSpecimenCarouselPanels: View {
    let model: HabitatSpecimenCarouselModel
    let layout: HabitatSpecimenCarouselLayout
    let selectedIndex: Int
    let tab: HabitatSpecimenCarouselTab
    let onTab: (HabitatSpecimenCarouselTab) -> Void

    var body: some View {
        let scale = CGFloat(layout.scale)
        let reduced = model.reduced
        let contentHeight = CGFloat(max(24, layout.factsTop - layout.descriptionTop - 4 * layout.scale))
        let rowHeight = min(24 * scale, contentHeight / 3)
        let panelWidth = CGFloat(layout.width) - 54 * scale
        let curve: Animation? = reduced ? .timingCurve(0.2, 0, 0, 1, duration: 0.16) : tabCurve
        ZStack(alignment: .topLeading) {
            Tabs(layout: layout, tab: tab, reduced: reduced, onTab: onTab)

            // About
            HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: contentHeight, selectedIndex: selectedIndex) { index in
                ScrollView(.vertical, showsIndicators: false) {
                    // A 19 pt line box with the text centred in it, as the reference sets it.
                    let spare = max(0, 19 * scale - UIFont.systemFont(ofSize: 11.5 * scale).lineHeight)
                    Text(Specimen.all[index].copy)
                        .font(.system(size: 11.5 * scale))
                        .lineSpacing(spare)
                        .padding(.top, spare / 2)
                        .foregroundStyle(Color(habitatSC: 0xC4CEC0))
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            .frame(width: panelWidth, height: contentHeight)
            .opacity(tab == .about ? 1 : 0)
            .offset(x: 27 * scale, y: CGFloat(layout.descriptionTop) + (tab == .about || reduced ? 0 : 4 * scale))
            .allowsHitTesting(tab == .about)
            .accessibilityHidden(tab != .about)
            .animation(curve, value: tab)

            // Base stats
            HabitatSpecimenCarouselReveal(key: tab.rawValue * 10 + (reduced ? 1 : 0), reduced: reduced) { reveal in
                HStack(alignment: .top, spacing: 22 * scale) {
                    ForEach(0..<2, id: \.self) { column in
                        VStack(spacing: 0) {
                            ForEach(0..<3, id: \.self) { row in
                                StatRow(model: model, index: column * 3 + row, reveal: reveal, scale: scale, height: rowHeight, selectedIndex: selectedIndex)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .frame(width: panelWidth, height: contentHeight, alignment: .topLeading)
            }
            .opacity(tab == .stats ? 1 : 0)
            .offset(x: 27 * scale, y: CGFloat(layout.descriptionTop) + (tab == .stats || reduced ? 0 : 4 * scale))
            .allowsHitTesting(false)
            .accessibilityHidden(tab != .stats)
            .animation(curve, value: tab)

            // Evolution
            HabitatSpecimenCarouselReveal(key: tab.rawValue * 10 + (reduced ? 1 : 0), reduced: reduced) { reveal in
                HabitatSpecimenCarouselTextSwap(model: model, count: 3, height: contentHeight, selectedIndex: selectedIndex) { index in
                    EvolutionView(spec: Specimen.all[index], reduced: reduced, reveal: reveal, scale: scale, width: panelWidth)
                }
                .frame(width: panelWidth, height: contentHeight)
            }
            .opacity(tab == .evolution ? 1 : 0)
            .offset(x: 27 * scale, y: CGFloat(layout.descriptionTop) + (tab == .evolution || reduced ? 0 : 4 * scale))
            .allowsHitTesting(false)
            .accessibilityHidden(tab != .evolution)
            .animation(curve, value: tab)
        }
        .frame(width: CGFloat(layout.width), height: CGFloat(layout.height), alignment: .topLeading)
    }

    private struct Tabs: View {
        let layout: HabitatSpecimenCarouselLayout
        let tab: HabitatSpecimenCarouselTab
        let reduced: Bool
        let onTab: (HabitatSpecimenCarouselTab) -> Void

        /// Where each label sits in the row (its start and its width), as Yoga lays the row out.
        private var frames: [(x: CGFloat, width: CGFloat)] {
            let scale = CGFloat(layout.scale)
            let font = UIFont.systemFont(ofSize: 11 * scale, weight: .semibold)
            var cursor: CGFloat = 0
            return tabLabels.map { label in
                let width = (label as NSString).size(withAttributes: [.font: font]).width
                let measured = (width * 3).rounded(.up) / 3
                defer { cursor += measured + 31 * scale }
                return (cursor, measured)
            }
        }

        var body: some View {
            let scale = CGFloat(layout.scale)
            let frames = frames
            let target = frames[tab.rawValue]
            let rowWidth = CGFloat(layout.width) - 54 * scale
            ZStack(alignment: .topLeading) {
                Rectangle().fill(Color(habitatSC: 0xD4E4DA, 0x19 / 255.0)).frame(height: 1 / 3)
                HStack(spacing: 31 * scale) {
                    ForEach(0..<3, id: \.self) { index in
                        Button { onTab(HabitatSpecimenCarouselTab(rawValue: index)!) } label: {
                            Text(tabLabels[index])
                                .font(.system(size: 11 * scale, weight: .semibold))
                                .foregroundStyle(tab.rawValue == index ? Color(habitatSC: 0xF1F4E5) : Color(habitatSC: 0x8F9E8F))
                                .animation(.timingCurve(0.2, 0, 0, 1, duration: 0.15), value: tab)
                                .fixedSize()
                                .contentShape(Rectangle().inset(by: -10))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(tab.rawValue == index ? [.isSelected] : [])
                    }
                }
                .padding(.top, 12)
                // The underline keeps the short 21 pt mark of the design, on the label's start.
                Capsule().fill(Color(habitatSC: 0xE0EACC))
                    .frame(width: min(21 * scale, target.width), height: 2)
                    .offset(x: target.x, y: 12 + 11 * scale * 1.2 + 12 * scale - 2)
                    .animation(reduced ? nil : .spring(response: 0.3, dampingFraction: 1), value: tab)
                    .allowsHitTesting(false)
            }
            .frame(width: rowWidth, height: 32 * scale, alignment: .topLeading)
            .offset(x: 27 * scale, y: CGFloat(layout.tabsTop))
        }
    }
}

/// A creature's PNG as a SwiftUI image, for the thumbnails and the evolution chain.
struct HabitatSpecimenCarouselImage: View {
    let asset: String

    var body: some View {
        if let image = Self.image(asset) {
            Image(uiImage: image).resizable().scaledToFit()
        } else {
            Color.clear
        }
    }

    private static var cache: [String: UIImage] = [:]
    private static func image(_ asset: String) -> UIImage? {
        if let cached = cache[asset] { return cached }
        guard let url = Bundle.main.url(forResource: "HabitatSpecimenCarousel-" + asset, withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        let ready = image.preparingForDisplay() ?? image
        cache[asset] = ready
        return ready
    }
}
