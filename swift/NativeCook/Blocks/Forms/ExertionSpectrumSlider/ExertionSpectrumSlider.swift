import SwiftUI

struct ExertionSpectrumSliderLevel: Equatable {
    var name: String
    var description: String
    static let samples = [
        Self(name: "Very, Very Light", description: "Very easy activity, like slowly walking"),
        Self(name: "Very Light", description: "Easy activity, like a casual walk"),
        Self(name: "Light", description: "Light effort, breathing slightly harder than normal"),
        Self(name: "Moderate", description: "Moderate effort, breathing\nheavier and sweating lightly"),
        Self(name: "Somewhat Hard", description: "Starting to feel challenging,\ncan still talk in sentences"),
        Self(name: "Hard", description: "Challenging effort, hard to speak in full sentences"),
        Self(name: "Very Hard", description: "Very challenging, can only\nspeak a few words at a time"),
        Self(name: "Very, Very Hard", description: "Extremely challenging, can barely speak"),
        Self(name: "Extremely Hard", description: "Close to your limit, impossible\nto keep up for long"),
        Self(name: "Maximal", description: "All-out effort, nothing left to give"),
    ]
}

struct ExertionSpectrumSliderStop: Equatable {
    var at: Double
    var color: Color
    static let samples = [
        Self(at: 0, color: Color(hex: 0x1fb8a6)),
        Self(at: 0.06, color: Color(hex: 0x19c3f0)),
        Self(at: 0.15, color: Color(hex: 0x12b5f5)),
        Self(at: 0.205, color: Color(hex: 0x5fd080)),
        Self(at: 0.25, color: Color(hex: 0xf5e500)),
        Self(at: 0.35, color: Color(hex: 0xff9a00)),
        Self(at: 0.45, color: Color(hex: 0xff7a00)),
        Self(at: 0.55, color: Color(hex: 0xf0142a)),
        Self(at: 0.65, color: Color(hex: 0xee1048)),
        Self(at: 0.75, color: Color(hex: 0xd800d8)),
        Self(at: 0.85, color: Color(hex: 0x9a10c8)),
        Self(at: 1, color: Color(hex: 0x6a0dad)),
    ]
}

private enum ExertionSpectrumSliderPalette {
    static let page = Color(hex: 0x0b0b0c)
    static let sheet = Color.rgba(28, 28, 30, 0.82)
    static let ink = Color.white
    static let numeral = Color(hex: 0xf5f5f5)
    static let mute = Color(hex: 0x9a9aa0)
    static let caption = Color(hex: 0x6f6f75)
    static let divider = Color.white.opacity(0.09)
    static let above = Color(hex: 0xf0142a)
    static let below = Color(hex: 0x19c3f0)
    static let smudges: [(Double, Double, Double, Double, Color)] = [
        (0.04, 0.22, 0.26, 0.022, Color(hex: 0x56565c)),
        (0.04, 0.36, 0.15, 0.022, Color(hex: 0x4a4a50)),
        (0.5, 0.25, 0.24, 0.02, Color(hex: 0x3c3c42)),
        (0.3, 0.672, 0.05, 0.022, Color(hex: 0x8b1fb4)),
        (0.31, 0.722, 0.12, 0.026, Color(hex: 0xd0202c)),
        (0.31, 0.775, 0.19, 0.026, Color(hex: 0xd6c400)),
        (0.03, 0.674, 0.2, 0.014, Color(hex: 0x3c3c42)),
        (0.03, 0.726, 0.24, 0.014, Color(hex: 0x3c3c42)),
        (0.72, 0.726, 0.2, 0.014, Color(hex: 0x3c3c42)),
    ]
}

private enum ExertionSpectrumSliderMetrics {
    static let smudgeBlur = 9.0
    static let sheetTop = 8.0
    static let sheetRadius = 30.0
    static let margin = 28.0
    static let numeralSize = 112.0
    static let numeralSlot = 118.0
    static let numeralCanvas = 300.0
    static let numeralWidth = 240.0
    static let trackMargin = 42.0
    static let slider = 116.0
    static let thin = 9.0
    static let wide = 54.0
    static let wideCorner = 5.0
    static let band = 70.0
    static let hatchStep = 8.0
    static let hatchWidth = 2.2
    static let hatchAlpha = 0.1
    static let hatchRest = 0.45
    static let shade = 0.46
    static let thumb = 28.0
    static let thumbGrown = 30.0
    static let dot = 11.0
    static let dotGrown = 24.0
    static let tagWidth = 132.0
    static let tagHeight = 18.0
    static let tagEdge = 12.0
    static let save = 56.0
    static let saveRadius = 20.0
    static let titleSize = 25.0
    static let titleHeight = 30.0
    static let subtitleSize = 16.0
    static let subtitleHeight = 20.0
    static let wordsHeight = 70.0
    static let wordsTop = 14.0
    static let nameSize = 17.0
    static let nameHeight = 22.0
    static let descriptionSize = 15.0
    static let descriptionHeight = 20.0
    static let descriptionLeading = descriptionHeight - UIFont.systemFont(ofSize: descriptionSize).lineHeight
    static let captionSize = 10.5
    static let captionHeight = 13.0
    static let tagSize = 10.0
    static let tagTextHeight = 13.0
    static let clearSize = 17.0
    static let clearHeight = 22.0
    static let help = 48.0
    static let helpSize = 14.0
    static let helpHeight = 18.0
    static let saveSize = 16.0
    static let saveTextHeight = 20.0
    static let noteSize = 10.5
    static let noteHeight = 13.0
    static let gapWeight = 3.8
}

