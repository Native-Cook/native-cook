import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { ACTION, ACTION_GAP, QUICK_REPLIES } from "./CourierActions.types";
import type { CourierActionsProps, CourierLocateProps } from "./CourierActions.types";

const BLUE = "#2F6BFF";

/** Call, Message and the locate button where SwiftUI is not available (Android): plain round buttons; the quick
 * messages show as a row under them while the dialog is open. iOS: CourierActions.ios.tsx. */
export function CourierActions({ dialog, onCall, onMessage, onDismiss, onConfirmCall, onSend }: CourierActionsProps): JSX.Element {
  return (
    <View style={{ width: ACTION * 2 + ACTION_GAP, height: ACTION }}>
      <View style={[styles.row, { gap: ACTION_GAP }]}>
        <Pressable accessibilityLabel="Call Marco" accessibilityRole="button" onPress={onCall} style={styles.round}>
          <Text style={styles.glyph}>☎</Text>
        </Pressable>
        <Pressable accessibilityLabel="Message Marco" accessibilityRole="button" onPress={onMessage} style={styles.round}>
          <Text style={styles.glyph}>✉</Text>
        </Pressable>
      </View>
      {dialog !== 0 && (
        <View style={styles.sheet}>
          {dialog === 1 ? (
            <Pressable onPress={onConfirmCall} style={styles.option}>
              <Text style={styles.optionText}>Call Marco</Text>
            </Pressable>
          ) : (
            QUICK_REPLIES.map((r) => (
              <Pressable key={r} onPress={() => onSend(r)} style={styles.option}>
                <Text style={styles.optionText}>{r}</Text>
              </Pressable>
            ))
          )}
          <Pressable onPress={onDismiss} style={styles.option}>
            <Text style={[styles.optionText, styles.cancel]}>Cancel</Text>
          </Pressable>
        </View>
      )}
    </View>
  );
}

export function CourierLocate({ following, onPress }: CourierLocateProps): JSX.Element {
  return (
    <Pressable accessibilityLabel="Follow the courier" accessibilityRole="button" onPress={onPress} style={[styles.round, styles.locate]}>
      <Text style={[styles.glyph, !following && styles.glyphOff]}>➤</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row" },
  round: { width: ACTION, height: ACTION, borderRadius: ACTION / 2, backgroundColor: "rgba(47,107,255,0.12)", alignItems: "center", justifyContent: "center" },
  locate: { backgroundColor: "#FFFFFF", elevation: 3 },
  glyph: { fontSize: 19, color: BLUE },
  glyphOff: { opacity: 0.45 },
  sheet: { position: "absolute", right: 0, bottom: ACTION + 8, width: 220, borderRadius: 14, backgroundColor: "#FFFFFF", elevation: 8, paddingVertical: 4 },
  option: { height: 44, justifyContent: "center", paddingHorizontal: 16 },
  optionText: { fontSize: 16, color: BLUE },
  cancel: { fontWeight: "600" },
});
