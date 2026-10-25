import SwiftUI
import UIKit

/*
 Crayon Rubbing Card — a museum's rubbing station: a thin sheet of paper over a bronze plaque that
 a wax crayon rubs until the hidden heron and inscription come up, collected, then peeled away to
 a fresh sheet. One Metal shader draws the hero (plaque, paper, wax, peel) from two fields: the
 relief's height and the 41 × 52 rubbed grid.

 autoPlay (loop = 9 s, CrayonRubbingCardMotion): 0–0.35 s the crayon is picked up; 0.35–3.4 s it
 scrubs a zig-zag of 14 strokes top to bottom, 3.4–4.0 s circles over the heron; 4.0–4.55 s it is
 laid down; the badge pops 4.0–4.6 s; the rubbing holds; 7.3–8.75 s the sheet peels off to a fresh
 one. The percentage rolls on digit wheels.

 Touch: drag on the paper and the crayon follows just above-left of the finger, rubbing what it
 passes; a selection haptic every 10 %, the badge and a success haptic at 70 %. Wax chips recolour
 the wax. Two seconds after the last touch the sheet peels off and the crayon goes home within
 0.9 s, and the loop restarts. Reduced motion: the finished rubbing with its badge; nothing plays.
 */
struct CrayonRubbingCard: View {
    /// Scrubs the plaque until the heron shows, collects it and peels the sheet, every loop.
    var autoPlay = false

    static let loop = 9.0
    typealias M = CrayonRubbingCardMotion
    typealias Art = CrayonRubbingCardArt

    struct Wax {
        let name: String
        let rgb: (Double, Double, Double)
        let tones: [Color]
    }

    static let waxes: [Wax] = [
        Wax(name: "INDIGO", rgb: (0.17, 0.19, 0.36), tones: [Color(hex: 0x5A6296), Color(hex: 0x2C3160), Color(hex: 0x181B3A)]),
        Wax(name: "OXBLOOD", rgb: (0.4, 0.11, 0.12), tones: [Color(hex: 0x9A4A48), Color(hex: 0x651D20), Color(hex: 0x3E0F12)]),
        Wax(name: "GRAPHITE", rgb: (0.15, 0.15, 0.16), tones: [Color(hex: 0x6E6E72), Color(hex: 0x38383C), Color(hex: 0x1D1D20)]),
    ]

