package com.minecolonies.raids.raid.spawn;

import net.minecraft.core.BlockPos;

public final class RaidSpawnDiagnostics {
    private int anchorsChecked;
    private int positionsChecked;
    private int rejectedUnloaded;
    private int rejectedNotEntityTicking;
    private int rejectedUnderground;
    private int rejectedUnsafeGround;
    private int rejectedHeadroom;
    private int rejectedFluid;
    private int rejectedDanger;
    private int rejectedCollision;
    private int rejectedInsideColony;
    private int rejectedNearBuilding;
    private int rejectedTooFarFromPlayer;
    private int rejectedTooCloseToColony;
    private int rejectedTooFarFromColony;
    private BlockPos selectedAnchor;

    void anchorChecked() {
        this.anchorsChecked++;
    }

    void positionChecked() {
        this.positionsChecked++;
    }

    void selectedAnchor(final BlockPos selectedAnchor) {
        this.selectedAnchor = selectedAnchor;
    }

    void recordRejection(final RaidSpawnRejection rejection) {
        switch (rejection) {
            case UNLOADED -> this.rejectedUnloaded++;
            case NOT_ENTITY_TICKING -> this.rejectedNotEntityTicking++;
            case UNDERGROUND -> this.rejectedUnderground++;
            case UNSAFE_GROUND -> this.rejectedUnsafeGround++;
            case HEADROOM_BLOCKED -> this.rejectedHeadroom++;
            case FLUID -> this.rejectedFluid++;
            case DANGEROUS_BLOCK -> this.rejectedDanger++;
            case COLLISION -> this.rejectedCollision++;
            case INSIDE_COLONY -> this.rejectedInsideColony++;
            case NEAR_BUILDING -> this.rejectedNearBuilding++;
            case TOO_FAR_FROM_PLAYER -> this.rejectedTooFarFromPlayer++;
            case TOO_CLOSE_TO_COLONY -> this.rejectedTooCloseToColony++;
            case TOO_FAR_FROM_COLONY -> this.rejectedTooFarFromColony++;
            case VALID -> {
            }
        }
    }

    public int anchorsChecked() {
        return this.anchorsChecked;
    }

    public int positionsChecked() {
        return this.positionsChecked;
    }

    public int rejectedUnloaded() {
        return this.rejectedUnloaded;
    }

    public int rejectedNotEntityTicking() {
        return this.rejectedNotEntityTicking;
    }

    public int rejectedUnderground() {
        return this.rejectedUnderground;
    }

    public int rejectedUnsafeGround() {
        return this.rejectedUnsafeGround;
    }

    public int rejectedHeadroom() {
        return this.rejectedHeadroom;
    }

    public int rejectedFluid() {
        return this.rejectedFluid;
    }

    public int rejectedDanger() {
        return this.rejectedDanger;
    }

    public int rejectedCollision() {
        return this.rejectedCollision;
    }

    public int rejectedInsideColony() {
        return this.rejectedInsideColony;
    }

    public int rejectedNearBuilding() {
        return this.rejectedNearBuilding;
    }

    public int rejectedTooFarFromPlayer() {
        return this.rejectedTooFarFromPlayer;
    }

    public int rejectedTooCloseToColony() {
        return this.rejectedTooCloseToColony;
    }

    public int rejectedTooFarFromColony() {
        return this.rejectedTooFarFromColony;
    }

    public BlockPos selectedAnchor() {
        return this.selectedAnchor;
    }
}
