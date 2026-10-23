import SwiftUI
import UIKit

struct WorkoutStackListRenderer: View, Animatable {
    enum Layer {
        case list
        case detail
    }

    let layer: Layer
    var scene: WorkoutStackListModel.Scene
    let width: CGFloat
    let height: CGFloat
    let reduceMotion: Bool
    let pressedCategory: Int?
    var cardPress: Double
    var backPress: Double

    var animatableData: AnimatablePair<WorkoutStackListModel.Scene, AnimatablePair<Double, Double>> {
        get { AnimatablePair(scene, AnimatablePair(cardPress, backPress)) }
        set {
            scene = newValue.first
            cardPress = newValue.second.first
            backPress = newValue.second.second
        }
    }

    var body: some View {
        Canvas { context, _ in
            switch layer {
            case .list:
                drawList(in: &context)
            case .detail:
                drawDetail(in: &context)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    private func drawList(in context: inout GraphicsContext) {
        let look = listLook
        guard look.opacity > 0 else { return }
        var listContext = context
        listContext.opacity = look.opacity
        listContext.translateBy(x: width / 2, y: height / 2)
        listContext.scaleBy(x: look.scale, y: look.scale)
        listContext.translateBy(x: -width / 2, y: -height / 2)

        for index in WorkoutStackListModel.categories.indices.reversed() {
            drawDeckCard(index: index, in: &listContext)
        }
        drawProfile(in: &listContext)
        drawBell(x: width - WorkoutStackListModel.Header.side - WorkoutStackListModel.Header.icon, y: WorkoutStackListModel.Header.top, in: &listContext)
    }

    private func drawDeckCard(index: Int, in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        let category = model.categories[index]
        let pose = model.pose(Double(index) - scene.deck)
        guard pose.opacity > 0 else { return }
        if scene.open > 0, Int(scene.category.rounded()) == index { return }

        let pressedScale = pressedCategory == index ? CGFloat(cardPress) : 1
        let effectiveScale = pose.scale * pressedScale
        let cardWidth = model.Deck.width * effectiveScale
        let cardHeight = model.Deck.height * effectiveScale
        let x = (width - model.Deck.width) / 2 + model.deckShift + (model.Deck.width - cardWidth) / 2
        let rect = CGRect(x: x, y: pose.top, width: cardWidth, height: cardHeight)
        let radius = model.Deck.radius * effectiveScale
        var card = context
        card.opacity = pose.opacity
        card.clip(to: Path(roundedRect: rect, cornerRadius: radius, style: .continuous))
        card.fill(Path(rect), with: .color(category.color))

        let photoWidth = model.Deck.width * model.Deck.photoZoom * effectiveScale
        let photoHeight = photoWidth * 990 / 1173
        let photoRect = CGRect(
            x: x + ((model.Deck.width - model.Deck.width * model.Deck.photoZoom) / 2) * effectiveScale,
            y: pose.top + model.Deck.photoTop * effectiveScale,
            width: photoWidth,
            height: photoHeight
        )
        card.draw(Image(uiImage: WorkoutStackListAssets.image(category.image)), in: photoRect)
        if pose.fog > 0 {
            card.fill(Path(rect), with: .color(Color.black.opacity(pose.fog)))
        }

        let titleSize = model.Deck.titleSize * effectiveScale * pose.titleScale
        let title = Text(category.name)
            .font(WorkoutStackListAssets.font(WorkoutStackListAssets.futuraBold, size: titleSize))
            .foregroundStyle(model.Palette.white)
        card.draw(
            title,
            at: CGPoint(x: rect.midX, y: pose.top + pose.title * pressedScale - model.Deck.titleAscent * titleSize),
            anchor: .top
        )
    }

    private func drawProfile(in context: inout GraphicsContext) {
        let header = WorkoutStackListModel.Header.self
        let rect = CGRect(x: header.side, y: header.top, width: header.avatar, height: header.avatar)
        var avatar = context
        avatar.clip(to: Path(ellipseIn: rect))
        drawCover(WorkoutStackListAssets.image("WorkoutStackList-coach-2.jpg"), in: rect, context: &avatar)
    }

    private func drawDetail(in context: inout GraphicsContext) {
        let opacity = scene.open > 0 ? 1 - scene.leave : 0
        guard opacity > 0 else { return }
        var detail = context
        detail.opacity = opacity
        let categoryIndex = min(max(Int(scene.category.rounded()), 0), WorkoutStackListModel.categories.count - 1)
        let category = WorkoutStackListModel.categories[categoryIndex]
        drawHero(category: category, categoryIndex: categoryIndex, in: &detail)
        drawDetailTitle(category.name, in: &detail)

        let reveal = WorkoutStackListModel.segment(scene.open, 0.45...1)
        var calendar = detail
        calendar.opacity = reveal
        calendar.translateBy(
            x: 0,
            y: WorkoutStackListModel.Detail.collapsedShift * CGFloat(1 - scene.scroll) + (reduceMotion ? 0 : 12 * CGFloat(1 - reveal))
        )
        drawCalendar(in: &calendar)

        var cards = detail
        cards.opacity = reveal
        cards.translateBy(x: 0, y: reduceMotion ? 0 : 12 * CGFloat(1 - reveal))
        drawCards(in: &cards)
        drawDetailHeader(in: &detail)
    }

    private func drawHero(
        category: WorkoutStackListModel.Category,
        categoryIndex: Int,
        in context: inout GraphicsContext
    ) {
        let model = WorkoutStackListModel.self
        let source = model.pose(2)
        let sourceWidth = model.Deck.width * source.scale
        let sourceHeight = model.Deck.height * source.scale
        let open = reduceMotion ? 1 : scene.open
        let rect = CGRect(
            x: model.mix((width - sourceWidth) / 2, 0, open),
            y: model.mix(source.top, 0, open) - model.Detail.heroScrollAway * CGFloat(scene.scroll),
            width: model.mix(sourceWidth, width, open),
            height: model.mix(sourceHeight, model.Detail.heroHeight, open)
        )
        let radius = model.mix(model.Deck.radius * source.scale, 0, open)
        var hero = context
        hero.opacity = reduceMotion ? scene.open : 1
        hero.clip(to: Path(roundedRect: rect, cornerRadius: radius, style: .continuous))
        hero.fill(Path(rect), with: .color(model.Palette.background))
        drawCover(WorkoutStackListAssets.image(category.image), in: rect, context: &hero)

        let portraitOpacity = model.segment(scene.open, 0.2...0.8)
        if portraitOpacity > 0 {
            var portrait = hero
            portrait.opacity = portraitOpacity
            let image = categoryIndex == model.crossfitIndex
                ? WorkoutStackListAssets.image("WorkoutStackList-hero-crossfit.jpg")
                : WorkoutStackListAssets.image(category.image)
            drawCover(image, in: rect, context: &portrait)
            let gradient = Gradient(stops: [
                .init(color: model.Palette.background.opacity(0), location: 0.55),
                .init(color: model.Palette.background.opacity(0.85), location: 1),
            ])
            portrait.fill(
                Path(rect),
                with: .linearGradient(gradient, startPoint: CGPoint(x: rect.midX, y: rect.minY), endPoint: CGPoint(x: rect.midX, y: rect.maxY))
            )
        }
    }

    private func drawDetailTitle(_ name: String, in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        let source = model.pose(2)
        let open = reduceMotion ? 1 : scene.open
        let scale = model.mix((model.Deck.titleSize * source.scale) / model.Detail.titleSize, 1, open)
        let x = model.Detail.titleLeft + model.mix(width / 2 - 80, 0, open)
        let y = model.Detail.titleInkTop - model.Deck.titleAscent * model.Detail.titleSize
            + model.mix(source.top + 84.3 - model.Detail.titleInkTop, 0, open)
            - model.Detail.heroScrollAway * CGFloat(scene.scroll)
        let text = Text(name)
            .font(WorkoutStackListAssets.font(WorkoutStackListAssets.futuraBold, size: model.Detail.titleSize * scale))
            .tracking(model.Detail.titleTracking * scale)
            .foregroundStyle(model.Palette.white)
        var title = context
        title.opacity = reduceMotion ? scene.open : 1
        title.draw(text, at: CGPoint(x: x, y: y), anchor: .topLeading)
    }

    private func drawCalendar(in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        let detail = model.Detail.self
        let cell = (model.screenWidth - detail.weekSide * 2) / 7
        for (index, letter) in model.letters.enumerated() {
            let color = index == 2 ? model.Palette.white : model.Palette.dim
            drawText(
                letter,
                font: WorkoutStackListAssets.poppinsMedium,
                size: detail.letterSize,
                color: color,
                at: CGPoint(x: detail.weekSide + cell * CGFloat(index), y: detail.lettersTop - 10),
                width: cell,
                alignment: .center,
                in: &context
            )
        }

        let selectedX = detail.weekSide + cell * 2 + (cell - detail.selected) / 2 + cell * CGFloat(scene.day)
        let selectedRect = CGRect(
            x: selectedX,
            y: detail.numbersTop - detail.selected / 2,
            width: detail.selected,
            height: detail.selected
        )
        context.fill(Path(roundedRect: selectedRect, cornerRadius: detail.selectedRadius, style: .continuous), with: .color(model.Palette.red))

        for (rowIndex, week) in model.weeks.enumerated() {
            var row = context
            if rowIndex > 0 {
                row.opacity = scene.month
                row.translateBy(x: 0, y: -12 * CGFloat(1 - scene.month))
            }
            let rowTop = detail.numbersTop - 12 + detail.monthRow * CGFloat(rowIndex)
            for (column, day) in week.enumerated() where day != 0 {
                let color = model.dimDays.contains(day) ? model.Palette.dim : model.Palette.white
                drawText(
                    String(day),
                    font: WorkoutStackListAssets.poppinsSemibold,
                    size: detail.numberSize,
                    color: color,
                    at: CGPoint(x: detail.weekSide + cell * CGFloat(column), y: rowTop + 2),
                    width: cell,
                    alignment: .center,
                    in: &row
                )
                let markers = Array(model.dots[day] ?? "")
                let total = CGFloat(max(markers.count - 1, 0)) * 7
                for (index, marker) in markers.enumerated() {
                    let dotRect = CGRect(
                        x: detail.weekSide + cell * CGFloat(column) + cell / 2 - total / 2 - 2 + CGFloat(index) * 7,
                        y: rowTop + 22,
                        width: 4,
                        height: 4
                    )
                    row.fill(Path(ellipseIn: dotRect), with: .color(marker == "g" ? model.Palette.dotGreen : model.Palette.dotRed))
                }
            }
        }

        let grabberRect = CGRect(
            x: (width - detail.grabberWidth) / 2,
            y: detail.grabberTop + detail.monthRow * 3.3 * CGFloat(scene.month),
            width: detail.grabberWidth,
            height: 3
        )
        context.fill(Path(roundedRect: grabberRect, cornerRadius: 1.5), with: .color(model.Palette.grabber))
    }

    private func drawCards(in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        let labelY = model.Detail.labelTop + model.Detail.listScroll * CGFloat(1 - scene.scroll)
        if scene.month < 1 {
            var label = context
            label.opacity = 1 - scene.month
            drawText(
                "Live Class Schedule",
                font: WorkoutStackListAssets.poppinsSemibold,
                size: 15,
                color: model.Palette.label,
                at: CGPoint(x: model.Detail.listSide, y: labelY),
                in: &label
            )
        }
        drawCardSet(model.dayEight, set: 0, in: &context)
        drawCardSet(model.dayNine, set: 1, in: &context)
    }

    private func drawCardSet(
        _ cards: [WorkoutStackListModel.ClassCard],
        set: Int,
        in context: inout GraphicsContext
    ) {
        for (index, card) in cards.enumerated() {
            let day = scene.day
            let visible = set == 0 ? day < 1 : day > 0
            guard visible else { continue }
            let horizontal = set == 0 ? -width * CGFloat(day) : width * CGFloat(1 - day)
            let listScale = index == 0
                ? WorkoutStackListModel.mix(1, WorkoutStackListModel.Detail.liveScale, scene.scroll)
                : 1
            let listTop = index == 0
                ? WorkoutStackListModel.mix(WorkoutStackListModel.Detail.liveRest, WorkoutStackListModel.Detail.listTops[0], scene.scroll)
                : WorkoutStackListModel.Detail.listTops[index] + WorkoutStackListModel.Detail.listScroll * CGFloat(1 - scene.scroll)
            let top = WorkoutStackListModel.mix(listTop, WorkoutStackListModel.Detail.stackTops[index], scene.month)
            let scale = WorkoutStackListModel.mix(listScale, WorkoutStackListModel.Detail.stackScales[index], scene.month)
            let height = WorkoutStackListModel.Detail.heights[index]
            var placed = context
            placed.translateBy(
                x: WorkoutStackListModel.Detail.listSide + horizontal,
                y: top + height * (scale - 1) / 2
            )
            placed.scaleBy(x: scale, y: scale)
            drawClassCard(card, height: height, in: &placed)
        }
    }

    private func drawClassCard(
        _ card: WorkoutStackListModel.ClassCard,
        height: CGFloat,
        in context: inout GraphicsContext
    ) {
        let model = WorkoutStackListModel.self
        let rect = CGRect(x: 0, y: 0, width: model.Detail.cardWidth, height: height)
        var cardContext = context
        cardContext.clip(to: Path(roundedRect: rect, cornerRadius: model.Detail.cardRadius, style: .continuous))
        let gradient = Gradient(colors: [
            model.Palette.color(card.colors.0, alpha: 0.902),
            model.Palette.color(card.colors.1, alpha: 0.902),
        ])
        cardContext.fill(Path(rect), with: .linearGradient(gradient, startPoint: .zero, endPoint: CGPoint(x: rect.maxX, y: rect.maxY)))
        if card.kind == .live, card.id == "live8" {
            drawLiveArt(height: height, in: &cardContext)
        }
        if let imageName = card.image {
            let size = height + 10
            cardContext.draw(
                Image(uiImage: WorkoutStackListAssets.image(imageName)),
                in: CGRect(x: model.Detail.cardWidth + 6 - size, y: -10, width: size, height: size)
            )
        }

        switch card.kind {
        case .live:
            drawLiveCardContent(card, height: height, in: &cardContext)
        case .quiz:
            drawQuizCardContent(card, height: height, in: &cardContext)
        case .session:
            drawSessionCardContent(card, in: &cardContext)
        }
    }

    private func drawLiveArt(height: CGFloat, in context: inout GraphicsContext) {
        let width = WorkoutStackListModel.Detail.cardWidth
        let sky = Gradient(stops: [
            .init(color: WorkoutStackListModel.Palette.color(0xB86CD8), location: 0),
            .init(color: WorkoutStackListModel.Palette.color(0x8F94FE), location: 0.45),
            .init(color: WorkoutStackListModel.Palette.color(0x6CD2FC), location: 1),
        ])
        context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height)), with: .linearGradient(sky, startPoint: .zero, endPoint: CGPoint(x: width, y: height * 0.3)))
        let scaleY = height / 180
        var mat = Path()
        mat.move(to: CGPoint(x: 150, y: 0))
        mat.addLine(to: CGPoint(x: width - 50, y: 0))
        mat.addLine(to: CGPoint(x: width - 70, y: 60 * scaleY))
        mat.addLine(to: CGPoint(x: width - 105, y: 110 * scaleY))
        mat.addLine(to: CGPoint(x: 190, y: 80 * scaleY))
        mat.closeSubpath()
        context.fill(mat, with: .linearGradient(Gradient(colors: [WorkoutStackListModel.Palette.color(0xF22589), WorkoutStackListModel.Palette.color(0x8C0242)]), startPoint: .zero, endPoint: CGPoint(x: width, y: height)))
        var leaf = Path()
        leaf.move(to: CGPoint(x: width - 92, y: height))
        leaf.addQuadCurve(to: CGPoint(x: width, y: 120 * scaleY), control: CGPoint(x: width - 70, y: 110 * scaleY))
        leaf.addLine(to: CGPoint(x: width, y: height))
        leaf.closeSubpath()
        context.fill(leaf, with: .color(WorkoutStackListModel.Palette.color(0x0A5A2A)))
        var vein = Path()
        vein.move(to: CGPoint(x: width - 60, y: height))
        vein.addQuadCurve(to: CGPoint(x: width, y: 150 * scaleY), control: CGPoint(x: width - 30, y: 140 * scaleY))
        context.stroke(vein, with: .color(WorkoutStackListModel.Palette.color(0x1E7A3E)), lineWidth: 3)
    }

