package packetproxy.common;

import java.io.*;
import java.net.InetSocketAddress;

public class MockEndPoint implements Endpoint {
    private InputStream mockInputStream;
    InetSocketAddress addr;

    public MockEndPoint(InetSocketAddress addr, byte[] mockResponseData){
        this.addr = addr;
        init(mockResponseData);
    }

    private void init(byte[] mockResponseData){
        mockInputStream = new DelayByteArrayInputStream(mockResponseData);
    }

    @Override
    public InputStream getInputStream() {
        return mockInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public InetSocketAddress getAddress() {
        return addr;
    }

    @Override
    public int getLocalPort() {
        throw new java.lang.UnsupportedOperationException("MockEndPoint is not supported getLocalPort!");
    }

    @Override
    public String getName() {
        return addr.getHostName();
    }

    class DelayByteArrayInputStream extends ByteArrayInputStream{

        public DelayByteArrayInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public synchronized int read(byte b[], int off, int len){
            try {
                /*
                TODO: HTTPSでMockResponseを使ったときにHisotry上にレスポンスがリクエストより先に表示されてしまう対策
                暫定でThread.sleep(millis)を使っているがおそらく重いリクエストが来ると先にレスポンスが出てしまう。
                 */
                Thread.sleep(300);
            }catch (Exception e){
                e.printStackTrace();
            }
            return super.read(b, off, len);
        }
    }
}
