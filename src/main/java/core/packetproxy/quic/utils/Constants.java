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

package packetproxy.quic.utils;

public class Constants {

    public enum Role {
        CLIENT,
        SERVER,
    }

    public enum QuicPacketType {
        PacketInitial,
        PacketHandshake,
        PacketZeroRTT,
        PacketApplication,
    }

    public enum PnSpaceType {
        PnSpaceInitial,
        PnSpaceHandshake,
        PnSpaceApplicationData,
    }

    static public final int CONNECTION_ID_SIZE = 8; // Max: 20 bytes
    static public final int TOKEN_SIZE = 16;
    static public final long kGranularity = 1; // 1ms
    static public final float kTimeThreshold = 9f/8f;
    static public final long kPacketThreshold = 3;

}
