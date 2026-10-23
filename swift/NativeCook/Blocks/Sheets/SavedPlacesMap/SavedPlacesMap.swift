import SwiftUI
import UIKit

struct SavedPlacesMap: View {
    static let loop = SavedPlacesMapMotion.LOOP
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = SavedPlacesMapPlayback()
    @State private var timelinePaused = true

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        SavedPlacesMapAssets.prepare()
    }

    /// Reduce Motion or a screen reader, followed live: the loop settles to the pin's frame and stops.
    private var calm: Bool { reduceMotion || voiceOver }

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let u = size.width / SavedPlacesMapTheme.REF_PX_W
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: timelinePaused || scenePhase != .active)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = playback.frame(at: now)
                SavedPlacesMapStage(frame: frame, reduced: reduceMotion, size: size, u: u)
                    .overlay {
                        SavedPlacesMapTouch(
                            playback: playback,
                            frame: frame,
                            reduced: reduceMotion,
                            onActivity: { timelinePaused = false }
                        )
                    }
                    .onChange(of: playback.shouldTick(at: now)) { _, moving in
                        if !moving { timelinePaused = true }
                    }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
        }
        .background(SavedPlacesMapTheme.color(0xDDEEF8))
        .ignoresSafeArea()
        .preferredColorScheme(.light)
        .onAppear {
            playback.start(autoPlay: autoPlay, reduced: reduceMotion, calm: calm, at: clock)
            timelinePaused = !playback.shouldTick(at: clock)
        }
        .onChange(of: autoPlay) { _, _ in reconfigure() }
        .onChange(of: reduceMotion) { _, _ in reconfigure() }
        .onChange(of: voiceOver) { _, _ in reconfigure() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { timelinePaused = !playback.shouldTick(at: clock) }
        }
    }

    private func reconfigure() {
        playback.configure(autoPlay: autoPlay, reduced: reduceMotion, calm: calm, at: clock)
        timelinePaused = !playback.shouldTick(at: clock)
    }

    private var clock: Double { Date().timeIntervalSinceReferenceDate }
}

/// Every layer of the screen, bottom to top, as a function of one motion frame.
private struct SavedPlacesMapStage: View {
    typealias Theme = SavedPlacesMapTheme
    typealias Motion = SavedPlacesMapMotion
    typealias Art = SavedPlacesMapArt

    let frame: Motion.Frame
    let reduced: Bool
    let size: CGSize
    let u: Double

