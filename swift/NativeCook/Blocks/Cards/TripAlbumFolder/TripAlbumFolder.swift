import SwiftUI

struct TripAlbumFolderPhoto: Equatable {
    /// Address of the photo.
    var photo: String
    /// The photo's main colour: shown until the photo arrives, and as its smudge behind the frosted front.
    var tint: Color
}

/// The flag on the postage stamp.
enum TripAlbumFolderFlag {
    /// A red disc on white.
    case disc
    /// A red cross edged in white on blue.
    case cross
}

/// The second sticker.
enum TripAlbumFolderEmblem {
    /// A red shrine gate.
    case gate
    /// Two snowy peaks.
    case peak
}

struct TripAlbumFolderAlbum: Equatable {
    var title: String
    var count: Int
    /// Four photos, left to right.
    var photos: [TripAlbumFolderPhoto]
    var flag = TripAlbumFolderFlag.disc
    var emblem = TripAlbumFolderEmblem.gate

    static let samples = [
        TripAlbumFolderAlbum(
            title: "Japan 2024",
            count: 83,
            photos: [
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1540959733332-eab4deabeeaf"), tint: Color(hex: 0x4e8690)),
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1493976040374-85c8e12f0c0e"), tint: Color(hex: 0x5b5353)),
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1441974231531-c6227db76b6e"), tint: Color(hex: 0x645f3d)),
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1519681393784-d120267933ba"), tint: Color(hex: 0x3b4a66)),
            ]
        ),
        TripAlbumFolderAlbum(
            title: "Iceland 2023",
            count: 124,
            photos: [
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1433086966358-54859d0ed716"), tint: Color(hex: 0x58683f)),
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1531366936337-7c912a4589a7"), tint: Color(hex: 0x4e5c80)),
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1504829857797-ddff29c27927"), tint: Color(hex: 0x8e9da3)),
                TripAlbumFolderPhoto(photo: tripAlbumFolderPhoto("1470071459604-3b5ec3a7fe05"), tint: Color(hex: 0x6e756c)),
            ],
            flag: .cross,
            emblem: .peak
        ),
    ]
}

private func tripAlbumFolderPhoto(_ id: String) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=300&h=390&q=80"
}

/// A photo album shown as a folder: four bordered photos stick out behind a frosted front that carries
/// two stickers, with the album's name and photo count underneath. While it is pressed the folder grows,
/// the photos rise and fan out and the stickers lift; a quick tap drops the photos into the folder and
/// brings up the next album's.
struct TripAlbumFolder: View {
    /// The albums a tap flips through, in order.
    var albums = TripAlbumFolderAlbum.samples
    /// The word after the count in the pill.
    var unit = "photos"
    /// Presses, releases and taps by itself, flipping through the albums and back to the first.
    var autoPlay = false
    /// Called with the index of the album a tap flipped to.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    @State private var pressedAt: Date? = nil
    /// Counts finished touches: each one restarts the wait before the loop resumes.
    @State private var touches = 0
    @State private var impacts = 0
    @State private var flips = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        // The press spring, restarted from its current value and speed at every change.
        var pressFrom = 0.0
        var pressTo = 0.0
        var pressSpeed = 0.0
        var pressStart = Date.distantPast
        // The album being left, the album shown, and when that flip began.
        var from = 0
        var to = 0
        var flipStart = Date.distantPast

        func press(at date: Date) -> Double {
            pressFrom + pressSpring.value(target: pressTo - pressFrom, initialVelocity: pressSpeed, time: date.timeIntervalSince(pressStart))
        }

        func setPress(_ target: Double, at date: Date = .now) {
            let elapsed = date.timeIntervalSince(pressStart)
            let value = press(at: date)
            pressSpeed = pressSpring.velocity(target: pressTo - pressFrom, initialVelocity: pressSpeed, time: elapsed)
            pressFrom = value
            pressTo = target
            pressStart = date
        }

