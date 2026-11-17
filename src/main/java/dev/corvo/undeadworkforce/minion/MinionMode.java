package dev.corvo.undeadworkforce.minion;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Standing order for a minion. Saved on the entity via {@code ModAttachments.MINION_MODE}. */
public enum MinionMode implements StringRepresentable {
    FOLLOW("follow"),
    STAY("stay"),
    WANDER("wander");

    public static final Codec<MinionMode> CODEC = StringRepresentable.fromEnum(MinionMode::values);

    private final String name;

    MinionMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "minion_mode.undeadworkforce." + name;
    }
}
