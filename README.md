# enchantment-nms

Main Class
```java
    @Override
    public void onLoad() {
        EnchantmentUtility e = new EnchantmentUtility();
        e.unfreezeRegistry();


        e.registerEnchantment(Enchantments.POINT_LOOTING);

        e.freezeRegistry();
    }
```
