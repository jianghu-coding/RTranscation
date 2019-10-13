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
package com.rt;

import com.rt.rm.XAResourceManager;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author hejianglong
 * @date 2019-10-13 00:00
 */
public class GlobalInfo {
    public static final ThreadLocal<String> globalTransactionId = new ThreadLocal<>();

    public static final ThreadLocal<List<XAResourceManager>> xidListMap = new ThreadLocal<>();

    public static final ConcurrentHashMap<String, Boolean> prepareStatus = new ConcurrentHashMap<>();

    public static void remove() {
        prepareStatus.remove(globalTransactionId.get());
        globalTransactionId.remove();
        List<XAResourceManager> xaResourceManagers = xidListMap.get();
        for (XAResourceManager xaResourceManager: xaResourceManagers) {
            // 对每一个开启的 RM 进行关闭
            xaResourceManager.close();
        }
        xidListMap.remove();
    }
}
