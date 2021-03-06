package me.jellysquid.mods.lithium.mixin.voxelshape.fast_shape_comparisons;

import net.minecraft.util.BooleanBiFunction;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.*;
import org.spongepowered.asm.mixin.*;

@Mixin(VoxelShapes.class)
public abstract class MixinVoxelShapes {
    @Mutable
    @Shadow
    @Final
    public static VoxelShape UNBOUNDED;

    @Mutable
    @Shadow
    @Final
    private static VoxelShape FULL_CUBE;

    @Mutable
    @Shadow
    @Final
    private static VoxelShape EMPTY;

    private static final VoxelSet FULL_CUBE_VOXELS;

    static {
        FULL_CUBE_VOXELS = new BitSetVoxelSet(1, 1, 1);
        FULL_CUBE_VOXELS.set(0, 0, 0, true, true);

        UNBOUNDED = new Lithium_VoxelShapeSimpleCube(FULL_CUBE_VOXELS, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

        FULL_CUBE = createFullShape();
        EMPTY = createEmptyShape();
    }

    private static VoxelShape createFullShape() {
        return new Lithium_VoxelShapeSimpleCube(FULL_CUBE_VOXELS, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    }

    private static VoxelShape createEmptyShape() {
        return new Lithium_VoxelShapeEmpty(new BitSetVoxelSet(0, 0, 0));
    }

    /**
     * Responsible for determining whether or not a shape occludes light
     *
     * @reason Avoid the expensive shape combination
     * @author JellySquid
     */
    @Overwrite
    public static boolean unionCoversFullCube(VoxelShape a, VoxelShape b) {
        // At least one shape is a full cube and will match
        if (a == VoxelShapes.fullCube() || b == VoxelShapes.fullCube()) {
            return true;
        }

        boolean ae = a == VoxelShapes.empty() || a.isEmpty();
        boolean be = b == VoxelShapes.empty() || b.isEmpty();

        if (ae && be) {
            return false;
        } else {
            // Test each shape individually if they're non-empty and fail fast
            if (!ae && VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), a, BooleanBiFunction.ONLY_FIRST)) {
                return false;
            }

            return be || !VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), b, BooleanBiFunction.ONLY_FIRST);
        }
    }

    /**
     * @reason Use our optimized shape type
     * @author JellySquid
     */
    @Overwrite
    public static VoxelShape cuboid(Box box) {
        return new Lithium_VoxelShapeSimpleCube(FULL_CUBE_VOXELS, box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
    }
}
