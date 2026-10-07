# HyperLux lsp-v2.3.1

This source release is based on upstream HyperLux 2.3.1 (`bf839360ba1c6d2b47ae2a7fe3f3c500e7ade1c8`) and contains the following fixes:

- Restore automatic brightness when a raw-panel session that started from automatic mode is stopped, disabled, closed, or fails while pausing its lease.
- Restore the same automatic-mode ownership after a `system_server` restart or disconnected stop, without restoring an old raw node target.
- Validate the selected primary panel node before stopping an existing native guard, then validate it again after asset installation.
- Add host regression coverage for raw-panel recovery and the node-transaction ordering contract.

The attached asset is a source archive and has not been rebuilt into an APK. The upstream 2.3.1 APK files are not claimed to contain these branch fixes. Host syntax and static checks pass; Java/native execution and real-device HyperOS 4 validation require the project toolchain and hardware.

See `SHA256SUMS.txt` for the source archive checksum.
