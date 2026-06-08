#!/data/data/com.termux/files/usr/bin/sh
set -eu

RETUI_VERSION="0.3.1"
RETUI_DEFAULT_BASE_URL="https://raw.githubusercontent.com/DvilSpawn/Re-TUI/master/termux/retui"
RETUI_INSTALL_BASE_URL="${RETUI_INSTALL_BASE_URL:-$RETUI_DEFAULT_BASE_URL}"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

say() {
    printf '%s\n' "$*"
}

die() {
    printf '%s\n' "retui install: $*" >&2
    exit 1
}

need_dir() {
    mkdir -p "$1"
}

download() {
    url="$1"
    target="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$url" -o "$target"
        return
    fi
    if command -v wget >/dev/null 2>&1; then
        wget -qO "$target" "$url"
        return
    fi
    die "curl or wget is required"
}

install_tmux() {
    if command -v tmux >/dev/null 2>&1; then
        return
    fi
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y tmux
        return
    fi
    die "tmux missing and pkg was not found"
}

install_socat() {
    if command -v socat >/dev/null 2>&1; then
        return
    fi
    if command -v pkg >/dev/null 2>&1; then
        pkg install -y socat
        return
    fi
    die "socat missing and pkg was not found"
}

install_retui() {
    bin_dir="$PREFIX/bin"
    share_dir="$PREFIX/share/retui"
    need_dir "$bin_dir"
    need_dir "$share_dir"

    script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd || printf '')"
    tmp_file="$(mktemp)"
    trap 'rm -f "$tmp_file"' EXIT HUP INT TERM

    if [ -n "$script_dir" ] && [ -f "$script_dir/bin/retui" ]; then
        cp "$script_dir/bin/retui" "$tmp_file"
    else
        download "$RETUI_INSTALL_BASE_URL/bin/retui" "$tmp_file"
    fi

    install -m 0755 "$tmp_file" "$bin_dir/retui"
    printf '%s\n' "$RETUI_VERSION" > "$share_dir/version"
}

install_tmux
install_socat
install_retui

say "retui $RETUI_VERSION installed"
say "test with: retui bridge status"
