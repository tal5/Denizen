package com.denizenscript.denizen.nms.v1_20.impl.network.handlers.packet;

import com.denizenscript.denizen.nms.v1_20.ReflectionMappingsInfo;
import com.denizenscript.denizen.nms.v1_20.impl.network.handlers.DenizenNetworkManagerImpl;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.utilities.blocks.ChunkCoordinate;
import com.denizenscript.denizen.utilities.blocks.FakeBlock;
import com.denizenscript.denizen.utilities.blocks.SectionCoordinate;
import com.denizenscript.denizencore.utilities.ReflectionHelper;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.v1_20_R3.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FakeBlocksPacketHandlers {

    public static void registerHandlers() {
        DenizenNetworkManagerImpl.registerPacketHandler(ClientboundLevelChunkWithLightPacket.class, FakeBlocksPacketHandlers::processLevelChunkWithLightPacket);
        DenizenNetworkManagerImpl.registerPacketHandler(ClientboundSectionBlocksUpdatePacket.class, FakeBlocksPacketHandlers::processSectionBlocksUpdatePacket);
        DenizenNetworkManagerImpl.registerPacketHandler(ClientboundBlockUpdatePacket.class, FakeBlocksPacketHandlers::processBlockUpdatePacket);
    }

    public static BlockState getNMSState(FakeBlock block) {
        return ((CraftBlockData) block.material.getModernData()).getState();
    }

    public static Field SECTIONPOS_MULTIBLOCKCHANGE = ReflectionHelper.getFields(ClientboundSectionBlocksUpdatePacket.class).get(ReflectionMappingsInfo.ClientboundSectionBlocksUpdatePacket_sectionPos, SectionPos.class);
    public static Field OFFSETARRAY_MULTIBLOCKCHANGE = ReflectionHelper.getFields(ClientboundSectionBlocksUpdatePacket.class).get(ReflectionMappingsInfo.ClientboundSectionBlocksUpdatePacket_positions, short[].class);
    public static Field BLOCKARRAY_MULTIBLOCKCHANGE = ReflectionHelper.getFields(ClientboundSectionBlocksUpdatePacket.class).get(ReflectionMappingsInfo.ClientboundSectionBlocksUpdatePacket_states, BlockState[].class);

    public static Packet<ClientGamePacketListener> processLevelChunkWithLightPacket(DenizenNetworkManagerImpl networkManager, ClientboundLevelChunkWithLightPacket chunkPacket) {
        if (FakeBlock.blocks.isEmpty()) {
            return chunkPacket;
        }
        FakeBlock.FakeBlockMap map = FakeBlock.blocks.get(networkManager.player.getUUID());
        if (map == null) {
            return chunkPacket;
        }
        int chunkX = chunkPacket.getX();
        int chunkZ = chunkPacket.getZ();
        ChunkCoordinate chunkCoord = new ChunkCoordinate(chunkX, chunkZ, networkManager.player.level().getWorld().getName());
        Map<SectionCoordinate, List<FakeBlock>> blocksPerSection = map.byChunk.get(chunkCoord);
        if (blocksPerSection == null || blocksPerSection.isEmpty()) {
            return chunkPacket;
        }
        List<Packet<ClientGamePacketListener>> packets = new ArrayList<>(blocksPerSection.size() + 1);
        packets.add(chunkPacket);
        for (Map.Entry<SectionCoordinate, List<FakeBlock>> entry : blocksPerSection.entrySet()) {
            List<FakeBlock> fakeBlocks = entry.getValue();
            if (fakeBlocks.size() == 1) {
                FakeBlock block = fakeBlocks.get(0);
                packets.add(new ClientboundBlockUpdatePacket(CraftLocation.toBlockPosition(block.location), getNMSState(block)));
                continue;
            }
            short[] positionOffsets = new short[fakeBlocks.size()];
            BlockState[] states = new BlockState[fakeBlocks.size()];
            int i = 0;
            for (FakeBlock fakeBlock : fakeBlocks) {
                positionOffsets[i] = SectionPos.sectionRelativePos(CraftLocation.toBlockPosition(fakeBlock.location));
                states[i] = getNMSState(fakeBlock);
                i++;
            }
            SectionCoordinate section = entry.getKey();
            packets.add(new ClientboundSectionBlocksUpdatePacket(SectionPos.of(section.x(), section.y(), section.z()), new ShortArraySet(positionOffsets), states));
        }
        return new ClientboundBundlePacket(packets);
    }

    public static ClientboundSectionBlocksUpdatePacket processSectionBlocksUpdatePacket(DenizenNetworkManagerImpl networkManager, ClientboundSectionBlocksUpdatePacket sectionUpdatePacket) throws Exception {
        if (FakeBlock.blocks.isEmpty()) {
            return sectionUpdatePacket;
        }
        FakeBlock.FakeBlockMap map = FakeBlock.blocks.get(networkManager.player.getUUID());
        if (map == null) {
            return sectionUpdatePacket;
        }
        SectionPos coord = (SectionPos) SECTIONPOS_MULTIBLOCKCHANGE.get(sectionUpdatePacket);
        ChunkCoordinate coordinateDenizen = new ChunkCoordinate(coord.getX(), coord.getZ(), networkManager.player.level().getWorld().getName());
        if (!map.byChunk.containsKey(coordinateDenizen)) {
            return sectionUpdatePacket;
        }
        short[] originalOffsetArray = (short[]) OFFSETARRAY_MULTIBLOCKCHANGE.get(sectionUpdatePacket);
        BlockState[] originalDataArray = (BlockState[]) BLOCKARRAY_MULTIBLOCKCHANGE.get(sectionUpdatePacket);
        BlockState[] dataArray = Arrays.copyOf(originalDataArray, originalDataArray.length);
        LocationTag location = new LocationTag(networkManager.player.level().getWorld(), 0, 0, 0);
        for (int i = 0; i < originalOffsetArray.length; i++) {
            short offset = originalOffsetArray[i];
            BlockPos pos = coord.relativeToBlockPos(offset);
            location.setX(pos.getX());
            location.setY(pos.getY());
            location.setZ(pos.getZ());
            FakeBlock block = map.byLocation.get(location);
            if (block != null) {
                dataArray[i] = getNMSState(block);
            }
        }
        return new ClientboundSectionBlocksUpdatePacket(coord, new ShortArraySet(originalOffsetArray), dataArray);
    }

    public static ClientboundBlockUpdatePacket processBlockUpdatePacket(DenizenNetworkManagerImpl networkManager, ClientboundBlockUpdatePacket blockUpdatePacket) {
        if (FakeBlock.blocks.isEmpty()) {
            return blockUpdatePacket;
        }
        BlockPos pos = blockUpdatePacket.getPos();
        LocationTag loc = new LocationTag(networkManager.player.level().getWorld(), pos.getX(), pos.getY(), pos.getZ());
        FakeBlock block = FakeBlock.getFakeBlockFor(networkManager.player.getUUID(), loc);
        if (block != null) {
            return new ClientboundBlockUpdatePacket(blockUpdatePacket.getPos(), getNMSState(block));
        }
        return blockUpdatePacket;
    }

// TODO: 1.19: Can no longer determine what block this packet is for. Would have to track separately? Possibly from the inbound packet rather than the outbound one.
/*
    public static ClientboundBlockChangedAckPacket processBlockChangedAckPacket(DenizenNetworkManagerImpl networkManager, ClientboundBlockChangedAckPacket blockChangedAckPacket) {
        if (FakeBlock.blocks.isEmpty()) {
            return packet;
        }
        ClientboundBlockChangedAckPacket origPack = (ClientboundBlockChangedAckPacket) packet;
        BlockPos pos = origPack.pos();
        LocationTag loc = new LocationTag(player.getLevel().getWorld(), pos.getX(), pos.getY(), pos.getZ());
        FakeBlock block = FakeBlock.getFakeBlockFor(player.getUUID(), loc);
        if (block != null) {
            ClientboundBlockChangedAckPacket newPacket = new ClientboundBlockChangedAckPacket(origPack.pos(), FakeBlockHelper.getNMSState(block), origPack.action(), false);
            oldManager.send(newPacket, genericfuturelistener);
            return true;
        }
    }
 */
}
