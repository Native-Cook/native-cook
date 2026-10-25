import SwiftUI
import UIKit

/*
 Card Catalog List — a library's card catalogue drawer, seen from above: 24 index cards stand on
 their bottom edges behind a reading line. Thumbing through tips each card forward over the
 drawer's lip (past upright you see its back) while the cards behind lean back, fanning further
 the faster you go; the panel under the drawer rolls the front card's title, author, call number
 and status, and the counter rolls its number.

 autoPlay (loop = 9 s, CardCatalogListMotion.autoPosition): rest 0–1.4 s; thumb forward 1.4–4.6 s
 to Gaskell (card 15) with a small overshoot; hold; thumb back 5.8–7.6 s; rest.

 Touch: drag down to thumb forward, up to go back (rubber-banded at the ends, released onto the
 nearest card with the finger's speed); a tap tips one card. A selection haptic per card. Two
 seconds after the last touch the drawer eases back and the loop restarts. Reduced motion: the
 drawer open at Gaskell; nothing plays.
 */
struct CardCatalogList: View {
    /// Thumbs through the drawer to Gaskell and back, every loop.
    var autoPlay = false

    static let loop = 9.0
    typealias M = CardCatalogListMotion
    typealias Art = CardCatalogListArt

    static let cream = Color(hex: 0xF3E8D4)
    static let muted = Color.rgba(243, 232, 212, 0.62)
    static let amber = Color(hex: 0xE4B36A)
    /// The card's perspective: far enough that a card lying forward does not leave the screen.
    static let perspective = 1600.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = CardCatalogListModel()
    @State private var art: Art.Baked?
    @State private var awake = false

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let roomH = size.height - insets.top - insets.bottom
            let k = min(size.width / M.designW, roomH / M.designH, 1.15)
            let ox = (size.width - M.designW * k) / 2
            let oy = insets.top + max(0, (roomH - M.designH * k) / 2)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, sz in Art.drawBackdrop(ctx, size: sz, ox: ox, oy: oy, k: k, art: art) }
                    .frame(width: size.width, height: size.height)
                design
                    .frame(width: M.designW, height: M.designH, alignment: .topLeading)
                    .scaleEffect(k, anchor: .topLeading)
                    .offset(x: ox, y: oy)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x1C130D).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task { art = await Art.shared.value }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    private var design: some View {
        let ready = art != nil
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !((auto && ready) || awake))) { timeline in
                let p = model.frame(timeline.date.timeIntervalSinceReferenceDate, auto: auto && ready, reduced: reduceMotion) {
                    DispatchQueue.main.async { awake = false }
                }
                ZStack(alignment: .topLeading) {
                    CardCatalogListHeader(s: p.s)
                    cards(p)
                    CardCatalogListFront(art: art)
                        .offset(x: M.front.minX, y: M.front.minY)
                    CardCatalogListPanel(s: p.s)
                }
                .frame(width: M.designW, height: M.designH, alignment: .topLeading)
            }
            .allowsHitTesting(false)
            Color.clear
                .contentShape(Rectangle())
                .frame(width: M.drawer.w, height: M.front.minY - M.drawer.y + 40)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            model.drag(v.translation.height, at: Date.now.timeIntervalSinceReferenceDate)
                        }
                        .onEnded { v in
                            model.end(v.translation, velocity: v.velocity.height, at: Date.now.timeIntervalSinceReferenceDate)
                        }
                )
                .offset(x: M.drawer.x, y: M.drawer.y)
                .accessibilityElement()
                .accessibilityLabel("Catalogue drawer. Drag down to thumb forward, up to go back, or tap to tip one card.")
                .accessibilityAddTraits(.allowsDirectInteraction)
            CardCatalogListHold()
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
    }

    private func cards(_ p: CardCatalogListModel.Pose) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(M.cards.enumerated().reversed()), id: \.offset) { i, card in
                let pose = M.cardPose(Double(i) - p.s, p.lean)
                if pose.opacity > 0.001 {
                    CardCatalogListCard(card: card, index: i)
                        .overlay(CardCatalogListBack(tab: card.tab).opacity(pose.back))
                        .overlay(CardCatalogListShade(tab: card.tab).opacity(pose.shade))
                        .rotation3DEffect(.degrees(pose.rot), axis: (x: 1, y: 0, z: 0), anchor: .bottom,
                                          perspective: M.cardH / Self.perspective * (M.cardW / M.cardH))
                        .scaleEffect(pose.scale, anchor: .bottom)
                        .offset(y: pose.ty)
                        .opacity(pose.opacity)
                        .offset(x: M.cardX, y: M.pivotY - M.cardH)
                }
            }
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
    }
}

