package com.joserayos.aiplayers.client;

import com.joserayos.aiplayers.network.AgentActionPayload;
import com.joserayos.aiplayers.network.AgentListPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Agent Manager: list agents, change mode/model, stop, despawn and message them. Opened with /ai gui. */
public class AgentManagerScreen extends Screen {
    static final int ROW_HEIGHT = 44;
    static final String[] MODES = {"follow", "stay", "guard", "autonomous"};

    private AgentListPayload data;
    private int scroll;
    private EditBox message;
    private String selected = "";

    public AgentManagerScreen(AgentListPayload data) {
        super(Component.literal("AI Players: Agent Manager"));
        this.data = data;
        if (!data.agents().isEmpty()) selected = data.agents().getFirst().name();
    }

    public void update(AgentListPayload newData) {
        this.data = newData;
        if (newData.agents().stream().noneMatch(a -> a.name().equals(selected))) {
            selected = newData.agents().isEmpty() ? "" : newData.agents().getFirst().name();
        }
        String typed = message == null ? "" : message.getValue();
        rebuildWidgets();
        message.setValue(typed);
    }

    static void send(String agent, String action, String arg) {
        ClientPacketDistributor.sendToServer(new AgentActionPayload(agent, action, arg));
    }

    int visibleRows() { return Math.max(1, (height - 90) / ROW_HEIGHT); }

    @Override
    protected void init() {
        int left = width / 2 - 170;
        List<AgentListPayload.Entry> agents = data.agents();
        scroll = Math.max(0, Math.min(scroll, agents.size() - visibleRows()));
        for (int i = 0; i < visibleRows() && scroll + i < agents.size(); i++) {
            AgentListPayload.Entry a = agents.get(scroll + i);
            int y = 30 + i * ROW_HEIGHT + 18;
            int x = left;
            addRenderableWidget(Button.builder(Component.literal(a.name().equals(selected) ? "> Talk" : "Talk"), b -> {
                selected = a.name();
                update(data);
            }).bounds(x, y, 44, 18).build());
            x += 46;
            for (String m : MODES) {
                Button btn = Button.builder(Component.literal(cap(m)), b -> send(a.name(), "mode", m)).bounds(x, y, 52, 18).build();
                btn.active = a.canControl() && !a.mode().equals(m);
                addRenderableWidget(btn);
                x += 54;
            }
            Button model = Button.builder(Component.literal("Model"), b -> send(a.name(), "model", nextProfile(a.profile())))
                    .bounds(x, y, 44, 18).tooltip(Tooltip.create(Component.literal("Switch to the next model profile"))).build();
            model.active = a.canControl() && data.profiles().size() > 1;
            addRenderableWidget(model);
            x += 46;
            Button stop = Button.builder(Component.literal("Stop"), b -> send(a.name(), "stop", "")).bounds(x, y, 34, 18).build();
            stop.active = a.canControl();
            addRenderableWidget(stop);
            x += 36;
            Button despawn = Button.builder(Component.literal("X"), b -> send(a.name(), "despawn", "")).bounds(x, y, 18, 18)
                    .tooltip(Tooltip.create(Component.literal("Despawn " + a.name()))).build();
            despawn.active = a.canControl();
            addRenderableWidget(despawn);
        }
        int bottom = height - 30;
        message = new EditBox(font, left, bottom, 230, 20, Component.literal("Message"));
        message.setMaxLength(400);
        message.setHint(Component.literal(selected.isEmpty() ? "No agent selected" : "Message " + selected + "..."));
        addRenderableWidget(message);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> sendMessage()).bounds(left + 234, bottom, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> send("", "refresh", "")).bounds(left + 288, bottom, 52, 20).build());
        if (agents.size() > visibleRows()) {
            addRenderableWidget(Button.builder(Component.literal("^"), b -> { scroll--; update(data); }).bounds(left + 344, 48, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("v"), b -> { scroll++; update(data); }).bounds(left + 344, 68, 16, 16).build());
        }
    }

    private void sendMessage() {
        if (selected.isEmpty() || message.getValue().isBlank()) return;
        send(selected, "tell", message.getValue());
        message.setValue("");
    }

    String nextProfile(String current) {
        List<String> p = data.profiles();
        if (p.isEmpty()) return current;
        int i = p.indexOf(current);
        return p.get((i + 1) % p.size());
    }

    static String cap(String s) { return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        int left = width / 2 - 170;
        List<AgentListPayload.Entry> agents = data.agents();
        if (agents.isEmpty()) g.centeredText(font, "No agents yet. Spawn one with /ai spawn <name>.", width / 2, 40, 0xFFAAAAAA);
        for (int i = 0; i < visibleRows() && scroll + i < agents.size(); i++) {
            AgentListPayload.Entry e = agents.get(scroll + i);
            int y = 30 + i * ROW_HEIGHT;
            String head = e.name() + "  [" + e.mode() + "]  " + Math.round(e.health()) + "/" + Math.round(e.maxHealth()) + " hp  "
                    + e.profile() + "  owner " + e.owner();
            g.text(font, head, left, y + 2, e.canControl() ? 0xFFFFFF55 : 0xFFAAAAAA);
            String sub = "task: " + e.task() + (e.goal().isEmpty() ? "" : " | goal: " + e.goal());
            g.text(font, font.plainSubstrByWidth(sub, 340), left, y + 11, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
