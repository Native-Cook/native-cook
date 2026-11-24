import { StoweListScreen } from './StoweListScreen';

// The packing list of an invented travel app, Stowe. Long-press lifts a row, its neighbours part on springs as it
// moves, the list scrolls itself near the edges, the row settles with a small overshoot and its check draws. Rows are
// transforms over a fixed order table. Long-press to drag, tap to check; the film returns 2 s after the last touch.
export default function SpringReorderChecklistDemo() {
  return <StoweListScreen autoPlay />;
}
