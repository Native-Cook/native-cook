import SwiftUI
import UIKit

/*
 Postcard Stamp Cancel — a travel journal's day page: a cream postcard on an indigo linen desk and
 a wooden duplex handstamp on its ultramarine ink pad. Set the handstamp down on the stamp and the
 date ring and five wavy bars print with real hand-stamp unevenness (one Metal shader reading the
 rubber face as a mask); the card jolts and the trip counter rolls from 12 to 13. Tap the card to
 turn it over to its photo.

 autoPlay (loop = 9 s, PostcardStampCancelMotion.autoPose): rest 0–1.4 s; lift 1.4–1.75 s; carry
 along a 46 pt arc 1.6–2.4 s; anticipation, drop and press 2.4–3.2 s (ink 2.72–3.15 s, the card
 kicks −1.6°); lift 3.2–3.55 s; the counter rolls 3.6–3.95 s; home 3.6–4.5 s, set down 4.5–4.85 s
 with a re-inking squash; the card turns to its photo 5.8–6.5 s and on to a fresh card 6.9–7.6 s.

 Touch: grab the handstamp and drag; let go within 64 pt of the stamp and it springs over it and
 presses; elsewhere it sets down. A tap on the card turns it over. Two seconds after the last touch
 everything returns within 0.9 s and the loop restarts. Reduced motion: the card shown cancelled,
 the stamp on its pad, nothing plays.
 */
struct PostcardStampCancel: View {
    /// Lifts the postmark off its pad, cancels the stamp, and turns the card, every loop.
    var autoPlay = false

    static let loop = 9.0
    typealias M = PostcardStampCancelMotion
    typealias Art = PostcardStampCancelArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = PostcardStampCancelModel()
    @State private var art: Art.Baked?
    @State private var awake = false

