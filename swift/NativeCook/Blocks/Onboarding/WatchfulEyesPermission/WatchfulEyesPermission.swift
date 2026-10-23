import SwiftUI

struct WatchfulEyesPermissionMessage {
    var text: String
    /// The user's own reply, on the right.
    var mine = false

    static let samples = [
        WatchfulEyesPermissionMessage(text: "Hmm, sounds great!", mine: true),
        WatchfulEyesPermissionMessage(text: "☝️ Statistically, 81% of my users feel significant sleep improvement during the first 4 weeks. And I have a question that perfectly predicts how well we match"),
        WatchfulEyesPermissionMessage(text: "Good idea 🤝", mine: true),
        WatchfulEyesPermissionMessage(text: "Wow, we have a few interesting things in common! Usually, people who choose these options are impressed with what I have to offer 😎"),
        WatchfulEyesPermissionMessage(text: "Intriguing! Please, show me 🚀", mine: true),
        WatchfulEyesPermissionMessage(text: "Super! Let's start by quickly analyzing your sleep and habits 🕵️ Sometimes, it helps to uncover curious insights right away"),
        WatchfulEyesPermissionMessage(text: "Let's see what you can find out 🤓", mine: true),
    ]
}

/// A permission primer told by a pair of glowing eyes. They sit over a chat, come down and grow
/// to ask for health data from inside a little home, fly back up while a phone's outline draws
/// itself, watch six switches flip on, and smile at the check mark. They blink, glance, startle
/// and shake a "no" on their own clock, and look towards a finger.
struct WatchfulEyesPermission: View {
    /// The chat the screen opens on, top to bottom.
    var messages = WatchfulEyesPermissionMessage.samples
    /// What the eyes say when they ask.
    var message = "Don't worry, this analysis happens right on your phone, and your data remains securely protected on device."
    var startLabel = "Start Health analysis"
    var laterLabel = "Later"
    /// The name under the tile on the switches' page.
    var title = "Health"
    var allOnLabel = "Turn on all"
    var allOffLabel = "Turn off all"
    var buttonLabel = "Turn on all categories"
    var doneLabel = "Perfect!"
    /// The colour of a switch that is on.
    var tint = Color(hex: 0x7fe3c4)
    /// Plays the four pages by itself, back at the chat every `WatchfulEyesPermissionTimeline.loop` seconds.
    var autoPlay = false
    var onStart: (() -> Void)? = nil
    var onLater: (() -> Void)? = nil
    /// Called when every switch is on and the check mark shows.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    /// The screen's clock counts seconds from here.
    @State private var epoch: Date? = nil
    /// The chat, as if it had been arrived at a while ago: every loop begins like this.
    @State private var stage = T.Stage(from: T.donePage, to: T.chatPage, t0: -T.rest)
    @State private var switches = T.switchesOff()
    // The last tap that did something, the last "no", and the finger the eyes look at.
    @State private var press = Press(target: T.tapNone, at: -T.rest)
    @State private var shook = -T.rest
    @State private var look = Look(x: 0, y: 0, since: -T.rest, held: false)
    @State private var plan = Plan(start: .infinity, step: 0, resume: .infinity)
    /// Nothing is moving: the clock rests until the next blink, or until something is due.
    @State private var asleep = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var switchHaptic = UISelectionFeedbackGenerator()

    private typealias T = WatchfulEyesPermissionTimeline
    /// What the screen is drawn from: the stage, the seconds into it, and how much of the pages shows.
    private typealias Seen = (from: Int, to: Int, e: Double, opacity: Double)

    private struct Press: Equatable {
        var target: Int
        var at: Double
    }

    /// Where a finger makes the eyes look, since when, and whether it is still down.
    private struct Look {
        var x: Double
        var y: Double
        var since: Double
        var held: Bool
    }

    /// autoPlay: when its loop began, which of its taps is next, and when it may start over after a touch.
    private struct Plan: Equatable {
        var start: Double
        var step: Int
        var resume: Double
    }

