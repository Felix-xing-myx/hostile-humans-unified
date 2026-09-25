package com.craftix.hostile_humans.entity;

public enum AggressionMode {
    PASSIVE,
    AGGRESSIVE_MONSTER,
    AGGRESSIVE_ALL;


    public static AggressionMode get(String aggressionLevel) {
        if (aggressionLevel == null || aggressionLevel.isEmpty()) {
            return PASSIVE;
        }
        try {
            return AggressionMode.valueOf(aggressionLevel);
        }
        catch (IllegalArgumentException e) {
            return PASSIVE;
        }
    }

    public AggressionMode getNext() {
        return AggressionMode.values()[(this.ordinal() + 1) % AggressionMode.values().length];
    }
}

