import SwiftUI
import UIKit

/*
 Ghost Complete Field: a reply in Postbox, a mail app that finishes your sentences.

 The loop types without focusing anything (no keyboard). In Cc it types "theo@", two domain
 chips pop beside the caret and the tapped one stretches into the field. In the body it types
 "Hi Mara, tha": the rest of the phrase appears behind the caret in grey, word by word out of a
 blur, and a tap on the keycap fills it with ink while the caret jumps to its end. A few more
 letters, a second suggestion, and a swipe to the right accepts it. Then the reply clears.
*/
struct GhostCompleteField: View {
    static let loop: Double = GhostCompleteFieldMotion.loop

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model = GhostCompleteFieldModel()

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    var body: some View {
        GeometryReader { outer in
            let top = outer.safeAreaInsets.top
            let bottom = outer.safeAreaInsets.bottom
            GeometryReader { inner in
                let g = GhostCompleteFieldGeo(W: inner.size.width, H: inner.size.height, top: top, bottom: bottom)
                content(g)
                    .onAppear { model.configure(g, autoPlay: autoPlay, reduced: reduceMotion) }
            }
            .ignoresSafeArea()
        }
        .background(GhostCompleteFieldGeo.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { model.stop() }
    }

    @ViewBuilder
    private func content(_ g: GhostCompleteFieldGeo) -> some View {
        ZStack(alignment: .topLeading) {
            GhostCompleteFieldGeo.page
                .contentShape(Rectangle())
                .onTapGesture { model.dismissKeyboard() }

            GhostCompleteFieldStatic(g: g).equatable()

            if let body = model.body, let cc = model.cc {
                TimelineView(.animation(minimumInterval: nil, paused: model.isPaused)) { timeline in
                    live(g, body: body, cc: cc, now: timeline.date.timeIntervalSinceReferenceDate)
                }
            }
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .clipped()
    }

    private func live(_ g: GhostCompleteFieldGeo, body: GhostCompleteFieldController, cc: GhostCompleteFieldController, now: Double) -> some View {
        let t = model.loopTime(now)
        let bodyPose = model.bodyPose(now: now, t: t)
        let ccPose = model.ccPose(now: now, t: t)
        let bodyLayout = model.bodyLayout
        let ccLayout = model.ccLayout
        let chips = model.chipsPose(now: now, t: t)
        let send = model.sendValue(now: now, t: t)
        let dot = model.dotPose(t: t)

        return ZStack(alignment: .topLeading) {
            GhostCompleteFieldDrawing(pose: ccPose, layout: ccLayout, font: cc.font, width: g.ccWidth, height: GhostCompleteFieldGeo.rowLine,
                                      lineHeight: GhostCompleteFieldGeo.rowLine, placeholder: "Add people",
                                      ghost: GhostCompleteFieldRGB(0xD9472B, 0))
                .offset(x: g.ccX, y: g.ccTop + (g.rowH - GhostCompleteFieldGeo.rowLine) / 2)

            GhostCompleteFieldDrawing(pose: bodyPose, layout: bodyLayout, font: body.font, width: g.width, height: g.bodyH,
                                      lineHeight: GhostCompleteFieldGeo.line, placeholder: "Write your reply",
                                      ghost: GhostCompleteFieldRGB(0xB7BCC8))
                .offset(x: g.margin, y: g.bodyTop)

            GhostCompleteFieldLineInput(kind: .cc, field: cc, register: { model.ccInput = $0 },
                                        onTouch: model.enterUser, onFocus: model.onFocus, onBlur: model.onBlur)
                .frame(width: g.ccWidth, height: GhostCompleteFieldGeo.rowLine)
                .offset(x: g.ccX, y: g.ccTop + (g.rowH - GhostCompleteFieldGeo.rowLine) / 2)

            GhostCompleteFieldLineInput(kind: .subject, field: nil, register: { model.subjectInput = $0 },
                                        onTouch: model.touch, onFocus: { model.enterUser(); model.onFocus() }, onBlur: model.onBlur)
                .frame(width: g.width - g.subjectX, height: g.rowH)
                .offset(x: g.margin + g.subjectX, y: g.rowsTop + g.rowH * 2)

            GhostCompleteFieldBodyInput(field: body, onTouch: model.enterUser, onFocus: model.onFocus, onBlur: model.onBlur)
                .frame(width: g.width, height: g.bodyH)
                .offset(x: g.margin, y: g.bodyTop)

            GhostCompleteFieldKeycap(pose: bodyPose, layout: bodyLayout, lineHeight: GhostCompleteFieldGeo.line,
                                     onPress: { model.enterUser(); body.accept() },
                                     onDown: { body.press($0) })
                .offset(x: g.margin, y: g.bodyTop)

            chipRow(g, chips, ccPose: ccPose, ccLayout: ccLayout)

            sendButton(g, send)

            finger(g, dot)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
    }

    private func sendButton(_ g: GhostCompleteFieldGeo, _ send: Double) -> some View {
        let grey = GhostCompleteFieldRGB(0xD5D8E0)
        let color = grey.mix(GhostCompleteFieldDrawing.accent, send).color
        let shade = Color(.sRGB, red: 21 / 255, green: 26 / 255, blue: 36 / 255)
        return Button { model.onSend() } label: {
            ZStack {
                Circle().fill(color)
                Circle()
                    .strokeBorder(
                        LinearGradient(colors: [.white.opacity(0.25), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.12)),
                        lineWidth: 1
                    )
                Image(systemName: "arrow.up")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
            }
            .frame(width: 32, height: 32)
            .shadow(color: shade.opacity(0.14), radius: 1, y: 1)
            .contentShape(Circle().inset(by: -10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Send")
        .offset(x: g.margin + g.width - 32, y: g.navTop + 6)
    }

    private func chipRow(_ g: GhostCompleteFieldGeo, _ p: GhostCompleteFieldMotion.ChipsPose,
                         ccPose: GhostCompleteFieldMotion.Pose, ccLayout: GhostCompleteFieldLayout) -> some View {
        let stop: Int = (ccPose.run >= 0 && ccPose.run < ccLayout.runs.count) ? ccLayout.runs[ccPose.run].start : ccPose.n
        let caret = ccLayout.cx[min(ccLayout.cx.count - 1, max(0, stop))]
        let labels = model.chips
        let widths = labels.map(GhostCompleteFieldModel.chipWidth)
        let originX = g.ccX + caret + GhostCompleteFieldGeo.chipsLeft
        let originY = g.ccTop + (g.rowH - 26) / 2
        let stretch = model.stretchValue(now: Date().timeIntervalSinceReferenceDate)

        return ZStack(alignment: .topLeading) {
            if p.show > 0 {
                ForEach(Array(labels.enumerated()), id: \.element) { index, label in
                    let x = widths[..<index].reduce(0, +) + CGFloat(index) * 8
                    GhostCompleteFieldChip(index: index, label: label, width: widths[index], x: x, pose: p, stretch: stretch) {
                        model.pick(index)
                    }
                    .offset(x: x)
                }
            }
        }
        .offset(x: originX, y: originY)
    }

    private func finger(_ g: GhostCompleteFieldGeo, _ p: GhostCompleteFieldMotion.DotPose) -> some View {
        let targets = model.fingerTargets(g)
        let at = targets[p.target]
        let x = min(g.W - 30, at.x + CGFloat(p.travel * GhostCompleteFieldMotion.demoSwipe))
        let ink = Color(.sRGB, red: 21 / 255, green: 26 / 255, blue: 36 / 255)
        return Circle()
            .fill(ink.opacity(0.14))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.8), lineWidth: 1))
            .shadow(color: ink.opacity(0.14), radius: 6, y: 4)
            .frame(width: 44, height: 44)
            .scaleEffect(p.scale)
            .offset(x: x - 22, y: at.y - 22 + (p.target == 4 ? 0 : (1 - p.alpha) * 8))
            .opacity(p.alpha)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Geometry

struct GhostCompleteFieldGeo: Equatable {
    static let page = Color(.sRGB, red: 0xFB / 255, green: 0xFB / 255, blue: 0xFC / 255)
    static let line: CGFloat = 26
    static let rowLine: CGFloat = 22
    static let labelW: CGFloat = 36
    static let chipsLeft: CGFloat = 12

    let W: CGFloat, H: CGFloat, margin: CGFloat, width: CGFloat
    let navTop: CGFloat, rowH: CGFloat, rowsTop: CGFloat, ccTop: CGFloat, ccX: CGFloat, ccWidth: CGFloat
    let bodyTop: CGFloat, bodyH: CGFloat, toolbarTop: CGFloat, quoteTop: CGFloat, quoteH: CGFloat
    /// Where the Subject field starts: after its label and the label's 8 pt gap.
    let subjectX: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        let compact = H < 740
        self.W = W
        self.H = H
        margin = max(20, (W - 440) / 2)
        width = W - margin * 2
        navTop = top + (compact ? 2 : 6)
        rowH = compact ? 42 : 46
        rowsTop = navTop + 44 + (compact ? 2 : 8)
        ccTop = rowsTop + rowH
        ccX = margin + Self.labelW
        ccWidth = width - Self.labelW
        bodyTop = rowsTop + rowH * 3 + (compact ? 12 : 16)
        bodyH = Self.line * (compact ? 4 : 6)
        toolbarTop = H - bottom - 50
        quoteTop = bodyTop + bodyH + (compact ? 8 : 14)
        quoteH = max(0, toolbarTop - 12 - quoteTop)
        let label = ("Subject:" as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 15)]).width
        subjectX = ceil(label) + 8
    }
}

// MARK: - Everything that never moves

private struct GhostCompleteFieldStatic: View, Equatable {
    let g: GhostCompleteFieldGeo

    private let ink = GhostCompleteFieldDrawing.ink.color
    private let muted = Color(.sRGB, red: 0x8A / 255, green: 0x90 / 255, blue: 0xA0 / 255)
    private let hairline = Color(.sRGB, red: 0xE6 / 255, green: 0xE7 / 255, blue: 0xEB / 255)
    private let body2 = Color(.sRGB, red: 0x5B / 255, green: 0x61 / 255, blue: 0x70 / 255)

    static func == (a: Self, b: Self) -> Bool { a.g == b.g }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text("Cancel")
                .font(.system(size: 17))
                .foregroundStyle(GhostCompleteFieldDrawing.accent.color)
                .frame(height: 44)
                .offset(x: g.margin, y: g.navTop)

            Text("Reply")
                .font(.system(size: 17, weight: .semibold))
                .kerning(-0.2)
                .foregroundStyle(ink)
                .frame(width: g.W, height: 44)
                .offset(y: g.navTop)

            row(top: g.rowsTop) {
                rowLabel("To:")
                token
            }
            row(top: g.ccTop) { rowLabel("Cc:") }
            row(top: g.rowsTop + g.rowH * 2) { rowLabel("Subject:") }

            quote
                .frame(width: g.width, height: g.quoteH, alignment: .topLeading)
                .clipped()
                .overlay(alignment: .bottom) {
                    LinearGradient(colors: [GhostCompleteFieldGeo.page.opacity(0), GhostCompleteFieldGeo.page],
                                   startPoint: .top, endPoint: .bottom)
                        .frame(height: 44)
                }
                .offset(x: g.margin, y: g.quoteTop)

            toolbar
                .frame(width: g.width, height: 50)
                .overlay(alignment: .top) { hairline.frame(height: 1 / 3) }
                .offset(x: g.margin, y: g.toolbarTop)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func rowLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 15))
            .foregroundStyle(muted)
            .padding(.trailing, 8)
    }

    private func row<C: View>(top: CGFloat, @ViewBuilder content: () -> C) -> some View {
        HStack(spacing: 0) {
            content()
            Spacer(minLength: 0)
        }
        .frame(width: g.width, height: g.rowH)
        .overlay(alignment: .bottom) { hairline.frame(height: 1 / 3) }
        .offset(x: g.margin, y: top)
    }

    private var token: some View {
        HStack(spacing: 6) {
            ZStack {
                Circle().fill(Color(.sRGB, red: 0x3E / 255, green: 0x4A / 255, blue: 0x63 / 255))
                Text("MO")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Color(.sRGB, red: 0xF4 / 255, green: 0xF5 / 255, blue: 0xF8 / 255))
            }
            .frame(width: 20, height: 20)
            Text("Mara Okafor")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(ink)
        }
        .padding(.leading, 4)
        .padding(.trailing, 10)
        .frame(height: 28)
        .background(Capsule().fill(Color(.sRGB, red: 0xEE / 255, green: 0xF0 / 255, blue: 0xF4 / 255)))
    }

    private func quoteLine(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 15))
            .lineSpacing(21 - UIFont.systemFont(ofSize: 15).lineHeight)
            .padding(.vertical, (21 - UIFont.systemFont(ofSize: 15).lineHeight) / 2)
            .foregroundStyle(body2)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var file: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 6).fill(Color(.sRGB, red: 0xFC / 255, green: 0xE9 / 255, blue: 0xE3 / 255))
                UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 4, bottomTrailingRadius: 0, topTrailingRadius: 6)
                    .fill(Color(.sRGB, red: 0xF3 / 255, green: 0xC9 / 255, blue: 0xBC / 255))
                    .frame(width: 10, height: 10)
                    .offset(x: 20)
                VStack(alignment: .leading, spacing: 3) {
                    ForEach([14, 18, 10], id: \.self) { w in
                        Capsule()
                            .fill(GhostCompleteFieldDrawing.accent.color.opacity(0.55))
                            .frame(width: CGFloat(w), height: 2)
                    }
                }
                .offset(x: 6, y: 13)
            }
            .frame(width: 30, height: 36)
            VStack(alignment: .leading, spacing: 1) {
                Text("Tuesday notes.pdf")
                    .font(.system(size: 15, weight: .semibold))
                    .kerning(-0.1)
                    .foregroundStyle(ink)
                Text("3 pages \u{00B7} 240 KB")
                    .font(.system(size: 13).monospacedDigit())
                    .foregroundStyle(muted)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .frame(height: 56)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white)
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(hairline, lineWidth: 1 / 3))
                .shadow(color: Color(.sRGB, red: 21 / 255, green: 26 / 255, blue: 36 / 255).opacity(0.05), radius: 10, y: 8)
                .shadow(color: Color(.sRGB, red: 21 / 255, green: 26 / 255, blue: 36 / 255).opacity(0.05), radius: 1, y: 1)
        )
        .padding(.bottom, 4)
    }

    private var quote: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("On Tue 12 May at 16:42, Mara Okafor wrote:")
                .font(.system(size: 13))
                .foregroundStyle(muted)
                .lineLimit(1)
                .frame(height: 18)
            HStack(alignment: .top, spacing: 12) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(Color(.sRGB, red: 0xE1 / 255, green: 0xE4 / 255, blue: 0xEA / 255))
                    .frame(width: 3)
                VStack(alignment: .leading, spacing: 7) {
                    file
                    quoteLine("Hi Ines,")
                    quoteLine("Notes from today's walkthrough are attached. Three things to settle before Friday:")
                    quoteLine("1. Move the launch review to Thursday")
                    quoteLine("2. Pick one of the two onboarding flows")
                    quoteLine("3. Decide who owns the pricing page")
                    quoteLine("Shout if I missed anything.")
                    quoteLine("Mara")
                }
                .padding(.vertical, 2)
                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var toolbar: some View {
        HStack(spacing: 0) {
            HStack(spacing: 24) {
                ForEach(["paperclip", "photo", "textformat"], id: \.self) { name in
                    Image(systemName: name)
                        .font(.system(size: 14.5))
                        .foregroundStyle(muted)
                        .frame(width: 20, height: 20)
                }
            }
            Spacer(minLength: 0)
            Text("Swipe right or tap Tab to accept")
                .font(.system(size: 13))
                .foregroundStyle(muted)
        }
    }
}

