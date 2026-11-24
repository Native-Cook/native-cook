// Heat Haze Weather — a scorching afternoon you can scrub to evening.
//
// Dayline's today screen: the scene behind it is one Metal colour effect (HeatHazeWeather.metal), a
// desert road drawn in code with heat haze rising from it. The hour slider moves the afternoon: the
// sun sinks, the haze calms, the colours cool, the temperature rolls and the UV chip follows the
// forecast.
//
// The reusable piece is this view: give it a place, a date, a forecast (an hourly temperature and UV
// index from 12 PM to 9 PM, and the sunset) and three stats.
//
// autoPlay: loop = 10 s. A ghost finger drags the hour from 1 PM to 3:30 PM (the haze builds and the
// temperature counts up), rests on the road's mirage to add a shimmer, drags on to 8 PM (it calms),
// then back to 1 PM; frame 10 s equals frame 0. The slider (with momentum and a rubber band) or a
// touch on the scene (a local shimmer under the finger) takes over at once; 2 s after the last touch
// the hour eases back to 1 PM and the loop restarts as the haze's flow wraps. Reduced motion: 1 PM,
// still.

import SwiftUI

struct HeatHazeStat: Hashable {
    var label: String
    var value: String
}

struct HeatHazeWeather: View {
    static let loop: Double = HeatHazeWeatherMotion.loop
    private typealias M = HeatHazeWeatherMotion
    typealias Forecast = HeatHazeWeatherMotion.Forecast

    private let place: String
    private let date: String
    private let forecast: Forecast
    private let stats: [HeatHazeStat]
    private let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @State private var clock = HeatHazeWeatherClock()
    @State private var sliding = false
    @State private var poking = false
    @GestureState private var slideHeld = false
    @GestureState private var pokeHeld = false

    init(
        place: String = "Saltbrush Flats",
        date: String = "Thursday, 14 July",
        forecast: Forecast = HeatHazeWeatherMotion.forecast,
        stats: [HeatHazeStat] = [
            HeatHazeStat(label: "Wind", value: "NE 9 km/h"),
            HeatHazeStat(label: "Humidity", value: "11%"),
            HeatHazeStat(label: "Air", value: "Good · 38"),
        ],
        autoPlay: Bool = true
    ) {
        self.place = place
        self.date = date
        self.forecast = forecast
        self.stats = stats
        self.autoPlay = autoPlay
    }

    private static let ink = Color(hex: 0xFBF6EE)
    private static let mute = Color.rgba(251, 246, 238, 0.74)
    private static let cardMute = Color.rgba(251, 246, 238, 0.74)
    private static let digit = 104.0
    private static let ghost = 46.0
    private static let conditions = ["Hot and hazy", "Scorching", "Cooling off"]
    private static let uvWords = ["Low", "Moderate", "High", "Very high", "Extreme"]
    /// 1: pass the scene through the React Native build's sRGB → Display P3 step
    private static let wide: Float = 0

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width, h = proxy.size.height + top + bottom
            let _ = clock.width = w
            let _ = clock.height = h
            let _ = clock.trackW = w - 76
            let _ = clock.auto = autoPlay && !reduced
            let _ = reduced ? clock.still() : ()
            ZStack(alignment: .topLeading) {
                live { f in scene(f, w, h) }
                    .frame(width: w, height: h)
                    .contentShape(Rectangle())
                    .gesture(reduced ? nil : poke)
                page(w, h, top, bottom)
                live { f in sceneGhost(f) }
                    .allowsHitTesting(false)
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .coordinateSpace(name: "heatHazeWeather")
            .clipped()
            .offset(y: -top)
        }
        .background(Color(hex: 0x2C69AE).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onChange(of: slideHeld) { _, now in
            // a touch the system took away never reaches onEnded
            if !now, sliding {
                sliding = false
                clock.sliderCancelled()
            }
        }
        .onChange(of: pokeHeld) { _, now in
            if !now, poking {
                poking = false
                clock.sceneEnded()
            }
        }
    }

    private func live<Content: View>(@ViewBuilder _ content: @escaping (HeatHazeWeatherClock.Frame) -> Content) -> some View {
        TimelineView(.animation(paused: reduced)) { context in
            content(clock.frame(at: context.date))
        }
    }

    // MARK: - the scene

