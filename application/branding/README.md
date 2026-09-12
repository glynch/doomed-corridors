# Doomed Corridors branding

The `source` directory contains editable SVG masters for exact game and engine
branding. The `images` directory contains the runtime PNG exports, the native
macOS icon, and the branded disk-image background.

The launch splash uses all three runtime images. The main menu reuses the
corridor background and Doomed Corridors title through ordinary `overlay-image`
resource definitions, so the same authored resources are available to the
runtime and a future editor. The application-directory exporter copies the PNGs
and resource definitions while excluding these editable SVG masters.

No studio logo is currently shown. The splash configuration deliberately treats
it as optional so a studio identity can be added later without inventing a
placeholder brand.

The corridor background was generated with OpenAI image generation from this
project-owned prompt:

> Original ominous industrial space-station corridor with a symmetrical central
> vanishing point, dark steel bulkheads, restrained amber and crimson emergency
> lighting, upper-centre title space, and no text, logos, characters, weapons,
> monsters, watermarks, or copied game designs.

The square application-icon master was generated with OpenAI image generation
from the corridor artwork and title as project-owned visual references. The
prompt requested a centred, front-facing industrial bulkhead with amber and red
lighting, a strong small-size silhouette, macOS masking margins, and no text,
characters, weapons, monsters, watermarks, or third-party icon designs. The
checked-in ICNS file contains the native sizes used by macOS packaging.

The disk-image background is a 517 by 270 pixel composition derived from the
project-owned corridor and title images. It is intentionally darker than the
runtime splash so Finder's application and Applications icons remain legible.
