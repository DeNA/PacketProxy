/*
 * Copyright (c) 1998-2011, Brian Wellington.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package packetproxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.ExtendedFlags;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

public class PrivateDnsResponseBuilder {

	private final String spoofIP;
	private final Record answer;
	private final Record[] answers;

	public PrivateDnsResponseBuilder() {
		this(null, null, null);
	}

	public PrivateDnsResponseBuilder(String ip) {
		this(ip, null, null);
	}

	public PrivateDnsResponseBuilder(Record answer) {
		this(null, answer, null);
	}

	public PrivateDnsResponseBuilder(Record[] answers) {
		this(null, null, answers);
	}

	private PrivateDnsResponseBuilder(String spoofIP, Record answer, Record[] answers) {
		this.spoofIP = spoofIP;
		this.answer = answer;
		this.answers = answers;
	}

	public byte[] generateReply(Message query, byte[] in, int length, Socket s) throws IOException {
		var header = query.getHeader();
		if (header.getFlag(Flags.QR)) {
			return null;
		}
		if (header.getRcode() != Rcode.NOERROR) {
			return errorMessage(query, Rcode.FORMERR);
		}
		if (header.getOpcode() != Opcode.QUERY) {
			return errorMessage(query, Rcode.NOTIMP);
		}

		var queryRecord = query.getQuestion();
		var queryOpt = query.getOPT();
		var response = new Message(header.getID());
		response.getHeader().setFlag(Flags.QR);
		if (header.getFlag(Flags.RD)) {
			response.getHeader().setFlag(Flags.RD);
		}
		response.addRecord(queryRecord, Section.QUESTION);

		if (answer != null) {
			response.addRecord(answer, Section.ANSWER);
		}
		if (answers != null) {
			for (var record : answers) {
				if (record != null) {
					response.addRecord(record, Section.ANSWER);
				}
			}
		}
		if (spoofIP != null) {
			response.addRecord(createSpoofedRecord(queryRecord), Section.ANSWER);
		}

		var maxLength = getMaxLength(queryOpt, s);
		addOptRecord(response, queryOpt);
		return response.toWire(maxLength);
	}

	private Record createSpoofedRecord(Record queryRecord) throws IOException {
		var name = queryRecord.getName();
		var dclass = queryRecord.getDClass();
		var address = InetAddress.getByName(spoofIP);
		if (queryRecord.getType() == Type.A) {
			return new ARecord(name, dclass, 0, address);
		}
		return new AAAARecord(name, dclass, 0, address);
	}

	private int getMaxLength(OPTRecord queryOpt, Socket s) {
		if (s != null) {
			return 65535;
		}
		if (queryOpt != null) {
			return Math.max(queryOpt.getPayloadSize(), 512);
		}
		return 512;
	}

	private void addOptRecord(Message response, OPTRecord queryOpt) {
		if (queryOpt == null) {
			return;
		}
		var optFlags = (queryOpt.getFlags() & ExtendedFlags.DO) != 0 ? ExtendedFlags.DO : 0;
		response.addRecord(new OPTRecord((short) 4096, Rcode.NOERROR, (byte) 0, optFlags), Section.ADDITIONAL);
	}

	private byte[] errorMessage(Message query, int rcode) {
		return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
	}

	private byte[] buildErrorMessage(Header header, int rcode, Record question) {
		var response = new Message();
		response.setHeader(header);
		for (var i = Section.QUESTION; i <= Section.ADDITIONAL; i++) {
			response.removeAllRecords(i);
		}
		if (rcode == Rcode.SERVFAIL && question != null) {
			response.addRecord(question, Section.QUESTION);
		}
		header.setRcode(rcode);
		return response.toWire();
	}
}
