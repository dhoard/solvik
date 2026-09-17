#!/usr/bin/env bash
set -Eeuo pipefail

readonly MODEL="deepseek/deepseek-v4-flash"
readonly THINKING_LEVEL="medium"
readonly MAX_ATTEMPTS_PER_PHASE=8

usage() {
    printf '%s\n' \
        "Usage: ./workflow.sh [--checkpoint-dirty | --resume-dirty]" \
        "" \
        "Runs fresh Pi agent sessions until docs/STATUS.md records NEXT as COMPLETE." \
        "Productive partial runs continue the same phase in a fresh context. Each" \
        "completed phase is independently checked with ./build.sh and" \
        "./build-native.sh, then committed locally." \
        "" \
        "  --checkpoint-dirty  Validate and commit existing changes before starting NEXT." \
        "                      Use only when STATUS already describes those completed changes." \
        "  --resume-dirty      Treat existing changes as incomplete work for the current NEXT" \
        "                      phase and let a fresh worker finish them." \
        "  --help              Show this help." \
        "" \
        "The default requires a clean worktree. The workflow never pushes commits."
}

die() {
    printf 'workflow.sh: %s\n' "$*" >&2
    exit 1
}

dirty_mode="reject"
while (($# > 0)); do
    case "$1" in
        --checkpoint-dirty)
            [[ "$dirty_mode" == "reject" ]] ||
                die "choose only one dirty-worktree option"
            dirty_mode="checkpoint"
            ;;
        --resume-dirty)
            [[ "$dirty_mode" == "reject" ]] ||
                die "choose only one dirty-worktree option"
            dirty_mode="resume"
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            usage >&2
            die "unknown option: $1"
            ;;
    esac
    shift
done

command -v git >/dev/null 2>&1 || die "git is required"
command -v pi >/dev/null 2>&1 || die "pi is required"
command -v tee >/dev/null 2>&1 || die "tee is required"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" ||
    die "run this script from the Solvik repository"
cd "$repo_root"

readonly STATUS_FILE="$repo_root/docs/STATUS.md"
readonly PROMPT_FILE="$repo_root/prompts/IMPLEMENT_SOLVIK.md"

[[ -f "$STATUS_FILE" ]] || die "missing $STATUS_FILE"
[[ -f "$PROMPT_FILE" ]] || die "missing $PROMPT_FILE"
[[ -x "$repo_root/build.sh" ]] || die "build.sh is missing or not executable"
[[ -x "$repo_root/build-native.sh" ]] ||
    die "build-native.sh is missing or not executable"
git symbolic-ref --quiet HEAD >/dev/null ||
    die "a checked-out branch is required; detached HEAD is not supported"

readonly lock_file="/tmp/solvik-workflow${repo_root//\//_}.lock"
exec 9>"$lock_file"
if command -v flock >/dev/null 2>&1; then
    flock -n 9 || die "another Solvik workflow appears to be running"
fi

run_stamp="$(date '+%Y%m%d-%H%M%S')"
log_dir="${WORKFLOW_LOG_DIR:-/tmp/solvik-workflow-$run_stamp}"
mkdir -p "$log_dir"

next_phase() {
    local values
    values="$(sed -n 's/^- .*NEXT.*: //p' "$STATUS_FILE")"
    [[ -n "$values" ]] || die "could not find the NEXT marker in $STATUS_FILE"
    [[ "$(printf '%s\n' "$values" | wc -l)" -eq 1 ]] ||
        die "$STATUS_FILE must contain exactly one NEXT marker"
    printf '%s\n' "$values"
}

phase_number() {
    local value="$1"
    if [[ "$value" =~ ^Phase[[:space:]]+([0-9]+)([[:space:]]|$) ]]; then
        printf '%s\n' "${BASH_REMATCH[1]}"
        return
    fi
    die "invalid NEXT value: $value"
}

working_tree_dirty() {
    [[ -n "$(git status --porcelain --untracked-files=all)" ]]
}

worktree_fingerprint() {
    {
        git diff --no-ext-diff --binary HEAD
        while IFS= read -r -d '' path; do
            printf 'untracked:%s\0' "$path"
            git hash-object -- "$path"
        done < <(git ls-files --others --exclude-standard -z)
    } | git hash-object --stdin
}

commit_all() {
    local message="$1"
    git add -A
    git diff --cached --quiet &&
        die "there are no changes to commit for: $message"
    git -c commit.gpgSign=false commit -m "$message"
}

validate_independently() {
    printf '\n==> Independent JVM validation: ./build.sh\n'
    ./build.sh
    printf '\n==> Independent native validation: ./build-native.sh\n'
    ./build-native.sh
}