        func role(of album: Int) -> Role {
            album == to ? .entering : album == from ? .leaving : .hidden
        }
    }

    private enum Role {
        case hidden, leaving, entering
    }

    private var auto: Bool { autoPlay && !reduceMotion && albums.count > 1 }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let press = reduceMotion ? 0 : motion.press(at: now)
            let t = now.timeIntervalSince(motion.flipStart)

            ZStack {
                ForEach(albums.indices, id: \.self) { a in
                    let role = motion.role(of: a)
                    ForEach(Metrics.drawOrder, id: \.self) { i in
                        if i < albums[a].photos.count {
                            let pose = photoPose(i, rise: rise(role, slot: i, t: t), press: press)
                            PhotoView(photo: albums[a].photos[i])
                                .equatable()
                                .scaleEffect(pose.scale)
                                .rotationEffect(.degrees(pose.rotation))
                                .position(x: pose.x, y: pose.y)
                                .opacity(fade(role, t: t))
                        }
                    }
                }

                Canvas { context, _ in
                    drawFront(context, press: press, t: t)
                }

                ForEach(albums.indices, id: \.self) { a in
                    let shown = textOpacity(motion.role(of: a), t: t)
                    VStack(spacing: 0) {
                        Text(albums[a].title)
                            .font(.system(size: 24, weight: .medium))
                            .tracking(-0.3)
                            .foregroundStyle(Color(hex: 0x1c1c1e))
                            .frame(height: 30)
                        Text("\(albums[a].count) \(unit)")
                            .font(.system(size: 17))
                            .monospacedDigit()
                            .foregroundStyle(Color(hex: 0x8a8a8f))
                            .padding(.horizontal, 15)
                            .frame(height: 34)
                            .background(Capsule().fill(Color(hex: 0xe9e9eb)))
                            .padding(.top, 11)
                    }
                    .lineLimit(1)
                    .fixedSize()
                    .position(x: Metrics.width / 2, y: Metrics.textY + 37.5)
                    .offset(y: reduceMotion ? 0 : (1 - shown) * Metrics.textShift)
                    .opacity(shown)
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)

                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: Metrics.width, height: Metrics.folderY + Metrics.folderH)
                    .position(x: Metrics.width / 2, y: (Metrics.folderY + Metrics.folderH) / 2)
                    .gesture(touch)
                    .accessibilityElement()
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel(Text(verbatim: "\(albums[motion.to].title), \(albums[motion.to].count) \(unit)"))
                    .accessibilityHint("Shows the next album")
                    .accessibilityAction { advance() }
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: impacts)
        .sensoryFeedback(.selection, trigger: flips)
        .task(id: LoopKey(auto: auto, touching: pressedAt != nil, touches: touches)) {
            await runLoop()
        }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touching: Bool
        let touches: Int
    }

    private struct PhotoView: View, Equatable {
        let photo: TripAlbumFolderPhoto

        var body: some View {
            let inner = Metrics.photoRadius - Metrics.photoBorder
            photo.tint
                .frame(width: Metrics.photoW - Metrics.photoBorder * 2, height: Metrics.photoH - Metrics.photoBorder * 2)
                .overlay {
                    AsyncImage(url: URL(string: photo.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: inner, style: .circular))
                .padding(Metrics.photoBorder)
                .background(RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .circular).fill(.white))
                .shadow(color: .black.opacity(0.16), radius: 7, x: 0, y: 4)
        }
    }

    // MARK: Touch and the autoPlay loop

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard pressedAt == nil else { return }
                pressedAt = .now
                motion.setPress(1)
                impacts += 1
            }
            .onEnded { value in
                let held = pressedAt.map { Date.now.timeIntervalSince($0) } ?? 1
                pressedAt = nil
                touches += 1
                motion.setPress(0)
                // A press that ends at once, where it began, is a tap: on to the next album.
                if held < Metrics.tapWindow, abs(value.translation.width) + abs(value.translation.height) < 20 { advance() }
            }
    }

    private func advance() {
        guard albums.count > 1, Date.now.timeIntervalSince(motion.flipStart) >= Metrics.flipTime else { return }
        let next = (motion.to + 1) % albums.count
        flip(to: next)
        flips += 1
        onSelect?(next)
    }

    private func flip(to album: Int) {
        motion.from = motion.to
        motion.to = album
        motion.flipStart = .now
    }

    /// The script a finger would follow: a long press, then a tap, once per album.
    private func runLoop() async {
        guard auto, pressedAt == nil else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, then go back to the first album before the script restarts.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                if motion.to != 0 {
                    flip(to: 0)
                    try await Task.sleep(for: .seconds(Metrics.flipTime))
                }
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                try await clock.sleep(until: start + .seconds(Metrics.pressAt))
                motion.setPress(1)
                try await clock.sleep(until: start + .seconds(Metrics.releaseAt))
                motion.setPress(0)
                try await clock.sleep(until: start + .seconds(Metrics.tapAt))
                motion.setPress(1)
                try await clock.sleep(until: start + .seconds(Metrics.tapAt + Metrics.tapHold))
                motion.setPress(0)
                flip(to: (motion.to + 1) % albums.count)
                start += .seconds(Metrics.cycle)
            }
        } catch {}
    }

    // MARK: The flip, as functions of the seconds since it began

    /// 0 down in the folder … 1 standing at rest; the spring carries it a little past 1 on the way up.
    private func rise(_ role: Role, slot: Int, t: Double) -> Double {
        switch role {
        case .hidden: return 0
        case .leaving:
            if reduceMotion { return 1 }
            return 1 - backIn(clamp01((t - Double(slot) * Metrics.dropStagger) / Metrics.dropTime))
        case .entering:
            if reduceMotion { return 1 }
            return settle(t - Metrics.riseAt - Double(slot) * Metrics.riseStagger, stiffness: Metrics.riseStiffness, damping: Metrics.riseDamping)
        }
    }

    /// Under reduced motion the two albums cross-fade in place instead.
    private func fade(_ role: Role, t: Double) -> Double {
        switch role {
        case .hidden: return 0
        case .leaving: return reduceMotion ? 1 - clamp01(t / Metrics.fadeTime) : 1
        case .entering: return reduceMotion ? clamp01(t / Metrics.fadeTime) : 1
        }
    }

    private func textOpacity(_ role: Role, t: Double) -> Double {
        if reduceMotion { return fade(role, t: t) }
        switch role {
        case .hidden: return 0
        case .leaving: return 1 - clamp01(t / Metrics.textOut)
        case .entering: return smooth(clamp01((t - Metrics.textAt) / Metrics.textIn))
        }
    }

    /// A sticker is peeled off (grows and fades) and the next one is slapped on (lands from larger).
    private func sticker(_ role: Role, slot: Int, t: Double) -> (scale: Double, opacity: Double) {
        if reduceMotion { return (1, fade(role, t: t)) }
        switch role {
        case .hidden: return (1, 0)
        case .leaving:
            let u = clamp01(t / Metrics.peelTime)
            return (1 + Metrics.peelGrow * u, 1 - u)
        case .entering:
            let since = t - Metrics.stickAt[slot]
            let landed = settle(since, stiffness: Metrics.stickStiffness, damping: Metrics.stickDamping)
            return (1 + Metrics.stickGrow * (1 - landed), clamp01(since / Metrics.stickFade))
        }
    }

    /// Centre, rotation in degrees and scale of photo `i`, between its place inside the folder (`rise` 0)
    /// and its place standing behind it (`rise` 1), fanned further out by `press`.
    private func photoPose(_ i: Int, rise: Double, press: Double) -> (x: Double, y: Double, rotation: Double, scale: Double) {
        let rest = Metrics.rest[i]
        let lift = Metrics.lift[i]
        return (
            x: Metrics.folderX + Metrics.folderW / 2 + mix(rest.x * Metrics.dropPull, rest.x + lift.x * press, rise),
            y: Metrics.folderY + Metrics.photoH / 2 + mix(Metrics.dropY, rest.y + lift.y * press, rise),
            rotation: (rest.r + lift.r * press) * rise,
            scale: mix(Metrics.dropScale, 1 + Metrics.photoGrow * press, rise)
        )
    }

    // MARK: Drawing

    /// The folder's front with everything on it: shadow, panel, the photos' smudges, embossed lines, stickers.
    private func drawFront(_ context: GraphicsContext, press: Double, t: Double) {
        var folder = context
        // The folder grows about its centre while it is pressed.
        folder.translateBy(x: Metrics.folderX + Metrics.folderW / 2, y: Metrics.folderY + Metrics.folderH / 2)
        folder.scaleBy(x: 1 + Metrics.folderGrow * press, y: 1 + Metrics.folderGrow * press)
        folder.translateBy(x: -Metrics.folderW / 2, y: -Metrics.folderH / 2)

        var shadow = folder
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: 16, y: 44 + 8 * press, width: Metrics.folderW - 32, height: Metrics.folderH - 40),
                cornerRadius: 16, style: .circular
            ),
            with: .color(.black.opacity(0.13 + 0.05 * press))
        )

        folder.fill(
            folderOutline,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf0f0f2), Color(hex: 0xf8f8f9)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: Metrics.folderH)
            )
        )

        var inside = folder
        inside.clip(to: folderOutline)
        // The front is frosted: each photo behind it shows through as a soft smudge of its colour.
        for a in albums.indices {
            let role = motion.role(of: a)
            guard role != .hidden else { continue }
            for (i, photo) in albums[a].photos.prefix(Metrics.rest.count).enumerated() {
                let up = rise(role, slot: i, t: t)
                let pose = photoPose(i, rise: up, press: press)
                var smudge = inside
                smudge.opacity = Metrics.smudge * clamp01(up) * fade(role, t: t)
                smudge.addFilter(.blur(radius: 14))
                smudge.translateBy(x: pose.x - Metrics.folderX, y: pose.y - Metrics.folderY)
                smudge.rotate(by: .degrees(pose.rotation))
                smudge.fill(
                    Path(
                        roundedRect: CGRect(x: -Metrics.photoW / 2, y: -Metrics.photoH / 2, width: Metrics.photoW, height: Metrics.photoH),
                        cornerRadius: Metrics.photoRadius, style: .circular
                    ),
                    with: .color(photo.tint)
                )
            }
        }
        // Two embossed lines: a dark hairline with a light one under it.
        for y in Metrics.lines {
            inside.fill(Path(CGRect(x: 20, y: y, width: Metrics.folderW - 40, height: 1)), with: .color(.black.opacity(0.055)))
            inside.fill(Path(CGRect(x: 20, y: y + 1, width: Metrics.folderW - 40, height: 1)), with: .color(.white.opacity(0.9)))
        }
        // Half of this stroke is clipped away, which leaves a bright rim just inside the edge.
        inside.stroke(folderOutline, with: .color(.white.opacity(0.95)), lineWidth: 3)
        folder.stroke(folderOutline, with: .color(.black.opacity(0.07)), lineWidth: 0.5)

        for a in albums.indices {
            let role = motion.role(of: a)
            guard role != .hidden else { continue }
            drawStamp(folder, flag: albums[a].flag, look: sticker(role, slot: 0, t: t), press: press)
            drawEmblem(folder, art: emblemArt(albums[a].emblem), look: sticker(role, slot: 1, t: t), press: press)
        }
    }

    /// A copy of `context` moved to where sticker `slot` sits: it lifts, turns and grows with `press`.
    private func stickerContext(_ context: GraphicsContext, slot: Int, look: (scale: Double, opacity: Double), press: Double) -> GraphicsContext {
        let place = Metrics.stickers[slot]
        var copy = context
        copy.opacity = look.opacity
        copy.translateBy(x: place.x + place.dx * press, y: place.y + place.dy * press)
        copy.rotate(by: .degrees(place.r + place.dr * press))
        copy.scaleBy(x: look.scale * (1 + Metrics.stickerGrow * press), y: look.scale * (1 + Metrics.stickerGrow * press))
        return copy
    }

    /// The shadow stays on the folder while the sticker lifts, so it slides out from under it.
    private func drawStickerShadow(_ context: GraphicsContext, shape: Path, press: Double) {
        var shadow = context
        shadow.addFilter(.blur(radius: 3))
        shadow.translateBy(x: 0, y: 1.5 + 5 * press)
        shadow.fill(shape, with: .color(.black.opacity(0.2)))
    }

    private func drawStamp(_ context: GraphicsContext, flag: TripAlbumFolderFlag, look: (scale: Double, opacity: Double), press: Double) {
        let stamp = stickerContext(context, slot: 0, look: look, press: press)
        drawStickerShadow(stamp, shape: stampOutline, press: press)
        stamp.fill(stampOutline, with: .color(.white))

        let field = CGRect(x: -20, y: -18, width: 40, height: 36)
        var inside = stamp
        inside.clip(to: Path(field))
        switch flag {
        case .disc:
            inside.fill(Path(field), with: .color(Color(hex: 0xf7f7f8)))
            inside.fill(Path(ellipseIn: CGRect(x: -9.5, y: -9.5, width: 19, height: 19)), with: .color(Color(hex: 0xe5383b)))
        case .cross:
            inside.fill(Path(field), with: .color(Color(hex: 0x2b5ba8)))
            inside.fill(Path(CGRect(x: -9.5, y: -18, width: 8, height: 36)), with: .color(.white))
            inside.fill(Path(CGRect(x: -20, y: -4, width: 40, height: 8)), with: .color(.white))
            inside.fill(Path(CGRect(x: -7.3, y: -18, width: 3.6, height: 36)), with: .color(Color(hex: 0xd72f34)))
            inside.fill(Path(CGRect(x: -20, y: -1.8, width: 40, height: 3.6)), with: .color(Color(hex: 0xd72f34)))
        }
        stamp.stroke(Path(field), with: .color(.black.opacity(0.1)), lineWidth: 0.5)
    }

    private func drawEmblem(_ context: GraphicsContext, art: EmblemArt, look: (scale: Double, opacity: Double), press: Double) {
        var emblem = stickerContext(context, slot: 1, look: look, press: press)
        // The art is drawn in a 60 × 56 box.
        emblem.translateBy(x: -30, y: -28)
        let backing = art.outline.strokedPath(StrokeStyle(lineWidth: 9, lineJoin: .round)).union(art.outline)
        drawStickerShadow(emblem, shape: backing, press: press)
        emblem.fill(backing, with: .color(.white))
        for layer in art.layers {
            emblem.fill(layer.path, with: .color(layer.color))
        }
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 400.0

    static let folderX = 44.0
    static let folderY = 96.0
    static let folderW = 252.0
    static let folderH = 190.0
    /// Y of the two embossed lines.
    static let lines = [160.0, 172.0]
    /// Top of the title, measured from the top of the component.
    static let textY = 310.0
    static let textShift = 6.0

    static let photoW = 104.0
    static let photoH = 132.0
    static let photoBorder = 6.0
    static let photoRadius = 18.0
    /// Each photo at rest: centre x from the folder's centre, top from the folder's top, degrees.
    static let rest = [(x: -100.0, y: 12.0, r: -11.0), (x: -52.0, y: -38.0, r: -3.0), (x: 36.0, y: -27.0, r: 5.0), (x: 102.0, y: 4.0, r: 9.0)]
    /// What a full press adds to that.
    static let lift = [(x: -16.0, y: -20.0, r: -7.0), (x: -10.0, y: -30.0, r: -4.0), (x: 10.0, y: -30.0, r: 4.0), (x: 18.0, y: -22.0, r: 8.0)]
    /// Back to front: the outer photos lie over their neighbours.
    static let drawOrder = [2, 1, 0, 3]
    // Inside the folder the photos pull towards the middle, sink below the tab and shrink a little.
    static let dropPull = 0.45
    static let dropY = 44.0
    static let dropScale = 0.92

    static let folderGrow = 0.04
    static let photoGrow = 0.03
    static let stickerGrow = 0.07
    /// Opacity of a photo's smudge on the frosted front.
    static let smudge = 0.16
    /// The two stickers on the folder: centre, degrees, and what a full press adds.
    static let stickers = [
        (x: 64.0, y: 78.0, r: -8.0, dx: -3.0, dy: -5.0, dr: -4.0),
        (x: 188.0, y: 112.0, r: 8.0, dx: 4.0, dy: -6.0, dr: 5.0),
    ]

    // The flip, in seconds from its start.
    static let dropTime = 0.26
    static let dropStagger = 0.03
    static let riseAt = 0.3
    static let riseStagger = 0.07
    static let riseStiffness = 180.0
    static let riseDamping = 16.0
    static let peelTime = 0.16
    static let peelGrow = 0.15
    static let stickAt = [0.5, 0.62]
    static let stickFade = 0.1
    static let stickGrow = 0.4
    static let stickStiffness = 300.0
    static let stickDamping = 18.0
    static let textOut = 0.2
    static let textAt = 0.26
    static let textIn = 0.3
    /// A second flip is ignored until this one has settled.
    static let flipTime = 1.2
    /// Length of the cross-fade that replaces the flip under reduced motion.
    static let fadeTime = 0.25

    /// A press shorter than this is a tap.
    static let tapWindow = 0.3

    // autoPlay (seconds into one album's turn): a long press, then a tap that flips to the next album.
    static let pressAt = 0.8
    static let releaseAt = 2.8
    static let tapAt = 3.4
    static let tapHold = 0.12
    /// One loop goes through the default two albums and is back at the first.
    static let loop = 10.0
    static let cycle = loop / 2
    /// After a touch the loop waits this long, then starts over from the first album.
    static let resumeAfter = 2.0
}

