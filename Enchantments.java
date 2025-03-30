public class Enchantments
{
    public static final EnchantmentWrapper POINT_LOOTING = new EnchantmentWrapper(
            "point_looting",
            5,
            5,
            5,
            "Point Looter",
            ITEMS.get(ItemTags.SWORD_ENCHANTABLE).orElseThrow(),
            ITEMS.get(ItemTags.SWORD_ENCHANTABLE).orElseThrow(),
            0,
            5,
            null,
            null,
            null
    );
}
