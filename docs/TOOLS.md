# Tools (skills the model can call)

Every tool is plain Java: the model only picks a tool and arguments, and the mod validates them
before anything happens in the world. Tools that take time run as **tasks**; when the last queued
task finishes (or one fails) the agent gets a `[task_result]` message and thinks again.

Models without native tool calling can use `"tool_mode": "json_prompt"` in `models.json`. The agent then answers with
`{"say": "...", "actions": [{"tool": "go_to", "args": {"x": 1, "y": 64, "z": 2}}]}`.

| Tool | Arguments | What it does | Milestone |
|---|---|---|---|
| `say` | `text` | Say something in chat | M3 |
| `follow_player` | `player?` (default: speaker) | Follow a player; FOLLOW mode | M3 |
| `go_to` | `x`, `y`, `z` | Walk to coordinates (waypoints for long trips, max 512 blocks) | M3 |
| `go_to_player` | `player?` | Walk to a player | M3 |
| `stay` | | Stop and wait here (STAY mode) | M3 |
| `stop` | | Cancel the current and queued tasks | M3 |
| `give_item` | `player?`, `item`, `count?` | Walk to a player and toss items | M3 |
| `pickup_items` | `radius?` (1-16) | Pick up dropped items nearby | M3 |
| `get_status` | | Returns the full perception snapshot | M3 |
| `collect_block` | `block`, `count` (1-64), `radius?` (8-48) | Find, walk to and mine blocks; picks the best tool, refuses unsafe blocks (lava/falling), skips unreachable ones | M4 |
| `equip` | `item` | Hold an item in the main hand | M4 |
| `drop_item` | `item`, `count?` | Toss items on the ground | M4 |
| `deposit` | `x`,`y`,`z`, `item?`, `count?` | Put items in a chest (all if no item given) | M4 |
| `withdraw` | `x`,`y`,`z`, `item`, `count` | Take items from a chest | M4 |
| `inventory_summary` | | List held item and inventory | M4 |
| `craft_item` | `item`, `count?` | Craft from inventory; auto-crafts planks/sticks etc. and places a crafting table when needed | M5 |
| `list_craftable` | | Items craftable right now | M5 |
| `attack` | `target` (mob type or name) | Fight the nearest matching mob until it dies (never the owner; players only if allowed) | M6 |
| `guard` | `player?` (`me`, name, or `here`), `radius?` (4-24) | GUARD mode: follow and protect, fight hostiles in range | M6 |
| `stop_attacking` | | Stop fighting, leave GUARD mode | M6 |
| `equip_best_armor` | | Wear best armor, hold best weapon | M6 |
| `eat` | | Eat the best safe food to heal | M6 |
| `remember` / `recall` / `forget` | `key`, `text` / `query` / `key` | Long-term notes, saved in `<world>/aiplayers/memory/` | M7 |
| `set_location` / `go_to_location` | `name`, optional `x y z` / `name` | Named locations shared by all agents, saved in `<world>/aiplayers/team.json` | M7 |
| `message_agent` | `agent`, `message` | Message another agent (max 3 reply hops, 4 per minute) | M7 |
| `place_block` | `item`, optional `x y z` | Place one block | M8 |
| `build_shape` | `shape` (wall/floor/pillar/box), `material`, `width/height/length`, optional `x y z` | Build a simple shape; `material` may be a tag like `#minecraft:planks` or `any_building` | M8 |
| `build_blueprint` | `name`, optional `x y z` | Build a blueprint (in front of you; `shelter` around you) | M8 |
| `list_blueprints` | | Available blueprints | M8 |
| `smelt` | `item` (input or output), `count?` | Smelt in a nearby furnace (places one from inventory), picks fuel automatically | M5 |

The perception snapshot also lists `[RESOURCES]` (logs, ores, stone, water, lava within 24 m) and
`[STRUCTURES]` (crafting tables, furnaces, chests, beds) from an incremental, lag-free block scan.
Server owners can disable block breaking or chest access with `allowBreakBlocks` / `allowContainerAccess`
in `config/aiplayers-server.toml`.

`player` accepts a player name, `me` (whoever is talking) or `owner`. Item and block names are fuzzy-matched:
`"oak logs"`, `"Oak_Log"` and `"minecraft:oak_log"` all work.

## Testing tools without a model

Use the `mock` profile (`/ai spawn Ava mock` or `/ai model Ava mock`). The mock model turns `!tool {json}`
in your message into a tool call, for example:

```
@Ava !go_to {"x": 100, "y": 64, "z": -20}
@Ava !give_item {"item": "oak_log", "count": 3}
```

## Reflexes (no model call)

Every tick, in Java: fight back when hit (never the owner or other agents), defend the owner from mobs targeting them,
eat when health < 14 (skips rotten flesh etc.), flee hostiles when health < 6, climb out of lava or fire, and slowly
regenerate when not hurt for 5 s. Damage shows up in `[RECENT EVENTS]`; tasks pause while fighting and resume after.
