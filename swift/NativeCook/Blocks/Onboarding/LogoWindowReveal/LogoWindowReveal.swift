import SwiftUI

struct LogoWindowRevealEvent {
    var title: String
    /// How long until it, written out.
    var when: String
    /// A photo's address; the card and the screen crop it.
    var photo: URL?

    private static func photo(_ id: String, width: Int = 660, height: Int = 660) -> URL? {
        URL(string: "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=\(width)&h=\(height)&q=80")
    }

    static let samples = [
        LogoWindowRevealEvent(title: "Tokyo Trip", when: "IN 3 WEEKS, 2 DAYS", photo: photo("1540959733332-eab4deabeeaf")),
        LogoWindowRevealEvent(
            title: "Kate's Birthday", when: "IN 6 DAYS",
            photo: photo("1544005313-94ddf0286df2", width: 720, height: Int(720 / LogoWindowRevealTimeline.heroAspect))
        ),
        LogoWindowRevealEvent(title: "Paris", when: "IN 78 DAYS", photo: photo("1502602898657-3e91760cbb34")),
        LogoWindowRevealEvent(title: "New York", when: "IN 85 DAYS", photo: photo("1496442226666-8d4d0e62e6e9")),
        LogoWindowRevealEvent(title: "Maldives", when: "IN 51 DAYS", photo: photo("1573843981267-be1999ff37cd")),
        LogoWindowRevealEvent(title: "Santorini", when: "IN 129 DAYS", photo: photo("1570077188670-e3a8d69ac5ff")),
        LogoWindowRevealEvent(title: "Summit Hike", when: "IN 1 YEAR", photo: photo("1464822759023-fed622ff2c3b")),
        LogoWindowRevealEvent(title: "Bruno's Birthday", when: "IN 14 DAYS, 3 HOURS", photo: photo("1506794778202-cad84cf45f1d")),
    ]
}

/// An app's intro. Its mark, a disc with a window in it, swells and flies at the viewer until the
/// window has swallowed the screen: what was seen through it is a photo, which then shrinks into
/// one card of a list. The list scrolls, zooms out into three columns, and runs up until its last
/// row hangs over the app's name.
struct LogoWindowReveal: View {
    /// Eight events, top to bottom. The mark opens on the second; the last hangs in the middle at the end.
    var events = LogoWindowRevealEvent.samples
    /// The app's name, spelled out letter by letter at the end.
    var name = "UNTIL"
    var tagline = "Countdown to all of your special moments"
    /// Runs the show by itself every `LogoWindowRevealTimeline.loop` seconds.
    var autoPlay = false
    /// Called when the show has reached its end, with the name on the screen.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    /// The screen's clock counts seconds from here.
    @State private var epoch: Date? = nil
    /// The stretch of the show being played against the clock.
    @State private var run = Run(t0: 0, from: 0, to: 0)
    /// When autoPlay may go on after a touch; infinite while no touch is waited out.
    @State private var resume = Double.infinity
    /// Nothing is moving: the clock rests.
    @State private var stopped = true
    /// How far a finger scrolled the list, and where that drag started (not a number: it does not scroll).
    @State private var extra = 0.0
    @State private var grabbed: Double? = nil
    @State private var photos: [URL: UIImage] = [:]

    private typealias T = LogoWindowRevealTimeline

    /// From `from` at `t0` until `to`, where it stops.
    private struct Run: Equatable {
        var t0: Double
        var from: Double
        var to: Double

        func position(at now: Double) -> Double { min(to, from + max(0, now - t0)) }
    }

    private struct Drive: Equatable {
        let run: Run
        let resume: Double
        let auto: Bool
    }

    private enum Palette {
        static let background = Color(hex: 0xfffdff)
        static let ink = Color(hex: 0x010103)
        static let halo = Color(hex: 0x808083)
        static let placeholder = Color(hex: 0x26262b)
        static let name = Color(hex: 0x1b1b1b)
        static let tagline = Color(hex: 0x3b4248)
        static let shade = [Color.black.opacity(0), Color.black.opacity(0.7)]
    }

