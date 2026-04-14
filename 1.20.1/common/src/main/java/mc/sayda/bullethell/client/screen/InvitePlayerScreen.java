package mc.sayda.bullethell.client.screen;

import com.mojang.authlib.GameProfile;
import mc.sayda.bullethell.network.BHPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class InvitePlayerScreen extends Screen {

    private static final int CARD_W = 120;
    private static final int CARD_H = 60;
    private static final int CARD_GAP = 16;
    private static final int BTN_H = 20;

    private final Screen parent;
    private final List<PlayerInfo> players = new ArrayList<>();
    private int selectedIndex = 0;
    private int cardStartX;
    private int cardTopY;

    public InvitePlayerScreen(Screen parent) {
        super(Component.literal("Invite Player"));
        this.parent = parent;
        
        UUID localUuid = Minecraft.getInstance().player.getUUID();
        for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
            if (!info.getProfile().getId().equals(localUuid)) {
                players.add(info);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int n = players.size();
        if (n == 0) return;

        // Simple row layout for now (can be expanded to grid if many players)
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        cardStartX = (width - totalW) / 2;
        cardTopY = height / 2 - CARD_H / 2;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            int btnX = bx + (CARD_W - 80) / 2;
            int btnY = cardTopY + CARD_H - BTN_H - 6;

            addRenderableWidget(Button.builder(
                    Component.literal(i == selectedIndex ? "INVITE" : "SELECT"),
                    btn -> {
                        selectedIndex = idx;
                        confirm();
                    })
                    .pos(btnX, btnY)
                    .size(80, BTN_H)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xAA000000); // Semi-transparent overlay

        gfx.drawCenteredString(font, "INVITE CO-OP PARTNER", width / 2, 20, 0xFFFFE600);

        if (players.isEmpty()) {
            gfx.drawCenteredString(font, "No other players online.", width / 2, height / 2, 0xFF8888AA);
        } else {
            gfx.drawCenteredString(font, "\u2190 / \u2192  browse     Enter  invite     ESC  cancel",
                    width / 2, 33, 0xFF445566);

            int n = players.size();
            for (int i = 0; i < n; i++) {
                PlayerInfo info = players.get(i);
                int bx = cardStartX + i * (CARD_W + CARD_GAP);
                boolean sel = (i == selectedIndex);

                gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H,
                        sel ? 0xFF1A1A38 : 0xFF0A0A1E);
                int brd = sel ? 0xFFFFE600 : 0xFF334466;
                gfx.hLine(bx, bx + CARD_W - 1, cardTopY, brd);
                gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
                gfx.vLine(bx, cardTopY, cardTopY + CARD_H, brd);
                gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

                int cx = bx + CARD_W / 2;
                int textY = cardTopY + 12;

                gfx.drawCenteredString(font, info.getProfile().getName(), cx, textY, sel ? 0xFFFFDD00 : 0xFFCCCCCC);
                
                if (sel) {
                    gfx.drawCenteredString(font, "\u25bc", cx, cardTopY + CARD_H - BTN_H - 6, 0xFFFFE600);
                }
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void renderBackground(GuiGraphics gfx) {
        // Overlay logic handled in render
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int n = players.size();
        for (int i = 0; i < n; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            if (mx >= bx && mx < bx + CARD_W && my >= cardTopY && my < cardTopY + CARD_H) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    rebuildButtons();
                }
                return true;
            }
        }
        return false;
    }

    private void confirm() {
        if (players.isEmpty()) return;
        PlayerInfo target = players.get(selectedIndex);
        BHPackets.sendInvitePlayer(target.getProfile().getId());
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        if (players.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);
        
        if (keyCode == 263 && selectedIndex > 0) {
            selectedIndex--;
            rebuildButtons();
            return true;
        }
        if (keyCode == 262 && selectedIndex < players.size() - 1) {
            selectedIndex++;
            rebuildButtons();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
