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

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JOptionPane;

import org.bouncycastle.asn1.dvcs.Data;

import com.j256.ormlite.dao.Dao;

import packetproxy.ListenPortManager;
import packetproxy.OpenVPN;
import packetproxy.model.Database.DatabaseMessage;

public class OpenVPNForwardPorts extends Observable implements Observer {
    private static OpenVPNForwardPorts instance;

    public static OpenVPNForwardPorts getInstance() throws Exception {
        if (instance == null) {
            instance = new OpenVPNForwardPorts();
        }
        return instance;
    }

    private Database database;
    private Dao<OpenVPNForwardPort, Integer> dao;
    private DaoQueryCache<OpenVPNForwardPort> cache;

    private OpenVPNForwardPorts() throws Exception {
        database = Database.getInstance();
        dao = database.createTable(OpenVPNForwardPort.class, this);
        cache = new DaoQueryCache<>();
        if (!isLatestVersion()) {
            RecreateTable();
        }
        if (dao.countOf() == 0) {
            create(new OpenVPNForwardPort(OpenVPNForwardPort.TYPE.TCP, 80, 8080));
            create(new OpenVPNForwardPort(OpenVPNForwardPort.TYPE.TCP, 443, 8443));
        }
    }

    public void create(OpenVPNForwardPort forwardPort) throws Exception {
        dao.createIfNotExists(forwardPort);
        cache.clear();
        notifyObservers();
    }

    public void delete(int id) throws Exception {
        dao.deleteById(id);
        cache.clear();
        notifyObservers();
    }

    public void delete(OpenVPNForwardPort forwardPort) throws Exception {
        dao.delete(forwardPort);
        cache.clear();
        notifyObservers();
    }

    public void update(OpenVPNForwardPort forwardPort) throws Exception {
        dao.update(forwardPort);
        cache.clear();
        notifyObservers();
    }

    public void refresh() {
        notifyObservers();
    }

    public OpenVPNForwardPort query(int id) throws Exception {
        List<OpenVPNForwardPort> ret = cache.query("query", id);
        if (ret != null) {
            return ret.get(0);
        }

        OpenVPNForwardPort forwardPort = dao.queryForId(id);

        cache.set("query", id, forwardPort);
        return forwardPort;
    }

    public List<OpenVPNForwardPort> queryAll() throws Exception {
        List<OpenVPNForwardPort> ret = cache.query("queryAll", 0);
        if (ret != null) {
            return ret;
        }

        ret = dao.queryBuilder().query();

        cache.set("queryAll", 0, ret);
        return ret;
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
        clearChanged();
    }

    @Override
    public void update(Observable o, Object arg) {
        DatabaseMessage message = (DatabaseMessage) arg;
        try {
            switch (message) {
                case PAUSE:
                    // TODO ロックを取る
                    break;
                case RESUME:
                    // TODO ロックを解除
                    break;
                case DISCONNECT_NOW:
                    break;
                case RECONNECT:
                    database = Database.getInstance();
                    dao = database.createTable(OpenVPNForwardPort.class, this);
                    cache.clear();
                    notifyObservers(arg);
                    break;
                case RECREATE:
                    database = Database.getInstance();
                    dao = database.createTable(OpenVPNForwardPort.class, this);
                    cache.clear();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isLatestVersion() throws Exception {
        String result = dao.queryRaw("SELECT sql FROM sqlite_master WHERE name='openvpn_forward_ports'")
                .getFirstResult()[0];
        return result.equals(
                "CREATE TABLE `openvpn_forward_ports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `type` VARCHAR , `fromPort` INTEGER , `toPort` INTEGER , UNIQUE (`type`,`fromPort`,`toPort`) )");
    }

    private void RecreateTable() throws Exception {
        int option = JOptionPane.showConfirmDialog(null,
                "OpenVPNForwardPortsテーブルの形式が更新されているため\n現在のテーブルを削除して再起動しても良いですか？",
                "テーブルの更新",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (option == JOptionPane.YES_OPTION) {
            database.dropTable(OpenVPNForwardPort.class);
            dao = database.createTable(OpenVPNForwardPort.class, this);
        }
    }
}
