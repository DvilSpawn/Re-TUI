set -eu
umask 077
export PATH="$PREFIX/bin:$PATH"
root=retui_workspace
fail() { printf '%s\n' "$*" >&2; exit 1; }
has_root() { tmux has-session -t "=$root" 2>/dev/null; }
valid_window() {
    case "$1" in @*[!0-9]*|@|'') return 1;; @*) ;; *) return 1;; esac
    tmux list-windows -t "=$root" -F '#{window_id}' | grep -F -x -- "$1" >/dev/null
}
decode() {
    [ "$1" != - ] || return 0
    case "$1" in *[!A-Za-z0-9+/=]*) return 1;; esac
    printf '%s' "$1" | base64 -d
}
new_shell() {
    if command -v bash >/dev/null 2>&1; then tmux "$@" "$(command -v bash)" --noprofile --norc
    else tmux "$@" "$PREFIX/bin/sh"; fi
}
# Only whitespace-delimited validated fields are accepted; never evaluate a client command.
set -f
set -- ${SSH_ORIGINAL_COMMAND:-}
operation=${1:-}
case "$operation" in
    list)
        [ "$#" = 1 ] || fail 'Invalid list arguments'
        has_root || exit 0
        tmux list-windows -t "=$root" -F '#{?pane_dead,,#{window_id}:#{window_index}:#{window_name}}'
        ;;
    mode)
        [ "$#" = 2 ] || fail 'Invalid mode arguments'
        has_root && valid_window "$2" || fail 'Workspace window no longer exists'
        tmux display-message -p -t "$2" '#{alternate_on}'
        ;;
    new)
        [ "$#" = 3 ] || fail 'Invalid new arguments'
        name=$(decode "$2") || fail 'Invalid window name encoding'
        command=$(decode "$3") || fail 'Invalid command encoding'
        # Preserve the existing helper shell choice and literal initial-command behavior.
        if has_root; then
            if [ -n "$name" ]; then target=$(new_shell new-window -d -P -F '#{window_id}' -t "$root:" -n "$name" -c "$HOME")
            else target=$(new_shell new-window -d -P -F '#{window_id}' -t "$root:" -c "$HOME"); fi
        else
            target=$(new_shell new-session -d -P -F '#{window_id}' -s "$root" -n "${name:-1}" -c "$HOME")
        fi
        if [ -n "$command" ]; then
            tmux send-keys -t "$target" -l -- "$command"
            tmux send-keys -t "$target" Enter
        fi
        printf '%s\n' "$target"
        ;;
    attach)
        [ "$#" = 3 ] || fail 'Invalid attach arguments'
        case "$2" in ''|*[!A-Za-z0-9_-]*) fail 'Invalid tile identity';; esac
        [ "${#2}" -le 96 ] || fail 'Tile identity too long'
        has_root && valid_window "$3" || fail 'Workspace window no longer exists'
        viewer="retui_view_$2"
        cleanup() { tmux kill-session -t "=$viewer" 2>/dev/null || true; }
        # A killed viewer only unlinks this grouped session; workspace windows remain alive.
        cleanup
        tmux new-session -d -s "$viewer" -t "$root"
        trap cleanup EXIT HUP INT TERM
        tmux set-option -t "=$viewer:" status off
        tmux select-window -t "$viewer:$3"
        tmux attach-session -t "=$viewer"
        ;;
    control)
        [ "$#" = 1 ] || fail 'Invalid control arguments'
        has_root || exit 0
        exec tmux -C attach-session -f no-output,ignore-size -t "=$root"
        ;;
    *) fail 'Unsupported Re:TUI SSH command';;
esac
