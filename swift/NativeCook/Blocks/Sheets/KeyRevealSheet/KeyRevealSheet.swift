import SwiftUI

/// A wallet page and a floating sheet that warns about the private key. Reveal scans a face,
/// swaps the warning for the key, which unblurs from left to right while a light runs once around
/// its box; tapping the key copies it. Nothing real is kept here: `words` is whatever you pass.
struct KeyRevealSheet: View {
    /// The words of the key, shown in a grid of three columns.
    var words = ["river", "amber", "falcon", "lunar", "maple", "quartz", "harbor", "velvet", "cactus", "ember", "orbit", "willow"]
    var walletName = "BFF"
    var balance = "0.02 ETH"
    var title = "Private Key"
    /// The body in three lines, which rise one after the other.
    var bodyLines = ["Your Private Key is the key used to", "back up your wallet. Keep it secret", "and secure at all times."]
    /// The three reminders under the body.
    var notes = ["Keep your Private Key safe", "Don't share it with anyone else", "If you lose it, we can't recover it"]
    var cancelLabel = "Cancel"
    var revealLabel = "Reveal"
    var copiedLabel = "Copied"
    /// Opens the sheet, reveals the key, copies it and closes again by itself every `KeyRevealSheet.loop` seconds.
    var autoPlay = false
    /// Called when Reveal is pressed.
    var onReveal: (() -> Void)? = nil
    /// Called with the key's words when the key is tapped to copy.
    var onCopy: (([String]) -> Void)? = nil

    /// Seconds for one pass of the autoPlay loop.
    static let loop = 9.0
    // The timeline, in seconds from the start of a loop.
    private static let pressRowAt = 0.3
    private static let openAt = 0.5
    private static let pressRevealAt = 2.2
    private static let revealAt = 2.5
    private static let copyAt = 5.2
    private static let hideAt = 6.5
    private static let closeAt = 7.5
    /// Seconds after the last touch before the loop starts again from rest.
    private static let userHold = 3.0