    private func drawLiveCardContent(
        _ card: WorkoutStackListModel.ClassCard,
        height: CGFloat,
        in context: inout GraphicsContext
    ) {
        let model = WorkoutStackListModel.self
        let tagWidth = measuredWidth("LIVE", font: WorkoutStackListAssets.futuraBold, size: 12, tracking: 0.4) + 12
        let hint = "Starting now, tap to join!"
        let hintWidth = measuredWidth(hint, font: WorkoutStackListAssets.futuraMedium, size: 12)
        let rowHeight = ceil(max(WorkoutStackListAssets.uiFont(WorkoutStackListAssets.futuraBold, size: 12).lineHeight + 4, WorkoutStackListAssets.uiFont(WorkoutStackListAssets.futuraMedium, size: 12).lineHeight))
        let rowWidth = tagWidth + 8 + hintWidth + 10
        context.fill(Path(roundedRect: CGRect(x: 12, y: 12, width: rowWidth, height: rowHeight), cornerRadius: 6), with: .color(model.Palette.white.opacity(0.28)))
        context.fill(Path(roundedRect: CGRect(x: 12, y: 12, width: tagWidth, height: rowHeight), cornerRadius: 4), with: .color(model.Palette.white))
        drawText("LIVE", font: WorkoutStackListAssets.futuraBold, size: 12, color: model.Palette.tag, at: CGPoint(x: 18, y: 14), tracking: 0.4, in: &context)
        drawText(hint, font: WorkoutStackListAssets.futuraMedium, size: 12, color: model.Palette.white, at: CGPoint(x: 12 + tagWidth + 8, y: 14), in: &context)
        drawMultiline(card.title, font: WorkoutStackListAssets.futuraBold, size: 24, lineHeight: 29, color: model.Palette.white, at: CGPoint(x: 16, y: 56), tracking: 0.4, in: &context)
        if let coach = card.coach { drawCoach(coach, height: height, in: &context) }
    }

