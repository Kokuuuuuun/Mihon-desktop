<div align="center">

<a href="https://mihon.app">
    <img src="./.github/assets/logo.png" alt="Mihon Desktop logo" title="Mihon Desktop logo" width="80"/>
</a>

# Mihon Desktop

### A community desktop fork of [Mihon](https://github.com/mihonapp/mihon)

Mihon Desktop is an independent, community-driven effort to bring the Mihon manga reader
to the desktop — starting with **Linux** and with **Windows** planned for the future. It is
built with JetBrains' [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
and embeds a local [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) instance so
the reader works out of the box without external setup.

> ⚠️ This project is **not** affiliated with, endorsed by, or officially maintained by the
> Mihon Open Source Project. All credit for the reader, UI and source-engine architecture
> goes to the [Mihon contributors](https://github.com/mihonapp/mihon/graphs/contributors).
> This fork is provided "as is" without warranty of any kind.

[![License: Apache-2.0](https://img.shields.io/github/license/Kokuuuuuun/Text-desktop?labelColor=27303D&color=0877d2)](/LICENSE)

## Branch layout

This repository is organised per target platform. `main` is the common entry point and
holds the platform-agnostic documentation; each desktop target lives in its own branch:

| Branch     | Status      | What it contains                                                           |
| ---------- | ----------- | -------------------------------------------------------------------------- |
| `main`     | ✅ active   | Fork overview, shared docs, upstream-sync base for new platform branches.  |
| `linux`    | ✅ active   | Working Linux build (AppImage / portable tar.gz / deb) + Tray fix + CI.     |
| `windows`  | 🚧 planned  | Windows (MSI / portable zip) build, branched off `main` when work begins.   |

To get a runnable build **today**, check out the [`linux`](https://github.com/Kokuuuuuun/Text-desktop/tree/linux)
branch. The `windows` branch does not exist yet — it will be created from `main` (keeping the
original platform-agnostic history) once Windows packaging starts.

## Features shared across platforms

<div align="left">

* Compose Multiplatform UI reusing Mihon's themes, colour schemes and typography.
* Embedded **Suwayomi-Server** managed automatically on launch (no manual server setup).
* Library, browse, updates, history, downloads and reader screens.
* Coil-based image loading of covers and reader pages over the Suwayomi REST/GraphQL bridge.
* System-tray integration gated by `isTraySupported` so it never crashes on headless platforms.

</div>

See the [`linux` branch README](https://github.com/Kokuuuuuun/Text-desktop/blob/linux/README.md)
for the platform-specific download links, build instructions and the live roadmap of
upcoming features.

## Contributing

Pull requests are welcome — please open an issue first to discuss any major change. Platform
work should target the corresponding platform branch (`linux` or, eventually, `windows`); only
shared documentation changes belong on `main`.

## Disclaimer

The developer(s) of this application do not have any affiliation with the content providers
available, and this application hosts zero content.

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Licensed under the Apache License, Version 2.0 (the "License");

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>
