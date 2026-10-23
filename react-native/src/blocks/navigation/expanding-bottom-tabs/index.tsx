import React, { useState } from 'react';
import { View } from 'react-native';

import { BottomTabs } from './components/bottom-tabs';
import ChatScreen from './screens/ChatScreen';
import DiscoverScreen from './screens/DiscoverScreen';
import HomeScreen from './screens/HomeScreen';
import MoreScreen from './screens/MoreScreen';

// The original app mounted these four screens in a React Navigation bottom-tabs navigator of its own.
// A block lives inside the host app's navigation tree, so the selected tab is kept in local state here
// and handed to the same custom tab bar.
const Screens = [
  { name: 'Home', component: HomeScreen },
  { name: 'Discover', component: DiscoverScreen },
  { name: 'Chat', component: ChatScreen },
  { name: 'More', component: MoreScreen },
];

const routeNames = Screens.map((screen) => screen.name);

const ExpandingBottomTabsDemo = () => {
  const [index, setIndex] = useState(0);
  const Screen = Screens[index].component;

  return (
    <View style={{ flex: 1 }}>
      <View style={{ flex: 1 }}>
        <Screen />
      </View>
      <BottomTabs
        state={{ index, routeNames }}
        navigation={{
          navigate: (name) => {
            const next = routeNames.indexOf(name);
            if (next !== -1) {
              setIndex(next);
            }
          },
        }}
      />
    </View>
  );
};

export default ExpandingBottomTabsDemo;
