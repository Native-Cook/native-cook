import SwiftUI
import UIKit

struct BadgeVaultGridDemo: View {
    var body: some View {
        BadgeVaultGrid(autoPlay: true)
            .ignoresSafeArea()
            .statusBarHidden()
    }
}

struct BadgeVaultGrid: View {
    var autoPlay = false

    var body: some View {
        GeometryReader { geometry in
            let windowInsets = UIApplication.shared.connectedScenes
                .compactMap { ($0 as? UIWindowScene)?.keyWindow?.safeAreaInsets }.first ?? .zero
            let top = max(geometry.safeAreaInsets.top, windowInsets.top)
            let bottom = max(geometry.safeAreaInsets.bottom, windowInsets.bottom)
            BadgeVaultGridScreen(layout: .init(width: geometry.size.width, height: geometry.size.height,
                                               safeTop: top > 0 ? top : (geometry.size.height > 800 ? 62 : 0),
                                               safeBottom: bottom > 0 ? bottom : (geometry.size.height > 800 ? 34 : 0)),
                                 autoPlay: autoPlay)
        }
        .background(.black)
    }
}

private struct BadgeVaultGridScreen: View {
    let layout: BadgeVaultGridLayout
    let autoPlay: Bool
    @StateObject private var engine: BadgeVaultGridEngine
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(layout: BadgeVaultGridLayout, autoPlay: Bool) {
        self.layout = layout
        self.autoPlay = autoPlay
        _engine = StateObject(wrappedValue: BadgeVaultGridEngine(
            layout: layout, autoPlay: autoPlay, reduced: UIAccessibility.isReduceMotionEnabled))
    }

    var body: some View {
        let _ = engine.serial
        ZStack(alignment: .topLeading) {
            Color.black
            BadgeVaultGridMetalView(layer: .grid, engine: engine, serial: engine.serial)
                .allowsHitTesting(false)
            gridCopy
                .scaleEffect(engine.reduced ? 1 : 1 - 0.06 * engine.hero.recede)
                .opacity(1 - 0.88 * engine.hero.recede)
                .allowsHitTesting(false)
                .accessibilityHidden(engine.selected >= 0)
            BadgeVaultGridMetalView(layer: .hero, engine: engine, serial: engine.serial)
                .allowsHitTesting(false)
            BadgeVaultGridTouchView(engine: engine, share: share)
                .accessibilityHidden(true)
            accessibilityControls
        }
        .frame(width: layout.width, height: layout.height)
        .clipped()
        .onAppear { engine.start() }
        .onDisappear { engine.stop() }
        .onChange(of: reduceMotion) { _, value in
            engine.reduced = value
            engine.autoPlay = autoPlay && !value
        }
        .onChange(of: layout.width) { _, _ in engine.layout = layout }
        .onChange(of: layout.height) { _, _ in engine.layout = layout }
    }

