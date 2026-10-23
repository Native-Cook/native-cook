import SwiftUI
import UIKit

struct DragCardToSendScene<DragGestureType: Gesture>: View {
    let layout: DragCardToSendLayout
    let frame: DragCardToSendFrame
    let dragGesture: DragGestureType
    let onUndo: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            DragCardToSendPalette.page
            DragCardToSendHeader(width: layout.width)
            DragCardToSendWalletCard(frame: frame, layout: layout)
                .frame(width: layout.cardWidth, height: layout.cardHeight)
                .position(x: layout.width / 2, y: layout.cardTop + layout.cardHeight / 2 + frame.cardOffset)
                .scaleEffect(frame.cardScale)
                .gesture(dragGesture)
            DragCardToSendPeople(frame: frame, layout: layout)
            DragCardToSendActivityList(frame: frame, layout: layout)
            DragCardToSendLiquid(frame: frame, layout: layout, onUndo: onUndo)
        }
        .coordinateSpace(name: "DragCardToSendScreen")
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .clipped()
    }
}

struct DragCardToSendHeader: View {
    let width: CGFloat

    var body: some View {
        HStack {
            Text("Wallet")
                .font(.system(size: 32, weight: .bold))
                .tracking(-0.7)
                .foregroundStyle(DragCardToSendPalette.ink)
            Spacer()
            DragCardToSendAvatar(name: "jules", size: 36)
                .accessibilityLabel("Account, Jules Morrow")
        }
        .frame(width: width - 32, height: 40)
        .position(x: width / 2, y: 104)
    }
}

struct DragCardToSendPeople: View {
    let frame: DragCardToSendFrame
    let layout: DragCardToSendLayout

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text("Send to")
                .font(.system(size: 19, weight: .semibold))
                .tracking(-0.4)
                .foregroundStyle(DragCardToSendPalette.ink)
                .frame(height: 24)
                .offset(x: 16, y: layout.sectionTop)

            Text(hint)
                .font(.system(size: 15))
                .tracking(-0.2)
                .foregroundStyle(frame.amount == nil ? DragCardToSendPalette.mute : DragCardToSendPalette.ink)
                .frame(height: 20)
                .fixedSize()
                .position(x: layout.width - 16 - hintWidth / 2, y: layout.sectionTop + 12)

            ForEach(Array(DragCardToSendData.people.enumerated()), id: \.element.id) { index, person in
                let selected = frame.selectedPerson == index
                VStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .stroke(DragCardToSendPalette.accent, lineWidth: 2.5)
                            .frame(width: 60, height: 60)
                            .opacity(selected ? 1 : 0)
                        DragCardToSendAvatar(name: person.id, size: 52)
                            .scaleEffect(selected ? 1 - 0.1 * frame.catchProgress : 1)
                    }
                    .frame(width: 60, height: 60)
                    Text(person.name)
                        .font(.system(size: 13, weight: .medium))
                        .tracking(-0.1)
                        .foregroundStyle(selected ? DragCardToSendPalette.ink : DragCardToSendPalette.mute)
                        .frame(height: 16)
                }
                .position(x: layout.avatarCenters[index], y: layout.avatarTop + 38)
            }
        }
    }

    private var hint: String {
        if let amount = frame.amount, let person = frame.selectedPerson {
            return frame.sending
                ? "Sending $\(amount) to \(DragCardToSendData.people[person].name)"
                : "Let go to send $\(amount) to \(DragCardToSendData.people[person].name)"
        }
        return "Pull the card to send"
    }

    private var hintWidth: CGFloat {
        (hint as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 15)]).width
    }
}

struct DragCardToSendActivityList: View {
    let frame: DragCardToSendFrame
    let layout: DragCardToSendLayout

