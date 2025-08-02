package packetproxy.extensions.mcp;

import static packetproxy.util.Logging.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
	private JTextArea logArea;
	private JButton startButton;
	private JButton stopButton;
	private boolean isRunning = false;

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
			log("MCP Server started");

		} catch (Exception e) {
			addLog("Failed to start server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void stopServer() {
		if (!isRunning || server == null) {
			return;
		}

		try {
			server.stop();
			server = null;
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
}
