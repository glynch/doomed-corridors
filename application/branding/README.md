# Doomed Corridors branding

The `source` directory contains editable SVG masters for exact game and engine
branding. The `images` directory contains the runtime PNG exports plus the
original Doomed Corridors corridor background generated for this project.

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
