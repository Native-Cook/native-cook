import type { SharedValue } from 'react-native-reanimated';

import type { SceneInput } from './burst-timeline';

/** Both enqueue and drain execute on UI: a JS read can never overwrite a drain. */
export function enqueueSceneInput(
  commands: SharedValue<SceneInput[]>,
  serial: SharedValue<number>,
  kind: SceneInput['kind'],
  index: number,
) {
  'worklet';
  const nextSerial = serial.get() + 1;
  serial.set(nextSerial);
  commands.set([...commands.get(), { serial: nextSerial, kind, index }]);
}