    private enum Metrics {
        /// A heavy condensed face the system carries.
        static let condensed = "HelveticaNeue-CondensedBlack"
        // Where the hero's lines, the name and the tagline start, as shares of the screen's height.
        static let heroTextY = 0.645
        static let nameY = 0.505
        static let taglineY = 0.607
        static let taglineWidth = 290.0
        // The events of the side columns' rows, from the second row down.
        static let left = [3, 5, 0, 6, 2, 1, 4]
        static let right = [6, 4, 7, 2, 5, 0, 3]
        /// A finger that has moved less than this has tapped.
        static let slop = 8.0
    }

    private var now: Double { epoch.map { Date.now.timeIntervalSince($0) } ?? 0 }

    private func event(_ index: Int) -> LogoWindowRevealEvent { events[index % events.count] }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)

            TimelineView(.animation(paused: stopped)) { timeline in
                let position = run.position(at: epoch.map { timeline.date.timeIntervalSince($0) } ?? 0)
                // Reduced motion shows the mark and the end only, and fades between the two.
                let t = still ? T.end : position
                let shown = still ? T.stillEnd(position) : 1
                let markTime = still ? 0 : position

                ZStack {
                    Palette.background
                    cards(t, size: size).opacity(shown)
                    ending(t, size: size).opacity(shown)
                    hero(markTime, size: size)
                    let mark = T.mark(markTime, size)
                    Circle()
                        .fill(Palette.halo)
                        .frame(width: T.markSize * T.halo * mark.scale, height: T.markSize * T.halo * mark.scale)
                        .opacity(mark.halo)
                        .position(x: size.width / 2, y: size.height / 2)
                    T.markPath(mark.scale, size)
                        .fill(Palette.ink, style: FillStyle(eoFill: true))
                        .opacity(mark.opacity * (still ? 1 - shown : 1))
                    heroLines(markTime, size: size)
                }
                .frame(width: size.width, height: size.height)
                .clipped()
            }
            .contentShape(Rectangle())
            .onTapGesture { tapped() }
            .simultaneousGesture(scrolling)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .accessibilityElement()
        .accessibilityLabel("\(name). \(tagline)")
        .accessibilityAddTraits(.isButton)
        .task { await load() }
        .task(id: Drive(run: run, resume: resume, auto: autoPlay)) { await drive() }
    }

    // MARK: Pieces

    private func cards(_ t: Double, size: CGSize) -> some View {
        ZStack {
            ForEach(0..<T.rows, id: \.self) { row in
                card(event(row), column: 0, row: row, t, size: size)
            }
            ForEach(Array(Metrics.left.enumerated()), id: \.offset) { i, index in
                card(event(index), column: -1, row: i + 1, t, size: size)
            }
            ForEach(Array(Metrics.right.enumerated()), id: \.offset) { i, index in
                card(event(index), column: 1, row: i + 1, t, size: size)
            }
        }
    }

    private func card(_ event: LogoWindowRevealEvent, column: Int, row: Int, _ t: Double, size: CGSize) -> some View {
        let p = T.place(column: column, row: row, t, size, extra)
        return LogoWindowRevealCard(event: event, image: event.photo.flatMap { photos[$0] }, text: p.text, placeholder: Palette.placeholder, shade: Palette.shade)
            .scaleEffect(p.scale)
            .position(x: p.x, y: p.y)
            .opacity(p.opacity)
    }

    /// The photo seen through the mark's window, which becomes the screen and then a card. Drawn, so
    /// that its box, the photo and the shade over it are placed by the same numbers.
    private func hero(_ t: Double, size: CGSize) -> some View {
        let h = T.hero(t, size)
        let image = event(T.heroRow).photo.flatMap { photos[$0] }
        return Canvas { context, _ in
            context.clip(to: Path(roundedRect: h.rect, cornerRadius: h.radius, style: .circular))
            context.fill(Path(h.rect), with: .color(Palette.placeholder))
            if let image {
                // The photo covers the screen with its whole width, and shrinks with the box when that becomes a card.
                let width = max(size.width, size.height * T.heroAspect) * h.k
                let height = width * image.size.height / image.size.width
                context.draw(Image(uiImage: image), in: CGRect(x: (size.width - width) / 2, y: (size.height - height) / 2, width: width, height: height))
            }
            let lower = CGRect(x: h.rect.minX, y: h.rect.midY, width: h.rect.width, height: h.rect.height / 2)
            context.fill(
                Path(lower),
                with: .linearGradient(Gradient(colors: Palette.shade), startPoint: CGPoint(x: 0, y: lower.minY), endPoint: CGPoint(x: 0, y: lower.maxY))
            )
        }
        .opacity(h.opacity)
    }

    private func heroLines(_ t: Double, size: CGSize) -> some View {
        let words = event(T.heroRow).title.split(separator: " ", maxSplits: 1).map(String.init)
        let when = T.heroLine(t, 0)
        let title = T.heroLine(t, 1)
        return VStack(spacing: 0) {
            Text(event(T.heroRow).when)
                .font(.system(size: 12, weight: .medium))
                .tracking(1.2)
                .foregroundStyle(.white.opacity(0.85))
                .frame(height: 16)
                .offset(y: when.rise)
                .opacity(when.opacity)
            VStack(spacing: 0) {
                ForEach(words, id: \.self) { word in
                    Text(word).font(.custom(Metrics.condensed, fixedSize: 52)).frame(height: 56)
                }
            }
            .foregroundStyle(.white)
            .padding(.top, 22)
            .offset(y: title.rise)
            .opacity(title.opacity)
        }
        .lineLimit(1)
        .frame(width: size.width, height: size.height, alignment: .top)
        .offset(y: size.height * Metrics.heroTextY)
        .allowsHitTesting(false)
    }

    /// The name, letter by letter, and the line under it.
    private func ending(_ t: Double, size: CGSize) -> some View {
        let out = T.nameOut(t)
        return ZStack {
            HStack(spacing: 9) {
                ForEach(Array(name.enumerated()), id: \.offset) { index, char in
                    let l = T.letter(t, index)
                    Text(String(char))
                        .font(.system(size: 46, weight: .heavy))
                        .frame(height: 56)
                        .offset(y: l.rise)
                        .opacity(l.opacity)
                }
            }
            .foregroundStyle(Palette.name)
            .scaleEffect(out.scale)
            .opacity(out.opacity)
            .position(x: size.width / 2, y: size.height * Metrics.nameY + 28)

            // Lines 25 points apart, the first one centred in its own 25 points.
            Text(tagline)
                .font(.system(size: 17))
                .lineSpacing(4.7)
                .multilineTextAlignment(.center)
                .foregroundStyle(Palette.tagline)
                .padding(.top, 2.35)
                .frame(width: Metrics.taglineWidth, height: 100, alignment: .top)
                .opacity(T.tagline(t))
                .position(x: size.width / 2, y: size.height * Metrics.taglineY + 50)
        }
    }

    // MARK: Touch

    /// A touch takes over: the show stops at its end, and autoPlay starts again a while later.
    private func touched() {
        let now = self.now
        if autoPlay { resume = now + T.userHold }
        if run.to == T.loop, run.position(at: now) < T.end { run.to = T.end }
    }

    private func tapped() {
        let now = self.now
        let t = run.position(at: now)
        let idle = run.to == 0
        touched()
        // The mark starts the show; the end closes it.
        if idle {
            run = Run(t0: now, from: T.swellAt, to: T.end)
        } else if t >= T.end, t < T.closeAt {
            run = Run(t0: now, from: T.closeAt, to: T.loop)
        }
    }

    /// A finger on the list scrolls it, and holds the show where it is.
    private var scrolling: some Gesture {
        DragGesture(minimumDistance: Metrics.slop)
            .onChanged { value in
                if grabbed == nil {
                    touched()
                    let t = run.position(at: now)
                    grabbed = t >= T.listAt && t < T.hangAt ? extra + value.translation.height : .nan
                    if t >= T.listAt, t < T.hangAt { run = Run(t0: .infinity, from: t, to: run.to) }
                }
                if let grabbed, !grabbed.isNaN { extra = grabbed - value.translation.height }
            }
            .onEnded { _ in
                grabbed = nil
                if run.t0 == .infinity { run.t0 = now + T.scrollHold }
            }
    }

    // MARK: The clock

    /// Waits out the stretch being played, and decides what follows it. Starts over whenever that changes.
    private func drive() async {
        guard epoch != nil else {
            epoch = .now
            run = Run(t0: 0, from: 0, to: autoPlay ? T.loop : 0)
            return
        }
        let r = run
        guard r.t0.isFinite else { return }
        let end = r.t0 + r.to - r.from
        let playing = end > now
        stopped = !playing
        if playing {
            try? await Task.sleep(for: .seconds(end - now))
            guard !Task.isCancelled else { return }
        }
        if r.to == T.loop {
            // Closed: the mark again. The loop goes on from when it was due, so that every one is `loop` long.
            extra = 0
            run = Run(t0: end, from: 0, to: autoPlay && resume == .infinity ? T.loop : 0)
            return
        }
        stopped = true
        if playing, r.to == T.end { onDone?() }
        guard autoPlay, resume.isFinite else { return }
        try? await Task.sleep(for: .seconds(max(0, resume - now)))
        guard !Task.isCancelled else { return }
        // The touches are over: close the show if it is open, and start again.
        let start = now
        resume = .infinity
        run = r.to == T.end ? Run(t0: start, from: T.closeAt, to: T.loop) : Run(t0: start, from: 0, to: T.loop)
    }

    /// Each photo is fetched once, however many cards show it.
    private func load() async {
        await withTaskGroup(of: (URL, Data?).self) { group in
            for url in Set(events.compactMap(\.photo)) {
                group.addTask { (url, try? await URLSession.shared.data(from: url).0) }
            }
            for await (url, data) in group {
                if let image = data.flatMap(UIImage.init(data:)) { photos[url] = image }
            }
        }
    }
}

