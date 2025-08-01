package com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_6;

import com.fastasyncworldedit.bukkit.adapter.AbstractBukkitGetBlocks;
import com.fastasyncworldedit.bukkit.adapter.DelegateSemaphore;
import com.fastasyncworldedit.bukkit.adapter.NativeEntityFunctionSet;
import com.fastasyncworldedit.core.FaweCache;
import com.fastasyncworldedit.core.configuration.Settings;
import com.fastasyncworldedit.core.extent.processor.heightmap.HeightMapType;
import com.fastasyncworldedit.core.internal.exception.FaweException;
import com.fastasyncworldedit.core.math.BitArrayUnstretched;
import com.fastasyncworldedit.core.math.IntPair;
import com.fastasyncworldedit.core.nbt.FaweCompoundTag;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.fastasyncworldedit.core.util.MathMan;
import com.fastasyncworldedit.core.util.NbtUtils;
import com.fastasyncworldedit.core.util.collection.AdaptedMap;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitEntity;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.internal.Constants;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import io.papermc.lib.PaperLib;
import io.papermc.paper.event.block.BeaconDeactivatedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.apache.logging.log4j.Logger;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinDoubleTag;
import org.enginehub.linbus.tree.LinFloatTag;
import org.enginehub.linbus.tree.LinListTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_6.PaperweightPlatformAdapter.createInput;
import static com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_6.PaperweightPlatformAdapter.createOutput;
import static net.minecraft.core.registries.Registries.BIOME;

public class PaperweightGetBlocks extends AbstractBukkitGetBlocks<ServerLevel, LevelChunk> {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    private static final Function<BlockPos, BlockVector3> posNms2We = v -> BlockVector3.at(v.getX(), v.getY(), v.getZ());
    public static final Function<BlockEntity, FaweCompoundTag> NMS_TO_TILE = ((PaperweightFaweAdapter) WorldEditPlugin
            .getInstance()
            .getBukkitImplAdapter()).blockEntityToCompoundTag();
    private final PaperweightFaweAdapter adapter = ((PaperweightFaweAdapter) WorldEditPlugin
            .getInstance()
            .getBukkitImplAdapter());
    private final ReadWriteLock sectionLock = new ReentrantReadWriteLock();
    private final Registry<Biome> biomeRegistry;
    private final IdMap<Holder<Biome>> biomeHolderIdMap;
    private final Object sendLock = new Object();
    private LevelChunk levelChunk;
    private LevelChunkSection[] sections;
    private DataLayer[] blockLight;
    private DataLayer[] skyLight;
    private boolean lightUpdate = false;

    public PaperweightGetBlocks(World world, int chunkX, int chunkZ) {
        this(((CraftWorld) world).getHandle(), chunkX, chunkZ);
    }

    public PaperweightGetBlocks(ServerLevel serverLevel, int chunkX, int chunkZ) {
        super(serverLevel, chunkX, chunkZ, serverLevel.getMinY(), serverLevel.getMaxY() - 1);
        this.skyLight = new DataLayer[getSectionCount()];
        this.blockLight = new DataLayer[getSectionCount()];
        this.biomeRegistry = serverLevel.registryAccess().lookupOrThrow(BIOME);
        this.biomeHolderIdMap = biomeRegistry.asHolderIdMap();
    }

    @Override
    public void setLightingToGet(char[][] light, int minSectionPosition, int maxSectionPosition) {
        if (light != null) {
            lightUpdate = true;
            try {
                fillLightNibble(light, LightLayer.BLOCK, minSectionPosition, maxSectionPosition);
            } catch (Throwable e) {
                LOGGER.error("Error setting lighting to get", e);
            }
        }
    }

    @Override
    public void setSkyLightingToGet(char[][] light, int minSectionPosition, int maxSectionPosition) {
        if (light != null) {
            lightUpdate = true;
            try {
                fillLightNibble(light, LightLayer.SKY, minSectionPosition, maxSectionPosition);
            } catch (Throwable e) {
                LOGGER.error("Error setting sky lighting to get", e);
            }
        }
    }

    @Override
    public void setHeightmapToGet(HeightMapType type, int[] data) {
        // height + 1 to match server internal
        BitArrayUnstretched bitArray = new BitArrayUnstretched(MathMan.log2nlz(getChunk().getHeight() + 1), 256);
        bitArray.fromRaw(data);
        Heightmap.Types nativeType = Heightmap.Types.valueOf(type.name());
        Heightmap heightMap = getChunk().heightmaps.get(nativeType);
        heightMap.setRawData(getChunk(), nativeType, bitArray.getData());
    }

    @Override
    public BiomeType getBiomeType(int x, int y, int z) {
        LevelChunkSection section = getSections(false)[(y >> 4) - getMinSectionPosition()];
        Holder<Biome> biomes = section.getNoiseBiome(x >> 2, (y & 15) >> 2, z >> 2);
        return PaperweightPlatformAdapter.adapt(biomes, serverLevel);
    }

