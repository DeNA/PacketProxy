/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.common;

import packetproxy.util.CharSetUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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