package gregtech.apiOld.metatileentity;

import gregtech.apiOld.GTValues;
import gregtech.apiOld.GregTechAPI;
import gregtech.apiOld.gui.ModularUI;
import gregtech.apiOld.gui.UIFactory;
import gregtech.apiOld.metatileentity.interfaces.IGregTechTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * {@link UIFactory} implementation for {@link MetaTileEntity}
 */
public class MetaTileEntityUIFactory extends UIFactory<IGregTechTileEntity> {

    public static final MetaTileEntityUIFactory INSTANCE = new MetaTileEntityUIFactory();

    private MetaTileEntityUIFactory() {
    }

    public void init() {
        GregTechAPI.UI_FACTORY_REGISTRY.register(0, new ResourceLocation(GTValues.MODID, "meta_tile_entity_factory"), this);
    }

    @Override
    protected ModularUI createUITemplate(IGregTechTileEntity tileEntity, EntityPlayer entityPlayer) {
        return tileEntity.getMetaTileEntity().createUI(entityPlayer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IGregTechTileEntity readHolderFromSyncData(PacketBuffer syncData) {
        return (IGregTechTileEntity) Minecraft.getMinecraft().world.getTileEntity(syncData.readBlockPos());
    }

    @Override
    protected void writeHolderToSyncData(PacketBuffer syncData, IGregTechTileEntity holder) {
        syncData.writeBlockPos(holder.pos());
    }
}