private enum ExertionSpectrumSliderMotion {
    static let follow = (mass: 1.0, stiffness: 520.0, damping: 44.0)
    static let swell = (mass: 1.0, stiffness: 300.0, damping: 21.0)
    static let pop = (mass: 1.0, stiffness: 420.0, damping: 22.0)
    static let roll = 0.24
    static let rollStep = 86.0
    static let rollBlurX = 2.5
    static let rollBlurY = 13.0
    static let textFade = 0.16
    static let tone = 0.26
    static let note = 0.2
    static let noteShift = 8.0
    static let loop = 9.4
    static let downAt = 0.8
    static let upFrom = 1.15
    static let upFor = 3.1
    static let peak = 8
    static let backFrom = 4.7
    static let backFor = 1.5
    static let settle = 4
    static let liftAt = 6.6
    static let clearAt = 7.4
    static let pressFor = 0.15
    static let userHold = 3.0
    static let returnFor = 0.8
    static let frameLimit = 0.064
    static let energyThreshold = 6e-9
    static let gamma = 2.2
    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }
    static func quad(_ t: Double) -> Double {
        let u = clamp(t)
        return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
    }
    static func glide(_ t: Double) -> Double {
        let u = clamp(t)
        return 0.55 * u + 0.45 * u * u * (3 - 2 * u)
    }
}

private enum ExertionSpectrumSliderDrawing {
    private typealias S = ExertionSpectrumSliderMetrics
    static let gaussian: [(offset: Double, weight: Double)] = {
        let offsets = (-4...4).map { Double($0) * 0.75 }
        let sum = offsets.reduce(0) { $0 + exp(-0.5 * $1 * $1) }
        return offsets.map { ($0, exp(-0.5 * $0 * $0) / sum) }
    }()
    static func track(x0: Double, x1: Double, cy: Double, swell: Double) -> Path {
        let a = S.thin / 2
        let b = (S.thin + (S.wide - S.thin) * swell) / 2
        let corner = S.thin / 2 + (S.wideCorner - S.thin / 2) * swell
        let k = 0.5523
        var path = Path()
        path.move(to: CGPoint(x: x0 + a, y: cy - a))
        path.addLine(to: CGPoint(x: x1 - corner, y: cy - b))
        path.addCurve(to: CGPoint(x: x1, y: cy - b + corner), control1: CGPoint(x: x1 - corner + corner * k, y: cy - b), control2: CGPoint(x: x1, y: cy - b + corner - corner * k))
        path.addLine(to: CGPoint(x: x1, y: cy + b - corner))
        path.addCurve(to: CGPoint(x: x1 - corner, y: cy + b), control1: CGPoint(x: x1, y: cy + b - corner + corner * k), control2: CGPoint(x: x1 - corner + corner * k, y: cy + b))
        path.addLine(to: CGPoint(x: x0 + a, y: cy + a))
        path.addCurve(to: CGPoint(x: x0, y: cy), control1: CGPoint(x: x0 + a - a * k, y: cy + a), control2: CGPoint(x: x0, y: cy + a * k))
        path.addCurve(to: CGPoint(x: x0 + a, y: cy - a), control1: CGPoint(x: x0, y: cy - a * k), control2: CGPoint(x: x0 + a - a * k, y: cy - a))
        path.closeSubpath()
        return path
    }
    static func hatch(x0: Double, x1: Double, cy: Double) -> Path {
        var path = Path()
        for x in stride(from: x0 - S.band, to: x1, by: S.hatchStep) {
            path.move(to: CGPoint(x: x, y: cy + S.band / 2))
            path.addLine(to: CGPoint(x: x + S.band, y: cy - S.band / 2))
        }
        return path
    }
    static func line(x: Double, cy: Double, color: Color, width: Double, in context: GraphicsContext) {
        var path = Path()
        path.move(to: CGPoint(x: x, y: cy - S.band / 2)); path.addLine(to: CGPoint(x: x, y: cy + S.band / 2))
        context.stroke(path, with: .color(color), lineWidth: width)
    }
    /// An isotropic horizontal blur plus vertically sampled layers approximates Skia's two sigmas.
    static func blurred(x: Double, y: Double, in context: GraphicsContext, draw: (GraphicsContext) -> Void) {
        if y < 0.001 { draw(context); return }
        context.drawLayer { layer in
            if x > 0 { layer.addFilter(.blur(radius: x)) }
            let vertical = sqrt(max(0, y * y - x * x))
            for sample in gaussian {
                var sum = layer; sum.opacity = sample.weight; sum.blendMode = .plusLighter
                sum.drawLayer { image in
                    image.opacity = 1; image.blendMode = .normal
                    image.translateBy(x: 0, y: sample.offset * vertical)
                    draw(image)
                }
            }
        }
    }
}

