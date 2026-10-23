import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { FadeIn, FadeOut, LayoutAnimationConfig } from 'react-native-reanimated';

type Glyph = keyof typeof MaterialCommunityIcons.glyphMap;
type Icon = keyof typeof Ionicons.glyphMap;
type Product = { label: string; color: string; glyph: Glyph };

type Props = {
  /** The destination's name; the page under it is the shop front when `index` is 0. */
  title: string;
  /** The destination's place in the menu, which picks what its shelf holds. */
  index: number;
  /** The destination's id: the tab with the same id is lit. */
  active: string;
  accent: string;
  insets: { top: number; bottom: number };
  onMenu: () => void;
  onTab: (id: string) => void;
};

const BACKGROUND = '#FAFCFE';
const INK = '#2B2B33';
const MUTE = '#A9B0BA';
const OUTLINE = '#EEF1F4';
const TAB_IDLE = '#B0B5BF';
const ALERT = '#F0443C';
const GLYPH = 'rgba(255,255,255,0.8)';
const BRAND_SKY = ['#7ED8F5', '#35A7F0'] as const;
const NIGHT = '#23232B';

const MARGIN = 20;
const GAP = 12;
const TILE = 112;
const TILE_RADIUS = 16;
const BRAND_WIDTH = 240;
const BRAND_HEIGHT = 130;
const DEAL_HEIGHT = 150;
const TABS = 50;
// The old destination fades out while the new one fades in.
const SWAP = 250;

const PRODUCTS: Product[] = [
  { label: 'Laptops', color: '#F6B8C8', glyph: 'laptop' },
  { label: 'Headphones', color: '#FBC813', glyph: 'headphones' },
  { label: 'Storage', color: '#F4A6A6', glyph: 'usb-flash-drive-outline' },
  { label: 'Tablets', color: '#BCF5C8', glyph: 'tablet' },
  { label: 'Watches', color: NIGHT, glyph: 'watch' },
  { label: 'Earbuds', color: '#BCF5C8', glyph: 'earbuds-outline' },
  { label: 'Cameras', color: '#F6B8C8', glyph: 'camera-outline' },
];

/** The bar's tabs. A tab opens the menu item that has its id. */
const BAR: { id: string; label: string; icon: Icon; lit: Icon }[] = [
  { id: 'home', label: 'Home', icon: 'home-outline', lit: 'home' },
  { id: 'cart', label: 'Cart', icon: 'cart-outline', lit: 'cart' },
  { id: 'wishlist', label: 'Wishlist', icon: 'heart-outline', lit: 'heart' },
  { id: 'account', label: 'Account', icon: 'person-outline', lit: 'person' },
];

function Tile({ product, size, style }: { product: Product; size: number; style: object }) {
  return (
    <View style={[styles.tile, style, { backgroundColor: product.color }]}>
      <MaterialCommunityIcons name={product.glyph} size={size} color={GLYPH} />
      <Text style={styles.tileLabel} numberOfLines={1}>
        {product.label}
      </Text>
    </View>
  );
}

function Heading({ title }: { title: string }) {
  return (
    <View style={styles.heading}>
      <Text style={styles.headingTitle} numberOfLines={1}>
        {title}
      </Text>
      <Text style={styles.viewAll}>View all</Text>
    </View>
  );
}

/** The shop front: three shelves, each running off the right edge or filling the width. */
function Front() {
  return (
    <>
      <Heading title="Recommended" />
      <View style={styles.shelf}>
        {PRODUCTS.slice(0, 4).map((product) => (
          <Tile key={product.label} product={product} size={48} style={styles.square} />
        ))}
      </View>
      <Heading title="Popular Brand" />
      <View style={styles.shelf}>
        <LinearGradient colors={BRAND_SKY} style={[styles.tile, styles.brand]}>
          <MaterialCommunityIcons name="tablet" size={64} color={GLYPH} />
          <Text style={styles.brandName}>Northwind</Text>
        </LinearGradient>
        <Tile product={PRODUCTS[4]} size={56} style={styles.brand} />
      </View>
      <Heading title="Daily Deals" />
      <View style={styles.shelf}>
        <Tile product={PRODUCTS[5]} size={56} style={styles.deal} />
        <Tile product={PRODUCTS[6]} size={56} style={styles.deal} />
      </View>
    </>
  );
}

/** Any other destination: its name over four tiles. */
function Shelf({ title, index }: { title: string; index: number }) {
  const picks = [0, 1, 2, 3].map((slot) => PRODUCTS[(index * 2 + slot) % PRODUCTS.length]);
  return (
    <>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      <Text style={styles.count}>{picks.length} items</Text>
      {[0, 2].map((row) => (
        <View key={row} style={[styles.shelf, styles.grid]}>
          <Tile product={picks[row]} size={56} style={styles.deal} />
          <Tile product={picks[row + 1]} size={56} style={styles.deal} />
        </View>
      ))}
    </>
  );
}

