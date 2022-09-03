package packetproxy.http3;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http3.qpack.Instruction;
import org.eclipse.jetty.http3.qpack.QpackDecoder;
import org.eclipse.jetty.http3.qpack.QpackEncoder;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.SimpleBytes;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class QpackTest {
    @Test
    public void Encode後DeCodeできるかテスト() throws Exception {

        ByteBufferPool bufferPool = new MappedByteBufferPool();
        ByteBufferPool.Lease lease = new ByteBufferPool.Lease(bufferPool);

        ByteBufferPool bufferPool2 = new MappedByteBufferPool();
        ByteBufferPool.Lease lease2 = new ByteBufferPool.Lease(bufferPool2);

        QpackEncoder encoder = new QpackEncoder(new Instruction.Handler() {
            @Override
            public void onInstructions(List<Instruction> instructions) {
                System.out.println("Encode Instructions: " + instructions);
                instructions.stream().forEach(i -> i.encode(lease));
            }
        }, 100);

        QpackDecoder decoder = new QpackDecoder(new Instruction.Handler() {
            @Override
            public void onInstructions(List<Instruction> instructions) {
                System.out.println("Decode Instructions: " + instructions);
                instructions.stream().forEach(i -> i.encode(lease2));
            }
        }, 100);

        encoder.setCapacity(100);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        HttpFields httpFields = HttpFields.build().add("hoge", "fuga");
        encoder.encode(buffer, 0, new MetaData(HttpVersion.HTTP_3, httpFields));
        buffer.flip();
        System.out.println("Stream data: " + Hex.encodeHexString(SimpleBytes.parse(buffer, buffer.remaining()).getBytes()));

        lease.getByteBuffers().forEach(b -> {
            try {
                decoder.parseInstructions(b);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        buffer.position(0);
        decoder.decode(0, buffer, new QpackDecoder.Handler() {
            @Override
            public void onMetaData(long streamId, MetaData metadata) {
                System.out.println("streamId: " + streamId + ", meta: " + metadata);
                assertThat(httpFields.asImmutable()).isEqualTo(metadata.getFields());
            }
        });

        System.out.println(encoder.dump());
        lease2.getByteBuffers().forEach(b -> {
            try {
                encoder.parseInstructions(b);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        System.out.println(encoder.dump());

    }
}
