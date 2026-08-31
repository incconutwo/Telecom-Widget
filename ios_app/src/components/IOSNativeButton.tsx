import React from 'react';
import {
  Pressable,
  Text,
  StyleSheet,
  ActivityIndicator,
  ViewStyle,
  StyleProp,
  Platform,
} from 'react-native';
import * as Haptics from 'expo-haptics';

interface IOSNativeButtonProps {
  title: string;
  onPress: () => void;
  isLoading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}

export const IOSNativeButton: React.FC<IOSNativeButtonProps> = ({
  title,
  onPress,
  isLoading = false,
  disabled = false,
  style,
}) => {
  const handlePress = () => {
    if (isLoading || disabled) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    onPress();
  };

  return (
    <Pressable
      onPress={handlePress}
      disabled={disabled || isLoading}
      accessibilityRole="button"
      style={({ pressed }) => [
        styles.button,
        pressed && styles.buttonPressed,
        (disabled || isLoading) && styles.buttonDisabled,
        style,
      ]}
    >
      {isLoading ? (
        <ActivityIndicator color="#FFFFFF" size="small" />
      ) : (
        <Text style={styles.buttonText}>{title}</Text>
      )}
    </Pressable>
  );
};

const styles = StyleSheet.create({
  button: {
    height: 50,
    borderRadius: 14, // Apple HIG standard continuous curve
    backgroundColor: Platform.select({ ios: '#0A84FF', default: '#007AFF' }), // Apple System Blue
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  buttonPressed: {
    opacity: 0.82,
    transform: [{ scale: 0.985 }],
  },
  buttonDisabled: {
    opacity: 0.45,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.4,
  },
});
