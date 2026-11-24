import { SkeletonContentResolve } from './SkeletonContentResolve';

// The block has the whole screen: the home of an invented podcast app, Hearth. Its loading state
// turns into the content instead of being swapped for it: bars with one band of light crossing all
// of them stretch to the exact width of the lines they stood for, the text fades in inside them,
// and the covers open through a soft radial wipe, top to bottom. In the 10 s film a ghost finger
// pulls to refresh twice (a second feed, then the first again). Pull the list down to refresh it
// yourself (the film returns 2 s after the last touch).
export default function SkeletonContentResolveDemo() {
  return <SkeletonContentResolve autoPlay />;
}
