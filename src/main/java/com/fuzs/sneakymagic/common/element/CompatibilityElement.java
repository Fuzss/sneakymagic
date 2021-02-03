package com.fuzs.sneakymagic.common.element;

import com.fuzs.puzzleslib_sm.config.ConfigManager;
import com.fuzs.puzzleslib_sm.config.deserialize.EntryCollectionBuilder;
import com.fuzs.puzzleslib_sm.element.AbstractElement;
import com.fuzs.puzzleslib_sm.element.side.ICommonElement;
import com.fuzs.puzzleslib_sm.registry.RegistryManager;
import com.fuzs.sneakymagic.SneakyMagic;
import com.fuzs.sneakymagic.common.util.CompatibilityManager;
import com.fuzs.sneakymagic.mixin.accessor.IAbstractArrowEntityAccessor;
import net.minecraft.enchantment.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ObjectHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompatibilityElement extends AbstractElement implements ICommonElement {

    @ObjectHolder(SneakyMagic.MODID + ":" + "plundering")
    public static final Enchantment PLUNDERING = null;
    
    public Set<Enchantment> swordEnchantments;
    public Set<Enchantment> axeEnchantments;
    public Set<Enchantment> tridentEnchantments;
    public Set<Enchantment> bowEnchantments;
    public Set<Enchantment> crossbowEnchantments;
    public Set<Item> swordBlacklist;
    public Set<Item> axeBlacklist;
    public Set<Item> tridentBlacklist;
    public Set<Item> bowBlacklist;
    public Set<Item> crossbowBlacklist;

    @Override
    public String getDescription() {

        return "Most vanilla enchantments can be applied to a lot more tools and weapons.";
    }

    @Override
    public void setupCommon() {

        RegistryManager.get().register("plundering", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentType.create("SHOOTABLE", item -> item instanceof BowItem || item instanceof CrossbowItem)) {});
        this.addListener(this::onArrowLoose);
        this.addListener(this::onItemUseTick);
        this.addListener(this::onLootingLevel);
    }

    @Override
    public void loadCommon() {

        // register after config has been loaded once
        ConfigManager.get().addListener(new CompatibilityManager(this)::load);
    }

    @Override
    public void setupCommonConfig(ForgeConfigSpec.Builder builder) {

        String compatibility = "Additional enchantments to be made usable with ";
        String blacklist = " to be disabled from receiving additional enchantments.";
        addToConfig(builder.comment(compatibility + "swords.").define("Sword Enchantments", getEnchantmentList(Enchantments.IMPALING)), v -> this.swordEnchantments = deserializeToSet(v, ForgeRegistries.ENCHANTMENTS));
        addToConfig(builder.comment(compatibility + "axes.").define("Axe Enchantments", getEnchantmentList(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING, Enchantments.SWEEPING, Enchantments.IMPALING)), v -> this.axeEnchantments = deserializeToSet(v, ForgeRegistries.ENCHANTMENTS));
        addToConfig(builder.comment(compatibility + "tridents.").define("Trident Enchantments", getEnchantmentList(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING, Enchantments.SWEEPING, Enchantments.QUICK_CHARGE)), v -> this.tridentEnchantments = deserializeToSet(v, ForgeRegistries.ENCHANTMENTS));
        addToConfig(builder.comment(compatibility + "bows.").define("Bow Enchantments", getEnchantmentList(Enchantments.PIERCING, Enchantments.MULTISHOT, Enchantments.QUICK_CHARGE)), v -> this.bowEnchantments = deserializeToSet(v, ForgeRegistries.ENCHANTMENTS));
        addToConfig(builder.comment(compatibility + "crossbows.").define("Crossbow Enchantments", getEnchantmentList(Enchantments.FLAME, Enchantments.PUNCH, Enchantments.POWER, Enchantments.INFINITY)), v -> this.crossbowEnchantments = deserializeToSet(v, ForgeRegistries.ENCHANTMENTS));
        addToConfig(builder.comment("Swords" + blacklist).define("Sword Blacklist", new ArrayList<String>()), v -> this.swordBlacklist = deserializeToSet(v, ForgeRegistries.ITEMS));
        addToConfig(builder.comment("Axes" + blacklist).define("Axe Blacklist", new ArrayList<String>()), v -> this.axeBlacklist = deserializeToSet(v, ForgeRegistries.ITEMS));
        addToConfig(builder.comment("Tridents" + blacklist).define("Trident Blacklist", new ArrayList<String>()), v -> this.tridentBlacklist = deserializeToSet(v, ForgeRegistries.ITEMS));
        addToConfig(builder.comment("Bows" + blacklist).define("Bow Blacklist", new ArrayList<String>()), v -> this.bowBlacklist = deserializeToSet(v, ForgeRegistries.ITEMS));
        addToConfig(builder.comment("Crossbows" + blacklist).define("Crossbow Blacklist", new ArrayList<String>()), v -> this.crossbowBlacklist = deserializeToSet(v, ForgeRegistries.ITEMS));
    }

    private static List<String> getEnchantmentList(Enchantment... enchantments) {

        return Stream.of(enchantments)
                .map(ForgeRegistries.ENCHANTMENTS::getKey)
                .filter(Objects::nonNull)
                .map(ResourceLocation::toString)
                .collect(Collectors.toList());
    }

    @Override
    public String[] getCommonDescription() {

        return new String[]{"Only enchantments included by default are guaranteed to work. While any modded enchantments or other vanilla enchantments can work, they are highly unlikely to do so.",
                "The blacklists for each item group are supposed to disable items which can be enchanted, but where the enchantments do not function as expected.",
                EntryCollectionBuilder.CONFIG_STRING};
    }

    private void onArrowLoose(final ArrowLooseEvent evt) {

        // multishot enchantment for bows
        ItemStack stack = evt.getBow();
        if (evt.hasAmmo() && EnchantmentHelper.getEnchantmentLevel(Enchantments.MULTISHOT, stack) > 0) {

            float velocity = BowItem.getArrowVelocity(evt.getCharge());
            if ((double) velocity >= 0.1) {

                PlayerEntity playerentity = evt.getPlayer();
                ItemStack itemstack = playerentity.findAmmo(stack);
                ArrowItem arrowitem = (ArrowItem) (itemstack.getItem() instanceof ArrowItem ? itemstack.getItem() : Items.ARROW);

                for (int i = 0; i < 2; i++) {

                    AbstractArrowEntity abstractarrowentity = arrowitem.createArrow(evt.getWorld(), itemstack, playerentity);
                    // shoot
                    abstractarrowentity.func_234612_a_(playerentity, playerentity.rotationPitch, playerentity.rotationYaw - 10.0F + i * 20.0F, 0.0F, velocity * 3.0F, 1.0F);
                    applyCommonEnchantments(abstractarrowentity, stack);
                    applyPiercingEnchantment(abstractarrowentity, stack);
                    abstractarrowentity.pickupStatus = AbstractArrowEntity.PickupStatus.CREATIVE_ONLY;
                    evt.getWorld().addEntity(abstractarrowentity);
                }
            }
        }
    }

    private void onItemUseTick(final LivingEntityUseItemEvent.Tick evt) {

        Item item = evt.getItem().getItem();
        int duration = evt.getItem().getUseDuration() - evt.getDuration();
        if (item instanceof BowItem && duration < 20 || item instanceof TridentItem && duration < 10) {

            // quick charge enchantment for bows and tridents
            int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.QUICK_CHARGE, evt.getItem());
            evt.setDuration(evt.getDuration() - i);
        }
    }

    private void onLootingLevel(final LootingLevelEvent evt) {

        Entity source = evt.getDamageSource().getImmediateSource();
        if (source instanceof AbstractArrowEntity) {

            if (source instanceof TridentEntity) {

                ItemStack trident = ((IAbstractArrowEntityAccessor) source).callGetArrowStack();
                int level = EnchantmentHelper.getEnchantmentLevel(Enchantments.LOOTING, trident);
                if (level > 0) {

                    evt.setLootingLevel(level);
                }
            } else {


            }
        }
    }

    public static void applyCommonEnchantments(AbstractArrowEntity abstractarrowentity, ItemStack stack) {

        int j = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, stack);
        if (j > 0) {

            abstractarrowentity.setDamage(abstractarrowentity.getDamage() + (double) j * 0.5 + 0.5);
        }

        int k = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, stack);
        if (k > 0) {

            abstractarrowentity.setKnockbackStrength(k);
        }

        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, stack) > 0) {

            abstractarrowentity.setFire(100);
        }
    }

    public static void applyPiercingEnchantment(AbstractArrowEntity abstractarrowentity, ItemStack stack) {

        int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.PIERCING, stack);
        if (i > 0) {

            abstractarrowentity.setPierceLevel((byte) i);
        }
    }

}
