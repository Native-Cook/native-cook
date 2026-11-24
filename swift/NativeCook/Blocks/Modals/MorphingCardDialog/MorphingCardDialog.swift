import SwiftUI
import UIKit

/// "Weekend tables": a restaurant guide whose cards open into a dialog by morphing.
///
/// One frame travels from the tapped card's rect to the dialog's on a spring; the photo, the
/// title, the subtitle and the "+" ride inside it and the detail content rises out of a blur.
/// In `autoPlay` every frame is a pure function of the loop time (`MorphingCardDialogMotion`);
/// a finger takes the same values over live.
struct MorphingCardDialog: View {
    /// Plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch.
    var autoPlay = true
    var places = MorphingCardDialogPlace.all
    var onReserve: (MorphingCardDialogPlace) -> Void = { _ in }

    static let loop: Double = MorphingCardDialogMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = MorphingCardDialogModel()
    @State private var meal = 1
    @State private var awake = false
    @State private var finger = MorphingCardDialogFinger()

    private typealias M = MorphingCardDialogMotion
    private typealias Art = MorphingCardDialogArt
    private typealias Layout = MorphingCardDialogLayout

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private var playing: Bool { autoPlay && !reducedMotion }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { geometry in
                let layout = Layout(width: geometry.size.width, height: geometry.size.height,
                                    top: insets.top, bottom: insets.bottom, count: places.count)
                TimelineView(.animation(paused: scenePhase != .active || !(playing || awake))) { timeline in
                    let state = model.state(at: timeline.date, playing: playing, reducedMotion: reducedMotion)
                    screen(state, layout)
                }
            }
            .ignoresSafeArea()
        }
        .background(Art.paper.ignoresSafeArea())
        .preferredColorScheme(.light)
        .task(id: awake) {
            // Without the loop the timeline only runs while something moves.
            guard awake, !playing else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                if model.resting { awake = false }
            }
        }
    }

    // MARK: Screen

    private func screen(_ state: MorphingCardDialogState, _ layout: Layout) -> some View {
        let open = M.clamp(state.p, 0, 1)
        return ZStack(alignment: .topLeading) {
            scene(state, layout)
                .scaleEffect(reducedMotion ? 1 : M.mix(1, M.sceneScale, open))

            MorphingCardDialogBackdropBlur(intensity: M.sceneBlur * open)
                .allowsHitTesting(false)
            Art.scrim.opacity(M.sceneDim * open)
                .allowsHitTesting(false)

            // The touch layer only exists while a dialog is out; otherwise touches reach the cards.
            Color.clear
                .contentShape(Rectangle())
                .gesture(dialogGesture(layout))
                .allowsHitTesting(state.active >= 0)

            if state.active >= 0, state.active < places.count {
                frame(state, layout)
                    .allowsHitTesting(false)
            }

            if playing, let ghost = state.ghost, ghost.opacity > 0 {
                ghostDot(ghost, layout)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }

    private func scene(_ state: MorphingCardDialogState, _ layout: Layout) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline) {
                    Text(MorphingCardDialogPlace.brand)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Art.ember)
                    Spacer(minLength: 0)
                    Text("\(places.count) with a table left")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(Art.inkSoft)
                }
                .frame(height: 18)
                Text(MorphingCardDialogPlace.title)
                    .font(.system(size: 32, weight: .heavy))
                    .tracking(-0.4)
                    .foregroundStyle(Art.ink)
                    .frame(height: 38)
                    .padding(.bottom, 10)
                    .accessibilityAddTraits(.isHeader)
                Picker("Meal", selection: $meal) {
                    ForEach(Array(MorphingCardDialogPlace.meals.enumerated()), id: \.offset) { index, label in
                        Text(label).tag(index)
                    }
                }
                .pickerStyle(.segmented)
                .frame(height: 32)
            }
            .frame(width: layout.width - Layout.side * 2, alignment: .leading)
            .offset(x: Layout.side, y: layout.top + 8)

            ForEach(Array(places.enumerated()), id: \.offset) { index, place in
                let rect = layout.cards[index]
                MorphingCardDialogFace(place: place, shape: layout.cardShape(rect), detail: nil)
                    .equatable()
                    .opacity(!reducedMotion && state.active == index ? 0 : 1)
                    .scaleEffect(state.pressed == index ? 1 - (1 - M.pressScale) * state.pressAmount : 1)
                    .contentShape(Rectangle())
                    .gesture(cardGesture(index))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(place.name), \(place.area)")
                    .accessibilityAddTraits(.isButton)
                    .offset(x: rect.minX, y: rect.minY)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    /// The morphing frame: the open card's content at wherever the spring is.
    private func frame(_ state: MorphingCardDialogState, _ layout: Layout) -> some View {
        let source = layout.cards[state.active]
        let target = layout.target
        let q = reducedMotion ? 1 : state.p
        let shape = layout.shape(state, source: source, reducedMotion: reducedMotion)
        let detail = layout.detail(state, source: source, reducedMotion: reducedMotion)
        return MorphingCardDialogFace(place: places[state.active], shape: shape, detail: detail)
            .scaleEffect(1 - (1 - M.pressScale) * state.pressAmount)
            .opacity(reducedMotion ? M.clamp(state.p, 0, 1) : 1)
            .offset(x: M.mix(source.minX, target.minX, q) + state.dragX,
                    y: M.mix(source.minY, target.minY, q) + state.dragY)
    }

    private func ghostDot(_ ghost: MorphingCardDialogGhost, _ layout: Layout) -> some View {
        var point: CGPoint
        switch ghost.spot {
        case .card(let index):
            let rect = layout.cards[min(index, layout.cards.count - 1)]
            point = CGPoint(x: rect.midX, y: rect.minY + rect.height * 0.42)
        case .grab:
            point = CGPoint(x: layout.target.midX, y: layout.target.minY + Layout.ghostGrabY)
        case .close:
            point = CGPoint(x: layout.target.minX + layout.closeHit.midX, y: layout.target.minY + layout.closeHit.midY)
        }
        point.x += ghost.dx
        point.y += ghost.dy
        return Circle()
            .fill(Art.ink.opacity(0.20))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.75), lineWidth: 1.5))
            .shadow(color: Art.ink.opacity(0.18), radius: 5, y: 2)
            .frame(width: Layout.ghost, height: Layout.ghost)
            .scaleEffect(1 - Layout.ghostPress * ghost.press)
            .opacity(ghost.opacity)
            .offset(x: point.x - Layout.ghost / 2, y: point.y - Layout.ghost / 2)
            .allowsHitTesting(false)
    }

    // MARK: Touch

    private func impact() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// A card: it dips while the finger is down and opens when a tap ends on it.
    private func cardGesture(_ index: Int) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !finger.down else { return }
                finger.down = true
                awake = true
                model.touch { live in
                    live.fingers = 1
                    if live.state.active < 0 { live.pressIn(index) }
                }
            }
            .onEnded { value in
                finger.down = false
                let tapped = hypot(value.translation.width, value.translation.height) < 12
                var opened = false
                model.touch { live in
                    live.fingers = 0
                    live.pressOut()
                    if tapped { opened = live.open(index) }
                }
                if opened { impact() }
            }
    }

    /// The open dialog: a drag down shrinks it toward its card, a tap on the "×" or outside closes it.
    private func dialogGesture(_ layout: Layout) -> some Gesture {
        let target = layout.target
        func within(_ point: CGPoint, _ hit: CGRect, _ slop: Double) -> Bool {
            hit.offsetBy(dx: target.minX, dy: target.minY).insetBy(dx: -slop, dy: -slop).contains(point)
        }
        return DragGesture(minimumDistance: 0)
            .onChanged { value in
                let start = value.startLocation
                if !finger.down {
                    finger = MorphingCardDialogFinger(down: true)
                    awake = true
                    model.touch { live in
                        live.fingers = 1
                        guard live.goal == 1 else { return }
                        if within(start, layout.closeHit, Layout.hitSlop) { live.pressClose(true) }
                        else if within(start, layout.actionHit, 0) { live.pressAction(true) }
                    }
                }
                let travel = hypot(value.translation.width, value.translation.height)
                if !finger.panning, travel >= M.dragSlop {
                    // A pan: the buttons let go, and the frame is caught if the finger began on it.
                    finger.panning = true
                    model.touch { live in
                        live.pressClose(false)
                        live.pressAction(false)
                        if live.goal == 1, target.contains(start) { live.dragBegin() }
                    }
                }
                if finger.panning {
                    model.touch { live in
                        if live.dragging { live.dragMove(value.translation.width, value.translation.height) }
                    }
                }
            }
            .onEnded { value in
                let panned = finger.panning
                finger = MorphingCardDialogFinger()
                var closed = false
                var reserved = -1
                model.touch { live in
                    live.fingers = 0
                    live.pressClose(false)
                    live.pressAction(false)
                    if panned {
                        closed = live.dragEnd(value.velocity.width, value.velocity.height)
                    } else if live.goal == 1 {
                        if within(value.location, layout.closeHit, Layout.hitSlop) || !target.contains(value.location) {
                            live.close(0)
                            closed = true
                        } else if within(value.location, layout.actionHit, 0) {
                            reserved = live.state.active
                        }
                    }
                }
                if closed { impact() }
                if reserved >= 0, reserved < places.count {
                    impact()
                    onReserve(places[reserved])
                }
            }
    }
}

