package com.rt.rm;

import javax.transaction.xa.XAException;
import java.sql.SQLException;

/**
 * @author hejianglong
 * @date 2019/10/12
 */
public interface XAResourceManager {

    void begin() throws XAException, SQLException;

    boolean prepare() throws XAException, SQLException;

    void commit() throws XAException, SQLException;

    void rollback() throws XAException, SQLException;

    void execute(String sql) throws SQLException;

    void close();
}
