# Security policy

## Supported scope

Security maintenance is best-effort for the latest source on `main` and the latest
public preview. Reproduce against the latest version where safe; older previews
may require an update rather than a backported patch. Preview builds are not
store-signed production releases. There is no promised response deadline or bug bounty.

## Report a vulnerability privately

Use [Report a vulnerability](https://github.com/AbdulrahmanAhmedGit/Recall/security/advisories/new)
in this repository's Security → Advisories area. GitHub private vulnerability
reporting is enabled for the project. Do **not** open a public issue or PR with
exploit details before coordinated disclosure.

Include the affected version/commit, Android version, minimal reproduction steps,
expected impact and a proposed fix if available. Use synthetic study data. Redact
personal information, credentials and unrelated device logs; do not attach a full
personal backup. Share a working proof of concept only in the private report.

Relevant areas include malicious JSON/ZIP imports, archive path traversal,
attachment/provider access, unintended disclosure of local study data and unsafe
Android component permissions. Normal feature requests, UI bugs, scheduling
questions and manufacturer battery restrictions belong in Issues or Discussions
unless they demonstrate a security impact.

The maintainer will assess reports when available, discuss reproduction/fixes
privately and coordinate a disclosure or release if warranted. Do not test against
other people's data/devices or publish identifying information.

## Data and build precautions

Full backup ZIPs are **unencrypted** and include material contents: store and share
them privately. External AI providers are optional and have their own policies.
Never commit signing keys, tokens, personal backups or machine-local configuration.
Check release checksums and signing notes before installing an APK; do not uninstall
an existing installation after a signature mismatch without preserving its data.
