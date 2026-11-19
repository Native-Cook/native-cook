import { Button, ConfirmationDialog, HStack, Host, Image } from "@expo/ui/swift-ui";
import { buttonBorderShape, buttonStyle, controlSize, frame, tint } from "@expo/ui/swift-ui/modifiers";
import { isLiquidGlassAvailable } from "expo-glass-effect";
import type { JSX } from "react";
import { StyleSheet, View } from "react-native";

import { ACTION, ACTION_GAP, COURIER_PHONE, QUICK_REPLIES } from "./CourierActions.types";
import type { CourierActionsProps, CourierLocateProps } from "./CourierActions.types";

const BLUE = "#2F6BFF";
const GLASS = isLiquidGlassAvailable();

/**
 * Call and Message: real SwiftUI buttons. A finger tap opens the system confirmation dialog (a popover anchored to the
 * button on iOS 26). The dialog is mounted only while it is open, so nothing can present at mount or linger; the film
 * never opens it (its Call and Message moments are the in-app pill and toast). Android: CourierActions.tsx.
 */
export function CourierActions({ dialog, onCall, onMessage, onDismiss, onConfirmCall, onSend }: CourierActionsProps): JSX.Element {
  const round = [buttonStyle("bordered"), buttonBorderShape("circle"), controlSize("large"), tint(BLUE)];
  const call = (
    <Button modifiers={round} onPress={onCall}>
      <Image color={BLUE} modifiers={[frame({ width: 24, height: 24 })]} size={19} systemName="phone.fill" />
    </Button>
  );
  const message = (
    <Button modifiers={round} onPress={onMessage}>
      <Image color={BLUE} modifiers={[frame({ width: 24, height: 24 })]} size={19} systemName="message.fill" />
    </Button>
  );
  const closed = (v: boolean) => {
    if (!v) onDismiss();
  };
  return (
    <Host colorScheme="light" style={{ width: ACTION * 2 + ACTION_GAP, height: ACTION }}>
      <HStack spacing={ACTION_GAP}>
        {dialog === 1 ? (
          <ConfirmationDialog isPresented onIsPresentedChange={closed} title="Call Marco?" titleVisibility="visible">
            <ConfirmationDialog.Trigger>{call}</ConfirmationDialog.Trigger>
            <ConfirmationDialog.Actions>
              <Button label={`Call ${COURIER_PHONE}`} onPress={onConfirmCall} />
              <Button label="Cancel" onPress={onDismiss} role="cancel" />
            </ConfirmationDialog.Actions>
          </ConfirmationDialog>
        ) : (
          call
        )}
        {dialog === 2 ? (
          <ConfirmationDialog isPresented onIsPresentedChange={closed} title="Quick message to Marco" titleVisibility="visible">
            <ConfirmationDialog.Trigger>{message}</ConfirmationDialog.Trigger>
            <ConfirmationDialog.Actions>
              {QUICK_REPLIES.map((r) => (
                <Button key={r} label={r} onPress={() => onSend(r)} />
              ))}
              <Button label="Cancel" onPress={onDismiss} role="cancel" />
            </ConfirmationDialog.Actions>
          </ConfirmationDialog>
        ) : (
          message
        )}
      </HStack>
    </Host>
  );
}

/** The locate button over the map: Liquid Glass on iOS 26, a white disc before it. */
export function CourierLocate({ following, onPress }: CourierLocateProps): JSX.Element {
  return (
    <View style={[styles.locate, !GLASS && styles.locateSolid]}>
      <Host colorScheme="light" style={{ width: ACTION, height: ACTION }}>
        <Button modifiers={[buttonStyle(GLASS ? "glass" : "plain"), buttonBorderShape("circle"), controlSize("large")]} onPress={onPress}>
          <Image color={BLUE} modifiers={[frame({ width: 24, height: 24 })]} size={19} systemName={following ? "location.fill" : "location"} />
        </Button>
      </Host>
    </View>
  );
}

const styles = StyleSheet.create({
  locate: { width: ACTION, height: ACTION, borderRadius: ACTION / 2, alignItems: "center", justifyContent: "center" },
  locateSolid: {
    backgroundColor: "#FFFFFF",
    shadowColor: "#0A1A40",
    shadowOpacity: 0.16,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
});
