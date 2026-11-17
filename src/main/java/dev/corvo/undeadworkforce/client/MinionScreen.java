package dev.corvo.undeadworkforce.client;

import dev.corvo.undeadworkforce.menu.MinionMenu;
import dev.corvo.undeadworkforce.minion.MinionCommand;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Minion gear screen. Left: weapon / tool / armor / collar slots and a live preview of the minion.
 * Right: order buttons (the current order is greyed out), the Fight toggle, and the 5-slot pack.
 * Bottom: player inventory.
 * Drawn with plain fills in the vanilla palette, so it needs no GUI texture.
 */
public class MinionScreen extends AbstractContainerScreen<MinionMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    // Preview box, between the gear column and the right-hand side.
    private static final int PREVIEW_X1 = 26;
    private static final int PREVIEW_Y1 = MinionMenu.GEAR_Y - 1;
    private static final int PREVIEW_X2 = 76;
    private static final int PREVIEW_Y2 = MinionMenu.GEAR_Y + 4 * 18 - 1;

    private static final int BUTTON_W = 44;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_GAP = 2;

    private final Map<MinionCommand, Button> orderButtons = new EnumMap<>(MinionCommand.class);
    private Button fightButton;

    public MinionScreen(MinionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = MinionMenu.PLAYER_INV_Y + 82;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        orderButtons.clear();
        MinionCommand[] layout = {MinionCommand.FOLLOW, MinionCommand.STAY, MinionCommand.WANDER, MinionCommand.PICK_UP};
        for (int i = 0; i < layout.length; i++) {
            MinionCommand command = layout[i];
            int x = leftPos + MinionMenu.PACK_X - 1 + (i % 2) * (BUTTON_W + BUTTON_GAP);
            int y = topPos + MinionMenu.GEAR_Y - 1 + (i / 2) * (BUTTON_H + BUTTON_GAP);
            Button button = Button.builder(Component.translatable("screen.undeadworkforce.order." + key(command)),
                            b -> send(command))
                    .bounds(x, y, BUTTON_W, BUTTON_H)
                    .build();
            orderButtons.put(command, addRenderableWidget(button));
        }
        fightButton = addRenderableWidget(Button.builder(fightLabel(), b -> send(MinionCommand.TOGGLE_FIGHT))
                .bounds(leftPos + MinionMenu.PACK_X - 1, topPos + MinionMenu.GEAR_Y - 1 + 2 * (BUTTON_H + BUTTON_GAP),
                        2 * BUTTON_W + BUTTON_GAP, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("screen.undeadworkforce.fight.tooltip")))
                .build());
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void send(MinionCommand command) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, command.ordinal());
    }

    private void updateButtons() {
        String current = menu.mode().getSerializedName();
        orderButtons.forEach((command, button) -> button.active = !key(command).equals(current));
        fightButton.setMessage(fightLabel());
    }

    private Component fightLabel() {
        return menu.isFighting()
                ? Component.translatable("screen.undeadworkforce.fight.on").withStyle(ChatFormatting.RED)
                : Component.translatable("screen.undeadworkforce.fight.off");
    }

    private static String key(MinionCommand command) {
        return command.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1);
        }
        graphics.fill(x + PREVIEW_X1, y + PREVIEW_Y1, x + PREVIEW_X2, y + PREVIEW_Y2, OUTLINE);
        Mob minion = menu.minion();
        if (minion != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + PREVIEW_X1, y + PREVIEW_Y1,
                    x + PREVIEW_X2, y + PREVIEW_Y2, 30, 0.0625F, mouseX, mouseY, minion);
        }
    }

    /** Empty minion slots name what they're for. */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Slot slot = hoveredSlot;
        if (menu.getCarried().isEmpty() && slot != null && !slot.hasItem() && slot.index < MinionMenu.MINION_SLOTS) {
            Component name = slot.index < MinionMenu.EQUIPMENT_SLOTS
                    ? MinionMenu.gearSlotName(slot.index)
                    : Component.translatable("screen.undeadworkforce.pack");
            graphics.renderTooltip(font, name, mouseX, mouseY);
        } else {
            super.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /** Vanilla-style raised panel: black outline, white top-left bevel, grey bottom-right bevel. */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 3, y + 3, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 3, HIGHLIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 2, y, x + w - 2, y + 1, OUTLINE);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, OUTLINE);
        g.fill(x, y + 2, x + 1, y + h - 2, OUTLINE);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, OUTLINE);
        g.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, OUTLINE);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, OUTLINE);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, OUTLINE);
    }

    /** Sunken 18x18 slot frame at (x, y). */
    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_FILL);
        g.fill(x, y, x + 17, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, HIGHLIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, HIGHLIGHT);
    }
}
