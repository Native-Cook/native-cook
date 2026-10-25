import SwiftUI

/// "Northline", a commuter app whose bottom navigation is a little signal box: four painted levers
/// with steel handles (Home, Trips, Tickets, Account) under an enamel track diagram. Pulling a
/// lever sets a route: the two switch blades throw, the section's signal and lever lamp turn from
/// red to green, the route lights up, a two-car train runs from the current section's platform to
/// the new one, and the page slides in from the side the train went. The machinery is Canvas
/// drawing (RailwaySwitchNavArt.swift); the pages are views.
///
/// autoPlay (loop = 9 s, RailwaySwitchNavMotion.swift; a "trip" is one lever pull):
///   0.0 – 1.2   rest at Home.
///   1.2 – 3.6   pull Tickets, the train runs Home → Tickets, the Tickets page slides in.
///   4.4 – 6.4   pull Trips, the train runs back left, the page comes from the left.
///   6.8 – 8.4   pull Home, the train returns; rest to 9.0.
/// Touch: drag a lever down or tap it; past 40 % it commits the trip, short of that it springs
/// back. 2 s after the last touch the train goes home on a quick trip and the loop starts again.
/// Reduced motion: no loop; a lever pull changes the page and lamps at once, no train run.
struct RailwaySwitchNav: View {
    var autoPlay = false

    static let loop = RailwaySwitchNavData.loop
    static let background = railwaySwitchNavHex(0x131D19)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = RailwaySwitchNavModel()
    @State private var touchLive = false
    @State private var touchSerial = 0
    @State private var dragging = false

