set -eu
export PATH="$PREFIX/bin:$PATH"
state="$HOME/.retui/ssh"
config="$state/sshd_config"
is_listener() {
    case "$1" in ''|*[!0-9]*) return 1;; esac
    kill -0 "$1" 2>/dev/null &&
        [ "$(readlink "/proc/$1/exe" 2>/dev/null)" = "$PREFIX/bin/sshd" ] &&
        tr '\000' '\n' < "/proc/$1/cmdline" 2>/dev/null | grep -F -x -- "$config" >/dev/null
}
if [ -r "$state/sshd.pid" ]; then
    pid=$(cat "$state/sshd.pid")
    if is_listener "$pid"; then exit 0; fi
fi
# A failed competing startup can overwrite sshd's PID file before failing to bind.
# Recover the actual dedicated listener; never treat an authenticated child as the daemon.
for process in /proc/[0-9]*; do
    pid=${process##*/}
    if is_listener "$pid"; then
        printf '%s\n' "$pid" > "$state/sshd.pid"
        exit 0
    fi
done
exec "$PREFIX/bin/sshd" -D -e -f "$config"
