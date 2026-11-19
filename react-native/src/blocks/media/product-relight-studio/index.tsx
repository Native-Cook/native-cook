import { ProductRelightStudio } from "./ProductRelightStudio";

// The block has the whole screen: a photo editor's Light tool on a product shot. The photo is a G-buffer rendered
// in Blender (the pocket watch on a seamless paper sweep: colour, normals, depth, roughness, metal, occlusion, the
// floor's reflection) and shaded live by a Skia runtime effect, so the light really moves: drag the softbox over the
// photo, pick Studio, Golden hour or Moody blue, set the intensity, tap Compare and drag the divider to see the flat
// original. A 16-second film uses every control once; it stops when touched and comes back 2 s after the last touch.
// On iOS the segmented control and the slider are the system's (@expo/ui, a development build).
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function ProductRelightStudioDemo() {
  return <ProductRelightStudio autoPlay />;
}
