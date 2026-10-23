import SwiftUI

struct TiltSwipeActionsBalanceCard: View {
    let width: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.cardRadius, style: .continuous)
                .fill(
                    LinearGradient(
                        stops: [
                            .init(color: Color(red: 34 / 255, green: 35 / 255, blue: 42 / 255), location: 0),
                            .init(color: TiltSwipeActionsColor.ink, location: 0.55),
                            .init(color: Color(red: 18 / 255, green: 19 / 255, blue: 23 / 255), location: 1),
                        ],
                        startPoint: UnitPoint(x: 0.386, y: -0.406),
                        endPoint: UnitPoint(x: 0.614, y: 1.406)
                    )
                )
                .overlay {
                    TiltSwipeActionsAssets.guilloche
                        .resizable()
                        .frame(width: 370, height: 128)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.cardRadius, style: .continuous))
                }
                .overlay {
                    RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.cardFrameRadius, style: .continuous)
                        .stroke(TiltSwipeActionsColor.brass.opacity(0.38), lineWidth: 0.75)
                        .padding(TiltSwipeActionsLayout.cardFrameInset)
                }
                .shadow(color: Color.white.opacity(0.32), radius: 0, y: 1)
                .shadow(color: Color(red: 30 / 255, green: 24 / 255, blue: 12 / 255).opacity(0.26), radius: 18, y: 9)

            Text("PRIVATE ACCOUNT")
                .font(.custom(TiltSwipeActionsFont.sansMedium, fixedSize: 11))
                .tracking(1.98)
                .foregroundStyle(TiltSwipeActionsColor.brass)
                .offset(x: 24, y: 22)
            Text("···· \(TiltSwipeActionsData.cardNumber)")
                .font(.custom(TiltSwipeActionsFont.sans, fixedSize: 12))
                .tracking(0.96)
                .foregroundStyle(TiltSwipeActionsColor.ivory.opacity(0.55))
                .frame(width: width - 48, alignment: .trailing)
                .offset(x: 24, y: 22)
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text("$")
                    .font(.custom(TiltSwipeActionsFont.money22Regular, fixedSize: 22))
                    .foregroundStyle(TiltSwipeActionsColor.brass)
                    .padding(.trailing, 3)
                Text("1,148")
                    .font(.custom(TiltSwipeActionsFont.display, fixedSize: 48))
                    .tracking(-0.5)
                    .foregroundStyle(TiltSwipeActionsColor.ivory)
                Text(".90")
                    .font(.custom(TiltSwipeActionsFont.money22, fixedSize: 22))
                    .foregroundStyle(TiltSwipeActionsColor.ivory)
                    .padding(.leading, 2)
            }
            .fixedSize()
            .offset(x: 22, y: 43)
            Text("Available today")
                .font(.custom(TiltSwipeActionsFont.sans, fixedSize: 12))
                .foregroundStyle(TiltSwipeActionsColor.ivory.opacity(0.5))
                .offset(x: 24, y: 100)
        }
        .frame(width: width, height: TiltSwipeActionsLayout.cardHeight, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Private account ending 4021, $1,148.90 available today")
    }
}
