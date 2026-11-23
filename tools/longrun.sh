#!/usr/bin/env bash
# Runs a long Gradle run (verifySoak, verifySlow, a perf batch) on the committed HEAD in separate git worktrees,
# so the working copy stays free for edits and other builds (docs/05-testing.md "Long runs").
#
#   tools/longrun.sh [--shards N] [--name NAME] -- <gradle tasks and options>
#   tools/longrun.sh --clean [--name NAME]
#
# --shards N splits the AI soak's seeds (QA-12.4, aisoak.seeds, 20 by default) into N contiguous ranges, one
# worktree and one JVM each: shard i runs -Paisoak.firstSeed=<first> -Paisoak.seeds=<count>. Every other task in
# the arguments runs in every shard, so shard only a soak-only run (`:mod:runGameTestSoak`); run `:core:soak` and
# `verifySlow` unsharded. Each soak JVM takes 6 GB: two shards fit a 24 GB machine beside a desktop, three do not.
#
# The script blocks until every shard ends (start it in the background), then prints one line per shard and exits
# non-zero if any failed. Logs: <worktree>/longrun.log. Worktrees live under $STRATEGOS_LONGRUN_ROOT (default: a
# sibling folder of the repository, strategos-longrun/) and are reused by the next run; --clean removes them.
# Never run it beside an L6 measurement (perf batches, JMH) on the same machine: the foreign load invalidates it.
set -euo pipefail

top=$(git rev-parse --show-toplevel)
root=${STRATEGOS_LONGRUN_ROOT:-$(dirname "$top")/strategos-longrun}
shards=1
name=run
clean=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --shards) shards=$2; shift 2 ;;
    --name) name=$2; shift 2 ;;
    --clean) clean=1; shift ;;
    --) shift; break ;;
    *) echo "longrun: unknown option $1 (the Gradle arguments follow --)" >&2; exit 2 ;;
  esac
done

if [[ $clean -eq 1 ]]; then
  for wt in "$root/$name"-*; do
    [[ -d $wt ]] && git -C "$top" worktree remove --force "$wt" && echo "removed $wt"
  done
  git -C "$top" worktree prune
  exit 0
fi

[[ $# -gt 0 ]] || { echo "longrun: no Gradle arguments after --" >&2; exit 2; }
[[ $shards =~ ^[1-9][0-9]*$ ]] || { echo "longrun: --shards takes a positive number" >&2; exit 2; }
if [[ -n $(git -C "$top" status --porcelain --untracked-files=no) ]]; then
  echo "longrun: the working copy has uncommitted changes; the run takes the committed HEAD only" >&2
fi

sha=$(git -C "$top" rev-parse HEAD)
total=${AISOAK_SEEDS:-20}
per=$(( (total + shards - 1) / shards ))
mkdir -p "$root"
pids=()
dirs=()
for (( i = 0; i < shards; i++ )); do
  wt="$root/$name-$i"
  if [[ -d $wt ]]; then
    git -C "$wt" checkout -q --detach "$sha"
  else
    git -C "$top" worktree add -q --detach "$wt" "$sha"
  fi
  args=("$@")
  if [[ $shards -gt 1 ]]; then
    first=$(( 1 + i * per ))
    count=$(( total - i * per < per ? total - i * per : per ))
    if [[ $count -le 0 ]]; then
      continue
    fi
    args+=("-Paisoak.firstSeed=$first" "-Paisoak.seeds=$count")
  fi
  rm -f "$wt/longrun.exit"
  ( cd "$wt" && { ./gradlew --console=plain --no-daemon "${args[@]}" > longrun.log 2>&1; echo $? > longrun.exit; } ) &
  pids+=($!)
  dirs+=("$wt")
  echo "shard $i: ${sha:0:8} in $wt: ${args[*]}"
done

failed=0
for (( j = 0; j < ${#pids[@]}; j++ )); do
  wait "${pids[$j]}" || true
  code=$(cat "${dirs[$j]}/longrun.exit" 2>/dev/null || echo 1)
  verdict=$([[ $code == 0 ]] && echo green || echo "red (exit $code)")
  echo "${dirs[$j]}: $verdict; $(grep -E 'BUILD (SUCCESSFUL|FAILED) in' "${dirs[$j]}/longrun.log" | tail -1)"
  [[ $code == 0 ]] || failed=1
done
exit $failed
