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

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "extensions")
public class Extension {
    @DatabaseField(id = true)
    private String name;
    @DatabaseField
    private boolean enabled;
    @DatabaseField
    private String path;

    public Extension() {
        // ORMLite needs a no-arg constructor
    }
    public Extension(String name, String path) throws Exception {
        setEnabled(false);
        setName(name);
        setPath(path);
    }

    public boolean isEnabled() {
        return this.enabled;
    }
    public void setEnabled(boolean e) {
        this.enabled = e;
    }
    public String getName() {
        return this.name;
    }
    public void setName(String s) {
        this.name = s;
    }
    public String getPath() {
        return this.path;
    }
    public void setPath(String s) {
        this.path = s;
    }

    public JComponent createPanel() throws Exception {
        // Please override this
        System.out.println("NG: createPanel");
        return new JPanel();
    }
    public JMenuItem historyClickHandler() {
        // Please override this
        System.out.println("NG: historyClickHandler");
        return new JMenuItem("extensions");
    }
}
