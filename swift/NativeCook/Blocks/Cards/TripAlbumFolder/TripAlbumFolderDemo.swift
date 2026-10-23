import SwiftUI

struct TripAlbumFolderDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xf5f5f6), height: 440) { TripAlbumFolder(autoPlay: true) }
    }
}

#Preview {
    TripAlbumFolderDemo()
}
