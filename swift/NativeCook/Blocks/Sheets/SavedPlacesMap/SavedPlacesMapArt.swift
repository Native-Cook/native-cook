import SwiftUI
import UIKit

/// Everything the block draws, in ref px (a 1206-wide screen). Callers scale the context by `unit` first.
enum SavedPlacesMapArt {
    typealias Theme = SavedPlacesMapTheme
    typealias Motion = SavedPlacesMapMotion
    typealias Data = SavedPlacesMapData
    typealias Icons = SavedPlacesMapIcons

    static let riseRef = Motion.RISE * 3

    // MARK: Ground (flat map plane; the view projects it)

    static func drawGroundBase(_ context: GraphicsContext) {
        var ctx = context
        ctx.draw(SavedPlacesMapAssets.image("map"), in: CGRect(x: 0, y: 0, width: 1206, height: Theme.mapH))
        for label in Theme.mapLabels {
            text(&ctx, label.text, x: label.x, centerX: label.cx, base: label.base, size: label.size,
                 font: label.font, color: label.color, tracking: label.spacing, opacity: label.opacity)
        }
        // highway shield
        var shield = ctx
        shield.translateBy(x: Theme.shield.minX, y: Theme.shield.minY)
        let cap = Icons.svg("M37 1.5 C22 1.5 11 6.5 2 11 L2 22 L72 22 L72 11 C63 6.5 52 1.5 37 1.5 Z")
        let body = Icons.svg("M2 22 L72 22 L72 38 C72 48 56 54 37 55.5 C18 54 2 48 2 38 Z")
        for (p, c) in [(cap, Theme.Colors.shieldRed), (body, Theme.Colors.shieldBlue)] {
            shield.fill(p, with: .color(c))
            shield.stroke(p, with: .color(.white), style: StrokeStyle(lineWidth: 3, lineJoin: .round))
        }
        // top 21, line height 30: baseline = top + (30 - (asc + desc) * size) / 2 + asc * size
        let size = Theme.shieldSize
        let base = 21 + (30 - (Theme.ascent(Theme.FontName.barlowSemi) + Theme.descent(Theme.FontName.barlowSemi)) * size) / 2
            + Theme.ascent(Theme.FontName.barlowSemi) * size
        text(&shield, Theme.shieldText, centerX: 37, base: base, size: size, font: Theme.FontName.barlowSemi, color: .white)
    }

    /// Long contact shadows and landing ripples, painted on the ground plane.
    static func drawGroundMarks(_ context: GraphicsContext, tilt: Double) {
        for (i, marker) in Data.markers.enumerated() {
            _ = i
            let c = marker.center
            let rank = Theme.depthRank[marker.id] ?? 0
            let st = Motion.standOf(tilt, rank)
            // shadow: origin at the pin's foot, rotate then scaleX
            let shadowOpacity = Motion.SHADOW.opacity * min(1, st)
            if shadowOpacity > 0.001 {
                var s = context
                s.opacity = shadowOpacity
                s.translateBy(x: c.x, y: c.y)
                s.rotate(by: .degrees(Motion.SHADOW.angle))
                s.scaleBy(x: max(0.001, st), y: 1)
                let len = Motion.SHADOW.len, w = Motion.SHADOW.w
                // radial gradient: centre (0.1, 0.5), radii (0.9, 0.5) of the box; the ellipse fills the box
                s.translateBy(x: 0.1 * len, y: 0)
                s.scaleBy(x: 0.9 * len, y: 0.5 * w)
                let shadow = Theme.Depth.shadow
                let ellipse = Path(ellipseIn: CGRect(x: (0.5 - 0.1) / 0.9 - 0.5 / 0.9, y: -1, width: 1 / 0.9, height: 2))
                s.fill(ellipse, with: .radialGradient(
                    Gradient(stops: [.init(color: shadow, location: 0), .init(color: shadow.opacity(0.45), location: 0.45),
                                     .init(color: shadow.opacity(0), location: 1)]),
                    center: .zero, startRadius: 0, endRadius: 1))
            }
            let q = Motion.standLinear(tilt, rank)
            let e = Motion.segment(q, Motion.RIPPLE.at, 1)
            let rippleOpacity = 0.8 * Motion.segment(q, Motion.RIPPLE.at, Motion.RIPPLE.at + 0.05) * (1 - e)
            if rippleOpacity > 0.001 {
                var r = context
                r.opacity = rippleOpacity
                let d = Motion.RIPPLE.d
                let k = Motion.lerp(1, Motion.RIPPLE.grow, Motion.easeOut(e))
                scale(&r, around: c, x: k, y: k)
                let ring = Theme.Depth.rippleRing
                r.stroke(Path(ellipseIn: CGRect(x: c.x - d / 2 + ring / 2, y: c.y - d / 2 + ring / 2, width: d - ring, height: d - ring)),
                         with: .color(Theme.Colors.bookmark), lineWidth: ring)
            }
        }
    }

