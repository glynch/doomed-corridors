# VS Code workspace and change model

Research date: 2026-09-12. Sources are limited to official VS Code documentation
and the `microsoft/vscode` repository.

## Conclusion

VS Code does not represent the workbench as one global editor-state observable. It
composes several resource-oriented modules:

- workspace context identifies the open roots;
- working copies own independently editable resources;
- a working-copy registry derives aggregate dirty state;
- configuration resolves layered settings and reports affected keys;
- filesystem operations and external filesystem observations remain distinct;
- workspace edits group changes across resources;
- undo and redo records identify their affected resources; and
- commands are independent of buttons, menus, and shortcuts.

JScene3D should adopt that separation without copying VS Code's complete internal
architecture or TypeScript interfaces.

## Workspace context

VS Code defines a workspace as zero or more folders opened in one window. The
workspace supplies context for configuration, tasks, UI restoration, and extension
enablement; it is not itself one editable document. Its internal workspace module
resolves which root contains a resource and reports root changes separately.

Sources:

- [VS Code workspaces](https://code.visualstudio.com/docs/editing/workspaces/workspaces)
- [VS Code workspace source](https://github.com/microsoft/vscode/blob/main/src/vs/platform/workspace/common/workspace.ts)

JScene3D can expose one project root initially while still modelling resources
relative to a workspace. Visible multi-root support is not required for this work.

## Working copies and dirty state

VS Code assigns dirty state to independently editable resources. Its `TextDocument`
has a URI, increasing version, dirty state, and save lifecycle. Internally,
`IWorkingCopy` generalises the same model to non-text editors. A working copy is
identified by resource URI and type, reports content and dirty changes, and owns
save, revert, and backup behaviour.

`IWorkingCopyService` registers those working copies and derives aggregate facts
such as dirty count, dirty resources, and whether anything is dirty. It does not
merge their content into a single workspace document.

Sources:

- [VS Code `TextDocument`](https://code.visualstudio.com/api/references/vscode-api#TextDocument)
- [VS Code working-copy interface](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/services/workingCopy/common/workingCopy.ts)
- [VS Code working-copy registry](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/services/workingCopy/common/workingCopyService.ts)
- [VS Code working-copy overview](https://github.com/microsoft/vscode/wiki/Working-Copies)

For JScene3D, worlds, entity definitions, resource descriptors, project settings,
and Java sources must be able to become separate working copies. Project-wide dirty
state is derived from their registry. An exact hierarchy decoration is finer-grained
state reported by the working copy that owns the corresponding authored element.

## Configuration

VS Code configuration is a separate semantic module. It merges default, user,
workspace, folder, and language-specific values. Consumers inspect effective values
and subscribe to change events that can answer whether a particular setting and
scope were affected.

This permits two different workflows:

1. The Settings UI persists a value through the configuration module and publishes
   a configuration change without leaving an unsaved editor.
2. Opening `settings.json` directly makes it an ordinary editable working copy;
   saving it causes configuration to reload and publish the semantic change.

Sources:

- [VS Code user and workspace settings](https://code.visualstudio.com/docs/configure/settings)
- [VS Code `WorkspaceConfiguration`](https://code.visualstudio.com/api/references/vscode-api#WorkspaceConfiguration)
- [VS Code configuration source](https://github.com/microsoft/vscode/blob/main/src/vs/platform/configuration/common/configuration.ts)

JScene3D should apply this model to its existing precedence: session override,
machine-local per-workspace override, shared `.jscene3d/settings.json`, then default.
A project configuration module should own effective values, atomic persistence,
reload, validation diagnostics, and affected-key events.

## Filesystem changes and file operations

VS Code distinguishes physical filesystem observation from semantic operations.
`FileSystemWatcher` reports external create, change, and delete events. A separate
working-copy-aware file-operation module coordinates editor-initiated create, move,
copy, and delete operations and publishes will, did, and failure events.

Sources:

- [VS Code `FileSystemWatcher`](https://code.visualstudio.com/api/references/vscode-api#FileSystemWatcher)
- [VS Code `FileSystemProvider`](https://code.visualstudio.com/api/references/vscode-api#FileSystemProvider)
- [VS Code workspace file events](https://code.visualstudio.com/api/references/vscode-api#workspace)
- [VS Code working-copy file operations](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/services/workingCopy/common/workingCopyFileService.ts)

JScene3D therefore needs both a semantic workspace file module and a scoped external
watcher/reconciler. An editor-created Java class is a create operation that updates
the workspace index and may open a working copy. A file created by Finder, Git,
Maven, or another editor arrives through the watcher. An external modification of a
dirty working copy must produce a conflict instead of silently overwriting content.

## Workspace edits and undo/redo

VS Code's `WorkspaceEdit` groups text and resource changes across multiple files.
Its internal undo module supports both single-resource and workspace-wide undo
elements. Workspace edits are ordered operations, not a promise of a transactional
filesystem.

Sources:

- [VS Code `WorkspaceEdit`](https://code.visualstudio.com/api/references/vscode-api#WorkspaceEdit)
- [VS Code `workspace.applyEdit`](https://code.visualstudio.com/api/references/vscode-api#workspace.applyEdit)
- [VS Code undo/redo source](https://github.com/microsoft/vscode/blob/main/src/vs/platform/undoRedo/common/undoRedo.ts)

JScene3D edits should identify their affected resources. A property edit can affect
one world file, while renaming a Java class can change a Java file, project metadata,
and references as one labelled workspace undo operation. Physical persistence still
uses safe atomic file writes.

## Commands and context

VS Code commands have stable identities and handlers independent of their menu,
button, keybinding, or programmatic invocation. Context keys control command
availability and presentation consistently across those surfaces.

Sources:

- [VS Code commands](https://code.visualstudio.com/api/extension-guides/command)
- [VS Code contribution points](https://code.visualstudio.com/api/references/contribution-points)
- [VS Code when-clause contexts](https://code.visualstudio.com/api/references/when-clause-contexts)

JScene3D already has a useful command identity and placement seam. It should add
shared context facts such as `workspaceOpen`, `workingCopyDirty`, `canUndo`,
`canRedo`, selection kind, and runtime state. Save, undo, redo, play, pause, and stop
then use the same handlers from every presentation.

## Recommended JScene3D modules

| Module | Responsibility |
| --- | --- |
| `ProjectWorkspaceService` | Open/close lifecycle, roots, resource membership, project index |
| `WorkingCopy` | Current and saved state for one editable resource |
| `WorkingCopyRegistry` | Registration and aggregate dirty/modified facts |
| `ProjectConfigurationService` | Effective settings, scopes, persistence, reload, affected-key events |
| `WorkspaceFileService` | Editor-initiated create/delete/move/copy with working-copy coordination |
| `WorkspaceFileWatcher` | Reconciliation of external resource changes |
| `WorkspaceEditService` | Preflight and ordered application of multi-resource changes |
| `UndoRedoService` | Resource and workspace operation histories |
| `CommandService` | Stable command registration and execution |
| `ContextKeyService` | Derived facts controlling command availability and presentation |

Observable interfaces should publish immutable, typed event values and return a
disposable registration. There should be no global untyped event bus and no single
snapshot containing every category of workbench state.

## Recommended implementation order

1. Introduce canonical resource identity for files in the project workspace.
2. Introduce one reusable typed event interface using the existing disposable
   registration lifecycle.
3. Convert the open world document into the first `WorkingCopy` and add the registry.
4. Derive the project dirty indicator and dirty resource count from that registry.
5. Add the project configuration module and make cache location its first consumer.
6. Add semantic workspace file operations and scoped external watching.
7. Tag undo records with affected resources and support grouped workspace operations.
8. Add context keys to the existing command and placement infrastructure.

The interfaces should leave room for backups, virtual resources, additional roots,
and external tooling, but those implementations should be added only with concrete
consumers.

## Decisions still to make

- Which resource identity type JScene3D exposes publicly.
- Whether project settings changed through the Settings UI save immediately.
- Which external project paths are watched and which generated/cache paths are
  excluded.
- The conflict experience when a dirty resource changes externally.
- Whether file create/delete/rename operations enter the undo history immediately.
- Which working-copy capabilities extensions may implement in the first public
  interface.
