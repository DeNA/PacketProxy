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
package packetproxy.extensions.randomness;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.NumberFormatter;

import packetproxy.controller.ResendController;
import packetproxy.extensions.randomness.test.RandomnessTestManager;
import packetproxy.gui.GUIBulkSenderData;
import packetproxy.gui.GUIPacket;
import  packetproxy.model.Extension;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.util.PacketProxyUtility;

public class RandomnessExtension extends Extension {
    private static JFrame owner;

    private OneShotPacket sendPacket;
    private Map<Integer, OneShotPacket> recvPackets;
    private List<String> tokens;
    private JTextField regexField;
    private JFormattedTextField countField;
    private JProgressBar requestProgressBar;
    private JComboBox<String> preprocess;
    private JComboBox<String> testMethods;
    private JFreeChart chart;

    private GUIBulkSenderData sendData;
    private int sendPacketId;

    public RandomnessExtension() {
        super();
        recvPackets = new HashMap<>();
        tokens = new ArrayList<>();
        this.setName("Randomness");
    }

    public RandomnessExtension(String name, String path) throws Exception {
        super(name, path);
        recvPackets = new HashMap<>();
        tokens = new ArrayList<>();
        this.setName("Randomness");
    }

    @Override
    public JComponent createPanel() throws Exception {
        JSplitPane vsplit_panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        vsplit_panel.add(createSendPanel());
        vsplit_panel.add(createResultPanel());
        vsplit_panel.setDividerLocation(0.5);
        return vsplit_panel;
    }

    public void add(OneShotPacket oneshot, int packetId) throws Exception {
        oneshot.setId(sendPacketId);
        sendPacket = oneshot;
        sendPacketId++;

        if (oneshot != null) {
            sendData.setData(oneshot.getData());
        }
    }

