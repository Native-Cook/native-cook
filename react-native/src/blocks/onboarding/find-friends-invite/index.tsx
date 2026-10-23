import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FindFriendsInvite } from './FindFriendsInvite';

export default function FindFriendsInviteDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F6F6F6' }}>
      <StatusBar style="dark" />
      <FindFriendsInvite autoPlay />
    </View>
  );
}
