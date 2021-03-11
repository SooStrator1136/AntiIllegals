package org.zeroBzeroT.antiillegals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class AntiIllegals extends JavaPlugin {

    private static final int maxLoreEnchantmentLevel = 1;
    private static AntiIllegals instance;

    /**
     * constructor
     */
    public AntiIllegals() {
        // save the plugin instance for logging
        instance = this;
    }

    /**
     * use this method to check and remove illegal items from inventories
     *
     * @param inventory     the inventory that should be checked
     * @param location      location of the inventory holder for possible item drops
     * @param checkShulkers true, if items inside shulkers should be checked
     */
    public static void checkInventory(Inventory inventory, Location location, boolean checkShulkers) {
        List<ItemStack> removeItemStacks = new ArrayList<>();
        List<ItemStack> bookItemStacks = new ArrayList<>();

        boolean wasFixed = false;
        int fixesIllegals = 0;
        int fixesBooks = 0;

        // Loop through Inventory
        for (ItemStack itemStack : inventory.getContents()) {
            switch (checkItemStack(itemStack, location, checkShulkers)) {
                case illegal:
                    removeItemStacks.add(itemStack);
                    break;

                case wasFixed:
                    wasFixed = true;
                    break;

                // Book inside a shulker
                case written_book:
                    if (inventory.getHolder() instanceof ShulkerBox) {
                        bookItemStacks.add(itemStack);
                    }
                    break;

                default:
                    break;
            }
        }

        // Remove illegal items - TODO: check if that is needed if setAmount(0) is in place
        for (ItemStack itemStack : removeItemStacks) {
            itemStack.setAmount(0);
            inventory.remove(itemStack);
            fixesIllegals++;
        }

        // Remove books
        if (bookItemStacks.size() > 5) {
            //Location loc = player == null ? null : player.getLocation();

            if (location != null) {
                for (ItemStack itemStack : bookItemStacks) {
                    inventory.remove(itemStack);
                    fixesBooks++;

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                location.getWorld().dropItem(location, itemStack).setPickupDelay(20 * 5);
                            } catch (NullPointerException exception) {
                                cancel();
                            }
                        }
                    }.runTaskLater(instance, 0);
                }
            } else {
                log("checkInventory", "Found too many books in shulker but could not find location to drop them.");
            }
        }

        // Log
        if (wasFixed || fixesIllegals > 0 || fixesBooks > 0) {
            log("checkInventory", "Illegal Blocks: " + fixesIllegals + " - Dropped Books: " + fixesBooks + " - Wrong Enchants: " + wasFixed + ".");
        }
    }

    /**
     * Check an item and try to fix it. If it is an illegal item, then remove it.
     *
     * @param itemStack     Item
     * @param location      Location for item drops
     * @param checkShulkers True, if inventories of containing shulkers should be checked
     * @return
     */
    public static ItemState checkItemStack(ItemStack itemStack, Location location, boolean checkShulkers) {
        boolean wasFixed = false;

        // null Item
        if (itemStack == null) return ItemState.empty;

        // Unbreakables
//        if (itemStack.getType().isItem() && !itemStack.getType().isEdible() && !itemStack.getType().isBlock()) {
//            if (itemStack.getDurability() > itemStack.getType().getMaxDurability() || itemStack.getDurability() < 0 || itemStack.getItemMeta().isUnbreakable()) {
//                itemStack.setDurability((short) 0);
//                itemStack.getItemMeta().setUnbreakable(false);
//                itemStack.setAmount(0);
//            }
//        }

        // Illegal Blocks
        if (Checks.isIllegalBlock(itemStack.getType())) {
            itemStack.setAmount(0);
            return ItemState.illegal;
        }

        // nbt furnace check
        if (itemStack.getType() == Material.FURNACE && itemStack.toString().contains("internal=")) {
            // TODO: replace this hack with a solution that checks the nbt tag
            itemStack.setAmount(0);
            return ItemState.illegal;
        }

        // Revert Overstacked Items
        if (itemStack.getAmount() > itemStack.getMaxStackSize()) {
            itemStack.setAmount(itemStack.getMaxStackSize());
            wasFixed = true;
        }

        // Check items with lore
        if (itemStack.getItemMeta() != null && itemStack.getItemMeta().hasLore()) {
            // Christmas Illegals
            /*if (itemStack.getItemMeta().getLore().contains("Christmas Advent Calendar 2020"))
                return ItemState.clean;*/

            // Thunderclouds Item
            if (itemStack.getItemMeta().getLore().contains("ThunderCloud's Happy Little Friend. :)")) {
                itemStack.setAmount(0);
                return ItemState.illegal;
            }
        }

        // Max Enchantment
        for (Enchantment enchantment : itemStack.getEnchantments().keySet()) {
            if (!enchantment.canEnchantItem(itemStack) && !Checks.isArmor(itemStack) && !Checks.isWeapon(itemStack)
                    && itemStack.getEnchantmentLevel(enchantment) > maxLoreEnchantmentLevel) {
                wasFixed = true;

                itemStack.removeEnchantment(enchantment);
                itemStack.addUnsafeEnchantment(enchantment, maxLoreEnchantmentLevel);
            } else if (itemStack.getEnchantmentLevel(enchantment) > enchantment.getMaxLevel()) {
                wasFixed = true;

                itemStack.removeEnchantment(enchantment);
                itemStack.addEnchantment(enchantment, enchantment.getMaxLevel());
            }
        }

        // ShulkerBox Check
        if (checkShulkers && itemStack.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta blockMeta = (BlockStateMeta) itemStack.getItemMeta();

            if (blockMeta.getBlockState() instanceof ShulkerBox) {
                ShulkerBox shulker = (ShulkerBox) blockMeta.getBlockState();

                Inventory inventoryShulker = shulker.getInventory();

                checkInventory(inventoryShulker, location, true);

                shulker.getInventory().setContents(inventoryShulker.getContents());
                blockMeta.setBlockState(shulker);

                // JsonParseException
                try {
                    itemStack.setItemMeta(blockMeta);
                } catch (Exception e) {
                    log("checkItem", "Exception " + e.getMessage());
                }
            }
        }

        // books
        if (itemStack.getType() == Material.WRITTEN_BOOK)
            return ItemState.written_book;

        return wasFixed ? ItemState.wasFixed : ItemState.clean;
    }

    public static void log(String module, String message) {
        instance.getLogger().info("§a[" + module + "] §e" + message + "§r");
    }

    /**
     * fired when the plugin gets enabled
     */
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new Events(), this);
        log("onEnable", "");
    }

    public enum ItemState {
        empty, clean, wasFixed, illegal, written_book;
    }
}