// MARK: - Model

final class CardCatalogListModel {
    typealias M = CardCatalogListMotion

    struct Pose {
        var s: Double
        var lean: Double
    }

    private static let last = Double(M.cards.count - 1)

    private var autoStart: Double?
    private var lastFrame: Double?
    private(set) var pose = Pose(s: 0, lean: 0)

    private var owned = false
    private var touching = false
    private var returning = false
    private var releasedAt = 0.0
    private var returnEnd = 0.0
    private var start = 0.0
    private var panning = false
    private var held = M.Track(0)
    private var lean = 0.0
    private var reduced = false
    private var lastCard = 0.0

    private let tick = UISelectionFeedbackGenerator()

    private var rest: Double { reduced ? M.target : 0 }

    func frame(_ now: Double, auto: Bool, reduced: Bool, sleep: () -> Void) -> Pose {
        self.reduced = reduced
        let dt = min(max(now - (lastFrame ?? now), 0), 1 / 20)
        defer { lastFrame = now }
        if owned {
            if !touching && !returning && now - releasedAt >= 2 {
                returning = true
                let far = abs(held.value(now) - rest)
                let d = min(0.85, 0.3 + far * 0.04)
                held.timing(rest, d, .bezier(0.65, 0, 0.35, 1), at: now)
                returnEnd = now + d
            }
            if returning && now >= returnEnd {
                returning = false
                owned = false
                autoStart = now
                sleep()
            }
        }
        if owned {
            let s = held.value(now)
            let speed = dt > 0 ? abs(s - pose.s) / dt : 0
            lean += (min(speed * 1.4, 12) - lean) * min(1, dt * 9)
            if s.rounded() != lastCard {
                lastCard = s.rounded()
                tick.selectionChanged()
            }
            pose = Pose(s: s, lean: lean)
            return pose
        }
        if reduced {
            pose = Pose(s: M.target, lean: 0)
            return pose
        }
        guard auto else {
            autoStart = nil
            pose = Pose(s: 0, lean: 0)
            return pose
        }
        if autoStart == nil { autoStart = now }
        if let last = lastFrame, now - last > 0.25, let s = autoStart { autoStart = s + (now - last) - 1 / 60 }
        let t = max(0, (now - autoStart!).truncatingRemainder(dividingBy: M.loop))
        pose = Pose(s: M.autoPosition(t), lean: M.autoLean(t))
        lean = pose.lean
        return pose
    }

    /// A finger lands: the drawer stops playing (or returning) and stays where it is, held by the finger.
    private func takeOver(_ now: Double) {
        touching = true
        if !owned || returning {
            held.set(pose.s)
            lastCard = pose.s.rounded()
        } else {
            held.set(held.value(now))
        }
        returning = false
        owned = true
    }

    func drag(_ dy: Double, at now: Double) {
        if !touching {
            takeOver(now)
            start = held.value(now)
            panning = false
        }
        if !panning && abs(dy) > 6 { panning = true }
        guard panning else { return }
        // Pulling down tips the front card toward you, so the drawer moves on.
        held.set(M.rubberBand(start + dy / M.ptPerCard, Self.last))
    }

    func end(_ translation: CGSize, velocity: Double, at now: Double) {
        guard touching else { return }
        if panning {
            let v = velocity / M.ptPerCard
            let aim = min(Self.last, max(0, (held.value(now) + v * 0.18).rounded()))
            held.spring(aim, damping: 22, stiffness: 170, mass: 1, velocity: v, at: now)
        } else if hypot(translation.width, translation.height) < 8 {
            // A tap tips one card forward.
            held.spring(min(Self.last, held.value(now).rounded() + 1), damping: 22, stiffness: 170, mass: 1, at: now)
        }
        panning = false
        touching = false
        releasedAt = now
    }
}

// MARK: - Pieces

