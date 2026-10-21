# Changelog

All versions target Minecraft Java 26.1.2 with NeoForge 26.1.2.x and Java 25.

## 0.13.0 (M13) Exploration
- New `explore` tool (direction or auto, distance, optional `looking_for`) with chunk loading while travelling, water/cliff/stuck detours.
- New `go_to_known` tool and a persisted per-agent resource/landmark map; shown as `[KNOWN PLACES]` and in `/ai status`.
- `collect_block` and the autonomous fallback explore instead of failing when nothing is nearby; curiosity exploring.
- Distance caps are now config with generous defaults (`maxGoToDistance` 0 = unlimited, `explorationRadius` 0 = unlimited, `maxCollectRadius` 96, `scanRadius` 32).

## 0.12.0 (M12, partial)
- `/ai debug perf` and a 5-agent performance GameTest; dedicated server start verified.
- `pickup_items` skips items it can't reach.

## 0.11.0 (M11) Voice
- Push-to-talk (hold V): local whisper.cpp or any OpenAI-compatible speech-to-text, transcript confirmed in the chat box.

## 0.10.0 (M10) GUI
- `/ai gui` Agent Manager screen (mode, model, stop, despawn, message) over permission-checked network payloads.

## 0.9.0 (M9) Autonomous mode
- Idle heartbeat, curriculum (wood, tools, food, hut, iron), `set_goal` / `complete_goal` with inventory checks.

## 0.8.0 (M8) Building
- `place_block`, `build_shape`, `build_blueprint`, built-in `hut` and `shelter`, user blueprints in JSON.

## 0.7.0 (M7) Multi-agent and memory
- Persistent memory (`remember` / `recall` / `forget`), shared locations, `message_agent` with loop limits, `/ai memory`.

## 0.6.0 (M6) Survival and combat
- Reflexes (fight back, defend owner, eat, flee, escape lava), GUARD mode, `attack`, `equip_best_armor`, `eat`.

## 0.5.0 (M5) Crafting and smelting
- Crafting planner with intermediate items and automatic crafting tables, `smelt`, `list_craftable`.

## 0.4.0 (M4) Gathering
- `collect_block` with tool choice, safety checks and block claims; chests; nearby-resource perception.

## 0.3.0 (M3) Tools
- Native tool calling with JSON fallback, task executor with timeouts and stuck detection, first skills.

## 0.2.0 (M2) Providers
- OpenAI-compatible (Ollama, LM Studio, xAI, OpenAI...), Anthropic and mock providers; budgets, rate limits, fallbacks.

## 0.1.0 (M1) MVP
- Agent entity that follows its owner and chats through a model profile.
