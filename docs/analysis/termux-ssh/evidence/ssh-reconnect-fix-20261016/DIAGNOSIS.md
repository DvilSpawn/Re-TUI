# Confirmed SSH reconnect failure cause

Physical device I2220 / Android 16, 16 October 2026. These are diagnosis runs before the final fix, not passing stability evidence.

The first trace attempt could not replace the original listener because the dedicated `sshd.pid` contained PID 30154 while the actual verified listener was PID 19322. The diagnostic process failed to bind port 8928. This explains why the earlier configuration experiments did not establish that the running daemon loaded their changes.

The second fixture revision located the listener by executable and exact dedicated config path, terminated only that listener, and started an actual DEBUG3 listener (PID 16310). It preserved SSH session children, tmux jobs, personal SSH configuration, and byte-for-byte dedicated state restoration excluding the transient PID file.

The real daemon log explicitly identifies the cause:

- `diagnostic-second-native-after.log`, lines 327–937: multiple unauthenticated keyscan connections accumulate an inactive source penalty; the additional readiness connection activates a 15.180 second penalty for `connections without attempting authentication`.
- The same log, lines 1561 onward: subsequent legitimate connections are dropped for that exact penalty.
- The corresponding test failed after 30 font cycles and nine completed foreground reconnects, in 68.664 seconds. Selection, Unicode draft and both remote processes survived up to the blocked reconnect.

The broad default `ssh-keyscan` attempts several key types on every resume, followed by the client's separate open-and-close TCP readiness probe. These local unauthenticated probes trigger OpenSSH's source penalty. The earlier original listener already had accumulated penalty state, explaining its earlier fifth-reconnect failure. The dedicated service configuration's public-key authentication and strict host pinning were not the failure.

A separate live-process inspection found existing orphan tmux control clients. The revised appearance fixture asserts one added control client while connected and exact restoration of the pre-existing client PID set after each pause. Final validation must run that fixture against the normal shipping daemon (`sshDaemonTrace` omitted), not only the diagnostic listener.

## Final fix validation

The default-daemon phone run subsequently passed 73 font cycles and 24 reconnects over 120,203 ms, including exact control-client cleanup on every pause. See [final validation](README.md). Default OpenSSH penalties and strict host authentication remain enabled.
