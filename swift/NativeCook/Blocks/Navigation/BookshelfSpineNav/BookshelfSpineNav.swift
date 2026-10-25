import SwiftUI
import UIKit

/*
 Bookshelf Spine Nav — a reading app's library whose section menu is a shelf of book spines (cloth,
 leather and linen bindings with foil titles). Pull or tap a spine: the book tips out about its
 bottom, slides up toward the middle, turns cover-out, opens its cover and becomes the section
 page, while its neighbours lean into the gap.

 autoPlay (loop = 9 s, BookshelfSpineNavMotion.autoTau): rest 0–1.2 s; "Essays" is pulled and
 opened 1.2–3.8 s (tip 0–0.5, slide 0.4–1.6, turn 1.0–1.6, cover 1.6–2.25, page 1.9–2.6 of open
 time); hold 3.8–5.4 s; it closes and slides back 5.4–7.2 s; the neighbours settle 7.2–8.6 s.

 Touch: drag a spine up to pull it out (released past 0.7 s of open time, or flicked, it opens),
 or tap it; tap the page to put the book back. Two seconds after the last touch the book goes back
 within 0.9 s and the loop restarts. Reduced motion: the home page and the shelf at rest.
 */
struct BookshelfSpineNav: View {
    /// Pulls "Essays" off the shelf, opens it into its page, holds, and puts it back, every loop.
    var autoPlay = false

    static let loop = 9.0
    typealias M = BookshelfSpineNavMotion
    typealias Art = BookshelfSpineNavArt

    static let perspective = 1100.0
    /// The shelf's touch area starts here (design points), below the page.
    static let shelfHitY = 440.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = BookshelfSpineNavModel()
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
        .background(Color(hex: 0x17120F).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task { art = await Art.shared.value }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    private var design: some View {
        let ready = art != nil
        return ZStack(alignment: .topLeading) {
            BookshelfSpineNavHeader()
            TimelineView(.animation(paused: !((auto && ready) || awake))) { timeline in
                let p = model.frame(timeline.date.timeIntervalSinceReferenceDate, auto: auto && ready, reduced: reduceMotion) {
                    DispatchQueue.main.async { awake = false }
                }
                scene(p)
            }
            .allowsHitTesting(false)
            // The page: a tap puts the book back.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: M.page.width, height: M.page.height)
                .onTapGesture {
                    awake = true
                    model.tapPage(at: Date.now.timeIntervalSinceReferenceDate)
                }
                .offset(x: M.page.minX, y: M.page.minY)
            Color.clear
                .contentShape(Rectangle())
                .frame(width: M.designW, height: M.boardY + 30 - Self.shelfHitY)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            model.drag(v.startLocation, v.translation, at: Date.now.timeIntervalSinceReferenceDate)
                        }
                        .onEnded { v in
                            model.end(v.startLocation, v.translation, velocity: v.velocity.height, at: Date.now.timeIntervalSinceReferenceDate)
                        }
                )
                .offset(y: Self.shelfHitY)
                .accessibilityElement()
                .accessibilityLabel("Bookshelf. Tap a spine to open its section, or pull it up.")
                .accessibilityAddTraits(.allowsDirectInteraction)
            Text("Tap or pull a spine to open its section")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.rgba(244, 234, 219, 0.5))
                .frame(width: 345)
                .offset(x: 24, y: 740)
                .allowsHitTesting(false)
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
    }

    private func scene(_ p: BookshelfSpineNavModel.Pose) -> some View {
        let a = p.active
        let pose = M.bookPose(p.tau, a)
        let r = M.coverRect(a)
        let g = pose.grow
        let sx = r.width / M.page.width + (1 - r.width / M.page.width) * g
        let sy = r.height / M.page.height + (1 - r.height / M.page.height) * g
        let away = M.seg(p.tau, 0.6, 1.4)
        return ZStack(alignment: .topLeading) {
            BookshelfSpineNavHome()
                .scaleEffect(1 - 0.03 * away)
                .opacity(1 - 0.55 * away)
                .offset(x: M.page.minX, y: M.page.minY)
            if p.tau > 1.62 {
                BookshelfSpineNavSection(book: M.books[a], text: pose.text)
                    .scaleEffect(x: sx, y: sy, anchor: .topLeading)
                    .offset(x: M.page.minX + (r.minX - M.page.minX) * (1 - g), y: M.page.minY + (r.minY - M.page.minY) * (1 - g))
            }
            ForEach(0..<M.books.count, id: \.self) { i in
                book(i, p, pose)
                    .zIndex(i == a ? 5 : 1)
            }
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
    }

    @ViewBuilder
    private func book(_ i: Int, _ p: BookshelfSpineNavModel.Pose, _ pose: M.BookPose) -> some View {
        let b = M.books[i]
        let cw = M.coverW[i]
        let anchor = UnitPoint(x: (b.w / 2) / (b.w + cw), y: 1)
        let persp = b.h / Self.perspective
        let on = i == p.active
        let turn = on ? pose.turn : 0
        let opened = on ? pose.open : 0
        let faces = ZStack(alignment: .topLeading) {
            BookshelfSpineNavSpine(index: i)
                .rotation3DEffect(.degrees(-turn), axis: (x: 0, y: 1, z: 0), anchor: .trailing, perspective: persp)
            if turn > 0.5 {
                ZStack(alignment: .topLeading) {
                    BookshelfSpineNavCover(index: i)
                    BookshelfSpineNavEndpaper(index: i)
                        .opacity(opened > 0.57 ? 1 : 0)
                }
                .rotation3DEffect(.degrees(90 - turn - opened * 158), axis: (x: 0, y: 1, z: 0), anchor: .leading, perspective: persp)
                .offset(x: b.w)
            }
        }
        .frame(width: b.w + cw, height: b.h, alignment: .topLeading)
        if on {
            faces
                .rotation3DEffect(.degrees(pose.tilt), axis: (x: 1, y: 0, z: 0), anchor: anchor, perspective: persp)
                .scaleEffect(pose.scale, anchor: anchor)
                .offset(x: pose.dx, y: pose.dy)
                .opacity(pose.alpha)
                .offset(x: M.bookX[i], y: M.boardY - b.h)
        } else {
            let lean = M.neighbourLean(i, p.active, pose.gap, p.wobble)
            let rad = lean * Double.pi / 180
            faces
                .rotationEffect(.degrees(lean), anchor: anchor)
                .offset(x: (lean > 0 ? 1 : lean < 0 ? -1 : 0) * b.w * (1 - cos(rad)) / 2, y: -b.w / 2 * abs(sin(rad)))
                .offset(x: M.bookX[i], y: M.boardY - b.h)
        }
    }
}

