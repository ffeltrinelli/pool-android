# UI font

`Lato-Regular.ttf` is the unmodified Lato Regular font by Łukasz Dziedzic, distributed under the bundled [SIL Open Font License 1.1](OFL.txt).

Source: [Google Fonts Lato](https://github.com/google/fonts/tree/main/ofl/lato), downloaded on 24 October 2026. Bundled font SHA-256: `d636e4683231f931eda222d588e944d082bfd3bdba02f928bee461c0f185b251`. The font's copyright notice and reserved font name are preserved in `OFL.txt`.

`UiFonts` generates display-sized glyph atlases through libGDX FreeType for the splash, HUD, settings, and result text. Sizes are defined in the 420-unit UI coordinate system and converted using the actual render target width, including high-density backbuffers and deterministic screenshot framebuffers. Resize replaces and disposes old atlases; each UI owner disposes its generator and fonts. Linear filtering and fractional glyph positions preserve smooth edges without enlarging a small source font.

Ball markings retain their existing generated textures and libGDX default-font source. This UI change does not alter them.
