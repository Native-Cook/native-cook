import SwiftUI
import UIKit

/*
 Mention Autocomplete — the composer of Huddle, a team chat.

 Typing "@" opens a popover of people that grows out of the caret; each letter filters it, with the
 matched letters in bold and the rows sliding to their new places on springs. Return or a tap turns
 the typed letters into a token chip that grows out of them, followed by a space for the next word.
 Backspace takes that space first; the next one on the chip selects it, and another breaks it back
 into plain text. Sending pushes the conversation up by the new message's height while the message
 rises out of the composer: one offset of the whole thread, eased over 0.3 s.

 The composer is a real text field whose text is drawn as runs (plain text, and the chips' names
 in semibold accent); the chips' backgrounds sit behind it, placed by measuring the same runs. The
 loop (MentionAutocompleteMotion) types into the value without focusing it, so no keyboard shows;
 a touch hands everything to the person, and two seconds after the last touch the loop comes back.
*/
struct MentionAutocomplete: View {
    static let loop: Double = MentionAutocompleteMotion.loop
    typealias M = MentionAutocompleteMotion
    typealias G = MentionAutocompleteGeo

    private let autoPlay: Bool
    private let channel: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model = MentionAutocompleteModel()

    init(autoPlay: Bool = true, channel: String = "#launch-week") {
        self.autoPlay = autoPlay
        self.channel = channel
    }