private struct ExertionSpectrumSliderNumeral: View {
    let count: Int
    let roll: Double
    let still: Bool
    private typealias S = ExertionSpectrumSliderMetrics
    private typealias M = ExertionSpectrumSliderMotion
    var body: some View {
        Canvas { context, _ in
            let between = still ? 0 : sin(.pi * (roll - floor(roll)))
            ExertionSpectrumSliderDrawing.blurred(x: M.rollBlurX * between, y: M.rollBlurY * between, in: context) { layer in
                for value in 1...count {
                    let opacity = max(0, 1 - abs(Double(value) - roll))
                    if opacity > 0 {
                        var digit = layer; digit.opacity = opacity
                        let text = Text(String(value)).font(.system(size: S.numeralSize, weight: .bold)).tracking(-2)
                            .foregroundStyle(ExertionSpectrumSliderPalette.numeral)
                        let y = S.numeralCanvas / 2 + (still ? 0 : (Double(value) - roll) * M.rollStep)
                        digit.draw(text, at: CGPoint(x: S.numeralWidth / 2, y: y), anchor: .center)
                    }
                }
            }
        }
        .frame(width: S.numeralWidth, height: S.numeralCanvas)
        .clipped()
    }
}

private struct ExertionSpectrumSliderTrack: View {
    let width: Double
    let count: Int
    let rangeFrom: Double
    let rangeTo: Double
    let spectrum: [ExertionSpectrumSliderStop]
    let place: Double
    let swell: Double
    let outside: Double
    private typealias S = ExertionSpectrumSliderMetrics
    private typealias M = ExertionSpectrumSliderMotion
    private typealias D = ExertionSpectrumSliderDrawing
    var body: some View {
        Canvas { context, _ in
            let x0 = S.trackMargin; let x1 = width - S.trackMargin; let span = x1 - x0; let cy = S.slider / 2
            let hatch = D.hatch(x0: x0, x1: x1, cy: cy)
            let gradient = Gradient(stops: spectrum.map { Gradient.Stop(color: $0.color, location: $0.at) })
            let colors = GraphicsContext.Shading.linearGradient(gradient, startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x1, y: 0))
            var left = context
            left.clip(to: Path(CGRect(x: x0, y: cy - S.band / 2, width: rangeFrom * span, height: S.band)))
            left.stroke(hatch, with: .linearGradient(Gradient(colors: [.white.opacity(0), .white.opacity(S.hatchAlpha)]), startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x0 + rangeFrom * span, y: 0)), lineWidth: S.hatchWidth)
            var right = context
            right.clip(to: Path(CGRect(x: x0 + rangeTo * span, y: cy - S.band / 2, width: (1 - rangeTo) * span, height: S.band)))
            let rightGradient = Gradient(stops: [
                .init(color: .white.opacity(S.hatchAlpha), location: 0),
                .init(color: .white.opacity(S.hatchAlpha), location: 0.55),
                .init(color: .white.opacity(0), location: 1),
            ])
            right.stroke(hatch, with: .linearGradient(rightGradient, startPoint: CGPoint(x: x0 + rangeTo * span, y: 0), endPoint: CGPoint(x: x1, y: 0)), lineWidth: S.hatchWidth)
            D.line(x: x0 + rangeFrom * span, cy: cy, color: .white.opacity(0.14), width: 1, in: context)
            D.line(x: x0 + rangeTo * span, cy: cy, color: .white.opacity(0.14), width: 1, in: context)

            let p = M.clamp(place)
            let from = p > rangeTo ? rangeTo : min(p, rangeFrom)
            let to = p > rangeTo ? p : rangeFrom
            let litOpacity = outside * (S.hatchRest + (1 - S.hatchRest) * M.clamp(swell))
            var lit = context; lit.opacity = litOpacity
            lit.clip(to: Path(CGRect(x: x0 + from * span, y: cy - S.band / 2, width: max(0, to - from) * span, height: S.band)))
            lit.stroke(hatch, with: colors, lineWidth: S.hatchWidth + 0.6)
            let thumbX = x0 + p * span
            D.line(x: thumbX, cy: cy, color: .white.opacity(litOpacity), width: 1.5, in: context)