// MARK: - Domain chip

private struct GhostCompleteFieldChip: View {
    typealias M = GhostCompleteFieldMotion

    let index: Int
    let label: String
    let width: CGFloat
    let x: CGFloat
    let pose: M.ChipsPose
    let stretch: CGFloat
    let onPress: () -> Void

    var body: some View {
        let p = pose
        let pop = M.easeOutBack(M.clamp01((p.show - Double(index) * M.chipGap) / M.chipS), 1.5)
        let picked = p.pick == index
        let other = p.pick >= 0 && !picked
        let leave = other ? 1 - M.clamp01(p.take * 3) : 1 - M.seg(p.take, 0.6, 0.92)
        let travel: CGFloat = picked
            ? -(x + GhostCompleteFieldGeo.chipsLeft - 2) * CGFloat(M.easeInOutCubic(M.seg(p.take, 0, 0.55)))
            : (other ? 10 * CGFloat(p.take) : 0)
        let s = picked ? M.easeOutBack(M.seg(p.take, 0.05, 0.6), 1.4) : 0
        let scaleX = picked ? 1 + (stretch / max(1, width) - 1) * CGFloat(s) : 1
        let scaleY = picked ? 1 - 0.14 * CGFloat(s) : 1
        let labelOpacity = picked ? 1 - M.seg(p.take, 0.08, 0.38) : 1
        let shape = RoundedRectangle(cornerRadius: 13, style: .circular)

        return Button(action: onPress) {
            ZStack {
                shape
                    .fill(Color(.sRGB, red: 0xFC / 255, green: 0xE9 / 255, blue: 0xE3 / 255))
                    .overlay(shape.strokeBorder(Color(.sRGB, red: 0xF3 / 255, green: 0xC9 / 255, blue: 0xBC / 255), lineWidth: 1 / 3))
                    .scaleEffect(x: scaleX, y: scaleY, anchor: .leading)
                Text(label)
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundStyle(Color(.sRGB, red: 0xB4 / 255, green: 0x3A / 255, blue: 0x22 / 255))
                    .lineLimit(1)
                    .fixedSize()
                    .opacity(labelOpacity)
            }
            .frame(width: width, height: 26)
            .contentShape(Rectangle().inset(by: -6))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Use \(label)")
        .scaleEffect(0.8 + 0.2 * pop)
        .offset(x: travel)
        .opacity(p.show <= 0 ? 0 : M.clamp01(pop * 2) * leave)
    }
}

// MARK: - The hand-over between the loop and a person

@MainActor
final class GhostCompleteFieldModel: ObservableObject {
    typealias M = GhostCompleteFieldMotion
    typealias L = GhostCompleteFieldLayout
    typealias Tw = GhostCompleteFieldTween

