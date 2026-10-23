import { StatusBar } from 'expo-status-bar';

import { GlassLockKey } from './GlassLockKey';

export default function GlassLockKeyDemo() {
  return (
    <>
      <StatusBar style="light" />
      <GlassLockKey autoPlay />
    </>
  );
}
