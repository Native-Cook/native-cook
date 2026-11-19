import { CourierZoomTracker } from "./CourierZoomTracker";

// The block has the whole screen: a 19-second film of a parcel tracker. The map frames the courier's van and the
// customer's door together, so it zooms in by itself as the van gets closer: the whole city, the district, the street
// (where it turns north-up and leans into 3D, buildings raised from their footprints), and for the last 100 m right in
// to the front door, where the van parks and the panel says Arrived. A ghost finger drags the map to look ahead and taps
// locate to follow the van again, then taps Call ("Calling Marco…") and Message (a quick reply is sent);
// the camera pulls back out to the whole city. Drag and pinch the map to take over; locate follows the van again.
//
// Everything is drawn with Skia and Reanimated (no 3D engine); Call, Message and locate are SwiftUI controls through
// @expo/ui on iOS. The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function CourierZoomTrackerDemo() {
  return <CourierZoomTracker autoPlay />;
}
