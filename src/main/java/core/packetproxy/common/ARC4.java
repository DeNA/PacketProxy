/*
Copyright (c) 2008, Interactive Pulp, LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright 
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in the 
      documentation and/or other materials provided with the distribution.
    * Neither the name of Interactive Pulp, LLC nor the names of its 
      contributors may be used to endorse or promote products derived from 
      this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.
*/
package packetproxy.common;

import java.math.BigInteger;
import java.util.Random;

/**
An unofficial implementation of the ARC4 cipher algorithm.
*/
public class ARC4 implements Cloneable {
	
	private byte[] key;
	private byte[] state;
	private int x;
	private int y;
	
	/**
	    Constructs a new ARC4 object with a randomly generated encryption key.
	*/
	public ARC4() {
	    this.state = new byte[256];
	    this.key = new BigInteger(2048, new Random()).toByteArray();
	    reset();
	}
	
	/**
	    Constructs a new ARC4 object with the specified encryption key. 
	    The key can be at most 256 bytes in length.
	    @param key the encryption key.
	*/
	public ARC4(byte[] key) {
	    this.state = new byte[256];
	    
	    int length = Math.min(256, key.length);
	    byte[] keyCopy = new byte[length];
	    System.arraycopy(key, 0, keyCopy, 0, length);
	    this.key = keyCopy;
	    reset();
	}
	
	/**
	    Resets the cipher to start encrypting a new stream of data.
	*/
	public void reset() {
	    // The key-scheduling algorithm
	    for (int i = 0; i < 256; i++) {
	        state[i] = (byte)i;
	    }
	    int j = 0;
	    for (int i = 0; i < 256; i++) {
	        j = (j + state[i] + key[i % key.length]) & 0xff;
	        byte temp = state[i];
	        state[i] = state[j];
	        state[j] = temp;
	    }
	    
	    x = 0;
	    y = 0;
	}
	
	/**
	    Crypts the data.
	    @param data The data to crpyt.
	*/
	public void crypt(byte[] data) {
	    crypt(data, data);
	}
	    
	/**
	    Crypts the data from the input array to the output array.
	    @param input The source data.
	    @param output The array to store the crpyted data, which must be as long as the input
	    data.
	*/
	public void crypt(byte[] input, byte[] output) {
	    
	    // The pseudo-random generation algorithm
	    for (int i = 0; i < input.length; i++) {
	        x = (x + 1) & 0xff;
	        y = (state[x] + y) & 0xff;
	        
	        byte temp = state[x];
	        state[x] = state[y];
	        state[y] = temp;
	        
	        output[i] = (byte)((input[i] ^ state[(state[x] + state[y]) & 0xff]));
	    }
	}
	
    @Override
    public ARC4 clone() {
    	ARC4 ret = null;
        try {
            ret = (ARC4)super.clone();
            ret.key = this.key.clone();
            ret.state = this.state.clone();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }
}
