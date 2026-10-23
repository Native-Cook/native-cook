import SwiftUI
import UIKit

struct DayOneMedalDemo: View {
    var body: some View { DayOneMedal(autoPlay: true) }
}

struct DayOneMedal: View {
    var autoPlay = false
    @StateObject private var model = DayOneMedalModel()
    @Environment(\.accessibilityReduceMotion) private var reduced

    var body: some View {
        GeometryReader { geometry in
            let top = geometry.safeAreaInsets.top
            let bottom = geometry.safeAreaInsets.bottom
            let layout = DayOneMedalLayout(width: geometry.size.width, height: geometry.size.height + top + bottom, safeTop: top, safeBottom: bottom)
            DayOneMedalScreen(model: model, layout: layout)
                .frame(width: layout.width, height: layout.height)
                .offset(y: -top)
                .onAppear {
                    model.configure(layout, reduced: reduced, autoPlay: autoPlay)
                    model.startClock()
                }
                .onChange(of: layout) { _, value in model.configure(value, reduced: reduced, autoPlay: autoPlay) }
                .onChange(of: reduced) { _, value in model.configure(layout, reduced: value, autoPlay: autoPlay) }
        }
        .background(Color.black.ignoresSafeArea())
        .statusBarHidden()
        .onDisappear { model.stopClock() }
    }
}

enum DayOneMedalColour {
    static func hex(_ value: UInt32) -> Color {
        Color(.sRGB, red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
}

private struct DayOneMedalScreen: View {
    @ObservedObject var model: DayOneMedalModel
    let layout: DayOneMedalLayout
    private let days = [1, 7, 30]
    private let copy = ["Starting is the hardest part. Let's get going.\nDay 1!", "A full week. The habit is taking shape.\nDay 7!", "Thirty days. This is who you are now.\nDay 30!"]

    private var sheetY: Double {
        model.reduced ? layout.sheetTop : layout.sheetTop + (1 - model.engine.present) * layout.sheetHeight + model.engine.drag
    }

    private var hint: String {
        let state = model.engine.state
        if !state.forged { return "Hold to forge" }
        if state.wearing { return "Drag to swing" }
        if state.clock - state.struck > 4.5 {
            return state.day == 2 ? "Swipe down for the previous milestone" : "Swipe up for the next milestone"
        }
        return "Drag to spin. Flick it hard."
    }

    var body: some View {
        let engine = model.engine
        let state = engine.state
        let p = model.reduced ? 0 : engine.present
        let sheetAlpha = model.reduced ? engine.present : 1
        ZStack(alignment: .topLeading) {
            Color.black
            grid
                .scaleEffect(1 - 0.08 * p)
                .offset(y: (layout.safeTop + 8 - layout.height * 0.04) * p)
                .clipShape(RoundedRectangle(cornerRadius: 38 * p))
                .accessibilityHidden(engine.opened)
            Color.black.opacity((model.reduced ? 0.9 : 0.5) * engine.present)
                .allowsHitTesting(false)
            UnevenRoundedRectangle(topLeadingRadius: 38, topTrailingRadius: 38)
                .fill(DayOneMedalColour.hex(0x0E0D10))
                .frame(width: layout.width, height: layout.sheetHeight + 80)
                .offset(y: sheetY)
                .opacity(sheetAlpha)
            if (model.reduced || engine.present < 1 || engine.drag != 0) && state.forged && engine.opened {
                DayOneMedalConfetti(near: false, scale: layout.scale, time: model.reduced ? 20 : max(0, state.clock - state.struck))
                    .frame(width: layout.width, height: layout.sheetHeight)
                    .offset(y: sheetY)
                    .opacity(sheetAlpha)
            }
            DayOneMedalRibbonView(state: engine.ribbon, scale: layout.scale)
                .frame(width: layout.width, height: layout.sheetHeight)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 38, topTrailingRadius: 38))
                .offset(y: sheetY)
                .opacity(sheetAlpha)
            DayOneMedalMetalView(model: model)
                .frame(width: layout.width, height: layout.height)
                .opacity(engine.opened ? state.fade * (model.reduced ? engine.present : 1) : 1)
                .accessibilityHidden(true)
            if engine.opened {
                sheetControls
                    .frame(width: layout.width, height: layout.sheetHeight + 80, alignment: .topLeading)
                    .offset(y: sheetY)
                    .opacity(sheetAlpha)
                    .accessibilityAddTraits(.isModal)
            }
            if (model.reduced || engine.present < 1 || engine.drag != 0) && state.forged && engine.opened {
                DayOneMedalConfetti(near: true, scale: layout.scale, time: model.reduced ? 20 : max(0, state.clock - state.struck))
                    .frame(width: layout.width, height: layout.sheetHeight)
                    .offset(y: sheetY)
                    .opacity(sheetAlpha)
            }
            DayOneMedalSparkView(sparks: engine.sparks, clock: state.clock, scale: layout.scale)
                .frame(width: layout.width, height: layout.sheetHeight)
                .offset(y: sheetY)
                .opacity(engine.opened && !model.reduced ? sheetAlpha : 0)
        }
        .frame(width: layout.width, height: layout.height)
        .clipped()
    }

