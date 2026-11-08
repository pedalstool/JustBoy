# Changes

## 1.0.8

- Screen size is Fit, Integer, and 2x through 5x. 6x was the same as Integer, and 7x could not fit

## 1.0.7

- The menu button opens a quick menu for the save slot, save, load, resume, restart, quit, and settings
- Saving or loading a state closes the menu and returns to the game

## 1.0.6

- 90 and 120 draw the frames in between, while the game keeps its normal speed
- Remove the pixel grid control

## 1.0.5

- Add a Video frame rate choice: Native, 90, or 120
- Native stays the normal GBA speed. 90 and 120 run the game faster

## 1.0.4

- Draw the game in full color and keep each pixel square
- Add brightness, contrast, saturation, temperature, sharpness, and pixel grid
- Put every settings page on a tab along the top
- Use an SD card Saves folder that already contains save files

## 1.0.3

- Start at the game menu instead of restoring the last session
- Add a Saves setting, Resume last session, for the old behavior

## 1.0.2

- Load GBA battery saves such as Golden Sun, whose flash type is not known until the game starts
- Prefer a replaced `.sav` over an older autosave state

## 1.0.1

- Store battery saves, save states, and cheats in `Saves` at the root of the SD card
- Let the save folder be changed from Settings, and copy existing saves into the new folder

## 1.0.0

- First JustBoy release
- Play Game Boy, Game Boy Color, and Game Boy Advance on libmGBA
- Physical-controller remaps, hotkeys, three save slots, cheats, shaders, and Fit/Integer/2x–7x screen size
