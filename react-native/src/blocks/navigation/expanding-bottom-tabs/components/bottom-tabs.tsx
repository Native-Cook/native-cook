import { useWindowDimensions, View } from 'react-native';

import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TabsName } from './constant';
import { TabItem } from './tab-item';

// What this tab bar reads of React Navigation's BottomTabBarProps.
export type BottomTabsProps = {
  state: { index: number; routeNames: string[] };
  navigation: { navigate: (name: string) => void };
};

export const BottomTabs: React.FC<BottomTabsProps> = ({ state, navigation }) => {
  const currentIndex = state.index;
  const setActiveTabIndex = (index: number) => {
    navigation.navigate(state.routeNames[index]);
  };
  const { width } = useWindowDimensions();
  const { bottom } = useSafeAreaInsets();

  const tabsWidth = width - 14 * (TabsName.length - 1);
  const maxWidth = 140;
  const minWidth = (tabsWidth - maxWidth) / (TabsName.length - 1);


  return (
    <View
      style={{
        gap: 10,
        flexDirection: 'row',
        paddingBottom: bottom,
        paddingTop: 12,
        backgroundColor: "#000000",
        borderTopColor: "#333333",
        borderTopWidth: 1,
        paddingHorizontal: 12
      }}>
      {TabsName.map((tab, index) => (
        <TabItem
          onPress={() => {
            setActiveTabIndex(index);
          }}
          icon={tab.icon}
          key={index}
          label={tab.label}
          maxWidth={maxWidth}
          minWidth={minWidth}
          isActive={index === currentIndex}
        />
      ))}
    </View>
  );
};