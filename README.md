<div align="center">

<a href="https://mihon.app">
    <img src="./.github/assets/logo.png" alt="Mihon Desktop logo" title="Mihon Desktop logo" width="80"/>
</a>

# Mihon Desktop [App](#)

### Desktop fork of Mihon

Discover and read manga, webtoons and comics on **Linux** — a desktop port of the
[Mihon](https://github.com/mihonapp/mihon) Android reader, built with JetBrains'
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) and an embedded
[Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) backend.

[![CI](https://img.shields.io/github/actions/workflow/status/Kokuuuuuun/Text-desktop/desktop-build.yml?label=Desktop%20CI&labelColor=27303D)](https://github.com/Kokuuuuuun/Text-desktop/actions/workflows/desktop-build.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/Kokuuuuuun/Text-desktop?labelColor=27303D&color=0877d2)](/LICENSE)

This is an independent community port of Mihon for the desktop. It is **not** affiliated with,
endorsed by, or officially maintained by the Mihon Open Source Project. All credit for the reader,
UI and source-engine architecture goes to the Mihon contributors.

## Download

Build artifacts are produced automatically by GitHub Actions for every push to the
`linux` branch:

* **AppImage** — portable single-file runtime, just `chmod +x` and run.
* **tar.gz** — portable archive containing the bundled app-image + JVM runtime.
* **deb** — Debian / Ubuntu system package.

Grab the latest build from the
[Actions → Desktop Build](https://github.com/Kokuuuuuun/Text-desktop/actions/workflows/desktop-build.yml)
runs and download the artifact you need.

> **Note:** The Linux packages bundle their own Java runtime, so no system JDK is required to run the
> AppImage or the tar.gz bundle. The `.deb` installs under `/opt/mihon-desktop`.

## Features

<div align="left">

* Cross-platform desktop reader targeting **Linux** (AppImage / deb / portable tar.gz).
* Compose Multiplatform UI reusing Mihon's themes, colour schemes and typography.
* Embeds and manages a local **Suwayomi-Server** instance automatically on launch.
* Library, browse, updates, history, downloads and reader screens.
* Coil-based image loading of covers and reader pages over the Suwayomi REST/GraphQL bridge.
* System-tray integration (`Tray` is enabled only on supported platforms via `isTraySupported`).
* Local-first design backed by the Mihon `domain` / `data` layer.

</div>

## Roadmap — coming soon

The following desktop-specific work is planned and not yet implemented:

* **Full local-source parity** — first-class reading of local files / archives without a server.
* **Offline database** — a native desktop backing store (replacing the in-memory Suwayomi bridge).
* **Updater** — in-app self-update checking GitHub releases for new desktop builds.
* **Windows / macOS builds** — currently only Linux packaging is wired in the CI workflow.
* **Cloud backups & trackers** — restore MAL / AniList / Kitsu / Shikimori sync.
* **Keyboard / gamepad reader controls** — full keybinding customisation remap.
* **Window multi-instance** — opening multiple manga / reader windows side by side.
* **Native notifications** for new chapters and download completion.

## Building from source

Requirements: **JDK 17** and an internet connection.

```bash
git clone https://github.com/Kokuuuuuun/Text-desktop
cd Text-desktop
./gradlew :desktop:packageDistributionForCurrentOS
```

Artifacts are written to `desktop/build/compose/binaries/main/`:

| Format   | Path                                          |
| -------- | --------------------------------------------- |
| AppImage | `app/mihon-desktop-1.0.0/mihon-desktop-1.0.0.AppImage` |
| Deb      | `deb/mihon-desktop-1.0.0-1_amd64.deb`         |
| tar.gz   | `app/mihon-desktop-1.0.0.tar.gz` *(built by CI)* |

## Contributing

Pull requests are welcome — please open an issue first to discuss any major change.

Before reporting a new issue, search the existing
[issues](https://github.com/Kokuuuuuun/Text-desktop/issues) and review the upstream
[Mihon FAQ](https://mihon.app/docs/faq/general) when in doubt about reader behaviour inherited
from the Android app.

### Credits

This would not exist without the Mihon Open Source Project and its contributors:

<a href="https://github.com/mihonapp/mihon/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=mihonapp/mihon" alt="Mihon app contributors" title="Mihon app contributors" width="800"/>
</a>

Suwayomi-Server powers the backend bridge:

* [Suwayomi/Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server)

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers
available, and this application hosts zero content. Mihon Desktop is a community fork provided
"as is" without warranty of any kind.

### License

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
