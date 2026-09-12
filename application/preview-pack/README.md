# JScene3D Preview Pack

Thanks for trying JScene3D. This preview pack contains a playable game, a gallery
of interactive 3D examples, the JScene3D Editor, and the Doomed Corridors project
that the editor can open.

Everything is ready to run on an Apple Silicon Mac, including M1, M2, M3, and M4
models. You do not need Java, Maven, Terminal, or any developer tools.

## A quick tour

For the easiest introduction, try the applications in this order:

1. Play **Doomed Corridors**.
2. Browse **JScene3D Examples**.
3. Open the Doomed Corridors project in **JScene3D Editor**.

## Play Doomed Corridors

Open `applications/doomed-corridors-1.0.0-macos-arm64.dmg`, then drag
**Doomed Corridors** into the Applications folder shown in the window. Open it
from Applications when the copy has finished.

Doomed Corridors is an early first-person game built with JScene3D and artwork
from Freedoom. This preview lets you explore the first level, open supported
doors, fight zombiemen with the pistol and shotgun, collect health, armour,
ammunition and weapons, and see the game HUD in action. Some enemies and objects
are visible but do not have their full behaviour yet.

### Controls

| Action | Control |
| --- | --- |
| Start controlling the game | Click inside the game window |
| Move | W, A, S and D |
| Look around | Mouse |
| Fire | Left mouse button |
| Open a supported door | E |
| Select pistol | 1 |
| Select shotgun | 2, after collecting it |
| Pause or release the mouse | Escape |

## Browse the examples

Open `applications/jscene3d-examples-1.0.0-macos-arm64.dmg`, drag
**JScene3D Examples** to Applications, and then open it.

The Example Browser is a searchable gallery showing the rendering features that
JScene3D currently supports. Select an example to see demonstrations including
lighting, materials, shadows, animation, imported 3D models, Littlest Tokyo and
the water bottle model.

## Explore the project in JScene3D Editor

First open `applications/jscene3d-editor-1.0.0-macos-arm64.dmg` and drag
**JScene3D Editor** to Applications.

Then:

1. Open the `project` folder in this preview pack.
2. Double-click `doomed-corridors-project-workspace-0.1.0-SNAPSHOT.zip` to
   extract it.
3. Launch JScene3D Editor.
4. Select **Open Project...**.
5. Choose the extracted `doomed-corridors` folder. It is the folder containing
   `jscene3d.json`.

The editor can currently open and preview a project, browse its scenes and
assets, show the scene hierarchy, inspect selected objects, display diagnostics,
browse installed extensions, and customise the workbench layout.

This is still an early read-only editor preview. Editing and saving project
content, editing Java code, and launching the game from inside the editor are
not implemented yet. The separate Doomed Corridors application is the playable
version.

## If macOS blocks an application

These private preview applications are not notarised by Apple, so macOS may
block the first launch even though they have been packaged and checked locally.

Try to open the application once. If macOS blocks it:

1. Open **System Settings**.
2. Select **Privacy & Security**.
3. Scroll down to the Security section.
4. Select **Open Anyway** beside the blocked application.
5. Confirm that you want to open it.

Only do this for this preview pack if you received it directly from someone you
trust. You should not need to disable any macOS security settings.

## Removing the preview

You can remove any of the applications by dragging it from Applications to the
Bin. The extracted project folder can be deleted normally whenever you no longer
want it.

## Credits

Doomed Corridors uses attributed content from Freedoom Phase 2. The Example
Browser includes attributed demonstration assets from their respective
creators. Detailed licences and attribution are included inside the relevant
applications and project workspace.