    static let cream = Color(hex: 0xF3EBDA)
    static let muted = Color.rgba(243, 235, 218, 0.62)
    static let amber = Color(hex: 0xEDB262)

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
                Canvas { ctx, sz in Art.drawDesk(ctx, size: sz, ox: ox, oy: oy, k: k, art: art) }
                    .frame(width: size.width, height: size.height)
                design
                    .frame(width: M.designW, height: M.designH, alignment: .topLeading)
                    .scaleEffect(k, anchor: .topLeading)
                    .offset(x: ox, y: oy)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x14213A).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task { art = await Art.shared.value }
    }

    private var auto: Bool { autoPlay && !reduceMotion }

    private var design: some View {
        let ready = art != nil
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !((auto && ready) || awake))) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let p = model.frame(now, auto: auto && ready, reduced: reduceMotion) {
                    DispatchQueue.main.async { awake = false }
                }
                ZStack(alignment: .topLeading) {
                    PostcardStampCancelHeader(count: p.count)
                    cardShadow(p)
                    card(p)
                    PostcardStampCancelInfo()
                    tool(p)
                }
                .frame(width: M.designW, height: M.designH, alignment: .topLeading)
            }
            .allowsHitTesting(false)
            touchArea
            PostcardStampCancelActions()
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
        .coordinateSpace(name: "PostcardStampCancelDesign")
    }

    // MARK: The card

    private func lifted(_ p: M.Pose) -> Double {
        sin(Double.pi * min(180, p.flip.truncatingRemainder(dividingBy: 180.001)) / 180)
    }

    private func cardShadow(_ p: M.Pose) -> some View {
        let l = lifted(p)
        return RoundedRectangle(cornerRadius: 10)
            .fill(Color.rgba(4, 8, 18, 0.35))
            .shadow(color: .rgba(3, 6, 14, 0.55), radius: 15, y: 14)
            .shadow(color: .rgba(3, 6, 14, 0.45), radius: 2, y: 2)
            .frame(width: M.card.width - 12, height: M.card.height - 8)
            .scaleEffect(x: max(0.2, abs(cos(p.flip * Double.pi / 180))), y: 1)
            .offset(y: 10 * l)
            .opacity(1 - 0.4 * l)
            .offset(x: M.card.minX + 6, y: M.card.minY + 10)
    }

    private func card(_ p: M.Pose) -> some View {
        let l = lifted(p)
        let front = cos(p.flip * Double.pi / 180) >= 0
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                PostcardStampCancelWritingSide(photo: art?.photo)
                Canvas { ctx, _ in Art.drawMask(ctx) }
                    .frame(width: M.inkBox.width, height: M.inkBox.height)
                    .layerEffect(ShaderLibrary.postcardStampCancelInk(
                        .float2(M.inkBox.width, M.inkBox.height),
                        .float(p.ink), .float(p.fade), .float2(0.8, -0.5), .float(3.7)
                    ), maxSampleOffset: CGSize(width: 2, height: 2))
                    .offset(x: M.inkBox.minX, y: M.inkBox.minY)
            }
            .opacity(front ? 1 : 0)
            PostcardStampCancelPhotoSide(photo: art?.photo)
                .scaleEffect(x: -1, y: 1)
                .opacity(front ? 0 : 1)
        }
        .frame(width: M.card.width, height: M.card.height, alignment: .topLeading)
        .scaleEffect(1 + 0.04 * l)
        .rotation3DEffect(.degrees(p.flip), axis: (x: 0, y: 1, z: 0), perspective: M.card.width / 1200)
        .rotationEffect(.degrees(p.tilt))
        .offset(y: -10 * l)
        .offset(x: M.card.minX, y: M.card.minY)
        .allowsHitTesting(false)
    }

    // MARK: The handstamp

    private func tool(_ p: M.Pose) -> some View {
        let lift = p.h
        let scale = (1 + 0.1 * lift) * (1 - 0.025 * p.squash)
        let tw = M.tool.w, th = M.tool.h
        let full = CGSize(width: (tw + Art.toolPad * 2) * scale, height: (th + Art.toolPad * 2) * scale)
        let dark = Color(hex: 0x05070C)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 14)
                .fill(dark.opacity(0.42 - 0.16 * lift))
                .frame(width: tw * scale, height: th * scale)
                .blur(radius: 5 + 14 * lift)
                .offset(x: p.x - tw * scale / 2 + 6 + 10 * lift, y: p.y - th * scale / 2 + 6 + 22 * lift)
            RoundedRectangle(cornerRadius: 12)
                .fill(dark.opacity(0.5 * (1 - min(1, lift * 3))))
                .frame(width: tw, height: th)
                .blur(radius: 2)
                .offset(x: p.x - tw / 2, y: p.y - th / 2 + 2)
            PostcardStampCancelTool(art: art)
                .scaleEffect(scale, anchor: .topLeading)
                .offset(x: p.x - full.width / 2, y: p.y - full.height / 2)
        }
        .frame(width: M.designW, height: M.designH, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: Touch

    private var touchArea: some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: M.designW, height: 580)
            .offset(y: 100)
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named("PostcardStampCancelDesign"))
                    .onChanged { v in
                        awake = true
                        model.drag(v.startLocation, v.location, at: Date.now.timeIntervalSinceReferenceDate, reduced: reduceMotion)
                    }
                    .onEnded { v in
                        model.end(v.startLocation, v.location, at: Date.now.timeIntervalSinceReferenceDate)
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Postcard and postmark. Drag the postmark onto the stamp to cancel it.")
    }
}

// MARK: - Model

/// The finger's side of the block: animation tracks evaluated against the clock, and the hand-off
/// between the self-playing loop and the finger.
final class PostcardStampCancelModel {
    typealias M = PostcardStampCancelMotion

    private var autoStart: Double?
    private var lastFrame: Double?
    private(set) var pose = M.autoPose(0)

    private var owned = false
    private var touching = false
    private var returning = false
    private var releasedAt = 0.0
    private var returnEnd = 0.0
    private var reduced = false
    private var gesture: (grab: Bool, offset: CGPoint)?