    var body: some View {
        GeometryReader { outer in
            let top = outer.safeAreaInsets.top
            let bottom = outer.safeAreaInsets.bottom
            GeometryReader { inner in
                let g = G(W: inner.size.width, H: inner.size.height, top: top, bottom: bottom)
                screen(g)
                    .onAppear { model.configure(autoPlay: autoPlay, reduced: reduceMotion, bottom: bottom) }
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea(.keyboard)
        .background(G.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { model.stop() }
    }

    private func screen(_ g: G) -> some View {
        TimelineView(.animation(minimumInterval: nil, paused: model.isPaused)) { tl in
            let now = tl.date.timeIntervalSinceReferenceDate
            let p = model.pose(now)
            let c = model.content(now)
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    G.page
                        .contentShape(Rectangle())
                        .onTapGesture { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
                    // The thread hangs from the clip's bottom edge, just above the composer, lifted by its own
                    // height: a sent message is laid out out of sight and rises into place with the offset.
                    let threadW = g.W - (g.margin + 4) * 2
                    let clipH = g.composerTop - 4
                    MentionAutocompleteThread(messages: model.messages, width: threadW)
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { model.threadLaidOut(Double($0)) }
                        .offset(y: clipH - model.threadValue(now))
                        .frame(width: threadW, height: clipH, alignment: .topLeading)
                        .clipped()
                        .offset(x: g.margin + 4)
                        .allowsHitTesting(false)
                    composer(g, p, c)
                    popover(g, p, c)
                }
                .offset(y: -model.liftValue(now))
                MentionAutocompleteHeader(g: g, channel: channel).equatable()
                finger(g, p, c, model.dot(now))
            }
            .frame(width: g.W, height: g.H, alignment: .topLeading)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
        .clipped()
    }

    // MARK: Measuring

    /// x of the "@" being typed, from the start of the text (0 once the popover closes, as in RN).
    private func atX(_ c: MentionAutocompleteModel.Content) -> CGFloat {
        guard let q = c.query else { return 0 }
        return MentionAutocompleteComposerField.width(c.doc.prefix(q.at))
    }

    private func popLeft(_ g: G, _ x: CGFloat) -> CGFloat {
        min(max(g.textLeft + x - 26, g.margin), g.W - g.margin - g.popW)
    }

    // MARK: Composer

    private func composer(_ g: G, _ p: M.Pose, _ c: MentionAutocompleteModel.Content) -> some View {
        let border = G.mix(G.hairlineRGB, MentionAutocompleteRGB(0x2F63D6, 0.45), p.focus)
        let sendColor = G.mix(MentionAutocompleteRGB(0xE2DFD8), MentionAutocompleteRGB(0x2F63D6), p.send)
        let endX = MentionAutocompleteComposerField.width(c.doc)
        let hidden = c.mode == .morph ? c.active : -1
        let look = MentionAutocompleteComposerField.Look(doc: c.doc, hidden: hidden, caret: model.user ? model.uCaret : nil)
        return ZStack(alignment: .topLeading) {
            Menu {
                Button("Photo", systemImage: "photo") { model.attachPicked() }
                Button("File", systemImage: "doc") { model.attachPicked() }
                Button("Poll", systemImage: "chart.bar") { model.attachPicked() }
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(G.ink)
                    .frame(width: G.plus, height: G.plus)
                    .background(
                        Circle().fill(G.card)
                            .overlay(Circle().strokeBorder(G.hairline, lineWidth: 0.5))
                            .shadow(color: Color.rgba(30, 26, 20, 0.08), radius: 1, y: 1)
                    )
                    .contentShape(Circle())
            }
            .simultaneousGesture(TapGesture().onEnded { model.takeOver() })
            .accessibilityLabel("Attach")
            .offset(x: g.margin, y: g.composerTop + (G.pillH - G.plus) / 2)

            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(Color.rgba(47, 99, 214, 0.10))
                    .frame(width: g.pillW + 8, height: G.pillH + 8)
                    .offset(x: -4, y: -4)
                    .opacity(p.focus)
                Capsule()
                    .fill(G.card)
                    .shadow(color: Color.rgba(30, 26, 20, 0.08), radius: 1, y: 1)
                    .shadow(color: Color.rgba(30, 26, 20, 0.07), radius: 10, y: 8)
                    .frame(width: g.pillW, height: G.pillH)
                ZStack(alignment: .topLeading) {
                    chipLayer(c, p)
                    if c.doc.text.isEmpty {
                        Text("Message \(channel)")
                            .font(.system(size: 16))
                            .foregroundStyle(Color(hex: 0xA7A297))
                            .lineLimit(1)
                            .frame(width: g.textW, height: G.pillH, alignment: .leading)
                            .opacity(p.placeholder)
                            .allowsHitTesting(false)
                    }
                    MentionAutocompleteComposerField(look: look, channel: channel,
                                                     onText: { model.onText($0, caret: $1) },
                                                     onFocus: { model.focusChanged($0) },
                                                     onReturn: { model.onReturn() })
                        .frame(width: g.textW, height: G.pillH)
                    RoundedRectangle(cornerRadius: 1)
                        .fill(G.accent)
                        .frame(width: 2, height: 22)
                        .offset(x: endX + 1, y: (G.pillH - 22) / 2)
                        .opacity(p.caret)
                        .allowsHitTesting(false)
                }
                .frame(width: g.textW, height: G.pillH, alignment: .topLeading)
                .clipped()
                .offset(x: G.textPad)
                Button { model.sendMessage() } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(hex: 0xFFFDF8))
                        .frame(width: G.send, height: G.send)
                        .background(Circle().fill(sendColor.color))
                        .scaleEffect(0.9 + 0.1 * p.send)
                        .contentShape(Circle().inset(by: -6))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Send")
                .offset(x: g.pillW - 6 - G.send, y: (G.pillH - G.send) / 2)
                Capsule()
                    .strokeBorder(border.color, lineWidth: 1)
                    .frame(width: g.pillW, height: G.pillH)
                    .allowsHitTesting(false)
            }
            .frame(width: g.pillW, height: G.pillH, alignment: .topLeading)
            .offset(x: g.pillLeft, y: g.composerTop)
        }
    }

    /// Chip backgrounds behind the field, and the letters of the chip being born or broken.
    private func chipLayer(_ c: MentionAutocompleteModel.Content, _ p: M.Pose) -> some View {
        let doc = c.doc
        let chipY = (G.pillH - G.chipH) / 2
        return ZStack(alignment: .topLeading) {
            ForEach(Array(doc.chips.enumerated()), id: \.offset) { i, chip in
                let x = MentionAutocompleteComposerField.width(doc.prefix(chip.start))
                let w = MentionAutocompleteComposerField.width(doc.slice(chip.start, chip.start + chip.len), semibold: true)
                if i == c.active && (c.mode == .morph || c.mode == .selected || c.mode == .chip) {
                    liveChip(x: x, w: w, fromW: MentionAutocompleteComposerField.width(c.from),
                             label: doc.slice(chip.start, chip.start + chip.len), from: c.from, morphing: c.mode == .morph, p: p)
                } else {
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(G.chipBg)
                        .frame(width: w + G.chipPad * 2, height: G.chipH)
                        .offset(x: x - G.chipPad, y: chipY)
                }
            }
            if c.mode == .breaking {
                let x = MentionAutocompleteComposerField.width(c.broke.prefix)
                let w = MentionAutocompleteComposerField.width(c.broke.label, semibold: true)
                let b = p.breakUp
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(G.mix(MentionAutocompleteRGB(0x2F63D6), MentionAutocompleteRGB(0xE3EAFB), M.seg(b, 0, 0.5)).color)
                    .frame(width: w + G.chipPad * 2, height: G.chipH)
                    .scaleEffect(x: 1 + 0.06 * b, y: 1 + 0.14 * b)
                    .opacity(1 - M.seg(b, 0.05, 0.75))
                    .offset(x: x - G.chipPad, y: chipY)
                Text(c.broke.label)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(G.mix(MentionAutocompleteRGB(0xFFFDF8), MentionAutocompleteRGB(0x2452B9), M.seg(b, 0, 0.4)).color)
                    .fixedSize()
                    .frame(height: G.pillH)
                    .offset(x: x, y: 2 * b)
                    .opacity(1 - M.seg(b, 0.1, 0.65))
            }
        }
        .allowsHitTesting(false)
    }

    private func liveChip(x: CGFloat, w: CGFloat, fromW: CGFloat, label: String, from: String, morphing: Bool, p: M.Pose) -> some View {
        let m = morphing ? p.morph : 1
        let grow = M.easeOutBack(m)
        let pad = G.chipPad * M.clamp01(m * 3)
        let width = fromW + (w - fromW) * grow + pad * 2
        let bg = G.mix(MentionAutocompleteRGB(0xE3EAFB), MentionAutocompleteRGB(0x2F63D6), p.select)
        let pulse = 1 + 0.04 * sin(Double.pi * M.seg(p.select, 0, 1)) * (1 - p.select)
        let boxAlpha = morphing ? M.seg(m, 0, 0.25) * 0.6 + 0.4 * M.clamp01(m * 4) : 1
        let chipY = (G.pillH - G.chipH) / 2
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(bg.color)
                .frame(width: max(0, width), height: G.chipH)
                .scaleEffect(pulse)
                .opacity(boxAlpha)
                .offset(x: x - pad, y: chipY)
            if morphing {
                ZStack(alignment: .topLeading) {
                    Text(from)
                        .font(.system(size: 16))
                        .foregroundStyle(G.ink)
                        .fixedSize()
                        .frame(height: G.pillH)
                        .offset(y: -2 * M.seg(m, 0, 0.45))
                        .opacity(1 - M.seg(m, 0.08, 0.45))
                    Text(label)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x2452B9))
                        .fixedSize()
                        .frame(height: G.pillH)
                        .offset(y: 3 * (1 - M.seg(m, 0.12, 0.6)))
                        .opacity(M.seg(m, 0.12, 0.55))
                }
                .frame(width: max(0, width - pad), height: G.chipH, alignment: .leading)
                .clipped()
                .offset(x: x, y: chipY)
                .scaleEffect(pulse)
            }
        }
    }

    // MARK: Popover

    private func popover(_ g: G, _ p: M.Pose, _ c: MentionAutocompleteModel.Content) -> some View {
        let x = atX(c)
        let left = popLeft(g, x)
        let anchor = g.textLeft + x + 5 - left
        let open = c.query.map { !MentionAutocompleteData.rank($0.query).isEmpty } ?? false
        let ranked = c.query.map { MentionAutocompleteData.rank($0.query) } ?? []
        return MentionAutocompletePopover(pose: p, query: c.query?.query ?? "", open: open && model.user, listed: open,
                                         tappable: { ranked.contains($0) },
                                         width: g.popW, anchor: anchor, channel: channel, accent: G.accent,
                                         onPick: { model.pick($0) })
            .offset(x: left, y: g.popBottom - M.popMaxH)
            // A closed popover keeps its rows laid out (scaled into the caret); VoiceOver must not read them.
            .accessibilityHidden(!open || p.pop < 0.5)
    }

    // MARK: Ghost finger

    private func finger(_ g: G, _ p: M.Pose, _ c: MentionAutocompleteModel.Content, _ d: M.Dot) -> some View {
        var x = g.pillLeft + g.pillW * 0.42
        var y = g.composerTop + G.pillH / 2
        if d.target == 1 {
            let n = max(1, p.count)
            x = popLeft(g, atX(c)) + g.popW * 0.4
            y = g.popBottom - M.popPad - (n - 1) * M.rowH - M.rowH / 2
        }
        let ink = Color.rgba(30, 26, 20, 1)
        return Circle()
            .fill(ink.opacity(0.16))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.75), lineWidth: 1))
            .shadow(color: ink.opacity(0.14), radius: 6, y: 4)
            .frame(width: 44, height: 44)
            .scaleEffect(d.scale)
            .offset(x: x - 22, y: y - 22 + (1 - d.alpha) * 8)
            .opacity(d.alpha)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Conversation and chrome

