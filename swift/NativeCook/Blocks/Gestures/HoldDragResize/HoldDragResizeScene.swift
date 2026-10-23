import SwiftUI
import UIKit

struct HoldDragResizeScene: View {
    let layout: HoldDragResizeLayout
    let snapshot: HoldDragResizeSnapshot

    var body: some View {
        let backdrop = HoldDragResizeBackdropCache.images(for: layout)
        Canvas(opaque: true, rendersAsynchronously: true) { context, _ in
            context.draw(backdrop.sharp, in: CGRect(x: 0, y: 0, width: layout.width, height: layout.height))
            drawHeader(&context, backdrop: backdrop)
            drawWeek(&context, backdrop: backdrop)
            drawLabels(&context)
            drawCards(&context, backdrop: backdrop)
            drawBar(&context, backdrop: backdrop)
            drawFinger(&context)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Thursday, 16 October, 8 plans")
    }

    private func drawHeader(_ context: inout GraphicsContext, backdrop: HoldDragResizeBackdrop) {
        drawBaselineText(&context, "Thursday", x: 16, baseline: layout.top + 38, size: 30, font: HoldDragResizeFonts.semibold, color: HoldDragResizePalette.ink)
        drawBaselineText(&context, "16 October · 8 plans", x: 16, baseline: layout.top + 60, size: 15, font: HoldDragResizeFonts.regular, color: HoldDragResizePalette.mute)

        let chip = CGRect(x: layout.right - 116, y: layout.top + 10, width: 116, height: 48)
        drawGlass(&context, rect: chip, radius: 24, tint: HoldDragResizePalette.meeting.opacity(0.06), backdrop: backdrop)
        context.fill(Path(ellipseIn: CGRect(x: chip.minX + 18, y: chip.minY + 14, width: 6, height: 6)), with: .color(HoldDragResizePalette.focus))
        drawText(&context, "Focus today", x: chip.minX + 29, y: chip.minY + 9, size: 11, font: HoldDragResizeFonts.medium, color: HoldDragResizePalette.mute)
        drawRoll(
            &context,
            minutes: snapshot.focus,
            right: chip.maxX - 18,
            baseline: chip.minY + 39,
            color: HoldDragResizePalette.focusInk
        )
    }

    private func drawWeek(_ context: inout GraphicsContext, backdrop: HoldDragResizeBackdrop) {
        let rect = CGRect(x: 16, y: layout.top + 76, width: layout.right - 16, height: 60)
        drawGlass(&context, rect: rect, radius: 30, tint: HoldDragResizePalette.meeting.opacity(0.055), backdrop: backdrop)
        let step = (rect.width - 8) / 7
        for index in HoldDragResizeData.week.indices {
            let item = HoldDragResizeData.week[index]
            let centerX = rect.minX + 4 + step * (CGFloat(index) + 0.5)
            let today = index == 3
            if today {
                drawGlass(
                    &context,
                    rect: CGRect(x: centerX - 22, y: rect.minY + 4, width: 44, height: 52),
                    radius: 26,
                    tint: Color.white.opacity(0.1),
                    backdrop: backdrop,
                    raised: true
                )
            }
            drawText(
                &context,
                item.day,
                x: centerX,
                y: rect.minY + 10,
                size: 11,
                font: HoldDragResizeFonts.medium,
                color: today ? HoldDragResizePalette.ink : HoldDragResizePalette.faint,
                anchor: .top
            )
            drawText(
                &context,
                String(item.date),
                x: centerX,
                y: rect.minY + 26,
                size: 17,
                font: HoldDragResizeFonts.semibold,
                color: today || index > 3 ? HoldDragResizePalette.ink : HoldDragResizePalette.mute,
                anchor: .top
            )
            for dot in item.dots.indices {
                let x = centerX + (CGFloat(dot) - CGFloat(item.dots.count - 1) / 2) * 6
                context.fill(
                    Path(ellipseIn: CGRect(x: x - 1.75, y: rect.minY + 46.25, width: 3.5, height: 3.5)),
                    with: .color(color(for: item.dots[dot]).opacity(today ? 1 : 0.7))
                )
            }
        }
    }

    private func drawLabels(_ context: inout GraphicsContext) {
        let edgeY: CGFloat? = snapshot.shown >= 0 && snapshot.lift > 0.01
            ? layout.y(snapshot.edge == 1 ? snapshot.tops[snapshot.shown] + snapshot.lengths[snapshot.shown] : snapshot.tops[snapshot.shown])
            : nil
        let nowY = layout.y(HoldDragResizeData.now)
        for hour in 9...18 {
            let y = layout.y(Double(hour * 60))
            var opacity = smooth(12, 20, abs(y - nowY))
            if let edgeY { opacity *= 1 - smooth(0.2, 0.5, snapshot.lift) * (1 - smooth(22, 30, abs(y - edgeY))) }
            drawText(
                &context,
                HoldDragResizeData.clock(Double(hour * 60)),
                x: 50,
                y: y - 7,
                size: 11,
                font: HoldDragResizeFonts.mono,
                color: HoldDragResizePalette.faint.opacity(opacity),
                anchor: .topTrailing
            )
        }
        let nowRect = pillRect(label: HoldDragResizeData.clock(HoldDragResizeData.now), centerY: nowY, mono: true)
        context.fill(Path(roundedRect: nowRect, cornerRadius: 11), with: .color(HoldDragResizePalette.now))
        drawText(
            &context,
            HoldDragResizeData.clock(HoldDragResizeData.now),
            x: 50,
            y: nowY - 7,
            size: 11,
            font: HoldDragResizeFonts.monoBold,
            color: HoldDragResizePalette.ink,
            anchor: .topTrailing
        )
    }

    private func drawCards(_ context: inout GraphicsContext, backdrop: HoldDragResizeBackdrop) {
        let raised = snapshot.shown >= 0 && snapshot.lift > 0.001 ? snapshot.shown : -1
        for index in HoldDragResizeData.plan.indices where index != raised {
            drawCard(&context, index: index, raised: false, backdrop: backdrop)
        }
        if raised >= 0 {
            drawCard(&context, index: raised, raised: true, backdrop: backdrop)
            drawEdgePill(&context, index: raised)
        }
    }

    private func drawCard(_ context: inout GraphicsContext, index: Int, raised: Bool, backdrop: HoldDragResizeBackdrop) {
        let item = HoldDragResizeData.plan[index]
        let rect = cardRect(index: index, raised: raised)
        let radius = min(HoldDragResizeMetrics.radius, rect.height / 2)
        let path = Path(roundedRect: rect, cornerRadius: radius)
        let elevation = raised ? snapshot.lift : (snapshot.pressCard == index ? 0.35 : 1)
        var shadow = context
        shadow.addFilter(.shadow(
            color: Color(red: 0, green: 0.01, blue: 0.03).opacity(raised ? 0.52 * snapshot.lift : 0.26),
            radius: raised ? 8 + 13 * elevation : 3,
            x: 0,
            y: raised ? 1 + 0.75 * 12 * elevation : 1
        ))
        shadow.fill(path, with: .color(Color.black.opacity(0.45)))

        let locked = snapshot.lock[index]
        var tint = color(for: item.kind).opacity(item.kind == .focus ? 0.16 : item.kind == .task ? 0.08 : 0.055)
        if locked > 0 { tint = Color.black.opacity(0.12 + 0.12 * locked) }
        drawGlass(&context, rect: rect, radius: radius, tint: tint, backdrop: backdrop, raised: raised)

        if raised { drawDelta(&context, index: index, rect: rect) }
        drawCardContent(&context, index: index, rect: rect)

        let capsuleColor = item.kind == .meeting ? Color(red: 110 / 255, green: 114 / 255, blue: 123 / 255) : color(for: item.kind)
        let capsuleRect = CGRect(x: 60, y: rect.minY, width: 6, height: rect.height)
        context.fill(Path(roundedRect: capsuleRect, cornerRadius: 3), with: .color(capsuleColor))

        if raised, snapshot.lift > 0.01 {
            let y = snapshot.edge == 1 ? rect.maxY - 10 : rect.minY + 6
            let grip = CGRect(x: rect.midX - 14, y: y, width: 28, height: 4)
            context.fill(Path(roundedRect: grip, cornerRadius: 2), with: .color(HoldDragResizePalette.ink.opacity(0.7 * pillShow)))
        }
    }

    private func drawCardContent(_ context: inout GraphicsContext, index: Int, rect: CGRect) {
        let item = HoldDragResizeData.plan[index]
        let settled = settledLayout()
        let twoLines = rect.height >= 44
        let titleY = twoLines ? rect.minY + 8 : rect.midY - 8
        var x = rect.minX + 12
        let right = rect.maxX - 12
        if item.kind == .task {
            let check = CGRect(x: x, y: titleY + 1, width: 11.5, height: 11.5)
            context.stroke(Path(ellipseIn: check), with: .color(HoldDragResizePalette.task.opacity(0.85)), lineWidth: 1.25)
            x += 21
        }

        if twoLines {
            drawText(&context, item.title, x: x, y: titleY, size: 14, font: HoldDragResizeFonts.medium, color: HoldDragResizePalette.ink)
            let start = item.kind == .meeting ? item.start : settled.tops[index]
            let end = item.kind == .meeting ? item.end : settled.tops[index] + settled.lengths[index]
            let range = item.kind == .focus
                ? "\(HoldDragResizeData.clock(start))–\(HoldDragResizeData.clock(end)) · \(HoldDragResizeData.length(end - start))"
                : "\(HoldDragResizeData.clock(start))–\(HoldDragResizeData.clock(end))"
            let subColor = item.kind == .focus ? HoldDragResizePalette.focusInk : HoldDragResizePalette.mute
            drawText(&context, range, x: x, y: titleY + 20, size: 11, font: HoldDragResizeFonts.mono, color: subColor)
            if item.kind == .meeting, !item.note.isEmpty {
                drawText(&context, " · \(item.note)", x: x + CGFloat(range.count) * 6.55, y: titleY + 20, size: 12, font: HoldDragResizeFonts.regular, color: subColor)
            }
        } else {
            let side = item.kind == .meeting ? item.note : HoldDragResizeData.clock(settled.tops[index])
            let pushed = item.kind == .task && settled.tops[index] > snapshot.starts[index] + 0.5
            if pushed {
                let old = HoldDragResizeData.clock(snapshot.oldTimes[index])
                drawText(&context, old, x: right - 74, y: titleY, size: 11, font: HoldDragResizeFonts.mono, color: HoldDragResizePalette.faint)
                context.stroke(Path { path in
                    path.move(to: CGPoint(x: right - 75, y: titleY + 7))
                    path.addLine(to: CGPoint(x: right - 39, y: titleY + 7))
                }, with: .color(HoldDragResizePalette.faint), lineWidth: 1)
                drawText(&context, side, x: right, y: titleY, size: 11, font: HoldDragResizeFonts.monoBold, color: HoldDragResizePalette.ink, anchor: .topTrailing)
            } else if !side.isEmpty {
                drawText(
                    &context,
                    side,
                    x: right - (snapshot.lock[index] > 0 ? 18 : 0),
                    y: titleY,
                    size: item.kind == .meeting ? 12 : 11,
                    font: item.kind == .meeting ? HoldDragResizeFonts.regular : HoldDragResizeFonts.mono,
                    color: HoldDragResizePalette.mute,
                    anchor: .topTrailing
                )
            }
            drawText(&context, item.title, x: x, y: titleY, size: 14, font: HoldDragResizeFonts.medium, color: HoldDragResizePalette.ink)
        }

        if snapshot.lock[index] > 0 {
            let center = CGPoint(x: right - 4, y: titleY + 7)
            let lockOpacity = snapshot.lock[index]
            context.stroke(
                Path { path in
                    path.move(to: CGPoint(x: center.x - 3, y: center.y - 1))
                    path.addCurve(
                        to: CGPoint(x: center.x + 3, y: center.y - 1),
                        control1: CGPoint(x: center.x - 3, y: center.y - 7),
                        control2: CGPoint(x: center.x + 3, y: center.y - 7)
                    )
                },
                with: .color(HoldDragResizePalette.ink.opacity(0.7 * lockOpacity)),
                lineWidth: 1.25
            )
            context.fill(Path(roundedRect: CGRect(x: center.x - 4.5, y: center.y - 1, width: 9, height: 7), cornerRadius: 1.75), with: .color(HoldDragResizePalette.ink.opacity(0.7 * lockOpacity)))
        }
    }

    private func drawDelta(_ context: inout GraphicsContext, index: Int, rect: CGRect) {
        let delta = snapshot.edge == 1 ? snapshot.target - snapshot.grabEdge : snapshot.grabEdge - snapshot.target
        guard abs(delta) >= 15 else { return }
        let label = "\(delta >= 0 ? "+" : "−")\(HoldDragResizeData.length(delta))"
        let y = snapshot.edge == 1 ? rect.maxY - 25 : rect.minY + 15
        drawText(&context, label, x: rect.maxX - 12, y: y, size: 12, font: HoldDragResizeFonts.semibold, color: HoldDragResizePalette.focusInk.opacity(pillShow), anchor: .topTrailing)
        if delta > 0 {
            let oldEdge = layout.y(snapshot.grabEdge)
            let fill = CGRect(x: rect.minX, y: max(rect.minY, oldEdge), width: rect.width, height: max(0, rect.maxY - max(rect.minY, oldEdge)))
            context.fill(
                Path(fill),
                with: .linearGradient(
                    Gradient(colors: [.clear, HoldDragResizePalette.focus.opacity(0.2 * pillShow)]),
                    startPoint: CGPoint(x: fill.midX, y: fill.minY),
                    endPoint: CGPoint(x: fill.midX, y: fill.maxY)
                )
            )
        }
    }

    private func drawEdgePill(_ context: inout GraphicsContext, index: Int) {
        guard pillShow > 0.01 else { return }
        let rect = cardRect(index: index, raised: true)
        let settled = settledLayout()
        let minute = snapshot.edge == 1 ? settled.tops[index] + settled.lengths[index] : settled.tops[index]
        let centerY = snapshot.edge == 1 ? rect.maxY : rect.minY
        let label = HoldDragResizeData.clock(minute)
        let pill = pillRect(label: label, centerY: min(max(centerY, layout.gridTop), layout.y(HoldDragResizeData.dayEnd)), mono: true)
        context.fill(Path(roundedRect: pill, cornerRadius: 11), with: .color(HoldDragResizePalette.focus.opacity(pillShow)))
        drawText(&context, label, x: 50, y: pill.minY + 4, size: 11, font: HoldDragResizeFonts.monoBold, color: HoldDragResizePalette.onAccent.opacity(pillShow), anchor: .topTrailing)
    }

    private func drawBar(_ context: inout GraphicsContext, backdrop: HoldDragResizeBackdrop) {
        let plusRect = CGRect(x: layout.right - 52, y: layout.barTop, width: 52, height: 52)
        let barRect = CGRect(x: 16, y: layout.barTop, width: plusRect.minX - 24, height: 52)
        drawGlass(&context, rect: barRect, radius: 26, tint: HoldDragResizePalette.meeting.opacity(0.055), backdrop: backdrop)
        drawGlass(&context, rect: plusRect, radius: 26, tint: HoldDragResizePalette.meeting.opacity(0.055), backdrop: backdrop)
        let baseline = layout.barTop + 32
        let rollRight: CGFloat = 100
        drawRoll(&context, minutes: snapshot.open, right: rollRight, baseline: baseline, color: HoldDragResizePalette.ink)
        drawBaselineText(&context, " open until 18:00", x: rollRight, baseline: baseline - 1, size: 15, font: HoldDragResizeFonts.regular, color: HoldDragResizePalette.mute)
        let center = CGPoint(x: plusRect.midX, y: plusRect.midY)
        context.stroke(Path { path in
            path.move(to: CGPoint(x: center.x - 8, y: center.y))
            path.addLine(to: CGPoint(x: center.x + 8, y: center.y))
            path.move(to: CGPoint(x: center.x, y: center.y - 8))
            path.addLine(to: CGPoint(x: center.x, y: center.y + 8))
        }, with: .color(HoldDragResizePalette.ink), style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }

    private func drawFinger(_ context: inout GraphicsContext) {
        guard snapshot.ghostOpacity > 0.01 else { return }
        let radius = HoldDragResizeMetrics.finger / 2 * (1 - 0.12 * snapshot.ghostPress)
        let rect = CGRect(x: snapshot.ghost.x - radius, y: snapshot.ghost.y - radius, width: radius * 2, height: radius * 2)
        context.fill(Path(ellipseIn: rect), with: .color(HoldDragResizePalette.finger.opacity(snapshot.ghostOpacity)))
        context.stroke(Path(ellipseIn: rect.insetBy(dx: 0.5, dy: 0.5)), with: .color(HoldDragResizePalette.fingerEdge.opacity(snapshot.ghostOpacity)), lineWidth: 1)
    }

    private func drawGlass(
        _ context: inout GraphicsContext,
        rect: CGRect,
        radius: CGFloat,
        tint: Color,
        backdrop: HoldDragResizeBackdrop,
        raised: Bool = false
    ) {
        guard rect.width > 0, rect.height > 0 else { return }
        let path = Path(roundedRect: rect, cornerRadius: radius)
        context.drawLayer { layer in
            layer.clip(to: path)
            layer.draw(backdrop.frosted, in: CGRect(x: 0, y: 0, width: layout.width, height: layout.height))
            layer.fill(path, with: .color(Color(red: 0.03, green: 0.04, blue: 0.07).opacity(0.33)))
            layer.fill(path, with: .color(tint))
            layer.fill(
                path,
                with: .linearGradient(
                    Gradient(colors: [Color.white.opacity(raised ? 0.1 : 0.065), .clear, Color.black.opacity(0.12)]),
                    startPoint: CGPoint(x: rect.midX, y: rect.minY),
                    endPoint: CGPoint(x: rect.midX, y: rect.maxY)
                )
            )
        }
        context.stroke(path, with: .color(Color.white.opacity(raised ? 0.35 : 0.22)), lineWidth: raised ? 1.15 : 0.75)
        let inner = Path(roundedRect: rect.insetBy(dx: 1.5, dy: 1.5), cornerRadius: max(radius - 1.5, 0))
        context.stroke(inner, with: .color(Color.white.opacity(raised ? 0.12 : 0.055)), lineWidth: 1)
    }

    private func cardRect(index: Int, raised: Bool) -> CGRect {
        let item = HoldDragResizeData.plan[index]
        let planned = item.end - item.start
        let squash = max(0, 1 - snapshot.lengths[index] / planned)
        let bulge = item.kind == .meeting || index == snapshot.active ? 0 : min(3, 14 * squash)
        let lift = raised && !snapshot.reduced ? snapshot.lift : 0
        let give = item.kind == .meeting && !snapshot.reduced ? 1.5 * snapshot.lock[index] : 0
        let baseY = layout.y(snapshot.tops[index]) + 2 - CGFloat(2 * lift) + CGFloat(give)
        let height = max(CGFloat(snapshot.lengths[index]) * layout.ppm - 4 - CGFloat(give), 4)
        let scale = 1 + CGFloat(0.012 * lift)
        let rect = CGRect(x: layout.cardX - CGFloat(bulge), y: baseY, width: layout.cardW + CGFloat(2 * bulge), height: height)
        guard scale != 1 else { return rect }
        return CGRect(
            x: rect.midX - rect.width * scale / 2,
            y: rect.midY - rect.height * scale / 2,
            width: rect.width * scale,
            height: rect.height * scale
        )
    }

    private var pillShow: Double {
        smooth(0.5, 1, snapshot.lift)
    }

    private func pillRect(label: String, centerY: CGFloat, mono: Bool) -> CGRect {
        let width = CGFloat(label.count) * (mono ? 7.15 : 7) + 14
        return CGRect(x: 57 - width, y: centerY - 11, width: width, height: 22)
    }

    private func settledLayout() -> (tops: [Double], lengths: [Double]) {
        var tops = Array(repeating: 0.0, count: snapshot.starts.count)
        var lengths = Array(repeating: 0.0, count: snapshot.starts.count)
        var floor = HoldDragResizeData.dayStart
        for index in snapshot.starts.indices {
            var start = snapshot.starts[index]
            var end = snapshot.ends[index]
            if index == snapshot.active {
                if snapshot.edge == 1 { end = snapshot.target }
                else { start = snapshot.target }
            }
            tops[index] = HoldDragResizeData.plan[index].kind == .meeting || index == snapshot.active
                ? start
                : max(start, floor)
            lengths[index] = end - start
            floor = tops[index] + lengths[index]
        }
        return (tops, lengths)
    }

    private func drawRoll(
        _ context: inout GraphicsContext,
        minutes: Double,
        right: CGFloat,
        baseline: CGFloat,
        color: Color
    ) {
        let value = max(Int(minutes.rounded()), 0)
        let hour = String(value / 60)
        let minute = String(format: "%02d", value % 60)
        let tight: CGFloat = 1.5
        let loose: CGFloat = 5
        var x = right

        x -= textWidth("m", size: 13, font: HoldDragResizeFonts.medium)
        drawBaselineText(&context, "m", x: x, baseline: baseline, size: 13, font: HoldDragResizeFonts.medium, color: color)
        x -= tight + textWidth(minute, size: 17, font: HoldDragResizeFonts.semibold)
        drawBaselineText(&context, minute, x: x, baseline: baseline, size: 17, font: HoldDragResizeFonts.semibold, color: color)
        x -= loose + textWidth("h", size: 13, font: HoldDragResizeFonts.medium)
        drawBaselineText(&context, "h", x: x, baseline: baseline, size: 13, font: HoldDragResizeFonts.medium, color: color)
        x -= tight + textWidth(hour, size: 17, font: HoldDragResizeFonts.semibold)
        drawBaselineText(&context, hour, x: x, baseline: baseline, size: 17, font: HoldDragResizeFonts.semibold, color: color)
    }

    private func textWidth(_ string: String, size: CGFloat, font: String) -> CGFloat {
        guard let uiFont = UIFont(name: font, size: size) else { return 0 }
        return (string as NSString).size(withAttributes: [.font: uiFont]).width
    }

    private func drawBaselineText(
        _ context: inout GraphicsContext,
        _ string: String,
        x: CGFloat,
        baseline: CGFloat,
        size: CGFloat,
        font: String,
        color: Color
    ) {
        let ascent = UIFont(name: font, size: size)?.ascender ?? size * 0.8
        drawText(&context, string, x: x, y: baseline - ascent, size: size, font: font, color: color)
    }

    private func color(for kind: HoldDragResizeKind) -> Color {
        switch kind {
        case .meeting: HoldDragResizePalette.meeting
        case .task: HoldDragResizePalette.task
        case .focus: HoldDragResizePalette.focus
        }
    }

    private func smooth(_ lower: Double, _ upper: Double, _ value: Double) -> Double {
        let t = min(max((value - lower) / (upper - lower), 0), 1)
        return t * t * (3 - 2 * t)
    }

    private func smooth(_ lower: CGFloat, _ upper: CGFloat, _ value: CGFloat) -> Double {
        let t = min(max((value - lower) / (upper - lower), 0), 1)
        return Double(t * t * (3 - 2 * t))
    }

    private func drawText(
        _ context: inout GraphicsContext,
        _ string: String,
        x: CGFloat,
        y: CGFloat,
        size: CGFloat,
        font: String,
        color: Color,
        anchor: UnitPoint = .topLeading
    ) {
        context.draw(
            Text(string).font(.custom(font, fixedSize: size)).foregroundStyle(color),
            at: CGPoint(x: x, y: y),
            anchor: anchor
        )
    }
}