private let pressSpring = Spring(mass: 1, stiffness: 210, damping: 17)

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

/// Ease-in that first backs up a little: the photos lift before they drop.
private func backIn(_ t: Double) -> Double { t * t * (2.2 * t - 1.2) }

/// Where a spring of mass 1 released at 0 towards 1 is after `t` seconds.
private func settle(_ t: Double, stiffness: Double, damping: Double) -> Double {
    guard t > 0 else { return 0 }
    let omega = stiffness.squareRoot()
    let decay = damping / 2
    let ring = (omega * omega - decay * decay).squareRoot()
    return 1 - exp(-decay * t) * (cos(ring * t) + decay / ring * sin(ring * t))
}

/// A path from the absolute `M`, `L`, `Q`, `C` and `Z` commands of an SVG path, separated by spaces.
private func outline(_ commands: String) -> Path {
    let tokens = commands.split(separator: " ")
    var path = Path()
    var index = 0
    func point() -> CGPoint {
        defer { index += 2 }
        return CGPoint(x: Double(tokens[index]) ?? 0, y: Double(tokens[index + 1]) ?? 0)
    }
    while index < tokens.count {
        let command = tokens[index]
        index += 1
        switch command {
        case "M": path.move(to: point())
        case "L": path.addLine(to: point())
        case "Q":
            let control = point()
            path.addQuadCurve(to: point(), control: control)
        case "C":
            let first = point()
            let second = point()
            path.addCurve(to: point(), control1: first, control2: second)
        default: path.closeSubpath()
        }
    }
    return path
}

