package protocolsupportpocketstuff.packet.play;

import io.netty.buffer.ByteBuf;
import protocolsupport.protocol.ConnectionImpl;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.utils.CollectionsUtils;
import protocolsupportpocketstuff.packet.PEPacket;

import java.util.List;

public class SpawnEntityPacket extends PEPacket {

	private long entityId;
	private String entityType;
	private float x;
	private float y;
	private float z;
	private float motionX;
	private float motionY;
	private float motionZ;
	private float pitch;
	private float yaw;
	private float headYaw;
	private List<SetAttributesPacket.Attribute> attributes;
	//private CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata;

	public SpawnEntityPacket() { }

	public SpawnEntityPacket(long entityId, String entityType, float x, float y, float z,
							 float motionX, float motionY, float motionZ, float pitch, float yaw,
							 float headYaw, List<SetAttributesPacket.Attribute> attributes, CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata) {
		this.entityId = entityId;
		this.entityType = entityType;
		this.x = x;
		this.y = y;
		this.z = z;
		this.motionX = motionX;
		this.motionY = motionY;
		this.motionZ = motionZ;
		this.pitch = pitch;
		this.yaw = yaw;
		this.headYaw = headYaw;
		this.attributes = attributes;
		//this.metadata = metadata;
	}

	@Override
	public int getPacketId() {
		return PEPacketIDs.SPAWN_ENTITY;
	}

	@Override
	public void toData(ConnectionImpl connection, ByteBuf serializer) {
		VarNumberSerializer.writeSVarLong(serializer, entityId); // entity ID
		VarNumberSerializer.writeVarLong(serializer, entityId); // runtime ID
		StringSerializer.writeString(serializer, connection.getVersion(), entityType);
		serializer.writeFloatLE(x);
		serializer.writeFloatLE(y);
		serializer.writeFloatLE(z);
		serializer.writeFloatLE(motionX);
		serializer.writeFloatLE(motionY);
		serializer.writeFloatLE(motionZ);
		serializer.writeFloatLE(pitch);
		serializer.writeFloatLE(yaw);
		serializer.writeFloatLE(headYaw);
		// We can't use SetAttributePackets#encodeAttributes because MCPE uses an different format in SpawnEntityPacket (why mojang?)
		VarNumberSerializer.writeVarInt(serializer, attributes.size());
		for (SetAttributesPacket.Attribute attribute : attributes) {
			StringSerializer.writeString(serializer, connection.getVersion(), attribute.getName());
			serializer.writeFloatLE(attribute.getMinimum());
			serializer.writeFloatLE(attribute.getValue());
			serializer.writeFloatLE(attribute.getMaximum());
		}
		//TODO: fix
		VarNumberSerializer.writeVarInt(serializer, 0); // metadata count
		//EntityMetadata.encodeMeta(serializer, connection.getVersion(), I18NData.DEFAULT_LOCALE, metadata);
		VarNumberSerializer.writeVarInt(serializer, 0); //links, not used
	}

	@Override
	public void readFromClientData(ConnectionImpl connection, ByteBuf clientdata) {
		throw new UnsupportedOperationException();
	}

}