import SwiftUI

enum CutoutWindowCardIcon {
    case snow, peak, tree, house, sun, sail, glass, fork
}

struct CutoutWindowCardDetail: Equatable {
    var label: String
    var value: String
}

struct CutoutWindowCardPage: Equatable {
    /// One or two lines, separated by a line break.
    var title: String
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
    /// The four small lines at the bottom left.
    var details: [CutoutWindowCardDetail]
    /// The four pictograms at the bottom right.
    var icons: [CutoutWindowCardIcon]

    static let samples = [
        CutoutWindowCardPage(
            title: "Winter\nBreaks",
            photo: "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=680&h=1160&q=80",
            tint: Color(hex: 0x6f7391),
            details: [
                .init(label: "DATE", value: "11 Apr 2024"), .init(label: "FROM", value: "London Gatwick"),
                .init(label: "FOR", value: "14 Nights"), .init(label: "BOARD", value: "Half Board"),
            ],
            icons: [.snow, .peak, .tree, .house]
        ),
        CutoutWindowCardPage(
            title: "Villa\nEscapes",
            photo: "https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=680&h=1160&q=80",
            tint: Color(hex: 0xa98a78),
            details: [
                .init(label: "DATE", value: "03 May 2024"), .init(label: "FROM", value: "London Gatwick"),
                .init(label: "FOR", value: "4 Nights"), .init(label: "BOARD", value: "Self-Catering"),
            ],
            icons: [.house, .sun, .sail, .glass]
        ),
        CutoutWindowCardPage(
            title: "Summer\nHot Spots",
            photo: "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=680&h=1160&q=80",
            tint: Color(hex: 0x4a9d97),
            details: [
                .init(label: "DATE", value: "19 Jul 2024"), .init(label: "FROM", value: "London Gatwick"),
                .init(label: "FOR", value: "7 Nights"), .init(label: "BOARD", value: "Room Only"),
            ],
            icons: [.sun, .sail, .tree, .fork]
        ),
    ]
}

