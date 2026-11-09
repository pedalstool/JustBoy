# Changes

## 1.0.21

- The game always runs at its normal speed. The 90 and 120 frame rate choices are gone

## 1.0.20

- 90 keeps the screen on its faster refresh instead of falling back to 60

## 1.0.19

- Each settings page can restore its own defaults

## 1.0.18

- 90 and 120 switch the screen to that refresh rate and draw the frames in between

## 1.0.17

- Picture grade follows NX Redux's pixel-perfect controls: a slightly cool, deeper starting point, with gamma and tint in Video settings
- Core pixels are expanded the same way NX Redux expands RGB565

## 1.0.16

- Resume game is in the quick menu, below Settings and above Restart game
- Picture color is back to 1.0.14

## 1.0.14

- Game Boy Color saves go in `Saves/GBC`, including `.gb` cartridges that are Color games

## 1.0.13

- GBA, GB, and GBC cartridge saves go in `Saves/GBA`, `Saves/GB`, and `Saves/GBC`

## 1.0.12

- JustBoy installs as the Standalone PokeDaisy target, so NeoStation can launch it for GBA, GB, and GBC from one app

## 1.0.11

- Opening the app leaves the Saves folder alone
- Existing saves are copied only when the save folder is changed

## 1.0.10

- Opening a game no longer writes a save file on the way out
- A `.sav` is written only after the game changes cartridge save data
- The session snapshot is written only when Resume last session is on

## 1.0.9

- Put Settings above Restart in the quick menu, and remove Resume. B or the menu button returns to the game
- Remove the built-in shaders and Original GBA colors. The picture stays nearest-neighbor

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