    private typealias D = RailwaySwitchNavData
    private typealias G = RailwaySwitchNavGeo
    private typealias S = RailwaySwitchNavStyle
    /// The pages' height when the screen has room; on a short screen they get what is left.
    private static let pageHeight = 320.0

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var paused: Bool { !(auto || touchLive) }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        model.configure(auto: auto, reduced: reduceMotion)
        // The pages take up to 320 pt; the track diagram (the hero) grows into the rest, 150 – 240 pt.
        let pageTop = insets.top + 64
        let mapBottom = size.height - insets.bottom - G.frameH - 12
        let mapH = max(G.mapMin, min(240, mapBottom - pageTop - 16 - Self.pageHeight))
        let mapTop = mapBottom - mapH
        let pageH = mapTop - 16 - pageTop
        let g = G(w: size.width, h: size.height, mapTop: mapTop, mapH: mapH)
        let pageW = size.width - 48

        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: railwaySwitchNavHex(0x18251F), location: 0),
                    .init(color: railwaySwitchNavHex(0x131D19), location: 0.5),
                    .init(color: railwaySwitchNavHex(0x0E1512), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )

            header
                .padding(.horizontal, 24)
                .frame(width: size.width, height: 36)
                .offset(y: insets.top + 12)

            // The pages: the old one slides fully out (clipped by the page area) as the new one
            // slides in beside it.
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                ZStack(alignment: .topLeading) {
                    ForEach(0..<D.count, id: \.self) { k in
                        let (dx, op) = pageMotion(k, p, width: pageW)
                        RailwaySwitchNavPage(k: k, room: pageH)
                            .frame(width: pageW, alignment: .topLeading)
                            .offset(x: dx)
                            .opacity(op)
                    }
                }
                .frame(width: pageW, height: max(160, pageH), alignment: .topLeading)
                .clipped()
            }
            .offset(x: 24, y: pageTop)
            .allowsHitTesting(false)

            Canvas { ctx, _ in RailwaySwitchNavArt.drawStatic(ctx, g) }
                .frame(width: size.width, height: size.height)
                .allowsHitTesting(false)
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                Canvas { ctx, _ in RailwaySwitchNavArt.drawMoving(ctx, g, p) }
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)

            labels(g: g, size: size)
                .allowsHitTesting(false)

            levers(g: g, size: size)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Self.background)
        .clipped()
    }

    private func pageMotion(_ k: Int, _ p: RailwaySwitchNavPose, width: Double) -> (Double, Double) {
        let dir = p.to > p.from ? 1.0 : -1.0
        if p.from == p.to { return (0, k == p.to ? 1 : 0) }
        let span = width + 24
        if k == p.to { return (dir * span * (1 - p.page), 1) }
        if k == p.from { return (-dir * span * p.page, p.page < 1 ? 1 : 0) }
        return (0, 0)
    }

    // MARK: Header

    private var header: some View {
        HStack {
            HStack(spacing: 10) {
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 9, style: .continuous).fill(S.gold)
                    Capsule().fill(S.dark).frame(width: 20, height: 3).offset(x: 4, y: 13)
                    Capsule().fill(S.dark).frame(width: 20, height: 3).rotationEffect(.degrees(-35)).offset(x: 4, y: 9)
                }
                .frame(width: 28, height: 28, alignment: .topLeading)
                .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                Text("Northline")
                    .font(.system(size: 20, weight: .bold))
                    .kerning(-0.3)
                    .foregroundStyle(S.ink)
            }
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                Circle().fill(S.good).frame(width: 7, height: 7)
                Text("Live · Elm Cross")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(S.muted)
            }
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(Capsule().fill(S.line.opacity(0.06)))
            .overlay(Capsule().strokeBorder(S.line.opacity(0.16), lineWidth: 1 / 3))
        }
    }

    // MARK: Labels

    private func labels(g: G, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            Text("BOX 4 · ELM CROSS JUNCTION")
                .font(.system(size: 9, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(Color(red: 232 / 255, green: 224 / 255, blue: 200 / 255).opacity(0.45))
                .fixedSize()
                .offset(x: g.px + 24, y: g.py + g.ph - 22)
            ForEach(0..<D.count, id: \.self) { k in
                Text(D.sections[k].uppercased())
                    .font(.system(size: 9, weight: .bold))
                    .kerning(1.1)
                    .foregroundStyle(Color(red: 232 / 255, green: 224 / 255, blue: 200 / 255).opacity(0.6))
                    .lineLimit(1)
                    .fixedSize()
                    .frame(width: 56, alignment: .trailing)
                    .offset(x: g.xs[k] - 82, y: g.top + g.plat + 6)
            }
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                ZStack(alignment: .topLeading) {
                    ForEach(0..<D.count, id: \.self) { k in
                        plate(k)
                            .overlay(plate(k, on: true).opacity(p.signals[k]))
                            .frame(width: 72, height: 24)
                            .offset(x: g.xs[k] - 36, y: g.pivot + 12)
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func plate(_ k: Int, on: Bool = false) -> some View {
        Text(D.sections[k].uppercased())
            .font(.system(size: 10, weight: .heavy))
            .kerning(1.2)
            .foregroundStyle(on ? railwaySwitchNavHex(0x2B1C05) : Color(red: 60 / 255, green: 40 / 255, blue: 10 / 255).opacity(0.5))
            .lineLimit(1)
            .fixedSize()
            .frame(width: 72, height: 24)
    }

    // MARK: Levers (touch)

    private func levers(g: G, size: CGSize) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: size.width, height: max(0, size.height - g.frame))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        if !dragging {
                            beginTouch()
                            model.began(at: v.startLocation, xs: g.xs)
                        }
                        model.moved(to: v.location)
                    }
                    .onEnded { v in
                        model.ended(at: v.location)
                        endTouch()
                    }
            )
            .offset(y: g.frame)
            .accessibilityElement()
            .accessibilityLabel("Sections: Home, Trips, Tickets, Account. Pull a lever to go there.")
    }

    private func beginTouch() {
        dragging = true
        touchSerial += 1
        touchLive = true
    }

    /// Keeps the timeline running until the trip and the return home have finished.
    private func endTouch() {
        dragging = false
        touchSerial += 1
        let serial = touchSerial
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(400))
            while model.moving && serial == touchSerial {
                try? await Task.sleep(for: .milliseconds(300))
            }
            if serial == touchSerial && !auto { touchLive = false }
        }
    }
}
