import SwiftUI
import UIKit

struct PinchToSummarize: View {
    static let loop: TimeInterval = 10
    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var motion = PinchToSummarizeMotion()

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(minimumInterval: 1 / 60, paused: !motion.needsFrames(autoPlay: autoPlay, reduced: reduceMotion))) { timeline in
                PinchToSummarizeStage(
                    size: proxy.size,
                    safeTop: proxy.safeAreaInsets.top,
                    safeBottom: proxy.safeAreaInsets.bottom,
                    frame: motion.frame(at: timeline.date, autoPlay: autoPlay, reduced: reduceMotion),
                    motion: motion,
                    reduceMotion: reduceMotion
                )
            }
        }
        .ignoresSafeArea()
        .background(Color.white)
        .onAppear { motion.start(autoPlay: autoPlay, reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, value in motion.reduceMotionChanged(value) }
    }
}

private struct PinchToSummarizeStage: View {
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let frame: PinchToSummarizeFrame
    @ObservedObject var motion: PinchToSummarizeMotion
    let reduceMotion: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            PinchToSummarizeField(size: size, time: frame.lightTime)
                .opacity(frame.fieldAlpha)
            PinchToSummarizeMotes(size: size, time: frame.lightTime, alpha: frame.summaryAlpha)

            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(PinchToSummarizePalette.stack)
                .frame(width: size.width * 0.64, height: 270)
                .scaleEffect(0.92)
                .position(x: size.width / 2, y: 125 - 10 + 135)
                .opacity(frame.stackAlpha)
                .shadow(color: PinchToSummarizePalette.pageShadow.opacity(0.24), radius: 16, y: 12)

            PinchToSummarizeSummary(size: size, safeBottom: safeBottom, frame: frame, motion: motion)
            PinchToSummarizeArticle(size: size, safeTop: safeTop, safeBottom: safeBottom, frame: frame, motion: motion)
            PinchToSummarizeFlyingSentences(size: size, frame: frame)
            PinchToSummarizeStatusLabels(size: size, safeBottom: safeBottom, frame: frame)

            if frame.fingerAlpha > 0.001 {
                PinchToSummarizeFingers(size: size, frame: frame).allowsHitTesting(false)
            }
            PinchToSummarizePinchSurface(motion: motion).allowsHitTesting(!reduceMotion)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .background(Color.white)
    }
}

// MARK: Article

private struct PinchToSummarizeArticle: View {
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let frame: PinchToSummarizeFrame
    @ObservedObject var motion: PinchToSummarizeMotion

    private var crop: CGFloat { pow(max(0, min(1, frame.pageProgress)), 2.2) }
    private var cardWidth: CGFloat { size.width * 0.64 }
    private var pageWidth: CGFloat { PinchToSummarizeMath.mix(size.width, cardWidth, crop) }
    private var pageHeight: CGFloat { PinchToSummarizeMath.mix(size.height, 270, crop) }
    private var pageScale: CGFloat { pageWidth / size.width }