            var track = context
            track.clip(to: D.track(x0: x0, x1: x1, cy: cy, swell: swell))
            track.fill(Path(CGRect(x: x0, y: 0, width: span, height: S.slider)), with: colors)
            let shadeLeft = min(place, rangeFrom) * span
            if shadeLeft > 0 { track.fill(Path(CGRect(x: x0, y: 0, width: shadeLeft, height: S.slider)), with: .color(.black.opacity(S.shade))) }
            let shadeRight = x0 + max(place, rangeTo) * span
            track.fill(Path(CGRect(x: shadeRight, y: 0, width: max(0, x1 - shadeRight), height: S.slider)), with: .color(.black.opacity(S.shade)))
            if count > 1 {
                for step in 2...count {
                    let share = Double(step - 1) / Double(count)
                    let height = 2 + max(0, swell) * (0.34 * (S.thin + (S.wide - S.thin) * share) - 2)
                    let rect = CGRect(x: x0 + share * span - 1, y: cy - height / 2, width: 2, height: height)
                    track.fill(Path(roundedRect: rect, cornerRadius: 1), with: .color(.white.opacity(0.3)))
                }
            }
            let radius = (S.thumb + (S.thumbGrown - S.thumb) * swell) / 2
            let dot = (S.dot + (S.dotGrown - S.dot) * swell) / 2
            context.drawLayer { shadow in
                shadow.addFilter(.blur(radius: 5))
                shadow.fill(Path(ellipseIn: CGRect(x: thumbX - radius, y: cy + 2 - radius, width: radius * 2, height: radius * 2)), with: .color(.black.opacity(0.45)))
            }
            context.fill(Path(ellipseIn: CGRect(x: thumbX - radius, y: cy - radius, width: radius * 2, height: radius * 2)), with: .color(.white))
            context.fill(Path(ellipseIn: CGRect(x: thumbX - dot, y: cy - dot, width: dot * 2, height: dot * 2)), with: .color(ExertionSpectrumSliderRGB.at(place, in: spectrum).color))
        }
        .frame(width: width, height: S.slider)
    }
}

#Preview { ExertionSpectrumSlider(autoPlay: true) }

/// Frame steps retain the velocity and stopping rules used by the source's physical springs.
private struct ExertionSpectrumSliderScalar {
    private(set) var value: Double
    private var target: Double
    private var from: Double
    private var velocity = 0.0
    private var energy = 0.0
    private var last = Date.distantPast
    private var start = Date.distantPast
    private var duration = 0.0
    private var cubic = false
    private var spring: (mass: Double, stiffness: Double, damping: Double)? = nil
    private var active = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func spring(to next: Double, constants: (mass: Double, stiffness: Double, damping: Double), at date: Date, still: Bool) {
        if still { value = next; target = next; velocity = 0; active = false; return }
        guard target != next else { return }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; spring = constants
        energy = 0.5 * constants.stiffness * pow(value - target, 2)
        if !active { last = date }
        active = true
    }
    mutating func time(to next: Double, duration: Double, at date: Date, cubic: Bool = false) {
        guard target != next else { return }
        from = value; target = next; self.duration = duration; start = date
        self.cubic = cubic; spring = nil; active = true
    }
    mutating func advance(at date: Date) {
        guard active else { return }
        guard let spring else {
            let t = ExertionSpectrumSliderMotion.clamp(date.timeIntervalSince(start) / duration)
            let u = cubic ? 1 - pow(1 - t, 3) : ExertionSpectrumSliderMotion.quad(t)
            value = from + (target - from) * u
            if t == 1 { active = false }
            return
        }
        let dt = min(ExertionSpectrumSliderMotion.frameLimit, max(0, date.timeIntervalSince(last)))
        last = date
        let omega = sqrt(spring.stiffness / spring.mass); let decay = spring.damping / (2 * spring.mass)
        let x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay)
            let b = (velocity + decay * x) / damped
            let wave = x * cos(damped * dt) + b * sin(damped * dt)
            let attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt)))
            value = target + attenuation * wave
        } else {
            let b = velocity + omega * x; let attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt)
            velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * spring.stiffness * pow(value - target, 2) + 0.5 * spring.mass * velocity * velocity
        if energy == 0 || current / energy <= ExertionSpectrumSliderMotion.energyThreshold {
            value = target; velocity = 0; active = false
        }
    }
}

private struct ExertionSpectrumSliderRGB {
    let r: Double; let g: Double; let b: Double
    init(_ color: Color) {
        var r = CGFloat(0); var g = CGFloat(0); var b = CGFloat(0); var a = CGFloat(0)
        UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
        self.r = Double(r) * 255; self.g = Double(g) * 255; self.b = Double(b) * 255
    }
    init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }
    var color: Color { Color.rgba(r, g, b, 1) }
    func blended(to other: Self, at t: Double, gamma: Double = 1) -> Self {
        func channel(_ a: Double, _ b: Double) -> Double {
            floor(pow(pow(a / 255, gamma) * (1 - t) + pow(b / 255, gamma) * t, 1 / gamma) * 255 + 0.5)
        }
        return Self(r: channel(r, other.r), g: channel(g, other.g), b: channel(b, other.b))
    }
    static func at(_ p: Double, in spectrum: [ExertionSpectrumSliderStop]) -> Self {
        var i = 1
        while i < spectrum.count - 1 && p > spectrum[i].at { i += 1 }
        let a = spectrum[i - 1]; let b = spectrum[i]
        let t = ExertionSpectrumSliderMotion.clamp((p - a.at) / (b.at - a.at))
        return Self(a.color).blended(to: Self(b.color), at: t)
    }
}

