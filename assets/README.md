# Source assets

Place the pinned Freedoom Phase 2 WAD at `assets/freedoom2.wad`.

The selected source is `freedoom2.wad` from the official Freedoom 0.13.0 release:

- release: <https://github.com/freedoom/freedoom/releases/tag/v0.13.0>
- archive: `freedoom-0.13.0.zip`
- archive SHA-256: `3f9b264f3e3ce503b4fb7f6bdcb1f419d93c7b546f4df3e874dd878db9688f59`
- extracted WAD SHA-256: `a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b`

The WAD is an authoritative source asset, not a Maven resource and not a generated
JScene3D scene. A future Doom WAD importer will read it, validate its digest, and
produce a disposable import cache. The project manifest currently reports a warning
until the file is present.

The project manifest and WAD loader verify the extracted WAD digest. Do not replace
it with a development snapshot or a file from a mutable URL. WAD files are ignored
by Git until the repository's source-asset distribution policy is decided.
Keep the tracked upstream `COPYING.txt`, `CREDITS.txt`, and
`CREDITS-MUSIC.txt` files beside the local WAD.