    var body: some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                Color.white.frame(height: safeTop)
                masthead
                heroPanel
                articleBody
                Color.clear.frame(height: 120 + safeBottom)
            }
            .frame(width: size.width, alignment: .top)
            .offset(y: -frame.scrollY - 6 * crop)

            Color.white.opacity(frame.dim).allowsHitTesting(false)
            Color.white.frame(width: size.width, height: safeTop)
            LinearGradient(colors: [.white, .white.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: 18).offset(y: safeTop).opacity(min(1, frame.scrollY / 18))
            toolbar.opacity(max(0, 1 - frame.pageProgress / 0.3))
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color.white)
        .frame(width: size.width, height: pageHeight / max(pageScale, 0.01), alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 18 * crop / max(pageScale, 0.01), style: .continuous))
        .scaleEffect(pageScale, anchor: .topLeading)
        .offset(x: (size.width - pageWidth) / 2 + frame.drift.width, y: 125 * crop - frame.lift + frame.drift.height)
        .opacity(frame.pageAlpha)
        .shadow(color: PinchToSummarizePalette.pageShadow.opacity(0.35 * frame.pageProgress), radius: 18 * frame.pageProgress, y: 14 * frame.pageProgress)
        .contentShape(Rectangle())
        .simultaneousGesture(DragGesture(minimumDistance: 8)
            .onChanged { value in guard frame.pageProgress < 0.08 else { return }; motion.scrollChanged(translation: value.translation.height) }
            .onEnded { value in motion.scrollEnded(translation: value.translation.height) })
        .onTapGesture { if frame.pageProgress > 0.82 { motion.showArticle(source: nil) } }
    }

    private var masthead: some View {
        HStack {
            HStack(spacing: 8) {
                PinchToSummarizeMeridianMark().frame(width: 34, height: 22)
                Text("MERIDIAN").font(.system(size: 25, weight: .black)).tracking(0.4).foregroundStyle(PinchToSummarizePalette.ink)
            }
            Spacer()
            Circle().fill(PinchToSummarizePalette.toolbar).frame(width: 28, height: 28)
                .overlay(Image(systemName: "person.fill").font(.system(size: 12, weight: .semibold)).foregroundStyle(PinchToSummarizePalette.inkSoft))
        }
        .padding(.horizontal, 16).frame(height: 42)
    }

    private var heroPanel: some View {
        let width = size.width - 16
        return VStack(spacing: 0) {
            Text("REVIEW").font(.system(size: 8, weight: .heavy)).tracking(0.8).foregroundStyle(PinchToSummarizePalette.ink)
                .padding(.horizontal, 8).frame(height: 16).background(Capsule().fill(Color.white))
            Text("My Week Living Inside the Halo One Visor")
                .font(.custom("Georgia-Bold", size: 32)).multilineTextAlignment(.center).foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true).padding(.horizontal, 20).padding(.top, 21)
            Text("Mara Okafor gets hands-on — and then some — in this in-depth Halo One review.")
                .font(.custom("Georgia-Bold", size: 15)).tracking(-0.3).multilineTextAlignment(.center).foregroundStyle(Color.white.opacity(0.78))
                .fixedSize(horizontal: false, vertical: true).padding(.horizontal, 20).padding(.top, 12)
            Image(uiImage: PinchToSummarizeAssets.hero).resizable().scaledToFill()
                .frame(width: width, height: width * 262 / 386).clipped().padding(.top, 17)
            Circle().fill(Color.white.opacity(0.78)).frame(width: 4, height: 4).padding(.top, 46).padding(.bottom, 12)
            Text("BY MARA OKAFOR").font(.system(size: 8, weight: .bold)).tracking(0.8).foregroundStyle(Color.white.opacity(0.78))
            Text("FEB 2, 2026").font(.system(size: 8, weight: .bold)).tracking(0.8).foregroundStyle(Color.white.opacity(0.78)).padding(.top, 4)
        }
        .padding(.top, 27).padding(.bottom, 42).frame(width: width)
        .background(PinchToSummarizePalette.panel).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 8)
    }

    private var articleBody: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Text("GEAR REVIEWS").font(.system(size: 13, weight: .heavy)).tracking(1.1)
                Rectangle().fill(PinchToSummarizePalette.rule).frame(height: 0.5)
            }.padding(.bottom, 16)
            ForEach(Array(PinchToSummarizeCopy.paragraphs.enumerated()), id: \.offset) { index, paragraph in
                PinchToSummarizeParagraph(paragraph: paragraph, index: index, mark: frame.mark, markAlpha: frame.markAlpha, flashIndex: frame.flashIndex, flash: frame.flash)
                    .padding(.bottom, 16)
            }
        }
        .padding(.horizontal, 20).padding(.top, 96)
    }

    private var toolbar: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(colors: [.white.opacity(0), .white], startPoint: .top, endPoint: .center).frame(height: 80 + safeBottom)
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 6).fill(PinchToSummarizePalette.toolbarInk.opacity(0.25)).frame(width: 22, height: 22).rotationEffect(.degrees(8)).offset(x: 3)
                    RoundedRectangle(cornerRadius: 6).fill(LinearGradient(colors: [.orange, .pink, PinchToSummarizePalette.accent], startPoint: .topLeading, endPoint: .bottomTrailing)).frame(width: 22, height: 22).rotationEffect(.degrees(-8))
                }.frame(width: 30, height: 30)
                Spacer()
                Button(action: motion.summarizeButton) {
                    Image(systemName: "plus").font(.system(size: 20, weight: .medium)).foregroundStyle(PinchToSummarizePalette.toolbarInk)
                        .frame(width: 56, height: 30).background(Capsule().fill(PinchToSummarizePalette.toolbar))
                }.buttonStyle(.plain)
                Spacer()
                Circle().fill(PinchToSummarizePalette.toolbar).frame(width: 30, height: 30)
                    .overlay(Image(systemName: "chevron.up").font(.system(size: 14, weight: .semibold)).foregroundStyle(PinchToSummarizePalette.toolbarInk))
            }
            .padding(.horizontal, 18).frame(height: 52).padding(.bottom, 19)
        }
        .frame(width: size.width, height: 100 + safeBottom, alignment: .bottom)
        .offset(y: size.height - 100 - safeBottom)
    }
}

private struct PinchToSummarizeParagraph: View {
    let paragraph: PinchToSummarizeParagraphCopy
    let index: Int
    let mark: CGFloat
    let markAlpha: CGFloat
    let flashIndex: Int?
    let flash: CGFloat

    var body: some View {
        let keyIndex = PinchToSummarizeCopy.keyParagraphIndices.firstIndex(of: index)
        let progress = keyIndex.map { min(1, max(0, mark - CGFloat($0))) } ?? 0
        let boosted = flashIndex == keyIndex ? flash : 0
        Text(attributed(progress: progress, boosted: boosted))
            .font(.custom("Georgia", size: 17)).lineSpacing(9).foregroundStyle(PinchToSummarizePalette.ink)
            .fixedSize(horizontal: false, vertical: true).opacity(keyIndex == nil ? 1 : max(0.02, markAlpha))
    }

    private func attributed(progress: CGFloat, boosted: CGFloat) -> AttributedString {
        let full = (paragraph.leadIn ?? "") + (paragraph.key ?? "") + paragraph.rest
        var value = AttributedString(full)
        if let lead = paragraph.leadIn, let range = value.range(of: lead) { value[range].font = .custom("Georgia-Bold", size: 17) }
        if progress > 0, let key = paragraph.key, let range = value.range(of: key) {
            value[range].backgroundColor = UIColor(PinchToSummarizePalette.marker.opacity(0.5 + 0.32 * progress + 0.18 * boosted))
        }
        return value
    }
}

