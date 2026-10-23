import { StatusBar } from 'expo-status-bar';

import { DragMultiSelect } from './DragMultiSelect';

export default function DragMultiSelectDemo() {
  return (
    <>
      <StatusBar style="dark" />
      <DragMultiSelect autoPlay />
    </>
  );
}
