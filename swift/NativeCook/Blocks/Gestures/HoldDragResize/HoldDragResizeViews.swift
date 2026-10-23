import SwiftUI
import UIKit

private typealias T = HoldDragResizeTokens

private extension Color {
    static let hdrChrome = Color(red: 28 / 255, green: 28 / 255, blue: 30 / 255)
    static let hdrChromeHi = Color(red: 44 / 255, green: 44 / 255, blue: 46 / 255)
    static let hdrMute = Color(red: 142 / 255, green: 142 / 255, blue: 147 / 255)
    static let hdrFaint = Color(red: 90 / 255, green: 90 / 255, blue: 94 / 255)
    static let hdrDivider = Color(red: 42 / 255, green: 42 / 255, blue: 44 / 255)
    static let hdrToolDim = Color(red: 92 / 255, green: 92 / 255, blue: 96 / 255)
    static let hdrBlue = Color(red: 30 / 255, green: 143 / 255, blue: 255 / 255)
    static let hdrStrip = Color(red: 21 / 255, green: 21 / 255, blue: 23 / 255)
    static let hdrSticker = Color(red: 63 / 255, green: 163 / 255, blue: 77 / 255)
    static let hdrStickerRing = Color(red: 184 / 255, green: 233 / 255, blue: 134 / 255)
}

struct HoldDragResizePreview: View {
    @ObservedObject var motion: HoldDragResizeMotion
    let width: Double
    let reduceMotion: Bool

    var body: some View {
        let frame = HoldDragResizeGeometry.preview(height: motion.shownHeight, width: width)
        let fullness = HoldDragResizeGeometry.fullness(motion.shownHeight)
        let radius = T.layout["previewRadius"]! * (1 - fullness)
        ZStack {
            ForEach(T.previewOrder, id: \.self) { index in
                HoldDragResizeImage.named(T.clips[index].asset)
                    .resizable()
                    .scaledToFill()
                    .frame(width: frame.width, height: frame.height)
                    .scaleEffect(1 + push(index: index))
                    .opacity(layerOpacity(index: index))
                    .clipped()
            }
            caption(frame: frame)
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .stroke(Color.white.opacity(T.upgrade.rim * motion.held), lineWidth: 1)
            badge
        }
        .frame(width: frame.width, height: frame.height)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        .background(Color.hdrChrome)
        .contentShape(Rectangle())
        .scaleEffect(1 - (1 - T.upgrade.press) * motion.press * (1 - motion.held) - (1 - T.resize.lift) * motion.held)
        .shadow(color: .black.opacity(T.upgrade.shadowOpacity * motion.held), radius: T.upgrade.shadowRadius, y: T.upgrade.shadowY * motion.held)
        .position(x: frame.midX, y: frame.midY)
    }

    private func layerOpacity(index: Int) -> Double {
        let clip = Double(T.timing.clip) / 1_000
        let fade = Double(T.timing.crossfade) / 1_000
        let clock = motion.clock
        let start = Double(index) * clip - fade
        if index == 0 {
            return clock < clip ? 1 - min(max((clock - (clip - fade)) / fade, 0), 1) : min(max((clock - (T.loop - fade)) / fade, 0), 1)
        }
        if index == 1 { return clock >= start && clock < Double(index + 1) * clip ? 1 : 0 }
        return clock >= start && clock < Double(index + 1) * clip ? min((clock - start) / fade, 1) : 0
    }

    private func push(index: Int) -> Double {
        guard !reduceMotion else { return 0 }
        let clip = Double(T.timing.clip) / 1_000
        let fade = Double(T.timing.crossfade) / 1_000
        let start = Double(index) * clip - fade
        let local = ((motion.clock - start).truncatingRemainder(dividingBy: T.loop) + T.loop).truncatingRemainder(dividingBy: T.loop)
        return T.timing.kenBurns * min(local / (clip + fade), 1)
    }

    @ViewBuilder private func caption(frame: CGRect) -> some View {
        let caption = T.clips[motion.currentClip].caption
        Text(caption)
            .font(.system(size: 20 * motion.shownHeight / T.sizes[2], weight: .heavy))
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
            .shadow(color: .black.opacity(0.6), radius: 6, y: 1)
            .frame(width: frame.width)
            .position(x: frame.width / 2, y: frame.height * 0.62)
            .animation(.linear(duration: Double(T.timing.crossfade) / 1_000), value: motion.currentClip)
    }

