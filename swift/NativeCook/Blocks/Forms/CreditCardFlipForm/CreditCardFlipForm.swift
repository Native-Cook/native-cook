import SwiftUI
import UIKit

/// A payment form under a live preview of the card. Digits roll onto the card like slot reels as
/// the number is typed, the card's colours spread out from its mark once the first digit tells the
/// network, the name and expiry fill in under a frame of light that glides to whichever part is
/// being edited (a ring on the form glides with it), and the CVV turns the card over. The card
/// tilts under a finger and its light slides across the brushed surface. Pay folds the button into
/// a spinner, then a check.
struct CreditCardFlipForm: View {
    /// Types a whole card, turns it over for the CVV, pays and starts over.
    var autoPlay = false
    /// The total on the pay button.
    var amount = "€248.00"
    /// The bank printed on the card.
    var issuer = "Halcyon"
    var onPay: ((_ number: String, _ name: String, _ expiry: String, _ cvv: String) -> Void)? = nil

    init(
        autoPlay: Bool = false,
        amount: String = "€248.00",
        issuer: String = "Halcyon",
        onPay: ((_ number: String, _ name: String, _ expiry: String, _ cvv: String) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.amount = amount
        self.issuer = issuer
        self.onPay = onPay
        _playing = State(initialValue: autoPlay)
    }

    typealias Field = CreditCardFlipFormField
    fileprivate typealias M = CreditCardFlipFormMetrics
    private typealias C = CreditCardFlipFormCardMetrics

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var number = ""
    @State private var name = ""
    @State private var expiry = ""
    @State private var cvv = ""
    @State private var save = true
    @State private var field: Field?
    @State private var stage = CreditCardFlipFormStage.idle
    @State private var playing: Bool
    @State private var scriptPress = false
    @State private var held = false
    @State private var byUserPay = false
    // Which real field should hold the keyboard (nil: none), and a count that makes each ask new.
    @State private var keyboardRequest: (field: Field?, id: Int) = (nil, 0)
    @State private var blurTask: Task<Void, Never>?
    @State private var keyboard = 0.0
    @State private var engine = CreditCardFlipFormEngine()
    @State private var live = true
    @State private var wake = 0
    @State private var layout: CreditCardFlipFormLayout?
    // The name and the expiry on the card, measured so the outline hugs them.
    @State private var nameBox = CGSize(width: 96, height: 34)
    @State private var expiryBox = CGSize(width: 52, height: 34)
    @State private var tilting = false

    private var network: CreditCardFlipFormNetwork { .of(number) }
    private var expired: Bool { creditCardFlipFormIsExpired(expiry) }
    private func done(_ target: Field) -> Bool {
        switch target {
        case .number: return number.count == 16
        case .name: return name.trimmingCharacters(in: .whitespaces).count > 1
        case .expiry: return expiry.count == 4 && !expired
        case .cvv: return cvv.count == 3
        }
    }
    private var valid: Bool { done(.number) && done(.name) && done(.expiry) && done(.cvv) }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let l = CreditCardFlipFormLayout(size: size, insets: insets, nameBox: nameBox, expiryBox: expiryBox)
            screen(l)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
                .onAppear { setUp(l) }
                .onChange(of: l) { _, new in layout = new }
        }
        .ignoresSafeArea(.keyboard)
        .environment(\.colorScheme, .dark)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            guard let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
            let height = max(0, (layout?.size.height ?? frame.maxY) - frame.minY)
            let duration = note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25
            withAnimation(.easeOut(duration: max(duration, 0.01))) { keyboard = height }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { note in
            let duration = note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25
            withAnimation(.easeOut(duration: max(duration, 0.01))) { keyboard = 0 }
        }
        .onChange(of: number) { old, new in
            engine.showNumber(new, at: .now, still: reduceMotion)
            engine.turnTo(.of(new), at: .now, still: reduceMotion)
            wakeUp()
        }
        .onChange(of: name) { _, new in
            engine.showName(new, at: .now)
            wakeUp()
        }
        .onChange(of: expiry) { _, new in
            engine.showExpiry(new, at: .now, still: reduceMotion)
            wakeUp()
        }
        .onChange(of: cvv) { _, new in
            engine.showCvv(new, at: .now, still: reduceMotion)
            wakeUp()
        }
        .onChange(of: field) { _, new in
            if let layout { engine.focus(new, rects: layout.outlines, fields: layout.fields, at: .now, still: reduceMotion) }
            wakeUp()
        }
        .onChange(of: layout) { _, new in
            if let new { engine.reframe(field, rects: new.outlines, at: .now, still: reduceMotion) }
            wakeUp()
        }
        .onChange(of: valid, initial: true) { _, new in
            engine.setReady(new, at: .now)
            wakeUp()
        }
        .onChange(of: stage) { _, new in
            engine.pay(new, at: .now)
            wakeUp()
            run(stage: new)
        }
        .task(id: playing) { await play() }
        .task(id: ResumeKey(playing: playing, field: field, stage: stage, text: number + "|" + name + "|" + expiry + "|" + cvv + "|\(save)")) { await resume() }
        .task(id: wake) {
            // The timeline ticks while something moves, then rests.
            while !Task.isCancelled {
                let now = Date.now
                if !engine.moving(at: now) {
                    live = false
                    return
                }
                let wait = max(0.05, min(engine.settledAt.timeIntervalSince(now), 1))
                try? await Task.sleep(for: .seconds(wait))
            }
        }
    }

    private struct ResumeKey: Equatable {
        let playing: Bool
        let field: Field?
        let stage: CreditCardFlipFormStage
        let text: String
    }

    private func setUp(_ l: CreditCardFlipFormLayout) {
        layout = l
        engine.placeOutline(l.outlines[.number] ?? .zero)
    }

    private func wakeUp() {
        live = true
        wake += 1
    }

    // MARK: Stage

    private func screen(_ l: CreditCardFlipFormLayout) -> some View {
        let rise = keyboard > 0 ? field.map { max(0, l.fieldBottom($0) + 16 - (l.size.height - keyboard)) } ?? 0 : 0
        // The big header fades out before it rises into the top inset, and the compact bar takes its place.
        let k = min(1, max(0, rise / M.headerRoom))
        let collapse = k * k * (3 - 2 * k)
        return ZStack(alignment: .topLeading) {
            M.background
                .contentShape(Rectangle())
                .onTapGesture { requestKeyboard(nil) }

            ZStack(alignment: .topLeading) {
                header(l)
                    .opacity(1 - collapse)
                TimelineView(.animation(minimumInterval: nil, paused: !live)) { timeline in
                    let now = timeline.date
                    let _ = engine.prune(at: now)
                    ZStack(alignment: .topLeading) {
                        shadow(l, now: now)
                        card(l, now: now)
                    }
                }
                form(l)
                    .offset(x: M.side, y: l.formTop)
                saveRow(l)
                    .offset(x: M.side, y: l.formTop + l.formH + M.saveGap)
            }
            .offset(y: -rise)

            compactBar(l, collapse: collapse)

            HStack(spacing: 6) {
                CreditCardFlipFormSymbol(name: "lock.fill", size: 11, color: M.muted)
                Text("Encrypted end to end · never stored on this device")
                    .font(.system(size: 12))
                    .foregroundStyle(M.muted)
            }
            .frame(width: l.size.width)
            .offset(y: l.payTop - 34)
            .allowsHitTesting(false)

            TimelineView(.animation(minimumInterval: nil, paused: !live)) { timeline in
                payButton(l, now: timeline.date)
            }
            .offset(x: M.side, y: l.payTop)
        }
        .frame(width: l.size.width, height: l.size.height, alignment: .topLeading)
        .clipped()
    }

    /// The compact bar: the page slides under it while the keyboard is up.
    private func compactBar(_ l: CreditCardFlipFormLayout, collapse: Double) -> some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: l.insets.top)
            HStack(alignment: .firstTextBaseline) {
                Text("Card details")
                    .font(.system(size: 17, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(M.text)
                Spacer()
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("Total")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(M.muted)
                    Text(amount)
                        .font(.system(size: 15, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.text)
                }
            }
            .padding(.horizontal, M.side)
            .frame(width: l.size.width, height: M.barH)
            .offset(y: reduceMotion ? 0 : 6 * (1 - collapse))
        }
        .frame(width: l.size.width, height: l.insets.top + M.barH, alignment: .top)
        .background(M.background)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.white.opacity(0.08)).frame(height: 0.5)
        }
        .opacity(collapse)
        .allowsHitTesting(false)
        .accessibilityHidden(collapse < 0.5)
    }

    private func header(_ l: CreditCardFlipFormLayout) -> some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                Text("CHECKOUT · STEP 2 OF 2")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.6)
                    .foregroundStyle(M.muted)
                Text("Card details")
                    .font(.system(size: 30, weight: .bold))
                    .tracking(-0.6)
                    .foregroundStyle(M.text)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("Total")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(M.muted)
                Text(amount)
                    .font(.system(size: 17, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(M.text)
            }
            .padding(.bottom, 3)
        }
        .frame(width: l.size.width - M.side * 2)
        .offset(x: M.side, y: l.insets.top + 14)
        .allowsHitTesting(false)
    }

    /// The shadow on the "table" under the card: a dark contact shadow and a wider one tinted with
    /// the card's own light colour, both sliding a little against the tilt.
    private func shadow(_ l: CreditCardFlipFormLayout, now: Date) -> some View {
        let tint = creditCardFlipFormMix(engine.fromNetwork.palette[2], engine.toNetwork.palette[2], engine.mix.value(at: now))
        let tilt = engine.tilt(at: now)
        let left = (l.size.width - l.cardW) / 2 - 40
        let top = l.cardTop - 20
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: C.radius)
                .fill(tint)
                .frame(width: l.cardW - 48, height: l.cardH - 10)
                .blur(radius: 28)
                .opacity(0.3)
                .offset(x: 64, y: 44)
            RoundedRectangle(cornerRadius: C.radius)
                .fill(Color.black.opacity(0.7))
                .frame(width: l.cardW - 24, height: l.cardH - 8)
                .blur(radius: 12)
                .offset(x: 52, y: 34)
        }
        .offset(x: left - tilt.y * 1.2, y: top + tilt.x * 0.8)
        .allowsHitTesting(false)
    }

    // MARK: Card

    private func card(_ l: CreditCardFlipFormLayout, now: Date) -> some View {
        let still = reduceMotion
        let f = engine.turn(at: now, still: still)
        let up = still ? 0 : sin(min(1, max(0, f)) * .pi)
        let size = CGSize(width: l.cardW, height: l.cardH)
        let tilt = engine.tilt(at: now)
        return ZStack(alignment: .topLeading) {
            front(l, now: now, tilt: tilt)
                .clipShape(RoundedRectangle(cornerRadius: C.radius, style: .continuous))
                .modifier(CreditCardFlipFormTurn(degrees: still ? 0 : f * 180, size: size))
                .opacity(still ? 1 - min(1, max(0, f)) : (f < 0.5 ? 1 : 0))
            back(l, now: now, tilt: tilt)
                .clipShape(RoundedRectangle(cornerRadius: C.radius, style: .continuous))
                .modifier(CreditCardFlipFormTurn(degrees: still ? 0 : f * 180 - 180, size: size))
                .opacity(still ? min(1, max(0, f)) : (f >= 0.5 ? 1 : 0))
        }
        .frame(width: l.cardW, height: l.cardH)
        .modifier(CreditCardFlipFormTilt(x: tilt.x, y: tilt.y, size: size))
        .scaleEffect(1 + C.liftScale * up)
        .offset(y: -C.lift * up)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    let d = hypot(value.translation.width, value.translation.height)
                    if !tilting && d < 6 { return }
                    if !tilting {
                        tilting = true
                        takeOver()
                    }
                    guard !still else { return }
                    engine.hold(
                        tiltX: creditCardFlipFormSoft(-value.translation.height * C.tiltPerPt, C.tiltMaxX),
                        tiltY: creditCardFlipFormSoft(value.translation.width * C.tiltPerPt, C.tiltMaxY)
                    )
                    wakeUp()
                }
                .onEnded { value in
                    if tilting {
                        tilting = false
                        engine.letGo(speedX: -value.velocity.height * C.tiltPerPt, speedY: value.velocity.width * C.tiltPerPt, at: .now)
                        wakeUp()
                    } else {
                        cardTapped(value.location, l)
                    }
                }
        )
        .offset(x: (l.size.width - l.cardW) / 2, y: l.cardTop)
        .accessibilityElement()
        .accessibilityLabel("Card preview" + (network != .none ? ", \(network.rawValue)" : ""))
        .accessibilityAddTraits(.isButton)
    }

    private func front(_ l: CreditCardFlipFormLayout, now: Date, tilt: (x: Double, y: Double)) -> some View {
        let o = engine.outline.map { $0.value(at: now) }
        let art = CreditCardFlipFormArt(
            size: CGSize(width: l.cardW, height: l.cardH), back: false,
            from: engine.fromNetwork, to: engine.toNetwork,
            mix: engine.mix.value(at: now), shine: engine.shine.value(at: now),
            tiltX: tilt.x, tiltY: tilt.y, origin: l.markOrigin,
            outline: CreditCardFlipFormOutline(
                rect: CGRect(x: o[0], y: o[1], width: o[2], height: o[3]),
                on: engine.outlineOn.value(at: now),
                orbit: orbit(now)
            )
        )
        let numberFont = Font.system(size: 21, weight: .semibold)
        let smallFont = Font.system(size: 14, weight: .semibold)
        return ZStack(alignment: .topLeading) {
            art
            Text(issuer.uppercased())
                .font(.system(size: 12, weight: .bold))
                .tracking(3.2)
                .foregroundStyle(.white.opacity(0.92))
                .offset(x: C.pad, y: 24)
            marks(now: now)
                .frame(width: l.cardW - C.pad, height: 26, alignment: .trailing)
                .offset(y: 18)
            CreditCardFlipFormSymbol(name: "wave.3.right", size: 17, weight: .medium, color: .white.opacity(0.75))
                .offset(x: C.chip.maxX + 12, y: C.chip.minY + 6)
            HStack(spacing: C.groupGap) {
                ForEach(0..<4, id: \.self) { group in
                    HStack(spacing: 0) {
                        ForEach(0..<4, id: \.self) { k in
                            let reel = engine.numberReels[group * 4 + k]
                            CreditCardFlipFormReelView(
                                roll: reel.roll(at: now), speed: reel.speed(at: now), placeholder: "•",
                                width: l.slotW, line: C.numberLine, font: numberFont, color: .white, shadow: true
                            )
                        }
                    }
                }
            }
            .offset(x: C.pad, y: l.numberTop)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    cardLabel("CARD HOLDER")
                    nameLine(now: now)
                }
                .fixedSize()
                .onGeometryChange(for: CGSize.self) { $0.size } action: { new in
                    if abs(new.width - nameBox.width) >= 0.5 || abs(new.height - nameBox.height) >= 0.5 { nameBox = new }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 4) {
                    cardLabel("EXPIRES")
                    HStack(spacing: 0) {
                        ForEach(0..<2, id: \.self) { i in expiryReel(i, now: now, font: smallFont) }
                        Text("/")
                            .font(smallFont)
                            .foregroundStyle(.white.opacity(0.7))
                            .frame(height: C.smallLine)
                            .padding(.horizontal, 2)
                        ForEach(2..<4, id: \.self) { i in expiryReel(i, now: now, font: smallFont) }
                    }
                }
                .fixedSize()
                .onGeometryChange(for: CGSize.self) { $0.size } action: { new in
                    if abs(new.width - expiryBox.width) >= 0.5 || abs(new.height - expiryBox.height) >= 0.5 { expiryBox = new }
                }
            }
            .frame(width: l.cardW - C.pad * 2)
            .offset(x: C.pad, y: l.bottomTop)
        }
        .frame(width: l.cardW, height: l.cardH, alignment: .topLeading)
    }

    private func expiryReel(_ i: Int, now: Date, font: Font) -> some View {
        let reel = engine.expiryReels[i]
        return CreditCardFlipFormReelView(
            roll: reel.roll(at: now), speed: reel.speed(at: now), placeholder: i < 2 ? "M" : "Y",
            width: 10, line: C.smallLine, font: font, color: .white
        )
    }

    private func cardLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 8.5, weight: .bold))
            .tracking(1.4)
            .foregroundStyle(.white.opacity(0.6))
    }

    /// The mark of the network, popping in as the old one fades and grows away.
    private func marks(now: Date) -> some View {
        ZStack(alignment: .trailing) {
            ForEach(Array(engine.leavingMarks.enumerated()), id: \.offset) { _, leaving in
                let t = now.timeIntervalSince(leaving.at)
                CreditCardFlipFormMark(network: leaving.network)
                    .scaleEffect(1 + 0.15 * CreditCardFlipFormCurve.inOutQuad.value(at: min(max(t / 0.2, 0), 1)))
                    .opacity(1 - CreditCardFlipFormCurve.inOutQuad.value(at: min(max(t / 0.16, 0), 1)))
            }
            let t = now.timeIntervalSince(engine.mark.at)
            CreditCardFlipFormMark(network: engine.mark.network)
                .scaleEffect(engine.markScale.value(at: now))
                .offset(y: 6 * (1 - CreditCardFlipFormCurve.easeOut.value(at: min(max(t / 0.3, 0), 1))))
                .opacity(CreditCardFlipFormCurve.inOutQuad.value(at: min(max(t / 0.26, 0), 1)))
        }
    }

    /// The name on the card, letter by letter: each rises 9 pt and grows from 0.86 as it comes and
    /// fades up as it goes; "YOUR NAME" stands in while it is empty.
    private func nameLine(now: Date) -> some View {
        let glyph = Font.system(size: 14, weight: .semibold)
        func letter(_ c: Character) -> String { c == " " ? "\u{00a0}" : String(c) }
        func rise(_ at: Date) -> (Double, Double, Double) {
            let t = now.timeIntervalSince(at)
            let k = CreditCardFlipFormCurve.easeOut.value(at: min(max(t / 0.3, 0), 1))
            return (CreditCardFlipFormCurve.inOutQuad.value(at: min(max(t / 0.2, 0), 1)), 9 * (1 - k), 0.86 + 0.14 * k)
        }
        func leave(_ at: Date) -> (Double, Double) {
            let k = CreditCardFlipFormCurve.inOutQuad.value(at: min(max(now.timeIntervalSince(at) / 0.18, 0), 1))
            return (1 - k, -6 * k)
        }
        return ZStack(alignment: .topLeading) {
            if engine.name.isEmpty {
                let (o, y, s) = rise(engine.placeholderAt)
                Text("YOUR NAME").font(glyph).tracking(1).foregroundStyle(.white.opacity(0.4))
                    .frame(height: C.smallLine)
                    .scaleEffect(s).offset(y: y).opacity(o)
            } else if let left = engine.placeholderLeftAt, now.timeIntervalSince(left) < 0.2 {
                let (o, y) = leave(left)
                Text("YOUR NAME").font(glyph).tracking(1).foregroundStyle(.white.opacity(0.4))
                    .frame(height: C.smallLine)
                    .offset(y: y).opacity(o)
            }
            ForEach(Array(engine.ghosts.enumerated()), id: \.offset) { _, ghost in
                let (o, y) = leave(ghost.at)
                HStack(spacing: 0) {
                    ForEach(Array(ghost.text.enumerated()), id: \.offset) { i, c in
                        Text(letter(c)).font(glyph).tracking(1).foregroundStyle(.white)
                            .frame(height: C.smallLine)
                            .offset(y: ghost.leaving.contains(i) ? y : 0)
                            .opacity(ghost.leaving.contains(i) ? o : 0)
                    }
                }
            }
            HStack(spacing: 0) {
                ForEach(Array(engine.name.enumerated()), id: \.offset) { i, c in
                    let (o, y, s) = reduceMotion ? (1, 0, 1) : rise(engine.letterTimes[i])
                    Text(letter(c)).font(glyph).tracking(1).foregroundStyle(.white)
                        .frame(height: C.smallLine)
                        .scaleEffect(s).offset(y: y).opacity(o)
                }
            }
        }
        .frame(height: C.smallLine, alignment: .topLeading)
        .clipped()
    }

    private func back(_ l: CreditCardFlipFormLayout, now: Date, tilt: (x: Double, y: Double)) -> some View {
        let boxTop = C.strip.y + (C.strip.height - C.cvvBox.height) / 2
        let boxLeft = l.cardW - C.strip.x - 4 - C.cvvBox.width
        // The CVV's outline: the same frame of light, round the box, while the CVV is typed.
        let art = CreditCardFlipFormArt(
            size: CGSize(width: l.cardW, height: l.cardH), back: true,
            from: engine.fromNetwork, to: engine.toNetwork,
            mix: engine.mix.value(at: now), shine: engine.shine.value(at: now),
            tiltX: tilt.x, tiltY: tilt.y, origin: l.markOrigin,
            outline: CreditCardFlipFormOutline(
                rect: CGRect(x: boxLeft - 5, y: boxTop - 5, width: C.cvvBox.width + 10, height: C.cvvBox.height + 10),
                on: engine.ring.value(at: now),
                orbit: orbit(now),
                dark: true
            )
        )
        return ZStack(alignment: .topLeading) {
            art
            Text(name.isEmpty ? "" : creditCardFlipFormTitleCase(name))
                .font(.custom("Snell Roundhand", size: 19).weight(.semibold))
                .foregroundStyle(Color(hex: 0x2a2f45))
                .lineLimit(1)
                .frame(width: l.cardW - C.strip.x * 2 - C.cvvBox.width - 30, height: 28, alignment: .leading)
                .offset(x: C.strip.x + 12, y: C.strip.y + 4)
            Text("CVV")
                .font(.system(size: 9, weight: .bold))
                .tracking(1.4)
                .foregroundStyle(.white.opacity(0.75))
                .frame(width: l.cardW - C.strip.x - 4, alignment: .trailing)
                .offset(y: C.strip.y - 16)
            HStack(spacing: 0) {
                ForEach(0..<3, id: \.self) { i in
                    let reel = engine.cvvReels[i]
                    CreditCardFlipFormReelView(
                        roll: reel.roll(at: now), speed: reel.speed(at: now), placeholder: "•",
                        width: 13, line: 22, font: .system(size: 15, weight: .bold).italic(), color: Color(hex: 0x15161c)
                    )
                }
            }
            .frame(width: C.cvvBox.width, height: C.cvvBox.height)
            .background(RoundedRectangle(cornerRadius: 6).fill(.white))
            .offset(x: boxLeft, y: boxTop)
            Text("Authorised signature. This card stays the property of \(issuer) and is to be returned on request.")
                .font(.system(size: 8, weight: .medium))
                .lineSpacing(1.5)
                .foregroundStyle(.white.opacity(0.55))
                .frame(width: l.cardW - C.pad * 2, alignment: .leading)
                .frame(height: l.cardH - 18, alignment: .bottomLeading)
                .offset(x: C.pad)
        }
        .frame(width: l.cardW, height: l.cardH, alignment: .topLeading)
    }

    // MARK: Form

    /// The fields at their places, and the one focus ring that glides between them.
    private func form(_ l: CreditCardFlipFormLayout) -> some View {
        ZStack(alignment: .topLeading) {
            formField(.number, l, label: "Card number", placeholder: "0000 0000 0000 0000", display: creditCardFlipFormGroup(number))
            formField(.name, l, label: "Cardholder name", placeholder: "As printed on the card", display: name)
            formField(.expiry, l, label: expired ? "Card expired" : "Expiry", placeholder: "MM/YY", display: creditCardFlipFormShowExpiry(expiry), error: expired)
            formField(.cvv, l, label: "CVV", placeholder: "3 digits", display: cvv)
            TimelineView(.animation(minimumInterval: nil, paused: !live)) { timeline in
                let now = timeline.date
                let r = engine.formRing.map { $0.value(at: now) }
                let tint = creditCardFlipFormMix(engine.fromNetwork.palette[2], engine.toNetwork.palette[2], engine.mix.value(at: now))
                RoundedRectangle(cornerRadius: M.fieldRadius - 0.75)
                    .stroke(tint, lineWidth: 1.5)
                    .frame(width: max(0, r[2] - 1.5), height: max(0, r[3] - 1.5))
                    .offset(x: r[0] + 0.75, y: r[1] + 0.75)
                    .opacity(engine.formRingOn.value(at: now))
            }
            .allowsHitTesting(false)
        }
        .frame(width: l.formW, height: l.formH, alignment: .topLeading)
    }

    private func saveRow(_ l: CreditCardFlipFormLayout) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Save card for next time")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(M.text)
                Text("Kept by \(issuer), never on this phone")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(M.muted)
            }
            Spacer(minLength: 0)
            Toggle("Save card for next time", isOn: Binding(get: { save }, set: { next in
                takeOver()
                save = next
            }))
            .labelsHidden()
            .tint(M.success)
        }
        .frame(width: l.formW)
    }

    private func requestKeyboard(_ target: Field?) {
        keyboardRequest = (target, keyboardRequest.id + 1)
    }

    /// A field with its label inside, above the value, and a check (or a warning) at its end.
    private func formField(_ target: Field, _ l: CreditCardFlipFormLayout, label: String, placeholder: String, display: String, error: Bool = false) -> some View {
        let isOn = field == target
        let rect = l.fields[target] ?? .zero
        let complete = done(target)
        return ZStack(alignment: .topLeading) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(error ? M.danger : isOn ? M.text : M.muted)
                .animation(.easeInOut(duration: 0.2), value: isOn)
                .animation(.easeInOut(duration: 0.2), value: error)
                .offset(x: 16, y: M.labelTop)
            CreditCardFlipFormInput(
                field: target,
                text: display,
                placeholder: placeholder,
                accent: UIColor(M.text),
                request: keyboardRequest,
                onChange: { text in edit(target, text, byUser: true) },
                format: { formatted(target) },
                onFocus: { gotFocus(target) },
                onBlur: { lostFocus(target) },
                onReturn: target == .name ? { requestKeyboard(.expiry) } : nil
            )
            .padding(.leading, 16)
            .padding(.trailing, 44)
            .frame(width: rect.width, height: M.valueH + 8)
            .offset(y: M.valueTop - 4)
            .accessibilityLabel(label)
            // autoPlay's caret: the field is "focused" without a keyboard.
            if playing && isOn {
                HStack(spacing: 0) {
                    Text(display.replacingOccurrences(of: " ", with: "\u{00a0}"))
                        .font(.system(size: 17, weight: .medium))
                        .monospacedDigit()
                        .hidden()
                    CreditCardFlipFormCaret(color: M.text)
                        .padding(.leading, 1)
                }
                .frame(height: M.valueH + 8)
                .offset(x: 16, y: M.valueTop - 4)
                .allowsHitTesting(false)
            }
            ZStack {
                if error {
                    CreditCardFlipFormSymbol(name: "exclamationmark.circle.fill", size: 18, color: M.danger)
                        .transition(CreditCardFlipFormBadge.transition)
                } else if complete {
                    CreditCardFlipFormSymbol(name: "checkmark.circle.fill", size: 18, color: M.success)
                        .transition(CreditCardFlipFormBadge.transition)
                }
            }
            .animation(CreditCardFlipFormBadge.animation, value: error)
            .animation(CreditCardFlipFormBadge.animation, value: complete)
            .frame(width: 18, height: M.fieldH)
            .offset(x: rect.width - 14 - 18)
            .allowsHitTesting(false)
        }
        .frame(width: rect.width, height: M.fieldH, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: M.fieldRadius, style: .continuous).fill(M.fieldBackground))
        .overlay(
            RoundedRectangle(cornerRadius: M.fieldRadius, style: .continuous)
                .strokeBorder(error ? M.danger : M.fieldBorder, lineWidth: 1)
                .animation(.easeInOut(duration: 0.2), value: error)
        )
        .offset(x: rect.minX, y: rect.minY)
    }

    /// What a field shows for the text it holds now.
    private func formatted(_ target: Field) -> String {
        switch target {
        case .number: return creditCardFlipFormGroup(number)
        case .name: return name
        case .expiry: return creditCardFlipFormShowExpiry(expiry)
        case .cvv: return cvv
        }
    }

    // MARK: Pay button

    private func payButton(_ l: CreditCardFlipFormLayout, now: Date) -> some View {
        let f = engine.fold.value(at: now)
        let c = engine.check.value(at: now)
        let w = l.payW + (M.payHeight - l.payW) * f
        let enabled = valid && stage == .idle
        let down = (scriptPress || held) && enabled
        let spinning = stage == .paying
        let r = engine.ready.value(at: now)
        return ZStack {
            Capsule()
                .fill(creditCardFlipFormMix3(0x1d1e25, 0xf4f5f8, r, then: 0x3ccf7e, c))
            Text("Pay \(amount)")
                .font(.system(size: 17, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(creditCardFlipFormMix(0x5d6170, 0x0d0e12, r))
                .lineLimit(1)
                .fixedSize()
                .opacity(max(0, 1 - f * 3))
            Canvas { context, _ in
                context.stroke(Path(ellipseIn: CGRect(x: 3, y: 3, width: 20, height: 20)), with: .color(.rgba(13, 14, 18, 0.15)), lineWidth: 2.6)
                var arc = Path()
                arc.addArc(center: CGPoint(x: 13, y: 13), radius: 10, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
                context.stroke(arc, with: .color(M.background), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))
            }
            .frame(width: 26, height: 26)
            .rotationEffect(.degrees(engine.spin(at: now) * 360))
            .opacity(spinning ? min(1, max(0, (f - 0.6) * 4)) * (1 - c) : 0)
            .animation(.easeInOut(duration: 0.12), value: spinning)
            CreditCardFlipFormCheck()
                .trim(from: 0, to: max(0, (CreditCardFlipFormCheck.length - 26 * (1 - c)) / CreditCardFlipFormCheck.length))
                .stroke(.white, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                .frame(width: 26, height: 26)
        }
        .frame(width: w, height: M.payHeight)
        .clipShape(Capsule())
        .scaleEffect(down ? 0.97 : 1)
        .animation(.easeInOut(duration: 0.14), value: down)
        .frame(width: l.payW, height: M.payHeight)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in if enabled && !held { held = true } }
                .onEnded { value in
                    held = false
                    let inside = CGRect(x: -20, y: -20, width: l.payW + 40, height: M.payHeight + 40).contains(value.location)
                    if enabled && inside {
                        takeOver()
                        pay(byUser: true)
                    }
                }
        )
        .accessibilityElement()
        .accessibilityLabel(stage == .paid ? "Paid" : stage == .paying ? "Paying" : "Pay \(amount)")
        .accessibilityAddTraits(.isButton)
    }

    // MARK: Actions

    private func haptic(_ kind: String) {
        switch kind {
        case "select": UISelectionFeedbackGenerator().selectionChanged()
        case "light": UIImpactFeedbackGenerator(style: .light).impactOccurred()
        default: UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    /// What each field's text becomes when typed, by hand or by the script.
    private func edit(_ target: Field, _ text: String, byUser: Bool) {
        guard stage == .idle else { return }
        switch target {
        case .number:
            let digits = String(creditCardFlipFormDigits(text).prefix(16))
            if byUser && CreditCardFlipFormNetwork.of(digits) != network && !digits.isEmpty { haptic("select") }
            // Typed by hand, a number that just became complete moves the keyboard on to the name.
            let advance = byUser && digits.count == 16 && number.count < 16
            if digits != number { number = digits }
            if advance { requestKeyboard(.name) }
        case .name:
            let clean = String(text.uppercased().filter { ("A"..."Z").contains($0) || $0 == "'" || $0 == " " || $0 == "-" }.prefix(20))
            if clean != name { name = clean }
        case .expiry:
            let clean = creditCardFlipFormCleanExpiry(text)
            // Typed by hand, an expiry that just became complete (and good) moves on to the CVV.
            let advance = byUser && clean.count == 4 && expiry.count < 4 && !creditCardFlipFormIsExpired(clean)
            if clean != expiry { expiry = clean }
            if advance { requestKeyboard(.cvv) }
        case .cvv:
            let clean = String(creditCardFlipFormDigits(text).prefix(3))
            if clean != cvv { cvv = clean }
        }
    }

    private func gotFocus(_ target: Field) {
        takeOver()
        blurTask?.cancel()
        if target == .cvv && field != .cvv { haptic("light") }
        field = target
    }

    /// A field left is let go after 60 ms, so moving between fields never drops the outline.
    private func lostFocus(_ target: Field) {
        blurTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.blurGrace))
            if Task.isCancelled { return }
            if field == target { field = nil }
        }
    }

    /// A tap on the card edits what was tapped: the back is the CVV, the front its number, name or expiry.
    private func cardTapped(_ point: CGPoint, _ l: CreditCardFlipFormLayout) {
        takeOver()
        guard stage == .idle else { return }
        var target = Field.number
        if field == .cvv {
            target = .number
        } else if point.y > l.bottomTop - 12 {
            target = point.x > l.cardW * 0.62 ? .expiry : .name
        }
        requestKeyboard(target)
    }

    private func pay(byUser: Bool) {
        guard valid, stage == .idle else { return }
        byUserPay = byUser
        if byUser {
            requestKeyboard(nil)
            onPay?(number, name, expiry, cvv)
        }
        field = nil
        stage = .paying
    }

    // Paying: fold, spin, check, hold, then empty the form.
    private func run(stage new: CreditCardFlipFormStage) {
        switch new {
        case .idle:
            break
        case .paying:
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(CreditCardFlipFormEngine.payFold + CreditCardFlipFormEngine.payDuration))
                if stage == .paying { stage = .paid }
            }
        case .paid:
            if byUserPay { haptic("success") }
            engine.crossLight(at: .now, still: reduceMotion)
            wakeUp()
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(M.paidHold + CreditCardFlipFormEngine.checkDuration))
                if stage == .paid { reset() }
            }
        }
    }

    private func reset() {
        // A field focused while the payment ran gives its keyboard back with the emptied form.
        requestKeyboard(nil)
        number = ""
        name = ""
        expiry = ""
        cvv = ""
        field = nil
        stage = .idle
    }

    /// The script's focus is only make-believe (no keyboard), so it is dropped with the script.
    private func takeOver() {
        guard playing else { return }
        playing = false
        field = nil
    }

    /// Where the comet is round an outline (0…1), once round every `orbit` s; still under reduced motion.
    private func orbit(_ now: Date) -> Double {
        reduceMotion ? 0.62 : now.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: C.orbit) / C.orbit
    }

    // MARK: autoPlay

    private enum Act {
        case focus(Field?)
        case type(Field, String)
        case press
        case pay
    }

    private static func steps() -> [(at: Double, act: Act)] {
        var steps: [(at: Double, act: Act)] = [(0.6, .focus(.number))]
        func typing(_ target: Field, _ text: String, from: Double, key: Double) {
            var at = from
            let chars = Array(text)
            for i in 1...chars.count {
                steps.append((at, .type(target, String(chars[0..<i]))))
                at += key * M.rhythm[i % M.rhythm.count]
            }
        }
        typing(.number, M.script.number, from: 0.95, key: 0.15)
        steps.append((3.55, .focus(.name)))
        typing(.name, M.script.name, from: 3.8, key: 0.085)
        steps.append((5.15, .focus(.expiry)))
        typing(.expiry, M.script.expiry, from: 5.4, key: 0.2)
        steps.append((6.4, .focus(.cvv)))
        typing(.cvv, M.script.cvv, from: 6.95, key: 0.24)
        steps.append(contentsOf: [(7.9, .focus(nil)), (8.22, .press), (8.4, .pay)])
        return steps
    }

    private func perform(_ act: Act) {
        switch act {
        case let .focus(target): field = target
        case let .type(target, text): edit(target, text, byUser: false)
        case .press: scriptPress = true
        case .pay:
            scriptPress = false
            pay(byUser: false)
        }
    }

    /// Every step is timed from the start of its loop, so the period never drifts.
    private func play() async {
        guard playing else {
            scriptPress = false
            return
        }
        let steps = Self.steps()
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
                do { try await clock.sleep(until: origin + .seconds(step.at)) } catch { return }
                perform(step.act)
            }
            do { try await clock.sleep(until: origin + .seconds(M.loop)) } catch { return }
            origin += .seconds(M.loop)
        }
    }

    /// A touch takes over; once the form is left alone again the loop starts over from an empty form.
    private func resume() async {
        guard autoPlay, !playing, field == nil, stage == .idle else { return }
        do { try await Task.sleep(for: .seconds(M.resumeAfter)) } catch { return }
        reset()
        playing = true
    }
}

