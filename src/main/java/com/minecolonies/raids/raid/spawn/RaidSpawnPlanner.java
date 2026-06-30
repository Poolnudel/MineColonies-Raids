package com.minecolonies.raids.raid.spawn;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.raids.MineColoniesRaids;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

public final class RaidSpawnPlanner {
    private static final int MIN_DISTANCE_FROM_BUILDING = 32;
    private static final int MIN_DISTANCE_OUTSIDE_COLONY = 64;
    private static final int MAX_DISTANCE_FROM_COLONY = 224;
    private static final int MAX_DISTANCE_FROM_PLAYER = 224;
    private static final int DIRECTION_COUNT = 16;
    private static final int DISTANCE_STEP = 16;
    private static final int EXTRA_RING_DISTANCE = 128;
    private static final int MAX_ANCHORS_TO_EVALUATE = 48;
    private static final int MAX_GROUP_RADIUS = 18;
    private static final boolean DIAGNOSTIC_LOGGING = true;

    private RaidSpawnPlanner() {
    }

    public static Optional<RaidSpawnPlan> createPlan(
            final ServerLevel level,
            final IColony colony,
            final ServerPlayer player,
            final BlockPos fallbackCenter,
            final Collection<BlockPos> buildingPositions,
            final int enemyCount) {
        final BlockPos center = colony.getCenter() == null ? fallbackCenter : colony.getCenter();
        final double colonyRadius = estimateColonyRadius(center, buildingPositions);
        final int innerRadius = Math.max(MIN_DISTANCE_OUTSIDE_COLONY, (int) Math.ceil(colonyRadius) + 32);
        final int outerRadius = Math.min(MAX_DISTANCE_FROM_COLONY, innerRadius + EXTRA_RING_DISTANCE);
        final RaidSpawnDiagnostics diagnostics = new RaidSpawnDiagnostics();
        final List<ScoredAnchor> anchors = new ArrayList<>();

        for (int directionIndex = 0; directionIndex < DIRECTION_COUNT; directionIndex++) {
            final double angle = (Math.PI * 2.0D / DIRECTION_COUNT) * directionIndex + level.random.nextDouble() * 0.12D;
            final double dirX = Math.cos(angle);
            final double dirZ = Math.sin(angle);

            for (int distance = innerRadius; distance <= outerRadius; distance += DISTANCE_STEP) {
                final BlockPos xz = new BlockPos(
                        center.getX() + (int) Math.round(dirX * distance),
                        center.getY(),
                        center.getZ() + (int) Math.round(dirZ * distance));
                diagnostics.anchorChecked();
                final Optional<ValidatedPosition> candidate = resolveSurfacePosition(level, colony, player, center, buildingPositions, xz, diagnostics);
                if (candidate.isEmpty()) {
                    continue;
                }

                anchors.add(new ScoredAnchor(candidate.get().pos(), scoreAnchor(candidate.get(), center, player.blockPosition())));
            }
        }

        anchors.sort(Comparator.comparingInt(ScoredAnchor::score).reversed());
        final int anchorsToEvaluate = Math.min(MAX_ANCHORS_TO_EVALUATE, anchors.size());
        for (int i = 0; i < anchorsToEvaluate; i++) {
            final ScoredAnchor anchor = anchors.get(i);
            final List<BlockPos> positions = resolveGroupPositions(level, colony, player, center, buildingPositions, anchor.pos(), enemyCount, diagnostics);
            if (!positions.isEmpty()) {
                diagnostics.selectedAnchor(anchor.pos());
                logPlan(level, colony, center, player.blockPosition(), anchor.pos(), positions, diagnostics, innerRadius, outerRadius);
                return Optional.of(new RaidSpawnPlan(anchor.pos(), List.copyOf(positions), diagnostics));
            }
        }

        logNoPlan(colony, center, player.blockPosition(), diagnostics, innerRadius, outerRadius);
        return Optional.empty();
    }