    @Override
    public void removeSectionLighting(int layer, boolean sky) {
        SectionPos sectionPos = SectionPos.of(getChunk().getPos(), layer);
        DataLayer dataLayer = serverLevel.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(
                sectionPos);
        if (dataLayer != null) {
            lightUpdate = true;
            synchronized (dataLayer) {
                byte[] bytes = dataLayer.getData();
                Arrays.fill(bytes, (byte) 0);
            }
        }
        if (sky) {
            SectionPos sectionPos1 = SectionPos.of(getChunk().getPos(), layer);
            DataLayer dataLayer1 = serverLevel
                    .getChunkSource()
                    .getLightEngine()
                    .getLayerListener(LightLayer.SKY)
                    .getDataLayerData(sectionPos1);
            if (dataLayer1 != null) {
                lightUpdate = true;
                synchronized (dataLayer1) {
                    byte[] bytes = dataLayer1.getData();
                    Arrays.fill(bytes, (byte) 0);
                }
            }
        }
    }

    @Override
    public FaweCompoundTag tile(final int x, final int y, final int z) {
        BlockEntity blockEntity = getChunk().getBlockEntity(new BlockPos((x & 15) + (
                chunkX << 4), y, (z & 15) + (
                chunkZ << 4)));
        if (blockEntity == null) {
            return null;
        }
        return NMS_TO_TILE.apply(blockEntity);

    }

    @Override
    public Map<BlockVector3, FaweCompoundTag> tiles() {
        Map<BlockPos, BlockEntity> nmsTiles = getChunk().getBlockEntities();
        if (nmsTiles.isEmpty()) {
            return Collections.emptyMap();
        }
        return AdaptedMap.immutable(nmsTiles, posNms2We, NMS_TO_TILE);
    }

    @Override
    public int getSkyLight(int x, int y, int z) {
        int layer = y >> 4;
        int alayer = layer - getMinSectionPosition();
        if (skyLight[alayer] == null) {
            SectionPos sectionPos = SectionPos.of(getChunk().getPos(), layer);
            DataLayer dataLayer =
                    serverLevel.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
            // If the server hasn't generated the section's NibbleArray yet, it will be null
            if (dataLayer == null) {
                byte[] LAYER_COUNT = new byte[2048];
                // Safe enough to assume if it's not created, it's under the sky. Unlikely to be created before lighting is fixed anyway.
                Arrays.fill(LAYER_COUNT, (byte) 15);
                dataLayer = new DataLayer(LAYER_COUNT);
                ((LevelLightEngine) serverLevel.getChunkSource().getLightEngine()).queueSectionData(
                        LightLayer.BLOCK,
                        sectionPos,
                        dataLayer
                );
            }
            skyLight[alayer] = dataLayer;
        }
        return skyLight[alayer].get(x & 15, y & 15, z & 15);
    }

    @Override
    public int getEmittedLight(int x, int y, int z) {
        int layer = y >> 4;
        int alayer = layer - getMinSectionPosition();
        if (blockLight[alayer] == null) {
            serverLevel.getRawBrightness(new BlockPos(1, 1, 1), 5);
            SectionPos sectionPos = SectionPos.of(getChunk().getPos(), layer);
            DataLayer dataLayer = serverLevel
                    .getChunkSource()
                    .getLightEngine()
                    .getLayerListener(LightLayer.BLOCK)
                    .getDataLayerData(sectionPos);
            // If the server hasn't generated the section's DataLayer yet, it will be null
            if (dataLayer == null) {
                byte[] LAYER_COUNT = new byte[2048];
                // Safe enough to assume if it's not created, it's under the sky. Unlikely to be created before lighting is fixed anyway.
                Arrays.fill(LAYER_COUNT, (byte) 15);
                dataLayer = new DataLayer(LAYER_COUNT);
                ((LevelLightEngine) serverLevel.getChunkSource().getLightEngine()).queueSectionData(LightLayer.BLOCK, sectionPos,
                        dataLayer
                );
            }
            blockLight[alayer] = dataLayer;
        }
        return blockLight[alayer].get(x & 15, y & 15, z & 15);
    }

    @Override
    public int[] getHeightMap(HeightMapType type) {
        long[] longArray = getChunk().heightmaps.get(Heightmap.Types.valueOf(type.name())).getRawData();
        BitArrayUnstretched bitArray = new BitArrayUnstretched(9, 256, longArray);
        return bitArray.toRaw(new int[256]);
    }

