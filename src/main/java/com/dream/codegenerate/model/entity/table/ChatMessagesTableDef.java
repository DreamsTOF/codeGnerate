package com.dream.codegenerate.model.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

import java.io.Serial;

/**
 *  表定义层。
 *
 * @author dream
 */
public class ChatMessagesTableDef extends TableDef {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    public static final ChatMessagesTableDef CHAT_MESSAGES = new ChatMessagesTableDef();


    public final QueryColumn ID = new QueryColumn(this, "id");


    public final QueryColumn TEXT = new QueryColumn(this, "text");


    public final QueryColumn CONTENTS = new QueryColumn(this, "contents");


    public final QueryColumn MEMORY_ID = new QueryColumn(this, "memory_id");


    public final QueryColumn TOOL_NAME = new QueryColumn(this, "tool_name");


    public final QueryColumn CREATED_AT = new QueryColumn(this, "created_at");


    public final QueryColumn EMBEDDING = new QueryColumn(this, "embedding");


    public final QueryColumn TOOL_CALL_ID = new QueryColumn(this, "tool_call_id");


    public final QueryColumn MESSAGE_TYPE = new QueryColumn(this, "message_type");


    public final QueryColumn TOOL_EXECUTION_REQUESTS = new QueryColumn(this, "tool_execution_requests");

    /**
     * 所有字段。
     */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /**
     * 默认字段，不包含逻辑删除或者 large 等字段。
     */
    public final QueryColumn[] DEFAULT_COLUMNS = new QueryColumn[]{ID, MEMORY_ID, MESSAGE_TYPE, CONTENTS, TEXT, TOOL_EXECUTION_REQUESTS, TOOL_CALL_ID, TOOL_NAME, EMBEDDING, CREATED_AT};

    public ChatMessagesTableDef() {
        super("", "chat_messages");
    }

    private ChatMessagesTableDef(String schema, String name, String alisa) {
        super(schema, name, alisa);
    }

    public ChatMessagesTableDef as(String alias) {
        String key = getNameWithSchema() + "." + alias;
        return getCache(key, k -> new ChatMessagesTableDef("", "chat_messages", alias));
    }

}