struct CardCatalogListHeader: View {
    var s: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("ELM STREET LIBRARY")
                .font(.system(size: 12, weight: .bold)).kerning(2)
                .foregroundStyle(CardCatalogList.amber)
            HStack {
                Text("Fiction, F–G")
                    .font(.system(size: 32, weight: .heavy)).kerning(-0.6)
                    .foregroundStyle(CardCatalogList.cream)
                Spacer(minLength: 8)
                HStack(spacing: 0) {
                    Text("CARD")
                        .font(.system(size: 10, weight: .bold)).kerning(1.4)
                        .foregroundStyle(CardCatalogList.muted)
                        .padding(.trailing, 6)
                    CardCatalogListDigit(value: s, place: 10)
                    CardCatalogListDigit(value: s, place: 1)
                    Text("/ \(CardCatalogListMotion.cards.count)")
                        .font(.system(size: 14, weight: .semibold).monospacedDigit())
                        .foregroundStyle(CardCatalogList.muted)
                        .padding(.leading, 4)
                }
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.rgba(243, 232, 212, 0.08))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.rgba(243, 232, 212, 0.22), lineWidth: 0.5))
                )
                .accessibilityElement()
                .accessibilityLabel("Card of \(CardCatalogListMotion.cards.count)")
            }
            Text("Drawer 14 · Card catalogue, ground floor")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(CardCatalogList.muted)
        }
        .frame(width: 345, alignment: .leading)
        .offset(x: 24, y: 8)
    }
}

/// One digit of the card number, rolling like a counter wheel.
struct CardCatalogListDigit: View {
    var value: Double
    var place: Int

    var body: some View {
        let v = max(1, value + 1)
        let ones = v.truncatingRemainder(dividingBy: 10)
        let at = place == 1 ? ones : floor(v / 10) + min(max(ones - 9, 0), 1)
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 15, weight: .bold).monospacedDigit())
                    .foregroundStyle(CardCatalogList.cream)
                    .frame(width: 9, height: 20)
            }
        }
        .offset(y: -at * 20)
        .frame(width: 9, height: 20, alignment: .top)
        .clipped()
    }
}

/// A column of labels in a clipped box, rolled to the card at the front.
struct CardCatalogListRoll: View {
    var s: Double
    var height: Double
    var lines: [String]
    var font: Font
    var color: Color
    var alignment: Alignment = .leading

    var body: some View {
        let at = min(max(s, -0.4), Double(lines.count) - 0.6)
        VStack(spacing: 0) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                Text(line)
                    .font(font)
                    .foregroundStyle(color)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: alignment)
                    .frame(height: height)
            }
        }
        .offset(y: -at * height)
        .frame(height: height, alignment: .top)
        .clipped()
    }
}

struct CardCatalogListPanel: View {
    var s: Double

    private static let cards = CardCatalogListMotion.cards
    private static let titles = cards.map(\.title)
    private static let bylines = cards.map { "\($0.given) \($0.surname) · \($0.year)" }
    private static let calls = cards.map { $0.call.replacingOccurrences(of: "\n", with: " ") }
    private static let statuses = cards.map { CardCatalogListMotion.statusLabel[$0.status] }

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                CardCatalogListRoll(s: s, height: 24, lines: Self.titles, font: .system(size: 18, weight: .bold), color: CardCatalogList.cream)
                CardCatalogListRoll(s: s, height: 20, lines: Self.bylines, font: .system(size: 14, weight: .medium).monospacedDigit(), color: CardCatalogList.muted)
            }
            VStack(alignment: .trailing, spacing: 6) {
                CardCatalogListRoll(s: s, height: 20, lines: Self.calls, font: .custom("CourierNewPS-BoldMT", fixedSize: 15),
                                    color: CardCatalogList.amber, alignment: .trailing)
                CardCatalogListRoll(s: s, height: 18, lines: Self.statuses, font: .system(size: 12, weight: .semibold),
                                    color: Color.rgba(170, 210, 160, 0.9), alignment: .trailing)
            }
            .frame(width: 104)
        }
        .padding(.horizontal, 18)
        .frame(width: 361, height: 96)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.rgba(24, 15, 9, 0.82))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Color.rgba(243, 232, 212, 0.16), lineWidth: 0.5))
                .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.35), radius: 15, y: 12)
        )
        .offset(x: 16, y: 590)
    }
}

