/*
 * Copyright 2025 DeNA Co., Ltd.
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

import java.beans.PropertyChangeEvent;

public enum PropertyChangeEventType {
    INTERCEPT_DATA("interceptData"),
    INTERCEPT_MODE("interceptMode"),

    SSL_PASS_THROUGHS("sslPassThroughs"),

    SERVERS("servers"),

    FILTERS("filters"),

    LISTEN_PORTS("listenPorts"),

    CONFIGS("configs"),

    CHARSET_UPDATED("charsetUpdated"),

    MODIFICATIONS_UPDATED("modificationsUpdated"),

    RESOLUTIONS_UPDATED("resolutionsUpdated"),

    SELECTED_INDEX("selectedIndex"),

    INTERCEPT_OPTIONS("interceptOptions"),

    DATABASE_MESSAGE("databaseMessage"),

    PACKETS("packets"),

    RESENDER_PACKETS("resenderPackets"),

    CLIENT_CERTIFICATES("clientCertificates"),

    EXTENSIONS("extensions"),

    FORWARD_PORTS("forwardPorts");

    private final String value;

    PropertyChangeEventType(String value) {
        this.value = value;
    }

    public boolean matches(PropertyChangeEvent event) {
        return value.equals(event.getPropertyName());
    }

    @Override
    public String toString() {
        return value;
    }
}
