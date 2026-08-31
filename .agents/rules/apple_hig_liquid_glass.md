---
name: ios-native-design-enforcer
description: Enforces standard Apple Human Interface Guidelines (HIG) and true native iOS component usage in React Native and Expo projects. Prevents simulated CSS/JS UI approximations and mandates canonical UIKit/SwiftUI component patterns.
---

# iOS Native HIG & Component Enforcer

This skill enforces strict adherence to native iOS UIKit/SwiftUI primitives and Apple Human Interface Guidelines (HIG). It prevents the generation of hacky, custom-simulated components (e.g., custom JavaScript spring segmented controls, CSS-heavy gradient glass buttons, or simulated modal backdrops) and mandates canonical iOS native patterns.

---

## 1. Core Directives

1. **Native Bridge Over JavaScript Simulation:** Always use official native bridge libraries (e.g., `@react-native-segmented-control/segmented-control`, `expo-symbols`, `@react-native-menu/menu`) instead of manually building UI primitives with `View`, `Animated.spring`, `expo-linear-gradient`, or simulated grabber handles.
2. **Canonical HIG Fallback:** If a requested concept does not exist as a standalone UIKit component (e.g., "Liquid Glass Button"), map it directly to the canonical Apple HIG equivalent found in first-party iOS apps (e.g., Prominent Filled Button, Inset-Grouped List, or native `UIVisualEffectView`).
3. **No Synthetic Android/Web Ports on iOS:** Avoid Material Design outlined text boxes or floating pill hacks when building iOS screens. Use iOS Inset-Grouped cards and native hairline dividers.

---

## 2. Component Mapping & Fallback Rules

| Requested / Simulated UI | Forbidden Custom Pattern | Mandatory Native / HIG Implementation |
| :--- | :--- | :--- |
| **Segmented Control** | `View` + `Animated.View` + manual `onLayout` calculations | `@react-native-segmented-control/segmented-control` (Backed by `UISegmentedControl`) |
| **Action / CTA Button** | `Pressable` + `expo-linear-gradient` overlays | **Prominent Filled Button** (`UIButton.Configuration.filled` style: 50pt height, 14pt continuous curve, Apple System Blue `#0A84FF` / `#007AFF`) |
| **Bottom Sheet / Modal** | `<Modal transparent animationType="slide">` + custom backdrop & fake grabber `<View>` | `<Modal presentationStyle="pageSheet" animationType="slide">` (Backed by native `UISheetPresentationController`) |
| **Form Inputs** | Floating rectangular outlined boxes (`borderWidth: 1.5`, standalone rounded boxes) | **Inset-Grouped Form Rows** (Grouped container `#1C1C1E` / `#FFFFFF`, 48–52pt row height, hairline dividers `marginLeft: 46` or text inset) |
| **Context Menus / Dropdowns** | Custom floating absolute positioned modal popovers | `@react-native-menu/menu` (Backed by native `UIMenu` / `UIContextMenu`) |
| **Icons** | Generic web SVG approximations | `expo-symbols` (Native Apple **SF Symbols** with hierarchical color and system weights) |
| **Translucent / Frosted UI** | Stacked translucent gradients (`expo-linear-gradient`) | Native `BlurView` from `expo-blur` using Apple system materials (`systemMaterial`, `systemUltraThinMaterial`) |
| **Settings / Lists** | Manual cards with custom spacing | **iOS Inset-Grouped Table Style** (`secondarySystemGroupedBackground`, 10–12pt corner radius) |

---

## 3. Implementation Checklists

### A. Primary Action Buttons
* **Standard Button:** Render solid Apple System Blue (`#0A84FF` in Dark Mode, `#007AFF` in Light Mode) with `#FFFFFF` semi-bold text (`fontSize: 17`, `letterSpacing: -0.4`).
* **Loading State:** Use native `<ActivityIndicator color="#FFFFFF" size="small" />`.
* **Touch Physics:** Apply standard iOS tap scale (`0.985`) or opacity reduction (`0.82`) with light/medium haptic feedback (`Haptics.impactAsync`).

### B. Segmented Controls
* Must accept dynamic OS theme via `useColorScheme()`.
* Must handle selection via `event.nativeEvent.value` or `selectedIndex`.

```tsx
import SegmentedControl from '@react-native-segmented-control/segmented-control';

<SegmentedControl
  values={['Option 1', 'Option 2', 'Option 3']}
  selectedIndex={selectedIndex}
  onChange={(e) => setSelectedIndex(e.nativeEvent.selectedSegmentIndex)}
  appearance={colorScheme ?? 'dark'}
  style={{ height: 36, marginBottom: 20 }}
/>
```

### C. Page Sheets & Modals
* Always pass `presentationStyle="pageSheet"` on iOS to enable native card-stack drag gestures and dismiss physics.
* Do not render manual grabber lines (`<View style={{ width: 36, height: 5 }} />`) inside native page sheets.

```tsx
<Modal
  visible={isVisible}
  animationType="slide"
  presentationStyle={Platform.OS === 'ios' ? 'pageSheet' : 'fullScreen'}
  onRequestClose={onClose}
>
  {/* Content */}
</Modal>
```

### D. Inset-Grouped Forms
* Group related form fields into a unified rounded container (`borderRadius: 12`).
* Separate rows using `StyleSheet.hairlineWidth` dividers with left margin matching content alignment.
* Labels above sections must be uppercase, small (`fontSize: 13`), and muted (`secondaryLabel`).

---

## 4. Constraints

* **DO NOT** create custom sliding capsules with JavaScript `Animated.spring` or Reanimated to mimic a segmented control.
* **DO NOT** build fake bottom sheet containers with `maxHeight: '90%'` and manual backdrop tap handlers when a native `pageSheet` or native bottom sheet is applicable.
* **DO NOT** invent non-standard UI gradients (e.g., multi-color linear glossy shine) to simulate glass; rely strictly on iOS system blur materials.