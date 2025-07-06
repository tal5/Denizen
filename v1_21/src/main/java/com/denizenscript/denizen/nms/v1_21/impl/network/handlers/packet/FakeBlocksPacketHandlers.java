package com.denizenscript.denizen.nms.v1_21.impl.network.handlers.packet;

import com.denizenscript.denizen.nms.v1_21.ReflectionMappingsInfo;
import com.denizenscript.denizen.nms.v1_21.impl.network.handlers.DenizenNetworkManagerImpl;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.utilities.blocks.ChunkCoordinate;
import com.denizenscript.denizen.utilities.blocks.FakeBlock;
import com.denizenscript.denizen.utilities.blocks.SectionCoordinate;
import com.denizenscript.denizencore.utilities.ReflectionHelper;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R5.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_21_R5.util.CraftLocation;

import java.lang.invoke.MethodHandle;
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

    public static MethodHandle SECTION_BLOCKS_UPDATE_SECTION = ReflectionHelper.getFields(ClientboundSectionBlocksUpdatePacket.class).getGetter(ReflectionMappingsInfo.ClientboundSectionBlocksUpdatePacket_sectionPos, SectionPos.class);
    public static MethodHandle SECTION_BLOCKS_UPDATE_POSITIONS = ReflectionHelper.getFields(ClientboundSectionBlocksUpdatePacket.class).getGetter(ReflectionMappingsInfo.ClientboundSectionBlocksUpdatePacket_positions, short[].class);
    public static MethodHandle SECTION_BLOCKS_UPDATE_STATES = ReflectionHelper.getFields(ClientboundSectionBlocksUpdatePacket.class).getGetter(ReflectionMappingsInfo.ClientboundSectionBlocksUpdatePacket_states, BlockState[].class);

    public static Packet<ClientGamePacketListener> processLevelChunkWithLightPacket(DenizenNetworkManagerImpl networkManager, ClientboundLevelChunkWithLightPacket chunkPacket) {
        if (FakeBlock.blocks.isEmpty()) {
            return chunkPacket;
        }
        FakeBlock.FakeBlockMap map = FakeBlock.blocks.get(networkManager.player.getUUID());
        if (map == null) {
            return chunkPacket;
        }
        ChunkCoordinate chunkCoord = new ChunkCoordinate(chunkPacket.getX(), chunkPacket.getZ(), networkManager.player.level().getWorld().getName());
        Map<SectionCoordinate, List<FakeBlock>> blocksBySection = map.byChunk.get(chunkCoord);
        if (blocksBySection == null || blocksBySection.isEmpty()) {
            return chunkPacket;
        }
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(blocksBySection.size() + 1);
        packets.add(chunkPacket);
        BlockPos.MutableBlockPos nmsMutablePos = new BlockPos.MutableBlockPos();
        for (Map.Entry<SectionCoordinate, List<FakeBlock>> sectionEntry : blocksBySection.entrySet()) {
            List<FakeBlock> blocksInSection = sectionEntry.getValue();
            if (blocksInSection.size() == 1) {
                FakeBlock singleBlock = blocksInSection.getFirst();
                packets.add(new ClientboundBlockUpdatePacket(CraftLocation.toBlockPosition(singleBlock.location), getNMSState(singleBlock)));
                continue;
            }
            short[] inSectionOffsets = new short[blocksInSection.size()];
            BlockState[] sectionBlocks = new BlockState[blocksInSection.size()];
            for (int i = 0; i < blocksInSection.size(); i++) {
                FakeBlock blockInSection = blocksInSection.get(i);
                Location blockLocation = blockInSection.location;
                nmsMutablePos.set(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());
                inSectionOffsets[i] = SectionPos.sectionRelativePos(nmsMutablePos);
                sectionBlocks[i] = getNMSState(blockInSection);
            }
            SectionCoordinate section = sectionEntry.getKey();
            packets.add(new ClientboundSectionBlocksUpdatePacket(SectionPos.of(section.x(), section.y(), section.z()), new ShortArraySet(inSectionOffsets), sectionBlocks));
        }
        return new ClientboundBundlePacket(packets);
    }

    public static ClientboundSectionBlocksUpdatePacket processSectionBlocksUpdatePacket(DenizenNetworkManagerImpl networkManager, ClientboundSectionBlocksUpdatePacket sectionUpdatePacket) throws Throwable {
        if (FakeBlock.blocks.isEmpty()) {
            return sectionUpdatePacket;
        }
        FakeBlock.FakeBlockMap map = FakeBlock.blocks.get(networkManager.player.getUUID());
        if (map == null) {
            return sectionUpdatePacket;
        }
        SectionPos coord = (SectionPos) SECTION_BLOCKS_UPDATE_SECTION.invokeExact(sectionUpdatePacket);
        ChunkCoordinate coordinateDenizen = new ChunkCoordinate(coord.getX(), coord.getZ(), networkManager.player.level().getWorld().getName());
        if (!map.byChunk.containsKey(coordinateDenizen)) {
            return sectionUpdatePacket;
        }
        short[] originalOffsetArray = (short[]) SECTION_BLOCKS_UPDATE_POSITIONS.invokeExact(sectionUpdatePacket);
        BlockState[] originalDataArray = (BlockState[]) SECTION_BLOCKS_UPDATE_STATES.invokeExact(sectionUpdatePacket);
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
            return new ClientboundBlockUpdatePacket(pos, getNMSState(block));
        }
        return blockUpdatePacket;
    }

    // TODO: 1.19: Can no longer determine what block this packet is for. Would have to track separately? Possibly from the inbound packet rather than the outbound one.
    /*
    public static ClientboundBlockChangedAckPacket processBlockChangedAckPacket(DenizenNetworkManagerImpl networkManager, ClientboundBlockChangedAckPacket blockChangedAckPacket) {
        if (FakeBlock.blocks.isEmpty()) {
            return blockChangedAckPacket;
        }
        BlockPos pos = blockChangedAckPacket.pos();
        LocationTag loc = new LocationTag(player.getLevel().getWorld(), pos.getX(), pos.getY(), pos.getZ());
        FakeBlock block = FakeBlock.getFakeBlockFor(player.getUUID(), loc);
        if (block != null) {
            ClientboundBlockChangedAckPacket newPacket = new ClientboundBlockChangedAckPacket(blockChangedAckPacket.pos(), FakeBlockHelper.getNMSState(block), blockChangedAckPacket.action(), false);
            oldManager.send(newPacket, genericfuturelistener);
            return true;
        }
    }
    */
}