/// A photo panel with a cream card standing in its middle. The top of the card is a window onto the
/// same photo, lined up with the one behind. A sideways swipe turns the page: the photos slide, the
/// card turns around its vertical axis with the speed of the slide and straightens as it settles,
/// the picture in the window slides at its own slower rate, and the texts roll over.
struct CutoutWindowCard: View {
    /// The pages, in a ring. At least three.
    var pages = CutoutWindowCardPage.samples
    /// Turns a page every three seconds, as if a finger flicked it.
    var autoPlay = false
    /// Called with the index of the page the panel is settling on.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pager = Pager()
    /// Keeps the timeline running while a touch is being followed.
    @State private var awake = false
    @State private var touches = 0
    @State private var turns = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !auto && !awake)) { timeline in
            let count = pages.count
            let _ = pager.advance(to: timeline.date, count: count)
            let pos = pager.pos
            let tilt = reduceMotion ? 0 : pager.tilt
            let current = wrapIndex(Int(pager.target.rounded()), count)

            ZStack {
                ForEach(pages.indices, id: \.self) { index in
                    let place = relative(index, pos, count)
                    Photo(url: pages[index].photo, tint: pages[index].tint)
                        .frame(width: Metrics.width, height: Metrics.height)
                        .clipped()
                        .offset(x: reduceMotion ? 0 : place * Metrics.width)
                        .opacity(reduceMotion ? crossFade(place) : 1)
                        .zIndex(abs(place) > 0.5 ? 1 : 0)
                }
                // The photo behind is a little darker than the picture in the window.
                Color.rgba(8, 14, 11, Metrics.scrim).zIndex(2)

                card(pos: pos, tilt: tilt, count: count)
                    .rotation3DEffect(
                        .degrees(tilt), axis: (x: 0, y: 1, z: 0),
                        perspective: Metrics.cardHeight / Metrics.perspective
                    )
                    .zIndex(3)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
            .contentShape(Rectangle())
            .gesture(drag(count: count))
            .onChange(of: current) { onChange?(current) }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(pages[current].title.replacingOccurrences(of: "\n", with: " "))
            .accessibilityValue(pages[current].details.map { "\($0.label) \($0.value)" }.joined(separator: ", "))
            .accessibilityAdjustableAction { direction in
                turn(by: direction == .increment ? 1 : -1)
            }
        }
        .sensoryFeedback(.selection, trigger: turns)
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .task(id: touches) {
            guard touches > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.userHold))
            awake = false
        }
    }

    private func card(pos: Double, tilt: Double, count: Int) -> some View {
        ZStack(alignment: .topLeading) {
            // The card's side: it shows on the edge that turns towards the viewer.
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .fill(Color(hex: 0xd3cbb7))
                .offset(x: -tilt * Metrics.edge)
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .fill(Color(hex: 0xf7f3ea))
                .shadow(color: .black.opacity(0.34), radius: 22, y: 14)

            // The window: one slide per page, each holding the photo where the panel shows it.
            ZStack(alignment: .topLeading) {
                ForEach(pages.indices, id: \.self) { index in
                    let place = relative(index, pos, count)
                    Photo(url: pages[index].photo, tint: pages[index].tint)
                        .frame(width: Metrics.width, height: Metrics.height)
                        .clipped()
                        .offset(
                            x: -Metrics.windowX - (reduceMotion ? 0 : place * Metrics.windowDrift),
                            y: -Metrics.windowY
                        )
                        .frame(width: Metrics.windowWidth, height: Metrics.windowHeight, alignment: .topLeading)
                        .clipped()
                        .offset(x: reduceMotion ? 0 : place * Metrics.windowWidth)
                        .opacity(reduceMotion ? crossFade(place) : 1)
                        .zIndex(abs(place) > 0.5 ? 1 : 0)
                }
            }
            .frame(width: Metrics.windowWidth, height: Metrics.windowHeight, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.windowRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Metrics.windowRadius, style: .continuous)
                    .strokeBorder(Color(hex: 0x0f3d2a, opacity: 0.16), lineWidth: 1)
            )
            .offset(x: Metrics.windowInset, y: Metrics.windowInset)

            // The title rolls up and out of its box as the next one rolls in from below.
            ZStack(alignment: .topLeading) {
                ForEach(pages.indices, id: \.self) { index in
                    let place = relative(index, pos, count)
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(pages[index].title.components(separatedBy: "\n"), id: \.self) { line in
                            Text(line).frame(height: Metrics.titleLine)
                        }
                    }
                    .font(.system(size: 31, weight: .heavy))
                    .tracking(-1.3)
                    .lineLimit(1)
                    .fixedSize()
                    .offset(y: 2 + (reduceMotion ? 0 : place * Metrics.titleBox))
                    .opacity(rollFade(place))
                }
            }
            .frame(width: Metrics.cardWidth - 24, height: Metrics.titleBox, alignment: .topLeading)
            .clipped()
            .offset(x: 12, y: Metrics.titleY)

            ZStack(alignment: .topLeading) {
                ForEach(pages.indices, id: \.self) { index in
                    let place = relative(index, pos, count)
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(pages[index].details, id: \.label) { detail in
                            (Text(detail.label).fontWeight(.heavy) + Text(" " + detail.value))
                                .frame(height: Metrics.detailLine)
                        }
                    }
                    .font(.system(size: 7.5, weight: .medium))
                    .lineLimit(1)
                    .fixedSize()
                    .offset(y: reduceMotion ? 0 : place * Metrics.detailsBox)
                    .opacity(rollFade(place))
                }
            }
            .frame(width: Metrics.detailsWidth, height: Metrics.detailsBox, alignment: .topLeading)
            .clipped()
            .offset(x: 12, y: Metrics.cardHeight - 14 - Metrics.detailsBox)

            // The pictograms change one after another.
            ForEach(pages.indices, id: \.self) { index in
                let place = relative(index, pos, count)
                HStack(spacing: Metrics.iconGap) {
                    ForEach(Array(pages[index].icons.enumerated()), id: \.offset) { order, icon in
                        let shown = popFade(place, order)
                        Glyph(icon: icon)
                            .scaleEffect(reduceMotion ? 1 : 0.6 + 0.4 * shown)
                            .opacity(shown)
                    }
                }
                .offset(x: Metrics.cardWidth - 12 - Metrics.iconsWidth, y: Metrics.cardHeight - 14 - Metrics.icon)
            }

            // Turned away from the light, the face gets a little darker.
            RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                .fill(.black.opacity(abs(tilt) * 0.006))
        }
        .foregroundStyle(Color(hex: 0x0f3d2a))
        .frame(width: Metrics.cardWidth, height: Metrics.cardHeight, alignment: .topLeading)
    }

    private func drag(count: Int) -> some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                touch()
                if !pager.touching {
                    pager.touching = true
                    pager.grabbed = pager.pos
                }
                // One page at most per swipe.
                let home = pager.grabbed.rounded()
                pager.pos = min(home + 1, max(home - 1, pager.grabbed - value.translation.width / Metrics.dragSpan))
            }
            .onEnded { _ in
                guard pager.touching else { return }
                pager.touching = false
                let home = pager.grabbed.rounded()
                let landing = (pager.pos + pager.velocity * Metrics.throwTime).rounded()
                let next = min(home + 1, max(home - 1, landing))
                if next != pager.target.rounded() { turns += 1 }
                pager.target = next
            }
    }

    private func sync() {
        pager.looping = auto
        pager.hold = 0
        pager.clock = 0
        pager.base = pager.target
    }

    /// A touch takes over from the loop, which starts again from rest a little later.
    private func touch() {
        pager.hold = Metrics.userHold
        awake = true
        touches += 1
    }

    private func turn(by pages: Double) {
        touch()
        pager.target = pager.target.rounded() + pages
        turns += 1
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 580.0
    static let radius = 28.0
    static let cardWidth = 196.0
    static let cardHeight = 300.0
    static let cardRadius = 8.0
    static let cardX = (width - cardWidth) / 2
    static let cardY = (height - cardHeight) / 2
    static let windowInset = 10.0
    static let windowWidth = cardWidth - windowInset * 2
    static let windowHeight = 118.0
    static let windowRadius = 3.0
    /// The window's corner, measured in the panel.
    static let windowX = cardX + windowInset
    static let windowY = cardY + windowInset
    /// Points the picture lags behind its slide of the window over one page.
    static let windowDrift = 48.0
    /// Opacity of the dark veil over the photo behind the card.
    static let scrim = 0.16

    // The box the titles roll through, and the one of the four small lines.
    static let titleY = 138.0
    static let titleLine = 30.0
    static let titleBox = 64.0
    static let detailLine = 10.5
    static let detailsBox = detailLine * 4
    static let icon = 14.0
    static let iconGap = 4.0
    static let iconsWidth = icon * 4 + iconGap * 3
    static let detailsWidth = cardWidth - 24 - iconsWidth - 6

    /// Distance of the eye from the card, in points: sets how strong the perspective is.
    static let perspective = 800.0
    /// Degrees the card turns per page per second of slide, and the most it turns.
    static let tiltGain = 2.6
    static let tiltMax = 13.0
    /// How fast the turn follows the speed, per second.
    static let tiltRate = 12.0
    /// Points of the card's side that show per degree of turn.
    static let edge = 0.34

    // The spring that carries the pages to rest (mass 1).
    static let stiffness = 150.0
    static let damping = 20.0
    /// Finger travel that turns one page.
    static let dragSpan = 260.0
    /// A release lands on the page the slide would reach after this many seconds at its speed.
    static let throwTime = 0.18

    // autoPlay (seconds): the three default pages go round once per `loop`, the first flick at
    // `first` and the next ones every `step`.
    static let loop = 9.0
    static let first = 0.8
    static let step = loop / 3
    /// After a touch the loop waits this long before it starts again.
    static let userHold = 2.5
}

