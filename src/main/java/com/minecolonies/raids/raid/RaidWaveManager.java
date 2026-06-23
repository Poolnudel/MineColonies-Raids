package com.minecolonies.raids.raid;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.raids.integration.minecolonies.MineColoniesIntegration;
import com.minecolonies.raids.MineColoniesRaids;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RaidWaveManager {
    private static final int TEST_WAVE_SIZE = 5;
    private static final int REWARD_DEFENSE_POINTS = 100;
    private static final int MIN_DISTANCE_FROM_BUILDING = 32;
    private static final int BOSSBAR_PLAYER_RADIUS = 192;
    private static final String WAVE_ENTITY_TAG = MineColoniesRaids.MOD_ID + ".mvp_wave";
    private static final List<ActiveWave> ACTIVE_WAVES = new ArrayList<>();

    private RaidWaveManager() {
    }

    /**
     * Deprecated development entry point retained for the old Raid Command Block.
     */
    public static void startTestWaveFromPosition(final Player player, final ServerLevel level, final BlockPos originPos) {
        final Optional<IColony> colony = findColony(level, originPos);
        if (colony.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_colony").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        final BlockPos townHallPos = colony.get().getServerBuildingManager().getTownHall() == null
                ? originPos
                : colony.get().getServerBuildingManager().getTownHall().getPosition();
        startTestWave(player, level.dimension(), colony.get().getID(), townHallPos);
    }

    public static void startTestWave(final Player player, final ResourceKey<Level> dimension, final int colonyId, final BlockPos townHallPos) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimension().equals(dimension)) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_colony").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        final Optional<MineColoniesIntegration.TownHallContext> townHallContext = MineColoniesIntegration.getTownHallContext(level, townHallPos);
        if (townHallContext.isEmpty() || townHallContext.get().colonyId() != colonyId) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_colony").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        final Optional<IColony> colony = MineColoniesIntegration.getColony(level, colonyId);
        if (colony.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_colony").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        if (!MineColoniesIntegration.canManageColony(serverPlayer, colony.get())) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_permission").withStyle(ChatFormatting.RED), false);
            return;
        }

        if (hasActiveWave(level.dimension(), colonyId)) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.wave_active").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        final IColony resolvedColony = colony.get();
        final List<BlockPos> buildingPositions = getBuildingPositions(resolvedColony);
        final Optional<BlockPos> spawnPos = findColonyOuterSpawnPos(level, resolvedColony, townHallPos, buildingPositions);
        if (spawnPos.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_spawn").withStyle(ChatFormatting.RED), false);
            return;
        }

        final List<UUID> spawnedEnemies = new ArrayList<>();

        for (int i = 0; i < TEST_WAVE_SIZE; i++) {
            final BlockPos individualSpawnPos = findSurfaceSpawnPos(level, spawnPos.get().offset(i % 3 - 1, 0, i / 3)).orElse(spawnPos.get());
            final Pillager pillager = EntityType.PILLAGER.create(level);
            if (pillager == null) {
                continue;
            }

            pillager.moveTo(individualSpawnPos.getX() + 0.5D, individualSpawnPos.getY(), individualSpawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(individualSpawnPos), MobSpawnType.EVENT, null);
            pillager.addTag(WAVE_ENTITY_TAG);
            if (level.addFreshEntity(pillager)) {
                spawnedEnemies.add(pillager.getUUID());
            }
        }

        if (spawnedEnemies.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.spawn_failed").withStyle(ChatFormatting.RED), false);
            return;
        }

        final ActiveWave wave = new ActiveWave(
                level.dimension(),
                spawnedEnemies,
                serverPlayer.getUUID(),
                resolvedColony.getID(),
                resolvedColony.getCenter(),
                new ServerBossEvent(Component.literal("Raid Wave: " + spawnedEnemies.size() + " remaining"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));

        ACTIVE_WAVES.add(wave);
        updateBossBarPlayers(level, wave);
        wave.updateBossBar(level);
        serverPlayer.displayClientMessage(Component.literal("Raid wave started."), false);
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        if (ACTIVE_WAVES.isEmpty()) {
            return;
        }

        final MinecraftServer server = event.getServer();
        final Iterator<ActiveWave> iterator = ACTIVE_WAVES.iterator();
        while (iterator.hasNext()) {
            final ActiveWave wave = iterator.next();
            final ServerLevel level = server.getLevel(wave.dimension());
            if (level == null) {
                iterator.remove();
                wave.removeBossBar();
                continue;
            }

            updateBossBarPlayers(level, wave);
            wave.updateBossBar(level);

            if (wave.isComplete(level)) {
                iterator.remove();
                wave.removeBossBar();
                rewardWave(server, wave);
            }
        }
    }

    private static boolean hasActiveWave(final ResourceKey<Level> dimension, final int colonyId) {
        return ACTIVE_WAVES.stream().anyMatch(wave -> wave.dimension().equals(dimension) && wave.colonyId() == colonyId);
    }

    private static Optional<IColony> findColony(final ServerLevel level, final BlockPos commandBlockPos) {
        try {
            return Optional.ofNullable(IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, commandBlockPos));
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not resolve MineColonies colony for raid command block at {}", commandBlockPos, exception);
            return Optional.empty();
        }
    }

    private static List<BlockPos> getBuildingPositions(final IColony colony) {
        try {
            return colony.getServerBuildingManager().getBuildings().values().stream()
                    .map(IBuilding::getPosition)
                    .toList();
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not read MineColonies building positions for colony {}", colony.getID(), exception);
            return List.of();
        }
    }

    private static Optional<BlockPos> findColonyOuterSpawnPos(
            final ServerLevel level,
            final IColony colony,
            final BlockPos commandBlockPos,
            final Collection<BlockPos> buildingPositions) {
        final BlockPos center = colony.getCenter() == null ? commandBlockPos : colony.getCenter();
        final double colonyRadius = Math.max(48.0D, buildingPositions.stream()
                .mapToDouble(pos -> Math.sqrt(pos.distSqr(center)))
                .max()
                .orElse(48.0D));

        final double angle = level.random.nextDouble() * Math.PI * 2.0D;
        final double dirX = Math.cos(angle);
        final double dirZ = Math.sin(angle);

        for (int distance = (int) colonyRadius + 40; distance <= colonyRadius + 160; distance += 16) {
            final BlockPos candidateBase = new BlockPos(
                    center.getX() + (int) Math.round(dirX * distance),
                    center.getY(),
                    center.getZ() + (int) Math.round(dirZ * distance));

            final Optional<BlockPos> spawnPos = findSurfaceSpawnPos(level, candidateBase);
            if (spawnPos.isPresent() && isValidOuterSpawn(level, colony, spawnPos.get(), buildingPositions)) {
                return spawnPos;
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findSurfaceSpawnPos(final ServerLevel level, final BlockPos base) {
        final BlockPos.MutableBlockPos mutable = base.mutable();

        for (int y = Math.min(level.getMaxBuildHeight() - 2, base.getY() + 24); y > level.getMinBuildHeight() + 1; y--) {
            mutable.set(base.getX(), y, base.getZ());
            if (hasSolidGroundAndHeadroom(level, mutable)) {
                return Optional.of(mutable.immutable());
            }
        }

        return Optional.empty();
    }

    private static boolean isValidOuterSpawn(final ServerLevel level, final IColony colony, final BlockPos spawnPos, final Collection<BlockPos> buildingPositions) {
        final IColony colonyAtSpawn = findColony(level, spawnPos).orElse(null);
        if (colonyAtSpawn != null && colonyAtSpawn.getID() == colony.getID()) {
            return false;
        }

        for (final BlockPos buildingPos : buildingPositions) {
            if (buildingPos.distSqr(spawnPos) < MIN_DISTANCE_FROM_BUILDING * MIN_DISTANCE_FROM_BUILDING) {
                return false;
            }
        }

        return hasSolidGroundAndHeadroom(level, spawnPos);
    }

    private static boolean hasSolidGroundAndHeadroom(final ServerLevel level, final BlockPos pos) {
        final BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir();
    }

    private static void updateBossBarPlayers(final ServerLevel level, final ActiveWave wave) {
        final double maxDistanceSqr = BOSSBAR_PLAYER_RADIUS * BOSSBAR_PLAYER_RADIUS;
        for (final ServerPlayer player : level.players()) {
            final boolean shouldSeeBar = player.blockPosition().distSqr(wave.colonyCenter()) <= maxDistanceSqr;
            if (shouldSeeBar) {
                wave.bossBar().addPlayer(player);
            } else {
                wave.bossBar().removePlayer(player);
            }
        }
    }

    private static void rewardWave(final MinecraftServer server, final ActiveWave wave) {
        final ServerLevel overworld = server.overworld();
        DefensePointData.get(overworld).addDefensePoints(REWARD_DEFENSE_POINTS);

        final ServerPlayer player = server.getPlayerList().getPlayer(wave.owner());
        if (player != null) {
            player.displayClientMessage(Component.literal("Raid wave completed."), false);
            player.displayClientMessage(Component.literal("Reward: 100 Defense Points."), false);
        }
    }

    private record ActiveWave(ResourceKey<Level> dimension, List<UUID> enemies, UUID owner, int colonyId, BlockPos colonyCenter, ServerBossEvent bossBar) {
        private boolean isComplete(final ServerLevel level) {
            return remainingEnemies(level) <= 0;
        }

        private int remainingEnemies(final ServerLevel level) {
            int remaining = 0;
            for (final UUID enemyId : this.enemies) {
                final Entity entity = level.getEntity(enemyId);
                if (entity != null && entity.isAlive()) {
                    remaining++;
                }
            }

            return remaining;
        }

        private void updateBossBar(final ServerLevel level) {
            final int remaining = remainingEnemies(level);
            this.bossBar.setName(Component.literal("Raid Wave: " + remaining + " remaining"));
            this.bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, remaining / (float) this.enemies.size())));
        }

        private void removeBossBar() {
            this.bossBar.removeAllPlayers();
        }
    }
}