private struct PinchToSummarizeMeridianMark: View {
    var body: some View {
        Canvas { context, _ in
            context.stroke(Path(ellipseIn: CGRect(x: 2.5, y: 2.5, width: 17, height: 17)), with: .color(PinchToSummarizePalette.ink), lineWidth: 3.4)
            context.stroke(Path(ellipseIn: CGRect(x: 14.5, y: 2.5, width: 17, height: 17)), with: .color(PinchToSummarizePalette.ink), lineWidth: 3.4)
        }
    }
}

// MARK: Background and transition chips

private struct PinchToSummarizeField: View {
    let size: CGSize
    let time: CGFloat
    var body: some View {
        Image(uiImage: time.truncatingRemainder(dividingBy: 11) < 5.5 ? PinchToSummarizeAssets.field : PinchToSummarizeAssets.fieldB)
            .resizable().interpolation(.high).scaledToFill().frame(width: size.width, height: size.height).clipped()
            .scaleEffect(1 + 0.015 * sin(time / 11 * .pi * 2)).overlay(Color.black.opacity(0.035))
    }
}

private struct PinchToSummarizeMotes: View {
    let size: CGSize
    let time: CGFloat
    let alpha: CGFloat
    var body: some View {
        Canvas { context, _ in
            guard alpha > 0.001 else { return }
            for i in 0..<22 {
                let x = CGFloat((i * 83 + 19) % 103) / 103 * size.width
                let speed = 18 + CGFloat((i * 11) % 23)
                let raw = CGFloat((i * 31) % 97) / 97 * size.height - time * speed
                let y = raw.truncatingRemainder(dividingBy: size.height + 40) + 20
                let r = 0.7 + 1.3 * CGFloat((i * 47 + 13) % 101) / 101
                context.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(0.2 * alpha)))
            }
        }.frame(width: size.width, height: size.height).allowsHitTesting(false)
    }
}

private struct PinchToSummarizeFlyingSentences: View {
    let size: CGSize
    let frame: PinchToSummarizeFrame
    private let text = ["THE HALO ONE is a computer you wear…", "It has the best screen I have worn…", "The split strap spreads the weight…", "It starts at $1,899…"]
    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { i in
                let u = PinchToSummarizeMath.sheet(min(1, max(0, (frame.flight - CGFloat(3 - i) * 0.1) / 0.7)))
                let source = CGPoint(x: 20, y: 245 + CGFloat(i) * 70)
                let targets = [CGPoint(x: 16, y: 425), CGPoint(x: 50, y: 570), CGPoint(x: 50, y: 655), CGPoint(x: 50, y: 735)]
                let target = targets[i]
                let bow = 28 + abs(target.y - source.y) * 0.14
                let x = PinchToSummarizeMath.mix(source.x, target.x, u) + sin(u * .pi) * bow * (i.isMultiple(of: 2) ? 1 : -1)
                let y = PinchToSummarizeMath.mix(source.y, target.y, u)
                Text(text[i]).font(.custom("Georgia", size: 14)).lineLimit(2)
                    .foregroundStyle(u < 0.35 ? PinchToSummarizePalette.ink : Color.white)
                    .padding(.horizontal, 6).padding(.vertical, 5).frame(width: min(230, size.width - 40), alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 9).fill(PinchToSummarizePalette.chipPaper.opacity(1 - u)).overlay(PinchToSummarizePalette.chipPill.opacity(u)))
                    .offset(x: x, y: y).scaleEffect(1 - 0.03 * u)
                    .opacity(frame.flightActive * (1 - PinchToSummarizeMath.span(u, 0.72, 1)))
            }
        }.frame(width: size.width, height: size.height, alignment: .topLeading).allowsHitTesting(false)
    }
}

// MARK: Summary

private struct PinchToSummarizeSummary: View {
    let size: CGSize
    let safeBottom: CGFloat
    let frame: PinchToSummarizeFrame
    @ObservedObject var motion: PinchToSummarizeMotion
    private var level: PinchToSummarizeLevel { PinchToSummarizeLevel(rawValue: frame.level) ?? .keyPoints }

