import { GhostCompleteField } from './GhostCompleteField';

/**
 Ghost Complete Field: a mail reply whose body offers the rest of the sentence in grey; a swipe
 right or the keycap chip fills it with ink. The Cc field offers mail domains as chips.
 Component: GhostCompleteField.tsx (screen), GhostField.tsx (the reusable field).
 */
export default function GhostCompleteFieldDemo() {
  return <GhostCompleteField autoPlay />;
}