    private var x = M.Track(M.pad.x), y = M.Track(M.pad.y), h = M.Track(0), squash = M.Track(0)
    private var ink = M.Track(0), fade = M.Track(1), flip = M.Track(0), tilt = M.Track(0), count = M.Track(0)

    private let select = UISelectionFeedbackGenerator()
    private let thump = UIImpactFeedbackGenerator(style: .medium)

    private var rest: M.Pose { reduced ? M.stillPose() : M.autoPose(0) }

    func frame(_ now: Double, auto: Bool, reduced: Bool, sleep: () -> Void) -> M.Pose {
        self.reduced = reduced
        defer { lastFrame = now }
        if owned {
            if !touching && !returning && now - releasedAt >= 2 { startReturn(now) }
            if returning && now >= returnEnd {
                returning = false
                owned = false
                autoStart = now
                sleep()
            }
        }
        if owned {
            pose = M.Pose(x: x.value(now), y: y.value(now), h: h.value(now), squash: squash.value(now),
                          ink: ink.value(now), fade: fade.value(now), flip: flip.value(now), tilt: tilt.value(now),
                          count: count.value(now))
            return pose
        }
        if reduced {
            pose = M.stillPose()
            return pose
        }
        guard auto else {
            autoStart = nil
            pose = M.autoPose(0)
            return pose
        }
        if autoStart == nil { autoStart = now }
        // A stalled frame pauses the loop's clock instead of skipping ahead.
        if let last = lastFrame, now - last > 0.25, let s = autoStart { autoStart = s + (now - last) - 1 / 60 }
        let t = (now - autoStart!).truncatingRemainder(dividingBy: M.loop)
        pose = M.autoPose(max(0, t))
        return pose
    }

    /// A finger lands: the loop stops and the tracks take over from the frame on screen.
    private func takeOver(_ now: Double) {
        touching = true
        if owned && !returning { return }
        let p = pose
        x.set(p.x); y.set(p.y); h.set(p.h); squash.set(p.squash); ink.set(p.ink); fade.set(p.fade)
        flip.set(p.flip.truncatingRemainder(dividingBy: 360)); tilt.set(p.tilt); count.set(p.count)
        returning = false
        owned = true
    }

    private func letGo(_ now: Double) {
        touching = false
        releasedAt = now
    }

    func drag(_ start: CGPoint, _ at: CGPoint, at now: Double, reduced: Bool) {
        self.reduced = reduced
        if gesture == nil {
            // Only a touch that lands on the tool picks it up; anywhere else it may be a tap.
            let p = pose
            let onTool = abs(start.x - p.x) <= M.tool.w / 2 + 14 && abs(start.y - p.y) <= M.tool.h / 2 + 14
            gesture = (onTool, CGPoint(x: p.x - start.x, y: p.y - start.y))
            if onTool {
                takeOver(now)
                h.spring(1, damping: 14, stiffness: 240, at: now)
                select.selectionChanged()
            }
        }
        guard let g = gesture, g.grab else { return }
        x.set(at.x + g.offset.x)
        y.set(at.y + g.offset.y)
    }

    func end(_ start: CGPoint, _ at: CGPoint, at now: Double) {
        guard let g = gesture else { return }
        gesture = nil
        if g.grab {
            if hypot(x.value(now) - M.target.x, y.value(now) - M.target.y) < 64 {
                press(now)
            } else {
                h.timing(0, 0.26, .bezier(0.65, 0, 0.35, 1), at: now)
            }
            letGo(now)
        } else if hypot(at.x - start.x, at.y - start.y) < 10 && M.card.contains(at) {
            // A tap on the card turns it over.
            takeOver(now)
            flip.spring(flip.value(now) < 90 ? 180 : 0, damping: 16, stiffness: 120, at: now)
            letGo(now)
        }
    }

