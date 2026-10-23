import { StatusBar } from 'expo-status-bar';

import { DragMultiSelect } from './DragMultiSelect';

export default function DragMultiSelectDemo() {
  return (
    <>
      <StatusBar style="light" />
      <DragMultiSelect autoPlay />
    </>
  );
}
