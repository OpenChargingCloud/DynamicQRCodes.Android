# Icon artwork

The launcher icon itself lives in
`app/src/main/res/drawable/ic_launcher_background.xml` and
`ic_launcher_foreground.xml`, drawn as vectors. **Those two files are the
source of truth**; everything here is rendered from them.

| File | What it is for |
|------|----------------|
| `play-store-icon-512.png` | The Google Play listing, which takes a 512×512 bitmap of its own rather than reading the icon out of the app bundle |
| `icon-1024.png` | The same artwork larger, for documentation, slides and anywhere a bitmap is easier than a vector |

Google rounds the corners of the listing icon itself, the same way a launcher
masks the adaptive icon, so both files are the full square without any
rounding or shadow of their own.

## Regenerating

`RenderIcon.java` draws the same shapes on the same 108 unit canvas the
drawables use. It needs nothing but a JDK:

```
java RenderIcon.java play-store-icon-512.png 512
java RenderIcon.java icon-1024.png 1024
```

It does not read the vector drawables, it repeats them - so a change to the
icon has to be made in both places. The renderer says so at the top as well.
