package com.automation.domain.scenario.script;

import java.io.IOException;
import java.util.List;

/**
 * 脚本仓库接口，负责按脚本名加载配置。
 */
public interface ScriptRepository {

    /**
     * 加载脚本。
     *
     * @param scriptName 脚本名称
     * @return 脚本定义
     * @throws IOException 加载失败时抛出
     */
    ScenarioScript load(String scriptName) throws IOException;

    /**
     * 列出可用脚本名称。
     *
     * @return 脚本名称列表
     * @throws IOException 枚举失败时抛出
     */
    default List<String> listScriptNames() throws IOException {
        return List.of();
    }
}
