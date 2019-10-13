/*
 * Project: PACKAGE_NAME
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

import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.jdbc.MysqlXAConnection;
import com.mysql.cj.jdbc.MysqlXid;
import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author hejianglong
 * @date 2019-10-13 08:59
 */
public class Test {

    private static final boolean logXaCommands = true;

    private static final String URL = "jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";

    public static void main(String[] args) throws SQLException {
// 是否打印 XA 日志

        // 这里可以分别获取 2 个不同服务器的 mysql 由于本机只有一个暂时就用一个来演示
        // 获得 RM1
        Connection conn1 = DriverManager.getConnection(URL, "root", "123456");
        // 获得 RM2
        Connection conn2 = DriverManager.getConnection(URL, "root", "123456");

        XAConnection xaConnection1 = new MysqlXAConnection((JdbcConnection) conn1, logXaCommands);
        XAConnection xaConnection2 = new MysqlXAConnection((JdbcConnection) conn2, logXaCommands);

        XAResource rm1 = xaConnection1.getXAResource();
        XAResource rm2 = xaConnection2.getXAResource();

        // AP 去请求 TM 执行一个分布式事务，TM 生成全局事务 ID
        byte[] gtrid = "g12345".getBytes();
        int formatId = 1;

        // 2个分支事务的事务 ID
        byte[] bqual1 = "b00003".getBytes();
        Xid xid1 = new MysqlXid(gtrid, bqual1, formatId);

        byte[] bqual2 = "b00004".getBytes();
        Xid xid2 = new MysqlXid(gtrid, bqual2, formatId);
        try {
            // 在 RM1 上面执行事务
            // 生成 RM1 上面的事务 ID
            rm1.start(xid1, XAResource.TMNOFLAGS);
            PreparedStatement ps1 = conn1.prepareStatement("insert into t values(21,19,19)");
            ps1.execute();
            // 取消调用者与事务分支的关联
            // 将底层事务状态变为 IDLE，对于 IDLE 状态的事务可以执行 PREPARE 或者 COMMIT
            rm1.end(xid1, XAResource.TMSUCCESS);

            // 执行 RM2 上的事务分支
            rm2.start(xid2, XAResource.TMNOFLAGS);
            PreparedStatement ps2 = conn2.prepareStatement("insert into t values(19,20,20)");
            ps2.execute();
            // 取消调用者与事务分支的关联
            // 将底层事务状态变为 IDLE，对于 IDLE 状态的事务可以执行 PREPARE 或者 COMMIT
            rm2.end(xid2, XAResource.TMSUCCESS);

            // 两段提交开始
            // 首先询问能否提交事务
            int prepare1 = rm1.prepare(xid1);
            int prepare2 = rm2.prepare(xid2);

            // 无法优化为一阶段提交，因为存在 2 个分支
            boolean onePhase = false;
            if (prepare1 == XAResource.XA_OK & prepare2 == XAResource.XA_OK) {
                rm1.commit(xid1, onePhase);
                rm2.commit(xid2, onePhase);
            } else {
                rm1.rollback(xid1);
                rm2.rollback(xid2);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                rm1.rollback(xid1);
                rm2.rollback(xid2);
            } catch (XAException e1) {
                e1.printStackTrace();
            }
        }
    }

}
