package com.gamerking195.dev.up2date.util;

import com.gamerking195.dev.up2date.Up2Date;
import com.gamerking195.dev.up2date.update.PluginInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

/**
 * Created by Caden Kriese (GamerKing195) on 10/21/17.
 * <p>
 * License is specified by the distributor which this
 * file was written for. Otherwise it can be found in the LICENSE file.
 * If there is no license file the code is then completely copyrighted
 * and you must contact me before using it IN ANY WAY.
 */
public class UtilDatabase {
    private UtilDatabase() {}
    private static UtilDatabase instance = new UtilDatabase();
    public static UtilDatabase getInstance() {
        return instance;
    }

    private HikariDataSource dataSource;

    private String tablename = "incompatibilities";

    public void init() {
        //Do all this async bc we cant let my DB being down affect the performance of the plugin.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl("jdbc:mysql://172.106.203.70/fh_1738");
                    config.setUsername("fh_1738");
                    config.setPassword("b62a462f62");

                    config.setMaximumPoolSize(3);

                    dataSource = new HikariDataSource(config);
                }

                runStatementSync("CREATE TABLE IF NOT EXISTS "+tablename+" (id varchar(6) NOT NULL, name TEXT, author TEXT, description TEXT, version TEXT, premium TEXT, notified TEXT, disabled TEXT, PRIMARY KEY(id))");
            }
        }.runTaskAsynchronously(Up2Date.getInstance());
    }

    public void addIncompatiblePlugin(PluginInfo info) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    boolean notified = false;

                    ResultSet rs = runQuery("SELECT version FROM TABLENAME WHERE id = '"+info.getId()+"'");
                    if (rs != null && rs.first() && !rs.isClosed()) {
                        String version = rs.getString("version");
                        if (!version.equalsIgnoreCase(info.getLatestVersion())) {
                            notified = true;
                        }
                    }

                    runStatement("INSERT INTO TABLENAME (id, name, author, version, description, premium, notified) VALUES ('" + info.getId() + "','" + info.getName() + "', '" + info.getAuthor() + "', '" + info.getLatestVersion() + "', '" + info.getDescription() + "', '" + info.isPremium() + "', 'true') ON DUPLICATE KEY UPDATE notified = '"+notified+"'");
                } catch (Exception ex) {
                    Up2Date.getInstance().printError(ex, "Error occurred while checking version difference.");
                }
            }
        }.runTaskAsynchronously(Up2Date.getInstance());
    }

    public ArrayList<PluginInfo> getIncompatiblePlugins(ArrayList<PluginInfo> infos) {

        ArrayList<PluginInfo> incompatibles = new ArrayList<>();

        StringBuilder sb = new StringBuilder();

        sb.append("SELECT * FROM TABLENAME WHERE id IN (");
        sb.append(infos.get(0).getId());
        if (infos.size() > 1) {
            sb.append(",");

            for (int i = 1; i < infos.size(); i++) {
                if (i + 1 == infos.size()) {
                    sb.append(infos.get(i).getId());
                    sb.append(")");
                } else {
                    sb.append(infos.get(i).getId());
                    sb.append(",");
                }
            }
        } else
            sb.append(")");


        ResultSet rs = runQuery(sb.toString());

        try {
            if (rs != null && rs.first() && !rs.isClosed()) {
                    while (rs.next())
                        incompatibles.add(new PluginInfo(rs.getString("name"), rs.getInt("id"), rs.getString("author"), rs.getString("version"), rs.getString("description"), rs.getBoolean("premium")));
                        rs.close();
            }
        } catch (Exception ex) {
            Up2Date.getInstance().printError(ex, "Error occurred while reading result set.");
        }

        return incompatibles;
    }

    /*
     * SQL UTILITIES
     */

    private void runStatement(String statement) {
        final String updatedStatement = statement.replace("TABLENAME", tablename);

        Connection connection;

        if (dataSource == null)
            init();

        try {
            connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updatedStatement);

            ExecutorService pool = Up2Date.getInstance().getFixedThreadPool();

            pool.submit(() -> {
                try {
                    preparedStatement.execute();

                    connection.close();
                } catch (Exception ex) {
                    Up2Date.getInstance().systemOutPrintError(ex, "Error occurred while executing statement to private DB.");
                }
            });
        }
        catch(Exception ex) {
            Up2Date.getInstance().printError(ex, "Error occurred while running MySQL statement");
        }
    }

    private void runStatementSync(String statement) {
        final String updatedStatement = statement.replace("TABLENAME", tablename);

        if (dataSource == null) {
            Up2Date.getInstance().printPluginError("Error occurred while sending statement to private DB.", "This error is not very important and doesn't affect anything ingame but you should probably report it to the dev anyways.");
            return;
        }

        Connection connection;

        try {
            connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updatedStatement);

            try {
                preparedStatement.execute();

                connection.close();
            } catch (Exception ex) {
                Up2Date.getInstance().systemOutPrintError(ex, "Error occurred while closing connection");
            }
        }
        catch(Exception ex) {
            Up2Date.getInstance().printError(ex, "Error occurred while running MySQL statement.");
        }
    }

    private ResultSet runQuery(String query) {
        final String updatedQuery = query.replace("TABLENAME", tablename);

        Connection connection;

        if (dataSource == null) {
            init();
            return null;
        }

        try {
            connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(updatedQuery);

            //Give whatever task that is using this 1 second to complete it, TODO find a better way to close the connection.
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        connection.close();
                    }
                    catch(Exception ex) {
                        Up2Date.getInstance().printError(ex, "Error occurred while closing connection");
                    }
                }
            }.runTaskLater(Up2Date.getInstance(), 40L);

            return preparedStatement.executeQuery();
        }
        catch(Exception ex) {
            Up2Date.getInstance().printError(ex, "Error occurred while running query '"+updatedQuery+"'.");
        }

        return null;
    }
}