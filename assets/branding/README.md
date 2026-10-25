# Feltri 8-Ball artwork

`eight-ball.png` is the selected orange comic-burst cartoon eight-ball, generated with the built-in OpenAI image generation tool on 24 October 2026. It supplies the shared splash and Android launcher derivatives.

The source was cropped to its alpha bounds and centered on a transparent 1024-pixel canvas. Android foreground padding fits visible artwork inside the adaptive safe circle; legacy density variants composite the same artwork on the dark splash background. These operations only resize and package the selected artwork. Keep both consumers synchronized when changing it.

Android uses adaptive definitions in `mipmap-anydpi-v26` for both the standard and explicit `roundIcon` manifest entries. Keep the circular entry adaptive too, so launchers do not wrap a legacy bitmap in an additional white plate. The same foreground and dark background serve both masks.
