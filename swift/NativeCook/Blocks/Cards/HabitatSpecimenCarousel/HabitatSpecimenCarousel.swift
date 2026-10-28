import SwiftUI
import UIKit

private typealias M = HabitatSpecimenCarouselMotion
private typealias Specimen = HabitatSpecimenCarouselSpecimen

/// Habitat Specimen Carousel: three flat-art Pokémon on a Pokédex card. Each one lives in its own
/// habitat, drawn live by Metal; a swipe is a habitat front that wipes one specimen for the next,
/// a tap performs the specimen's signature move, and a hold pets it. Mirrors
/// HabitatSpecimenCarousel.tsx: one clock (`HabitatSpecimenCarouselModel`) drives three stacked Metal
/// layers (environment and particles behind the art, the creatures, effects in front) and the
/// SwiftUI text between them.
struct HabitatSpecimenCarousel: View {
    /// Seconds of the demo's loop.
    static let loop = M.loop

    var autoPlay = true
    /// Freezes the screen at this second of the loop, for review.
    var previewTime: Double?
    var previewReducedMotion = false

    @StateObject private var engine: HabitatSpecimenCarouselEngine

    init(autoPlay: Bool = true, previewTime: Double? = nil, previewReducedMotion: Bool = false) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.previewReducedMotion = previewReducedMotion
        _engine = StateObject(wrappedValue: HabitatSpecimenCarouselEngine(
            autoPlay: autoPlay, previewTime: previewTime, previewReduced: previewReducedMotion ? true : nil
        ))
    }

    var body: some View {
        HabitatSpecimenCarouselScreen(engine: engine, model: engine.model)
            .ignoresSafeArea()
            .background(Color(habitatSC: 0x111D19))
            .preferredColorScheme(.dark)
            .statusBarHidden(false)
    }
}

private struct HabitatSpecimenCarouselScreen: View {
    let engine: HabitatSpecimenCarouselEngine
    @ObservedObject var model: HabitatSpecimenCarouselModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var favorites = [false, false, false]
    /// A tab the person picks holds until the demo itself moves on to another panel.
    @State private var picked: (tab: HabitatSpecimenCarouselTab, over: Int)?