    @Override
    public @Nullable FaweCompoundTag entity(final UUID uuid) {
        List<Entity> entities = PaperweightPlatformAdapter.getEntities(getChunk());
        Entity entity = null;
        for (Entity e : entities) {
            if (e.getUUID().equals(uuid)) {
                entity = e;
                break;
            }
        }
        if (entity != null) {
            org.bukkit.entity.Entity bukkitEnt = entity.getBukkitEntity();
            return FaweCompoundTag.of(BukkitAdapter.adapt(bukkitEnt).getState().getNbt());
        }
        for (FaweCompoundTag tag : entities()) {
            if (uuid.equals(NbtUtils.uuid(tag))) {
                return tag;
            }
        }
        return null;
    }

    @Override
    public Collection<FaweCompoundTag> entities() {
        List<Entity> entities = PaperweightPlatformAdapter.getEntities(getChunk());
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return new NativeEntityFunctionSet<>(entities, Entity::getUUID, e -> {
            // TODO (VI/O)
            TagValueOutput output = createOutput();
            e.save(output);
            return FaweCompoundTag.of(() -> (LinCompoundTag) adapter.toNativeLin(output.buildResult()));
        });
    }

    @Override
    public Set<com.sk89q.worldedit.entity.Entity> getFullEntities() {
        List<Entity> entities = PaperweightPlatformAdapter.getEntities(getChunk());
        if (entities.isEmpty()) {
            return Collections.emptySet();
        }
        return new NativeEntityFunctionSet<>(entities, Entity::getUUID, e -> new BukkitEntity(e.getBukkitEntity()));
    }

    private void removeEntity(Entity entity) {
        entity.discard();
    }

    @Override
    public CompletableFuture<LevelChunk> ensureLoaded(ServerLevel nmsWorld) {
        return PaperweightPlatformAdapter.ensureLoaded(nmsWorld, chunkX, chunkZ);
    }

