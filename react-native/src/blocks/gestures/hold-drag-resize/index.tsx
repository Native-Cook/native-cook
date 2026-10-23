import { StatusBar } from 'expo-status-bar';

import { HoldDragResize } from './HoldDragResize';

export default function HoldDragResizeDemo() {
  return (
    <>
      <StatusBar style="light" />
      <HoldDragResize autoPlay />
    </>
  );
}
