# Icon artwork

`chargy-qrcode-shield.png` is the unmodified, transparent source logo. All
launcher and store icon bitmaps are generated from it by `RenderIcon.java`.

| Asset | Purpose |
|---|---|
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | Colour foreground for Android adaptive icons; the logo stays inside the 66 dp safe zone |
| `app/src/main/res/mipmap-*/ic_launcher_monochrome.png` | One-colour alpha mask for Android themed icons |
| `app/src/main/res/mipmap-*/ic_launcher.png` | Density-specific legacy/fallback square icon |
| `app/src/main/res/mipmap-*/ic_launcher_round.png` | Density-specific legacy/fallback round icon |
| `play-store-icon-512.png` | Full-square, 512×512 Google Play listing asset without pre-applied corner radius or shadow |
| `icon-1024.png` | High-resolution general-purpose bitmap |

The adaptive and round adaptive definitions live in
`app/src/main/res/mipmap-anydpi-v26`. Their white background fills the mask
chosen by the launcher, while the two-colour shield remains a separate layer
for parallax and other system effects.

## Regenerating

Run this from the repository root with JDK 17 or newer:

```sh
java images/RenderIcon.java
```

The renderer crops only the source image's transparent outer pixels. It keeps
the source artwork's aspect ratio and colours unchanged.
