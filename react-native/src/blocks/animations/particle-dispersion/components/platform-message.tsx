import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

export function PlatformMessage({
  title,
  body,
  actionLabel,
  onAction,
  busy = false,
}: {
  title: string;
  body: string;
  actionLabel?: string;
  onAction?: () => void;
  busy?: boolean;
}) {
  return (
    <View style={styles.messageScreen}>
      <View style={styles.messageCard}>
        <View style={styles.messageGlyph}>
          {busy ? (
            <ActivityIndicator color="#FFFFFF" />
          ) : (
            <Text style={styles.messageGlyphText}>◉</Text>
          )}
        </View>
        <Text style={styles.messageTitle}>{title}</Text>
        <Text style={styles.messageBody}>{body}</Text>
        {actionLabel && onAction ? (
          <Pressable
            accessibilityLabel={actionLabel}
            accessibilityRole="button"
            onPress={onAction}
            style={({ pressed }) => [
              styles.permissionButton,
              pressed && styles.permissionButtonPressed,
            ]}
          >
            <Text style={styles.permissionButtonLabel}>{actionLabel}</Text>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  messageBody: {
    color: 'rgba(255, 255, 255, 0.68)',
    fontSize: 15,
    lineHeight: 22,
    textAlign: 'center',
  },
  messageCard: {
    alignItems: 'center',
    backgroundColor: '#181818',
    borderColor: 'rgba(255, 255, 255, 0.1)',
    borderRadius: 28,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 13,
    maxWidth: 380,
    padding: 28,
    width: '88%',
  },
  messageGlyph: {
    alignItems: 'center',
    backgroundColor: '#2B2B2B',
    borderRadius: 27,
    height: 54,
    justifyContent: 'center',
    marginBottom: 3,
    width: 54,
  },
  messageGlyphText: {
    color: '#FFFFFF',
    fontSize: 26,
  },
  messageScreen: {
    alignItems: 'center',
    backgroundColor: '#080808',
    flex: 1,
    justifyContent: 'center',
  },
  messageTitle: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '800',
    letterSpacing: -0.25,
    textAlign: 'center',
  },
  permissionButton: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 22,
    justifyContent: 'center',
    marginTop: 7,
    minHeight: 46,
    paddingHorizontal: 20,
  },
  permissionButtonLabel: {
    color: '#101010',
    fontSize: 15,
    fontWeight: '800',
  },
  permissionButtonPressed: {
    opacity: 0.78,
  },
});
