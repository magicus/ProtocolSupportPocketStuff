package protocolsupportpocketstuff.hacks.playerheads;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.Material;
import protocolsupport.api.Connection;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.libs.com.google.gson.JsonObject;
import protocolsupport.protocol.serializer.ArraySerializer;
import protocolsupport.protocol.serializer.ItemStackSerializer;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.PositionSerializer;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.typeremapper.pe.PESkinModel;
import protocolsupport.protocol.utils.NBTTagCompoundSerializer;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectFloatLe;
import protocolsupport.protocol.utils.i18n.I18NData;
import protocolsupport.protocol.utils.types.Position;
import protocolsupport.utils.CollectionsUtils;
import protocolsupport.zplatform.itemstack.ItemStackWrapper;
import protocolsupport.zplatform.itemstack.NBTTagCompoundWrapper;
import protocolsupport.zplatform.itemstack.NBTTagType;
import protocolsupportpocketstuff.ProtocolSupportPocketStuff;
import protocolsupportpocketstuff.api.util.PocketCon;
import protocolsupportpocketstuff.packet.TileDataUpdatePacket;
import protocolsupportpocketstuff.packet.play.EntityDestroyPacket;
import protocolsupportpocketstuff.packet.play.SpawnPlayerPacket;
import protocolsupportpocketstuff.storage.Skins;
import protocolsupportpocketstuff.util.StuffUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.SplittableRandom;
import java.util.UUID;

public class PlayerHeadsPacketListener extends Connection.PacketListener {
	private ProtocolSupportPocketStuff plugin;
	private Connection con;
	private boolean isSpawned = false;
	private final HashMap<Long, CachedSkullBlock> cachedSkullBlocks = new HashMap<Long, CachedSkullBlock>();
	private static final int SKULL_BLOCK_ID = 144;

	public PlayerHeadsPacketListener(ProtocolSupportPocketStuff plugin, Connection con) {
		this.plugin = plugin;
		this.con = con;
	}

	@Override
	public void onRawPacketReceiving(RawPacketEvent event) {
		ByteBuf data = event.getData();
		int packetId = VarNumberSerializer.readVarInt(data);

		data.readByte();
		data.readByte();

		if (packetId == PEPacketIDs.PLAYER_MOVE) {
			if (isSpawned)
				return;

			isSpawned = true;

			for (CachedSkullBlock cachedSkullBlock : cachedSkullBlocks.values()) {
				if (cachedSkullBlock.isCustomSkull()) {
					cachedSkullBlock.spawn(this);
				}
			}
			return;
		}
	}

