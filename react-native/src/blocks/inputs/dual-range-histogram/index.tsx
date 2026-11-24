import { NookFilterScreen } from './NookFilterScreen';

// The filter sheet of an invented rental search app, Nook. A price histogram sits on a two-thumb range: bars inside
// take the colour, bars outside drain and sink, the labels ride the thumbs and roll their digits, and the thumbs
// squash when held and refuse to cross with a rubber band. Drag a thumb or tap a bar to take over; the film returns
// 2 s after the last touch.
export default function DualRangeHistogramDemo() {
  return <NookFilterScreen autoPlay />;
}