    private var rows: [DragCardToSendActivity] {
        guard frame.sentCommitted else { return DragCardToSendData.rows }
        let person = DragCardToSendData.people[frame.receiptPerson]
        return [DragCardToSendActivity(id: "sent", name: person.fullName, meta: "Just now", cents: -frame.receiptAmount * 100,
                                       person: frame.receiptPerson, merchant: nil)] + Array(DragCardToSendData.rows.prefix(3))
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text("Activity")
                .font(.system(size: 19, weight: .semibold))
                .tracking(-0.4)
                .foregroundStyle(DragCardToSendPalette.ink)
                .frame(height: 24)
                .offset(x: 16, y: layout.activityTop)
            Text("See all")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(DragCardToSendPalette.accent)
                .frame(height: 20)
                .offset(x: layout.width - 64, y: layout.activityTop + 2)

            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                DragCardToSendActivityRow(row: row)
                    .frame(width: layout.width - 32, height: 56)
                    .offset(x: 16, y: layout.rowTop + CGFloat(index) * 56)
            }
        }
    }
}

struct DragCardToSendActivityRow: View {
    let row: DragCardToSendActivity

    var body: some View {
        HStack(spacing: 12) {
            DragCardToSendActivityIcon(row: row)
            VStack(alignment: .leading, spacing: 0) {
                Text(row.name)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.3)
                    .foregroundStyle(DragCardToSendPalette.ink)
                    .frame(height: 20)
                Text(row.meta)
                    .font(.system(size: 13))
                    .tracking(-0.1)
                    .foregroundStyle(DragCardToSendPalette.mute)
                    .frame(height: 16)
            }
            Spacer(minLength: 8)
            Text(DragCardToSendFormat.money(row.cents, signed: true))
                .font(.system(size: 16, weight: .semibold))
                .tracking(-0.3)
                .monospacedDigit()
                .foregroundStyle(row.cents > 0 ? DragCardToSendPalette.accent : DragCardToSendPalette.ink)
        }
    }
}

struct DragCardToSendActivityIcon: View {
    let row: DragCardToSendActivity

    var body: some View {
        Group {
            if let person = row.person {
                DragCardToSendAvatar(name: DragCardToSendData.people[person].id, size: 40)
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(iconBackground)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(DragCardToSendPalette.hairline))
                    Image(systemName: iconName)
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(iconColor)
                }
                .frame(width: 40, height: 40)
            }
        }
    }

    private var iconName: String {
        switch row.merchant { case .coffee: "cup.and.saucer.fill"; case .grocer: "leaf.fill"; case .transit: "tram.fill"; case nil: "" }
    }
    private var iconBackground: Color {
        switch row.merchant {
        case .coffee: Color(red: 245/255, green: 235/255, blue: 224/255)
        case .grocer: Color(red: 225/255, green: 239/255, blue: 229/255)
        case .transit: Color(red: 248/255, green: 240/255, blue: 207/255)
        case nil: .clear
        }
    }
    private var iconColor: Color {
        switch row.merchant {
        case .coffee: Color(red: 105/255, green: 63/255, blue: 37/255)
        case .grocer: Color(red: 46/255, green: 113/255, blue: 72/255)
        case .transit: Color(red: 120/255, green: 91/255, blue: 12/255)
        case nil: .clear
        }
    }
}

struct DragCardToSendAvatar: View {
    let name: String
    let size: CGFloat

    var body: some View {
        Image(uiImage: DragCardToSendImages.photo(named: name))
            .resizable()
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(Circle())
            .overlay(Circle().stroke(DragCardToSendPalette.hairline, lineWidth: 1))
    }
}

enum DragCardToSendImages {
    private static var cache: [String: UIImage] = [:]

    static func photo(named name: String) -> UIImage {
        if let image = cache[name] { return image }
        let resource = "DragCardToSend-\(name)"
        let image = Bundle.main.path(forResource: resource, ofType: "jpg")
            .flatMap(UIImage.init(contentsOfFile:)) ?? UIImage()
        cache[name] = image
        return image
    }
}
