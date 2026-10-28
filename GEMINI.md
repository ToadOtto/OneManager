# OneManager Project Rules & Guidelines

## 1. Build & Environment
- **Java Home**: Always use JDK 21 located at:
  `C:\Users\bobik\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2`
- **Build Command**:
  `cmd.exe /c "set JAVA_HOME=C:\Users\bobik\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2&& gradlew.bat test jar --daemon"`
- **External Directory**: Never modify files in `oneshop/`.
- **Jar Distribution**: After building `build/libs/OneManager-1.0.0.jar`, copy it to `build/libs/OneManager.jar`.
- **VCS**: Always commit working changes and push to `origin main`.

## 2. UI Development Guidelines (owo-ui)
- **Universal Scaling (Standard = GUI Scale 3)**:
  - All hub UI scaling must be based on GUI scale 3 as standard (`BASE_GUI_SCALE = 3.0`, `BASE_UI_SCALE = 0.70f`).
  - Calculate dynamic scale using:
    `float scale = (float) (0.70f * (3.0 / Minecraft.getInstance().getWindow().getGuiScale()));`
  - Ensure adapter dimensions and mouse interactions divide coordinates by this dynamic scale so UI is independent of player's GUI scale setting.
- **Text Wrapping**:
  - Always enforce `maxWidth(...)` on text labels inside cards, modal windows, and table rows to prevent text overflowing container bounds.
- **Tooltip & Scissor Safety**:
  - Never call `tooltip((Component) null)`—use explicit `shouldDrawTooltip()` checks or remove listeners to prevent scissor stack corruptions.
  - When rendering tooltips over modal dialogs or graph overlays, disable depth test and elevate Z-index (`Z >= 800`) to prevent modals from drawing over them.