    var body: some View {
        ZStack(alignment: .topLeading) {
            titleRow.offset(x: 16, y: 91)
            summaryContent.offset(y: 419)
            footer.offset(y: size.height - max(16, safeBottom - 10) - 44)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .opacity(frame.summaryAlpha).scaleEffect(frame.shareLift > 0 ? 1.03 : 1).offset(y: -12 * frame.shareLift)
        .shadow(color: PinchToSummarizePalette.shareShadow.opacity(0.28 * frame.shareLift), radius: 24, y: 18)
        .onLongPressGesture(minimumDuration: 0.35, pressing: motion.sharePressed) { }
    }

    private var titleRow: some View {
        HStack(spacing: 8) {
            Text("Halo One review: my week inside the visor").font(.system(size: 11, weight: .semibold)).foregroundStyle(.white).lineLimit(1)
            Spacer()
            HStack(spacing: 4) { ForEach(0..<5, id: \.self) { i in Capsule().fill(Color.white.opacity(i < Int(ceil(frame.readProgress * 5)) ? 0.9 : 0.24)).frame(width: 12, height: 4) } }
        }.frame(width: size.width - 32, height: 16)
    }

    private var summaryContent: some View {
        let data = PinchToSummarizeCopy.levels[level.rawValue]
        return VStack(alignment: .leading, spacing: 0) {
            Text(data.lead).font(.system(size: data.tldr ? 24 : 19, weight: data.tldr ? .bold : .semibold))
                .tracking(data.tldr ? -0.6 : -0.4).lineSpacing(data.tldr ? 6 : 7).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
                .onTapGesture { motion.showArticle(source: 0) }
            if level != .tldr {
                HStack(spacing: 8) {
                    ForEach(PinchToSummarizeCopy.entities(for: level), id: \.self) { entity in
                        Button(action: { motion.flashEntity(entity) }) {
                            Text(entity).font(.system(size: 13, weight: .semibold)).foregroundStyle(.white).padding(.horizontal, 12).frame(height: 28)
                                .background(Capsule().fill(Color.white.opacity(0.14)).overlay(Capsule().stroke(Color.white.opacity(0.32), lineWidth: 0.75)))
                        }.buttonStyle(.plain)
                    }
                }.padding(.top, 12)
            }
            ForEach(data.points, id: \.self) { index in
                PinchToSummarizePoint(point: PinchToSummarizeCopy.points[index], number: index, reveal: frame.summaryLineAlpha, entityFlash: frame.entityFlash) {
                    motion.showArticle(source: PinchToSummarizeCopy.points[index].source)
                }.padding(.top, 20)
            }
        }.padding(.horizontal, 16).frame(width: size.width, alignment: .leading)
    }

    private var footer: some View {
        HStack(spacing: 10) {
            HStack(spacing: 10) {
                Circle().fill(LinearGradient(colors: [Color(hex: 0xFF7A9C), Color(hex: 0xFF4F8B), Color(hex: 0x8E5BFF)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 20, height: 20).overlay(Image(systemName: "sparkles").font(.system(size: 10, weight: .bold)).foregroundStyle(.white))
                VStack(alignment: .leading, spacing: 0) {
                    Text("Summarized for you").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.white.opacity(0.94))
                    Text("You read \(Int(round(frame.readProgress * 50)))% · 14 min → \(Int(round(frame.summarySeconds))) s")
                        .font(.system(size: 12).monospacedDigit()).foregroundStyle(Color.white.opacity(0.7))
                }
                Spacer(minLength: 4)
            }.padding(.leading, 12).padding(.trailing, 16).frame(height: 44).background(Capsule().fill(Color.white.opacity(0.16)))
            Button(action: { motion.showArticle(source: nil) }) {
                Circle().fill(Color.white.opacity(0.16)).frame(width: 44, height: 44)
                    .overlay(Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).foregroundStyle(.white))
            }.buttonStyle(.plain)
        }.padding(.horizontal, 16).frame(width: size.width)
    }
}

private struct PinchToSummarizePoint: View {
    let point: PinchToSummarizePointCopy
    let number: Int
    let reveal: CGFloat
    let entityFlash: String?
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 10) {
                Circle().fill(PinchToSummarizePalette.iconDisc).frame(width: 24, height: 24)
                    .overlay(Image(systemName: point.icon).font(.system(size: 12, weight: .bold)).foregroundStyle(.white)).scaleEffect(0.8 + 0.2 * reveal)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 0) {
                        Text(point.title).font(.system(size: 17, weight: .semibold)).tracking(-0.4)
                        Text(PinchToSummarizeCopy.superscripts[min(number, 2)]).font(.system(size: 11, weight: .semibold)).foregroundStyle(Color.white.opacity(0.62)).baselineOffset(5)
                        if let meter = point.meter {
                            HStack(spacing: 3) { ForEach(0..<5, id: \.self) { i in Capsule().fill(Color.white.opacity(i < meter ? 0.95 : 0.24)).frame(width: 12, height: 4) } }.padding(.leading, 10)
                        }
                    }
                    Text(point.body).font(.system(size: 15)).tracking(-0.2).lineSpacing(5).foregroundStyle(Color.white.opacity(0.8)).fixedSize(horizontal: false, vertical: true)
                        .background(entityFlash.map { point.body.contains($0) ? PinchToSummarizePalette.termFlash : Color.clear } ?? Color.clear)
                }.foregroundStyle(.white)
                Spacer(minLength: 0)
            }.padding(.horizontal, 8).padding(.vertical, 4)
        }.buttonStyle(.plain).opacity(reveal)
    }
}

private struct PinchToSummarizeStatusLabels: View {
    let size: CGSize
    let safeBottom: CGFloat
    let frame: PinchToSummarizeFrame
    var body: some View {
        Group {
            if frame.thinkingAlpha > 0.001 {
                Text("Summarizing for you…").font(.system(size: 15, weight: .medium)).foregroundStyle(Color.white.opacity(0.68)).opacity(frame.thinkingAlpha)
            } else if frame.depthAlpha > 0.001 {
                HStack(spacing: 0) {
                    ForEach(Array(PinchToSummarizeCopy.levelNames.enumerated()), id: \.offset) { index, name in
                        Text(name).font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(index == frame.level ? PinchToSummarizePalette.depthInkActive : Color.white.opacity(0.72))
                            .padding(.horizontal, 12).frame(height: 26).background(Capsule().fill(index == frame.level ? Color.white.opacity(0.94) : Color.clear))
                    }
                }.padding(3).frame(height: 32).background(Capsule().fill(PinchToSummarizePalette.depthGlass).overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 0.75))).opacity(frame.depthAlpha)
            }
        }.frame(width: size.width).offset(y: size.height - safeBottom - 124).allowsHitTesting(false)
    }
}

