/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.quic.service.framegenerator;

import net.luminis.tls.handshake.ClientHello;
import net.luminis.tls.handshake.HandshakeMessage;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.frame.CryptoFrame;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoFramesToMessagesTest {

    private CryptoFramesToMessages stream;
    private byte[] clientHelloBytes;
    private HandshakeMessage clientHello;

    @BeforeEach
    public void before() throws Exception {
        this.stream = new CryptoFramesToMessages();
        this.clientHelloBytes = Hex.decodeHex("010000ed0303ebf8fa56f12939b9584a3896472ec40bb863cfd3e86804fe3a47f06a2b69484c00000413011302010000c000000010000e00000b6578616d706c652e636f6dff01000100000a00080006001d0017001800100007000504616c706e000500050100000000003300260024001d00209370b2c9caa47fbabaf4559fedba753de171fa71f50f1ce15d43e994ec74d748002b0003020304000d0010000e0403050306030203080408050806002d00020101001c00024001003900320408ffffffffffffffff05048000ffff07048000ffff0801100104800075300901100f088394c8f03e51570806048000ffff".toCharArray());
        this.clientHello = CryptoFramesToMessages.convertToHandshakeMessage(clientHelloBytes);
    }

    @Test
    public void 複数のHandshakeMessageを処理できる() {
        this.stream.write(new CryptoFrame(0, this.clientHelloBytes));
        this.stream.write(new CryptoFrame(this.clientHelloBytes.length, this.clientHelloBytes));
        Optional<HandshakeMessage> retMsg1 = this.stream.getHandshakeMessage();
        Optional<HandshakeMessage> retMsg2 = this.stream.getHandshakeMessage();
        assertThat(retMsg1.get()).isInstanceOf(ClientHello.class);
        assertThat(retMsg2.get()).isInstanceOf(ClientHello.class);
    }

    @Test
    public void 一つのHandshakeMessageが分割された状態でも処理できる() {
        byte[] array1 = ArrayUtils.subarray(this.clientHelloBytes, 0, 10);
        byte[] array2 = ArrayUtils.subarray(this.clientHelloBytes, 10, 50);
        byte[] array3 = ArrayUtils.subarray(this.clientHelloBytes, 50, this.clientHelloBytes.length);

        CryptoFrame cryptoFrame1 = new CryptoFrame(0, array1);
        CryptoFrame cryptoFrame2 = new CryptoFrame(10, array2);
        CryptoFrame cryptoFrame3 = new CryptoFrame(50, array3);

        this.stream.write(cryptoFrame1);
        this.stream.write(cryptoFrame2);
        this.stream.write(cryptoFrame3);

        Optional<HandshakeMessage> ret = stream.getHandshakeMessage();
        assertThat(ret.get()).isInstanceOf(ClientHello.class);
    }

    @Test
    public void 一つのHandshakeMessageが分割されてシャフルされた状態でも処理できる() {
        byte[] array1 = ArrayUtils.subarray(this.clientHelloBytes, 0, 10);
        byte[] array2 = ArrayUtils.subarray(this.clientHelloBytes, 10, 50);
        byte[] array3 = ArrayUtils.subarray(this.clientHelloBytes, 50, this.clientHelloBytes.length);

        CryptoFrame cryptoFrame1 = new CryptoFrame(0, array1);
        CryptoFrame cryptoFrame2 = new CryptoFrame(10, array2);
        CryptoFrame cryptoFrame3 = new CryptoFrame(50, array3);

        this.stream.write(cryptoFrame3);
        this.stream.write(cryptoFrame1);
        this.stream.write(cryptoFrame2);

        Optional<HandshakeMessage> ret = stream.getHandshakeMessage();
        assertThat(ret.get()).isInstanceOf(ClientHello.class);
    }

    @Test
    public void 複数のHandshakeMessageが分割されてシャフルされた状態でも処理できる() {
        byte[] array10 = ArrayUtils.subarray(this.clientHelloBytes, 0, 10);
        byte[] array40 = ArrayUtils.subarray(this.clientHelloBytes, 10, 50);
        byte[] arrayRemaining = ArrayUtils.subarray(this.clientHelloBytes, 50, this.clientHelloBytes.length);

        CryptoFrame cryptoFrame1 = new CryptoFrame(0, array10);
        CryptoFrame cryptoFrame2 = new CryptoFrame(10, array40);
        CryptoFrame cryptoFrame3 = new CryptoFrame(50, arrayRemaining);
        CryptoFrame cryptoFrame4 = new CryptoFrame(this.clientHelloBytes.length, array10);
        CryptoFrame cryptoFrame5 = new CryptoFrame(this.clientHelloBytes.length + 10, array40);
        CryptoFrame cryptoFrame6 = new CryptoFrame(this.clientHelloBytes.length + 50, arrayRemaining);

        this.stream.write(cryptoFrame4);
        this.stream.write(cryptoFrame1);
        this.stream.write(cryptoFrame2);
        this.stream.write(cryptoFrame5);
        this.stream.write(cryptoFrame6);
        this.stream.write(cryptoFrame3);

        Optional<HandshakeMessage> ret1 = stream.getHandshakeMessage();
        Optional<HandshakeMessage> ret2 = stream.getHandshakeMessage();
        assertThat(ret1.get()).isInstanceOf(ClientHello.class);
        assertThat(ret2.get()).isInstanceOf(ClientHello.class);
    }

    @Test
    public void 一つのCryptoFrameに複数のHandshakeMessageが入った状態でも処理できる() {
        byte[] array10 = ArrayUtils.subarray(this.clientHelloBytes, 0, 10);
        byte[] array40 = ArrayUtils.subarray(this.clientHelloBytes, 10, 50);
        byte[] arrayRemaining = ArrayUtils.subarray(this.clientHelloBytes, 50, this.clientHelloBytes.length);

        CryptoFrame cryptoFrame1 = new CryptoFrame(0, array10);
        CryptoFrame cryptoFrame2 = new CryptoFrame(10, array40);
        CryptoFrame cryptoFrame3 = new CryptoFrame(50, ArrayUtils.addAll(arrayRemaining, array10)); /* 次のMessageが混じった状態 */
        CryptoFrame cryptoFrame4 = new CryptoFrame(this.clientHelloBytes.length + 10, array40);
        CryptoFrame cryptoFrame5 = new CryptoFrame(this.clientHelloBytes.length + 50, arrayRemaining);

        this.stream.write(cryptoFrame4);
        this.stream.write(cryptoFrame1);
        this.stream.write(cryptoFrame2);
        this.stream.write(cryptoFrame5);
        this.stream.write(cryptoFrame3);

        Optional<HandshakeMessage> ret1 = stream.getHandshakeMessage();
        Optional<HandshakeMessage> ret2 = stream.getHandshakeMessage();
        assertThat(ret1.get()).isInstanceOf(ClientHello.class);
        assertThat(ret2.get()).isInstanceOf(ClientHello.class);
    }


}