    private enum Pressed { case none, row, reveal }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var open = false
    @State private var revealed = false
    @State private var pressed = Pressed.none
    @State private var closing: Task<Void, Never>? = nil
    @State private var tapTask: Task<Void, Never>? = nil
    @State private var toastTask: Task<Void, Never>? = nil
    @State private var pulseTask: Task<Void, Never>? = nil
    @State private var touches = 0
    @State private var touching = false
    @State private var tick = UIImpactFeedbackGenerator(style: .light)
    @State private var done = UINotificationFeedbackGenerator()
    @State private var sheet = 0.0
    @State private var size = 0.0
    @State private var intro = 0.0
    @State private var mode = 0.0
    @State private var scan = 1.0
    @State private var keys = 0.0
    @State private var glow = 0.0
    @State private var toast = 0.0
    @State private var tap = 0.0
    @State private var pulse = 1.0

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            screen(width: width, height: height, insets: insets)
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
                .ignoresSafeArea()
        }
        .environment(\.colorScheme, .light)
        .preferredColorScheme(.light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !touching else { return }
                    touching = true
                    touched()
                }
                .onEnded { _ in touching = false }
        )
        .onAppear {
            tick.prepare()
            done.prepare()
        }
        .onDisappear {
            closing?.cancel()
            tapTask?.cancel()
            toastTask?.cancel()
            pulseTask?.cancel()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await playLoop() }
    }

    // MARK: Layout

    private func screen(width: Double, height: Double, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            Color.white
            VStack(alignment: .leading, spacing: 0) {
                header(top: insets.top)
                wallet.padding(.horizontal, M.margin)
                section("Wallet settings")
                settings
                section("Recent activity")
                activity
            }

            Color.black
                .modifier(Driven(sheet) { content, p in content.opacity(M.scrim * p) })
                .onTapGesture { dismiss() }
                .accessibilityLabel("Close")
                .allowsHitTesting(open)

            sheetView(width: width - M.margin * 2, bottomInset: insets.bottom)
                .frame(width: width, height: height, alignment: .bottom)
        }
    }

    private func header(top: Double) -> some View {
        HStack {
            Image(systemName: "xmark").font(.system(size: 19, weight: .regular)).foregroundStyle(M.ink)
            Spacer(minLength: 0)
            Image(systemName: "gift")
                .font(.system(size: 20))
                .foregroundStyle(M.ink)
                .overlay(alignment: .topTrailing) {
                    Circle()
                        .fill(M.dot)
                        .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                        .frame(width: 10, height: 10)
                        .offset(x: 3, y: -3)
                }
        }
        .frame(height: 24)
        .padding(.horizontal, 24)
        .padding(.top, top + 8)
        .frame(height: top + 52, alignment: .top)
    }

    private var wallet: some View {
        let shape = RoundedRectangle(cornerRadius: M.cardRadius, style: .continuous)
        return ZStack(alignment: .topLeading) {
            LinearGradient(stops: M.amber, startPoint: .topLeading, endPoint: .bottomTrailing)
            LinearGradient(colors: [.white.opacity(0.28), .white.opacity(0)], startPoint: .topLeading, endPoint: UnitPoint(x: 0.6, y: 0.7))
            // A large faint heart in the corner, the card's own pattern.
            Image(systemName: "heart.fill")
                .font(.system(size: 180))
                .foregroundStyle(.white.opacity(0.10))
                .frame(width: 210, height: 210)
                .rotationEffect(.degrees(-14))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .offset(x: 40, y: 56)
            Image(systemName: "heart.fill")
                .font(.system(size: 19))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(Circle().fill(.white.opacity(0.2)))
                .offset(x: 18, y: 18)
            HStack(spacing: 6) {
                Text("Copy Address").font(.system(size: 15, weight: .semibold))
                Image(systemName: "doc.on.doc").font(.system(size: 13))
            }
            .foregroundStyle(.white.opacity(0.92))
            .frame(height: 20)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.trailing, 20)
            .padding(.top, 28)
            VStack(alignment: .leading, spacing: 0) {
                Text(walletName).font(.system(size: 22, weight: .heavy)).tracking(-0.3).foregroundStyle(.white).frame(height: 28)
                Text(balance).font(.system(size: 15, weight: .semibold).monospacedDigit()).foregroundStyle(.white.opacity(0.78)).frame(height: 20)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            .padding(.leading, 20)
            .padding(.bottom, 18)
            HStack(spacing: 6) {
                Image(systemName: "paintpalette").font(.system(size: 12))
                Text("Customize").font(.system(size: 13, weight: .bold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(Capsule().fill(.white.opacity(0.22)))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.trailing, 18)
            .padding(.bottom, 20)
        }
        .frame(height: M.cardHeight)
        .clipShape(shape)
        .overlay(shape.strokeBorder(.white.opacity(0.22), lineWidth: 1))
        .background {
            shape.fill(Color.rgba(200, 120, 0, 0.32)).offset(y: 16).blur(radius: 16)
            shape.fill(Color.rgba(120, 70, 0, 0.18)).offset(y: 2).blur(radius: 3)
        }
        .modifier(Driven(sheet) { content, p in content.scaleEffect(1 - 0.04 * p).offset(y: -8 * p) })
    }

    private func section(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .tracking(0.2)
            .foregroundStyle(M.mute)
            .frame(height: 18)
            .padding(.top, 24)
            .padding(.bottom, 8)
            .padding(.horizontal, M.margin + 4)
    }

    private var settings: some View {
        VStack(spacing: 0) {
            ForEach(Array(M.settings.enumerated()), id: \.offset) { index, item in
                if index == 0 {
                    Button(action: show) {
                        settingRow(item, title: title, line: false)
                    }
                    .buttonStyle(KeyRevealSheetItemStyle(held: pressed == .row))
                    .accessibilityLabel(title)
                } else {
                    settingRow(item, title: item.title, line: true)
                }
            }
        }
        .background(M.well)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, M.margin)
    }

    private func settingRow(_ item: KeyRevealSheetSetting, title: String, line: Bool) -> some View {
        listRow(icon: item.icon, tint: item.tint, title: title, caption: item.caption, line: line) {
            Image(systemName: "chevron.right").font(.system(size: 15, weight: .semibold)).foregroundStyle(M.chevron)
        }
    }

    private var activity: some View {
        VStack(spacing: 0) {
            ForEach(Array(M.activity.enumerated()), id: \.offset) { index, item in
                listRow(
                    icon: item.received ? "arrow.down" : "arrow.up",
                    tint: item.received ? M.green : M.note,
                    title: item.title,
                    caption: item.caption,
                    line: index > 0
                ) {
                    Text(item.amount)
                        .font(.system(size: 15, weight: .bold).monospacedDigit())
                        .foregroundStyle(item.received ? M.green : M.ink)
                }
            }
        }
        .background(M.well)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, M.margin)
    }

    private func listRow<Trailing: View>(
        icon: String, tint: Color, title: String, caption: String, line: Bool, @ViewBuilder trailing: () -> Trailing
    ) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 38, height: 38)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(tint.opacity(0.12)))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 16, weight: .semibold)).tracking(-0.2).foregroundStyle(M.ink).frame(height: 21)
                Text(caption).font(.system(size: 14)).foregroundStyle(M.mute).frame(height: 18)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .padding(.horizontal, 16)
        .frame(height: 68)
        .overlay(alignment: .top) {
            if line { Rectangle().fill(M.itemLine).frame(height: 1.0 / 3) }
        }
        .contentShape(Rectangle())
    }

    // MARK: The sheet

    private func sheetView(width: Double, bottomInset: Double) -> some View {
        ZStack(alignment: .topLeading) {
            frontPane
                .modifier(Driven(mode) { content, m in content.opacity(1 - min(1, m * 2.2)) })
                .allowsHitTesting(!revealed)
            backPane(width: width)
                .modifier(Driven(mode) { content, m in
                    content.opacity(min(1, max(0, m * 2.2 - 1.2))).offset(y: 8 * (1 - m))
                })
                .allowsHitTesting(revealed)
        }
        .frame(width: width, alignment: .topLeading)
        .modifier(Driven(size) { content, s in content.frame(height: M.front + (M.back - M.front) * s, alignment: .topLeading) })
        .background {
            RoundedRectangle(cornerRadius: M.sheetRadius, style: .continuous)
                .fill(.white)
                .shadow(color: .black.opacity(0.22), radius: 30, y: 24)
                .shadow(color: .black.opacity(0.08), radius: 4, y: 2)
        }
        .clipShape(RoundedRectangle(cornerRadius: M.sheetRadius, style: .continuous))
        .modifier(Driven(sheet) { content, p in content.offset(y: (1 - p) * (M.front + M.sheetBottom + bottomInset + 24)) })
        .padding(.bottom, M.sheetBottom)
        .allowsHitTesting(open)
    }

    private func closeButton() -> some View {
        Button { dismiss() } label: {
            Image(systemName: "xmark")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(M.mute)
                .frame(width: 30, height: 30)
                .background(Circle().fill(M.cancel))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close")
    }

    private var frontPane: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top) {
                    KeyRevealSheetKeyIcon()
                    Spacer(minLength: 0)
                    closeButton()
                }
                .frame(height: 34, alignment: .top)
                titleText.padding(.top, 10)
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(bodyLines.enumerated()), id: \.offset) { index, line in
                        Text(line).font(.system(size: 16)).foregroundStyle(M.mute).frame(height: 22)
                            .modifier(Driven(intro) { content, p in
                                let t = min(1, max(0, (p * M.intro - Double(index) * M.lineStagger) / M.noteIn))
                                content.opacity(t).offset(y: 8 * (1 - t))
                            })
                    }
                }
                .padding(.top, 6)
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(notes.enumerated()), id: \.offset) { index, text in
                        HStack(spacing: 12) {
                            Image(systemName: M.noteIcons[index % M.noteIcons.count])
                                .font(.system(size: 15))
                                .foregroundStyle(M.note)
                                .frame(width: 18, height: 18)
                                .scaleEffect(index == 0 ? pulse : 1)
                            Text(text).font(.system(size: 15)).foregroundStyle(M.note).frame(height: 20)
                        }
                        .modifier(Driven(intro) { content, p in
                            let t = min(1, max(0, (p * M.intro - (M.noteStart + Double(index) * M.noteStagger)) / M.noteIn))
                            content.opacity(t).offset(x: -M.noteSlide * (1 - t))
                        })
                    }
                }
                .padding(.top, 20)
            }
            HStack(spacing: 10) {
                Button { dismiss() } label: {
                    Text(cancelLabel)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(M.ink)
                        .frame(width: M.cancelWidth, height: M.buttonHeight)
                        .background(Capsule().fill(M.cancel))
                }
                .buttonStyle(.plain)
                Button { reveal(auto: false) } label: {
                    HStack(spacing: 8) {
                        Color.clear.modifier(Driven(scan) { _, s in KeyRevealSheetFaceScan(scan: s, color: .white) })
                            .frame(width: 18, height: 18)
                        Text(revealLabel).font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: M.buttonHeight)
                }
                .buttonStyle(KeyRevealSheetRevealStyle(held: pressed == .reveal))
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
        }
        .padding(M.pad)
    }

    private func backPane(width: Double) -> some View {
        let boxWidth = width - M.pad * 2
        let chipWidth = (boxWidth - M.boxPad * 2 - M.chipGap * Double(M.columns - 1)) / Double(M.columns)
        let around = 2 * (boxWidth - 2 * M.boxRadius) + 2 * (M.boxHeight - 2 * M.boxRadius) + 2 * .pi * M.boxRadius
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                titleText
                Spacer(minLength: 0)
                closeButton()
            }
            .frame(height: 30)
            .padding(.bottom, 16)
            Button { copy(auto: false) } label: {
                keyBox(boxWidth: boxWidth, chipWidth: chipWidth, around: around)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Copy the key")
            ZStack {
                Text("Tap the key to copy it")
                    .font(.system(size: 14))
                    .foregroundStyle(M.mute)
                    .modifier(Driven(toast) { content, t in content.opacity(1 - t) })
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 14)).foregroundStyle(M.cyan)
                    Text(copiedLabel).font(.system(size: 14, weight: .bold)).foregroundStyle(M.toastInk)
                }
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(Capsule().fill(M.toastFill))
                .modifier(Driven(toast) { content, t in content.opacity(t).offset(y: 8 * (1 - t)) })
            }
            .frame(maxWidth: .infinity)
            .frame(height: 28)
            .padding(.top, 14)
        }
        .padding(M.pad)
    }

    private func keyBox(boxWidth: Double, chipWidth: Double, around: Double) -> some View {
        let rows = stride(from: 0, to: words.count, by: M.columns).map { Array(words[$0..<min($0 + M.columns, words.count)]) }
        return VStack(alignment: .leading, spacing: M.chipGap) {
            ForEach(Array(rows.enumerated()), id: \.offset) { rowIndex, row in
                HStack(spacing: M.chipGap) {
                    ForEach(Array(row.enumerated()), id: \.offset) { column, word in
                        let chip = RoundedRectangle(cornerRadius: 11, style: .continuous)
                        HStack(spacing: 7) {
                            Text("\(rowIndex * M.columns + column + 1)")
                                .font(.custom("Menlo-Bold", size: 11))
                                .foregroundStyle(M.cyan)
                                .frame(width: 14, alignment: .trailing)
                            Text(word)
                                .font(.custom("Menlo", size: 14).weight(.semibold))
                                .foregroundStyle(M.ink)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.horizontal, 9)
                        .frame(width: chipWidth, height: M.chipHeight)
                        .background {
                            chip.fill(.white).shadow(color: .rgba(16, 24, 40, 0.06), radius: 1, y: 1)
                        }
                        .overlay(chip.strokeBorder(M.line, lineWidth: 1.0 / 3))
                    }
                }
            }
        }
        .padding(M.boxPad)
        .frame(width: boxWidth, height: M.boxHeight, alignment: .topLeading)
        .background(M.well)
        .overlay(alignment: .topLeading) {
            // The bright edge that leads the unblur across the key.
            Color.clear.modifier(Driven(keys) { _, k in
                ZStack(alignment: .trailing) {
                    LinearGradient(colors: [M.cyan.opacity(0), M.cyan.opacity(0.22)], startPoint: .leading, endPoint: .trailing)
                    Rectangle().fill(M.cyan).frame(width: 2).shadow(color: M.cyan.opacity(0.9), radius: 4)
                }
                .frame(width: M.edge, height: M.boxHeight)
                .offset(x: boxWidth * k - M.edge)
                .opacity(sin(.pi * k))
            })
            .frame(width: boxWidth, height: M.boxHeight, alignment: .topLeading)
            .allowsHitTesting(false)
        }
        .overlay(alignment: .trailing) {
            // The blur covers the key from the right and pulls back towards it.
            Color.clear.modifier(Driven(keys) { _, k in
                Rectangle().fill(.ultraThinMaterial)
                    .frame(width: boxWidth * (1 - k), height: M.boxHeight)
            })
            .frame(width: boxWidth, height: M.boxHeight, alignment: .trailing)
            .allowsHitTesting(false)
        }
        .overlay {
            Color.clear.modifier(Driven(glow) { _, g in
                Canvas { context, size in
                    for (width, opacity) in [(6.0, 0.22), (M.glowWidth, 1.0)] {
                        let rect = CGRect(origin: .zero, size: size).insetBy(dx: width / 2, dy: width / 2)
                        context.stroke(
                            RoundedRectangle(cornerRadius: M.boxRadius, style: .circular).path(in: rect),
                            with: .color(M.cyan.opacity(opacity)),
                            style: StrokeStyle(lineWidth: width, lineCap: .round, dash: [around * 0.25, around * 0.75], dashPhase: -g * around)
                        )
                    }
                }
                .opacity(sin(.pi * g))
            })
            .allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: M.boxRadius, style: .continuous))
        .modifier(Driven(tap) { content, t in content.scaleEffect(1 - 0.02 * t) })
    }

    private var titleText: some View {
        Text(title).font(.system(size: 24, weight: .bold)).tracking(-0.5).foregroundStyle(M.ink).frame(height: 30)
    }

    // MARK: Actions

    private func animate(_ value: Binding<Double>, to target: Double, _ animation: Animation?) {
        withAnimation(animation) { value.wrappedValue = target }
    }

    private func show() {
        guard !open else { return }
        closing?.cancel()
        open = true
        pressed = .none
        for value in [$intro, $mode, $keys, $glow, $toast, $tap, $size] { animate(value, to: 0, nil) }
        animate($scan, to: 1, nil)
        withAnimation(reduceMotion ? M.timing(M.fade) : M.spring) { sheet = 1 }
        animate($intro, to: 1, .linear(duration: (reduceMotion ? M.fade : M.intro) / 1000).delay(reduceMotion ? 0 : M.introAfter / 1000))
        pulseTask?.cancel()
        pulseTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds((M.introAfter + M.noteStart) / 1000), tolerance: .zero)
            guard !Task.isCancelled else { return }
            animate($pulse, to: 1.3, M.timing(180))
            try? await Task.sleep(for: .seconds(0.18), tolerance: .zero)
            guard !Task.isCancelled else { return }
            animate($pulse, to: 1, M.timing(260))
        }
    }

    private func reveal(auto: Bool) {
        guard !revealed else { return }
        pressed = .none
        revealed = true
        if !auto { tick.impactOccurred() }
        onReveal?()
        let wait = reduceMotion ? 0 : M.switchAfter
        animate($scan, to: 0, nil)
        if reduceMotion {
            animate($scan, to: 1, nil)
        } else {
            animate($scan, to: 1, .linear(duration: M.scan / 1000))
        }
        animate($mode, to: 1, M.timing(reduceMotion ? M.fade : M.switchMs).delay(wait / 1000))
        animate($size, to: 1, (reduceMotion ? M.timing(M.fade) : M.spring).delay(wait / 1000))
        let unblurAt = (wait + (reduceMotion ? 0 : M.unblurAfter)) / 1000
        animate($keys, to: 1, .linear(duration: (reduceMotion ? M.fade : M.unblur) / 1000).delay(unblurAt))
        animate($glow, to: reduceMotion ? 0 : 1, M.timing(M.glowMs, curve: M.quadInOut).delay(unblurAt))
    }

    private func copy(auto: Bool) {
        guard revealed else { return }
        if !auto { done.notificationOccurred(.success) }
        onCopy?(words)
        tapTask?.cancel()
        tapTask = Task { @MainActor in
            animate($tap, to: 1, M.timing(90))
            try? await Task.sleep(for: .seconds(0.09), tolerance: .zero)
            guard !Task.isCancelled else { return }
            animate($tap, to: 0, M.timing(180))
        }
        toastTask?.cancel()
        let rise = reduceMotion ? M.fade : 260
        toastTask = Task { @MainActor in
            animate($toast, to: 1, M.timing(rise))
            try? await Task.sleep(for: .seconds((rise + M.toastFor) / 1000), tolerance: .zero)
            guard !Task.isCancelled else { return }
            animate($toast, to: 0, M.timing(M.fade))
        }
    }

    private func hide() {
        guard revealed else { return }
        revealed = false
        animate($keys, to: 0, M.timing(reduceMotion ? M.fade : M.reblur))
        animate($glow, to: 0, nil)
        let wait = reduceMotion ? 0 : M.reblur
        animate($mode, to: 0, M.timing(reduceMotion ? M.fade : M.switchMs).delay(wait / 1000))
        animate($size, to: 0, (reduceMotion ? M.timing(M.fade) : M.spring).delay(wait / 1000))
    }

    private func dismiss(ms: Double? = nil) {
        let ms = ms ?? (reduceMotion ? M.fade : M.closeMs)
        open = false
        pressed = .none
        withAnimation(M.timing(ms, curve: M.cubicIn), completionCriteria: .logicallyComplete) {
            sheet = 0
        } completion: {
            // Out of sight: the next opening starts from the warning again.
            guard !open else { return }
            for value in [$mode, $size, $keys, $intro] { animate(value, to: 0, nil) }
        }
        closing?.cancel()
        closing = Task { @MainActor in
            try? await Task.sleep(for: .seconds(ms / 1000), tolerance: .zero)
            guard !Task.isCancelled else { return }
            revealed = false
        }
    }

    private func rest() {
        closing?.cancel()
        open = false
        revealed = false
        pressed = .none
        for value in [$sheet, $size, $intro, $mode, $keys, $glow, $toast, $tap] { animate(value, to: 0, M.timing(M.restFade)) }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func playLoop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(Self.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let steps: [(Double, () -> Void)] = [
            (Self.pressRowAt, { pressed = .row }),
            (Self.openAt, show),
            (Self.pressRevealAt, { pressed = .reveal }),
            (Self.revealAt, { reveal(auto: true) }),
            (Self.copyAt, { copy(auto: true) }),
            (Self.hideAt, hide),
            (Self.closeAt, { dismiss() }),
        ]
        var origin = clock.now
        while !Task.isCancelled {
            for (at, run) in steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                run()
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

// MARK: Support

/// Re-evaluates `transform` on every frame of an animation of `value`, so a number that moves
/// under a spring can drive several properties through non-linear maps.
private struct Driven: ViewModifier, Animatable {
    var value: Double
    private let transform: (Content, Double) -> AnyView

    init<Result: View>(_ value: Double, @ViewBuilder _ transform: @escaping (Content, Double) -> Result) {
        self.value = value
        self.transform = { AnyView(transform($0, $1)) }
    }

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    func body(content: Content) -> some View { transform(content, value) }
}

private struct KeyRevealSheetSetting {
    /// An SF Symbol name.
    let icon: String
    let tint: Color
    let title: String
    let caption: String
}

/// A card with two lines of text and an eye over its corner, as the design draws it.
private struct KeyRevealSheetKeyIcon: View {
    var body: some View {
        Canvas { context, _ in
            let stroke = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
            context.stroke(Path(roundedRect: CGRect(x: 1.5, y: 1.5, width: 30, height: 22), cornerRadius: 6), with: .color(M.mute), style: stroke)
            context.stroke(Path { $0.move(to: CGPoint(x: 8, y: 9.5)); $0.addLine(to: CGPoint(x: 18, y: 9.5)) }, with: .color(M.mute), style: stroke)
            context.stroke(Path { $0.move(to: CGPoint(x: 8, y: 15)); $0.addLine(to: CGPoint(x: 14, y: 15)) }, with: .color(M.mute), style: stroke)
            let eye = Path {
                $0.move(to: CGPoint(x: 18, y: 24.5))
                $0.addCurve(to: CGPoint(x: 27.8, y: 17.9), control1: CGPoint(x: 20.6, y: 20.1), control2: CGPoint(x: 23.9, y: 17.9))
                $0.addCurve(to: CGPoint(x: 37.6, y: 24.5), control1: CGPoint(x: 31.7, y: 17.9), control2: CGPoint(x: 35, y: 20.1))
                $0.addCurve(to: CGPoint(x: 27.8, y: 31.1), control1: CGPoint(x: 35, y: 28.9), control2: CGPoint(x: 31.7, y: 31.1))
                $0.addCurve(to: CGPoint(x: 18, y: 24.5), control1: CGPoint(x: 23.9, y: 31.1), control2: CGPoint(x: 20.6, y: 28.9))
                $0.closeSubpath()
            }
            context.fill(eye, with: .color(.white))
            context.stroke(eye, with: .color(M.mute), style: stroke)
            context.stroke(Path(ellipseIn: CGRect(x: 25, y: 21.7, width: 5.6, height: 5.6)), with: .color(M.mute), style: stroke)
        }
        .frame(width: 40, height: 34)
        .accessibilityHidden(true)
    }
}

private struct KeyRevealSheetItemStyle: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.background(held || configuration.isPressed ? Color(hex: 0xe8ebef) : M.well)
    }
}

private struct KeyRevealSheetRevealStyle: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = held || configuration.isPressed
        configuration.label
            .background {
                Capsule()
                    .fill(down ? M.cyanPressed : M.cyan)
                    .shadow(color: M.cyan.opacity(0.35), radius: 9, y: 8)
            }
            .scaleEffect(down ? 0.96 : 1)
    }
}

