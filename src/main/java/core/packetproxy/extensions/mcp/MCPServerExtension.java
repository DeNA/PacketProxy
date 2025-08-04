package packetproxy.extensions.mcp;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import packetproxy.model.Extension;

public class MCPServerExtension extends Extension {

	private MCPServer server;
	private HttpServer httpServer;
	private JTextArea logArea;
	private JButton startButton;
	private JButton stopButton;
	private boolean isRunning = false;
	private static final int HTTP_PORT = 8765;

	public MCPServerExtension() {
		super();
		this.setName("MCP Server");
	}

	public MCPServerExtension(String name, String path) throws Exception {
		super(name, path);
		this.setName("MCP Server");
	}

	@Override
	public JComponent createPanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		// Status panel
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusPanel.add(new JLabel("MCP Server Status: "));

		startButton = new JButton("Start Server");
		stopButton = new JButton("Stop Server");
		stopButton.setEnabled(false);

		startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startServer();
			}
		});

		stopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stopServer();
			}
		});

		statusPanel.add(startButton);
		statusPanel.add(stopButton);

		// Log area
		logArea = new JTextArea(20, 80);
		logArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(logArea);

		panel.add(statusPanel);
		panel.add(new JLabel("Server Logs:"));
		panel.add(scrollPane);

		return panel;
	}

	@Override
	public JMenuItem historyClickHandler() {
		return null; // MCP Serverは右クリックメニューに追加しない
	}

	private void startServer() {
		if (isRunning) {
			return;
		}

		try {
			server = new MCPServer(this::addLog);

			// Start HTTP server for MCP
			httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
			httpServer.createContext("/mcp", new MCPHttpHandler());
			httpServer.setExecutor(null); // creates a default executor
			httpServer.start();

			Thread serverThread = new Thread(() -> {
				try {
					server.run();
				} catch (Exception e) {
					addLog("Server error: " + e.getMessage());
					e.printStackTrace();
				}
			});
			serverThread.setDaemon(true);
			serverThread.start();

			isRunning = true;
			startButton.setEnabled(false);
			stopButton.setEnabled(true);
			addLog("MCP Server started");
			addLog("HTTP endpoint available at http://localhost:" + HTTP_PORT + "/mcp");
			log("MCP Server started with HTTP endpoint on port " + HTTP_PORT);

		} catch (Exception e) {
			addLog("Failed to start server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void stopServer() {
		if (!isRunning) {
			return;
		}

		try {
			if (server != null) {
				server.stop();
				server = null;
			}

			if (httpServer != null) {
				httpServer.stop(0);
				httpServer = null;
			}

			isRunning = false;
			startButton.setEnabled(true);
			stopButton.setEnabled(false);
			addLog("MCP Server stopped");
			log("MCP Server stopped");

		} catch (Exception e) {
			addLog("Failed to stop server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void addLog(String message) {
		if (logArea != null) {
			javax.swing.SwingUtilities.invokeLater(() -> {
				logArea.append("[" + new java.util.Date() + "] " + message + "\n");
				logArea.setCaretPosition(logArea.getDocument().getLength());
			});
		}
	}

	// HTTP handler for MCP requests
	private class MCPHttpHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			addLog("Received HTTP request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

			// Enable CORS
			exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
			exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
			exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

			if ("OPTIONS".equals(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(200, 0);
				exchange.getResponseBody().close();
				return;
			}

			if (!"POST".equals(exchange.getRequestMethod())) {
				String response = "Only POST method is supported";
				exchange.sendResponseHeaders(405, response.length());
				OutputStream os = exchange.getResponseBody();
				os.write(response.getBytes());
				os.close();
				return;
			}

			try {
				// Read request body
				InputStream is = exchange.getRequestBody();
				String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
				addLog("Request body: " + requestBody);

				// Process MCP request if server is available
				String responseBody;
				if (server != null) {
					try {
						JsonObject request = com.google.gson.JsonParser.parseString(requestBody).getAsJsonObject();
						JsonObject result = server.processTestRequest(request);
						responseBody = result.toString();
						addLog("Response: " + responseBody);
					} catch (Exception e) {
						addLog("Error processing request: " + e.getMessage());
						responseBody = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error: "
								+ e.getMessage() + "\"},\"id\":null}";
					}
				} else {
					responseBody = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32002,\"message\":\"Server not available\"},\"id\":null}";
				}

				// Send response
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
				OutputStream os = exchange.getResponseBody();
				os.write(responseBody.getBytes(StandardCharsets.UTF_8));
				os.close();

			} catch (Exception e) {
				addLog("HTTP handler error: " + e.getMessage());
				String errorResponse = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error\"},\"id\":null}";
				exchange.sendResponseHeaders(500, errorResponse.length());
				OutputStream os = exchange.getResponseBody();
				os.write(errorResponse.getBytes());
				os.close();
			}
		}
	}
}
