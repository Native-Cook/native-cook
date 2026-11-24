import CoreText
import SwiftUI
import UIKit

private typealias M = ScrubNumberFieldMotion
private typealias Track = ScrubNumberFieldTrack

// ---------------------------------------------------------------- look and layout (pt)

private enum Look {
    static let page = Color(hex: 0x111312)
    static let panel = Color(hex: 0x1C1F1D)
    static let ink = Color(hex: 0xF0EDE5)
    static let mute = Color(hex: 0x8D938E)
    static let accent = Color(hex: 0xF2B441)
    static let tickMinor = Color.rgba(240, 237, 229, 0.30)
    static let tickMajor = Color.rgba(240, 237, 229, 0.62)

    static let side = 16.0 // screen margin
    static let navH = 44.0
    static let captionH = 20.0
    static let fieldGap = 10.0
    static let fieldsGap = 16.0 // caption to the first field
    static let hintGap = 12.0
    static let hintH = 18.0
    static let fieldRadius = 20.0
    static let photoRadius = 22.0
    static let numberRight = 18.0 // number's right edge from the field's
    static let rulerBottom = 10.0 // ruler baseline from the field's bottom edge
    static let rulerFade = 0.2 // share of the width the ruler fades out over, each side
    static let dot = 44.0 // the ghost finger
    static let dragFrom = 0.44 // where across a field the ghost finger lands
    static let dragUp = 20.0 // its centre above the field's bottom edge: on the ruler, under the number
    static let tapAt = CGPoint(x: 0.56, y: 0.56) // where on the photo it double taps

    static let idle = 2.0 // s after the last touch, before the values ease back to rest
    static let returnS = 0.7 // the ease back, then the film restarts at t = 0
    static let returnHold = 0.15 // s at rest before the film takes over again
    static let columns = 3 // digits a field can show
    static let smearPad = 28.0 // room above and below a field for the smear to sample from

    static let hair = 1 / max(UIScreen.main.scale, 1)
}

/// The digit face and what the odometer needs to know about it.
private struct ScrubNumberFieldDigits {
    let size: Double
    let scale: Double // the glyphs are set at `opticalSize` pt and drawn this much larger
    let widths: [String: Double]
    let colW: Double // one digit column
    let cellH: Double // distance a digit rolls
    let baseline: Double // baseline below a cell's centre

    static let glyphs = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"]
    static let plus = "+"
    static let minus = "−"
    static let opticalSize = 17.0

    private static var cache: [Double: ScrubNumberFieldDigits] = [:]

    static func make(_ size: Double) -> ScrubNumberFieldDigits {
        if let hit = cache[size] { return hit }
        // `as CTFont`, not CTFontCreateWithName: the latter answers with Times for a system font.
        // Skia's system face keeps the text optical size at any point size (wider, sturdier digits
        // and a wider point than the display cut UIKit picks at 30 pt). The system font does not
        // take an opsz variation on iOS, so the glyphs are set at 17 pt and scaled up when drawn.
        let font = UIFont.systemFont(ofSize: opticalSize, weight: .semibold) as CTFont
        let scale = size / opticalSize
        var widths: [String: Double] = [:]
        var colW = 0.0
        var zeroBox = CGRect.zero
        for (i, c) in (glyphs + [plus, minus, "."]).enumerated() {
            var chars = Array(c.utf16)
            var glyph = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, &chars, &glyph, chars.count)
            var advance = CGSize.zero
            CTFontGetAdvancesForGlyphs(font, .horizontal, glyph, &advance, 1)
            widths[c] = advance.width * scale
            if i < 10 { colW = max(colW, advance.width * scale) }
            if i == 0 { CTFontGetBoundingRectsForGlyphs(font, .horizontal, glyph, &zeroBox, 1) }
        }
        // the "0" sits centred on its cell
        let baseline = zeroBox.height > 0 ? (zeroBox.minY + zeroBox.height / 2) * scale : size * 0.35
        let made = ScrubNumberFieldDigits(size: size, scale: scale, widths: widths, colW: ceil(colW) + 1, cellH: (size * 1.2).rounded(), baseline: baseline)
        cache[size] = made
        return made
    }
}

