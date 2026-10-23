import SwiftUI

private typealias M = ScatterGridAlbumMotion
private typealias P = ScatterGridAlbumPalette

private func scatterGridAlbumPhoto(_ id: String) -> String {
    // Sized for the largest it is drawn at (132 × 176 open) at 3×.
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=396&h=528&q=80"
}

extension ScatterGridAlbum {
    /// Eight photos in grid order: row by row, skipping the middle cell.
    static let defaultPhotos = [
        scatterGridAlbumPhoto("1433086966358-54859d0ed716"),
        scatterGridAlbumPhoto("1441974231531-c6227db76b6e"),
        scatterGridAlbumPhoto("1454496522488-7a8e488e8606"),
        scatterGridAlbumPhoto("1464822759023-fed622ff2c3b"),
        scatterGridAlbumPhoto("1472214103451-9374bd1c798e"),
        scatterGridAlbumPhoto("1506905925346-21bda4d32df4"),
        scatterGridAlbumPhoto("1526772662000-3f88f10405ff"),
        scatterGridAlbumPhoto("1531366936337-7c912a4589a7"),
    ]
}

/// An album screen. Open, its photos lie scattered and tilted over a pale blue and mint gradient that
/// fills the screen, under the album's name; a tap folds it: the gradient shrinks into a square card,
/// the photos fly into a 3×3 grid on it around the name, and a row of icons (share, like, info, delete)
/// rises under it. A tap on the card unfolds it again.
struct ScatterGridAlbum: View {
    var name = "Highlands"
    var year = "2025"
    /// Eight photo addresses, in grid order (row by row, skipping the middle cell).
    var photos: [String] = ScatterGridAlbum.defaultPhotos
    /// Folds, likes, unlikes and unfolds by itself every `loop` seconds.
    var autoPlay = false
    var onFoldChange: ((Bool) -> Void)? = nil
    var onLike: ((Bool) -> Void)? = nil

    static let loop = ScatterGridAlbumMotion.loop

    @Environment(\.accessibilityReduceMotion) private var still
    /// The events made by hand; the autoPlay's come from its loop start, nil while a touch holds it off.
    @State private var hand = M.Events()
    @State private var loopStart: Double? = nil
    /// Where the drift's clock counts from.
    @State private var origin = Date.now.timeIntervalSinceReferenceDate
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Keeps the clock running after a touch until everything is still.
    @State private var awake = false
    @State private var images: [UIImage?] = Array(repeating: nil, count: M.slots.count)
    /// The photos are in (or the wait for them is over): the autoPlay may start.
    @State private var ready = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var likeHaptic = UIImpactFeedbackGenerator(style: .medium)

    private static let iconLabels = ["Share", "Like", "Info", "Delete"]
    private static let heart = 1

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let layout = M.layout(width: width, height: height, top: insets.top, bottom: insets.bottom, nameWidth: nameWidth)
            let running = loopStart != nil || awake || (!hand.folded && !still)

            TimelineView(.animation(paused: !running)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let e = events(at: now)

                ZStack(alignment: .topLeading) {
                    album(layout: layout, e: e, now: now)
                        .contentShape(Rectangle())
                        .onTapGesture(coordinateSpace: .local) { point in tap(at: point, layout: layout) }
                        .accessibilityElement()
                        .accessibilityLabel("\(name), \(year)")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityHint(e.folded ? "Opens the album" : "Folds the album into a grid")
                        .accessibilityAction { toggle(fold: !isFolded, layout: layout) }
                    ForEach(0..<4, id: \.self) { k in
                        icon(k, layout: layout, e: e, now: now)
                    }
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .onChange(of: e.folded) { _, folded in onFoldChange?(folded) }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            tapHaptic.prepare()
            likeHaptic.prepare()
        }
        .task { await preload() }
        .task {
            try? await Task.sleep(for: .seconds(M.preloadWait))
            ready = true
        }
        .task(id: ScatterGridAlbumLoopKey(on: autoPlay, touches: touches, ready: ready)) { await loop() }
    }

    // MARK: Parts