    private var gridCopy: some View {
        ZStack(alignment: .topLeading) {
            label("COLLECTION", 12, .semibold, 0.38)
                .tracking(1.2)
                .frame(width: layout.width - 48, alignment: .leading)
                .position(x: layout.width / 2, y: layout.safeTop + 20 - engine.scrollY)
            label("Badge Vault", 34, .bold)
                .tracking(0.37)
                .frame(width: layout.width - 48, alignment: .leading)
                .position(x: layout.width / 2, y: layout.safeTop + 52 - engine.scrollY)
            label("\(countWord(engine.count)) earned. Tap one to hold it.", 15, .regular, 0.6)
                .frame(width: layout.width - 48, alignment: .leading)
                .position(x: layout.width / 2, y: layout.safeTop + 91 - engine.scrollY)
            ForEach(0..<18, id: \.self) { cellCopy($0) }
            footerCopy
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    private func cellCopy(_ index: Int) -> some View {
        let badge = BadgeVaultGridBadge.all[index]
        let visual = engine.visuals[index]
        let col = layout.column(index % 3)
        let rowY = layout.headerHeight + Double(index / 3) * layout.pitch - engine.scrollY
        let x = col.x + col.width / 2 + visual.shake
        let progress = badge.need > 0 ? "\(engine.cells[index].have) / \(badge.need) \(badge.unit)" : ""
        return ZStack {
            label(badge.name, 13, .semibold, 0.6 + 0.4 * visual.reveal)
                .tracking(-0.16).lineLimit(1).frame(width: col.width)
                .opacity(visual.pose.present)
                .position(x: x, y: rowY + layout.nameY + 9)
            label(visual.reveal > 0.5 ? (badge.earned ?? BadgeVaultGridBadge.unlockDate) : progress,
                  12, .regular, visual.reveal > 0.5 ? 0.6 : 0.38 + 0.62 * visual.flash)
                .monospacedDigit().lineLimit(1).frame(width: col.width)
                .position(x: x, y: rowY + layout.dateY + 8)
            if badge.need > 0 && visual.reveal < 1 {
                Capsule().fill(.white.opacity(0.12)).frame(width: 72, height: 4)
                    .overlay(alignment: .leading) {
                        Capsule().fill(colour(badge.a))
                            .frame(width: max(0, 72 * visual.progress), height: 4)
                    }
                    .opacity(1 - visual.reveal)
                    .position(x: x, y: rowY + layout.barY + 2)
            }
        }
    }

    private var footerCopy: some View {
        let y = layout.footerY - engine.scrollY
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(.white.opacity(0.12))
                .frame(width: layout.width - 32, height: 0.5)
                .position(x: layout.width / 2, y: y)
            rollingCount
                .position(x: 95, y: y + 38)
            label(engine.count == 18 ? "collected · all done" :
                  "collected · \(18 - engine.count) in progress", 13, .regular, 0.6)
                .position(x: 100, y: y + 65)
            Text(engine.count == 18 ? "All collected" : "Log a day")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(engine.count == 18 ? .white.opacity(0.6) :
                    Color(red: 0.043, green: 0.043, blue: 0.047))
                .position(x: layout.width - 90, y: y + 48)
        }
    }

    private var rollingCount: some View {
        let progress = BadgeVaultGridMath.ease((engine.now - engine.countAt) / 260)
        return ZStack(alignment: .leading) {
            label("\(engine.countFrom) of 18", 22, .semibold)
                .monospacedDigit()
                .opacity(1 - progress)
                .offset(y: -28 * progress)
            label("\(engine.count) of 18", 22, .semibold)
                .monospacedDigit()
                .opacity(progress)
                .offset(y: 28 * (1 - progress))
        }
        .frame(width: 142, height: 28, alignment: .leading)
        .clipped()
    }

    private var accessibilityControls: some View {
        ZStack(alignment: .topLeading) {
            if engine.selected < 0 {
                ForEach(0..<18, id: \.self) { index in
                    let badge = BadgeVaultGridBadge.all[index]
                    let cell = engine.cells[index]
                    let col = layout.column(index % 3)
                    let y = layout.headerHeight + Double(index / 3) * layout.pitch - engine.scrollY
                    Color.clear.frame(width: col.width, height: layout.pitch)
                        .contentShape(Rectangle())
                        .position(x: col.x + col.width / 2, y: y + layout.pitch / 2)
                        .accessibilityElement()
                        .accessibilityLabel(cell.earned ?
                            "\(badge.name), earned \(badge.earned ?? BadgeVaultGridBadge.unlockDate)" :
                            "\(badge.name), locked, \(cell.have) of \(badge.need) \(badge.unit)")
                        .accessibilityHint(cell.earned ? "Holds the badge up" : "Not earned yet")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction {
                            engine.touch(true)
                            engine.action("open", index: index)
                            engine.touch(false)
                        }
                }
                Color.clear.frame(width: 132, height: 48)
                    .position(x: layout.width - 90, y: layout.footerY - engine.scrollY + 48)
                    .accessibilityElement()
                    .accessibilityLabel(engine.count == 18 ? "All collected" : "Log a day")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityValue(engine.count == 18 ? "Disabled" : "")
                    .accessibilityAction {
                        if engine.count < 18 { engine.action("log") }
                    }
                    .disabled(engine.count == 18)
                Color.clear.frame(width: 142, height: 28)
                    .position(x: 95, y: layout.footerY - engine.scrollY + 38)
                    .accessibilityElement()
                    .accessibilityLabel("\(engine.count) of 18 collected")
            } else {
                let badge = BadgeVaultGridBadge.all[engine.selected]
                Color.clear.frame(width: 36, height: 36)
                    .position(x: 34, y: layout.safeTop + 22)
                    .accessibilityElement()
                    .accessibilityLabel("Close")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { engine.action("close") }
                Color.clear.frame(width: layout.width - 48, height: 56)
                    .position(x: layout.width / 2, y: layout.shareY + 28)
                    .accessibilityElement()
                    .accessibilityLabel("Share badge")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { share() }
                Color.clear.frame(width: layout.width - 32, height: 38)
                    .position(x: layout.width / 2, y: layout.titleY + 19)
                    .accessibilityElement()
                    .accessibilityLabel(badge.name)
                    .accessibilityAddTraits(.isHeader)
            }
        }
        .allowsHitTesting(false)
    }

    private func share() {
        guard engine.selected >= 0 else { return }
        let badge = BadgeVaultGridBadge.all[engine.selected]
        let text = "\(badge.name)\n\(badge.blurb)\nEarned \(badge.earned ?? BadgeVaultGridBadge.unlockDate) · Badge Vault"
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first else { return }
        root.present(UIActivityViewController(activityItems: [text], applicationActivities: nil), animated: true)
    }

    private func label(_ text: String, _ size: Double, _ weight: Font.Weight,
                       _ opacity: Double = 1) -> some View {
        Text(text).font(.system(size: size, weight: weight))
            .foregroundStyle(Color.white.opacity(opacity))
    }

    private func colour(_ rgb: SIMD3<Float>) -> Color {
        Color(red: Double(rgb.x), green: Double(rgb.y), blue: Double(rgb.z))
    }

    private func enamelTextColour(_ rgb: SIMD3<Float>) -> Color {
        func linear(_ value: Float) -> Double {
            let x = Double(value)
            return x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4)
        }
        let luminance = 0.2126 * linear(rgb.x) + 0.7152 * linear(rgb.y) + 0.0722 * linear(rgb.z)
        return luminance > 0.45 ? Color(red: 0.043, green: 0.043, blue: 0.047) : .white
    }

    private func countWord(_ count: Int) -> String {
        ["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
         "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
         "Seventeen", "Eighteen"][min(18, max(0, count))]
    }
}

#Preview {
    BadgeVaultGridDemo()
}
