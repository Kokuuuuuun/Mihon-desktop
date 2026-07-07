Mihon Desktop —bundled Suwayomi-Server resources
=================================================

Place a `Suwayomi-Server*.jar` in this directory to embed it in the packaged
app-image. At build time, `copySuwayomiJar` (see desktop/build.gradle.kts) copies
the jar into the app's runtime resources dir, so SuwayomiServerManager.locateServerJar()
finds it when the app-image runs (no MIHON_SUWAYOMI_JAR env var needed).

Optionally, a full `suwayomi-runtime/` JRE can be dropped here to run the server
with a pinned JRE distinct from the app's own runtime.

This directory is empty by default; the jar is intentionally NOT committed.
