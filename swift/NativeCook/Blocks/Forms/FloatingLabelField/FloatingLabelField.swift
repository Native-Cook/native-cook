import SwiftUI
import UIKit

/*
 Floating Label Field: the sign-up of Larch, a trail journal.

 The loop types the form without focusing anything (no keyboard): the name, an address with its
 dot missing, which fails on Return (one shake, red outline, a helper line), is fixed and passes,
 then a password whose strength bar fills segment by segment. With all three valid the button
 comes alive under a gleam and is pressed. A real tap focuses the real field, and the same
 animations run from what is typed.
*/
struct FloatingLabelField: View {
    static let loop: Double = FloatingLabelFieldMotion.loop

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model = FloatingLabelFieldModel()

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    var body: some View {
        GeometryReader { outer in
            let top = outer.safeAreaInsets.top
            let bottom = outer.safeAreaInsets.bottom
            GeometryReader { inner in
                let geo = FloatingLabelFieldGeo(W: inner.size.width, H: inner.size.height, top: top, bottom: bottom)
                TimelineView(.animation(minimumInterval: nil, paused: model.isPaused)) { timeline in
                    screen(geo, now: timeline.date.timeIntervalSinceReferenceDate)
                }
                .onAppear { model.geo = geo }
                .onChange(of: inner.size) { _, _ in model.geo = geo }
            }
            .ignoresSafeArea()
        }
        // the keyboard is handled by `lift`; without this its height joins the bottom inset and the
        // footer jumps up between the fields
        .ignoresSafeArea(.keyboard)
        .background(FloatingLabelFieldArt.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { model.start(autoPlay: autoPlay, reduced: reduceMotion) }
        .onDisappear { model.stop() }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            guard let end = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
            let screen = UIScreen.main.bounds.height
            model.keyboardChanged(max(0, screen - end.minY))
        }
    }

    private func screen(_ g: FloatingLabelFieldGeo, now: Double) -> some View {
        let t = model.loopTime(now)
        let poses = (0..<3).map { model.pose($0, now: now, t: t) }
        let button = model.buttonPose(now: now, t: t)
        let dot = model.dotPose(t: t)
        let live = model.mode != 0

        return ZStack(alignment: .topLeading) {
            FloatingLabelFieldArt.page
                .contentShape(Rectangle())
                .onTapGesture { model.dismissKeyboard() }

            ZStack(alignment: .topLeading) {
                FloatingLabelFieldStatic(g: g).equatable()

                ForEach(0..<3, id: \.self) { i in
                    FloatingLabelFieldCell(
                        index: i,
                        label: FloatingLabelFieldModel.labels[i],
                        pose: poses[i],
                        width: g.width,
                        height: g.fieldH,
                        helper: model.helpers[i],
                        live: live,
                        model: model
                    )
                    .offset(x: g.margin, y: g.fieldsTop + CGFloat(i) * g.pitch)
                }

                FloatingLabelFieldButton(pose: button, width: g.width, height: g.buttonH, welcome: model.welcome, model: model)
                    .offset(x: g.margin, y: g.buttonTop)
            }
            .offset(y: -model.lift.value(now))

            finger(dot, g)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .clipped()
    }

    private func finger(_ p: FloatingLabelFieldMotion.DotPose, _ g: FloatingLabelFieldGeo) -> some View {
        let targets: [CGPoint] = [
            CGPoint(x: g.margin + g.width * 0.64, y: g.fieldsTop + g.fieldH / 2),
            CGPoint(x: g.margin + g.width * 0.64, y: g.fieldsTop + g.pitch + g.fieldH / 2),
            CGPoint(x: g.margin + g.width * 0.64, y: g.fieldsTop + g.pitch * 2 + g.fieldH / 2),
            CGPoint(x: g.W / 2 + 34, y: g.buttonTop + g.buttonH / 2 + 2),
        ]
        let at = targets[p.target]
        let ink = Color(.sRGB, red: 30 / 255, green: 42 / 255, blue: 36 / 255)
        return Circle()
            .fill(ink.opacity(0.16))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1))
            .shadow(color: ink.opacity(0.14), radius: 6, y: 4)
            .frame(width: 44, height: 44)
            .scaleEffect(p.scale)
            .offset(x: at.x - 22, y: at.y - 22 + (1 - p.alpha) * 8)
            .opacity(p.alpha)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Geometry

struct FloatingLabelFieldGeo: Equatable {
    let W: CGFloat, H: CGFloat
    let margin: CGFloat, width: CGFloat, artH: CGFloat, markY: CGFloat
    let titleTop: CGFloat, titleSize: CGFloat, subTop: CGFloat
    let fieldH: CGFloat, pitch: CGFloat, slot: CGFloat, fieldsTop: CGFloat
    let buttonTop: CGFloat, buttonH: CGFloat, legalTop: CGFloat, footerTop: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        let compact = H < 740
        self.W = W
        self.H = H
        margin = max(24, (W - 420) / 2)
        width = W - margin * 2
        artH = top + (compact ? 92 : 209)
        markY = top + (compact ? 8 : 14)
        titleTop = artH + (compact ? 8 : 18)
        titleSize = compact ? 26 : 30
        subTop = titleTop + (compact ? 34 : 42)
        fieldH = compact ? 52 : 62
        slot = compact ? 24 : 30
        pitch = fieldH + slot
        fieldsTop = subTop + (compact ? 36 : 47)
        buttonTop = fieldsTop + pitch * 3 + (compact ? 2 : 8)
        buttonH = compact ? 50 : 56
        legalTop = buttonTop + buttonH + 12
        footerTop = H - bottom - (compact ? 36 : 46)
    }
}

