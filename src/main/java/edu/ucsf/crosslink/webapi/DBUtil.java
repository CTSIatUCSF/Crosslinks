package edu.ucsf.crosslink.webapi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtil {
	
	private static String dbUrl = "jdbc:sqlserver://stage-sql-ctsi.ucsf.edu;instanceName=default;portNumber=1433;databaseName=crosslink";
	private static String dbUser = "crosslink";
	private static String dbPassword = "crosslink";
	
	static {
		try { 
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

    static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(dbUrl, dbUser,
                    dbPassword);
            return conn;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