// MARK: - Model

final class BookshelfSpineNavModel {
    typealias M = BookshelfSpineNavMotion

    struct Pose {
        var tau: Double
        var wobble: Double
        var active: Int
    }

    private static let pull = 150.0

    private var autoStart: Double?
    private var lastFrame: Double?
    private(set) var pose = Pose(tau: 0, wobble: 0, active: M.demoBook)

    private var owned = false
    private var touching = false
    private var returning = false
    private var releasedAt = 0.0
    private var returnEnd = 0.0
    private var start = 0.0
    private var panning = false
    private var active = M.demoBook
    private var held = M.Track(0)
    private var wasOut = false

    private let thud = UIImpactFeedbackGenerator(style: .light)

    func frame(_ now: Double, auto: Bool, reduced: Bool, sleep: () -> Void) -> Pose {
        defer { lastFrame = now }
        if owned {
            if !touching && !returning && now - releasedAt >= 2 {
                // Put the book back quickly (at most 0.9 s), then let the demo start over.
                returning = true
                let from = held.value(now)
                let d = min(0.9, max(0.001, from * 0.36))
                held.timing(0, d, .bezier(0.65, 0, 0.35, 1), at: now)
                returnEnd = now + d
            }
            if returning && now >= returnEnd {
                returning = false
                owned = false
                autoStart = now
                active = M.demoBook
                sleep()
            }
        }
        if owned {
            let tau = held.value(now)
            let out = tau > 0.45
            if out != wasOut { thud.impactOccurred() }
            wasOut = out
            pose = Pose(tau: tau, wobble: 0, active: active)
            return pose
        }
        guard auto, !reduced else {
            autoStart = nil
            pose = Pose(tau: 0, wobble: 0, active: M.demoBook)
            return pose
        }
        if autoStart == nil { autoStart = now }
        if let last = lastFrame, now - last > 0.25, let s = autoStart { autoStart = s + (now - last) - 1 / 60 }
        let t = max(0, (now - autoStart!).truncatingRemainder(dividingBy: M.loop))
        pose = Pose(tau: M.autoTau(t), wobble: M.autoWobble(t), active: M.demoBook)
        return pose
    }

