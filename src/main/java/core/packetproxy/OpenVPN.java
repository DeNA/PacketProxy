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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectVolumeCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import packetproxy.model.OpenVPNForwardPort;
import packetproxy.model.OpenVPNForwardPorts;
import packetproxy.util.PacketProxyUtility;

public class OpenVPN {

	private static OpenVPN instance = null;
	private static final String imageName = "alekslitvinenk/openvpn";
	private static final String containerName = "packetproxy_ovpn";
	private static final String volumeName = "packetproxy_ovpn_volume";
	private boolean pulling = false;

	public static OpenVPN getInstance() throws Exception {
		if (instance == null) {

			instance = new OpenVPN();
		}
		return instance;
	}

	private DockerClient getClient() {
		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig()).maxConnections(100).connectionTimeout(Duration.ofSeconds(30))
				.responseTimeout(Duration.ofSeconds(45)).build();
		return DockerClientImpl.getInstance(config, httpClient);
	}

	public void startServer(String ip, String proto) {
		DockerClient client = getClient();
		if (!getImage(client)) {

			// TODO: disable checkbox
			return;
		}
		createContainer(client, ip, proto);
		startContainer(client, proto);
		patchContainer(client, ip, proto);
	}

	public void stopServer() {
		DockerClient client = getClient();
		removeContainer(client);
	}

	public boolean getImage(DockerClient client) {
		InspectImageCmd inspect = client.inspectImageCmd(imageName);
		try {

			inspect.exec();
		} catch (NotFoundException e) {

			if (pulling) {

				PacketProxyUtility.getInstance().packetProxyLog("already pulling image...");
				return false;
			}
			PacketProxyUtility.getInstance().packetProxyLog("docker image not found. start pulling...");
			pulling = true;
			client.pullImageCmd(imageName).exec(new PullImageResultCallback());
			return false;
		}
		return true;
	}

	public void createContainer(DockerClient client, String localIp, String proto) {
		InspectVolumeCmd inspectVolume = client.inspectVolumeCmd(volumeName);
		try {

			inspectVolume.exec();
		} catch (NotFoundException e) {

			// create volume
			client.createVolumeCmd().withName(volumeName).exec();
		}

		InspectContainerCmd inspect = client.inspectContainerCmd(containerName);
		try {

			inspect.exec();
		} catch (NotFoundException e) {

			// create container
			HostConfig hostConfig = new HostConfig().withBinds(new Bind(volumeName, new Volume("/opt")))
					.withPortBindings(new Ports(PortBinding.parse("0.0.0.0:1194:1194/udp"),
							PortBinding.parse("0.0.0.0:1194:1194/tcp"), PortBinding.parse("0.0.0.0:18080:8080/tcp")))
					.withCapAdd(Capability.NET_ADMIN);

			client.createContainerCmd(imageName).withName(containerName).withHostConfig(hostConfig)
					.withExposedPorts(new ExposedPort(1194)).withEnv("HOST_ADDR=" + localIp).exec();
		}
	}

	public void startContainer(DockerClient client, String proto) {
		InspectContainerResponse inspect = client.inspectContainerCmd(containerName).exec();
		if (inspect.getState().getRunning()) {

			// running
			PacketProxyUtility.getInstance().packetProxyLog("OpenVPN Server is already running");
			return;
		}

		try {

			// start the server
			client.startContainerCmd(containerName).exec();
		} catch (NotFoundException e) {

			PacketProxyUtility.getInstance().packetProxyLogErr(e.toString());
		} catch (NotModifiedException e) {

			PacketProxyUtility.getInstance().packetProxyLog(e.toString());
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	public void removeContainer(DockerClient client) {
		RemoveContainerCmd remove = client.removeContainerCmd(containerName).withForce(true);
		try {

			remove.exec();
		} catch (NotFoundException e) {

			PacketProxyUtility.getInstance().packetProxyLog(e.toString());
		}
	}

	public void execCommand(DockerClient client, String[] command) throws Exception {
		ExecCreateCmdResponse resp = client.execCreateCmd(containerName).withPrivileged(true).withUser("root")
				.withCmd(command).exec();

		ExecResultCallback<ResultCallback<Frame>, Frame> callback = new ExecResultCallback<>();
		client.execStartCmd(resp.getId()).exec(callback);
		callback.awaitCompletion();
	}

	public void patchContainer(DockerClient client, String localIp, String proto) {
		try {

			List<OpenVPNForwardPort> forwardPorts = OpenVPNForwardPorts.getInstance().queryAll();
			for (OpenVPNForwardPort forwardPort : forwardPorts) {

				String command = "/sbin/iptables -t nat -A PREROUTING -p " + forwardPort.getType().toString()
						+ " --dport " + forwardPort.getFromPort() + " -j DNAT --to-destination " + localIp + ":"
						+ forwardPort.getToPort();

				String[] commands = new String[]{"/bin/sh", "-c", command};
				execCommand(client, commands);
			}

			// patch server/client configs
			// change the server config to use inside the volume
			String[] commands = {"/bin/sh", "-c",
					"\"sed -i 's/\\/etc\\/openvpn\\/server\\.conf/\\/opt\\/Dockovpn\\/config\\/server\\.conf/' /opt/Dockovpn/start.sh\""};
			execCommand(client, commands);
			// change server/client config file(udp->tcp-server/tcp-client)
			switch (proto) {

				case "TCP" :
					commands = new String[]{"/bin/sh", "-c",
							"sed -i s/udp/tcp-server/ /opt/Dockovpn/config/server.conf"};
					execCommand(client, commands);
					commands = new String[]{"/bin/sh", "-c",
							"find /opt -name \"client.ovpn\" | xargs sed -i s/udp/tcp-client/"};
					execCommand(client, commands);
					break;
				case "UDP" :
					commands = new String[]{"/bin/sh", "-c",
							"sed -i s/tcp-server/udp/ /opt/Dockovpn/config/server.conf"};
					execCommand(client, commands);
					commands = new String[]{"/bin/sh", "-c",
							"find /opt -name \"client.ovpn\" | xargs sed -i s/tcp-client/udp/"};
					execCommand(client, commands);
					break;
			}
			/*
			 * restart the server. it is because
			 * - dockovpn is start in entrypoint, this means it is difficult to patch in CMD
			 * - so it seems to be necessary to patch in docker exec, but it is after docker
			 * start(now doing)
			 * - after patching, the server should be restarted to reflect the config
			 */
			PacketProxyUtility.getInstance().packetProxyLog("OpenVPN Server is restarting...");
			// kill the process and restart openvpn
			commands = new String[]{"/bin/sh", "-c", "kill $(pgrep openvpn)"};
			execCommand(client, commands);
			commands = new String[]{"openvpn", "--config", "/opt/Dockovpn/config/server.conf"};
			execCommand(client, commands);
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private class ExecResultCallback<RC_T extends ResultCallback<A_RES_T>, A_RES_T> implements ResultCallback<A_RES_T> {

		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch completed = new CountDownLatch(1);
		private Closeable stream;
		private boolean closed = false;

		@Override
		public void onStart(Closeable stream) {
			this.stream = stream;
			this.closed = false;
			started.countDown();
		}

		@Override
		public void onNext(A_RES_T object) {
			// nothing to do
		}

		@Override
		public void onError(Throwable error) {
			if (closed)
				return;
			System.err.println(error.toString());
			onComplete();
		}

		@Override
		public void onComplete() {
			try {

				close();
			} catch (IOException e) {

				throw new RuntimeException(e);
			}
		}

		@Override
		public void close() throws IOException {
			if (!closed) {

				closed = true;
				try {

					if (stream != null) {

						stream.close();
					}
				} finally {

					completed.countDown();
				}
			}
		}

		// blocks until onComplete was called
		public RC_T awaitCompletion() throws Exception {
			try {

				completed.await();
				close();
			} catch (IOException e) {

				e.printStackTrace();
			}
			return (RC_T) this;
		}
	}
}