private struct PinchToSummarizeFingers: View {
    let size: CGSize
    let frame: PinchToSummarizeFrame
    var body: some View {
        ZStack {
            ForEach([-1.0, 1.0], id: \.self) { side in
                Circle().fill(Color.white.opacity(0.34)).overlay(Circle().stroke(Color.white.opacity(0.75), lineWidth: 1)).frame(width: 44, height: 44)
                    .position(x: size.width / 2 + CGFloat(side) * 88 * frame.fingerSpread, y: size.height * 0.47)
            }
        }.frame(width: size.width, height: size.height).opacity(frame.fingerAlpha)
    }
}

// MARK: Motion

private struct PinchToSummarizeFrame {
    var pageProgress: CGFloat = 0, lift: CGFloat = 0, scrollY: CGFloat = 0, pageAlpha: CGFloat = 1, fieldAlpha: CGFloat = 0
    var stackAlpha: CGFloat = 0, summaryAlpha: CGFloat = 0, summaryLineAlpha: CGFloat = 0, thinkingAlpha: CGFloat = 0, depthAlpha: CGFloat = 0
    var mark: CGFloat = 0, markAlpha: CGFloat = 1, dim: CGFloat = 0, flight: CGFloat = 0, flightActive: CGFloat = 0
    var lightTime: CGFloat = 0, fingerAlpha: CGFloat = 0, fingerSpread: CGFloat = 1, readProgress: CGFloat = 0, summarySeconds: CGFloat = 40
    var shareLift: CGFloat = 0, flash: CGFloat = 0
    var drift: CGSize = .zero
    var level: Int = 0
    var flashIndex: Int?
    var entityFlash: String?
}

@MainActor private final class PinchToSummarizeMotion: ObservableObject {
    // TimelineView owns frame invalidation. Publishing each clock tick from inside its
    // update creates a SwiftUI feedback loop, so only direct user actions publish.
    @Published private var interactionRevision = 0
    private(set) var current = PinchToSummarizeFrame()
    private var epoch = Date(), lastFrame = Date(), gestureStart = PinchToSummarizeFrame()
    private var interacting = false, scrollStart: CGFloat?, resumeAt: Date?, resumeOffset: TimeInterval = 0
    private var manual: PinchToSummarizeManualAnimation?, flashUntil: Date?, entityUntil: Date?
    private var lastDepth = 0

    func start(autoPlay: Bool, reduced: Bool) { epoch = Date(); lastFrame = epoch; if reduced || !autoPlay { current = .init() } }
    func reduceMotionChanged(_ reduced: Bool) { interacting = false; manual = nil; resumeAt = nil; current = reduced ? .init() : current; epoch = Date(); publishInteraction() }
    func needsFrames(autoPlay: Bool, reduced: Bool) -> Bool { !reduced && autoPlay || manual != nil || flashUntil != nil || entityUntil != nil }

    func frame(at date: Date, autoPlay: Bool, reduced: Bool) -> PinchToSummarizeFrame {
        let dt = min(0.25, max(0, date.timeIntervalSince(lastFrame))); lastFrame = date
        if let animation = manual {
            let raw = CGFloat(date.timeIntervalSince(animation.started) / animation.duration)
            current = animation.from.interpolated(to: animation.to, amount: PinchToSummarizeMath.sheet(min(1, max(0, raw))))
            current.lightTime += CGFloat(dt)
            if raw >= 1 { manual = nil; current = animation.to }
        } else if autoPlay, !reduced, !interacting {
            if let resumeAt, date < resumeAt { current.lightTime += CGFloat(dt) }
            else {
                if let resumeAt, date >= resumeAt { epoch = date.addingTimeInterval(-resumeOffset); self.resumeAt = nil }
                current = autoplay(ms: CGFloat(date.timeIntervalSince(epoch).truncatingRemainder(dividingBy: PinchToSummarize.loop) * 1000))
            }
        }
        if let until = flashUntil { if until <= date { flashUntil = nil; current.flash = 0; current.flashIndex = nil } else { current.flash = CGFloat(min(1, until.timeIntervalSince(date) / 0.4)) } }
        if let until = entityUntil, until <= date { entityUntil = nil; current.entityFlash = nil }
        return current
    }

