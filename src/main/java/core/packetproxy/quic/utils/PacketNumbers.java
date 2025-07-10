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

package packetproxy.quic.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import packetproxy.quic.value.PacketNumber;

public class PacketNumbers {

	List<PacketNumber> packetNumberList;

	public PacketNumbers() {
		this.packetNumberList = new ArrayList<>();
	}

	public boolean add(PacketNumber packetNumber) {
		return this.packetNumberList.add(packetNumber);
	}

	public boolean addAll(PacketNumbers packetNumbers) {
		return this.packetNumberList.addAll(packetNumbers.packetNumberList);
	}

	public Stream<PacketNumber> stream() {
		return this.packetNumberList.stream();
	}

	public boolean isEmpty() {
		return this.packetNumberList.isEmpty();
	}

	public PacketNumber largest() {
		Optional<PacketNumber> pn = this.packetNumberList.stream()
				.max(Comparator.comparingLong(PacketNumber::getNumber));
		return pn.orElse(null);
	}

	public String toString() {
		return String.format("PacketNumbers([%s])", this.packetNumberList.stream()
				.map(pn -> String.valueOf(pn.getNumber())).collect(Collectors.joining(",")));
	}

}
