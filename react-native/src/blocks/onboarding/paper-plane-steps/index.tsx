import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PaperPlaneSteps } from './PaperPlaneSteps';

export default function PaperPlaneStepsDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1B2236' }}>
      <StatusBar hidden />
      <PaperPlaneSteps autoPlay />
    </View>
  );
}
