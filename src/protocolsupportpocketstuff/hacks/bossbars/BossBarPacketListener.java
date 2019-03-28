package protocolsupportpocketstuff.hacks.bossbars;

import net.minecraft.server.v1_13_R2.IChatBaseComponent;
import net.minecraft.server.v1_13_R2.PacketPlayOutBoss;
import protocolsupport.api.Connection;
import protocolsupport.protocol.packet.middleimpl.clientbound.play.v_pe.EntityMetadata.PeMetaBase;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectFloatLe;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectSVarLong;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectString;
import protocolsupport.utils.CollectionsUtils;
import protocolsupportpocketstuff.api.util.PocketCon;
import protocolsupportpocketstuff.packet.play.BossEventPacket;
import protocolsupportpocketstuff.packet.play.EntityDataPacket;
import protocolsupportpocketstuff.packet.play.EntityDestroyPacket;
import protocolsupportpocketstuff.packet.play.SetAttributesPacket;
import protocolsupportpocketstuff.packet.play.SpawnEntityPacket;
import protocolsupportpocketstuff.util.ChatUtils;
import protocolsupportpocketstuff.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BossBarPacketListener extends Connection.PacketListener {

	private Connection con;
	private HashMap<Long, CachedBossBar> cachedBossBars = new HashMap<>();

	// Reflection stuff
	private static Field BOSS_UUID;
	private static Field BOSS_ACTION;
	private static Field BOSS_TITLE;
	private static Field BOSS_HEALTH;

	// Constants
	private static final String FAKE_BOSS_ENTITY_ID = "minecraft:pig"; // Entity id for a pig, which we fake spawn

	static {
		try {
			BOSS_UUID = PacketPlayOutBoss.class.getDeclaredField("a");
			BOSS_ACTION = PacketPlayOutBoss.class.getDeclaredField("b");
			BOSS_TITLE = PacketPlayOutBoss.class.getDeclaredField("c");
			BOSS_HEALTH = PacketPlayOutBoss.class.getDeclaredField("d");

			BOSS_UUID.setAccessible(true);
			BOSS_ACTION.setAccessible(true);
			BOSS_TITLE.setAccessible(true);
			BOSS_HEALTH.setAccessible(true);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	public BossBarPacketListener(Connection con) {
		this.con = con;
	}

	@Override
	public void onPacketSending(PacketEvent event) {
		super.onPacketSending(event);

		if (!(event.getPacket() instanceof PacketPlayOutBoss))
			return;

		PacketPlayOutBoss packet = (PacketPlayOutBoss) event.getPacket();

		UUID uuid = (UUID) ReflectionUtils.get(BOSS_UUID, packet);
		long unique = uuid.getMostSignificantBits() & Long.MAX_VALUE;

		PacketPlayOutBoss.Action action = (PacketPlayOutBoss.Action) ReflectionUtils.get(BOSS_ACTION, packet);

		if (action == PacketPlayOutBoss.Action.ADD) {
			IChatBaseComponent title = (IChatBaseComponent) ReflectionUtils.get(BOSS_TITLE, packet);
			float percentage = ReflectionUtils.getFloat(BOSS_HEALTH, packet);

			CachedBossBar bossBar = new CachedBossBar(unique, ChatUtils.toLegacy(title), percentage);

			cachedBossBars.put(unique, bossBar);

			bossBar.spawn(this);
			return;
		}
		if (action == PacketPlayOutBoss.Action.REMOVE) {
			if (!cachedBossBars.containsKey(unique))
				return;

		 	CachedBossBar bossBar = cachedBossBars.get(unique);
		 	cachedBossBars.remove(unique);
		 	bossBar.despawn(this);
			return;
		}
		if (action == PacketPlayOutBoss.Action.UPDATE_NAME) {
			if (!cachedBossBars.containsKey(unique))
				return;

			IChatBaseComponent title = (IChatBaseComponent) ReflectionUtils.get(BOSS_TITLE, packet);

			CachedBossBar bossBar = cachedBossBars.get(unique);
			bossBar.title = ChatUtils.toLegacy(title);

			bossBar.updateMetadata(this);
			return;
		}
		if (action == PacketPlayOutBoss.Action.UPDATE_PCT) {
			if (!cachedBossBars.containsKey(unique))
				return;

			CachedBossBar bossBar = cachedBossBars.get(unique);
			float percentage = ReflectionUtils.getFloat(BOSS_HEALTH, packet);
			bossBar.updateHealth(percentage, this);
			return;
		}
	}

	static class CachedBossBar {
		private long unique;
		private String title;
		private float percentage;

		public CachedBossBar(long unique, String title, float health) {
			this.unique = unique;
			this.title = title;
			this.percentage = health;
		}

		private List<SetAttributesPacket.Attribute> getAttributes(float percentage) {
			return Arrays.asList(
				new SetAttributesPacket.Attribute("minecraft:health", 0.0f, 100f,
					percentage * 100, percentage * 100));
		}

		private CollectionsUtils.ArrayMap<DataWatcherObject<?>> getMetadata(String title) {
			// Flag setting inspired by https://github.com/thebigsmileXD/BossBarAPI/blob/master/src/xenialdan/BossBarAPI/API.php
			CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = new CollectionsUtils.ArrayMap<>(76);

			long flags = (1 << PeMetaBase.FLAG_INVISIBLE - 1) ^ (1 << PeMetaBase.FLAG_NO_AI - 1) ^ (1 << PeMetaBase.FLAG_SILENT - 1);
			metadata.put(PeMetaBase.FLAGS, new DataWatcherObjectSVarLong(flags));
			metadata.put(PeMetaBase.NAMETAG, new DataWatcherObjectString(title));
			metadata.put(PeMetaBase.SCALE, new DataWatcherObjectFloatLe(0.0f));
			metadata.put(PeMetaBase.BOUNDINGBOX_WIDTH, new DataWatcherObjectFloatLe(0.0f));
			metadata.put(PeMetaBase.BOUNDINGBOX_HEIGTH, new DataWatcherObjectFloatLe(0.0f));
			return metadata;
		}

		public void spawn(BossBarPacketListener listener) {
			// The magic lies in setting the correct attributes to the spawn packet
			List<SetAttributesPacket.Attribute> attributes = getAttributes(percentage);

			// We propagate the title of the boss bar (the name of the boss) in the metadata
			CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = getMetadata(title);

			SpawnEntityPacket packet = new SpawnEntityPacket(unique, FAKE_BOSS_ENTITY_ID,
				0, -10, 0, // coordinates
				0, 0, 0, // motion
				0, 0, 0, // pitch & yaw
				attributes, metadata);

			PocketCon.sendPocketPacket(listener.con, packet);
			PocketCon.sendPocketPacket(listener.con, new BossEventPacket(unique, BossEventPacket.SHOW));
		}

		public void despawn(BossBarPacketListener listener) {
			PocketCon.sendPocketPacket(listener.con, new EntityDestroyPacket(unique));
			PocketCon.sendPocketPacket(listener.con, new BossEventPacket(unique, BossEventPacket.REMOVE));
		}

		public void updateHealth(float percentage, BossBarPacketListener listener) {
			this.percentage = percentage;
			updateAttributes(listener);
		}

		public void updateMetadata(BossBarPacketListener listener) {
			CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = new CollectionsUtils.ArrayMap<>(PeMetaBase.NAMETAG);
			metadata.put(PeMetaBase.NAMETAG, new DataWatcherObjectString(title));
			PocketCon.sendPocketPacket(listener.con, new EntityDataPacket(unique, metadata));
		}

		public void updateAttributes(BossBarPacketListener listener) {
			PocketCon.sendPocketPacket(listener.con, new SetAttributesPacket(unique, new SetAttributesPacket.Attribute("minecraft:health", 0.0f, 100f, percentage * 100, percentage * 100)));
			PocketCon.sendPocketPacket(listener.con, new BossEventPacket(unique, BossEventPacket.UPDATE));
		}
	}
}
