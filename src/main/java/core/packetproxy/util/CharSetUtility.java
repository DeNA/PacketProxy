package packetproxy.util;

import packetproxy.model.CharSet;
import packetproxy.model.CharSets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CharSetUtility {
    private static CharSetUtility instance=null;
    private static String DEFAULT_CHARSET = "UTF-8";
    private String charSet=DEFAULT_CHARSET;

    public static CharSetUtility getInstance(){
        if(null==instance){
            instance = new CharSetUtility();
            instance.charSet = instance.getAvailableCharSetList().get(0);
        }
        return instance;
    }

    public void setCharSet(String charSet){
        if(getAvailableCharSetList().contains(charSet)){
            this.charSet = charSet;
        }else{
            //TODO: Throw Exception
            PacketProxyUtility.getInstance().packetProxyLog(String.format("%s is not supported charset", charSet));
        }
    }

    public String getCharSet(){
        return charSet;
    }

    public List<String> getAvailableCharSetList(){
        List<String> ret = new ArrayList<>();
        try {
            for(CharSet charset: CharSets.getInstance().queryAll()){
                ret.add(charset.toString());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return ret;
    }

}
