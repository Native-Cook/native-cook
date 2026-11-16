import SwiftUI
import UIKit

/// RN's ScrollView has bounces=false and scrolls only for expanded Dynamic Type.
struct NeonContourMoodScroll<Content: View>: UIViewControllerRepresentable {
    let contentWidth: Double
    let contentHeight: Double
    let viewportHeight: Double
    let content: Content

    func makeUIViewController(context: Context) -> Controller {
        Controller(content: content)
    }
    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.host.rootView = content
        controller.host.view.frame = CGRect(x: 0, y: 0, width: contentWidth, height: contentHeight)
        controller.scroll.contentSize = CGSize(width: contentWidth, height: contentHeight)
        controller.scroll.isScrollEnabled = contentHeight > viewportHeight + 1
    }
    final class Controller: UIViewController {
        let scroll = UIScrollView()
        let host: UIHostingController<Content>
        init(content: Content) {
            host = UIHostingController(rootView: content)
            super.init(nibName: nil, bundle: nil)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .clear
            scroll.backgroundColor = .clear
            scroll.bounces = false
            scroll.showsVerticalScrollIndicator = false
            scroll.showsHorizontalScrollIndicator = false
            scroll.contentInsetAdjustmentBehavior = .never
            scroll.frame = view.bounds
            scroll.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(scroll)
            addChild(host)
            host.safeAreaRegions = []
            host.view.backgroundColor = .clear
            scroll.addSubview(host.view)
            host.didMove(toParent: self)
        }
    }
}