    private JComponent createSendPanel() throws Exception {
        sendData = new GUIBulkSenderData(owner, GUIBulkSenderData.Type.CLIENT, data -> {
            OneShotPacket pkt = sendPacket;
            if (pkt != null)
                pkt.setData(data);
        });

        JPanel regexPanel = new JPanel();
        regexPanel.setBackground(Color.WHITE);
        regexPanel.setLayout(new BoxLayout(regexPanel, BoxLayout.X_AXIS));

        JLabel regexLabel = new JLabel("RegExp to pickup:");
        regexPanel.add(regexLabel);
        regexField = new JTextField("X-PacketProxy-HTTP2-UUID: ([0-9a-fA-F]{8})-([0-9a-fA-F]{4})-([0-9a-fA-F]{4})-([0-9a-fA-F]{4})-([0-9a-fA-F]{12})");
        regexField.setMaximumSize(new Dimension(Short.MAX_VALUE, regexField.getMinimumSize().height));
        regexPanel.add(regexField);

        JPanel countPanel = new JPanel();
        countPanel.setBackground(Color.WHITE);
        countPanel.setLayout(new BoxLayout(countPanel, BoxLayout.X_AXIS));

        JLabel countLabel = new JLabel("count:");
        countPanel.add(countLabel);
        NumberFormat countFormat = NumberFormat.getIntegerInstance();
        countFormat.setGroupingUsed(false);
        NumberFormatter countFormatter = new NumberFormatter(countFormat);
        countFormatter.setValueClass(Integer.class);
        countFormatter.setAllowsInvalid(false);
        countField = new JFormattedTextField(countFormatter);
        countField.setValue(100);
        countField.setMaximumSize(new Dimension(Short.MAX_VALUE, regexField.getMinimumSize().height));
        countPanel.add(countField);

        requestProgressBar = new JProgressBar();
        requestProgressBar.setStringPainted(true);
        requestProgressBar.setMinimum(0);

        JButton sendButton = new JButton("Start collection");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                try {
                    String regex = regexField.getText();
                    Pattern pat = Pattern.compile(regex);
                    int count = Integer.parseInt(countField.getText());
                    if (count < 0) {
                        return;
                    }
                    requestProgressBar.setMaximum(count);
                    requestProgressBar.setValue(0);
                    recvPackets.clear();
                    tokens.clear();

                    CompletableFuture<String> future = CompletableFuture.completedFuture("send packets for analysis");
                    for (int i = 0; i < count; i++) {
                        future = future.thenApplyAsync(arg -> {
                            try {
                                ResendController.getInstance().resend(new ResendController.ResendWorker(sendPacket, 1) {
                                    @Override
                                    protected void process(List<OneShotPacket> oneshots) {
                                        int id = requestProgressBar.getValue();
                                        for (OneShotPacket oneshot: oneshots) {
                                            recvPackets.put(id++, oneshot);
                                        }
                                        requestProgressBar.setValue(requestProgressBar.getValue() + oneshots.size());
                                        if (recvPackets.size() == count) {
                                            PacketProxyUtility.getInstance().packetProxyLog("all packet received");
                                            for(Map.Entry<Integer, OneShotPacket> entry : recvPackets.entrySet()) {
                                                OneShotPacket packet = entry.getValue();
                                                // TODO: auto encoding
                                                String content = toUTF8(packet.getData());
                                                Matcher match = pat.matcher(content);
                                                if (match.find()) {
                                                    String token = "";
                                                    for (int idx = 1; idx <= match.groupCount(); idx++) {
                                                        token += match.group(idx);
                                                    }
                                                    tokens.add(token);
                                                }
                                            }
                                            JOptionPane.showMessageDialog(
                                                    owner,
                                                    "get " + tokens.size() + " tokens",
                                                    "Packet collection finished",
                                                    JOptionPane.PLAIN_MESSAGE
                                            );
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return arg;
                        });
                        future = future.thenApplyAsync(arg -> {
                            try {
                                Thread.sleep(100); // wait 0.1 sec before sending next packet
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return arg;
                        });
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });

        JButton clearButton = new JButton("clear");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    sendPacket = null;
                    recvPackets.clear();
                    sendData.setData("".getBytes());
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendButton);
        buttonPanel.add(clearButton);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

        JPanel optionPanel = new JPanel();
        optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS));
        optionPanel.add(regexPanel);
        optionPanel.add(countPanel);
        optionPanel.add(requestProgressBar);
        optionPanel.add(buttonPanel);

        JPanel leftHalf = new JPanel();
        leftHalf.setLayout(new BoxLayout(leftHalf, BoxLayout.Y_AXIS));
        leftHalf.add(sendData.createPanel());
        leftHalf.add(optionPanel);
        leftHalf.setAlignmentX(Component.CENTER_ALIGNMENT);
        return leftHalf;
    }

    private JComponent createResultPanel() throws Exception {
        JPanel preprocessPanel = new JPanel();
        preprocessPanel.setBackground(Color.WHITE);
        preprocessPanel.setLayout(new BoxLayout(preprocessPanel, BoxLayout.X_AXIS));
        preprocessPanel.add(new JLabel("preprocess token to binary:"));
        preprocess = new JComboBox<>();
        preprocess.addItem("hex");
        preprocess.addItem("base64");
        preprocess.addItem("int64");
        preprocessPanel.add(preprocess);

        JPanel testPanel = new JPanel();
        testPanel.setBackground(Color.WHITE);
        testPanel.setLayout(new BoxLayout(testPanel, BoxLayout.X_AXIS));
        testPanel.add(new JLabel("testing method:"));
        testMethods = RandomnessTestManager.getInstance().createTestList();
        testPanel.add(testMethods);

        JButton analyzeButton = new JButton("Start analysis");
        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String preprocessKey = (String)preprocess.getSelectedItem();
                    String testMethodKey = (String)testMethods.getSelectedItem();

                    // preprocess
                    ArrayList<Integer[]> preprocessed = new ArrayList<>();
                    switch (preprocessKey) {
                        case "hex":
                            for (String token : tokens) {
                                byte[] bytes = Hex.decodeHex(token.toCharArray());
                                ArrayList<Integer> arr = new ArrayList<>();
                                for (int i = 0; i < bytes.length; i++) {
                                    for (int j = 7; j >= 0; j--) {
                                        arr.add((bytes[i] >> j) & 1);
                                    }
                                }
                                preprocessed.add(arr.toArray(new Integer[0]));
                            }
                            break;
                        case "base64":
                            for (String token : tokens) {
                                byte[] bytes = Base64.getDecoder().decode(token);
                                ArrayList<Integer> arr = new ArrayList<>();
                                for (int i = 0; i < bytes.length; i++) {
                                    for (int j = 7; j >= 0; j--) {
                                        arr.add((bytes[i] >> j) & 1);
                                    }
                                }
                                preprocessed.add(arr.toArray(new Integer[0]));
                            }
                            break;
                        case "int64":
                            for (String token : tokens) {
                                long num = Long.parseLong(token);
                                ArrayList<Integer> arr = new ArrayList<>();
                                for (int i = 63; i >= 0; i--) {
                                    arr.add((int)((num >> i) & 1));
                                }
                                preprocessed.add(arr.toArray(new Integer[0]));
                            }
                            break;
                        default:
                            throw new Exception("preprocessKey " + preprocessKey + " not found");
                    }

                    double[][] points = RandomnessTestManager.getInstance().analyze(testMethodKey, preprocessed);
                    XYSeries series = new XYSeries("data", false);
                    for (int i = 0; i < points.length; i++) {
                        series.add(points[i][0], points[i][1]);
                    }
                    XYSeriesCollection collection = new XYSeriesCollection();
                    collection.addSeries(series);
                    chart.getXYPlot().setDataset(collection);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(analyzeButton);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

        LogAxis xAxis = new LogAxis();
        xAxis.setBase(10.0);
        xAxis.setLabel("p-value");
        xAxis.setRange(new Range(0.001, 1));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("number of randomized bits");

        XYSeriesCollection sampleCollection = new XYSeriesCollection();

        // green
        IntervalMarker marker1 = new IntervalMarker(0.001, 0.01);
        marker1.setPaint(new Color(0xA5, 0xD6, 0xA7));
        marker1.setAlpha(0.5f);
        // yellow
        IntervalMarker marker2 = new IntervalMarker(0.01, 0.1);
        marker2.setPaint(new Color(0xFF, 0xF5, 0x9D));
        marker2.setAlpha(0.5f);
        // red
        IntervalMarker marker3 = new IntervalMarker(0.1, 1);
        marker3.setPaint(new Color(0xEF, 0x9A, 0x9A));
        marker3.setAlpha(0.5f);

        XYPlot xyPlot = new XYPlot(sampleCollection, xAxis, yAxis, new StandardXYItemRenderer());
        xyPlot.addDomainMarker(marker1);
        xyPlot.addDomainMarker(marker2);
        xyPlot.addDomainMarker(marker3);
        xyPlot.getRenderer().setSeriesStroke(0, new BasicStroke(2.0f));
        chart = new JFreeChart("p-value and randomized bits", (Plot)xyPlot);
        chart.removeLegend();
        ChartPanel chartPanel = new ChartPanel(chart);

        JPanel rightHalf = new JPanel();
        rightHalf.add(preprocessPanel);
        rightHalf.add(testPanel);
        rightHalf.add(buttonPanel);
        rightHalf.add(chartPanel);
        rightHalf.setLayout(new BoxLayout(rightHalf, BoxLayout.Y_AXIS));

        return rightHalf;
    }

    private String toUTF8(byte[] raw){
        try {
            return new String(raw, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static JFrame getOwner() {
        return owner;
    }

    private JMenuItem createMenuItem(String name, int key, KeyStroke hotkey, ActionListener l) {
        JMenuItem out = new JMenuItem (name);
        if (key >= 0) {
            out.setMnemonic(key);
        }
        if (hotkey != null) {
            out.setAccelerator(hotkey);
        }
        out.addActionListener(l);
        return out;
    }

    public static void setOwner(JFrame owner) {
        RandomnessExtension.owner = owner;
    }

    @Override
    public JMenuItem historyClickHandler() {
        JMenuItem randomness = createMenuItem ("send to Randomness Checker", -1, null, new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    Packet packet = GUIPacket.getInstance().getPacket();
                    add(packet.getOneShotFromModifiedData(), packet.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
         });
        return randomness;
    }
}