// MARK: - Layout

struct CreditCardFlipFormLayout: Equatable {
    let size: CGSize
    let insets: EdgeInsets
    let cardW: Double
    let cardH: Double
    let cardTop: Double
    let formTop: Double
    let formW: Double
    let formH: Double
    let payTop: Double
    let payW: Double
    let slotW: Double
    let numberTop: Double
    let bottomTop: Double
    let markOrigin: CGPoint
    /// Where the focus outline frames each part of the card's front.
    let outlines: [CreditCardFlipFormField: CGRect]
    /// Each field's box on the form, from the form's top left.
    let fields: [CreditCardFlipFormField: CGRect]

    private typealias M = CreditCardFlipFormMetrics
    private typealias C = CreditCardFlipFormCardMetrics

    init(size: CGSize, insets: EdgeInsets, nameBox: CGSize, expiryBox: CGSize) {
        self.size = size
        self.insets = insets
        cardW = max(200, min(size.width - M.side * 2, M.cardMax))
        cardH = (cardW / M.cardRatio).rounded()
        cardTop = insets.top + M.cardTop
        formTop = cardTop + cardH + M.formGap
        formW = size.width - M.side * 2
        let row = M.fieldH + M.fieldGap
        formH = row * 2 + M.fieldH
        payTop = size.height - insets.bottom - 16 - M.payHeight
        payW = formW
        slotW = (cardW - C.pad * 2 - C.groupGap * 3) / 16
        numberTop = (cardH * C.numberAt).rounded()
        bottomTop = cardH - C.bottomRow
        markOrigin = CGPoint(x: cardW - C.pad - 40, y: 30)
        outlines = [
            .number: CGRect(x: C.pad - C.outlinePadX, y: numberTop - 6, width: cardW - C.pad * 2 + C.outlinePadX * 2, height: C.numberLine + 12),
            .name: CGRect(
                x: C.pad - C.outlinePadX, y: bottomTop - C.outlinePadY,
                width: nameBox.width + C.outlinePadX * 2, height: nameBox.height + C.outlinePadY * 2
            ),
            .expiry: CGRect(
                x: cardW - C.pad - expiryBox.width - C.outlinePadX, y: bottomTop - C.outlinePadY,
                width: expiryBox.width + C.outlinePadX * 2, height: expiryBox.height + C.outlinePadY * 2
            ),
        ]
        let half = (formW - 12) / 2
        fields = [
            .number: CGRect(x: 0, y: 0, width: formW, height: M.fieldH),
            .name: CGRect(x: 0, y: row, width: formW, height: M.fieldH),
            .expiry: CGRect(x: 0, y: row * 2, width: half, height: M.fieldH),
            .cvv: CGRect(x: half + 12, y: row * 2, width: half, height: M.fieldH),
        ]
    }

