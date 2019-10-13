package com.rt.rm;

import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.jdbc.MysqlXAConnection;
import com.mysql.cj.jdbc.MysqlXid;
import com.mysql.cj.util.StringUtils;
import com.rt.db.DBPool;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static com.rt.GlobalInfo.globalTransactionId;
import static com.rt.GlobalInfo.prepareStatus;
import static com.rt.GlobalInfo.xidListMap;

/**
 * @author hejianglong
 * @date 2019/10/12
 */
public class XAResourceManagerImpl implements XAResourceManager {

    private final DBPool dbPool;

    private final MysqlXAConnection mysqlXAConnection;

    private final Connection connection;

    private final XAResource xaResource;

    private Xid xid;

    private final boolean onePhase = false;

    private final int formatId = 0;

    public XAResourceManagerImpl(DBPool dbPool) throws SQLException {
        this.dbPool = dbPool;
        // 这里目前采用 mysql 默认 JDBC 连接池
        this.connection = getJDBCConnection();
        this.mysqlXAConnection = new MysqlXAConnection((JdbcConnection) this.connection, true);
        this.xaResource = this.mysqlXAConnection.getXAResource();
    }

    public void begin() throws XAException {
        // 获取全局事务 ID
        String gtrid = globalTransactionId.get();
        if (StringUtils.isNullOrEmpty(gtrid)) {
            throw new RuntimeException("请先开启全局事务");
        }
        // 生成分支事务 ID 并将其与全局事务相对应
        Xid xid = generateTransactionXid(gtrid);
        // 开启分支事务
        this.xaResource.start(xid, XAResource.TMNOFLAGS);
        this.xid = xid;
        // 将当前线程和当前线程所用的资源管理器相邦定，便于让全局事务管理器 commit 的时候能够知道
        // 当前会话有哪些需要进行 prepare 和 commit 的分支事务
        List<XAResourceManager> xaResourceManagerList = xidListMap.get();
        if (xaResourceManagerList == null) {
            xaResourceManagerList = new ArrayList<>();
            xaResourceManagerList.add(this);
            xidListMap.set(xaResourceManagerList);
            return;
        }
        xaResourceManagerList.add(this);
    }

    @Override
    public boolean prepare() throws XAException {
        // 准备阶段
        this.xaResource.end(xid, XAResource.TMSUCCESS);
        // 查看事务是否可以提交
        int status = this.xaResource.prepare(xid);
        // 如果可以就进入准备阶段
        boolean success = status == XAResource.XA_OK;
        // 设置全局是否成功的标识
        // 如果之前设置的成功标志位为 true 那么可以继续设置为 true
        if (prepareStatus.get(globalTransactionId.get()) == null || prepareStatus.get(globalTransactionId.get())) {
            prepareStatus.put(globalTransactionId.get(), success);
        } else { // 否则的话就意味失败了
            prepareStatus.put(globalTransactionId.get(), false);
        }
        return success;
    }

    @Override
    public void commit() throws XAException {
        // 提交分支事务
        this.xaResource.commit(xid, onePhase);
    }

    @Override
    public void rollback() throws XAException {
        // 回滚分支事务
        this.xaResource.rollback(xid);
    }

    @Override
    public void execute(String sql) throws SQLException {
        // 执行 sql 语句
        PreparedStatement preparedStatement = this.connection.prepareStatement(sql);
        preparedStatement.execute();
    }

    @Override
    public void close() {
        try {
            this.mysqlXAConnection.close();
            this.connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (!this.connection.isClosed()) {
                    this.connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


    }

    private Connection getJDBCConnection() throws SQLException {
        return dbPool.getConnection();
    }

    /**
     * 获取唯一事务 ID
     * @param gtrid
     * @return
     */
    private Xid generateTransactionXid(String gtrid) {
        String uid = UUID.randomUUID().toString();
        byte[] bqual = uid.getBytes();
        MysqlXid mysqlXid = new MysqlXid(gtrid.getBytes(), bqual, formatId);
        return mysqlXid;
    }
}