/**
 * The sample screen the drawer moves aside: a shop with a top bar, a search field, the content of
 * the chosen destination and a tab bar. Replace it with the app's own screens.
 */
export function ShopPage({ title, index, active, accent, insets, onMenu, onTab }: Props) {
  return (
    <View style={styles.page}>
      <View style={[styles.bar, { marginTop: insets.top - 4 }]}>
        <Pressable style={styles.menu} onPress={onMenu} accessibilityRole="button" accessibilityLabel="Menu">
          <View style={styles.line} />
          <View style={styles.line} />
          <View style={styles.line} />
        </Pressable>
        <View style={styles.actions}>
          <Ionicons name="mail-outline" size={22} color={INK} />
          <View>
            <Ionicons name="notifications-outline" size={22} color={INK} />
            <View style={styles.alert} />
          </View>
        </View>
      </View>

      <View style={styles.search}>
        <Ionicons name="search-outline" size={18} color={INK} />
        <Text style={styles.placeholder} numberOfLines={1}>
          Find your items
        </Text>
        <Ionicons name="scan-outline" size={18} color={INK} />
      </View>

      <View style={styles.body}>
        <LayoutAnimationConfig skipEntering>
          <Animated.View key={active} style={StyleSheet.absoluteFill} entering={FadeIn.duration(SWAP)} exiting={FadeOut.duration(SWAP)}>
            {index === 0 ? <Front /> : <Shelf title={title} index={index} />}
          </Animated.View>
        </LayoutAnimationConfig>
      </View>

      <View style={[styles.tabs, { height: TABS + insets.bottom, paddingBottom: insets.bottom }]}>
        {BAR.map((tab) => {
          const lit = tab.id === active;
          return (
            <Pressable
              key={tab.id}
              style={styles.tab}
              onPress={() => onTab(tab.id)}
              accessibilityRole="tab"
              accessibilityState={{ selected: lit }}
              accessibilityLabel={tab.label}
            >
              <Ionicons name={lit ? tab.lit : tab.icon} size={22} color={lit ? accent : TAB_IDLE} />
              <Text style={[styles.tabLabel, { color: lit ? accent : TAB_IDLE }]} numberOfLines={1}>
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  bar: {
    height: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: 9,
    paddingRight: MARGIN,
  },
  menu: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  line: {
    width: 22,
    height: 2,
    borderRadius: 1,
    backgroundColor: INK,
  },
  actions: {
    flexDirection: 'row',
    gap: 16,
  },
  alert: {
    position: 'absolute',
    left: 2,
    top: 1,
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: ALERT,
  },
  search: {
    height: 44,
    marginTop: 5,
    marginHorizontal: MARGIN,
    paddingHorizontal: 14,
    borderRadius: 14,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: OUTLINE,
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  placeholder: {
    flex: 1,
    color: MUTE,
    fontSize: 14,
    lineHeight: 18,
    includeFontPadding: false,
  },
  body: {
    flex: 1,
    overflow: 'hidden',
  },
  heading: {
    height: 22,
    marginTop: 20,
    marginBottom: GAP,
    marginHorizontal: MARGIN,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headingTitle: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    includeFontPadding: false,
  },
  viewAll: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    includeFontPadding: false,
  },
  shelf: {
    flexDirection: 'row',
    gap: GAP,
    paddingHorizontal: MARGIN,
  },
  grid: {
    marginBottom: GAP,
  },
  tile: {
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  square: {
    width: TILE,
    height: TILE,
  },
  brand: {
    width: BRAND_WIDTH,
    height: BRAND_HEIGHT,
  },
  deal: {
    flex: 1,
    height: DEAL_HEIGHT,
  },
  tileLabel: {
    position: 'absolute',
    left: 10,
    bottom: 8,
    color: '#ffffff',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '600',
    includeFontPadding: false,
  },
  brandName: {
    position: 'absolute',
    right: 14,
    bottom: 12,
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 19,
    fontWeight: '600',
    includeFontPadding: false,
  },
  title: {
    marginTop: 20,
    marginHorizontal: MARGIN,
    color: INK,
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '700',
    includeFontPadding: false,
  },
  count: {
    marginTop: 2,
    marginBottom: 16,
    marginHorizontal: MARGIN,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  tabs: {
    flexDirection: 'row',
    paddingHorizontal: 8,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: [{ offsetX: 0, offsetY: -4, blurRadius: 16, color: 'rgba(43,43,51,0.06)' }],
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
  },
  tabLabel: {
    fontSize: 11,
    lineHeight: 14,
    includeFontPadding: false,
  },
});
