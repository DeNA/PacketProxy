package packetproxy.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import packetproxy.util.CharSetUtility;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class CharSetUtilityTest {
    @Test
    public void testCountChar() {
        String header = "HTTP/1.1 302 Moved Temporarily\nContent-Type: text/html; charset=utf-8\nConnection: keep-alive\n";
        String a = CharSetUtility.getInstance().guessCharSetFromHttpHeader(header.getBytes());
        assertEquals("utf-8", a);
        String header2 = "HTTP/1.1 302 Moved Temporarily\nContent-Type: text/html; charset=utf-8";
        String a2 = CharSetUtility.getInstance().guessCharSetFromHttpHeader(header2.getBytes());
        assertEquals("utf-8", a2);

        String html5 = "<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>test</title></head></html>";
        String b = CharSetUtility.getInstance().guessCharSetFromMetatag(html5.getBytes());
        assertEquals("UTF-8", b);
        String html4 = "<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n<title>test</title></head></html>";
        String c = CharSetUtility.getInstance().guessCharSetFromMetatag(html4.getBytes());
        assertEquals("UTF-8", c);
        String html4_2 = "<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8;\">\n<title>test</title></head></html>";
        String c2 = CharSetUtility.getInstance().guessCharSetFromMetatag(html4_2.getBytes());
        assertEquals("UTF-8", c2);
        String html4_3 = "<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"charset=UTF-8;text/html\">\n<title>test</title></head></html>";
        String c3 = CharSetUtility.getInstance().guessCharSetFromMetatag(html4_2.getBytes());
        assertEquals("UTF-8", c3);
    }
}