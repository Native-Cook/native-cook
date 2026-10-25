import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CorkPopButton } from './CorkPopButton';

export default function CorkPopButtonDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0C1E18' }}>
      <StatusBar hidden />
      <CorkPopButton autoPlay />
    </View>
  );
}