    private func drawQuizCardContent(
        _ card: WorkoutStackListModel.ClassCard,
        height: CGFloat,
        in context: inout GraphicsContext
    ) {
        let model = WorkoutStackListModel.self
        let firstWidth = drawTag(card.time ?? "", x: 16, filled: true, in: &context)
        _ = drawTag(card.length ?? "", x: 16 + firstWidth + 6, filled: false, in: &context)
        drawMultiline(card.title, font: WorkoutStackListAssets.futuraBold, size: 22, lineHeight: 28, color: model.Palette.white, at: CGPoint(x: 16, y: 52), tracking: 0.4, in: &context)
        if let coach = card.coach { drawCoach(coach, height: height, in: &context) }
    }

    private func drawTag(_ text: String, x: CGFloat, filled: Bool, in context: inout GraphicsContext) -> CGFloat {
        let width = measuredWidth(text, font: WorkoutStackListAssets.futuraMedium, size: 13) + 16
        let rect = CGRect(x: x, y: 16, width: width, height: 28)
        if filled {
            context.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(WorkoutStackListModel.Palette.tag))
        } else {
            context.stroke(Path(roundedRect: rect, cornerRadius: 6), with: .color(WorkoutStackListModel.Palette.white.opacity(0.6)), lineWidth: 1)
        }
        drawText(text, font: WorkoutStackListAssets.futuraMedium, size: 13, color: WorkoutStackListModel.Palette.white, at: CGPoint(x: rect.minX + 8, y: 21), in: &context)
        return width
    }

    private func drawCoach(_ coach: WorkoutStackListModel.Coach, height: CGFloat, in context: inout GraphicsContext) {
        let avatar = CGRect(x: 16, y: height - 52, width: 34, height: 34)
        var image = context
        image.clip(to: Path(ellipseIn: avatar))
        drawCover(WorkoutStackListAssets.image(coach.image), in: avatar, context: &image)
        context.stroke(Path(ellipseIn: avatar), with: .color(WorkoutStackListModel.Palette.white.opacity(0.7)), lineWidth: 1.5)
        drawText(coach.name, font: WorkoutStackListAssets.futuraMedium, size: 14, color: WorkoutStackListModel.Palette.white, at: CGPoint(x: 60, y: height - 43), in: &context)
    }

    private func drawSessionCardContent(_ card: WorkoutStackListModel.ClassCard, in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        drawMultiline(card.title, font: WorkoutStackListAssets.futuraBold, size: 30, lineHeight: 38, color: model.Palette.white, at: CGPoint(x: 16, y: 26), tracking: 0.5, in: &context)
        let selectWidth = measuredWidth("Select Session", font: WorkoutStackListAssets.futuraBold, size: 13) + 48
        let select = CGRect(x: 18, y: 128, width: selectWidth, height: 36)
        context.fill(Path(roundedRect: select, cornerRadius: 18), with: .color(model.Palette.white))
        drawText("Select Session", font: WorkoutStackListAssets.futuraBold, size: 13, color: model.Palette.color(card.colors.0), at: CGPoint(x: 42, y: 138), in: &context)
        let center = CGPoint(x: 29, y: 191)
        context.stroke(Path(ellipseIn: CGRect(x: center.x - 9, y: center.y - 9, width: 18, height: 18)), with: .color(model.Palette.white), lineWidth: 1.8)
        var hands = Path()
        hands.move(to: CGPoint(x: center.x, y: center.y - 4.5))
        hands.addLine(to: center)
        hands.addLine(to: CGPoint(x: center.x + 3, y: center.y + 2))
        context.stroke(hands, with: .color(model.Palette.white), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        drawText("Time", font: WorkoutStackListAssets.futuraMedium, size: 12, color: model.Palette.white, at: CGPoint(x: 50, y: 180), in: &context)
        drawText(card.length ?? "", font: WorkoutStackListAssets.futuraBold, size: 12, color: model.Palette.white, at: CGPoint(x: 50, y: 195), in: &context)
        let chipGap: CGFloat = 6
        let chipWidth = (model.Detail.cardWidth - 28 - chipGap * 3) / 4
        for (index, value) in model.sessionTimes.enumerated() {
            let rect = CGRect(x: 14 + CGFloat(index) * (chipWidth + chipGap), y: 225, width: chipWidth, height: 36)
            context.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(model.Palette.white.opacity(0.22)))
            drawText(value, font: WorkoutStackListAssets.futuraMedium, size: 13, color: model.Palette.white, at: CGPoint(x: rect.minX, y: rect.minY + 10), width: rect.width, alignment: .center, in: &context)
        }
    }

    private func drawDetailHeader(in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        var header = context
        header.opacity = model.segment(scene.open, 0.5...1)
        var backContext = header
        let backCenter = CGPoint(x: model.Header.side + 12, y: model.Header.top + 12)
        backContext.translateBy(x: backCenter.x, y: backCenter.y)
        backContext.scaleBy(x: CGFloat(backPress), y: CGFloat(backPress))
        backContext.translateBy(x: -backCenter.x, y: -backCenter.y)
        var back = Path()
        back.move(to: CGPoint(x: model.Header.side + 15, y: model.Header.top + 5))
        back.addLine(to: CGPoint(x: model.Header.side + 8, y: model.Header.top + 12))
        back.addLine(to: CGPoint(x: model.Header.side + 15, y: model.Header.top + 19))
        backContext.stroke(back, with: .color(model.Palette.white), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        if scene.month > 0 {
            var month = header
            month.opacity = scene.month
            drawText("April", font: WorkoutStackListAssets.poppinsSemibold, size: 20, color: model.Palette.white, at: CGPoint(x: 0, y: model.Header.top), width: width, alignment: .center, in: &month)
        }
        drawBell(x: width - model.Header.side - model.Header.icon, y: model.Header.top, in: &header)
    }

    private func drawBell(x: CGFloat, y: CGFloat, in context: inout GraphicsContext) {
        let model = WorkoutStackListModel.self
        var bell = Path()
        bell.move(to: CGPoint(x: x + 12, y: y + 3))
        bell.addCurve(to: CGPoint(x: x + 18, y: y + 9), control1: CGPoint(x: x + 15.5, y: y + 3), control2: CGPoint(x: x + 18, y: y + 5.5))
        bell.addLine(to: CGPoint(x: x + 18, y: y + 13))
        bell.addLine(to: CGPoint(x: x + 20, y: y + 16))
        bell.addLine(to: CGPoint(x: x + 4, y: y + 16))
        bell.addLine(to: CGPoint(x: x + 6, y: y + 13))
        bell.addLine(to: CGPoint(x: x + 6, y: y + 9))
        bell.addCurve(to: CGPoint(x: x + 12, y: y + 3), control1: CGPoint(x: x + 6, y: y + 5.5), control2: CGPoint(x: x + 8.5, y: y + 3))
        bell.closeSubpath()
        context.fill(bell, with: .color(model.Palette.white))
        context.fill(Path(ellipseIn: CGRect(x: x + 9.5, y: y + 19, width: 5, height: 2.5)), with: .color(model.Palette.white))
        context.fill(Path(ellipseIn: CGRect(x: x + 14.6, y: y + 1.6, width: 6.8, height: 6.8)), with: .color(model.Palette.red))
    }

    private func drawCover(_ image: UIImage, in rect: CGRect, context: inout GraphicsContext) {
        guard image.size.width > 0, image.size.height > 0, rect.width > 0, rect.height > 0 else { return }
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let target = CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2, width: size.width, height: size.height)
        var clipped = context
        clipped.clip(to: Path(rect))
        clipped.draw(Image(uiImage: image), in: target)
    }

    private func drawMultiline(
        _ value: String,
        font: String,
        size: CGFloat,
        lineHeight: CGFloat,
        color: Color,
        at point: CGPoint,
        tracking: CGFloat = 0,
        in context: inout GraphicsContext
    ) {
        for (index, line) in value.split(separator: "\n", omittingEmptySubsequences: false).enumerated() {
            drawText(String(line), font: font, size: size, color: color, at: CGPoint(x: point.x, y: point.y + CGFloat(index) * lineHeight), tracking: tracking, in: &context)
        }
    }

    private func drawText(
        _ value: String,
        font: String,
        size: CGFloat,
        color: Color,
        at point: CGPoint,
        width: CGFloat? = nil,
        alignment: TextAlignment = .leading,
        tracking: CGFloat = 0,
        in context: inout GraphicsContext
    ) {
        let text = Text(value)
            .font(WorkoutStackListAssets.font(font, size: size))
            .tracking(tracking)
            .foregroundStyle(color)
        let x: CGFloat
        let anchor: UnitPoint
        switch alignment {
        case .center:
            x = point.x + (width ?? 0) / 2
            anchor = .top
        case .trailing:
            x = point.x + (width ?? 0)
            anchor = .topTrailing
        default:
            x = point.x
            anchor = .topLeading
        }
        context.draw(text, at: CGPoint(x: x, y: point.y), anchor: anchor)
    }

    private func measuredWidth(_ value: String, font: String, size: CGFloat, tracking: CGFloat = 0) -> CGFloat {
        let base = (value as NSString).size(withAttributes: [.font: WorkoutStackListAssets.uiFont(font, size: size)]).width
        return base + CGFloat(max(value.count - 1, 0)) * tracking
    }

    private var listLook: (opacity: Double, scale: CGFloat) {
        if scene.leave > 0 {
            return (scene.leave, WorkoutStackListModel.mix(WorkoutStackListModel.Motion.backZoom, 1, scene.leave))
        }
        return (1 - WorkoutStackListModel.segment(scene.open, 0.25...0.75), 1)
    }
}