    private var grid: some View {
        let k = layout.gridScale
        let shelves = [("Goals", ["calendar", "flame"]), ("Special", ["laurel", "earned"]), ("Cities", ["pagoda", "tower", "bridge", "lantern", "gate", "skyline", "lighthouse"])]
        return VStack(spacing: 0) {
            ZStack {
                Text("Collectibles")
                    .font(.system(size: 17 * k))
                HStack {
                    DayOneMedalGlyph(name: "close", colour: .black)
                        .frame(width: 14, height: 14)
                        .frame(width: 24, height: 24)
                        .background(.white, in: Circle())
                    Spacer()
                }
                .padding(.horizontal, 16)
                .accessibilityHidden(true)
            }
            .frame(height: 52)
            .padding(.top, layout.safeTop)
            ForEach(shelves, id: \.0) { title, names in
                VStack(alignment: .leading, spacing: 14 * k) {
                    Text(title)
                        .font(.system(size: 17 * k))
                        .frame(height: 20 * k)
                    LazyVGrid(columns: Array(repeating: GridItem(.fixed(layout.slotWidth), spacing: 19 * k), count: 4), alignment: .leading, spacing: 19 * k) {
                        ForEach(names, id: \.self) { name in
                            if name == "earned" {
                                Button(action: model.open) {
                                    Capsule()
                                        .fill(DayOneMedalColour.hex(0x111013))
                                        .overlay(Capsule().strokeBorder(DayOneMedalColour.hex(0x3A3526), lineWidth: 2 / 3))
                                        .frame(width: layout.slotWidth, height: layout.slotHeight)
                                }
                                .buttonStyle(DayOneMedalButtonStyle())
                                .accessibilityLabel("Day \(days[model.engine.state.day]) medal, \(model.engine.state.forged ? "forged" : "ready to forge"). Opens the achievement.")
                            } else {
                                Capsule()
                                    .fill(DayOneMedalColour.hex(0x111013))
                                    .overlay(Capsule().strokeBorder(DayOneMedalColour.hex(0x26252A), lineWidth: 2 / 3))
                                    .overlay(DayOneMedalGlyph(name: name, colour: DayOneMedalColour.hex(0x4A4950)).frame(width: 22, height: 22))
                                    .frame(width: layout.slotWidth, height: layout.slotHeight)
                                    .accessibilityLabel("\(name), not earned yet")
                            }
                        }
                    }
                }
                .padding(.horizontal, layout.gridInset)
                .padding(.top, 18 * k)
            }
            Spacer(minLength: 0)
            Text(model.engine.state.forged ? "Tap your medal." : "Tap the medal to begin.")
                .font(.system(size: 13))
                .foregroundStyle(DayOneMedalColour.hex(0x77767D))
                .padding(.bottom, layout.safeBottom + 18)
        }
        .foregroundStyle(.white)
        .frame(width: layout.width, height: layout.height)
    }

