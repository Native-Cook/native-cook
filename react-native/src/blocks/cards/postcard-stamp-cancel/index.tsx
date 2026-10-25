import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PostcardStampCancel } from './PostcardStampCancel';

export default function PostcardStampCancelDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#14213A' }}>
      <StatusBar hidden />
      <PostcardStampCancel autoPlay />
    </View>
  );
}
