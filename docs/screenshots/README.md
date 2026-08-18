# 演示截图规范

公开截图必须来自本地可复现演示，并在提交前人工检查：

- 遮挡或移除用户名、邮箱、密码、Token、Cookie、Session ID 和内网地址。
- 只使用 `samples/` 或脚本生成的合成数据，不使用科研项目原始数据。
- 建议至少包含登录页、数据集/文件、分析结果和报告页面各一张。
- 文件使用有意义的英文名称，例如 `analysis-result.png`，不要包含真实账号或项目名称。

当前截图已完成脱敏检查，文件如下：

- `login.png`：登录页
- `scenario-generator.png`：仿真数据生成页
- `result-history.png`：历史结果页
- `experiment-detail.png`：实验详情、轨迹可视化和结果比较页

根目录下的 `sam_img/` 仅作为本地原始素材目录，已被 Git 忽略；公开仓库使用本目录中的规范化副本。