    var body: some View {
        let sheetOffset = Art.riseRef * u * frame.sheet
        ZStack(alignment: .topLeading) {
            // The map under its progressive blur: the scene, and a blurred copy of its top masked by a gradient.
            SavedPlacesMapScene(frame: frame, reduced: reduced, size: size, u: u)
            blurredTop
            LinearGradient(
                stops: Theme.TopBlur.fadeStops.map { .init(color: .white.opacity($0.1), location: $0.0) },
                startPoint: .top, endPoint: .bottom
            )
            .frame(width: size.width, height: Theme.TopBlur.fadeH * u)
            .allowsHitTesting(false)

            // back + badge, sharp above the blur
            Canvas { context, _ in
                var ctx = context
                ctx.scaleBy(x: u, y: u)
                Art.drawTopChrome(ctx)
            }
            .frame(width: size.width, height: 400 * u)

            // floating controls ride the sheet and fade as it rises
            SavedPlacesMapControlsLayer(u: u, size: size)
                .offset(y: -sheetOffset)
                .opacity(1 - Motion.easeOut(Motion.segment(frame.sheet, Motion.CONTROLS_FADE.0, Motion.CONTROLS_FADE.1)))

            SavedPlacesMapSheetLayer(u: u, size: size)
                .offset(y: -sheetOffset)
            Canvas { context, _ in
                var ctx = context
                ctx.scaleBy(x: u, y: u)
                Art.drawSheetDynamic(ctx, frame: frame, reduced: reduced)
            }
            .frame(width: size.width, height: size.height)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private var blurredTop: some View {
        let h = Theme.TopBlur.h * u
        return SavedPlacesMapScene(frame: frame, reduced: reduced, size: size, u: u)
            .frame(width: size.width, height: h, alignment: .top)
            .clipped()
            .blur(radius: Theme.TopBlur.sigma * u, opaque: true)
            .overlay(Color.white.opacity(Theme.TopBlur.tint))
            .mask(
                LinearGradient(
                    stops: [.init(color: .black, location: 0),
                            .init(color: .black, location: Theme.TopBlur.solid / Theme.TopBlur.h),
                            .init(color: .clear, location: 1)],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(width: size.width, height: h)
            )
            .frame(width: size.width, height: h, alignment: .top)
            .allowsHitTesting(false)
    }
}

/// Sky, the pitched map plane, horizon haze and the standing pins.
private struct SavedPlacesMapScene: View {
    typealias Theme = SavedPlacesMapTheme
    typealias Motion = SavedPlacesMapMotion
    typealias Art = SavedPlacesMapArt

    let frame: Motion.Frame
    let reduced: Bool
    let size: CGSize
    let u: Double

    var body: some View {
        let tilt = reduced ? 0 : Motion.tiltOf(frame.sheet)
        ZStack(alignment: .topLeading) {
            // sky: only seen above the horizon when the map pitches
            LinearGradient(colors: [Theme.Depth.sky.0, Theme.Depth.sky.1], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: Theme.mapH * u)

            // ground: the map plane, pitched with the sheet about (originX, originY)
            ZStack(alignment: .topLeading) {
                SavedPlacesMapGroundBase(u: u)
                if tilt > 0 {
                    Canvas { context, _ in
                        var ctx = context
                        ctx.scaleBy(x: u, y: u)
                        Art.drawGroundMarks(ctx, tilt: tilt)
                    }
                    .frame(width: size.width, height: size.height)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .projectionEffect(ProjectionTransform(Self.groundTransform(tilt: tilt, u: u)))

            // haze over the horizon
            let fog = reduced ? 0 : Motion.easeOut(Motion.segment(tilt, Motion.TILT.fog.0, Motion.TILT.fog.1))
            if fog > 0.001 {
                LinearGradient(
                    stops: Theme.Depth.fogStops.map {
                        .init(color: Color(.sRGB, red: Theme.Depth.fogRGB.0, green: Theme.Depth.fogRGB.1, blue: Theme.Depth.fogRGB.2, opacity: $0.1), location: $0.0)
                    },
                    startPoint: .top, endPoint: .bottom
                )
                .frame(width: size.width, height: Theme.Depth.fogH * u)
                .opacity(fog)
            }

            // billboards: pins, glyphs, bubbles
            Canvas { context, _ in
                var ctx = context
                ctx.scaleBy(x: u, y: u)
                Art.drawBillboards(ctx, frame: frame, reduced: reduced)
            }
            .frame(width: size.width, height: size.height)

            // faded top-right control (under the blur, like the pin)
            Canvas { context, _ in
                var ctx = context
                ctx.scaleBy(x: u, y: u)
                Art.drawFadedChip(ctx)
            }
            .frame(width: size.width, height: 300 * u)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    /// translateY(lift·p) · perspective(P) · rotateX(pitch·p) · scale(1 + (scale − 1)·p), about (originX, originY).
    static func groundTransform(tilt p: Double, u: Double) -> CATransform3D {
        guard p > 0 else { return CATransform3DIdentity }
        let s = 1 + (Motion.TILT.scale - 1) * p
        let theta = Motion.TILT.pitch * p * .pi / 180
        let ox = Motion.TILT.originX * u, oy = Motion.TILT.originY * u
        var t = CATransform3DMakeTranslation(-ox, -oy, 0)
        t = CATransform3DConcat(t, CATransform3DMakeScale(s, s, 1))
        t = CATransform3DConcat(t, CATransform3DMakeRotation(theta, 1, 0, 0))
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / (Motion.TILT.perspective * u)
        t = CATransform3DConcat(t, perspective)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(0, Motion.TILT.lift * p * u, 0))
        return CATransform3DConcat(t, CATransform3DMakeTranslation(ox, oy, 0))
    }
}

/// The still part of the map plane: image, place labels and the highway shield. Redrawn only when `u` changes.
private struct SavedPlacesMapGroundBase: View {
    let u: Double
    var body: some View {
        Canvas { context, _ in
            var ctx = context
            ctx.scaleBy(x: u, y: u)
            SavedPlacesMapArt.drawGroundBase(ctx)
        }
        .frame(width: 1206 * u, height: SavedPlacesMapTheme.mapH * u)
    }
}

/// Chips and the locate button, drawn once; the stage moves and fades them.
private struct SavedPlacesMapControlsLayer: View {
    let u: Double
    let size: CGSize
    var body: some View {
        Canvas { context, _ in
            var ctx = context
            ctx.scaleBy(x: u, y: u)
            SavedPlacesMapArt.drawControls(ctx)
        }
        .frame(width: size.width, height: size.height)
    }
}

/// The sheet and its still contents, drawn once (shadows included); the stage moves it.
private struct SavedPlacesMapSheetLayer: View {
    let u: Double
    let size: CGSize
    var body: some View {
        Canvas { context, _ in
            var ctx = context
            ctx.scaleBy(x: u, y: u)
            SavedPlacesMapArt.drawSheetStatic(ctx, screenH: size.height / u, unit: u)
        }
        .frame(width: size.width, height: size.height + SavedPlacesMapArt.riseRef * 2 * u, alignment: .topLeading)
    }
}
