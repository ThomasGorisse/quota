# Pass-2 captures — 21 October 2026

Android: fresh Android 16 emulator captures of the final debug APK, 1280×2856, offline Sample data.
Web: `?demo=1` served locally (`python3 -m http.server`), full-page Chrome captures through
Playwright with `colorScheme` emulated; compact is 390×844 at 2×, desktop 1440×900 at 1×.
iPhone: iPhone 17 simulator (iOS 27.0), Debug build launched with `-QuotaDemo`; the widget
capture is the in-app preview (`-QuotaScreen widgets`), not a WidgetKit host on the home screen.

| Surface | Light | Dark |
|---|---|---|
| Android phone | [android-phone-light.png](android-phone-light.png) | [android-phone-dark.png](android-phone-dark.png) |
| Android Glance, real RemoteViews in the debug AppWidgetHost | [android-glance-light.png](android-glance-light.png) | [android-glance-dark.png](android-glance-dark.png) |
| Web, compact 390×844 | [web-compact-light.png](web-compact-light.png) | [web-compact-dark.png](web-compact-dark.png) |
| Web, desktop 1440×900 | [web-desktop-light.png](web-desktop-light.png) | [web-desktop-dark.png](web-desktop-dark.png) |
| iPhone app | [iphone-app-light.png](iphone-app-light.png) | [iphone-app-dark.png](iphone-app-dark.png) |
| iPhone widget preview (in-app) | [iphone-widgets-light.png](iphone-widgets-light.png) | [iphone-widgets-dark.png](iphone-widgets-dark.png) |

Not captured: TRMNL e-ink output, the Watch app and complications, WidgetKit widgets placed on a
real home screen, and enlarged-text layouts. See the [completion report](../../pass2-2026-10-21.md).
