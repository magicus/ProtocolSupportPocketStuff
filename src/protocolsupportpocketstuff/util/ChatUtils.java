package protocolsupportpocketstuff.util;

import net.minecraft.server.v1_13_R2.ChatComponentText;
import net.minecraft.server.v1_13_R2.ChatMessage;
import net.minecraft.server.v1_13_R2.ChatModifier;
import net.minecraft.server.v1_13_R2.EnumChatFormat;
import net.minecraft.server.v1_13_R2.IChatBaseComponent;
import org.bukkit.ChatColor;

import java.util.List;

public class ChatUtils {
	public static String toLegacy(IChatBaseComponent s) {
		StringBuilder builder = new StringBuilder();
		legacy(builder, s);
		return builder.toString();
	}

	private static void legacy(StringBuilder builder, IChatBaseComponent s) {
		ChatModifier modifier = s.getChatModifier();
		colorize(builder, modifier);
		if (s instanceof ChatComponentText) {
			builder.append(((ChatComponentText) s).getText());
		} else if (s instanceof ChatMessage) {
			builder.append(((ChatMessage) s).getText());
		} else {
			throw new RuntimeException("[PSPS] Unhandled chatbase type: " + s.getClass().getSimpleName());
		}

		for (IChatBaseComponent c : getExtra(s)) {
			legacy(builder, c);
		}
	}

	private static void colorize(StringBuilder builder, ChatModifier modifier) {
		if (modifier == null) {
			return;
		}

		// Color first
		EnumChatFormat color = getColor(modifier);
		if (color != null) {
			builder.append(color.toString());
		}

		if (isBold(modifier)) {
			builder.append(ChatColor.BOLD);
		}
		if (isItalic(modifier)) {
			builder.append(ChatColor.ITALIC);
		}
		if (isRandom(modifier)) {
			builder.append(ChatColor.MAGIC);
		}
		if (isStrikethrough(modifier)) {
			builder.append(ChatColor.STRIKETHROUGH);
		}
		if (isUnderline(modifier)) {
			builder.append(ChatColor.UNDERLINE);
		}
	}

	// Helpers
	private static List<IChatBaseComponent> getExtra(IChatBaseComponent c) {
		return c.a();
	}

	private static EnumChatFormat getColor(ChatModifier c) {
		return c.getColor();
	}

	private static boolean isBold(ChatModifier c) {
		return c.isBold();
	}

	private static boolean isItalic(ChatModifier c) {
		return c.isItalic();
	}

	private static boolean isStrikethrough(ChatModifier c) {
		return c.isStrikethrough();
	}

	private static boolean isUnderline(ChatModifier c) {
		return c.isUnderlined();
	}

	private static boolean isRandom(ChatModifier c) {
		return c.isRandom();
	}
}