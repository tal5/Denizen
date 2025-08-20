package com.denizenscript.denizen.scripts.commands.entity;

import com.denizenscript.denizen.nms.NMSHandler;
import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Adjustable;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.DurationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class ClientAdjustCommand extends AbstractCommand implements Holdable {

    public ClientAdjustCommand() {
        setName("clientadjust");
        setSyntax("clientadjust [<entity>] [<mechanisms>|.../<mechanism>(:<value>)] (for:<player>|...) (speed:<duration>)");
        setPrefixesHandled("for", "speed");
        setRequiredArguments(2, 4);
        this.allowedDynamicPrefixes = true;
        autoCompile();
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        boolean foundEntity = false;
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("entity") && arg.matchesArgumentType(EntityTag.class)) {
                scriptEntry.addObject("entity", arg.asType(EntityTag.class));
                foundEntity = true;
            }
            else if (foundEntity && !scriptEntry.hasObject("frames") && arg.matchesArgumentList(MapTag.class)) {
                scriptEntry.addObject("frames", arg.asType(ListTag.class).filter(MapTag.class, scriptEntry));
            }
            else if (foundEntity && !scriptEntry.hasObject("mechanism")) {
                if (arg.hasPrefix()) {
                    scriptEntry.addObject("mechanism", new ElementTag(arg.getPrefix().getValue(), true));
                    scriptEntry.addObject("mechanism_value", arg.object);
                }
                else {
                    scriptEntry.addObject("mechanism", arg.asElement());
                }
            }
            else {
                arg.reportUnhandled();
            }
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        EntityTag inputEntity = scriptEntry.getObjectTag("entity");
        List<MapTag> frames = (List<MapTag>) scriptEntry.getObject("frames");
        ElementTag mechanism = scriptEntry.getElement("mechanism");
        ObjectTag value = null;
        if (mechanism != null) {
            value = scriptEntry.getObjectTag("mechanism_value");
        }
        DurationTag speed = scriptEntry.argForPrefix("speed", DurationTag.class, true);
        List<PlayerTag> forPlayers = scriptEntry.argForPrefixList("for", PlayerTag.class, true);
        final List<Player> sendTo;
        if (forPlayers == null) {
            PlayerTag player = Utilities.getEntryPlayer(scriptEntry);
            if (player == null) {
                throw new InvalidArgumentsRuntimeException("Must specify players to send adjustments to.");
            }
            sendTo = List.of(player.getPlayerEntity());
        }
        else {
            sendTo = forPlayers.stream().map(PlayerTag::getPlayerEntity).toList();
        }
        if (frames == null && mechanism == null) {
            throw new InvalidArgumentsRuntimeException("Must specify either a list of frames or a mechanism to adjust.");
        }
        if (scriptEntry.dbCallShouldDebug()) {
            Debug.report(scriptEntry, getName(), inputEntity, db("frames", frames), mechanism, value, db("for", forPlayers), speed);
        }
        if ((frames != null && frames.isEmpty()) || sendTo.isEmpty()) {
            return;
        }
        final Entity entity = inputEntity.getBukkitEntity();
        List<Object> originalData = NMSHandler.entityHelper.getInternalEntityData(entity);
        EntityTag copiedEntity = new EntityTag(entity.copy());
        if (mechanism != null) {
            copiedEntity.safeAdjust(new Mechanism(mechanism.asString(), value, scriptEntry.getContext()));
            handleSingleDataModification(entity, copiedEntity, sendTo, originalData, scriptEntry);
            return;
        }
        if (frames.size() == 1) {
            applyMechanisms(copiedEntity, frames.get(0), scriptEntry.getContext());
            handleSingleDataModification(entity, copiedEntity, sendTo, originalData, scriptEntry);
            return;
        }
        final List<List<Object>> internalFrames = new ArrayList<>(frames.size());
        for (MapTag frame : frames) {
            applyMechanisms(copiedEntity, frame, scriptEntry.getContext());
            List<Object> modifiedData = NMSHandler.entityHelper.getInternalEntityData(copiedEntity.getBukkitEntity());
            List<Object> internalFrame = new ArrayList<>(modifiedData);
            if (!internalFrame.isEmpty()) {
                internalFrame.removeAll(originalData);
            }
            originalData = modifiedData;
            if (internalFrame.isEmpty()) {
                continue;
            }
            internalFrames.add(internalFrame);
        }
        final long delayNanos = speed.getMillis() * 1_000_000L;
        if (delayNanos == 0) {
            for (List<Object> internalFrame : internalFrames) {
                NMSHandler.packetHelper.sendEntityDataPacket(sendTo, entity, internalFrame);
            }
            scriptEntry.setFinished(true);
            return;
        }
        DenizenCore.runAsync(() -> {
            long expectedTime = System.nanoTime();
            for (List<Object> internalFrame : internalFrames) {
                // Entries can be removed by sendEntityDataPacket
                if (sendTo.isEmpty()) {
                    break;
                }
                NMSHandler.packetHelper.sendEntityDataPacket(sendTo, entity, internalFrame);
                LockSupport.parkNanos(delayNanos - (System.nanoTime() - expectedTime));
                expectedTime += delayNanos;
            }
            scriptEntry.setFinished(true);
        });
    }

    public static void applyMechanisms(Adjustable adjustable, MapTag mechanisms, TagContext context) {
        for (Map.Entry<StringHolder, ObjectTag> entry : mechanisms.entrySet()) {
            Mechanism mechanism = new Mechanism(entry.getKey().str, entry.getValue(), context);
            adjustable.safeAdjust(mechanism);
        }
    }

    public static void handleSingleDataModification(Entity original, EntityTag copy, List<Player> sendTo, List<Object> originalData, ScriptEntry scriptEntry) {
        List<Object> modifiedData = NMSHandler.entityHelper.getInternalEntityData(copy.getBukkitEntity());
        if (!modifiedData.isEmpty()) {
            modifiedData.removeAll(originalData);
        }
        if (!modifiedData.isEmpty()) {
            NMSHandler.packetHelper.sendEntityDataPacket(sendTo, original, modifiedData);
        }
        scriptEntry.setFinished(true);
    }
}
