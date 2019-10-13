package com.rt.tm;

import com.rt.rm.XAResourceManager;
import javax.transaction.xa.XAException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.rt.GlobalInfo.globalTransactionId;
import static com.rt.GlobalInfo.prepareStatus;
import static com.rt.GlobalInfo.xidListMap;

/**
 * @author hejianglong
 * @date 2019/10/12
 */
public class TransactionManagerImpl implements TransactionManager {

    private static final TransactionManagerImpl singleton = new TransactionManagerImpl();

    private TransactionManagerImpl() {
    }

    public static TransactionManagerImpl getSingleton() {
        return singleton;
    }

    public void begin() {
        // 获取全局事务 ID
        String gtrid = globalTransactionId.get();
        // 将全局事务 ID 绑定到当前线程
        if (gtrid == null) {
            globalTransactionId.set(UUID.randomUUID().toString());
        }
    }

    public boolean prepare() {
        Boolean prepareSuccess = prepareStatus.get(globalTransactionId.get());
        return prepareSuccess != null && prepareSuccess;
    }

    public void commit() throws XAException, SQLException {
        List<XAResourceManager> xaResourceManagers = xidListMap.get();
        if (xaResourceManagers.isEmpty()) {
            return;
        }
        // 全局事务管理下当前线程所有的 RM 进行 COMMIT
        for (XAResourceManager xaResourceManager: xaResourceManagers) {
            xaResourceManager.commit();
        }
    }

    public void rollback() throws XAException, SQLException {
        List<XAResourceManager> xaResourceManagers = xidListMap.get();
        if (xaResourceManagers.isEmpty()) {
            return;
        }
        // 全局事务管理下当前线程所有的 RM 进行 COMMIT
        for (XAResourceManager xaResourceManager: xaResourceManagers) {
            xaResourceManager.rollback();
        }
    }

}
