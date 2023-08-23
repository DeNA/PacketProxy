/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.quic.service.key;

import lombok.Getter;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.key.level.ZeroRttKey;
import packetproxy.quic.value.key.level.ApplicationKey;
import packetproxy.quic.value.key.level.HandshakeKey;
import packetproxy.quic.value.key.level.InitialKey;
import packetproxy.quic.value.ConnectionId;

import java.util.Optional;

@Getter
public class RoleKeys {

    private Constants.Role role;

    private Optional<InitialKey> optionalInitialKey = Optional.empty();
    private Optional<ZeroRttKey> optionalZeroRttKey = Optional.empty();
    private Optional<HandshakeKey> optionalHandshakeKey = Optional.empty();
    private Optional<ApplicationKey> optionalApplicationKey = Optional.empty();

    boolean discardedInitialKey = false;
    boolean discardedHandshakeKey = false;

    public RoleKeys(Constants.Role role) {
        this.role = role;
    }

    public InitialKey getInitialKey() {
        return optionalInitialKey.orElseThrow();
    }
    public HandshakeKey getHandshakeKey() {
        return this.optionalHandshakeKey.orElseThrow();
    }
    public ZeroRttKey getZeroRttKey() {
        return this.optionalZeroRttKey.orElseThrow();
    }
    public ApplicationKey getApplicationKey() {
        return this.optionalApplicationKey.orElseThrow();
    }

    public boolean hasInitialKey() {
        return this.optionalInitialKey.isPresent();
    }
    public boolean hasHandshakeKey() {
        return this.optionalHandshakeKey.isPresent();
    }
    public boolean hasZeroRttKey() {
        return this.optionalZeroRttKey.isPresent();
    }
    public boolean hasApplicationKey() {
        return this.optionalApplicationKey.isPresent();
    }

    public void computeInitialKey(ConnectionId destConnId) {
        this.optionalInitialKey = Optional.of(InitialKey.of(this.role, destConnId));
    }
    public void computeZeroRttKey(byte[] secret) {
        this.optionalZeroRttKey = Optional.of(ZeroRttKey.of(secret));
    }
    public void computeHandshakeKey(byte[] secret) {
        this.optionalHandshakeKey = Optional.of(HandshakeKey.of(secret));
    }
    public void computeApplicationKey(byte[] secret) {
        this.optionalApplicationKey = Optional.of(ApplicationKey.of(secret));
    }

    public void discardInitialKey() {
        this.optionalInitialKey = Optional.empty();
        this.discardedInitialKey = true;
    }

    public void discardHandshakeKey() {
        //this.optionalHandshakeKey = Optional.empty();
        //this.discardedHandshakeKey = true;
    }

    public boolean discardedInitialKey() {
        return discardedInitialKey;
    }

    public boolean discardedHandshakeKey() {
        return discardedHandshakeKey;
    }
}
