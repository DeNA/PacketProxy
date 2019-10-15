package packetproxy.util;

import java.util.Arrays;
import java.util.List;

public class CharSetUtility {
    private static CharSetUtility instance=null;
    private String charSet="";
    private List<String> availableCharSetList = Arrays.asList(new String[]{"UTF-8", "Shift_JIS", "x-euc-jp-linux", "ISO-2022-JP", "ISO-8859-1"});

    public static CharSetUtility getInstance(){
        if(null==instance){
            instance = new CharSetUtility();
            instance.charSet = instance.getAvailableCharSetList().get(0);
        }
        return instance;
    }

    public void setCharSet(String charSet){
        if(availableCharSetList.contains(charSet)){
            this.charSet = charSet;
        }else{
            //TODO: Throw Exception
            PacketProxyUtility.getInstance().packetProxyLog(String.format("%s is not support charset", charSet));
        }
    }

    public String getCharSet(){
        return charSet;
    }

    public List<String> getAvailableCharSetList(){
        return availableCharSetList;
    }

}
