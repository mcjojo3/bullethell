package mc.sayda.bullethell.client.screen;

import mc.sayda.bullethell.entity.BHAttributes;
import mc.sayda.bullethell.client.CharacterUnlockClientState;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.network.BHPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class JoinCharacterSelectScreen extends Screen {

    private static final int PORTRAIT_SIZE = CharacterSelectScreen.PORTRAIT_SIZE;
    private static final int INFO_H        = CharacterSelectScreen.INFO_H;
    private static final int BTN_H         = CharacterSelectScreen.BTN_H;
    private static final int BTN_PAD       = CharacterSelectScreen.BTN_PAD;
    private static final int CARD_W        = CharacterSelectScreen.CARD_W;
    private static final int CARD_H        = CharacterSelectScreen.CARD_H;
    private static final int CARD_GAP      = CharacterSelectScreen.CARD_GAP;

    private final UUID   hostUuid;
    private final String hostName;
    private final List<CharacterDefinition> characters;

    private int selectedIndex = 0;
    private int cardStartX;
    private int cardTopY;

    public JoinCharacterSelectScreen(UUID hostUuid, String hostName) {
        super(Component.literal("Join " + hostName + "'s Arena"));
        this.hostUuid   = hostUuid;
        this.hostName   = hostName;
        this.characters = CharacterLoader.loadAll();
    }

    @Override
    protected void init() {
        super.init();
        ensureSelectedUnlocked();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int n      = characters.size();
        int totalW = n * CARD_W + (n - 1) * CARD_GAP;
        cardStartX = (width  - totalW) / 2;
        cardTopY   = height / 2 - CARD_H / 2;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            int bx   = cardStartX + i * (CARD_W + CARD_GAP);
            int btnX = bx + (CARD_W - 80) / 2;
            int btnY = cardTopY + PORTRAIT_SIZE + INFO_H + BTN_PAD * 2;
            boolean unlocked = isUnlocked(idx);

            Button b = Button.builder(
                    Component.literal(i == selectedIndex ? "JOIN" : "SELECT"),
                    btn -> {
                        if (!isUnlocked(idx))
                            return;
                        selectedIndex = idx;
                        confirm();
                    })
                    .pos(btnX, btnY)
                    .size(80, BTN_H)
                    .build();
            b.active = unlocked;
            addRenderableWidget(b);
        }

        addRenderableWidget(Button.builder(
                Component.literal("SHARE LAST RUN"),
                btn -> BHPackets.sendShareLastRun())
                .pos(width / 2 - 60, height - 28)
                .size(120, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, 0xFF0A0A14);
        gfx.drawCenteredString(font, "SELECT CHARACTER", width / 2, 20, 0xFFFFE600);
        gfx.drawCenteredString(font, "Joining " + hostName + "'s arena", width / 2, 33, 0xFF88CCFF);
        gfx.drawCenteredString(font, "\u2190 / \u2192  browse     Enter  confirm",
                width / 2, 44, 0xFF445566);

        int n = characters.size();
        for (int i = 0; i < n; i++) {
            CharacterDefinition ch = characters.get(i);
            int bx  = cardStartX + i * (CARD_W + CARD_GAP);
            boolean sel = (i == selectedIndex);
            boolean unlocked = isUnlocked(i);

            gfx.fill(bx, cardTopY, bx + CARD_W, cardTopY + CARD_H,
                    sel ? 0xFF1C1C36 : 0xFF0D0D20);
            int brd = unlocked ? (sel ? 0xFFFFE600 : 0xFF334466) : 0xFF444444;
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY,              brd);
            gfx.hLine(bx, bx + CARD_W - 1, cardTopY + CARD_H - 1, brd);
            gfx.vLine(bx,              cardTopY, cardTopY + CARD_H, brd);
            gfx.vLine(bx + CARD_W - 1, cardTopY, cardTopY + CARD_H, brd);

            int px = bx + (CARD_W - PORTRAIT_SIZE) / 2;
            int py = cardTopY + BTN_PAD;
            CharacterSelectScreen.renderPortrait(gfx, ch, px, py, PORTRAIT_SIZE);

            gfx.hLine(bx + 4, bx + CARD_W - 5,
                    cardTopY + PORTRAIT_SIZE + BTN_PAD + 2, 0x33FFFFFF);

            int infoY = cardTopY + PORTRAIT_SIZE + BTN_PAD + 5;
            int cx    = bx + CARD_W / 2;
            int nameCol = unlocked ? (sel ? 0xFFFFDD00 : 0xFFCCCCCC) : 0xFF777777;
            gfx.drawCenteredString(font, ch.name,        cx, infoY,                      nameCol);
            gfx.drawCenteredString(font, ch.description, cx, infoY + font.lineHeight + 2,
                    unlocked ? 0xFF8888AA : 0xFF555566);
            var pl = Minecraft.getInstance().player;
            int attrL = BHAttributes.extraLivesBonus(pl);
            int attrB = BHAttributes.extraBombsBonus(pl);
            int dispLives = ch.startingLives + attrL;
            int dispBombs = Math.min(9, ch.startingBombs + attrB);
            String stats = "\u2665" + dispLives + "  \u2736" + dispBombs
                         + "  Spd:" + (int) ch.speedNormal;
            gfx.drawCenteredString(font, stats, cx, infoY + font.lineHeight * 2 + 4,
                    unlocked ? 0xFF7799CC : 0xFF555566);

            if (sel && unlocked) {
                gfx.drawCenteredString(font, "\u25bc", cx,
                        cardTopY + PORTRAIT_SIZE + INFO_H + BTN_PAD - 1, 0xFFFFE600);
            }
            if (!unlocked) {
                gfx.drawCenteredString(font, "LOCKED", cx,
                        cardTopY + PORTRAIT_SIZE + INFO_H + BTN_PAD - 1, 0xFFAA4444);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int n = characters.size();
        for (int i = 0; i < n; i++) {
            int bx = cardStartX + i * (CARD_W + CARD_GAP);
            int py = cardTopY + BTN_PAD;
            if (mx >= bx && mx < bx + CARD_W && my >= py && my < py + PORTRAIT_SIZE + INFO_H) {
                if (!isUnlocked(i))
                    return true;
                if (selectedIndex != i) { selectedIndex = i; rebuildButtons(); }
                return true;
            }
        }
        return false;
    }

    private void confirm() {
        if (characters.isEmpty()) return;
        if (!isUnlocked(selectedIndex)) return;
        BHPackets.sendJoinMatch(hostUuid, characters.get(selectedIndex).id);
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && selectedIndex > 0)                       { selectedIndex = findPrevUnlocked(selectedIndex); rebuildButtons(); return true; }
        if (keyCode == 262 && selectedIndex < characters.size() - 1)   { selectedIndex = findNextUnlocked(selectedIndex); rebuildButtons(); return true; }
        if (keyCode == 257 || keyCode == 335)                          { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private boolean isUnlocked(int idx) {
        if (idx < 0 || idx >= characters.size())
            return false;
        return CharacterUnlockClientState.INSTANCE.isUnlockedAny(characters.get(idx).id);
    }

    private void ensureSelectedUnlocked() {
        if (characters.isEmpty())
            return;
        if (isUnlocked(selectedIndex))
            return;
        for (int i = 0; i < characters.size(); i++) {
            if (isUnlocked(i)) {
                selectedIndex = i;
                return;
            }
        }
    }

    private int findNextUnlocked(int from) {
        for (int i = from + 1; i < characters.size(); i++) {
            if (isUnlocked(i))
                return i;
        }
        return from;
    }

    private int findPrevUnlocked(int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (isUnlocked(i))
                return i;
        }
        return from;
    }
}
