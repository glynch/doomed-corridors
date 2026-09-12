---
status: accepted
date: 2026-09-12
---

# Treat JScene3D projects as editable development workspaces

A JScene3D project archive will preserve the complete authored development workspace, not just the content needed by the runtime. This keeps project-specific Java source, tests, build and quality configuration, and documentation available for the editor's planned Java-authoring capabilities, while keeping editor and application distributions focused on their separate purposes.

## Decision

JScene3D has three distinct distributable artifacts:

| Artifact | Purpose | Intended recipient |
| --- | --- | --- |
| **Editor Distribution** | Install and run JScene3D Editor | A developer or content author |
| **Project Workspace Archive** | Continue editing, building, and testing a project | A developer or content author |
| **Application Distribution** | Run the exported game without development tools | A player or tester |

These artifacts must not be treated as interchangeable packaging profiles for the same payload.

### Project Workspace Archive

The Project Workspace Archive is an editable source distribution. It includes Authored Project Files such as:

- `jscene3d.json` and all referenced worlds, game definitions, assets, resources, imports, branding, and extension declarations;
- `src/main/java` and `src/main/resources`, including project-specific game behaviour and extension metadata;
- `src/test` and other project-owned verification material;
- build descriptors and their checked-in wrappers, including `pom.xml`, `mvnw`, and `.mvn`;
- project-owned formatting, Checkstyle, and other quality configuration;
- project documentation, attribution, licences, and repository metadata useful to continued development; and
- portable project settings that are deliberately shared by the team.

It excludes Generated State and machine-local material such as:

- compiled classes, generated reports, staging directories, and other ordinary `target` output;
- version-control internals such as `.git`;
- IDE metadata, operating-system files, logs, and temporary files;
- developer-specific settings, absolute local paths, credentials, and secrets; and
- caches that the editor can reliably reproduce from included Authored Project Files.

A generated import cache may be added explicitly as a temporary compatibility measure when the receiving editor cannot yet reproduce it reliably. This is a narrow exception, not permission to include `target` wholesale. Import caches should eventually be reproducible and optional, with their lifecycle owned by the editor rather than exposed as authored project structure.

The Project Workspace Archive should be named and presented as a development artifact. It is not the standalone game.

### Editor and Application Distributions

The Editor Distribution contains JScene3D Editor and the runtime required to launch it. It does not contain the source of a particular game.

The Application Distribution contains the compiled game, required engine modules, runtime libraries, published assets, and application branding. It excludes Java source, tests, build tools, quality configuration, documentation intended only for developers, and import-only source assets that are unnecessary at runtime.

## Project browsing and source browsing

Archive membership and user-interface visibility are separate decisions. The existing Project view may continue to focus on game concepts such as worlds, entity definitions, assets, resources, and imports even though the Project Workspace also contains Java and build files.

Java authoring should introduce a source-oriented explorer or equivalent workbench view. Hiding development files from a content-focused view must never remove those files from the Project Workspace Archive.

## Java-authoring scope

The editor workbench will provide language-neutral editing primitives. Java knowledge belongs behind a language-tooling interface so that the workbench does not embed Java-specific semantics and community extensions can provide further language or tooling adapters later.

### Workbench foundation

The language-neutral workbench scope includes:

- file and source exploration;
- text documents, editor tabs, dirty state, save, undo, and redo;
- file creation, rename, deletion, and search;
- commands, keybindings, settings, output, and diagnostics;
- navigation requests and edits supplied by language tooling; and
- extension points for language support, build tools, quality tools, and debuggers.

### First Java milestone

First-party Java tooling should provide:

- opening, editing, creating, renaming, and deleting Java classes;
- Java syntax highlighting;
- compilation diagnostics in the standard Diagnostics view;
- completion, go to definition, references, and basic symbol navigation;
- formatting and import organization; and
- build and test commands using the detected project build system.

The likely implementation is an adapter to a mature Java language server, such as Eclipse JDT Language Server, rather than a new Java parser and semantic engine inside JScene3D Editor. The exact implementation remains a later technical decision.

### JScene3D Java authoring

JScene3D-specific authoring should build on the Java tooling and provide:

- a command or wizard for creating a game component class;
- templates for common JScene3D game component roles;
- extension-descriptor registration and validation where required;
- navigation between an entity's component declaration and its Java class;
- validation of component metadata and configuration;
- build and test integration; and
- eventually, safe rebuilding and reloading of changed game code.

The first milestone does not promise complete Visual Studio Code parity. Advanced refactoring, debugging, dependency-management interfaces, an integrated terminal, Git integration, hot reload, and community language extensions are later milestones built on the same workbench interfaces.

## Checkstyle and quality policy

JScene3D Java tooling will supply a versioned default Checkstyle policy so a new project receives useful diagnostics without copying a configuration file into every workspace. Projects remain free to replace or customize that policy.

Checkstyle configuration is resolved in this order, from highest to lowest precedence:

1. an explicit project setting naming a Checkstyle configuration;
2. configuration declared by the detected Maven, Gradle, or other build adapter;
3. a project or workspace setting selecting a Checkstyle profile; and
4. the default policy supplied by JScene3D Java tooling.

When a project owns a custom Checkstyle configuration, that file is an Authored Project File and belongs in the Project Workspace Archive. Checkstyle findings use the workbench's normal diagnostics interface and appear alongside compiler and project diagnostics rather than in a separate quality-tool user interface.

`jscene3d.json` remains the definition of the JScene3D Project and must not become coupled to Maven. Maven, Gradle, and future build systems are development-workspace concerns discovered through adapters.

## Consequences

- The current Doomed Corridors project archive should keep its Java source, tests, Maven wrapper, Checkstyle configuration, and documentation.
- Packaging cleanup should remove only generated, local, or unsafe material; it should not create a content-only archive under the name Project Workspace Archive.
- A separately named content/template export may be introduced later if a real content-only sharing use case appears.
- The editor can grow into a Java-capable development environment without changing the meaning of existing Project Workspaces.
- Java, Checkstyle, and build-system integrations remain replaceable adapters at explicit workbench seams rather than accumulating inside the core editor application.
