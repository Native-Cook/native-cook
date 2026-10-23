import SwiftUI

/// `src/components/queued/index.tsx`
struct QueuedView: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Landing page 1")
                        .font(.rounded(14))
                        .foregroundStyle(.white)
                    Text("For Chris")
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                }

                Spacer()

                HStack(spacing: 8) {
                    Circle()
                        .fill(Color(timeTargetHex: 0xFFDA50))
                        .frame(width: 7, height: 7)
                    Text("Queued")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.black)
                }
                .padding(.horizontal, 12)
                .frame(height: 36)
                .background(Color.white, in: Capsule())
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 16)
            .frame(height: 60)
            .background(Color(timeTargetHex: 0x00655C), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .fadeInUp(duration: 0.3)

            Text("Adjust project time target")
                .font(.rounded(14))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .fadeInUp(duration: 0.5)
        }
    }
}

#Preview {
    QueuedView()
        .background(Color.black)
        .preferredColorScheme(.dark)
}
