import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PunchCardForm } from './PunchCardForm';

export default function PunchCardFormDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#121613' }}>
      <StatusBar hidden />
      <PunchCardForm autoPlay />
    </View>
  );
}
