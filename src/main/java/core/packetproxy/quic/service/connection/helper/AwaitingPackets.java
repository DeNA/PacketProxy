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

package packetproxy.quic.service.connection.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Predicate;

public class AwaitingPackets<T> {

	private final Queue<T> awaitings = new ConcurrentLinkedDeque<>();

	public synchronized void put(List<T> packets) {
		awaitings.addAll(packets);
	}

	public synchronized void put(T packet) {
		this.awaitings.offer(packet);
	}

	public synchronized T get() {
		return this.awaitings.poll();
	}

	/**
	 * 全ての要素に対して関数を適用する。関数の結果がtrueになった場合は要素から削除
	 */
	public synchronized void forEachAndRemovedIfReturnTrue(Predicate<T> predicate) {
		T e;
		List<T> targets = new ArrayList<>();
		while ((e = awaitings.poll()) != null) {

			targets.add(e);
		}
		List<T> failed = new ArrayList<>();
		targets.forEach(target -> {

			if (!predicate.test(target)) {

				failed.add(target);
			}
		});
		if (!failed.isEmpty()) {

			this.awaitings.addAll(failed);
		}
	}

}