/// One finger's progress through a gesture (not drawn, so changing it costs nothing).
private struct MorphingCardDialogFinger {
    var down = false
    var panning = false
}

// MARK: Layout

/// Where everything sits, in points.
struct MorphingCardDialogLayout {
    private typealias M = MorphingCardDialogMotion

    static let side = 16.0
    static let gap = 12.0
    static let header = 124.0
    static let cardText = 60.0 // the strip under a card's photo
    static let cardAspect = 1.22 // a card is never taller than this × its width
    static let dialogSide = 20.0
    static let dialogMax = 380.0
    static let photoRatio = 0.6 // the dialog photo's height / width
    static let imageRatio = 0.75 // the bundled photos are 4:3
    static let padCard = 12.0
    static let padDialog = 20.0
    // Title and subtitle are set once at the dialog's size and scaled down for the card.
    static let titleSize = 26.0
    static let titleLine = 31.0
    static let titleScale = 15.0 / titleSize
    static let subtitleSize = 15.0
    static let subtitleLine = 20.0
    static let subtitleScale = 13.0 / subtitleSize
    static let titleTop = (card: 9.0, dialog: 18.0) // below the photo
    static let subtitleTop = (card: 30.0, dialog: 52.0)
    // The "+" that turns into the "×".
    static let plus = 30.0
    static let plusInset = (card: 40.0, dialogX: 44.0, dialogY: 12.0)
    static let closeScale = 32.0 / plus
    static let plusTurn = 135.0 // degrees
    // Detail content, measured down from the dialog photo's bottom edge.
    static let bodyTop = 86.0
    static let bodyLine = 21.0
    static let factsTop = 163.0
    static let factsHeight = 54.0
    static let actionTop = 233.0
    static let actionHeight = 50.0
    static let detailHeight = 303.0
    // The ghost finger.
    static let ghost = 46.0
    static let ghostPress = 0.16
    static let ghostGrabY = 110.0
    static let hitSlop = 8.0

