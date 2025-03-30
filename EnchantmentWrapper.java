import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantmentWrapper
{
    private String enchantmentId;

    private int anvilCost;
    private int weight;
    private int maxLevel;

    private Component display;
    private HolderSet.Named<Item> supportedItems;
    private HolderSet.Named<Item> primaryItems;
    private Enchantment.Cost minCost;
    private Enchantment.Cost maxCost;
    private EquipmentSlotGroup[] slots;
    private HolderSet<Enchantment> exclusiveSet;
    private DataComponentMap effects;

    public EnchantmentWrapper(
            String enchantmentId,
            int anvilCost,
            int weight,
            int maxLevel,
            String enchantmentName,
            HolderSet.Named<Item> supportedItems,
            HolderSet.Named<Item> primaryItems,
            int minCost,
            int maxCost,
            EquipmentSlotGroup[] slots,
            HolderSet<Enchantment> exclusiveSet,
            DataComponentMap effects
    ) {
        this.enchantmentId = enchantmentId;
        this.anvilCost = anvilCost;
        this.weight = weight;
        this.maxLevel = maxLevel;
        this.display = Component.literal(enchantmentName);
        this.supportedItems = supportedItems;
        this.primaryItems = primaryItems;
        this.minCost = new Enchantment.Cost(minCost, maxCost);
        this.maxCost = new Enchantment.Cost(minCost, maxCost);
        this.slots = slots;
        this.exclusiveSet = exclusiveSet;
        this.effects = effects;
    }

    public String getEnchantmentId() {
        return enchantmentId;
    }

    public int getAnvilCost() {
        return anvilCost;
    }

    public int getWeight() {
        return weight;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Component getDisplay() {
        return display;
    }

    public HolderSet.Named<Item> getSupportedItems() {
        return supportedItems;
    }

    public HolderSet.Named<Item> getPrimaryItems() {
        return primaryItems;
    }

    public Enchantment.Cost getMinCost() {
        return minCost;
    }

    public Enchantment.Cost getMaxCost() {
        return maxCost;
    }

    public EquipmentSlotGroup[] getSlots() {
        return slots;
    }

    public HolderSet<Enchantment> getExclusiveSet() {
        return exclusiveSet;
    }

    public DataComponentMap getEffects() {
        return effects;
    }
}
