package edu.ucsf.crosslink.io;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class DBUtil {

	private String dbUrl = "jdbc:sqlserver://stage-sql-ctsi.ucsf.edu;instanceName=default;portNumber=1433;databaseName=crosslink";
	private String dbUser = "crosslink";
	private String dbPassword = "crosslink";

	private static final Logger LOG = Logger.getLogger(DBUtil.class.getName());
	
	@Inject
	public DBUtil(@Named("dbUrl") String dbUrl, @Named("dbUser") String dbUser, @Named("dbPassword") String dbPassword) throws ClassNotFoundException {
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		this.dbUrl = dbUrl;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;
	}
	
    public Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(dbUrl, dbUser,
                    dbPassword);
            return conn;
        } catch (SQLException e) {
			LOG.log(Level.WARNING, "Can not connect to " + dbUrl, e);
            return null;
        }
    }
}
