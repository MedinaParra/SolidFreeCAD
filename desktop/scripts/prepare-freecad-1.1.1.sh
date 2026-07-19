#!/usr/bin/env bash
set -euo pipefail

FREECAD_REPOSITORY="https://github.com/FreeCAD/FreeCAD.git"
FREECAD_TAG="1.1.1"
DESTINATION="${1:-${PWD}/freecad-1.1.1}"

log() {
    printf '[solidfreecad-bootstrap] %s\n' "$*"
}

fail() {
    printf '[solidfreecad-bootstrap] ERROR: %s\n' "$*" >&2
    exit 1
}

command -v git >/dev/null 2>&1 || fail "git is required"

if [[ -e "${DESTINATION}" ]]; then
    fail "destination already exists: ${DESTINATION}"
fi

log "cloning official FreeCAD tag ${FREECAD_TAG}"
git clone \
    --branch "${FREECAD_TAG}" \
    --depth 1 \
    "${FREECAD_REPOSITORY}" \
    "${DESTINATION}"

log "renaming official remote to upstream"
git -C "${DESTINATION}" remote rename origin upstream

SOURCE_COMMIT="$(git -C "${DESTINATION}" rev-parse HEAD)"
printf '%s\n' "${SOURCE_COMMIT}" > "${DESTINATION}/SOLIDFREECAD_UPSTREAM_COMMIT"

log "official source prepared"
log "path: ${DESTINATION}"
log "tag: ${FREECAD_TAG}"
log "commit: ${SOURCE_COMMIT}"
log "next: copy desktop/overlay/src/Gui/SolidFreeCAD into src/Gui/SolidFreeCAD"
