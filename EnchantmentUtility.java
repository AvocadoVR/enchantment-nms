import com.lewdmc.util.EnchantmentWrapper;
import com.lewdmc.util.Reflex;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_21_R4.CraftEquipmentSlot;
import org.bukkit.craftbukkit.v1_21_R4.CraftServer;
import org.bukkit.craftbukkit.v1_21_R4.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.v1_21_R4.util.CraftNamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;

public class EnchantmentUtility
{
    private static final MinecraftServer SERVER;
    private static final MappedRegistry<Enchantment> ENCHANTS;
    public static final MappedRegistry<Item>        ITEMS;

    private static final String REGISTRY_FROZEN_TAGS_FIELD = "j"; // frozenTags
    private static final String REGISTRY_ALL_TAGS_FIELD    = "k"; // allTags
    private static final String TAG_SET_UNBOUND_METHOD     = "a"; // .unbound()
    private static final String TAG_SET_MAP_FIELD          = "val$map";

    static {
        SERVER = ((CraftServer) Bukkit.getServer()).getServer();
        ENCHANTS = (MappedRegistry<Enchantment>) SERVER.registryAccess().lookup(Registries.ENCHANTMENT).orElseThrow();
        ITEMS = (MappedRegistry<Item>) SERVER.registryAccess().lookup(Registries.ITEM).orElseThrow();
    }

    @NotNull
    public org.bukkit.enchantments.Enchantment registerEnchantment(@NotNull EnchantmentWrapper wrapper) {
        // Use your own values.
        int anvilCost = 3;
        int weight = 10;
        int maxLevel = 5;

        // See above for details.
        Component display = wrapper.getDisplay();
        HolderSet.Named<Item> supportedItems = wrapper.getSupportedItems();
        HolderSet.Named<Item> primaryItems = wrapper.getPrimaryItems();
        Enchantment.Cost minCost = wrapper.getMinCost();
        Enchantment.Cost maxCost = wrapper.getMaxCost();
        EquipmentSlotGroup[] slots = wrapper.getSlots();
        HolderSet<Enchantment> exclusiveSet = wrapper.getExclusiveSet();
        DataComponentMap effects = wrapper.getEffects();

        Enchantment.EnchantmentDefinition definition = Enchantment.definition(supportedItems, primaryItems, weight, maxLevel, minCost, maxCost, anvilCost, slots);

        Enchantment enchantment = new Enchantment(display, definition, exclusiveSet, effects);

        // Create a new Holder for the custom enchantment.
        Holder.Reference<Enchantment> reference = ENCHANTS.createIntrusiveHolder(enchantment);

        // Add it into Registry.
        Registry.register(ENCHANTS, wrapper.getEnchantmentId(), enchantment);

        // See Step #6 for details.
        this.setupDistribution(reference);

        return CraftEnchantment.minecraftToBukkit(enchantment);
    }

    public void unfreezeRegistry() {
        unfreeze(ENCHANTS);
        unfreeze(ITEMS);
    }

    public void freezeRegistry() {
        freeze(ITEMS);
        freeze(ENCHANTS);
    }

    private static <T> void unfreeze(@NotNull MappedRegistry<T> registry) {
        Reflex.setFieldValue(registry, "l", false);             // MappedRegistry#frozen
        Reflex.setFieldValue(registry, "m", new IdentityHashMap<>()); // MappedRegistry#unregisteredIntrusiveHolders
    }

