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
package packetproxy.common;

import java.io.File;
import java.util.List;
import packetproxy.model.Packet;

public class Logger {
	final static private String DEFAULT_LOG_DIR = System.getProperty("user.home")+"/.packetproxy/logs";
	final static private String DEFAULT_LOG_EXT = "txt";
	private String log_dir;
	private String log_ext;
	private String file_title;
	private List<Packet> packets;

	public Logger(List<Packet> packets) {
		this.packets = packets;
		this.log_dir = DEFAULT_LOG_DIR;
		this.log_ext = DEFAULT_LOG_EXT;
		this.file_title = null;
		File f = new File(DEFAULT_LOG_DIR);
		if (!f.exists()) f.mkdirs();
	}

	public void appendLogDir(String append_log_dir){
		// logs/appended_log_dir/以下に保存みたいなことをする
		// windowsにも対応できてるはず
		this.log_dir = String.join(File.separator, DEFAULT_LOG_DIR, append_log_dir);
	}

	public void setFileTitle(String file_title){ this.file_title = file_title; }

	public String outputToFile(String filename) throws Exception{
		if (filename == null) {
			filename = String.join(File.separator, this.log_dir, createFileName());
		}
		StringBuilder sb = loggingProcess();
		Utils.writefile(filename, sb.toString().getBytes());
		return filename;
	}

	/*
	 * ロギングのフォーマットを決定する部分
	 * 継承して使ってくれれば好きな出力にできる。はず。
	 * */
	protected StringBuilder loggingProcess() throws Exception{
		StringBuilder sb = new StringBuilder();
		for (Packet packet: packets) {
			sb.append(packet.getClient().toString());
			sb.append(" --> ");
			sb.append(packet.getServer().toString());
			sb.append("\r\n");
			sb.append("--------------------------------\r\n");
			if (packet.getDecodedData().length < 5000) {
				sb.append(new String(packet.getDecodedData()));
			} else {
				sb.append(new String(packet.getDecodedData(), 0, 5000, "UTF-8"));
				sb.append("...(snipped)...");
			}
			sb.append("\r\n");
			sb.append("--------------------------------\r\n");
			sb.append("\r\n");
		}	
		return sb;
	}

	private String createFileName(){
		if(this.file_title == null){
			return "log_"+System.currentTimeMillis()+"."+this.log_ext;
		}else{
			return this.file_title+this.log_ext;
		}
	}
}
