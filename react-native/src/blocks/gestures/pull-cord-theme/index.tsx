import { StatusBar } from 'expo-status-bar';

import { BulbReplica } from './BulbReplica';
import { PullCordTheme } from './PullCordTheme';
import { REPLICA } from './theme';

export default function PullCordThemeDemo() {
  if (!REPLICA) return <PullCordTheme autoPlay />;
  return (
    <>
      <StatusBar hidden />
      <BulbReplica />
    </>
  );
}
