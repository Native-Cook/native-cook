import { BottomSheetModalProvider } from '@gorhom/bottom-sheet';
import { StyleSheet, View } from 'react-native';
import 'react-native-reanimated';
import SubScreen from './screens/SubScreen';

export default function FabricPaywallSheetDemo() {
  return (
    <View style={styles.fill}>
      <BottomSheetModalProvider>
        <SubScreen />
      </BottomSheetModalProvider>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor:"black"
  },
});