    /// The press over the stamp: drop, squash, ink spreads, lift, the count rolls.
    private func press(_ now: Double) {
        x.spring(M.target.x, damping: 18, stiffness: 220, at: now)
        y.spring(M.target.y, damping: 18, stiffness: 220, at: now)
        let inOut = M.Curve.bezier(0.65, 0, 0.35, 1)
        h.run([
            .init(to: 1.08, kind: .timing(0.09, .outQuad)),
            .init(to: 0, kind: .timing(0.19, inOut)),
            .init(delay: 0.38, to: 0.85, kind: .timing(0.32, .bezier(0.2, 0.8, 0.2, 1))),
        ], at: now)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) { [thump] in thump.impactOccurred() }
        squash.run([
            .init(delay: 0.28, to: 1, kind: .timing(0.17, .inOutQuad)),
            .init(to: 0, kind: .timing(0.28, .inOutQuad)),
        ], at: now)
        if ink.value(now) < 1 {
            fade.set(1)
            ink.timing(1, 0.45, .bezier(0.2, 0.8, 0.2, 1), delay: 0.28, at: now)
            count.timing(1, 0.35, inOut, delay: 0.9, at: now)
        }
        tilt.run([
            .init(delay: 0.28, to: -1.6, kind: .timing(0.07, .inOutQuad)),
            .init(to: 0, kind: .spring(damping: 6, stiffness: 260, mass: 0.6, velocity: nil)),
        ], at: now)
    }

    /// Home within 0.9 s: the tool flies back, the card turns writing side up, the ink clears.
    private func startReturn(_ now: Double) {
        returning = true
        let r = rest
        let inOut = M.Curve.bezier(0.65, 0, 0.35, 1)
        x.timing(r.x, 0.76, inOut, at: now)
        y.timing(r.y, 0.76, inOut, at: now)
        h.run([.init(to: 0.7, kind: .timing(0.3, .inOutQuad)), .init(to: 0, kind: .timing(0.46, inOut))], at: now)
        squash.timing(0, 0.2, at: now)
        fade.timing(r.fade, 0.38, at: now)
        flip.timing(0, 0.76, inOut, at: now)
        tilt.timing(0, 0.3, at: now)
        count.timing(r.count, 0.76, inOut, at: now)
        ink.timing(r.ink, 0.38, delay: 0.4, at: now)
        returnEnd = now + 0.78
    }
}

// MARK: - Static pieces

/// Header: kicker, title with the trip counter, and the day's line.
struct PostcardStampCancelHeader: View {
    var count: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("POSTMARKS")
                .font(.system(size: 12, weight: .bold)).kerning(2.4)
                .foregroundStyle(PostcardStampCancel.amber)
            HStack {
                Text("Lisbon, day 4")
                    .font(.system(size: 32, weight: .heavy)).kerning(-0.6)
                    .foregroundStyle(PostcardStampCancel.cream)
                Spacer(minLength: 8)
                counter
            }
            Text("Tram 28 · Alfama · 24 October")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(PostcardStampCancel.muted)
        }
        .frame(width: 345, alignment: .leading)
        .offset(x: 24, y: 8)
    }

    private var counter: some View {
        let digit = Font.system(size: 16, weight: .bold).monospacedDigit()
        return HStack(spacing: 0) {
            Text("SENT")
                .font(.system(size: 10, weight: .bold)).kerning(1.4)
                .foregroundStyle(PostcardStampCancel.muted)
                .padding(.trailing, 6)
            Text("1").font(digit).foregroundStyle(PostcardStampCancel.cream).frame(height: 22)
            VStack(spacing: 0) {
                ForEach(0..<5, id: \.self) { d in
                    Text("\(d)").font(digit).foregroundStyle(PostcardStampCancel.cream).frame(height: 22)
                }
            }
            .offset(y: -(2 + count) * 22)
            .frame(height: 22, alignment: .top)
            .clipped()
        }
        .padding(.horizontal, 12)
        .frame(height: 32)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.rgba(243, 235, 218, 0.08))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.rgba(243, 235, 218, 0.22), lineWidth: 0.5))
        )
        .accessibilityElement()
        .accessibilityLabel("Postcards sent this trip")
    }
}

