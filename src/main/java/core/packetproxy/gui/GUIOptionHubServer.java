package packetproxy.gui;

import static packetproxy.model.PropertyChangeEventType.CONFIGS;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.*;
import org.apache.commons.lang3.RandomStringUtils;
import packetproxy.common.ConfigHttpServer;
import packetproxy.common.I18nString;
import packetproxy.model.ConfigBoolean;
import packetproxy.model.ConfigString;
import packetproxy.model.Configs;

public class GUIOptionHubServer implements PropertyChangeListener {

	private JFrame frame;
	private JPanel panel;
	private JCheckBox checkBox;
	private JTextField accessTokenField;
	private JButton reGenerateButton;
	private ConfigHttpServer server;

	public GUIOptionHubServer(JFrame frame) throws Exception {
		this.frame = frame;
		this.panel = new JPanel();

		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createCheckBox());
		panel.add(createAccessTokenPanel());

		Configs.getInstance().addPropertyChangeListener(this);
	}

	private JCheckBox createCheckBox() {
		checkBox = new JCheckBox(I18nString.get("Enabled"));
		checkBox.addActionListener(event -> {

			try {

				if (checkBox.isSelected()) {

					new ConfigBoolean("SharingConfigs").setState(true);
				} else {

					new ConfigBoolean("SharingConfigs").setState(false);
				}
			} catch (Exception e) {

				e.printStackTrace();
			}
		});
		checkBox.setMinimumSize(new Dimension(Short.MAX_VALUE, checkBox.getMaximumSize().height));
		return checkBox;
	}

	private JComponent createAccessTokenPanel() {
		JPanel panel = new JPanel();

		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		panel.add(new JLabel("AccessToken:"));

		accessTokenField = new JTextField();
		accessTokenField.setEditable(false);
		accessTokenField.setMaximumSize(new Dimension(Short.MAX_VALUE, accessTokenField.getMinimumSize().height));
		panel.add(accessTokenField);

		reGenerateButton = new JButton(I18nString.get("Regenerate"));
		reGenerateButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					generateAccessToken();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		panel.add(reGenerateButton);
		return panel;
	}

	private boolean isRunningServer() {
		return this.server != null && this.server.isAlive();
	}

	private void stopServer() {
		if (this.server == null)
			return;
		this.server.stop();
		this.server = null;
	}

	private void startServer() throws Exception {
		String accessToken = new ConfigString("SharingConfigsAccessToken").getString();
		this.server = new ConfigHttpServer("localhost", 32349, accessToken);
		this.server.start();
	}

	private void refresh() {
		try {

			String accessToken = new ConfigString("SharingConfigsAccessToken").getString();
			accessTokenField.setText(accessToken);

			checkBox.setSelected(new ConfigBoolean("SharingConfigs").getState());
			if (checkBox.isSelected()) {

				this.accessTokenField.setEnabled(true);
				this.reGenerateButton.setEnabled(true);
				if (!isRunningServer())
					startServer();
			} else {

				if (isRunningServer())
					stopServer();
				this.accessTokenField.setEnabled(false);
				this.reGenerateButton.setEnabled(false);
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private void generateAccessToken() throws Exception {
		String accessToken = RandomStringUtils.randomAlphabetic(20);
		new ConfigString("SharingConfigsAccessToken").setString(accessToken);
	}

	public JComponent createPanel() throws Exception {
		String accessToken = new ConfigString("SharingConfigsAccessToken").getString();
		if (accessToken.isEmpty()) {

			generateAccessToken();
		}
		refresh();
		return panel;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (CONFIGS.matches(evt)) {

			refresh();
		}
	}
}
