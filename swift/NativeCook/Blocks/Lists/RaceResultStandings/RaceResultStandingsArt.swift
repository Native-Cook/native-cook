import CoreText
import SwiftUI
import UIKit

/// Canvas renderer for the canonical React Native 402-point artboard.
enum RaceResultStandingsArt {
    private static let lineHeight = 1.219
    private static let rowAnchor = 447.83 - 83.15
    private static let rowMid = (403.48 + 422.4) / 2 - 6
    private static let fonts: Void = {
        ["Medium", "SemiBold", "Bold", "ExtraBold"].forEach { weight in
            guard let url = Bundle.main.url(forResource: "RaceResultStandings-Montserrat-\(weight)", withExtension: "ttf") else { return }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    private static let ink = RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.ink)
    private static let muted = RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.muted)
    private static let hairline = RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.hairline)

    static func draw(_ context: GraphicsContext, size: CGSize, safeTop: CGFloat, scale: CGFloat,
                     frame: RaceResultStandingsMotion.Frame, stretch: Double, backPress: Double = 0,
                     contentOpacity: Double = 1, displayScale: Double = 3) {
        _ = fonts
        drawBackdrop(context, size: size, safeTop: safeTop, scale: scale)
        var art = context
        art.translateBy(x: 0, y: safeTop - RaceResultStandingsTheme.REF_TOP * scale)
        art.scaleBy(x: scale, y: scale)
        var content = art
        content.opacity *= contentOpacity
        content.drawLayer { layer in
            layer.opacity = 1
            let pixel = 1 / max(displayScale, 1) / Double(scale)
            drawBack(&layer, press: backPress)
            drawTitle(&layer, frame: frame)
            var header = layer
            header.opacity *= 1 - 0.95 * RaceResultStandingsMotion.headerDim(frame.replay)
            text(&header, RaceResultStandingsTheme.copy.round, x: 20.34, baseline: 147.18, font: font("Montserrat-SemiBold", 14.17), color: muted)
            text(&header, RaceResultStandingsTheme.copy.event, x: 20.07, baseline: 177.03, font: font("Montserrat-ExtraBold", 22.83))
            drawPodium(&header, frame: frame)
            drawStats(&header, frame: frame)
            rule(&layer, y: 356.5, x: 20.33, width: 361.34, lineWidth: pixel)
            drawSlots(&layer, frame: frame, hairline: pixel)
            drawDrivers(&layer, frame: frame, stretch: stretch, hairline: pixel)
        }
    }

    private static func drawBackdrop(_ context: GraphicsContext, size: CGSize, safeTop: CGFloat, scale: CGFloat) {
        let bounds = CGRect(origin: .zero, size: size)
        let g = context
        g.fill(Path(bounds), with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.base)))
        let mappedY: (Double) -> Double = { safeTop + ($0 - RaceResultStandingsTheme.REF_TOP) * scale }
        var centre = RaceResultStandingsTheme.backdrop.centre.map { sample in
            Gradient.Stop(color: Color(.sRGB, white: sample.1 / 255, opacity: 1), location: min(1, max(0, mappedY(sample.0) / max(1, size.height))))
        }
        centre.append(.init(color: RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.base), location: 1))
        g.fill(Path(bounds), with: .linearGradient(Gradient(stops: centre), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
        g.drawLayer { layer in
            var edge = RaceResultStandingsTheme.backdrop.edge.map { sample in
                let rawAlpha = min(1, sample.1 * 1.2 / 255)
                let alpha = (rawAlpha * 10_000).rounded() / 10_000
                return Gradient.Stop(color: Color.white.opacity(alpha), location: min(1, max(0, mappedY(sample.0) / max(1, size.height))))
            }
            edge.append(.init(color: .white.opacity(0), location: 1))
            layer.fill(Path(bounds), with: .linearGradient(Gradient(stops: edge), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            layer.blendMode = .destinationIn
            let profile = RaceResultStandingsTheme.backdrop.edgeProfile
            let across = profile.enumerated().map { Gradient.Stop(color: .white.opacity($0.element), location: Double($0.offset) / Double(profile.count - 1)) }
            layer.fill(Path(bounds), with: .linearGradient(Gradient(stops: across), startPoint: .zero, endPoint: CGPoint(x: size.width, y: 0)))
        }
    }

    private static func font(_ name: String, _ size: CGFloat) -> Font { .custom(name, fixedSize: size) }
    private static func text(_ context: inout GraphicsContext, _ value: String, x: Double, baseline: Double,
                             font: Font, color: Color = ink, opacity: Double = 1, right: Double? = nil) {
        guard opacity > 0.001 else { return }
        var layer = context; layer.opacity *= opacity
        let resolved = layer.resolve(Text(value).font(font).foregroundColor(color))
        let bounds = resolved.measure(in: CGSize(width: 2_000, height: 200))
        layer.draw(resolved, at: CGPoint(x: right.map { $0 - bounds.width } ?? x, y: baseline - resolved.firstBaseline(in: bounds)), anchor: .topLeading)
    }
    private static func width(_ context: GraphicsContext, _ value: String, _ font: Font) -> Double {
        Double(context.resolve(Text(value).font(font)).measure(in: CGSize(width: 1_000, height: 100)).width)
    }
    private static func rule(_ context: inout GraphicsContext, y: Double, x: Double, width: Double, lineWidth: Double) {
        context.fill(Path(CGRect(x: x, y: y - lineWidth / 2, width: width, height: lineWidth)), with: .color(hairline))
    }

    private static func drawBack(_ context: inout GraphicsContext, press: Double) {
        let b = RaceResultStandingsTheme.layout.back.self
        var g = context
        let s = 1 - (1 - RaceResultStandingsMotion.pressScale) * press
        g.translateBy(x: b.cx, y: b.cy); g.scaleBy(x: s, y: s); g.translateBy(x: -b.cx, y: -b.cy)
        let rect = CGRect(x: b.cx - b.d / 2, y: b.cy - b.d / 2, width: b.d, height: b.d)
        g.fill(Path(ellipseIn: rect), with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.backFill)))
        g.stroke(Path(ellipseIn: rect.insetBy(dx: b.ring / 2, dy: b.ring / 2)), with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.backRing)), lineWidth: b.ring)
        var arrow = Path()
        arrow.move(to: CGPoint(x: b.cx + 8.33, y: b.cy)); arrow.addLine(to: CGPoint(x: b.cx - 7.97, y: b.cy))
        arrow.move(to: CGPoint(x: b.cx - 0.87, y: b.cy - 6.7)); arrow.addLine(to: CGPoint(x: b.cx - 7.97, y: b.cy)); arrow.addLine(to: CGPoint(x: b.cx - 0.87, y: b.cy + 6.7))
        g.stroke(arrow, with: .color(ink), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    private static func drawTitle(_ context: inout GraphicsContext, frame: RaceResultStandingsMotion.Frame) {
        let t = RaceResultStandingsTheme.layout.title.self, sort = RaceResultStandingsTheme.layout.sort.self
        let f = font("Montserrat-ExtraBold", t.size)
        let raceWidth = width(context, RaceResultStandingsData.sessions[0].word, f)
        let sprintWidth = width(context, RaceResultStandingsData.sessions[1].word, f)
        let delta = sprintWidth - raceWidth
        let q = RaceResultStandingsMotion.towards(from: frame.from, RaceResultStandingsMotion.wordProgress(frame.u))
        let left = t.x - 12, top = t.baseline - RaceResultStandingsTheme.ASCENT * t.size - 8
        let w = sort.x + sort.w + 12 - left, h = max(RaceResultStandingsTheme.space.hit, lineHeight * t.size + 16)
        var g = context
        let ps = 1 - (1 - RaceResultStandingsMotion.pressScale) * frame.press
        g.translateBy(x: left + w / 2, y: top + h / 2); g.scaleBy(x: ps, y: ps); g.translateBy(x: -(left + w / 2), y: -(top + h / 2))
        let wordX = t.x - delta * q / 2
        g.drawLayer { layer in
            layer.clip(to: Path(CGRect(x: wordX, y: top, width: max(raceWidth, sprintWidth) + 4, height: lineHeight * t.size + 16)))
            let roll = RaceResultStandingsMotion.roll * t.size
            text(&layer, RaceResultStandingsData.sessions[0].word, x: wordX, baseline: t.baseline - roll * q, font: f, opacity: 1 - q)
            text(&layer, RaceResultStandingsData.sessions[1].word, x: wordX, baseline: t.baseline + roll * (1 - q), font: f, opacity: q)
        }
        let shift = delta * q / 2
        text(&g, RaceResultStandingsTheme.copy.results, x: t.x + raceWidth + shift, baseline: t.baseline, font: f)
        let open = RaceResultStandingsMotion.chevronOpen * frame.press
        chevron(&g, x: sort.x + shift, y: sort.y - open, up: true)
        chevron(&g, x: sort.x + shift, y: sort.y + sort.h - 4.95 + open, up: false)
    }
    private static func chevron(_ context: inout GraphicsContext, x: Double, y: Double, up: Bool) {
        var p = Path(); p.move(to: CGPoint(x: x + 0.65, y: up ? y + 4.3 : y + 0.65)); p.addLine(to: CGPoint(x: x + 4.17, y: up ? y + 0.65 : y + 4.3)); p.addLine(to: CGPoint(x: x + 7.68, y: up ? y + 4.3 : y + 0.65))
        context.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: 1.55, lineCap: .round, lineJoin: .round))
    }

    private static func drawPodium(_ context: inout GraphicsContext, frame: RaceResultStandingsMotion.Frame) {
        let pod = RaceResultStandingsTheme.layout.podium.self
        for slot in 0..<3 {
            let q = RaceResultStandingsMotion.towards(from: frame.from, RaceResultStandingsMotion.podiumProgress(frame.u, slot: slot))
            let left = pod.markRight[slot] - pod.markW, unitWidth = pod.codeX[slot] - left + 30
            let base = CGRect(x: left, y: pod.markY - 4, width: unitWidth, height: pod.markH + 8)
            for session in 0..<2 {
                let shift = session == 0 ? q * unitWidth : -(1 - q) * unitWidth
                context.drawLayer { layer in
                    layer.clip(to: Path(base.offsetBy(dx: shift, dy: 0)))
                    let d = RaceResultStandingsData.drivers[RaceResultStandingsData.sessions[session].order[slot]]
                    livery(&layer, mark: d.mark, color: d.team.markColor, right: pod.markRight[slot], y: pod.markY)
                    text(&layer, d.code, x: pod.codeX[slot], baseline: pod.baseline, font: font("Montserrat-SemiBold", pod.size))
                }
            }
        }
    }

    private static func drawStats(_ context: inout GraphicsContext, frame: RaceResultStandingsMotion.Frame) {
        stopwatch(&context); award(&context); wheel(&context)
        let s = RaceResultStandingsTheme.layout.stats.self
        let q = RaceResultStandingsMotion.towards(from: frame.fastestFrom, RaceResultStandingsMotion.easeOut(frame.fastestU))
        let roll = RaceResultStandingsMotion.roll * s.valueSize
        let box = CGRect(x: s.lap.x, y: s.lap.baseline - RaceResultStandingsTheme.ASCENT * s.valueSize,
                         width: RaceResultStandingsTheme.REF_W - s.lap.x - RaceResultStandingsTheme.space.rowInset, height: lineHeight * s.valueSize)
        context.drawLayer { layer in
            layer.clip(to: Path(box))
            text(&layer, RaceResultStandingsData.sessions[0].fastest, x: s.lap.x, baseline: s.lap.baseline - roll * q, font: font("Montserrat-SemiBold", s.valueSize), opacity: 1 - q)
            text(&layer, RaceResultStandingsData.sessions[1].fastest, x: s.lap.x, baseline: s.lap.baseline + roll * (1 - q), font: font("Montserrat-SemiBold", s.valueSize), opacity: q)
        }
        text(&context, RaceResultStandingsTheme.copy.lapLabel, x: s.lap.labelX, baseline: s.lap.baseline + 17.33, font: font("Montserrat-Medium", s.labelSize))
        text(&context, RaceResultStandingsTheme.copy.dayValue, x: s.day.x, baseline: s.day.baseline, font: font("Montserrat-SemiBold", s.valueSize))
        text(&context, RaceResultStandingsTheme.copy.dayLabel, x: s.day.labelX, baseline: s.day.baseline + 17.33, font: font("Montserrat-Medium", s.labelSize))
        text(&context, RaceResultStandingsTheme.copy.pitValue, x: s.pit.x, baseline: s.pit.baseline, font: font("Montserrat-SemiBold", s.valueSize))
        text(&context, RaceResultStandingsTheme.copy.pitLabel, x: s.pit.labelX, baseline: s.pit.baseline + 17.33, font: font("Montserrat-Medium", s.labelSize))
    }

    private static func drawSlots(_ context: inout GraphicsContext, frame: RaceResultStandingsMotion.Frame, hairline: Double) {
        let r = RaceResultStandingsTheme.layout.rows.self
        for slot in 0..<6 {
            let y = Double(slot) * RaceResultStandingsTheme.space.rowPitch
            text(&context, "0\(slot + 1)", x: r.numberX, baseline: r.numberBaseline + y, font: font("Montserrat-Bold", r.numberSize))
            let q = RaceResultStandingsMotion.towards(from: frame.from, RaceResultStandingsMotion.pointsProgress(frame.u, slot: slot))
            let old = RaceResultStandingsData.sessions[0].points[slot], new = RaceResultStandingsData.sessions[1].points[slot]
            let direction = new < old ? 1.0 : -1.0, distance = RaceResultStandingsMotion.roll * r.pointsSize
            let replay = RaceResultStandingsMotion.replayPoints(slot, replay: frame.replay)
            var pointsContext = context
            pointsContext.opacity *= replay.alpha
            pointsContext.translateBy(x: 0, y: distance * replay.rise)
            let box = CGRect(x: r.pointsRight - 120, y: r.pointsBaseline + y - RaceResultStandingsTheme.ASCENT * r.pointsSize, width: 120, height: lineHeight * r.pointsSize)
            pointsContext.drawLayer { layer in
                layer.clip(to: Path(box))
                text(&layer, "\(old)", x: 0, baseline: r.pointsBaseline + y + direction * distance * q, font: font("Montserrat-ExtraBold", r.pointsSize), opacity: 1 - q, right: r.pointsRight)
                text(&layer, "\(new)", x: 0, baseline: r.pointsBaseline + y - direction * distance * (1 - q), font: font("Montserrat-ExtraBold", r.pointsSize), opacity: q, right: r.pointsRight)
            }
            text(&pointsContext, RaceResultStandingsTheme.copy.pts, x: 0, baseline: r.ptsBaseline + y, font: font("Montserrat-SemiBold", r.ptsSize), right: r.ptsRight)
            rule(&context, y: r.firstRule + y, x: 18.33, width: 365.34, lineWidth: hairline)
        }
    }

    private struct RowState {
        let index: Int
        let y, speed, bump, badgeProgress: Double
        let moving, rising: Bool
        let z: Int
    }

    private static func drawDrivers(_ context: inout GraphicsContext, frame: RaceResultStandingsMotion.Frame, stretch: Double, hairline: Double) {
        var states: [RowState] = []
        let fromSlots: [Int] = RaceResultStandingsData.slots[frame.from]
        let destinationSlots: [Int] = RaceResultStandingsData.slots[1 - frame.from]
        for index in 0..<RaceResultStandingsData.drivers.count {
            let y: Double
            let speed: Double
            let bump: Double
            let badgeProgress: Double
            let moving: Bool
            let rising: Bool
            if frame.replay > 0 {
                let replay = RaceResultStandingsMotion.replayRow(index, replay: frame.replay)
                y = replay.y
                speed = replay.speed * RaceResultStandingsTheme.space.rowPitch
                bump = replay.bump
                moving = replay.direction != 0
                rising = replay.direction < 0
                badgeProgress = 0
            } else {
                let fromSlot = fromSlots[index]
                let destination = destinationSlots[index]
                let progress = RaceResultStandingsMotion.rowProgress(frame.u, destination: destination)
                y = Double(fromSlot) + Double(destination - fromSlot) * progress
                speed = RaceResultStandingsMotion.rowSpeed(frame.u, destination: destination) *
                    Double(destination - fromSlot) * RaceResultStandingsTheme.space.rowPitch
                bump = fromSlot == destination ? 0 : sin(.pi * RaceResultStandingsMotion.clamp(progress))
                moving = fromSlot != destination
                rising = destination < fromSlot
                badgeProgress = progress
            }
            let z = rising ? 3 : (moving ? 1 : 2)
            states.append(RowState(index: index, y: y, speed: speed, bump: bump,
                                   badgeProgress: badgeProgress, moving: moving, rising: rising, z: z))
        }
        states.sort { $0.z == $1.z ? $0.index < $1.index : $0.z < $1.z }
        for state in states { drawDriver(&context, state: state, frame: frame, stretch: stretch, hairline: hairline) }
    }

    private static func drawDriver(_ context: inout GraphicsContext, state: RowState,
                                   frame: RaceResultStandingsMotion.Frame, stretch: Double, hairline: Double) {
        let r = RaceResultStandingsTheme.layout.rows.self
        let y = rowAnchor + state.y * RaceResultStandingsTheme.space.rowPitch
        let bump = state.bump
        let lane = state.moving ? bump * (state.rising ? RaceResultStandingsMotion.laneRise : RaceResultStandingsMotion.laneFall) : 0
        let speed = state.speed
        let lean = min(1, max(-1, speed / RaceResultStandingsMotion.leanSpeed)) * RaceResultStandingsMotion.leanMax
        let scale = state.rising ? 1 + RaceResultStandingsMotion.liftRise * bump : 1 - RaceResultStandingsMotion.sinkFall * bump
        var g = context
        g.opacity *= 1 - (state.rising ? 0 : RaceResultStandingsMotion.dimFall * bump)
        g.translateBy(x: r.nameX + lane + stretch, y: y + rowMid - rowAnchor)
        g.rotate(by: .degrees(lean)); g.scaleBy(x: scale, y: scale)
        g.translateBy(x: -r.nameX, y: -(rowMid - rowAnchor))

        if state.rising {
            let rect = CGRect(x: r.plate.x, y: r.plate.inset, width: r.plate.w,
                              height: RaceResultStandingsTheme.space.rowPitch - 2 * r.plate.inset)
            let plate = Path(roundedRect: rect, cornerRadius: r.plate.radius)
            g.fill(plate, with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.base)))
            if bump > 0.001 {
                g.fill(plate, with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.plate, opacity: bump)))
                let inset = hairline / 2
                g.stroke(Path(roundedRect: rect.insetBy(dx: inset, dy: inset), cornerRadius: r.plate.radius - inset),
                         with: .color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.plateEdge, opacity: bump)), lineWidth: hairline)
            }
        }

        let driver = RaceResultStandingsData.drivers[state.index]
        drawTrail(&g, speed: speed, color: driver.team.color)
        text(&g, driver.name, x: r.nameX, baseline: r.nameBaseline - rowAnchor, font: font("Montserrat-Bold", r.nameSize))
        text(&g, driver.team.name, x: r.teamX, baseline: r.teamBaseline - rowAnchor,
             font: font("Montserrat-SemiBold", r.teamSize), color: driver.team.color)
        drawBadges(&g, index: state.index, progress: state.badgeProgress, from: frame.from)
    }

    private static func drawTrail(_ context: inout GraphicsContext, speed: Double, color: Color) {
        let r = RaceResultStandingsTheme.layout.rows.self
        let length = min(r.trailMax, abs(speed) * RaceResultStandingsMotion.trailPerSpeed)
        let alpha = RaceResultStandingsMotion.trailAlpha * min(1, abs(speed) / RaceResultStandingsMotion.trailAlphaSpeed)
        guard length > 0.001, alpha > 0.001 else { return }
        let mid = rowMid - rowAnchor, end = mid + (speed < 0 ? length : -length)
        let rect = CGRect(x: r.trailX, y: min(mid, end), width: r.trailW, height: abs(end - mid))
        context.fill(Path(rect), with: .linearGradient(
            Gradient(stops: [.init(color: color.opacity(alpha), location: 0), .init(color: color.opacity(0), location: 1)]),
            startPoint: CGPoint(x: r.trailX, y: mid), endPoint: CGPoint(x: r.trailX, y: end)))
    }

    private static func drawBadges(_ context: inout GraphicsContext, index: Int, progress: Double, from: Int) {
        let q = RaceResultStandingsMotion.towards(from: from,
            RaceResultStandingsMotion.easeOut(RaceResultStandingsMotion.segment(
                progress, RaceResultStandingsMotion.badgeFrom, RaceResultStandingsMotion.badgeTo)))
        for session in 0..<2 {
            let show = session == 0 ? 1 - q : q
            let shift = (1 - show) * 3 * (session == 0 ? -1 : 1)
            let delta = RaceResultStandingsData.sessions[session].delta[index]
            badge(&context, delta: delta, x: 21, y: 418 - rowAnchor + shift, opacity: show)
            text(&context, "\(abs(delta))", x: 32.59, baseline: 424.88 - rowAnchor + shift,
                 font: font("Montserrat-SemiBold", 9.83), color: ink, opacity: show)
        }
    }

    // MARK: Exact RN vectors

    private static func livery(_ context: inout GraphicsContext, mark: RaceResultStandingsData.Mark,
                               color: Color, right: Double, y: Double) {
        let origin = right - 30
        let config: (Double?, [(Double, Double, Double, Double)], Bool)
        switch mark {
        case .fin: config = (20, [(19,8,0,3.6),(20.6,8,4.6,8.33)], false)
        case .twin: config = (15.5, [(14.5,6.2,0,8.33),(22.5,3.6,0,8.33)], false)
        case .stair: config = (nil, [(5.5,5.2,3.2,8.33),(12.6,5.2,1.6,8.33),(19.7,5.2,0,8.33)], false)
        case .chevron: config = (17, [], true)
        }
        if let end = config.0 {
            var streak = Path(); streak.move(to: CGPoint(x: origin, y: y + 1.1)); streak.addLine(to: CGPoint(x: origin + end, y: y)); streak.addLine(to: CGPoint(x: origin + end - 1.6, y: y + 4.3)); streak.closeSubpath()
            context.fill(streak, with: .linearGradient(Gradient(stops: [
                .init(color: color.opacity(0), location: 0), .init(color: color.opacity(0.9), location: 0.75), .init(color: color, location: 1)]),
                startPoint: CGPoint(x: origin, y: y), endPoint: CGPoint(x: origin + end, y: y)))
        }
        var bars = Path()
        if config.2 {
            bars.move(to: CGPoint(x: origin+16.5,y:y+8.33)); bars.addLine(to: CGPoint(x:origin+21,y:y+8.33)); bars.addLine(to:CGPoint(x:origin+26.8,y:y+4.17)); bars.addLine(to:CGPoint(x:origin+24.2,y:y)); bars.addLine(to:CGPoint(x:origin+19.7,y:y)); bars.addLine(to:CGPoint(x:origin+22.3,y:y+4.17)); bars.closeSubpath()
        } else {
            for b in config.1 { addBar(&bars, origin: origin, y: y, left: b.0, width: b.1, top: b.2, bottom: b.3) }
        }
        context.fill(bars, with: .color(color))
    }

    private static func addBar(_ path: inout Path, origin: Double, y: Double, left: Double,
                               width: Double, top: Double, bottom: Double) {
        let k = 3.2 / 8.33
        let rounded: (Double) -> Double = { ($0 * 100).rounded() / 100 }
        path.move(to: CGPoint(x:origin+rounded(left+(8.33-bottom)*k),y:y+bottom)); path.addLine(to:CGPoint(x:origin+rounded(left+width+(8.33-bottom)*k),y:y+bottom)); path.addLine(to:CGPoint(x:origin+rounded(left+width+(8.33-top)*k),y:y+top)); path.addLine(to:CGPoint(x:origin+rounded(left+(8.33-top)*k),y:y+top)); path.closeSubpath()
    }

    private static func badge(_ context: inout GraphicsContext, delta: Int, x: Double, y: Double, opacity: Double) {
        guard opacity > 0.001 else { return }
        let hex = delta < 0 ? RaceResultStandingsTheme.colors.down : RaceResultStandingsTheme.colors.up
        var p = Path()
        if delta == 0 {
            p.move(to:CGPoint(x:x+0.5,y:y+2.5));p.addLine(to:CGPoint(x:x+6.17,y:y+2.5));p.move(to:CGPoint(x:x+0.5,y:y+4.6));p.addLine(to:CGPoint(x:x+6.17,y:y+4.6))
        } else {
            let up = delta > 0
            for o in [0.0,3.6] { p.move(to:CGPoint(x:x+0.6,y:y+o+(up ? 3.15:0.6)));p.addLine(to:CGPoint(x:x+3.33,y:y+o+(up ? 0.6:3.15)));p.addLine(to:CGPoint(x:x+6.07,y:y+o+(up ? 3.15:0.6))) }
        }
        context.stroke(p, with:.color(RaceResultStandingsTheme.color(hex, opacity: opacity)), style:StrokeStyle(lineWidth:delta == 0 ? 1:1.3,lineCap:.round,lineJoin:.round))
    }

    private static func stopwatch(_ context: inout GraphicsContext) {
        let x = 32.67-12, y = 258.67-13.6
        var p=Path(ellipseIn:CGRect(x:x+2.45,y:y+4.05,width:19.1,height:19.1));p.move(to:CGPoint(x:x+12.33,y:y+4.05));p.addLine(to:CGPoint(x:x+12.33,y:y+2.2));p.move(to:CGPoint(x:x+9.6,y:y+1.35));p.addLine(to:CGPoint(x:x+14.7,y:y+1.35));p.move(to:CGPoint(x:x+18.9,y:y+6.7));p.addLine(to:CGPoint(x:x+20.2,y:y+5.3));p.move(to:CGPoint(x:x+13.2,y:y+9.2));p.addLine(to:CGPoint(x:x+10.2,y:y+12.3));p.addLine(to:CGPoint(x:x+13.9,y:y+14.3));p.addLine(to:CGPoint(x:x+10.6,y:y+17.8))
        context.stroke(p,with:.color(RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.stopwatch)),style:StrokeStyle(lineWidth:1.5,lineCap:.round,lineJoin:.round))
    }

    private static func award(_ context: inout GraphicsContext) {
        let x=32.67-12,y=302.5-9.8,red=RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.award)
        context.stroke(Path(ellipseIn:CGRect(x:x+5.75,y:y+3.55,width:12.5,height:12.5)),with:.color(red),lineWidth:1.5)
        for pts in [[(9.6,15.3),(3.3,18.3),(6.7,19.2),(7.9,22.2),(12,17.1)],[(14.4,15.3),(20.7,18.3),(17.3,19.2),(16.1,22.2),(12,17.1)]] {
            var p=Path();p.move(to:CGPoint(x:x+pts[0].0,y:y+pts[0].1));for point in pts.dropFirst(){p.addLine(to:CGPoint(x:x+point.0,y:y+point.1))};context.stroke(p,with:.color(red),style:StrokeStyle(lineWidth:1.5,lineCap:.round,lineJoin:.round))
        }
    }

    private static func wheel(_ context: inout GraphicsContext) {
        let c=CGPoint(x:205.83,y:305.67), yellow=RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.wheel), shade=RaceResultStandingsTheme.color(RaceResultStandingsTheme.colors.shade)
        context.stroke(Path(ellipseIn:CGRect(x:c.x-10.3,y:c.y-10.3,width:20.6,height:20.6)),with:.color(yellow),lineWidth:1.7);context.fill(Path(ellipseIn:CGRect(x:c.x-6.35,y:c.y-6.35,width:12.7,height:12.7)),with:.color(yellow));context.fill(Path(ellipseIn:CGRect(x:c.x-4.75,y:c.y-4.75,width:9.5,height:9.5)),with:.color(shade))
        for i in 0..<8 { let a=Double(i) * Double.pi / 4 + Double.pi / 8;var p=Path();p.move(to:CGPoint(x:c.x+cos(a)*2.1,y:c.y+sin(a)*2.1));p.addLine(to:CGPoint(x:c.x+cos(a)*5,y:c.y+sin(a)*5));context.stroke(p,with:.color(yellow),lineWidth:1.25) }
        context.fill(Path(ellipseIn:CGRect(x:c.x-2.15,y:c.y-2.15,width:4.3,height:4.3)),with:.color(yellow));context.fill(Path(ellipseIn:CGRect(x:c.x-1.05,y:c.y-1.05,width:2.1,height:2.1)),with:.color(shade))
    }

}
