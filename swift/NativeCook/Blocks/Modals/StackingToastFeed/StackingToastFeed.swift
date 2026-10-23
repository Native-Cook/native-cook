import SwiftUI

enum StackingToastFeedMotion {
    // One loop, in seconds: four toasts arrive in turn, the pile collapses, then four swipes clear it.
    static let loop = 10.0
    static let arriveAt = [0.7, 1.5, 2.4, 3.3]
    static let collapseAt = 4.3
    /// Each swipe: when it starts and where it goes (1 right, -1 left).
    static let swipes: [(at: Double, direction: Int)] = [(5.5, 1), (6.5, -1), (7.1, 1), (7.7, -1)]
    // The spread is put back this long into the loop, when nothing is on screen.
    static let resetAt = 0.4
    // After a touch the loop waits this long, then starts again from the empty page.
    static let userHold = 3.0

    static let collapseSpring = Spring(duration: 0.5, bounce: 1 - 0.9)
    static let slideSpring = Spring(duration: 0.45, bounce: 1 - 0.8)

    static let toastHeight = 84.0
    static let toastGap = 12.0
    static let spreadTop = 64.0
    // Collapsed, each toast behind the front one shows this much, and is this much smaller.
    static let stackStep = 12.0
    static let stackShrink = 0.04
    static let stackDim = 0.04
    static let stackMinScale = 0.8

    static let shakePoints = 4.0
    static let shakeFor = 0.05
    static let swipeFor = 0.45
    static let fadeFor = 0.35
    static let shrinkFor = 0.25
    static let shrinkTo = 0.9
    static let tiltDegrees = 12.0
    // A drag beyond this distance, or this fast, sends the toast away.
    static let swipeDistance = 90.0
    static let swipeSpeed = 800.0

    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t) }
    static func inQuad(_ t: Double) -> Double { t * t }
    static func inCubic(_ t: Double) -> Double { t * t * t }
}

private typealias M = StackingToastFeedMotion

private enum Metrics {
    static let margin = 20.0
    static let page = Color(hex: 0xf2f2f4)
    static let ink = Color(hex: 0x111318)
    static let mute = Color(hex: 0x6e7380)
    static let shadow = Color.rgba(17, 19, 24, 1)

    static func photo(_ id: String, _ w: Int, _ h: Int? = nil) -> URL? {
        URL(string: "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(h ?? w)&q=80")
    }

    // The shop page under the toasts: a featured ride and a short list of experiences.
    static let feature = "photo-1641128324972-af3212f0f6bd"
    static let experiences: [(title: String, meta: String, price: String, photo: String)] = [
        ("Night lights walking tour", "4.9 · 3 hours", "$48", "photo-1540959733332-eab4deabeeaf"),
        ("Five villages by boat", "4.8 · Full day", "$96", "photo-1516483638261-f4dbaf036963"),
        ("Pyramids at first light", "4.9 · 6 hours", "$72", "photo-1503177119275-0aa32b3a9368"),
        ("Lagoon snorkel morning", "4.7 · 4 hours", "$64", "photo-1573843981267-be1999ff37cd"),
    ]
    static let messages = [
        StackingToastFeedMessage(
            kind: .error, title: "Error", message: "That password isn't right. Can we help you ",
            link: "recover your password", tail: "?"
        ),
        StackingToastFeedMessage(kind: .warning, title: "Warning", message: "Looks like you've exceeded your limit. ", link: "Need an upgrade?"),
        StackingToastFeedMessage(kind: .info, title: "Info", message: "We've updated a few things. Check the ", link: "patch notes", tail: "!"),
        StackingToastFeedMessage(kind: .success, title: "Success", message: "Items added to cart. Yay!"),
    ]
}