/// The hint, the postmark card on the right, under the tool's flight.
struct PostcardStampCancelInfo: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            Text("Drag the postmark onto the stamp · tap the card to turn it")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(PostcardStampCancel.muted)
                .multilineTextAlignment(.center)
                .frame(width: 345)
                .offset(x: 24, y: 362)
            VStack(alignment: .leading, spacing: 3) {
                Text("TODAY’S POSTMARK")
                    .font(.system(size: 10, weight: .bold)).kerning(1.4)
                    .foregroundStyle(PostcardStampCancel.amber)
                Text("Lisboa · 24 Oct")
                    .font(.custom("Georgia-Bold", fixedSize: 18))
                    .foregroundStyle(PostcardStampCancel.cream)
                    .lineLimit(1).minimumScaleFactor(0.8)
                    .padding(.top, 2)
                Text("Ultramarine Nº 4 · 13:40")
                    .font(.system(size: 12, weight: .medium).monospacedDigit())
                    .foregroundStyle(PostcardStampCancel.muted)
                    .lineLimit(1).minimumScaleFactor(0.8)
                Rectangle().fill(Color.rgba(243, 235, 218, 0.2)).frame(height: 0.5).padding(.vertical, 6)
                Text("3 of 5 cards for this trip")
                    .font(.system(size: 12, weight: .medium).monospacedDigit())
                    .foregroundStyle(PostcardStampCancel.muted)
                    .lineLimit(1).minimumScaleFactor(0.8)
            }
            .padding(14)
            .frame(width: 163, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.rgba(243, 235, 218, 0.07))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color.rgba(243, 235, 218, 0.18), lineWidth: 0.5))
                    .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.3), radius: 14, y: 12)
            )
            .offset(x: 214, y: 456)
        }
        .allowsHitTesting(false)
    }
}

struct PostcardStampCancelPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

