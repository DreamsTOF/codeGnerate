package com.dream.codegenerate.utils;





import cn.hutool.core.collection.CollUtil;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SmartTreeBuilder<T, R> {

    private final List<T> flatList;
    private final R rootId;
    private Function<T, R> idGetter;
    private Function<T, R> parentIdGetter;
    private BiConsumer<T, List<T>> childrenSetter;

    public SmartTreeBuilder(List<T> flatList, R rootId) {
        this.flatList = flatList;
        this.rootId = rootId;
    }

    public static <T, R> SmartTreeBuilder<T, R> of(List<T> flatList, R rootId) {
        return new SmartTreeBuilder<>(flatList, rootId);
    }

    public SmartTreeBuilder<T, R> withId(Function<T, R> idGetter) {
        this.idGetter = idGetter;
        return this;
    }

    public SmartTreeBuilder<T, R> withParentId(Function<T, R> parentIdGetter) {
        this.parentIdGetter = parentIdGetter;
        return this;
    }

    public SmartTreeBuilder<T, R> withChildrenSetter(BiConsumer<T, List<T>> childrenSetter) {
        this.childrenSetter = childrenSetter;
        return this;
    }

    public List<T> build() {
        if (CollUtil.isEmpty(flatList)) return Collections.emptyList();

        // 1. O(N) 构建索引 Map
        Map<R, T> nodeMap = flatList.stream()
                .collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));

        // 2. O(N) 预初始化所有节点的子列表容器
        Map<R, List<T>> childrenGroup = flatList.stream()
                .filter(node -> !Objects.equals(parentIdGetter.apply(node), rootId))
                .collect(Collectors.groupingBy(parentIdGetter));

        // 3. O(N) 组装：只遍历一次 flatList 建立父子引用
        List<T> roots = new ArrayList<>();
        for (T node : flatList) {
            R id = idGetter.apply(node);
            R pid = parentIdGetter.apply(node);

            // 如果是根节点
            if (Objects.equals(pid, rootId)) {
                roots.add(node);
            }

            // 将预先分组好的子节点塞给当前节点
            List<T> children = childrenGroup.get(id);
            if (children != null) {
                childrenSetter.accept(node, children);
            }
        }
        return roots;
    }
}
