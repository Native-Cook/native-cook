import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WaxSealInvite } from './WaxSealInvite';

export default function WaxSealInviteDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#2b201c' }}>
      <StatusBar hidden />
      <WaxSealInvite autoPlay />
    </View>
  );
}