    private var badge: some View {
        HStack(spacing: T.badge.gap) {
            HStack(spacing: T.space.xs) {
                ForEach(T.sizeNames.indices, id: \.self) { index in
                    Circle().fill(index == motion.nearIndex ? Color.white : Color.white.opacity(0.35))
                        .frame(width: T.badge.dot, height: T.badge.dot)
                }
            }
            Text(T.sizeNames[motion.nearIndex])
                .font(.system(size: 13, weight: .semibold))
                .contentTransition(.opacity)
        }
        .padding(.horizontal, T.badge.padX)
        .frame(height: T.badge.height)
        .background(.ultraThinMaterial.opacity(0.75), in: Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 0.5))
        .opacity(motion.held)
        .scaleEffect(0.96 + 0.04 * motion.held)
        .offset(y: frameBadgeOffset)
        .frame(maxHeight: .infinity, alignment: .bottom)
        .padding(.bottom, T.badge.bottom)
        .animation(.easeOut(duration: motion.held > 0.5 ? Double(T.upgrade.badgeInMilliseconds) / 1_000 : Double(T.upgrade.badgeOutMilliseconds) / 1_000), value: motion.held)
    }

    private var frameBadgeOffset: Double { T.upgrade.badgeRise * (1 - motion.held) }
}

struct HoldDragResizeTimeline: View {
    @ObservedObject var motion: HoldDragResizeMotion
    let width: Double

    var body: some View {
        let shift = HoldDragResizeGeometry.belowShift(motion.lag)
        let squeeze = HoldDragResizeGeometry.squeeze(motion.shownHeight)
        let opacity = 1 - min(HoldDragResizeGeometry.fullness(motion.shownHeight) * 1.6, 1)
        ZStack(alignment: .topLeading) {
            controls.offset(y: T.squeeze.controls * squeeze)
            Rectangle().fill(Color.hdrDivider).frame(width: width, height: 1)
                .offset(y: 58 + T.squeeze.ruler * squeeze)
            rail(squeeze: squeeze)
            track(icon: "music.note", text: T.copy["audio"]!).offset(y: 169 + T.squeeze.below * squeeze)
            track(icon: "textformat", text: T.copy["text"]!).offset(y: 211 + T.squeeze.below * squeeze)
            Rectangle().fill(.white).frame(width: 2, height: 180).offset(x: width / 2 - 1, y: 70 + T.squeeze.below * squeeze)
            Text(T.copy["hint"]!)
                .font(.system(size: 12))
                .foregroundStyle(Color.hdrMute)
                .frame(width: width, height: 24)
                .background(Color.black)
                .offset(y: 284 + T.squeeze.hint * squeeze - shift)
        }
        .frame(width: width, height: 320, alignment: .topLeading)
        .offset(y: T.frames[0].top + T.sizes[0] + shift)
        .opacity(opacity)
    }

    private var controls: some View {
        HStack {
            HoldDragResizeCircleButton(symbol: motion.playing ? "pause.fill" : "play.fill", label: motion.playing ? "Pause" : "Play") { motion.togglePlay() }
            Spacer()
            HStack(spacing: 8) {
                HoldDragResizeCircleButton(symbol: "arrow.counterclockwise", label: "Undo") {}
                HoldDragResizeCircleButton(symbol: "arrow.clockwise", color: .hdrFaint, label: "Redo") {}
            }
        }
        .padding(.horizontal, T.layout["gutter"]!)
        .frame(width: width, height: T.layout["control"]!)
        .overlay {
            (Text(String(format: "0:%02d", motion.currentSecond)).foregroundStyle(Color.hdrMute) + Text("  /  0:12").foregroundStyle(Color.hdrFaint))
                .font(.system(size: 13, weight: .medium).monospacedDigit())
        }
        .offset(y: 13)
    }

