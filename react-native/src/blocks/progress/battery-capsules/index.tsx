import { StyleSheet, View } from 'react-native';
import { ComponentStage } from '@/shared/component-stage';

import { BatteryCapsules } from './BatteryCapsules';

export default function BatteryCapsulesDemo() {
  return (
    <ComponentStage background="#6c8ab3" height={250}>
      <View style={styles.row}>
        <BatteryCapsules autoPlay />
        <BatteryCapsules autoPlay variant="dark" />
      </View>
    </ComponentStage>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: 18,
  },
});
