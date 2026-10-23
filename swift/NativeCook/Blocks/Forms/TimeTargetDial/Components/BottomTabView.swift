import SwiftUI

/// `src/components/bottom-tab/index.tsx`
struct BottomTabView: View {
    private struct TabItem: Identifiable {
        let name: String
        let label: String
        let systemImage: String
        var id: String { name }
    }

    private let tabs: [TabItem] = [
        TabItem(name: "home", label: "Home", systemImage: "house"),
        TabItem(name: "payments", label: "Payments", systemImage: "creditcard"),
        TabItem(name: "project", label: "+ Project", systemImage: "plus"),
        TabItem(name: "messages", label: "Messages", systemImage: "message"),
        TabItem(name: "team", label: "Team", systemImage: "person.2"),
    ]

    /// The RN version compares `usePathname()` against the tab route ("/" is Home).
    @State private var activeTab = "home"

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(tabs.enumerated()), id: \.element.id) { index, tab in
                if index > 0 { Spacer(minLength: 0) }

                Button {
                    activeTab = tab.name
                } label: {
                    if tab.name == "project" {
                        Text(tab.label)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 13)
                            .background(Color(timeTargetHex: 0x424446), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .padding(.horizontal, 8) // centerTab marginHorizontal
                    } else {
                        let isActive = activeTab == tab.name
                        VStack(spacing: 4) {
                            Image(systemName: tab.systemImage)
                                .font(.system(size: 20))
                                .frame(height: 20)
                            Text(tab.label)
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundStyle(isActive ? Color.white : Color(timeTargetHex: 0x9CA3AF))
                        .padding(.vertical, 4)
                    }
                }
                .buttonStyle(TouchableOpacity(activeOpacity: 0.7))
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 12)
        .background(Color.black)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color(timeTargetHex: 0x374151))
                .frame(height: 1)
        }
    }
}

#Preview {
    BottomTabView()
        .preferredColorScheme(.dark)
}