    /// The backdrop, the heading and the photos; under reduced motion, the two layouts cross-faded.
    @ViewBuilder
    private func album(layout: M.Layout, e: M.Events, now: Double) -> some View {
        if still {
            let x = M.fadeAmount(e, now: now)
            ZStack(alignment: .topLeading) {
                if x > 0 {
                    scene(layout: layout, card: 1, photos: Array(repeating: 1, count: M.slots.count), driftT: nil)
                }
                if x < 1 {
                    scene(layout: layout, card: 0, photos: Array(repeating: 0, count: M.slots.count), driftT: nil)
                        .compositingGroup()
                        .opacity(1 - x)
                }
            }
        } else {
            scene(
                layout: layout,
                card: M.card(e, now: now),
                photos: M.slots.indices.map { M.photo($0, rank: layout.ranks[$0], e, now: now) },
                driftT: now - origin
            )
        }
    }

    private func scene(layout: M.Layout, card: Double, photos: [Double], driftT: Double?) -> some View {
        ZStack(alignment: .topLeading) {
            ScatterGridAlbumBackdrop(pose: M.cardPose(c: card, layout: layout))
            heading(M.headingPose(c: card, layout: layout))
            ForEach(M.drawOrder, id: \.self) { i in
                ScatterGridAlbumPhoto(image: images[i], pose: M.photoPose(i, u: photos[i], layout: layout, driftT: driftT))
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    /// The name, the year over it (open) and the year under it (folded), sliding and shrinking together.
    private func heading(_ pose: M.HeadingPose) -> some View {
        ZStack(alignment: .topLeading) {
            Text(year)
                .font(.system(size: M.yearSize, weight: .semibold))
                .foregroundStyle(P.year)
                .fixedSize()
                .frame(height: M.yearLine)
                .scaleEffect(pose.scale)
                .opacity(pose.yearTopOpacity)
                .position(pose.yearTop)
            Text(name)
                .font(.system(size: M.nameSize, weight: .heavy))
                .tracking(M.nameTracking)
                .foregroundStyle(.white)
                .fixedSize()
                .frame(height: M.nameLine)
                .shadow(color: P.headingShadow, radius: P.headingShadowRadius)
                .scaleEffect(pose.scale)
                .position(pose.name)
            Text(year)
                .font(.system(size: M.yearFoldedSize, weight: .semibold))
                .foregroundStyle(P.year)
                .fixedSize()
                .opacity(pose.yearCellOpacity)
                .position(pose.yearCell)
        }
        .shadow(color: P.headingShadow, radius: P.headingShadowRadius)
        .allowsHitTesting(false)
    }

    private func icon(_ k: Int, layout: M.Layout, e: M.Events, now: Double) -> some View {
        let shown = still ? M.fadeAmount(e, now: now) : M.icon(k, e, now: now)
        let rise = still ? 0 : M.iconRise * (1 - shown)
        let liked = k == Self.heart && e.heartOn
        return Button {
            if k == Self.heart { like() }
        } label: {
            glyph(k, e: e, now: now)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(ScatterGridAlbumPressStyle { down in press(k, down) })
        .scaleEffect(M.pressScale(k, e, now: now))
        .opacity(shown)
        .position(x: layout.icons[k].x, y: layout.icons[k].y + rise)
        .allowsHitTesting(e.folded && shown > 0.5)
        .accessibilityHidden(!e.folded)
        .accessibilityLabel(Self.iconLabels[k])
        .accessibilityAddTraits(liked ? .isSelected : [])
    }

    @ViewBuilder
    private func glyph(_ k: Int, e: M.Events, now: Double) -> some View {
        let size = M.iconSize
        let stroke = StrokeStyle(lineWidth: M.iconStroke, lineCap: .round, lineJoin: .round)
        switch k {
        case 0:
            ScatterGridAlbumShareIcon().stroke(P.icon, style: stroke).frame(width: size, height: size)
                .shadow(color: P.iconShadow, radius: P.iconShadowRadius, y: P.iconShadowY)
        case 1:
            let fill = M.heartFill(e, now: now)
            ZStack {
                // The dots fly out from the heart's centre, unscaled by its pop.
                if let burst = M.burst(e, now: now, still: still) {
                    ScatterGridAlbumBurst(progress: burst)
                }
                ZStack {
                    ScatterGridAlbumHeartIcon().stroke(P.icon, style: stroke)
                        .shadow(color: P.iconShadow, radius: P.iconShadowRadius, y: P.iconShadowY)
                    ZStack {
                        ScatterGridAlbumHeartIcon().fill(P.heart)
                        ScatterGridAlbumHeartIcon().stroke(P.heart, style: stroke)
                    }
                    .opacity(fill)
                }
                .frame(width: size, height: size)
                .scaleEffect(M.heartScale(e, now: now, still: still))
            }
        case 2:
            ZStack {
                ScatterGridAlbumInfoIcon().stroke(P.icon, style: stroke)
                ScatterGridAlbumInfoDot().fill(P.icon)
            }
            .frame(width: size, height: size)
            .shadow(color: P.iconShadow, radius: P.iconShadowRadius, y: P.iconShadowY)
        default:
            ScatterGridAlbumTrashIcon().stroke(P.icon, style: stroke).frame(width: size, height: size)
                .shadow(color: P.iconShadow, radius: P.iconShadowRadius, y: P.iconShadowY)
        }
    }

    // MARK: Touch

    private var nameWidth: Double {
        let font = UIFont.systemFont(ofSize: M.nameSize, weight: .heavy)
        return (name as NSString).size(withAttributes: [.font: font, .kern: M.nameTracking]).width
    }

    /// Folded on screen now, whether by hand or by the autoPlay.
    private var isFolded: Bool {
        let now = Date.now.timeIntervalSinceReferenceDate
        return loopStart.map { M.autoEvents(now: now, start: $0).folded } ?? hand.folded
    }

    private func tap(at point: CGPoint, layout: M.Layout) {
        let folded = isFolded
        // Open: a tap anywhere folds. Folded: only a tap on the card unfolds.
        if folded && !layout.card.contains(point) { return }
        toggle(fold: !folded, layout: layout)
    }

    private func toggle(fold: Bool, layout: M.Layout) {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        guard hand.folded != fold else { return }
        tapHaptic.impactOccurred()
        hand.from = M.snapshot(hand, ranks: layout.ranks, now: now, still: still)
        hand.folded = fold
        hand.at = now
    }

    private func like() {
        takeOver()
        hand.heartOn.toggle()
        hand.heartAt = Date.now.timeIntervalSinceReferenceDate
        if hand.heartOn { likeHaptic.impactOccurred() }
        onLike?(hand.heartOn)
    }

    private func press(_ k: Int, _ down: Bool) {
        takeOver()
        let now = Date.now.timeIntervalSinceReferenceDate
        if down { hand.downAt[k] = now } else { hand.upAt[k] = now }
    }

    private func events(at now: Double) -> M.Events {
        guard let start = loopStart else { return hand }
        return M.autoEvents(now: now, start: start)
    }

    /// A touch takes over from the autoPlay loop (from what is on screen), which starts again later.
    private func takeOver() {
        if let start = loopStart {
            hand = M.autoEvents(now: Date.now.timeIntervalSinceReferenceDate, start: start)
            loopStart = nil
        }
        awake = true
        touches += 1
    }

    // MARK: Clock

    private func preload() async {
        let addresses = Array(photos.prefix(M.slots.count))
        for (i, address) in addresses.enumerated() {
            images[i] = ScatterGridAlbumPhotos.cached(address)
        }
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (i, address) in addresses.enumerated() where images[i] == nil {
                group.addTask { (i, await ScatterGridAlbumPhotos.load(address)) }
            }
            for await (i, image) in group {
                images[i] = image
            }
        }
        ready = true
    }

    private func sleep(_ seconds: Double) async {
        try? await Task.sleep(for: .seconds(max(0, seconds)), tolerance: .zero)
    }

    private func loop() async {
        guard autoPlay else {
            // Let the clock stop once a touch has played out.
            guard touches > 0 else { return }
            await sleep(M.settle)
            if !Task.isCancelled, !M.anyPressed(hand) { awake = false }
            return
        }
        if touches > 0 {
            await sleep(M.userHold)
            guard !Task.isCancelled, !M.anyPressed(hand) else { return }
            // Back to the open album the way a tap on the card goes there, then the loop starts again.
            if hand.folded {
                let now = Date.now.timeIntervalSinceReferenceDate
                hand.from = M.snapshot(hand, ranks: Array(M.slots.indices), now: now, still: still)
                hand.folded = false
                hand.at = now
                await sleep(M.settle)
                guard !Task.isCancelled else { return }
            }
        } else {
            // The first loop waits for the photos, so a recording never shows a grey card.
            guard ready else { return }
        }
        hand = M.Events()
        loopStart = Date.now.timeIntervalSinceReferenceDate
    }
}

private struct ScatterGridAlbumLoopKey: Equatable {
    let on: Bool
    let touches: Int
    let ready: Bool
}

#Preview {
    ScatterGridAlbum(autoPlay: true)
}
