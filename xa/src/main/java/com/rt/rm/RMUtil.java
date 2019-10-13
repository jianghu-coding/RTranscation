/*
 * Project: com.rt.rm
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
package com.rt.rm;

import com.rt.db.DBPool;
import java.sql.SQLException;

/**
 * @author hejianglong
 * @date 2019-10-13 15:05
 */
public class RMUtil {

    public static XAResourceManager getResourceManager(DBPool dbPool) throws SQLException {
        return new XAResourceManagerImpl(dbPool);
    }
}
