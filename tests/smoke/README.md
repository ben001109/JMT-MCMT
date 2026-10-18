# Smoke tests

Starts a headless dedicated server for one loader through Gradle
(`:fabric:runServer` / `:neoforge:runServer`), feeds the commands in
`commands.txt` over stdin, waits, stops the server and scans the output for
errors. Lines matching a regex in `allowlist.txt` are ignored.

    bash tests/smoke/run-smoke.sh fabric 120
    bash tests/smoke/run-smoke.sh neoforge 120
    pwsh tests/smoke/run-smoke.ps1 -Loader fabric -TickSeconds 120

Output in `tests/smoke/out/`: `<loader>-report.txt` (verdict and offending
lines), `<loader>-stdout.log` (full server output), `<loader>-filtered.log`
(output minus allowlisted lines). Exit code 0 means PASS.

The run directory is `<loader>/runs/server`; `world/` and `logs/` are wiped
before every run so results are reproducible.
