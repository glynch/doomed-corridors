# Doomed Corridors Project Context

Doomed Corridors is both a JScene3D game and an editable development workspace. This glossary distinguishes authored project material from the distributions produced from it.

## Language

**JScene3D Project**:
The authored game content identified by `jscene3d.json`, including its worlds, assets, resources, imports, extension declarations, and game definitions.
_Avoid_: Content bundle

**Project Workspace**:
The developer-owned directory containing a JScene3D Project together with its Java source, tests, build configuration, quality configuration, and documentation.
_Avoid_: Runtime bundle

**Project Workspace Archive**:
A portable snapshot of a Project Workspace intended for another developer to extract, open in JScene3D Editor, modify, build, and test.
_Avoid_: Game distribution, project content ZIP

**Project Cache**:
Disposable, locally generated data that accelerates work on one Project Workspace and can be reproduced from Authored Project Files.
_Avoid_: Published content, project source

**Published Import Snapshot**:
A portable, read-only snapshot of generated imports supplied when another editor cannot yet reproduce them from the Project Workspace.
_Avoid_: Project Cache, authored import

**Editor Distribution**:
A self-contained installation of JScene3D Editor, such as the macOS editor DMG. It opens Project Workspaces but does not contain a particular game's source.
_Avoid_: Project distribution

**Application Distribution**:
A self-contained, playable export of a JScene3D Project, such as the Doomed Corridors DMG. It contains the runtime material required by players but not the project's development source or tooling.
_Avoid_: Project archive, source distribution

**Authored Project File**:
A file intentionally maintained by a developer or content author and required to understand, modify, build, test, or document the Project Workspace.
_Avoid_: Source asset when referring to all authored files

**Generated State**:
Disposable output derived from Authored Project Files, including compiled classes, reports, temporary files, and regenerable caches.
_Avoid_: Project source
