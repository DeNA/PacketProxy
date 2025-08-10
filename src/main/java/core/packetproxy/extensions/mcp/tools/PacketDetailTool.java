package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

public class PacketDetailTool extends AuthenticatedMCPTool {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final Gson gson = new Gson();

	@Override
	public String getName() {
		return "get_packet_detail";
	}

	@Override
	public String getDescription() {
		return "Get detailed information about a specific packet";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject packetIdProp = new JsonObject();
		packetIdProp.addProperty("type", "integer");
		packetIdProp.addProperty("description", "ID of the packet to retrieve");
		schema.add("packet_id", packetIdProp);

		JsonObject includeBodyProp = new JsonObject();
		includeBodyProp.addProperty("type", "boolean");
		includeBodyProp.addProperty("description", "Whether to include request/response body");
		includeBodyProp.addProperty("default", false);
		schema.add("include_body", includeBodyProp);

		JsonObject includePairProp = new JsonObject();
		includePairProp.addProperty("type", "boolean");
		includePairProp.addProperty("description",
				"Whether to include paired packet (request when response specified, response when request specified)");
		includePairProp.addProperty("default", true);
		schema.add("include_pair", includePairProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("PacketDetailTool called with arguments: " + arguments.toString());

		if (!arguments.has("packet_id")) {
			throw new Exception("packet_id is required");
		}

		int packetId = arguments.get("packet_id").getAsInt();
		boolean includeBody = arguments.has("include_body") && arguments.get("include_body").getAsBoolean();
		boolean includePair = !arguments.has("include_pair") || arguments.get("include_pair").getAsBoolean();

		try {
			Packets packets = Packets.getInstance();
			Packet packet = packets.query(packetId);

			if (packet == null) {
				throw new Exception("Packet not found: " + packetId);
			}

			JsonObject data = buildPacketDetail(packet, includeBody, includePair);

			JsonObject content = new JsonObject();
			content.addProperty("type", "text");
			content.addProperty("text", gson.toJson(data));

			JsonArray contentArray = new JsonArray();
			contentArray.add(content);

			JsonObject result = new JsonObject();
			result.add("content", contentArray);

			log("PacketDetailTool returning packet " + packetId);
			return result;

		} catch (Exception e) {
			log("PacketDetailTool error: " + e.getMessage());
			throw new Exception("Failed to get packet detail: " + e.getMessage());
		}
	}

	private JsonObject buildPacketDetail(Packet packet, boolean includeBody, boolean includePair) throws Exception {
		JsonObject result = new JsonObject();

		if (includePair) {
			// Try to find the paired packet (request/response)
			Packet pairedPacket = findPairedPacket(packet);

			if (pairedPacket != null) {
				// Build paired request/response structure
				Packet requestPacket = (packet.getDirection() == Packet.Direction.CLIENT) ? packet : pairedPacket;
				Packet responsePacket = (packet.getDirection() == Packet.Direction.SERVER) ? packet : pairedPacket;

				// Request details
				JsonObject request = buildSinglePacketDetail(requestPacket, includeBody, "request");
				result.add("request", request);

				// Response details
				JsonObject response = buildSinglePacketDetail(responsePacket, includeBody, "response");
				result.add("response", response);

				// Add pairing information
				result.addProperty("paired", true);
				result.addProperty("requested_packet_id", packet.getId());
				result.addProperty("group", packet.getGroup());
				result.addProperty("conn", packet.getConn());
			} else {
				// Single packet (no pair found)
				JsonObject singlePacket = buildSinglePacketDetail(packet, includeBody,
						packet.getDirection() == Packet.Direction.CLIENT ? "request" : "response");
				if (packet.getDirection() == Packet.Direction.CLIENT) {
					result.add("request", singlePacket);
					result.add("response", null);
				} else {
					result.add("request", null);
					result.add("response", singlePacket);
				}
				result.addProperty("paired", false);
				result.addProperty("requested_packet_id", packet.getId());
				result.addProperty("group", packet.getGroup());
				result.addProperty("conn", packet.getConn());
			}
		} else {
			// Return only the requested packet
			JsonObject singlePacket = buildSinglePacketDetail(packet, includeBody,
					packet.getDirection() == Packet.Direction.CLIENT ? "request" : "response");
			if (packet.getDirection() == Packet.Direction.CLIENT) {
				result.add("request", singlePacket);
				result.add("response", null);
			} else {
				result.add("request", null);
				result.add("response", singlePacket);
			}
			result.addProperty("paired", false);
			result.addProperty("requested_packet_id", packet.getId());
			result.addProperty("group", packet.getGroup());
			result.addProperty("conn", packet.getConn());
		}

		return result;
	}

	private Packet findPairedPacket(Packet packet) throws Exception {
		Packets packets = Packets.getInstance();

		// Look for a packet with same group and conn but opposite direction
		Packet.Direction targetDirection = (packet.getDirection() == Packet.Direction.CLIENT)
				? Packet.Direction.SERVER
				: Packet.Direction.CLIENT;

		// Search through packets with same group
		// Note: This is a simple implementation. In a real system, you might want to
		// add specific query methods to Packets class for better performance
		List<Packet> allPackets = packets.queryAll();
		for (Packet p : allPackets) {
			if (p.getGroup() == packet.getGroup() && p.getConn() == packet.getConn()
					&& p.getDirection() == targetDirection && p.getId() != packet.getId()) {
				return p;
			}
		}
		return null;
	}

	private JsonObject buildSinglePacketDetail(Packet packet, boolean includeBody, String type) throws Exception {
		JsonObject result = new JsonObject();

		// Basic packet info
		result.addProperty("id", packet.getId());
		result.addProperty("length", packet.getDecodedData().length);
		result.addProperty("time", dateFormat.format(packet.getDate()));
		result.addProperty("resend", packet.getResend());
		result.addProperty("modified", packet.getModified());
		result.addProperty("type", packet.getContentType());
		result.addProperty("encode", packet.getEncoder());
		result.addProperty("direction", packet.getDirection().toString().toLowerCase());

		// Client/Server info
		JsonObject client = new JsonObject();
		client.addProperty("ip", packet.getClientIP());
		client.addProperty("port", packet.getClientPort());
		result.add("client", client);

		JsonObject server = new JsonObject();
		server.addProperty("ip", packet.getServerIP());
		server.addProperty("port", packet.getServerPort());
		result.add("server", server);

		// Parse HTTP data if possible
		try {
			String data = new String(packet.getDecodedData(), StandardCharsets.UTF_8);
			parseHttpData(result, data, includeBody);
		} catch (Exception e) {
			// Not HTTP or parsing failed, include raw data
			if (includeBody) {
				result.addProperty("raw_data", new String(packet.getDecodedData(), StandardCharsets.UTF_8));
			}
		}

		return result;
	}

	private void parseHttpData(JsonObject result, String data, boolean includeBody) {
		String[] parts = data.split("\r\n\r\n", 2);
		if (parts.length == 0)
			return;

		String headers = parts[0];
		String body = parts.length > 1 ? parts[1] : "";

		String[] lines = headers.split("\r\n");
		if (lines.length == 0)
			return;

		// Parse request/response line
		String firstLine = lines[0];
		if (firstLine.startsWith("HTTP/")) {
			// Response
			String[] statusParts = firstLine.split(" ", 3);
			if (statusParts.length >= 2) {
				try {
					int status = Integer.parseInt(statusParts[1]);
					result.addProperty("status", status);
					if (statusParts.length >= 3) {
						result.addProperty("status_text", statusParts[2]);
					}
				} catch (NumberFormatException e) {
					// Invalid status code
				}
			}
		} else {
			// Request
			String[] requestParts = firstLine.split(" ", 3);
			if (requestParts.length >= 2) {
				result.addProperty("method", requestParts[0]);
				result.addProperty("url", requestParts[1]);
				if (requestParts.length >= 3) {
					result.addProperty("version", requestParts[2]);
				}
			}
		}

		// Parse headers
		JsonArray headersArray = new JsonArray();
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i];
			int colonIndex = line.indexOf(':');
			if (colonIndex > 0) {
				JsonObject header = new JsonObject();
				header.addProperty("name", line.substring(0, colonIndex).trim());
				header.addProperty("value", line.substring(colonIndex + 1).trim());
				headersArray.add(header);
			}
		}
		result.add("headers", headersArray);

		// Include body if requested
		if (includeBody && !body.isEmpty()) {
			result.addProperty("body", body);
		}
	}
}
