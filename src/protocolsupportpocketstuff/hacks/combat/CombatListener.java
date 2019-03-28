package protocolsupportpocketstuff.hacks.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import protocolsupportpocketstuff.api.util.PocketPlayer;

public class CombatListener implements Listener {
	public CombatListener() {
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

		Entity damager = event.getDamager();
		double damage = event.getDamage();

		if (damager instanceof Player) {
			Player player = (Player) damager;
			if (PocketPlayer.isPocketPlayer(player)) {
				// For some goddamned reason, PE damage is ~ 5 times too low.
				double newDamage = damage * 4.96820355142d;
				event.setDamage(newDamage);
			}
		}
	}
}
