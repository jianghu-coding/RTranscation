/*
 * Project: com.rt
 *
 * File Created at 2019-10-13
 *
 * Copyright 2019 CMCC Corporation Limited.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * ZYHY Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license.
 */
package com.rt.db;

import com.mysql.cj.jdbc.MysqlDataSource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author hejianglong
 * @date 2019-10-13 12:37
 */
public class MyJDBCDBPool implements DBPool {

    private final String username;

    private final String password;

    private final String dbUrl;

    private final DataSource dataSource;

    public MyJDBCDBPool(String username, String password, String dbUrl) {
        this.username = username;
        this.password = password;
        this.dbUrl = dbUrl;
        this.dataSource = getDataSource();
    }

    private DataSource getDataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(dbUrl);
        dataSource.setUser(this.username);
        dataSource.setPassword(this.password);
        return dataSource;
    }


    @Override
    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }
}