    private func autoplay(ms: CGFloat) -> PinchToSummarizeFrame {
        var f = PinchToSummarizeFrame(); f.lightTime = ms / 1000
        let read = PinchToSummarizeMath.easeInOut(PinchToSummarizeMath.span(ms, 400, 1500))
        let top = PinchToSummarizeMath.easeInOut(PinchToSummarizeMath.span(ms, 8300, 9400))
        f.scrollY = 720 * read * (1 - top); f.readProgress = 0.5 * read
        let pinchIn = PinchToSummarizeMath.sheet(PinchToSummarizeMath.span(ms, 1700, 2600))
        let collapse = PinchToSummarizeMath.critical(PinchToSummarizeMath.span(ms, 3700, 4400))
        let restore = PinchToSummarizeMath.critical(PinchToSummarizeMath.span(ms, 6800, 7400))
        let hold: CGFloat = 0.48
        if ms < 3700 { f.pageProgress = hold * pinchIn } else if ms < 6800 { f.pageProgress = PinchToSummarizeMath.mix(hold, 1, collapse) } else { f.pageProgress = 1 - restore }
        let link = PinchToSummarizeMath.smooth(PinchToSummarizeMath.span(f.pageProgress, hold, 1)); if ms >= 2600 && ms < 8300 { f.scrollY *= 1 - link }
        f.mark = 4 * min(1, f.pageProgress / 0.45); f.markAlpha = ms < 8300 ? 1 : 1 - PinchToSummarizeMath.span(ms, 8700, 9100)
        f.dim = 0.62 * min(1, f.pageProgress / 0.45) * (1 - collapse); f.fieldAlpha = min(1, f.pageProgress * 1.4); f.stackAlpha = PinchToSummarizeMath.span(f.pageProgress, 0.85, 0.93)
        f.thinkingAlpha = PinchToSummarizeMath.easeOut(PinchToSummarizeMath.span(ms, 2600, 2860)) * (1 - PinchToSummarizeMath.span(ms, 3700, 3740))
        f.depthAlpha = PinchToSummarizeMath.span(f.pageProgress, 0.12, 0.20) * (1 - f.thinkingAlpha)
        let forward = PinchToSummarizeMath.span(ms, 3700, 4400), reverse = PinchToSummarizeMath.span(ms, 6800, 7500)
        f.flight = ms < 6800 ? forward : 1 - reverse; f.flightActive = ms < 6800 ? (forward > 0 && forward < 1 ? 1 : 0) : (reverse > 0 && reverse < 1 ? 1 : 0)
        f.summaryAlpha = PinchToSummarizeMath.easeOut(PinchToSummarizeMath.span(ms, 4020, 4400)) * (1 - PinchToSummarizeMath.span(ms, 6800, 7000))
        f.summaryLineAlpha = PinchToSummarizeMath.easeOut(PinchToSummarizeMath.span(ms, 4100, 4550)) * (1 - PinchToSummarizeMath.span(ms, 6800, 6910))
        f.summarySeconds = 40 * PinchToSummarizeMath.easeOut(PinchToSummarizeMath.span(ms, 4460, 5060))
        let out = PinchToSummarizeMath.sheet(PinchToSummarizeMath.span(ms, 6800, 7650))
        let fingerIn = PinchToSummarizeMath.span(ms, 1550, 1700) * (1 - PinchToSummarizeMath.span(ms, 2600, 2670))
        let fingerOut = PinchToSummarizeMath.span(ms, 6650, 6800) * (1 - PinchToSummarizeMath.span(ms, 7650, 7720))
        f.fingerAlpha = max(fingerIn, fingerOut); f.fingerSpread = ms < 5000 ? PinchToSummarizeMath.mix(1, 0.52, pinchIn) : PinchToSummarizeMath.mix(0.52, 1.8, out)
        return f
    }

    func pinchBegan() { interacting = true; manual = nil; gestureStart = current; UIImpactFeedbackGenerator(style: .light).prepare() }
    func pinchChanged(scale: CGFloat, focal: CGPoint, velocity: CGFloat) {
        var next = gestureStart
        next.pageProgress = gestureStart.pageProgress > 0.7 ? max(0, min(1, gestureStart.pageProgress - (scale - 1) / 0.8)) : max(0, min(1, gestureStart.pageProgress + (1 - scale) / 0.5))
        next.mark = 4 * min(1, next.pageProgress / 0.45); next.dim = 0.62 * min(1, next.pageProgress / 0.45); next.fieldAlpha = min(1, next.pageProgress * 1.4)
        next.stackAlpha = PinchToSummarizeMath.span(next.pageProgress, 0.85, 0.93); next.depthAlpha = PinchToSummarizeMath.span(next.pageProgress, 0.12, 0.20)
        next.level = next.pageProgress >= 0.85 ? 2 : next.pageProgress >= 0.62 ? 1 : 0
        next.drift = CGSize(width: (focal.x - UIScreen.main.bounds.midX) * 0.6 * next.pageProgress, height: (focal.y - UIScreen.main.bounds.midY) * 0.6 * next.pageProgress)
        if next.level != lastDepth { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.65); lastDepth = next.level }
        current = next
        publishInteraction()
    }
    func pinchEnded(velocity: CGFloat) { let commit = current.pageProgress - velocity * 0.06 >= 0.32; if commit { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.8) }; transition(toSummary: commit) }
    func summarizeButton() { interacting = true; gestureStart = current; UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.75); transition(toSummary: true) }
    func showArticle(source: Int?) { interacting = true; if let source { current.flashIndex = source; current.flash = 1; flashUntil = Date().addingTimeInterval(0.6) }; transition(toSummary: false) }
    private func transition(toSummary: Bool) {
        var target = PinchToSummarizeFrame()
        if toSummary { target.pageProgress = 1; target.fieldAlpha = 1; target.stackAlpha = 1; target.summaryAlpha = 1; target.summaryLineAlpha = 1; target.mark = 4; target.flight = 1; target.readProgress = max(0.5, current.readProgress); target.summarySeconds = 40 }
        else if current.flashIndex != nil { target.scrollY = 720; target.mark = 4; target.flashIndex = current.flashIndex; target.flash = current.flash }
        manual = .init(from: current, to: target, started: Date(), duration: toSummary ? 0.7 : 0.6); resumeOffset = toSummary ? 6.4 : 0; resumeAt = Date().addingTimeInterval(2); interacting = false; publishInteraction()
    }
    func scrollChanged(translation: CGFloat) { if scrollStart == nil { scrollStart = current.scrollY; interacting = true; manual = nil }; current.scrollY = max(0, min(1850, (scrollStart ?? 0) - translation)); current.readProgress = min(1, current.scrollY / 1440); publishInteraction() }
    func scrollEnded(translation: CGFloat) { scrollStart = nil; resumeOffset = 0; resumeAt = Date().addingTimeInterval(2); interacting = false }
    func sharePressed(_ pressed: Bool) { current.shareLift = pressed ? 1 : 0; if pressed { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.65) }; resumeOffset = 6.4; resumeAt = Date().addingTimeInterval(2); publishInteraction() }
    func flashEntity(_ entity: String) { current.entityFlash = entity == "2-hour comfort" ? "2 hours" : entity; entityUntil = Date().addingTimeInterval(0.6); UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.55); resumeOffset = 6.4; resumeAt = Date().addingTimeInterval(2); publishInteraction() }
    private func publishInteraction() { interactionRevision &+= 1 }
}

