---
name: ui-audio
description: Guidelines and patterns for Minecraft UI audio feedback and click sounds in OneManager owo-ui screens.
---

# Minecraft UI Audio Feedback Guidelines

When developing screens and UI components using `owo-ui`:

1. **Standard Button Click Sound**:
   All interactive button-like components, left sidebar main tab navigation buttons, and category switcher tabs (such as «Ваши профессии» / «Каталог профессий» in `CharHistoryScreen`) MUST produce the standard Minecraft options/settings button click sound:
   ```java
   UiSoundHelper.playButtonClick();
   ```
   Under the hood, this executes:
   ```java
   Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   ```

2. **owo-ui Custom Container Caveat**:
   Vanilla Minecraft `Button` and `AbstractWidget` automatically call `playDownSound` on click. However, custom `FlowLayout` or `StackLayout` components used as styled buttons in owo-ui DO NOT play any sound automatically.
   Always invoke `UiSoundHelper.playButtonClick()` inside `btn.mouseDown().subscribe((mouseX, mouseY, button) -> { if (button == 0) { ... } })`.

3. **Where Required**:
   - Primary left sidebar tab buttons (`createTabButton`)
   - Category navigation / toggle buttons (`createProfessionSubTabButton`)
   - Reusable flat and icon buttons (`createFlatButton`, `createIconButton`, `createSmallButton`)
   - Interactive list cards (character dossier cards, profession cards)
   - Dialog confirmation / cancel buttons
