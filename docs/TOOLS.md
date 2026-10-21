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

`player` accepts a player name, `me` (whoever is talking) or `owner`. Item and block names are fuzzy-matched:
`"oak logs"`, `"Oak_Log"` and `"minecraft:oak_log"` all work.

## Testing tools without a model

Use the `mock` profile (`/ai spawn Ava mock` or `/ai model Ava mock`). The mock model turns `!tool {json}`
in your message into a tool call, for example:

```
@Ava !go_to {"x": 100, "y": 64, "z": -20}
@Ava !give_item {"item": "oak_log", "count": 3}
```
