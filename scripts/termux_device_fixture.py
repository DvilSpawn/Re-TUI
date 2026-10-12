"""Serve the shipped helper with a private real tmux server to an emulator only.

Loopback, fixed fixture token, bounded eight clients/10-minute lifetime. No phone
Termux install, global tmux server or real launcher data is modified.
"""
import signal
import socketserver
import subprocess
import threading
from test_termux_helper import HELPER, HelperTransportTest


def main():
    fixture = HelperTransportTest()
    fixture.setUp()
    # The Android client validates the production protocol's fixed session name.
    # Isolation comes from the private tmux server, not a different session name.
    fixture.env["RETUI_SESSION"] = "retui_workspace"
    stopped = threading.Event()
    slots = threading.BoundedSemaphore(8)
    children = set()
    lock = threading.Lock()

    class Handler(socketserver.BaseRequestHandler):
        def handle(self):
            if not slots.acquire(blocking=False):
                return
            child = None
            try:
                child = subprocess.Popen(["sh", str(HELPER), "bridge", "socket-client"],
                    env=fixture.env, stdin=self.request, stdout=self.request, stderr=subprocess.DEVNULL)
                with lock:
                    children.add(child)
                child.wait(timeout=600)
            except (OSError, subprocess.TimeoutExpired):
                pass
            finally:
                if child is not None:
                    if child.poll() is None:
                        child.terminate()
                        child.wait(timeout=5)
                    with lock:
                        children.discard(child)
                slots.release()

    class Server(socketserver.ThreadingTCPServer):
        daemon_threads = True
        allow_reuse_address = False

    try:
        assert fixture.helper("capture").returncode == 0
        with Server(("127.0.0.1", 0), Handler) as server:
            assert server.server_address[0] == "127.0.0.1"
            threading.Thread(target=server.serve_forever, daemon=True).start()
            for signum in (signal.SIGTERM, signal.SIGINT):
                signal.signal(signum, lambda *_: stopped.set())
            print("PORT=" + str(server.server_address[1]), flush=True)
            stopped.wait(600)
            server.shutdown()
    finally:
        with lock:
            for child in children:
                if child.poll() is None:
                    child.terminate()
        fixture.tearDown()


if __name__ == "__main__":
    main()