    let width: Double
    let height: Double
    let top: Double
    var cards: [CGRect] = []
    let target: CGRect
    let photoHeight: Double
    /// The buttons' places inside the open dialog.
    let closeHit: CGRect
    let actionHit: CGRect

    init(width: Double, height: Double, top: Double, bottom: Double, count: Int) {
        self.width = width
        self.height = height
        self.top = top
        let gridTop = top + Self.header
        let gridBottom = height - max(bottom, 12) - 4
        let rows = max(1, Int((Double(count) / 2).rounded(.up)))
        let cardWidth = (width - Self.side * 2 - Self.gap) / 2
        let cardHeight = min((gridBottom - gridTop - Self.gap * Double(rows - 1)) / Double(rows), cardWidth * Self.cardAspect)
        for i in 0..<count {
            cards.append(CGRect(x: Self.side + Double(i % 2) * (cardWidth + Self.gap),
                                y: gridTop + Double(i / 2) * (cardHeight + Self.gap),
                                width: cardWidth, height: cardHeight))
        }
        let dialogWidth = min(width - Self.dialogSide * 2, Self.dialogMax)
        photoHeight = (dialogWidth * Self.photoRatio).rounded()
        let dialogHeight = photoHeight + Self.detailHeight
        target = CGRect(x: (width - dialogWidth) / 2, y: top + max(8, (height - top - bottom - dialogHeight) / 2),
                        width: dialogWidth, height: dialogHeight)
        closeHit = CGRect(x: dialogWidth - Self.plusInset.dialogX - 1, y: Self.plusInset.dialogY - 1,
                          width: Self.plus + 2, height: Self.plus + 2)
        actionHit = CGRect(x: Self.padDialog, y: photoHeight + Self.actionTop,
                           width: dialogWidth - Self.padDialog * 2, height: Self.actionHeight)
    }