    /// A finger lands: the demo stops and the shelf holds still where it is.
    private func takeOver(_ now: Double) {
        touching = true
        if !owned || returning {
            held.set(pose.tau)
            active = pose.active
            wasOut = pose.tau > 0.45
        }
        returning = false
        owned = true
    }

    private func letGo(_ now: Double) {
        touching = false
        releasedAt = now
    }

    /// Runs the open time to `to` at a pace of `pace` seconds of τ per second.
    private func playTo(_ to: Double, pace: Double, at now: Double) {
        let from = held.value(now)
        held.timing(to, max(0.001, abs(to - from) / pace), .linear, at: now)
    }

    /// Which spine sits under x (design points), or -1.
    private func bookAt(_ x: Double) -> Int {
        for i in 0..<M.books.count where x >= M.bookX[i] - 1 && x <= M.bookX[i] + M.books[i].w + 1 { return i }
        return -1
    }

    func drag(_ startAt: CGPoint, _ translation: CGSize, at now: Double) {
        if !touching {
            takeOver(now)
            panning = false
            let current = held.value(now)
            held.set(current)
            if current <= 0.01 {
                let i = bookAt(startAt.x)
                if i >= 0 { active = i }
            }
            start = current
        }
        if !panning && abs(translation.height) > 8 { panning = true }
        guard panning, start <= 0.01 else { return }
        // Up to τ 1.6 the drag tips the book and slides it out; past that the release decides.
        let pulled = max(0, -translation.height / Self.pull)
        held.set(min(1.6, pulled * 1.6) + max(0, pulled - 1) * 0.1)
    }

    func end(_ startAt: CGPoint, _ translation: CGSize, velocity: Double, at now: Double) {
        guard touching else { return }
        if panning {
            let out = held.value(now) > 0.7 || velocity < -600
            playTo(out ? M.open : 0, pace: out ? 1.2 : 2, at: now)
        } else if hypot(translation.width, translation.height) < 10 {
            if held.value(now) > 0.01 {
                playTo(0, pace: 1.6, at: now)
            } else {
                let i = bookAt(startAt.x + translation.width)
                if i >= 0 {
                    active = i
                    playTo(M.open, pace: 1, at: now)
                }
            }
        }
        panning = false
        letGo(now)
    }

    func tapPage(at now: Double) {
        guard pose.tau > 2.2 else { return }
        takeOver(now)
        playTo(0, pace: 1.6, at: now)
        letGo(now)
    }
}

// MARK: - Pieces

struct BookshelfSpineNavHeader: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("SHELF")
                .font(.system(size: 12, weight: .bold)).kerning(2.4)
                .foregroundStyle(Color(hex: 0xE0B36C))
            Text("Your library")
                .font(.system(size: 32, weight: .heavy)).kerning(-0.6)
                .foregroundStyle(BookshelfSpineNavStyle.cream)
            Text("9 sections · 245 books")
                .font(.system(size: 14, weight: .medium).monospacedDigit())
                .foregroundStyle(BookshelfSpineNavStyle.muted)
        }
        .frame(width: 345, alignment: .leading)
        .offset(x: 24, y: 8)
        .allowsHitTesting(false)
    }
}

/// The spine: rounded binding, caps, bands or a label, and the foil title running top to bottom.
struct BookshelfSpineNavSpine: View {
    let index: Int