    static let cream = Color(hex: 0xF1EADF)
    static let muted = Color.rgba(241, 234, 223, 0.6)
    static let gold = Color(hex: 0xE2B66E)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = CrayonRubbingCardModel()
    @State private var art: Art.Baked?
    @State private var awake = false
    @State private var wax = 0

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
                Canvas { ctx, sz in Art.drawRoom(ctx, size: sz, ox: ox, oy: oy, k: k, art: art) }
                    .frame(width: size.width, height: size.height)
                design
                    .frame(width: M.designW, height: M.designH, alignment: .topLeading)
                    .scaleEffect(k, anchor: .topLeading)
                    .offset(x: ox, y: oy)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x211D19).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task {
            let baked = await Art.shared.value
            model.prepare(baked)
            art = baked
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var paused: Bool { !((auto && art != nil) || awake) }

    private func frame(_ date: Date) -> CrayonRubbingCardModel.Pose {
        model.frame(date.timeIntervalSinceReferenceDate, auto: auto && art != nil, reduced: reduceMotion) {
            DispatchQueue.main.async { awake = false }
        }
    }

    private var design: some View {
        ZStack(alignment: .topLeading) {
            CrayonRubbingCardHeader()
            TimelineView(.animation(paused: paused)) { timeline in
                hero(frame(timeline.date))
            }
            .frame(width: M.canvas.width, height: M.canvas.height, alignment: .topLeading)
            .allowsHitTesting(false)
            .offset(x: M.canvas.minX, y: M.canvas.minY)
            touchArea
            panel
            CrayonRubbingCardSave()
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
    }

    // MARK: Hero

    private func hero(_ p: CrayonRubbingCardModel.Pose) -> some View {
        let w = Self.waxes[wax]
        let relief = art?.relief
        let field = p.field
        return ZStack(alignment: .topLeading) {
            Canvas { ctx, sz in
                ctx.fill(Path(CGRect(origin: .zero, size: sz)), with: .color(.black))
                if let relief {
                    var r = ctx
                    r.addFilter(.colorMultiply(Color(.sRGB, red: 1, green: 0, blue: 0)))
                    r.draw(Image(decorative: relief, scale: Art.reliefBake), in: M.paper)
                }
                if let field {
                    var g = ctx
                    g.blendMode = .plusLighter
                    g.addFilter(.colorMultiply(Color(.sRGB, red: 0, green: 1, blue: 0)))
                    g.draw(Image(decorative: field, scale: 1).interpolation(.medium),
                           in: CGRect(x: M.paper.minX, y: M.paper.minY, width: Double(M.gx) * M.cell, height: Double(M.gy) * M.cell))
                }
            }
            .frame(width: M.canvas.width, height: M.canvas.height)
            .layerEffect(ShaderLibrary.crayonRubbingCardRub(
                .float4(M.plaque.minX, M.plaque.minY, M.plaque.width, M.plaque.height),
                .float4(M.paper.minX, M.paper.minY, M.paper.width, M.paper.height),
                .float(p.peel),
                .float3(w.rgb.0, w.rgb.1, w.rgb.2),
                .float(p.grain)
            ), maxSampleOffset: CGSize(width: 2, height: 2))
            .opacity(art == nil ? 0 : 1)

            Canvas { ctx, _ in
                Art.drawCrayon(ctx, x: p.cx, y: p.cy, angle: p.angle, contact: p.contact, tones: w.tones, name: w.name)
            }
            .frame(width: M.canvas.width, height: M.canvas.height)

            CrayonRubbingCardBadge()
                .opacity(min(1, p.badge * 1.4))
                .scaleEffect(0.86 + 0.14 * p.badge)
                .rotationEffect(.degrees(-4 * p.badge))
                .frame(width: M.canvas.width - 14, alignment: .trailing)
        }
        .frame(width: M.canvas.width, height: M.canvas.height, alignment: .topLeading)
    }

    private var touchArea: some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: M.canvas.width, height: M.canvas.height)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        awake = true
                        model.drag(v.location, at: Date.now.timeIntervalSinceReferenceDate, reduced: reduceMotion)
                    }
                    .onEnded { _ in model.end(at: Date.now.timeIntervalSinceReferenceDate) }
            )
            .offset(x: M.canvas.minX, y: M.canvas.minY)
            .accessibilityElement()
            .accessibilityLabel("A sheet of paper over a bronze plaque. Rub it with the crayon to reveal the relief.")
    }

    // MARK: Panel

    private var panel: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("REVEALED")
                    .font(.system(size: 10, weight: .bold)).kerning(1.4)
                    .foregroundStyle(Self.muted)
                TimelineView(.animation(paused: paused)) { timeline in
                    CrayonRubbingCardPercent(value: frame(timeline.date).shown)
                }
                .frame(width: 150, height: 34 + 2 + 6, alignment: .topLeading)
            }
            .frame(width: 150, alignment: .leading)
            Spacer(minLength: 0)
            VStack(alignment: .trailing, spacing: 8) {
                Text("WAX")
                    .font(.system(size: 10, weight: .bold)).kerning(1.4)
                    .foregroundStyle(Self.muted)
                HStack(spacing: 10) {
                    ForEach(0..<Self.waxes.count, id: \.self) { i in
                        Button {
                            wax = i
                            UISelectionFeedbackGenerator().selectionChanged()
                        } label: {
                            Circle()
                                .fill(LinearGradient(colors: Self.waxes[i].tones, startPoint: .top, endPoint: .bottom))
                                .padding(3)
                                .frame(width: 30, height: 30)
                                .overlay(Circle().strokeBorder(wax == i ? Self.gold : .clear, lineWidth: 1.5))
                                .contentShape(Circle().inset(by: -8))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(Self.waxes[i].name.lowercased()) wax")
                        .accessibilityAddTraits(wax == i ? .isSelected : [])
                    }
                }
            }
        }
        .padding(.horizontal, 18)
        .frame(width: 361, height: 72)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.rgba(241, 234, 223, 0.06))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Color.rgba(241, 234, 223, 0.16), lineWidth: 0.5))
                .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.3), radius: 14, y: 12)
        )
        .offset(x: 16, y: 612)
    }
}