    var textWidth: Double { target.width - Self.padDialog * 2 }

    /// A card at rest: exactly the frame at progress 0, so the hand-over cannot be seen.
    func cardShape(_ rect: CGRect) -> MorphingCardDialogShape {
        shape(MorphingCardDialogState(), source: rect, reducedMotion: false)
    }

    /// The frame and the parts that ride in it, for a dialog that came out of `source`.
    func shape(_ state: MorphingCardDialogState, source: CGRect, reducedMotion: Bool) -> MorphingCardDialogShape {
        let q = reducedMotion ? 1 : state.p
        let u = M.clamp(q, 0, 1)
        let title = reducedMotion ? 1 : state.titleFollow
        let subtitle = reducedMotion ? 1 : state.subtitleFollow
        let frameWidth = max(1, M.mix(source.width, target.width, q))
        let edge = max(1, M.mix(source.height - Self.cardText, photoHeight, q))
        // The photo always covers its box.
        let imageWidth = target.width, imageHeight = target.width * Self.imageRatio
        let k = max(frameWidth / imageWidth, edge / imageHeight)
        return MorphingCardDialogShape(
            width: frameWidth,
            height: max(1, M.mix(source.height, target.height, q)),
            radius: M.mix(M.radiusCard, M.radiusDialog, u),
            lifted: u,
            photoHeight: edge,
            image: CGRect(x: (frameWidth - imageWidth * k) / 2, y: (edge - imageHeight * k) / 2,
                          width: imageWidth * k, height: imageHeight * k),
            textWidth: textWidth,
            titleX: M.mix(Self.padCard, Self.padDialog, title),
            titleY: edge + M.mix(Self.titleTop.card, Self.titleTop.dialog, q),
            titleScale: M.mix(Self.titleScale, 1, title),
            subtitleX: M.mix(Self.padCard, Self.padDialog, subtitle),
            subtitleY: edge + M.mix(Self.subtitleTop.card, Self.subtitleTop.dialog, q),
            subtitleScale: M.mix(Self.subtitleScale, 1, subtitle),
            // "+" rides up the right edge and turns into "×", a beat behind the frame.
            plusX: frameWidth - M.mix(Self.plusInset.card, Self.plusInset.dialogX, q),
            plusY: M.mix(source.height - Self.plusInset.card, Self.plusInset.dialogY, q),
            plusTurn: Self.plusTurn * title,
            plusScale: M.mix(1, Self.closeScale, u) * (1 - 0.1 * state.closePress),
            plusGlass: u)
    }

    /// Body, facts and button: each comes up out of a blur once the frame is nearly open.
    func detail(_ state: MorphingCardDialogState, source: CGRect, reducedMotion: Bool) -> MorphingCardDialogDetail {
        let gate = reducedMotion ? M.clamp(state.p, 0, 1) : M.seg(state.p, M.contentGate.0, M.contentGate.1)
        var detail = MorphingCardDialogDetail(actionScale: 1 - 0.03 * state.actionPress)
        for order in 0..<3 {
            let start = M.contentStart[order]
            let u = M.easeOutCubic(M.seg(state.content, start, start + M.contentSpan)) * gate
            detail.shown[order] = u
            detail.away[order] = reducedMotion ? 0 : 1 - u
        }
        return detail
    }
}

struct MorphingCardDialogShape: Equatable {
    var width: Double
    var height: Double
    var radius: Double
    /// 0…1: the lifted dialog's shadow.
    var lifted: Double
    var photoHeight: Double
    /// The photo's frame inside its box.
    var image: CGRect
    var textWidth: Double
    var titleX: Double
    var titleY: Double
    var titleScale: Double
    var subtitleX: Double
    var subtitleY: Double
    var subtitleScale: Double
    var plusX: Double
    var plusY: Double
    var plusTurn: Double
    var plusScale: Double
    /// 0 = the solid "+", 1 = the glassy "×".
    var plusGlass: Double
}

struct MorphingCardDialogDetail: Equatable {
    /// Opacity of body, facts and button, and how far each still is from its place (1 = 14 pt low, 8 pt blur).
    var shown = [0.0, 0.0, 0.0]
    var away = [1.0, 1.0, 1.0]
    var actionScale = 1.0
}