    // MARK: Billboards (screen space)

    static func drawBillboards(_ context: GraphicsContext, frame: Motion.Frame, reduced: Bool) {
        let tilt = reduced ? 0 : Motion.tiltOf(frame.sheet)
        let radius = Data.radius
        if tilt > 0 {
            for (i, marker) in Data.markers.enumerated() {
                drawRig(context, marker: marker, index: i, tilt: tilt, frame: frame)
            }
        }
        for path in Data.glyphPaths { drawGlyph(context, path: path, frame: frame, tilt: tilt, reduced: reduced) }
        for (i, marker) in Data.markers.enumerated() {
            let c = marker.center
            let rank = Theme.depthRank[marker.id] ?? 0
            var group = context
            var m = 1.0
            if !reduced {
                let disc = Motion.discAt(c.x, c.y, tilt, rank, radius)
                m = disc.m
                group.translateBy(x: disc.x - c.x, y: disc.y - c.y)
                scale(&group, around: c, x: m, y: m)
            }
            drawMarker(group, marker: marker, index: i, frame: frame, reduced: reduced)
            drawBubble(group, cluster: Data.clusters[i], progress: frame.cluster, reduced: reduced)
        }
    }

    private static func drawRig(_ context: GraphicsContext, marker: SavedPlacesMapMarker, index: Int, tilt: Double, frame: Motion.Frame) {
        let c = marker.center
        let rank = Theme.depthRank[marker.id] ?? 0
        let g = Motion.project(c.x, c.y, tilt)
        let st = Motion.standOf(tilt, rank)
        var rig = context
        rig.translateBy(x: g.x - c.x, y: g.y - c.y)
        scale(&rig, around: c, x: g.m, y: g.m)

        let tone = marker.tag == .saved ? Theme.Depth.beamSaved : Theme.Depth.beamLiked
        func rgba(_ a: Double) -> Color { Color(.sRGB, red: tone.0, green: tone.1, blue: tone.2, opacity: a) }
        let on = Motion.segment(st, 0.4, 1)
        let flare = index == Data.visitedIndex
        let boost = flare ? 1 + Motion.BEAM.flare * max(0, frame.visitPop - 1) : 1
        let beamOpacity = min(1, Motion.BEAM.opacity * on * boost)
        if beamOpacity > 0.001 {
            var b = rig
            b.opacity = beamOpacity
            scale(&b, around: c, x: boost, y: Motion.lerp(0.3, 1, Motion.easeOut(on)))
            let w = Motion.BEAM.w, h = Motion.BEAM.h
            let top = CGPoint(x: 0, y: c.y - h), bottom = CGPoint(x: 0, y: c.y)
            func strip(_ rect: CGRect, _ from: Color, _ to: Color) {
                b.fill(Path(rect), with: .linearGradient(Gradient(colors: [from, to]),
                                                         startPoint: CGPoint(x: rect.midX, y: top.y), endPoint: CGPoint(x: rect.midX, y: bottom.y)))
            }
            let left = c.x - w * 1.5
            strip(CGRect(x: left, y: c.y - h, width: w * 3, height: h), rgba(0), rgba(0.16))
            strip(CGRect(x: left + w, y: c.y - h, width: w, height: h), rgba(0), rgba(0.34))
            strip(CGRect(x: left + w * 1.5 - Motion.BEAM.core / 2, y: c.y - h, width: Motion.BEAM.core, height: h),
                  Color.white.opacity(0), Color.white.opacity(0.8))
        }
        let foot = 0.45 * Motion.segment(st, 0, 0.5)
        if foot > 0.001 {
            let d = Theme.Depth.footD
            rig.fill(Path(ellipseIn: CGRect(x: c.x - d / 2, y: c.y - d / 4, width: d, height: d / 2)),
                     with: .color(Theme.Depth.foot.opacity(foot)))
        }
        let stalkOpacity = Motion.segment(st, 0, 0.2)
        if stalkOpacity > 0.001 {
            var s = rig
            s.opacity = stalkOpacity
            let sh = Motion.STAND.stalk + Data.radius
            let w = Theme.Depth.stalkW
            let height = sh * max(0.001, st)
            let rect = CGRect(x: c.x - w / 2, y: c.y - height, width: w, height: height)
            s.fill(Path(roundedRect: rect, cornerRadius: min(w / 2, height / 2)), with: .linearGradient(
                Gradient(colors: [Theme.Depth.stalkTop, Theme.Depth.stalkBottom]),
                startPoint: CGPoint(x: rect.minX, y: 0), endPoint: CGPoint(x: rect.maxX, y: 0)))
        }
    }