    private func rail(squeeze: Double) -> some View {
        let period = T.loop * T.timing.stripSpeed
        let firstX = width / 2 - period * Double(T.repeatCount)
        let count = T.clips.count * T.thumbsPerClip * T.repeatCount * 2
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                ForEach(0..<Int(ceil(period * Double(T.repeatCount) * 2 / T.layout["rulerPitch"]!)), id: \.self) { mark in
                    if mark.isMultiple(of: 2) {
                        Circle().fill(Color.hdrMute).frame(width: 3, height: 3)
                            .offset(x: T.rulerOffset + Double(mark) * T.layout["rulerPitch"]! - 1.5, y: 4.5)
                    } else {
                        Text(mark % 4 == 1 ? "10s" : "30s")
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundStyle(Color.hdrMute)
                            .frame(width: T.layout["rulerLabelW"]!)
                            .offset(x: T.rulerOffset + Double(mark) * T.layout["rulerPitch"]! - T.layout["rulerLabelW"]! / 2)
                    }
                }
                HStack(spacing: 0) {
                    ForEach(0..<count, id: \.self) { index in
                        HoldDragResizeImage.named(T.clips[(index / T.thumbsPerClip) % T.clips.count].asset)
                            .resizable().scaledToFill()
                            .frame(width: T.layout["thumb"]!, height: T.layout["strip"]!).clipped()
                    }
                }
                .offset(y: T.layout["ruler"]! + T.layout["stripGap"]!)
            }
            .frame(width: period * Double(T.repeatCount) * 2, height: 96, alignment: .topLeading)
            .offset(x: firstX - motion.clock * T.timing.stripSpeed)

            RoundedRectangle(cornerRadius: 3).fill(.white)
                .frame(width: T.trimHandle.width, height: T.trimHandle.height)
                .offset(x: T.trimHandle.x - T.trimHandle.width / 2, y: T.layout["ruler"]! + T.layout["stripGap"]! + 15)
            Circle().fill(Color.hdrSticker).frame(width: T.sticker.size, height: T.sticker.size)
                .overlay(Circle().stroke(Color.hdrStickerRing, lineWidth: 2))
                .overlay(Image(systemName: "face.smiling").font(.system(size: 14)))
                .offset(x: T.sticker.x - T.sticker.size / 2, y: T.layout["ruler"]! + T.layout["stripGap"]! + 17)
        }
        .frame(width: width, height: 96, alignment: .topLeading)
        .clipped()
        .offset(y: 69 + T.squeeze.below * squeeze)
    }

    private func track(icon: String, text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 13, weight: .bold))
            Text(text).font(.system(size: 13, weight: .medium))
            Spacer()
        }
        .foregroundStyle(Color.hdrMute)
        .padding(.leading, T.layout["trackIndent"]!)
        .frame(width: width, height: T.layout["track"]!, alignment: .leading)
        .background(Color.hdrChrome)
    }
}

struct HoldDragResizeChrome: View {
    @ObservedObject var motion: HoldDragResizeMotion
    let width: Double
    let height: Double
    let safeTop: Double
    let safeBottom: Double

    private let tools = [("textformat", "Text", false), ("face.smiling.inverse", "Sticker", false), ("music.note.list", "Audio", false), ("play.rectangle.fill", "Add clips", true), ("square.on.square.fill", "Overlay", false), ("scissors", "Edit", true), ("mic.fill", "Voiceover", false)]
    private let fullTools = ["camera", "textformat", "face.smiling", "music.note", "square.3.layers.3d", "slider.horizontal.3"]