    private var sheetControls: some View {
        let k = layout.scale
        let state = model.engine.state
        let ready = model.engine.present == 1
        let wake = state.forged ? DayOneMedalPhysics.easeInOut(min(1, max(0, (state.clock - state.struck) / 0.2))) : 0
        return ZStack(alignment: .topLeading) {
            DayOneMedalTouchSurface(model: model, header: false, originY: 60 * k)
                .frame(width: layout.width, height: 430 * k)
                .offset(y: 60 * k)
                .allowsHitTesting(ready)
                .accessibilityElement()
                .accessibilityLabel("Day \(days[state.day]) medal, \(state.forged ? (state.wearing ? "worn" : "forged") : "not forged yet")")
                .accessibilityValue("Day \(days[state.day]), \(state.engraved ? "back engraved" : "back not engraved")")
                .accessibilityHint(state.forged ? "Double tap to flip. Swipe up or down to change milestone." : "Double tap to forge, or hold a finger on the medal.")
                .accessibilityAction { model.command(state.forged ? .flip : .forge) }
                .accessibilityAdjustableAction { direction in model.command(.day(direction == .increment ? 1 : -1)) }
            DayOneMedalTouchSurface(model: model, header: true, originY: 0)
                .frame(width: layout.width - 80, height: 60 * k)
                .accessibilityHidden(true)
            Text("Achievement")
                .font(.system(size: 17 * k))
                .foregroundStyle(DayOneMedalColour.hex(0x5F5E64))
                .frame(width: layout.width, height: 22 * k)
                .offset(y: 23 * k)
                .opacity(model.engine.headerAlpha)
                .allowsHitTesting(false)
            Button("Done", action: model.close)
                .font(.system(size: 17 * k, weight: .semibold))
                .foregroundStyle(.white)
                .buttonStyle(DayOneMedalButtonStyle())
                .frame(width: 55 * k, height: 28 * k)
                .offset(x: layout.width - 77 * k, y: 20 * k)
                .accessibilityLabel("Done")
            Button { model.command(.flip) } label: {
                DayOneMedalGlyph(name: "capsule", colour: .white)
                    .frame(width: 20 * k, height: 20 * k)
                    .frame(width: 37 * k, height: 37 * k)
                    .background(DayOneMedalColour.hex(0x2B2A2F), in: RoundedRectangle(cornerRadius: 9 * k))
            }
            .buttonStyle(DayOneMedalButtonStyle())
            .disabled(!state.forged || !ready)
            .opacity(state.forged ? 1 : 0.35)
            .offset(x: layout.width - 59 * k, y: 81 * k)
            .accessibilityLabel("Turn the medal over")
            DayOneMedalTitle(day: days[state.day], scale: k)
                .frame(width: layout.width, height: 41 * 1.7 * k)
                .offset(y: layout.titleTop)
                .opacity(0.35 + 0.65 * wake)
                .accessibilityAddTraits(.isHeader)
            Text(state.forged ? copy[state.day] : "Hold the medal to forge it.")
                .font(.system(size: 15 * k))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineSpacing(max(0, layout.bodyHeight / 2 - 18 * k))
                .frame(width: layout.width - 48 * k, height: layout.bodyHeight, alignment: .top)
                .offset(x: 24 * k, y: layout.bodyTop)
            HStack(spacing: 5) {
                ForEach(0..<3) { day in
                    Circle().fill(DayOneMedalColour.hex(day == state.day ? 0xDDA85A : 0x4A4950)).frame(width: 4, height: 4)
                }
            }
            .frame(width: layout.width)
            .offset(y: layout.dotsTop)
            .accessibilityHidden(true)
            DayOneMedalFadingText(text: hint, time: model.engine.elapsed)
                .font(.system(size: 13 * k))
                .foregroundStyle(DayOneMedalColour.hex(0x77767D))
                .frame(width: layout.width, height: 16)
                .offset(y: layout.hintTop)
            Button { model.command(.wear) } label: {
                HStack(spacing: 8 * k) {
                    DayOneMedalGlyph(name: "medal", colour: .white).frame(width: 26 * k, height: 26 * k)
                    DayOneMedalFadingText(text: state.wearing ? "Take It Off" : "Wear Your Medal", time: model.engine.elapsed, alignment: .leading)
                        .frame(width: 150 * k, alignment: .leading)
                }
                .font(.system(size: 17 * k, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: layout.width - 44 * k, height: 55 * k)
                .background(DayOneMedalColour.hex(0x1C1B1F), in: RoundedRectangle(cornerRadius: 9 * k))
            }
            .buttonStyle(DayOneMedalButtonStyle())
            .disabled(!state.forged || !ready)
            .opacity(0.35 + 0.65 * wake)
            .offset(x: 22 * k, y: layout.wearTop)
            .accessibilityLabel(state.wearing ? "Take it off" : "Wear your medal")
            Button { model.command(.lap) } label: {
                Text("Share")
                    .font(.system(size: 17 * k, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(width: layout.width - 44 * k, height: 55 * k)
                    .background(.white, in: RoundedRectangle(cornerRadius: 9 * k))
            }
            .buttonStyle(DayOneMedalButtonStyle())
            .disabled(!state.forged || !ready)
            .opacity(0.35 + 0.65 * (state.forged ? min(1, max(0, (state.clock - state.struck - 0.06) / 0.2)) : 0))
            .offset(x: 22 * k, y: layout.shareTop)
            .accessibilityLabel("Share")
        }
    }
}

private struct DayOneMedalButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.12), value: configuration.isPressed)
    }
}

