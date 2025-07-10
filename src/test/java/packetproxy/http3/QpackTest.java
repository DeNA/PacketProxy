package packetproxy.http3;

import static org.assertj.core.api.Assertions.assertThat;
import static packetproxy.util.Throwing.rethrow;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http3.qpack.QpackDecoder;
import org.eclipse.jetty.http3.qpack.QpackEncoder;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.junit.jupiter.api.Test;
import packetproxy.common.Binary;
import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;
import packetproxy.quic.value.SimpleBytes;

public class QpackTest {

	@Test
	public void smoke() throws Exception {
		ByteBufferPool bufferPool = new MappedByteBufferPool();
		ByteBufferPool.Lease lease = new ByteBufferPool.Lease(bufferPool);

		QpackEncoder encoder = new QpackEncoder(instructions -> {

			instructions.forEach(i -> {

				i.encode(lease);
			});
		}, 100);
		encoder.setCapacity(100);

		HttpFields httpFields = HttpFields.build().add("hoge", "fuga");

		ByteBuffer buffer = ByteBuffer.allocate(1024);
		encoder.encode(buffer, 0x0, new MetaData(HttpVersion.HTTP_3, httpFields));
		buffer.flip();
		System.out.println(new Binary(SimpleBytes.parse(buffer, buffer.remaining()).getBytes()).toHexString());

		buffer.clear();
		encoder.encode(buffer, 0xb, new MetaData(HttpVersion.HTTP_3, httpFields));
		buffer.flip();
		System.out.println(new Binary(SimpleBytes.parse(buffer, buffer.remaining()).getBytes()).toHexString());

		ByteArrayOutputStream encoderInsts = new ByteArrayOutputStream();
		lease.getByteBuffers().forEach(rethrow(inst -> {

			encoderInsts.write(SimpleBytes.parse(inst, inst.remaining()).getBytes());
		}));
		System.out.println("send: QpackEncoder Instructions: " + Hex.encodeHexString(encoderInsts.toByteArray()));

	}

	@Test
	public void QpackEncoderとQpackDecoderのテスト() throws Exception {

		ByteBufferPool bufferPool = new MappedByteBufferPool();
		ByteBufferPool.Lease lease = new ByteBufferPool.Lease(bufferPool);

		ByteBufferPool bufferPool2 = new MappedByteBufferPool();
		ByteBufferPool.Lease lease2 = new ByteBufferPool.Lease(bufferPool2);

		QpackEncoder encoder = new QpackEncoder(instructions -> {

			instructions.forEach(i -> i.encode(lease));
		}, 100);

		QpackDecoder decoder = new QpackDecoder(instructions -> {

			instructions.forEach(i -> i.encode(lease2));
		}, 100);
		encoder.setCapacity(100);

		/* input data */
		HttpFields httpFields = HttpFields.build().add("hoge", "fuga");

		/* processing by Encoder */
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		encoder.encode(buffer, 0, new MetaData(HttpVersion.HTTP_3, httpFields));
		buffer.flip();
		byte[] HeadersBytes = SimpleBytes.parse(buffer, buffer.remaining()).getBytes();

		ByteArrayOutputStream encoderInsts = new ByteArrayOutputStream();
		lease.getByteBuffers().forEach(rethrow(inst -> {

			encoderInsts.write(SimpleBytes.parse(inst, inst.remaining()).getBytes());
		}));
		System.out.println("send: QpackEncoder Instructions: " + Hex.encodeHexString(encoderInsts.toByteArray()));
		System.out.println("send: HEADERS frame data: " + Hex.encodeHexString(HeadersBytes));

		/* processing by Decoder */
		decoder.parseInstructions(ByteBuffer.wrap(encoderInsts.toByteArray()));
		decoder.decode(0, ByteBuffer.wrap(HeadersBytes), (streamId, metadata) -> {

			System.out.println("streamId: " + streamId + ", meta: " + metadata);
			assertThat(httpFields.asImmutable()).isEqualTo(metadata.getFields());
		});
		ByteArrayOutputStream decoderInsts = new ByteArrayOutputStream();
		lease2.getByteBuffers().forEach(rethrow(inst -> {

			decoderInsts.write(SimpleBytes.parse(inst, inst.remaining()).getBytes());
		}));
		System.out.println("resp: QpackDecoder Instructions: " + Hex.encodeHexString(decoderInsts.toByteArray()));

		/* comparing internal contexts in Encoder and Decoder */
		System.out.println("encoder before: " + encoder.dump());
		encoder.parseInstructions(ByteBuffer.wrap(decoderInsts.toByteArray()));
		System.out.println("encoder after: " + encoder.dump());
		System.out.println("decoder: " + decoder.dump());
	}

	@Test
	public void MetaDataTest() throws Exception {
		String httpStr = "POST / HTTP/3\nhost: localhost\nx-hoge: fuga\n\nThis is a post data.";

		Http http = Http.create(httpStr.getBytes());
		HttpHeader header = http.getHeader();
		List<HeaderField> fields = header.getFields();
		HttpFields.Mutable jettyHttpFields = HttpFields.build();
		fields.forEach(field -> {

			jettyHttpFields.add(field.getName(), field.getValue());
		});
		MetaData md = new MetaData.Request(http.getMethod(), HttpURI.from("https://localhost/"), HttpVersion.HTTP_3,
				jettyHttpFields);

		System.out.println(md);

	}
}