// MARK: - The page around the form

private struct FloatingLabelFieldStatic: View, Equatable {
    let g: FloatingLabelFieldGeo

    private let ink = FloatingLabelFieldCell.ink.color
    private let muted = FloatingLabelFieldCell.muted.color
    private let accent = FloatingLabelFieldCell.accent.color

    static func == (a: Self, b: Self) -> Bool { a.g == b.g }

    var body: some View {
        ZStack(alignment: .topLeading) {
            FloatingLabelFieldArt(width: g.W, height: g.artH, markX: g.margin, markY: g.markY).equatable()

            Text("Larch")
                .font(.system(size: 17, weight: .bold))
                .kerning(-0.2)
                .foregroundStyle(ink)
                .frame(height: 22)
                .offset(x: g.margin + 34, y: g.markY + 2)

            Text("Create your account")
                .font(.system(size: g.titleSize, weight: .bold))
                .kerning(-0.6)
                .foregroundStyle(ink)
                .frame(width: g.width, height: g.titleSize + 6, alignment: .leading)
                .offset(x: g.margin, y: g.titleTop)

            Text("Trail logs, maps and gear lists in one place.")
                .font(.system(size: 15))
                .foregroundStyle(muted)
                .lineLimit(1)
                .frame(width: g.width, height: 20, alignment: .leading)
                .offset(x: g.margin, y: g.subTop)

            legal
                .font(.system(size: 12.5))
                .multilineTextAlignment(.center)
                .lineSpacing(2)
                .frame(width: g.width, alignment: .top)
                .offset(x: g.margin, y: g.legalTop + 1)

            footer
                .font(.system(size: 15))
                .frame(width: g.W, height: 20)
                .offset(y: g.footerTop)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private var legal: Text {
        Text("By continuing you agree to the ").foregroundColor(muted)
            + Text("Terms").foregroundColor(ink).fontWeight(.medium)
            + Text(" and ").foregroundColor(muted)
            + Text("Privacy Policy").foregroundColor(ink).fontWeight(.medium)
            + Text(".").foregroundColor(muted)
    }

    private var footer: Text {
        Text("Already have an account? ").foregroundColor(muted)
            + Text("Sign in").foregroundColor(accent).fontWeight(.semibold)
    }
}

// MARK: - The button

private struct FloatingLabelFieldButtonStyle: ButtonStyle {
    let onPressed: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, down in onPressed(down) }
    }
}

private struct FloatingLabelFieldButton: View {
    typealias RGB = FloatingLabelFieldRGB

    let pose: FloatingLabelFieldMotion.ButtonPose
    let width: CGFloat
    let height: CGFloat
    let welcome: String
    let model: FloatingLabelFieldModel

    private let gleamW: CGFloat = 90
    private static let light = RGB(0xF6F3EA)
    private static let dim = RGB(0xA7A698)