    var body: some View {
        let fullness = HoldDragResizeGeometry.fullness(motion.shownHeight)
        let editorOpacity = 1 - min(fullness * 1.6, 1)
        let overlayOpacity = min(max((fullness - 0.85) / 0.15, 0), 1)
        ZStack(alignment: .topLeading) {
            HStack {
                HoldDragResizeCircleButton(symbol: "chevron.up", label: "Smallest preview") { motion.step(-T.sizes.count) }
                Spacer()
                HoldDragResizeCircleButton(symbol: "arrow.right", background: .hdrBlue, label: "Next") {}
            }
            .frame(width: width - 40)
            .offset(x: 20, y: safeTop + T.layout["barY"]! - 16)
            .opacity(editorOpacity)

            toolRow
                .offset(y: height - T.layout["tools"]! - safeBottom + T.squeeze.tools * HoldDragResizeGeometry.squeeze(motion.shownHeight))
                .opacity(editorOpacity)

            fullScreenControls
                .opacity(overlayOpacity)
                .allowsHitTesting(overlayOpacity > 0.95)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private var toolRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: T.layout["toolGap"]!) {
                ForEach(Array(tools.enumerated()), id: \.offset) { _, tool in
                    Button {} label: {
                        VStack(spacing: 4) {
                            RoundedRectangle(cornerRadius: T.layout["toolRadius"]!)
                                .fill(Color.hdrChrome)
                                .frame(width: T.layout["tool"]!, height: T.layout["toolH"]!)
                                .overlay(Image(systemName: tool.0).font(.system(size: 19, weight: .bold)))
                            Text(tool.1).font(.system(size: 10.5, weight: .semibold)).lineLimit(1)
                        }
                        .foregroundStyle(tool.2 ? Color.hdrToolDim : Color.white)
                        .frame(width: T.layout["tool"]!)
                    }
                    .buttonStyle(HoldDragResizePressStyle())
                    .disabled(tool.2)
                }
            }
            .padding(.horizontal, T.layout["toolInset"]!)
            .padding(.top, 10)
        }
        .frame(width: width, height: T.layout["tools"]! + safeBottom, alignment: .top)
        .background(Color.black)
    }

    private var fullScreenControls: some View {
        ZStack(alignment: .topLeading) {
            HoldDragResizeCircleButton(symbol: "xmark", background: Color.hdrChrome.opacity(0.72), label: "Leave full screen") { motion.step(-1) }
                .offset(x: 20, y: safeTop + T.layout["barY"]! - 16)
            HStack {
                ForEach(Array(fullTools.enumerated()), id: \.offset) { index, symbol in
                    let u = min(max((HoldDragResizeGeometry.fullness(motion.shownHeight) - 0.85 - T.upgrade.stagger * Double(index)) / 0.1, 0), 1)
                    Circle().fill(Color.hdrChrome.opacity(0.72)).frame(width: 40, height: 40)
                        .overlay(Image(systemName: symbol).font(.system(size: 17, weight: .semibold)))
                        .opacity(u).offset(y: 8 * (1 - u))
                    if index < fullTools.count - 1 { Spacer() }
                }
            }
            .frame(width: width - 40).offset(x: 20, y: height - safeBottom - 112)
            HStack {
                HoldDragResizePill(title: "Edit video", blue: false) { motion.step(-1) }
                Spacer()
                HoldDragResizePill(title: "Next", blue: true, arrow: true) {}
            }
            .frame(width: width - 40).offset(x: 20, y: height - safeBottom - 60)
        }
    }
}

struct HoldDragResizeCoach: View {
    @ObservedObject var motion: HoldDragResizeMotion
    let width: Double
    let height: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.opacity(0.72)
            Circle().fill(Color.white.opacity(0.35)).frame(width: T.coachTouch.size, height: T.coachTouch.size)
                .offset(x: T.coachHand.x + T.coachTouch.x - T.coachTouch.size / 2, y: T.coachHand.y + T.coachTouch.y - T.coachTouch.size / 2 + T.demo.coachTravel * motion.coachProgress)
            HoldDragResizeHandShape().stroke(.white, style: StrokeStyle(lineWidth: 3.2, lineCap: .round, lineJoin: .round))
                .frame(width: T.coachHand.width, height: T.coachHand.height)
                .offset(x: T.coachHand.x, y: T.coachHand.y + T.demo.coachTravel * motion.coachProgress)
            Text(T.copy["coach"]!).font(.system(size: 22, weight: .bold)).foregroundStyle(.white).multilineTextAlignment(.center).lineSpacing(5)
                .frame(width: width).offset(y: T.coachTextTop)
            Button(T.copy["gotIt"]!) { motion.dismissCoach() }
                .font(.system(size: 13, weight: .semibold)).foregroundStyle(.black)
                .frame(width: T.coachButton.width, height: T.coachButton.height)
                .background(.white, in: Capsule())
                .buttonStyle(HoldDragResizePressStyle())
                .offset(x: T.coachButton.x, y: T.coachButton.y)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .opacity(motion.coach)
        .allowsHitTesting(motion.coach > 0.01)
        .accessibilityElement(children: .contain)
    }
}