/// The spring follows a finger without snapping; the number and copy observe the part it enters.
struct ExertionSpectrumSlider: View {
    var value: Double
    var levels: [ExertionSpectrumSliderLevel]
    var usualRange: ClosedRange<Int>
    var spectrum: [ExertionSpectrumSliderStop]
    var autoPlay: Bool
    var title: String
    var subtitle: String
    var basisLabel: String
    var rangeLabel: String
    var aboveLabel: String
    var belowLabel: String
    var aboveColor: Color
    var belowColor: Color
    var clearLabel: String
    var helpLabel: String
    var saveLabel: String
    var aboveNote: String
    var belowNote: String
    var onChange: ((Int) -> Void)?
    var onSave: ((Int) -> Void)?
    var onClear: (() -> Void)?
    var onHelp: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var current: Int
    @State private var side = 0
    @State private var lastSide = 1
    @State private var target: Double
    @State private var touching = 0.0
    @State private var place: ExertionSpectrumSliderScalar
    @State private var swell = ExertionSpectrumSliderScalar(0)
    @State private var roll: ExertionSpectrumSliderScalar
    @State private var tone: ExertionSpectrumSliderScalar
    @State private var above: ExertionSpectrumSliderScalar
    @State private var below: ExertionSpectrumSliderScalar
    @State private var outside: ExertionSpectrumSliderScalar
    @State private var byHand = false
    @State private var holding = false
    @State private var grip = 0.0
    @State private var panBegan = false
    @GestureState private var panActive = false
    @State private var touches = 0
    @State private var autoPressed = false
    @State private var origin: Date? = nil
    @State private var clockPrevious = -1.0
    @State private var loopSequence = 0
    @State private var entering: Date? = nil
    @State private var departing: [Words] = []
    @State private var wordsSequence = 0
    @State private var selectionHaptic = UISelectionFeedbackGenerator()
    @State private var pressHaptic = UIImpactFeedbackGenerator(style: .light)
    private typealias S = ExertionSpectrumSliderMetrics
    private typealias M = ExertionSpectrumSliderMotion
    private typealias P = ExertionSpectrumSliderPalette