private typealias M = KeyRevealSheetMetrics

private enum KeyRevealSheetMetrics {
    static let ink = Color(hex: 0x16181b)
    static let mute = Color(hex: 0x7d8187)
    static let note = Color(hex: 0x6b7076)
    static let well = Color(hex: 0xf4f6f8)
    static let line = Color(hex: 0xe9ecef)
    static let itemLine = Color(hex: 0xdde1e5)
    static let chevron = Color(hex: 0xb4b8bd)
    static let cancel = Color(hex: 0xf1f3f6)
    static let cyan = Color(hex: 0x14b8ff)
    static let cyanPressed = Color(hex: 0x0a9fe0)
    static let green = Color(hex: 0x22b07d)
    static let toastFill = Color(hex: 0xe6f7ff)
    static let toastInk = Color(hex: 0x0a7fb8)
    static let amber: [Gradient.Stop] = [
        .init(color: Color(hex: 0xf6c04a), location: 0),
        .init(color: Color(hex: 0xeaa31c), location: 0.55),
        .init(color: Color(hex: 0xd8870a), location: 1),
    ]
    static let dot = Color(hex: 0xe5334b)
    static let noteIcons = ["checkmark.shield", "doc.text", "nosign"]
    static let settings = [
        KeyRevealSheetSetting(icon: "eye", tint: Color(hex: 0x14b8ff), title: "Private Key", caption: "Back up your wallet"),
        KeyRevealSheetSetting(icon: "globe", tint: Color(hex: 0x7c5cff), title: "Network", caption: "Ethereum Mainnet"),
        KeyRevealSheetSetting(icon: "square.grid.2x2", tint: Color(hex: 0xff8a3d), title: "Connected apps", caption: "3 apps can see this wallet"),
    ]
    static let activity = [
        (title: "Received", caption: "From 0x8f3…a21 · 2h ago", amount: "+0.01 ETH", received: true),
        (title: "Sent", caption: "To mira.eth · Yesterday", amount: "−0.005 ETH", received: false),
    ]