/// Per-frame state of the pages. `pos` is the page in view, with fractions while it slides.
private final class Pager {
    var last: Date? = nil
    var pos = 0.0
    var target = 0.0
    /// Pages per second.
    var velocity = 0.0
    /// Degrees the card is turned around its vertical axis.
    var tilt = 0.0
    var looping = false
    /// Where the loop started counting from, and its clock.
    var base = 0.0
    var clock = 0.0
    /// Seconds the loop still waits after a touch.
    var hold = 0.0
    var touching = false
    /// Where the pages were when the finger took them.
    var grabbed = 0.0
    private var previous = 0.0

    func advance(to date: Date, count: Int) {
        guard let last else {
            self.last = date
            return
        }
        let dt = min(date.timeIntervalSince(last), 0.064)
        guard dt > 0 else { return }
        self.last = date
        let n = Double(count)

        if looping, hold <= 0 {
            let loop = Metrics.step * n
            clock += dt
            if clock >= loop {
                // Once round the ring: the same pages under smaller numbers.
                clock -= loop
                pos -= n
                previous -= n
            }
            target = base + flicks(clock, count: count)
        } else if hold > 0, !touching {
            hold -= dt
            if hold <= 0 {
                if pos == target {
                    base = Double(wrapIndex(Int(pos.rounded()), count))
                    pos = base
                    previous = base
                    target = base
                    clock = 0
                } else {
                    hold = 0.01
                }
            }
        }

        if touching {
            // The finger moves the pages; keep a smoothed speed for the release and the turn.
            velocity += ((pos - previous) / dt - velocity) * min(1, dt * 30)
        } else {
            velocity += (Metrics.stiffness * (target - pos) - Metrics.damping * velocity) * dt
            pos += velocity * dt
            if abs(target - pos) < 0.0004, abs(velocity) < 0.004 {
                pos = target
                velocity = 0
            }
        }
        previous = pos

        let lean = min(Metrics.tiltMax, max(-Metrics.tiltMax, -velocity * Metrics.tiltGain))
        tilt += (lean - tilt) * (1 - exp(-dt * Metrics.tiltRate))
        if velocity == 0, abs(tilt) < 0.01 { tilt = 0 }
    }
}

private func wrapIndex(_ index: Int, _ count: Int) -> Int {
    ((index % count) + count) % count
}