    @Override
    protected <T extends Future<T>> T internalCall(
            IChunkSet set,
            Runnable finalizer,
            int copyKey,
            LevelChunk nmsChunk,
            ServerLevel nmsWorld
    ) throws Exception {
        PaperweightGetBlocks_Copy copy = createCopy ? new PaperweightGetBlocks_Copy(nmsChunk) : null;
        if (createCopy) {
            if (copies.containsKey(copyKey)) {
                throw new IllegalStateException("Copy key already used.");
                }
            copies.put(copyKey, copy);
        }
        // Remove existing tiles. Create a copy so that we can remove blocks
        Map<BlockPos, BlockEntity> chunkTiles = new HashMap<>(nmsChunk.getBlockEntities());
        List<BlockEntity> beacons = null;
        if (!chunkTiles.isEmpty()) {
            for (Map.Entry<BlockPos, BlockEntity> entry : chunkTiles.entrySet()) {
                final BlockPos pos = entry.getKey();
                final int lx = pos.getX() & 15;
                final int ly = pos.getY();
                final int lz = pos.getZ() & 15;
                final int layer = ly >> 4;
                if (!set.hasSection(layer)) {
                    continue;
                }

                int ordinal = set.getBlock(lx, ly, lz).getOrdinal();
                if (ordinal != BlockTypesCache.ReservedIDs.__RESERVED__) {
                    BlockEntity tile = entry.getValue();
                    if (PaperLib.isPaper() && tile instanceof BeaconBlockEntity) {
                        if (beacons == null) {
                            beacons = new ArrayList<>();
                        }
                        beacons.add(tile);
                        PaperweightPlatformAdapter.removeBeacon(tile, nmsChunk);
                        continue;
                    }
                    nmsChunk.removeBlockEntity(tile.getBlockPos());
                    if (createCopy) {
                        copy.storeTile(tile);
                    }
                }
            }
        }
        final BiomeType[][] biomes = set.getBiomes();

        int bitMask = 0;
        synchronized (nmsChunk) {
            LevelChunkSection[] levelChunkSections = nmsChunk.getSections();

            for (int layerNo = getMinSectionPosition(); layerNo <= getMaxSectionPosition(); layerNo++) {

                int getSectionIndex = layerNo - getMinSectionPosition();
                int setSectionIndex = layerNo - set.getMinSectionPosition();

                if (!set.hasSection(layerNo)) {
                    // No blocks, but might be biomes present. Handle this lazily.
                    if (biomes == null) {
                        continue;
                    }
                    if (layerNo < set.getMinSectionPosition() || layerNo > set.getMaxSectionPosition()) {
                        continue;
                    }
                    if (biomes[setSectionIndex] != null) {
                        synchronized (super.sectionLocks[getSectionIndex]) {
                            LevelChunkSection existingSection = levelChunkSections[getSectionIndex];
                            if (createCopy && existingSection != null) {
                                copy.storeBiomes(getSectionIndex, existingSection.getBiomes());
                            }

                            if (existingSection == null) {
                                PalettedContainer<Holder<Biome>> biomeData = PaperweightPlatformAdapter.getBiomePalettedContainer(
                                        biomes[setSectionIndex],
                                        biomeHolderIdMap
                                );
                                LevelChunkSection newSection = PaperweightPlatformAdapter.newChunkSection(
                                        layerNo,
                                        new int[4096],
                                        adapter,
                                        biomeRegistry,
                                        biomeData
                                );
                                if (PaperweightPlatformAdapter.setSectionAtomic(
                                        nmsWorld.getWorld().getName(),
                                        chunkPos,
                                        levelChunkSections,
                                        null,
                                        newSection,
                                        getSectionIndex
                                )) {
                                    updateGet(nmsChunk, levelChunkSections, newSection, new int[4096], getSectionIndex);
                                    continue;
                                } else {
                                    existingSection = levelChunkSections[getSectionIndex];
                                    if (existingSection == null) {
                                        LOGGER.error("Skipping invalid null section. chunk: {}, {} layer: {}", chunkX, chunkZ,
                                                getSectionIndex
                                        );
                                        continue;
                                    }
                                }
                            } else {
                                PalettedContainer<Holder<Biome>> paletteBiomes = setBiomesToPalettedContainer(
                                        biomes,
                                        setSectionIndex,
                                        existingSection.getBiomes()
                                );
                                if (paletteBiomes != null) {
                                    PaperweightPlatformAdapter.setBiomesToChunkSection(existingSection, paletteBiomes);
                                }
                            }
                        }
                    }
                    continue;
                }

                bitMask |= 1 << getSectionIndex;

                // setArr is modified by PaperweightPlatformAdapter#newChunkSection. This is in order to write changes to
                // this chunk GET when #updateGet is called. Future dords, please listen this time.
                int[] tmp = set.load(layerNo);
                int[] setArr = new int[tmp.length];
                System.arraycopy(tmp, 0, setArr, 0, tmp.length);

                // synchronise on internal section to avoid circular locking with a continuing edit if the chunk was
                // submitted to keep loaded internal chunks to queue target size.
                synchronized (super.sectionLocks[getSectionIndex]) {

                    LevelChunkSection newSection;
                    LevelChunkSection existingSection = levelChunkSections[getSectionIndex];
                    // Don't attempt to tick section whilst we're editing
                    if (existingSection != null) {
                        PaperweightPlatformAdapter.clearCounts(existingSection);
                    }

                    if (createCopy) {
                        int[] tmpLoad = load(layerNo);
                        int[] copyArr = new int[4096];
                        System.arraycopy(tmpLoad, 0, copyArr, 0, 4096);
                        copy.storeSection(getSectionIndex, copyArr);
                        if (biomes != null && existingSection != null) {
                            copy.storeBiomes(getSectionIndex, existingSection.getBiomes());
                        }
                    }

                    if (existingSection == null) {
                        PalettedContainer<Holder<Biome>> biomeData = biomes == null ? new PalettedContainer<>(
                                biomeHolderIdMap,
                                biomeHolderIdMap.byIdOrThrow(adapter.getInternalBiomeId(BiomeTypes.PLAINS)),
                                PalettedContainer.Strategy.SECTION_BIOMES
                        ) : PaperweightPlatformAdapter.getBiomePalettedContainer(biomes[setSectionIndex], biomeHolderIdMap);
                        newSection = PaperweightPlatformAdapter.newChunkSection(
                                layerNo,
                                setArr,
                                adapter,
                                biomeRegistry,
                                biomeData
                        );
                        if (PaperweightPlatformAdapter.setSectionAtomic(
                                nmsWorld.getWorld().getName(),
                                chunkPos,
                                levelChunkSections,
                                null,
                                newSection,
                                getSectionIndex
                        )) {
                            updateGet(nmsChunk, levelChunkSections, newSection, setArr, getSectionIndex);
                            continue;
                        } else {
                            existingSection = levelChunkSections[getSectionIndex];
                            if (existingSection == null) {
                                LOGGER.error("Skipping invalid null section. chunk: {}, {} layer: {}", chunkX, chunkZ,
                                        getSectionIndex
                                );
                                continue;
                            }
                        }
                    }

                    //ensure that the server doesn't try to tick the chunksection while we're editing it. (Again)
                    PaperweightPlatformAdapter.clearCounts(existingSection);
                    DelegateSemaphore lock = PaperweightPlatformAdapter.applyLock(existingSection);

                    // Synchronize to prevent further acquisitions
                    synchronized (lock) {
                        lock.acquire(); // Wait until we have the lock
                        lock.release();
                        try {
                            sectionLock.writeLock().lock();
                            if (this.getChunk() != nmsChunk) {
                                this.levelChunk = nmsChunk;
                                this.sections = null;
                                this.reset();
                            } else if (existingSection != getSections(false)[getSectionIndex]) {
                                this.sections[getSectionIndex] = existingSection;
                                this.reset();
                            } else if (!Arrays.equals(
                                    update(getSectionIndex, new int[4096], true),
                                    load(layerNo)
                            )) {
                                this.reset(layerNo);
                        /*} else if (lock.isModified()) {
                            this.reset(layerNo);*/
                            }
                        } finally {
                            sectionLock.writeLock().unlock();
                        }

                        PalettedContainer<Holder<Biome>> biomeData = setBiomesToPalettedContainer(
                                biomes,
                                setSectionIndex,
                                existingSection.getBiomes()
                        );

                        newSection = PaperweightPlatformAdapter.newChunkSection(
                                layerNo,
                                this::load,
                                setArr,
                                adapter,
                                biomeRegistry,
                                biomeData != null ? biomeData : (PalettedContainer<Holder<Biome>>) existingSection.getBiomes()
                        );
                        if (!PaperweightPlatformAdapter.setSectionAtomic(
                                nmsWorld.getWorld().getName(),
                                chunkPos,
                                levelChunkSections,
                                existingSection,
                                newSection,
                                getSectionIndex
                        )) {
                            LOGGER.error("Skipping invalid null section. chunk: {}, {} layer: {}", chunkX, chunkZ,
                                    getSectionIndex
                            );
                        } else {
                            updateGet(nmsChunk, levelChunkSections, newSection, setArr, getSectionIndex);
                        }
                    }
                }
            }

            Map<HeightMapType, int[]> heightMaps = set.getHeightMaps();
            for (Map.Entry<HeightMapType, int[]> entry : heightMaps.entrySet()) {
                PaperweightGetBlocks.this.setHeightmapToGet(entry.getKey(), entry.getValue());
            }
            PaperweightGetBlocks.this.setLightingToGet(
                    set.getLight(),
                    set.getMinSectionPosition(),
                    set.getMaxSectionPosition()
            );
            PaperweightGetBlocks.this.setSkyLightingToGet(
                    set.getSkyLight(),
                    set.getMinSectionPosition(),
                    set.getMaxSectionPosition()
            );

            Runnable[] syncTasks = null;

            int bx = chunkX << 4;
            int bz = chunkZ << 4;

            // Call beacon deactivate events here synchronously
            // list will be null on spigot, so this is an implicit isPaper check
            if (beacons != null && !beacons.isEmpty()) {
                final List<BlockEntity> finalBeacons = beacons;

                syncTasks = new Runnable[4];

                syncTasks[3] = () -> {
                    for (BlockEntity beacon : finalBeacons) {
                        BeaconBlockEntity.playSound(beacon.getLevel(), beacon.getBlockPos(), SoundEvents.BEACON_DEACTIVATE);
                        new BeaconDeactivatedEvent(CraftBlock.at(beacon.getLevel(), beacon.getBlockPos())).callEvent();
                    }
                };
            }

            Set<UUID> entityRemoves = set.getEntityRemoves();
            if (entityRemoves != null && !entityRemoves.isEmpty()) {
                if (syncTasks == null) {
                    syncTasks = new Runnable[3];
                }

                syncTasks[2] = () -> {
                    Set<UUID> entitiesRemoved = new HashSet<>();
                    final List<Entity> entities = PaperweightPlatformAdapter.getEntities(nmsChunk);

                    for (Entity entity : entities) {
                        UUID uuid = entity.getUUID();
                        if (entityRemoves.contains(uuid)) {
                            if (createCopy) {
                                copy.storeEntity(entity);
                            }
                            removeEntity(entity);
                            entitiesRemoved.add(uuid);
                            entityRemoves.remove(uuid);
                        }
                    }
                    if (Settings.settings().EXPERIMENTAL.REMOVE_ENTITY_FROM_WORLD_ON_CHUNK_FAIL) {
                        for (UUID uuid : entityRemoves) {
                            Entity entity = nmsWorld.getEntities().get(uuid);
                            if (entity != null) {
                                removeEntity(entity);
                            }
                        }
                    }
                    // Only save entities that were actually removed to history
                    set.getEntityRemoves().clear();
                    set.getEntityRemoves().addAll(entitiesRemoved);
                };
            }

            Collection<FaweCompoundTag> entities = set.entities();
            if (entities != null && !entities.isEmpty()) {
                if (syncTasks == null) {
                    syncTasks = new Runnable[2];
                }

                syncTasks[1] = () -> {
                    Iterator<FaweCompoundTag> iterator = entities.iterator();
                    while (iterator.hasNext()) {
                        final FaweCompoundTag nativeTag = iterator.next();
                        final LinCompoundTag linTag = nativeTag.linTag();
                        final LinStringTag idTag = linTag.findTag("Id", LinTagType.stringTag());
                        final LinListTag<LinDoubleTag> posTag = linTag.findListTag("Pos", LinTagType.doubleTag());
                        final LinListTag<LinFloatTag> rotTag = linTag.findListTag("Rotation", LinTagType.floatTag());
                        if (idTag == null || posTag == null || rotTag == null) {
                            LOGGER.error("Unknown entity tag: {}", nativeTag);
                            continue;
                        }
                        final double x = posTag.get(0).valueAsDouble();
                        final double y = posTag.get(1).valueAsDouble();
                        final double z = posTag.get(2).valueAsDouble();
                        final float yaw = rotTag.get(0).valueAsFloat();
                        final float pitch = rotTag.get(1).valueAsFloat();
                        final String id = idTag.value();

                        EntityType<?> type = EntityType.byString(id).orElse(null);
                        if (type != null) {
                            Entity entity = type.create(nmsWorld, EntitySpawnReason.COMMAND);
                            if (entity != null) {
                                final CompoundTag tag = (CompoundTag) adapter.fromNativeLin(linTag);
                                for (final String name : Constants.NO_COPY_ENTITY_NBT_FIELDS) {
                                    tag.remove(name);
                                }
                                // TODO (VI/O)
                                ValueInput input = createInput(tag);
                                entity.load(input);
                                entity.absSnapTo(x, y, z, yaw, pitch);
                                entity.setUUID(NbtUtils.uuid(nativeTag));
                                if (!nmsWorld.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
                                    LOGGER.warn(
                                            "Error creating entity of type `{}` in world `{}` at location `{},{},{}`",
                                            id,
                                            nmsWorld.getWorld().getName(),
                                            x,
                                            y,
                                            z
                                    );
                                    // Unsuccessful create should not be saved to history
                                    iterator.remove();
                                }
                            }
                        }
                    }
                };
            }

            // set tiles
            Map<BlockVector3, FaweCompoundTag> tiles = set.tiles();
            if (tiles != null && !tiles.isEmpty()) {
                if (syncTasks == null) {
                    syncTasks = new Runnable[1];
                }

                syncTasks[0] = () -> {
                    for (final Map.Entry<BlockVector3, FaweCompoundTag> entry : tiles.entrySet()) {
                        final FaweCompoundTag nativeTag = entry.getValue();
                        final BlockVector3 blockHash = entry.getKey();
                        final int x = blockHash.x() + bx;
                        final int y = blockHash.y();
                        final int z = blockHash.z() + bz;
                        final BlockPos pos = new BlockPos(x, y, z);

                        synchronized (nmsWorld) {
                            BlockEntity tileEntity = nmsWorld.getBlockEntity(pos);
                            if (tileEntity == null || tileEntity.isRemoved()) {
                                nmsWorld.removeBlockEntity(pos);
                                tileEntity = nmsWorld.getBlockEntity(pos);
                            }
                            if (tileEntity != null) {
                                final CompoundTag tag = (CompoundTag) adapter.fromNativeLin(nativeTag.linTag());
                                tag.put("x", IntTag.valueOf(x));
                                tag.put("y", IntTag.valueOf(y));
                                tag.put("z", IntTag.valueOf(z));
                                // TODO (VI/O)
                                ValueInput input = createInput(tag);
                                tileEntity.loadWithComponents(input);
                            }
                        }
                    }
                };
            }

            Runnable callback;
            if (bitMask == 0 && biomes == null && !lightUpdate) {
                callback = null;
            } else {
                int finalMask = bitMask != 0 ? bitMask : lightUpdate ? set.getBitMask() : 0;
                callback = () -> {
                    // Set Modified
                    nmsChunk.setLightCorrect(true); // Set Modified
                    nmsChunk.mustNotSave = false;
                    nmsChunk.markUnsaved();
                    // send to player
                    if (!set
                            .getSideEffectSet()
                            .shouldApply(SideEffect.LIGHTING) || !Settings.settings().LIGHTING.DELAY_PACKET_SENDING || finalMask == 0 && biomes != null) {
                        this.send();
                    }
                    if (finalizer != null) {
                        finalizer.run();
                    }
                };
            }
            return handleCallFinalizer(syncTasks, callback, finalizer);
        }
    }

