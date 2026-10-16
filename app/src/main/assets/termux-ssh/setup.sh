set -eu
umask 077
export PATH="$PREFIX/bin:$PATH"
reported=0
stage=''
fail() { reported=1; printf '__RETUI_SSH_ERROR__ %s\n' "$*"; exit 1; }
finish() {
    result=$?
    [ -z "$stage" ] || rm -rf -- "$stage"
    if [ "$result" -ne 0 ] && [ "$reported" = 0 ]; then
        printf '__RETUI_SSH_ERROR__ %s\n' 'Termux setup could not finish. Check available storage and Termux permissions, then Retry.'
    fi
}
trap finish EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
public_key=${1:-}
install=${2:-0}
dispatcher=${3:-}
case "$install" in 0|1) ;; *) fail 'Invalid installation request';; esac
case "$public_key" in 'ecdsa-sha2-nistp256 '*) ;; *) fail 'Invalid Re:TUI public key';; esac
case "$public_key" in *'
'*|*"$(printf '\r')"*) fail 'Invalid Re:TUI public key';; esac
[ "${#public_key}" -le 1024 ] || fail 'Re:TUI public key is too large'
[ "${#dispatcher}" -le 65536 ] || fail 'Re:TUI SSH command support is too large'
missing=''
command -v tmux >/dev/null 2>&1 || missing="$missing tmux"
command -v sshd >/dev/null 2>&1 || missing="$missing openssh"
command -v flock >/dev/null 2>&1 || missing="$missing util-linux"
if [ -n "$missing" ]; then
    [ "$install" = 1 ] || fail 'Enable Termux pane to install missing tmux/OpenSSH support.'
    # Package names above are fixed; splitting is intentional.
    pkg install -y $missing || fail 'Package installation failed. Check Termux internet access and finish any running package installation, then Retry.'
fi
command -v tmux >/dev/null 2>&1 && command -v sshd >/dev/null 2>&1 && command -v flock >/dev/null 2>&1 || fail 'Termux requires tmux and openssh. Run pkg install tmux openssh util-linux, then Retry.'
state="$HOME/.retui/ssh"
mkdir -p "$state"
chmod 700 "$state"
stage=$(mktemp -d "$state/.setup.XXXXXXXX") || fail 'Could not prepare Termux setup files.'
printf '%s\n' "$public_key" > "$stage/authorized_keys"
ssh-keygen -l -f "$stage/authorized_keys" >/dev/null 2>&1 || fail 'Invalid Re:TUI public key'
printf '%s' "$dispatcher" | base64 -d > "$stage/dispatch.body" || fail 'Could not prepare SSH command support.'
{ printf '#!%s/bin/sh\n' "$PREFIX"; cat "$stage/dispatch.body"; } > "$stage/dispatch.sh"
sh -n "$stage/dispatch.sh" || fail 'Termux SSH command support failed validation.'
chmod 700 "$stage/dispatch.sh"
if [ ! -e "$state/host_ecdsa" ]; then
    # A kernel lock survives no process: Android may kill setup without running shell traps.
    exec 9> "$state/host-key.lock"
    attempts=0
    until flock -n 9; do
        attempts=$((attempts + 1))
        [ "$attempts" -lt 100 ] || fail 'Another Re:TUI setup is creating the SSH key. Wait, then Retry.'
        sleep 0.1
    done
    if [ ! -e "$state/host_ecdsa" ]; then
        ssh-keygen -q -t ecdsa -b 256 -N '' -f "$stage/host_ecdsa" || fail 'Could not create the Termux host key.'
        mv "$stage/host_ecdsa" "$state/host_ecdsa" || fail 'Could not save the Termux host key.'
    fi
    flock -u 9
    exec 9>&-
fi
chmod 600 "$state/host_ecdsa"
ssh-keygen -y -P '' -f "$state/host_ecdsa" > "$stage/host.public.raw" 2>/dev/null || fail 'The dedicated Termux host key is damaged or locked. Restore its unencrypted key before reconnecting.'
awk '{print $1 " " $2}' "$stage/host.public.raw" > "$stage/host_ecdsa.pub"
host_key=$(cat "$stage/host_ecdsa.pub")
case "$host_key" in 'ecdsa-sha2-nistp256 '*) ;; *) fail 'The dedicated Termux host key must use ECDSA P-256.';; esac
username=$(whoami)
case "$username" in ''|*[!A-Za-z0-9_-]*) fail 'Invalid Termux username';; esac
port=8928
# A foreign SSH listener must not be mistaken for this dedicated endpoint.
own_listener=0
if [ -r "$state/sshd.pid" ]; then
    pid=$(cat "$state/sshd.pid")
    case "$pid" in ''|*[!0-9]*) ;; *)
        if kill -0 "$pid" 2>/dev/null &&
            [ "$(readlink "/proc/$pid/exe" 2>/dev/null)" = "$PREFIX/bin/sshd" ] &&
            tr '\000' '\n' < "/proc/$pid/cmdline" 2>/dev/null | grep -F -x -- "$state/sshd_config" >/dev/null; then
            own_listener=1
        fi ;;
    esac
fi
# Repeated unauthenticated scans penalize legitimate reconnects on modern OpenSSH.
# A verified owned daemon needs no scan; JSch still authenticates its pinned host key.
existing_keys=''
if [ "$own_listener" = 0 ]; then
    existing_keys=$(ssh-keyscan -T 1 -p "$port" 127.0.0.1 2>/dev/null | awk '!/^#/ && NF >= 3 {print $2 " " $3}') || existing_keys=''
fi
if [ -n "$existing_keys" ] && ! printf '%s\n' "$existing_keys" | grep -F -x -- "$host_key" >/dev/null; then
    fail 'Another SSH service is using the Re:TUI connection port (8928). Stop that service or move it to another port, then Retry.'
fi
cat > "$stage/sshd_config" <<EOF_CONFIG
Port $port
ListenAddress 127.0.0.1
AddressFamily inet
HostKey "$state/host_ecdsa"
PidFile "$state/sshd.pid"
AuthorizedKeysFile "$state/authorized_keys"
AllowUsers $username
AuthenticationMethods publickey
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitEmptyPasswords no
PermitRootLogin no
StrictModes yes
DisableForwarding yes
PermitUserRC no
PermitUserEnvironment no
PermitTTY yes
UseDNS no
LogLevel ERROR
ForceCommand "$state/dispatch.sh"
EOF_CONFIG
sshd -t -f "$stage/sshd_config" || fail 'Termux SSH configuration failed validation. Update openssh in Termux, then Retry.'
# Publish only validated complete files; failed retries leave the working endpoint intact.
mv -f "$stage/dispatch.sh" "$state/dispatch.sh"
mv -f "$stage/authorized_keys" "$state/authorized_keys"
mv -f "$stage/host_ecdsa.pub" "$state/host_ecdsa.pub"
mv -f "$stage/sshd_config" "$state/sshd_config"
encoded_host_key=$(printf '%s' "$host_key" | base64 | tr -d '\n')
printf '__RETUI_SSH_USER__ %s\n__RETUI_SSH_PORT__ %s\n__RETUI_SSH_HOST_KEY__ %s\n' "$username" "$port" "$encoded_host_key"