    var body: some View {
        let b = BookshelfSpineNavMotion.books[index]
        let shade = BookshelfSpineNavMotion.shade
        let w = b.w, h = b.h
        let foil = Color(hex: b.foil)
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [
                .init(color: shade(b.color, -0.5), location: 0), .init(color: shade(b.color, 0.14), location: 0.32),
                .init(color: Color(hex: b.color), location: 0.62), .init(color: shade(b.color, -0.42), location: 1),
            ], startPoint: .leading, endPoint: .trailing)
            Rectangle().fill(shade(b.color, -0.35)).frame(height: 5)
            Rectangle().fill(shade(b.color, -0.45)).frame(height: 5).offset(y: h - 5)
            if b.binding == .leather {
                ForEach([20, 30, h - 36, h - 26], id: \.self) { y in
                    LinearGradient(colors: [shade(b.color, 0.35), Color(hex: b.color), shade(b.color, -0.55)], startPoint: .top, endPoint: .bottom)
                        .frame(width: w, height: 5)
                        .offset(y: y)
                }
            }
            if b.binding != .linen {
                ForEach([14, h - 16], id: \.self) { y in
                    Rectangle().fill(foil.opacity(0.85)).frame(width: w - 8, height: 1.5).offset(x: 4, y: y)
                }
            } else {
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.rgba(244, 236, 214, 0.92))
                    .overlay(RoundedRectangle(cornerRadius: 2).strokeBorder(Color.rgba(60, 40, 20, 0.35), lineWidth: 1))
                    .frame(width: w - 10, height: h - 62)
                    .offset(x: 5, y: h / 2 - (h - 70) / 2 - 4)
            }
            Text(b.section.uppercased())
                .font(BookshelfSpineNavStyle.serif(12)).kerning(2.4)
                .foregroundStyle(foil)
                .shadow(color: .black.opacity(0.55), radius: 0.25, y: 1)
                .lineLimit(1)
                .frame(width: h - 76, height: 20)
                .rotationEffect(.degrees(90))
                .offset(x: w / 2 - (h - 76) / 2, y: h / 2 - 10)
            Rectangle().fill(Color.white.opacity(0.08)).frame(width: 3).offset(x: w * 0.28)
            // Edge light on the left, shade on the right.
            Rectangle().fill(Color.white.opacity(0.14)).frame(width: 1)
            Rectangle().fill(Color.black.opacity(0.4)).frame(width: 1).offset(x: w - 1)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 3))
        .background(
            RoundedRectangle(cornerRadius: 3).fill(Color(hex: b.color))
                .shadow(color: .black.opacity(0.55), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.3), radius: 6, x: 4)
        )
    }
}

/// The front cover: the same binding, a foil frame and the section's name.
struct BookshelfSpineNavCover: View {
    let index: Int

    var body: some View {
        let b = BookshelfSpineNavMotion.books[index]
        let shade = BookshelfSpineNavMotion.shade
        let w = BookshelfSpineNavMotion.coverW[index], h = b.h
        let foil = Color(hex: b.foil)
        let shape = UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(topLeading: 0, bottomLeading: 0, bottomTrailing: 4, topTrailing: 4))
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [
                .init(color: shade(b.color, 0.16), location: 0), .init(color: Color(hex: b.color), location: 0.5),
                .init(color: shade(b.color, -0.38), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing)
            Rectangle().fill(shade(b.color, -0.4).opacity(0.8)).frame(width: 6)
            RoundedRectangle(cornerRadius: 2).strokeBorder(foil.opacity(0.85), lineWidth: 1.2)
                .frame(width: w - 20, height: h - 20).offset(x: 12, y: 10)
            RoundedRectangle(cornerRadius: 1).strokeBorder(foil.opacity(0.6), lineWidth: 0.6)
                .frame(width: w - 28, height: h - 28).offset(x: 16, y: 14)
            Text(b.section.uppercased())
                .font(BookshelfSpineNavStyle.serif(13)).kerning(2.2)
                .foregroundStyle(foil)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(width: w - 24)
                .offset(x: 14, y: h * 0.38)
            Rectangle().fill(foil.opacity(0.8)).frame(width: w * 0.16, height: 1.5).offset(x: w * 0.42, y: h * 0.52)
            Text("SHELF EDITIONS")
                .font(.system(size: 6, weight: .bold)).kerning(1.4)
                .foregroundStyle(foil.opacity(0.75))
                .frame(width: w - 24, height: 8)
                .offset(x: 14, y: h - 22 - 8)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipShape(shape)
        .background(
            shape.fill(Color(hex: b.color))
                .shadow(color: .black.opacity(0.45), radius: 2, y: 2)
                .shadow(color: .black.opacity(0.35), radius: 20, y: 18)
        )
    }
}

/// The cover's inside, seen once it swings open past its edge: a marbled endpaper.
struct BookshelfSpineNavEndpaper: View {
    let index: Int

    var body: some View {
        let b = BookshelfSpineNavMotion.books[index]
        let shade = BookshelfSpineNavMotion.shade
        LinearGradient(stops: [
            .init(color: shade(b.color, 0.5), location: 0), .init(color: shade(b.color, 0.25), location: 0.5),
            .init(color: shade(b.color, 0.42), location: 1),
        ], startPoint: .top, endPoint: .bottom)
        .frame(width: BookshelfSpineNavMotion.coverW[index], height: b.h)
        .clipShape(UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(topLeading: 4, bottomLeading: 4, bottomTrailing: 0, topTrailing: 0)))
    }
}
