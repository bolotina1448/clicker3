# PurpleClicker (Forge 1.8.9)

Client-side autoclicker with a purple, customizable menu.

- **Right Shift**: open the menu (drag it by the header)
- **R**: turn the clicker on/off
- Both keys can be changed in Options > Controls > PurpleClicker.

The clicker only runs while you hold the mouse button down:
- Left: random CPS between Min and Max (1-50)
- Right: fixed CPS (1-50), off by default
- "Break blocks" stops clicking while you mine, so blocks break normally
- Accent hue/saturation sliders recolor the menu and HUD; "Reset to purple" restores the default

Settings are saved to `config/purpleclicker.cfg`.

## Getting the .jar

Same as PlayerESP: make a new GitHub repo, upload everything in this folder
(including `.github/workflows/build.yml`), wait for the green check in **Actions**,
then download **PurpleClicker-jar** from the run's Artifacts.