/// A square card: the event's photo under a shade, with its two lines at the bottom.
struct LogoWindowRevealCard: View {
    let event: LogoWindowRevealEvent
    let image: UIImage?
    /// How much of the two lines shows.
    let text: Double
    let placeholder: Color
    let shade: [Color]

    var body: some View {
        let side = LogoWindowRevealTimeline.card
        placeholder
            .frame(width: side, height: side)
            .overlay {
                if let image { Image(uiImage: image).resizable().scaledToFill() }
            }
            .overlay(alignment: .bottom) {
                LinearGradient(colors: shade, startPoint: .top, endPoint: .bottom).frame(height: side / 2)
            }
            .overlay(alignment: .bottom) {
                VStack(spacing: 6) {
                    Text(event.when)
                        .font(.system(size: 10, weight: .medium))
                        .tracking(1)
                        .foregroundStyle(.white.opacity(0.85))
                        .frame(height: 14)
                    Text(event.title)
                        .font(.custom("HelveticaNeue-CondensedBlack", fixedSize: 32))
                        .foregroundStyle(.white)
                        .frame(height: 36)
                }
                .lineLimit(1)
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
                .opacity(text)
            }
            .clipShape(RoundedRectangle(cornerRadius: LogoWindowRevealTimeline.cardRadius))
    }
}

#Preview {
    LogoWindowReveal(autoPlay: true)
}