struct CardCatalogListFront: View {
    var art: CardCatalogListArt.Baked?

    var body: some View {
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in CardCatalogListArt.drawFront(ctx, art: art) }
                .frame(width: CardCatalogListMotion.front.width + 12, height: CardCatalogListMotion.front.height + 24)
            VStack(spacing: 0) {
                Text("FICTION · F – G")
                    .font(.custom("Georgia-Bold", fixedSize: 15)).kerning(1.2)
                    .foregroundStyle(Color(hex: 0x3A2614))
                Text("DRAWER 14")
                    .font(.system(size: 8, weight: .bold)).kerning(1.6)
                    .foregroundStyle(Color.rgba(58, 38, 20, 0.7))
            }
            .frame(width: 160, height: 34)
            .offset(x: CardCatalogListMotion.front.width / 2 - 80, y: 24)
        }
    }
}

struct CardCatalogListHold: View {
    var body: some View {
        Button {} label: {
            Text("Place a hold")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Color(hex: 0x2A1808))
                .frame(width: 361, height: 52)
                .background(
                    Capsule()
                        .fill(LinearGradient(stops: [
                            .init(color: Color(hex: 0xF2C98A), location: 0), .init(color: Color(hex: 0xE0A85C), location: 0.55),
                            .init(color: Color(hex: 0xC98A3E), location: 1),
                        ], startPoint: .top, endPoint: .bottom))
                        .overlay(Capsule().stroke(LinearGradient(colors: [Color.rgba(255, 245, 220, 0.6), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                        .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                        .shadow(color: Color(hex: 0xC98A3E).opacity(0.28), radius: 11, y: 10)
                )
        }
        .buttonStyle(CardCatalogListPressStyle())
        .accessibilityLabel("Place a hold on the card at the front, for example The Lantern Weir")
        .offset(x: 16, y: 702)
    }
}

struct CardCatalogListPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

/// A card's tab: rounded on top, standing above the body.
struct CardCatalogListTabShape: Shape {
    func path(in rect: CGRect) -> Path {
        Path(roundedRect: rect, cornerRadii: RectangleCornerRadii(topLeading: 8, bottomLeading: 0, bottomTrailing: 0, topTrailing: 8))
    }
}

/// One index card (static; only its container moves).
struct CardCatalogListCard: View {
    let card: CardCatalogListMotion.Card
    let index: Int

    static let paper = Color(hex: 0xF3EBD8)
    static let ink = Color(hex: 0x2A2730)
    static let rules: [Double] = [86, 106, 126, 146, 166]

    static func type(_ size: Double, bold: Bool) -> Font {
        .custom(bold ? "CourierNewPS-BoldMT" : "CourierNewPSMT", fixedSize: size)
    }

    var body: some View {
        let w = CardCatalogListMotion.cardW
        ZStack(alignment: .topLeading) {
            Text(String(card.surname.prefix(2)))
                .font(Self.type(12, bold: true)).kerning(1)
                .foregroundStyle(Color(hex: 0x6B4A2A))
                .padding(.bottom, 2)
                .frame(width: 92, height: 26)
                .background(
                    CardCatalogListTabShape().fill(Color(hex: 0xEFE4CB))
                        .overlay(CardCatalogListTabShape().stroke(Color.white.opacity(0.5), lineWidth: 0.5).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.2))))
                        .shadow(color: .rgba(40, 24, 10, 0.25), radius: 1, y: -1)
                )
                .offset(x: 14 + Double(card.tab) * 106)
            ZStack(alignment: .topLeading) {
                LinearGradient(stops: [.init(color: Color(hex: 0xF7F0DF), location: 0), .init(color: Self.paper, location: 0.5),
                                       .init(color: Color(hex: 0xE8DCC0), location: 1)], startPoint: .top, endPoint: .bottom)
                Rectangle().fill(Color.rgba(196, 64, 58, 0.7)).frame(height: 1.5).offset(y: 44)
                ForEach(Self.rules, id: \.self) { y in
                    Rectangle().fill(Color.rgba(80, 120, 180, 0.28)).frame(height: 1).offset(y: y)
                }
                Text(card.call)
                    .font(Self.type(13, bold: true))
                    .lineSpacing(0)
                    .foregroundStyle(Self.ink)
                    .offset(x: 14, y: 10)
                Text("\(card.surname), \(card.given).")
                    .font(Self.type(14, bold: true))
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                    .frame(width: w - 84, alignment: .leading)
                    .offset(x: 70, y: 18)
                Text("\(card.title) / \(card.given) \(card.surname).")
                    .font(Self.type(13, bold: false))
                    .lineSpacing(20 - 14.9)
                    .foregroundStyle(Self.ink)
                    .lineLimit(2)
                    .frame(width: w - 100, alignment: .leading)
                    .offset(x: 86, y: 56)
                Text("\(card.publisher), \(card.year).")
                    .font(Self.type(12, bold: false))
                    .foregroundStyle(Self.ink.opacity(0.85))
                    .lineLimit(1)
                    .frame(width: w - 100, height: 20, alignment: .leading)
                    .offset(x: 86, y: 114)
                Text("\(card.pages) p. ; 21 cm.")
                    .font(Self.type(12, bold: false))
                    .foregroundStyle(Self.ink.opacity(0.85))
                    .lineLimit(1)
                    .frame(width: w - 100, height: 20, alignment: .leading)
                    .offset(x: 86, y: 134)
                Text("ESL " + String(format: "%05d", 4100 + index * 37))
                    .font(Self.type(11, bold: true)).kerning(1)
                    .foregroundStyle(Color.rgba(98, 62, 140, 0.78))
                    .rotationEffect(.degrees(-4))
                    .frame(width: w - 14, height: 210 - 12, alignment: .bottomTrailing)
                CardCatalogListRodHole()
                    .offset(x: w / 2 - 7, y: 210 - 12 - 14)
            }
            .frame(width: w, height: 210, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .background(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Self.paper)
                    .shadow(color: .rgba(20, 10, 4, 0.35), radius: 1, y: 1)
                    .shadow(color: .rgba(10, 5, 2, 0.28), radius: 12, y: 10)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .stroke(LinearGradient(colors: [Color.white.opacity(0.75), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.05)), lineWidth: 1)
            )
            .offset(y: 22)
        }
        .frame(width: w, height: CardCatalogListMotion.cardH, alignment: .topLeading)
    }
}

