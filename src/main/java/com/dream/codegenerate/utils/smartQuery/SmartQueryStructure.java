package com.dream.codegenerate.utils.smartQuery;

import cn.hutool.v7.core.text.StrUtil;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.table.TableInfo;

import java.util.*;

/**
 * 结构构建器：负责解析 VO 并维护 Join 树
 */
public class SmartQueryStructure {

    public static class JoinNode {
        public String path;
        public String tableAlias;
        public String linkCol;
        public String localCol;
        public TableInfo tableInfo;
        public Class<?> entityClass;
        public Class<?> fieldType;
        public boolean isCollection;
        public boolean isLeaf;       // 是否 Relation
        public String remoteTargetCol;
        public String pkColName;
        public String voPkPropName;

        // 延迟加载专用
        public boolean isDeferred;
        public SmartFetch originalFetch;
        public String fieldName;

        public Map<String, String> selectFields = new LinkedHashMap<>();
        public Map<String, JoinNode> children = new LinkedHashMap<>();
        public List<JoinNode> deferredChildren = new ArrayList<>();
    }

    private final JoinNode rootNode = new JoinNode();
    private int aliasCounter = 0;
    public final Map<String, FilterMapping> filterRegistry = new HashMap<>();

    public record FilterMapping(QueryColumn column, MatchType type) {}

    public SmartQueryStructure(Class<?> entityClass, Class<?> resultClass) {
        TableInfo rootTable = SmartQueryContext.getTableInfo(entityClass);
        rootNode.path = "";
        rootNode.tableAlias = "t0";
        rootNode.tableInfo = rootTable;
        rootNode.entityClass = entityClass;
        rootNode.fieldType = resultClass;
        rootNode.pkColName = rootTable.getPrimaryKeyList().getFirst().getColumn();
        rootNode.voPkPropName = findPkPropInVo(resultClass, rootNode.pkColName, rootTable);
    }

    public JoinNode getRootNode() { return rootNode; }

    public void parseVoTree(Class<?> clazz, JoinNode parent, int collectionDepth) {
        // 确保选中主键用于 Context Identity
        if (!parent.selectFields.containsValue(parent.pkColName)) {
            String selectKey = parent.voPkPropName != null ? parent.voPkPropName : "Flex_Internal_PK";
            parent.selectFields.put(selectKey, parent.pkColName);
        }

        List<SmartQueryContext.VoFieldMeta> fields = SmartQueryContext.getVoFields(clazz);

        for (SmartQueryContext.VoFieldMeta meta : fields) {
            String name = meta.name();

            if (meta.smartFetch() != null) {
                handleSmartFetch(meta, parent, collectionDepth);
            } else if (meta.relation() != null) {
                handleRelation(meta, parent);
            } else {
                String col = parent.tableInfo.getColumnByProperty(name);
                if (col != null) parent.selectFields.put(name, col);
            }
        }
    }

    private void handleSmartFetch(SmartQueryContext.VoFieldMeta meta, JoinNode parent, int collectionDepth) {
        SmartFetch sf = meta.smartFetch();
        JoinNode node = createJoinNode(sf.targetEntity(), sf.localField(), sf.remoteFieldLink(), meta);
        node.isLeaf = false;
        node.fieldName = meta.name();
        node.originalFetch = sf;

        boolean shouldDefer = sf.fetchType() == FetchType.LAZY;
        if (sf.fetchType() == FetchType.AUTO && meta.isCollection() && collectionDepth >= 1) {
            shouldDefer = true;
        }

        if (shouldDefer) {
            node.isDeferred = true;
            parent.deferredChildren.add(node);
            parseVoTree(node.fieldType, node, 0);
        } else {
            node.isDeferred = false;
            node.pkColName = node.tableInfo.getPrimaryKeyList().getFirst().getColumn();
            node.voPkPropName = findPkPropInVo(node.fieldType, node.pkColName, node.tableInfo);
            int nextDepth = meta.isCollection() ? collectionDepth + 1 : collectionDepth;
            parseVoTree(node.fieldType, node, nextDepth);
            parent.children.put(meta.name(), node);
        }
    }

    private void handleRelation(SmartQueryContext.VoFieldMeta meta, JoinNode parent) {
        Relation rel = meta.relation();
        JoinNode node = createJoinNode(rel.targetEntity(), rel.localField(), rel.remoteFieldLink(), meta);
        node.isLeaf = true;
        node.remoteTargetCol = node.tableInfo.getColumnByProperty(rel.remoteField());
        node.selectFields.put(meta.name(), node.remoteTargetCol);

        // 注册反向过滤
        this.filterRegistry.put(meta.name(), new FilterMapping(new QueryColumn(node.tableAlias, node.remoteTargetCol), rel.matchType()));

        parent.children.put(meta.name(), node);
    }

    private JoinNode createJoinNode(Class<?> entity, String localProp, String remoteProp, SmartQueryContext.VoFieldMeta meta) {
        JoinNode node = new JoinNode();
        node.tableInfo = SmartQueryContext.getTableInfo(entity);
        node.tableAlias = "t" + (++aliasCounter);
        node.localCol = localProp;
        node.linkCol = StrUtil.isNotBlank(remoteProp) ? node.tableInfo.getColumnByProperty(remoteProp) : node.tableInfo.getPrimaryKeyList().getFirst().getColumn();
        node.isCollection = meta.isCollection();
        node.entityClass = entity;
        node.fieldType = meta.isCollection() ? meta.componentType() : meta.type();
        return node;
    }

    public void applyToWrapper(QueryWrapper queryWrapper, JoinNode node) {
        for (var entry : node.selectFields.entrySet()) {
            String alias = (node.path.isEmpty() ? "" : node.path + "$") + entry.getKey();
            queryWrapper.select(new QueryColumn(node.tableAlias, entry.getValue()).as(alias));
        }
        for (var entry : node.children.entrySet()) {
            JoinNode child = entry.getValue();
            child.path = (node.path.isEmpty() ? "" : node.path + "$") + entry.getKey();
            String hostCol = node.tableInfo.getColumnByProperty(child.localCol);
            queryWrapper.leftJoin(child.tableInfo.getTableName()).as(child.tableAlias)
                    .on(new QueryColumn(node.tableAlias, hostCol).eq(new QueryColumn(child.tableAlias, child.linkCol)));
            applyToWrapper(queryWrapper, child);
        }
    }

    // 辅助：在 VO 中寻找对应数据库主键的属性名
    private String findPkPropInVo(Class<?> voClass, String dbPkCol, TableInfo tableInfo) {
        for (SmartQueryContext.VoFieldMeta meta : SmartQueryContext.getVoFields(voClass)) {
            if (meta.smartFetch() != null || meta.relation() != null) continue;
            String colName = tableInfo.getColumnByProperty(meta.name());
            if (dbPkCol.equalsIgnoreCase(colName)) return meta.name();
        }
        return null;
    }
}
