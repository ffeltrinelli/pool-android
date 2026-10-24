# Feltri 8-Ball artwork

`eight-ball.png` is the selected orange comic-burst cartoon eight-ball, generated with the built-in OpenAI image generation tool on 24 October 2026. It supplies the shared splash and Android launcher derivatives. The user selected proposal 2 from speed-streak, comic-burst, and rolling-arc concepts.

Selected prompt:

> Use case: logo-brand. Asset type: Android launcher icon and matching enlarged splash artwork for Feltri 8-Ball. Create ONE square high quality cartoon illustration: bold ink outlined charcoal black cartoon 8-ball with cream white round number patch and perfectly readable black numeral 8, flying diagonally through a compact orange comic impact burst. Bold simple silhouette, polished friendly game aesthetic, subtle highlights, no face or limbs. Entire design contained within central 65% of square for circular launcher masking, generous transparent margin. Genuine transparent background. No text except a single numeral 8 on the ball, no app name, no border, no watermark. Crisp at small icon sizes.

The source was cropped to its alpha bounds and centered on a transparent 1024-pixel canvas. Android foreground padding fits visible artwork inside the adaptive safe circle; legacy density variants composite the same artwork on the dark splash background. These operations only resize and package the selected artwork. Keep both consumers synchronized when changing it.

Android uses adaptive definitions in `mipmap-anydpi-v26` for both the standard and explicit `roundIcon` manifest entries. Keep the circular entry adaptive too, so launchers do not wrap a legacy bitmap in an additional white plate. The same foreground and dark background serve both masks.
