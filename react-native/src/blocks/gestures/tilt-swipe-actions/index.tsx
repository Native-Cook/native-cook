import { StatusBar } from 'expo-status-bar';

import { QA_HOLD_AT, QA_STATE } from './motion';
import { TiltSwipeActions } from './TiltSwipeActions';

export default function TiltSwipeActionsDemo() {
  return (
    <>
      <StatusBar hidden />
      <TiltSwipeActions key={`${String(QA_HOLD_AT)}-${String(QA_STATE)}`} autoPlay />
    </>
  );
}