	@Override
	public void onRawPacketSending(RawPacketEvent event) {
		ByteBuf data = event.getData();
		int packetId = VarNumberSerializer.readVarInt(data);

		data.readByte();
		data.readByte();

		if (packetId == PEPacketIDs.CHANGE_DIMENSION) {
			System.out.println("CHANGE DIMENSION!!!");
			for (CachedSkullBlock cachedSkullBlock : cachedSkullBlocks.values()) {
				System.out.println("Killing skull head with ID: " + cachedSkullBlock.getEntityId());
				PocketCon.sendPocketPacket(con, new EntityDestroyPacket(cachedSkullBlock.getEntityId()));
			}
			cachedSkullBlocks.clear();
			return;
		}
		if (packetId == PEPacketIDs.MOB_ARMOR_EQUIPMENT) {
			long entityId = VarNumberSerializer.readVarLong(data);

			ItemStackWrapper itemStack = ItemStackSerializer.readItemStack(data, con.getVersion(), I18NData.DEFAULT_LOCALE, true);

			NBTTagCompoundWrapper tag = itemStack.getTag();

			if (itemStack.isNull() || itemStack.getType() != Material.SKULL_ITEM)
				return;

			if (tag.getIntNumber("SkullType") != 3) // We only care about player heads
				return;

			if (!tag.hasKeyOfType("Owner", NBTTagType.COMPOUND))
				return;

			NBTTagCompoundWrapper owner = tag.getCompound("Owner");

			if (!owner.hasKeyOfType("Properties", NBTTagType.COMPOUND))
				return;

			String signature = owner.getList("textures").getCompound(0).getString("Signature");
			String value = owner.getList("textures").getCompound(0).getString("Value");

			String _json = new String(Base64.getDecoder().decode(value));

			JsonObject json = StuffUtils.JSON_PARSER.parse(_json).getAsJsonObject();
			String skinUrl = json.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString();

			System.out.println("Skin URL: " + skinUrl);
			return;
		}
		if (packetId == PEPacketIDs.TILE_DATA_UPDATE) {
			Position position = PositionSerializer.readPEPosition(data); // position, we don't care about it, we only care about the position in the compound tag
			try {
				NBTTagCompoundWrapper tag = NBTTagCompoundSerializer.readPeTag(data, true);

				if (!isSkull(tag))
					return;

				handleSkull(position, tag);

				tag.remove("Owner");
				event.setData(new TileDataUpdatePacket(position.getX(), position.getY(), position.getZ(), tag).encode(con));
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		if (packetId == PEPacketIDs.UPDATE_BLOCK) {
			Position position = PositionSerializer.readPEPosition(data);
			int id = VarNumberSerializer.readVarInt(data);

			System.out.println("Updating block...");

			if (id == SKULL_BLOCK_ID)
				return;

			System.out.println("It isn't an skull, wow...");

			long asLong = asLong(position.getX(), position.getY(), position.getZ());

			if (!cachedSkullBlocks.containsKey(asLong))
				return;

			System.out.println("And it is cached! Killing it right now :O");

			cachedSkullBlocks.get(asLong).destroy(this);
			cachedSkullBlocks.remove(asLong);
			return;
		}
		if (packetId == PEPacketIDs.CHUNK_DATA) {
			VarNumberSerializer.readSVarInt(data); // chunk X
			VarNumberSerializer.readSVarInt(data); // chunk Z
			VarNumberSerializer.readVarInt(data); // length
			int sectionLength = data.readByte();
			// System.out.println("Section length: " + sectionLength);
			// data.skipBytes(10241 * sectionLength);
			for (int idx = 0; sectionLength > idx; idx++) {
				data.readByte(); // storage type
				data.skipBytes(4096 + 2048); // skip data, we don't care about that
			}
			data.skipBytes(512); // heights
			data.skipBytes(256); // biomes
			data.readByte(); // borders
			VarNumberSerializer.readSVarInt(data); // extra data
			while (data.readableBytes() != 0) {
				handleSkull(null, ItemStackSerializer.readTag(data, true, con.getVersion()));
			}
		}
	}

	public static long asLong(int x, int y, int z) {
		return ((x & 0x3FFFFFFL) << 38) | ((y & 0xFFFL) << 26) | (z & 0x3FFFFFFL);
	}

	public boolean isSkull(NBTTagCompoundWrapper tag) {
		return tag.getString("id").equals("Skull");
	}

	public String getUrlFromSkull(NBTTagCompoundWrapper tag) {
		if (!tag.getString("id").equals("Skull"))
			return null;

		if (!tag.hasKeyOfType("Owner", NBTTagType.COMPOUND))
			return null;

		NBTTagCompoundWrapper owner = tag.getCompound("Owner");

		if (!owner.hasKeyOfType("Properties", NBTTagType.COMPOUND))
			return null;

		NBTTagCompoundWrapper properties = owner.getCompound("Properties");

		if (!properties.hasKeyOfType("textures", NBTTagType.LIST))
			return null;

		String value = properties.getList("textures").getCompound(0).getString("Value");

		String _json = new String(Base64.getDecoder().decode(value));

		JsonObject json = StuffUtils.JSON_PARSER.parse(_json).getAsJsonObject();

		return json.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString();
	}

	public void handleSkull(Position position, NBTTagCompoundWrapper tag) {
		if (position == null && tag == null) {
			throw new RuntimeException("Both Position and NBTTagCompoundWrapper are null!");
		}
		if (position == null && tag != null) {
			int x = tag.getIntNumber("x");
			int y = tag.getIntNumber("y");
			int z = tag.getIntNumber("z");

			position = new Position(x, y, z);
		}

		CachedSkullBlock cachedSkullBlock = cachedSkullBlocks.getOrDefault(position.asLong(), new CachedSkullBlock(position));

		if (tag != null) {
			String url = getUrlFromSkull(tag);

			if (url != null) {
				cachedSkullBlock.url = url;
				cachedSkullBlock.tag = tag;
			}
		}

		cachedSkullBlocks.put(position.asLong(), cachedSkullBlock);

		if (!isSpawned)
			return;

		if (!cachedSkullBlock.isCustomSkull())
			return;

		if (cachedSkullBlock.isSpawned) {
			cachedSkullBlock.destroy(this);
		}

		cachedSkullBlock.spawn(this);
	}

	protected static void writeSkinData(ProtocolVersion version, ByteBuf serializer, boolean isSkinUpdate, boolean isSlim, byte[] skindata) {
		PESkinModel model = PESkinModel.getSkinModel(isSlim);
		if (isSkinUpdate) {
			StringSerializer.writeString(serializer, version, "6bcfb27d-7e0f-466d-a3d3-3764223e8c3b_Custom");
		}
		StringSerializer.writeString(serializer, version, "geometry.Mobs.Skeleton2");
		if (isSkinUpdate) {
			//TODO: find out how it is used and if its use matters.
			StringSerializer.writeString(serializer, version, "Steve");
		}
		ArraySerializer.writeByteArray(serializer, version, skindata);
		ArraySerializer.writeByteArray(serializer, version, new byte[0]); //cape data
		StringSerializer.writeString(serializer, version, "geometry.Mobs.Skeleton2");
		try {
			StringSerializer.writeString(serializer, version, FileUtils.readFileToString(new File("D:\\armor_stand.json")));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static byte[] toData(BufferedImage skin) {
		Validate.isTrue(skin.getWidth() == 64, "Must be 64 pixels wide");
		Validate.isTrue((skin.getHeight() == 64) || (skin.getHeight() == 32), "Must be 64 or 32 pixels high");
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		for (int y = 0; y < skin.getHeight(); y++) {
			for (int x = 0; x < skin.getWidth(); x++) {
				Color color = new Color(skin.getRGB(x, y), true);
				stream.write(color.getRed());
				stream.write(color.getGreen());
				stream.write(color.getBlue());
				stream.write(color.getAlpha());
			}
		}
		return stream.toByteArray();
	}

	static class CachedSkullBlock {
		private long entityId = new SplittableRandom().nextLong(Integer.MAX_VALUE, Long.MAX_VALUE);
		private Position position;
		private NBTTagCompoundWrapper tag;
		private String url;
		private int dataValue = 1; // TODO
		private boolean isSpawned = false;

		public CachedSkullBlock(Position position) {
			this.position = position;
		}

		public boolean isCustomSkull() {
			return tag != null && url != null;
		}

		public long getEntityId() {
			return entityId;
		}

		public void spawn(PlayerHeadsPacketListener listener) {
			isSpawned = true;
			System.out.println("Spawning...");
			if (Skins.INSTANCE.hasPeSkin(url)) {
				sendFakePlayer(listener, url, Skins.INSTANCE.getPeSkin(url));
			} else {
				new Thread() {
					public void run() {
						try {
							System.out.println("URL: " + url);
							BufferedImage image = ImageIO.read(new URL(url));
							byte[] data = toData(image);
							sendFakePlayer(listener, url, data);
							Skins.INSTANCE.cachePeSkin(url, data);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		}

		private void sendFakePlayer(PlayerHeadsPacketListener listener, String url, byte[] data) {
			int x = tag.getIntNumber("x");
			int y = tag.getIntNumber("y");
			int z = tag.getIntNumber("z");
			int rot = tag.getByteNumber("Rot");

			CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = new CollectionsUtils.ArrayMap<>(76);

			metadata.put(39, new DataWatcherObjectFloatLe(1.05f)); // scale
			metadata.put(54, new DataWatcherObjectFloatLe(0.001f)); // bb width
			metadata.put(55, new DataWatcherObjectFloatLe(0.001f)); // bb height

			UUID uuid = UUID.randomUUID();

			float yaw = 0;
			switch (rot) {
				case 0:
					yaw = 180F;
					break;
				case 1:
					yaw = 202.5F;
					break;
				case 2:
					yaw = 225F;
					break;
				case 3:
					yaw = 247.5F;
					break;
				case 4:
					yaw = 270F;
					break;
				case 5:
					yaw = 292.5F;
					break;
				case 6:
					yaw = 315F;
					break;
				case 7:
					yaw = 337.5F;
					break;
				case 8:
					yaw = 0F;
					break;
				case 9:
					yaw = 22.5F;
					break;
				case 10:
					yaw = 45F;
					break;
				case 11:
					yaw = 67.5F;
					break;
				case 12:
					yaw = 90F;
					break;
				case 13:
					yaw = 112.5F;
					break;
				case 14:
					yaw = 135F;
					break;
				case 15:
					yaw = 157.5F;
					break;
			}
			SpawnPlayerPacket packet = new SpawnPlayerPacket(
					uuid,
					"",
					entityId,
					(float) (x + 0.5), y, (float) (z + 0.5), // coordinates
					0, 0, 0, // motion
					0, 0, yaw, // pitch, head yaw & yaw
					metadata
			);

			ByteBuf serializer = Unpooled.buffer();
			VarNumberSerializer.writeVarInt(serializer, PEPacketIDs.PLAYER_SKIN);
			serializer.writeByte(0);
			serializer.writeByte(0);
			MiscSerializer.writeUUID(serializer, uuid);
			writeSkinData(listener.con.getVersion(), serializer, true, false, data);
			PocketCon.sendPocketPacket(listener.con, packet);
			listener.con.sendRawPacket(MiscSerializer.readAllBytes(serializer));

			TileDataUpdatePacket tileDataUpdatePacket = new TileDataUpdatePacket(x, y, z, tag);
			PocketCon.sendPocketPacket(listener.con, tileDataUpdatePacket);
		}

		public void destroy(PlayerHeadsPacketListener listener) {
			isSpawned = false;
			PocketCon.sendPocketPacket(listener.con, new EntityDestroyPacket(entityId));
		}
	}
}
