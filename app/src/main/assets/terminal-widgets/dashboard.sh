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
# Static system information is collected once, not on every clock tick.
if [ "$mode" = fetch ]; then
    model=$(getprop ro.product.model 2>/dev/null || true)
    android=$(getprop ro.build.version.release 2>/dev/null || true)
    kernel=$(uname -r)
    arch=$(uname -m)
    # A terminal icon spinning about its vertical axis, drawn as 20x22 pixel art with two
    # pixels per cell. o outline, k body, w prompt, s the edge seen while it turns. The back
    # is the face mirrored without its prompt. All 12 frames are worked out once, here.
    icon='..oooooooooooooooo..
.okkkkkkkkkkkkkkkko.
okkkkkkkkkkkkkkkkkko
okkkkkkkkkkkkkkkkkko
okkwwkkkkkkkkkkkkkko
okkkwwkkkkkkkkkkkkko
okkkkwwkkkkkkkkkkkko
okkkkkwwkkkkkkkkkkko
okkkkwwkkkkkkkkkkkko
okkkwwkkkkkkkkkkkkko
okkwwkkkkkkkkkkkkkko
okkkkkkkkwwwwwwkkkko
okkkkkkkkwwwwwwkkkko
okkkkkkkkkkkkkkkkkko
okkkkkkkkkkkkkkkkkko
okkkkkkkkkkkkkkkkkko
.okkkkkkkkkkkkkkkko.
..oooooooooooooooo..'
    frames=()
    frame_text=
    while IFS= read -r line; do
        if [ "$line" = '--' ]; then frames+=("$frame_text"); frame_text=; else frame_text+="$line"$'\n'; fi
    done < <(printf '%s\n' "$icon" | awk -v n=12 '
        { face[NR-1]=$0 }
        END {
            c["o"]=37; c["k"]=30; c["w"]=97; c["s"]=90; up="\342\226\200"; down="\342\226\204"
            for (f=0; f<n; f++) {
                a=2*3.14159265*f/n; co=cos(a); si=sin(a); ac=(co<0?-co:co); as=(si<0?-si:si)
                half=10*ac; t=2.5*as; side=(co*si<0?1:-1)
                for (y=0; y<22; y++) for (x=0; x<20; x++) px[y,x]="."
                for (y=0; y<18; y++) for (x=0; x<20; x++) {
                    d=x+0.5-10; ch="."
                    if (ac>0.02 && (d<0?-d:d)<half) {
                        u=int(d/ac+10); if (co<0) u=19-u
                        ch=substr(face[y],u+1,1); if (co<0 && ch=="w") ch="k"
                    } else if (y>=1 && y<=16 && ((side>0 && d>=half && d<half+t) || (side<0 && d>=-half-t && d<-half))) ch="s"
                    px[y+2,x]=ch
                }
                for (y=0; y<22; y+=2) {
                    line=""
                    for (x=0; x<20; x++) {
                        t2=px[y,x]; b=px[y+1,x]
                        if (t2=="." && b==".") line=line "\033[0m "
                        else if (b==".") line=line "\033[0;" c[t2] "m" up
                        else if (t2==".") line=line "\033[0;" c[b] "m" down
                        else line=line "\033[0;" c[t2] ";" (c[b]+10) "m" up
                    }
                    print line "\033[0m"
                }
                print "--"
            }
        }')
    frame=0
    esc=$'\033'
    # draw_fetch <rows>: one frame of the spin beside (or above) the details, built without
    # spawning processes since it runs five times a second.
    draw_fetch() {
        local art_rows=() lines=() i screen=
        while IFS= read -r i; do art_rows+=("$i"); done <<< "${frames[frame % ${#frames[@]}]%$'\n'}"
        frame=$((frame+1))
        if [ "$layout" = side ]; then
            for ((i=0; i<${#info_rows[@]} || i<${#art_rows[@]}; i++)); do
                lines+=("${art_rows[i]:-$(printf '%20s' '')}$esc[0;36m  ${info_rows[i]:0:$((width-22))}")
            done
        else
            lines=("${art_rows[@]}" "$esc[0;36m" "${info_rows[@]}")
        fi
        for ((i=0; i<${#lines[@]} && i<height; i++)); do screen+="${lines[i]}$esc[K"$'\n'; done
        printf '%s' "$esc[H$esc[36m$screen$esc[0m$esc[J"
    }
fi
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
    if [ "$mode" = fetch ]; then
        if [ $((now-${last_stats:-0})) -ge 30 ]; then
            uptime_text=$(awk '{d=int($1/86400); h=int($1/3600)%24; m=int($1/60)%60; printf "%dd %dh %dm",d,h,m}' /proc/uptime 2>/dev/null)
            memory=$(awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2} END {if(t) printf "%d / %d MiB", (t-a)/1024,t/1024; else print "unavailable"}' /proc/meminfo 2>/dev/null)
            storage=$(df -h "$HOME" 2>/dev/null | awk 'NR>1 {used=$(NF-3); total=$(NF-4); percent=$(NF-1)} END {printf "%s / %s (%s)",used,total,percent}')
            last_stats=$now
        fi
        info="RE:TUI / Termux
$(date '+%a %d %b %Y')
$(date '+%H:%M:%S %Z')
Device: ${model:-Android device}
Android: ${android:-unavailable}
Kernel: $kernel
Arch: $arch
Uptime: ${uptime_text:-unavailable}
RAM: ${memory:-unavailable}
Home: ${storage:-unavailable}
Shell: Bash ${BASH_VERSION%%(*}"
        layout=
        if [ "$width" -ge 46 ] && [ "$height" -ge 11 ]; then
            layout=side
        elif [ "$width" -ge 20 ] && [ "$height" -ge 24 ]; then
            layout=stack
        fi
        if [ -n "$layout" ]; then
            info_rows=()
            while IFS= read -r line; do info_rows+=("$line"); done < <(
                if [ "$layout" = side ]; then printf '%s\n' "$info"; else printf '%s\n' "$info" | fold -s -w "$width"; fi)
            # Five frames a second; the details above refresh once a second.
            for step in 1 2 3 4 5; do
                draw_fetch
                [ "${RETUI_WIDGET_ONCE:-0}" = 1 ] && break 2
                sleep 0.2
            done
            last_rendered=
            continue
        fi
        # Too small for the art: the details matter more.
        output="$info"
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
