package com.hexicube.solapple.tracking;

import com.hexicube.solapple.SOLApple;
import com.hexicube.solapple.SOLAppleConfig;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SOLApple.MOD_ID)
public final class MaxHealthHandler {
	private static final boolean HAS_FIRST_AID = ModList.get().isLoaded("firstaid");
	private static final UUID MILESTONE_HEALTH_MODIFIER_ID = UUID.fromString("b20d3436-0d39-4868-96ab-d0a4856e68c6");

	@SubscribeEvent
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		updateFoodHPModifier(event.getPlayer());
	}

	@SubscribeEvent
	public static void onPlayerClone(PlayerEvent.Clone event) {
		var prevModifier = getHealthModifier(event.getOriginal());
		if (prevModifier == null) return;

		updateHealthModifier(event.getPlayer(), prevModifier);
	}

	/** @return whether or not the player reached a new milestone in this update */
	public static boolean updateFoodHPModifier(Player player) {
		if (player.level.isClientSide) return false;

		var prevModifier = getHealthModifier(player);

		int healthPenalty = 2 * (SOLAppleConfig.getBaseHearts() - 10);

		ProgressInfo progressInfo = FoodList.get(player).getProgressInfo();
		int addedHealthFromFood = 0;
		for (SOLAppleConfig.Server.FoodGroupConfig group : progressInfo.completedGroups) addedHealthFromFood += group.hearts * 2;

		double totalHealthModifier = healthPenalty + addedHealthFromFood;
		boolean hasChanged = prevModifier == null || prevModifier.getAmount() != totalHealthModifier;

		AttributeModifier modifier = new AttributeModifier(
			MILESTONE_HEALTH_MODIFIER_ID,
			"Health Gained from Trying New Foods",
			totalHealthModifier,
			AttributeModifier.Operation.ADDITION
		);

		updateHealthModifier(player, modifier);

		return hasChanged;
	}

	@Nullable
	private static AttributeModifier getHealthModifier(Player player) {
		return maxHealthAttribute(player).getModifier(MILESTONE_HEALTH_MODIFIER_ID);
	}

	private static void updateHealthModifier(Player player, AttributeModifier modifier) {
		var oldMax = player.getMaxHealth();

		var attribute = maxHealthAttribute(player);
		attribute.removeModifier(modifier);
		attribute.addPermanentModifier(modifier);

		var newHealth = player.getHealth() * player.getMaxHealth() / oldMax;
		if (!HAS_FIRST_AID) { // This workaround breaks First Aid because it tries to distribute the health change and may kill the player that way.
			// because apparently it doesn't update unless changed
			player.setHealth(1f);
		}
		// adjust current health proportionally to increase in max health
		player.setHealth(newHealth);
	}

	private static AttributeInstance maxHealthAttribute(Player player) {
		return Objects.requireNonNull(player.getAttribute(Attributes.MAX_HEALTH));
	}

	private MaxHealthHandler() {}
}
