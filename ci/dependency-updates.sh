#!/usr/bin/env bash
#
# What in gradle/libs.versions.toml has a newer version, and Gradle itself.
#
# Written because "was sollte noch geupgraded werden?" is a question that gets
# asked over and over, and answering it by hand means opening a dozen Maven
# pages. nightly.yml runs this every night into the job summary; it is equally
# meant to be run by hand, and works with nothing but curl and coreutils:
#
#   bash ci/dependency-updates.sh              # a table on stdout
#   bash ci/dependency-updates.sh --markdown   # the same as GitHub Markdown
#
# Why not the Gradle Versions Plugin, which is the obvious answer: it would
# have to be applied to this build - or injected through an init script - and
# its compatibility with AGP 9 and Gradle 9.7 is one more thing that can break
# a report whose whole job is to be readable when other things are broken.
# Reading maven-metadata.xml is what that plugin does underneath anyway.
#
# It reports and never changes anything. Deciding whether a bump is worth it
# needs judgement this script does not have: core-ktx 1.19.0 looked like a
# routine minor and turned out to require AGP 9 and compileSdk 37, so a tool
# that bumped it unattended would have broken the build to save a lookup.
#
# One row per entry in [versions], because that is the granularity the file is
# edited at: the four CameraX artifacts share a single "camerax" ref, so they
# are checked once and the row says how many artifacts ride along.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CATALOG="$HERE/../gradle/libs.versions.toml"
WRAPPER="$HERE/../gradle/wrapper/gradle-wrapper.properties"

GOOGLE_MAVEN="https://dl.google.com/dl/android/maven2"
CENTRAL_MAVEN="https://repo1.maven.org/maven2"

MARKDOWN=false
[ "${1:-}" = "--markdown" ] && MARKDOWN=true

# ---------------------------------------------------------------------------
# The published versions of one artifact, one per line. Google's Maven hosts
# the AndroidX and AGP side, Maven Central the rest; rather than maintaining a
# map of which lives where, ask Google first and fall back. A miss there is a
# 404 in well under a second.
# ---------------------------------------------------------------------------
versions_of() {

    local path metadata repo
    path="${1//./\/}/$2"

    for repo in "$GOOGLE_MAVEN" "$CENTRAL_MAVEN"; do
        metadata="$(curl -sfL --max-time 20 "$repo/$path/maven-metadata.xml" 2>/dev/null)" || continue
        [ -n "$metadata" ] || continue
        printf '%s\n' "$metadata" \
            | grep -oE '<version>[^<]+</version>' \
            | sed -e 's|<version>||' -e 's|</version>||'
        return 0
    done

    return 1

}

# A pre-release is anything carrying a qualifier. Stable and newest are
# reported separately on purpose: an alpha is information, not a suggestion.
newest_stable() { grep -viE -- '-(alpha|beta|rc|dev|m[0-9]|snapshot|eap)' | sort -V | tail -1; }

# "sort -V" is filevercmp, which sorts 1.13.0-rc01 AFTER 1.13.0 - it has no
# notion of a pre-release, it just sees a longer string. That is backwards for
# Maven, and it made this column claim an rc was newer than the release it was
# a candidate for. Substituting the qualifier separator with a tilde fixes it:
# filevercmp does honour "~" as sorting below everything, the Debian
# convention, so 1.13.0~-rc01 lands before 1.13.0 while 1.14.0~-alpha01 still
# lands after it - which is the whole point of the column.
newest_any() { sed 's/-/~-/' | sort -V | tail -1 | sed 's/~-/-/'; }

# ---------------------------------------------------------------------------
# Read the catalog: the pinned version per ref, plus one representative
# group:artifact per ref and how many artifacts share it.
# ---------------------------------------------------------------------------
declare -A PINNED REPRESENTATIVE SHARED_BY

section=""

