# Hide And Seek (Forge 1.20.1)

A server-first **Hide & Seek** multiplayer minigame for Minecraft Java 1.20.1 (Forge 47.x, Java 17).
Mod ID: `hideandseek`.

Everything is controlled with `/hideandseek` (alias `/hns`). The mod is **server-side only**: it adds no
blocks, items or registries and no client code, so players should not need to install it
(`displayTest="IGNORE_SERVER_VERSION"`).

> **Status:** the source was written without access to a Gradle/Forge toolchain, so it has **not been compiled
> or tested yet**. See [Build](#build) and [Test status](#test-status).

## Build

Requirements: JDK 17 and Gradle 8.1.1 (or newer 8.x compatible with ForgeGradle 6).

```bash
gradle wrapper --gradle-version 8.1.1   # one time, generates gradlew
./gradlew build
```

The server jar is written to `build/libs/hideandseek-1.0.0.jar`.
A GitHub Actions workflow (`.github/workflows/build.yml`) also builds the jar on every push and uploads it as an artifact.

## Install

1. Install a Forge 1.20.1 (47.x) server.
2. Drop `hideandseek-1.0.0.jar` into the server's `mods/` folder.
3. Start the server once. `config/hideandseek-common.toml` is generated.

## Commands

| Command | Who | Description |
|---|---|---|
| `/hideandseek start <seeker> [minutes]` | op | Start a match (default 5 min, 15 s hiding countdown) |
| `/hideandseek start` | op | Start using the seeker from `setseeker` |
| `/hideandseek stop` | op | Stop the current match |
| `/hideandseek reset` | op | Stop and clear all state, including the preselected seeker |
| `/hideandseek setseeker <player>` | op | Preselect the seeker |
| `/hideandseek pause` / `resume` | op | Pause / resume the timer (catching is disabled while paused) |
| `/hideandseek catch <player>` | op | Eliminate a hider manually |
| `/hideandseek uncatch <player>` | op | Return a caught hider to the game |
| `/hideandseek config` | op | Show active configuration |
| `/hideandseek reload` | op | Re-read `hideandseek-common.toml` |
| `/hideandseek status` | everyone | Match information |
| `/hideandseek list` | everyone | Seeker, hiders, caught players |
| `/hideandseek join` / `leave` | everyone | Join (if `allowJoinDuringMatch`) / leave (if `allowLeaveDuringMatch`) |
| `/hideandseek help` | everyone | Command list |

Admin commands need permission level `commandPermissionLevel` (default 2).

Example: `/hideandseek start Player123 5`

## How a match works

1. Only **Survival/Adventure** players take part. **Creative and Spectator players are always excluded**, and
   anyone who switches into those modes mid-match is silently removed from the game.
2. 15 s hiding countdown: big centered numbers, sound each second. The seeker is frozen (Slowness, no jumping,
   blindness, position lock) and cannot interact, break/place blocks, attack or drop items. Hiders cannot hurt the seeker.
3. `SEEKER RELEASED!`, the boss bar timer starts. Warnings at 3/2/1 min, 30 s, 10 s and a 5 s final countdown (configurable).
4. Catching: `ATTACK` (default, hit a hider), `TAG` (come within `catchDistance` blocks) or `MANUAL` (`/hideandseek catch` only).
   Caught hiders are announced once, then optionally become spectators.
5. Seeker wins when every hider is caught; hiders win when time runs out. Previous game modes are restored.

If the seeker disconnects or leaves Survival/Adventure, the match is cancelled. If every hider leaves before anyone
was caught, the match is cancelled.

## Configuration

`config/hideandseek-common.toml` covers durations, countdown, boss bar, sounds (any sound id, including resource-pack
sounds), catch method/distance, allowed game modes, minimum players, spectator behaviour for eliminated hiders,
join/leave rules, game mode restore, permission level and every title/chat message (`&` colour codes and
`%placeholders%` supported). Edit the file and run `/hideandseek reload` (Forge also reloads it automatically on save).

## Test status

Nothing in this repository has been run yet. Before relying on it, check on a dedicated server:
command registration, startup, countdown, 5-minute timer, Creative/Spectator exclusion, disconnects, catching and win
conditions, and stop/reset/pause/resume.

## Known limitations

* A hider who dies naturally is not eliminated automatically (use `/hideandseek catch`).
* A player who disconnects mid-match is removed from it and must use `/hideandseek join` (if allowed) to return.
* Seeker blindness/slowness effects are applied as hidden infinite effects and removed when the seeker is released.
