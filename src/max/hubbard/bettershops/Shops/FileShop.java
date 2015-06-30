package max.hubbard.bettershops.Shops;

import max.hubbard.bettershops.Configurations.Config;
import max.hubbard.bettershops.Configurations.Language;
import max.hubbard.bettershops.Menus.MenuType;
import max.hubbard.bettershops.Menus.ShopMenu;
import max.hubbard.bettershops.Menus.ShopMenus.*;
import max.hubbard.bettershops.ShopManager;
import max.hubbard.bettershops.Shops.Items.FileShopItem;
import max.hubbard.bettershops.Shops.Items.ShopItem;
import max.hubbard.bettershops.Shops.Types.Holo.HologramManager;
import max.hubbard.bettershops.Shops.Types.Holo.ShopHologram;
import max.hubbard.bettershops.Shops.Types.NPC.NPCManager;
import max.hubbard.bettershops.Shops.Types.NPC.ShopsNPC;
import max.hubbard.bettershops.TradeManager;
import max.hubbard.bettershops.Utils.Transaction;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * ***********************************************************************
 * Copyright Max Hubbard (c) 2015. All Rights Reserved.
 * Any code contained within this document, and any associated documents with similar branding
 * are the sole property of Max. Distribution, reproduction, taking snippets, or
 * claiming any contents as your own will break the terms of the license, and void any
 * agreements with you, the third party.
 * ************************************************************************
 */
public class FileShop implements Shop {
    public YamlConfiguration config;
    public File file;
    private OfflinePlayer owner;
    private List<ShopItem> items = new ArrayList<ShopItem>();
    private List<ShopItem> buy = new ArrayList<>();
    private List<ShopItem> sell = new ArrayList<>();
    private List<OfflinePlayer> blacklist = new ArrayList<OfflinePlayer>();
    private List<OfflinePlayer> keepers = new ArrayList<OfflinePlayer>();
    private Location l;
    private HashMap<MenuType, ShopMenu> menus = new HashMap<>();
    private boolean transLoaded;
    private History history;