// MARK: The card and the dialog, one drawing

/// What is inside the frame. A card at rest is this at progress 0 without `detail`.
struct MorphingCardDialogFace: View, Equatable {
    let place: MorphingCardDialogPlace
    let shape: MorphingCardDialogShape
    let detail: MorphingCardDialogDetail?

    private typealias M = MorphingCardDialogMotion
    private typealias Art = MorphingCardDialogArt
    private typealias Layout = MorphingCardDialogLayout

    static func == (a: Self, b: Self) -> Bool {
        a.place.key == b.place.key && a.shape == b.shape && a.detail == b.detail
    }

    var body: some View {
        let outline = RoundedRectangle(cornerRadius: shape.radius, style: .continuous)
        ZStack(alignment: .topLeading) {
            // Two shadows that cross over: the card's stays, the lifted dialog's fades in.
            if shape.lifted > 0 {
                Group {
                    outline.fill(Art.card).shadow(color: Art.lift.opacity(0.30), radius: 28, y: 28)
                    outline.fill(Art.card).shadow(color: Art.lift.opacity(0.16), radius: 3, y: 2)
                }
                .opacity(shape.lifted)
            }
            outline.fill(Art.card).shadow(color: Art.rest.opacity(0.08), radius: 10, y: 8)
            outline.fill(Art.card).shadow(color: Art.rest.opacity(0.10), radius: 1, y: 1)

            ZStack(alignment: .topLeading) {
                Image(uiImage: place.photo)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: shape.image.width, height: shape.image.height)
                    .offset(x: shape.image.minX, y: shape.image.minY)
                    .frame(width: shape.width, height: shape.photoHeight, alignment: .topLeading)
                    .background(Art.photoBack)
                    .clipped()

                Text(place.name)
                    .font(.system(size: Layout.titleSize, weight: .bold))
                    .tracking(-0.3)
                    .foregroundStyle(Art.ink)
                    .lineLimit(1)
                    .frame(width: shape.textWidth, height: Layout.titleLine, alignment: .leading)
                    .scaleEffect(shape.titleScale, anchor: .topLeading)
                    .offset(x: shape.titleX, y: shape.titleY)
                Text(place.area)
                    .font(.system(size: Layout.subtitleSize, weight: .medium))
                    .foregroundStyle(Art.inkSoft)
                    .lineLimit(1)
                    .frame(width: shape.textWidth, height: Layout.subtitleLine, alignment: .leading)
                    .scaleEffect(shape.subtitleScale, anchor: .topLeading)
                    .offset(x: shape.subtitleX, y: shape.subtitleY)

                if let detail {
                    rise(detail, 0, top: Layout.bodyTop, height: Layout.bodyLine * 3) { bodyText }
                    rise(detail, 1, top: Layout.factsTop, height: Layout.factsHeight) { facts }
                    rise(detail, 2, top: Layout.actionTop, height: Layout.actionHeight) {
                        action.scaleEffect(detail.actionScale)
                    }
                }

                plus
            }
            .frame(width: shape.width, height: shape.height, alignment: .topLeading)
            .clipShape(outline)
        }
        .frame(width: shape.width, height: shape.height, alignment: .topLeading)
    }

    @ViewBuilder
    private func rise(_ detail: MorphingCardDialogDetail, _ order: Int, top: Double, height: Double,
                      @ViewBuilder content: () -> some View) -> some View {
        if detail.shown[order] > 0 {
            content()
                .frame(width: shape.textWidth, height: height, alignment: .topLeading)
                .blur(radius: detail.away[order] * M.contentBlur)
                .opacity(detail.shown[order])
                .offset(x: Layout.padDialog, y: shape.photoHeight + top + detail.away[order] * M.contentRise)
        }
    }

    private var bodyText: some View {
        Text(place.about)
            .font(.system(size: 15))
            .lineSpacing(Layout.bodyLine - 17.9)
            .foregroundStyle(Art.body)
            .lineLimit(3)
            .frame(width: shape.textWidth, alignment: .topLeading)
            .padding(.top, (Layout.bodyLine - 17.9) / 2)
    }

    private var facts: some View {
        HStack(spacing: 0) {
            ForEach(Array(place.facts.enumerated()), id: \.offset) { index, fact in
                VStack(spacing: 1) {
                    Text(fact.value)
                        .font(.system(size: 16, weight: .bold).monospacedDigit())
                        .foregroundStyle(Art.ink)
                    Text(fact.label)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Art.inkSoft)
                }
                .frame(maxWidth: .infinity)
                .overlay(alignment: .leading) {
                    if index > 0 { Art.ink.opacity(0.10).frame(width: 1 / 3) }
                }
            }
        }
        .frame(width: shape.textWidth, height: Layout.factsHeight)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Art.fill))
    }

    private var action: some View {
        ZStack {
            Capsule().fill(Art.ember).shadow(color: Art.ember.opacity(0.28), radius: 11, y: 10)
            Capsule().fill(Art.ember).shadow(color: Art.emberDeep.opacity(0.30), radius: 1, y: 1)
            MorphingCardDialogTopLight(opacity: 0.22)
            Text(MorphingCardDialogPlace.reserve)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Art.onEmber)
        }
        .frame(width: shape.textWidth, height: Layout.actionHeight)
    }

    private var plus: some View {
        ZStack {
            Circle()
                .fill(Art.ink.opacity(M.mix(1, 0.58, shape.plusGlass)))
                .shadow(color: Art.ink.opacity(0.22), radius: 3, y: 2)
            MorphingCardDialogTopLight(opacity: 0.14)
            Capsule().fill(Art.plusBar).frame(width: 12, height: 1.8)
            Capsule().fill(Art.plusBar).frame(width: 1.8, height: 12)
        }
        .frame(width: Layout.plus, height: Layout.plus)
        .scaleEffect(shape.plusScale)
        .rotationEffect(.degrees(shape.plusTurn))
        .offset(x: shape.plusX, y: shape.plusY)
    }
}

