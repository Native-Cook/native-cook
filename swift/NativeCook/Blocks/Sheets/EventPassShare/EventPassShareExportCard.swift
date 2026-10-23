import SwiftUI

struct EventPassShareExportCard: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Color(hex: 0x656565), Color(hex: 0x909090)], startPoint: .top, endPoint: .bottom)
            RoundedRectangle(cornerRadius: 23, style: .continuous)
                .fill(.white.opacity(0.12))
                .overlay(RoundedRectangle(cornerRadius: 23, style: .continuous).strokeBorder(.white.opacity(0.18), lineWidth: 1))
                .frame(width: 269, height: 381)
                .offset(x: 46.5, y: 80)
            EventPassSharePoster()
                .offset(x: 56, y: 89)
            Text(EventPassShareContent.title)
                .font(.system(size: 22, weight: .semibold))
                .tracking(-0.55)
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
                .frame(width: 235, height: 27, alignment: .topLeading)
                .offset(x: 64, y: 363)
            Text("Jul 18  Los Angeles")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white.opacity(0.92))
                .frame(width: 235, height: 18, alignment: .topLeading)
                .offset(x: 64, y: 432)
            Text("common\u{2726}")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white.opacity(0.31))
                .frame(width: 362)
                .offset(y: 488)
        }
        .frame(width: 362, height: 541)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
