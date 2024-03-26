package com.denizenscript.denizen.scripts.commands.player;

import com.denizenscript.denizen.nms.NMSHandler;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizen.utilities.packets.NetworkInterceptHelper;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;

public class BundleCommand extends AbstractCommand {

    public BundleCommand() {
        setName("bundle");
        setSyntax("bundle [start/stop]");
        autoCompile();
    }

    public enum Action {START, STOP}

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") Action action) {
        PlayerTag player = Utilities.getEntryPlayer(scriptEntry);
        if (player == null) {
            throw new InvalidArgumentsRuntimeException("Most have a linked player to bundle packets for.");
        }
        NetworkInterceptHelper.enable();
        switch (action) {
            case START -> NMSHandler.packetHelper.startBundlingPackets(player.getPlayerEntity());
            case STOP -> NMSHandler.packetHelper.stopAndSendBundledPackets(player.getPlayerEntity());
        }
    }
}
