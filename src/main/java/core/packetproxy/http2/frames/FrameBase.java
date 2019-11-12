package packetproxy.http2.frames;

public class FrameBase {
    private byte length[] = new byte[3];
    private static int type = 0x04;
    private byte flags = 0;
    private boolean r = false;
    private byte streamId[] = new byte[4];
    private byte framePayload[];
}