    func fieldBottom(_ field: CreditCardFlipFormField) -> Double {
        formTop + (fields[field]?.maxY ?? 0)
    }
}

enum CreditCardFlipFormMetrics {
    static let background = Color(hex: 0x0d0e12)
    static let text = Color(hex: 0xf4f5f8)
    static let muted = Color(hex: 0x8a8e9c)
    static let fieldBackground = Color(hex: 0x15161c)
    static let fieldBorder = Color(hex: 0x25272f)
    static let success = Color(hex: 0x3ccf7e)
    static let danger = Color(hex: 0xff6369)

    // Layout, in pt. Fields carry their label inside, above the value.
    static let side = 24.0
    // With the keyboard up the page rises; before the big header would reach the top inset (the
    // Dynamic Island), it gives way to a compact bar pinned under the inset, and the page slides under it.
    static let barH = 44.0 // the compact bar's height under the top inset
    static let headerRoom = 14.0 // pt of rise over which the big header hands over to the bar
    static let cardMax = 380.0
    static let cardRatio = 1.586
    static let cardTop = 84.0
    static let formGap = 30.0
    static let fieldH = 60.0
    static let fieldGap = 14.0
    static let fieldRadius = 14.0
    static let labelTop = 10.0
    static let valueTop = 26.0
    static let valueH = 26.0
    static let saveGap = 18.0
    static let payHeight = 56.0

