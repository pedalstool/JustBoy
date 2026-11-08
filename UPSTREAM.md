# Source provenance

- JustBoy is a standalone Android frontend on libmGBA. It plays GBA, GB, and GBC. It is not a fork of mGBA’s Qt or Android UI.
- The Android UI started from [carterprince/JustGBA](https://github.com/carterprince/JustGBA) (GPL-2.0). The preserved original README is [docs/UPSTREAM-README.md](docs/UPSTREAM-README.md).
- libmGBA: https://github.com/mgba-emu/mgba, tag 0.10.5, revision 26b7884bc25a5933960f3cdcd98bac1ae14d42e2, vendored in `vendor/mgba` (MPL-2.0).
- GBA color source: https://github.com/libretro/glsl-shaders/blob/master/handheld/shaders/color/gba-color.glsl. The original public-domain file is retained; the display stage adapts its default color transform.

The project is GPL-2.0 for the Android application, with libmGBA under MPL-2.0. Component notices stay in the source. No ROMs, proprietary BIOS, signing keys, tokens, SDK distributions, or build caches are part of this repository.