// MARK: - Model

final class CrayonRubbingCardModel {
    typealias M = CrayonRubbingCardMotion
    typealias Art = CrayonRubbingCardArt

    struct Pose {
        var field: CGImage?
        var cx: Double
        var cy: Double
        var angle: Double
        var contact: Double
        var peel: Double
        var badge: Double
        var grain: Double
        var shown: Double
    }

    /// Reduced motion shows the finished rubbing: the demo's field just after the badge lands.
    static let stillT = 5.0

    private var weights: [Double] = []
    private var total = 0.0
    private var shownTable: [Double] = []

    private var autoStart: Double?
    private var lastFrame: Double?
    private var lastNow = -1.0
    private(set) var pose = Pose(field: nil, cx: M.crayonRest.x, cy: M.crayonRest.y, angle: M.crayonRest.angle,
                                 contact: 0, peel: 0, badge: 0, grain: -0.6, shown: 0)

    private var owned = false
    private var touching = false
    private var returning = false
    private var ignoring = false
    private var releasedAt = 0.0
    private var returnEnd = 0.0
    private var last = CGPoint.zero
    private var grain = -0.6
    private var user = [Double](repeating: 0, count: M.gx * M.gy)
    private var grid = [Double](repeating: 0, count: M.gx * M.gy)
    private var fieldSum = -1.0
    private var field: CGImage?
    private var reveal = 0.0
    private var rounded = 0.0
    private var collected = false

    private var cx = M.Track(M.crayonRest.x), cy = M.Track(M.crayonRest.y), angle = M.Track(M.crayonRest.angle)
    private var contact = M.Track(0), peel = M.Track(0), badge = M.Track(0), shown = M.Track(0)

    private let tick = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    func prepare(_ art: Art.Baked) {
        weights = art.weights
        total = art.total
        shownTable = art.shown
    }

    private func tableShown(_ t: Double) -> Double {
        guard !shownTable.isEmpty else { return 0 }
        let n = shownTable.count
        let x = t * 60
        let i = Int(floor(x)) % n
        let f = x - floor(x)
        return shownTable[i] + (shownTable[(i + 1) % n] - shownTable[i]) * f
    }

    private func updateField(_ g: [Double]) {
        var sum = 0.0
        for v in g { sum += v }
        if sum != fieldSum || field == nil {
            fieldSum = sum
            field = Art.fieldImage(g)
            reveal = M.revealed(g, weights, total)
        }
    }