    var body: some View {
        let p = pose
        let shape = RoundedRectangle(cornerRadius: 16, style: .circular)
        let shadow = Color(.sRGB, red: 20 / 255, green: 60 / 255, blue: 44 / 255)
        let travel = -gleamW + (width + gleamW) * CGFloat(FloatingLabelFieldMotion.easeInOutCubic(p.gleam))
        let skew = tan(-20 * CGFloat.pi / 180)
        let gleamH = height + 20

        return Button { model.onCreate() } label: {
            ZStack {
                shape
                    .fill(Color(.sRGB, red: 0x1F / 255, green: 0x5C / 255, blue: 0x45 / 255))
                    .shadow(color: shadow.opacity(0.26), radius: 11, y: 10)
                    .shadow(color: shadow.opacity(0.35), radius: 1, y: 1)
                    .opacity(p.ready)

                ZStack(alignment: .topLeading) {
                    Color(.sRGB, red: 0xE4 / 255, green: 0xDE / 255, blue: 0xCF / 255)

                    ZStack(alignment: .top) {
                        LinearGradient(
                            stops: [
                                .init(color: Color(.sRGB, red: 0x2C / 255, green: 0x72 / 255, blue: 0x57 / 255), location: 0),
                                .init(color: Color(.sRGB, red: 0x1F / 255, green: 0x5C / 255, blue: 0x45 / 255), location: 0.55),
                                .init(color: Color(.sRGB, red: 0x18 / 255, green: 0x4B / 255, blue: 0x38 / 255), location: 1),
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        Color.white.opacity(0.22).frame(height: 1)
                    }
                    .opacity(p.ready)

                    LinearGradient(
                        colors: [.white.opacity(0), .white.opacity(0.4), .white.opacity(0)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: gleamW, height: gleamH)
                    .transformEffect(CGAffineTransform(a: 1, b: 0, c: skew, d: 1, tx: -skew * gleamH / 2, ty: 0))
                    .offset(x: travel, y: -10)
                    .opacity(sin(.pi * p.gleam) * p.ready)

                    Text("Create account")
                        .font(.system(size: 17, weight: .semibold))
                        .kerning(-0.2)
                        .foregroundStyle(Self.dim.mix(Self.light, p.ready).color)
                        .frame(width: width, height: height)
                        .offset(y: -8 * p.done)
                        .opacity(1 - p.done)

                    HStack(spacing: 7) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .regular))
                        Text("Welcome, \(welcome)")
                            .font(.system(size: 17, weight: .semibold))
                            .kerning(-0.2)
                            .lineLimit(1)
                    }
                    .foregroundStyle(Self.light.color)
                    .frame(width: width, height: height)
                    .offset(y: 8 * (1 - p.done))
                    .opacity(p.done)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .clipShape(shape)
            }
            .frame(width: width, height: height)
            .contentShape(shape)
            .scaleEffect(1 - 0.03 * p.press)
        }
        .buttonStyle(FloatingLabelFieldButtonStyle { model.pressButton($0) })
        .accessibilityLabel("Create account")
    }
}

// MARK: - The hand-over between the loop and a person

struct FloatingLabelFieldTweens {
    typealias Tw = FloatingLabelFieldTween
    var focus = Tw(), float = Tw(), lineStart = Tw(), lineEnd = Tw(), valid = Tw(), error = Tw()
    var shake = Tw(), count = Tw(), strength = Tw(), text = Tw(1), caretFade = Tw()
    /// When set, the drawn caret of a secure field blinks from this moment.
    var blinkFrom: Double?
}

@MainActor
final class FloatingLabelFieldModel: ObservableObject {
    typealias M = FloatingLabelFieldMotion
    typealias Tw = FloatingLabelFieldTween

    static let labels = ["Full name", "Email", "Password"]
    private static let defaultHelpers = [
        M.fieldMessage(0, ""), M.fieldMessage(1, M.emailTypo), M.fieldMessage(2, "x"),
    ]

    /// 0 = the loop (or rest), 1 = a person drives, 2 = easing back to the empty form.
    @Published private(set) var mode = 0
    @Published private(set) var helpers = FloatingLabelFieldModel.defaultHelpers
    @Published private(set) var welcome = "Mara"
    @Published private(set) var isPaused = true

