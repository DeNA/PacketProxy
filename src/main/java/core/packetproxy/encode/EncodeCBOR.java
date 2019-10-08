package packetproxy.encode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import packetproxy.http.Http;

import java.util.Map;

public class EncodeCBOR extends EncodeHTTPBase {

    private ObjectMapper cborMapper;
    private ObjectMapper jsonMapper;

    public EncodeCBOR(){
        super();
        CBORFactory f = new CBORFactory();
        cborMapper = new ObjectMapper(f);
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
        return ObjToAltObj(src, cborMapper, jsonMapper);
    }

    private byte[] jsonToCbor(byte[] src){
        return ObjToAltObj(src, jsonMapper, cborMapper);
    }

    @Override
    public String getName() {
        return "CBOR";
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