    static let paidHold = 1.8
    static let blurGrace = 0.06

    // autoPlay (s): the card it types and when each step happens. `loop` is the whole period.
    static let loop = 14.2
    static let script = (number: "5412753488102697", name: "JONAH VERRIER", expiry: "0829", cvv: "417")
    static let rhythm = [1, 0.8, 1.25, 0.9, 1.1, 0.75, 1.2, 1]
    // After a touch, autoPlay starts over this long after the form is left alone.
    static let resumeAfter = 4.0
}

/// A field's check or warning pops in from 60% on a lively spring and leaves in 140 ms.
enum CreditCardFlipFormBadge {
    static let transition = AnyTransition.asymmetric(
        insertion: .scale(scale: 0.6).combined(with: .opacity),
        removal: .scale(scale: 0.8).combined(with: .opacity).animation(.easeInOut(duration: 0.14))
    )
    static let animation = Animation.spring(duration: 0.38, bounce: 0.4)
}

// MARK: - Pieces

func creditCardFlipFormDigits(_ text: String) -> String { text.filter(\.isASCIIDigit) }

private extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}

func creditCardFlipFormGroup(_ digits: String) -> String {
    var out = ""
    for (i, c) in digits.enumerated() {
        if i > 0 && i % 4 == 0 { out.append(" ") }
        out.append(c)
    }
    return out
}