    static let margin = 20.0
    static let pad = 22.0
    static let cardHeight = 208.0
    static let cardRadius = 24.0
    static let sheetRadius = 32.0
    static let sheetBottom = 16.0
    // The sheet's height with the warning, and with the key.
    static let front = 372.0
    static let back = 318.0
    static let buttonHeight = 50.0
    static let cancelWidth = 128.0
    static let boxPad = 12.0
    static let chipHeight = 36.0
    static let chipGap = 8.0
    static let boxRadius = 14.0
    static let boxHeight = boxPad * 2 + chipHeight * 4 + chipGap * 3
    static let columns = 3
    static let glowWidth = 2.0
    /// The bright edge that leads the unblur across the key.
    static let edge = 36.0

    // Response 0.5 s, damping ratio 0.82.
    static let spring = Animation.interpolatingSpring(mass: 1, stiffness: 158, damping: 20.7)
    static let fade = 180.0
    static let scan = 500.0
    static let switchAfter = 560.0
    static let switchMs = 320.0
    static let unblurAfter = 480.0
    static let unblur = 800.0
    static let reblur = 350.0
    static let glowMs = 950.0
    static let toastFor = 1400.0
    static let introAfter = 700.0
    static let intro = 1000.0
    static let lineStagger = 80.0
    static let noteStagger = 100.0
    static let noteStart = 300.0
    static let noteIn = 300.0
    static let noteSlide = 12.0
    static let closeMs = 450.0
    static let restFade = 350.0
    static let scrim = 0.32

    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 1), endControlPoint: UnitPoint(x: 0.68, y: 1))
    static let cubicIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.32, y: 0), endControlPoint: UnitPoint(x: 0.67, y: 0))
    static let quadInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    /// A timed animation of `ms` milliseconds, cubic ease-out unless told otherwise.
    static func timing(_ ms: Double, curve: UnitCurve = cubicOut) -> Animation {
        .timingCurve(curve, duration: ms / 1000)
    }
}
