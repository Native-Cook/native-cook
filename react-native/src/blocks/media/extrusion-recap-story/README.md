# Extrusion Story

A full-screen, data-driven Skia story pager. A projected solid lofts from one SVG silhouette to the next; its distance-field normal lights the walls. The demo is a music recap with three pages and an exact 8-second timeline.

```tsx
import { ExtrusionStory } from './ExtrusionStory';
import { RECAP } from './ers-theme';

<ExtrusionStory pages={RECAP} autoAdvanceMs={5000} onIndexChange={trackPage} onComplete={closeRecap} />;
```

`ers-types.ts` exports the full props API. Supply `index` for React-controlled navigation, `defaultIndex` for local state, or a fractional Reanimated `progress` shared value for UI-thread control. A page accepts SVG path data or `SkPath`, two six-digit sRGB field colours, a face colour, optional direction, headline/caption/stat and a `render(active)` overlay slot. Paths are normalized to their own bounds and need no build-time SDF files. Caption and display font, headline colour, radius and peek size are configurable.

Tap either side to navigate. Drag horizontally (300 design points per page), flick to commit, hold to pause and steer vertically. Real device rotation uses Reanimated's sensor; a drag is the stand-in when no sensor exists. Touch interrupts the current spring. Autoplay resumes after two seconds idle, returning to the opening page first. At the last page, dragging stretches the extrusion and springs back. Autoplay is silent; haptics are attached to user grabs, wall crossing and landing.

`autoPlay` runs the showcase film. `autoAdvanceMs` enables a separate story timer, ending with `onComplete`; `null` disables this timer. Reduce Motion stops the film and uses a 250ms static-cap/field crossfade. VoiceOver stops auto-advance and exposes previous/next accessibility actions. The page's real native text scales; at large text sizes the content region scrolls.

Development hook: `globalThis.__ers.pin(seconds)`, `.at(seconds)`, `.progress(fractionalPage)`, `.play()`, `.state()`. It is registered only in development and removed on unmount.

There is no audio file, raster art, GLB, network request or direct WebGPU layer. Both main rendering and quarter-size bloom use `react-native-skia`. Production device review, GPU performance and haptic timing are still required; the supplied evidence is an offline CanvasKit render, not a device capture.
