#!/usr/bin/env bash
# Copy local-only files (not in git; see .git/info/exclude) from the primary
# checkout into this checkout. Fresh git worktrees do not inherit these, so a
# subagent working in `.worktrees/<n>-<slug>` cannot see AGENTS.md, docs/agents/,
# dev-docs/, journeys/, local.properties, etc. unless they are copied in.
#
# Usage: run from INSIDE the target checkout (a worktree). Safe to re-run; it
# only copies entries that are present in the primary checkout and missing here.
#
# This is project-specific content and stays in the repo — it is not wired into
# any global skill. Invoke it after `git worktree add` (or before Gradle), e.g.:
#   scripts/bootstrap-worktree.sh
set -euo pipefail

# Paths that live only on the primary checkout (local-only, never committed).
LOCAL_ONLY=(
  local.properties
  google-services.json
  AGENTS.md
  CLAUDE.md
  CONTEXT.md
  CONTEXT-MAP.md
  docs/agents
  dev-docs
  journeys
  .scratch
  .github/ISSUE_TEMPLATE/agent-ready-slice.md
)

main() {
  local checkout source
  checkout="$(git rev-parse --show-toplevel)"
  # Strip the `worktree ` prefix and keep the whole path — the checkout path can
  # contain spaces, so a naive `awk '{print $2}'` would truncate it here.
  source="$(git worktree list --porcelain | awk '/^worktree / { sub(/^worktree /, ""); print; exit }')"

  if [[ -z "$source" ]]; then
    echo "[bootstrap-worktree] no worktree info found; nothing to do." >&2
    return 0
  fi
  if [[ "$checkout" == "$source" ]]; then
    echo "[bootstrap-worktree] this checkout is the primary checkout; nothing to copy."
    return 0
  fi

  local copied=0
  for rel in "${LOCAL_ONLY[@]}"; do
    if [[ -e "$source/$rel" && ! -e "$checkout/$rel" ]]; then
      mkdir -p "$(dirname "$checkout/$rel")"
      cp -R "$source/$rel" "$checkout/$rel"
      echo "[bootstrap-worktree] copied $rel"
      copied=1
    fi
  done
  if [[ $copied -eq 0 ]]; then
    echo "[bootstrap-worktree] no local-only files needed copying."
  fi
}

main "$@"