    private void updateGet(
            LevelChunk nmsChunk,
            LevelChunkSection[] chunkSections,
            LevelChunkSection section,
            int[] arr,
            int layer
    ) {
        try {
            sectionLock.writeLock().lock();
            if (this.getChunk() != nmsChunk) {
                this.levelChunk = nmsChunk;
                this.sections = new LevelChunkSection[chunkSections.length];
                System.arraycopy(chunkSections, 0, this.sections, 0, chunkSections.length);
                this.reset();
            }
            if (this.sections == null) {
                this.sections = new LevelChunkSection[chunkSections.length];
                System.arraycopy(chunkSections, 0, this.sections, 0, chunkSections.length);
            }
            if (this.sections[layer] != section) {
                // Not sure why it's funky, but it's what I did in commit fda7d00747abe97d7891b80ed8bb88d97e1c70d1 and I don't want to touch it >dords
                this.sections[layer] = new LevelChunkSection[]{section}.clone()[0];
            }
        } finally {
            sectionLock.writeLock().unlock();
        }
        this.blocks[layer] = arr;
    }

    @Override
    public void send() {
        synchronized (sendLock) {
            PaperweightPlatformAdapter.sendChunk(new IntPair(chunkX, chunkZ), serverLevel, chunkX, chunkZ);
        }
    }

