#!/data/data/com.termux/files/usr/bin/bash
# Bundled terminal widget. TERM geometry is read on every frame after a resize.
mode=${1:-dashboard}
location=${2:-}
cache_dir=${XDG_CACHE_HOME:-$HOME/.cache}/retui-weather
mkdir -p "$cache_dir"
encoded_location=$(LC_ALL=C printf '%s' "$location" | od -An -v -tx1 | awk '{for(i=1;i<=NF;i++) printf "%%%s", $i}')
key=$(printf '%s' "$location" | cksum | awk '{print $1}')
cache="$cache_dir/$key.txt"
fetch_pid=
cleanup() { [ -z "$fetch_pid" ] || kill "$fetch_pid" 2>/dev/null; printf '\033[?25h\033[0m\033[?1049l'; }
trap cleanup EXIT
trap 'exit 0' INT TERM HUP
printf '\033[?1049h\033[?25l'
last_fetch=0
while :; do
    now=$(date +%s)
    read -r height width < <(stty size 2>/dev/null) || true
    width=${width:-$(tput cols 2>/dev/null || printf 40)}
    height=${height:-$(tput lines 2>/dev/null || printf 12)}
    if { [ "$mode" = weather ] || [ "$mode" = dashboard ]; } && [ -n "$location" ] && [ $((now-last_fetch)) -ge 900 ]; then
        if [ -z "$fetch_pid" ] || ! kill -0 "$fetch_pid" 2>/dev/null; then
            last_fetch=$now
            (
                tmp=$(mktemp "$cache.XXXXXX") || exit 1
                if curl --fail --silent --show-error --max-time 12 --get --data-urlencode 'format=%l: %C %t feels %f humidity %h' "https://wttr.in/$encoded_location" > "$tmp" 2>/dev/null && [ -s "$tmp" ]; then
                    LC_ALL=C tr -d '\000-\010\013-\037\177' < "$tmp" > "$tmp.clean"
                    mv "$tmp.clean" "$cache"
                fi
                rm -f "$tmp"
            ) &
            fetch_pid=$!
        fi
    fi
    output=
    block_clock=false
    if [ "$mode" = clock ] || [ "$mode" = dashboard ]; then
        clock=$(date '+%H:%M:%S')
        if [ "$width" -ge 23 ] && [ "$height" -ge 8 ]; then
            block_clock=true
            clock=$(date '+%H:%M' | awk '
                BEGIN {
                    d[0]="###|# #|# #|# #|###"; d[1]=" # |## | # | # |###"
                    d[2]="###|  #|###|#  |###"; d[3]="###|  #|###|  #|###"
                    d[4]="# #|# #|###|  #|  #"; d[5]="###|#  |###|  #|###"
                    d[6]="###|#  |###|# #|###"; d[7]="###|  #|  #|  #|  #"
                    d[8]="###|# #|###|# #|###"; d[9]="###|# #|###|  #|###"
                    d[":"]="   | # |   | # |   "
                }
                { for(row=1;row<=5;row++) { for(i=1;i<=length($0);i++) { split(d[substr($0,i,1)], parts, "[|]"); printf "%s ", parts[row] } printf "\n" } }
            ')
        fi
        output="$clock
$(date '+%a %d %b %Y')"
    fi
    if [ "$mode" = weather ] || [ "$mode" = dashboard ]; then
        weather='Weather unavailable — retrying periodically'
        [ ! -s "$cache" ] || weather=$(head -c 1000 "$cache")
        output="${output}${output:+

}${weather}"
    fi
    # Assemble first; never expose a cleared terminal between capture frames.
    rendered=$(printf '%s\n' "$output" | fold -s -w "${width:-40}" | head -n "$height")
    # Wrap the ASCII digit pattern before expanding it to single-cell UTF-8 blocks.
    # Byte-oriented fold must never split a block glyph in the middle.
    if [ "$block_clock" = true ]; then
        rendered=$(printf '%s\n' "$rendered" | sed '1,5s/#/█/g')
    fi
    if [ "$rendered" != "${last_rendered-}" ] || [ "$width:$height" != "${last_geometry-}" ]; then
        # Clear each line's tail so shorter content cannot leave old text behind.
        screen=$(printf '%s\n' "$rendered" | awk '{ printf "%s\033[K\n", $0 }')
        printf '\033[H\033[36m%s\033[0m\033[J' "$screen"
        last_rendered=$rendered
        last_geometry="$width:$height"
    fi
    [ "${RETUI_WIDGET_ONCE:-0}" = 1 ] && break
    sleep 1
done