private struct PinchToSummarizeManualAnimation { let from: PinchToSummarizeFrame, to: PinchToSummarizeFrame, started: Date, duration: TimeInterval }
private extension PinchToSummarizeFrame {
    func interpolated(to b: Self, amount t: CGFloat) -> Self {
        var v = self
        v.pageProgress = PinchToSummarizeMath.mix(pageProgress, b.pageProgress, t); v.lift = PinchToSummarizeMath.mix(lift, b.lift, t); v.scrollY = PinchToSummarizeMath.mix(scrollY, b.scrollY, t)
        v.pageAlpha = PinchToSummarizeMath.mix(pageAlpha, b.pageAlpha, t); v.fieldAlpha = PinchToSummarizeMath.mix(fieldAlpha, b.fieldAlpha, t); v.stackAlpha = PinchToSummarizeMath.mix(stackAlpha, b.stackAlpha, t)
        v.summaryAlpha = PinchToSummarizeMath.mix(summaryAlpha, b.summaryAlpha, t); v.summaryLineAlpha = PinchToSummarizeMath.mix(summaryLineAlpha, b.summaryLineAlpha, t); v.thinkingAlpha = PinchToSummarizeMath.mix(thinkingAlpha, b.thinkingAlpha, t)
        v.depthAlpha = PinchToSummarizeMath.mix(depthAlpha, b.depthAlpha, t); v.mark = PinchToSummarizeMath.mix(mark, b.mark, t); v.markAlpha = PinchToSummarizeMath.mix(markAlpha, b.markAlpha, t); v.dim = PinchToSummarizeMath.mix(dim, b.dim, t)
        v.flight = PinchToSummarizeMath.mix(flight, b.flight, t); v.readProgress = PinchToSummarizeMath.mix(readProgress, b.readProgress, t); v.summarySeconds = PinchToSummarizeMath.mix(summarySeconds, b.summarySeconds, t); v.shareLift = PinchToSummarizeMath.mix(shareLift, b.shareLift, t)
        v.drift = CGSize(width: PinchToSummarizeMath.mix(drift.width, b.drift.width, t), height: PinchToSummarizeMath.mix(drift.height, b.drift.height, t)); v.level = t < 0.5 ? level : b.level; return v
    }
}

private struct PinchToSummarizePinchSurface: UIViewRepresentable {
    @ObservedObject var motion: PinchToSummarizeMotion
    func makeCoordinator() -> Coordinator { Coordinator(motion: motion) }
    func makeUIView(context: Context) -> UIView { let view = UIView(); let pinch = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.changed(_:))); pinch.cancelsTouchesInView = false; pinch.delegate = context.coordinator; view.addGestureRecognizer(pinch); return view }
    func updateUIView(_ uiView: UIView, context: Context) { context.coordinator.motion = motion }
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var motion: PinchToSummarizeMotion
        init(motion: PinchToSummarizeMotion) { self.motion = motion }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
        @objc func changed(_ recognizer: UIPinchGestureRecognizer) {
            guard let view = recognizer.view else { return }
            switch recognizer.state { case .began: motion.pinchBegan(); case .changed: motion.pinchChanged(scale: recognizer.scale, focal: recognizer.location(in: view), velocity: recognizer.velocity); case .ended, .cancelled, .failed: motion.pinchEnded(velocity: recognizer.velocity); default: break }
        }
    }
}

// MARK: Copy and constants

private enum PinchToSummarizeLevel: Int { case keyPoints, brief, tldr }
private struct PinchToSummarizeParagraphCopy { let leadIn: String?, key: String?, rest: String }
private struct PinchToSummarizePointCopy { let icon: String, title: String, body: String; let source: Int, meter: Int? }
private struct PinchToSummarizeLevelCopy { let lead: String, tldr: Bool; let points: [Int] }

