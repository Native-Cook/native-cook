import { View } from 'react-native';
import { ComponentStage } from '@/shared/component-stage';

import { NoiseLevelMeter } from './NoiseLevelMeter';

export default function NoiseLevelMeterDemo() {
  return (
    <ComponentStage background="#ced2da" height={270}>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <NoiseLevelMeter autoPlay level={40} />
        <NoiseLevelMeter autoPlay level={60} />
      </View>
    </ComponentStage>
  );
}
