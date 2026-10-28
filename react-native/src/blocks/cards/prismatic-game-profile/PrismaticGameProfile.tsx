import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Image as ExpoImage } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, type Ref } from 'react';
import { AccessibilityInfo, Alert, AppState, BackHandler, Modal, Platform, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View, useWindowDimensions, type ImageStyle, type Insets, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { cubicBezier, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useSharedValue } from 'react-native-reanimated';
import { GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';

import { PROFILE_LAYOUT as L, createSourceGeometry, estimateSourceMetrics, sourceBadgeOrigin, type SourceMetric } from './PrismaticGameProfileLayout';
import { galleryCatchStrength, galleryContentOpacity, galleryPlayerTilt, initialIdentityLayout, peelIdentityAvoidance, usePrismaticScene } from './PrismaticGameProfileMotion';
import { peelSurfaceBounds } from './PrismaticPeelFormation';
import { PrismaticRail } from './PrismaticGameProfileRail';
import { PrismaticDetailMorph } from './PrismaticGameProfileDetailMorph';
import { PlayerCardIdentity, PlayerCardSubstrate, type PlayerCardScene, type PlayerSealLayout } from './PrismaticGameProfilePlayerCard';

export const LOOP = 10;
export type PrismaticGameProfileProps = { autoPlay?: boolean; previewTime?: number; previewReducedMotion?: boolean; renderer?: 'auto' | 'skia' };
const ART = {
  avatar: require('./assets/avatar.png'), landscape: require('./assets/landscape.png'),
  crown: require('./assets/crown.png'), shield: require('./assets/shield.png'), diamond: require('./assets/diamond.png'),
  game: require('./assets/game.png'), ruby: require('./assets/ruby.png'), coin: require('./assets/coin.png'),
  statsLevel: require('./assets/stats-level.png'), statsGames: require('./assets/stats-games.png'), statsTime: require('./assets/stats-time.png'),
};
const BADGES = [
  { name: 'Crown', image: ART.crown, colors: ['#DDBAFC', '#B92BFF'] as const },
  { name: 'Shield', image: ART.shield, colors: ['#FFE666', '#FF9A00'] as const },
  { name: 'Diamond', image: ART.diamond, colors: ['#F5F35A', '#D8DB00'] as const },
];
type Surface = 'stats' | 'object' | 'settings' | 'friend' | 'game' | 'home' | 'store';
const DISPLAY = Platform.OS === 'ios' ? 'ArialRoundedMTBold' : 'sans-serif';
const ReducedPressContext = createContext(false);
const PRESS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function Picture({ source, style }: { source: number; style: StyleProp<ImageStyle> }) {
  return <ExpoImage source={source} contentFit="contain" allowDownscaling enforceEarlyResizing transition={0} style={style} accessible={false} />;
}

export function PrismaticButton({ onPress, children, style, label, disabled = false, focusRef, hitSlop=12 }: {
  onPress: () => void; children: React.ReactNode; style?: StyleProp<ViewStyle>; label: string; disabled?: boolean; focusRef?: Ref<View>; hitSlop?: number | Insets;
}) {
  const reduced = useContext(ReducedPressContext);
  return <Pressable ref={focusRef} accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} style={style} hitSlop={hitSlop} pressRetentionOffset={16}>
    {({ pressed }) => <Animated.View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', opacity: disabled ? 0.35 : pressed && reduced ? 0.78 : 1,
      transform: [{ scale: pressed && !disabled && !reduced ? 0.97 : 1 }], transitionProperty: reduced ? 'opacity' : 'transform', transitionDuration: 120, transitionTimingFunction: PRESS_EASE_OUT }}>{children}</Animated.View>}
  </Pressable>;
}

function HeaderArt({whiteTop=190}:{whiteTop?:number}) {
  return <View pointerEvents="none" style={{position:'absolute',left:0,top:whiteTop,width:393,height:852-whiteTop,backgroundColor:'#FFFFFF',borderTopLeftRadius:32,borderTopRightRadius:32}} />;
}

