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

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.EventListener;

import javax.swing.event.EventListenerList;

public abstract class Duplex {
	protected EventListenerList duplexEventListenerList = new EventListenerList();
	private boolean flag_event_listener;
	
	public interface DuplexEventListener extends EventListener
	{
		public int onClientPacketReceived(byte[] data) throws Exception;
		public int onServerPacketReceived(byte[] data) throws Exception;
		public void onClientChunkArrived(byte[] data) throws Exception;
		public void onServerChunkArrived(byte[] data) throws Exception;
		public byte[] onClientChunkPassThrough() throws Exception;
		public byte[] onServerChunkPassThrough() throws Exception;
		public byte[] onClientChunkAvailable() throws Exception;
		public byte[] onServerChunkAvailable() throws Exception;
		public byte[] onClientChunkReceived(byte[] data) throws Exception;
		public byte[] onServerChunkReceived(byte[] data) throws Exception;
		public byte[] onClientChunkSend(byte[] data) throws Exception;
		public byte[] onServerChunkSend(byte[] data) throws Exception;
		public byte[] onClientChunkSendForced(byte[] data) throws Exception;
		public byte[] onServerChunkSendForced(byte[] data) throws Exception;
		public void onClientChunkFlowControl(byte[] data) throws Exception;
		public InputStream getClientChunkFlowControlSink() throws Exception;
	}
	
	private int PIPE_SIZE = 65536;
	private PipedOutputStream outputForFlowControl;
	private PipedInputStream inputForFlowControl;
	
	public Duplex() {
		try {
			outputForFlowControl = new PipedOutputStream();
			inputForFlowControl = new PipedInputStream(outputForFlowControl, PIPE_SIZE);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void disableDuplexEventListener() {
		flag_event_listener = false;
	}
	public void enableDuplexEventListener() {
		flag_event_listener = true;
	}
	boolean isEnabledDuplexEventListener() {
		return flag_event_listener;
	}
	public void addDuplexEventListener(DuplexEventListener listener)
	{
		duplexEventListenerList.add(DuplexEventListener.class, listener);
		enableDuplexEventListener();
	}

	public int callOnClientPacketReceived(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data.length;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onClientPacketReceived(data);
		}
		return data.length;
	}
	public int callOnServerPacketReceived(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data.length;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onServerPacketReceived(data);
		}
		return data.length;
	}
	public void callOnClientChunkArrived(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			listener.onClientChunkArrived(data);
		}
	}
	public void callOnServerChunkArrived(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			listener.onServerChunkArrived(data);
		}
	}
	public byte[] callOnClientChunkPassThrough() throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return null;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onClientChunkPassThrough();
		}
		return null;
	}
	public byte[] callOnServerChunkPassThrough() throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return null;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onServerChunkPassThrough();
		}
		return null;
	}
	public byte[] callOnClientChunkAvailable() throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return null;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onClientChunkAvailable();
		}
		return null;
	}
	public byte[] callOnServerChunkAvailable() throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return null;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onServerChunkAvailable();
		}
		return null;
	}
	public byte[] callOnClientChunkReceived(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onClientChunkReceived(data);
		}
		return data;
	}
	public byte[] callOnServerChunkReceived(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onServerChunkReceived(data);
		}
		return data;
	}
	public byte[] callOnClientChunkSend(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onClientChunkSend(data);
		}
		return data;
	}
	public byte[] callOnServerChunkSend(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onServerChunkSend(data);
		}
		return data;
	}
	public byte[] callOnClientChunkSendForced(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onClientChunkSendForced(data);
		}
		return data;
	}
	public byte[] callOnServerChunkSendForced(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return data;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.onServerChunkSendForced(data);
		}
		return data;
	}
	public void callOnClientChunkFlowControl(byte[] data) throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			outputForFlowControl.write(data);
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			listener.onClientChunkFlowControl(data);
		}
	}
	public InputStream getClientChunkFlowControlSink() throws Exception {
		if (isEnabledDuplexEventListener() == false) {
			return inputForFlowControl;
		}
		for (DuplexEventListener listener: duplexEventListenerList.getListeners(DuplexEventListener.class)) {
			return listener.getClientChunkFlowControlSink();
		}
		return null;
	}

	public void send(byte[] data) throws Exception { }
	public void encode(byte[] data) throws Exception { }
	public byte[] receive() throws Exception { return null; }
	public void receiveAll() throws Exception { }

	public void sendToClient(byte[] data) throws Exception {
		sendToClientImpl(callOnServerChunkSendForced(data));
	}
	public void sendToServer(byte[] data) throws Exception {
		sendToServerImpl(callOnClientChunkSendForced(data));
	}
	protected void sendToClientImpl(byte[] data) throws Exception {}
	protected void sendToServerImpl(byte[] data) throws Exception {}
	public void close() throws Exception {}

	public Duplex crateSameConnectionDuplex() throws Exception { return null; }
	public byte[] prepareFastSend(byte[] data) throws Exception { return null; }
	public void execFastSend(byte[] data) throws Exception {}
}
