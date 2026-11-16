# Glass Pour Menu

A live Skia liquid menu. The default backdrop is the procedural five-petal canvas. The demo enables the 7.2-second loop; the reusable component defaults to interaction only.

```tsx
import { GlassPourMenu } from './GlassPourMenu';

<GlassPourMenu
  items={[
    { key: 'share', label: 'Share', icon: 'share' },
    { key: 'remove', label: 'Delete', icon: 'delete', destructive: true },
  ]}
  onSelect={runAction}
/>;
```

Use `open` with `onOpenChange` for controlled state. `progress` accepts a Reanimated shared value: the menu writes it during gestures and animations, and external writes scrub the same geometry. `onComplete` reports settled endpoints. Autoplay does not select actions or play sensory cues.

`items` accepts 2–7 rows, custom SVG path strings or Skia paths, destructive styling and disabled actions. Built-in icons are `share`, `duplicate`, `rename`, and `delete`. Long visible labels are ellipsized using glyph widths; their full text remains accessible. Row height grows with system font scale. Defaults: 78 pt drop, 250 pt panel, 48 pt row pitch, bundled 17 pt Inter Medium.

`backdrop` accepts a `SkImage`, `SkShader`, or `RefObject<View | null>`. Images/shaders are rasterized once per source/size. View refs are captured once per opening: use a separate, non-collapsible backdrop view that does not contain the menu itself. Coordinates cover local component bounds; image inputs are scaled to those bounds. Frost is made once with a 10 pt Skia blur. `onBackdropError` reports failed captures; the procedural flower is shown until capture succeeds. Native snapshots, especially Android, need host-device validation.

`anchor` is in local points. `placement` is `below`, `above`, or `auto`. `size`, `tint`, `glass`, and `font` override geometry, color, optics and font family. Very large font scales with seven rows can exceed a short phone's height; this implementation has no scrolling viewport.

Tap the drop or pull 175 pt to pour. Grab the close bead and drag toward the drop to merge. Release projects 120 ms; a fast flick can leave a 220 ms satellite. Row dwell after 250 ms deepens the lens. `dragToSelect={false}` disables drag-through selection. Touch interrupts autoplay; it settles and restarts after two seconds without touch.

`haptics` defaults to true. `sound` is opt-in and uses three bundled 40/60 ms cues. It does not change the host's global audio mode. Reduced Motion uses fully formed endpoints and a 180 ms fade; its frame callback stops. Animated idle life otherwise has rendering cost.

Development builds expose `globalThis.__gpm.pin(seconds)`, `.progress(value)`, `.play()` and `.state()`. The hook is removed on unmount. Offline evidence and the upgrade report are under `.work/glass-pour-menu/` in the source workspace.
