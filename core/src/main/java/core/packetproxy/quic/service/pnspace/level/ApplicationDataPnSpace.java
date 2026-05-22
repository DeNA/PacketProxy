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

package packetproxy.quic.service.pnspace.level;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.service.frame.FramesBuilder;
import packetproxy.quic.service.framegenerator.MessagesToStreamFrames;
import packetproxy.quic.service.packet.QuicPacketBuilder;
import packetproxy.quic.service.pnspace.PnSpace;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.frame.Frame;

@Getter
public class ApplicationDataPnSpace extends PnSpace {

	private final MessagesToStreamFrames msgToStreamFrames = new MessagesToStreamFrames();

	public ApplicationDataPnSpace(Connection conn) {
		super(conn, Constants.PnSpaceType.PnSpaceApplicationData);
	}

	@Override
	public void addSendQuicMessage(QuicMessage msg) {
		this.msgToStreamFrames.put(msg);
		Frames frames = this.msgToStreamFrames.get();
		super.addSendFrames(frames);
	}

	@Override
	public List<QuicPacketBuilder> getAndRemoveSendFramesAndConvertPacketBuilders() {
		List<QuicPacketBuilder> builders = new ArrayList<>();
		for (Frame frame : sendFrameQueue.pollAll()) {

			builders.add(QuicPacketBuilder.getBuilder().setPnSpaceType(Constants.PnSpaceType.PnSpaceApplicationData)
					.setFramesBuilder(new FramesBuilder().add(frame)));
		}
		return builders;
	}
}