    func frame(_ now: Double, auto: Bool, reduced: Bool, sleep: () -> Void) -> Pose {
        if now == lastNow { return pose }
        lastNow = now
        defer { lastFrame = now }
        if owned {
            if !touching && !returning && now - releasedAt >= 2 { startReturn(now) }
            if returning && now >= returnEnd {
                for i in 0..<user.count { user[i] = 0 }
                peel.set(0)
                returning = false
                owned = false
                autoStart = now
                sleep()
            }
        }
        if owned {
            updateField(user)
            let r = min(99, (reveal * 100).rounded())
            if r != rounded {
                if Int(r) / 10 > Int(rounded) / 10 { tick.selectionChanged() }
                rounded = r
                shown.spring(r, damping: 20, stiffness: 180, at: now)
            }
            let collect = !returning && reveal > 0.7
            if collect && !collected {
                badge.spring(1, damping: 9, stiffness: 180, at: now)
                success.notificationOccurred(.success)
            }
            collected = collect
            pose = Pose(field: field, cx: cx.value(now), cy: cy.value(now), angle: angle.value(now), contact: contact.value(now),
                        peel: peel.value(now), badge: badge.value(now), grain: grain, shown: shown.value(now))
            return pose
        }
        var t = 0.0
        if reduced {
            t = Self.stillT
        } else if auto {
            if autoStart == nil { autoStart = now }
            if let last = lastFrame, now - last > 0.25, let s = autoStart { autoStart = s + (now - last) - 1 / 60 }
            t = max(0, (now - autoStart!).truncatingRemainder(dividingBy: M.loop))
        } else {
            autoStart = nil
        }
        M.autoGrid(t, into: &grid)
        updateField(grid)
        let c = M.crayonAt(t)
        grain = M.autoGrain(t)
        pose = Pose(field: field, cx: c.x, cy: c.y, angle: c.angle, contact: c.contact, peel: M.autoPeel(t),
                    badge: M.autoBadge(t), grain: grain, shown: tableShown(t))
        return pose
    }

    /// A finger lands on the paper: the demo stops and the finger carries on from what is on screen.
    private func takeOver(_ now: Double) {
        touching = true
        if owned && !returning { return }
        let p = pose
        cx.set(p.cx); cy.set(p.cy); angle.set(p.angle); contact.set(p.contact); badge.set(p.badge); shown.set(p.shown)
        // Mid-peel, the finger starts on the fresh sheet; otherwise it keeps rubbing the one on screen.
        let fresh = p.peel > 0.001
        let shownGrid = owned ? user : grid
        for i in 0..<user.count { user[i] = fresh ? 0 : shownGrid[i] }
        rounded = (p.shown).rounded()
        collected = !fresh && reveal > 0.7
        peel.set(0)
        returning = false
        owned = true
    }

    func drag(_ at: CGPoint, at now: Double, reduced: Bool) {
        // The crayon's working edge sits just above and left of the finger, so the finger never hides it.
        let x = at.x - 12, y = at.y - 16
        if !touching && !ignoring {
            if returning { ignoring = true; return }
            takeOver(now)
            cx.spring(x, damping: 20, stiffness: 400, at: now)
            cy.spring(y, damping: 20, stiffness: 400, at: now)
            angle.timing(-34, 0.16, at: now)
            contact.timing(1, 0.12, at: now)
            last = CGPoint(x: x, y: y)
            return
        }
        guard touching else { return }
        cx.set(x)
        cy.set(y)
        let dx = x - last.x, dy = y - last.y
        if dx * dx + dy * dy > 1 {
            grain = atan2(dy, dx)
            angle.set(-34 + max(-8, min(8, dx * 0.4)))
        }
        M.rubSegment(&user, last.x - M.paper.minX, last.y - M.paper.minY, x - M.paper.minX, y - M.paper.minY)
        last = CGPoint(x: x, y: y)
    }

    func end(at now: Double) {
        if ignoring { ignoring = false; return }
        touching = false
        releasedAt = now
        contact.timing(0, 0.18, at: now)
    }

    /// Back to rest within 0.9 s: the sheet peels away to a fresh one, the crayon goes home.
    private func startReturn(_ now: Double) {
        returning = true
        let glide = M.Curve.bezier(0.65, 0, 0.35, 1)
        cx.timing(M.crayonRest.x, 0.8, glide, at: now)
        cy.timing(M.crayonRest.y, 0.8, glide, at: now)
        angle.timing(M.crayonRest.angle, 0.8, glide, at: now)
        badge.timing(0, 0.3, at: now)
        peel.timing(1, 0.85, glide, at: now)
        returnEnd = now + 0.85
    }
}

// MARK: - Static pieces

