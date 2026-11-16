import { useRouter } from 'expo-router';

import { LightBoxReveal } from './gcu-reveal';

export { LightBoxReveal, type LightBoxRevealProps, type LightBoxCopy } from './gcu-reveal';
export { LOOP } from './gcu-motion';

export default function GelCubeUnboxDemo() {
  const router = useRouter();
  return <LightBoxReveal autoPlay onClose={() => router.back()} />;
}