    private static func drawGlyph(_ context: GraphicsContext, path: SavedPlacesMapGlyphPath, frame: Motion.Frame, tilt: Double, reduced: Bool) {
        let q = Motion.glyphProgress(cluster: frame.cluster, delay: path.delay)
        let g = path.glyph
        let size = Data.glyphSize(g.kind)
        var ctx = context
        var center = CGPoint(x: g.x, y: g.y)
        var k = 1.0
        if reduced {
            ctx.opacity = 1 - q
        } else {
            let from = Motion.project(g.x, g.y, tilt)
            let marker = Data.markers[path.cluster]
            let rank = Theme.depthRank[marker.id] ?? 0
            let disc = Motion.discAt(marker.center.x, marker.center.y, tilt, rank, Data.radius)
            let tx = disc.x + Theme.bubbleDx * disc.m
            let ty = disc.y + Theme.bubbleDy * disc.m
            let dx = tx - from.x, dy = ty - from.y
            let bulge = sin(.pi * q) * Motion.CLUSTER.arc
            ctx.opacity = 1 - Motion.segment(q, Motion.CLUSTER.fadeFrom, 1)
            center = CGPoint(x: from.x + dx * q - dy * bulge, y: from.y + dy * q + dx * bulge)
            k = Motion.lerp(from.m, Motion.CLUSTER.endScale * disc.m, q)
        }
        guard ctx.opacity > 0.001 else { return }
        scale(&ctx, around: center, x: k, y: k)
        drawMapGlyph(&ctx, kind: g.kind, rect: CGRect(x: center.x - size.width / 2, y: center.y - size.height / 2, width: size.width, height: size.height))
    }

    private static func drawMapGlyph(_ context: inout GraphicsContext, kind: SavedPlacesMapGlyphKind, rect: CGRect) {
        switch kind {
        case .bookmark: Icons.fillOutlined(&context, Icons.mapBookmark, in: rect, fill: Theme.Colors.bookmark, strokeWidth: 2.4)
        case .thumb: Icons.fillOutlined(&context, Icons.mapThumb, in: rect, fill: Theme.Colors.thumb, strokeWidth: 0.9)
        case .star: Icons.fillOutlined(&context, Icons.mapStar, in: rect, fill: Theme.Colors.star, strokeWidth: 1.2)
        }
    }

    private static func drawMarker(_ context: GraphicsContext, marker: SavedPlacesMapMarker, index: Int, frame: Motion.Frame, reduced: Bool) {
        let c = marker.center
        var disc = context
        let visit = index == Data.visitedIndex ? frame.visitPop : 1
        let tapped = frame.tapIndex == index ? frame.tapPop : 1
        let amount = reduced ? 1 : visit * tapped
        scale(&disc, around: c, x: amount, y: amount)
        shadowed(&disc, color: Theme.color(0x3E5568).opacity(0.28), radius: 10, y: 3) { layer in
            layer.fill(Path(ellipseIn: CGRect(x: c.x - 50, y: c.y - 50, width: 100, height: 100)), with: .color(Theme.color(0xFBFBFA)))
        }
        emojiText(&disc, marker.emoji, center: c, pointSize: marker.emojiSize)
        if index == Data.visitedIndex { drawCheckBadge(&disc, center: c, progress: frame.badge, reduced: reduced) }

        var ctx = context
        let saved = marker.tag == .saved
        let box = CGRect(x: marker.x - Data.markerIconDx - 3, y: marker.baseline - Data.markerBookmarkDy - 3,
                         width: saved ? 28 : 34, height: saved ? 32 : 38)
        drawMapGlyph(&ctx, kind: saved ? .bookmark : .thumb, rect: box)
        text(&ctx, saved ? "saved" : "liked", x: marker.x, base: marker.baseline - Data.markerSavedDy, size: Data.markerSubSize,
             font: Theme.FontName.barlowItalic, color: Theme.Colors.markerSub)
        text(&ctx, marker.name, x: marker.x, base: marker.baseline, size: Data.markerNameSize,
             font: Theme.FontName.barlowSemi, color: Theme.Colors.markerInk)
        text(&ctx, marker.subtitle, x: marker.x, base: marker.baseline + Data.markerSubDy, size: Data.markerSubSize,
             font: Theme.FontName.barlowItalic, color: Theme.Colors.markerSub)
    }

