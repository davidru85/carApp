#!/bin/sh

set -eu

script_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_directory/.." && pwd)
firebase_apple_version=$(
    sed -nE 's/^firebaseApple = "([^"]+)".*/\1/p' "$repository_root/gradle/libs.versions.toml"
)
google_sign_in_ios_version=$(
    sed -nE 's/^googleSignInIos = "([^"]+)".*/\1/p' "$repository_root/gradle/libs.versions.toml"
)

if [ -z "$firebase_apple_version" ]; then
    echo "Unable to read firebaseApple from gradle/libs.versions.toml" >&2
    exit 1
fi

if [ -z "$google_sign_in_ios_version" ]; then
    echo "Unable to read googleSignInIos from gradle/libs.versions.toml" >&2
    exit 1
fi

export FIREBASE_APPLE_VERSION="$firebase_apple_version"
export GOOGLE_SIGN_IN_IOS_VERSION="$google_sign_in_ios_version"
exec xcodegen generate --spec "$script_directory/project.yml" --project "$script_directory"