/// Number fields you scrub like in a design tool: press one and drag sideways. A ruler slides under
/// the number, the digits roll like an odometer (and smear when the drag is fast, which also makes
/// the steps coarser), the field stretches after the finger and a spring snaps to the step on
/// release. Three of them edit a photo live through one Metal shader.
struct ScrubNumberField: View {
    static let loop: Double = ScrubNumberFieldMotion.loop

    var autoPlay = true

    init(autoPlay: Bool = true) { self.autoPlay = autoPlay }

    private struct FieldState {
        var pos: Track
        var active = Track.hold(0)
        var stretch = Track.hold(0)
        var raw = 0.0 // pos before rubber-banding, while a finger scrubs
        var pressed = false
        var dragging = false
        var lastX = 0.0
        var shown: Double // the settled step, for screen readers
    }

    /// What the fingers last did. The idle wait, the ease back to rest and the film starting over
    /// are derived from the clock, so nothing is written per frame.
    private struct Session {
        var manual = false
        var fields = M.fields.map { FieldState(pos: .hold($0.rest), shown: $0.rest) }
        var lastTouch = 0.0
        var loopStart = Date.now.timeIntervalSinceReferenceDate
        var holding = 0 // fingers down
    }

    /// One instant of the screen.
    private struct Snapshot {
        var auto = false
        var tau = 0.0
        var pos = [Double](repeating: 0, count: 3)
        var active = [Double](repeating: 0, count: 3)
        var stretch = [Double](repeating: 0, count: 3)
        var speed = [Double](repeating: 0, count: 3)
    }

    /// Under a finger the value's speed is measured between frames (a one-pole filter), as in React
    /// Native. Kept outside SwiftUI state: it is read and advanced while drawing.
    private final class Meter {
        var speed = [Double](repeating: 0, count: 3)
        var prev = M.rest
        var last = 0.0
        var lastTick = 0.0

        func seed(_ pos: [Double], _ speed: [Double], _ now: Double) {
            prev = pos
            self.speed = speed
            last = now
        }

        func advance(_ pos: [Double], _ now: Double) -> [Double] {
            guard now > last else { return speed }
            let dt = M.clamp(now - last, 1.0 / 240, 0.05)
            last = now
            let k = 1 - exp(-dt / M.speedTau)
            for i in 0..<3 {
                let s = speed[i] + (abs(pos[i] - prev[i]) / dt - speed[i]) * k
                speed[i] = s < 0.05 ? 0 : s
            }
            prev = pos
            return speed
        }
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var session = Session()
    @State private var meter = Meter()
    @State private var settled = true // without the film: nothing is moving, the clock may stop
    @State private var settleTask: Task<Void, Never>?
    private let detent = UISelectionFeedbackGenerator()
    private let impact = UIImpactFeedbackGenerator(style: .light)

