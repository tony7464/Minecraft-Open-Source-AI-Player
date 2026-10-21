# Modrinth / CurseForge listing (draft)

**Name:** AI Players
**Summary:** AI teammates for Minecraft that you command in chat (or by voice), powered by local or hosted AI models.
**Categories:** Utility, Adventure, Game Mechanics · **Environment:** server required, client optional
(the client mod adds the Agent Manager screen and push-to-talk; plain chat works without it).
**Loader / version:** NeoForge 26.1.2 · Java 25 · **License:** MIT · **Source:** https://github.com/tony7464/Minecraft-Open-Source-AI-Player

## Description

Spawn AI agents (`/ai spawn Ava`) that follow you, chat, gather wood and stone, craft tools, smelt, fight mobs,
guard you, build huts, remember things across restarts, and work together. Talk to them with `@Ava get me 16 oak logs`.

- **Any model:** free local models through Ollama or LM Studio, or hosted models (xAI, OpenAI, Anthropic and other
  OpenAI-compatible APIs). Each agent can use a different model; switch any time with `/ai model`.
- **Cost controls:** daily budgets per profile, rate limits, usage log, fallback profiles, and a kill switch (`/ai stop all`).
- **Safe by default:** agents only obey their owner, never attack their owner, and server owners can turn off block
  breaking, placing, chest access or PvP for agents.
- **Fast reflexes without the model:** eating, fleeing, fighting back and escaping lava run in plain Java.
- **Autonomous mode:** agents set and verify their own goals (wood, tools, food, shelter, iron).
- **Voice:** hold V to talk, using your own local whisper server.

## Screenshots to take
1. Two agents chopping a tree together. 2. The Agent Manager (`/ai gui`). 3. A finished hut. 4. Chat with an agent replying.
