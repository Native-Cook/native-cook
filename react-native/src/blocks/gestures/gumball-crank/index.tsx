import { StatusBar } from 'expo-status-bar';

import { GumballUpgrade } from './GumballUpgrade';
import { GumballReplica } from './GumballReplica';
import { REPLICA } from './theme';

export default function GumballCrankDemo() {
  if (!REPLICA) return (
    <>
      <StatusBar style="dark" />
      <GumballUpgrade autoPlay />
    </>
  );
  return (
    <>
      <StatusBar hidden />
      <GumballReplica />
    </>
  );
}