    init(
        value: Double = 2, levels: [ExertionSpectrumSliderLevel] = ExertionSpectrumSliderLevel.samples,
        usualRange: ClosedRange<Int> = 2...4, spectrum: [ExertionSpectrumSliderStop] = ExertionSpectrumSliderStop.samples,
        autoPlay: Bool = false, title: String = "How hard was it?", subtitle: String = "Rate the effort of this workout",
        basisLabel: String = "Based on your heart rate", rangeLabel: String = "Usual range",
        aboveLabel: String = "Above usual range", belowLabel: String = "Below usual range",
        aboveColor: Color = ExertionSpectrumSliderPalette.above, belowColor: Color = ExertionSpectrumSliderPalette.below,
        clearLabel: String = "Clear Entry", helpLabel: String = "How to Self-Assess", saveLabel: String = "Save",
        aboveNote: String = "Effort Above Your Usual Range", belowNote: String = "Effort Below Your Usual Range",
        onChange: ((Int) -> Void)? = nil, onSave: ((Int) -> Void)? = nil,
        onClear: (() -> Void)? = nil, onHelp: (() -> Void)? = nil
    ) {
        self.value = value; self.levels = levels; self.usualRange = usualRange; self.spectrum = spectrum
        self.autoPlay = autoPlay; self.title = title; self.subtitle = subtitle
        self.basisLabel = basisLabel; self.rangeLabel = rangeLabel
        self.aboveLabel = aboveLabel; self.belowLabel = belowLabel; self.aboveColor = aboveColor; self.belowColor = belowColor
        self.clearLabel = clearLabel; self.helpLabel = helpLabel; self.saveLabel = saveLabel
        self.aboveNote = aboveNote; self.belowNote = belowNote
        self.onChange = onChange; self.onSave = onSave; self.onClear = onClear; self.onHelp = onHelp
        let first = Self.opening(value, count: levels.count)
        let home = (Double(first) - 0.5) / Double(levels.count)
        let from = Double(usualRange.lowerBound - 1) / Double(levels.count)
        let to = Double(usualRange.upperBound) / Double(levels.count)
        let initialSide = home < from ? -1 : home > to ? 1 : 0
        _current = State(initialValue: first); _target = State(initialValue: home)
        _place = State(initialValue: ExertionSpectrumSliderScalar(home))
        _roll = State(initialValue: ExertionSpectrumSliderScalar(Double(first)))
        _tone = State(initialValue: ExertionSpectrumSliderScalar(Double(first)))
        _above = State(initialValue: ExertionSpectrumSliderScalar(initialSide == 1 ? 1 : 0))
        _below = State(initialValue: ExertionSpectrumSliderScalar(initialSide == -1 ? 1 : 0))
        _outside = State(initialValue: ExertionSpectrumSliderScalar(initialSide == 0 ? 0 : 1))
    }
    private static func opening(_ value: Double, count: Int) -> Int {
        let first = min(Double(count), max(1, floor(value + 0.5)))
        return first >= Double(count) ? count : Int(first)
    }
    private var opening: Int { Self.opening(value, count: levels.count) }
    private var home: Double { (Double(opening) - 0.5) / Double(levels.count) }
    private var rangeFrom: Double { Double(usualRange.lowerBound - 1) / Double(levels.count) }
    private var rangeTo: Double { Double(usualRange.upperBound) / Double(levels.count) }
    private var shown: ExertionSpectrumSliderLevel { levels[current - 1] }
    private var wordsKey: WordsKey { WordsKey(value: current, level: shown) }
    private var cleared: Bool { current == opening }
    private var saveColor: Color {
        let t = min(Double(levels.count), max(1, tone.value))
        let first = min(levels.count - 1, Int(floor(t)) - 1)
        let second = min(levels.count - 1, first + 1)
        let a = ExertionSpectrumSliderRGB.at((Double(first) + 0.5) / Double(levels.count), in: spectrum)
        let b = ExertionSpectrumSliderRGB.at((Double(second) + 0.5) / Double(levels.count), in: spectrum)
        return a.blended(to: b, at: t - Double(first + 1), gamma: M.gamma).color
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let top = insets.top + S.sheetTop
            let bottom = max(insets.bottom, 16) + 8
            let hairline = 1 / displayScale
            TimelineView(.animation) { timeline in
                ZStack(alignment: .topLeading) {
                    P.page
                    smudges(width: width, height: height)
                    sheet(width: width, height: height - top, bottom: bottom, hairline: hairline, date: timeline.date)
                        .frame(width: width, height: height - top, alignment: .top)
                        .background(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous).fill(P.sheet))
                        .offset(y: top)
                }
                .frame(width: width, height: height)
                .offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in advance(at: date) }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            selectionHaptic.prepare(); pressHaptic.prepare()
            entering = reduceMotion ? nil : .now
            updateSide()
        }
        .onChange(of: wordsKey) { old, next in
            guard old.value != next.value else { return }
            if reduceMotion { entering = nil; departing = [] }
            else {
                let now = Date.now
                departing.append(Words(id: wordsSequence, level: old.level, start: now))
                wordsSequence += 1; entering = now
            }
        }
        .onChange(of: panActive) { _, active in
            if !active {
                // Canceled pans run the same finalization as an ordinary lift.
                DispatchQueue.main.async { if !panActive { liftFinger() } }
            }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches, home: home)) { await loop() }
    }

    private func smudges(width: Double, height: Double) -> some View {
        Canvas { context, _ in
            for (x, y, w, h, color) in P.smudges {
                context.drawLayer { layer in
                    layer.addFilter(.blur(radius: S.smudgeBlur))
                    layer.fill(Path(roundedRect: CGRect(x: x * width, y: y * height, width: w * width, height: h * height), cornerRadius: 6), with: .color(color))
                }
            }
        }.frame(width: width, height: height)
    }
    private func sheet(width: Double, height: Double, bottom: Double, hairline: Double, date: Date) -> some View {
        let fixed = 30 + S.titleHeight + 6 + S.subtitleHeight + S.numeralSlot + S.wordsTop + S.wordsHeight + S.slider + S.clearHeight + hairline + S.help + S.save + bottom
        let gap = max(0, (height - fixed) / S.gapWeight)
        let fade = entering.map { M.quad(date.timeIntervalSince($0) / M.textFade) } ?? 1
        return VStack(spacing: 0) {
            Text(title).font(.system(size: S.titleSize, weight: .bold)).foregroundStyle(P.ink).lineLimit(1).frame(height: S.titleHeight)
            Text(subtitle).font(.system(size: S.subtitleSize)).foregroundStyle(P.mute).lineLimit(1).frame(height: S.subtitleHeight).padding(.top, 6)
            Color.clear.frame(height: gap)
            ExertionSpectrumSliderNumeral(count: levels.count, roll: roll.value, still: reduceMotion)
                .frame(width: S.numeralWidth, height: S.numeralSlot)
                .accessibilityElement(children: .ignore).accessibilityLabel(title)
                .accessibilityValue("\(current), \(shown.name)")
                .accessibilityAdjustableAction { direction in
                    touched()
                    let next = min(levels.count, max(1, current + (direction == .increment ? 1 : -1)))
                    target = (Double(next) - 0.5) / Double(levels.count)
                }
            ZStack(alignment: .top) {
                ForEach(departing) { old in words(old.level).opacity(1 - M.quad(date.timeIntervalSince(old.start) / M.textFade)) }
                words(shown).opacity(fade)
            }
            .frame(maxWidth: .infinity).frame(height: S.wordsHeight, alignment: .top)
            .padding(.horizontal, S.margin).padding(.top, S.wordsTop)
            Color.clear.frame(height: gap * 1.15)
            slider(width: width)
            Color.clear.frame(height: gap * 0.55)
            Button {
                touched(); pressHaptic.impactOccurred(); clear()
            } label: {
                Text(clearLabel).font(.system(size: S.clearSize)).foregroundStyle(P.ink).lineLimit(1)
                    .frame(height: S.clearHeight).frame(maxWidth: .infinity).contentShape(Rectangle().inset(by: -12))
            }
            .buttonStyle(ClearStyle(cleared: cleared, held: autoPressed)).disabled(cleared)
            Color.clear.frame(height: gap * 0.55)
            P.divider.frame(height: hairline).padding(.horizontal, S.margin)
            Button {
                touched(); pressHaptic.impactOccurred(); onHelp?()
            } label: {
                HStack(spacing: 4) {
                    Text(helpLabel).font(.system(size: S.helpSize)).foregroundStyle(P.ink).lineLimit(1).frame(height: S.helpHeight)
                    Image(systemName: "chevron.right").font(.system(size: 14)).foregroundStyle(P.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading).frame(height: S.help).contentShape(Rectangle())
            }
            .buttonStyle(HelpStyle()).padding(.horizontal, S.margin)
            Color.clear.frame(height: gap * 0.55)
            Button {
                touched(); pressHaptic.impactOccurred(); onSave?(current)
            } label: {
                ZStack(alignment: .bottom) {
                    Text(saveLabel).font(.system(size: S.saveSize, weight: .bold)).foregroundStyle(P.ink).lineLimit(1)
                        .frame(height: S.saveTextHeight).shadow(color: .black.opacity(0.22), radius: 3, x: 0, y: 0.5)
                        .frame(maxWidth: .infinity, maxHeight: .infinity).offset(y: -M.noteShift * outside.value)
                    Text(lastSide > 0 ? aboveNote : belowNote).font(.system(size: S.noteSize, weight: .medium)).foregroundStyle(.white.opacity(0.85)).lineLimit(1)
                        .frame(height: S.noteHeight).shadow(color: .black.opacity(0.22), radius: 3, x: 0, y: 0.5)
                        .opacity(outside.value).padding(.bottom, 10)
                }
                .frame(maxWidth: .infinity).frame(height: S.save).background(saveColor)
            }
            .buttonStyle(SaveStyle()).clipShape(RoundedRectangle(cornerRadius: S.saveRadius, style: .continuous))
            .padding(.horizontal, S.trackMargin)
            .accessibilityLabel(side == 0 ? saveLabel : saveLabel + ", " + (side > 0 ? aboveNote : belowNote))
        }
        .padding(.top, 30).padding(.bottom, bottom)
    }
    private func words(_ level: ExertionSpectrumSliderLevel) -> some View {
        VStack(spacing: 0) {
            Text(level.name).font(.system(size: S.nameSize, weight: .bold)).foregroundStyle(P.ink).lineLimit(1).frame(height: S.nameHeight)
            Text(level.description).font(.system(size: S.descriptionSize)).foregroundStyle(P.mute).lineSpacing(S.descriptionLeading)
                .padding(.vertical, S.descriptionLeading / 2).lineLimit(2).fixedSize(horizontal: false, vertical: true).padding(.top, 4)
        }.multilineTextAlignment(.center).frame(maxWidth: .infinity)
    }
    private func slider(width: Double) -> some View {
        let span = width - S.trackMargin * 2
        let thumb = S.trackMargin + M.clamp(place.value) * span
        let tagX = max(S.tagEdge, min(width - S.tagWidth - S.tagEdge, thumb - S.tagWidth / 2))
        return ZStack(alignment: .topLeading) {
            ExertionSpectrumSliderTrack(width: width, count: levels.count, rangeFrom: rangeFrom, rangeTo: rangeTo, spectrum: spectrum, place: place.value, swell: swell.value, outside: outside.value)
            caption(basisLabel).frame(width: max(0, width - S.trackMargin - 10), alignment: .leading)
                .opacity(1 - outside.value).offset(x: S.trackMargin + 10)
            caption(rangeLabel).frame(width: (rangeTo - rangeFrom) * span)
                .offset(x: S.trackMargin + rangeFrom * span, y: S.slider - S.captionHeight)
            tag(aboveLabel, color: aboveColor, shown: above.value).offset(x: tagX)
            tag(belowLabel, color: belowColor, shown: below.value).offset(x: tagX)
        }
        .frame(width: width, height: S.slider).contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { event in
                if !panBegan {
                    panBegan = true; origin = nil; byHand = true; holding = true; touching = 1
                    let at = (event.startLocation.x - S.trackMargin) / span
                    grip = abs(at - target) * span <= S.thumb ? at - target : 0
                    target = M.clamp(at - grip); touched()
                }
                target = M.clamp((event.location.x - S.trackMargin) / span - grip)
            }
            .onEnded { _ in liftFinger() }
        )
    }
    private func caption(_ text: String) -> some View {
        Text(text.uppercased()).font(.system(size: S.captionSize)).tracking(0.2).foregroundStyle(P.caption).lineLimit(1).frame(height: S.captionHeight)
    }
    private func tag(_ label: String, color: Color, shown: Double) -> some View {
        Text(label.uppercased()).font(.system(size: S.tagSize, weight: .heavy)).tracking(0.3).foregroundStyle(P.ink).lineLimit(1)
            .frame(width: S.tagWidth, height: S.tagHeight)
            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(color))
            .opacity(M.clamp(shown * 2)).scaleEffect(reduceMotion ? 1 : 0.7 + 0.3 * shown)
            .offset(y: -2 + (reduceMotion ? 0 : (1 - shown) * 6)).allowsHitTesting(false)
    }
    private func touched() { autoPressed = false; origin = nil; touches += 1 }
    private func clear() { autoPressed = false; target = home; onClear?() }
    private func liftFinger() {
        guard panBegan else { return }
        panBegan = false; holding = false; touching = 0; touched()
    }
    private func updateSide() {
        let next = place.value < rangeFrom ? -1 : place.value > rangeTo ? 1 : 0
        side = next
        if next != 0 { lastSide = next }
    }
    private func advance(at date: Date) {
        if let origin {
            let t = max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)
            let was = clockPrevious > t ? -1 : clockPrevious
            let peak = (Double(min(levels.count, M.peak)) - 0.5) / Double(levels.count)
            let settle = (Double(min(levels.count, M.settle)) - 0.5) / Double(levels.count)
            if was < M.downAt && t >= M.downAt { touching = 1 }
            if t >= M.upFrom && t < M.backFrom { target = home + (peak - home) * M.glide((t - M.upFrom) / M.upFor) }
            else if t >= M.backFrom && t < M.liftAt { target = peak + (settle - peak) * M.glide((t - M.backFrom) / M.backFor) }
            if was < M.liftAt && t >= M.liftAt { touching = 0 }
            if was < M.clearAt && t >= M.clearAt { autoPressed = true }
            if was < M.clearAt + M.pressFor && t >= M.clearAt + M.pressFor { clear() }
            clockPrevious = t
        }
        place.spring(to: target, constants: M.follow, at: date, still: reduceMotion)
        swell.spring(to: touching, constants: M.swell, at: date, still: reduceMotion)
        place.advance(at: date); swell.advance(at: date)
        let next = min(levels.count, Int(floor(M.clamp(place.value) * Double(levels.count))) + 1)
        if next != current {
            current = next
            roll.time(to: Double(next), duration: M.roll, at: date, cubic: true)
            tone.time(to: Double(next), duration: M.tone, at: date)
            if byHand { selectionHaptic.selectionChanged() }
            onChange?(next)
        }
        updateSide()
        let aboveTarget = side == 1 ? 1.0 : 0.0
        let belowTarget = side == -1 ? 1.0 : 0.0
        if reduceMotion {
            above.time(to: aboveTarget, duration: M.note, at: date)
            below.time(to: belowTarget, duration: M.note, at: date)
        } else {
            above.spring(to: aboveTarget, constants: M.pop, at: date, still: false)
            below.spring(to: belowTarget, constants: M.pop, at: date, still: false)
        }
        outside.time(to: side == 0 ? 0 : 1, duration: M.note, at: date)
        roll.advance(at: date); tone.advance(at: date); above.advance(at: date); below.advance(at: date); outside.advance(at: date)
        departing.removeAll { date.timeIntervalSince($0.start) >= M.textFade }
    }
    private func loop() async {
        loopSequence += 1; let generation = loopSequence
        origin = nil
        defer { if generation == loopSequence { origin = nil } }
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled, !holding else { return }
            byHand = false
            target = home
            try? await Task.sleep(for: .seconds(M.returnFor))
            guard !Task.isCancelled else { return }
        }
        byHand = false; origin = .now; clockPrevious = -1
        while !Task.isCancelled { try? await Task.sleep(for: .seconds(M.loop)) }
    }
    private struct Words: Identifiable { let id: Int; let level: ExertionSpectrumSliderLevel; let start: Date }
    private struct WordsKey: Equatable { let value: Int; let level: ExertionSpectrumSliderLevel }
    private struct LoopKey: Equatable { let on: Bool; let touches: Int; let home: Double }
    private struct ClearStyle: ButtonStyle {
        let cleared: Bool; let held: Bool
        func makeBody(configuration: Configuration) -> some View {
            let opacity = cleared ? 0.35 : configuration.isPressed || held ? 0.5 : 1
            configuration.label.opacity(opacity)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.15), value: opacity)
        }
    }
    private struct HelpStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.5 : 1)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.12), value: configuration.isPressed)
        }
    }
    private struct SaveStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.overlay(Color.black.opacity(configuration.isPressed ? 0.18 : 0).allowsHitTesting(false))
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.11), value: configuration.isPressed)
        }
    }
}
