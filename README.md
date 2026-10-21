# Minecraft Open Source AI Player (AI Players mod)

An open source **NeoForge** mod for **Minecraft Java 26.1.2** that adds multiple AI player agents to your world.
Agents can play alongside you or on their own, take commands through chat, and run on **swappable LLM backends**:
local models (Ollama, LM Studio, llama.cpp) or hosted ones (OpenAI, xAI, Gemini, OpenRouter, Anthropic Claude).

The full design is in [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md).

> Status: early development. See the [roadmap](#roadmap) for what works today.

## How it works (short version)

- Each agent is a server-side entity (`AgentEntity`, a `PathfinderMob`) rendered with the player model.
- The AI model never touches the world directly. It runs on background threads and picks from a fixed set of
  Java-coded, validated tools (follow, go to, collect blocks, give items, ...).
- Each agent uses a **model profile** from `config/aiplayers/models.json`, so you can swap models per agent without code changes.

## Requirements

| Tool | Version |
|---|---|
| JDK | 25 (64-bit) |
| Minecraft | Java Edition 26.1.2 |
| NeoForge | 26.1.2.122 or newer 26.1.2.x |
| Gradle | none to install, the wrapper (`./gradlew`) downloads 9.x |

## Developer setup

```bash
git clone https://github.com/tony7464/Minecraft-Open-Source-AI-Player.git
cd Minecraft-Open-Source-AI-Player
./gradlew build          # first run downloads and decompiles Minecraft (can take a while)
./gradlew runClient      # start the game with the mod loaded
./gradlew runServer      # dedicated server (set eula=true in runs/server/eula.txt first)
./gradlew test           # JUnit tests (LLM layer, parsers)
./gradlew runGameTestServer  # in-world GameTests
```

Open the folder in IntelliJ IDEA (with the Minecraft Development plugin) to run and debug.
The built mod jar ends up in `build/libs/`.

## Ollama quickstart (free, local)

1. Install Ollama from <https://ollama.com>.
2. Pull a model that supports tool calling:
   ```bash
   ollama pull qwen3:8b
   ```
3. Check it works:
   ```bash
   curl http://localhost:11434/v1/chat/completions -H "Content-Type: application/json" \
     -d '{"model":"qwen3:8b","messages":[{"role":"user","content":"Say hi in 5 words"}]}'
   ```
4. Start the game (`./gradlew runClient`), open a world with cheats on, and run:
   ```
   /ai spawn Ava
   @Ava hi! what time is it and how far away am I?
   ```

## Configuring models (`models.json`)

On first launch the mod writes `config/aiplayers/models.json` with example profiles.
Each agent uses one profile; switch in-game with `/ai model <agent> <profile>` and re-read files with `/ai reload`.

```json
{
  "default_profile": "local-qwen",
  "profiles": {
    "local-qwen": {
      "provider": "openai_compat",
      "base_url": "http://localhost:11434/v1",
      "model": "qwen3:8b",
      "api_key": "",
      "tool_mode": "native",
      "temperature": 0.4,
      "max_output_tokens": 512,
      "timeout_seconds": 60
    },
    "lmstudio": {
      "provider": "openai_compat",
      "base_url": "http://localhost:1234/v1",
      "model": "your-loaded-model-id",
      "tool_mode": "json_prompt"
    },
    "grok": {
      "provider": "openai_compat",
      "base_url": "https://api.x.ai/v1",
      "model": "<model id from xAI docs>",
      "api_key": "${env:XAI_API_KEY}",
      "daily_budget_usd": 1.00
    },
    "claude": {
      "provider": "anthropic",
      "base_url": "https://api.anthropic.com",
      "model": "<model id from Anthropic docs>",
      "api_key": "${env:ANTHROPIC_API_KEY}",
      "daily_budget_usd": 1.00
    },
    "mock": { "provider": "mock" }
  }
}
```

| Field | Meaning |
|---|---|
| `provider` | `openai_compat` (Ollama, LM Studio, llama.cpp, OpenAI, xAI, OpenRouter, Gemini OpenAI endpoint), `anthropic`, or `mock` |
| `base_url` | API base URL |
| `model` | Model id as the provider names it |
| `api_key` | Use `${env:NAME}` to read it from an environment variable. **Never put a real key in a committed file.** |
| `tool_mode` | `native` (provider tool calling) or `json_prompt` (fallback for models without tool calling) |
| `temperature`, `max_output_tokens`, `timeout_seconds` | Request settings |
| `requests_per_minute` | Rate limit per profile (default 20) |
| `daily_budget_usd`, `price_per_million_input`, `price_per_million_output` | Cost guard for hosted models. Set the prices from your provider's pricing page, otherwise cost is estimated as $0 and the budget never triggers |
| `fallback_profile` | Profile to switch to when the daily budget is used up (otherwise the agent pauses) |

Usage is logged to `logs/aiplayers-usage.csv`; `/ai budget` shows today's totals.

## Tools

Agents act through validated Java tools such as `follow_player`, `go_to`, `give_item` and `pickup_items`.
See [`docs/TOOLS.md`](docs/TOOLS.md) for the full list and how to test them offline with the `mock` profile.

## Commands

| Command | What it does |
|---|---|
| `@Ava <message>` / `@all <message>` | Talk to one agent or all of your agents |
| `/ai spawn <name> [profile]` | Spawn an agent you own |
| `/ai despawn <name>` | Remove an agent |
| `/ai list` | List agents |
| `/ai tell <name> <message>` | Same as `@name` |
| `/ai mode <name> <follow\|idle\|stay>` | Change mode |
| `/ai model <name> <profile>` | Hot-swap the model profile |
| `/ai profiles` | List model profiles from models.json |
| `/ai stop <name\|all>` | Kill switch: cancel tasks and pending model calls |
| `/ai status <name>` | Health, task, profile |
| `/ai test-profile <profile>` | Connectivity and tool-calling test |
| `/ai budget` · `/ai reload` | Costs today · reload config files |
| `/ai debug snapshot <name>` | Show the world snapshot the model sees |

## Roadmap

- [x] **M0** Environment and hello mod (NeoForge 26.1.2 MDK, README, license, CI)
- [x] **M1** MVP: one agent that follows you and chats via a local model
- [x] **M2** Swappable providers, config profiles and cost controls
- [x] **M3** Tool calling and first skills
- [x] **M4** Mining, gathering and inventory
- [x] **M5** Crafting and smelting
- [x] **M6** Survival and combat
- [ ] **M7** Multi-agent and memory
- [ ] **M8** Building
- [ ] **M9** Autonomous mode
- [ ] **M10** GUI
- [ ] **M11** Voice
- [ ] **M12** Polish and release

## Continuous integration

A ready-made GitHub Actions workflow lives in [`docs/ci/build.yml`](docs/ci/build.yml). Move it to
`.github/workflows/build.yml` to enable CI (build, unit tests and GameTests on Java 25).

## License

[MIT](LICENSE) © 2026 Jose Rayos
