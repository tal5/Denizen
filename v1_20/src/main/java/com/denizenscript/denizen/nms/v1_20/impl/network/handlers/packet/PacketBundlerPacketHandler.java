package com.denizenscript.denizen.nms.v1_20.impl.network.handlers.packet;

import com.denizenscript.denizen.nms.NMSHandler;
import com.denizenscript.denizen.nms.v1_20.helpers.PacketHelperImpl;
import com.denizenscript.denizen.nms.v1_20.impl.network.handlers.DenizenNetworkManagerImpl;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PacketBundlerPacketHandler {

    public static void startBundling(Player player) {
        DenizenNetworkManagerImpl.getNetworkManager(player).toBundle = new ArrayList<>();
    }

    public static void stopBundling(Player player) {
        DenizenNetworkManagerImpl networkManager = DenizenNetworkManagerImpl.getNetworkManager(player);
        List<Packet<ClientGamePacketListener>> toBundle = networkManager.toBundle;
        if (toBundle == null) {
            Debug.echoError("Tried sending bundled packets, but bundling wasn't started.");
            return;
        }
        networkManager.toBundle = null;
        if (!toBundle.isEmpty()) {
            Debug.log("Sending bundled packets to " + player.getName() + ':');
            NMSHandler.debugPackets = true;
            toBundle.forEach(DenizenNetworkManagerImpl.getNetworkManager(player)::debugOutputPacket);
            NMSHandler.debugPackets = false;
            PacketHelperImpl.send(player, new ClientboundBundlePacket(toBundle));
        }
    }

    public static void register() {
        DenizenNetworkManagerImpl.registerPacketHandler(null, PacketBundlerPacketHandler::processPacketForBundle);
    }

    public static Packet<ClientGamePacketListener> processPacketForBundle(DenizenNetworkManagerImpl networkManager, Packet<ClientGamePacketListener> packet) {
        if (networkManager.toBundle != null) {
            networkManager.toBundle.add(packet);
            return null;
        }
        return packet;
    }
}
