import * as LocalAuthentication from 'expo-local-authentication';

export type BiometricType = 'faceId' | 'touchId' | 'biometrics';

export class BiometricsService {
  public static async isAvailable(): Promise<boolean> {
    try {
      const hasHardware = await LocalAuthentication.hasHardwareAsync();
      const isEnrolled = await LocalAuthentication.isEnrolledAsync();
      return hasHardware && isEnrolled;
    } catch (_e) {
      return false;
    }
  }

  public static async getBiometricType(): Promise<BiometricType> {
    try {
      const types = await LocalAuthentication.supportedAuthenticationTypesAsync();
      if (types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) {
        return 'faceId';
      }
      if (types.includes(LocalAuthentication.AuthenticationType.FINGERPRINT)) {
        return 'touchId';
      }
    } catch (_e) {}
    return 'biometrics';
  }

  public static async authenticate(promptMessage: string = 'Unlock Telecom Widget'): Promise<boolean> {
    try {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage,
        fallbackLabel: 'Use Passcode',
        cancelLabel: 'Cancel',
        disableDeviceFallback: false,
      });
      return result.success;
    } catch (_e) {
      return false;
    }
  }
}