    private static func drawBubble(_ context: GraphicsContext, cluster: SavedPlacesMapCluster, progress: Double, reduced: Bool) {
        let landed = cluster.delays.reduce(0.0) { $0 + Motion.segment(Motion.glyphProgress(cluster: progress, delay: $1), Motion.CLUSTER.landFrom, 1) }
        let show = reduced ? Motion.segment(progress, 0, 1) : Motion.segment(landed, 0, 0.5)
        guard show > 0.001 else { return }
        let count = Double(cluster.count)
        let gulp = !reduced && landed > 0 && landed < count ? Motion.CLUSTER.gulp * sin(.pi * landed.truncatingRemainder(dividingBy: 1)) : 0
        let k = reduced ? 1 : Motion.lerp(Motion.CLUSTER.bubbleFrom, 1, show) + gulp
        let h = Theme.bubbleH, ring = Theme.bubbleRing
        let w = max(h, Double(String(cluster.count).count) * Theme.bubbleDigit + Theme.bubblePadX * 2)
        let rect = CGRect(x: cluster.bubble.x - w / 2, y: cluster.bubble.y - h / 2, width: w, height: h)
        var ctx = context
        ctx.opacity = show
        scale(&ctx, around: cluster.bubble, x: k, y: k)
        // border box: ring, then the clipped number strip
        ctx.fill(Path(roundedRect: rect, cornerRadius: h / 2), with: .color(.white))
        let inner = rect.insetBy(dx: ring, dy: ring)
        ctx.fill(Path(roundedRect: inner, cornerRadius: h / 2 - ring), with: .color(Theme.Colors.bookmark))
        let row = h - ring * 2
        let rolled = reduced ? count : min(count, max(1, landed))
        var numbers = ctx
        numbers.clip(to: Path(roundedRect: inner, cornerRadius: h / 2 - ring))
        let size = Theme.bubbleSize
        let font = Theme.FontName.barlowSemi
        let lineBase = (row - (Theme.ascent(font) + Theme.descent(font)) * size) / 2 + Theme.ascent(font) * size
        let first = max(1, Int(floor(rolled)))
        let last = min(cluster.count, first + 1)
        for value in first...last {
            let top = inner.minY + Double(value - 1) * row - (rolled - 1) * row
            if top > inner.maxY || top + row < inner.minY { continue }
            text(&numbers, "\(value)", centerX: cluster.bubble.x, base: top + lineBase, size: size, font: font, color: .white, monospacedDigits: true)
        }
    }

    private static func drawCheckBadge(_ context: inout GraphicsContext, center: CGPoint, progress: Double, reduced: Bool) {
        guard progress > 0.001 else { return }
        let c = CGPoint(x: center.x + Theme.badgeDx, y: center.y + Theme.badgeDy)
        var badge = context
        badge.opacity = progress
        if !reduced { scale(&badge, around: c, x: Motion.lerp(0.9, 1, progress), y: Motion.lerp(0.9, 1, progress)) }
        let d = Theme.badgeD
        let rect = CGRect(x: c.x - d / 2, y: c.y - d / 2, width: d, height: d)
        badge.fill(Path(ellipseIn: rect), with: .color(Theme.Colors.check))
        badge.stroke(Path(ellipseIn: rect.insetBy(dx: Theme.badgeRing / 2, dy: Theme.badgeRing / 2)), with: .color(Theme.Colors.checkInk), lineWidth: Theme.badgeRing)
        // svg 29 x 29 box at 0.62 of the badge
        let box = d * 0.62
        let s = box / 29
        let t = CGAffineTransform(translationX: c.x - box / 2, y: c.y - box / 2).scaledBy(x: s, y: s)
        badge.stroke(Icons.tick.applying(t), with: .color(Theme.Colors.checkInk),
                     style: StrokeStyle(lineWidth: 3.4 * 1.3 * s, lineCap: .round, lineJoin: .round))
    }

    // MARK: Chrome above the blur

    static func drawFadedChip(_ context: GraphicsContext) {
        context.fill(Path(roundedRect: CGRect(x: 1105, y: 190, width: 67, height: 42), cornerRadius: 14), with: .color(.white.opacity(0.45)))
    }