    /**
     * Update a given (nullable) data array to the current data stored in the server's chunk, associated with this
     * {@link PaperweightPlatformAdapter} instance. Not synchronised to the {@link PaperweightPlatformAdapter} instance as synchronisation
     * is handled where necessary in the method, and should otherwise be handled correctly by this method's caller.
     *
     * @param layer      layer index (0 may denote a negative layer in the world, e.g. at y=-32)
     * @param data       array to be updated/filled with data or null
     * @param aggressive if the cached section array should be re-acquired.
     * @return the given array to be filled with data, or a new array if null is given.
     */
    @Override
    @SuppressWarnings("unchecked")
    public int[] update(int layer, int[] data, boolean aggressive) {
        LevelChunkSection section = getSections(aggressive)[layer];
        // Section is null, return empty array
        if (section == null) {
            data = new int[4096];
            Arrays.fill(data, BlockTypesCache.ReservedIDs.AIR);
            return data;
        }
        if (data == null || data == FaweCache.INSTANCE.EMPTY_INT_4096 || data.length != 4096) {
            data = new int[4096]; // new array, will be populated below
        }
        Semaphore lock = PaperweightPlatformAdapter.applyLock(section);
        synchronized (lock) {
            // Efficiently convert ChunkSection to raw data
            try {
                lock.acquire();

                final PalettedContainer<BlockState> blocks = section.getStates();
                final Object dataObject = PaperweightPlatformAdapter.fieldData.get(blocks);
                final BitStorage bits = (BitStorage) PaperweightPlatformAdapter.fieldStorage.get(dataObject);

                if (bits instanceof ZeroBitStorage) {
                    Arrays.fill(data, adapter.adaptToChar(blocks.get(0, 0, 0))); // get(int) is only public on paper
                    return data;
                }

                final Palette<BlockState> palette = (Palette<BlockState>) PaperweightPlatformAdapter.fieldPalette.get(dataObject);

                final int bitsPerEntry = bits.getBits();
                final long[] blockStates = bits.getRaw();

                new BitArrayUnstretched(bitsPerEntry, 4096, blockStates).toRaw(data);

                int num_palette;
                if (palette instanceof LinearPalette || palette instanceof HashMapPalette) {
                    num_palette = palette.getSize();
                } else {
                    // The section's palette is the global block palette.
                    adapter.mapFromGlobalPalette(data);
                    return data;
                }

                int[] paletteToOrdinal = FaweCache.INSTANCE.PALETTE_TO_BLOCK_CHAR.get();
                try {
                    if (num_palette == 1) {
                        int ordinal = ordinal(palette.valueFor(0), adapter);
                        Arrays.fill(data, ordinal);
                    } else {
                        for (int i = 0; i < num_palette; i++) {
                            int ordinal = ordinal(palette.valueFor(i), adapter);
                            paletteToOrdinal[i] = ordinal;
                        }
                        adapter.mapWithPalette(data, paletteToOrdinal);
                    }
                } finally {
                    Arrays.fill(paletteToOrdinal, 0, num_palette, Character.MAX_VALUE);
                }
                return data;
            } catch (IllegalAccessException | InterruptedException e) {
                LOGGER.error("Could not read block data from palette", e);
                throw new RuntimeException(e);
            } finally {
                lock.release();
            }
        }
    }