    static let subject = "Re: Tuesday notes"
    private static let ccFont = UIFont.systemFont(ofSize: 17)
    private static let bodyFont = UIFont.systemFont(ofSize: 18)
    private static let chipFont = UIFont.systemFont(ofSize: 13.5, weight: .medium)

    static func chipWidth(_ label: String) -> CGFloat {
        ceil((label as NSString).size(withAttributes: [.font: chipFont]).width) + 20
    }

    /// 0 = the loop (or rest), 1 = a person drives, 2 = clearing before the loop restarts.
    @Published private(set) var mode = 0
    @Published private(set) var chips = M.ccChips
    @Published private(set) var isPaused = true
    @Published private(set) var body: GhostCompleteFieldController?
    @Published private(set) var cc: GhostCompleteFieldController?

    var ccInput: UITextField?
    var subjectInput: UITextField?

    private var bodyAuto = L.empty
    private var ccAuto = L.empty
    private var autoStretch: CGFloat = 80
    private var uShow = Tw(), uPick = -1, uTake = Tw(), uStretch = Tw(80), uSend = Tw()
    private var focusCount = 0
    private var picking = false
    private var chipCount = M.ccChips.count
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var loopStart = 0.0
    private var auto = false
    private var still = false
    private var reduced = false
    private var watcher: Task<Void, Never>?

