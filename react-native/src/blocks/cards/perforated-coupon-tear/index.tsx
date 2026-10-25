import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PerforatedCouponTear } from './PerforatedCouponTear';

export default function PerforatedCouponTearDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EFE4D2' }}>
      <StatusBar hidden />
      <PerforatedCouponTear autoPlay />
    </View>
  );
}
