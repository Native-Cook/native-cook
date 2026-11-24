import SwiftUI
import UIKit

/// Tally, a tasks app: a round + button morphs into a compact composer card (width and height on two
/// staggered springs), and Add collapses the card into a pill that flies into the list as a new row
/// while the rows part. In `autoPlay` every frame is a pure function of the 9 s loop time; a finger
/// takes over at once and the loop resumes 2 s after the last touch.
struct QuickAddMorph: View {
    var autoPlay = true

    static let loop: Double = QuickAddMorphMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: QuickAddMorphModel
    @State private var awake = false
    @FocusState private var focused: Bool

    private typealias M = QuickAddMorphMotion

    init(autoPlay: Bool = true, tasks: [QuickAddMorphTask] = QuickAddMorphTask.all,
         lists: [String] = QuickAddMorphTask.lists) {
        self.autoPlay = autoPlay
        _model = State(initialValue: QuickAddMorphModel(tasks: tasks, lists: lists))
    }

    private var playing: Bool { autoPlay && !reducedMotion }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { proxy in
                let g = QuickAddMorphGeometry(width: proxy.size.width, height: proxy.size.height,
                                              top: insets.top, bottomInset: insets.bottom)
                TimelineView(.animation(paused: scenePhase != .active || !(playing || awake))) { timeline in
                    let f = frame(timeline.date, g)
                    screen(f, g)
                }
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea(.keyboard)
        .background(M.paper.color.ignoresSafeArea())
        .preferredColorScheme(.light)
        .background(QuickAddMorphTouchSpy { count in
            model.touch(fingers: count)
            awake = true
        })
        .onChange(of: focused) { _, value in
            model.focused = value
            awake = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { note in
            let frame = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect) ?? .zero
            let duration = (note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            model.keyboard(height: frame.height, duration: duration)
            awake = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { note in
            let duration = (note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            model.keyboard(height: 0, duration: duration)
            awake = true
        }
        .onChange(of: model.isOpen) { _, open in
            if !open { focused = false }
        }
        .task(id: awake) {
            // Without the loop the timeline only runs while something moves.
            guard awake else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                if !playing, model.resting(at: QuickAddMorphAnim.now) { awake = false }
            }
        }
    }

    private func frame(_ date: Date, _ g: QuickAddMorphGeometry) -> QuickAddMorphFrame {
        model.geometry = g
        model.reducedMotion = reducedMotion
        return model.frame(at: date.timeIntervalSinceReferenceDate, playing: playing)
    }

    // MARK: Screen

    private func screen(_ f: QuickAddMorphFrame, _ g: QuickAddMorphGeometry) -> some View {
        ZStack(alignment: .topLeading) {
            header(g)
            list(f, g)
            LinearGradient(
                stops: [
                    .init(color: M.paper.color.opacity(0), location: 0),
                    .init(color: M.paper.color.opacity(0.82), location: 0.55),
                    .init(color: M.paper.color, location: 1),
                ],
                startPoint: .top, endPoint: .bottom)
                .frame(width: g.width, height: g.bottom + M.fab + 44)
                .offset(y: g.height - (g.bottom + M.fab + 44))
                .allowsHitTesting(false)
            QuickAddMorphComposer(model: model, f: f, g: g, focus: $focused)
            if f.auto, playing {
                ghost(f)
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
    }

    private func header(_ g: QuickAddMorphGeometry) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Tally")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(M.accent.color)
                Spacer(minLength: 0)
                Text("\(model.doneCount) of \(model.tasks.count) done")
                    .font(.system(size: 14, weight: .medium).monospacedDigit())
                    .foregroundStyle(M.mute.color)
            }
            Text("Today")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(M.ink.color)
                .frame(height: 40)
                .padding(.top, 2)
                .accessibilityAddTraits(.isHeader)
            Text("Tuesday, November 24")
                .font(.system(size: 15))
                .foregroundStyle(M.mute.color)
                .padding(.top, 1)
        }
        .frame(width: g.width - (M.side + 4) * 2, alignment: .leading)
        .offset(x: M.side + 4, y: g.top + 8)
    }

    private func list(_ f: QuickAddMorphFrame, _ g: QuickAddMorphGeometry) -> some View {
        let total = model.tasks.count
        return ScrollViewReader { reader in
            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    Color.clear.frame(height: 0).id("top")
                    ForEach(Array(model.tasks.enumerated()), id: \.element.id) { index, task in
                        let slot = model.slot(of: index)
                        let parted = slot >= M.insert ? f.part * M.row : 0
                        let y = (index < f.rowY.count ? f.rowY[index] : Double(M.insert) * M.row) + parted
                        QuickAddMorphRow(task: task, done: f.done[task.id] ?? 0, checked: model.checked.contains(task.id),
                                         width: g.rowWidth, interactive: true) { model.toggleRow(task.id) }
                            .opacity(f.landId == index ? f.landed : 1)
                            .offset(x: M.side, y: y)
                    }
                    QuickAddMorphRow(task: QuickAddMorphTask.script, done: f.scriptDone, checked: false,
                                     width: g.rowWidth, interactive: false) {}
                        .opacity(f.landed * (f.landId < 0 ? 1 : 0))
                        .offset(x: M.side, y: Double(M.insert) * M.row)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
                .frame(width: g.width, height: Double(total + 1) * M.row + g.bottom + M.fab + 24, alignment: .topLeading)
                .background(
                    GeometryReader { p in
                        Color.clear.preference(key: QuickAddMorphScrollKey.self,
                                               value: -p.frame(in: .named("quick-add-list")).minY)
                    }
                )
            }
            .coordinateSpace(name: "quick-add-list")
            .scrollDismissesKeyboard(.never)
            .onPreferenceChange(QuickAddMorphScrollKey.self) { value in
                model.scrollY = value
            }
            .onChange(of: model.scrollRequest) { _, _ in
                withAnimation(.easeInOut(duration: 0.3)) { reader.scrollTo("top", anchor: .top) }
            }
        }
        .frame(width: g.width, height: max(0, g.height - g.listTop))
        .offset(y: g.listTop)
    }