struct CrayonRubbingCardHeader: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("RUBBINGS")
                .font(.system(size: 12, weight: .bold)).kerning(2.4)
                .foregroundStyle(CrayonRubbingCard.gold)
            Text("Hall C, plaque 14")
                .font(.system(size: 32, weight: .heavy)).kerning(-0.6)
                .foregroundStyle(CrayonRubbingCard.cream)
            Text("Cast bronze, 1911 · Riverside Gate")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(CrayonRubbingCard.muted)
        }
        .frame(width: 345, alignment: .leading)
        .offset(x: 24, y: 8)
        .allowsHitTesting(false)
    }
}

struct CrayonRubbingCardBadge: View {
    var body: some View {
        HStack(spacing: 8) {
            Path { p in
                p.move(to: CGPoint(x: 3.5, y: 8.4))
                p.addLine(to: CGPoint(x: 6.6, y: 11.4))
                p.addLine(to: CGPoint(x: 12.6, y: 4.8))
            }
            .stroke(Color(hex: 0x2A1A08), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            .frame(width: 16, height: 16)
            VStack(alignment: .leading, spacing: 0) {
                Text("Collected")
                    .font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(Color(hex: 0x2A1A08))
                Text("No. 14 of 40")
                    .font(.system(size: 10, weight: .bold)).kerning(0.4)
                    .foregroundStyle(Color.rgba(42, 26, 8, 0.7))
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, 16)
        .frame(height: 44)
        .background(
            Capsule().fill(CrayonRubbingCard.gold)
                .overlay(Capsule().stroke(LinearGradient(colors: [Color.rgba(255, 246, 220, 0.6), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.35), radius: 12, y: 10)
        )
    }
}

/// The percentage on two digit wheels and its bar.
struct CrayonRubbingCardPercent: View {
    var value: Double

    private static let digitH = 34.0

    private func wheel(_ at: Double) -> some View {
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 28, weight: .heavy).monospacedDigit())
                    .foregroundStyle(CrayonRubbingCard.cream)
                    .frame(width: 17, height: Self.digitH)
            }
        }
        .offset(y: -at * Self.digitH)
        .frame(width: 17, height: Self.digitH, alignment: .top)
        .clipped()
    }

    var body: some View {
        let v = max(0, value)
        let ones = v.truncatingRemainder(dividingBy: 10)
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .bottom, spacing: 0) {
                wheel(floor(v / 10) + max(0, ones - 9))
                wheel(ones)
                Text("%")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(CrayonRubbingCard.muted)
                    .padding(.leading, 2)
                    .padding(.bottom, 5)
            }
            .frame(height: Self.digitH)
            Capsule()
                .fill(Color.rgba(241, 234, 223, 0.12))
                .overlay(alignment: .leading) {
                    Rectangle().fill(CrayonRubbingCard.gold)
                        .scaleEffect(x: max(0.001, v / 100), y: 1, anchor: .leading)
                }
                .clipShape(Capsule())
                .frame(width: 150, height: 4)
                .padding(.top, 2)
        }
    }
}

struct CrayonRubbingCardSave: View {
    var body: some View {
        Button {} label: {
            Text("Save to my rubbings")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Color(hex: 0x2A1A08))
                .frame(width: 361, height: 52)
                .background(
                    Capsule()
                        .fill(LinearGradient(stops: [
                            .init(color: Color(hex: 0xF2CF8E), location: 0), .init(color: CrayonRubbingCard.gold, location: 0.55),
                            .init(color: Color(hex: 0xC9913F), location: 1),
                        ], startPoint: .top, endPoint: .bottom))
                        .overlay(Capsule().stroke(LinearGradient(colors: [Color.rgba(255, 246, 220, 0.6), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                        .shadow(color: .black.opacity(0.45), radius: 1, y: 1)
                        .shadow(color: Color(hex: 0xC9913F).opacity(0.28), radius: 12, y: 10)
                )
        }
        .buttonStyle(CrayonRubbingCardPressStyle())
        .offset(x: 16, y: 698)
    }
}

struct CrayonRubbingCardPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: configuration.isPressed)
    }
}
