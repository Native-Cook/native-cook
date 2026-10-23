import { StatusBar } from 'expo-status-bar';

import { SaveToCollection } from './SaveToCollection';

export default function SaveToCollectionDemo() {
  return (
    <>
      <StatusBar style="light" />
      <SaveToCollection autoPlay />
    </>
  );
}
