import { StyleSheet, View } from 'react-native';
import { ComponentStage } from '@/shared/component-stage';

import { LiquidUploadButton } from './LiquidUploadButton';

export default function LiquidUploadButtonDemo() {
  return (
    <ComponentStage background="#e1e9f7" height={250}>
      <View style={styles.uploads}>
        <View style={styles.uploadRow}>
          <LiquidUploadButton autoPlay />
          <LiquidUploadButton shape="round" tone="dark" autoPlay autoPlayDelay={900} />
        </View>
        <View style={styles.uploadRow}>
          <LiquidUploadButton shape="round" tone="light" autoPlay autoPlayDelay={1800} />
          <LiquidUploadButton tone="dark" autoPlay autoPlayDelay={2700} />
        </View>
      </View>
    </ComponentStage>
  );
}

const styles = StyleSheet.create({
  uploadRow: {
    flexDirection: 'row',
    gap: 24,
  },
  uploads: {
    gap: 22,
  },
});
