# 1.2.2-usage-test2

Continues usage-test1, based on stable 1.2.1. This remains a test build; main and the stable release are unchanged.

- Each SIM status card now uses the official Material 3 exposed dropdown for Default / Voice-centric / Data-centric. The separate Choose button, selection dialog and normal-state explanatory paragraphs are removed. Pending operations and failures retain short status messages.
- The status card groups SIM/carrier, configuration and IMS badges, data RAT, reported registered bands, RSRP, SINR and framework-reported bandwidth. Light/dark and dynamic colors follow the system.
- A lean read-only reader runs in the authorized keeper session. It reads framework-cached registered cells and signal strength, without active cell scans, cell identifiers, logging history, uploads, band changes, IMS resets or preference writes. Cell bands are read from CellIdentity, not guessed from channel numbers. LTE records are not presented as SA bands.
- Visible Status pages renew a 15-second lease for approximately five-second snapshots. The keeper returns to its normal background checking cadence when the page is hidden. UI process death does not terminate the keeper. Missing measurements show a dash, and the available measurement timestamp distinguishes older cached observations. Framework bandwidth lists are reported observations, not proof of simultaneously active carriers or throughput.
- Existing preference journaling, original-value recovery and bounded full-config guardian writes are retained. Developer-options detection and write gating remain absent. There is no permanent notification drawer entry added by this change.

Apply and start the guardian again after cover-installation to load the updated keeper and radio reader. No VoNR performance improvement is claimed by this UI revision. Preference semantics and recovery limitations remain documented in [TEST_USAGE_CHOICES.md](TEST_USAGE_CHOICES.md).

Verification includes three-choice dispatch, bandwidth/unknown-value/SA-NSA formatting, Chinese light/dark layout rendering, existing guardian and preference recovery tests, release Lint, APK entry-point checks, alignment and signature checks. Framework APIs are exercised by unit/UI tests; the new radio observations still need verification on the target handset.