    private static List<BlockPos> resolveGroupPositions(
            final ServerLevel level,
            final IColony colony,
            final ServerPlayer player,
            final BlockPos colonyCenter,
            final Collection<BlockPos> buildingPositions,
            final BlockPos anchor,
            final int enemyCount,
            final RaidSpawnDiagnostics diagnostics) {
        final List<BlockPos> positions = new ArrayList<>();
        final Set<BlockPos> used = new HashSet<>();

        for (final BlockPos offset : groupOffsets()) {
            if (positions.size() >= enemyCount) {
                break;
            }

            if (Math.abs(offset.getX()) > MAX_GROUP_RADIUS || Math.abs(offset.getZ()) > MAX_GROUP_RADIUS) {
                continue;
            }

            final BlockPos xz = anchor.offset(offset.getX(), 0, offset.getZ());
            diagnostics.positionChecked();
            final Optional<ValidatedPosition> candidate = resolveSurfacePosition(level, colony, player, colonyCenter, buildingPositions, xz, diagnostics);
            if (candidate.isEmpty()) {
                continue;
            }

            if (used.add(candidate.get().pos())) {
                positions.add(candidate.get().pos());
            }
        }

        return positions;
    }

    private static Optional<ValidatedPosition> resolveSurfacePosition(
            final ServerLevel level,
            final IColony colony,
            final ServerPlayer player,
            final BlockPos colonyCenter,
            final Collection<BlockPos> buildingPositions,
            final BlockPos xz,
            final RaidSpawnDiagnostics diagnostics) {
        final BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz);
        final Pillager probe = EntityType.PILLAGER.create(level);
        if (probe == null) {
            return Optional.empty();
        }

