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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledTimer {

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private final Runnable onTimeout;
	private ScheduledFuture<?> future;

	public ScheduledTimer(Runnable onTimeout) {
		this.onTimeout = onTimeout;
		this.future = null;
	}

	public synchronized void update(Instant time) {
		if (time == Instant.MAX) {
			this.cancel();
		} else {
			long delay = Duration.between(Instant.now(), time).toMillis();
			this.future = this.scheduler.schedule(this.onTimeout, delay, TimeUnit.MILLISECONDS);
		}
	}

	public synchronized void cancel() {
		if (this.future != null) {
			this.future.cancel(false);
		}
	}

}
