# BlockProt (VanilyaMC fork)

Block locks for a Towny server on Canvas / Folia 26.3. Players lock chests, barrels, furnaces, hoppers,
shulker boxes, doors, spawners and more; others cannot open or break locked blocks. The locks follow the land.

Fork of [LimeNetwork/BlockProt](https://github.com/LimeNetwork/BlockProt) (itself a fork of
[spnda/BlockProt](https://github.com/spnda/BlockProt) 1.2.7), branch `canvas-26`. GPL-3.0, see `LICENSE`.

## What this fork changes

- Paper API 26.3, Java 25, Gradle 9.8; no FoliaLib (native Paper schedulers), no bStats, no update checker.
- Text input uses a Paper dialog instead of an anvil window (works for Bedrock players through Geyser).
  `/kilit` opens the lock menu of the block you look at, so nobody has to crouch and right-click.
- Turkish is the default language, with Turkish block names.
- Towny: locks are removed on `/plot clear`, plot unclaim and owner change, town unclaim, town ruin and when
  a resident leaves or is kicked. Mayors and assistants remove locks on town-owned plots.
  The settings in `integrations/towny.yml` now mean what they say (a key that is present is no longer always true).
- `lock_expiry_days`: locks of players who have not joined for that long stop protecting anything.
- ChestShop: a shop can only be set up on a locked chest by its owner or a friend who may manage it.
- Spawners follow the AtlasSpawner owner: only the owner locks and always breaks his spawner.
- Folia safety: region-thread cleanup, locked statistics, concurrent maps, no async inventory changes.
- Lock on place is off by default; anvils are no longer lockable by default.

## Build

    ./gradlew build        # jar: spigot/build/libs/BlockProt-<version>.jar

## Player commands

    /kilit                 menu of the block you look at
    /kilit arkadas <isim>  let a player use the block you look at
    /kilit ayar            your own settings

Author of the changes in this fork: MrMirhan.