func creditCardFlipFormShowExpiry(_ digits: String) -> String {
    digits.count > 2 ? "\(digits.prefix(2))/\(digits.dropFirst(2))" : digits
}

/// Eases `v` into ±`limit`: linear near zero, never past the limit.
func creditCardFlipFormSoft(_ v: Double, _ limit: Double) -> Double { limit * tanh(v / limit) }

/// A full MMYY whose month has already ended.
func creditCardFlipFormIsExpired(_ digits: String) -> Bool {
    guard digits.count == 4, let month = Int(digits.prefix(2)), let yy = Int(digits.suffix(2)) else { return false }
    let now = Calendar.current.dateComponents([.year, .month], from: .now)
    let year = 2000 + yy
    let thisYear = now.year ?? 0
    return year < thisYear || (year == thisYear && month < (now.month ?? 1))
}

/// `a` mixed towards `b` by `k`, then that towards `c` by `k2`, as `interpolateColor` twice.
func creditCardFlipFormMix3(_ a: UInt32, _ b: UInt32, _ k: Double, then c: UInt32, _ k2: Double) -> Color {
    func channel(_ x: UInt32, _ shift: UInt32) -> Double { pow(Double((x >> shift) & 0xff) / 255, 2.2) }
    func mixed(_ shift: UInt32) -> Double {
        let first = pow(channel(a, shift) + (channel(b, shift) - channel(a, shift)) * k, 1 / 2.2)
        let lin = pow((first * 255).rounded() / 255, 2.2)
        return pow(lin + (channel(c, shift) - lin) * k2, 1 / 2.2)
    }
    return Color(.sRGB, red: mixed(16), green: mixed(8), blue: mixed(0), opacity: 1)
}

