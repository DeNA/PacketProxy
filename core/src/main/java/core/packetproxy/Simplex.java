/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy;

import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.EventListener;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import javax.swing.event.EventListenerList;
import org.apache.commons.lang3.ArrayUtils;

class Simplex extends Thread {

	private final int TIMEOUT = 30 * 1000;
	private InputStream in;
	private OutputStream out;
	private boolean flag_enable_event;
	private boolean flag_break_loop = false;
	private boolean flag_close = true;
	private final byte[] input_data;

	protected EventListenerList simplexEventListenerList = new EventListenerList();

	public interface SimplexEventListener extends EventListener {

		void onChunkArrived(byte[] data) throws Exception;

		byte[] onChunkPassThrough() throws Exception;

		byte[] onChunkAvailable() throws Exception;

		int onPacketReceived(byte[] data) throws Exception;

		byte[] onChunkReceived(byte[] data) throws Exception;

		byte[] onChunkSend(byte[] data) throws Exception;
	}

	public abstract static class SimplexEventAdapter implements SimplexEventListener {

		ByteArrayOutputStream inputData = new ByteArrayOutputStream();

		@Override
		public void onChunkArrived(byte[] data) throws Exception {
			inputData.write(data);
		}

		@Override
		public byte[] onChunkPassThrough() throws Exception {
			return null;
		}

		@Override
		public byte[] onChunkAvailable() throws Exception {
			byte[] ret = inputData.toByteArray();
			inputData.reset();
			return ret;
		}

		@Override
		public int onPacketReceived(byte[] data) throws Exception {
			return data.length;
		}

		@Override
		public byte[] onChunkReceived(byte[] data) throws Exception {
			return data;
		}

		@Override
		public byte[] onChunkSend(byte[] data) throws Exception {
			return data;
		}
	}

	public void disableSimplexEvent() {
		flag_enable_event = false;
	}

	public void enableSimplexEvent() {
		flag_enable_event = true;
	}

	public boolean isEnabledSimplexEvent() {
		return flag_enable_event;
	}

	public void addSimplexEventListener(SimplexEventListener listener) {
		simplexEventListenerList.add(SimplexEventListener.class, listener);
	}

	public int callOnPacketReceived(byte[] data) throws Exception {
		if (!isEnabledSimplexEvent()) {

			return data.length;
		}
		for (SimplexEventListener listener : simplexEventListenerList.getListeners(SimplexEventListener.class)) {

			return listener.onPacketReceived(data);
		}
		return data.length;
	}

	public void callOnChunkArrived(byte[] data) throws Exception {
		if (!isEnabledSimplexEvent()) {

			return;
		}
		for (SimplexEventListener listener : simplexEventListenerList.getListeners(SimplexEventListener.class)) {

			listener.onChunkArrived(data);
		}
	}

	public byte[] callOnChunkPassThrough() throws Exception {
		if (!isEnabledSimplexEvent()) {

			return null;
		}
		for (SimplexEventListener listener : simplexEventListenerList.getListeners(SimplexEventListener.class)) {

			return listener.onChunkPassThrough();
		}
		return null;
	}

	public byte[] callOnChunkAvailable() throws Exception {
		if (!isEnabledSimplexEvent()) {

			return null;
		}
		for (SimplexEventListener listener : simplexEventListenerList.getListeners(SimplexEventListener.class)) {

			return listener.onChunkAvailable();
		}
		return null;
	}

	public byte[] callOnChunkReceived(byte[] data) throws Exception {
		if (!isEnabledSimplexEvent()) {

			return data;
		}
		for (SimplexEventListener listener : simplexEventListenerList.getListeners(SimplexEventListener.class)) {

			return listener.onChunkReceived(data);
		}
		return data;
	}

	public byte[] callOnChunkSend(byte[] data) throws Exception {
		if (!isEnabledSimplexEvent()) {

			return data;
		}
		for (SimplexEventListener listener : simplexEventListenerList.getListeners(SimplexEventListener.class)) {

			return listener.onChunkSend(data);
		}
		return data;
	}

