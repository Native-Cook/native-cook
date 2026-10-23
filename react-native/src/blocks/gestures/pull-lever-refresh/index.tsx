import { StatusBar } from 'expo-status-bar';

import { PullLeverRefresh } from './PullLeverRefresh';

export default function PullLeverRefreshDemo() {
  return (
    <>
      <StatusBar hidden />
      <PullLeverRefresh autoPlay />
    </>
  );
}
