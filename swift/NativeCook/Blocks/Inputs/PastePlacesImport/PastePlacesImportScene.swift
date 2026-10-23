import SwiftUI
import UIKit

struct PastePlacesImportScene: View {
    let time: Double
    let layout: PastePlacesImportLayout
    let pastePress: Double
    let startPress: Double
    let closePress: Double
    /// Characters in the text area (the user's own text); 0 when it is empty.
    var draftCount = 0
    /// The start button's fill while the text area holds text (0...1).
    var draftFill = 0.0
    var faces: PastePlacesImportFaces?

    var body: some View {
        Color.clear
            .frame(width: layout.size.width, height: layout.size.height)
            .overlay(alignment: .topLeading) { PastePlacesImportPage(layout: layout) }
            .overlay(alignment: .topLeading) { textArea }
            .overlay(alignment: .topLeading) { PastePlacesImportPaper(time: time, layout: layout, faces: faces) }
            .overlay(alignment: .topLeading) { icons }
            .overlay(alignment: .topLeading) { heading }
            .overlay(alignment: .topLeading) { hint }
            .overlay(alignment: .topLeading) { tip }
            .overlay(alignment: .topLeading) { counter }
            .overlay(alignment: .topLeading) { startButton }
            .overlay(alignment: .topLeading) { closeControl }
            .overlay(alignment: .topLeading) { chips }
        .accessibilityHidden(true)
    }

    /// The live text area, replaced by the paper panels from the first fold to the end of the reset.
    @ViewBuilder
    private var textArea: some View {
        if !PastePlacesImportMotion.panels(at: time) {
            PastePlacesImportCardFace(
                time: time,
                layout: layout,
                pastePress: pastePress,
                hasDraft: draftCount > 0
            )
        }
    }