    /// Everything the clock's driver waits on; it starts over when any of it changes.
    private struct Drive: Equatable {
        let running: Bool
        let stage: T.Stage
        let switches: T.Switches
        let pressed: Double
        let shook: Double
        let looked: Double
        let held: Bool
        let plan: Plan
        let auto: Bool
        let still: Bool
    }

    /// The screen, and the heights its three groups hang from.
    private struct Places {
        let size: CGSize
        let center: Double
        let middle: Double
        /// What the buttons keep clear of at the bottom, and the height they stand on.
        let base: Double
        let floor: Double
        let top: Double
        var eyesTop: Double { top + T.eyePlaces.top }
        var eyesHover: Double { middle + T.eyePlaces.hover }
        var eyesAsk: Double { middle + T.eyePlaces.ask }

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            center = size.width / 2
            middle = size.height / 2
            base = max(insets.bottom, 16)
            floor = size.height - base
            top = insets.top
        }
    }

    private enum Palette {
        // Night: black, a band of navy low on the screen, black again.
        static let sky = [
            Gradient.Stop(color: Color(hex: 0x050508), location: 0),
            Gradient.Stop(color: Color(hex: 0x050508), location: 0.28),
            Gradient.Stop(color: Color(hex: 0x0a1124), location: 0.55),
            Gradient.Stop(color: Color(hex: 0x11182d), location: 0.7),
            Gradient.Stop(color: Color(hex: 0x05070d), location: 0.86),
            Gradient.Stop(color: Color(hex: 0x030305), location: 1),
        ]
        static let eye = Color(hex: 0xf4ede2)
        // The eyes' glow is a disc that fades to nothing at its rim, not a blur.
        static let glow = Gradient(stops: [
            Gradient.Stop(color: Color(hex: 0xf4ede2, opacity: 0.34), location: 0),
            Gradient.Stop(color: Color(hex: 0xf4ede2, opacity: 0.13), location: 0.4),
            Gradient.Stop(color: Color(hex: 0xf4ede2, opacity: 0.035), location: 0.75),
            Gradient.Stop(color: Color(hex: 0xf4ede2, opacity: 0), location: 1),
        ])
        static let halo = Gradient(stops: [
            Gradient.Stop(color: .white.opacity(0.1), location: 0),
            Gradient.Stop(color: .white.opacity(0.035), location: 0.5),
            Gradient.Stop(color: .white.opacity(0), location: 1),
        ])
        static let house = Color(hex: 0x191e21)
        static let houseEdge = Color.white.opacity(0.1)
        static let rain = Color.white.opacity(0.12)
        static let outline = Color.white.opacity(0.22)
        static let ink = Color(hex: 0xd9dee8)
        static let faint = Color.white.opacity(0.35)
        static let panel = Color.white.opacity(0.05)
        static let soft = Color.white.opacity(0.04)
        static let heart = Color(hex: 0xff2d55)
        static let bar = Color(hex: 0x212a41)
        static let track = Color(hex: 0x2a3145)
        static let allInk = Color(hex: 0x5b6b8c)
        static let disc = Gradient(stops: [
            Gradient.Stop(color: Color(hex: 0x142030), location: 0),
            Gradient.Stop(color: Color(hex: 0x142030), location: 0.55),
            Gradient.Stop(color: Color(hex: 0x142030, opacity: 0), location: 1),
        ])
        static let check = Color(hex: 0x5fe0bc)
    }

    private enum Metrics {
        /// A smiling eye is flatter than an open one.
        static let squint = 0.5
        // The chat's bubbles keep clear of the screen's sides by these, the assistant's stop `short` of the
        // user's on the right, and the small tile on the button is this large.
        static let chat = (left: 32.0, right: 43.0, short: 5.0)
        static let smallTile = (size: 26.0, radius: 7.0, heart: 13.0)
        /// A finger that has moved less than this has tapped.
        static let slop = 8.0
        // The clock rests once a page has been arriving this long (the rain never lets the ask rest),
        // and this long after a tap, a "no", a look, a flipped switch and its knob.
        static let settle = [0.8, Double.infinity, 2, 1.3]
        static let stillSettle = 0.7
        static let pressFor = 0.4
        static let shakeFor = 0.7
        static let lookFor = 0.5
        static let flipFor = 1.3
        static let knobFor = 0.8
        /// It wakes this long before a blink, and stays up this long after one.
        static let lead = 0.05
    }

    private var now: Double { epoch.map { Date.now.timeIntervalSince($0) } ?? 0 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let places = Places(size: size, insets: insets)

            TimelineView(.animation(paused: asleep)) { timeline in
                let now = epoch.map { timeline.date.timeIntervalSince($0) } ?? 0
                let v = T.seen(stage, now, still: still)

                ZStack {
                    LinearGradient(stops: Palette.sky, startPoint: .top, endPoint: .bottom)
                    Group {
                        chat(v, places)
                        picture(v, now, places)
                        asking(v, now, places)
                        switching(v, now, places)
                    }
                    .opacity(v.opacity)
                }
                .frame(width: size.width, height: size.height)
                .clipped()
            }
            .contentShape(Rectangle())
            .gesture(touch(places))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { tapHaptic.prepare() }
        .onChange(of: autoPlay) { _, on in plan = Plan(start: .infinity, step: 0, resume: on ? 0 : .infinity) }
        .task(id: Drive(
            running: epoch != nil, stage: stage, switches: switches, pressed: press.at, shook: shook,
            looked: look.since, held: look.held, plan: plan, auto: autoPlay, still: still
        )) { await drive() }
    }

    // MARK: Pages

    /// Lines `height` apart, the first and the last centred in their own.
    private func lines(_ text: String, size: Double, height: Double) -> some View {
        let extra = height - UIFont.systemFont(ofSize: size, weight: .medium).lineHeight
        return Text(text)
            .font(.system(size: size, weight: .medium))
            .lineSpacing(extra)
            .padding(.vertical, extra / 2)
            .fixedSize(horizontal: false, vertical: true)
            .foregroundStyle(Palette.ink)
    }

    private func label(_ text: String, size: Double = 14, weight: Font.Weight = .medium, color: Color = .white) -> some View {
        Text(text).font(.system(size: size, weight: weight)).foregroundStyle(color).lineLimit(1)
    }

    /// What a screen reader needs of a button that is drawn, not pressed: its page, and what a tap on it means.
    private func control(_ view: some View, on page: Int, _ action: Int) -> some View {
        view
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { act(action, at: now) }
            .accessibilityHidden(stage.to != page)
    }

    /// How much a tapped button gives.
    private func give(_ target: Int, _ now: Double) -> Double {
        still || press.target != target ? 1 : T.pressed(now - press.at)
    }

    private func chat(_ v: Seen, _ p: Places) -> some View {
        let c = T.chat(v.from, v.to, v.e)
        return control(
            VStack(spacing: 8) {
                ForEach(Array(messages.enumerated()), id: \.offset) { _, one in
                    lines(one.text, size: 13.5, height: 21)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10.5)
                        .background(RoundedRectangle(cornerRadius: 18).fill(Palette.panel))
                        .padding(.trailing, one.mine ? 0 : Metrics.chat.short)
                        .frame(maxWidth: .infinity, alignment: one.mine ? .trailing : .leading)
                }
            }
            .padding(.leading, Metrics.chat.left)
            .padding(.trailing, Metrics.chat.right)
            .padding(.top, p.top + T.chatTop)
            .frame(width: p.size.width, height: p.size.height, alignment: .top)
            .offset(y: c.shift)
            .opacity(c.opacity),
            on: T.chatPage, T.tapChat
        )
    }

    @ViewBuilder
    private func asking(_ v: Seen, _ now: Double, _ p: Places) -> some View {
        let said = T.ask(v.from, v.to, v.e, part: 0)
        let start = T.ask(v.from, v.to, v.e, part: 1)
        let later = T.ask(v.from, v.to, v.e, part: 2)
        let tile = Metrics.smallTile

        lines(message, size: 16.5, height: 26)
            .padding(.horizontal, 20)
            .padding(.vertical, 13.5)
            .frame(width: p.size.width - T.bubble.side * 2, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 22).fill(Palette.soft))
            .padding(.bottom, p.base + T.bubble.bottom)
            .frame(width: p.size.width, height: p.size.height, alignment: .bottom)
            .offset(y: said.shift)
            .opacity(said.opacity)
            .accessibilityHidden(stage.to != T.askPage)

        control(
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: tile.radius)
                    .fill(.white)
                    .frame(width: tile.size, height: tile.size)
                    .overlay {
                        T.heartPath
                            .applying(CGAffineTransform(scaleX: tile.heart / 2, y: tile.heart / 2))
                            .offsetBy(dx: tile.size / 2, dy: tile.size / 2)
                            .fill(Palette.heart)
                    }
                label(startLabel)
            }
            .frame(width: p.size.width - T.row.side * 2, height: T.row.height)
            .background(RoundedRectangle(cornerRadius: 18).fill(Palette.soft))
            .scaleEffect(give(T.tapStart, now))
            .offset(y: start.shift)
            .opacity(start.opacity)
            .position(x: p.center, y: p.floor - T.row.bottom - T.row.height / 2),
            on: T.askPage, T.tapStart
        )

        control(
            label(laterLabel, size: 13, color: Palette.faint)
                .frame(width: T.later.reach * 2, height: T.later.height)
                .scaleEffect(give(T.tapLater, now))
                .offset(y: later.shift)
                .opacity(later.opacity)
                .position(x: p.center, y: p.floor - T.later.bottom),
            on: T.askPage, T.tapLater
        )
    }

    @ViewBuilder
    private func switching(_ v: Seen, _ now: Double, _ p: Places) -> some View {
        let name = T.item(v.from, v.to, v.e, T.itemOrder.title)
        let first = T.item(v.from, v.to, v.e, T.itemOrder.all)
        let off = T.allOn(switches, now)
        let done = T.check(v.from, v.to, v.e)

        // What shrinks into the check mark moves towards it as well.
        label(title, size: 17)
            .scaleEffect(name.scale)
            .offset(y: name.rise + (T.titleY - T.mark.y) * (name.scale - 1))
            .opacity(name.opacity)
            .position(x: p.center, y: p.middle + T.titleY)
            .accessibilityHidden(stage.to != T.switchesPage)

        control(
            ZStack(alignment: .leading) {
                label(allOnLabel, size: 13, weight: .regular, color: Palette.allInk).opacity(1 - off)
                label(allOffLabel, size: 13, weight: .regular, color: Palette.allInk).opacity(off)
            }
            .padding(.leading, 12)
            .frame(width: T.list.width, height: T.all.height, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 8).fill(Palette.panel))
            .scaleEffect(first.scale * give(T.tapAll, now))
            .offset(y: first.rise + (T.all.top + T.all.height / 2 - T.mark.y) * (first.scale - 1))
            .opacity(first.opacity)
            .position(x: p.center, y: p.middle + T.all.top + T.all.height / 2),
            on: T.switchesPage, T.tapAll
        )

        label(doneLabel, size: 17)
            .offset(y: done.rise)
            .opacity(done.label)
            .position(x: p.center, y: p.middle + T.labelY)
            .accessibilityHidden(stage.to != T.donePage)

        control(
            label(buttonLabel)
                .frame(width: p.size.width - T.pillBox.side * 2, height: T.pillBox.height)
                .background(Capsule().fill(Palette.soft))
                .scaleEffect(give(T.tapPill, now))
                .opacity(T.pill(v.from, v.to, v.e))
                .position(x: p.center, y: p.floor - T.pillBox.bottom - T.pillBox.height / 2),
            on: stage.to == T.donePage ? T.donePage : T.switchesPage, T.tapPill
        )
    }

    // MARK: The picture

    /// The eyes as drawn: with what a "no", a flipped switch and a finger add to their place.
    private func pose(_ v: Seen, _ now: Double, _ p: Places) -> (x: Double, y: Double, size: Double, gap: Double, glow: Double, halo: Double, tall: Double, smile: Double) {
        let e = T.eyes(v.from, v.to, v.e, still: still, top: p.eyesTop, hover: p.eyesHover, ask: p.eyesAsk)
        if still { return (p.center, e.y, e.size, e.gap, e.glow, e.halo, e.tall, e.smile) }
        let f = T.follow(held: look.held, now - look.since)
        return (
            p.center + e.dx + T.shake(now - shook) + look.x * f,
            e.y + T.lookDown(now - switches.flippedAt) + look.y * f,
            e.size, e.gap, e.glow, e.halo, e.tall, e.smile
        )
    }

    /// Everything that is drawn, back to front: the eyes' halo, their home, the phone and its switches, the check mark, the eyes.
    private func picture(_ v: Seen, _ now: Double, _ p: Places) -> some View {
        let eyes = pose(v, now, p)
        let home = T.house(v.from, v.to, v.e)
        let edge = T.outline(v.from, v.to, v.e)
        let tile = T.item(v.from, v.to, v.e, T.itemOrder.tile)
        let done = T.check(v.from, v.to, v.e)
        return Canvas { context, _ in
            // The soft light the eyes throw while they are large.
            var light = context
            light.opacity = eyes.halo
            light.fillEllipse(center: CGPoint(x: eyes.x, y: eyes.y), radii: CGSize(width: T.halo, height: T.halo), gradient: Palette.halo)

            if home.opacity > 0 {
                var layer = context
                layer.opacity = home.opacity
                layer.translateBy(x: p.center, y: p.eyesAsk)
                layer.scaleBy(x: home.scale, y: home.scale)
                layer.fill(T.housePath, with: .color(Palette.house))
                var inside = layer
                inside.clip(to: T.housePath)
                inside.translateBy(x: 0, y: T.rain(now, still: still))
                inside.fill(T.rainPath, with: .color(Palette.rain))
                layer.stroke(T.housePath, with: .color(Palette.houseEdge), lineWidth: 1)
            }

            // The phone's outline, drawn from the middle of the notch to both sides at once.
            if edge.opacity > 0 {
                var layer = context
                layer.opacity = edge.opacity
                let half = T.outlinePath(middle: p.center, top: p.middle + T.phone.top, bottom: p.size.height + T.phone.radius).trimmedPath(from: 0, to: edge.drawn)
                layer.stroke(half, with: .color(Palette.outline), lineWidth: 1)
                layer.translateBy(x: p.size.width, y: 0)
                layer.scaleBy(x: -1, y: 1)
                layer.stroke(half, with: .color(Palette.outline), lineWidth: 1)
            }

            let content = context.scaled(tile.scale, about: CGPoint(x: p.center, y: p.middle + T.mark.y))
            if tile.opacity > 0 {
                var box = content
                box.opacity = tile.opacity
                box.translateBy(x: p.center, y: p.middle + T.tile.y + tile.rise)
                box.fill(Path(roundedRect: CGRect(x: -T.tile.size / 2, y: -T.tile.size / 2, width: T.tile.size, height: T.tile.size), cornerRadius: T.tile.radius), with: .color(.white))
                box.scaleBy(x: T.tile.heart / 2, y: T.tile.heart / 2)
                box.fill(T.heartPath, with: .color(Palette.heart))
            }
            for row in 0..<T.count {
                let it = T.item(v.from, v.to, v.e, T.itemOrder.row + row)
                guard it.opacity > 0 else { continue }
                var line = content
                line.opacity = it.opacity
                line.translateBy(x: p.center - T.list.width / 2, y: p.middle + T.list.top + Double(row) * T.list.pitch + it.rise)
                line.fill(capsule(CGRect(x: 0, y: -T.list.thick / 2, width: T.list.bar, height: T.list.thick)), with: .color(Palette.bar))
                let on = T.knob(on: switches.on[row], now - switches.at[row], still: still)
                let track = capsule(CGRect(x: T.list.width - T.toggle.width, y: -T.toggle.height / 2, width: T.toggle.width, height: T.toggle.height))
                line.fill(track, with: .color(Palette.track))
                // The track of a switch that is on, faded in over the one that is off.
                var lit = line
                lit.opacity = it.opacity * T.clamp01(on)
                lit.fill(track, with: .color(tint))
                let x = T.list.width - T.toggle.width + T.toggle.height / 2 + (T.toggle.width - T.toggle.height) * on
                line.fill(Path(ellipseIn: CGRect(x: x - T.toggle.knob / 2, y: -T.toggle.knob / 2, width: T.toggle.knob, height: T.toggle.knob)), with: .color(.white))
            }

            if done.opacity > 0 {
                var layer = context
                layer.opacity = done.opacity
                layer.translateBy(x: p.center, y: p.middle + T.mark.y)
                layer.scaleBy(x: done.scale, y: done.scale)
                layer.fillEllipse(center: .zero, radii: CGSize(width: T.mark.disc / 2, height: T.mark.disc / 2), gradient: Palette.disc)
                layer.stroke(
                    T.checkPath.trimmedPath(from: 0, to: done.drawn), with: .color(Palette.check),
                    style: StrokeStyle(lineWidth: T.checkStroke, lineCap: .round, lineJoin: .round)
                )
            }

            for side in [-1.0, 1.0] {
                let middle = CGPoint(x: eyes.x + side * eyes.gap / 2, y: eyes.y)
                let reach = eyes.size / 2 + eyes.glow
                // A shut eye gives no light.
                var glow = context
                glow.opacity = min(1, eyes.tall)
                glow.fillEllipse(center: middle, radii: CGSize(width: reach, height: reach), gradient: Palette.glow)
                let height = eyes.size * eyes.tall * (1 - Metrics.squint * eyes.smile)
                var disc = context
                disc.opacity = 1 - eyes.smile
                disc.fill(Path(ellipseIn: CGRect(x: middle.x - eyes.size / 2, y: middle.y - height / 2, width: eyes.size, height: height)), with: .color(Palette.eye))
                var arc = context
                arc.opacity = eyes.smile
                arc.translateBy(x: middle.x, y: middle.y)
                arc.scaleBy(x: eyes.size, y: eyes.size)
                arc.stroke(T.smilePath, with: .color(Palette.eye), style: StrokeStyle(lineWidth: T.smileStroke, lineCap: .round))
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Touch

    private func go(_ to: Int, at: Double) {
        stage = T.Stage(from: stage.to, to: to, t0: at)
        // The switches' page always opens with everything off; it is not on the screen yet.
        if to == T.switchesPage { switches = T.switchesOff() }
    }

    /// Does what a tap means, as of `at`. A touch and autoPlay both come through here.
    @discardableResult
    private func act(_ action: Int, at: Double) -> Bool {
        guard action != T.tapNone, T.ready(stage.to, at - stage.t0) else { return false }
        switch (stage.to, action) {
        case (T.chatPage, _):
            go(T.askPage, at: at)
        case (T.askPage, T.tapStart):
            go(T.switchesPage, at: at)
            onStart?()
        case (T.askPage, T.tapLater):
            shook = at
            onLater?()
        case (T.switchesPage, T.tapAll):
            switches = T.flip(switches, switches.all ? T.flipAllOff : T.flipAllOn, at: at)
        case (T.switchesPage, T.tapPill):
            switches = T.flip(switches, T.flipAllOn, at: at)
        case (T.switchesPage, T.tapSwitch...):
            switches = T.flip(switches, action - T.tapSwitch, at: at)
        case (T.donePage, T.tapPill):
            go(T.chatPage, at: at)
        default:
            return false
        }
        press = Press(target: action, at: at)
        return true
    }

    /// A touch takes over from autoPlay, which goes back to the chat and starts again a while later.
    private func touched(_ now: Double) {
        if autoPlay { plan = Plan(start: .infinity, step: 0, resume: now + T.userHold) }
    }

    /// The eyes look towards a finger for as long as it is down; one that has not moved has tapped.
    private func touch(_ p: Places) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = self.now
                if !look.held {
                    touched(now)
                    look = Look(x: 0, y: 0, since: now, held: true)
                }
                look.x = T.toward(value.location.x - p.center)
                look.y = T.toward(value.location.y - pose(T.seen(stage, now, still: still), now, p).y)
            }
            .onEnded { value in
                let now = self.now
                look.since = now
                look.held = false
                guard abs(value.translation.width) < Metrics.slop, abs(value.translation.height) < Metrics.slop else { return }
                touched(now)
                let action = T.hit(stage.to, value.startLocation, p.size, floor: p.floor)
                guard act(action, at: now) else { return }
                if action >= T.tapSwitch { switchHaptic.selectionChanged() } else { tapHaptic.impactOccurred() }
            }
    }

    // MARK: The clock

    /// Until when something that a touch or a page change began is still moving.
    private var busy: Double {
        if look.held { return .infinity }
        let page = stage.t0 + (still ? Metrics.stillSettle : Metrics.settle[stage.to])
        let knobs = (switches.at.max() ?? 0) + Metrics.knobFor
        return max(page, knobs, switches.flippedAt + Metrics.flipFor, press.at + Metrics.pressFor, shook + Metrics.shakeFor, look.since + Metrics.lookFor)
    }

    /// The blink the eyes are in at `now`, or else their next one, with a little room on both sides.
    private func blink(at now: Double) -> (from: Double, to: Double) {
        if still { return (.infinity, .infinity) }
        let first = stage.t0 + T.blinks[stage.to].first
        let every = T.blinks[stage.to].every
        let past = (now - first - T.blinkFor - Metrics.lead) / every
        let start = first + max(0, past.rounded(.down) + 1) * every
        return (start - Metrics.lead, start + T.blinkFor + Metrics.lead)
    }

    /// Does what is due, and lets the clock rest while nothing moves. Starts over whenever a touch or a step changes something.
    private func drive() async {
        guard epoch != nil else {
            epoch = .now
            plan = Plan(start: .infinity, step: 0, resume: autoPlay ? 0 : .infinity)
            return
        }
        while !Task.isCancelled {
            let now = self.now
            // What is due: the last page once every switch is on, autoPlay's fresh start, or its next tap.
            let done = stage.to == T.switchesPage ? switches.doneAt : .infinity
            let tap = plan.start + (plan.step < T.play.count ? T.play[plan.step].at : T.loop)
            let auto = autoPlay ? min(plan.resume, tap) : .infinity
            if now >= min(done, auto) {
                asleep = false
                if done <= auto {
                    go(T.donePage, at: done)
                    onDone?()
                } else if plan.resume <= tap {
                    // autoPlay begins, or the touches are over: back to the chat, and on with the loop from there.
                    if stage.to == T.chatPage { stage = T.Stage(from: T.chatPage, to: T.chatPage, t0: now - T.rest) } else { go(T.chatPage, at: now) }
                    plan = Plan(start: stage.t0 + T.rest, step: 0, resume: .infinity)
                } else if plan.step < T.play.count {
                    // Timed from when it was due, not from now: every loop is the same length.
                    act(T.play[plan.step].tap, at: tap)
                    plan.step += 1
                } else {
                    plan = Plan(start: tap, step: 0, resume: .infinity)
                }
                return
            }
            let wink = blink(at: now)
            let awake = now >= wink.from ? max(busy, wink.to) : busy
            asleep = now >= awake
            // An hour stands in for "until something changes", which starts this over anyway.
            let until = min(done, auto, asleep ? wink.from : awake, now + 3600)
            try? await Task.sleep(for: .seconds(max(0, until - now)))
        }
    }
}

#Preview {
    WatchfulEyesPermission(autoPlay: true)
}
