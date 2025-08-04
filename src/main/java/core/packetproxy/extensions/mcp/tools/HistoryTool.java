package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import javax.swing.RowFilter;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.common.FilterTextParser;

public class HistoryTool extends AuthenticatedMCPTool {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "get_history";
	}

	@Override
	public String getDescription() {
		return "Get packet history from PacketProxy";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject limitProp = new JsonObject();
		limitProp.addProperty("type", "integer");
		limitProp.addProperty("description", "Maximum number of packets to return");
		limitProp.addProperty("default", 100);
		schema.add("limit", limitProp);

		JsonObject offsetProp = new JsonObject();
		offsetProp.addProperty("type", "integer");
		offsetProp.addProperty("description", "Number of packets to skip");
		offsetProp.addProperty("default", 0);
		schema.add("offset", offsetProp);

		JsonObject filterProp = new JsonObject();
		filterProp.addProperty("type", "string");
		filterProp.addProperty("description", 
			"PacketProxy Filter syntax for filtering packets. " +
			"Available columns: id, request, response, length, client_ip, client_port, server_ip, server_port, time, resend, modified, type, encode, alpn, group, full_text, full_text_i, method, url, status. " +
			"Operators: == (equals), != (not equals), >= (greater or equal), <= (less or equal), =~ (regex match), !~ (regex not match), && (AND), || (OR). " +
			"Examples: 'method == GET', 'status >= 400', 'url =~ /api/', 'method == POST && status >= 400', 'length > 1000', 'full_text_i =~ authorization'"
		);
		schema.add("filter", filterProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("HistoryTool called with arguments: " + arguments.toString());

		int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 100;
		int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;
		String filter = arguments.has("filter") ? arguments.get("filter").getAsString() : null;

		// Validate parameters
		if (limit < 1 || limit > 1000) {
			throw new Exception("Limit must be between 1 and 1000");
		}
		if (offset < 0) {
			throw new Exception("Offset must be non-negative");
		}

		try {
			Packets packets = Packets.getInstance();
			List<Packet> allPackets = packets.queryAll();
			List<Packet> filteredPackets = allPackets;

			// Apply filter if provided
			if (filter != null && !filter.trim().isEmpty()) {
				filteredPackets = applyFilter(allPackets, filter);
			}

			JsonArray packetsArray = new JsonArray();

			int totalCount = filteredPackets.size();
			int startIndex = Math.min(offset, totalCount);
			int endIndex = Math.min(startIndex + limit, totalCount);

			for (int i = startIndex; i < endIndex; i++) {
				Packet packet = filteredPackets.get(i);
				JsonObject packetJson = convertPacketToJson(packet);
				packetsArray.add(packetJson);
			}

			JsonObject data = new JsonObject();
			data.add("packets", packetsArray);
			data.addProperty("total_count", totalCount);
			data.addProperty("has_more", endIndex < totalCount);
			if (filter != null && !filter.trim().isEmpty()) {
				data.addProperty("filter_applied", filter);
			}

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", gson.toJson(data));

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject result = new JsonObject();
			result.add("content", contentArray);

			log("HistoryTool returning " + packetsArray.size() + " packets (filtered from " + allPackets.size() + " total)");
			return result;

		} catch (Exception e) {
			log("HistoryTool error: " + e.getMessage());
			throw new Exception("Failed to get packet history: " + e.getMessage());
		}
	}

	private List<Packet> applyFilter(List<Packet> packets, String filterText) throws Exception {
		List<Packet> filtered = new ArrayList<>();
		
		try {
			// Parse the filter using FilterTextParser
			RowFilter<Object, Object> rowFilter = FilterTextParser.parse(filterText);
			
			for (Packet packet : packets) {
				// Create a mock table entry to test the filter
				Object[] rowData = createRowDataFromPacket(packet);
				MockTableEntry entry = new MockTableEntry(rowData);
				
				if (rowFilter.include(entry)) {
					filtered.add(packet);
				}
			}
		} catch (Exception e) {
			log("Filter parsing error: " + e.getMessage());
			throw new Exception("Invalid filter syntax: " + e.getMessage());
		}
		
		return filtered;
	}

	private Object[] createRowDataFromPacket(Packet packet) {
		Object[] rowData = new Object[17]; // Based on columnMapper size
		
		rowData[0] = packet.getId(); // id
		
		// Extract request and response data
		try {
			String request = new String(packet.getDecodedData(), "UTF-8");
			rowData[1] = request; // request
			rowData[2] = ""; // response (not available in current packet data)
		} catch (Exception e) {
			rowData[1] = "";
			rowData[2] = "";
		}
		
		rowData[3] = packet.getDecodedData().length; // length
		rowData[4] = packet.getClientIP(); // client_ip
		rowData[5] = packet.getClientPort(); // client_port
		rowData[6] = packet.getServerIP(); // server_ip
		rowData[7] = packet.getServerPort(); // server_port
		rowData[8] = packet.getDate(); // time
		rowData[9] = packet.getResend(); // resend
		rowData[10] = packet.getModified(); // modified
		rowData[11] = packet.getContentType(); // type
		rowData[12] = packet.getEncoder(); // encode
		rowData[13] = ""; // alpn (not available)
		rowData[14] = packet.getGroup(); // group
		rowData[15] = (String) rowData[1]; // full_text (same as request)
		rowData[16] = ((String) rowData[1]).toLowerCase(); // full_text_i (lowercase)
		
		return rowData;
	}

	// Mock table entry class for filter testing
	private static class MockTableEntry extends RowFilter.Entry<Object, Object> {
		private final Object[] data;
		
		public MockTableEntry(Object[] data) {
			this.data = data;
		}
		
		@Override
		public Object getModel() {
			return null;
		}
		
		@Override
		public int getValueCount() {
			return data.length;
		}
		
		@Override
		public Object getValue(int index) {
			return index < data.length ? data[index] : null;
		}
		
		@Override
		public String getStringValue(int index) {
			Object value = getValue(index);
			return value != null ? value.toString() : "";
		}
		
		@Override
		public Object getIdentifier() {
			return null;
		}
	}

	private JsonObject convertPacketToJson(Packet packet) {
		JsonObject packetJson = new JsonObject();

		packetJson.addProperty("id", packet.getId());
		packetJson.addProperty("length", packet.getDecodedData().length);
		packetJson.addProperty("client_ip", packet.getClientIP());
		packetJson.addProperty("client_port", packet.getClientPort());
		packetJson.addProperty("server_ip", packet.getServerIP());
		packetJson.addProperty("server_port", packet.getServerPort());
		packetJson.addProperty("time", dateFormat.format(packet.getDate()));
		packetJson.addProperty("resend", packet.getResend());
		packetJson.addProperty("modified", packet.getModified());
		packetJson.addProperty("type", packet.getContentType());
		packetJson.addProperty("encode", packet.getEncoder());
		packetJson.addProperty("group", packet.getGroup());

		// HTTPの場合、methodとurlとstatusを抽出
		try {
			String request = new String(packet.getDecodedData(), "UTF-8");
			String[] lines = request.split("\n");
			if (lines.length > 0) {
				String[] requestLine = lines[0].split(" ");
				if (requestLine.length >= 2) {
					packetJson.addProperty("method", requestLine[0]);
					packetJson.addProperty("url", requestLine[1]);
				}
				
				// レスポンスの場合、ステータスコードを抽出
				if (requestLine.length >= 3 && requestLine[0].startsWith("HTTP/")) {
					try {
						int status = Integer.parseInt(requestLine[1]);
						packetJson.addProperty("status", status);
					} catch (NumberFormatException e) {
						// ステータスコードが数値でない場合は無視
					}
				}
			}
		} catch (Exception e) {
			// HTTP以外のパケットの場合は無視
		}

		return packetJson;
	}
}