    private func ghost(_ f: QuickAddMorphFrame) -> some View {
        Circle()
            .fill(Color(.sRGB, red: 120 / 255, green: 114 / 255, blue: 104 / 255, opacity: 0.34))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.72), lineWidth: 1))
            .frame(width: M.ghost, height: M.ghost)
            .scaleEffect(1 - 0.16 * f.ghostPress)
            .opacity(f.ghostOpacity * 0.9)
            .offset(x: f.ghost.x - M.ghost / 2, y: f.ghost.y - M.ghost / 2)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

private struct QuickAddMorphScrollKey: PreferenceKey {
    static let defaultValue = 0.0
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

/// One task: a check ring that fills and draws its tick, the title struck through when done.
struct QuickAddMorphRow: View {
    let task: QuickAddMorphTask
    let done: Double
    let checked: Bool
    let width: Double
    let interactive: Bool
    let onToggle: () -> Void

    private typealias M = QuickAddMorphMotion

    var body: some View {
        let d = M.clamp01(done)
        let shape = RoundedRectangle(cornerRadius: M.radiusRow, style: .continuous)
        HStack(spacing: 0) {
            Button(action: onToggle) { check(d) }
                .buttonStyle(QuickAddMorphPlainButton())
                .disabled(!interactive)
                .accessibilityLabel(task.title)
                .accessibilityValue(checked ? "checked" : "unchecked")
                .accessibilityAddTraits(.isToggle)
            VStack(alignment: .leading, spacing: 1) {
                Text(task.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(M.ink.color)
                    .lineLimit(1)
                    .opacity(1 - 0.5 * d)
                    .overlay {
                        Rectangle().fill(M.mute.color)
                            .frame(height: 1.5)
                            .scaleEffect(x: d, y: 1, anchor: .leading)
                            .opacity(d)
                    }
                Text(task.meta)
                    .font(.system(size: 13))
                    .foregroundStyle(M.mute.color)
                    .lineLimit(1)
            }
            .padding(.leading, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            if task.priority {
                Circle().fill(M.amber.color).frame(width: 8, height: 8)
                    .padding(.leading, 10)
                    .accessibilityLabel("Priority")
            }
        }
        .padding(.leading, M.checkLeft)
        .padding(.trailing, 16)
        .frame(width: width, height: M.rowHeight)
        .background(
            ZStack {
                shape.fill(M.card.color)
                    .shadow(color: Color(.sRGB, red: 58 / 255, green: 44 / 255, blue: 28 / 255, opacity: 0.05), radius: 7, y: 6)
                shape.fill(M.card.color)
                    .shadow(color: Color(.sRGB, red: 58 / 255, green: 44 / 255, blue: 28 / 255, opacity: 0.07), radius: 1, y: 1)
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.8), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.1)),
                                   lineWidth: 1)
            }
        )
    }

    private func check(_ d: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Circle().strokeBorder(Color(.sRGB, red: 33 / 255, green: 30 / 255, blue: 26 / 255, opacity: 0.22), lineWidth: 1.6)
            Circle().fill(M.accent.color)
                .scaleEffect(0.7 + 0.3 * d)
                .opacity(d)
            // The tick sits 1.6 pt in, as in RN, where the SVG fills the ring's padding box.
            QuickAddMorphTick()
                .trim(from: 0, to: max(0, (QuickAddMorphTick.length - 20 * (1 - d)) / QuickAddMorphTick.length))
                .stroke(M.card.color, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                .frame(width: M.checkSize, height: M.checkSize)
                .offset(x: 1.6, y: 1.6)
        }
        .frame(width: M.checkSize, height: M.checkSize, alignment: .topLeading)
        .contentShape(Rectangle().inset(by: -10))
    }
}

struct QuickAddMorphTick: Shape {
    static let length = hypot(3.9, 3.8) + hypot(7.2, 8.2)

    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 7.5, y: 13.6))
        p.addLine(to: CGPoint(x: 11.4, y: 17.4))
        p.addLine(to: CGPoint(x: 18.6, y: 9.2))
        return p
    }
}