initial_next="$(next_phase)"
if [[ "$initial_next" == "COMPLETE" ]]; then
    printf 'Solvik implementation is already complete.\n'
    exit 0
fi
phase_number "$initial_next" >/dev/null

if working_tree_dirty; then
    case "$dirty_mode" in
        checkpoint)
            printf 'Validating existing changes before creating the initial checkpoint.\n'
            validate_independently
            [[ "$(next_phase)" == "$initial_next" ]] ||
                die "NEXT changed during initial validation"
            commit_all "chore: checkpoint Solvik work before automated phases"
            ;;
        resume)
            printf 'Resuming uncommitted work for %s.\n' "$initial_next"
            ;;
        reject)
            die "the worktree is dirty; commit it first, or use --checkpoint-dirty or --resume-dirty"
            ;;
    esac
elif [[ "$dirty_mode" != "reject" ]]; then
    printf 'Worktree is clean; the selected dirty-worktree option has no effect.\n'
fi

prompt="$(printf '%s\n' \
    "Follow prompts/IMPLEMENT_SOLVIK.md exactly." \
    "Read AGENTS.md and every authoritative document it names before editing." \
    "Execute only the phase marked NEXT in docs/STATUS.md." \
    "If there is existing uncommitted work, treat it as partial work for that same phase and finish it." \
    "Meet every exit criterion and required positive and negative test for the phase." \
    "Run the required repository build wrapper or wrappers before updating status." \
    "Update docs/STATUS.md with concrete evidence only after the phase is complete." \
    "Advance NEXT by exactly one phase; after Phase 16 set NEXT to COMPLETE." \
    "Do not begin another phase, commit, amend, reset, push, or alter git history." \
    "When the phase is complete, stop after your final response.")"

active_phase=""
attempt=0

while true; do
    before="$(next_phase)"
    if [[ "$before" == "COMPLETE" ]]; then
        printf '\nAll Solvik phases are complete. Logs: %s\n' "$log_dir"
        exit 0
    fi

    if [[ "$before" != "$active_phase" ]]; then
        active_phase="$before"
        attempt=0
    fi
    attempt=$((attempt + 1))
    ((attempt <= MAX_ATTEMPTS_PER_PHASE)) ||
        die "$before remained incomplete after $MAX_ATTEMPTS_PER_PHASE attempts"

    before_number="$(phase_number "$before")"
    before_head="$(git rev-parse HEAD)"
    before_fingerprint="$(worktree_fingerprint)"
    log_file="$log_dir/phase-$before_number-attempt-$attempt.log"

    printf '\n==> Worker attempt %d/%d: %s\n' \
        "$attempt" "$MAX_ATTEMPTS_PER_PHASE" "$before"
    printf '    model: %s\n' "$MODEL"
    printf '    thinking: %s\n' "$THINKING_LEVEL"
    printf '    log:   %s\n' "$log_file"

    set +e
    pi --approve --no-session --mode json --model "$MODEL" \
        --thinking "$THINKING_LEVEL" -- "$prompt" \
        2>&1 | tee "$log_file"
    pi_status="${PIPESTATUS[0]}"
    set -e

    ((pi_status == 0)) ||
        die "Pi exited with status $pi_status during $before; fix or resume the phase"

    after="$(next_phase)"
    after_fingerprint="$(worktree_fingerprint)"
    if [[ "$after" == "$before" ]]; then
        [[ "$after_fingerprint" != "$before_fingerprint" ]] ||
            die "worker made no repository progress and left NEXT unchanged during $before"
        printf '==> %s remains incomplete; continuing in a fresh context.\n' "$before"
        continue
    fi

    if ((before_number < 16)); then
        expected=$((before_number + 1))
        expected_pattern="^Phase[[:space:]]+${expected}([[:space:]]|$)"
        [[ "$after" =~ $expected_pattern ]] ||
            die "invalid phase transition: $before -> $after; expected Phase $expected"
    elif ((before_number == 16)); then
        [[ "$after" == "COMPLETE" ]] ||
            die "Phase 16 must set NEXT to COMPLETE, but found: $after"
    else
        die "phase number is outside the implementation plan: $before_number"
    fi

    if [[ "$(git rev-parse HEAD)" != "$before_head" ]]; then
        die "worker changed git history during $before"
    fi
    working_tree_dirty ||
        die "worker advanced STATUS without producing repository changes for $before"

    validate_independently
    [[ "$(next_phase)" == "$after" ]] ||
        die "NEXT changed during independent validation"

    commit_all "implement Solvik phase $before_number"
    printf '==> Completed and checkpointed %s\n' "$before"
done