    static func drawTopChrome(_ context: GraphicsContext) {
        var ctx = context
        Icons.stroke(&ctx, Icons.chevronLeft, in: CGRect(x: 46, y: 221, width: 31, height: 56), color: .black, width: 2.4)
        ctx.fill(Path(roundedRect: CGRect(x: 44, y: 283, width: 54, height: 55), cornerRadius: Theme.Radius.badge * 3), with: .color(Theme.Colors.badge))
        let c = CGPoint(x: 71, y: 283 + 27.5)
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 19, y: c.y - 19, width: 38, height: 38)), with: .color(Theme.color(0xF4F4F4)))
        let font = Theme.FontName.barlowSemi
        let base = c.y - 15 + (30 - (Theme.ascent(font) + Theme.descent(font)) * 26) / 2 + Theme.ascent(font) * 26
        text(&ctx, "3", centerX: c.x, base: base, size: 26, font: font, color: Theme.color(0x222222))
    }

    // MARK: Controls (ride the sheet; the view offsets and fades them)

    static func drawControls(_ context: GraphicsContext) {
        var ctx = context
        shadowed(&ctx, color: Theme.color(0x3E5568).opacity(0.28), radius: 14, y: 5) { layer in
            layer.fill(Path(ellipseIn: CGRect(x: 1094 - 76, y: 1338 - 76, width: 152, height: 152)), with: .color(.white))
        }
        Icons.fill(&ctx, Icons.locate, in: CGRect(x: 1092 - 25, y: 1335 - 25, width: 50, height: 50), color: Theme.Colors.locate)
        for (index, chip) in Theme.chips.enumerated() {
            let rect = CGRect(x: chip.x, y: Theme.chipY, width: chip.w, height: Theme.chipH)
            shadowed(&ctx, color: Theme.color(0x4B5560).opacity(0.28), radius: 10, y: 5) { layer in
                layer.fill(Path(roundedRect: rect, cornerRadius: Theme.chipH / 2), with: .color(Theme.Colors.chip))
            }
            var shade = ctx
            shade.clip(to: Path(roundedRect: rect, cornerRadius: Theme.chipH / 2))
            shade.fill(Path(rect), with: .linearGradient(
                Gradient(stops: [.init(color: Theme.color(0xFDFCFE, opacity: 0), location: 0.75),
                                 .init(color: Theme.color(0xEBE8EC, opacity: 0.9), location: 1)]),
                startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)))
            if index == 0 {
                Icons.stroke(&ctx, Icons.globe, in: CGRect(x: 62, y: 1463, width: 35, height: 35), color: .black, width: 1.5)
            } else if let emoji = chip.emoji {
                emojiText(&ctx, emoji, center: CGPoint(x: chip.emojiX, y: 1481), pointSize: 12.6)
            }
            if !chip.text.isEmpty {
                text(&ctx, chip.text, x: chip.textX, base: Theme.chipBaseline, spec: Theme.TypeSpec.chip)
            }
        }
        Icons.stroke(&ctx, Icons.chevronDown, in: CGRect(x: 354, y: 1474, width: 28, height: 14), color: .black, width: 2.4)
    }

    // MARK: Sheet

    /// `height` is the canvas height in ref px (screen height plus the tail below the screen).
    static func drawSheetStatic(_ context: GraphicsContext, screenH: Double, unit: Double) {
        var ctx = context
        let top = Theme.sheetTop
        let r = Theme.Radius.sheet * 3
        let sheetRect = CGRect(x: 0, y: top, width: 1206, height: screenH - top)
        let topRound = UnevenRoundedRectangle(topLeadingRadius: r, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: r)
        ctx.fill(topRound.path(in: sheetRect), with: .linearGradient(
            Gradient(stops: [.init(color: Theme.color(0xECF2F4), location: 0), .init(color: Theme.color(0xE8F0F5), location: 0.25),
                             .init(color: Theme.color(0xE2EEF7), location: 0.55), .init(color: Theme.color(0xDDEEF8), location: 1)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: screenH)))
        // border-top: a band that follows the corner arcs and tapers to nothing at the sides
        let bw = max(3, 1 / unit)  // RN: max(1 pt, 3 ref px)
        var band = Path()
        let kappa = 0.5522847498
        band.move(to: CGPoint(x: 0, y: top + r))
        band.addCurve(to: CGPoint(x: r, y: top), control1: CGPoint(x: 0, y: top + r - r * kappa), control2: CGPoint(x: r - r * kappa, y: top))
        band.addLine(to: CGPoint(x: 1206 - r, y: top))
        band.addCurve(to: CGPoint(x: 1206, y: top + r), control1: CGPoint(x: 1206 - r + r * kappa, y: top), control2: CGPoint(x: 1206, y: top + r - r * kappa))
        let ry = r - bw
        band.addCurve(to: CGPoint(x: 1206 - r, y: top + bw), control1: CGPoint(x: 1206, y: top + bw + ry - ry * kappa), control2: CGPoint(x: 1206 - r + r * kappa, y: top + bw))
        band.addLine(to: CGPoint(x: r, y: top + bw))
        band.addCurve(to: CGPoint(x: 0, y: top + r), control1: CGPoint(x: r - r * kappa, y: top + bw), control2: CGPoint(x: 0, y: top + bw + ry - ry * kappa))
        band.closeSubpath()
        ctx.fill(band, with: .color(Theme.Colors.highlight))
        // the sheet continues below the screen so the raised sheet has no bottom edge
        ctx.fill(Path(CGRect(x: 0, y: screenH - 1, width: 1206, height: riseRef * 2 + 1)), with: .color(Theme.color(0xDDEEF8)))

        drawImage(&ctx, "cafe", rect: CGRect(x: 48, y: 1646, width: 306, height: 390), radius: 24)
        text(&ctx, Theme.Copy.title, x: 385, base: 1760, spec: Theme.TypeSpec.title)
        Icons.stroke(&ctx, Icons.pin, in: CGRect(x: 386, y: 1808, width: 31, height: 36), color: Theme.Colors.meta, width: 1.6)
        text(&ctx, Theme.Copy.places, x: 435, base: 1840, spec: Theme.TypeSpec.meta)
        Icons.stroke(&ctx, Icons.bookmarkOutline, in: CGRect(x: 650, y: 1808, width: 29, height: 35), color: Theme.Colors.meta, width: 1.5)
        text(&ctx, Theme.Copy.saves, x: 699, base: 1840, spec: Theme.TypeSpec.meta)
        Icons.stroke(&ctx, Icons.bars, in: CGRect(x: 894, y: 1809, width: 37, height: 34), color: Theme.Colors.meta, width: 2)
        text(&ctx, Theme.Copy.views, x: 946, base: 1840, spec: Theme.TypeSpec.meta)

        shadowed(&ctx, color: Theme.Colors.shadow.opacity(0.2), radius: 16, y: 6) { layer in
            layer.fill(Path(roundedRect: CGRect(x: 386, y: 1893, width: 319, height: 91), cornerRadius: 45.5), with: .color(.white))
        }
        drawImage(&ctx, "avatar", rect: CGRect(x: 397, y: 1904, width: 71, height: 71), radius: 35.5)
        text(&ctx, Theme.Copy.handle, x: 485, base: 1952, spec: Theme.TypeSpec.handle)

        for i in 1..<4 {
            let rect = CGRect(x: Theme.tileXs[i], y: Theme.tileY, width: Theme.tileW, height: Theme.tileH)
            shadowed(&ctx, color: Theme.Colors.shadow.opacity(0.2), radius: 20, y: 6) { layer in
                layer.fill(Path(roundedRect: rect, cornerRadius: Theme.Radius.tile * 3), with: .color(Theme.Colors.tile))
            }
            var shade = ctx
            shade.clip(to: Path(roundedRect: rect, cornerRadius: Theme.Radius.tile * 3))
            shade.fill(Path(rect), with: .linearGradient(
                Gradient(stops: [.init(color: Theme.color(0xFDFFFF, opacity: 0), location: 0.82),
                                 .init(color: Theme.color(0xEDEDF5, opacity: 0.95), location: 1)]),
                startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)))
        }
        let labels = [Theme.Copy.save, Theme.Copy.share, Theme.Copy.similar]
        for i in 0..<3 {
            text(&ctx, labels[i], centerX: Theme.tileCentres[i + 1], base: Theme.tileBaselines[i + 1], spec: Theme.TypeSpec.tile)
        }
        Icons.stroke(&ctx, Icons.bookmarkOutline, in: CGRect(x: 439, y: 2112, width: 45, height: 51), color: .black, width: 1.65)
        Icons.stroke(&ctx, Icons.share, in: CGRect(x: 720, y: 2112, width: 51, height: 51), color: .black, width: 1.8)
        Icons.stroke(&ctx, Icons.gridPlus, in: CGRect(x: 1002, y: 2113, width: 53, height: 52), color: .black, width: 2.1)

        // list cards; the first card's checkbox is drawn by the dynamic layer
        let rows: [(String, String, String, Double, Double, Double)] = [
            (Theme.Copy.listTitle, Theme.Copy.listSub, "bar", 1, 0, 0),
            ("Lumen Coffee", "coffee & pastries", "cafe", 1.9, -0.38, -0.42),
            ("Linden Wick", "pourover coffee", "bar", 1.7, -0.1, -0.35),
        ]
        for (index, row) in rows.enumerated() {
            let y = 2343 + (Theme.listCardH + Theme.listGap) * Double(index)
            let dy = y - 2343
            shadowed(&ctx, color: Theme.Colors.shadow.opacity(0.3), radius: 18, y: 4) { layer in
                layer.fill(Path(roundedRect: CGRect(x: 26, y: y, width: 1156, height: Theme.listCardH), cornerRadius: Theme.Radius.card * 3), with: .color(.white))
            }
            let photo = CGRect(x: 64, y: 2381 + dy, width: 252, height: 252)
            drawZoomedImage(&ctx, row.2, rect: photo, zoom: row.3, ox: row.4, oy: row.5, radius: 36)
            text(&ctx, row.0, x: 357, base: 2500 + dy, spec: Theme.TypeSpec.listTitle)
            text(&ctx, row.1, x: 358, base: 2563 + dy, spec: Theme.TypeSpec.listSub)
            if index > 0 { drawCheckboxOutline(&ctx, rect: CGRect(x: 360, y: 2598 + dy, width: 29, height: 29)) }
            drawImage(&ctx, "avatar", rect: CGRect(x: 407, y: 2598 + dy, width: 48, height: 48), radius: 24)
        }
    }

    static func drawSheetDynamic(_ context: GraphicsContext, frame: Motion.Frame, reduced: Bool) {
        var sheet = context
        sheet.translateBy(x: 0, y: -riseRef * frame.sheet)

        var grabber = sheet
        let g = Theme.grabber
        scale(&grabber, around: CGPoint(x: g.midX, y: g.midY), x: Motion.lerp(1, Motion.GRAB.scale, frame.grab), y: 1)
        grabber.fill(Path(roundedRect: g, cornerRadius: 8), with: .color(mix(Theme.Colors.grabber, Theme.Colors.grabberTaken, frame.grab)))

        // tile 0: "0/59 you visited" (pressable; the digit rolls)
        var tile = sheet
        let rect = CGRect(x: Theme.tileXs[0], y: Theme.tileY, width: Theme.tileW, height: Theme.tileH)
        let tileScale = frame.pressTarget == SavedPlacesMapPlayback.Hit.tile.rawValue ? 1 - (1 - Motion.PRESS.tile) * frame.press : 1
        scale(&tile, around: CGPoint(x: rect.midX, y: rect.midY), x: tileScale, y: tileScale)
        let radius = Theme.Radius.tile * 3
        tile.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(Theme.Colors.tile0))
        tile.stroke(Path(roundedRect: rect.insetBy(dx: 1, dy: 1), cornerRadius: radius - 1), with: .color(Theme.Colors.tile0Border), lineWidth: 2)
        var strip = tile
        strip.clip(to: Path(Theme.rollBox))
        let numBase = 2155.0
        let spec = Theme.TypeSpec.visitedNum
        if reduced {
            text(&strip, Theme.Copy.visitedNum, x: 101, base: numBase, spec: spec, opacity: 1 - frame.roll)
            text(&strip, Theme.Copy.visitedNext, centerX: Theme.rollCentre, base: numBase, spec: spec, opacity: frame.roll)
        } else {
            strip.translateBy(x: 0, y: -frame.roll * Theme.rollRow)
            text(&strip, Theme.Copy.visitedNum, x: 101, base: numBase, spec: spec)
            text(&strip, Theme.Copy.visitedNext, centerX: Theme.rollCentre, base: numBase + Theme.rollRow, spec: spec)
        }
        text(&tile, Theme.Copy.visitedDen, x: 144, base: numBase, spec: Theme.TypeSpec.visitedDen)
        text(&tile, Theme.Copy.visited, centerX: Theme.tileCentres[0], base: Theme.tileBaselines[0], spec: Theme.TypeSpec.visited)

        // first card's checkbox
        let box = CGRect(x: 360, y: 2598, width: 29, height: 29)
        var cb = sheet
        let pressed = frame.pressTarget == SavedPlacesMapPlayback.Hit.checkbox.rawValue ? 1 - (1 - Motion.PRESS.checkbox) * frame.press : 1
        scale(&cb, around: CGPoint(x: box.midX, y: box.midY), x: pressed, y: pressed)
        drawCheckboxOutline(&cb, rect: box)
        guard frame.fill > 0.001 else { return }
        var fill = cb
        fill.opacity = frame.fill
        if !reduced { scale(&fill, around: CGPoint(x: box.midX, y: box.midY), x: Motion.lerp(0.9, 1, frame.fill), y: Motion.lerp(0.9, 1, frame.fill)) }
        fill.fill(Path(roundedRect: box, cornerRadius: 7), with: .color(Theme.Colors.check))
        let visible = min(1, frame.draw * Icons.tickLength / Icons.tickActualLength)
        if visible > 0.001 {
            let t = CGAffineTransform(translationX: box.minX, y: box.minY)
            fill.stroke(Icons.tick.applying(t).trimmedPath(from: 0, to: visible), with: .color(Theme.Colors.checkInk),
                        style: StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round))
        }
    }

    private static func drawCheckboxOutline(_ context: inout GraphicsContext, rect: CGRect) {
        context.stroke(Path(roundedRect: rect.insetBy(dx: 1.5, dy: 1.5), cornerRadius: 5.5), with: .color(Theme.Colors.checkbox), lineWidth: 3)
    }

    // MARK: Helpers

    private static func drawImage(_ context: inout GraphicsContext, _ name: String, rect: CGRect, radius: Double) {
        var image = context
        image.clip(to: Path(roundedRect: rect, cornerRadius: radius))
        image.draw(SavedPlacesMapAssets.image(name), in: rect)
    }

    private static func drawZoomedImage(_ context: inout GraphicsContext, _ name: String, rect: CGRect,
                                        zoom: Double, ox: Double, oy: Double, radius: Double) {
        var image = context
        image.clip(to: Path(roundedRect: rect, cornerRadius: radius))
        image.draw(SavedPlacesMapAssets.image(name), in: CGRect(x: rect.minX + ox * rect.width * zoom, y: rect.minY + oy * rect.height * zoom,
                                                                width: rect.width * zoom, height: rect.height * zoom))
    }

    static func text(_ context: inout GraphicsContext, _ value: String, x: Double? = nil, centerX: Double? = nil, base: Double,
                     size: Double, font: String, color: Color, tracking: Double = 0, opacity: Double = 1, monospacedDigits: Bool = false) {
        guard opacity > 0.001 else { return }
        var layer = context
        layer.opacity *= opacity
        var f = Font.custom(font, fixedSize: size)
        if monospacedDigits { f = f.monospacedDigit() }
        let resolved = layer.resolve(Text(value).font(f).tracking(tracking).foregroundColor(color))
        let bounds = resolved.measure(in: CGSize(width: 2_000, height: 300))
        let left = centerX.map { $0 - bounds.width / 2 } ?? x ?? 0
        layer.draw(resolved, at: CGPoint(x: left, y: base - resolved.firstBaseline(in: bounds)), anchor: .topLeading)
    }

    static func text(_ context: inout GraphicsContext, _ value: String, x: Double? = nil, centerX: Double? = nil, base: Double,
                     spec: Theme.TextSpec, opacity: Double = 1) {
        text(&context, value, x: x, centerX: centerX, base: base, size: spec.size, font: spec.font, color: spec.color, opacity: opacity)
    }

    private static func emojiText(_ context: inout GraphicsContext, _ value: String, center: CGPoint, pointSize: Double) {
        let resolved = context.resolve(Text(value).font(.system(size: pointSize * 3)))
        context.draw(resolved, at: center, anchor: .center)
    }

    private static func shadowed(_ context: inout GraphicsContext, color: Color, radius: Double, y: Double,
                                 drawing: (inout GraphicsContext) -> Void) {
        context.drawLayer { layer in
            layer.addFilter(.shadow(color: color, radius: radius, x: 0, y: y))
            drawing(&layer)
        }
    }

    private static func scale(_ context: inout GraphicsContext, around point: CGPoint, x: Double, y: Double) {
        context.translateBy(x: point.x, y: point.y)
        context.scaleBy(x: x, y: y)
        context.translateBy(x: -point.x, y: -point.y)
    }

    private static func mix(_ from: Color, _ to: Color, _ progress: Double) -> Color {
        let a = UIColor(from), b = UIColor(to)
        var ar = CGFloat.zero, ag = CGFloat.zero, ab = CGFloat.zero, aa = CGFloat.zero
        var br = CGFloat.zero, bg = CGFloat.zero, bb = CGFloat.zero, ba = CGFloat.zero
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        let p = CGFloat(Motion.clamp01(progress))
        return Color(red: ar + (br - ar) * p, green: ag + (bg - ag) * p, blue: ab + (bb - ab) * p, opacity: aa + (ba - aa) * p)
    }
}