struct MentionAutocompleteThread: View {
    let messages: [MentionAutocompleteModel.Message]
    let width: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Rectangle().fill(MentionAutocompleteGeo.hairline).frame(height: 0.5)
                Text("Today").font(.system(size: 12, weight: .medium)).foregroundStyle(MentionAutocompleteGeo.muted)
                Rectangle().fill(MentionAutocompleteGeo.hairline).frame(height: 0.5)
            }
            .padding(.bottom, 2)
            ForEach(messages) { m in
                MentionAutocompleteMessageRow(message: m)
            }
        }
        .padding(.bottom, 12)
        .frame(width: width, alignment: .topLeading)
        .fixedSize(horizontal: false, vertical: true)
    }
}

struct MentionAutocompleteMessageRow: View {
    let message: MentionAutocompleteModel.Message

    var body: some View {
        let a = message.author
        HStack(alignment: .top, spacing: 11) {
            MentionAutocompleteAvatar(person: a, size: 36, online: a.online, ring: MentionAutocompleteGeo.page)
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 7) {
                    Text(a.id == "you" ? "You" : a.fullName)
                        .font(.system(size: 15, weight: .semibold))
                        .kerning(-0.2)
                        .foregroundStyle(MentionAutocompleteGeo.ink)
                    Text(message.time)
                        .font(.system(size: 12))
                        .monospacedDigit()
                        .foregroundStyle(MentionAutocompleteGeo.muted)
                }
                text
                    .font(.system(size: 16))
                    .kerning(-0.15)
                    .lineSpacing(21 - UIFont.systemFont(ofSize: 16).lineHeight)
                    .foregroundStyle(Color(hex: 0x34312C))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var text: Text {
        message.doc.runs.reduce(Text("")) { acc, r in
            if r.chip < 0 { return acc + Text(r.text) }
            var s = AttributedString(r.text)
            s.backgroundColor = MentionAutocompleteGeo.chipBg
            return acc + Text(s).fontWeight(.semibold).foregroundStyle(Color(hex: 0x2452B9))
        }
    }
}

struct MentionAutocompleteHeader: View, Equatable {
    let g: MentionAutocompleteGeo
    let channel: String

