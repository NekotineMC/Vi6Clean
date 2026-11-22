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

## Unattended bot runs

`/bot run <4|6> [seed]` (OP; works from the console and over RCON) starts a game played by bots, from the lobby:

- It removes every bot, then adds `min(count, 6 - humans online)` bots. It refuses outside the lobby, and when 6 humans are online.
- Humans online stay in the game and must ready up in every game of the run.
- Games loop until the next `/bot run` or a reset.
- The server log gets `Run started: requested=..., added=..., seed=...`, then one line per game: `Game ended: game=N, reason=lost-timer|all-thieves-left, stolen=..., escaped=..., duration=Ns, seed=...`.

### What the seed replays

The seed (random when omitted) only replays which entrance each thief bot picks in preparation. Teams do not depend on it: a bot joins the smaller team, guards on a tie, in roster order. Everything else stays random from one run to the next, even with the same seed: KoTH placement and effects, the weather, each player's free rune, the entrance given to a thief who picked none, and the random parts of some tools (lantern, shadow, smoke pool).

The seed is meant to drive more of a run later, KoTH map generation for one; update this section when it does.