/// Month first: a first digit above 1 is a single-digit month, and the month never passes 12.
func creditCardFlipFormCleanExpiry(_ text: String) -> String {
    var digits = String(creditCardFlipFormDigits(text).prefix(4))
    if let first = digits.first?.wholeNumberValue, first > 1 { digits = String(("0" + digits).prefix(4)) }
    if digits.count >= 2 {
        let month = Int(digits.prefix(2)) ?? 0
        if month > 12 || digits.prefix(2) == "00" { digits = String(digits.prefix(1)) }
    }
    return digits
}

func creditCardFlipFormTitleCase(_ name: String) -> String {
    // Like JS's `\b\w` → upper case: a letter that starts a word is capitalised, the rest lowered.
    var out = ""
    var inWord = false
    for c in name.lowercased() {
        let word = c.isLetter || c.isNumber || c == "_"
        out.append(word && !inWord ? Character(c.uppercased()) : c)
        inWord = word
    }
    return out
}

/// The caret autoPlay types behind: on for 45% of a second, then off.
private struct CreditCardFlipFormCaret: View {
    let color: Color

    var body: some View {
        TimelineView(.animation) { timeline in
            let phase = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1)
            let opacity = phase < 0.45 ? 1 : phase < 0.55 ? 1 - (phase - 0.45) / 0.1 : 0
            RoundedRectangle(cornerRadius: 1)
                .fill(color)
                .frame(width: 2, height: 22)
                .opacity(opacity)
        }
    }
}

/// The check drawn in the paid button, in its 26 pt box.
private struct CreditCardFlipFormCheck: Shape {
    /// The path's length; RN dashes it 26 long, so it starts to show a little into the stroke.
    static let length = hypot(4.5, 4.5) + hypot(8.5, 9.5)

    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 6.5, y: 13.5))
        p.addLine(to: CGPoint(x: 11, y: 18))
        p.addLine(to: CGPoint(x: 19.5, y: 8.5))
        return p
    }
}

/// An SF Symbol fitted into a square of `size` pt, as RN's SymbolView draws it.
struct CreditCardFlipFormSymbol: View {
    let name: String
    let size: Double
    var weight: Font.Weight = .regular
    let color: Color

    var body: some View {
        Image(systemName: name)
            .resizable()
            .fontWeight(weight)
            .scaledToFit()
            .foregroundStyle(color)
            .frame(width: size, height: size)
    }
}
