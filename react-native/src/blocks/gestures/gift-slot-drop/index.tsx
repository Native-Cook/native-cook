import { StatusBar } from 'expo-status-bar';

import { GiftSlotDrop } from './GiftSlotDrop';

export default function GiftSlotDropDemo() {
  return (
    <>
      <StatusBar style="light" />
      <GiftSlotDrop autoPlay />
    </>
  );
}
