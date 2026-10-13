# Termux terminal engine

Unmodified Java sources from Termux 0.118.3, commit
`5b657c6adf4304e5198951ce815fe0205dcac29c`, archive SHA-256
`a79a8ae388caab56d4d6df8f80aa213c21090e3a1f605c28d7f93fb466d6e7ca`.

Upstream: https://github.com/termux/termux-app/tree/5b657c6adf4304e5198951ce815fe0205dcac29c/terminal-emulator

The upstream root license notice is reproduced in
`LICENSES/Termux-Terminal-Emulator.txt`, including its Apache-2.0 exception.
Full GPL-3.0 and Apache-2.0 texts are already packaged under
`LICENSES/Tasker-GPL-3.0.txt` and `LICENSES/Material-Symbols-Apache-2.0.txt`.
Source files retain upstream headers. No terminal-view code or Termux execution
service is used. The existing launcher renderer consumes the emulator's cells.