    public FileShop(YamlConfiguration config, File file, OfflinePlayer owner) {
        this.config = config;
        this.file = file;
        this.owner = owner;

        final FileShop s = this;

        String[] locs = config.getString("Location").split(" ");
        String world = locs[0];
        int start = 1;
        for (int i = 1; i < locs.length; i++){
            try {
                Double.parseDouble(locs[i]);
                start = i;
                break;
            } catch (Exception e){
                world = world + " " + locs[i];
            }
        }

        World w = Bukkit.getWorld(world);

        double x = Double.parseDouble(locs[start]);
        double y = Double.parseDouble(locs[start + 1]);
        double z = Double.parseDouble(locs[start + 2]);

        l = new Location(w, x, y, z);
        history = new History(s);

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("BetterShops"), new Runnable() {
            @Override
            public void run() {

                loadItems();
                loadMenus();
                loadTransactions();
                loadKeepers();
                loadBlacklist();
                TradeManager.loadTrades(s);
            }
        });

    }

    public OfflinePlayer getOwner() {
        return owner;
    }

    public Object getObject(String s) {
        return config.get(s);
    }

    public void setObject(String path, Object obj) {
        config.set(path, obj);
        saveConfig();
    }

    public String getName() {
        return (String) getObject("Name");
    }

    public boolean setName(String name) {
        if (ShopManager.fromString(name) == null) {
            File old = file;
            String oldName = getName();
            File file1 = new File(file.getParentFile().getAbsolutePath(), name + ".yml");
            try {
                FileUtils.moveFile(file, file1);
                file = file1;
                old.delete();
                setObject("Name", name);

                ShopManager.names.remove(oldName);
                ShopManager.names.put(name, this);

                if (isNPCShop()) {
                    getNPCShop().entity.setCustomName("§a§l" + name);
                }
                if (isHoloShop()) {
                    getHolographicShop().getNameLine().setText("§a§l" + name);
                }

            } catch (IOException e) {
                return false;
            }

            loadMenus();
            return true;
        } else {
            return false;
        }
    }

    public boolean setOwner(OfflinePlayer owner) {
        if (!ShopManager.atLimit(owner)) {
            List<Shop> s = ShopManager.playerShops.get(getOwner().getUniqueId());
            s.remove(this);
            List<Shop> s1 = new ArrayList<>();
            if (ShopManager.playerShops.containsKey(owner.getUniqueId())) {
                s1 = ShopManager.playerShops.get(owner.getUniqueId());
            }
            s1.add(this);
            ShopManager.playerShops.put(getOwner().getUniqueId(), s);
            ShopManager.playerShops.put(owner.getUniqueId(), s1);

            File fi = new File(Bukkit.getPluginManager().getPlugin("BetterShops").getDataFolder(), "Shops/" + owner.getUniqueId().toString() + "/" + getName() + ".yml");
            File old = file;
            try {
                FileUtils.moveFile(file, fi);
                file = fi;
                old.delete();
                setObject("Owner", owner.getUniqueId().toString());
                this.owner = owner;
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public void loadItems() {
        if (config.isConfigurationSection("Items")) {
            for (String s : config.getConfigurationSection("Items").getKeys(false)) {
                int id = Integer.parseInt(s);
                ShopItem item = FileShopItem.loadShopItem(this, id);

                items.add(item);
                if (item.isSelling()) {
                    sell.add(item);
                } else {
                    buy.add(item);
                }

            }
        }
    }

    public void loadMenus() {
        menus.clear();
        menus.put(MenuType.OWNER_BUYING, new OwnerBuying(this));
        menus.put(MenuType.OWNER_SELLING, new OwnerSelling(this));
        menus.put(MenuType.MAIN_BUYING, new MainBuying(this));
        menus.put(MenuType.MAIN_SELLING, new MainSelling(this));
        menus.put(MenuType.KEEPER_MANAGER, new KeeperManager(this));
        menus.put(MenuType.SHOP_SETTINGS, new ShopSettings(this));
        menus.put(MenuType.HISTORY, new max.hubbard.bettershops.Menus.ShopMenus.History(this));
        menus.put(MenuType.ITEM_MANAGER_BUYING, new ItemManagerBuying(this));
        menus.put(MenuType.ITEM_MANAGER_SELLING, new ItemManagerSelling(this));
        menus.put(MenuType.LIVE_ECONOMY, new LiveEconomy(this));
        menus.put(MenuType.KEEPER_ITEM_MANAGER, new KeeperItemManager(this));
        menus.put(MenuType.BUY_ITEM, new BuyItem(this));
        menus.put(MenuType.SELL_ITEM, new SellItem(this));
        menus.put(MenuType.NPC_CHOOSE, new NPCChoose(this));
        menus.put(MenuType.NPC_CONFIGURE, new NPCConfigure(this));
        menus.put(MenuType.REARRANGE, new Rearrange(this));
        menus.put(MenuType.AMOUNT_CHOOSER, new AmountChooser(this));
        menus.put(MenuType.CART, new Cart(this));
        menus.put(MenuType.SEARCH_ENGINE, new SearchEngine(this));
        menus.put(MenuType.TRADING, new Trading(this));
        menus.put(MenuType.TRADE_MANAGER, new max.hubbard.bettershops.Menus.ShopMenus.TradeManager(this));
        menus.put(MenuType.TRADE_CONFIRM, new TradeConfirm(this));
        menus.put(MenuType.TRADE_CHOOSE, new TradeChoose(this));
        menus.put(MenuType.PLAYER_BLACKLIST, new PlayerBlacklist(this));
    }

    public ShopMenu getMenu(MenuType type) {
        return menus.get(type);
    }

    public void clearTransactions() {
        if (config.isConfigurationSection("Transactions")) {
            config.set("Transactions", null);
        } else {
            config.createSection("Transactions");
        }

        saveConfig();
    }

    public void loadTransactions() {

        if ((Boolean) Config.getObject("UseTransactions")) {
            if (config.isConfigurationSection("Transactions")) {

                if (history != null) {
                    history.clearAllTransactions();
                }


                Object[] carl = config.getConfigurationSection("Transactions").getKeys(false).toArray();

                for (int i = carl.length; i > 0; i--) {

                    if (carl.length - i < 36) {

                        String s = (String) carl[i - 1];


                        ItemStack item = null;
                        String it = "";
                        Date d = new Date(config.getConfigurationSection("Transactions").getConfigurationSection(s).getLong("Date"));
                        OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(config.getConfigurationSection("Transactions").getConfigurationSection(s).getString("Player")));
                        if (config.getConfigurationSection("Transactions").getConfigurationSection(s).isItemStack("Item")) {
                            item = config.getConfigurationSection("Transactions").getConfigurationSection(s).getItemStack("Item");
                        } else {
                            it = config.getConfigurationSection("Transactions").getConfigurationSection(s).getString("Item");
                        }
                        double price = config.getConfigurationSection("Transactions").getConfigurationSection(s).getDouble("Price");
                        int amount = config.getConfigurationSection("Transactions").getConfigurationSection(s).getInt("Amount");
                        boolean sell = config.getConfigurationSection("Transactions").getConfigurationSection(s).getBoolean("Selling");


                        if (item != null) {
                            ShopItem ite = FileShopItem.fromItemStack(this, item, sell);
                            if (ite != null) {
                                history.addTransaction(p, d, ite, price, amount, sell, false);

                                if (ite.getDisplayName() != null)
                                    config.getConfigurationSection("Transactions").getConfigurationSection(s).set("Item", ite.getDisplayName());
                                else
                                    config.getConfigurationSection("Transactions").getConfigurationSection(s).set("Item", ite.getItem().getType().name());
                            } else {
                                if (item.getItemMeta().getDisplayName() != null) {
                                    config.getConfigurationSection("Transactions").getConfigurationSection(s).set("Item", item.getItemMeta().getDisplayName());
                                    history.addTransaction(p, d, item.getItemMeta().getDisplayName(), price, amount, sell, false);
                                } else {
                                    config.getConfigurationSection("Transactions").getConfigurationSection(s).set("Item", item.getType().name());
                                    history.addTransaction(p, d, item.getType().name(), price, amount, sell, false);
                                }
                            }
                        } else {
                            history.addTransaction(p, d, it, price, amount, sell, false);
                        }
                    } else {
                        String s = (String) carl[i - 1];

                        config.getConfigurationSection("Transactions").set(s, null);
                    }
                }
            } else {
                clearTransactions();
            }

        } else {
            config.createSection("Transactions");

        }

        saveConfig();

        transLoaded = true;
    }

    public void deleteShopItem(ShopItem item) {
        config.getConfigurationSection("Items").set("" + item.getId(), null);

        items.remove(item);

        if (item.isSelling()) {
            this.sell.remove(item);
        } else {
            buy.remove(item);
        }

        saveConfig();

    }

    public void deleteFirstTransaction() {
        if (!config.isConfigurationSection("Transactions")) {
            config.createSection("Transactions");
        } else {

            Object[] carl = config.getConfigurationSection("Transactions").getKeys(false).toArray();

            if (carl.length > 0) {
                config.getConfigurationSection("Transactions").set((String) carl[0], null);
            }
        }
        saveConfig();
    }

    /**
     * @param t    - the Transaction to save
     * @param save - a boolean whether to save to the file or not
     */
    public void saveTransaction(Transaction t, boolean save) {

        if (!config.isConfigurationSection("Transactions")) {
            config.createSection("Transactions");
        }

        int amt = config.getConfigurationSection("Transactions").getKeys(false).size();

        config.getConfigurationSection("Transactions").createSection("" + (amt + 1));

        config.getConfigurationSection("Transactions").getConfigurationSection("" + (amt + 1)).set("Date", t.getDate().getTime());
        config.getConfigurationSection("Transactions").getConfigurationSection("" + (amt + 1)).set("Player", t.getPlayer().getUniqueId().toString());
        config.getConfigurationSection("Transactions").getConfigurationSection("" + (amt + 1)).set("Item", t.getItem());
        config.getConfigurationSection("Transactions").getConfigurationSection("" + (amt + 1)).set("Price", t.getPrice());
        config.getConfigurationSection("Transactions").getConfigurationSection("" + (amt + 1)).set("Amount", t.getAmount());
        config.getConfigurationSection("Transactions").getConfigurationSection("" + (amt + 1)).set("Selling", t.isSell());

        if (save) {
            saveConfig();
        }
    }

    public List<ShopItem> getShopItems() {
        return items;
    }

    public List<ShopItem> getShopItems(boolean sell) {

        if (sell) {
            return this.sell;
        } else {
            return buy;
        }
    }

    public void loadKeepers() {

        if (!config.isConfigurationSection("Managers")) {
            config.createSection("Managers");
            saveConfig();
        } else {

            for (String s : config.getConfigurationSection("Managers").getKeys(false)) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(s));
                keepers.add(p);
            }
        }
    }

    public void addKeeper(OfflinePlayer p) {
        keepers.add(p);
        config.getConfigurationSection("Managers").set(p.getUniqueId().toString(), p.getUniqueId().toString());
        saveConfig();
    }

    public void removeKeeper(OfflinePlayer p) {
        keepers.remove(p);
        config.getConfigurationSection("Managers").set(p.getUniqueId().toString(), null);
        saveConfig();
    }

    public List<OfflinePlayer> getKeepers() {
        return keepers;
    }

    public void loadBlacklist() {

        if (!config.isConfigurationSection("Blacklist")) {
            config.createSection("Blacklist");
            saveConfig();
        } else {

            for (String s : config.getConfigurationSection("Blacklist").getKeys(false)) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(s));
                blacklist.add(p);
            }
        }
    }

    public void addBlacklist(OfflinePlayer p) {
        blacklist.add(p);
        config.getConfigurationSection("Blacklist").set(p.getUniqueId().toString(), p.getUniqueId().toString());
        saveConfig();
    }

    public void removeBlacklist(OfflinePlayer p) {
        blacklist.remove(p);
        config.getConfigurationSection("Blacklist").set(p.getUniqueId().toString(), null);
        saveConfig();
    }

    public List<OfflinePlayer> getBlacklist() {
        return blacklist;
    }

    public byte getFrameColor() {
        if (config.get("Frame") != null) {
            return (byte) config.getInt("Frame");
        } else {
            setObject("Frame", 7);
            return 7;
        }
    }

    public Location getLocation() {
        return l;
    }

    public void setOpen(boolean b) {
        setObject("Open", b);

        if (l.getWorld().getBlockAt(l).getState() instanceof Chest) {
            Chest chest = (Chest) l.getWorld().getBlockAt(l).getState();

            Block block = chest.getBlock();

            Sign sign = null;
            if (block.getRelative(1, 0, 0).getType() == Material.WALL_SIGN) {
                sign = (Sign) block.getRelative(1, 0, 0).getState();
            } else if (block.getRelative(-1, 0, 0).getType() == Material.WALL_SIGN) {
                sign = (Sign) block.getRelative(-1, 0, 0).getState();
            } else if (block.getRelative(0, 0, 1).getType() == Material.WALL_SIGN) {
                sign = (Sign) block.getRelative(0, 0, 1).getState();
            } else if (block.getRelative(0, 0, -1).getType() == Material.WALL_SIGN) {
                sign = (Sign) block.getRelative(0, 0, -1).getState();
            }

            if (sign != null) {
                if (sign.getLine(0).contains(Language.getString("MainGUI", "SignLine1"))) {
                    if (sign.getLine(3).contains(Language.getString("MainGUI", "SignLine4"))) {
                        if (sign.getLine(1).contains(Language.getString("MainGUI", "SignLine2"))) {
                            if (b) {
                                sign.setLine(2, Language.getString("MainGUI", "SignLine3Open"));
                            } else {
                                sign.setLine(2, Language.getString("MainGUI", "SignLine3Closed"));
                            }
                            sign.update();
                        }
                    }
                }
            }
        }
    }

    public History getHistory() {
        return history;
    }

    public ShopHologram getHolographicShop() {
        return HologramManager.getShopHologram(this);
    }

    public ShopsNPC getNPCShop() {
        return NPCManager.getNPCShop(this);
    }

    public boolean isOpen() {
        return config.getBoolean("Open") || config.getString("Open").equalsIgnoreCase("true");
    }

    public boolean isNPCShop() {
        return getObject("NPC") != null && config.getBoolean("NPC") || config.getString("NPC").equalsIgnoreCase("true");
    }

    public boolean isHoloShop() {
        return getObject("Holo") != null && config.getBoolean("Holo") || config.getString("Holo").equalsIgnoreCase("true");
    }

    public boolean isServerShop() {
        return getObject("Server") != null && config.getBoolean("Server") || config.getString("Server").equalsIgnoreCase("true");
    }

    public boolean isNotify() {
        return getObject("Notify") != null && config.getBoolean("Notify") || config.getString("Notify").equalsIgnoreCase("true");
    }

    public ShopItem createShopItem(ItemStack it, int slot, int page, boolean sell) {
        if (FileShopItem.fromItemStack(this, it, sell) == null) {
            ShopItem item = FileShopItem.createShopItem(this, it, getNextAvailableId(), page, slot, sell);
            items.add(item);

            config.getConfigurationSection("Items").createSection("" + item.getId());
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Id", item.getId());
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("ItemStack", item.getItem());
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Page", page);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Slot", slot);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Selling", item.isSelling());
            if (!sell) {
                config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Stock", 1);
            } else {
                config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Stock", 0);
            }
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Amount", 1);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Price", Config.getObject("DefaultPrice"));
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("OrigPrice", Config.getObject("DefaultPrice"));
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("Infinite", false);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("LiveEconomy", false);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("PriceChangePercent", 1);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("DoubleAmount", 750);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("MinimumPrice", 0);
            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("MaximumPrice", Config.getMaxPrice());

            config.getConfigurationSection("Items").getConfigurationSection("" + item.getId()).set("AdjustedPrice", Config.getObject("DefaultPrice"));
            config.set("NextShopId", item.getId() + 1);

            try {
                config.save(file);

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (sell) {
                this.sell.add(item);
            } else {
                buy.add(item);
            }

            return item;
        }
        return null;
    }

    public int getNextAvailableId() {
        return config.getInt("NextShopId");
    }

    public boolean pageFull(int page, boolean sell) {
        return getNumberOfItemsOnPage(page, sell) == 36;
    }

    public int getNumberOfItemsOnPage(int page, boolean sell) {
        int i = 0;
        for (ShopItem item : getShopItems(sell)) {
            if (item.getPage() == page) {
                i++;
            }
        }
        return i;
    }

    public int getNextAvailablePage(boolean sell) {
        int page = 0;

        for (int i = 1; i < 1000; i++) {
            if (!pageFull(i, sell)) {
                page = i;
                break;
            }
        }

        return page;
    }

    public int getNextSlotForPage(int page, boolean sell) {
        List<Integer> slots = new ArrayList<>();
        for (ShopItem item : getShopItems(sell)) {
            if (item.getPage() == page) {
                slots.add(item.getSlot());
            }
        }

        if (slots.size() > 0) {
            for (int i = 18; i < 54; i++) {
                if (!slots.contains(i)) {
                    return i;
                }
            }
        }

        return 18;
    }

    public void saveConfig() {
        try {
            config.save(file);
        } catch (IOException ignored) {

        }
    }
}