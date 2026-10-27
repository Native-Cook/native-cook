import SwiftUI
import UIKit

/// The page in an edge-to-edge `UIScrollView` (React Native's `ScrollView`): it draws under the
/// status bar and the home indicator and adds the safe-area insets to its own content, so the
/// system never adjusts the content inset. The SwiftUI content sits in a hosting controller whose
/// view is as tall as the content wants to be and as wide as the scroll view.
struct CipherSeamScroll<Content: View>: UIViewRepresentable {
    @ViewBuilder var content: Content

    func makeCoordinator() -> UIHostingController<Content> {
        UIHostingController(rootView: content)
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scroll = UIScrollView()
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.showsVerticalScrollIndicator = false
        scroll.showsHorizontalScrollIndicator = false
        scroll.alwaysBounceVertical = true
        // React Native's scroll view hands touches to its content at once, so a press shows at once.
        scroll.delaysContentTouches = false
        scroll.backgroundColor = .clear

        let host = context.coordinator
        host.safeAreaRegions = []
        host.sizingOptions = .intrinsicContentSize
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            host.view.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            host.view.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
        ])
        return scroll
    }

    func updateUIView(_ scroll: UIScrollView, context: Context) {
        context.coordinator.rootView = content
    }
}
