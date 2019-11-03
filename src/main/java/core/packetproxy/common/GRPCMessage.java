package packetproxy.common;

import net.arnx.jsonic.JSON;
import org.xbill.DNS.utils.base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;


public class GRPCMessage {
    private static final int GRPC_WEB_FH_DATA = 0b0;
    private static final int GRPC_WEB_FH_TRAILER = 0b10000000;

    public int type;
    public Map<String, Object> message;

    static public List<GRPCMessage> decodeMessages(String base64Str) throws Exception {
        ByteArrayInputStream bio = new ByteArrayInputStream(base64.fromString(base64Str));
        List<GRPCMessage> ret = new ArrayList<>();
        while (bio.available() > 0) {
            ret.add(new GRPCMessage(bio));
        }
        return ret;
    }

    static public String encodeMessages(List<Map<String, Object>> messages) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : messages) {
            sb.append(base64.toString(new GRPCMessage(msg).toBytes()));
        }
        return sb.toString();
    }

    public GRPCMessage(InputStream bio) throws Exception {
        type = bio.read();
        int length = 0;
        for (int i = 0; i < 4; i++) {
            length <<= 8;
            length += bio.read();
        }
        byte[] raw = new byte[length];
        bio.read(raw);
        if (type == GRPC_WEB_FH_DATA) {
            message = JSON.decode(Protobuf3.decode(raw));
        } else if (type == GRPC_WEB_FH_TRAILER) {
            message = new HashMap<>();
            String str = new String(raw);
            message.put("headers", str.split("\r\n"));
        } else {
            throw new RuntimeException("Unknown GRPC Frame Type");
        }
    }

    public GRPCMessage(Map<String, Object> json) {
        type = Integer.parseInt(json.get("type").toString());
        message = (Map<String, Object>) json.get("message");
    }

    public byte[] toBytes() throws Exception {
        byte[] bytes = new byte[0];
        if (type == GRPC_WEB_FH_TRAILER) {
            StringJoiner joiner = new StringJoiner("\r\n", "", "\r\n");
            for (String s : (List<String>) message.get("headers")) {
                joiner.add(s);
            }
            bytes = joiner.toString().getBytes();
        } else if (type == GRPC_WEB_FH_DATA) {
            bytes = Protobuf3.encode(JSON.encode(message));
        } else {
            throw new RuntimeException("Unknown GRPC Frame Type");
        }
        ByteArrayOutputStream bio = new ByteArrayOutputStream();
        bio.write(type);
        ByteBuffer bf = ByteBuffer.allocate(4);
        bio.write(bf.putInt(bytes.length).array());
        bio.write(bytes);
        return bio.toByteArray();
    }
}

