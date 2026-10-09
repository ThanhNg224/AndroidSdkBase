# Theming, strings and locale

How an SDK screen gets its colours, text and language, and how a host changes them. The shared code
is `:sdk:core-ui-compose` (`io.github.thanhng224.sdkbase.ui`); a feature's own screen is its
`<name>-ui-compose` module (e.g. `:sdk:features:otp-ui-compose`).

## Colours

An SDK UI module never contains a colour literal. `./gradlew check` fails (`ui-color-literal`,
`checkSourceRules`) on `Color(0xFF…)`, `Color.Red` and the other named colours, `Color.parseColor`,
and on a `#hex` or `@android:color/…` in the module's `res/` XML. `Color.Transparent` and
`Color.Unspecified` are allowed because they carry no hue.

Every colour comes from a token set. `SdkColors` (`background`, `onBackground`, `accent`, `error`,
`outline`) is the shared one, and `SdkColors.fromMaterialTheme()` derives it from the host's Material
theme, so a host that themes its app themes the SDK screens with no extra work. A feature screen
exposes its own token class and a theme wrapper with the same default — `OtpColors` and `OtpTheme`
are the worked example:

```kotlin
OtpTheme(colors = OtpColors(/* your tokens */)) { OtpScreen(state, onCommand) }
```

`meetsContrast(foreground, background)` (WCAG AA, 4.5:1 by default) and `contrastRatio` let a host
check its own token set.

## Spacing and touch targets

`SdkSpacing` is the 8-point scale (`Half` 4dp, `One` 8dp, `Two` 16dp, `Three` 24dp, `Four` 32dp) and
`SdkDimens.MinTouchTarget` is 48dp. A UI module uses them instead of ad-hoc `dp` values, and every
tappable control is at least `MinTouchTarget`.

## Text

User-visible text is a string resource, never a literal. It is chosen by the error's **code**:

```kotlin
Text(sdkErrorMessage(error))          // @Composable
val res = sdkErrorMessageRes(error)   // @StringRes
```

`sdkErrorMessageRes` covers the codes in core's `SdkErrors`; any other code gets the generic
"something went wrong" text. A feature's UI module maps its own codes and falls back to the shared
text, exactly as `otp-ui-compose` does. `SdkError.reason` is diagnostic and never reaches the screen.
An error whose `disposition` is `SILENT` should not be shown at all
(see [ERROR_CODE_REFERENCE.md](ERROR_CODE_REFERENCE.md)).

English and Vietnamese ship in the AAR. **A host overrides any string by declaring one with the same
name in its own resources** — no API involved. The shared strings are `sdk_core_ui_compose_*`; a
feature's are `sdk_<feature>_ui_compose_*`. A string that a feature reuses from the shared set (the
OTP network/timeout/generic errors) stays a feature key that aliases the shared one, so overriding
either the feature key or the shared key works. Renaming or removing a string resource is a **major**
change (`COMPATIBILITY.md`).

## Locale

To render one screen in a chosen language regardless of the device language, wrap it:

```kotlin
ProvideSdkLocale(Locale.forLanguageTag("vi")) { OtpTheme { OtpScreen(state, onCommand) } }
```

`null` keeps the host's locale. Inside the block `LocalContext.current` is a configuration wrapper,
not the Activity. An app published as an Android App Bundle must not split by language
(`android.bundle.language.enableSplit = false`), or the requested language may be missing at runtime.
There is no runtime or remote language manager: language is the device's, or set per screen here.

## Demo host UI

`apps/demo` owns a small, internal host UI under its `demo/ui` package. It is not part of the
published toolkit: application navigation stays with the host. The capsule navigation and static
palette, typography and shapes are adapted from AndroidComposeBase. Dynamic colour is used on
Android 12+; the explicit static light/dark palettes are used on older devices.

`DemoTheme` and `DemoAppearance` apply the saved appearance to both Activities. `DemoPreferences`
stores theme and language in the host's named preferences; its Activity-owned readers expose
state through `DemoSettingsViewModel` and release their listeners when cleared. The root wraps
host and SDK UI in `ProvideSdkLocale`, and language splitting is disabled in the demo bundle.

For another host screen, use `DemoScreen` for a header, scrollable content and optional lower action;
compose `DemoCard`, `DemoStatus` and `DemoChoiceRow` with ordinary Material 3 controls. Components
receive state/callbacks, a root modifier and content slots where needed. Keep screen wiring in the
Activity/ViewModel and render content from values and callbacks. Use `SdkSpacing`/`SdkDimens` and
Material theme roles. Add English/Vietnamese text to the host's `demo_*` resources.

`DemoShell` keeps the selected tab and each tab's saveable UI state, moving the same screen
composition between bottom navigation and rail layouts. Its compact layout measures
the entire navigation slot before content, consumes the reserved bottom inset and leaves screens
to consume the remaining safe insets. At 600dp width and 480dp height it uses a navigation rail.
OTP opens a separate Activity with no tab navigation: its ViewModel survives rotation and closes
the session when the Activity finishes. The host supplies a scrollable viewport so the unchanged
SDK keypad remains reachable in short windows. The locale wrapper always uses the resolved locale,
including System, to preserve the composition identity when changing language. Adding an SDK flow does not require moving host components
into `core-ui-compose` or changing an SDK API.

Behavior tests live in `apps/demo/src/androidTest`: tab/reselect/Back and saved-state restoration,
appearance preferences in isolated stores, and real OTP session lifecycle/validation/resend.
Build the debug and test APKs, install both with `adb install -r`, then run
`adb shell am instrument -w io.github.thanhng224.sdkbase.demo.test/androidx.test.runner.AndroidJUnitRunner`.
This avoids uninstalling or clearing the host's data. Successful instrumentation is distinct from
visual review and actual TalkBack listening; neither establishes a performance benchmark result.
