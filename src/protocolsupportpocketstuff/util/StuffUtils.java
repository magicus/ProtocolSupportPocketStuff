package protocolsupportpocketstuff.util;

import org.apache.commons.io.IOUtils;
import protocolsupport.protocol.utils.types.Position;
import protocolsupportpocketstuff.ProtocolSupportPocketStuff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class StuffUtils {
	public static final int CHUNK_SIZE = 1048576;
	public static final String SKIN_PROPERTY_NAME = "textures";
	public static final String APPLY_SKIN_ON_JOIN_KEY = "applySkinOnJoin";
	public static final String CLIENT_INFO_KEY = "PEClientInformationMap";
	public static final String CLIENT_UUID_KEY = "clientUniqueId";
	private static final String RESOURCES_DIRECTORY = "resources";

	public static BufferedReader getResource(String name) {
		return new BufferedReader(new InputStreamReader(getResourceAsStream(name), StandardCharsets.UTF_8));
	}

	public static String getResourceAsString(String name) {
		try {
			return IOUtils.toString(getResource(name));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static InputStream getResourceAsStream(String name) {
		return ProtocolSupportPocketStuff.class.getClassLoader().getResourceAsStream(RESOURCES_DIRECTORY + "/" + name);
	}

	public static long convertPositionToLong(Position position) {
		return convertCoordinatesToLong(position.getX(), position.getY(), position.getZ());
	}

	public static long convertCoordinatesToLong(int x, int y, int z) {
		return ((x & 0x3FFFFFFL) << 38) | ((y & 0xFFFL) << 26) | ((z & 0x3FFFFFFL));
	}
}