    private int ordinal(BlockState ibd, PaperweightFaweAdapter adapter) {
        if (ibd == null) {
            return BlockTypesCache.ReservedIDs.AIR;
        } else {
            return adapter.adaptToChar(ibd);
        }
    }

    public LevelChunkSection[] getSections(boolean force) {
        force &= forceLoadSections;
        LevelChunkSection[] tmp = sections;
        if (tmp == null || force) {
            try {
                sectionLock.writeLock().lock();
                tmp = sections;
                if (tmp == null || force) {
                    LevelChunkSection[] chunkSections = getChunk().getSections();
                    tmp = new LevelChunkSection[chunkSections.length];
                    System.arraycopy(chunkSections, 0, tmp, 0, chunkSections.length);
                    sections = tmp;
                }
            } finally {
                sectionLock.writeLock().unlock();
            }
        }
        return tmp;
    }

    public LevelChunk getChunk() {
        LevelChunk levelChunk = this.levelChunk;
        if (levelChunk == null) {
            synchronized (this) {
                levelChunk = this.levelChunk;
                if (levelChunk == null) {
                    try {
                        this.levelChunk = levelChunk = ensureLoaded(this.serverLevel).get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.error("Could not get chunk at {},{}", chunkX, chunkZ, e);
                        throw new FaweException(
                                TextComponent.of("Could not get chunk at " + chunkX + "," + chunkZ + ": " + e.getMessage()),
                                FaweException.Type.OTHER,
                                false
                        );
                    }
                }
            }
        }
        return levelChunk;
    }