    private func scene(_ f: HeatHazeWeatherClock.Frame, _ w: Double, _ h: Double) -> some View {
        let dusk = M.duskAt(f.hour, forecast.sunset)
        let heat = M.heatAt(M.sample(forecast.temps, f.hour), dusk)
        let sun = M.sunAt(f.hour, forecast.sunset, w, h)
        return Rectangle()
            .fill(Color.black)
            .colorEffect(ShaderLibrary.heatHazeWeatherScene(
                .float2(Float(w), Float(h)),
                .float2(Float(sun.0), Float(sun.1)),
                .float(Float(heat)),
                .float(Float(dusk)),
                .float(Float(f.phase)),
                .float3(Float(f.touchX), Float(f.touchY), Float(f.touchOn)),
                .float(Float(displayScale)),
                .float(Self.wide)
            ))
            .accessibilityHidden(true)
    }

    private func sceneGhost(_ f: HeatHazeWeatherClock.Frame) -> some View {
        ghostCircle(Self.ghost, stroke: 0.62)
            .opacity(f.sceneGhostA * 0.9)
            .offset(x: f.sceneGhostX - Self.ghost / 2, y: f.sceneGhostY - Self.ghost / 2)
    }

    private func ghostCircle(_ size: Double, stroke: Double) -> some View {
        Circle()
            .fill(Color.rgba(251, 246, 238, 0.16))
            .overlay(Circle().strokeBorder(Color.rgba(251, 246, 238, stroke), lineWidth: 1.5))
            .overlay(Circle().fill(Color.rgba(251, 246, 238, 0.5)).frame(width: 14, height: 14))
            .frame(width: size, height: size)
    }