/// The front of the folder, 252 × 190, with its tab on the upper left.
private let folderOutline = outline(
    "M 0 16 Q 0 0 16 0 L 100 0 C 120 0 120 28 140 28 L 236 28 Q 252 28 252 44 L 252 174 Q 252 190 236 190 L 16 190 Q 0 190 0 174 Z"
)

/// A 54 × 50 stamp around its centre, with perforations bitten out of every side.
private let stampOutline: Path = {
    var holes = Path()
    for n in 0..<8 {
        let x = -27 + 54 * (Double(n) + 0.5) / 8
        holes.addEllipse(in: CGRect(x: x - 2.2, y: -27.2, width: 4.4, height: 4.4))
        holes.addEllipse(in: CGRect(x: x - 2.2, y: 22.8, width: 4.4, height: 4.4))
    }
    for n in 0..<7 {
        let y = -25 + 50 * (Double(n) + 0.5) / 7
        holes.addEllipse(in: CGRect(x: -29.2, y: y - 2.2, width: 4.4, height: 4.4))
        holes.addEllipse(in: CGRect(x: 24.8, y: y - 2.2, width: 4.4, height: 4.4))
    }
    return Path(CGRect(x: -27, y: -25, width: 54, height: 50)).subtracting(holes)
}()

/// A sticker drawn in a 60 × 56 box: the silhouette its white backing follows, then its coloured parts.
private struct EmblemArt {
    let outline: Path
    let layers: [(path: Path, color: Color)]
}

