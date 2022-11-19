package gregtech.apiOld.items.toolitem;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.apiOld.capability.tool.IHammerItem;
import gregtech.apiOld.items.metaitem.stats.IItemCapabilityProvider;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public class HammerItemStat implements IItemCapabilityProvider {

    @Override
    public ICapabilityProvider createProvider(ItemStack itemStack) {
        return new CapabilityProvider(itemStack);
    }

    private static class CapabilityProvider extends AbstractToolItemCapabilityProvider<IHammerItem> implements IHammerItem {

        public CapabilityProvider(ItemStack itemStack) {
            super(itemStack);
        }

        @Override
        protected Capability<IHammerItem> getCapability() {
            return GregtechCapabilities.CAPABILITY_HAMMER;
        }
    }
}
