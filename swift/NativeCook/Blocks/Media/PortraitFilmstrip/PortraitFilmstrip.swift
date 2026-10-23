import SwiftUI
import UIKit

struct PortraitFilmstrip: View {
    static let loop: Double = 8
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var touched: Date? = nil
    @State private var isTouching = false
    @State private var manual: Double = 0
    @State private var dragBase: Double? = nil
    @State private var releaseVelocity = 0.0
    @State private var tapDuration = 0.0
    private let photos = ["portrait", "mountain", "forest"]
    private let titles = ["Quiet mornings", "Beyond the ridge", "A softer world"]

    private func smooth(_ a: Double, _ b: Double, _ t: Double) -> Double {
        let x = min(1, max(0, (t - a) / (b - a)))
        return x * x * x * (10 + x * (-15 + 6 * x))
    }
    private func time(_ date: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reduceMotion { return 2.5 }
        if let touched {
            let d = date.timeIntervalSince(touched)
            if isTouching { return manual }
            if d < 2.7 { return min(7.6, max(0, manual + min(tapDuration, d) + releaseVelocity * 0.16 * (1 - exp(-d / 0.16)))) }
            return autoPlay ? (d - 2.7).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? max(0, date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0
    }
    private func input(_ value: Double) {
        manual = min(7.6, max(0, value)); touched = Date()
    }
    private func returnWeight(_ date: Date) -> Double {
        guard previewTime == nil, !reduceMotion, !isTouching, let touched else { return 1 }
        let d = date.timeIntervalSince(touched)
        return d < 2.7 ? 1 - smooth(2, 2.7, d) : 1
    }
    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused: previewTime != nil || reduceMotion || (!autoPlay && touched == nil))) { tick in
                let t = time(tick.date)
                let weight = returnWeight(tick.date)
                let zoom = smooth(1.05, 1.85, t) * (1 - smooth(2.8, 3.55, t)) * weight
                let zoomNext = smooth(4.85, 5.45, t) * (1 - smooth(6.05, 6.65, t)) * weight
                let scroll = smooth(3.75, 4.55, t) * (1 - smooth(6.85, 7.6, t)) * weight
                let s = geo.size.width / 393
                let h = geo.size.height / s
                ZStack(alignment: .topLeading) {
                    Color(red: 0.063, green: 0.063, blue: 0.059)
                    HStack {
                        Text("FIELD NOTES").font(.system(size: 12, weight: .medium)).tracking(2.2)
                        Spacer()
                        ZStack {
                            Text("01 / 03").opacity(1 - smooth(0.3, 0.7, scroll))
                            Text("02 / 03").opacity(smooth(0.3, 0.7, scroll))
                        }.font(.system(size: 11, weight: .medium, design: .monospaced))
                    }.foregroundStyle(Color.white.opacity(0.64)).padding(.horizontal, 25).offset(y: 143).zIndex(22)
                        .opacity(1 - max(zoom, zoomNext))
                    ForEach(-1...2, id: \.self) { index in
                        let n = (index + 3) % 3
                        let z = index == 0 ? zoom : index == 1 ? zoomNext : 0
                        let width = 271 + 122 * z
                        let height = 530 + (h - 530) * z
                        let baseY = 160 + Double(index) * 550 - 550 * scroll
                        let y = baseY * (1 - z)
                        let bend = sin(.pi * z)
                        let closing = index == 0 ? t > 2.8 : t > 6.05
                        let yaw = 5.5 * bend * (closing ? -1.0 : 1.0) - 3 * sin(.pi * scroll) * (1 - z)
                        let labelOpacity = index == 0 ? 1 - smooth(0.15, 0.75, scroll) : index == 1 ? smooth(0.25, 0.8, scroll) : 0
                        ZStack(alignment: .bottomLeading) {
                            Image(uiImage: UIImage(named: "PortraitFilmstrip-" + photos[n] + ".jpg") ?? UIImage()).resizable().scaledToFill()
                                .frame(width: width, height: height).scaleEffect(1 + 36 * bend / width).offset(x: -9 * bend).clipped()
                            LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .center, endPoint: .bottom)
                            VStack(alignment: .leading, spacing: 5) {
                                Text("JOURNAL / 0\(n + 1)").font(.system(size: 9, weight: .medium)).tracking(1.9).opacity(0.7)
                                Text(titles[n]).font(.system(size: 18, weight: .semibold))
                            }.foregroundStyle(.white).padding(.leading, 20).padding(.bottom, 27).opacity(labelOpacity)
                            Image(systemName: "arrow.up.right").font(.system(size: 12, weight: .medium))
                                .foregroundStyle(.black).frame(width: 28, height: 28).background(.white, in: Circle())
                                .frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, 18).padding(.bottom, 28).opacity(labelOpacity)
                        }.frame(width: width, height: height, alignment: .topLeading).clipShape(RoundedRectangle(cornerRadius: 30 * (1 - z)))
                            .overlay(RoundedRectangle(cornerRadius: 30 * (1 - z)).stroke(.white.opacity(0.12), lineWidth: 0.7))
                            .shadow(color: .black.opacity(0.16 * bend), radius: 2, y: 2)
                            .shadow(color: .black.opacity(0.28 * bend), radius: 18, y: 14)
                            .rotation3DEffect(.degrees(yaw), axis: (x: 0, y: 1, z: 0), perspective: 0.45)
                            .rotation3DEffect(.degrees(-2 * bend), axis: (x: 1, y: 0, z: 0), perspective: 0.45)
                            .offset(x: (393 - width) / 2, y: y)
                            .zIndex(Double(index + 2) + 20 * z)
                    }
                    Text("DRAG TO EXPLORE").font(.system(size: 9, weight: .medium)).tracking(2)
                        .foregroundStyle(.white.opacity(0.35)).frame(width: 393).offset(y: h - 58)
                        .opacity(1 - max(zoom, zoomNext))
                }.frame(width: 393, height: h, alignment: .topLeading)
                    .scaleEffect(s, anchor: .topLeading).frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading).clipped()
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 8).onChanged { v in
                        if dragBase == nil { dragBase = time(Date()); releaseVelocity = 0; tapDuration = 0 }
                        isTouching = true; input((dragBase ?? 0) - v.translation.height / (135 * s))
                    }.onEnded { v in
                        releaseVelocity = min(3, max(-3, -(v.predictedEndTranslation.height - v.translation.height) / (135 * s * 0.25)))
                        dragBase = nil; isTouching = false; touched = Date()
                    })
                    .onTapGesture { releaseVelocity = 0; tapDuration = scroll > 0.5 ? 0.6 : 0.8; input(scroll > 0.5 ? 4.85 : 1.05); UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Field Notes photo journal. Drag vertically to explore the filmstrip, or tap to expand a photo.")
            }
        }.ignoresSafeArea().preferredColorScheme(.dark)
            .task(id: touched) {
                guard !autoPlay, previewTime == nil, let completedTouch = touched else { return }
                try? await Task.sleep(for: .seconds(2.7))
                guard !Task.isCancelled, !isTouching, touched == completedTouch else { return }
                touched=nil;tapDuration=0;releaseVelocity=0
            }
    }
}
