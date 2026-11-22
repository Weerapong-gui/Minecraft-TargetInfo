# TargetInfo

Paper plugin (Minecraft 26.3): aim at a mob or another player and its health and armor float above its head,
e.g. **❤ 15/20 +4 ⛨ 8** (+4 = absorption). Only the player aiming sees it.

Each viewer gets a text display spawned hidden from everyone else (`setVisibleByDefault(false)` +
`showEntity`), moved with the target every tick. It does not ride the target, since a passenger
would block teleporting that player. Blocks stop the aim; invisible targets are skipped.

## Config

```yaml
range: 24          # blocks
linger-ticks: 30   # keep showing after you look away (20 = 1 s)
show-players: true # also other players
```

## Build

```sh
./build.sh /path/to/paper-server-dir   # -> build/TargetInfo.jar
```