/// A one-point highlight along the top inside edge of a capsule (an inset shadow moved down by 1).
private struct MorphingCardDialogTopLight: View {
    let opacity: Double

    var body: some View {
        Capsule()
            .fill(Color.white.opacity(opacity))
            .mask {
                ZStack {
                    Capsule()
                    Capsule().offset(y: 1).blendMode(.destinationOut)
                }
                .compositingGroup()
            }
            .allowsHitTesting(false)
    }
}

// MARK: Palette

/// One warm neutral family, one accent.
enum MorphingCardDialogArt {
    static let paper = color(0xF3EEE6)
    static let card = color(0xFFFCF7)
    static let ink = color(0x1B1713)
    static let inkSoft = color(0x857B6F)
    static let body = color(0x4A423A)
    static let fill = color(0xF2ECE3)
    static let ember = color(0xBF4A26)
    static let emberDeep = color(0x78280E)
    static let onEmber = color(0xFFF8F1)
    static let plusBar = color(0xFBF7F0)
    static let photoBack = color(0x2A221B)
    static let scrim = color(0x1A130C)
    /// Shadow tints of the resting card and of the lifted dialog.
    static let rest = color(0x3A2818)
    static let lift = color(0x281A0E)

    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: 1)
    }
}

// MARK: Backdrop

/// A live backdrop blur of a given strength (0 to 100): a `UIVisualEffectView` whose light blur
/// effect is paused part-way through its animation.
private struct MorphingCardDialogBackdropBlur: UIViewRepresentable {
    let intensity: Double

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: nil)
        view.isUserInteractionEnabled = false
        context.coordinator.attach(to: view)
        context.coordinator.apply(intensity, to: view)
        return view
    }

    func updateUIView(_ view: UIVisualEffectView, context: Context) {
        context.coordinator.apply(intensity, to: view)
    }

    static func dismantleUIView(_ view: UIVisualEffectView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator {
        private var animator: UIViewPropertyAnimator?

        func attach(to view: UIVisualEffectView) {
            stop()
            view.effect = nil
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) {
                view.effect = UIBlurEffect(style: .light)
            }
            animator.pausesOnCompletion = true
            self.animator = animator
        }

        func apply(_ intensity: Double, to view: UIVisualEffectView) {
            // The system ends a paused animator when the app leaves the screen: start another.
            if animator?.state != .active, animator?.state != .inactive { attach(to: view) }
            view.isHidden = intensity <= 0.01
            animator?.fractionComplete = CGFloat(max(0.001, min(1, intensity / 100)))
        }

        func stop() {
            guard let animator else { return }
            if animator.state == .active {
                animator.stopAnimation(false)
                animator.finishAnimation(at: .current)
            }
            self.animator = nil
        }

        deinit { stop() }
    }
}
