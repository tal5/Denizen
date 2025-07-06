package com.denizenscript.denizen.utilities.blocks;

import org.bukkit.Location;

public record SectionCoordinate(int x, int y, int z) {

    public SectionCoordinate(int x, int y, int z) {
        this.x = x >> 4;
        this.y = y >> 4;
        this.z = z >> 4;
    }

    public SectionCoordinate(Location location) {
        this(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
