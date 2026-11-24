import SwiftUI

/// The reusable piece: a round + button that morphs into a composer card, and collapses into a pill
/// that flies to a row. It owns no list; the model hands it the landing row and takes the draft.
struct QuickAddMorphComposer: View {
    @Bindable var model: QuickAddMorphModel
    let f: QuickAddMorphFrame
    let g: QuickAddMorphGeometry
    var placeholder = "New task"
    var focus: FocusState<Bool>.Binding

    private typealias M = QuickAddMorphMotion

    var body: some View {
        ZStack(alignment: .topLeading) {
            fab
            morph
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
    }

    // MARK: Button

    private var fab: some View {
        let shown = f.active == 1 ? f.fabIn : 1
        let r = g.fab
        return Button { model.open() } label: {
            ZStack {
                Circle().fill(M.ink.color)
                    .shadow(color: Color(.sRGB, red: 33 / 255, green: 30 / 255, blue: 26 / 255, opacity: 0.24), radius: 11, y: 10)
                Circle().fill(M.ink.color)
                    .shadow(color: Color(.sRGB, red: 33 / 255, green: 30 / 255, blue: 26 / 255, opacity: 0.28), radius: 1, y: 1)
                Circle().strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.14), .clear], startPoint: .top, endPoint: .center),
                    lineWidth: 1)
                plus(QuickAddMorphMotion.cream.color)
            }
            .frame(width: r.width, height: r.height)
            .contentShape(Circle())
        }
        .buttonStyle(QuickAddMorphPlainButton())
        .accessibilityLabel("New task")
        .opacity(M.clamp01(shown))
        .scaleEffect((0.6 + 0.4 * shown) * (1 - 0.08 * f.press))
        .offset(x: r.minX, y: r.minY)
        .allowsHitTesting(f.active == 0 || shown > 0.5)
    }

    private func plus(_ color: Color) -> some View {
        ZStack {
            Capsule().fill(color).frame(width: M.bar, height: 2.4)
            Capsule().fill(color).frame(width: M.bar, height: 2.4).rotationEffect(.degrees(90))
        }
        .frame(width: M.icon, height: M.icon)
    }

    // MARK: Morph

    private var morph: some View {
        let w = f.openW, h = f.openH
        let co = M.clamp01(f.collapse)
        let fl = M.clamp01(f.fly)
        let bottom = Double(g.fab.maxY)
        let width0 = M.mix(w, g.fab.width, g.card.width)
        let height0 = M.mix(h, g.fab.height, g.card.height)
        let x0 = g.fab.maxX - width0
        let y0 = bottom - height0 - f.lift * M.clamp01(h) + max(f.drag, -40)
        let x1 = M.mix(co, x0, g.pill.minX)
        let y1 = M.mix(co, y0, g.pill.minY)
        let width1 = M.mix(co, width0, g.pill.width)
        let height1 = M.mix(co, height0, g.pill.height)
        let rowY = g.listTop + Double(M.insert) * M.row - model.scrollY
        let x = M.mix(fl, x1, g.rowX) - sin(fl * .pi) * M.flyArc
        let y = M.mix(fl, y1, rowY)
        let width = max(0, M.mix(fl, width1, g.rowWidth))
        let height = max(0, M.mix(fl, height1, M.rowHeight))
        let radius = fl > 0 ? M.mix(fl, M.radiusPill, M.radiusRow)
            : co > 0 ? M.mix(co, M.radiusCard, M.radiusPill)
            : M.mix(M.clamp01(h), M.radiusFab, M.radiusCard)
        let opened = M.ink.mixed(M.card, at: M.clamp01(h), from: 0, to: 0.6)
        let pill = opened.mixed(M.accent, co)
        let color = pill.mixed(M.card, M.seg(fl, 0.62, 0.3))
        let shape = RoundedRectangle(cornerRadius: min(radius, min(width, height) / 2), style: .continuous)
        let barColor = M.cream.mixed(M.ink, at: M.clamp01(h), from: 0, to: 0.6).color

        return ZStack(alignment: .topLeading) {
            ZStack {
                shape.fill(color.color)
                    .shadow(color: Color(.sRGB, red: 33 / 255, green: 30 / 255, blue: 26 / 255, opacity: 0.20), radius: 18, y: 16)
                shape.fill(color.color)
                    .shadow(color: Color(.sRGB, red: 33 / 255, green: 30 / 255, blue: 26 / 255, opacity: 0.14), radius: 1.5, y: 1)
            }
            .frame(width: width, height: height)

            inner
                .frame(width: g.card.width, height: g.card.height)
                .opacity(f.content > 0.001 ? 1 : 0)
                .frame(width: width, height: height, alignment: .bottomTrailing)
                .clipShape(shape)

            Text(model.pillTitle)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(M.cream.color)
                .lineLimit(1)
                .padding(.horizontal, 16)
                .frame(width: width, height: height)
                .opacity(M.seg(f.collapse, 0.5, 0.4) * (1 - M.seg(f.fly, 0.66, 0.24)))
                .clipShape(shape)
                .allowsHitTesting(false)

            plus(barColor)
                .rotationEffect(.degrees(M.clamp01(w) * M.plusTurn))
                .opacity(1 - M.clamp01(f.collapse * 3))
                .offset(x: width - M.iconInset - M.icon, y: M.iconInset)
                .allowsHitTesting(false)

            if model.isOpen {
                Button { model.close() } label: {
                    Color.clear.frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .buttonStyle(QuickAddMorphPlainButton())
                .accessibilityLabel("Close")
                .offset(x: width - 8 - 44, y: 8)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .contentShape(shape)
        .gesture(dragToClose, including: model.isOpen ? .all : .subviews)
        .scaleEffect(1 - 0.08 * f.press * (1 - M.clamp01(h)))
        .opacity(f.active == 1 ? 1 - M.seg(fl, 0.88, 0.12) : 0)
        .offset(x: x, y: y)
        .allowsHitTesting(model.isOpen)
    }

    private var dragToClose: some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .global)
            .onChanged { value in
                // Mostly sideways first: not a close.
                if abs(value.translation.width) > 18, abs(value.translation.height) < 8 { return }
                model.dragChanged(value.translation.height)
            }
            .onEnded { value in
                model.dragEnded(value.translation.height, velocity: value.velocity.height)
            }
    }

    // MARK: Card content

    private var shownTitle: String {
        f.scripted ? String(QuickAddMorphTask.script.title.prefix(f.typed)) : model.title
    }

    private func rise(_ index: Int) -> Double {
        M.seg(f.content, M.contentStagger[index], M.contentItem)
    }

    private var inner: some View {
        let canAdd = !shownTitle.trimmingCharacters(in: .whitespaces).isEmpty
        return ZStack(alignment: .topLeading) {
            field
                .frame(width: g.card.width - M.pad * 2 - M.icon - 6, height: M.fieldHeight, alignment: .leading)
                .opacity(rise(0))
                .offset(x: M.pad, y: M.fieldTop + (1 - rise(0)) * M.contentRise)

            HStack(spacing: M.chipGap) {
                chip("Today", width: M.chipWidths[0], on: f.today, action: model.toggleToday)
                chip("Priority", width: M.chipWidths[1], on: f.priority, action: model.togglePriority)
                listChip
            }
            .frame(height: M.chipHeight)
            .opacity(rise(1))
            .offset(x: M.pad, y: M.chipsTop + (1 - rise(1)) * M.contentRise)

            Button { model.submit() } label: {
                Text("Add")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(M.cream.color)
                    .frame(width: g.card.width - M.pad * 2, height: M.addHeight)
                    .background(
                        Capsule(style: .continuous).fill(M.accent.color)
                            .shadow(color: Color(.sRGB, red: 20 / 255, green: 70 / 255, blue: 58 / 255, opacity: 0.30), radius: 1, y: 1)
                    )
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(LinearGradient(colors: [.white.opacity(0.16), .clear], startPoint: .top, endPoint: .center), lineWidth: 1)
                    )
            }
            .buttonStyle(QuickAddMorphPressDim())
            .disabled(!canAdd)
            .opacity(canAdd ? 1 : 0.42)
            .accessibilityLabel("Add task")
            .scaleEffect(1 - 0.04 * f.addPress)
            .opacity(rise(2))
            .offset(x: M.pad, y: M.addTop + (1 - rise(2)) * M.contentRise)
        }
        .frame(width: g.card.width, height: g.card.height, alignment: .topLeading)
    }

    @ViewBuilder
    private var field: some View {
        if f.scripted {
            // autoPlay types into the value without focusing, so no keyboard shows.
            Text(shownTitle.isEmpty ? placeholder : shownTitle)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(shownTitle.isEmpty ? M.mute.color : M.ink.color)
                .lineLimit(1)
        } else {
            TextField("", text: Binding(get: { model.title }, set: { model.typedByHand($0) }),
                      prompt: Text(placeholder).foregroundStyle(M.mute.color))
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(M.ink.color)
                .tint(M.accent.color)
                .submitLabel(.done)
                .focused(focus)
                .onSubmit { model.submit() }
                .accessibilityLabel("Task title")
        }
    }

    private func chip(_ label: String, width: Double, on: Double, action: @escaping () -> Void) -> some View {
        let u = M.clamp01(on)
        return Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(M.ink.mixed(M.cream, u).color)
                .lineLimit(1)
                .frame(width: width, height: M.chipHeight)
                .background(Capsule(style: .continuous).fill(M.fill.mixed(M.accent, u).color))
                .scaleEffect(1 + 0.05 * sin(u * .pi))
                .contentShape(Capsule())
        }
        .buttonStyle(QuickAddMorphPlainButton())
        .accessibilityLabel(label)
    }

    private var listChip: some View {
        let roll = M.clamp01(f.listRoll)
        return Button(action: model.cycleList) {
            HStack(spacing: 6) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(model.labels.0).frame(height: M.labelLine)
                    Text(model.labels.1).frame(height: M.labelLine)
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(M.ink.color)
                .lineLimit(1)
                .offset(y: -roll * M.labelLine)
                .frame(height: M.labelLine, alignment: .top)
                .clipped()
                Rectangle().fill(.clear)
                    .frame(width: 6, height: 6)
                    .overlay(alignment: .bottomTrailing) {
                        Path { p in
                            p.move(to: CGPoint(x: 5.2, y: 0)); p.addLine(to: CGPoint(x: 5.2, y: 5.2)); p.addLine(to: CGPoint(x: 0, y: 5.2))
                        }
                        .stroke(M.mute.color, lineWidth: 1.6)
                        .frame(width: 6, height: 6)
                    }
                    .rotationEffect(.degrees(45))
                    .offset(y: -1.5)
            }
            .padding(.horizontal, 10)
            .frame(width: M.chipWidths[2], height: M.chipHeight)
            .background(Capsule(style: .continuous).fill(M.fill.color))
            .contentShape(Capsule())
        }
        .buttonStyle(QuickAddMorphPlainButton())
        .accessibilityLabel("List, \(model.lists[model.listIndex])")
    }
}

struct QuickAddMorphPlainButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label }
}

struct QuickAddMorphPressDim: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.85 : 1)
    }
}