/// A page that collects toasts: each one slides in from above with a short shake and pushes the
/// others down, then the pile collapses into a stack and the toasts are swiped away one by one.
/// Every position comes from a toast's place in the list. By hand, tap the page to add one, swipe
/// or tap the x to remove one, long-press the page to collapse or spread the pile.
struct StackingToastFeed: View {
    /// Adds, collapses and swipes away toasts by itself, in a loop, and leaves the page empty.
    var autoPlay = false
    /// The messages a tap on the page goes through, one after the other.
    var messages = Metrics.messages
    /// Most toasts kept at once; the oldest leaves when another arrives.
    var limit = 5
    /// Called when a toast has left, by swipe or by its x.
    var onDismiss: ((StackingToastFeedMessage) -> Void)? = nil
    /// Called when a toast arrives.
    var onShow: ((StackingToastFeedMessage) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var items: [StackingToastFeedItem] = []
    /// 0 spread out, 1 collapsed into a pile.
    @State private var stack = StackingToastFeedChannel(0)
    @State private var counter = 0
    @State private var next = 0
    @State private var collapsed = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var touching = false
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var medium = UIImpactFeedbackGenerator(style: .medium)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )

            ZStack(alignment: .topLeading) {
                StackingToastFeedContours(size: screen)
                page(insets: insets, screen: screen)
                // The clock only runs while a toast is on the page.
                TimelineView(.animation(paused: items.isEmpty)) { timeline in
                    toasts(at: timeline.date, screen: screen)
                }
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Metrics.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard autoPlay, !touching else { return }
                    touching = true
                    touches += 1
                }
                .onEnded { _ in touching = false }
        )
        .onAppear {
            light.prepare()
            medium.prepare()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: Page and toasts

    private func page(insets: EdgeInsets, screen: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            content
                .padding(.horizontal, Metrics.margin)
                .padding(.top, insets.top + 4)

            Text("Tap to add a toast, hold to stack them")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Metrics.mute)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, insets.bottom + 20)
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .onTapGesture { addByHand() }
        .onLongPressGesture(minimumDuration: 0.45) { toggleByHand() }
        .accessibilityLabel("Page")
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Good evening, Lena")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Metrics.mute)
                        .frame(height: 20)
                    Text("Explore")
                        .font(.system(size: 34, weight: .heavy))
                        .tracking(-1)
                        .frame(height: 40)
                }
                Spacer(minLength: 0)
                Image(systemName: "bag")
                    .font(.system(size: 19, weight: .regular))
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(.white).shadow(color: Metrics.shadow.opacity(0.08), radius: 4, y: 2))
                    .overlay(alignment: .topTrailing) {
                        Text("2")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundStyle(.white)
                            .frame(minWidth: 14, minHeight: 14)
                            .background(Circle().fill(Color(hex: 0xff4d4f)))
                            .padding(2)
                            .background(Circle().fill(Metrics.page))
                            .offset(x: 2, y: -2)
                    }
                    .padding(.bottom, 2)
            }

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Metrics.mute)
                Text("Experiences, cities, tours")
                    .font(.system(size: 16))
                    .foregroundStyle(Metrics.mute)
                    .frame(height: 21)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .frame(height: 44)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(.white)
                    .shadow(color: Metrics.shadow.opacity(0.06), radius: 1.5, y: 1)
            )
            .padding(.top, 16)

            ZStack(alignment: .bottomLeading) {
                Color(hex: 0xd9a066)
                StackingToastFeedPhoto(url: Metrics.photo(Metrics.feature, 800, 440))
                LinearGradient(
                    stops: [.init(color: .black.opacity(0), location: 0.35), .init(color: .black.opacity(0.55), location: 1)],
                    startPoint: .top, endPoint: .bottom
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sunrise balloon ride")
                        .font(.system(size: 22, weight: .heavy))
                        .tracking(-0.5)
                        .frame(height: 27)
                    Text("Cappadocia · from $189")
                        .font(.system(size: 14))
                        .opacity(0.88)
                        .frame(height: 19)
                }
                .foregroundStyle(.white)
                .padding(18)
            }
            .overlay(alignment: .topLeading) {
                Text("Trending")
                    .font(.system(size: 13, weight: .bold))
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Capsule().fill(.white.opacity(0.92)))
                    .padding(16)
            }
            .frame(height: 196)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .padding(.top, 20)

            HStack(alignment: .firstTextBaseline) {
                Text("Popular near you")
                    .font(.system(size: 20, weight: .heavy))
                    .tracking(-0.4)
                Spacer(minLength: 0)
                Text("See all")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x2f6bff))
            }
            .frame(height: 25)
            .padding(.top, 24)
            .padding(.bottom, 4)

            ForEach(Array(Metrics.experiences.enumerated()), id: \.offset) { i, item in
                HStack(spacing: 12) {
                    StackingToastFeedPhoto(url: Metrics.photo(item.photo, 160))
                        .frame(width: 60, height: 60)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.title)
                            .font(.system(size: 16, weight: .semibold))
                            .tracking(-0.2)
                            .lineLimit(1)
                            .frame(height: 21)
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(Color(hex: 0xf5a623))
                            Text(item.meta)
                                .font(.system(size: 13))
                                .foregroundStyle(Metrics.mute)
                        }
                        .frame(height: 17)
                    }
                    Spacer(minLength: 0)
                    Text(item.price)
                        .font(.system(size: 16, weight: .bold))
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(Metrics.ink))
                }
                .frame(height: 80)
                .overlay(alignment: .top) {
                    if i > 0 { Rectangle().fill(Color.rgba(60, 60, 67, 0.2)).frame(height: 1 / displayScale) }
                }
            }
        }
        .foregroundStyle(Metrics.ink)
    }

    private func toasts(at date: Date, screen: CGSize) -> some View {
        var place: [Int: Int] = [:]
        for (i, item) in items.filter({ $0.leaving == nil }).enumerated() { place[item.id] = i }
        let pile = stack.value(at: date)
        return ZStack(alignment: .topLeading) {
            ForEach(items) { item in
                StackingToastFeedToast(
                    date: date, item: item, index: place[item.id] ?? 0, stack: pile,
                    width: screen.width - Metrics.margin * 2, screenWidth: screen.width, reduced: reduceMotion,
                    onLeave: leave, onGone: gone
                )
            }
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
    }

    // MARK: Behaviour

    private func setPile(_ value: Double) {
        collapsed = value == 1
        if reduceMotion { stack.set(value) } else { stack.go(to: value, .spring(M.collapseSpring)) }
    }

    private func push(_ message: StackingToastFeedMessage) {
        counter += 1
        let id = counter
        onShow?(message)
        let kept = items.filter { $0.leaving == nil }
        // The oldest toast makes room by leaving the way a tap on its x would.
        let surplus = max(kept.count + 1 - limit, 0)
        let leaving = Set(kept.suffix(surplus).map(\.id))
        let rest = items.map { item -> StackingToastFeedItem in
            var item = item
            if leaving.contains(item.id) { item.leaving = 0 }
            return item
        }
        items = [StackingToastFeedItem(id: id, message: message)] + rest
    }

    private func leave(_ id: Int, _ direction: Int) {
        guard let i = items.firstIndex(where: { $0.id == id }), items[i].leaving == nil else { return }
        items[i].leaving = direction
    }

    private func gone(_ id: Int) {
        guard let left = items.first(where: { $0.id == id }) else { return }
        items.removeAll { $0.id == id }
        onDismiss?(left.message)
    }

    private func addByHand() {
        light.impactOccurred()
        push(messages[next % messages.count])
        next += 1
    }

    private func toggleByHand() {
        medium.impactOccurred()
        setPile(collapsed ? 0 : 1)
    }

    // MARK: The autoPlay loop

    private struct Step {
        let at: Double
        let run: @MainActor (StackingToastFeed) -> Void
    }

    private func steps() -> [Step] {
        var steps = [Step(at: M.resetAt) { $0.setPile(0) }]
        for (i, at) in M.arriveAt.enumerated() {
            steps.append(Step(at: at) { $0.push($0.messages[i % $0.messages.count]) })
        }
        steps.append(Step(at: M.collapseAt) { $0.setPile(1) })
        for swipe in M.swipes {
            steps.append(Step(at: swipe.at) { feed in
                if let toast = feed.items.first(where: { $0.leaving == nil }) { feed.leave(toast.id, swipe.direction) }
            })
        }
        return steps.enumerated().sorted { ($0.element.at, $0.offset) < ($1.element.at, $1.offset) }.map(\.element)
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        let steps = steps()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            // Back to the empty page, then the loop from its first second.
            for i in items.indices where items[i].leaving == nil { items[i].leaving = 0 }
            try? await Task.sleep(for: .seconds(0.8), tolerance: .zero)
        }
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run(self)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

/// A photo that fills its frame, cropped, with nothing drawn until it has loaded.
private struct StackingToastFeedPhoto: View {
    let url: URL?

    var body: some View {
        Color.clear
            .overlay {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
            }
            .clipped()
    }
}
