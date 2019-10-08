package packetproxy.encode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import packetproxy.http.Http;

import java.util.Map;

public class EncodeMsgPack extends EncodeHTTPBase {

    private ObjectMapper msgPackMapper;
    private ObjectMapper jsonMapper;

    public EncodeMsgPack(){
        super();
        MessagePackFactory f = new MessagePackFactory();
        msgPackMapper = new ObjectMapper(f);
        jsonMapper = new ObjectMapper();
    }

    private byte[] ObjToAltObj(byte[] src, ObjectMapper srcObjMapper, ObjectMapper dstObjMapper){
        try{
            Map<String, Object> objMap = srcObjMapper.readValue(src, new TypeReference<Map<String,Object>>(){});
            return dstObjMapper.writeValueAsBytes(objMap);

        }catch (Exception e){
            e.printStackTrace();
        }
        return new byte[0];
    }

    private byte[] cborToJson(byte[] src){
        return ObjToAltObj(src, msgPackMapper, jsonMapper);
    }

    private byte[] jsonToCbor(byte[] src){
        return ObjToAltObj(src, jsonMapper, msgPackMapper);
    }

    @Override
    public String getName() {
        return "MessagePack2JSON";
    }

    @Override
    protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(cborToJson(inputHttp.getBody()));
        return inputHttp;
    }

    @Override
    protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(jsonToCbor(inputHttp.getBody()));
        return inputHttp;
    }

    @Override
    protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(cborToJson(inputHttp.getBody()));
        return inputHttp;
    }

    @Override
    protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(jsonToCbor(inputHttp.getBody()));
        return inputHttp;
    }
}
