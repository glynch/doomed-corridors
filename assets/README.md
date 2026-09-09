# Source assets

Place the pinned Freedoom Phase 2 WAD at `assets/freedoom2.wad`.

The selected source is `freedoom2.wad` from the official Freedoom 0.13.0 release:

- release: <https://github.com/freedoom/freedoom/releases/tag/v0.13.0>
- archive: `freedoom-0.13.0.zip`
- archive SHA-256: `3f9b264f3e3ce503b4fb7f6bdcb1f419d93c7b546f4df3e874dd878db9688f59`
- extracted WAD SHA-256: `a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b`

The WAD is an authoritative source asset, not a Maven resource or a generated
JScene3D world. The project manifest declares it as the `freedoom` source asset.
The engine Doom importer publishes MAP01 geometry, materials, textures, and
collision, while the Doomed Corridors importer publishes the selected actors and
their presentation resources. Both write reproducible output beneath
`target/import-cache`; that cache can be deleted and regenerated at any time.

Maven prepares stale or missing imports during `process-classes`, so the WAD must
be present before building, running, or previewing the project. A missing file or
checksum mismatch is a build error rather than a request to download or replace
the source automatically.

The project manifest and WAD loader verify the extracted WAD digest. Do not replace
it with a development snapshot or a file from a mutable URL. WAD files are ignored
by Git and are not distributed from this repository.
Keep the tracked upstream `COPYING.txt`, `CREDITS.txt`, and
`CREDITS-MUSIC.txt` files beside the local WAD.
