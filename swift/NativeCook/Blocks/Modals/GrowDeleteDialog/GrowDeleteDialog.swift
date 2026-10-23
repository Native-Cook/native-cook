import SwiftUI

enum GrowDeleteDialogMotion {
    // One loop, in seconds: the trash of the second row is pressed, the dialog grows out of it, the
    // icon shakes, Confirm is pressed, the dialog turns into its success version, closes, and the
    // row collapses; at the end the row returns.
    static let loop = 9.0
    static let targetRow = 1
    static let pressAt = 0.8
    static let openAt = 1.1
    static let shakeAt = 2.6
    static let confirmPressAt = 3.6
    static let confirmAt = 3.8
    static let closeAt = 6.2
    static let deleteAt = 6.4
    static let returnAt = 8.2
    // After a touch the loop waits this long, then starts again from the list.
    static let userHold = 3.0

    static let growSpring = Spring(duration: 0.5, bounce: 1 - 0.82)
    static let shrinkSpring = Spring(duration: 0.42, bounce: 1 - 0.9)
    static let contentAfter = 0.25
    static let contentFor = 0.7
    static let swapFor = 0.4
    static let drawFor = 0.4
    static let rippleFor = 0.6
    static let closeFor = 0.5
    static let fade = 0.2
    static let shakeDegrees = 8.0
    static let shakeFor = 0.07
    static let closeScale = 0.9
    static let scrimOpacity = 0.4

    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t) }
    static func outQuad(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }
    static func outCubic(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
    static func inQuad(_ t: Double) -> Double { t * t }
    static func linear(_ t: Double) -> Double { t }

    static func stage(_ value: Double, _ start: Double, _ length: Double) -> Double {
        min(1, max(0, (value - start) / length))
    }

    static func backOut(_ p: Double) -> Double {
        1 + 2.70158 * pow(p - 1, 3) + 1.70158 * pow(p - 1, 2)
    }
}

private typealias M = GrowDeleteDialogMotion
private typealias Channel = GrowDeleteDialogChannel
private typealias Row = GrowDeleteDialogFileRow

private enum Metrics {
    static let dialogWidth = 320.0
    static let dialogHeight = 248.0
    static let radiusFrom = 11.0
    static let radiusTo = 28.0
    static let tile = 58.0
    static let tileRadius = 18.0
    static let buttonHeight = 50.0
    static let buttonGap = 12.0
    static let side = 24.0
    static let fullWidth = dialogWidth - side * 2
    static let buttonWidth = (fullWidth - buttonGap) / 2

    // The storage card at the top: what each kind of file takes, as a share of the bar.
    static let storage: [(label: String, share: Double, color: UInt32)] = [
        ("Documents", 0.34, 0xe5484d),
        ("Photos", 0.26, 0x3b6fe0),
        ("Sheets", 0.12, 0x22a35a),
        ("Other", 0.08, 0xf0a100),
    ]
    static let tabs: [(label: String, symbol: String)] = [
        ("Recents", "clock.fill"),
        ("Shared", "person.2"),
        ("Browse", "folder"),
    ]
}

private enum Palette {
    static let red: Color = GrowDeleteDialogFileRow.trashInk
    static let redTile: UInt32 = 0xfdeced
    static let green: UInt32 = 0x52b992
    static let greenInk = Color(hex: 0x4cb890)
    static let greenTile: UInt32 = 0xe8f8f1
    static let page = Color(hex: 0xf5f5f7)
    static let ink = Color(hex: 0x111318)
    static let mute = Color(hex: 0x71757e)
    static let blue = Color(hex: 0x3b6fe0)
    static let tab = Color(hex: 0x9a9da5)
    static let scrim = Color(hex: 0x0b0d12)
    static let shadow = Color.rgba(17, 19, 24, 1)
}