    var geo: FloatingLabelFieldGeo?
    var inputs: [UITextField?] = [nil, nil, nil]
    private(set) var lift = Tw()

    private var u = [FloatingLabelFieldTweens(), FloatingLabelFieldTweens(), FloatingLabelFieldTweens()]
    private var uReady = Tw(), uGleam = Tw(), uPress = Tw(), uDone = Tw()
    private var values = ["", "", ""]
    private var focusIndex = -1
    private var level = 0
    private var ready = false
    private var keyboard: CGFloat = 0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var loopStart = 0.0
    private var auto = false
    private var still = false
    private var reduced = false
    private var watcher: Task<Void, Never>?

    private var clockNow: Double { Date().timeIntervalSinceReferenceDate }

    private static let stops: [[CGFloat]] = (0..<2).map { field in
        let font = UIFont.systemFont(ofSize: 17)
        let bar = ("|" as NSString).size(withAttributes: [.font: font]).width
        return M.values[field].map { value in
            let w = ((value + "|") as NSString).size(withAttributes: [.font: font]).width
            return max(0, w - bar) + 1
        }
    }

    static func caretStop(_ field: Int, _ frame: Int) -> CGFloat {
        guard field < 2, frame >= 0, frame < stops[field].count else { return -1 }
        return stops[field][frame]
    }

    // MARK: Lifecycle

