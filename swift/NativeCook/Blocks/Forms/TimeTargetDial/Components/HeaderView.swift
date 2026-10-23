import SwiftUI

/// `src/components/header/index.tsx`
struct HeaderView: View {
    var body: some View {
        ZStack(alignment: .bottom) {
            Text("Landing page 1")
                .font(.rounded(16))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 18)

            HStack(alignment: .top) {
                HStack(spacing: 8) {
                    Image("time-target-avatar")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 32, height: 32)
                        .clipShape(Circle())
                    Image(systemName: "chevron.down")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(timeTargetHex: 0x424446))
                }

                Spacer()

                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(timeTargetHex: 0x424446, alpha: 0x70 / 255.0))
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(Color(timeTargetHex: 0x63666A))
                }
                .frame(width: 32, height: 32)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color(timeTargetHex: 0x374151))
                .frame(height: 1)
        }
    }
}

#Preview {
    HeaderView()
        .background(Color.black)
        .preferredColorScheme(.dark)
}
