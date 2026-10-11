"""Explicit, VPS-only dvil reservation/policy setup. Never prints credentials or raw IRC frames.

Run as root with --apply. Existing account ownership is checked, never overwritten.
Requires the VPS's existing PyYAML and bcrypt. Config rollback copies contain secrets.
"""
import argparse
import base64
import copy
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import ssl
import subprocess
import time


class Peer:
    def __init__(self, host, nickname, password=None):
        self.sock = ssl.create_default_context().wrap_socket(
            socket.create_connection((host, 6697), timeout=10), server_hostname=host)
        self.sock.settimeout(1)
        self.buffer = b""
        self.send("CAP LS 302")
        self.send("NICK " + nickname)
        self.send("USER " + nickname + " 0 * :ReTUI provisioning")
        self.wait(lambda cmd, p: cmd == "CAP" and p[1] == "LS" and p[2] != "*")
        if password is not None:
            self.send("CAP REQ :sasl echo-message")
            self.wait(lambda cmd, p: cmd == "CAP" and p[1] == "ACK")
            self.send("AUTHENTICATE PLAIN")
            self.wait(lambda cmd, p: cmd == "AUTHENTICATE" and p == ["+"])
            auth = base64.b64encode(("\0" + nickname + "\0" + password).encode()).decode()
            self.send("AUTHENTICATE " + auth)
            self.wait(lambda cmd, p: cmd == "903")
        self.send("CAP END")
        self.wait(lambda cmd, p: cmd == "001")

    def send(self, line):
        if "\r" in line or "\n" in line or len(line.encode()) > 510:
            raise RuntimeError("Invalid outgoing frame")
        self.sock.sendall((line + "\r\n").encode())

    def wait(self, match, timeout=12):
        end = time.monotonic() + timeout
        seen = []
        while time.monotonic() < end:
            if b"\n" not in self.buffer:
                try:
                    data = self.sock.recv(8192)
                except socket.timeout:
                    continue
                if not data:
                    raise RuntimeError("IRC connection closed")
                self.buffer += data
                if len(self.buffer) > 65536:
                    raise RuntimeError("IRC input limit exceeded")
                continue
            raw, self.buffer = self.buffer.split(b"\n", 1)
            line = raw.decode().rstrip("\r")
            if line.startswith("@"):
                line = line.split(" ", 1)[1]
            if line.startswith(":"):
                line = line.split(" ", 1)[1]
            middle, sep, tail = line.partition(" :")
            words = middle.split()
            cmd, params = words[0], words[1:] + ([tail] if sep else [])
            if cmd == "PING":
                self.send("PONG :" + params[-1])
                continue
            if cmd in ("ERROR", "904", "905", "906", "907", "433", "464"):
                raise RuntimeError("IRC rejected authentication or nickname")
            seen.append((cmd, params))
            if match(cmd, params):
                return seen
        raise RuntimeError("IRC response timed out (raw frames withheld)")

    def notice(self, command, service="NickServ"):
        self.send("PRIVMSG " + service + " :" + command)
        return self.wait(lambda cmd, p: cmd == "NOTICE")[-1][1][-1]

    def close(self):
        try:
            self.send("QUIT :Setup check complete")
        finally:
            self.sock.close()


