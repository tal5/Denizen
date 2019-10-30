package com.denizenscript.denizen.objects.properties.inventory;

import com.denizenscript.denizen.objects.InventoryTag;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.properties.Property;

public class InventoryUniquifier implements Property {

    public static boolean describes(ObjectTag inventory) {
        // All inventories can potentially have a uniquifier
        return inventory instanceof InventoryTag;
    }

    public static InventoryUniquifier getFrom(ObjectTag inventory) {
        if (!describes(inventory)) {
            return null;
        }
        return new InventoryUniquifier((InventoryTag) inventory);
    }

    public static final String[] handledMechs = new String[] {
            "uniquifier"
    };

    ///////////////////
    // Instance Fields and Methods
    /////////////

    InventoryTag inventory;

    public InventoryUniquifier(InventoryTag inventory) {
        this.inventory = inventory;
    }

    /////////
    // Property Methods
    ///////

    @Override
    public String getPropertyString() {
        if (inventory.uniquifier == null) {
            return null;
        }
        return String.valueOf(inventory.uniquifier);
    }

    @Override
    public String getPropertyId() {
        return "uniquifier";
    }

    public static void registerTags() {
        // Intentionally no tags.
    }

    @Override
    public void adjust(Mechanism mechanism) {

        // Undocumented / internal
        if (mechanism.matches("uniquifier")) {
            inventory.uniquifier = mechanism.getValue().asLong();
        }
    }
}
