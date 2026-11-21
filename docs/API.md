# VeyrionCharacter Developer API

Package `com.veyrion.character.api` (+ `.event`). Stable within one `API_VERSION` (currently 1). Everything else is internal.

## Setup
plugin.yml of your plugin: `depend: [VeyrionCharacter]` (or `softdepend`). Compile against the jar.

```java
VeyrionCharacterAPI api = VeyrionCharacterAPI.get();            // throws if plugin not running
Optional<VeyrionCharacterAPI> maybe = VeyrionCharacterAPI.find(); // softdepend
```
Also registered in Bukkit's `ServicesManager`.

## Threads
Futures and callbacks complete on the **main thread**. Methods without a future answer from memory and never block.

## Methods
| Area | Method |
|---|---|
| Character | `character(uuid)` (cached, instant), `loadCharacter(uuid)` (DB), `grantExp(uuid, amount)` |
| Races | `races()`, `race(id)` |
| Quests | `quests()`, `quest(id)`, `isQuestActive(player, id)`, `hasCompletedQuest(player, id)`, `acceptQuest(player, id, cb)`, `progressCustomObjective(player, key, amount)` |
| Economy | `economyAvailable()`, `balance(uuid)`, `formatMoney(amount)` |
| NPC | `npcs()`, `npc(id)` |

Rank is always derived from EXP with the live rank table (never stale).

## Events (main thread)
- `CharacterExpGainEvent` : uuid, gained, old/new EXP.
- `CharacterRankChangeEvent` : uuid, old/new rank.
- `NpcInteractEvent` (cancellable) : player, NPC id, whether it was a Shift + Right Click editor open. Cancel to stop the NPC's own reaction.

## Example
```java
@EventHandler
void onRank(CharacterRankChangeEvent e) {
    Bukkit.broadcast(Component.text(e.uuid() + " is now " + e.newRank()));
}
api.grantExp(player.getUniqueId(), 100).thenAccept(g -> player.sendMessage("+" + g.gained()));
```
See the Javadoc in `VeyrionCharacterAPI` for exact semantics.

## NPCs (built in)
Commands `/vnpc create|edit|tp|remove|list|reload` (perm `veyrion.npc.admin`). Shift + Right Click opens the editor. Data in `plugins/VeyrionCharacter/npcs.yml`. NPC bodies are fake players sent as packets through **ProtocolLib** (softdepend; Paper 1.21.1 packet layout). Quest objective `TALK_TO_NPC` uses the NPC id.