export function PrismaticGameProfile(props: PrismaticGameProfileProps) {
  const window = useWindowDimensions();
  useEffect(()=>{
    // This app's installed native binary uses application-owned status styling.
    // Reassert only on profile mount/foreground; no router/controller override.
    StatusBar.setStyle('light',false);
    const listener=AppState.addEventListener('change',state=>{if(state==='active')StatusBar.setStyle('light',false);});
    return ()=>listener.remove();
  },[]);
  const [viewport, setViewport] = useState<{ width: number; height: number }>();
  const size = viewport ?? window;
  const scale = Math.min(size.width / 393, size.height / 852);
  const [sourceMetrics,setSourceMetrics]=useState(()=>estimateSourceMetrics(window.fontScale));
  const sourceLayout=useMemo(()=>createSourceGeometry(sourceMetrics),[sourceMetrics]);
  const sourceGeometry=useSharedValue(sourceLayout);
  useLayoutEffect(()=>{sourceGeometry.set(sourceLayout);},[sourceGeometry,sourceLayout]);
  const measureSource=useCallback((key:SourceMetric,value:number)=>{
    if(!Number.isFinite(value)||value<=0)return;
    setSourceMetrics(old=>Math.abs(old[key]-value)<0.1?old:{...old,[key]:value});
  },[]);
  const sourcePlayHeight=Math.max(44,Math.ceil((sourceMetrics.playHeight+24)/4)*4);
  const sourcePlayWidth=Math.max(74,Math.ceil(sourceMetrics.playWidth+45));
  const [surface, setSurface] = useState<Surface>();
  const [, setObjectIndex] = useState(1);
  const [playerContentReady,setPlayerContentReady] = useState(false);
  const cardSeal=useSharedValue<PlayerSealLayout>({x:286.5,scale:1.05,identity:initialIdentityLayout()});
  const playerBackWidth=Math.min(345,208+Math.max(0,window.fontScale-1)*137);
  const [gpuReady, setGpuReady] = useState(false);
  const [localReduced, setLocalReduced] = useState(false);
  const [friendName, setFriendName] = useState('');
  const [friends, setFriends] = useState<string[]>([]);
  const [savedGame, setSavedGame] = useState(false);
  const statisticsFocus = useRef<View>(null);
  const panelFocus = useRef<Text>(null);
  const playerFocus = useRef<Text>(null);
  const carouselFocus = useRef<View>(null);
  const focusGeneration = useRef(0);
  const focusedPanel = useRef<Surface | undefined>(undefined);
  useEffect(() => { if (!surface) focusedPanel.current=undefined; },[surface]);
  const mountInteractive = useCallback((kind: 'object' | 'stats', index: number) => {
    focusGeneration.current += 1; setObjectIndex(index); setSurface(kind);
  }, []);
  const closeInteractive = useCallback((kind: 'object' | 'stats', stillCurrent: () => boolean) => {
    const token = focusGeneration.current; setSurface(undefined);
    requestAnimationFrame(() => {
      if (!stillCurrent() || token !== focusGeneration.current || AppState.currentState === 'background' || AppState.currentState === 'inactive') return;
      const target = kind === 'object' ? carouselFocus.current : statisticsFocus.current;
      if (target) AccessibilityInfo.sendAccessibilityEvent(target,'focus');
    });
  }, []);
  const scene = usePrismaticScene({ ...props, localReduced, scale, sourceGeometry, onInspect: () => open('object'), onInteractiveBegin: mountInteractive, onInteractiveClose: closeInteractive });
  const { frame, detail, detailContent, objectOrigin, detailGeneration, detailDragging, catchStrength:cardCatch, cardTiltX, cardTiltY, foreground, detailPresented, detailKind, detailIndex } = scene;
  const selected = scene.selected;
  const open = (next: Surface) => {
    focusGeneration.current += 1;
    setObjectIndex(selected); setSurface(next); scene.open(next === 'stats' || next === 'object', next === 'object' ? selected : undefined);
  };
  const close = useCallback(() => {
    const focusToken = focusGeneration.current;
    const closingKind=surface ?? (scene.galleryKind === 1 ? 'object' : 'stats');
    if (!surface && scene.galleryPanel) { setObjectIndex(1); setSurface(scene.galleryKind === 1 ? 'object' : 'stats'); }
    scene.close(() => {
      const generationToken = detailGeneration.get();
      setSurface(undefined);
      requestAnimationFrame(() => {
        if (generationToken !== detailGeneration.get() || detailDragging.get() || focusToken !== focusGeneration.current || AppState.currentState === 'background' || AppState.currentState === 'inactive') return;
        const target = closingKind === 'object' ? carouselFocus.current : statisticsFocus.current;
        if (target) AccessibilityInfo.sendAccessibilityEvent(target, 'focus');
      });
    });
  }, [surface, scene, detailGeneration, detailDragging]);
  const player = surface === 'object' || (!surface && scene.galleryKind === 1);
  const compact = surface === 'stats' || (!surface && scene.galleryKind === 2);
  const anySurface = surface != null || scene.galleryPanel;
  const rendererReady = useCallback((ready: boolean) => setGpuReady(ready), []);
  const stagePosition = { left: (size.width - 393) / 2, top: (size.height - 852) / 2, transform: [{ scale }] };
  const panelProgress = useDerivedValue(() => Math.max(0,Math.min(1,surface === 'stats' || surface === 'object' ? detail.get() : frame.get().panel)));
  const proxyOrigin = useDerivedValue(() => surface === 'object' ? objectOrigin.get() : sourceBadgeOrigin(1,1,sourceGeometry.get()));
  const statisticsPose = useAnimatedStyle(() => ({ opacity: (surface === 'stats' ? detail.get() : frame.get().panelKind === 2 ? frame.get().panel : 0) > 0 ? 0 : 1 }));
  const compactContent = useAnimatedStyle(() => {
    const opacity = surface === 'stats' || surface === 'object' ? detailContent.get() : galleryContentOpacity(frame.get().time,frame.get().reduced);
    return { opacity, transform: [{ translateY: 3*(1-opacity) }] };
  });
  const playerFrame = useDerivedValue<PlayerCardScene>(() => {
    const f=frame.get(); const manual=detailPresented.get() && detailKind.get() === 1;
    const progress=Math.max(0,Math.min(1,manual ? detail.get() : f.panelKind === 1 ? f.panel : 0));
    const origin=manual ? objectOrigin.get() : sourceBadgeOrigin(1,1,sourceGeometry.get());
    const tilt=manual ? {x:cardTiltX.get(),y:cardTiltY.get()} : galleryPlayerTilt(f.time);
    return { progress,index:manual ? detailIndex.get() : Math.max(0,Math.min(2,Math.round(f.rail))),content:progress <= 0 ? 0 : manual ? detailContent.get() : f.panelKind === 1 ? galleryContentOpacity(f.time,f.reduced) : 0,
      catchStrength:f.reduced || progress<=0 ? 0 : manual ? Math.max(0,cardCatch.get()) : f.panelKind===1 ? galleryCatchStrength(f.time) : 0,
      peelAvoidance:peelIdentityAvoidance(progress,f.reduced,sourceGeometry.get(),cardSeal.get().identity),sourceX:origin.x,sourceY:origin.y,sourceSize:origin.size,sealX:cardSeal.get().x,sealScale:cardSeal.get().scale,tiltX:f.reduced ? 0 : tilt.x,tiltY:f.reduced ? 0 : tilt.y,reduced:f.reduced,active:foreground.get() && progress > 0 };
  });
  useAnimatedReaction(() => playerFrame.get().content >= 0.999,(ready,previous) => {
    if (ready !== previous) scheduleOnRN(setPlayerContentReady,ready);
  });
  const playerDim = useAnimatedStyle(() => ({opacity:0.62*playerFrame.get().progress}));
  const playerControls = useAnimatedStyle(() => { const content=playerFrame.get().content; return {opacity:content,transform:[{translateY:playerFrame.get().reduced ? 0 : 2*(1-content)}]}; });
  const statisticsProxy = useAnimatedStyle(() => ({ opacity:panelProgress.get()>0?Math.max(0,1-3*panelProgress.get()):0,transform:[{translateX:(196.5-sourceGeometry.get().statistics.left-sourceGeometry.get().statistics.width/2)*panelProgress.get()},{translateY:(356-sourceGeometry.get().statistics.top)*panelProgress.get()}] }));
  const focusCommittedPanel = useCallback((token: number) => {
    if (token !== detailGeneration.get() || detailDragging.get() || detail.get() < 0.999 || detailContent.get() < 0.999
      || (surface !== 'stats' && surface !== 'object') || AppState.currentState === 'background' || AppState.currentState === 'inactive') return;
    if (focusedPanel.current === surface) return;
    focusedPanel.current=surface;
    const target=surface === 'object' ? playerFocus.current : panelFocus.current;
    if (target) AccessibilityInfo.sendAccessibilityEvent(target,'focus');
  }, [detailGeneration, detailDragging, detail, detailContent, surface]);
  useAnimatedReaction(() => ({ ready: (surface === 'stats' || surface === 'object') && !detailDragging.get() && detail.get() >= 0.999 && detailContent.get() >= 0.999, token: detailGeneration.get() }),
    (current, previous) => { if (current.ready && (!previous?.ready || previous.token !== current.token)) scheduleOnRN(focusCommittedPanel,current.token); });
  useEffect(() => {
    if (!anySurface) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => { close(); return true; });
    return () => sub.remove();
  }, [anySurface, scene, close]);
  const demo = (title: string, message: string) => Alert.alert(title, message, [{ text: 'Done' }]);
  return <ReducedPressContext.Provider value={scene.reduced}><View style={styles.root} onLayout={({ nativeEvent: { layout } }) => setViewport(old => old?.width === layout.width && old.height === layout.height ? old : { width: layout.width, height: layout.height })}>
    <StatusBar style="light" />
    <View style={[styles.stage, stagePosition]} accessibilityViewIsModal={player}>
    <View style={StyleSheet.absoluteFill} accessibilityElementsHidden={anySurface} importantForAccessibility={anySurface ? 'no-hide-descendants' : 'auto'}>
      <ExpoImage source={ART.landscape} contentFit="cover" contentPosition={{left:'50%',top:'44%'}} allowDownscaling enforceEarlyResizing transition={0} style={styles.landscape} accessible={false} />
      <HeaderArt whiteTop={sourceLayout.header.whiteTop} />
      <PrismaticButton style={[styles.utility,{left:24}]} hitSlop={0} label="Profile settings" onPress={()=>open('settings')}><View style={styles.utilitySurface}><MaterialCommunityIcons name="cog" size={24} color="#FFFFFF" /></View></PrismaticButton>
      <PrismaticButton style={[styles.utility,{right:24}]} hitSlop={0} label="Add a local friend" onPress={()=>open('friend')}><View style={styles.utilitySurface}><MaterialCommunityIcons name="account-plus" size={24} color="#FFFFFF" /></View></PrismaticButton>
      <View style={[styles.balanceBar,{top:sourceLayout.balances.top,height:sourceLayout.balances.height}]}>
        {([{asset:ART.ruby,value:'72,498',color:'#D72D7A',label:'Ruby'},{asset:ART.coin,value:'15,380',color:'#A96B00',label:'Coin'}] as const).map(balance=><View key={balance.label} style={[styles.balanceCell,{flexDirection:sourceLayout.balances.stacked?'column':'row',gap:sourceLayout.balances.stacked?4:8}]} accessible accessibilityLabel={`${balance.label} balance, ${balance.value}`}>
          <Picture source={balance.asset} style={{width:sourceLayout.balances.iconSize,height:sourceLayout.balances.iconSize}} />
          <Text onLayout={e=>{measureSource('balanceHeight',e.nativeEvent.layout.height);measureSource('balanceWidth',e.nativeEvent.layout.width);}} style={[styles.currencyValue,{color:balance.color}]}>{balance.value}</Text>
        </View>)}
        <View pointerEvents="none" style={[styles.balanceDivider,{height:sourceLayout.balances.height-24}]} />
      </View>
      <Text onLayout={e=>measureSource('sectionHeight',e.nativeEvent.layout.height)} style={[styles.collectionTitle,{top:sourceLayout.collection.top}]} accessibilityRole="header">Collection</Text>
      <Animated.View style={[styles.statistics,sourceLayout.statistics,statisticsPose]}><PrismaticButton focusRef={statisticsFocus} hitSlop={0} style={{flex:1}} label="Statistics. Open profile statistics" onPress={()=>open('stats')}><View style={[styles.statisticsSurface,{width:sourceLayout.statistics.width}]}><MaterialCommunityIcons name="chart-donut" size={18} color="#FFFFFF"/><Text onLayout={e=>{measureSource('statisticsHeight',e.nativeEvent.layout.height);measureSource('statisticsWidth',e.nativeEvent.layout.width);}} style={styles.statisticsText}>Statistics</Text></View></PrismaticButton></Animated.View>
      <PrismaticButton style={[styles.arrow,{left:L.arrows.left,top:sourceLayout.arrows.top}]} hitSlop={0} label="Previous badge" disabled={selected===0} onPress={()=>scene.select(selected-1)}><MaterialCommunityIcons name="chevron-left" size={24} color="#8BA8B7"/></PrismaticButton>
      <PrismaticButton style={[styles.arrow,{left:L.arrows.right,top:sourceLayout.arrows.top}]} hitSlop={0} label="Next badge" disabled={selected===2} onPress={()=>scene.select(selected+1)}><MaterialCommunityIcons name="chevron-right" size={24} color="#8BA8B7"/></PrismaticButton>
      <View style={[styles.sectionDivider,{top:sourceLayout.lastPlayed.separatorTop}]} />
      <Text onLayout={e=>measureSource('lastHeight',e.nativeEvent.layout.height)} style={[styles.lastPlayed,{top:sourceLayout.lastPlayed.top}]} accessibilityRole="header">Last played</Text>
      <View style={[styles.gameCard,{top:sourceLayout.game.top,height:sourceLayout.game.height}]}>
        <Picture source={ART.game} style={styles.gameImage}/>
        <View onLayout={e=>measureSource('gameCopyHeight',e.nativeEvent.layout.height)} style={styles.gameCopy}><Text style={styles.gameTitle}>Neon Run</Text><View style={styles.gameMeta}><View style={styles.metric}><MaterialCommunityIcons name="thumb-up" size={12} color="#7C96A4"/><Text style={styles.metadata}>87%</Text></View><View style={styles.metric}><MaterialCommunityIcons name="play" size={12} color="#7C96A4"/><Text style={styles.metadata}>315.6K</Text></View></View></View>
        <PrismaticButton style={[styles.play,{width:sourcePlayWidth,height:sourcePlayHeight}]} hitSlop={0} label="Play Neon Run local preview" onPress={()=>open('game')}><View style={[styles.playSurface,{width:sourcePlayWidth,height:sourcePlayHeight}]}><MaterialCommunityIcons name="play" size={17} color="#FFFFFF"/><Text onLayout={e=>{measureSource('playHeight',e.nativeEvent.layout.height);measureSource('playWidth',e.nativeEvent.layout.width);}} style={styles.playText}>Play</Text></View></PrismaticButton>
      </View>
      <View style={[styles.tabBar,{top:sourceLayout.tabs.top,height:sourceLayout.tabs.height}]}>{(['Home','Profile','Games','Store'] as const).map((title,i)=><PrismaticButton key={title} style={[styles.tabButton,{height:sourceLayout.tabs.cellHeight}]} hitSlop={0} label={title==='Profile'?'Profile, current tab':`${title}, local preview`} onPress={()=>i===1?close():open(i===0?'home':i===2?'game':'store')}><View style={styles.tabContent}><View style={[styles.tabIcon,i===1&&styles.tabIconActive]}><MaterialCommunityIcons name={(['home','account','gamepad-variant','cart'] as const)[i]} size={24} color={i===1?'#00ABD9':'#8BA1AD'}/></View><Text onTextLayout={e=>{const line=e.nativeEvent.lines[0];if(line&&i===1)measureSource('tabWidth',line.width);}} onLayout={e=>measureSource('tabHeight',e.nativeEvent.layout.height)} style={[styles.tabText,{color:i===1?'#082F43':'#8BA1AD'}]}>{title}</Text></View></PrismaticButton>)}</View>
      <Text accessible accessibilityLabel={`Selected ${BADGES[selected].name} badge, ${selected + 1} of 3`} testID="prismatic-selected" style={styles.qaMarker}>Selected {BADGES[selected].name}</Text>
      <Text accessible accessibilityLabel={`Renderer ${gpuReady && props.renderer !== 'skia' ? 'WebGPU' : 'Skia'}`} testID="prismatic-renderer" style={styles.qaMarker}>Renderer {gpuReady && props.renderer !== 'skia' ? 'WebGPU' : 'Skia'}</Text>
    </View>
    <PrismaticRail scene={scene} renderer={props.renderer ?? 'auto'} onRenderer={rendererReady} inspect={() => open('object')} targetRef={carouselFocus} player={playerFrame} hidden={anySurface} />
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill,{zIndex:10,backgroundColor:'#161127'},playerDim]} />
    <PlayerCardSubstrate frame={playerFrame} />
    <PlayerCardIdentity frame={playerFrame} hidden={anySurface && !player} focusRef={playerFocus} cardContentAccessible={playerContentReady} seal={cardSeal} sourceGeometry={sourceGeometry} onSourceMeasure={measureSource} caption={selected===1?'Skills':BADGES[selected].name} hasScore={selected===1} />
    {player ? <GestureDetector gesture={scene.panelGesture}><View collapsable={false} style={[StyleSheet.absoluteFill,{zIndex:2000}]} pointerEvents="box-none">
      <Pressable style={StyleSheet.absoluteFill} onPress={event => {
        const {locationX:x,locationY:y}=event.nativeEvent; const f=playerFrame.get(); const p=f.progress;
        const bounds=peelSurfaceBounds(p,f.sourceX,f.sourceY,f.sourceSize,f.reduced);
        if (x<bounds.x-16||x>bounds.x+bounds.width+16||y<bounds.y-16||y>bounds.y+bounds.height+16) close();
      }} accessible={false} />
      <Animated.View style={[styles.playerBack,{left:(393-playerBackWidth)/2,width:playerBackWidth},playerControls]} accessibilityElementsHidden={!playerContentReady} importantForAccessibility={playerContentReady?'auto':'no-hide-descendants'}><PrismaticButton style={{flex:1}} label="Back to profile" onPress={close}><LinearGradient colors={['#2DC5EE','#0875D4']} style={[styles.playerBackSurface,{width:playerBackWidth}]}><MaterialCommunityIcons name="chevron-down" size={22} color="#FFFFFF"/><Text style={styles.playerBackText}>Back to profile</Text></LinearGradient></PrismaticButton></Animated.View>
      <Text testID="prismatic-player-card" accessible accessibilityLabel="Player card preview. Drag to tilt. Pull down to return to profile." style={styles.qaMarker}>Player card</Text>
    </View></GestureDetector> : null}
    </View>
    {compact ? <GestureDetector gesture={scene.panelGesture}><View onLayout={()=>StatusBar.setStyle('light',false)} collapsable={false} style={[styles.stage, stagePosition]} pointerEvents="box-none" accessibilityViewIsModal>
      <Pressable style={StyleSheet.absoluteFill} onPress={close} accessibilityRole="button" accessibilityLabel="Close profile details" />
      <PrismaticDetailMorph sourceGeometry={sourceGeometry} progress={panelProgress} origin={proxyOrigin} object={false} originColor="#00BCE9" />
      <Animated.View style={[styles.statisticsProxy,sourceLayout.statistics,statisticsProxy]} pointerEvents="none"><MaterialCommunityIcons name="chart-donut" size={18} color="#FFFFFF" /><Text style={styles.statisticsText}>Statistics</Text></Animated.View>
        <Animated.View style={[styles.compactInner, compactContent]}>
          <View style={styles.compactHeader}><Text ref={panelFocus} style={styles.compactTitle} accessibilityRole="header">Your skills</Text><PrismaticButton style={styles.compactClose} label="Close profile details" onPress={close}><MaterialCommunityIcons name="close" size={21} color="#1A4B71" /></PrismaticButton></View>
          <><View style={styles.compactStats}>{([{ title: 'Level', value: '75', image: ART.statsLevel, color: '#0864B8' }, { title: 'Games', value: '28', image: ART.statsGames, color: '#9622CF' }, { title: 'Time', value: '9h23m', image: ART.statsTime, color: '#587B00' }] as const).map(item => <View key={item.title} style={styles.compactTile} accessible accessibilityLabel={`${item.title}, ${item.value}`}><Picture source={item.image} style={{ width: 44, height: 44 }} /><Text style={styles.statLabel} maxFontSizeMultiplier={1.3}>{item.title}</Text><Text style={[styles.compactValue, { color: item.color }]} maxFontSizeMultiplier={1.2}>{item.value}</Text></View>)}</View><Text style={styles.compactNote}>Profile statistics · Read-only demo</Text></>

        </Animated.View>
    </View></GestureDetector> : null}
    <Modal onShow={()=>StatusBar.setStyle('light',false)} onDismiss={()=>StatusBar.setStyle('light',false)} visible={surface != null && !compact && !player} transparent animationType={scene.reduced ? 'none' : 'fade'} onRequestClose={close} statusBarTranslucent>
      <View style={styles.modalRoot}><Pressable style={StyleSheet.absoluteFill} onPress={close} accessibilityLabel="Close profile panel" accessibilityRole="button" /><View style={styles.panel} accessibilityViewIsModal>
        <View style={styles.panelHeader}><Text style={styles.panelTitle} accessibilityRole="header">{surface === 'stats' ? 'Your skills' : surface === 'object' ? `${BADGES[selected].name} badge` : surface === 'settings' ? 'Profile settings' : surface === 'friend' ? 'Local friends' : surface === 'game' ? 'Neon Run' : surface === 'home' ? 'Home preview' : 'Badge previews'}</Text><PrismaticButton style={styles.close} label="Close profile panel" onPress={close}><MaterialCommunityIcons name="close" size={22} color="#1A4B71" /></PrismaticButton></View>
        <ScrollView contentContainerStyle={styles.panelContent} keyboardShouldPersistTaps="handled">
          {surface === 'settings' ? <><View style={styles.setting}><Text style={styles.body}>Reduce motion</Text><Switch value={scene.reduced} disabled={!!props.previewReducedMotion || (scene.reduced && !localReduced)} onValueChange={setLocalReduced} accessibilityLabel="Reduce motion for this profile" /></View><Text style={styles.note}>This local preference also respects your system’s Reduce Motion setting.</Text></> : null}
          {surface === 'friend' ? <><TextInput value={friendName} onChangeText={setFriendName} autoCapitalize="none" placeholder="Username" placeholderTextColor="#607080" accessibilityLabel="Local friend's username" style={styles.input} /><PrismaticButton style={styles.formButton} label="Add friend locally" disabled={!friendName.trim()} onPress={() => { setFriends(old => [...new Set([...old, friendName.trim()])]); setFriendName(''); }}><Text style={styles.formButtonText}>Add locally</Text></PrismaticButton>{friends.map(name => <Text key={name} style={styles.body}>{name} · Saved locally</Text>)}<Text style={styles.note}>No friend request is sent from this demo.</Text></> : null}
          {surface === 'game' ? <><Picture source={ART.game} style={{ width: '100%', height: 160, borderRadius: 18 }} /><Text style={styles.body}>87% likes · 315.6K plays</Text><Text style={styles.note}>Local game preview. No external game is launched.</Text><PrismaticButton style={styles.formButton} label={savedGame ? 'Remove saved game' : 'Save game locally'} onPress={() => setSavedGame(value => !value)}><Text style={styles.formButtonText}>{savedGame ? 'Saved locally' : 'Save locally'}</Text></PrismaticButton></> : null}
          {surface === 'store' ? <><Text style={styles.note}>Preview an object without spending rubies or coins.</Text>{BADGES.map((badge, index) => <PrismaticButton key={badge.name} style={styles.storeRow} label={`Preview ${badge.name} badge without a purchase`} onPress={() => { scene.close(() => { setSurface(undefined); scene.select(index); }); }}><View style={styles.storeItem}><Picture source={badge.image} style={{ width: 44, height: 44 }} /><Text style={styles.body}>{badge.name}</Text><Text style={styles.link}>Preview</Text></View></PrismaticButton>)}</> : null}
          {surface === 'home' ? <><Text style={styles.body}>Everen’s local profile</Text><Text style={styles.note}>Skills 1.500 · Ruby balance 72,498 · Coin balance 15,380</Text><PrismaticButton style={styles.formButton} label="Return to profile" onPress={close}><Text style={styles.formButtonText}>View profile</Text></PrismaticButton><PrismaticButton style={styles.formButton} label="Show local demo information" onPress={() => demo('Local profile', 'All previews stay on this device. No purchases or external game launch occur.')}><Text style={styles.formButtonText}>About this preview</Text></PrismaticButton></> : null}
        </ScrollView>
      </View></View>
    </Modal>
  </View></ReducedPressContext.Provider>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#FFFFFF', overflow: 'hidden' }, stage: { position: 'absolute', width: 393, height: 852 },
  landscape:{position:'absolute',left:0,top:0,width:393,height:218},
  utility:{position:'absolute',top:66,width:44,height:44},utilitySurface:{width:44,height:44,borderRadius:22,backgroundColor:'#FFFFFF3D',borderWidth:1,borderColor:'#FFFFFF5C',alignItems:'center',justifyContent:'center'},
  balanceBar:{position:'absolute',left:24,top:262,width:345,height:56,paddingHorizontal:16,borderRadius:20,backgroundColor:'#F0F7FA',flexDirection:'row',alignItems:'center',gap:16},
  balanceCell:{flex:1,alignItems:'center',justifyContent:'center',gap:8},balanceDivider:{position:'absolute',left:172.5,top:12,width:1,height:32,backgroundColor:'#D8E7EE'},currencyValue:{fontFamily:DISPLAY,fontSize:19,lineHeight:24,fontWeight:'800',letterSpacing:-0.5,includeFontPadding:false},
  collectionTitle:{position:'absolute',left:24,top:344,fontFamily:DISPLAY,fontSize:20,lineHeight:24,fontWeight:'800',letterSpacing:-0.55,color:'#092D3D',includeFontPadding:false},
  statistics:{position:'absolute',left:L.statistics.left,top:L.statistics.top,width:L.statistics.width,height:L.statistics.height},statisticsSurface:{flex:1,width:118,borderRadius:16,backgroundColor:'#00BCE9',flexDirection:'row',alignItems:'center',justifyContent:'center',gap:8,paddingHorizontal:16,shadowColor:'#0CB4E6',shadowOffset:{width:0,height:5},shadowOpacity:0.17,shadowRadius:6,elevation:3},statisticsText:{fontFamily:DISPLAY,color:'#FFFFFF',fontSize:13,lineHeight:18,fontWeight:'800',includeFontPadding:false},
  arrow:{position:'absolute',top:L.arrows.top,width:44,height:44,borderRadius:22,backgroundColor:'#F0F7FA'},sectionDivider:{position:'absolute',left:24,top:610,width:345,height:1,backgroundColor:'#E7F0F4'},
  lastPlayed:{position:'absolute',left:24,top:627,width:345,fontFamily:DISPLAY,fontSize:20,lineHeight:24,fontWeight:'800',letterSpacing:-0.55,color:'#092D3D',includeFontPadding:false},
  gameCard:{position:'absolute',left:24,top:663,width:345,height:64,flexDirection:'row',alignItems:'center',gap:12},gameImage:{width:64,height:64,borderRadius:18},gameCopy:{flex:1,gap:4},gameTitle:{fontFamily:DISPLAY,fontSize:16,lineHeight:24,fontWeight:'800',letterSpacing:-0.3,color:'#092D3D',includeFontPadding:false},gameMeta:{flexDirection:'row',alignItems:'center',gap:8},metric:{flexDirection:'row',alignItems:'center',gap:2},metadata:{fontSize:12,lineHeight:16,fontWeight:'600',color:'#7C96A4',includeFontPadding:false},
  play:{width:74,height:44},playSurface:{width:74,height:44,borderRadius:16,backgroundColor:'#00BCE9',flexDirection:'row',alignItems:'center',justifyContent:'center',gap:4,paddingHorizontal:12,shadowColor:'#00A9E5',shadowOffset:{width:0,height:4},shadowOpacity:0.17,shadowRadius:5,elevation:3},playText:{fontSize:14,lineHeight:20,color:'#FFFFFF',fontWeight:'800',includeFontPadding:false},
  tabBar:{position:'absolute',left:24,top:746,width:345,height:72,borderTopWidth:1,borderTopColor:'#E7F0F4',backgroundColor:'#FFFFFF',flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:4},tabButton:{width:83.25,height:64},tabContent:{alignItems:'center',justifyContent:'center',gap:4},tabIcon:{width:48,height:32,borderRadius:12,alignItems:'center',justifyContent:'center'},tabIconActive:{backgroundColor:'#DCF6FC'},tabText:{fontFamily:DISPLAY,fontSize:12,lineHeight:16,fontWeight:'700',includeFontPadding:false},
  qaMarker: { position: 'absolute', width: 1, height: 1, color: 'transparent', fontSize: 1, left: 0, top: 0 },
  playerBack:{position:'absolute',left:92.5,top:704,width:208,height:48},playerBackSurface:{flex:1,width:208,borderRadius:24,flexDirection:'row',alignItems:'center',justifyContent:'center',gap:7,borderWidth:1,borderColor:'#8EE5FD'},
  playerBackText:{color:'#FFFFFF',fontSize:15,fontWeight:'700',includeFontPadding:false},
  compactInner: { position: 'absolute', left: 70, top: 356, width: 253, height: 178, paddingHorizontal: 12 }, compactHeader: { height: 43, flexDirection: 'row', alignItems: 'center', paddingLeft: 4 }, compactTitle: { fontSize: 19, fontWeight: '800', color: '#123B55', flex: 1 }, compactClose: { width: 44, height: 44 }, compactStats: { flexDirection: 'row', gap: 7 }, compactTile: { flex: 1, borderRadius: 16, paddingVertical: 7, alignItems: 'center', backgroundColor: '#FFFFFF', gap: 3 }, compactValue: { fontSize: 18, fontWeight: '800', includeFontPadding: false }, compactNote: { fontSize: 10, lineHeight: 17, color: '#536B7D', textAlign: 'center', marginTop: 5 }, objectRead: { flexDirection: 'row', gap: 12, alignItems: 'center' }, objectProxy: { position: 'absolute', left: 139.5, top: 386, width: 114, height: 126, transformOrigin: 'top left', zIndex: 2 }, statisticsProxy:{position:'absolute',left:L.statistics.left,top:L.statistics.top,width:L.statistics.width,height:L.statistics.height, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  modalRoot: { flex: 1, justifyContent: 'center', padding: 20, backgroundColor: '#102E4D22' }, panel: { maxHeight: '80%', borderRadius: 24, backgroundColor: '#F4FBFF', borderColor: '#CFDFEC', borderWidth: 1, shadowColor: '#183F59', shadowOpacity: 0.2, shadowRadius: 14, shadowOffset: { width: 0, height: 6 } }, panelHeader: { paddingLeft: 18, minHeight: 52, flexDirection: 'row', alignItems: 'center' }, panelTitle: { flex: 1, fontSize: 20, fontWeight: '800', color: '#102E45' }, close: { width: 48, height: 48 }, panelContent: { padding: 16, paddingTop: 4, gap: 14 }, statsRow: { flexDirection: 'row', gap: 8 }, statTile: { flex: 1, borderRadius: 18, backgroundColor: '#FFFFFF', paddingVertical: 16, paddingHorizontal: 4, alignItems: 'center', gap: 8 }, statLabel: { fontSize: 13, fontWeight: '500', color: '#536B7D' }, statValue: { fontSize: 21, fontWeight: '800' }, note: { fontSize: 13, lineHeight: 19, color: '#536B7D' }, body: { fontSize: 16, lineHeight: 22, color: '#183E55' }, setting: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, input: { borderRadius: 12, borderWidth: 1, borderColor: '#9DB6C8', padding: 12, color: '#183E55', fontSize: 16, backgroundColor: '#FFFFFF' }, formButton: { height: 44, backgroundColor: '#006BC8', borderRadius: 14 }, formButtonText: { fontSize: 15, fontWeight: '600', color: '#FFFFFF' }, storeRow: { height: 60 }, storeItem: { flexDirection: 'row', alignItems: 'center', gap: 12, width: '100%' }, link: { marginLeft: 'auto', color: '#0063B8', fontSize: 14, fontWeight: '700' },
});
