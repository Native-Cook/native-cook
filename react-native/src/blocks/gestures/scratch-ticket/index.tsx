import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ScratchTicket } from './ScratchTicket';

export default function ScratchTicketDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#13251b' }}>
      <StatusBar hidden />
      <ScratchTicket autoPlay />
    </View>
  );
}
