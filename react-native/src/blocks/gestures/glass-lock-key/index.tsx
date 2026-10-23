import { StatusBar } from 'expo-status-bar';

import { GlassUpgrade } from './GlassUpgrade';
import { GlassReplica } from './GlassReplica';
import { REPLICA } from './theme';

export default function GlassLockKeyDemo() {
  if (!REPLICA) return <GlassUpgrade autoPlay />;
  return (
    <>
      <StatusBar hidden />
      <GlassReplica />
    </>
  );
}