    private var clockNow: Double { Date().timeIntervalSinceReferenceDate }

    var bodyLayout: L { mode == 0 ? bodyAuto : (body?.layout ?? L.empty) }
    var ccLayout: L { mode == 0 ? ccAuto : (cc?.layout ?? L.empty) }

    func configure(_ g: GhostCompleteFieldGeo, autoPlay: Bool, reduced: Bool) {
        guard body == nil else { return }
        self.reduced = reduced
        auto = autoPlay && !reduced
        still = autoPlay && reduced
        bodyAuto = L(font: Self.bodyFont, text: M.bodyFull, ghosts: M.bodyGhosts, width: Double(g.width), lineHeight: Double(GhostCompleteFieldGeo.line))
        ccAuto = L(font: Self.ccFont, text: M.ccFull, ghosts: M.ccGhosts, width: Double(g.ccWidth), lineHeight: Double(GhostCompleteFieldGeo.rowLine), keycap: false)
        autoStretch = CGFloat(L.textWidth(Self.ccFont, M.ccDomain)) + 8

        let b = GhostCompleteFieldController(font: Self.bodyFont, width: Double(g.width), lineHeight: Double(GhostCompleteFieldGeo.line),
                                             multiline: true, keycap: true, suggests: true)
        let c = GhostCompleteFieldController(font: Self.ccFont, width: Double(g.ccWidth), lineHeight: Double(GhostCompleteFieldGeo.rowLine),
                                             multiline: false, keycap: false, suggests: false)
        b.still = reduced
        c.still = reduced
        b.onChange = { [weak self] text in self?.onBodyChange(text) }
        c.onChange = { [weak self] text in self?.onCcChange(text) }
        body = b
        cc = c
        loopStart = clockNow
        isPaused = !auto
    }

