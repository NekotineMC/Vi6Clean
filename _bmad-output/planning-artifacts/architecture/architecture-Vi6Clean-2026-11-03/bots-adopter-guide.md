# Bots in your Nekotine minigame: the short version

This page is for anyone writing a Nekotine minigame who wants fake players: to fill a lobby, or to run a game with no humans as a test. It describes the design. The code doesn't exist yet. The binding rules are in `ARCHITECTURE-SPINE.md` (cited as AD-n).

## What a bot is

A bot is a **real player with no client behind it**. It shows up in `getOnlinePlayers()`, fires `PlayerJoinEvent` and `PlayerQuitEvent`, and everything it does reaches the server as the same packets a real client sends. Your game code therefore treats it exactly like a human, with no `if (isBot)` anywhere.

## What you get, and where

| Need | Module | You use |
| --- | --- | --- |
| Spawn, remove and drive a bot | NekotineCore `bots` | bot roster, low-level controls, per-bot tick event |
| Find a path | NekotineCore `core` | pathfinder interface; returns Paper's `PathResult` |
| Follow a path | NekotineCore `core` | a walker strategy (vanilla-like for now) |
| Fight at melee range | NekotineCore `core` | a melee controller strategy |
| Decide what a bot does in *your* game | your plugin | your "brain": a listener on the per-bot tick event |

`core` is Paper API only. `bots` uses Minecraft internals and is therefore tied to one Paper version. If you never use bots, you never depend on `bots`.

## Setting it up

1. Have NekotineCore as a submodule and `includeBuild` it, as Vi6Clean does. The test harness (paper-junit-server) comes with it.
2. Depend on `core`, and on `bots` if you want bots. Your plugin, and only your plugin, shades them (AD-2). Your plugin must be a Paper plugin (`paper-plugin.yml`).
3. Write your own commands (add, list, remove…). The library ships none (AD-14).

If a Paper update breaks the bot internals, the bot module says so loudly at boot and turns itself off. Your game keeps running for humans (AD-3).

## Driving a bot

Each tick, the library fires an event per bot, on the main thread. Anything you set inside that event applies **this tick**, and all bot work stays on the main thread (AD-5, AD-9).

The controls are exactly what a client could send (AD-4):

- **Movement:** a movement heading plus forward/back, strafe, jump, sneak, sprint. Keys stay pressed until you change them.
- **Aim:** look direction. It is independent of the movement heading, so a bot can run one way and look another.
- **Hands:** hotbar slot, use item, use on a block, left-click, attack, interact, swap hands. These fire once.
- **Other:** send a command (exactly as if typed), respawn.

There is no teleport or "set position": a bot only moves by walking.

Walking and fighting are strategies you plug in (AD-8):

```text
path = pathfinder.find(from, to, yourPassabilityRule)  // your game decides what is walkable
walker.follow(bot, path)                                // owns the movement controls
melee.engage(bot, target)                               // owns aim and hands
```

Each control group (movement, aim, hands) has one owner at a time, so the walker and the melee controller run together without fighting. The pathfinder never knows your game rules: you pass them in (AD-7).

## Rules your brain must follow

- **Play like a human (AD-12).** Read your game through its public API and act only through controls and player commands. Never call your own game's "set team" or "next phase" for a bot, or a bot-driven test proves nothing.
- **Guard your own listener.** If your brain throws, catch it, log it with your game's context, and stop that brain. The library logs only its own failures: once, with the bot's name, and then it disables that bot (AD-11).
- **Keep tests apart (AD-13).** Bot library tests live in NekotineCore. Your game's tests test your game and use bots only as a driver.

## Identity and data

A bot's UUID is derived from its name, as in offline mode, so a bot called `Bot_Alice` is always the same player. On removal, the library deletes its saved state and its statistics by default. Turn either flag off at spawn if you want, for example, per-bot stats over time (AD-10). Bots never survive a restart or `/reload`.

## Not included

Skins, chat, reading dialogs, difficulty levels, anti-cheat friendliness, and bots that pass as human. Those are deliberate non-goals, not missing features.