    func start(autoPlay: Bool, reduced: Bool) {
        self.reduced = reduced
        auto = autoPlay && !reduced
        still = autoPlay && reduced
        loopStart = clockNow + M.startDelay
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

    func pose(_ i: Int, now: Double, t: Double) -> M.FieldPose {
        if mode == 0 {
            var p = M.autoField(i, t)
            if still { p.caret = 0 }
            return p
        }
        let v = u[i]
        var p = M.FieldPose()
        p.focus = v.focus.value(now)
        p.float = v.float.value(now)
        p.lineStart = v.lineStart.value(now)
        p.lineEnd = v.lineEnd.value(now)
        p.valid = v.valid.value(now)
        p.error = v.error.value(now)
        p.shake = v.shake.value(now)
        p.frame = -1
        p.count = v.count.value(now)
        p.strength = v.strength.value(now)
        p.text = v.text.value(now)
        if let from = v.blinkFrom {
            let phase = (now - from).truncatingRemainder(dividingBy: 1.14)
            if phase < 0.5 { p.caret = 1 }
            else if phase < 0.64 { p.caret = 1 - (phase - 0.5) / 0.14 }
            else if phase < 1.0 { p.caret = 0 }
            else { p.caret = (phase - 1.0) / 0.14 }
        } else {
            p.caret = v.caretFade.value(now)
        }
        return p
    }

    func buttonPose(now: Double, t: Double) -> M.ButtonPose {
        if mode == 0 { return M.autoButton(t) }
        return M.ButtonPose(ready: uReady.value(now), gleam: uGleam.value(now), press: uPress.value(now), done: uDone.value(now))
    }

    func dotPose(t: Double) -> M.DotPose {
        mode == 0 && auto ? M.autoDot(t) : M.DotPose()
    }

    // MARK: Field motion

    private func ms(_ duration: Double) -> Double { reduced ? 0 : duration }

    private func drawOutline(_ i: Int, _ now: Double) {
        let start = u[i].lineStart.value(now)
        let end = u[i].lineEnd.value(now)
        if end - start > 0.98 { return }
        if start >= end - 0.001 {
            u[i].lineStart.set(0)
            u[i].lineEnd.set(0)
        } else {
            u[i].lineStart.to(0, ms(0.3), .outCubic, now: now)
        }
        u[i].lineEnd.to(1, ms(M.drawS), .inOutCubic, now: now)
    }

    private func focusMotion(_ i: Int, _ now: Double) {
        u[i].focus.to(1, ms(M.focusS), .outCubic, now: now)
        u[i].float.to(1, ms(M.floatS), .outCubic, now: now)
        drawOutline(i, now)
        if i == 2 {
            u[i].blinkFrom = reduced ? nil : now
            if reduced { u[i].caretFade.set(1) }
        }
    }

    private func failMotion(_ i: Int, _ now: Double) {
        u[i].valid.to(0, ms(0.15), now: now)
        u[i].error.to(1, ms(M.errorS), .outCubic, now: now)
        if !reduced {
            u[i].shake.set(0)
            u[i].shake.to(1, M.shakeS, .linear, now: now)
        }
        drawOutline(i, now)
    }

    private func blurMotion(_ i: Int, empty: Bool, ok: Bool, _ now: Double) {
        u[i].focus.to(0, ms(M.focusS), now: now)
        if u[i].blinkFrom != nil { u[i].caretFade.set(1) }
        u[i].blinkFrom = nil
        u[i].caretFade.to(0, ms(0.1), now: now)
        if empty {
            u[i].float.to(0, ms(M.floatS), .inOutCubic, now: now)
            u[i].valid.to(0, ms(0.15), now: now)
            if u[i].error.value(now) < 0.5 {
                u[i].lineStart.to(1, ms(M.retractS), .inOutCubic, now: now)
            }
            return
        }
        if ok {
            u[i].valid.to(1, ms(M.checkS), .outCubic, now: now)
            u[i].lineStart.to(1, ms(M.retractS), .inOutCubic, now: now)
            return
        }
        failMotion(i, now)
    }

    private func restField(_ i: Int, _ d: Double, _ now: Double) {
        u[i].focus.to(0, d * 0.4, now: now)
        u[i].blinkFrom = nil
        u[i].caretFade.set(0)
        u[i].text.to(0, d * 0.45, now: now)
        u[i].valid.to(0, d * 0.45, now: now)
        u[i].error.to(0, d * 0.45, now: now)
        u[i].strength.to(0, d * 0.6, now: now)
        u[i].lineStart.to(1, d * 0.8, now: now)
        u[i].float.to(0, d * 0.65, .inOutCubic, now: now, delay: d * 0.35)
    }

    // MARK: Taking over

    private func enterUser(keep: Int) {
        let now = clockNow
        if mode == 1 { return }
        if mode == 0 {
            let t = still ? M.tFilled : (auto ? loopTime(now) : 0)
            for j in 0..<3 {
                values[j] = M.values[j][M.frameAt(j, t)]
                inputs[j]?.text = values[j]
            }
            level = M.passwordLevel(values[2])
            ready = allOk
            for j in 0..<3 {
                let p = M.autoField(j, t)
                u[j].focus.set(p.focus)
                u[j].float.set(p.float)
                u[j].lineStart.set(p.lineStart)
                u[j].lineEnd.set(p.lineEnd)
                u[j].valid.set(p.valid)
                u[j].error.set(p.error)
                u[j].shake.set(0)
                u[j].count.set(p.count)
                u[j].caretFade.set(0)
                u[j].blinkFrom = nil
                u[j].strength.set(p.strength)
                u[j].text.set(1)
                if p.focus > 0.5 && j != keep {
                    u[j].focus.to(0, M.focusS, now: now)
                    if p.error < 0.5 { u[j].lineStart.to(1, M.retractS, now: now) }
                    if p.frame == 0 { u[j].float.to(0, M.floatS, now: now) }
                }
            }
            let b = M.autoButton(t)
            uReady.set(b.ready)
            uReady.to(ready ? 1 : 0, M.readyS, now: now)
            uGleam.set(b.gleam)
            uPress.set(0)
            uDone.set(b.done)
            uDone.to(0, 0.2, now: now)
        } else {
            for j in 0..<3 {
                u[j].text.to(1, 0.15, now: now)
                u[j].float.to(values[j].isEmpty ? 0 : 1, M.floatS, now: now)
                u[j].valid.to(M.fieldOk(j, values[j]) ? 1 : 0, 0.2, now: now)
            }
            u[2].count.set(Double(values[2].utf16.count))
            u[2].strength.to(Double(level), 0.2, now: now)
            ready = allOk
            uReady.to(ready ? 1 : 0, M.readyS, now: now)
        }
        mode = 1
        isPaused = false
        lastTouch = now
        startWatching()
    }

    private var allOk: Bool {
        (0..<3).allSatisfy { M.fieldOk($0, values[$0]) }
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
            if focusIndex < 0 && now - lastTouch > M.idle {
                mode = 2
                returnAt = now
                let d = reduced ? 0 : M.returnLen * 0.9
                for i in 0..<3 { restField(i, d, now) }
                uReady.to(0, d * 0.6, now: now)
                uDone.to(0, d * 0.4, now: now)
                uPress.set(0)
            }
            return
        }
        if now - returnAt > M.returnLen {
            for i in 0..<3 {
                u[i] = FloatingLabelFieldTweens()
                inputs[i]?.text = ""
            }
            uReady.set(0)
            uGleam.set(0)
            uPress.set(0)
            uDone.set(0)
            values = ["", "", ""]
            level = 0
            ready = false
            focusIndex = -1
            helpers = Self.defaultHelpers
            welcome = "Mara"
            loopStart = now
            mode = 0
            isPaused = !auto
        }
    }