def secure_json(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as output:
        json.dump(value, output, indent=2)
        output.write("\n")


def main():
    import bcrypt
    import yaml
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--rotate-history-password", action="store_true")
    args = parser.parse_args()
    if not args.apply:
        raise SystemExit("No changes made. Read docs/irc.md before using --apply on the VPS.")
    if os.geteuid() != 0:
        raise SystemExit("VPS root access required")
    config_path = Path("/etc/ergo/ircd.yaml")
    credentials_path = Path("/root/retui-irc-dvil.json")
    config = yaml.safe_load(config_path.read_text())
    host = config["server"]["name"]
    if host != "irc.kokodoko.cloud" or set(config.get("opers", {})) - {"dvil"}:
        raise RuntimeError("Unexpected network/operator configuration; nothing changed")
    probe = Peer(host, "check" + secrets.token_hex(4))
    try:
        available = "not registered" in probe.notice("INFO dvil")
    finally:
        probe.close()
    if not credentials_path.exists():
        if not available:
            raise RuntimeError("dvil is claimed; existing account was not modified")
        secure_json(credentials_path, {"server": host, "port": 6697, "account": "dvil",
                    "password": secrets.token_urlsafe(32), "operator": "dvil",
                    "operator_password": secrets.token_urlsafe(32)})
    if credentials_path.stat().st_mode & 0o077:
        raise RuntimeError("Credential file must be private (0600)")
    credentials = json.loads(credentials_path.read_text())
    if available:
        registrant = Peer(host, "dvil")
        try:
            registrant.notice("REGISTER " + credentials["password"])
        finally:
            registrant.close()
    # Successful SASL proves that any existing registration belongs to this recovery file.
    owner = Peer(host, "dvil", credentials["password"])
    try:
        print("PASS: dvil reserved and SASL authentication verified")
        changed = copy.deepcopy(config)
        changed["channels"]["default-modes"] = "+ntCR"
        changed["channels"]["operator-only-creation"] = True
        changed["channels"]["registration"]["operator-only"] = True
        # Pilot remains closed to new accounts until public release is explicitly approved.
        changed["accounts"]["registration"]["enabled"] = False
        changed["oper-classes"]["retui-moderator"] = {"title": "ReTUI Moderator",
            "capabilities": ["kill", "ban", "sajoin", "samode", "chanreg"]}
        changed["opers"] = {"dvil": {"class": "retui-moderator",
            "password": bcrypt.hashpw(credentials["operator_password"].encode(), bcrypt.gensalt(12)).decode()}}
        backup = Path("/root/retui-ergo-config-" + time.strftime("%Y%m%d-%H%M%S") + ".yaml")
        shutil.copyfile(config_path, backup); backup.chmod(0o600)
        mysql_changed = False
        previous = config["datastore"].get("mysql", {})
        if args.rotate_history_password:
            if not previous.get("enabled") or previous.get("user") != "retui_ergo" or previous.get("host") != "localhost":
                raise RuntimeError("Unexpected history database; rotation cancelled")
            new_password = secrets.token_urlsafe(36)
            changed["datastore"]["mysql"]["password"] = new_password
            sql = "ALTER USER 'retui_ergo'@'localhost' IDENTIFIED BY '" + new_password + "';"
            result = subprocess.run(["mysql"], input=sql, text=True, capture_output=True)
            if result.returncode:
                raise RuntimeError("History credential rotation failed (SQL withheld)")
            mysql_changed = True
        try:
            # Keep comments and ordering on the live config; only replace the policy blocks and one credential.
            text = config_path.read_text()
            import re
            for section in ("accounts", "channels", "oper-classes", "opers"):
                replacement = yaml.safe_dump({section: changed[section]}, sort_keys=False).rstrip()
                text, count = re.subn(r"(?ms)^" + section + r":.*?(?=^[A-Za-z][A-Za-z0-9_-]*:|\Z)", replacement + "\n\n", text)
                if count != 1:
                    raise RuntimeError("Configuration section cannot be replaced safely")
            if mysql_changed:
                old = previous["password"]
                if text.count(old) != 1:
                    raise RuntimeError("History credential location is ambiguous")
                text = text.replace(old, new_password)
            assert yaml.safe_load(text) == changed
            temp = config_path.with_suffix(".retui-new")
            temp.write_text(text); temp.chmod(0o640); shutil.chown(temp, user="root", group="ergo")
            os.replace(temp, config_path)
            subprocess.run(["systemctl", "reload", "ergo"], check=True, capture_output=True)
            # Rehash completion is verified through operator authentication, not assumed from systemctl success.
            time.sleep(1)
            owner.send("OPER dvil " + credentials["operator_password"])
            owner.wait(lambda cmd, p: cmd == "381")
        except Exception:
            if mysql_changed:
                escaped = previous["password"].replace("\\", "\\\\").replace("'", "''")
                subprocess.run(["mysql"], input="ALTER USER 'retui_ergo'@'localhost' IDENTIFIED BY '" + escaped + "';", text=True, capture_output=True, check=True)
            shutil.copyfile(backup, config_path)
            subprocess.run(["systemctl", "reload", "ergo"], capture_output=True, check=True)
            raise
        print("PASS: sole operator dvil; registered-only channel defaults; new-account registration closed for pilot")
        # No takeover of already-registered channels. Stop if another founder owns a requested channel.
        for channel in ("#retui", "#help"):
            reply = owner.notice("INFO " + channel, "ChanServ")
            if "is registered" in reply:
                founder = owner.wait(lambda cmd, p: cmd == "NOTICE" and "Founder:" in p[-1])[-1][1][-1]
                if founder.split("Founder:", 1)[-1].strip() != "dvil":
                    raise RuntimeError("Requested channel already has another founder; no takeover performed")
            owner.send("JOIN " + channel)
            owner.wait(lambda cmd, p: cmd == "366" and channel in p)
            owner.send("SAMODE " + channel + " +ntCR")
            if "is registered" not in reply:
                response = owner.notice("REGISTER " + channel, "ChanServ")
                if "successfully registered" not in response:
                    raise RuntimeError("Channel registration not confirmed")
            owner.send("MODE " + channel)
            modes = owner.wait(lambda cmd, p: cmd == "324" and channel in p)[-1][1][2]
            if "R" not in modes:
                raise RuntimeError("Registered-only channel mode missing")
        guest = Peer(host, "guest" + secrets.token_hex(4))
        try:
            guest.send("JOIN #retui")
            guest.wait(lambda cmd, p: cmd == "477" and "#retui" in p)
            guest.send("JOIN #new" + secrets.token_hex(4))
            guest.wait(lambda cmd, p: cmd in ("403", "482"))
        finally:
            guest.close()
        print("PASS: official channels belong to dvil; guest joins and channel creation rejected")
        if mysql_changed:
            # Validate the replacement directly without exposing it in args or environment.
            from tempfile import NamedTemporaryFile
            with NamedTemporaryFile(mode="w", suffix=".cnf") as defaults:
                defaults.write('[client]\nuser=retui_ergo\npassword="' + new_password + '"\nhost=localhost\n'); defaults.flush()
                result = subprocess.run(["mysql", "--defaults-extra-file=" + defaults.name, "-N", "-e", "SELECT 1"], capture_output=True, text=True)
                if result.returncode or result.stdout.strip() != "1":
                    raise RuntimeError("New history credential verification failed")
            print("PASS: history database credential rotated and verified")
        print("Protected credential file:", credentials_path)
        print("Protected config rollback:", backup)
    finally:
        owner.close()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Tracebacks could include a remote response/SQL/config value: intentionally withhold them.
        raise SystemExit("Provisioning stopped. Inspect private state without printing secrets; do not blindly retry.")
