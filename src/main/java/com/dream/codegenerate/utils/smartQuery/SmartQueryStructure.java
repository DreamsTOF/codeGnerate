package com.dream.codegenerate.utils.smartQuery;

import cn.hutool.v7.core.text.StrUtil;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.table.TableInfo;

import java.util.*;

/**
 * 结构构建器：负责解析 VO 并维护 Join 树
 * <p>
 * 核心逻辑：
 * 1. 维护 JoinNode 树形结构。
 * 2. 笛卡尔积防御：控制哪些节点 Left Join，哪些节点 Deferred (子查询)。
 * </p>
 */
public class SmartQueryStructure {

    public static class JoinNode {
        public String path;
        public String tableAlias;
        public String linkCol;
        public String localCol;
        public TableInfo tableInfo;
        public Class<?> entityClass;
        public Class<?> fieldType; // VO 类型
        public boolean isCollection;
        public boolean isLeaf;       // 是否 Relation (单字段)
        public String remoteTargetCol;
        public String pkColName;
        public String voPkPropName;

        // 延迟加载/子查询专用
        public boolean isDeferred;
        public SmartFetch originalFetch;
        public String fieldName; // VO 属性名

        public Map<String, String> selectFields = new LinkedHashMap<>();
        public Map<String, JoinNode> children = new LinkedHashMap<>();
        public List<JoinNode> deferredChildren = new ArrayList<>();
    }

    public static class FilterMapping {
        public String field;
        public QueryColumn column;
        public MatchType type;
        public FilterMapping(String field, QueryColumn column, MatchType type) {
            this.field = field; this.column = column; this.type = type;
        }
        public QueryColumn column() { return column; }
        public MatchType type() { return type; }
    }

    private final JoinNode rootNode = new JoinNode();
    private int aliasCounter = 0;
    public final Map<String, FilterMapping> filterRegistry = new HashMap<>();

    public SmartQueryStructure(Class<?> entityClass, Class<?> resultClass) {
        TableInfo info = SmartQueryContext.getTableInfo(entityClass);
        this.rootNode.tableAlias = "t0";
        this.rootNode.tableInfo = info;
        this.rootNode.entityClass = entityClass;
        this.rootNode.fieldType = resultClass;
        this.rootNode.path = "";
        this.rootNode.pkColName = info.getPrimaryKeyList().getFirst().getColumn();
    }

    public JoinNode getRootNode() { return rootNode; }

    /**
     * 递归解析 VO 树
     * @param collectionDepth 当前路径上叠加的集合层数 (用于判断嵌套 List)
     */
    public void parseVoTree(Class<?> voClass, JoinNode node, int collectionDepth) {
        node.voPkPropName = findPkPropInVo(voClass, node.pkColName, node.tableInfo);

        for (SmartQueryContext.VoFieldMeta meta : SmartQueryContext.getVoFields(voClass)) {
            // 1. @Relation 单字段映射 (总是 Join)
            if (meta.relation() != null) {
                processRelation(meta, node);
                continue;
            }
            // 2. @SmartFetch 嵌套对象映射 (智能判断)
            if (meta.smartFetch() != null) {
                processSmartFetch(meta, node, collectionDepth);
                continue;
            }
            // 3. 普通字段映射
            if (node.tableInfo != null) {
                String col = node.tableInfo.getColumnByProperty(meta.name());
                if (col != null) {
                    node.selectFields.put(meta.name(), col);
                    filterRegistry.put(meta.name(), new FilterMapping(meta.name(), new QueryColumn(node.tableAlias, col), MatchType.CUSTOM));
                }
            }
        }
    }