    private var poke: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("heatHazeWeather"))
            .updating($pokeHeld) { _, state, _ in state = true }
            .onChanged { value in
                if !poking {
                    poking = true
                    clock.sceneBegan(x: value.location.x, y: value.location.y)
                } else {
                    clock.sceneMoved(x: value.location.x, y: value.location.y)
                }
            }
            .onEnded { _ in
                poking = false
                clock.sceneEnded()
            }
    }

    private var slide: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($slideHeld) { _, state, _ in state = true }
            .onChanged { value in
                if !sliding {
                    sliding = true
                    clock.sliderBegan(x: value.startLocation.x)
                    clock.sliderMoved(x: value.location.x)
                } else {
                    clock.sliderMoved(x: value.location.x)
                }
            }
            .onEnded { value in
                sliding = false
                clock.sliderEnded(velocityX: value.velocity.width)
            }
    }

    // MARK: - the page

    private func page(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> some View {
        let compact = h < 740
        return VStack(alignment: .leading, spacing: 0) {
            heading(compact)
                .allowsHitTesting(false)
            Spacer(minLength: 0)
            card(w)
        }
        .padding(.horizontal, 20)
        .padding(.top, top + (compact ? 6 : 12))
        .padding(.bottom, max(bottom, 12) + (compact ? 4 : 8))
        .frame(width: w, height: h, alignment: .topLeading)
    }

    private func heading(_ compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(place)
                .font(.system(size: 22, weight: .bold))
                .tracking(-0.3)
                .foregroundStyle(Self.ink)
                .shadow(color: .rgba(20, 30, 60, 0.25), radius: 4)
                .accessibilityAddTraits(.isHeader)
            Text(date)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Self.mute)
                .padding(.top, 2)
            live { f in temperature(f) }
                .frame(height: Self.digit, alignment: .topLeading)
                .padding(.top, compact ? 0 : 4)
            live { f in condition(f) }
                .frame(height: 26, alignment: .topLeading)
                .padding(.top, -2)
            live { f in chip(f) }
                .padding(.top, 10)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func temperature(_ f: HeatHazeWeatherClock.Frame) -> some View {
        let temp = M.rolled(M.sample(forecast.temps, f.hour))
        return HStack(alignment: .top, spacing: 0) {
            digitStrip(M.stripOf(temp, tens: true))
            digitStrip(M.stripOf(temp, tens: false))
            Text("°")
                .font(.system(size: 64, weight: .medium))
                .foregroundStyle(Self.ink)
                .frame(height: 84)
                .padding(.leading, 2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(Int(M.sample(forecast.temps, f.hour).rounded())) degrees")
    }

    private func digitStrip(_ strip: Double) -> some View {
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 96, weight: .bold).monospacedDigit())
                    .tracking(-3)
                    .foregroundStyle(Self.ink)
                    .shadow(color: .rgba(20, 30, 60, 0.22), radius: 7)
                    .fixedSize()
                    // React Native's 104 pt line box sits the glyphs 5 pt higher than a centred line
                    .offset(y: -5)
                    .frame(height: Self.digit)
                    .clipped()
            }
        }
        .offset(y: -Self.digit * strip)
        .frame(height: Self.digit, alignment: .top)
        .clipped()
        .accessibilityHidden(true)
    }

    private func condition(_ f: HeatHazeWeatherClock.Frame) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { i in
                let k = f.condFilm * M.conditionAt(f.hour, i) + (1 - f.condFilm) * M.clamp01(1 - abs(f.condPicked - Double(i)))
                Text(Self.conditions[i])
                    .font(.system(size: 20, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Self.ink)
                    .fixedSize()
                    .opacity(k)
                    .offset(y: 6 * (1 - k))
                    .accessibilityHidden(k < 0.5)
            }
        }
    }

    private func fade(_ labels: [String], value: Double, size: Double, weight: Font.Weight, width: Double) -> some View {
        ZStack(alignment: .leading) {
            ForEach(Array(labels.enumerated()), id: \.offset) { i, label in
                let d = Double(i) - value
                Text(label)
                    .font(.system(size: size, weight: weight).monospacedDigit())
                    .foregroundStyle(Self.ink)
                    .fixedSize()
                    .opacity(M.clamp01(1 - abs(d)))
                    .offset(y: 6 * d)
                    .accessibilityHidden(true)
            }
        }
        .frame(width: width, height: 30, alignment: .leading)
    }

    private func chip(_ f: HeatHazeWeatherClock.Frame) -> some View {
        let uv = M.sample(forecast.uv, f.hour)
        return HStack(spacing: 0) {
            Text("UV")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Self.mute)
            fade((0...11).map(String.init), value: M.rolled(uv + 0.5, 0.6), size: 15, weight: .bold, width: 22)
                .padding(.leading, 5)
            Circle().fill(Self.mute).frame(width: 3, height: 3)
                .padding(.trailing, 8)
            fade(Self.uvWords, value: M.uvBandAt(uv), size: 14, weight: .semibold, width: 74)
        }
        .padding(.horizontal, 12)
        .frame(height: 30)
        .background(Capsule().fill(Color.rgba(16, 24, 48, 0.24)))
        .overlay(Capsule().strokeBorder(Color.rgba(251, 246, 238, 0.32), lineWidth: 1 / displayScale))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("UV index \(Int(uv.rounded()))")
    }

    private func card(_ w: Double) -> some View {
        let trackW = w - 76
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Hourly")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Self.ink)
                Spacer(minLength: 0)
                Text("Sunset \(M.formatHour(forecast.sunset))")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.cardMute)
            }
            .padding(.bottom, 6)
            ZStack(alignment: .topLeading) {
                live { f in HeatHazeWeatherSlider(frame: f, trackW: trackW) }
                Color.clear
                    .frame(width: trackW, height: 36)
                    .contentShape(Rectangle())
                    .gesture(reduced ? nil : slide)
                    .offset(y: 30)
                    .accessibilityElement()
                    .accessibilityLabel("Hour")
                    .accessibilityAdjustableAction { direction in
                        clock.nudge(direction == .increment ? 0.5 : -0.5)
                    }
            }
            HStack(alignment: .top, spacing: 0) {
                ForEach(stats, id: \.self) { s in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.label)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Self.cardMute)
                        Text(s.value)
                            .font(.system(size: 15, weight: .semibold).monospacedDigit())
                            .foregroundStyle(Self.ink)
                    }
                    .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.top, 12)
            .overlay(alignment: .top) {
                Rectangle().fill(Color.rgba(251, 246, 238, 0.22)).frame(height: 1 / displayScale)
            }
            .padding(.top, 14)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(width: w - 40, alignment: .leading)
        .background {
            ZStack {
                HeatHazeWeatherBlur(intensity: 0.28)
                Color.rgba(28, 20, 18, 0.28)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(Color.rgba(251, 246, 238, 0.3), lineWidth: 1 / displayScale))
        .compositingGroup()
        .shadow(color: .rgba(20, 12, 8, 0.18), radius: 2, y: 2)
        .shadow(color: .rgba(20, 12, 8, 0.22), radius: 20, y: 18)
        .contentShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
    }
}

/// expo-blur's BlurView: a dark UIBlurEffect held part of the way in by a paused animator
struct HeatHazeWeatherBlur: UIViewRepresentable {
    let intensity: Double

    final class BlurView: UIVisualEffectView {
        private var animator: UIViewPropertyAnimator?

        func apply(_ intensity: Double) {
            animator?.stopAnimation(true)
            effect = nil
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
                self?.effect = UIBlurEffect(style: .dark)
            }
            animator.fractionComplete = intensity
            animator.pausesOnCompletion = true
            self.animator = animator
        }

        deinit { animator?.stopAnimation(true) }
    }

    func makeUIView(context: Context) -> BlurView {
        let view = BlurView(effect: nil)
        view.isUserInteractionEnabled = false
        view.apply(intensity)
        return view
    }

    func updateUIView(_ view: BlurView, context: Context) {}
}