	public Simplex(InputStream in, OutputStream out) throws Exception {
		this.in = in;
		this.out = out;
		input_data = new byte[100 * 1024];
		enableSimplexEvent();
	}

	@Override
	public void run() {
		if (in == null) {

			return;
		}
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Callable<Integer> readTask = new Callable<Integer>() {

			public Integer call() throws Exception {
				int ret;
				try {

					ret = in.read(input_data);
				} catch (SSLException e) {

					// Logging.err(String.format("SSLException: %s", e.getMessage()));
					ret = -1; // should be finished
				} catch (SocketException e) {

					// Logging.err(String.format("SocketException: %s", e.getMessage()));
					ret = -1; // should be finished
				}
				return ret;
			}
		};

		try {

			while (!flag_break_loop) {

				Future<Integer> future = executor.submit(readTask);
				int timeout = bout.size() > 0 ? TIMEOUT : 24 * 60 * 60 * 1000;
				int length = future.get(timeout, TimeUnit.MILLISECONDS);
				if (length == -1) {

					break;
				}

				bout.write(input_data, 0, length);
				while (bout.size() > 0) {

					int accepted_input_size = callOnPacketReceived(bout.toByteArray());
					if (accepted_input_size < 0 || accepted_input_size > bout.size()) {

						break;
					}
					byte[] accepted_array = ArrayUtils.subarray(bout.toByteArray(), 0, accepted_input_size);
					byte[] unaccepted_array = ArrayUtils.subarray(bout.toByteArray(), accepted_input_size, bout.size());
					bout.reset();
					bout.write(unaccepted_array);

					callOnChunkArrived(accepted_array);

					for (byte[] pass_through_data = callOnChunkPassThrough(); pass_through_data != null
							&& pass_through_data.length > 0; pass_through_data = callOnChunkPassThrough()) {

						out.write(pass_through_data);
						out.flush();
					}

					for (byte[] available_data = callOnChunkAvailable(); available_data != null
							&& available_data.length > 0; available_data = callOnChunkAvailable()) {

						byte[] send_data = callOnChunkReceived(available_data);
						if (send_data != null && send_data.length > 0) {

							send(send_data);
						}
					}
				}
			}
		} catch (TimeoutException e) {

			errWithStackTrace(e);
			log("-----");
			log(new String(bout.toByteArray()));
			log("-----");
			try {

				in.close();
			} catch (Exception e1) {

				errWithStackTrace(e);
			}
		} catch (SSLException e) {

			// Logging.err(String.format("SSLException: %s", e.getMessage()));
		} catch (SocketException e) {

			// Logging.err(String.format("SocketException: %s", e.getMessage()));
		} catch (Exception e) {

			errWithStackTrace(e);
		} finally {

			executor.shutdownNow();
			if (flag_close) {

				try {

					if (in != null)
						in.close();
					if (out != null)
						out.close();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
				try {

					if (out != null)
						out.close();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		}
	}

	public void send(byte[] input_data) throws Exception {
		input_data = callOnChunkSend(input_data);
		if (out != null && input_data != null) {

			out.write(input_data);
			out.flush();
		}
	}

	public void sendWithoutRecording(byte[] input_data) throws Exception {
		if (out != null) {

			out.write(input_data);
			out.flush();
		}
	}

	public void setInputStream(InputStream input) {
		in = input;
	}

	public void setOutputStream(OutputStream output) {
		out = output;
	}

	public void setStreams(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}

	public void forceClose() throws Exception {
		flag_break_loop = true;
		flag_close = true;
		in.close();
		out.close();
	}

	public void finishWithoutClose() {
		flag_break_loop = true;
		flag_close = false;
	}

	public void close() {
		flag_break_loop = true;
		flag_close = true;
	}
}