while IFS= read -r line; do

    line="${line%%#*}"
    [ -z "${line// }" ] && continue

    if [[ "$line" =~ ^\[(.*)\][[:space:]]*$ ]]; then
        section="${BASH_REMATCH[1]}"
        continue
    fi

    case "$section" in

        versions)
            if [[ "$line" =~ ^[[:space:]]*([A-Za-z0-9_-]+)[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
                PINNED[${BASH_REMATCH[1]}]="${BASH_REMATCH[2]}"
            fi
            ;;

        libraries)
            if [[ "$line" =~ group[[:space:]]*=[[:space:]]*\"([^\"]+)\".*name[[:space:]]*=[[:space:]]*\"([^\"]+)\".*version\.ref[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
                ref="${BASH_REMATCH[3]}"
                SHARED_BY[$ref]=$(( ${SHARED_BY[$ref]:-0} + 1 ))
                if [ -z "${REPRESENTATIVE[$ref]:-}" ]; then
                    REPRESENTATIVE[$ref]="${BASH_REMATCH[1]}:${BASH_REMATCH[2]}"
                fi
            fi
            ;;

        plugins)
            # The Android Gradle plugin is published as
            # com.android.tools.build:gradle; its plugin marker carries the same
            # versions but is an indirection with nothing to gain here.
            if [[ "$line" =~ id[[:space:]]*=[[:space:]]*\"com\.android\.application\".*version\.ref[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
                ref="${BASH_REMATCH[1]}"
                SHARED_BY[$ref]=$(( ${SHARED_BY[$ref]:-0} + 1 ))
                REPRESENTATIVE[$ref]="com.android.tools.build:gradle"
            fi
            ;;

    esac

done < "$CATALOG"

# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------
outdated=0
rows=()

for ref in $(printf '%s\n' "${!REPRESENTATIVE[@]}" | sort); do

    coordinates="${REPRESENTATIVE[$ref]}"
    group="${coordinates%%:*}"
    artifact="${coordinates##*:}"
    pinned="${PINNED[$ref]:-?}"

    if ! all="$(versions_of "$group" "$artifact")"; then
        rows+=("$ref|$coordinates|$pinned|(unreachable)|(unreachable)|")
        continue
    fi

    stable="$(printf '%s\n' "$all" | newest_stable)"
    any="$(printf '%s\n' "$all" | newest_any)"

    note=""
    if [ -n "$stable" ] && [ "$pinned" != "$stable" ]; then
        note="update"
        outdated=$(( outdated + 1 ))
    fi

    shared="${SHARED_BY[$ref]:-1}"
    if [ "$shared" -gt 1 ]; then
        coordinates="$coordinates (+$(( shared - 1 )) more)"
    fi

    rows+=("$ref|$coordinates|$pinned|$stable|$any|$note")

done

# Gradle is not in the catalog - it is pinned in the wrapper properties - but
# it is the single most disruptive version in this build, so it belongs in the
# same answer. AGP constrains it from above: 8.x could not run on Gradle 9.6.0
# or newer, which cost an afternoon to find out.
gradle_pinned="$(grep -oE 'gradle-[0-9][^-]*-bin\.zip' "$WRAPPER" | sed -e 's/gradle-//' -e 's/-bin\.zip//')"
gradle_current="$(curl -sfL --max-time 20 https://services.gradle.org/versions/current \
                  | grep -oE '"version"[[:space:]]*:[[:space:]]*"[^"]+"' \
                  | sed -e 's/.*"\(.*\)"/\1/')"
[ -z "$gradle_current" ] && gradle_current="(unreachable)"

gradle_note=""
if [ "$gradle_current" != "(unreachable)" ] && [ "$gradle_pinned" != "$gradle_current" ]; then
    gradle_note="update"
    outdated=$(( outdated + 1 ))
fi
rows+=("(wrapper)|gradle|$gradle_pinned|$gradle_current|$gradle_current|$gradle_note")

if $MARKDOWN; then

    echo "| Catalog entry | Artifact | Pinned | Newest stable | Newest any | |"
    echo "|---|---|---|---|---|---|"
    for row in "${rows[@]}"; do
        IFS='|' read -r ref coordinates pinned stable any note <<< "$row"
        [ "$note" = "update" ] && note='**update**'
        printf '| `%s` | `%s` | `%s` | `%s` | `%s` | %s |\n' \
               "$ref" "$coordinates" "$pinned" "$stable" "$any" "$note"
    done
    echo

else

    printf '%-18s %-50s %-12s %-14s %-14s %s\n' \
           "CATALOG" "ARTIFACT" "PINNED" "NEWEST STABLE" "NEWEST ANY" ""
    for row in "${rows[@]}"; do
        IFS='|' read -r ref coordinates pinned stable any note <<< "$row"
        printf '%-18s %-50s %-12s %-14s %-14s %s\n' \
               "$ref" "$coordinates" "$pinned" "$stable" "$any" "$note"
    done
    echo

fi

if [ "$outdated" -eq 0 ]; then
    echo "Everything is on its newest stable release."
else
    echo "$outdated of ${#rows[@]} entries are behind their newest stable release."
fi

# Always zero: this reports, it does not gate. A script that exited non-zero
# for "there is an update" would be a trap for anyone chaining it.
exit 0
