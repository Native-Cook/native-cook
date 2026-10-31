import SwiftUI

/// The gallery, and over it the block that is open.
struct RootView: View {
    // `-block <slug>` on the command line opens that block at launch, without a tap.
    @State private var open: Block? = blockRegistry.first {
        $0.slug == UserDefaults.standard.string(forKey: "block")
    }
    // `-recording 1` as well: the RecordBlocks tests pass it, so a demo that does not hide the
    // status bar itself is recorded without the clock and the battery.
    private let recording = UserDefaults.standard.bool(forKey: "recording")
    // While the RecordBlocks tests take the sound too, the block opens only after the sync flash
    // (RecordingSound.swift), on a black screen once the launch transition is over, so the flash is
    // a clean cut in the video and never inside a loop the site keeps.
    @State private var syncing = RecordingSound.log != nil

    private let slide = Animation.easeInOut(duration: 0.3)

    var body: some View {
        ZStack {
            GalleryScreen { block in
                withAnimation(slide) { open = block }
            }
            .allowsHitTesting(open == nil)
            .accessibilityHidden(open != nil)

            if syncing {
                Color.black
                    .ignoresSafeArea()
                    .zIndex(2)
                    .onAppear { RecordingSound.flash { syncing = false } }
            } else if let block = open {
                DemoScreen(block: block) {
                    withAnimation(slide) { open = nil }
                }
                .id(block.slug)
                .transition(.move(edge: .trailing))
                .zIndex(1)
            }
        }
        .background(GalleryColors.background.ignoresSafeArea())
        // The gallery is dark. An open block chooses for itself, status bar included.
        .preferredColorScheme(open == nil ? .dark : nil)
        .statusBarHidden(recording && open != nil)
    }
}

#Preview {
    RootView()
}
