import { BlurMask, Canvas, Fill, Group, RoundedRect, Shader, Skia } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { Image, StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import { WALLET_ICON } from '../characters';
import { Caption, Centered, Frame, Heading, Leading, Panel, Reveal, SFSymbol, formatAmount, useEntrance, useSettle } from '../primitives';
import { WALLET } from '../shaders';
import { hex, palette, white } from '../theme';

/** Step 1: the wallet card (275 × 375 at x 66.7, y 161.7) over "Your Wallet. / Your Strategy". */
export function WalletScene({ reduced }: { reduced: boolean }) {
  const card = useEntrance(reduced, { spring: [0.75, 0.9] });
  const cardStyle = useAnimatedStyle(() => ({
    opacity: card.get(),
    transform: [{ translateY: reduced ? 0 : (1 - card.get()) * 46 }, { scale: reduced ? 1 : 0.94 + 0.06 * card.get() }],
  }));
  const balance = useSettle(12_035, 12_485.32, 0.89, 0.06, 0.45, 2.2, reduced);
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Frame x={66.7 + 137.5} y={161.7 + 187.5} width={275} height={375}>
        <Animated.View style={[{ transformOrigin: 'top' }, cardStyle]}>
          <WalletCard balance={balance} />
        </Animated.View>
      </Frame>
      <Heading first="Your Wallet." second="Your Strategy" reduced={reduced} />
      <Centered x={201} y={681.7}>
        <Reveal reduced={reduced} delay={0.35} y={6}>
          <Caption text={'Connect your wallet and access the tools\nyou need to manage your trades.'} />
        </Reveal>
      </Centered>
    </View>
  );
}

function WalletCard({ balance }: { balance: number }) {
  return (
    <Panel
      width={275}
      height={375}
      radius={32}
      fill={[hex('#1a1a1a'), hex('#161616'), hex('#121212')]}
      stroke={[white(0.09), white(0.03), white(0.07)]}
      shadow={{ color: '#000', opacity: 0.9, radius: 24, y: 16 }}
      style={{ overflow: 'visible' }}>
      <View accessible accessibilityLabel={`Secure wallet connection. Total wallet balance ${Math.floor(balance)} dollars`} style={StyleSheet.absoluteFill}>
        <Frame x={21.8} y={34.8} width={16} height={16} style={{ alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '-45deg' }] }}>
          <SFSymbol name="powerplug" size={13} />
        </Frame>
        <Leading left={35} y={34.8}>
          <Text style={{ fontSize: 13.6, fontWeight: '500', letterSpacing: -0.1, color: palette.ink }}>Secure Wallet Connection</Text>
        </Leading>
        <Frame x={243.8} y={33.8} width={31} height={31} style={{ borderRadius: 10, borderCurve: 'continuous', backgroundColor: hex('#141414'), borderWidth: 1, borderColor: white(0.13), alignItems: 'center', justifyContent: 'center' }}>
          <SFSymbol name="plus" size={11} weight="semibold" />
        </Frame>
        <View style={{ position: 'absolute', left: 6, top: 64, width: 263, height: 311, overflow: 'hidden', borderTopLeftRadius: 24, borderTopRightRadius: 24, borderBottomLeftRadius: 26, borderBottomRightRadius: 26, borderCurve: 'continuous' }}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={WALLET} uniforms={{ size: [263, 311] }} />
            </Fill>
          </Canvas>
          <ConnectRow />
          <View style={{ position: 'absolute', left: 1.5, top: 60.3 }}>
            <BalanceInset balance={balance} />
          </View>
          <View style={{ position: 'absolute', left: -0.5, top: 156 }}>
            <Holdings />
          </View>
        </View>
      </View>
    </Panel>
  );
}

function ConnectRow() {
  return (
    <View style={{ position: 'absolute', left: 0, top: 0, width: 263, height: 52 }}>
      <Frame x={17.3} y={25.3} width={14.6} height={14.6} style={{ borderRadius: 4, backgroundColor: white(0.88), alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: 10, height: 6, borderRadius: 2, backgroundColor: hex('#7d8ff4'), transform: [{ translateY: 2 }], alignItems: 'center', justifyContent: 'center' }}>
          <SFSymbol name="chevron.up" size={5} weight="bold" color="#fff" />
        </View>
      </Frame>
      <Leading left={31.6} y={25.3}>
        <Text style={{ fontSize: 12, letterSpacing: -0.05, color: '#fff' }}>Connect Any Wallet</Text>
      </Leading>
      <Centered x={209.6} y={25.3} box={140}>
        <View style={{ flexDirection: 'row' }}>
          <Logo fill="#fff">
            <View style={{ flexDirection: 'row', gap: 2.6, transform: [{ translateX: -1 }, { translateY: -0.5 }] }}>
              <View style={{ width: 3.4, height: 6.4, borderRadius: 1.7, backgroundColor: '#000' }} />
              <View style={{ width: 3.4, height: 6.4, borderRadius: 1.7, backgroundColor: '#000' }} />
            </View>
          </Logo>
          <Logo fill={hex('#1b1b1b')} overlap>
            <SFSymbol name="shield.fill" size={13} color={hex('#f04d22')} />
          </Logo>
          <Logo fill={hex('#5236f2')} overlap>
            <Text style={{ fontSize: 14, fontWeight: '900', fontStyle: 'italic', color: '#fff' }}>S</Text>
          </Logo>
          <Logo fill={hex('#101010')} overlap>
            <SFSymbol name="mountain.2.fill" size={9} color="#fff" />
          </Logo>
        </View>
      </Centered>
    </View>
  );
}

