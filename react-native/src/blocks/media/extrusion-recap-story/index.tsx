import { useRouter } from 'expo-router';
import { ExtrusionStory } from './ExtrusionStory';
import { RECAP } from './ers-theme';
export { ExtrusionStory } from './ExtrusionStory';
export type { ExtrusionPage, ExtrusionStoryProps } from './ers-types';
export { LOOP } from './ers-motion';
export default function ExtrusionRecapStoryDemo() {
  const router = useRouter();
  return <ExtrusionStory pages={RECAP} autoPlay header={{ onClose: () => router.back() }} />;
}
