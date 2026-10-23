import { StatusBar } from 'expo-status-bar';

import { QA_HOLD_AT } from './motion';
import { SaveToCollection } from './SaveToCollection';

export default function SaveToCollectionDemo() {
  return (
    <>
      <StatusBar style="light" />
      <SaveToCollection key={String(QA_HOLD_AT)} autoPlay />
    </>
  );
}
