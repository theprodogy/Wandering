# Wandering Roadmap

Wandering is a Paper 1.21.11 fork for PvP/FFA networks and SMPs. It moves main-thread work onto other cores
without changing vanilla gameplay and without breaking Bukkit/Paper plugins.

Rules for every item:
- Gameplay must stay identical to vanilla/Paper. No DAB, reduced tick rates, spawn throttling or lag compensation.
- No Folia-style regionized ticking. Plugins must keep seeing a single main thread for their events and API calls.
- Every optimization has a toggle in `config/wandering.yml`. It defaults on only once vanilla parity is verified.
- Ported code keeps its license (Paper is GPLv3; check each source patch before porting) and credits the
  original authors in the commit message.

## How changes are stored

- Paper-side code (`paper-server/src/main/java`, `paper-api`): edit directly, mark lines with `// Wandering`.
- New Wandering code goes in its own package (`dev.wandering...`).
- Minecraft source (`paper-server/src/minecraft/java`, created by `applyPatches`): commit there, then
  `./gradlew rebuildPatches` turns it into per-file patches in `paper-server/patches/sources`. Larger multi-file
  changes become feature patches in `paper-server/patches/features`.
- Upstream fixes: `git fetch upstream ver/1.21.11 && git merge upstream/ver/1.21.11`. 1.21.11 is no longer
  Paper's active branch, so conflicts should be rare.

## Already off the main thread in Paper 1.21.11 (don't redo)

- Chunk loading, generation, saving and IO: Moonrise chunk system, worker pool.
- Lighting: Starlight engine inside Moonrise, runs on chunk workers.
- Chat: `AsyncChatEvent`, async tab-complete.
- Network compression and encryption: Netty IO threads (Velocity natives).
- Region file IO and some data saving: Moonrise IO threads.

## Ranked items

| # | Item | Main-thread cost removed | Approach | Gameplay risk | Plugin risk | Reference |
|---|------|--------------------------|----------|---------------|-------------|-----------|
| 1 | Async entity tracker | Big on busy PvP/SMP: per-player visibility checks and movement/metadata packets for every tracked entity | Compute tracker updates in parallel per entity on a worker pool, send packets off-thread; Bukkit visibility events stay on main | Low if packet order per entity is kept | Medium: plugins that hide entities or send fake entities (holograms, NPCs, TAB nametags) | Leaf `MultithreadedTracker` (from Petal) |
| 2 | Async packet sending / flush batching | Netty write/flush syscalls per packet from main | Queue on the connection's event loop, flush once per tick | None if order is kept | Low: ProtocolLib/PacketEvents still see packets | Leaf / Gale connection flush patches |
| 3 | Async pathfinding | `PathFinder.findPath` for mobs; heavy on SMP farms | Run the A* search on a worker against a block snapshot, apply the result next tick | Low-medium: path ready 1 tick later; must fall back to sync when the snapshot is stale | Low | Petal / Leaf async pathfinding |
| 4 | Async mob spawn calculation | `NaturalSpawner` mob-cap counting each tick | Count spawn-category caps off-thread, spawn on main | Low: spawning itself stays on main with identical rules | Low | Pufferfish async mob spawning |
| 5 | Parallel world ticking | Whole world tick: overworld, nether and end each on their own core | One tick thread per world, cross-world operations (portals, teleports) synced at tick boundaries | Medium: cross-dimension edge cases | High: plugins touching another world mid-tick; needs thread checks plus a sync fallback. Default off | SparklyPaper parallel world ticking |
| 6 | Async entity collision / lookup prep | `getEntities` for collision, pushing, hoppers | Parallel broad-phase on a worker, narrow-phase results applied on main | Medium: order of collision resolution must match | Low | Research item; no proven implementation yet |
| 7 | Async playerdata / stats / advancements saving | Disk writes on autosave and quit | Serialize on main, write the file on an IO worker | None | Low: plugins reading the file right after quit | Leaf / Gale async playerdata saving |
| 8 | Async structure locate (`/locate`, eyes of ender, treasure maps) | Long main-thread freezes on SMP | Search on a worker, complete the item/command when done | None (result identical, delivered later) | Low | Petal / Leaf async locator |

Order of work: 2, 7, 1, 4, 3, 8, 5, 6. Safe, clear wins first; 5 and 6 only after the rest are profiled.

## Verifying each item

- `./gradlew applyPatches createMojmapPaperclipJar` passes, and `./gradlew test` passes.
- Toggle on and off: the server boots and behaves identically in both states.
- spark profile before and after under a load test (bots plus mobs), showing main-thread time moved.
- Vanilla parity check for the touched system: farms, mob AI, knockback/combat with KnockbackSync and
  CelestCombat, redstone where relevant.
- Plugin smoke test: LuckPerms, TAB, WorldGuard, EssentialsX, FAWE, Skript, CelestCombat, KnockbackSync,
  FancyHolograms, AJLeaderboards.
