package com.denizenscript.denizen.utilities.blocks;

import org.bukkit.Location;

public record SectionCoordinate(int x, int y, int z) {

    public static int coordinateToSection(int coordinate) {
        return coordinate >> 4;
    }

    public SectionCoordinate(int x, int y, int z) {
        this.x = coordinateToSection(x);
        this.y = coordinateToSection(y);
        this.z = coordinateToSection(z);
    }

    public SectionCoordinate(Location location) {
        this(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
