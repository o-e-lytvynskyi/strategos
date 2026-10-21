#!/bin/sh
# Starts the dev server and two clients that join it by Quick Play (QA-09.1): runClientJoin (Dev1) and
# runClient2 (Dev2); the plain runClient opens the title screen and is not used here. The owner runs
# this; the assistant never does, because a client window takes keyboard focus.
set -eu
cd "$(dirname "$0")/.."
./gradlew :mod:classes :mod:createLaunchScripts
mkdir -p mod/run/server mod/run/client mod/run/client2
[ -f mod/run/server/server.properties ] ||
    printf 'level-type=minecraft\\:flat\nonline-mode=false\n' > mod/run/server/server.properties
rm -f mod/run/server/logs/latest.log
(
    waited=0
    until grep -q 'Done (' mod/run/server/logs/latest.log 2>/dev/null; do
        waited=$((waited + 1))
        [ "$waited" -le 300 ] || exit 1
        sleep 1
    done
    mod/build/moddev/runClientJoin.sh > mod/run/client/console.log 2>&1 &
    mod/build/moddev/runClient2.sh > mod/run/client2/console.log 2>&1 &
) &
exec mod/build/moddev/runServer.sh   # foreground: this console takes `op Dev1` and `stop`
