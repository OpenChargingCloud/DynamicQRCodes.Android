# Icon artwork

`chargy-qrcode-shield.png` is the unmodified, transparent source logo. All
launcher and store icon bitmaps are generated from it by `RenderIcon.java`.

The shield carries a real QR code, so replacing the source replaces a link that
ends up on every phone and on the store listing. Scan the new file before
committing it - the artwork looking right says nothing about where it points.

| Asset | Purpose |
|---|---|
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | Colour foreground for Android adaptive icons; the logo stays inside the 66 dp safe zone |
| `app/src/main/res/mipmap-*/ic_launcher_monochrome.png` | One-colour alpha mask for Android themed icons |
| `play-store-icon-512.png` | Full-square, 512×512 Google Play listing asset without pre-applied corner radius or shadow |
| `icon-1024.png` | High-resolution general-purpose bitmap |

The adaptive and round adaptive definitions live in
`app/src/main/res/mipmap-anydpi`. Their white background fills the mask chosen
by the launcher, while the two-colour shield remains a separate layer for
parallax and other system effects.

There are no legacy `ic_launcher.png` squares any more: minSdk is 31, so every
device that can install this app resolves `@mipmap/ic_launcher` to the adaptive
icon and the flat squares were never drawn.

## Regenerating

Run this from the repository root with JDK 17 or newer:

```sh
java images/RenderIcon.java
```

The renderer crops only the source image's transparent outer pixels. It keeps
the source artwork's aspect ratio and colours unchanged.
