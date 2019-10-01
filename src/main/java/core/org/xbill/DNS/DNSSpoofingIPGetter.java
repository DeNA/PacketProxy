package org.xbill.DNS;

import packetproxy.gui.GUIOptionPrivateDNS;

public class DNSSpoofingIPGetter {
	private GUIOptionPrivateDNS gui;
	public DNSSpoofingIPGetter(GUIOptionPrivateDNS gui){
		this.gui = gui;
	}
	
	public String get(){
		return gui.getSpoofingIP();
	}

}