    func stop() {
        watcher?.cancel()
        watcher = nil
        isPaused = true
    }

    func loopTime(_ now: Double) -> Double {
        if still { return M.tFilled }
        if !auto { return 0 }
        let e = now - loopStart
        return e <= 0 ? 0 : e.truncatingRemainder(dividingBy: M.loop)
    }

    // MARK: Poses

    func bodyPose(now: Double, t: Double) -> M.Pose {
        if mode != 0 { return body?.pose(now) ?? M.Pose() }
        var p = M.autoBody(t)
        if still { p.caret = 0 }
        return p
    }

    func ccPose(now: Double, t: Double) -> M.Pose {
        if mode != 0 { return cc?.pose(now) ?? M.Pose() }
        var p = M.autoCc(t)
        if still { p.caret = 0 }
        return p
    }

    func chipsPose(now: Double, t: Double) -> M.ChipsPose {
        if mode == 0 { return auto ? M.autoChips(t) : M.ChipsPose() }
        return M.ChipsPose(show: uShow.value(now), pick: uPick, take: uTake.value(now))
    }

    func stretchValue(now: Double) -> CGFloat {
        mode == 0 ? autoStretch : CGFloat(uStretch.value(now))
    }

    func sendValue(now: Double, t: Double) -> Double {
        mode == 0 ? M.autoSend(t) : uSend.value(now)
    }

