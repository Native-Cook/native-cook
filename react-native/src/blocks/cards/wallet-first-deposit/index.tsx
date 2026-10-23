import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { WalletFirstDeposit } from './WalletFirstDeposit';

export default function WalletFirstDepositDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#FFFFFF' }}>
      <StatusBar style="dark" />
      <WalletFirstDeposit autoPlay />
    </View>
  );
}
