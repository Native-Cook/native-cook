import { useLocalSearchParams } from 'expo-router';

import { WidgetStampPicker } from './WidgetStampPicker';

export default function WidgetStampPickerDemo() {
  // Dev only: `?t=<ms>` holds the autoplay still at that moment, for pixel comparisons with the pin.
  const { t } = useLocalSearchParams<{ t?: string }>();
  // (a negative `t` plays again, since a link without the query keeps the last one)
  const freezeAt = __DEV__ && t !== undefined && Number(t) >= 0 ? Number(t) : undefined;
  return <WidgetStampPicker key={String(freezeAt)} autoPlay freezeAt={freezeAt} />;
}
