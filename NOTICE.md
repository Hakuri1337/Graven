# Graven Third-Party Notices

Graven is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE) for the full license text.

This project contains code derived from or adapted from the following upstream
projects.

## Meteor Client

- Repository: [MeteorDevelopment/meteor-client](https://github.com/MeteorDevelopment/meteor-client)
- License: GNU General Public License v3.0
- Copyright: Copyright (c) 2021 Meteor Development.
- Used in Graven: ESP-related functionality.

The original code has been modified and adapted for Graven's module,
rendering, event, and multi-loader architecture.

## Orbit

- Repository: [MeteorDevelopment/orbit](https://github.com/MeteorDevelopment/orbit)
- License: MIT License
- Copyright: Copyright (c) 2021 Meteor Development
- Used in Graven: event bus implementation.

The original code has been modified and adapted for Graven's package
structure and event system.

### MIT License Notice For Orbit

```text
Copyright (c) 2021 Meteor Development

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## LeavesHack

- Repository: [MrBZBZ/LeavesHack](https://github.com/MrBZBZ/LeavesHack)
- License: GNU Affero General Public License v3.0
- Used in Graven: PacketMine module.

The original code has been modified and adapted for Graven's module,
setting, rendering, inventory, and event systems.

## FFmpeg

- Repository: [FFmpeg/FFmpeg](https://github.com/FFmpeg/FFmpeg)
- Embedded binary: FFmpeg 8.1.2 full build for Windows x64
- License: GNU General Public License v3.0 or later
- Used in Graven: Windows-only decoding of the MainMenu `New` video background.

The Windows executable is bundled as `graven/native/windows-x86_64/ffmpeg.exe`.
It is extracted only into the user's `.graven/native/` directory and is never
resolved from the system `PATH`.

LeavesHack is licensed under the GNU Affero General Public License v3.0. The
GNU GPLv3 and GNU AGPLv3 include compatibility terms for combining GPLv3 and
AGPLv3 works; the AGPLv3 network-interaction source requirements apply where
required by that license.

## TrollHack

- Repository: [Luna5ama/TrollHack](https://github.com/Luna5ama/TrollHack)
- License: GNU General Public License v3.0
- Used in Graven: ZealotCrystalPlus module.

The original code has been modified and adapted for Graven's module,
setting, rotation, and event systems.