    private void fillLightNibble(char[][] light, LightLayer lightLayer, int minSectionPosition, int maxSectionPosition) {
        for (int Y = 0; Y <= maxSectionPosition - minSectionPosition; Y++) {
            if (light[Y] == null) {
                continue;
            }
            SectionPos sectionPos = SectionPos.of(levelChunk.getPos(), Y + minSectionPosition);
            DataLayer dataLayer = serverLevel.getChunkSource().getLightEngine().getLayerListener(lightLayer).getDataLayerData(
                    sectionPos);
            if (dataLayer == null) {
                byte[] LAYER_COUNT = new byte[2048];
                Arrays.fill(LAYER_COUNT, lightLayer == LightLayer.SKY ? (byte) 15 : (byte) 0);
                dataLayer = new DataLayer(LAYER_COUNT);
                ((LevelLightEngine) serverLevel.getChunkSource().getLightEngine()).queueSectionData(
                        lightLayer,
                        sectionPos,
                        dataLayer
                );
            }
            synchronized (dataLayer) {
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            int i = y << 8 | z << 4 | x;
                            if (light[Y][i] < 16) {
                                dataLayer.set(x, y, z, light[Y][i]);
                            }
                        }
                    }
                }
            }
        }
    }

    private PalettedContainer<Holder<Biome>> setBiomesToPalettedContainer(
            final BiomeType[][] biomes,
            final int sectionIndex,
            final PalettedContainerRO<Holder<Biome>> data
    ) {
        BiomeType[] sectionBiomes;
        if (biomes == null || (sectionBiomes = biomes[sectionIndex]) == null) {
            return null;
        }
        PalettedContainer<Holder<Biome>> biomeData = data.recreate();
        for (int y = 0, index = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++, index++) {
                    BiomeType biomeType = sectionBiomes[index];
                    if (biomeType == null) {
                        biomeData.set(x, y, z, data.get(x, y, z));
                    } else {
                        biomeData.set(
                                x,
                                y,
                                z,
                                biomeHolderIdMap.byIdOrThrow(adapter.getInternalBiomeId(biomeType))
                        );
                    }
                }
            }
        }
        return biomeData;
    }

    @Override
    public boolean hasSection(int layer) {
        layer -= getMinSectionPosition();
        return getSections(false)[layer] != null;
    }

    @Override
    public boolean hasNonEmptySection(int layer) {
        layer -= getMinSectionPosition();
        LevelChunkSection section = getSections(false)[layer];
        return section != null && !section.hasOnlyAir();
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized boolean trim(boolean aggressive) {
        skyLight = new DataLayer[getSectionCount()];
        blockLight = new DataLayer[getSectionCount()];
        if (aggressive) {
            sectionLock.writeLock().lock();
            sections = null;
            levelChunk = null;
            sectionLock.writeLock().unlock();
            return super.trim(true);
        } else if (sections == null) {
            // don't bother trimming if there are no sections stored.
            return true;
        } else {
            for (int i = getMinSectionPosition(); i <= getMaxSectionPosition(); i++) {
                int layer = i - getMinSectionPosition();
                if (!hasSection(i) || super.blocks[layer] == null) {
                    continue;
                }
                LevelChunkSection existing = getSections(true)[layer];
                try {
                    final PalettedContainer<BlockState> blocksExisting = existing.getStates();

                    final Object dataObject = PaperweightPlatformAdapter.fieldData.get(blocksExisting);
                    final Palette<BlockState> palette = (Palette<BlockState>) PaperweightPlatformAdapter.fieldPalette.get(
                            dataObject);
                    int paletteSize;

                    if (palette instanceof LinearPalette || palette instanceof HashMapPalette) {
                        paletteSize = palette.getSize();
                    } else {
                        super.trim(false, i);
                        continue;
                    }
                    if (paletteSize == 1) {
                        //If the cached palette size is 1 then no blocks can have been changed i.e. do not need to update these chunks.
                        continue;
                    }
                    super.trim(false, i);
                } catch (IllegalAccessException ignored) {
                    super.trim(false, i);
                }
            }
            return true;
        }
    }

}
