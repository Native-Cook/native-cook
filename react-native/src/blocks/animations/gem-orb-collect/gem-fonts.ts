// Local Inter faces keep the portable block independent of the catalog's font loader.
import { useFonts } from 'expo-font';

import { TYPE } from './gem-contract';

const sources = {
  [TYPE.number.family]: require('./assets/GemInter-Bold.ttf'),
  [TYPE.body.family]: require('./assets/GemInter-Regular.ttf'),
};

export function useGemFonts(): boolean {
  const [loaded, error] = useFonts(sources);
  if (error) throw error;
  return loaded;
}
