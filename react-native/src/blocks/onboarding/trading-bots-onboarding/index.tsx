import { useLocalSearchParams } from 'expo-router';

import { TradingBotsOnboarding } from './TradingBotsOnboarding';

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage.
 * QA parameters: `?step=0…5` opens a scene, `holdFocus=1` keeps Pro on its first card,
 * `autoplay=1` plays the walkthrough.
 */
export default function TradingBotsOnboardingDemo() {
  const params = useLocalSearchParams<{ step?: string; holdFocus?: string; autoplay?: string }>();
  return (
    <TradingBotsOnboarding
      startStep={params.step ? Number(params.step) : 0}
      holdFocus={params.holdFocus === '1'}
      autoplay={params.autoplay === '1'}
    />
  );
}
