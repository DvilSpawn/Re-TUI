#!/data/data/com.termux/files/usr/bin/sh
# The signed APK supplies a pinned helper on stdin; never fetch mutable remote code.
set -eu
umask 077

expected_version="${1:?Expected version required}"
expected_sha="${2:?Expected SHA-256 required}"
case "$expected_version" in ''|*[!0-9.]*) exit 1 ;; esac
case "$expected_sha" in *[!0-9a-f]*) exit 1 ;; esac
[ "${#expected_sha}" = 64 ] || exit 1
prefix="${PREFIX:?Termux prefix required}"
bin_dir="$prefix/bin"
share_dir="$prefix/share/retui"
target="$bin_dir/retui"
previous="$share_dir/retui.previous"
version_file="$share_dir/version"
lock_dir="$share_dir/.update-lock"

fail() { printf '%s\n' "__RETUI_UPDATE_ERROR__ $1"; exit 1; }
for tool in sha256sum mktemp head wc chmod cp mv sh base64; do
    command -v "$tool" >/dev/null 2>&1 || fail tools
done
command -v tmux >/dev/null 2>&1 && command -v socat >/dev/null 2>&1 || fail dependencies
[ -d "$bin_dir" ] && [ ! -L "$bin_dir" ] || fail path
[ ! -L "$prefix/share" ] && [ ! -L "$share_dir" ] || fail path
mkdir -p "$share_dir"
for path in "$target" "$previous" "$version_file"; do
    [ ! -L "$path" ] || fail path
    [ ! -e "$path" ] || [ -f "$path" ] || fail path
done
mkdir "$lock_dir" 2>/dev/null || fail busy
stage=''
restore=''
promoted=0
had_target=0
had_version=0
cleanup() {
    result=$?
    trap - EXIT HUP INT TERM
    if [ "$promoted" = 1 ] && [ "$result" != 0 ]; then
        if [ "$had_target" = 1 ]; then
            mv -f "$restore" "$target" || printf '%s\n' '__RETUI_UPDATE_ERROR__ rollback'
            restore=''
        else
            rm -f "$target"
        fi
        if [ "$had_version" = 1 ]; then mv -f "$lock_dir/version-before" "$version_file"; else rm -f "$version_file"; fi
    fi
    [ -z "$stage" ] || rm -f "$stage"
    [ -z "$restore" ] || rm -f "$restore"
    rm -f "$lock_dir/version-before" "$lock_dir/version-next" "$lock_dir/previous-next" "$lock_dir/payload"
    rmdir "$lock_dir"
    exit "$result"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
stage="$(mktemp "$bin_dir/.retui-update.XXXXXX")"
# Termux may append a newline to stdin. Decode transport framing before hashing.
head -c 174766 > "$lock_dir/payload"
[ "$(wc -c < "$lock_dir/payload")" -le 174765 ] || fail size
base64 -d < "$lock_dir/payload" > "$stage" || fail encoding
[ "$(wc -c < "$stage")" -le 131072 ] || fail size
actual_sha="$(sha256sum "$stage")"
[ "${actual_sha%% *}" = "$expected_sha" ] || fail checksum
sh -n "$stage" || fail syntax
[ "$(sh "$stage" version)" = "$expected_version" ] || fail version
chmod 0755 "$stage"
if [ -f "$target" ]; then
    had_target=1
    restore="$(mktemp "$bin_dir/.retui-restore.XXXXXX")"
    cp -p "$target" "$restore"
    cp -p "$target" "$lock_dir/previous-next"
    mv -f "$lock_dir/previous-next" "$previous"
fi
if [ -f "$version_file" ]; then had_version=1; cp -p "$version_file" "$lock_dir/version-before"; fi
printf '%s\n' "$expected_version" > "$lock_dir/version-next"
promoted=1
mv -f "$stage" "$target"
stage=''
actual_sha="$(sha256sum "$target")"
[ "${actual_sha%% *}" = "$expected_sha" ] && [ "$(sh "$target" version)" = "$expected_version" ] || fail verification
mv -f "$lock_dir/version-next" "$version_file"
printf '%s\n' "__RETUI_UPDATE_OK__ $expected_version"