    private static <T> void freeze(@NotNull MappedRegistry<T> registry) {
        // Get original TagSet object of the registry before unbound.
        // We MUST keep original TagSet object and only modify an inner map object inside it.
        // Otherwise it will throw an Network Error on client join because of 'broken' tags that were bound to other TagSet object.
        Object tagSet = getAllTags(registry);

        Map<TagKey<T>, HolderSet.Named<T>> tagsMap = getTagsMap(tagSet);
        Map<TagKey<T>, HolderSet.Named<T>> frozenTags = getFrozenTags(registry);

        // Here we add all registered and bound vanilla tags to the 'frozenTags' map for further freeze & bind.
        // For some reason 'frozenTags' map does not contain all the tags, so some of them will be absent if not added back here
        // and result in broken gameplay features.
        tagsMap.forEach(frozenTags::putIfAbsent);

        // We MUST 'unbound' the registry tags to be able to call .freeze() method of it.
        // Otherwise it will throw an error saying tags are not bound.
        unbound(registry);

        // This method will register all tags from the 'frozenTags' map and assign a new TagSet object to the 'allTags' field of registry.
        // But we MUST replace the 'allTags' field value with the original (before unbound) TagSet object to prevent Network Error for clients.
        registry.freeze();

        // Here we need to put in 'tagsMap' map of TagSet object all new/custom registered tags.
        // Otherwise it will cause Network Error because custom tags are not present in the TagSet tags map.
        frozenTags.forEach(tagsMap::putIfAbsent);

        // Update inner tags map of the TagSet object that is 'allTags' field of the registry.
        Reflex.setFieldValue(tagSet, TAG_SET_MAP_FIELD, tagsMap);
        // Assign original TagSet object with modified tags map to the 'allTags' field of the registry.
        Reflex.setFieldValue(registry, REGISTRY_ALL_TAGS_FIELD, tagSet);
    }

    public static EquipmentSlotGroup[] nmsSlots(EquipmentSlot[] slots) {
        EquipmentSlotGroup[] nmsSlots = new EquipmentSlotGroup[slots.length];

        for (int index = 0; index < nmsSlots.length; index++) {
            EquipmentSlot bukkitSlot = slots[index];
            nmsSlots[index] = CraftEquipmentSlot.getNMSGroup(bukkitSlot.getGroup());
        }

        return nmsSlots;
    }

    @NotNull
    private static HolderSet.Named<Item> createItemsSet(@NotNull String prefix, @NotNull String enchantId, @NotNull Set<Material> materials) {
        TagKey<Item> customKey = getTagKey(ITEMS, prefix + "/" + enchantId);
        List<Holder<Item>> holders = new ArrayList<>();

        materials.forEach(material -> {
            ResourceLocation location = CraftNamespacedKey.toMinecraft(material.getKey());
            Holder.Reference<Item> holder = ITEMS.get(location).orElse(null);
            if (holder == null) return;

            holders.add(holder);
        });

        ITEMS.bindTag(customKey, holders);

        return getFrozenTags(ITEMS).get(customKey);
    }

    private static <T> void unbound(@NotNull MappedRegistry<T> registry) {
        Class<?> tagSetClass = Reflex.getInnerClass(MappedRegistry.class.getName(), "TagSet");

        Method unboundMethod = Reflex.getMethod(tagSetClass, TAG_SET_UNBOUND_METHOD);
        Object unboundTagSet = Reflex.invokeMethod(unboundMethod, registry); // new TagSet object.

        Reflex.setFieldValue(registry, REGISTRY_ALL_TAGS_FIELD, unboundTagSet);
    }


    private void setupDistribution(@NotNull Holder.Reference<Enchantment> reference) {
        boolean experimentalTrades = SERVER.getWorldData().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE);

        boolean isCurse = false;
        boolean isTreasure = false;
        boolean isOnRandomLoot = true;
        boolean isOnTradedEquipment = true;
        boolean isOnMobSpawnEquipment = true;
        boolean isTradeable = true;
        boolean isDiscoverable = true;

        if (isTreasure) {
            addInTag(EnchantmentTags.TREASURE, reference);
            addInTag(EnchantmentTags.DOUBLE_TRADE_PRICE, reference);
        }
        else addInTag(EnchantmentTags.NON_TREASURE, reference);

        if (isOnRandomLoot) {
            addInTag(EnchantmentTags.ON_RANDOM_LOOT, reference);
        }

