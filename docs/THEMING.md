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
