package packetproxy.common;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class I18nString {
	
	public static ResourceBundle bundle = ResourceBundle.getBundle("strings");
	//public static Locale currentLocale = Locale.ENGLISH;
	public static Locale currentLocale = Locale.getDefault();
	
	private static String normalize(String message) {
		return message.replace(' ', '_')
				.replace('=', '_')
				.replaceAll(":", "\\:")
				.replaceAll(Pattern.quote("("), "\\(")
				.replaceAll(Pattern.quote(")"), "\\)");
	}
	
	public static Locale getLocale() {
		return currentLocale;
	}
	
	public static String get(String message, Object... args) {
		String localed = get(message);
		try {
			return String.format(localed, args);
		} catch (Exception e) {
			return String.format(message, args);
		}
	}
	
	public static String get(String message) {
		if (currentLocale == Locale.JAPAN) {
			try {
				String localeMsg = bundle.getString(normalize(message));
				return localeMsg.length() > 0 ? localeMsg : message;
			} catch (java.util.MissingResourceException e) {
				return message;
			} catch (Exception e) {
				System.err.println(String.format("[Error] can't read resource: %s", message));
				return message;
			}
		}
		return message;
	}

}