    var body: some View {
        let stack = [MentionAutocompleteData.people[2], MentionAutocompleteData.people[0], MentionAutocompleteData.people[1]]
        HStack(spacing: 12) {
            Image(systemName: "chevron.left")
                .font(.system(size: 19, weight: .regular))
                .foregroundStyle(MentionAutocompleteGeo.ink)
                .accessibilityLabel("Back")
            VStack(alignment: .leading, spacing: 1) {
                Text(channel)
                    .font(.system(size: 17, weight: .semibold))
                    .kerning(-0.3)
                    .foregroundStyle(MentionAutocompleteGeo.ink)
                Text("14 members · 5 online")
                    .font(.system(size: 12.5))
                    .foregroundStyle(MentionAutocompleteGeo.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: -9) {
                ForEach(Array(stack.enumerated()), id: \.offset) { i, p in
                    MentionAutocompleteAvatar(person: p, size: 26)
                        .padding(2)
                        .background(Circle().fill(MentionAutocompleteGeo.bar))
                        .zIndex(Double(3 - i))
                }
            }
            .accessibilityHidden(true)
        }
        .padding(.horizontal, g.margin + 2)
        .frame(width: g.W, height: g.headerH)
        .padding(.top, g.top)
        .background(
            MentionAutocompleteGeo.bar
                .overlay(alignment: .bottom) { Rectangle().fill(MentionAutocompleteGeo.hairline).frame(height: 0.5) }
        )
    }
}

struct MentionAutocompleteRGB {
    var r: Double, g: Double, b: Double, a: Double