private struct FramePreference: PreferenceKey {
    static let defaultValue: [Int: CGRect] = [:]
    static func reduce(value: inout [Int: CGRect], nextValue: () -> [Int: CGRect]) {
        value.merge(nextValue()) { $1 }
    }
}

private let screenSpace = "GrowDeleteDialogScreen"

/// A list of files. A trash button grows into a confirmation dialog: the dialog starts as the
/// button's own rounded square, measured on screen, and spreads to the centre while its content
/// pops in. Confirm turns the icon into a check that draws itself and the two buttons into one;
/// closing the dialog collapses the deleted row. Cancel folds the dialog back into the button it
/// came from.
struct GrowDeleteDialog: View {
    /// Presses a trash button by itself, confirms, and brings the file back, in a loop.
    var autoPlay = false
    var files = GrowDeleteDialogFile.samples
    var title = "Files"
    var deleteTitle = "Delete"
    var deleteMessage = "Are you sure you want to delete?"
    var successTitle = "Success"
    var successMessage = "Action is done successfully!"
    var cancelLabel = "Cancel"
    var confirmLabel = "Confirm"
    /// Called when the file's deletion is confirmed and the dialog is closed.
    var onDelete: ((GrowDeleteDialogFile) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var removed: [Int] = []
    @State private var target: Int? = nil
    @State private var pressed: Int? = nil
    @State private var confirmPressed = false
    @State private var succeeded = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var touching = false
    /// Where each trash button sits on the screen.
    @State private var trashFrames: [Int: CGRect] = [:]
    /// True while a row moves with no dialog open, so the clock only runs then.
    @State private var live = false
    @State private var phase = 0
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var medium = UIImpactFeedbackGenerator(style: .medium)

    // Where the dialog grows from: the trash button's rect on the screen.
    @State private var originX = 0.0
    @State private var originY = 0.0
    @State private var grow = Channel(0)
    @State private var content = Channel(0)
    @State private var done = Channel(0)
    @State private var draw = Channel(0)
    @State private var ripple = Channel(0)
    @State private var shake = Channel(0)
    @State private var out = Channel(0)

    private var byHand: Bool { !autoPlay || touches > 0 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )

            TimelineView(.animation(paused: !(live || target != nil))) { timeline in
                ZStack(alignment: .topLeading) {
                    page(at: timeline.date, top: insets.top + 8)
                    tabBar(bottom: insets.bottom)
                        .frame(width: screen.width, height: screen.height, alignment: .bottom)
                    overlay(at: timeline.date, screen: screen, top: insets.top)
                }
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            }
            .coordinateSpace(name: screenSpace)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Palette.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onPreferenceChange(FramePreference.self) { trashFrames = $0 }
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard autoPlay, !touching else { return }
                    touching = true
                    touches += 1
                }
                .onEnded { _ in touching = false }
        )
        .onAppear {
            light.prepare()
            medium.prepare()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(at date: Date, top: Double) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text(title)
                    .font(.system(size: 34, weight: .heavy))
                    .tracking(-1)
                    .foregroundStyle(Palette.ink)
                    .frame(height: 40)
                Spacer(minLength: 0)
                if byHand && !removed.isEmpty {
                    Button {
                        restore()
                    } label: {
                        Text("Restore files")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Palette.red)
                            .contentShape(Rectangle().inset(by: -10))
                    }
                    .buttonStyle(.plain)
                } else {
                    Image(systemName: "plus")
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(Palette.ink)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(.white).shadow(color: Palette.shadow.opacity(0.08), radius: 4, y: 2))
                }
            }
            .frame(height: 48)
            .padding(.bottom, 16)

            storage

            HStack(alignment: .firstTextBaseline) {
                Text("Recent files")
                    .font(.system(size: 20, weight: .heavy))
                    .tracking(-0.4)
                    .foregroundStyle(Palette.ink)
                Spacer(minLength: 0)
                Text("See all")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Palette.blue)
            }
            .frame(height: 25)
            .padding(.top, 24)
            .padding(.bottom, 12)

            ForEach(Array(files.enumerated()), id: \.offset) { index, file in
                Row(date: date, file: file, removed: removed.contains(index), pressed: pressed == index, onTrash: { trash(index) })
                    .background(
                        GeometryReader { row in
                            // The trash button sits at the row's right edge, inset, in its middle.
                            Color.clear.preference(
                                key: FramePreference.self,
                                value: [index: CGRect(
                                    x: row.frame(in: .named(screenSpace)).maxX - Row.trashInset - Row.trashSize,
                                    y: row.frame(in: .named(screenSpace)).minY + (Row.rowHeight - Row.trashSize) / 2,
                                    width: Row.trashSize, height: Row.trashSize
                                )]
                            )
                        }
                        .frame(height: Row.rowHeight)
                        .frame(maxHeight: .infinity, alignment: .top)
                    )
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, top)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Palette.page)
        .accessibilityHidden(target != nil)
    }

    private var storage: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Storage")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Palette.mute)
                        .frame(height: 17)
                    (Text("12.8 GB ").font(.system(size: 22, weight: .heavy)).tracking(-0.5).foregroundColor(Palette.ink)
                        + Text("of 16 GB used").font(.system(size: 15, weight: .medium)).foregroundColor(Palette.mute))
                        .frame(height: 28)
                }
                Spacer(minLength: 0)
                Image(systemName: "cloud.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(Palette.blue)
                    .frame(width: 36, height: 36)
                    .background(RoundedRectangle(cornerRadius: 11).fill(Color(hex: 0xe3ecfd)))
            }
            GeometryReader { bar in
                let gaps = Double(Metrics.storage.count) * 2
                HStack(spacing: 2) {
                    ForEach(Metrics.storage, id: \.label) { part in
                        Color(hex: part.color).frame(width: (bar.size.width - gaps) * part.share)
                    }
                }
            }
            .frame(height: 8)
            .background(Color(hex: 0xeef0f3))
            .clipShape(Capsule())
            .padding(.top, 14)
            HStack(spacing: 14) {
                ForEach(Metrics.storage, id: \.label) { part in
                    HStack(spacing: 6) {
                        Circle().fill(Color(hex: part.color)).frame(width: 8, height: 8)
                        Text(part.label)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Palette.mute)
                            .frame(height: 16)
                    }
                }
            }
            .padding(.top, 12)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.white)
                .shadow(color: Palette.shadow.opacity(0.04), radius: 1, y: 1)
                .shadow(color: Palette.shadow.opacity(0.05), radius: 9, y: 6)
        )
    }

    private func tabBar(bottom: Double) -> some View {
        HStack(spacing: 0) {
            ForEach(Metrics.tabs.indices, id: \.self) { i in
                let tint = i == 0 ? Palette.blue : Palette.tab
                VStack(spacing: 2) {
                    Image(systemName: Metrics.tabs[i].symbol)
                        .font(.system(size: 20))
                        .frame(height: 24)
                    Text(Metrics.tabs[i].label)
                        .font(.system(size: 11, weight: .semibold))
                        .frame(height: 14)
                }
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, bottom)
        .background(Color.white.opacity(0.96))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.rgba(60, 60, 67, 0.2)).frame(height: 1 / displayScale)
        }
        .accessibilityHidden(target != nil)
    }

    // MARK: The dialog

    private func overlay(at date: Date, screen: CGSize, top: Double) -> some View {
        let g = grow.value(at: date)
        let e = out.value(at: date)
        let cx = (screen.width - Metrics.dialogWidth) / 2
        let cy = max(top + 20, (screen.height - Metrics.dialogHeight) / 2)
        let scrim = M.scrimOpacity * min(1, max(0, g * 1.5)) * (1 - e)
        let frame = reduceMotion
            ? CGRect(x: cx, y: cy, width: Metrics.dialogWidth, height: Metrics.dialogHeight)
            : CGRect(
                x: originX + (cx - originX) * g,
                y: originY + (cy - originY) * g,
                width: Row.trashSize + (Metrics.dialogWidth - Row.trashSize) * g,
                height: Row.trashSize + (Metrics.dialogHeight - Row.trashSize) * g
            )
        let radius = reduceMotion ? Metrics.radiusTo : Metrics.radiusFrom + (Metrics.radiusTo - Metrics.radiusFrom) * min(g, 1.1)
        let opacity = reduceMotion ? min(1, max(0, g)) * (1 - e) : min(1, max(0, g) * 3) * (1 - e)
        let scale = reduceMotion ? 1 : 1 - (1 - M.closeScale) * e

        return ZStack(alignment: .topLeading) {
            Palette.scrim.opacity(scrim).allowsHitTesting(false)

            if target != nil {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture { succeeded ? close(deleteNow: true) : fold() }
                    .accessibilityLabel("Dismiss")

                ZStack(alignment: .topLeading) {
                    holder(at: date)
                        .offset(x: -(Metrics.dialogWidth - frame.width) / 2, y: -(Metrics.dialogHeight - frame.height) / 2)
                }
                .frame(width: frame.width, height: frame.height, alignment: .topLeading)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
                .shadow(color: Color.rgba(11, 13, 18, 0.1), radius: 5, y: 4)
                .shadow(color: Color.rgba(11, 13, 18, 0.28), radius: 28, y: 24)
                .scaleEffect(scale)
                .opacity(opacity)
                .offset(x: frame.minX, y: frame.minY)
            }
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
    }

    /// The content keeps its own size and sits in the middle of the growing frame.
    private func holder(at date: Date) -> some View {
        let c = content.value(at: date)
        let d = done.value(at: date)
        let p = reduceMotion ? (c > 0 ? 1 : 0) : M.backOut(M.stage(c, 0, 0.4))
        let ripples = ripple.value(at: date)
        let rise: (Double, Double) -> Double = { start, length in
            reduceMotion ? 0 : (1 - M.stage(c, start, length)) * 8
        }
        let fade: (Double, Double) -> Double = { start, length in
            reduceMotion ? (c > 0 ? 1 : 0) : M.stage(c, start, length)
        }

        return ZStack(alignment: .topLeading) {
            ZStack {
                RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous)
                    .strokeBorder(Palette.greenInk, lineWidth: 2)
                    .frame(width: Metrics.tile, height: Metrics.tile)
                    .opacity(0.45 * (1 - ripples) * (ripples > 0 ? 1 : 0))
                    .scaleEffect(1 + ripples * 0.9)
                ZStack {
                    Image(systemName: "trash")
                        .font(.system(size: 23))
                        .foregroundStyle(Palette.red)
                        .opacity(1 - d)
                        .scaleEffect(1 - d * 0.4)
                    check(draw: draw.value(at: date))
                        .opacity(min(1, d * 3))
                }
                .frame(width: Metrics.tile, height: Metrics.tile)
                .background(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous).fill(Self.mix(Palette.redTile, Palette.greenTile, d)))
                .scaleEffect(max(p, 0))
                .rotationEffect(.degrees(shake.value(at: date)))
                .opacity(min(1, p * 2))
            }
            .frame(width: Metrics.tile, height: Metrics.tile)
            .offset(x: (Metrics.dialogWidth - Metrics.tile) / 2, y: 28)

            swapping(deleteTitle, successTitle, size: 21, height: 26, weight: .bold, tracking: -0.4, ink: Palette.ink, done: d)
                .opacity(fade(0.09, 0.4))
                .offset(x: Metrics.side, y: 102 + rise(0.09, 0.4))
            swapping(deleteMessage, successMessage, size: 15, height: 21, weight: .regular, ink: Palette.mute, done: d)
                .opacity(fade(0.18, 0.4))
                .offset(x: Metrics.side, y: 134 + rise(0.18, 0.4))

            buttons(done: d)
                .opacity(fade(0.27, 0.4))
                .offset(x: Metrics.side, y: 176 + rise(0.27, 0.4))

            Button {
                fold()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 26, height: 26)
                    .background(Circle().fill(Color(hex: 0x5f636b)))
                    .contentShape(Rectangle().inset(by: -8))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .opacity(1 - d)
            .allowsHitTesting(!succeeded)
            .offset(x: Metrics.dialogWidth - 14 - 26, y: 14)
        }
        .frame(width: Metrics.dialogWidth, height: Metrics.dialogHeight, alignment: .topLeading)
    }

    /// Two lines of text one over the other: the confirmation turns into the success.
    private func swapping(
        _ first: String, _ second: String, size: Double, height: Double, weight: Font.Weight, tracking: Double = 0, ink: Color, done: Double
    ) -> some View {
        ZStack {
            Text(first).opacity(1 - done)
            Text(second).opacity(done)
        }
        .font(.system(size: size, weight: weight))
        .tracking(tracking)
        .foregroundStyle(ink)
        .multilineTextAlignment(.center)
        .frame(width: Metrics.fullWidth, height: height)
    }

    private func buttons(done: Double) -> some View {
        let confirmWidth = Metrics.buttonWidth + (Metrics.fullWidth - Metrics.buttonWidth) * done
        return ZStack(alignment: .topLeading) {
            Button {
                fold()
            } label: {
                Text(cancelLabel)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .frame(width: Metrics.buttonWidth * (1 - done), height: Metrics.buttonHeight)
                    .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(Color(hex: 0xf2f3f5)))
                    .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    .opacity(max(0, 1 - done * 1.5))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(succeeded)

            Button {
                confirmByHand()
            } label: {
                Text(confirmLabel)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(width: confirmWidth, height: Metrics.buttonHeight)
                    .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(Self.mix(0xd14b5a, Palette.green, done)))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .scaleEffect(confirmPressed ? 0.96 : 1)
            .offset(x: (Metrics.buttonWidth + Metrics.buttonGap) * (1 - done))
        }
        .frame(width: Metrics.fullWidth, height: Metrics.buttonHeight, alignment: .topLeading)
    }

    /// The check inside the success ring, in a 30 point box.
    private func check(draw: Double) -> some View {
        ZStack {
            Circle().stroke(Palette.greenInk, lineWidth: 2.2).frame(width: 25, height: 25)
            Path { path in
                path.move(to: CGPoint(x: 9.5, y: 15.5))
                path.addLine(to: CGPoint(x: 13.2, y: 19))
                path.addLine(to: CGPoint(x: 20.5, y: 11))
            }
            .trim(from: 0, to: draw)
            .stroke(Palette.greenInk, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            .frame(width: 30, height: 30, alignment: .topLeading)
        }
        .frame(width: 30, height: 30)
    }

    /// A straight mix in RGB, so a fading fill keeps its colour.
    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let from = Double((a >> shift) & 0xff)
            let to = Double((b >> shift) & 0xff)
            return (from + (to - from) * t) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }

    // MARK: Behaviour

    /// Keeps the clock running for a while after a row starts to move.
    private func wake(_ seconds: Double) {
        live = true
        phase += 1
        let token = phase
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds), tolerance: .zero)
            if token == phase { live = false }
        }
    }

    private func open(_ index: Int) {
        guard let button = trashFrames[index] else { return }
        phase += 1
        originX = button.minX
        originY = button.minY
        target = index
        succeeded = false
        out.set(0)
        done.set(0)
        draw.set(0)
        ripple.set(0)
        if reduceMotion {
            grow.go(to: 1, .timing(M.fade, M.inOutQuad))
            content.go(to: 1, .timing(M.fade, M.inOutQuad))
            return
        }
        grow.go(to: 1, .spring(M.growSpring))
        content.go(to: 1, .timing(M.contentFor, M.linear), delay: M.contentAfter)
    }

    /// The dialog is closed: the next one starts from the confirmation.
    private func finish() {
        grow.set(0)
        content.set(0)
        out.set(0)
        done.set(0)
        draw.set(0)
        target = nil
        succeeded = false
    }

    private func finish(after seconds: Double) {
        phase += 1
        let token = phase
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds), tolerance: .zero)
            if token == phase { finish() }
        }
    }

    private func fold() {
        content.go(to: 0, .timing(0.15, M.inOutQuad))
        if reduceMotion {
            grow.go(to: 0, .timing(M.fade, M.inOutQuad), delay: 0.1)
            finish(after: 0.1 + M.fade)
        } else {
            grow.go(to: 0, .spring(M.shrinkSpring), delay: 0.1)
            finish(after: 0.1 + M.shrinkSpring.settlingDuration)
        }
    }

    private func confirm() {
        guard !succeeded, target != nil else { return }
        succeeded = true
        done.go(to: 1, .timing(M.swapFor, M.inOutQuad))
        draw.go(to: 1, .timing(M.drawFor, M.outCubic), delay: M.swapFor * 0.4)
        if reduceMotion { return }
        ripple.set(0)
        ripple.go(to: 1, .timing(M.rippleFor, M.outQuad))
    }

    private func removeFile(_ index: Int) {
        if !removed.contains(index) { removed.append(index) }
        wake(1)
        onDelete?(files[index])
    }

    private func restore() {
        removed = []
        wake(1)
    }

    /// Close the success dialog; the file it was about goes.
    private func close(deleteNow: Bool) {
        let index = target
        let seconds = reduceMotion ? M.fade : M.closeFor
        out.go(to: 1, .timing(seconds, M.inQuad))
        finish(after: seconds)
        if let index, deleteNow { removeFile(index) }
    }

    private func shakeIcon() {
        if reduceMotion { return }
        let d = M.shakeDegrees
        let f = M.shakeFor
        shake.go([
            .init(to: d, curve: .timing(f, M.inOutQuad)),
            .init(to: -d, curve: .timing(f * 2, M.inOutQuad)),
            .init(to: d, curve: .timing(f * 2, M.inOutQuad)),
            .init(to: -d, curve: .timing(f * 2, M.inOutQuad)),
            .init(to: d, curve: .timing(f * 2, M.inOutQuad)),
            .init(to: 0, curve: .timing(f, M.inOutQuad)),
        ])
    }

    private func trash(_ index: Int) {
        guard target == nil else { return }
        light.impactOccurred()
        open(index)
    }

    private func confirmByHand() {
        medium.impactOccurred()
        if !succeeded { confirm() } else { close(deleteNow: true) }
    }

    // MARK: The autoPlay loop

    private struct Step {
        let at: Double
        let run: @MainActor (GrowDeleteDialog) -> Void
    }

    private static let steps = [
        Step(at: M.pressAt) { $0.pressed = M.targetRow },
        Step(at: M.openAt) {
            $0.pressed = nil
            $0.open(M.targetRow)
        },
        Step(at: M.shakeAt) { $0.shakeIcon() },
        Step(at: M.confirmPressAt) { $0.confirmPressed = true },
        Step(at: M.confirmAt) {
            $0.confirmPressed = false
            $0.confirm()
        },
        Step(at: M.closeAt) { $0.close(deleteNow: false) },
        Step(at: M.deleteAt) { $0.removeFile(M.targetRow) },
        Step(at: M.returnAt) { $0.restore() },
    ]

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            pressed = nil
            confirmPressed = false
            restore()
            if target != nil { close(deleteNow: false) }
            try? await Task.sleep(for: .seconds(0.8), tolerance: .zero)
        }
        var origin = clock.now
        while !Task.isCancelled {
            for step in Self.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run(self)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}
