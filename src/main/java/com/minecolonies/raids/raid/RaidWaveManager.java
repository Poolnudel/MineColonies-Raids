package com.minecolonies.raids.raid;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.raids.integration.minecolonies.ColonyRaidAnalyzer;
import com.minecolonies.raids.integration.minecolonies.MineColoniesIntegration;
import com.minecolonies.raids.MineColoniesRaids;
import com.minecolonies.raids.menu.SiegeDefenseViewData;
import com.minecolonies.raids.raid.spawn.RaidSpawnPlan;
import com.minecolonies.raids.raid.spawn.RaidSpawnPlanner;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RaidWaveManager {
    private static final int BASE_REWARD_DEFENSE_POINTS = 100;
    private static final int BOSSBAR_PLAYER_RADIUS = 192;
    private static final int DEVELOPMENT_GLOWING_TICKS = 20 * 20;
    private static final boolean DIAGNOSTIC_LOGGING = true;
    private static final boolean DEVELOPMENT_SPAWN_CHAT = true;
    private static final boolean DEVELOPMENT_GLOWING = true;
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
        startNextWave(player, level.dimension(), colony.get().getID(), townHallPos);
    }

    public static void startRepeatLastCompletedWave(final Player player, final ResourceKey<Level> dimension, final int colonyId, final BlockPos townHallPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        final ColonyRaidSavedData.Progress progress = ColonyRaidSavedData.get(level).getProgress(dimension, colonyId);
        if (progress.lastCompletedWave() <= 0) {
            player.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.repeat_unavailable").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        startWave(player, dimension, colonyId, townHallPos, progress.lastCompletedWave(), false);
    }

    public static void startNextWave(final Player player, final ResourceKey<Level> dimension, final int colonyId, final BlockPos townHallPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        final ColonyRaidSavedData.Progress progress = ColonyRaidSavedData.get(level).getProgress(dimension, colonyId);
        if (progress.highestCompletedWave() >= WaveCalculator.MAX_WAVE) {
            player.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.all_complete").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        final int nextWave = Math.min(progress.highestUnlockedWave(), progress.highestCompletedWave() + 1);
        startWave(player, dimension, colonyId, townHallPos, nextWave, true);
    }

    public static SiegeDefenseViewData createViewData(final ServerLevel level, final int colonyId) {
        final ColonyRaidSavedData.Progress progress = ColonyRaidSavedData.get(level).getProgress(level.dimension(), colonyId);
        final Optional<IColony> colony = MineColoniesIntegration.getColony(level, colonyId);
        if (colony.isEmpty()) {
            return SiegeDefenseViewData.EMPTY;
        }

        final ColonyRaidSnapshot snapshot = ColonyRaidAnalyzer.createSnapshot(colony.get());
        final WaveCalculation repeatCalculation = progress.lastCompletedWave() > 0
                ? WaveCalculator.calculate(snapshot, progress.lastCompletedWave())
                : null;
        final WaveCalculation nextCalculation = progress.highestCompletedWave() >= WaveCalculator.MAX_WAVE
                ? null
                : WaveCalculator.calculate(snapshot, Math.min(progress.highestUnlockedWave(), progress.highestCompletedWave() + 1));
        final Optional<ActiveWaveInfo> activeWaveInfo = getActiveWaveInfo(level, colonyId);

        return SiegeDefenseViewData.from(
                colonyId,
                progress,
                activeWaveInfo.isPresent(),
                activeWaveInfo.map(ActiveWaveInfo::waveNumber).orElse(0),
                activeWaveInfo.map(ActiveWaveInfo::remainingEnemies).orElse(0),
                repeatCalculation,
                nextCalculation);
    }

    private static void startWave(final Player player, final ResourceKey<Level> dimension, final int colonyId, final BlockPos townHallPos, final int selectedWave, final boolean progressionWave) {
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
        final ColonyRaidSavedData savedData = ColonyRaidSavedData.get(level);
        final ColonyRaidSavedData.Progress progress = savedData.getProgress(level.dimension(), colonyId);
        if (progressionWave && selectedWave > progress.highestUnlockedWave()) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.wave_locked").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        final ColonyRaidSnapshot snapshot = ColonyRaidAnalyzer.createSnapshot(resolvedColony);
        final WaveCalculation calculation = WaveCalculator.calculate(snapshot, selectedWave);
        logCalculation(calculation);

        final List<BlockPos> buildingPositions = getBuildingPositions(resolvedColony);
        final Optional<RaidSpawnPlan> spawnPlan = RaidSpawnPlanner.createPlan(level, resolvedColony, serverPlayer, townHallPos, buildingPositions, calculation.enemyCount());
        if (spawnPlan.isEmpty() || !spawnPlan.get().hasSpawns()) {
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.no_spawn").withStyle(ChatFormatting.RED), false);
            return;
        }

        if (DEVELOPMENT_SPAWN_CHAT) {
            serverPlayer.displayClientMessage(Component.literal("Raid debug: selected surface spawn anchor " + formatPos(spawnPlan.get().anchor())), false);
        }

        final WaveDiagnostics diagnostics = new WaveDiagnostics(calculation.enemyCount());
        final List<TrackedEnemy> spawnedEnemies = new ArrayList<>();

        for (final BlockPos individualSpawnPos : spawnPlan.get().positions()) {
            diagnostics.attempted++;
            final Pillager pillager = EntityType.PILLAGER.create(level);
            if (pillager == null) {
                logEntitySpawnResult(calculation, diagnostics.attempted, individualSpawnPos, null, false, "EntityType.PILLAGER.create returned null");
                continue;
            }

            pillager.moveTo(individualSpawnPos.getX() + 0.5D, individualSpawnPos.getY(), individualSpawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(individualSpawnPos), MobSpawnType.EVENT, null);
            pillager.setPersistenceRequired();
            pillager.addTag(WAVE_ENTITY_TAG);
            if (DEVELOPMENT_GLOWING) {
                pillager.addEffect(new MobEffectInstance(MobEffects.GLOWING, DEVELOPMENT_GLOWING_TICKS, 0, false, false));
            }

            if (level.addFreshEntity(pillager)) {
                spawnedEnemies.add(new TrackedEnemy(pillager.getUUID(), individualSpawnPos));
                diagnostics.spawned++;
                logEntitySpawnResult(calculation, diagnostics.attempted, individualSpawnPos, pillager.getUUID(), true, "spawned");
            } else {
                logEntitySpawnResult(calculation, diagnostics.attempted, individualSpawnPos, pillager.getUUID(), false, "level.addFreshEntity returned false");
            }
        }

        final int failedSpawns = calculation.enemyCount() - spawnedEnemies.size();
        if (failedSpawns > 0) {
            MineColoniesRaids.LOGGER.warn("Colony {} wave {} requested {} enemies, spawned {}, failed {}",
                    colonyId, calculation.selectedWave(), calculation.enemyCount(), spawnedEnemies.size(), failedSpawns);
        }

        if (spawnedEnemies.isEmpty()) {
            logWaveAbortSummary(resolvedColony.getID(), calculation.selectedWave(), diagnostics, "no enemies spawned successfully");
            serverPlayer.displayClientMessage(Component.translatable("message.minecolonies_raids.siege_defense.spawn_failed").withStyle(ChatFormatting.RED), false);
            return;
        }

        savedData.markWaveStarted(level.dimension(), colonyId, calculation.selectedWave());
        final ActiveWave wave = new ActiveWave(
                level.dimension(),
                spawnedEnemies,
                serverPlayer.getUUID(),
                resolvedColony.getID(),
                calculation.selectedWave(),
                calculation.enemyCount(),
                diagnostics,
                resolvedColony.getCenter(),
                new ServerBossEvent(Component.literal("Raid Wave " + calculation.selectedWave() + ": " + spawnedEnemies.size() + "/" + spawnedEnemies.size() + " remaining"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));

        ACTIVE_WAVES.add(wave);
        updateBossBarPlayers(level, wave);
        wave.updateBossBar(level);
        serverPlayer.displayClientMessage(Component.literal("Raid wave started."), false);
        serverPlayer.displayClientMessage(Component.literal("Wave " + calculation.selectedWave() + " spawned " + spawnedEnemies.size() + " of " + calculation.enemyCount() + " calculated enemies."), false);
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

            wave.refresh(level);
            updateBossBarPlayers(level, wave);
            wave.updateBossBar(level);

            if (wave.isComplete(level)) {
                iterator.remove();
                wave.removeBossBar();
                completeWave(server, wave);
            }
        }
    }

    public static void onLivingDeath(final LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        final UUID uuid = event.getEntity().getUUID();
        for (final ActiveWave wave : ACTIVE_WAVES) {
            if (wave.dimension().equals(level.dimension()) && wave.markKilled(uuid, event.getSource().toString(), event.getEntity().blockPosition())) {
                logEnemyStateChange(wave, uuid, "death", event.getEntity().blockPosition(), event.getSource().toString(), null);
                return;
            }
        }
    }

    public static void onEntityLeaveLevel(final EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        final Entity entity = event.getEntity();
        final UUID uuid = entity.getUUID();
        for (final ActiveWave wave : ACTIVE_WAVES) {
            if (!wave.dimension().equals(level.dimension())) {
                continue;
            }

            final Entity.RemovalReason reason = entity.getRemovalReason();
            if (wave.markRemoval(uuid, reason, entity.blockPosition())) {
                logEnemyStateChange(wave, uuid, "leave_level", entity.blockPosition(), reason == null ? "unknown removal reason" : reason.name(), reason);
                return;
            }
        }
    }

    private static Optional<ActiveWaveInfo> getActiveWaveInfo(final ServerLevel level, final int colonyId) {
        for (final ActiveWave wave : ACTIVE_WAVES) {
            if (wave.dimension().equals(level.dimension()) && wave.colonyId() == colonyId) {
                return Optional.of(new ActiveWaveInfo(wave.waveNumber(), wave.remainingEnemies(level)));
            }
        }
        return Optional.empty();
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

    private static String formatPos(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void logEntitySpawnResult(
            final WaveCalculation calculation,
            final int attempt,
            final BlockPos spawnPos,
            final UUID uuid,
            final boolean spawnResult,
            final String result) {
        if (!DIAGNOSTIC_LOGGING) {
            return;
        }

        MineColoniesRaids.LOGGER.info(
                "Raid entity spawn result colony={} wave={} attempt={}/{} pos={} spawnResult={} uuid={} result={}",
                calculation.snapshot().colonyId(),
                calculation.selectedWave(),
                attempt,
                calculation.enemyCount(),
                formatPos(spawnPos),
                spawnResult,
                uuid,
                result);
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

    private static void completeWave(final MinecraftServer server, final ActiveWave wave) {
        final ServerLevel overworld = server.overworld();
        final int reward = BASE_REWARD_DEFENSE_POINTS * wave.waveNumber();
        ColonyRaidSavedData.get(overworld).markWaveCompleted(wave.dimension(), wave.colonyId(), wave.waveNumber(), reward);
        logWaveSummary(wave);

        final ServerPlayer player = server.getPlayerList().getPlayer(wave.owner());
        if (player != null) {
            player.displayClientMessage(Component.literal("Raid wave completed."), false);
            player.displayClientMessage(Component.literal("Reward: " + reward + " Defense Points."), false);
            player.displayClientMessage(Component.literal("Raid debug summary: calculated=" + wave.diagnostics().calculated
                    + " attempted=" + wave.diagnostics().attempted
                    + " spawned=" + wave.diagnostics().spawned
                    + " killed=" + wave.diagnostics().killed
                    + " despawned=" + wave.diagnostics().despawned
                    + " unloaded=" + wave.diagnostics().unloaded
                    + " removed=" + wave.diagnostics().removed
                    + " unresolved=" + wave.diagnostics().unresolved), false);
        }
    }

    private static void logEnemyStateChange(
            final ActiveWave wave,
            final UUID uuid,
            final String event,
            final BlockPos lastKnownPos,
            final String detail,
            final Entity.RemovalReason removalReason) {
        if (!DIAGNOSTIC_LOGGING) {
            return;
        }

        MineColoniesRaids.LOGGER.info(
                "Raid enemy diagnostic colony={} wave={} uuid={} event={} removalReason={} lastKnownPos={} detail={}",
                wave.colonyId(),
                wave.waveNumber(),
                uuid,
                event,
                removalReason,
                formatPos(lastKnownPos),
                detail);
    }

    private static void logWaveSummary(final ActiveWave wave) {
        if (!DIAGNOSTIC_LOGGING) {
            return;
        }

        MineColoniesRaids.LOGGER.info(
                "Raid wave diagnostic summary colony={} wave={} calculated={} attempted={} spawned={} killed={} despawned={} unloaded={} removed={} unresolved={}",
                wave.colonyId(),
                wave.waveNumber(),
                wave.diagnostics().calculated,
                wave.diagnostics().attempted,
                wave.diagnostics().spawned,
                wave.diagnostics().killed,
                wave.diagnostics().despawned,
                wave.diagnostics().unloaded,
                wave.diagnostics().removed,
                wave.diagnostics().unresolved);
    }

    private static void logWaveAbortSummary(final int colonyId, final int wave, final WaveDiagnostics diagnostics, final String reason) {
        if (!DIAGNOSTIC_LOGGING) {
            return;
        }

        MineColoniesRaids.LOGGER.warn(
                "Raid wave diagnostic abort colony={} wave={} reason={} calculated={} attempted={} spawned={} killed={} despawned={} unloaded={} removed={} unresolved={}",
                colonyId,
                wave,
                reason,
                diagnostics.calculated,
                diagnostics.attempted,
                diagnostics.spawned,
                diagnostics.killed,
                diagnostics.despawned,
                diagnostics.unloaded,
                diagnostics.removed,
                diagnostics.unresolved);
    }

    private static void logCalculation(final WaveCalculation calculation) {
        MineColoniesRaids.LOGGER.info("""
                Colony {} wave calculation:
                TownHall={} contribution={}
                Builders={} contribution={}
                Residences={} contribution={}
                Score={} multiplier={} raw={} enemies={}""",
                calculation.snapshot().colonyId(),
                calculation.snapshot().townHallLevel(),
                calculation.townHallContribution(),
                calculation.snapshot().builderHutLevels(),
                calculation.builderContribution(),
                calculation.snapshot().residenceLevels(),
                calculation.residenceContribution(),
                calculation.colonyScore(),
                String.format("%.2f", calculation.waveMultiplier()),
                calculation.rawCalculatedValue(),
                calculation.enemyCount());
    }

    private static final class ActiveWave {
        private final ResourceKey<Level> dimension;
        private final List<TrackedEnemy> enemies;
        private final UUID owner;
        private final int colonyId;
        private final int waveNumber;
        private final int requestedEnemyCount;
        private final WaveDiagnostics diagnostics;
        private final BlockPos colonyCenter;
        private final ServerBossEvent bossBar;

        private ActiveWave(
                final ResourceKey<Level> dimension,
                final List<TrackedEnemy> enemies,
                final UUID owner,
                final int colonyId,
                final int waveNumber,
                final int requestedEnemyCount,
                final WaveDiagnostics diagnostics,
                final BlockPos colonyCenter,
                final ServerBossEvent bossBar) {
            this.dimension = dimension;
            this.enemies = List.copyOf(enemies);
            this.owner = owner;
            this.colonyId = colonyId;
            this.waveNumber = waveNumber;
            this.requestedEnemyCount = requestedEnemyCount;
            this.diagnostics = diagnostics;
            this.colonyCenter = colonyCenter;
            this.bossBar = bossBar;
        }

        private ResourceKey<Level> dimension() {
            return this.dimension;
        }

        private int colonyId() {
            return this.colonyId;
        }

        private int waveNumber() {
            return this.waveNumber;
        }

        private UUID owner() {
            return this.owner;
        }

        private BlockPos colonyCenter() {
            return this.colonyCenter;
        }

        private ServerBossEvent bossBar() {
            return this.bossBar;
        }

        private WaveDiagnostics diagnostics() {
            return this.diagnostics;
        }

        private int requestedEnemyCount() {
            return this.requestedEnemyCount;
        }

        private int spawnedEnemyCount() {
            return this.enemies.size();
        }

        private void refresh(final ServerLevel level) {
            for (final TrackedEnemy enemy : this.enemies) {
                if (enemy.isTerminal()) {
                    continue;
                }

                final Entity entity = level.getEntity(enemy.uuid());
                if (entity == null) {
                    final boolean chunkLoaded = level.isLoaded(enemy.lastKnownPos());
                    final boolean entitiesLoaded = chunkLoaded && level.areEntitiesLoaded(ChunkPos.asLong(enemy.lastKnownPos()));
                    if (!chunkLoaded || !entitiesLoaded) {
                        enemy.markNonTerminal(EnemyState.UNLOADED, "chunkLoaded=" + chunkLoaded + " entitiesLoaded=" + entitiesLoaded);
                        this.diagnostics.unloaded = countState(EnemyState.UNLOADED);
                    } else {
                        enemy.markNonTerminal(EnemyState.UNRESOLVED, "entity UUID not found in loaded chunk");
                        this.diagnostics.unresolved = countState(EnemyState.UNRESOLVED);
                    }
                    continue;
                }

                enemy.updateLastKnownPos(entity.blockPosition());
                if (entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) {
                    enemy.markTerminal(EnemyState.KILLED, "isDeadOrDying", entity.getRemovalReason(), entity.blockPosition());
                    this.diagnostics.killed = countState(EnemyState.KILLED);
                } else if (entity.isRemoved()) {
                    markRemoval(enemy.uuid(), entity.getRemovalReason(), entity.blockPosition());
                } else {
                    enemy.markNonTerminal(EnemyState.ACTIVE, "observed alive");
                    this.diagnostics.unloaded = countState(EnemyState.UNLOADED);
                    this.diagnostics.unresolved = countState(EnemyState.UNRESOLVED);
                }
            }
        }

        private boolean isComplete(final ServerLevel level) {
            return this.enemies.stream().allMatch(TrackedEnemy::isTerminal);
        }

        private int remainingEnemies(final ServerLevel level) {
            return (int) this.enemies.stream().filter(enemy -> !enemy.isTerminal()).count();
        }

        private void updateBossBar(final ServerLevel level) {
            final int remaining = remainingEnemies(level);
            this.bossBar.setName(Component.literal("Raid Wave " + this.waveNumber + ": " + remaining + "/" + this.enemies.size() + " remaining"));
            this.bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, remaining / (float) this.enemies.size())));
        }

        private void removeBossBar() {
            this.bossBar.removeAllPlayers();
        }

        private boolean markKilled(final UUID uuid, final String detail, final BlockPos pos) {
            final Optional<TrackedEnemy> enemy = findEnemy(uuid);
            if (enemy.isEmpty() || enemy.get().isTerminal()) {
                return false;
            }

            enemy.get().markTerminal(EnemyState.KILLED, detail, Entity.RemovalReason.KILLED, pos);
            this.diagnostics.killed = countState(EnemyState.KILLED);
            this.diagnostics.unloaded = countState(EnemyState.UNLOADED);
            this.diagnostics.unresolved = countState(EnemyState.UNRESOLVED);
            return true;
        }

        private boolean markRemoval(final UUID uuid, final Entity.RemovalReason reason, final BlockPos pos) {
            final Optional<TrackedEnemy> enemy = findEnemy(uuid);
            if (enemy.isEmpty() || enemy.get().isTerminal()) {
                return false;
            }

            if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
                enemy.get().markNonTerminal(EnemyState.UNLOADED, reason.name());
            } else if (reason == Entity.RemovalReason.KILLED) {
                enemy.get().markTerminal(EnemyState.KILLED, reason.name(), reason, pos);
            } else if (reason == Entity.RemovalReason.DISCARDED) {
                enemy.get().markTerminal(EnemyState.DESPAWNED, reason.name(), reason, pos);
            } else {
                enemy.get().markTerminal(EnemyState.REMOVED, reason == null ? "unknown removal reason" : reason.name(), reason, pos);
            }

            this.diagnostics.killed = countState(EnemyState.KILLED);
            this.diagnostics.despawned = countState(EnemyState.DESPAWNED);
            this.diagnostics.removed = countState(EnemyState.REMOVED);
            this.diagnostics.unloaded = countState(EnemyState.UNLOADED);
            this.diagnostics.unresolved = countState(EnemyState.UNRESOLVED);
            return true;
        }

        private Optional<TrackedEnemy> findEnemy(final UUID uuid) {
            return this.enemies.stream().filter(enemy -> enemy.uuid().equals(uuid)).findFirst();
        }

        private int countState(final EnemyState state) {
            return (int) this.enemies.stream().filter(enemy -> enemy.state() == state).count();
        }
    }

    private record ActiveWaveInfo(int waveNumber, int remainingEnemies) {
    }

    private static final class TrackedEnemy {
        private final UUID uuid;
        private final BlockPos spawnPos;
        private BlockPos lastKnownPos;
        private EnemyState state = EnemyState.ACTIVE;
        private Entity.RemovalReason removalReason;
        private String detail = "spawned";

        private TrackedEnemy(final UUID uuid, final BlockPos spawnPos) {
            this.uuid = uuid;
            this.spawnPos = spawnPos;
            this.lastKnownPos = spawnPos;
        }

        private UUID uuid() {
            return this.uuid;
        }

        private BlockPos lastKnownPos() {
            return this.lastKnownPos;
        }

        private EnemyState state() {
            return this.state;
        }

        private boolean isTerminal() {
            return this.state == EnemyState.KILLED || this.state == EnemyState.DESPAWNED || this.state == EnemyState.REMOVED;
        }

        private void updateLastKnownPos(final BlockPos pos) {
            this.lastKnownPos = pos;
        }

        private void markNonTerminal(final EnemyState state, final String detail) {
            this.state = state;
            this.detail = detail;
        }

        private void markTerminal(final EnemyState state, final String detail, final Entity.RemovalReason removalReason, final BlockPos pos) {
            this.state = state;
            this.detail = detail;
            this.removalReason = removalReason;
            this.lastKnownPos = pos;
        }
    }

    private enum EnemyState {
        ACTIVE,
        KILLED,
        DESPAWNED,
        UNLOADED,
        REMOVED,
        UNRESOLVED
    }

    private static final class WaveDiagnostics {
        private final int calculated;
        private int attempted;
        private int spawned;
        private int killed;
        private int despawned;
        private int unloaded;
        private int removed;
        private int unresolved;

        private WaveDiagnostics(final int calculated) {
            this.calculated = calculated;
        }
    }

}
