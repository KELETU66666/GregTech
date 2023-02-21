package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.IDataAccessHatch;
=======
import codechicken.lib.vec.Vector3;
import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.util.GTUtility;
import gregtech.client.particle.GTLaserBeamParticle;
import gregtech.client.particle.GTParticleManager;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiFluidHatch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityAssemblyLine extends RecipeMapMultiblockController {

    private static final ResourceLocation LASER_LOCATION = new ResourceLocation(GTValues.MODID,"textures/fx/laser/laser.png");
    private static final ResourceLocation LASER_HEAD_LOCATION = new ResourceLocation(GTValues.MODID,"textures/fx/laser/laser_start.png");

    public MetaTileEntityAssemblyLine(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.ASSEMBLY_LINE_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityAssemblyLine(metaTileEntityId);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start(FRONT, UP, RIGHT)
                .aisle("FIF", "RTR", "SAG", " Y ")
                .aisle("FIF", "RTR", "DAG", " Y ").setRepeatable(3, 15)
                .aisle("FOF", "RTR", "GAG", " Y ")
                .where('S', selfPredicate())
                .where('F', states(getCasingState())
                        .or(autoAbilities(false, true, false, false, false, false, false))

                        // if ordered fluids are enabled, ban multi fluid hatches, otherwise allow all types
                        .or(ConfigHolder.machines.enableResearch && ConfigHolder.machines.orderedAssembly && ConfigHolder.machines.orderedFluidAssembly ?
                                metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.IMPORT_FLUIDS).stream()
                                .filter(mte -> !(mte instanceof MetaTileEntityMultiFluidHatch)).toArray(MetaTileEntity[]::new)).setMaxGlobalLimited(4) :
                                abilities(MultiblockAbility.IMPORT_FLUIDS).setMaxGlobalLimited(4)))

                .where('O', abilities(MultiblockAbility.EXPORT_ITEMS).addTooltips("gregtech.multiblock.pattern.location_end"))
                .where('Y', states(getCasingState()).or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(3)))
                .where('I', metaTileEntities(MetaTileEntities.ITEM_IMPORT_BUS[0]))
                .where('G', states(MetaBlocks.MULTIBLOCK_CASING.getState(BlockMultiblockCasing.MultiblockCasingType.GRATE_CASING)))
                .where('A', states(MetaBlocks.MULTIBLOCK_CASING.getState(BlockMultiblockCasing.MultiblockCasingType.ASSEMBLY_CONTROL)))
                .where('R', states(MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.LAMINATED_GLASS)))
                .where('T', states(MetaBlocks.MULTIBLOCK_CASING.getState(BlockMultiblockCasing.MultiblockCasingType.ASSEMBLY_LINE_CASING)))

                // if research is enabled, require the data hatch, otherwise use a grate instead
                .where('D', ConfigHolder.machines.enableResearch ? abilities(MultiblockAbility.DATA_ACCESS_HATCH)
                        .setMinGlobalLimited(1).setMaxGlobalLimited(2).or(states(getGrateState())) :
                        states(getGrateState()))
                .where(' ', any())
                .build();
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.SOLID_STEEL_CASING;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
    }

    protected IBlockState getGrateState() {
        return MetaBlocks.MULTIBLOCK_CASING.getState(BlockMultiblockCasing.MultiblockCasingType.GRATE_CASING);
    }

    @Override
    protected Function<BlockPos, Integer> multiblockPartSorter() {
        // ensure the inputs are always in order
        EnumFacing frontFacing = getFrontFacing();
        if (frontFacing == EnumFacing.NORTH) return pos -> -pos.getX();
        if (frontFacing == EnumFacing.SOUTH) return BlockPos::getX;
        if (frontFacing == EnumFacing.EAST) return pos -> -pos.getZ();
        if (frontFacing == EnumFacing.WEST) return BlockPos::getZ;
        return BlockPos::hashCode;
    }

    @Override
    public boolean checkRecipe(@Nonnull Recipe recipe, boolean consumeIfSuccess) {
        if (!ConfigHolder.machines.enableResearch) return true;

        List<IDataAccessHatch> dataHatches = getAbilities(MultiblockAbility.DATA_ACCESS_HATCH);
        for (IDataAccessHatch hatch : dataHatches) {
            // creative hatches do not need to check, they always have the recipe
            if (hatch.isCreative()) return true;

            // hatches need to have the recipe allowed
            if (hatch.getAvailableRecipes().contains(recipe)) {
                // check ordered items
                if (ConfigHolder.machines.orderedAssembly) {
                    List<GTRecipeInput> inputs = recipe.getInputs();
                    List<IItemHandlerModifiable> itemInputInventory = getAbilities(MultiblockAbility.IMPORT_ITEMS);

                    // slot count is not enough, so don't try to match it
                    if (itemInputInventory.size() < inputs.size()) return false;

                    for (int i = 0; i < inputs.size(); i++) {
                        if (!inputs.get(i).acceptsStack(itemInputInventory.get(i).getStackInSlot(0))) {
                            return false;
                        }
                    }

                    // check ordered fluids
                    if (ConfigHolder.machines.orderedFluidAssembly) {
                        inputs = recipe.getFluidInputs();
                        List<IFluidTank> fluidInputInventory = getAbilities(MultiblockAbility.IMPORT_FLUIDS);

                        // slot count is not enough, so don't try to match it
                        if (fluidInputInventory.size() < inputs.size()) return false;

                        for (int i = 0; i < inputs.size(); i++) {
                            if (!inputs.get(i).acceptsFluid(fluidInputInventory.get(i).getFluid())) {
                                return false;
                            }
                        }
                    }
                }
                // all conditions matched for any given config
                return true;
            }
        }
        return false;
        }

    private int beamCount;
    @SideOnly(Side.CLIENT)
    private GTLaserBeamParticle[][] beamParticles;

    @Override
    public void update() {
        super.update();
        if(ConfigHolder.client.shader.assemblyLineParticles) {
            if (getRecipeMapWorkable().isWorking()) {
                int maxBeams = getAbilities(MultiblockAbility.IMPORT_ITEMS).size() + 1;
                int maxProgress = getRecipeMapWorkable().getMaxProgress();

                // Each beam should be visible for an equal amount of time, which is derived from the maximum number of
                // beams and the maximum progress in the recipe.
                int beamTime = Math.max(1, maxProgress / maxBeams);

                int currentBeamCount = Math.min(maxBeams, getRecipeMapWorkable().getProgress() / beamTime);

                if (currentBeamCount != beamCount) {
                    beamCount = currentBeamCount;
                    writeCustomData(GregtechDataCodes.UPDATE_PARTICLE, this::writeParticles);
                }
            }
            else if (beamCount != 0) {
                beamCount = 0;
                writeCustomData(GregtechDataCodes.UPDATE_PARTICLE, this::writeParticles);
            }
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        writeParticles(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        readParticles(buf);
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        if (dataId == GregtechDataCodes.UPDATE_PARTICLE) {
            readParticles(buf);
        } else {
            super.receiveCustomData(dataId, buf);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (getWorld().isRemote && beamParticles != null) {
            for (GTLaserBeamParticle[] particle : beamParticles) {
                if (particle[0] != null) {
                    particle[0].setExpired();
                    particle[1].setExpired();
                }
            }
        }
        beamParticles = null;
    }

    private void writeParticles(PacketBuffer buf) {
        buf.writeVarInt(beamCount);
    }

    private void readParticles(PacketBuffer buf) {
        beamCount = buf.readVarInt();
        if (beamParticles == null) {
            beamParticles = new GTLaserBeamParticle[17][2];
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(getPos());
        for (int i = 0; i < beamParticles.length; i++) {
            GTLaserBeamParticle particle = beamParticles[i][0];
            if (i < beamCount && particle == null) {
                pos.setPos(getPos());
                Vector3 startPos = new Vector3().add(
                                pos.move(getFrontFacing().rotateY().getOpposite(), i))
                        .add(0.5, 0, 0.5);
                Vector3 endPos = startPos.copy().subtract(0, 1, 0);

                beamParticles[i][0] = createALParticles(getWorld(), startPos, endPos);

                pos.setPos(getPos());
                startPos = new Vector3().add(
                                pos.move(getFrontFacing().rotateY().getOpposite(), i).move(getFrontFacing().getOpposite(), 2))
                        .add(0.5, 0, 0.5);
                endPos = startPos.copy().subtract(0, 1, 0);
                beamParticles[i][1] = createALParticles(getWorld(), startPos, endPos);

                // Don't forget to add particles
                GTParticleManager.INSTANCE.addEffect(beamParticles[i][0], beamParticles[i][1]);

            } else if (i >= beamCount && particle != null) {
                particle.setExpired();
                beamParticles[i][0] = null;
                beamParticles[i][1].setExpired();
                beamParticles[i][1] = null;
            }
        }
    }

    private GTLaserBeamParticle createALParticles(World world, Vector3 startPos, Vector3 endPos) {
        GTLaserBeamParticle particle =  new GTLaserBeamParticle(world, startPos, endPos)
                .setBody(LASER_LOCATION)
                .setBeamHeight(0.125f)
                // Try commenting or adjusting on the next four lines to see what happens
                .setDoubleVertical(true)
                .setHead(LASER_HEAD_LOCATION)
                .setHeadWidth(0.1f)
                .setEmit(0.2f);

        particle.setOnUpdate(p -> {
            if (!isValid() || !GTUtility.isPosChunkLoaded(getWorld(), getPos()) || getWorld().getTileEntity(getPos()) != this.getHolder()) {
                p.setExpired();
            }
        });

        return particle;
    }
}
