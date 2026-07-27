package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.OrderSnapshot;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.OrderSnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcOrderSnapshotRepository implements OrderSnapshotRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(OrderSnapshot snapshot) {
        jdbc.update("insert into cs_data_order_snapshot (id, tenant_id, workspace_id, order_no, "
                        + "snapshot_seq, source_system, source_key, order_status, product_name, "
                        + "sku, quantity, amount, currency, ordered_at, paid_at, shipped_at, "
                        + "received_at, logistics_no, logistics_company, detail_schema_version, "
                        + "detail_json, content_hash, import_batch_id) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                snapshot.getId(), snapshot.getScope().getTenantId(),
                snapshot.getScope().getWorkspaceId(), snapshot.getOrderNo(),
                snapshot.getSnapshotSeq(), snapshot.getSourceSystem(), snapshot.getSourceKey(),
                snapshot.getOrderStatus(), snapshot.getProductName(), snapshot.getSku(),
                snapshot.getQuantity(), snapshot.getAmount(), snapshot.getCurrency(),
                JdbcServiceDataSupport.timestamp(snapshot.getOrderedAt()),
                JdbcServiceDataSupport.timestamp(snapshot.getPaidAt()),
                JdbcServiceDataSupport.timestamp(snapshot.getShippedAt()),
                JdbcServiceDataSupport.timestamp(snapshot.getReceivedAt()),
                snapshot.getLogisticsNo(), snapshot.getLogisticsCompany(),
                snapshot.getDetailSchemaVersion(), snapshot.getDetailJson(),
                snapshot.getContentHash(), snapshot.getImportBatchId());
    }

    @Override
    public boolean existsByContent(ScopeRef scope, String orderNo, String contentHash) {
        return count("select count(*) from cs_data_order_snapshot where tenant_id = ? "
                        + "and workspace_id = ? and order_no = ? and content_hash = ?",
                scope, orderNo, contentHash) > 0;
    }

    @Override
    public boolean existsByOrderNo(ScopeRef scope, String orderNo) {
        return count("select count(*) from cs_data_order_snapshot where tenant_id = ? "
                        + "and workspace_id = ? and order_no = ?",
                scope, orderNo) > 0;
    }

    @Override
    public int nextSnapshotSeq(ScopeRef scope, String orderNo) {
        List<Integer> rows = jdbc.query(
                "select snapshot_seq from cs_data_order_snapshot where tenant_id = ? "
                        + "and workspace_id = ? and order_no = ? "
                        + "order by snapshot_seq desc limit 1 for update",
                (rs, rowNum) -> rs.getInt("snapshot_seq"),
                scope.getTenantId(), scope.getWorkspaceId(), orderNo);
        return rows.isEmpty() ? 1 : rows.get(0) + 1;
    }

    @Override
    public long countByScope(ScopeRef scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_order_snapshot where tenant_id = ? and workspace_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId());
        return count == null ? 0L : count;
    }

    private long count(String sql, ScopeRef scope, Object... tail) {
        Object[] arguments = new Object[2 + tail.length];
        arguments[0] = scope.getTenantId();
        arguments[1] = scope.getWorkspaceId();
        System.arraycopy(tail, 0, arguments, 2, tail.length);
        Long count = jdbc.queryForObject(sql, Long.class, arguments);
        return count == null ? 0L : count;
    }
}
