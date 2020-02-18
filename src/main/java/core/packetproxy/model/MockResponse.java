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
package packetproxy.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@DatabaseTable(tableName = "mock_responses")
public class MockResponse
{
    @DatabaseField(generatedId = true)
    private int id;
    @DatabaseField
    private Boolean enabled;
    @DatabaseField(uniqueCombo = true)
    private String ip;
    @DatabaseField(uniqueCombo = true)
    private int port;
    @DatabaseField(uniqueCombo = true)
    private String path;
    @DatabaseField
    private String mockResponse;
    @DatabaseField
    private String comment;

    private boolean specifiedByHostName;

    public MockResponse() {
        // ORMLite needs a no-arg constructor
    }
    public MockResponse(String ip, int port, String path, String mockResponse) {
    	initialize(ip, port, path, mockResponse, "");
    }

    public MockResponse(String ip, int port, String path, String mockResponse, String comment) {
        initialize(ip, port, path, mockResponse, comment);
    }
    private void initialize(String ip, int port, String path, String mockResponse, String comment) {
        this.ip = ip;
        this.enabled = false;
        this.port = port;
        this.path = path;
        this.mockResponse = mockResponse;
        this.comment = comment;
        this.specifiedByHostName = isHostName(ip);
    }
    static private boolean isHostName(String host){
    	try {
    		return !(InetAddress.getByName(host).getHostAddress().equals(host));
    	} catch (UnknownHostException e) {
    		e.printStackTrace();
    		return true;
    	}
    }
    @Override
	public String toString() {
    	return String.format("%s:%d", ip, port);
    }
    public InetSocketAddress getAddress() {
    	return new InetSocketAddress(ip, port);
    }
    public int getId() {
    	return this.id;
    }
    public String getIp() {
        return this.ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public boolean isEnabled() {
        return this.enabled;
    }
    public void setEnabled() {
        this.enabled = true;
    }
    public void setDisabled() {
        this.enabled = false;
    }


    public String getPath(){
        return path;
    }
    public void setPath(String path){
        this.path = path;
    }
    public String getMockResponse(){
        return mockResponse;
    }
    public void setMockResponse(String mockResponse){
        this.mockResponse = mockResponse;
    }
    public String getComment(){
        return comment;
    }
    public void setComment(String comment){
        this.comment = comment;
    }
    
    public List<InetAddress> getIps(){
    	try {
    		if(specifiedByHostName){
    			List<InetAddress> ips = Arrays.asList(InetAddress.getAllByName(getAddress().getHostName()));
    			return ips;
    		}else{
    			List<InetAddress> ips = new ArrayList<InetAddress>();
    			ips.add(InetAddress.getByName(ip));
    			return ips;
    		}
    	} catch (UnknownHostException e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    		return new ArrayList<InetAddress>();
    	}
    }
}