    private var icons: some View {
        let fan = PastePlacesImportMotion.fan(at: time)
        return ZStack(alignment: .topLeading) {
            icon("doc", fan: fan, values: PastePlacesImportMotion.documentFan)
            icon("chat", fan: fan, values: PastePlacesImportMotion.chatFan)
            icon("notes", fan: fan, values: PastePlacesImportMotion.notesFan)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    private func icon(
        _ name: String,
        fan: Double,
        values: (rotation: Double, x: Double, y: Double, scale: Double)
    ) -> some View {
        let spec = PastePlacesImportTheme.icons[name]!
        return Image(uiImage: PastePlacesImportAssets.image(name))
            .resizable()
            .interpolation(.high)
            .frame(width: layout.px(spec.width), height: layout.px(spec.height))
            .scaleEffect(PastePlacesImportMotion.interpolate(1, values.scale, fan))
            .rotationEffect(.degrees(values.rotation * fan))
            .position(
                x: layout.px(spec.x + spec.width / 2) + values.x * layout.pointScale * fan,
                y: layout.y(spec.y + spec.height / 2) + values.y * layout.pointScale * fan
            )
    }

    private var heading: some View {
        ZStack(alignment: .topLeading) {
            ForEach(PastePlacesImportTheme.headingLines, id: \.self) { line in
                ForEach(line.words, id: \.self) { word in
                    PastePlacesImportTextRun(
                        text: word.text,
                        fontName: PastePlacesImportTheme.extraFontName,
                        size: PastePlacesImportTheme.headingSize,
                        tracking: word.tracking,
                        color: PastePlacesImportTheme.ink,
                        baseline: line.baseline,
                        left: word.x,
                        layout: layout
                    )
                }
            }
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    private var hint: some View {
        return ZStack(alignment: .topLeading) {
            Text(PastePlacesImportTheme.pin.text)
                .font(.system(size: PastePlacesImportTheme.pin.size * layout.pointScale))
                .frame(width: 60 * layout.unit, height: 60 * layout.unit, alignment: .topLeading)
                .position(
                    x: layout.px(PastePlacesImportTheme.pin.left) + 30 * layout.unit,
                    y: layout.y(PastePlacesImportTheme.pin.top) + 30 * layout.unit
                )
            ForEach(PastePlacesImportTheme.hintLines, id: \.self) { line in
                ForEach(line.words, id: \.self) { word in
                    PastePlacesImportTextRun(
                        text: word.text,
                        fontName: PastePlacesImportTheme.textFontName,
                        size: PastePlacesImportTheme.hintSize,
                        tracking: word.tracking,
                        color: PastePlacesImportTheme.hint,
                        baseline: line.baseline,
                        left: word.x,
                        layout: layout
                    )
                }
            }
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    private var tip: some View {
        let underline = PastePlacesImportMotion.tipLine(at: time)
        let dash = PastePlacesImportTheme.tipDash
        let line = PastePlacesImportTheme.tipUnderline
        return ZStack(alignment: .topLeading) {
            ForEach(PastePlacesImportTheme.tipWords, id: \.self) { word in
                PastePlacesImportTextRun(
                    text: word.text,
                    fontName: PastePlacesImportTheme.textFontName,
                    size: PastePlacesImportTheme.hintSize,
                    tracking: word.tracking,
                    color: PastePlacesImportTheme.hint,
                    baseline: PastePlacesImportTheme.tipBaseline,
                    left: word.x,
                    layout: layout
                )
            }
            Rectangle()
                .fill(PastePlacesImportTheme.hint)
                .frame(width: layout.px(dash.width), height: layout.px(dash.height))
                .position(x: layout.px(dash.x + dash.width / 2), y: layout.y(dash.y + dash.height / 2))
            Capsule()
                .fill(PastePlacesImportTheme.detect)
                .frame(width: layout.px(line.width), height: layout.px(line.height))
                .scaleEffect(x: max(underline, 0.001), anchor: .leading)
                .opacity(underline > 0 ? 1 : 0)
                .position(x: layout.px(line.x + line.width / 2), y: layout.y(line.y + line.height / 2))
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    @ViewBuilder
    private var counter: some View {
        if draftCount > 0 {
            let word = PastePlacesImportTheme.counterWords[0]
            PastePlacesImportTextRun(
                text: "\(Self.grouped(draftCount))/\(Self.grouped(PastePlacesImportTheme.Input.max)) characters",
                fontName: PastePlacesImportTheme.textFontName,
                size: PastePlacesImportTheme.counterSize,
                tracking: word.tracking,
                color: PastePlacesImportTheme.counter,
                baseline: PastePlacesImportTheme.counterBaseline,
                left: word.x,
                layout: layout
            )
            .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        } else {
            PastePlacesImportCounter(time: time, layout: layout)
        }
    }

    /// "1,234": the counter's digits while the user types.
    private static func grouped(_ value: Int) -> String {
        let digits = String(value)
        var result = ""
        for (offset, character) in digits.reversed().enumerated() {
            if offset > 0, offset % 3 == 0 { result.append(",") }
            result.append(character)
        }
        return String(result.reversed())
    }

    private var startButton: some View {
        let button = PastePlacesImportTheme.button
        let amount = startPress
        let scale = PastePlacesImportMotion.interpolate(1, PastePlacesImportMotion.pressScale, amount)
        let fill = max(PastePlacesImportMotion.fill(at: time), draftFill)
        let labels = PastePlacesImportMotion.labels(at: time)
        let lineBox = layout.lineBox(size: PastePlacesImportTheme.start.size, baseline: PastePlacesImportTheme.start.baseline)

        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .leading) {
                PastePlacesImportTheme.buttonFill
                PastePlacesImportTheme.buttonSolid
                    .frame(width: layout.px(button.width), height: layout.px(button.height))
                    .offset(x: (fill - 1) * layout.px(button.width))
            }
            .frame(width: layout.px(button.width), height: layout.px(button.height), alignment: .leading)
            .clipShape(Capsule())
            .position(x: layout.px(button.x + button.width / 2), y: layout.y(button.y + button.height / 2))

            PastePlacesImportTextRun(
                text: PastePlacesImportTheme.start.text,
                fontName: PastePlacesImportTheme.extraFontName,
                size: PastePlacesImportTheme.start.size,
                tracking: PastePlacesImportTheme.start.tracking,
                color: PastePlacesImportTheme.white,
                baseline: PastePlacesImportTheme.start.baseline,
                layout: layout
            )
            .opacity(1 - labels.oldGone)
            .offset(y: -PastePlacesImportMotion.labelShift * layout.pointScale * labels.oldGone)

            PastePlacesImportFoundLabel(
                count: max(1, PastePlacesImportMotion.foundCount(at: time)),
                pluralOpacity: PastePlacesImportMotion.clamp(PastePlacesImportMotion.foundCount(at: time) - 1),
                lineBox: lineBox,
                layout: layout
            )
            .opacity(labels.newIn)
            .offset(y: PastePlacesImportMotion.labelShift * layout.pointScale * (1 - labels.newIn))
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .scaleEffect(scale, anchor: UnitPoint(
            x: layout.px(button.x + button.width / 2) / layout.size.width,
            y: layout.y(button.y + button.height / 2) / layout.size.height
        ))
    }

    private var closeControl: some View {
        let close = PastePlacesImportTheme.close
        let scale = PastePlacesImportMotion.interpolate(1, PastePlacesImportMotion.closeScale, closePress)
        let opacity = PastePlacesImportMotion.interpolate(1, PastePlacesImportMotion.closeDim, closePress)
        return Path { path in
            path.move(to: CGPoint(x: layout.px(close.x1), y: layout.y(close.y1)))
            path.addLine(to: CGPoint(x: layout.px(close.x2), y: layout.y(close.y2)))
            path.move(to: CGPoint(x: layout.px(close.x2), y: layout.y(close.y1)))
            path.addLine(to: CGPoint(x: layout.px(close.x1), y: layout.y(close.y2)))
        }
        .stroke(
            PastePlacesImportTheme.ink,
            style: StrokeStyle(lineWidth: layout.px(close.stroke), lineCap: .round)
        )
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
        .scaleEffect(scale, anchor: UnitPoint(
            x: layout.px(1098) / layout.size.width,
            y: layout.y(224.5) / layout.size.height
        ))
        .opacity(opacity)
    }

    private var chips: some View {
        let words = layout.noteWordBoxes()
        let sizes = layout.chipSizes()
        let cardX = layout.px(PastePlacesImportTheme.card.x)
        let cardY = layout.y(PastePlacesImportTheme.card.y)
        return ZStack(alignment: .topLeading) {
            ForEach(Array(PastePlacesImportTheme.noteLines.dropFirst().enumerated()), id: \.element) { index, line in
                let spot = PastePlacesImportTheme.spots[index]
                PastePlacesImportChip(
                    index: index,
                    text: line.place,
                    time: time,
                    word: words[index + 1],
                    size: sizes[index],
                    spot: CGPoint(x: cardX + layout.px(spot.x), y: cardY + layout.px(spot.y)),
                    layout: layout
                )
            }
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }
}

private struct PastePlacesImportPage: View {
    let layout: PastePlacesImportLayout

    var body: some View {
        let heightInReference = (layout.size.height - layout.deltaY) / layout.unit
        let stops = PastePlacesImportTheme.pageStops.map { value, color in
            Gradient.Stop(color: color, location: min(1, value / heightInReference))
        }
        let band = PastePlacesImportTheme.glowBand
        let corner = PastePlacesImportTheme.glowCorner
        ZStack(alignment: .topLeading) {
            LinearGradient(gradient: Gradient(stops: stops), startPoint: .top, endPoint: .bottom)
                .frame(height: layout.size.height - layout.deltaY)
                .offset(y: layout.deltaY)
            Circle()
                .fill(RadialGradient(
                    colors: [band.color.opacity(band.opacity), band.color.opacity(0)],
                    center: .center,
                    startRadius: 0,
                    endRadius: layout.px(band.radiusX)
                ))
                .frame(width: layout.px(band.radiusX * 2), height: layout.px(band.radiusX * 2))
                .scaleEffect(x: 1, y: band.radiusY / band.radiusX)
                .position(x: layout.px(band.centerX), y: layout.y(band.centerY))
            Circle()
                .fill(RadialGradient(
                    colors: [corner.color.opacity(corner.opacity), corner.color.opacity(0)],
                    center: .center,
                    startRadius: 0,
                    endRadius: layout.px(corner.radiusX)
                ))
                .frame(width: layout.px(corner.radiusX * 2), height: layout.px(corner.radiusX * 2))
                .scaleEffect(x: 1, y: corner.radiusY / corner.radiusX)
                .position(x: layout.px(corner.centerX), y: layout.y(corner.centerY))
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }
}

struct PastePlacesImportTextRun: View {
    let text: String
    var fontName: String? = nil
    var systemWeight: Font.Weight? = nil
    let size: Double
    let tracking: Double
    let color: Color
    let baseline: Double
    var left: Double? = nil
    var ascent = PastePlacesImportTheme.anybodyAscent
    var descent = PastePlacesImportTheme.anybodyDescent
    let layout: PastePlacesImportLayout

    var body: some View {
        let box = layout.lineBox(size: size, baseline: baseline, ascent: ascent, descent: descent)
        let x = left.map(layout.px) ?? 0
        let width = left == nil ? layout.size.width : max(0, layout.size.width - x)
        Text(text)
            .font(fontName.map { .custom($0, fixedSize: box.fontSize) }
                ?? .system(size: box.fontSize, weight: systemWeight ?? .regular))
            .tracking(tracking * layout.pointScale)
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(width: width, height: box.lineHeight, alignment: left == nil ? .center : .leading)
            .position(x: x + width / 2, y: box.top + box.lineHeight / 2)
    }
}

struct PastePlacesImportMeasuredText: View {
    let text: String
    let fontName: String
    let fontSize: Double
    let color: Color
    let frame: CGRect

    var body: some View {
        Text(text)
            .font(.custom(fontName, fixedSize: fontSize))
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(width: frame.width + 2, height: frame.height, alignment: .leading)
            .position(x: frame.minX + frame.width / 2 + 1, y: frame.midY)
    }
}

struct PastePlacesImportClipboard: View {
    let layout: PastePlacesImportLayout

    var body: some View {
        Canvas { context, size in
            let scale = size.width / PastePlacesImportTheme.clipboard.width
            let stroke = 3.8 * scale
            var body = Path(roundedRect: CGRect(
                x: 1.9 * scale,
                y: 5.9 * scale,
                width: size.width - 3.8 * scale,
                height: size.height - 7.8 * scale
            ), cornerRadius: 5 * scale)
            context.stroke(body, with: .color(PastePlacesImportTheme.glyph), lineWidth: stroke)
            body = Path(roundedRect: CGRect(
                x: 8.9 * scale,
                y: 1.9 * scale,
                width: size.width - 17.8 * scale,
                height: 8.7 * scale
            ), cornerRadius: 3 * scale)
            context.fill(body, with: .color(PastePlacesImportTheme.white))
            context.stroke(body, with: .color(PastePlacesImportTheme.glyph), lineWidth: stroke)
        }
    }
}

/// A place chip: starts as its word (label over the word, same size), hovers off the paper while it folds, flies on
/// an arc to its place on the map and drops onto it as a pin (gravity, bounces, squash). All transforms are about
/// the chip's centre, computed so the right point of the chip lands on the right point of the screen.
private struct PastePlacesImportChip: View {
    let index: Int
    let text: String
    let time: Double
    let word: PastePlacesImportLayout.Box
    let size: PastePlacesImportLayout.ChipSize
    let spot: CGPoint
    let layout: PastePlacesImportLayout

    var body: some View {
        let motion = PastePlacesImportMotion.self
        let k = layout.pointScale
        let pass = motion.placePasses[index]
        let lift = pass + motion.liftDelay
        let pop = motion.pop(at: time, index: index)

        if time < lift || pop >= 1 {
            Color.clear.frame(width: 0, height: 0)
        } else {
            let s0 = PastePlacesImportTheme.Note.size / PastePlacesImportTheme.Chip.size
            let mapScale = PastePlacesImportTheme.Chip.mapScale
            let pin = PastePlacesImportTheme.Pin.self
            let cx = size.width / 2
            let cy = size.height / 2
            // Start: the label's left / middle on the word's, at the word's size, hovering.
            let hover = motion.hover(at: time, pass: pass)
            let sA = s0 * (1 + motion.hoverT.grow * hover)
            let ax = word.x - sA * (size.labelX - cx)
            let ay = word.y + word.height / 2 - motion.hoverT.rise * k * hover
            // End: the pin's foot right above the dot, at map scale.
            let qy = spot.y - (pin.dotR + pin.gap) * k
            let bx = spot.x - mapScale * (size.pinCenterX - cx)
            let by = qy - mapScale * (size.height - cy)
            let f = motion.flight(at: time, index: index)
            let fc = motion.clamp(f)
            let arc = sin(.pi * fc)
            let d = motion.dropAt(time, index: index)
            let s = motion.interpolate(sA, mapScale, fc) * (1 + motion.fly.grow * arc) * (1 - motion.popT.shrink * pop)
            let sx = s * (1 + motion.dropT.squashX * d.imp)
            let sy = s * (1 - motion.dropT.squashY * d.imp)
            let x = motion.interpolate(ax, bx, f)
            // The drop lowers the chip by its height above the map; the squash keeps its foot on the map.
            let y = motion.interpolate(ay, by - motion.dropT.height * k * d.h, f)
                - motion.fly.arc * k * arc + (s - sy) * cy - motion.popT.rise * k * pop
            let tilt = (index.isMultiple(of: 2) ? -1.0 : 1.0) * motion.fly.tilt * arc
            let pill = motion.chipPill(at: time, pass: pass)
            let air = max(0.45 * hover * (1 - fc), sin(.pi * fc), 0.6 * d.h * fc)

            chip(pill: pill, air: air)
                .frame(width: size.width, height: size.height)
                .scaleEffect(x: sx, y: sy)
                .rotationEffect(.degrees(tilt))
                .position(x: x, y: y)
                .opacity(1 - pop)
        }
    }

    private func chip(pill: Double, air: Double) -> some View {
        let k = layout.pointScale
        let shape = RoundedRectangle(cornerRadius: layout.px(PastePlacesImportTheme.Chip.radius), style: .circular)
        let labelFont = Font.custom(PastePlacesImportTheme.textFontName, fixedSize: PastePlacesImportTheme.Chip.size * k)
        return ZStack(alignment: .topLeading) {
            shape
                .fill(PastePlacesImportTheme.chipFill)
                .overlay {
                    shape.strokeBorder(PastePlacesImportTheme.chipBorder, lineWidth: layout.px(PastePlacesImportTheme.hairline))
                }
                .frame(width: size.width, height: size.height)
                .opacity(pill)
                .shadow(
                    color: PastePlacesImportTheme.chipShadow.opacity((0.05 + 0.15 * air) * pill),
                    radius: (2 + 9 * air) * k,
                    x: 0,
                    y: (1 + 9 * air) * k
                )
            Text(PastePlacesImportTheme.pin.text)
                .font(.system(size: PastePlacesImportTheme.Chip.pinSize * k))
                .opacity(pill)
                .fixedSize()
                .position(x: size.pinCenterX, y: size.height / 2)
            Text(text)
                .font(labelFont)
                .foregroundStyle(PastePlacesImportTheme.ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: (PastePlacesImportTheme.Chip.size * k * 1.4).rounded())
                .position(x: size.labelX + labelWidth / 2, y: size.height / 2)
        }
    }

    private var labelWidth: Double {
        size.width - size.labelX - layout.px(PastePlacesImportTheme.Chip.padRight)
    }
}

private struct PastePlacesImportCounter: View {
    private static let digits = String(PastePlacesImportTheme.noteText.count).compactMap { $0.wholeNumberValue }

    let time: Double
    let layout: PastePlacesImportLayout

    var body: some View {
        let master = PastePlacesImportMotion.count(at: time)
        let rolling = master > 0
        let box = layout.lineBox(
            size: PastePlacesImportTheme.counterSize,
            baseline: PastePlacesImportTheme.counterBaseline
        )
        let word = PastePlacesImportTheme.counterWords[0]
        let tracking = word.tracking * layout.pointScale
        let columnWidth = PastePlacesImportTheme.textDigitAdvance * box.fontSize + tracking
        let zeroWidth = (1139.0 / 2000.0) * box.fontSize + tracking
        let span = time >= PastePlacesImportMotion.countOutStart
            ? PastePlacesImportMotion.countOutDuration
            : PastePlacesImportMotion.countInDuration
        let stagger = time >= PastePlacesImportMotion.countOutStart
            ? PastePlacesImportMotion.countOutStagger
            : PastePlacesImportMotion.countInStagger
        let shifts = Self.digits.indices.map { index in
            PastePlacesImportMotion.slot(
                master: master,
                index: index,
                count: Self.digits.count,
                span: span,
                stagger: stagger
            )
        }
        let totalShift = shifts.dropLast().reduce(0) { result, value in
            result + PastePlacesImportMotion.clamp(value * 3) * columnWidth
        }
        let x0 = layout.px(word.x)

        return ZStack(alignment: .topLeading) {
            PastePlacesImportTextRun(
                text: word.text,
                fontName: PastePlacesImportTheme.textFontName,
                size: PastePlacesImportTheme.counterSize,
                tracking: word.tracking,
                color: PastePlacesImportTheme.counter,
                baseline: PastePlacesImportTheme.counterBaseline,
                left: word.x,
                layout: layout
            )
            .opacity(rolling ? 0 : 1)

            ForEach(Self.digits.indices, id: \.self) { index in
                let steps = Self.digits[index] + 10 * index
                let preceding = shifts.prefix(index).reduce(0) { result, value in
                    result + PastePlacesImportMotion.clamp(value * 3) * columnWidth
                }
                let grow = index == Self.digits.count - 1
                    ? 1
                    : PastePlacesImportMotion.clamp(shifts[index] * 3)
                VStack(spacing: 0) {
                    ForEach(0...steps, id: \.self) { value in
                        Text("\(value % 10)")
                            .font(.custom(PastePlacesImportTheme.textFontName, fixedSize: box.fontSize))
                            .foregroundStyle(PastePlacesImportTheme.counter)
                            .frame(width: columnWidth, height: box.lineHeight)
                    }
                }
                .offset(y: -shifts[index] * Double(steps) * box.lineHeight)
                .frame(width: columnWidth, height: box.lineHeight, alignment: .top)
                .clipped()
                .opacity(rolling ? grow : 0)
                .position(
                    x: x0 - (columnWidth - zeroWidth) / 2 + columnWidth / 2 + preceding,
                    y: box.top + box.lineHeight / 2
                )
            }

            PastePlacesImportMeasuredText(
                text: String(word.text.dropFirst()),
                fontName: PastePlacesImportTheme.textFontName,
                fontSize: box.fontSize,
                color: PastePlacesImportTheme.counter,
                frame: CGRect(
                    x: x0 + zeroWidth + totalShift,
                    y: box.top,
                    width: layout.size.width - x0 - zeroWidth - totalShift,
                    height: box.lineHeight
                )
            )
            .opacity(rolling ? 1 : 0)

            PastePlacesImportTextRun(
                text: PastePlacesImportTheme.counterWords[1].text,
                fontName: PastePlacesImportTheme.textFontName,
                size: PastePlacesImportTheme.counterSize,
                tracking: PastePlacesImportTheme.counterWords[1].tracking,
                color: PastePlacesImportTheme.counter,
                baseline: PastePlacesImportTheme.counterBaseline,
                left: PastePlacesImportTheme.counterWords[1].x,
                layout: layout
            )
            .offset(x: totalShift)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }
}

private struct PastePlacesImportFoundLabel: View {
    let count: Double
    let pluralOpacity: Double
    let lineBox: PastePlacesImportLayout.LineBox
    let layout: PastePlacesImportLayout

    var body: some View {
        let tracking = PastePlacesImportTheme.start.tracking * layout.pointScale
        let columnWidth = PastePlacesImportTheme.extraDigitAdvance * lineBox.fontSize + tracking
        HStack(spacing: 0) {
            Text(PastePlacesImportTheme.foundPrefix)
            VStack(spacing: 0) {
                ForEach(1...PastePlacesImportMotion.placePasses.count, id: \.self) { value in
                    Text("\(value)")
                        .frame(width: columnWidth, height: lineBox.lineHeight)
                }
            }
            .offset(y: -(count - 1) * lineBox.lineHeight)
            .frame(width: columnWidth, height: lineBox.lineHeight, alignment: .top)
            .clipped()
            Text(PastePlacesImportTheme.foundSuffix)
            Text(PastePlacesImportTheme.foundPlural).opacity(pluralOpacity)
        }
        .font(.custom(PastePlacesImportTheme.extraFontName, fixedSize: lineBox.fontSize))
        .tracking(tracking)
        .foregroundStyle(PastePlacesImportTheme.white)
        .frame(width: layout.size.width, height: lineBox.lineHeight, alignment: .center)
        .position(x: layout.size.width / 2, y: lineBox.top + lineBox.lineHeight / 2)
    }
}
