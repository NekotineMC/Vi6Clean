# test-bundle

Server folder that `gradlew integrationTest` stages into `build/it-server/` and boots in-process (paper-junit-server). Everything here except this README is gitignored: fill it locally and keep it in line with the production server.

## What goes in

Copy these from the dev or production server:

- `server.properties` (`level-name=world`; RCON and query are turned off at staging, the port is chosen per run), `bukkit.yml`, `spigot.yml`, `commands.yml`, `permissions.yml`, `help.yml`, `eula.txt`
- `config/` (Paper configs)
- `worlds/`
- `plugins/ProtocolLib.jar` and `plugins/ProtocolLib/`
- `plugins/FastAsyncWorldEdit-Paper-<version>.jar` and `plugins/FastAsyncWorldEdit/`
- `plugins/Vi6Clean/` (`config.yml`, `koths/`, `tools/`, maps)

A `plugins/Vi6Clean-*.jar` left here is ignored; the freshly built shadow jar is staged instead. Logs, `cache/`, `libraries/`, `versions/`, root jars and `session.lock` are skipped too.

The Paper version comes from the build's locked dev bundle, not from this folder.

Plugin jar versions must match the ones the build compiles against in `gradle/libs.versions.toml` (for example ProtocolLib `protocollib`).

## What never goes in

- `plugins/update/`: dropping a jar there makes `UpdateFolderRestart` restart the server.
- The server's other plugins (Citizens, SkinsRestorer, voicechat, which binds a fixed UDP port, spark, UnifiedMetrics, ...). Only ProtocolLib and WorldEdit (FAWE) are required by Vi6Clean.

Without ProtocolLib and WorldEdit, Vi6Clean doesn't load and the harness fails the run, naming the target plugin as not loaded. Without this folder at all, `preparePaperTestServer` fails with the bundle path.

A NullPointerException from Vi6's `onDisable` at stop is expected noise (tracked in `_bmad-output/implementation-artifacts/deferred-work.md`).
