package packetproxy.common;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.Locale;

import javax.swing.JComponent;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;

import packetproxy.model.ConfigInteger;
import packetproxy.model.ConfigString;

@SuppressWarnings("serial")
public class FontManager {
	
	private static FontManager instance;

	public static FontManager getInstance() throws Exception {
		if (instance == null) {
			instance = new FontManager();
		}
		return instance;
	}

	private ConfigString configUIFontName = new ConfigString("UIFontName");
	private ConfigInteger configUIFontSize = new ConfigInteger("UIFontSize");
	private ConfigString configFontName = new ConfigString("FontName");
	private ConfigInteger configFontSize = new ConfigInteger("FontSize");
	private Font uiFont;
	private Font uiCaptionFont;
	private Font font;
	private MultiKeyMap<String, LocaleFontStyles> defaultFonts = new MultiKeyMap<String, LocaleFontStyles>() {
		{
			put(new MultiKey<String>("Windows", Locale.JAPAN.getLanguage()),   new LocaleFontStyles(new FontStyle("SansSerif",13), new FontStyle("ＭＳ ゴシック", 13)));
			put(new MultiKey<String>("Windows", Locale.ENGLISH.getLanguage()), new LocaleFontStyles(new FontStyle("SansSerif",12), new FontStyle("Monospaced", 12)));
			put(new MultiKey<String>("Mac",     Locale.JAPAN.getLanguage()),   new LocaleFontStyles(new FontStyle("SansSerif",12), new FontStyle("Monospaced", 12)));
			put(new MultiKey<String>("Mac",     Locale.ENGLISH.getLanguage()), new LocaleFontStyles(new FontStyle("SansSerif",12), new FontStyle("Monospaced", 12)));
			put(new MultiKey<String>("Default", Locale.ENGLISH.getLanguage()), new LocaleFontStyles(new FontStyle("SansSerif",12), new FontStyle("Monospaced", 12)));
			put(new MultiKey<String>("Default", Locale.JAPAN.getLanguage()), new LocaleFontStyles(new FontStyle("SansSerif",12), new FontStyle("Monospaced", 12)));
        }
    };

	private FontManager() throws Exception {
		createUIFont();
		createFont();
	}
	
	private LocaleFontStyles getLocaleFontStyles() {
		String os = "Default";
		if (Utils.isWindows()) {
			os = "Windows";
		} else if (Utils.isMac()) {
			os = "Mac";
		}
		String lang = "en";
		if (I18nString.getLocale().getLanguage().equals("ja")) {
			lang = "ja";
		}
		return defaultFonts.get(os, lang);
	}

	private void createUIFont() throws Exception {
		LocaleFontStyles lfs = getLocaleFontStyles();

		String uiFontName = configUIFontName.getString();
		if (uiFontName.isEmpty()) {
			uiFontName = lfs.uiFont.fontName;
			configUIFontName.setString(uiFontName);
		}

		int uiFontSize = configUIFontSize.getInteger();
		if (uiFontSize == 0)  {
			uiFontSize = lfs.uiFont.fontSize;
			configUIFontSize.setInteger(uiFontSize);
		}
		
		uiFont = new Font(uiFontName, Font.PLAIN, uiFontSize);
		uiCaptionFont = new Font(uiFontName, Font.BOLD, uiFontSize+2);
	}

	private void createFont() throws Exception {
		LocaleFontStyles lfs = getLocaleFontStyles();

		String fontName = configFontName.getString();
		if (fontName.isEmpty()) {
			fontName = lfs.font.fontName;
			configFontName.setString(fontName);
		}

		int fontSize = configFontSize.getInteger();
		if (fontSize == 0)  {
			fontSize = lfs.font.fontSize;
			configFontSize.setInteger(fontSize);
		}
		
		font = new Font(fontName, Font.PLAIN, fontSize);
	}
	
	private class FontStyle {
		String fontName;
		int fontSize;
		FontStyle(String fontName, int fontSize) {
			this.fontName = fontName;
			this.fontSize = fontSize;
		}
	}
	
	private class LocaleFontStyles {
		FontStyle uiFont;
		FontStyle font;
		LocaleFontStyles(FontStyle uiFont, FontStyle font) {
			this.uiFont = uiFont;
			this.font = font;
		}
	}
	
	public Font getFont() { return this.font; }
	public Font getUIFont() { return this.uiFont; }
	public int  getUIFontHeight(JComponent comp) { return comp.getFontMetrics(this.uiFont).getHeight(); }
	public Font getUICaptionFont() { return this.uiCaptionFont; }

	public void setUIFont(Font font) throws Exception {
		this.configUIFontName.setString(font.getName());
		this.configUIFontSize.setInteger(font.getSize());
		createUIFont();
	}
	public void setFont(Font font) throws Exception {
		this.configFontName.setString(font.getName());
		this.configFontSize.setInteger(font.getSize());
		createFont();
	}
	public void restoreUIFont() throws Exception {
		LocaleFontStyles lfs = getLocaleFontStyles();
		this.configUIFontName.setString(lfs.uiFont.fontName);
		this.configUIFontSize.setInteger(lfs.uiFont.fontSize);
		createUIFont();
	}
	public void restoreFont() throws Exception {
		LocaleFontStyles lfs = getLocaleFontStyles();
		this.configFontName.setString(lfs.font.fontName);
		this.configFontSize.setInteger(lfs.font.fontSize);
		createFont();
	}
}
