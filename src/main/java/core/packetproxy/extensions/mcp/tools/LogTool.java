package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import packetproxy.gui.GUILog;

public class LogTool implements MCPTool {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "get_logs";
	}

	@Override
	public String getDescription() {
		return "Get logs from PacketProxy";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject levelProp = new JsonObject();
		levelProp.addProperty("type", "string");
		levelProp.addProperty("description", "Log level filter: debug, info, warn, error");
		levelProp.addProperty("default", "info");
		schema.add("level", levelProp);

		JsonObject limitProp = new JsonObject();
		limitProp.addProperty("type", "integer");
		limitProp.addProperty("description", "Maximum number of log entries to return");
		limitProp.addProperty("default", 100);
		schema.add("limit", limitProp);

		JsonObject sinceProp = new JsonObject();
		sinceProp.addProperty("type", "string");
		sinceProp.addProperty("description", "Start time in ISO 8601 format (e.g., 2025-01-15T00:00:00Z)");
		schema.add("since", sinceProp);

		JsonObject filterProp = new JsonObject();
		filterProp.addProperty("type", "string");
		filterProp.addProperty("description", "Regular expression filter for log messages");
		schema.add("filter", filterProp);

		return schema;
	}

	@Override
	public JsonObject call(JsonObject arguments) throws Exception {
		log("LogTool called with arguments: " + arguments.toString());

		String level = arguments.has("level") ? arguments.get("level").getAsString() : "info";
		int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 100;
		String since = arguments.has("since") ? arguments.get("since").getAsString() : null;
		String filter = arguments.has("filter") ? arguments.get("filter").getAsString() : null;

		// Validate parameters
		if (limit < 1 || limit > 1000) {
			throw new Exception("Limit must be between 1 and 1000");
		}

		if (!isValidLogLevel(level)) {
			throw new Exception("Invalid log level. Use: debug, info, warn, error");
		}

		LocalDateTime sinceDateTime = null;
		if (since != null) {
			try {
				sinceDateTime = LocalDateTime.parse(since.replace("Z", ""), 
					DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
			} catch (DateTimeParseException e) {
				throw new Exception("Invalid date format. Use ISO 8601 format (e.g., 2025-01-15T00:00:00Z)");
			}
		}

		Pattern filterPattern = null;
		if (filter != null && !filter.trim().isEmpty()) {
			try {
				filterPattern = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
			} catch (Exception e) {
				throw new Exception("Invalid regex pattern: " + e.getMessage());
			}
		}

		try {
			// 実際のログ取得処理
			// PacketProxyのログはutil.Loggingを通してGUILogに保存されているため、
			// そこからログエントリを取得する
			List<LogEntry> logEntries = getLogEntriesFromGUILog(level, sinceDateTime, filterPattern, limit);

			JsonArray logsArray = new JsonArray();
			for (LogEntry entry : logEntries) {
				JsonObject logJson = new JsonObject();
				logJson.addProperty("timestamp", dateFormat.format(entry.getTimestamp()));
				logJson.addProperty("level", entry.getLevel());
				logJson.addProperty("message", entry.getMessage());
				logJson.addProperty("thread", entry.getThread());
				logJson.addProperty("class", entry.getClassName());
				logsArray.add(logJson);
			}

			JsonObject data = new JsonObject();
			data.add("logs", logsArray);
			data.addProperty("total_count", logEntries.size());
			data.addProperty("has_more", logEntries.size() >= limit);

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", gson.toJson(data));

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject result = new JsonObject();
			result.add("content", contentArray);

			log("LogTool returning " + logsArray.size() + " log entries");
			return result;

		} catch (Exception e) {
			log("LogTool error: " + e.getMessage());
			throw new Exception("Failed to get logs: " + e.getMessage());
		}
	}

	private boolean isValidLogLevel(String level) {
		return level.equals("debug") || level.equals("info") || level.equals("warn") || level.equals("error");
	}

	private List<LogEntry> getLogEntriesFromGUILog(String level, LocalDateTime since, Pattern filter, int limit) {
		List<LogEntry> entries = new ArrayList<>();
		
		try {
			GUILog guiLog = GUILog.getInstance();
			String logText = guiLog.getLogText();
			
			if (logText != null && !logText.trim().isEmpty()) {
				// ログテキストを行ごとに分析
				String[] lines = logText.split("\n");
				
				for (String line : lines) {
					if (line.trim().isEmpty()) {
						continue;
					}
					
					LogEntry entry = parseLogLine(line.trim());
					if (entry != null) {
						entries.add(entry);
					}
				}
			}
			
			// 最新のログが上に来るようにリバース
			java.util.Collections.reverse(entries);

		} catch (Exception e) {
			log("Error getting log entries: " + e.getMessage());
		}
		
		// フィルタリング適用
		List<LogEntry> filteredEntries = new ArrayList<>();
		for (LogEntry entry : entries) {
			// レベルフィルタ
			if (!matchesLogLevel(entry.getLevel(), level)) {
				continue;
			}
			
			// 時間フィルタ
			if (since != null) {
				LocalDateTime entryTime = LocalDateTime.ofInstant(
					entry.getTimestamp().toInstant(), ZoneId.systemDefault());
				if (entryTime.isBefore(since)) {
					continue;
				}
			}
			
			// 正規表現フィルタ
			if (filter != null && !filter.matcher(entry.getMessage()).find()) {
				continue;
			}
			
			filteredEntries.add(entry);
			
			// 制限チェック
			if (filteredEntries.size() >= limit) {
				break;
			}
		}
		
		return filteredEntries;
	}

	private boolean matchesLogLevel(String entryLevel, String filterLevel) {
		// レベルの優先度: debug < info < warn < error
		int entryPriority = getLogLevelPriority(entryLevel);
		int filterPriority = getLogLevelPriority(filterLevel);
		return entryPriority >= filterPriority;
	}

	private int getLogLevelPriority(String level) {
		switch (level.toLowerCase()) {
			case "debug": return 0;
			case "info": return 1; 
			case "warn": return 2;
			case "error": return 3;
			default: return 1; // デフォルトはinfo
		}
	}

	private LogEntry parseLogLine(String line) {
		try {
			// PacketProxyのログ形式: "yyyy/MM/dd HH:mm:ss       message"
			// util.Loggingの形式に基づく
			if (line.length() < 19) {
				return null; // 最小の日時フォーマット長より短い
			}
			
			String dateTimePart = line.substring(0, 19);
			String messagePart = line.length() > 26 ? line.substring(26) : "";
			
			// 日時をパース
			java.util.Date timestamp;
			try {
				LocalDateTime localDateTime = LocalDateTime.parse(dateTimePart, dtf);
				timestamp = java.util.Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
			} catch (DateTimeParseException e) {
				// 日時パースに失敗した場合は現在時刻を使用
				timestamp = new java.util.Date();
			}
			
			// ログレベルを推定（メッセージ内容から）
			String level = "info"; // デフォルト
			String lowerMessage = messagePart.toLowerCase();
			if (lowerMessage.contains("error") || lowerMessage.contains("exception") || 
				lowerMessage.contains("failed") || lowerMessage.contains("fail")) {
				level = "error";
			} else if (lowerMessage.contains("warn") || lowerMessage.contains("warning")) {
				level = "warn";
			} else if (lowerMessage.contains("debug")) {
				level = "debug";
			}
			
			// スレッド名とクラス名を推定
			String thread = "main"; // デフォルト
			String className = "packetproxy"; // デフォルト
			
			// メッセージからクラス名を抽出を試行
			if (messagePart.contains("MCP")) {
				className = "packetproxy.extensions.mcp";
			} else if (messagePart.contains("Server")) {
				className = "packetproxy.extensions.mcp.MCPServer";
			} else if (messagePart.contains("Tool")) {
				className = "packetproxy.extensions.mcp.tools";
			}
			
			return new LogEntry(timestamp, level, messagePart, thread, className);
			
		} catch (Exception e) {
			// パースに失敗した場合はnullを返す
			return null;
		}
	}

	// ログエントリを表すクラス
	private static class LogEntry {
		private final java.util.Date timestamp;
		private final String level;
		private final String message;
		private final String thread;
		private final String className;

		public LogEntry(java.util.Date timestamp, String level, String message, String thread, String className) {
			this.timestamp = timestamp;
			this.level = level;
			this.message = message;
			this.thread = thread;
			this.className = className;
		}

		public java.util.Date getTimestamp() { return timestamp; }
		public String getLevel() { return level; }
		public String getMessage() { return message; }
		public String getThread() { return thread; }
		public String getClassName() { return className; }
	}
}