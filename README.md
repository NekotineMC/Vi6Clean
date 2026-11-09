# Vi6Clean

PaperMC plugin for "Voleur Industriel 6": thieves infiltrate a factory to steal artefacts while defenders stop them.

## Clone

The build compiles two git submodules, one nested in the other: [NekotineCore](https://github.com/NekotineMC/NekotineCore) at `NekotineCore/`, and the in-process Paper test harness [paper-junit-server](https://github.com/XxGoldenbluexX/paper-junit-server) at `NekotineCore/paper-junit-server/`. Clone recursively:

```sh
git clone --recursive https://github.com/NekotineMC/Vi6Clean.git
```

or, in an existing clone:

```sh
git submodule update --init --recursive
```

### Existing clones

paper-junit-server used to be a top-level submodule. After pulling this layout, run:

```sh
git submodule sync --recursive
git submodule update --init --recursive
```

then delete the leftover top-level `paper-junit-server/` folder (Git leaves it behind; the build uses `NekotineCore/paper-junit-server/` only).

## Build

Requires JDK 25. Run `gradlew` (`gradlew.bat` on Windows) to build the plugin jar; see `AGENTS.md` for the build, test and deployment details.
