package me.jellysquid.mods.lithium.mixin.entity.chunk_cache;

import me.jellysquid.mods.lithium.common.cache.EntityChunkCache;
import me.jellysquid.mods.lithium.common.entity.cache.EntityWithChunkCache;
import me.jellysquid.mods.lithium.common.shapes.LithiumVoxelShapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.Tag;
import net.minecraft.util.ReusableStream;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityWithChunkCache {
    @Shadow
    public abstract Entity getVehicle();

    @Shadow
    public World world;

    @Shadow
    public double y;

    @Shadow
    public double x;

    @Shadow
    public double z;

    @Shadow
    public abstract float getStandingEyeHeight();

    @Shadow
    public abstract Box getBoundingBox();

    private EntityChunkCache chunkCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(EntityType<?> type, World world, CallbackInfo ci) {
        this.chunkCache = new EntityChunkCache((Entity) (Object) this);
    }

    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();

    /**
     * @reason Use the chunk cache.
     * @author JellySquid
     */
    @Overwrite
    public boolean isSubmergedIn(Tag<Fluid> tag, boolean flag) {
        if (this.getVehicle() instanceof BoatEntity) {
            return false;
        }

        double eyeY = this.y + (double) this.getStandingEyeHeight();

        int bX = MathHelper.floor(this.x);
        int bY = MathHelper.floor(eyeY);
        int bZ = MathHelper.floor(this.z);

        FluidState fluid = this.chunkCache.getFluidState(bX, bY, bZ);

        if (fluid.matches(tag)) {
            return eyeY < (bY + (double) fluid.getHeight(this.world, this.scratchPos.set(bX, bY, bZ)) + 0.11111111D);
        }

        return false;
    }

    /**
     * @reason Use the chunk cache.
     * @author JellySquid
     */
    @Overwrite
    private boolean isInsideBubbleColumn() {
        return this.chunkCache.getBlockState(MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z)).getBlock() == Blocks.BUBBLE_COLUMN;
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        this.chunkCache.updateChunks(this.getBoundingBox());
    }

    @Redirect(method = {"move", "checkBlockCollision", "playStepSound", "isInsideWall", "getLandingPos", "checkBlockCollision", "getVelocityMultiplier" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"), require = 0)
    private BlockState redirectGetBlockState(World world, BlockPos pos) {
        return this.chunkCache == null ? world.getBlockState(pos) : this.chunkCache.getBlockState(pos);
    }

    @Redirect(method = "checkBlockCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRegionLoaded(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Z"), require = 0)
    private boolean redirectIsRegionLoaded(World world, BlockPos min, BlockPos max) {
        return this.chunkCache == null ? world.isRegionLoaded(min, max) : this.chunkCache.isRegionLoaded(min, max);
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;doesAreaContainFireSource(Lnet/minecraft/util/math/Box;)Z"), require = 0)
    private boolean redirectDoesAreaContainFireSource(World world, Box box) {
        int minX = MathHelper.floor(box.x1);
        int maxX = MathHelper.ceil(box.x2);
        int minY = MathHelper.floor(box.y1);
        int maxY = MathHelper.ceil(box.y2);
        int minZ = MathHelper.floor(box.z1);
        int maxZ = MathHelper.ceil(box.z2);

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    Block block = this.chunkCache.getBlockState(x, y, z).getBlock();

                    if (block == Blocks.FIRE || block == Blocks.LAVA) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Redirect(method = "adjustMovementForCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityContext;Lnet/minecraft/util/ReusableStream;)Lnet/minecraft/util/math/Vec3d;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;adjustSingleAxisMovementForCollisions(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/WorldView;Lnet/minecraft/entity/EntityContext;Lnet/minecraft/util/ReusableStream;)Lnet/minecraft/util/math/Vec3d;"), require = 0)
    private static Vec3d redirectCalculateTangentialMotionVector(Vec3d movement, Box entityBoundingBox, WorldView world, EntityContext context, ReusableStream<VoxelShape> collisions, Entity entity, Vec3d a5, Box a4, World a3, EntityContext a2, ReusableStream<VoxelShape> a1) {
        if (entity == null) {
            return Entity.adjustSingleAxisMovementForCollisions(movement, entityBoundingBox, world, context, collisions);
        }

        EntityChunkCache chunkCache = EntityWithChunkCache.getChunkCache(entity);

        double x = movement.x;
        double y = movement.y;
        double z = movement.z;

        if (y != 0.0D) {
            y = LithiumVoxelShapes.calculatePushVelocity(Direction.Axis.Y, entityBoundingBox, chunkCache, y, context, collisions.stream());

            if (y != 0.0D) {
                entityBoundingBox = entityBoundingBox.offset(0.0D, y, 0.0D);
            }
        }

        boolean flag = Math.abs(x) < Math.abs(z);

        if (flag && z != 0.0D) {
            z = LithiumVoxelShapes.calculatePushVelocity(Direction.Axis.Z, entityBoundingBox, chunkCache, z, context, collisions.stream());

            if (z != 0.0D) {
                entityBoundingBox = entityBoundingBox.offset(0.0D, 0.0D, z);
            }
        }

        if (x != 0.0D) {
            x = LithiumVoxelShapes.calculatePushVelocity(Direction.Axis.X, entityBoundingBox, chunkCache, x, context, collisions.stream());

            if (!flag && x != 0.0D) {
                entityBoundingBox = entityBoundingBox.offset(x, 0.0D, 0.0D);
            }
        }

        if (!flag && z != 0.0D) {
            z = LithiumVoxelShapes.calculatePushVelocity(Direction.Axis.Z, entityBoundingBox, chunkCache, z, context, collisions.stream());
        }

        return new Vec3d(x, y, z);
    }

    @Redirect(method = "updateMovementInFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;"))
    private FluidState getFluidStateWhileUpdatingMovement(World world, BlockPos pos) {
        EntityChunkCache cache = EntityWithChunkCache.getChunkCache((Entity) (Object) this);
        return cache.getFluidState(pos);
    }

    @Override
    public EntityChunkCache getEntityChunkCache() {
        return this.chunkCache;
    }
}