    init(_ hex: UInt32, _ a: Double = 1) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
        self.a = a
    }

    init(r: Double, g: Double, b: Double, a: Double) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
}

struct MentionAutocompleteGeo: Equatable {
    static let accent = Color(hex: 0x2F63D6)
    static let chipBg = Color(hex: 0xE3EAFB)
    static let ink = Color(hex: 0x1E1D1A)
    static let muted = Color(hex: 0x7A766D)
    static let page = Color(hex: 0xF5F4F0)
    static let bar = Color(hex: 0xFBFAF7)
    static let card = Color(hex: 0xFFFEFB)
    static let hairline = Color.rgba(30, 28, 24, 0.12)
    static let hairlineRGB = MentionAutocompleteRGB(r: 30 / 255, g: 28 / 255, b: 24 / 255, a: 0.12)
    static let pillH: CGFloat = 44
    static let plus: CGFloat = 36
    static let textPad: CGFloat = 14
    static let send: CGFloat = 32
    static let chipPad: CGFloat = 4
    static let chipH: CGFloat = 24

    static func mix(_ a: MentionAutocompleteRGB, _ b: MentionAutocompleteRGB, _ t: Double) -> MentionAutocompleteRGB {
        let k = min(max(t, 0), 1)
        return MentionAutocompleteRGB(r: a.r + (b.r - a.r) * k, g: a.g + (b.g - a.g) * k, b: a.b + (b.b - a.b) * k, a: a.a + (b.a - a.a) * k)
    }

    let W: CGFloat, H: CGFloat, top: CGFloat, margin: CGFloat, composerTop: CGFloat
    let pillLeft: CGFloat, pillW: CGFloat, textLeft: CGFloat, textW: CGFloat, popW: CGFloat
    let headerH: CGFloat, popBottom: CGFloat

    init(W: CGFloat, H: CGFloat, top: CGFloat, bottom: CGFloat) {
        self.W = W
        self.H = H
        self.top = top
        margin = max(12, (W - 460) / 2)
        composerTop = H - max(bottom, 10) - 8 - Self.pillH
        pillLeft = margin + Self.plus + 8
        pillW = W - margin - pillLeft
        textLeft = pillLeft + Self.textPad
        textW = pillW - Self.textPad - Self.send - 12
        popW = min(300, W - 24)
        headerH = 56
        popBottom = composerTop - 8
    }
}