struct PostcardStampCancelActions: View {
    var body: some View {
        HStack(spacing: 12) {
            Button {} label: {
                Text("Write another")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(PostcardStampCancel.cream)
                    .frame(width: 151.7, height: 52)
                    .background(
                        Capsule().fill(Color.rgba(243, 235, 218, 0.06))
                            .overlay(Capsule().stroke(Color.rgba(243, 235, 218, 0.3), lineWidth: 1))
                    )
            }
            Button {} label: {
                Text("Send card")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color(hex: 0x2A1806))
                    .frame(width: 197.3, height: 52)
                    .background(
                        Capsule()
                            .fill(LinearGradient(stops: [
                                .init(color: Color(hex: 0xF6CB86), location: 0),
                                .init(color: PostcardStampCancel.amber, location: 0.55),
                                .init(color: Color(hex: 0xD38E3C), location: 1),
                            ], startPoint: .top, endPoint: .bottom))
                            .overlay(Capsule().inset(by: 0.5).trim(from: 0, to: 1).stroke(
                                LinearGradient(colors: [Color.rgba(255, 246, 222, 0.6), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                            .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                            .shadow(color: Color(hex: 0xD38E3C).opacity(0.3), radius: 11, y: 10)
                    )
            }
        }
        .buttonStyle(PostcardStampCancelPressStyle())
        .offset(x: 16, y: 694)
    }
}

/// The handstamp, drawn once and placed every frame.
struct PostcardStampCancelTool: View {
    var art: PostcardStampCancelArt.Baked?

    var body: some View {
        Canvas { ctx, _ in PostcardStampCancelArt.drawTool(ctx, art: art) }
            .frame(width: PostcardStampCancelMotion.tool.w + 12, height: PostcardStampCancelMotion.tool.h + 12)
    }
}

/// The writing side: note, address, stamp and a dog-eared corner showing the photo.
struct PostcardStampCancelWritingSide: View {
    var photo: UIImage?

    private static let ink = Color(hex: 0x26305A)
    private static let rule = Color.rgba(90, 70, 50, 0.25)

    var body: some View {
        let italic = UIFont(name: "Georgia-Italic", size: 13)
        let extra = max(0, 20 - (italic?.lineHeight ?? 15))
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [
                .init(color: Color(hex: 0xF8F2E4), location: 0), .init(color: Color(hex: 0xF4ECDA), location: 0.55),
                .init(color: Color(hex: 0xEADFC6), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing)
            Text("Day four. Rode the yellow tram all the way up to the castle, then got lost in Alfama on purpose. Custard tarts still warm — I have had three.")
                .font(.custom("Georgia-Italic", fixedSize: 13))
                .lineSpacing(extra)
                .foregroundStyle(Self.ink)
                .frame(width: 160, alignment: .leading)
                .padding(.top, extra / 2)
                .offset(x: 16, y: 16)
            Text("— J.")
                .font(.custom("Georgia-Italic", fixedSize: 15))
                .foregroundStyle(Self.ink)
                .offset(x: 112, y: 186)
            Rectangle().fill(Color.rgba(90, 70, 50, 0.28)).frame(width: 1, height: 230 - 44).offset(x: 188, y: 22)
            Canvas { ctx, _ in PostcardStampCancelArt.drawStamp(ctx) }
                .frame(width: 70, height: 84)
                .shadow(color: .rgba(40, 25, 10, 0.35), radius: 0.75, y: 1)
                .offset(x: 262, y: 14)
            ForEach(Array(["Ines Marlowe", "14 Wren Lane", "Bristol BS8 4QT", "United Kingdom"].enumerated()), id: \.offset) { i, line in
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    Text(line)
                        .font(.custom("Georgia-Italic", fixedSize: 14))
                        .foregroundStyle(Self.ink)
                        .padding(.leading, 4)
                        .frame(maxWidth: .infinity, minHeight: 20, maxHeight: 20, alignment: .leading)
                    Rectangle().fill(Self.rule).frame(height: 1)
                }
                .frame(width: 131, height: 22)
                .offset(x: 200, y: 120 + Double(i) * 25)
            }
            Text("POST CARD · CARTE POSTALE")
                .font(.system(size: 7, weight: .bold)).kerning(1.4)
                .foregroundStyle(Color.rgba(90, 70, 50, 0.5))
                .frame(height: 9)
                .offset(x: 200, y: 230 - 3 - 9)
            Canvas { ctx, _ in PostcardStampCancelArt.drawDogEar(ctx, photo: photo) }
                .frame(width: 34, height: 34)
                .offset(x: 345 - 34, y: 230 - 34)
        }
        .frame(width: 345, height: 230, alignment: .topLeading)
        .background(Color(hex: 0xF4ECDA))
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}

/// The photo side: Alfama's rooftops in a white border, with a greeting.
struct PostcardStampCancelPhotoSide: View {
    var photo: UIImage?

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color(hex: 0xFBF8F1)
            Group {
                if let photo {
                    Image(uiImage: photo).resizable().scaledToFill()
                } else {
                    Color(hex: 0x6F7F96)
                }
            }
            .frame(width: 329, height: 214)
            .clipShape(RoundedRectangle(cornerRadius: 2))
            .offset(x: 8, y: -8)
            LinearGradient(colors: [Color.rgba(20, 24, 40, 0), Color.rgba(20, 24, 40, 0.55)], startPoint: UnitPoint(x: 0, y: 0.45), endPoint: .bottom)
                .frame(width: 329, height: 110)
                .clipShape(RoundedRectangle(cornerRadius: 2))
                .offset(x: 8, y: -8)
            Text("Lisboa")
                .font(.custom("Georgia-BoldItalic", fixedSize: 34))
                .foregroundStyle(Color(hex: 0xFFF7E8))
                .shadow(color: .black.opacity(0.45), radius: 1.5, y: 1)
                .offset(x: 22, y: -34)
            Text("ALFAMA ROOFTOPS · TEJO")
                .font(.system(size: 9, weight: .bold)).kerning(1.6)
                .foregroundStyle(Color.rgba(255, 247, 232, 0.85))
                .offset(x: 24, y: -20)
        }
        .frame(width: 345, height: 230)
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}
