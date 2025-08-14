/*
 * Copyright 2025 DeNA Co., Ltd.
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
package packetproxy.controller;

import static packetproxy.http2.frames.FrameUtils.PREFACE;
import static packetproxy.http2.frames.FrameUtils.SETTINGS;
import static packetproxy.http2.frames.FrameUtils.WINDOW_UPDATE;
import static packetproxy.util.Logging.err;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import packetproxy.DuplexFactory;
import packetproxy.DuplexSync;
import packetproxy.EncoderManager;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;
import packetproxy.http2.frames.HeadersFrame;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;

public class SinglePacketAttackController {
	private final AttackFrames baseAttackFrames;
	private final DuplexSync attackConnection;
	private final int sleepTimeMs;

	public SinglePacketAttackController(final OneShotPacket oneshot) throws Exception {
		this(oneshot, 100);
	}

	public SinglePacketAttackController(final OneShotPacket oneshot, final int sleepTimeMs) throws Exception {
		if (oneshot == null) {
			throw new IllegalArgumentException("OneShotPacket cannot be null");
		}

		if (!isHttp2(oneshot)) {
			throw new IllegalArgumentException("Only HTTP/2 requests are supported for Single Packet Attack");
		}

		if (!isRequest(oneshot)) {
			throw new IllegalArgumentException("Only client requests are supported for Single Packet Attack");
		}

		if (isGetMethod(oneshot)) {
			throw new IllegalArgumentException(
					"GET requests are not supported by Single Packet Attack because they cannot have DATA frames in HTTP/2.");
		}

		this.attackConnection = DuplexFactory.createDuplexSyncForSinglePacketAttack(oneshot);
		this.baseAttackFrames = generateAttackFrames(oneshot);
		this.sleepTimeMs = sleepTimeMs;
	}

	public void attack(final int count) throws Exception {
		sendConnectionPreface();
		launchAttack(count);
	}

	private void sendConnectionPreface() throws Exception {
		final var preface = new ByteArrayOutputStream();
		preface.write(PREFACE);
		preface.write(SETTINGS);
		preface.write(WINDOW_UPDATE);

		attackConnection.execFastSend(preface.toByteArray());
	}

	private void launchAttack(final int count) throws Exception {
		var currentStreamId = 1;

		for (var i = 0; i < count; i++) {
			try {
				final var request = new SingleAttackRequest(currentStreamId, baseAttackFrames, attackConnection,
						sleepTimeMs);

				request.execute();
			} catch (Exception e) {
				err("Stream %d : Single Packet Attack failed with exception: %s", currentStreamId, e.getMessage());
			}

			currentStreamId += 2;
		}
	}

	private static boolean isHttp2(final OneShotPacket oneshot) {
		var alpn = oneshot.getAlpn();
		return alpn != null && (alpn.equals("h2") || alpn.equals("grpc") || alpn.equals("grpc-exp"));
	}

	private static boolean isRequest(final OneShotPacket oneshot) {
		return oneshot.getDirection() == Packet.Direction.CLIENT;
	}

	private static boolean isGetMethod(final OneShotPacket oneshot) {
		final var httpText = new String(oneshot.getData());
		final var lines = httpText.split("\r?\n");
		if (lines.length == 0) {
			return false;
		}

		final var requestLine = lines[0];
		final var parts = requestLine.split(" ");
		if (parts.length < 1) {
			return false;
		}

		final var method = parts[0].toUpperCase();

		return method.equals("GET");
	}

	private static AttackFrames generateAttackFrames(final OneShotPacket packet) throws Exception {
		final var originalFrames = convertPacketToFrames(packet);

		if (originalFrames.isEmpty()) {
			throw new IllegalArgumentException("No frames found after encoding and parsing");
		}

		final var categorizedFrames = new CategorizedFrames(originalFrames);
		final var attackFrames = new AttackFrames(categorizedFrames);

		return attackFrames;
	}

	private static List<Frame> convertPacketToFrames(final OneShotPacket packet) throws Exception {
		final var encoder = EncoderManager.getInstance().createInstance(packet.getEncoder(), packet.getAlpn());

		if (encoder == null) {
			throw new IllegalStateException("Could not create encoder for target packet");
		}

		final var httpTextData = packet.getData();
		final var binaryFrames = encoder.encodeClientRequest(httpTextData);
		return FrameUtils.parseFrames(binaryFrames);
	}

	private static class CategorizedFrames {
		private final List<HeadersFrame> headersFrames;
		private final List<DataFrame> dataFrames;
		private final List<Frame> otherFrames;

		CategorizedFrames(final List<Frame> frames) throws Exception {
			this.headersFrames = new ArrayList<>();
			this.dataFrames = new ArrayList<>();
			this.otherFrames = new ArrayList<>();

			final var filteredFrames = filterOutEmptyDataFrames(frames);
			categorizeFrames(filteredFrames);
			assertSameStreamId(filteredFrames);
		}

		int getStreamId() {
			if (!headersFrames.isEmpty()) {
				return headersFrames.get(0).getStreamId();
			} else if (!dataFrames.isEmpty()) {
				return dataFrames.get(0).getStreamId();
			} else if (!otherFrames.isEmpty()) {
				return otherFrames.get(0).getStreamId();
			} else {
				throw new IllegalStateException("No frames available to get stream ID");
			}
		}

		private static List<Frame> filterOutEmptyDataFrames(final List<Frame> frames) {
			return frames.stream()
					.filter(frame -> !(frame instanceof DataFrame) || ((DataFrame) frame).getPayload().length > 0)
					.collect(Collectors.toList());
		}

		private void categorizeFrames(final List<Frame> frames) {
			for (final Frame frame : frames) {
				if (frame instanceof HeadersFrame) {
					headersFrames.add((HeadersFrame) frame);
				} else if (frame instanceof DataFrame) {
					dataFrames.add((DataFrame) frame);
				} else {
					otherFrames.add(frame);
				}
			}
		}

		private void assertSameStreamId(final List<Frame> frames) {
			if (frames.isEmpty()) {
				throw new IllegalStateException("No frames found to determine stream ID");
			}

			final var expectedStreamId = frames.get(0).getStreamId();

			for (final var frame : frames) {
				if (frame.getStreamId() != expectedStreamId) {
					throw new IllegalStateException(
							String.format("Frame has different stream ID: expected %d, got %d (frame type: %s)",
									expectedStreamId, frame.getStreamId(), frame.getClass().getSimpleName()));
				}
			}
		}
	}

	private static class AttackFrames {
		private final List<Frame> firstFrames;
		private final List<Frame> lastFrames;

		AttackFrames(CategorizedFrames categorized) throws Exception {
			this.firstFrames = new ArrayList<>();
			this.lastFrames = new ArrayList<>();

			if (!categorized.dataFrames.isEmpty()) {
				createWithBody(categorized);
			} else {
				createWithoutBody(categorized);
			}
		}

		AttackFrames(final List<Frame> firstFrames, final List<Frame> lastFrames) {
			this.firstFrames = new ArrayList<>(firstFrames);
			this.lastFrames = new ArrayList<>(lastFrames);
		}

		private void createWithBody(final CategorizedFrames categorized) throws Exception {
			processHeadersAndOtherFrames(categorized);

			final var dataFrames = categorized.dataFrames;

			if (dataFrames.isEmpty()) {
				throw new IllegalStateException("No DATA frames found for request with body");
			}

			processDataFramesExceptLast(dataFrames);
			processLastDataFrame(dataFrames.get(dataFrames.size() - 1));
		}

		private void createWithoutBody(final CategorizedFrames categorized) throws Exception {
			if (!categorized.dataFrames.isEmpty()) {
				throw new IllegalStateException("DATA frames found for request without body");
			}

			final int streamId = categorized.getStreamId();

			processHeadersAndOtherFrames(categorized);

			final var emptyDataFrame = new DataFrame(DataFrame.FLAG_END_STREAM, streamId, new byte[]{});
			this.lastFrames.add(emptyDataFrame);
		}

		private void processHeadersAndOtherFrames(final CategorizedFrames categorized) throws Exception {
			for (final var headersFrame : categorized.headersFrames) {
				final var modifiedHeadersFrame = new HeadersFrame(headersFrame.toByteArray(), null);
				modifiedHeadersFrame.setFlags(modifiedHeadersFrame.getFlags() & ~HeadersFrame.FLAG_END_STREAM);
				this.firstFrames.add(modifiedHeadersFrame);
			}

			this.firstFrames.addAll(categorized.otherFrames);
		}

		private void processDataFramesExceptLast(final List<DataFrame> dataFrames) {
			for (int i = 0; i < dataFrames.size() - 1; i++) {
				final var dataFrame = dataFrames.get(i);
				final var modifiedDataFrame = new DataFrame(dataFrame.getFlags() & ~DataFrame.FLAG_END_STREAM,
						dataFrame.getStreamId(), dataFrame.getPayload());
				this.firstFrames.add(modifiedDataFrame);
			}
		}

		private void processLastDataFrame(final DataFrame lastDataFrame) {
			final var lastPayload = lastDataFrame.getPayload();

			if (lastPayload.length == 0) {
				throw new IllegalStateException("Last DATA frame has no payload, which is not allowed");
			} else if (lastPayload.length == 1) {
				final var finalDataFrame = new DataFrame(DataFrame.FLAG_END_STREAM, lastDataFrame.getStreamId(),
						new byte[]{lastPayload[0]});
				this.lastFrames.add(finalDataFrame);
			} else {
				splitLastDataFrame(lastDataFrame);
			}
		}

		private void splitLastDataFrame(final DataFrame lastDataFrame) {
			final var lastPayload = lastDataFrame.getPayload();
			if (lastPayload.length <= 1) {
				throw new IllegalStateException(
						"Last DATA frame has no payload or only one byte, which is not allowed");
			}

			final var payloadExceptLast = new byte[lastPayload.length - 1];
			System.arraycopy(lastPayload, 0, payloadExceptLast, 0, lastPayload.length - 1);

			final var firstPartDataFrame = new DataFrame(lastDataFrame.getFlags() & ~DataFrame.FLAG_END_STREAM,
					lastDataFrame.getStreamId(), payloadExceptLast);
			this.firstFrames.add(firstPartDataFrame);

			final var lastByte = new byte[1];
			lastByte[0] = lastPayload[lastPayload.length - 1];

			final var finalDataFrame = new DataFrame(DataFrame.FLAG_END_STREAM, lastDataFrame.getStreamId(), lastByte);
			this.lastFrames.add(finalDataFrame);
		}
	}

	private static class SingleAttackRequest {
		private final int streamId;
		private final AttackFrames originalAttackFrames;
		private final DuplexSync connection;
		private final AttackFrames streamAttackFrames;
		private final int sleepTimeMs;

		public SingleAttackRequest(final int streamId, final AttackFrames originalAttackFrames,
				final DuplexSync connection, final int sleepTimeMs) throws Exception {
			this.streamId = streamId;
			this.originalAttackFrames = originalAttackFrames;
			this.connection = connection;
			this.streamAttackFrames = createStreamAttackFrames();
			this.sleepTimeMs = sleepTimeMs;
		}

		public void execute() throws Exception {
			sendFirstFrames();
			Thread.sleep(sleepTimeMs);
			sendPing();
			sendLastFrames();
			connection.receive();
		}

		private AttackFrames createStreamAttackFrames() throws Exception {
			final var newFirstFrames = updateFrameStreamIds(originalAttackFrames.firstFrames, streamId);
			final var newLastFrames = updateFrameStreamIds(originalAttackFrames.lastFrames, streamId);
			return new AttackFrames(newFirstFrames, newLastFrames);
		}

		private List<Frame> updateFrameStreamIds(final List<Frame> originalFrames, final int newStreamId)
				throws Exception {
			final var updatedFrames = new ArrayList<Frame>();

			for (final var frame : originalFrames) {
				final var clonedFrame = new Frame(frame);
				clonedFrame.setStreamId(newStreamId);
				updatedFrames.add(clonedFrame);
			}

			return updatedFrames;
		}

		private void sendFirstFrames() throws Exception {
			final var firstFramesData = FrameUtils.toByteArray(streamAttackFrames.firstFrames);
			connection.execFastSend(firstFramesData);
		}

		private void sendPing() throws Exception {
			final var pingPayload = new byte[8];
			final var pingFrame = new Frame(Frame.Type.PING, 0, 0, pingPayload);
			final var pingData = pingFrame.toByteArray();

			connection.execFastSend(pingData);
		}

		private void sendLastFrames() throws Exception {
			final var lastFramesData = FrameUtils.toByteArray(streamAttackFrames.lastFrames);

			connection.execFastSend(lastFramesData);
		}
	}
}
