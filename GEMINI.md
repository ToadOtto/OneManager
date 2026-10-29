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
- **Button Click Audio Feedback (Standard Minecraft Button Click)**:
  - All primary navigation tab buttons in the left list, category switching buttons (such as in the professions tab), sub-tabs, interactive cards, and action buttons MUST play the standard Minecraft button click sound from settings:
    `UiSoundHelper.playButtonClick();` (invoking `Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));`).
  - Since custom owo-ui container components (`FlowLayout`, `StackLayout`) used as buttons do not play sound automatically, always ensure this sound is explicitly called in `mouseDown()` / click handlers when `button == 0`.

## 3. Project Knowledge Base
- **Full Architecture & Features**: Always refer to `PROJECT_CONTEXT.md` for complete documentation on all existing systems (Treasury, Chests, Graph, Characters, Laws, Notes, GUI tabs, Networking, Data storage). Update `PROJECT_CONTEXT.md` upon user request when new features are added.