private struct DayOneMedalTitle: UIViewRepresentable {
    let day: Int
    let scale: Double
    func makeUIView(context: Context) -> DayOneMedalTitleCanvas { DayOneMedalTitleCanvas() }
    func updateUIView(_ view: DayOneMedalTitleCanvas, context: Context) {
        view.day = day
        view.scale = scale
        view.setNeedsDisplay()
    }
}

private final class DayOneMedalTitleCanvas: UIView {
    var day = 1
    var scale = 1.0
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        isAccessibilityElement = true
    }
    required init?(coder: NSCoder) { nil }
    override func draw(_ rect: CGRect) {
        let text = "DAY \(day)" as NSString
        let font = UIFont(name: DayOneMedalFonts.names[1], size: 41 * scale)!
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white, .kern: 0]
        let width = text.size(withAttributes: attributes).width
        text.draw(at: CGPoint(x: (bounds.width - width) / 2, y: 41 * 1.1 * scale - font.ascender), withAttributes: attributes)
        accessibilityLabel = text as String
    }
}

private struct DayOneMedalSparkView: View {
    let sparks: [DayOneMedalSpark]
    let clock: Double
    let scale: Double
    var body: some View {
        Canvas { context, _ in
            var ink = context
            ink.blendMode = .plusLighter
            for spark in sparks {
                let t = clock - spark.born
                let remaining = 1 - t / spark.life
                let x = spark.point.x + spark.velocity.x * t
                let y = spark.point.y + spark.velocity.y * t + 180 * scale * t * t
                var path = Path()
                path.move(to: CGPoint(x: x, y: y))
                path.addLine(to: CGPoint(x: x - spark.velocity.x * 0.018, y: y - spark.velocity.y * 0.018))
                ink.stroke(path, with: .color(DayOneMedalColour.hex(remaining > 0.65 ? 0xFFF3D6 : 0xFF8A2A).opacity(remaining * remaining * 0.85)), style: StrokeStyle(lineWidth: 0.85 * scale, lineCap: .round))
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview { DayOneMedalDemo() }

private struct DayOneMedalFadingText: View {
    let text: String
    let time: Double
    var alignment: Alignment = .center
    @State private var previous: String?
    @State private var changedAt = -1.0

    var body: some View {
        let amount = changedAt < 0 ? 1 : min(1, max(0, (time - changedAt) / 0.16))
        ZStack(alignment: alignment) {
            if let previous, amount < 1 { Text(previous).opacity(1 - amount) }
            Text(text).opacity(amount)
        }
        .onChange(of: text) { old, _ in
            previous = old
            changedAt = time
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}