struct CardCatalogListRodHole: View {
    var body: some View {
        Circle()
            .fill(Color(hex: 0x2B1C12))
            .overlay(Circle().stroke(Color.black.opacity(0.7), lineWidth: 2).blur(radius: 1).offset(y: 1).clipShape(Circle()))
            .background(Circle().fill(Color.white.opacity(0.6)).offset(y: 1))
            .frame(width: 14, height: 14)
    }
}

/// The card's back, seen once it has tipped past upright.
struct CardCatalogListBack: View {
    let tab: Int

    var body: some View {
        let w = CardCatalogListMotion.cardW
        ZStack(alignment: .topLeading) {
            CardCatalogListTabShape().fill(Color(hex: 0xE4D7BA))
                .frame(width: 92, height: 26)
                .offset(x: 14 + Double(tab) * 106)
            ZStack(alignment: .topLeading) {
                Color(hex: 0xE6D9BC)
                ForEach(CardCatalogListCard.rules, id: \.self) { y in
                    Rectangle().fill(Color.rgba(120, 90, 60, 0.12)).frame(height: 1).offset(y: y)
                }
                CardCatalogListRodHole().offset(x: w / 2 - 7, y: 210 - 12 - 14)
            }
            .frame(width: w, height: 210, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .offset(y: 22)
        }
        .frame(width: w, height: CardCatalogListMotion.cardH, alignment: .topLeading)
    }
}

/// The shade of the cards behind the front one.
struct CardCatalogListShade: View {
    let tab: Int

    var body: some View {
        ZStack(alignment: .topLeading) {
            CardCatalogListTabShape().fill(Color(hex: 0x0C0704))
                .frame(width: 92, height: 26)
                .offset(x: 14 + Double(tab) * 106)
            RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Color(hex: 0x0C0704))
                .frame(width: CardCatalogListMotion.cardW, height: 210)
                .offset(y: 22)
        }
        .frame(width: CardCatalogListMotion.cardW, height: CardCatalogListMotion.cardH, alignment: .topLeading)
    }
}
