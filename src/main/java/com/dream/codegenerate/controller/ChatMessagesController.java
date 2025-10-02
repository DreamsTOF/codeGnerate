package com.dream.codegenerate.controller;

import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import com.dream.codegenerate.service.ChatMessagesService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/**
 *  控制层。
 *
 * @author dream
 */
@RestController
@RequestMapping("/chatMessages")
public class ChatMessagesController {

    @Autowired
    private ChatMessagesService chatMessagesService;

    /**
     * 保存。
     *
     * @param chatMessagesEntity
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody ChatMessagesEntity chatMessagesEntity) {
        return chatMessagesService.save(chatMessagesEntity);
    }

    /**
     * 根据主键删除。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable UUID id) {
        return chatMessagesService.removeById(id);
    }

    /**
     * 根据主键更新。
     *
     * @param chatMessagesEntity
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public boolean update(@RequestBody ChatMessagesEntity chatMessagesEntity) {
        return chatMessagesService.updateById(chatMessagesEntity);
    }

    /**
     * 查询所有。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<ChatMessagesEntity> list() {
        return chatMessagesService.list();
    }

    /**
     * 根据主键获取。
     *
     * @param id 主键
     * @return 详情
     */
    @GetMapping("getInfo/{id}")
    public ChatMessagesEntity getInfo(@PathVariable UUID id) {
        return chatMessagesService.getById(id);
    }

    /**
     * 分页查询。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<ChatMessagesEntity> page(Page<ChatMessagesEntity> page) {
        return chatMessagesService.page(page);
    }

}