    func dotPose(t: Double) -> M.DotPose {
        mode == 0 && auto ? M.autoDot(t) : M.DotPose()
    }

    func fingerTargets(_ g: GhostCompleteFieldGeo) -> [CGPoint] {
        let line = GhostCompleteFieldGeo.line
        let r0 = bodyAuto.runs.first
        let r1 = bodyAuto.runs.count > 1 ? bodyAuto.runs[1] : nil
        let typedStop = M.ccTyped.utf16.count
        let typedX = ccAuto.cx.count > typedStop ? ccAuto.cx[typedStop] : 0
        let swipeY = r1.map { bodyAuto.cy[$0.end] } ?? 0
        return [
            CGPoint(x: g.ccX + 48, y: g.ccTop + g.rowH / 2),
            CGPoint(x: g.ccX + typedX + GhostCompleteFieldGeo.chipsLeft + 44, y: g.ccTop + g.rowH / 2),
            CGPoint(x: g.margin + 72, y: g.bodyTop + line / 2),
            CGPoint(x: g.margin + (r0?.chipX ?? 0) + L.keycapW / 2, y: g.bodyTop + (r0?.chipY ?? 0) + line / 2),
            CGPoint(x: g.margin + g.width * 0.2, y: g.bodyTop + swipeY + line / 2),
        ]
    }

    // MARK: Events

    private func onBodyChange(_ text: String) {
        uSend.to(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0 : 1, 0.22, now: clockNow)
    }

