package com.rt.tm;

import javax.transaction.xa.XAException;
import java.sql.SQLException;

/**
 * @author hejianglong
 * @date 2019/10/12
 */
public interface TransactionManager {

    void begin();

    /**
     * 询问子事务是否完成
     * @return
     * @throws XAException
     */
    boolean prepare() throws XAException;

    void commit() throws XAException, SQLException;

    void rollback() throws XAException, SQLException;
}
