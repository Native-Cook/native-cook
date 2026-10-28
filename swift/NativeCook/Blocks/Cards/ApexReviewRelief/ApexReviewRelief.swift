import SwiftUI
import UIKit

struct ApexReviewRelief: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var manual: Double? = nil
    @State private var dragStart = 0.0
    @GestureState private var gestureActive = false
    @State private var touching = false
    @State private var release = Date.distantPast
    @State private var rating = 4
    private let cream = Color(hex: 0xFFF1EA)
    private let gold = Color(hex: 0xFFC66D)

    private func smooth(_ x: Double) -> Double {
        let v = min(1, max(0, x)); return v * v * (3 - 2 * v)
    }
    private func pose(_ date: Date) -> Double {
        if reduced { return 0.7 }
        if let manual {
            return touching ? manual : manual * (1 - smooth((date.timeIntervalSince(release) - 2) / 0.45))
        }
        let t = previewTime ?? (autoPlay ? max(0, date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0)
        return smooth((t - 1.4) / 1.6) * (1 - smooth((t - 4.8) / 1.6))
    }
    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width / 393, geo.size.height / 760)
            TimelineView(.animation(paused: scenePhase != .active || reduced || previewTime != nil || (!autoPlay && manual == nil))) { clock in
                let p = pose(clock.date)
                VStack(spacing: 0) {
                    HStack {
                        Image(systemName: "flag.checkered").font(.system(size: 15))
                        Text("APEX / RACE JOURNAL").font(.system(size: 11, weight: .semibold)).tracking(2)
                        Spacer()
                        Text("08").font(.system(size: 13, design: .monospaced))
                    }.padding(.horizontal, 30).frame(height: 44)
                    Text("Ember Coast").font(.system(size: 34, weight: .bold, design: .rounded)).padding(.top, 18)
                    Text("ROUND 08  ·  SUNDAY SESSION").font(.system(size: 11, weight: .medium)).tracking(1.7).opacity(0.65).padding(.top, 9)
                    relief(p).frame(width: 333, height: 315)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 1).updating($gestureActive) { _, active, _ in active = true }.onChanged { value in
                            if !touching {
                                dragStart = pose(.now); touching = true
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            }
                            manual = min(1, max(0, dragStart + value.translation.width / 150))
                        }.onEnded { _ in touching = false; release = .now })
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Ember Coast circuit relief")
                        .accessibilityHint("Drag right to lift the circuit")
                        .accessibilityAdjustableAction { direction in
                            manual = direction == .increment ? 1 : 0; release = .now
                        }
                    HStack(spacing: 15) {
                        Image(systemName: "laurel.leading").foregroundStyle(gold)
                        Text("4.8").font(.system(size: 48, weight: .semibold, design: .rounded))
                        Image(systemName: "laurel.trailing").foregroundStyle(gold)
                    }.font(.system(size: 30))
                    Text("A weekend worth remembering.").font(.system(size: 14)).opacity(0.65).padding(.top, 5)
                    HStack {
                        metric("129", "RATINGS")
                        Rectangle().fill(cream.opacity(0.12)).frame(width: 1, height: 32)
                        metric("2.4k", "REVIEWS")
                        Rectangle().fill(cream.opacity(0.12)).frame(width: 1, height: 32)
                        metric("08", "ROUND")
                    }.padding(.horizontal, 25).padding(.top, 28)
                    HStack(spacing: 11) {
                        ForEach(1...5, id: \.self) { star in
                            Button {
                                let current = pose(.now)
                                rating = star; manual = current; release = .now
                                UISelectionFeedbackGenerator().selectionChanged()
                            } label: {
                                Image(systemName: "star.fill").font(.system(size: 26))
                                    .foregroundStyle(star <= rating ? gold : cream.opacity(0.16))
                                    .frame(width: 44, height: 54)
                            }.buttonStyle(.plain)
                                .accessibilityLabel("Rate \(star) stars")
                                .accessibilityAddTraits(star == rating ? .isSelected : [])
                        }
                    }.padding(.horizontal, 22).padding(.vertical, 6)
                        .background(.black.opacity(0.22), in: Capsule())
                        .overlay(Capsule().stroke(cream.opacity(0.12), lineWidth: 1))
                        .padding(.top, 28)
                    Text("YOUR RATING  /  \(rating) OF 5").font(.system(size: 10, weight: .medium)).tracking(2).opacity(0.5).padding(.top, 12)
                    Spacer(minLength: 15)
                    Text("Drag the circuit. Relive the race.").font(.system(size: 12)).opacity(0.5).padding(.bottom, 15)
                }.foregroundStyle(cream).frame(width: 393, height: 760)
                    .scaleEffect(scale).frame(width: geo.size.width, height: geo.size.height)
            }
        }
        .background(LinearGradient(stops: [.init(color: Color(hex: 0xB91529), location: 0), .init(color: Color(hex: 0x390911), location: 0.45), .init(color: Color(hex: 0x100B10), location: 0.8)], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: release) {
            guard manual != nil, !touching else { return }
            do { try await Task.sleep(for: .seconds(2.45)) } catch { return }
            guard !touching else { return }
            manual = nil; epoch = .now
        }
        .onChange(of: gestureActive) { _, active in
            if !active && touching { touching = false; release = .now }
        }
        .onChange(of: scenePhase) { _, phase in if phase == .active { epoch = .now; manual = nil; touching = false } }
        .onChange(of: reduced) { _, _ in manual = nil; touching = false; epoch = .now }
    }
    private func metric(_ value: String, _ label: String) -> some View {
        VStack(spacing: 5) {
            Text(value).font(.system(size: 24, weight: .medium, design: .rounded))
            Text(label).font(.system(size: 9, weight: .medium)).tracking(1.6).opacity(0.5)
        }.frame(maxWidth: .infinity)
    }
    private func relief(_ p: Double) -> some View {
        ZStack {
            Circle().fill(Color(hex: 0xFF4C50).opacity(0.25)).frame(width: 220, height: 220).blur(radius: 40)
            ForEach(0..<3, id: \.self) { layer in
                let depth = Double(2 - layer)
                RoundedRectangle(cornerRadius: 44)
                    .fill(LinearGradient(colors: [Color(hex: 0xC52739), Color(hex: 0x651421)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .overlay(RoundedRectangle(cornerRadius: 44).stroke(cream.opacity(layer == 2 ? 0.18 : 0.07), lineWidth: 1))
                    .shadow(color: .black.opacity(0.25), radius: 3, y: 2)
                    .shadow(color: .black.opacity(0.25), radius: 28, y: 22)
                    .frame(width: 225, height: 225)
                    .offset(x: depth * (8 + p * 9), y: depth * (11 + p * 8))
                    .rotationEffect(.degrees(-12 + p * 7))
            }
            VStack(spacing: 12) {
                ZStack {
                    ForEach(0..<9, id: \.self) { step in
                        ApexReviewReliefCircuit()
                            .stroke(step == 8 ? Color(hex: 0xF04E59) : Color(hex: 0x6A1023), style: StrokeStyle(lineWidth: 17, lineCap: .round, lineJoin: .round))
                            .offset(y: -Double(step) * (1.1 + p * 1.2))
                    }
                    ApexReviewReliefCircuit().stroke(cream.opacity(0.25), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)).offset(y: -8.8 - p * 9.6)
                }.frame(width: 145, height: 100).shadow(color: .black.opacity(0.35), radius: 7, y: 10 + p * 12)
                Text("EMBER GP").font(.system(size: 22, weight: .black, design: .rounded)).foregroundStyle(Color(hex: 0xF2A1A6))
                Text("CIRCUIT NO. 08").font(.system(size: 9, weight: .bold)).tracking(2).foregroundStyle(Color(hex: 0xE58A94))
            }.rotationEffect(.degrees(-12 + p * 7)).offset(x: -5, y: -6 - p * 9)
            HStack(spacing: 3) {
                ForEach(0..<5, id: \.self) { _ in Image(systemName: "star.fill").font(.system(size: 12)) }
            }.foregroundStyle(gold).padding(12)
                .background(LinearGradient(colors: [Color(hex: 0xD9394C), Color(hex: 0x670F23)], startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(cream.opacity(0.18)))
                .rotationEffect(.degrees(-16)).scaleEffect(1 + p * 0.06)
                .offset(x: 96, y: -83 - p * 16)
                .shadow(color: .black.opacity(0.3), radius: 10, y: 8)
        }
        .rotation3DEffect(.degrees(p * 14), axis: (x: 1, y: 0, z: 0), perspective: 0.45)
        .rotation3DEffect(.degrees(p * -18), axis: (x: 0, y: 1, z: 0), perspective: 0.45)
    }
}

private struct ApexReviewReliefCircuit: Shape {
    func path(in rect: CGRect) -> Path {
        let points: [CGPoint] = [.init(x: 0.05, y: 0.82), .init(x: 0.28, y: 0.12), .init(x: 0.48, y: 0.12), .init(x: 0.45, y: 0.43), .init(x: 0.70, y: 0.31), .init(x: 0.77, y: 0.08), .init(x: 0.93, y: 0.24), .init(x: 0.84, y: 0.62), .init(x: 0.56, y: 0.68), .init(x: 0.39, y: 0.9), .init(x: 0.05, y: 0.82)]
        return Path { path in
            for (i, point) in points.enumerated() {
                let p = CGPoint(x: point.x * rect.width, y: point.y * rect.height)
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            path.closeSubpath()
        }
    }
}