        if (!isTreasure) {
            if (isOnMobSpawnEquipment) {
                addInTag(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT, reference);
            }

            if (isOnTradedEquipment) {
                addInTag(EnchantmentTags.ON_TRADED_EQUIPMENT, reference);
            }
        }

        if (experimentalTrades) {
            if (isTradeable) {
                addInTag(EnchantmentTags.TRADES_DESERT_COMMON, reference);
                addInTag(EnchantmentTags.TRADES_JUNGLE_COMMON, reference);
                // Add more trade tags if needed.
            }
        }
        else {
            if (isTradeable) {
                addInTag(EnchantmentTags.TRADEABLE, reference);
            }
            else removeFromTag(EnchantmentTags.TRADEABLE, reference);
        }

        if (isCurse) {
            addInTag(EnchantmentTags.CURSE, reference);
        }
        else {
            if (!isTreasure) {
                if (isDiscoverable) {
                    addInTag(EnchantmentTags.IN_ENCHANTING_TABLE, reference);
                }
                else removeFromTag(EnchantmentTags.IN_ENCHANTING_TABLE, reference);
            }
        }
    }

    @NotNull
    private static HolderSet.Named<Enchantment> createExclusiveSet(@NotNull String enchantId) {
        TagKey<Enchantment> customKey = getTagKey(ENCHANTS, "exclusive_set/" + enchantId);
        List<Holder<Enchantment>> holders = new ArrayList<>();

        ENCHANTS.bindTag(customKey, holders);

        return getFrozenTags(ENCHANTS).get(customKey);
    }

    private void addInTag(@NotNull TagKey<Enchantment> tagKey, @NotNull Holder.Reference<Enchantment> reference) {
        modfiyTag(ENCHANTS, tagKey, reference, List::add);
    }

    private void removeFromTag(@NotNull TagKey<Enchantment> tagKey, @NotNull Holder.Reference<Enchantment> reference) {
        modfiyTag(ENCHANTS, tagKey, reference, List::remove);
    }

    private <T> void modfiyTag(@NotNull MappedRegistry<T> registry, @NotNull TagKey<T> tagKey, @NotNull Holder.Reference<T> reference, @NotNull BiConsumer<List<Holder<T>>, Holder.Reference<T>> consumer) {
        HolderSet.Named<T> holders = registry.get(tagKey).orElse(null);
        if (holders == null) return;

        List<Holder<T>> contents = new ArrayList<>(holders.stream().toList());
        consumer.accept(contents, reference);

        registry.bindTag(tagKey, contents);
    }


    @NotNull
    private static <T> ResourceKey<T> getResourceKey(@NotNull Registry<T> registry, @NotNull String name) {
        return ResourceKey.create(registry.key(), ResourceLocation.withDefaultNamespace(name));
    }

    private static <T> TagKey<T> getTagKey(@NotNull Registry<T> registry, @NotNull String name) {
        return TagKey.create(registry.key(), ResourceLocation.withDefaultNamespace(name));
    }

    @NotNull
    private static <T> Map<TagKey<T>, HolderSet.Named<T>> getFrozenTags(@NotNull MappedRegistry<T> registry) {
        return (Map<TagKey<T>, HolderSet.Named<T>>) Reflex.getFieldValue(registry, REGISTRY_FROZEN_TAGS_FIELD);
    }

    @NotNull
    private static <T> Object getAllTags(@NotNull MappedRegistry<T> registry) {
        return Reflex.getFieldValue(registry, REGISTRY_ALL_TAGS_FIELD);
    }

    @NotNull
    private static <T> Map<TagKey<T>, HolderSet.Named<T>> getTagsMap(@NotNull Object tagSet) {
        // new HashMap, because original is ImmutableMap.
        return new HashMap<>((Map<TagKey<T>, HolderSet.Named<T>>) Reflex.getFieldValue(tagSet, TAG_SET_MAP_FIELD));
    }
}
