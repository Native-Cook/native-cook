import SwiftUI

/// "Latest spending": swipe a row left to flag or delete it; it leans with your finger.
struct TiltSwipeActions: View {
    /// Plays a 10 s loop: Rent is opened, Groceries is swiped away and brought back with Undo, Corner
    /// Café is leaned on and let go. A touch interrupts it; it starts again from rest after the last one.
    var autoPlay: Bool
    /// Seconds: `LOOP_MS` of the React Native block, 10 000 ms.
    // TiltSwipeActionsTokens.LOOP_MS / 1000; a literal so the catalog check can read it
    static let loop: Double = 10

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        TiltSwipeActionsFonts.register()
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var model = TiltSwipeActionsModel()

    var body: some View {
        // The block draws under the system bars.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            ZStack(alignment: .topLeading) {
                TiltSwipeActionsDiagonalGradient(colors: TiltSwipeActionsColor.desk, locations: [0, 1])
                TiltSwipeActionsPlane(model: model, autoPlay: autoPlay, reduceMotion: reduceMotion, displayScale: displayScale)
                TiltSwipeActionsTouchSurface(model: model)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(TiltSwipeActionsColor.gradient[0].ignoresSafeArea())
        .onAppear { model.start(autoPlay: autoPlay, reduceMotion: reduceMotion) }
        .onDisappear { model.stop() }
        .onChange(of: reduceMotion) { _, new in model.reduceMotion = new }
    }
}

/// `LinearGradient` from (0, 0) to (1, 1) the way the React Native block's expo-linear-gradient draws
/// it: in the unit square, then stretched to the box, so the colour lines run along the box's other
/// diagonal instead of staying square to the first.
struct TiltSwipeActionsDiagonalGradient: View {
    let colors: [Color]
    let locations: [CGFloat]

    var body: some View {
        Canvas { context, size in
            var unit = context
            unit.scaleBy(x: size.width, y: size.height)
            let gradient = Gradient(stops: zip(colors, locations).map { Gradient.Stop(color: $0, location: $1) })
            unit.fill(
                Path(CGRect(x: 0, y: 0, width: 1, height: 1)),
                with: .linearGradient(gradient, startPoint: .zero, endPoint: CGPoint(x: 1, y: 1))
            )
        }
    }
}

/// The app, laid out on the React Native block's 406 x 888 design plane and seen at its source angle.
struct TiltSwipeActionsPlane: View {
    let model: TiltSwipeActionsModel
    let autoPlay: Bool
    let reduceMotion: Bool
    let displayScale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                gradient: Gradient(stops: zip(TiltSwipeActionsColor.gradient, TiltSwipeActionsColor.gradientStops).map {
                    Gradient.Stop(color: $0, location: $1)
                }),
                startPoint: .top,
                endPoint: .bottom
            )
            TiltSwipeActionsOverviewLayer(model: model, reduceMotion: reduceMotion)
            TiltSwipeActionsSpendingLayer(model: model, reduceMotion: reduceMotion, displayScale: displayScale)
            TiltSwipeActionsUndoPill(model: model, reduceMotion: reduceMotion)
            TiltSwipeActionsTabPanel(model: model, reduceMotion: reduceMotion, displayScale: displayScale)
            if autoPlay {
                TiltSwipeActionsFinger(model: model)
            }
        }
        .frame(width: TiltSwipeActionsFlat.planeWidth, height: TiltSwipeActionsFlat.planeHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: TiltSwipeActionsTiltView.screenRadius, style: .circular))
        .transformEffect(TiltSwipeActionsGeometry.plane)
        .allowsHitTesting(false)
    }
}

/// Overview tab: the cards slide up and fade out as Spending comes in.
private struct TiltSwipeActionsOverviewLayer: View {
    let model: TiltSwipeActionsModel
    let reduceMotion: Bool

    var body: some View {
        let shown = model.tabShown.value
        TiltSwipeActionsCardsView(model: model)
            .opacity(1 - shown)
            .offset(y: shown * -TiltSwipeActionsMotion.Timing.tabSlide)
            .accessibilityHidden(model.tab != 0)
    }
}

/// Spending tab: the account card, the title and the rows.
private struct TiltSwipeActionsSpendingLayer: View {
    let model: TiltSwipeActionsModel
    let reduceMotion: Bool
    let displayScale: CGFloat

    var body: some View {
        let shown = model.tabShown.value
        let menu = min(max(model.menu.value, 0), 1)
        let flat = TiltSwipeActionsFlat.self
        ZStack(alignment: .topLeading) {
            TiltSwipeActionsBalanceCard(model: model)
                .frame(width: flat.width - flat.card.left - flat.card.right, height: flat.card.height)
                .offset(x: flat.card.left, y: flat.card.top)
            TiltSwipeActionsLabel(
                text: "Latest spending",
                style: TiltSwipeActionsType.section,
                color: TiltSwipeActionsColor.title
            )
            .accessibilityAddTraits(.isHeader)
            .offset(x: flat.section.left, y: flat.section.centreY - TiltSwipeActionsType.section.lineHeight / 2)
            VStack(spacing: 0) {
                ForEach(model.rows, id: \.key) { row in
                    TiltSwipeActionsRowView(model: model, row: row, reduceMotion: reduceMotion, displayScale: displayScale)
                }
            }
            .frame(width: flat.width, alignment: .topLeading)
            .frame(height: flat.panel.top - TiltSwipeActionsTokens.Panel.rowsBottomGap - flat.rowsTop, alignment: .topLeading)
            .clipped()
            .offset(y: flat.rowsTop)
        }
        .frame(width: flat.planeWidth, height: flat.planeHeight, alignment: .topLeading)
        .opacity(shown * (1 - TiltSwipeActionsTokens.Panel.listFade * menu))
        .offset(y: (1 - shown) * TiltSwipeActionsMotion.Timing.tabSlide - TiltSwipeActionsTokens.Panel.listLift * menu)
        .accessibilityHidden(model.tab != 2)
    }
}

/// The demo's finger.
private struct TiltSwipeActionsFinger: View {
    let model: TiltSwipeActionsModel

    var body: some View {
        let size = TiltSwipeActionsLayout.finger
        Circle()
            .fill(TiltSwipeActionsColor.finger)
            .overlay(Circle().strokeBorder(TiltSwipeActionsColor.fingerEdge, lineWidth: TiltSwipeActionsTokens.Finger.border))
            .frame(width: size, height: size)
            .scaleEffect(1 - TiltSwipeActionsTokens.Finger.pressScale * model.ghostPress.value)
            .offset(x: model.ghostX - size / 2, y: model.ghostY - size / 2)
            .opacity(model.ghostOpacity.value)
            .accessibilityHidden(true)
    }
}
