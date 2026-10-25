import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WhiskMeringueGesture } from './WhiskMeringueGesture';

export default function WhiskMeringueGestureDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#E4DED5' }}>
      <StatusBar hidden />
      <WhiskMeringueGesture autoPlay />
    </View>
  );
}
