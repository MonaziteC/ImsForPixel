# 1.2.2-data-test5: remove developer-options detection entirely

Developer/USB/wireless-debugging detection is deleted, including its two source classes, ContentObservers, shell commands, worker, runtime reference, private refresh broadcast, UI polling requests, flag display and exported fields. Deleted-feature tests and translations are removed. Initial wireless ADB pairing/authorization remains; it is not a detector for later background operation.

The guardian still checks app-owned custom configuration, compares before writing, verifies by an independent read, pauses during any SIM call, and retains original SIM identity, permission checks and rolling retry limits. As in test4, no developer-options reading participates in writes or verification. There is no substitute heuristic or new state detector.

Release validation rejects the deleted settings keys, query-source marker and debug-field names in DEX. The ordinary status UI reports guardian/configuration state. Exported radio history and subscription-usage experiment behavior are unchanged. The original preference journal and undo recovery are retained.

Existing unit/UI tests, release Lint, entry-name, APK signature/alignment and source-revision checks are required. Target-device configuration recovery and indoor throughput are not established by host tests.

The previous test documents are historical. This file describes the current removal; test4's independence of configuration recovery remains in force.
