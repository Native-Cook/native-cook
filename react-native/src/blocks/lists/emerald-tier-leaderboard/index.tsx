import { useLocalSearchParams } from 'expo-router';
import { EmeraldTierLeaderboard } from './EmeraldTierLeaderboard';
export { EmeraldTierLeaderboard, EMERALD_TIER_LEADERBOARD_LOOP_SECONDS, LOOP } from './EmeraldTierLeaderboard';
export default function EmeraldTierLeaderboardDemo() {
  const { t, reduced } = useLocalSearchParams<{ t?: string; reduced?: string }>();
  const previewTime = __DEV__ && t !== undefined && Number.isFinite(Number(t)) ? Math.max(0, Number(t)) : undefined;
  return <EmeraldTierLeaderboard autoPlay previewTime={previewTime} previewReducedMotion={reduced === '1' ? true : undefined} />;
}
