import { HoppingDotTabBar } from './HoppingDotTabBar';

/** The block fills the screen and keeps the status bar, so it is shown without a stage. It starts on the last tab, so the first hop is the long one. */
export default function HoppingDotTabBarDemo() {
  return <HoppingDotTabBar autoPlay initialTab={3} />;
}