    private void processSmartFetch(SmartQueryContext.VoFieldMeta meta, JoinNode parentNode, int collectionDepth) {
        SmartFetch fetch = meta.smartFetch();
        Class<?> targetEntity = fetch.targetEntity();
        TableInfo targetInfo = SmartQueryContext.getTableInfo(targetEntity);
        if (targetInfo == null) return;

        JoinNode child = new JoinNode();
        child.fieldName = meta.name();
        child.tableInfo = targetInfo;
        child.entityClass = targetEntity;
        child.fieldType = meta.isCollection() ? meta.componentType() : meta.type();
        child.isCollection = meta.isCollection();
        child.originalFetch = fetch;

        // --- 核心：Join 还是 Defer? ---
        boolean shouldDefer = false;

        if (fetch.fetchType() == FetchType.LAZY) {
            shouldDefer = true; // 强制切分
        }  else {
            // AUTO 模式
            if (child.isCollection) {
                // 规则 A: 嵌套 List (深度 > 0)，切分。
                if (collectionDepth > 0) {
                    shouldDefer = true;
                }
                // 规则 B: 同层级并行 List。
                // 如果父节点已经有了一个 Collection 类型的子节点被 Join 了，那当前这个必须切分。
                else if (hasCollectionSibling(parentNode)) {
                    shouldDefer = true;
                }
            }
        }

        if (shouldDefer) {
            // 加入延迟任务列表
            child.isDeferred = true;
            parentNode.deferredChildren.add(child);
            // 递归解析子结构 (深度重置为 0，因为是新的查询)
            parseVoTree(child.fieldType, child, 0);
        } else {
            // 加入 Join 树
            child.tableAlias = "t" + (++aliasCounter);
            child.linkCol = StrUtil.isNotBlank(fetch.remoteFieldLink()) ? targetInfo.getColumnByProperty(fetch.remoteFieldLink()) : targetInfo.getPrimaryKeyList().getFirst().getColumn();
            child.localCol = fetch.localField();

            parentNode.children.put(meta.name(), child);

            // 递归解析，深度累加
            int nextDepth = collectionDepth + (child.isCollection ? 1 : 0);
            parseVoTree(child.fieldType, child, nextDepth);
        }
    }

    private void processRelation(SmartQueryContext.VoFieldMeta meta, JoinNode parentNode) {
        Relation relation = meta.relation();
        TableInfo targetInfo = SmartQueryContext.getTableInfo(relation.targetEntity());
        if (targetInfo == null) return;

        JoinNode child = new JoinNode();
        child.isLeaf = true;
        child.tableAlias = "t" + (++aliasCounter);
        child.tableInfo = targetInfo;
        child.entityClass = relation.targetEntity();
        child.fieldType = meta.type();

        child.linkCol = StrUtil.isNotBlank(relation.remoteFieldLink()) ? targetInfo.getColumnByProperty(relation.remoteFieldLink()) : targetInfo.getPrimaryKeyList().getFirst().getColumn();
        child.localCol = relation.localField();
        child.remoteTargetCol = relation.remoteField();

        parentNode.children.put(meta.name(), child);
    }

    public void applyToWrapper(QueryWrapper queryWrapper, JoinNode node) {
        // Select 当前表字段
        for (var entry : node.selectFields.entrySet()) {
            String alias = (node.path.isEmpty() ? "" : node.path + "$") + entry.getKey();
            queryWrapper.select(new QueryColumn(node.tableAlias, entry.getValue()).as(alias));
        }

        // 递归处理 Join
        for (var entry : node.children.entrySet()) {
            JoinNode child = entry.getValue();
            child.path = (node.path.isEmpty() ? "" : node.path + "$") + entry.getKey();

            String hostCol = node.tableInfo.getColumnByProperty(child.localCol);

            if (child.isLeaf) {
                // Relation 的 Select
                String targetCol = child.tableInfo.getColumnByProperty(child.remoteTargetCol);
                String alias = child.path + "$" + entry.getKey();
                queryWrapper.select(new QueryColumn(child.tableAlias, targetCol).as(alias));
            }

            queryWrapper.leftJoin(child.tableInfo.getTableName()).as(child.tableAlias)
                    .on(new QueryColumn(node.tableAlias, hostCol).eq(new QueryColumn(child.tableAlias, child.linkCol)));

            applyToWrapper(queryWrapper, child);
        }
    }

    /**
     * 检查当前父节点下，是否已经挂载了其他的 Collection 类型的 Join 子节点
     */
    private boolean hasCollectionSibling(JoinNode parent) {
        for (JoinNode child : parent.children.values()) {
            if (child.isCollection) return true;
        }
        return false;
    }

    private String findPkPropInVo(Class<?> voClass, String dbPkCol, TableInfo tableInfo) {
        String pkProp = SmartQueryContext.getPropertyByColumn(tableInfo.getEntityClass(), dbPkCol);
        return pkProp != null ? pkProp : "id";
    }
}