private let gateArt = EmblemArt(
    outline: outline("M 4 9 Q 30 15 56 9 L 54 17 L 51 24 L 51 29.5 L 46 29.5 L 47.5 52 L 12.5 52 L 14 29.5 L 9 29.5 L 9 24 L 6 17 Z"),
    layers: [
        (outline("M 15.5 17 L 21.5 17 L 19.5 50 L 12.5 50 Z M 38.5 17 L 44.5 17 L 47.5 50 L 40.5 50 Z"), Color(hex: 0xe8452c)),
        (outline("M 9 24 L 51 24 L 51 29.5 L 9 29.5 Z M 27.5 18 L 32.5 18 L 32.5 24 L 27.5 24 Z"), Color(hex: 0xe8452c)),
        (outline("M 4 9 Q 30 15 56 9 L 54 17 Q 30 21 6 17 Z"), Color(hex: 0xe8452c)),
        (outline("M 4 9 Q 30 15 56 9 L 55.3 11.8 Q 30 17.6 4.7 11.8 Z"), Color(hex: 0x2a2a2e)),
        (outline("M 11.5 46.5 L 20.5 46.5 L 20.5 52 L 11.5 52 Z M 39.5 46.5 L 48.5 46.5 L 48.5 52 L 39.5 52 Z"), Color(hex: 0x2a2a2e)),
    ]
)

private let peakArt = EmblemArt(
    outline: outline("M 3 50 L 24 8 L 33.5 27 L 43 18 L 57 50 Z"),
    layers: [
        (outline("M 26 50 L 43 18 L 57 50 Z"), Color(hex: 0x7fa0cf)),
        (outline("M 43 18 L 47.6 28.5 L 44.5 26.5 L 42 30 L 39.6 27 L 37.4 28.6 Z"), Color(hex: 0xdbe7f4)),
        (outline("M 3 50 L 24 8 L 45 50 Z"), Color(hex: 0x3f66a3)),
        (outline("M 24 8 L 31.5 23 L 27.5 20.5 L 24 25.5 L 20.5 20.5 L 16.5 23 Z"), Color(hex: 0xdbe7f4)),
    ]
)

private func emblemArt(_ emblem: TripAlbumFolderEmblem) -> EmblemArt {
    switch emblem {
    case .gate: return gateArt
    case .peak: return peakArt
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf5f5f6).ignoresSafeArea()
        TripAlbumFolder(autoPlay: true)
    }
}