extension WorkoutStackListRenderer {
    struct TabBar: View, Animatable {
        var scene: WorkoutStackListModel.Scene
        let width: CGFloat
        let height: CGFloat
        let reduceTransparency: Bool

        var animatableData: WorkoutStackListModel.Scene {
            get { scene }
            set { scene = newValue }
        }

        var body: some View {
            let look = scene.leave > 0
                ? (scene.leave, WorkoutStackListModel.mix(WorkoutStackListModel.Motion.backZoom, 1, scene.leave))
                : (1 - WorkoutStackListModel.segment(scene.open, 0.25...0.75), 1)
            ZStack(alignment: .topLeading) {
                ZStack {
                    RoundedRectangle(cornerRadius: WorkoutStackListModel.TabBar.radius, style: .continuous)
                        .fill(reduceTransparency ? AnyShapeStyle(WorkoutStackListModel.Palette.tabSolid) : AnyShapeStyle(.ultraThinMaterial))
                    RoundedRectangle(cornerRadius: WorkoutStackListModel.TabBar.radius, style: .continuous)
                        .fill(WorkoutStackListModel.Palette.tabWash)
                    Canvas { context, _ in drawIcons(in: &context) }
                }
                .frame(width: WorkoutStackListModel.TabBar.width, height: WorkoutStackListModel.TabBar.height)
                .clipShape(RoundedRectangle(cornerRadius: WorkoutStackListModel.TabBar.radius, style: .continuous))
                .offset(x: WorkoutStackListModel.TabBar.left, y: WorkoutStackListModel.TabBar.top)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .scaleEffect(look.1)
            .opacity(look.0)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }

        private func drawIcons(in context: inout GraphicsContext) {
            let tab = WorkoutStackListModel.TabBar.self
            let ink = WorkoutStackListModel.Palette.tabInk
            let localCenters = tab.centers.map { $0 - tab.left }
            let centerY = tab.iconY - tab.top
            var home = Path()
            home.move(to: CGPoint(x: localCenters[0], y: centerY - 10))
            home.addLine(to: CGPoint(x: localCenters[0] - 10, y: centerY - 1))
            home.addLine(to: CGPoint(x: localCenters[0] - 7, y: centerY - 1))
            home.addLine(to: CGPoint(x: localCenters[0] - 7, y: centerY + 10))
            home.addLine(to: CGPoint(x: localCenters[0] + 7, y: centerY + 10))
            home.addLine(to: CGPoint(x: localCenters[0] + 7, y: centerY - 1))
            home.addLine(to: CGPoint(x: localCenters[0] + 10, y: centerY - 1))
            home.closeSubpath()
            context.fill(home, with: .color(ink))
            context.fill(Path(ellipseIn: CGRect(x: localCenters[0] - 2.4, y: tab.dotY - tab.top - 2.4, width: 4.8, height: 4.8)), with: .color(ink))

            let calendar = CGRect(x: localCenters[1] - 9.5, y: centerY - 9.5, width: 19, height: 19)
            context.stroke(Path(roundedRect: calendar, cornerRadius: 2), with: .color(ink), lineWidth: 2.2)
            context.fill(Path(CGRect(x: calendar.minX, y: calendar.minY + 4, width: calendar.width, height: 4.6)), with: .color(ink))
            context.fill(Path(roundedRect: CGRect(x: calendar.minX + 3.4, y: calendar.minY + 11, width: 12, height: 2.4), cornerRadius: 1), with: .color(ink))
            context.fill(Path(roundedRect: CGRect(x: calendar.minX + 3.4, y: calendar.minY + 15, width: 8, height: 2.4), cornerRadius: 1), with: .color(ink))

            let plus = CGRect(x: localCenters[2] - tab.plus / 2, y: centerY - tab.plus / 2, width: tab.plus, height: tab.plus)
            var plusContext = context
            plusContext.addFilter(.shadow(color: .black.opacity(0.16), radius: 8, x: 0, y: 3))
            plusContext.fill(Path(ellipseIn: plus), with: .color(.white))
            context.stroke(Path { path in
                path.move(to: CGPoint(x: plus.midX, y: plus.midY - 7.5))
                path.addLine(to: CGPoint(x: plus.midX, y: plus.midY + 7.5))
                path.move(to: CGPoint(x: plus.midX - 7.5, y: plus.midY))
                path.addLine(to: CGPoint(x: plus.midX + 7.5, y: plus.midY))
            }, with: .color(WorkoutStackListModel.Palette.tabPlusInk), style: StrokeStyle(lineWidth: 2.6, lineCap: .round))

            var bolt = Path()
            bolt.move(to: CGPoint(x: localCenters[3] + 3, y: centerY - 11))
            bolt.addLine(to: CGPoint(x: localCenters[3] - 7, y: centerY + 2))
            bolt.addLine(to: CGPoint(x: localCenters[3] - 1, y: centerY + 2))
            bolt.addLine(to: CGPoint(x: localCenters[3] - 3, y: centerY + 11))
            bolt.addLine(to: CGPoint(x: localCenters[3] + 7, y: centerY - 1))
            bolt.addLine(to: CGPoint(x: localCenters[3] + 1, y: centerY - 1))
            bolt.closeSubpath()
            context.fill(bolt, with: .color(ink))

            let hub = CGPoint(x: localCenters[4], y: centerY)
            let nodes = [CGPoint(x: hub.x - 8.4, y: hub.y - 8.4), CGPoint(x: hub.x + 8.4, y: hub.y - 8.4), CGPoint(x: hub.x - 9.8, y: hub.y + 3.4), CGPoint(x: hub.x + 5.6, y: hub.y + 8.6)]
            for node in nodes {
                var line = Path()
                line.move(to: hub)
                line.addLine(to: node)
                context.stroke(line, with: .color(ink), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                context.fill(Path(ellipseIn: CGRect(x: node.x - 2.8, y: node.y - 2.8, width: 5.6, height: 5.6)), with: .color(ink))
            }
            context.fill(Path(ellipseIn: CGRect(x: hub.x - 4.6, y: hub.y - 4.6, width: 9.2, height: 9.2)), with: .color(ink))
            context.fill(Path(ellipseIn: CGRect(x: hub.x - 1.8, y: hub.y - 1.8, width: 3.6, height: 3.6)), with: .color(WorkoutStackListModel.Palette.tabWash))
        }
    }
}