struct HoldDragResizeGhost: View {
    @ObservedObject var motion: HoldDragResizeMotion
    var body: some View {
        Circle().fill(Color.white.opacity(0.35)).overlay(Circle().stroke(Color.white.opacity(0.7), lineWidth: 1))
            .frame(width: T.layout["finger"]!, height: T.layout["finger"]!)
            .scaleEffect(1 - 0.18 * motion.ghostPress)
            .opacity(motion.ghostOpacity)
            .position(motion.ghost)
            .allowsHitTesting(false)
    }
}

struct HoldDragResizeHandShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 14, y: 40)); path.addLine(to: CGPoint(x: 14, y: 9))
        path.addCurve(to: CGPoint(x: 24, y: 9), control1: CGPoint(x: 14, y: 2), control2: CGPoint(x: 24, y: 2))
        path.addLine(to: CGPoint(x: 24, y: 31)); path.addCurve(to: CGPoint(x: 33, y: 31), control1: CGPoint(x: 24, y: 25), control2: CGPoint(x: 33, y: 25))
        path.addLine(to: CGPoint(x: 33, y: 34)); path.addCurve(to: CGPoint(x: 42, y: 34), control1: CGPoint(x: 33, y: 28), control2: CGPoint(x: 42, y: 28))
        path.addLine(to: CGPoint(x: 42, y: 38)); path.addCurve(to: CGPoint(x: 42.5, y: 38), control1: CGPoint(x: 42, y: 33), control2: CGPoint(x: 48, y: 34))
        path.addLine(to: CGPoint(x: 42.5, y: 52)); path.addCurve(to: CGPoint(x: 25.5, y: 69), control1: CGPoint(x: 42.5, y: 62), control2: CGPoint(x: 35.5, y: 69))
        path.addLine(to: CGPoint(x: 21.5, y: 69)); path.addCurve(to: CGPoint(x: 5.5, y: 59), control1: CGPoint(x: 13.5, y: 69), control2: CGPoint(x: 9.5, y: 65))
        path.addLine(to: CGPoint(x: 3, y: 47)); path.addCurve(to: CGPoint(x: 7, y: 41), control1: CGPoint(x: 1, y: 44), control2: CGPoint(x: 3, y: 40))
        path.addCurve(to: CGPoint(x: 14, y: 46), control1: CGPoint(x: 10, y: 41), control2: CGPoint(x: 12, y: 43))
        return path
    }
}

struct HoldDragResizeCircleButton: View {
    let symbol: String
    var color: Color = .white
    var background: Color = .hdrChromeHi
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) { Image(systemName: symbol).font(.system(size: 14, weight: .bold)).foregroundStyle(color).frame(width: 32, height: 32).background(background, in: Circle()) }
            .buttonStyle(HoldDragResizePressStyle()).accessibilityLabel(label).contentShape(Rectangle().inset(by: -6))
    }
}

struct HoldDragResizePill: View {
    let title: String
    let blue: Bool
    var arrow = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) { Text(title); if arrow { Image(systemName: "arrow.right").font(.system(size: 15, weight: .semibold)) } }
                .font(.system(size: 15, weight: .semibold)).foregroundStyle(.white).padding(.horizontal, 18).frame(height: 44)
                .background(blue ? Color.hdrBlue : Color.hdrChrome.opacity(0.72), in: Capsule())
        }.buttonStyle(HoldDragResizePressStyle())
    }
}

struct HoldDragResizePressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(reduceMotion ? 1 : (configuration.isPressed ? T.upgrade.buttonPress : 1))
            .opacity(reduceMotion && configuration.isPressed ? 0.7 : 1)
            .animation(.timingCurve(0.33, 1, 0.68, 1, duration: Double(configuration.isPressed ? T.upgrade.pressInMilliseconds : T.upgrade.pressOutMilliseconds) / 1_000), value: configuration.isPressed)
    }
}
