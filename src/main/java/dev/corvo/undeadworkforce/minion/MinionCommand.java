package dev.corvo.undeadworkforce.minion;

/** Orders a player can give from the minion menu. Sent as menu button clicks: the id is the ordinal. */
public enum MinionCommand {
    FOLLOW,
    STAY,
    WANDER,
    PICK_UP,
    TOGGLE_FIGHT
}