    private func touch() { lastTouch = clockNow }

    // MARK: Events from the fields

    private func showHelper(_ i: Int) {
        let message = M.fieldMessage(i, values[i])
        if helpers[i] != message { helpers[i] = message }
    }

    private func syncReady(_ now: Double) {
        let ok = allOk
        if ok == ready { return }
        ready = ok
        uReady.to(ok ? 1 : 0, ms(M.readyS), .inOutCubic, now: now)
        if ok && !reduced {
            uGleam.set(0)
            uGleam.to(1, M.gleamS, .linear, now: now, delay: 0.25)
        }
    }

    private func fail(_ i: Int) {
        showHelper(i)
        failMotion(i, clockNow)
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }

    func onFocus(_ i: Int) {
        enterUser(keep: i)
        focusIndex = i
        touch()
        focusMotion(i, clockNow)
        raise(i)
    }

    func onBlur(_ i: Int) {
        if focusIndex == i { focusIndex = -1 }
        touch()
        let v = values[i]
        let ok = M.fieldOk(i, v)
        if !v.isEmpty && !ok {
            showHelper(i)
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
        blurMotion(i, empty: v.isEmpty, ok: ok, clockNow)
    }

    func onChange(_ i: Int, _ v: String) {
        values[i] = v
        touch()
        let now = clockNow
        if u[i].error.value(now) > 0 { u[i].error.to(0, ms(M.errorClearS), now: now) }
        let ok: Bool
        if i == 2 {
            let next = M.passwordLevel(v)
            if next > level { UISelectionFeedbackGenerator().selectionChanged() }
            level = next
            ok = next >= 3
            u[2].count.to(Double(v.utf16.count), ms(M.dotS), .outCubic, now: now)
            u[2].strength.to(Double(next), ms(M.strengthS), .outCubic, now: now)
            if !reduced { u[2].blinkFrom = now }
        } else {
            ok = M.fieldOk(i, v)
        }
        u[i].valid.to(ok ? 1 : 0, ms(ok ? M.checkS : 0.15), .outCubic, now: now)
        syncReady(now)
    }

    func onSubmit(_ i: Int) {
        touch()
        if !M.fieldOk(i, values[i]) {
            fail(i)
            return
        }
        if i < 2 { inputs[i + 1]?.becomeFirstResponder() } else { inputs[i]?.resignFirstResponder() }
    }

    func onCreate() {
        enterUser(keep: -1)
        touch()
        let wrong = (0..<3).filter { !M.fieldOk($0, values[$0]) }
        if !wrong.isEmpty {
            wrong.forEach(fail)
            return
        }
        dismissKeyboard()
        let first = values[0].split(whereSeparator: { $0.isWhitespace }).first.map(String.init)
        welcome = first ?? "Mara"
        uDone.to(1, ms(0.35), .outCubic, now: clockNow)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    func pressButton(_ down: Bool) {
        if mode == 0 { return }
        uPress.to(down ? 1 : 0, down ? 0.1 : 0.18, now: clockNow)
    }

    func dismissKeyboard() {
        inputs.forEach { $0?.resignFirstResponder() }
    }

    func keyboardChanged(_ height: CGFloat) {
        keyboard = height
        raise(height > 0 ? focusIndex : -1)
    }

    private func raise(_ i: Int) {
        guard let g = geo else { return }
        var need: CGFloat = 0
        if i >= 0 && keyboard > 0 {
            need = g.fieldsTop + CGFloat(i) * g.pitch + g.fieldH + g.slot + 16 - (g.H - keyboard)
        }
        lift.to(Double(max(0, need)), ms(0.28), .outCubic, now: clockNow)
    }
}