/// Pages flicked `clock` seconds into the autoPlay loop.
private func flicks(_ clock: Double, count: Int) -> Double {
    var done = 0.0
    for index in 0..<count where clock >= Metrics.first + Metrics.step * Double(index) { done += 1 }
    return done
}

/// Where page `index` stands: 0 in view, 1 one page to the right, −1 one page to the left.
private func relative(_ index: Int, _ pos: Double, _ count: Int) -> Double {
    let n = Double(count)
    var place = (Double(index) - pos).truncatingRemainder(dividingBy: n)
    if place <= -n / 2 { place += n }
    if place > n / 2 { place -= n }
    return place
}

private func smooth(_ t: Double) -> Double {
    let x = min(1, max(0, t))
    return x * x * (3 - 2 * x)
}

/// Opacity of a text that rolls through its box with the page at `place`: it fades only as it leaves the box.
private func rollFade(_ place: Double) -> Double {
    smooth((0.75 - abs(place)) / 0.3)
}

/// Reduced motion: the pages stay in place and the one further away fades over the nearer one.
private func crossFade(_ place: Double) -> Double {
    abs(place) <= 0.5 ? 1 : max(0, 1 - abs(place))
}

/// Opacity of pictogram number `order` of the page at `place`: gone before the page is half-way
/// out, and the further right the later it comes.
private func popFade(_ place: Double, _ order: Int) -> Double {
    smooth((0.47 - 0.03 * Double(order) - abs(place)) / 0.2)
}

/// The photo over a flat tint of its main colour, faded in when it arrives.
private struct Photo: View {
    let url: String
    let tint: Color

    var body: some View {
        tint.overlay {
            AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                } else {
                    Color.clear
                }
            }
        }
    }
}

/// A small line pictogram, drawn from the same strokes in every stack.
private struct Glyph: View {
    let icon: CutoutWindowCardIcon

    var body: some View {
        Path { path in
            let unit = Metrics.icon / 16
            for line in strokes(icon) {
                for index in stride(from: 0, to: line.count, by: 2) {
                    let point = CGPoint(x: line[index] * unit, y: line[index + 1] * unit)
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
            }
            if icon == .sun {
                path.addEllipse(in: CGRect(x: 5.3 * unit, y: 5.3 * unit, width: 5.4 * unit, height: 5.4 * unit))
            }
        }
        .stroke(style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
        .frame(width: Metrics.icon, height: Metrics.icon)
    }
}

/// Polylines of a pictogram in a 16 × 16 box, as x, y pairs. The sun also has a circle of radius 2.7 in its middle.
private func strokes(_ icon: CutoutWindowCardIcon) -> [[Double]] {
    switch icon {
    case .snow:
        [[8, 1.8, 8, 14.2], [2.6, 4.9, 13.4, 11.1], [2.6, 11.1, 13.4, 4.9]]
    case .peak:
        [[1.2, 13.6, 6, 4, 8.6, 8.8, 10.6, 6.2, 14.8, 13.6, 1.2, 13.6]]
    case .tree:
        [[8, 1.8, 3.4, 10.6, 12.6, 10.6, 8, 1.8], [8, 10.6, 8, 14.2]]
    case .house:
        [[1.8, 8, 8, 2.4, 14.2, 8], [3.6, 6.8, 3.6, 13.8, 12.4, 13.8, 12.4, 6.8], [6.8, 13.8, 6.8, 10.2, 9.2, 10.2, 9.2, 13.8]]
    case .sun:
        [
            [8, 1.4, 8, 3], [8, 13, 8, 14.6], [1.4, 8, 3, 8], [13, 8, 14.6, 8],
            [3.3, 3.3, 4.5, 4.5], [12.7, 3.3, 11.5, 4.5], [3.3, 12.7, 4.5, 11.5], [12.7, 12.7, 11.5, 11.5],
        ]
    case .sail:
        [[8, 1.8, 8, 10], [8, 2.6, 12.8, 10, 8, 10], [6.6, 4.8, 3.6, 10, 6.6, 10, 6.6, 4.8], [2, 12, 14, 12, 11.8, 14.4, 4.2, 14.4, 2, 12]]
    case .glass:
        [[3, 2.6, 13, 2.6, 8, 8.4, 3, 2.6], [8, 8.4, 8, 13.8], [5.2, 13.8, 10.8, 13.8]]
    case .fork:
        [[4.6, 1.8, 4.6, 14.2], [2.8, 1.8, 2.8, 5.6, 6.4, 5.6, 6.4, 1.8], [11.2, 14.2, 11.2, 1.8, 13.2, 5, 13.2, 8.2, 11.2, 8.2]]
    }
}

#Preview {
    ZStack {
        Color(hex: 0x0e1411).ignoresSafeArea()
        CutoutWindowCard(autoPlay: true)
    }
}