    private static let photo: UIImage? = {
        guard let url = Bundle.main.url(forResource: "ScrubNumberField-valley", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return image.preparingForDisplay() ?? image
    }()

    private var film: Bool { autoPlay && !reduceMotion }
    private var clock: Double { Date.now.timeIntervalSinceReferenceDate }

    // ------------------------------------------------------------ the state at an instant

    private func filmSnapshot(_ tau: Double) -> Snapshot {
        var s = Snapshot(auto: true, tau: tau)
        for i in 0..<M.fieldCount {
            s.pos[i] = M.filmPos(i, tau)
            s.active[i] = M.filmActive(i, tau)
            s.stretch[i] = M.filmStretch(i, tau)
            s.speed[i] = M.filmSpeed(i, tau)
        }
        return s
    }

    /// The state at `now`, without the measured speed.
    private func resolve(_ now: Double) -> Snapshot {
        if film && !session.manual {
            return filmSnapshot(max(0, (now - session.loopStart).truncatingRemainder(dividingBy: M.loop)))
        }
        let returnAt = session.lastTouch + Look.idle
        let returning = film && session.holding <= 0 && now > returnAt
        if returning && now > returnAt + Look.returnS + Look.returnHold {
            return filmSnapshot((now - returnAt - Look.returnS - Look.returnHold).truncatingRemainder(dividingBy: M.loop))
        }
        var s = Snapshot()
        for i in 0..<M.fieldCount {
            let f = session.fields[i]
            if returning {
                let from = f.pos.value(at: returnAt)
                s.pos[i] = from + (M.rest[i] - from) * M.easeInOutCubic((now - returnAt) / Look.returnS)
            } else {
                s.pos[i] = f.pos.value(at: now)
            }
            s.active[i] = f.active.value(at: now)
            s.stretch[i] = f.stretch.value(at: now)
        }
        return s
    }

    private func snapshot(_ now: Double) -> Snapshot {
        var s = resolve(now)
        if s.auto { meter.seed(s.pos, s.speed, now) } else { s.speed = meter.advance(s.pos, now) }
        return s
    }

    // ------------------------------------------------------------ fingers

    /// A finger anywhere: the film stops and every value settles on the step it is nearest.
    private func take(_ now: Double) {
        let cur = resolve(now)
        let returnAt = session.lastTouch + Look.idle
        if cur.auto {
            for i in 0..<M.fieldCount {
                session.fields[i].pos = Track(kind: .spring, from: cur.pos[i], to: cur.pos[i].rounded(), t0: now)
                session.fields[i].active = Track(kind: .quad, from: cur.active[i], to: 0, t0: now, duration: 0.2)
                session.fields[i].stretch = Track(kind: .quad, from: cur.stretch[i], to: 0, t0: now, duration: 0.2)
            }
        } else if film && session.manual && session.holding <= 0 && now > returnAt {
            // caught on the way back to rest: keep that move
            for i in 0..<M.fieldCount {
                let from = session.fields[i].pos.value(at: returnAt)
                session.fields[i].pos = Track(kind: .cubic, from: from, to: M.rest[i], t0: returnAt, duration: Look.returnS)
            }
        }
        session.manual = true
        session.lastTouch = now
        wake()
    }

    /// Without the film the clock only runs while something can still be moving.
    private func wake() {
        guard !film else { return }
        settled = false
        settleTask?.cancel()
        settleTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.6))
            if !Task.isCancelled && session.holding <= 0 { settled = true }
        }
    }

    /// A double tap: one field (or all, index < 0) rolls back to where the screen opened.
    private func reset(_ index: Int) {
        let now = clock
        take(now)
        var n = 0
        for i in 0..<M.fieldCount where index < 0 || i == index {
            let from = session.fields[i].pos.value(at: now)
            let delay = reduceMotion ? 0 : Double(n) * M.resetStagger
            session.fields[i].pos = Track(kind: .cubic, from: from, to: M.rest[i], t0: now + delay, duration: reduceMotion ? 0.18 : M.resetS)
            session.fields[i].shown = M.rest[i]
            n += 1
        }
        impact.impactOccurred()
    }

    private func dragChanged(_ i: Int, _ value: DragGesture.Value) {
        let now = clock
        let spec = M.fields[i]
        if !session.fields[i].pressed {
            take(now)
            session.fields[i].pressed = true
            session.fields[i].dragging = false
            session.holding += 1
            session.fields[i].active = Track(kind: .quad, from: session.fields[i].active.value(at: now), to: 1, t0: now, duration: 0.16)
            detent.prepare()
        }
        let x = value.translation.width
        if !session.fields[i].dragging && abs(x) > 4 {
            // catch the value wherever its spring is
            session.fields[i].raw = session.fields[i].pos.value(at: now)
            session.fields[i].dragging = true
            session.fields[i].lastX = x
        }
        if session.fields[i].dragging {
            let vx = value.velocity.width
            let before = session.fields[i].pos.value(at: now).rounded()
            let next = session.fields[i].raw + (x - session.fields[i].lastX) / M.tick * M.scrubGain(abs(vx))
            session.fields[i].lastX = x
            // the raw value cannot run away past the limits: keep it within reach of the rubber band
            session.fields[i].raw = M.clamp(next, spec.min - 60, spec.max + 60)
            let pos = M.rubber(session.fields[i].raw, spec.min, spec.max)
            session.fields[i].pos = .hold(pos)
            if !reduceMotion {
                let st = session.fields[i].stretch.value(at: now)
                session.fields[i].stretch = .hold(st + (M.clamp(vx / M.stretchSpeed, -1, 1) - st) * 0.3)
            }
            // a detent for every step that passes under the finger, at most about 14 a second
            if pos.rounded() != before && now - meter.lastTick >= 0.07 {
                meter.lastTick = now
                detent.selectionChanged()
            }
        }
        session.lastTouch = now
    }

    private func dragEnded(_ i: Int, _ value: DragGesture.Value) { release(i, vx: value.velocity.width) }

    /// The finger is gone, lifted (with its speed) or cancelled by the system (vx = 0).
    private func release(_ i: Int, vx: Double) {
        let now = clock
        let spec = M.fields[i]
        guard session.fields[i].pressed else { return }
        if session.fields[i].dragging {
            let v = vx / M.tick * M.scrubGain(abs(vx)) // steps per second
            let p = session.fields[i].pos.value(at: now)
            let inside = p >= spec.min && p <= spec.max
            let goal = M.clamp((p + (inside ? v * M.project : 0)).rounded(), spec.min, spec.max)
            session.fields[i].pos = reduceMotion
                ? Track(kind: .quad, from: p, to: goal, t0: now, duration: 0.16)
                : Track(kind: .spring, from: p, to: goal, v0: inside ? v : 0, t0: now)
            session.fields[i].shown = goal
        }
        session.holding = max(0, session.holding - 1)
        session.fields[i].pressed = false
        session.fields[i].dragging = false
        session.fields[i].active = Track(kind: .quad, from: session.fields[i].active.value(at: now), to: 0, t0: now, duration: 0.32)
        session.fields[i].stretch = Track(kind: .spring, from: session.fields[i].stretch.value(at: now), to: 0, t0: now)
        session.lastTouch = now
        wake()
    }

    private func nudge(_ i: Int, by: Double) {
        let now = clock
        take(now)
        let spec = M.fields[i]
        let goal = min(spec.max, max(spec.min, session.fields[i].shown + by))
        session.fields[i].shown = goal
        session.fields[i].pos = Track(kind: .quad, from: session.fields[i].pos.value(at: now), to: goal, t0: now, duration: 0.16)
    }

    // ------------------------------------------------------------ the screen

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let width = geo.size.width + insets.leading + insets.trailing
            let height = geo.size.height + insets.top + insets.bottom
            let compact = height < 740
            let contentW = min(width, 480)
            let w = contentW - Look.side * 2
            let left = (width - contentW) / 2 + Look.side
            let fieldH = compact ? 66.0 : 76.0
            let navTop = insets.top + 4
            let photoTop = navTop + Look.navH + (compact ? 8 : 12)
            // the photo takes the height the fields leave: close to square on a tall phone
            let fieldsBlock = Double(M.fieldCount) * fieldH + Double(M.fieldCount - 1) * Look.fieldGap
            let below = 12 + Look.captionH + Look.fieldsGap + fieldsBlock + Look.hintGap + Look.hintH + max(insets.bottom, 12) + 8
            let photoH = M.clamp(height - photoTop - below, w * 0.5, w * 1.25).rounded()
            let captionTop = photoTop + photoH + 12
            let fieldsTop = captionTop + Look.captionH + Look.fieldsGap
            let hintTop = fieldsTop + fieldsBlock + Look.hintGap
            let digits = ScrubNumberFieldDigits.make(compact ? 28 : 30)

            TimelineView(.animation(paused: !film && settled)) { context in
                let s = snapshot(context.date.timeIntervalSinceReferenceDate)
                ZStack(alignment: .topLeading) {
                    nav.frame(width: w, height: Look.navH).offset(x: left, y: navTop)

                    photo(s, width: w, height: photoH).offset(x: left, y: photoTop)

                    caption.frame(width: w, height: Look.captionH).offset(x: left, y: captionTop)

                    ForEach(0..<M.fieldCount, id: \.self) { i in
                        field(i, s, digits: digits, width: w, height: fieldH)
                            .offset(x: left, y: fieldsTop + Double(i) * (fieldH + Look.fieldGap))
                    }

                    Text("Drag a value sideways to change it. Double-tap to reset.")
                        .font(.system(size: 13))
                        .foregroundStyle(Look.mute)
                        .lineLimit(1)
                        .frame(width: w, height: Look.hintH, alignment: .top)
                        .offset(x: left, y: hintTop)

                    ghost(
                        s,
                        dragX: left + w * Look.dragFrom, fieldY: fieldsTop + fieldH - Look.dragUp, fieldPitch: fieldH + Look.fieldGap,
                        tapX: left + w * Look.tapAt.x, tapY: photoTop + photoH * Look.tapAt.y
                    )
                }
                .frame(width: width, height: height, alignment: .topLeading)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Look.page.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { session = Session() }
        .onDisappear { settleTask?.cancel() }
    }

    private var nav: some View {
        HStack(spacing: 0) {
            Text("Cancel").font(.system(size: 17)).foregroundStyle(Look.mute).frame(width: 72, alignment: .leading)
            Spacer(minLength: 0)
            Text("Adjust").font(.system(size: 17, weight: .semibold)).foregroundStyle(Look.ink)
            Spacer(minLength: 0)
            Text("Done")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Look.page)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(Look.accent, in: RoundedRectangle(cornerRadius: 16))
                .frame(width: 72, alignment: .trailing)
        }
    }

    private var caption: some View {
        HStack(spacing: 0) {
            Text("Valley overlook").font(.system(size: 14, weight: .semibold)).foregroundStyle(Look.ink)
            Spacer(minLength: 0)
            Text("35 mm · f/8 · 1/640 s · ISO 100").font(.system(size: 13)).monospacedDigit().foregroundStyle(Look.mute)
        }
        .padding(.horizontal, 4)
    }

    private func photo(_ s: Snapshot, width: Double, height: Double) -> some View {
        let shape = RoundedRectangle(cornerRadius: Look.photoRadius)
        return ZStack {
            if let image = Self.photo {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipped()
                    .colorEffect(ShaderLibrary.scrubNumberFieldGrade(
                        .float2(Float(width), Float(height)),
                        .float(Float(s.pos[0] * 0.05)),
                        .float(Float(s.pos[1] * 0.01)),
                        .float(Float(max(0, s.pos[2] * 0.01))),
                        .float(Float(UIScreen.main.scale))
                    ))
                    .clipShape(shape)
            }
        }
        .frame(width: width, height: height)
        .background {
            shape.fill(Look.panel)
                .shadow(color: .black.opacity(0.35), radius: 2, y: 2)
                .shadow(color: .black.opacity(0.35), radius: 20, y: 18)
        }
        .overlay { shape.strokeBorder(Color.white.opacity(0.16), lineWidth: Look.hair) }
        .contentShape(shape)
        .gesture(
            TapGesture(count: 2).onEnded { reset(-1) }
                .simultaneously(with: DragGesture(minimumDistance: 0).onChanged { _ in
                    if !session.manual || clock - session.lastTouch > 0.05 { take(clock) }
                })
        )
        .accessibilityElement()
        .accessibilityLabel("A valley between snowy mountains, seen over spruce trees")
        .accessibilityHint("Double tap twice to reset the adjustments")
        .accessibilityAddTraits(.isImage)
    }

    // ------------------------------------------------------------ one field

    private func field(_ i: Int, _ s: Snapshot, digits: ScrubNumberFieldDigits, width: Double, height: Double) -> some View {
        let spec = M.fields[i]
        let shape = RoundedRectangle(cornerRadius: Look.fieldRadius, style: .continuous)
        let active = s.active[i]
        // the field grows towards the finger's direction; its trailing edge stays where it is
        let st = s.stretch[i]
        let sx = 1 + M.stretchMax * abs(st)
        let sy = 1 - M.squashMax * abs(st)
        let sigma = reduceMotion ? 0 : M.digitBlur(s.speed[i])

        return ZStack(alignment: .topLeading) {
            shape.fill(Look.panel)
                .shadow(color: .black.opacity(0.40), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.22), radius: 12, y: 10)
            // a hairline highlight along the top edge
            shape.strokeBorder(
                LinearGradient(colors: [.white.opacity(0.10), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.22)),
                lineWidth: Look.hair
            )
            // pressed: a faint fill and an amber edge
            shape.fill(Color.rgba(240, 237, 229, 0.045))
                .overlay { shape.strokeBorder(Color.rgba(242, 180, 65, 0.55), lineWidth: 1) }
                .opacity(active)

            HStack(spacing: 9) {
                Image(systemName: spec.symbol)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Look.mute)
                    .frame(width: 20, height: 20)
                Text(spec.label).font(.system(size: 16, weight: .medium)).tracking(-0.2).foregroundStyle(Look.ink)
            }
            .frame(height: height)
            .padding(.leading, 16)

            ruler(spec, pos: s.pos[i], width: width, height: height).opacity(active)

            Color.clear
                .frame(width: width, height: height)
                .overlay(alignment: .top) {
                    number(spec, pos: s.pos[i], lift: -M.lift * active, sigma: sigma, digits: digits, width: width, height: height)
                }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .contentShape(Rectangle())
        .modifier(ScrubNumberFieldFieldGesture(
            changed: { dragChanged(i, $0) },
            ended: { dragEnded(i, $0) },
            cancelled: { release(i, vx: 0) },
            doubleTap: { reset(i) }
        ))
        .scaleEffect(x: sx, y: sy)
        .offset(x: (st < 0 ? -1 : 1) * (sx - 1) * width / 2)
        .accessibilityElement()
        .accessibilityLabel(spec.label)
        .accessibilityValue("\(M.formatValue(spec, session.fields[i].shown)) \(spec.unit)".trimmingCharacters(in: .whitespaces))
        .accessibilityHint("Drag sideways to change. Double tap twice to reset.")
        .accessibilityAdjustableAction { direction in
            nudge(i, by: direction == .increment ? 1 : -1)
        }
    }

    /// The ruler: every step a tick, the value under a fixed amber index in the middle, its ends
    /// faded out inside one layer.
    private func ruler(_ spec: ScrubNumberFieldSpec, pos: Double, width: Double, height: Double) -> some View {
        Canvas { ctx, _ in
            let rulerY = height - Look.rulerBottom
            let centre = width / 2
            ctx.drawLayer { layer in
                let lo = max(Int(spec.min), Int(floor(pos - centre / M.tick)) - 1)
                let hi = min(Int(spec.max), Int(ceil(pos + centre / M.tick)) + 1)
                var minor = Path()
                var major = Path()
                if lo <= hi {
                    for step in lo...hi where step != 0 {
                        let x = centre + (Double(step) - pos) * M.tick
                        if step % M.majorEvery == 0 {
                            major.move(to: CGPoint(x: x, y: rulerY))
                            major.addLine(to: CGPoint(x: x, y: rulerY - 9))
                        } else {
                            minor.move(to: CGPoint(x: x, y: rulerY))
                            minor.addLine(to: CGPoint(x: x, y: rulerY - 5))
                        }
                    }
                }
                layer.stroke(minor, with: .color(Look.tickMinor), lineWidth: 1)
                layer.stroke(major, with: .color(Look.tickMajor), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                if spec.min < 0 {
                    let x = centre - pos * M.tick
                    layer.fill(Path(roundedRect: CGRect(x: x - 1, y: rulerY - 12, width: 2, height: 12), cornerRadius: 1), with: .color(Look.ink))
                }
                layer.blendMode = .destinationIn
                layer.fill(
                    Path(CGRect(x: -2, y: rulerY - 16, width: width + 4, height: 18)),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: .clear, location: 0), .init(color: .white, location: Look.rulerFade),
                            .init(color: .white, location: 1 - Look.rulerFade), .init(color: .clear, location: 1),
                        ]),
                        startPoint: .zero, endPoint: CGPoint(x: width, y: 0)
                    )
                )
            }
            ctx.fill(Path(roundedRect: CGRect(x: centre - 1, y: rulerY - 15, width: 2, height: 15), cornerRadius: 1), with: .color(Look.accent))
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
    }

    /// The number, always shown: one strip of digits per column, right-aligned at a fixed inset. The
    /// canvas is taller than the field so the smear has the strips above and below the window to
    /// sample; the shader blurs the two fastest columns vertically and cuts them to the window.
    private func number(
        _ spec: ScrubNumberFieldSpec, pos: Double, lift: Double, sigma: Double, digits: ScrubNumberFieldDigits, width: Double, height: Double
    ) -> some View {
        let pad = Look.smearPad
        let ups = spec.unitsPerStep
        let colW = digits.colW
        let cellH = digits.cellH
        let minDigits = spec.decimals + 1
        let xr = width - Look.numberRight
        let dotW = spec.decimals > 0 ? digits.widths["."] ?? 0 : 0
        let colLeft = [xr - colW, xr - colW * 2, xr - colW * 3 - dotW]
        let signLeft = colLeft[Look.columns - 1] - max(digits.widths[ScrubNumberFieldDigits.plus] ?? 0, digits.widths[ScrubNumberFieldDigits.minus] ?? 0) - 1.5
        let x = abs(pos) * Double(ups) // in units of the last digit
        let cy = pad + height / 2 + lift
        let p = (0..<Look.columns).map { $0 == 0 ? M.odoLow(x, ups) : M.odoHigh(x, $0, ups) }
        let f1 = p[1] - floor(p[1])
        let sigmas = [sigma, sigma * 4 * f1 * (1 - f1)] // the second column smears only while it turns
        let slot = (top: cy - cellH / 2 - 1, bottom: cy + cellH / 2 + 1)

        return Canvas { ctx, _ in
            let font = Font.system(size: ScrubNumberFieldDigits.opticalSize, weight: .semibold)
            let k = digits.scale
            func draw(_ g: String, x gx: Double, baseline: Double, opacity: Double, in c: GraphicsContext) {
                guard opacity > 0 else { return }
                let text = c.resolve(Text(g).font(font).foregroundColor(Look.ink))
                let rise = text.firstBaseline(in: CGSize(width: 200, height: 200))
                var copy = c
                copy.opacity = opacity
                copy.scaleBy(x: k, y: k)
                copy.draw(text, at: CGPoint(x: gx / k, y: baseline / k - rise), anchor: .topLeading)
            }
            let base = cy + digits.baseline

            if spec.signed {
                // the sign sits against the first digit that shows
                var hidden = 0.0
                for k in minDigits..<Look.columns { hidden += (1 - M.clamp01(M.odoHigh(x, k, ups))) * colW }
                draw(ScrubNumberFieldDigits.plus, x: signLeft + hidden, baseline: base, opacity: M.clamp01(pos / 0.5), in: ctx)
                draw(ScrubNumberFieldDigits.minus, x: signLeft + hidden, baseline: base, opacity: M.clamp01(-pos / 0.5), in: ctx)
            }
            if spec.decimals > 0 { draw(".", x: xr - colW * 2 - dotW, baseline: base, opacity: 1, in: ctx) }

            for k in 0..<Look.columns {
                let cycle = k == 0 ? ScrubNumberFieldDigits.glyphs.enumerated().filter { $0.offset % ups == 0 }.map(\.element) : ScrubNumberFieldDigits.glyphs
                let n = Double(cycle.count)
                // a digit rests on its line and turns in the middle half of a step
                let v = M.rollEase(p[k])
                let at = v - floor(v / n) * n
                let opacity = k < minDigits ? 1 : M.clamp01(p[k])
                var c = ctx
                let reach: Double
                if k < 2 {
                    reach = cellH + pad // the shader cuts these to the window after smearing them
                } else {
                    reach = cellH
                    c.clip(to: Path(CGRect(x: colLeft[k] - 1, y: slot.top, width: colW + 2, height: slot.bottom - slot.top)))
                }
                for i in 0...cycle.count { // the first glyph again at the end, for the wrap
                    let dy = (Double(i) - at) * cellH
                    guard abs(dy) < reach else { continue }
                    let g = cycle[i % cycle.count]
                    draw(g, x: colLeft[k] + (colW - (digits.widths[g] ?? colW)) / 2, baseline: base + dy, opacity: opacity, in: c)
                }
            }
        }
        .frame(width: width, height: height + pad * 2)
        .layerEffect(
            ShaderLibrary.scrubNumberFieldSmear(
                .float4(Float(colLeft[0] - 1), Float(colLeft[0] + colW + 1), Float(sigmas[0]), 0),
                .float4(Float(colLeft[1] - 1), Float(colLeft[1] + colW - 1), Float(sigmas[1]), 0),
                .float2(Float(slot.top), Float(slot.bottom))
            ),
            maxSampleOffset: CGSize(width: 0, height: 24)
        )
        .offset(y: -pad)
        .allowsHitTesting(false)
    }

    // ------------------------------------------------------------ the ghost finger

    /// In the film a soft dot shows the gesture: a press and a sideways drag on each field, a double tap on the photo.
    @ViewBuilder
    private func ghost(_ s: Snapshot, dragX: Double, fieldY: Double, fieldPitch: Double, tapX: Double, tapY: Double) -> some View {
        if s.auto {
            let t = s.tau
            let dot = ghostDot(t, dragX: dragX, fieldY: fieldY, fieldPitch: fieldPitch, tapX: tapX, tapY: tapY)
            if dot.alpha > 0 {
                Circle()
                    .fill(Color.rgba(240, 237, 229, 0.22))
                    .overlay { Circle().strokeBorder(Color.rgba(240, 237, 229, 0.55), lineWidth: 1.5) }
                    .frame(width: Look.dot, height: Look.dot)
                    .scaleEffect(1 - 0.16 * dot.press)
                    .opacity(dot.alpha)
                    .offset(x: dot.x - Look.dot / 2, y: dot.y - Look.dot / 2)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }

    private func ghostDot(_ t: Double, dragX: Double, fieldY: Double, fieldPitch: Double, tapX: Double, tapY: Double) -> (alpha: Double, x: Double, y: Double, press: Double) {
        var alpha = M.filmTapAlpha(t)
        var x = tapX
        var y = tapY
        var press = M.filmTapPress(t)
        for i in 0..<M.fieldCount {
            let a = M.filmDotAlpha(i, t)
            if a > alpha {
                alpha = a
                x = dragX + M.filmFinger(i, t)
                y = fieldY + Double(i) * fieldPitch
                press = M.smooth(M.seg(t, M.dragA[i] - M.pressLead, M.dragA[i] - 0.02)) * (1 - M.smooth(M.seg(t, M.dragB[i], M.dragB[i] + 0.12)))
            }
        }
        return (alpha, x, y, press)
    }
}

/// A field's drag and double tap. SwiftUI does not call `onEnded` for a cancelled gesture, but it
/// always resets a `GestureState`, so that reset releases a finger the system took away.
private struct ScrubNumberFieldFieldGesture: ViewModifier {
    let changed: (DragGesture.Value) -> Void
    let ended: (DragGesture.Value) -> Void
    let cancelled: () -> Void
    let doubleTap: () -> Void

    @GestureState private var down = false

    func body(content: Content) -> some View {
        content
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .global)
                    .updating($down) { _, state, _ in state = true }
                    .onChanged(changed)
                    .onEnded(ended)
                    .simultaneously(with: TapGesture(count: 2).onEnded(doubleTap))
            )
            .onChange(of: down) { _, isDown in
                // next turn of the run loop: a normal lift has run `ended`, with its speed, by then
                if !isDown { DispatchQueue.main.async { cancelled() } }
            }
    }
}