        probe.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D, 0.0F, 0.0F);
        final SpawnValidation validation = validate(level, colony, player, colonyCenter, buildingPositions, xz, surface, probe);
        diagnostics.recordRejection(validation.rejection());
        if (!validation.valid()) {
            return Optional.empty();
        }

        return Optional.of(new ValidatedPosition(surface, validation));
    }

    private static SpawnValidation validate(
            final ServerLevel level,
            final IColony colony,
            final ServerPlayer player,
            final BlockPos colonyCenter,
            final Collection<BlockPos> buildingPositions,
            final BlockPos requestedXz,
            final BlockPos surface,
            final Entity entity) {
        final boolean chunkLoaded = level.isLoaded(surface);
        final boolean entitiesLoaded = chunkLoaded && level.areEntitiesLoaded(ChunkPos.asLong(surface));
        final boolean entityTicking = chunkLoaded && level.isPositionEntityTicking(surface);
        final BlockState groundBlock = chunkLoaded ? level.getBlockState(surface.below()) : Blocks.AIR.defaultBlockState();
        final BlockState spawnBlock = chunkLoaded ? level.getBlockState(surface) : Blocks.AIR.defaultBlockState();
        final BlockState headBlock = chunkLoaded ? level.getBlockState(surface.above()) : Blocks.AIR.defaultBlockState();
        final FluidState fluidState = chunkLoaded ? level.getFluidState(surface) : Blocks.WATER.defaultBlockState().getFluidState();
        final int worldSurfaceY = chunkLoaded ? level.getHeight(Heightmap.Types.WORLD_SURFACE, requestedXz.getX(), requestedXz.getZ()) : surface.getY();
        final boolean underground = surface.getY() < worldSurfaceY - 1;
        final boolean solidGround = chunkLoaded && groundBlock.isFaceSturdy(level, surface.below(), Direction.UP);
        final boolean headroom = chunkLoaded && spawnBlock.isAir() && headBlock.isAir();
        final boolean noFluid = chunkLoaded && fluidState.isEmpty() && level.getFluidState(surface.above()).isEmpty();
        final boolean safeBlocks = chunkLoaded && !isDangerousBlock(groundBlock) && !isDangerousBlock(spawnBlock) && !isDangerousBlock(headBlock);
        final boolean collisionFree = chunkLoaded && level.noCollision(entity);
        final IColony colonyAtSpawn = chunkLoaded ? findColony(level, surface).orElse(null) : null;
        final boolean outsideColony = colonyAtSpawn == null || colonyAtSpawn.getID() != colony.getID();
        final boolean awayFromBuildings = buildingPositions.stream().noneMatch(buildingPos -> buildingPos.distSqr(surface) < MIN_DISTANCE_FROM_BUILDING * MIN_DISTANCE_FROM_BUILDING);
        final double distanceFromColonySqr = surface.distSqr(colonyCenter);
        final boolean farEnoughFromColony = distanceFromColonySqr >= MIN_DISTANCE_OUTSIDE_COLONY * MIN_DISTANCE_OUTSIDE_COLONY;
        final boolean closeEnoughToColony = distanceFromColonySqr <= MAX_DISTANCE_FROM_COLONY * MAX_DISTANCE_FROM_COLONY;
        final boolean nearPlayer = player.blockPosition().distSqr(surface) <= MAX_DISTANCE_FROM_PLAYER * MAX_DISTANCE_FROM_PLAYER;

        RaidSpawnRejection rejection = RaidSpawnRejection.VALID;
        if (!chunkLoaded || !entitiesLoaded) {
            rejection = RaidSpawnRejection.UNLOADED;
        } else if (!entityTicking) {
            rejection = RaidSpawnRejection.NOT_ENTITY_TICKING;
        } else if (underground) {
            rejection = RaidSpawnRejection.UNDERGROUND;
        } else if (!solidGround) {
            rejection = RaidSpawnRejection.UNSAFE_GROUND;
        } else if (!headroom) {
            rejection = RaidSpawnRejection.HEADROOM_BLOCKED;
        } else if (!noFluid) {
            rejection = RaidSpawnRejection.FLUID;
        } else if (!safeBlocks) {
            rejection = RaidSpawnRejection.DANGEROUS_BLOCK;
        } else if (!collisionFree) {
            rejection = RaidSpawnRejection.COLLISION;
        } else if (!outsideColony) {
            rejection = RaidSpawnRejection.INSIDE_COLONY;
        } else if (!awayFromBuildings) {
            rejection = RaidSpawnRejection.NEAR_BUILDING;
        } else if (!nearPlayer) {
            rejection = RaidSpawnRejection.TOO_FAR_FROM_PLAYER;
        } else if (!farEnoughFromColony) {
            rejection = RaidSpawnRejection.TOO_CLOSE_TO_COLONY;
        } else if (!closeEnoughToColony) {
            rejection = RaidSpawnRejection.TOO_FAR_FROM_COLONY;
        }

        final boolean valid = rejection == RaidSpawnRejection.VALID;
        if (DIAGNOSTIC_LOGGING) {
            MineColoniesRaids.LOGGER.debug(
                    "Raid spawn candidate colony={} requestedXz={} surface={} chunkLoaded={} entitiesLoaded={} entityTicking={} ground={} fluid={} worldSurfaceY={} underground={} solidGround={} headroom={} noFluid={} safeBlocks={} collisionFree={} outsideColony={} awayFromBuildings={} nearPlayer={} rejection={}",
                    colony.getID(),
                    formatPos(requestedXz),
                    formatPos(surface),
                    chunkLoaded,
                    entitiesLoaded,
                    entityTicking,
                    groundBlock,
                    fluidState,
                    worldSurfaceY,
                    underground,
                    solidGround,
                    headroom,
                    noFluid,
                    safeBlocks,
                    collisionFree,
                    outsideColony,
                    awayFromBuildings,
                    nearPlayer,
                    rejection);
        }

        return new SpawnValidation(valid, rejection, surface, groundBlock, fluidState, level.canSeeSky(surface));
    }

    private static int scoreAnchor(final ValidatedPosition position, final BlockPos colonyCenter, final BlockPos playerPos) {
        int score = 100;
        final double colonyDistance = Math.sqrt(position.pos().distSqr(colonyCenter));
        final double playerDistance = Math.sqrt(position.pos().distSqr(playerPos));

        score -= Math.abs(120 - (int) colonyDistance) / 2;
        score -= Math.max(0, (int) playerDistance - 160) / 2;
        if (position.validation().canSeeSky()) {
            score += 20;
        }
        return score;
    }

    private static List<BlockPos> groupOffsets() {
        final List<BlockPos> offsets = new ArrayList<>();
        offsets.add(BlockPos.ZERO);
        for (int radius = 2; radius <= MAX_GROUP_RADIUS; radius += 2) {
            for (int x = -radius; x <= radius; x += 2) {
                offsets.add(new BlockPos(x, 0, -radius));
                offsets.add(new BlockPos(x, 0, radius));
            }
            for (int z = -radius + 2; z <= radius - 2; z += 2) {
                offsets.add(new BlockPos(-radius, 0, z));
                offsets.add(new BlockPos(radius, 0, z));
            }
        }
        return offsets;
    }

    private static double estimateColonyRadius(final BlockPos center, final Collection<BlockPos> buildingPositions) {
        return Math.max(48.0D, buildingPositions.stream()
                .mapToDouble(pos -> Math.sqrt(pos.distSqr(center)))
                .max()
                .orElse(48.0D));
    }

    private static Optional<IColony> findColony(final ServerLevel level, final BlockPos pos) {
        try {
            return Optional.ofNullable(IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, pos));
        } catch (RuntimeException exception) {
            MineColoniesRaids.LOGGER.warn("Could not resolve MineColonies colony while validating raid spawn at {}", pos, exception);
            return Optional.empty();
        }
    }

    private static boolean isDangerousBlock(final BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW);
    }

    private static void logPlan(
            final ServerLevel level,
            final IColony colony,
            final BlockPos colonyCenter,
            final BlockPos playerPos,
            final BlockPos anchor,
            final List<BlockPos> positions,
            final RaidSpawnDiagnostics diagnostics,
            final int innerRadius,
            final int outerRadius) {
        if (!DIAGNOSTIC_LOGGING) {
            return;
        }

        MineColoniesRaids.LOGGER.info(
                "Raid spawn plan colony={} center={} player={} innerRadius={} outerRadius={} anchor={} positions={} anchorsChecked={} positionsChecked={} rejectedUnloaded={} rejectedUnderground={} rejectedFluid={} rejectedDanger={} rejectedCollision={} rejectedInsideColony={} rejectedNearBuilding={} rejectedTooFarFromPlayer={}",
                colony.getID(),
                formatPos(colonyCenter),
                formatPos(playerPos),
                innerRadius,
                outerRadius,
                formatPos(anchor),
                positions.stream().map(RaidSpawnPlanner::formatPos).toList(),
                diagnostics.anchorsChecked(),
                diagnostics.positionsChecked(),
                diagnostics.rejectedUnloaded(),
                diagnostics.rejectedUnderground(),
                diagnostics.rejectedFluid(),
                diagnostics.rejectedDanger(),
                diagnostics.rejectedCollision(),
                diagnostics.rejectedInsideColony(),
                diagnostics.rejectedNearBuilding(),
                diagnostics.rejectedTooFarFromPlayer());
    }

    private static void logNoPlan(
            final IColony colony,
            final BlockPos colonyCenter,
            final BlockPos playerPos,
            final RaidSpawnDiagnostics diagnostics,
            final int innerRadius,
            final int outerRadius) {
        if (!DIAGNOSTIC_LOGGING) {
            return;
        }

        MineColoniesRaids.LOGGER.warn(
                "No raid spawn plan found colony={} center={} player={} innerRadius={} outerRadius={} anchorsChecked={} positionsChecked={} rejectedUnloaded={} rejectedUnderground={} rejectedUnsafeGround={} rejectedHeadroom={} rejectedFluid={} rejectedDanger={} rejectedCollision={} rejectedInsideColony={} rejectedNearBuilding={} rejectedTooFarFromPlayer={} rejectedTooCloseToColony={} rejectedTooFarFromColony={}",
                colony.getID(),
                formatPos(colonyCenter),
                formatPos(playerPos),
                innerRadius,
                outerRadius,
                diagnostics.anchorsChecked(),
                diagnostics.positionsChecked(),
                diagnostics.rejectedUnloaded(),
                diagnostics.rejectedUnderground(),
                diagnostics.rejectedUnsafeGround(),
                diagnostics.rejectedHeadroom(),
                diagnostics.rejectedFluid(),
                diagnostics.rejectedDanger(),
                diagnostics.rejectedCollision(),
                diagnostics.rejectedInsideColony(),
                diagnostics.rejectedNearBuilding(),
                diagnostics.rejectedTooFarFromPlayer(),
                diagnostics.rejectedTooCloseToColony(),
                diagnostics.rejectedTooFarFromColony());
    }

    private static String formatPos(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private record ScoredAnchor(BlockPos pos, int score) {
    }

    private record ValidatedPosition(BlockPos pos, SpawnValidation validation) {
    }

    private record SpawnValidation(
            boolean valid,
            RaidSpawnRejection rejection,
            BlockPos surface,
            BlockState groundBlock,
            FluidState fluidState,
            boolean canSeeSky) {
    }
}
