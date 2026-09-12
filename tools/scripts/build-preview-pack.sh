#!/usr/bin/env bash

set -euo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
project_root=$(cd -- "${script_directory}/../.." && pwd -P)
jscene3d_root="${project_root}/../threejs-java"
preview_version="0.1.0"

usage() {
    printf 'Usage: %s [--jscene3d PATH]\n' "$0"
}

while (($# > 0)); do
    case "$1" in
        --jscene3d)
            if (($# < 2)); then
                usage >&2
                exit 2
            fi
            jscene3d_root=$2
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown argument: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ $(uname -s) != Darwin || $(uname -m) != arm64 ]]; then
    printf 'The preview pack must be built on an Apple Silicon Mac.\n' >&2
    exit 1
fi

if [[ ! -x "${jscene3d_root}/mvnw" || ! -f "${jscene3d_root}/pom.xml" ]]; then
    printf 'JScene3D repository not found at: %s\n' "$jscene3d_root" >&2
    exit 1
fi

jscene3d_root=$(cd -- "$jscene3d_root" && pwd -P)

printf 'Building JScene3D Editor and Examples distributions...\n'
"${jscene3d_root}/mvnw" \
    -f "${jscene3d_root}/pom.xml" \
    clean install \
    -pl jscene3d-editor,jscene3d-examples \
    -am \
    -Peditor-distribution-macos-arm64,example-distribution-macos-arm64

printf 'Generating JScene3D Javadocs...\n'
"${jscene3d_root}/mvnw" \
    -q \
    -f "${jscene3d_root}/pom.xml" \
    -pl jscene3d-editor,jscene3d-examples \
    -am \
    javadoc:javadoc

printf 'Building Doomed Corridors application and Project Workspace distributions...\n'
"${project_root}/mvnw" \
    -f "${project_root}/pom.xml" \
    clean verify \
    -Pproject-archive,export-directory,verify-export-directory,macos-distribution

printf 'Generating Doomed Corridors Javadocs...\n'
"${project_root}/mvnw" -q -f "${project_root}/pom.xml" javadoc:javadoc

editor_disk_image="${jscene3d_root}/jscene3d-editor/target/distribution/jscene3d-editor-1.0.0-macos-arm64.dmg"
examples_disk_image="${jscene3d_root}/jscene3d-examples/target/distribution/jscene3d-examples-1.0.0-macos-arm64.dmg"
game_disk_image="${project_root}/target/distribution/doomed-corridors-1.0.0-macos-arm64.dmg"
project_archive="${project_root}/target/doomed-corridors-project-workspace-0.1.0-SNAPSHOT.zip"
readme_source="${project_root}/application/preview-pack/README.md"

for required_file in \
    "$editor_disk_image" \
    "$examples_disk_image" \
    "$game_disk_image" \
    "$project_archive" \
    "$readme_source"; do
    if [[ ! -s "$required_file" ]]; then
        printf 'Required preview-pack artifact is missing or empty: %s\n' "$required_file" >&2
        exit 1
    fi
done

printf 'Verifying application disk images...\n'
hdiutil verify "$editor_disk_image"
hdiutil verify "$examples_disk_image"
hdiutil verify "$game_disk_image"

printf 'Verifying the Project Workspace archive...\n'
unzip -tq "$project_archive"

preview_work_directory="${project_root}/target/preview-pack"
bundle_name="jscene3d-preview-pack-${preview_version}-macos-arm64"
bundle_directory="${preview_work_directory}/${bundle_name}"
bundle_archive="${project_root}/target/distribution/${bundle_name}.zip"

case "$preview_work_directory" in
    "${project_root}/target/preview-pack") ;;
    *)
        printf 'Refusing to replace unexpected preview staging directory: %s\n' "$preview_work_directory" >&2
        exit 1
        ;;
esac

rm -rf "$preview_work_directory"
mkdir -p "${bundle_directory}/applications" "${bundle_directory}/project"
cp "$readme_source" "${bundle_directory}/README.md"
cp "$editor_disk_image" "${bundle_directory}/applications/"
cp "$examples_disk_image" "${bundle_directory}/applications/"
cp "$game_disk_image" "${bundle_directory}/applications/"
cp "$project_archive" "${bundle_directory}/project/"

rm -f "$bundle_archive"
ditto -c -k --norsrc --noextattr --noqtn --noacl --keepParent \
    "$bundle_directory" \
    "$bundle_archive"
unzip -tq "$bundle_archive"

printf 'Preview pack created successfully:\n%s\n' "$bundle_archive"
shasum -a 256 "$bundle_archive"
