import SwiftUI

/// `src/components/footer/index.tsx`
struct FooterView: View {
    var body: some View {
        VStack(spacing: 10) {
            Button {
                print("Confirm pressed")
            } label: {
                Text("Confirm")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color(timeTargetHex: 0x504E4E))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: .white.opacity(0.3), radius: 8, x: 0, y: 4)
            }

            Button {
                print("Back pressed")
            } label: {
                Text("← Back")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Color(timeTargetHex: 0x333333), lineWidth: 2)
                    )
            }
        }
        .buttonStyle(TouchableOpacity())
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
        .fadeInUp(duration: 0.8)
    }
}

#Preview {
    FooterView()
        .background(Color.black)
        .preferredColorScheme(.dark)
}
