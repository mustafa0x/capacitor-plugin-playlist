#!/usr/bin/env bash
set -euo pipefail

xcodebuild -quiet -scheme CapacitorPluginPlaylist -destination generic/platform=iOS

simulator=$(xcrun simctl list devices available --json | jq -r '
    ([.devices[][] | select(.name | startswith("iPhone"))]
    | sort_by(.state != "Booted")
    | first) // empty
    | [.udid, .state]
    | @tsv
')

if [ -z "$simulator" ]; then
    echo 'No available iPhone simulator found' >&2
    exit 1
fi

IFS=$'\t' read -r simulator_id simulator_state <<< "$simulator"
if [ "$simulator_state" != 'Booted' ]; then
    xcrun simctl boot "$simulator_id"
fi
xcrun simctl bootstatus "$simulator_id" -b

xcodebuild -quiet test \
    -scheme CapacitorPluginPlaylist \
    -destination "platform=iOS Simulator,id=$simulator_id"