    private func onCcChange(_ text: String) {
        if picking { return }
        let next = L.suggestDomains(text)
        if !next.isEmpty && chipCount == 0 {
            uShow.set(0)
            uShow.to(2, 2, now: clockNow)
        }
        if next.isEmpty { uShow.set(0) }
        chipCount = next.count
        if chips != next { chips = next }
    }

    func touch() {
        lastTouch = clockNow
    }

    /// Hands the loop's current text to the person, so taking over never jumps.
    func enterUser() {
        touch()
        if mode == 1 { return }
        let now = clockNow
        if mode == 0 {
            let t = still ? M.tFilled : (auto ? loopTime(now) : 0)
            let address = String(decoding: Array(M.ccFull.utf16).prefix(M.ccCount(t)), as: UTF16.self)
            let message = String(decoding: Array(M.bodyFull.utf16).prefix(M.bodyCount(t)), as: UTF16.self)
            cc?.adopt(address, thenSuggest: false)
            body?.adopt(message)
            let next = L.suggestDomains(address)
            chipCount = next.count
            chips = next
            uShow.set(next.isEmpty ? 0 : 2)
            uPick = -1
            uTake.set(0)
            uSend.set(message.isEmpty ? 0 : 1)
        } else {
            body?.ink.to(1, 0.15, now: now)
            cc?.ink.to(1, 0.15, now: now)
        }
        mode = 1
        isPaused = false
        startWatching()
    }

    func onFocus() {
        focusCount += 1
    }

    func onBlur() {
        focusCount = max(0, focusCount - 1)
        touch()
    }

    private func startWatching() {
        guard watcher == nil else { return }
        watcher = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 100_000_000)
                guard let self else { return }
                if self.mode == 0 { self.watcher = nil; return }
                self.watch()
            }
        }
    }

    private func watch() {
        let now = clockNow
        if mode == 1 {
            if focusCount == 0 && now - lastTouch > M.idle {
                mode = 2
                returnAt = now
                let d = reduced ? 0 : M.returnLen * 0.6
                body?.ink.to(0, d, now: now)
                cc?.ink.to(0, d, now: now)
                uSend.to(0, d, now: now)
                uShow.set(0)
            }
            return
        }
        if now - returnAt > M.returnLen {
            uPick = -1
            uTake.set(0)
            body?.adopt("", thenSuggest: false)
            cc?.adopt("", thenSuggest: false)
            subjectInput?.attributedText = NSAttributedString(string: Self.subject, attributes: GhostCompleteFieldLineInput.subjectAttributes)
            picking = false
            chipCount = M.ccChips.count
            chips = M.ccChips
            loopStart = now
            mode = 0
            isPaused = !auto
        }
    }

    /// A domain chip was tapped: it stretches into the field and its text takes ink there.
    func pick(_ index: Int) {
        enterUser()
        guard let cc else { return }
        let address = cc.typed
        let parts = address.components(separatedBy: "@")
        let options = L.suggestDomains(address)
        guard parts.count == 2, index < options.count, !picking else { return }
        let label = options[index]
        let rest = String(label.dropFirst(parts[1].count))
        guard !rest.isEmpty else { return }
        picking = true
        uStretch.set(L.textWidth(Self.ccFont, rest) + 8)
        cc.offer(rest, quiet: true)
        cc.accept(velocity: 0, fresh: true)
        uPick = index
        uTake.set(0)
        uTake.to(1, reduced ? 0 : M.pickS, now: clockNow)
        let wait = (reduced ? 0 : M.pickS) + 0.06
        DispatchQueue.main.asyncAfter(deadline: .now() + wait) { [weak self] in
            guard let self else { return }
            self.picking = false
            self.chipCount = 0
            self.chips = []
            self.uShow.set(0)
            self.uPick = -1
            self.uTake.set(0)
        }
    }

    func onSend() {
        enterUser()
        dismissKeyboard()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { [weak self] in
            guard let self else { return }
            self.lastTouch = self.clockNow - M.idle
        }
    }

    func dismissKeyboard() {
        ccInput?.resignFirstResponder()
        subjectInput?.resignFirstResponder()
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}