    var body: some View {
        GeometryReader { proxy in
            let insets = Self.safeInsets
            let layout = HabitatSpecimenCarouselLayout(width: proxy.size.width, height: proxy.size.height, insetTop: insets.top, insetBottom: insets.bottom)
            content(layout)
                .onAppear { engine.configure(layout) }
                .onChange(of: layout) { _, next in engine.configure(next) }
        }
        .onAppear {
            model.setSystemReduced(reduceMotion)
            model.setForeground(scenePhase == .active)
            model.setFocused(true)
            model.onSignature = { Self.signatureFeedback() }
            model.onLand = { UISelectionFeedbackGenerator().selectionChanged() }
            model.onPet = { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        }
        .onDisappear { model.setFocused(false) }
        .onChange(of: reduceMotion) { _, value in model.setSystemReduced(value) }
        .onChange(of: scenePhase) { _, phase in model.setForeground(phase == .active) }
    }

    @ViewBuilder
    private func content(_ layout: HabitatSpecimenCarouselLayout) -> some View {
        let scale = CGFloat(layout.scale)
        let selected = model.selectedIndex
        let specimen = Specimen.all[selected]
        let favorite = favorites[selected]
        let reduced = model.reduced
        let tab = picked.flatMap { $0.over == model.demoTab ? $0.tab : nil } ?? HabitatSpecimenCarouselTab(rawValue: model.demoTab)!

        ZStack(alignment: .topLeading) {
            if let renderer = engine.renderer {
                HabitatSpecimenCarouselMetalHost(renderer: renderer, role: .environment)
                    .frame(width: CGFloat(layout.width), height: CGFloat(layout.height))
                    .allowsHitTesting(false)
            } else {
                Color(habitatSC: 0x111D19)
            }

            HabitatSpecimenCarouselGhost(model: model, layout: layout)

            if let renderer = engine.renderer {
                HabitatSpecimenCarouselMetalHost(renderer: renderer, role: .creatures)
                    .frame(width: CGFloat(layout.width), height: CGFloat(layout.overlayHeight))
                    .allowsHitTesting(false)
            }

            HabitatSpecimenCarouselTouchSurface(
                model: model, label: "Pokédex carousel, \(specimen.name)",
                hint: "Swipe horizontally to choose a Pokémon. Tap to see its signature move, or touch and hold to pet it",
                value: specimen.name, onStep: { model.select((selected + ($0 > 0 ? 1 : 2)) % 3) }
            )
            .frame(width: CGFloat(layout.width), height: CGFloat(layout.heroHeight))
            .offset(y: CGFloat(layout.heroTop))

            HabitatSpecimenCarouselEyebrow(model: model, layout: layout, selectedIndex: selected)
            HabitatSpecimenCarouselIdentity(model: model, layout: layout)
            HabitatSpecimenCarouselTypes(model: model, layout: layout, selectedIndex: selected)
            HabitatSpecimenCarouselPanels(model: model, layout: layout, selectedIndex: selected, tab: tab) { next in
                model.holdDemo(6000)
                picked = (next, model.demoTab)
            }
            HabitatSpecimenCarouselFacts(model: model, layout: layout, selectedIndex: selected)
            HabitatSpecimenCarouselFooter(model: model, layout: layout, selectedIndex: selected) { model.select($0) }
            HabitatSpecimenCarouselHint(layout: layout, reduced: reduced, touched: model.touched)

            header(layout, scale: scale, selected: selected, favorite: favorite, name: specimen.name, reduced: reduced)

            if let renderer = engine.renderer {
                HabitatSpecimenCarouselMetalHost(renderer: renderer, role: .overlay)
                    .frame(width: CGFloat(layout.width), height: CGFloat(layout.overlayHeight))
                    .allowsHitTesting(false)
            }
        }
        .frame(width: CGFloat(layout.width), height: CGFloat(layout.height), alignment: .topLeading)
        .clipped()
    }

    private func header(_ layout: HabitatSpecimenCarouselLayout, scale: CGFloat, selected: Int, favorite: Bool, name: String, reduced: Bool) -> some View {
        HStack(spacing: 0) {
            Button { model.select((selected + 2) % 3) } label: {
                HabitatSpecimenCarouselIcon(name: "chevron-left", size: 25 * scale, color: Color(habitatSC: 0xF3F4E9))
            }
            .buttonStyle(HabitatSpecimenCarouselButtonStyle(reduced: reduced))
            .accessibilityLabel("Previous Pokémon")
            Spacer(minLength: 0)
            Button {
                if !favorite { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                favorites[selected].toggle()
            } label: {
                HabitatSpecimenCarouselHeart(on: favorite, size: 22 * scale, reduced: reduced, burst: Color(habitatSC: Specimen.favoriteBurst[selected]))
            }
            .buttonStyle(HabitatSpecimenCarouselButtonStyle(reduced: reduced))
            .accessibilityLabel("\(favorite ? "Remove" : "Add") \(name) \(favorite ? "from" : "to") favorites")
            .accessibilityAddTraits(favorite ? [.isSelected] : [])
        }
        .frame(width: CGFloat(layout.width) - 34 * scale, height: 48)
        .offset(x: 17 * scale, y: CGFloat(layout.insetTop) + 1 * scale)
    }

    /// A light tap as the specimen gathers, the heavier one with the release after the crouch.
    private static func signatureFeedback() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.215) { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    }

    private static var safeInsets: (top: Double, bottom: Double) {
        let window = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow }
        let insets = window?.safeAreaInsets ?? .zero
        return (Double(insets.top), Double(insets.bottom))
    }
}

/// One of the three stacked Metal layers.
private struct HabitatSpecimenCarouselMetalHost: UIViewRepresentable {
    let renderer: HabitatSpecimenCarouselRenderer
    let role: HabitatSpecimenCarouselLayer

    func makeUIView(context: Context) -> HabitatSpecimenCarouselMetalView { renderer.makeView(role) }
    func updateUIView(_ view: HabitatSpecimenCarouselMetalView, context: Context) {}
}
