# Potato AI V21 — Integration Alpha

This project contains the original supplied Kotlin source, packaged as an Android library (`:potatoai`) and a small installable demo host (`:demo`).

## Included
- Movable floating assistant overlay and chat panel.
- OpenAI, Claude, Gemini and OpenAI-compatible provider selection.
- User-configurable endpoint, model and API credentials.
- User-authorized single-frame screen capture through Android MediaProjection.
- Basic HTTP JSON-RPC MCP tools/list and tools/call client.
- Host integration helper.

## Build
Requires JDK 17 and Android SDK Platform 35. From this directory:
```bash
gradle :potatoai:assembleRelease :demo:assembleDebug
```
Outputs:
- `potatoai/build/outputs/aar/potatoai-release.aar`
- `demo/build/outputs/apk/debug/demo-debug.apk`

GitHub Actions builds both outputs on every push to main and uploads them as a workflow artifact.

## Important V20 limitation
The V20 APK supplied separately is already compiled and signed. Its original Android/Gradle source project and signing key are not included. This repository therefore builds a standalone Potato AI V21 demo APK and an AAR; it does **not** inject or merge V21 into the existing V20 APK. Rebuilding the actual V20 host requires its original project and authorized signing setup.

The app only analyzes a single screen frame after the user explicitly accepts Android's capture prompt. It does not automate gameplay, control Roblox, or bypass game protections.

API keys are stored in app-private preferences and are not protected by a dedicated secrets vault. Use a personal key only on a device you control; API usage may incur charges. This is an alpha build and requires device testing.