private enum PinchToSummarizeCopy {
    static let paragraphs: [PinchToSummarizeParagraphCopy] = [
        .init(leadIn: "THE HALO ONE", key: " is a computer you wear: a private cinema and an office in one visor, and the closest anyone has come to that old promise.", rest: " I spent a week working, watching and travelling with it, and mostly forgot it was there."),
        .init(leadIn: nil, key: "It has the best screen I have worn: text is as sharp as on a good laptop, and a film in a dark room beats my television.", rest: " I never reached for a controller, because my hands were enough."),
        .init(leadIn: nil, key: "The split strap spreads the weight, but by the second hour I wanted a break.", rest: " The battery pack in a pocket is a cable you never quite forget."),
        .init(leadIn: nil, key: "It starts at $1,899, and the travel case you will want is extra.", rest: " That is laptop money for something most people will not wear all day yet."),
        .init(leadIn: nil, key: nil, rest: "Apps are the open question. The ones made for the visor are lovely; the rest float in flat windows, and you notice the seams within a day."),
        .init(leadIn: nil, key: nil, rest: "Calls are the surprise. People see a soft rendering of my face, which is uncanny for a minute and then simply useful, like a good webcam."),
        .init(leadIn: nil, key: nil, rest: "Travel is where it earns its keep. On a five-hour flight the cabin disappeared, and the battery lasted one film and most of a second."),
        .init(leadIn: nil, key: nil, rest: "Setup takes ten minutes, most of it fitting the light seal. Get that wrong and the edges glow; get it right and the room falls away."),
        .init(leadIn: nil, key: nil, rest: "Battery life is the honest weak spot: a little over two hours of mixed use, and the pack grows warm in a pocket by the end of a film."),
        .init(leadIn: nil, key: nil, rest: "Sound is better than it has any right to be. The speakers sit just off the ears, and in a quiet room nobody beside you hears much at all."),
        .init(leadIn: nil, key: nil, rest: "Typing works with any keyboard, and the visor floats a larger screen above it; for writing I preferred that to anything else on the trip."),
        .init(leadIn: nil, key: nil, rest: "Friends asked the same thing every time: does it feel lonely? Less than I feared, though handing it over to a guest takes a fiddly minute."),
        .init(leadIn: nil, key: nil, rest: "Would I buy one? Not this year. But I have stopped thinking of headsets as gadgets, and that is the real change.")]
    static let keyParagraphIndices = [0, 1, 2, 3]
    static let points = [
        PinchToSummarizePointCopy(icon: "star.fill", title: "Review sentiment", body: "The best screen Mara has worn: laptop-sharp text and films that beat her TV.", source: 1, meter: 4),
        PinchToSummarizePointCopy(icon: "figure.stand", title: "Comfort and fit", body: "The split strap spreads the weight, but she wanted a break after 2 hours.", source: 2, meter: nil),
        PinchToSummarizePointCopy(icon: "tag.fill", title: "Price", body: "Starts at $1,899; the travel case is extra.", source: 3, meter: nil)]
    static let levels = [
        PinchToSummarizeLevelCopy(lead: "The Halo One is a computer you wear: a private cinema and an office in one visor, the closest yet to that old promise.", tldr: false, points: [0, 1, 2]),
        PinchToSummarizeLevelCopy(lead: "The Halo One is a $1,899 computer you wear: a private cinema and an office in one visor, the closest yet to its promise.", tldr: false, points: [1]),
        PinchToSummarizeLevelCopy(lead: "The Halo One: the best wearable screen yet, at $1,899, though you will want a break after 2 hours.", tldr: true, points: [])]
    static let levelNames = ["Key points", "Brief", "TL;DR"], superscripts = ["¹", "²", "³"]
    static func entities(for level: PinchToSummarizeLevel) -> [String] { level == .tldr ? [] : ["Halo One", "$1,899", "2-hour comfort"] }
}

private enum PinchToSummarizePalette {
    static let ink = Color(hex: 0x111216), inkSoft = Color(hex: 0x5B5E68), rule = Color(hex: 0xE7E7EC), panel = Color(hex: 0x8E90A8)
    static let toolbar = Color(hex: 0xF1F1F4), toolbarInk = Color(hex: 0x2A2C33), accent = Color(hex: 0x3D63F2), stack = Color(hex: 0xD8D9F4)
    static let pageShadow = Color(hex: 0x120A40), marker = Color(hex: 0xDCD4FF), chipPaper = Color.white.opacity(0.94), chipPill = Color(hex: 0x4A35D8).opacity(0.7)
    static let iconDisc = Color(hex: 0x3D63F2), termFlash = Color(hex: 0xFFE296).opacity(0.42), depthGlass = Color(hex: 0x161040).opacity(0.62)
    static let depthInkActive = Color(hex: 0x17132A), shareShadow = Color(hex: 0x0B0630)
}

private enum PinchToSummarizeAssets {
    static let hero = load("PinchToSummarize-hero.jpg")
    static let field = load("PinchToSummarize-field.png")
    static let fieldB = load("PinchToSummarize-field-b.png")
    private static func load(_ name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Pinch to Summarize asset: \(name)")
            return UIImage()
        }
        return image.preparingForDisplay() ?? image
    }
}

private enum PinchToSummarizeMath {
    static func mix(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat { a + (b - a) * t }
    static func span(_ x: CGFloat, _ a: CGFloat, _ b: CGFloat) -> CGFloat { max(0, min(1, (x - a) / max(0.0001, b - a))) }
    static func smooth(_ t: CGFloat) -> CGFloat { t * t * (3 - 2 * t) }
    static func easeOut(_ t: CGFloat) -> CGFloat { 1 - pow(1 - t, 3) }
    static func easeInOut(_ t: CGFloat) -> CGFloat { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    static func sheet(_ t: CGFloat) -> CGFloat { let u = 1 - t; return 3 * u * t * t + t * t * t }
    static func critical(_ t: CGFloat) -> CGFloat { let x = max(0, min(1, t)); return 1 - (1 + 7 * x) * exp(-7 * x) }
}

private extension Color {
    init(hex: UInt32) { self.init(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1) }
}