function Logo({ fill, overlap, children }: { fill: string; overlap?: boolean; children: React.ReactNode }) {
  return (
    <View style={{ width: 24.3, height: 24.3, borderRadius: 12.15, backgroundColor: fill, borderWidth: 1.6, borderColor: '#000', alignItems: 'center', justifyContent: 'center', marginLeft: overlap ? -4.1 : 0 }}>
      {children}
    </View>
  );
}

const AMOUNT: TextStyle = { fontSize: 18.5, fontWeight: '600', letterSpacing: -0.1, fontVariant: ['tabular-nums'] };

const INSET_GLOW = Skia.RRectXY(Skia.XYWHRect(0, 0, 257, 89), 26, 26);

function BalanceInset({ balance }: { balance: number }) {
  return (
    <View style={{ width: 257, height: 89 }}>
      <Canvas style={StyleSheet.absoluteFill}>
        <RoundedRect rect={INSET_GLOW} color="#000" />
        <Group clip={INSET_GLOW}>
          <RoundedRect rect={INSET_GLOW} color={hex('#8696ff', 0.28)} style="stroke" strokeWidth={4}>
            <BlurMask blur={2} style="normal" />
          </RoundedRect>
        </Group>
      </Canvas>
      <Frame x={16.7 + 22.2} y={20.7 + 23} width={48} height={48}>
        <Image source={WALLET_ICON} style={{ width: 48, height: 48 }} accessibilityIgnoresInvertColors />
      </Frame>
      <Leading left={75} y={33.3}>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 2.5 }}>
          <Text style={[AMOUNT, { color: hex('#8a8a8a') }]}>$</Text>
          <Text style={[AMOUNT, { color: palette.ink }]}>{formatAmount(balance, 2)}</Text>
        </View>
      </Leading>
      <Leading left={75} y={58}>
        <Text style={{ fontSize: 9, color: hex('#8c8c8c') }}>Total Wallet Balance</Text>
      </Leading>
    </View>
  );
}

function Holdings() {
  return (
    <View style={{ width: 260, height: 160, borderRadius: 26, borderCurve: 'continuous', backgroundColor: hex('#0a0a0a'), overflow: 'hidden' }}>
      <Centered x={133} y={23.3} box={200}>
        <Text style={{ fontSize: 9.2, color: hex('#e4e4e4') }}>Your holdings</Text>
      </Centered>
      <Frame x={19 + 114.7} y={43.6 + 23.35} width={229.3} height={46.7}>
        <Holding name="Tether" ticker="USDT" value="0.22%" tint={hex('#4fd1b5')} dim={false} />
      </Frame>
      <Frame x={19 + 114.7} y={100.6 + 23.35} width={229.3} height={46.7}>
        <Holding name="SushiSwap" ticker="SUSHI" value="5.12%" tint={hex('#d2647e')} dim />
      </Frame>
      <LinearGradient pointerEvents="none" colors={['transparent', hex('#0a0a0a', 0.55)]} locations={[0.6, 1]} style={StyleSheet.absoluteFill} />
    </View>
  );
}

function Holding({ name, ticker, value, tint, dim }: { name: string; ticker: string; value: string; tint: string; dim: boolean }) {
  return (
    <View style={{ width: 229.3, height: 46.7, borderRadius: 16, borderCurve: 'continuous', backgroundColor: hex('#1c1c1c'), opacity: dim ? 0.62 : 1 }}>
      <Frame x={22.6} y={23.35} width={25} height={25} style={{ borderRadius: 12.5, backgroundColor: hex('#262626'), alignItems: 'center', justifyContent: 'center' }}>
        {dim ? (
          <LinearGradient colors={[hex('#ff8fb0'), hex('#7c5cff')]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={{ width: 11, height: 7, borderRadius: 3.5, transform: [{ rotate: '-25deg' }] }} />
        ) : (
          <>
            <SFSymbol name="shield.fill" size={13} color={hex('#45d6b2')} style={{ transform: [{ rotate: '180deg' }] }} />
            <Text style={{ position: 'absolute', fontSize: 8, fontWeight: '800', color: '#000', transform: [{ translateY: -1 }] }}>T</Text>
          </>
        )}
      </Frame>
      <Leading left={42.6} y={23.35}>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 6 }}>
          <Text style={{ fontSize: 10.7, color: hex('#e2e2e2') }}>{name}</Text>
          <Text style={{ fontSize: 6.5, fontWeight: '500', color: hex('#5a5a5a') }}>{ticker}</Text>
        </View>
      </Leading>
      <Frame x={204.1} y={23.35} width={32.3} height={15.3} style={{ borderRadius: 5, backgroundColor: hex('#2a2a2a'), alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ fontSize: 8.2, fontWeight: '500', color: tint }}>{value}</Text>
      </Frame>
    </View>
  );
}
