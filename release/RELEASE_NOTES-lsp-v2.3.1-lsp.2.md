# HyperLux v2.3.1-lsp.2

This is the first rebuilt APK for the `lsp-v2.3.1` branch. It contains the raw-panel recovery and node-validation fixes described in the `v2.3.1-lsp.1` source release. The app version remains 2.3.1; version code is 23107 and build identity is `release-2.3.1-lsp.2`.

The build machine did not have the original signing keystore. This APK uses a new signing key, so Android cannot install it as an update over an existing copy of `top.rongshangs.lumacurve`. Uninstall the existing app first; its local settings and data may be removed. The private signing key is not included.

Host tests, Java and ARM64 native compilation, APK signature/alignment, and packaged-asset checks are run for this build. Proprietary firmware fixtures and Android hardware were unavailable; this release does not claim device verification.
