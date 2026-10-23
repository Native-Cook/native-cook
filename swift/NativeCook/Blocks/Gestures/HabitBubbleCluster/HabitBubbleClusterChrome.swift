import SwiftUI

struct HabitBubbleClusterChrome: View {
    let model: HabitBubbleClusterModel

    var body: some View {
        ZStack(alignment: .topLeading) {
            home.opacity(1 - model.detailProgress).allowsHitTesting(model.detail < 0)
            detail.opacity(model.detailProgress).offset(y: HabitBubbleClusterTokens.render.detailTravel * (1 - model.detailProgress)).allowsHitTesting(model.detail >= 0)
        }
    }

    private var home: some View {
        ZStack(alignment: .topLeading) {
            header
            dateBlock
            discAndDot
            cornerButtons
        }
    }

    private var header: some View {
        HStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(Array(HabitBubbleClusterTokens.copy.tabs.enumerated()), id: \.offset) { index, label in
                    Button { model.selectTab(index) } label: {
                        Text(label)
                            .font(.system(size: HabitBubbleClusterTokens.type.segmentFontSize, weight: HabitBubbleClusterTokens.weight.segment))
                            .foregroundStyle(HabitBubbleClusterColor(index == model.tab ? HabitBubbleClusterTokens.Color.controlInkSelected : HabitBubbleClusterTokens.Color.inkSoft))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(index == model.tab ? HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.controlSelected) : .clear)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(label) habits")
                    .accessibilityAddTraits(index == model.tab ? .isSelected : [])
                }
            }
            .padding(HabitBubbleClusterTokens.space.xs)
            .frame(width: HabitBubbleClusterTokens.layout.segmentWidth, height: HabitBubbleClusterTokens.layout.control)
            .background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.control))
            .clipShape(Capsule())
            Spacer()
            Image(systemName: "location.fill")
                .font(.system(size: 17))
                .foregroundStyle(.white)
                .frame(width: HabitBubbleClusterTokens.layout.iconButton, height: HabitBubbleClusterTokens.layout.iconButton)
                .background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.control))
                .clipShape(Circle())
                .accessibilityHidden(true)
            HabitBubbleClusterAvatar().padding(.leading, HabitBubbleClusterTokens.space.x2)
        }
        .frame(width: model.size.width - HabitBubbleClusterTokens.layout.gutter * 2 + 3)
        .offset(x: HabitBubbleClusterTokens.layout.gutter, y: model.safeTop + HabitBubbleClusterTokens.layout.headerTop)
    }

    private var dateBlock: some View {
        VStack(spacing: HabitBubbleClusterTokens.space.s) {
            Text(model.tab == 1 ? HabitBubbleClusterTokens.copy.personalDate : HabitBubbleClusterTokens.copy.date)
                .font(.system(size: model.tab == 1 ? HabitBubbleClusterTokens.type.personalDateFontSize : HabitBubbleClusterTokens.type.dateFontSize, weight: model.tab == 1 ? HabitBubbleClusterTokens.weight.personalDate : HabitBubbleClusterTokens.weight.date))
                .foregroundStyle(.white)
            Text(HabitBubbleClusterTokens.copy.now)
                .font(.system(size: model.tab == 1 ? HabitBubbleClusterTokens.type.personalNowFontSize : HabitBubbleClusterTokens.type.nowFontSize, weight: model.tab == 1 ? HabitBubbleClusterTokens.weight.personalNow : HabitBubbleClusterTokens.weight.now))
                .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft))
        }
        .frame(width: model.size.width)
        .offset(y: model.tab == 1 ? model.size.height * HabitBubbleClusterTokens.layout.personalDateY : HabitBubbleClusterTokens.layout.dateY)
        .allowsHitTesting(false)
    }

    private var discAndDot: some View {
        ZStack(alignment: .topLeading) {
            Circle()
                .fill(HabitBubbleClusterColor(model.tab == 1 ? HabitBubbleClusterTokens.Color.discDim : HabitBubbleClusterTokens.Color.disc))
                .frame(width: HabitBubbleClusterTokens.layout.disc, height: HabitBubbleClusterTokens.layout.disc)
                .position(x: model.size.width / 2, y: HabitBubbleClusterTokens.layout.discY)
            Circle()
                .fill(.white)
                .frame(width: HabitBubbleClusterTokens.layout.dot, height: HabitBubbleClusterTokens.layout.dot)
                .position(x: model.tab == 1 ? model.size.width * HabitBubbleClusterTokens.layout.personalDotX : model.size.width / 2 + HabitBubbleClusterTokens.layout.dotOffset, y: model.tab == 1 ? model.size.height * HabitBubbleClusterTokens.layout.personalDotY : HabitBubbleClusterTokens.layout.discY)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(model.doneCount) of \(model.totalCount) habits done today")
    }

    private var cornerButtons: some View {
        HStack {
            Image(systemName: "ellipsis").frame(width: HabitBubbleClusterTokens.layout.corner, height: HabitBubbleClusterTokens.layout.corner).background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.control)).clipShape(Circle())
            Spacer()
            Image(systemName: "music.note").frame(width: HabitBubbleClusterTokens.layout.corner, height: HabitBubbleClusterTokens.layout.corner).background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.control)).clipShape(Circle())
        }
        .font(.system(size: 20))
        .foregroundStyle(.white)
        .padding(.horizontal, HabitBubbleClusterTokens.layout.cornerInset)
        .frame(width: model.size.width)
        .offset(y: HabitBubbleClusterTokens.layout.cornerY - HabitBubbleClusterTokens.layout.corner / 2)
        .accessibilityHidden(true)
    }

    private var detail: some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Button { model.closeDetail() } label: {
                    Image(systemName: "chevron.left").font(.system(size: 20, weight: .medium)).frame(width: HabitBubbleClusterTokens.layout.iconButton, height: HabitBubbleClusterTokens.layout.iconButton).background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.control)).clipShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                Spacer()
                VStack(spacing: 1) {
                    Text(HabitBubbleClusterTokens.copy.category).font(.system(size: HabitBubbleClusterTokens.type.categoryFontSize, weight: HabitBubbleClusterTokens.weight.category)).foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft))
                    Text(detailTitle).font(.system(size: HabitBubbleClusterTokens.type.categoryTitleFontSize, weight: HabitBubbleClusterTokens.weight.categoryTitle)).foregroundStyle(.white).accessibilityAddTraits(.isHeader)
                }
                Spacer()
                Image(systemName: "square.and.arrow.up").font(.system(size: 18)).frame(width: HabitBubbleClusterTokens.layout.iconButton, height: HabitBubbleClusterTokens.layout.iconButton).background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.control)).clipShape(Circle()).accessibilityHidden(true)
            }
            .foregroundStyle(.white)
            .frame(width: model.size.width - HabitBubbleClusterTokens.layout.gutter * 2)
            .offset(x: HabitBubbleClusterTokens.layout.gutter, y: model.safeTop + HabitBubbleClusterTokens.layout.headerTop)

            HStack {
                Text(HabitBubbleClusterTokens.copy.balance).font(.system(size: HabitBubbleClusterTokens.type.balanceFontSize, weight: HabitBubbleClusterTokens.weight.balance)).foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft))
                Spacer()
                Text(HabitBubbleClusterTokens.copy.good).font(.system(size: HabitBubbleClusterTokens.type.goodFontSize, weight: HabitBubbleClusterTokens.weight.good)).foregroundStyle(.white).padding(.horizontal, HabitBubbleClusterTokens.space.l).frame(height: HabitBubbleClusterTokens.view.goodHeight).background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.good)).clipShape(Capsule())
            }
            .padding(.horizontal, HabitBubbleClusterTokens.space.xl)
            .frame(width: model.size.width - HabitBubbleClusterTokens.layout.cornerInset * 2, height: HabitBubbleClusterTokens.layout.rowHeight)
            .background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.card))
            .clipShape(RoundedRectangle(cornerRadius: HabitBubbleClusterTokens.view.balanceCorner))
            .offset(x: HabitBubbleClusterTokens.layout.cornerInset, y: model.size.height * HabitBubbleClusterTokens.layout.detailBalanceY)

            VStack(alignment: .leading, spacing: HabitBubbleClusterTokens.space.x3) {
                Text(HabitBubbleClusterTokens.copy.description).font(.system(size: HabitBubbleClusterTokens.type.sectionTitleFontSize, weight: HabitBubbleClusterTokens.weight.sectionTitle)).foregroundStyle(.white)
                Rectangle().fill(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.hairline)).frame(height: HabitBubbleClusterTokens.view.hairline).padding(.top, -HabitBubbleClusterTokens.space.s)
                ForEach(HabitBubbleClusterTokens.copy.body, id: \.self) { paragraph in
                    Text(paragraph).font(.system(size: HabitBubbleClusterTokens.type.bodyFontSize, weight: HabitBubbleClusterTokens.weight.body)).foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft)).lineSpacing(HabitBubbleClusterTokens.type.bodyLineHeight - HabitBubbleClusterTokens.type.bodyFontSize)
                }
            }
            .padding(HabitBubbleClusterTokens.space.x3)
            .padding(.top, HabitBubbleClusterTokens.space.cardTop - HabitBubbleClusterTokens.space.x3)
            .frame(width: model.size.width - HabitBubbleClusterTokens.layout.cornerInset * 2, height: max(0, model.size.height * (1 - HabitBubbleClusterTokens.layout.detailCardY)), alignment: .topLeading)
            .background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.card))
            .clipShape(.rect(topLeadingRadius: HabitBubbleClusterTokens.view.cardCorner, topTrailingRadius: HabitBubbleClusterTokens.view.cardCorner))
            .offset(x: HabitBubbleClusterTokens.layout.cornerInset, y: model.size.height * HabitBubbleClusterTokens.layout.detailCardY)
        }
    }

    private var detailTitle: String {
        guard HabitBubbleClusterSimulation.entities.indices.contains(model.detail) else { return "" }
        let entity = HabitBubbleClusterSimulation.entities[model.detail]
        return HabitBubbleClusterTokens.category[entity.id] ?? entity.name
    }
}

struct HabitBubbleClusterAvatar: View {
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle().fill(LinearGradient(colors: [Color(red: 0.62, green: 0.49, blue: 0.42), Color(red: 0.24, green: 0.18, blue: 0.17)], startPoint: .topLeading, endPoint: .bottomTrailing)).overlay { Image(systemName: "person.fill").font(.system(size: 20)).foregroundStyle(.white.opacity(0.82)) }.frame(width: HabitBubbleClusterTokens.layout.iconButton, height: HabitBubbleClusterTokens.layout.iconButton)
            Image(systemName: "star.fill").font(.system(size: 8)).foregroundStyle(.white).frame(width: HabitBubbleClusterTokens.view.badge, height: HabitBubbleClusterTokens.view.badge).background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.badge)).clipShape(Circle()).overlay(Circle().stroke(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.bg), lineWidth: HabitBubbleClusterTokens.space.hair)).offset(x: HabitBubbleClusterTokens.space.xs, y: HabitBubbleClusterTokens.space.xs)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Profile")
    }
}
