# SimBench 首批 API

认证仍使用现有 JWT 登录接口。下面的接口是新增的评测闭环：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/catalog/benchmarks` | 查询已发布的公开 Benchmark 及版本 |
| GET | `/api/v1/catalog/protocols` | 查询已发布的公开评测协议 |
| POST | `/api/v1/algorithm-projects` | 创建算法项目 |
| PUT | `/api/v1/algorithm-projects/{id}/visibility` | 发布/隐藏算法项目 |
| POST | `/api/v1/algorithm-submissions` | 提交算法版本和输出文件 |
| POST | `/api/v1/algorithm-submissions/{id}/evaluate` | 创建异步评测运行 |
| GET | `/api/v1/evaluations/{id}` | 查询运行状态、指标和质量门禁 |
| GET | `/api/v1/evaluations/{id}/gate` | CI 查询 PASS/FAIL 结论 |
| GET | `/api/v1/public/leaderboard?...` | 查询公开项目排行榜 |
| POST | `/api/v1/admin/benchmarks` | 管理员创建 Benchmark |
| POST | `/api/v1/admin/benchmarks/{id}/versions` | 管理员创建 Benchmark 版本 |
| POST | `/api/v1/admin/benchmarks/{id}/publish` | 发布 Benchmark |
| POST | `/api/v1/admin/benchmark-versions/{id}/publish` | 发布 Benchmark 版本 |
| POST | `/api/v1/admin/protocols` | 管理员创建评测协议 |
| POST | `/api/v1/admin/protocols/{id}/publish` | 发布评测协议 |

协议 `rulesJson` 的最小格式为：

```json
{
  "defaultThreshold": 1.0,
  "metrics": [
    {"code": "RMSE", "threshold": 5.0, "comparison": "LTE"},
    {"code": "ABNORMAL_RATIO", "threshold": 0.05, "comparison": "LTE"}
  ]
}
```

`PASS` 只代表所有协议指标通过；`SUCCESS` 只代表计算完成，两者必须同时满足才允许算法版本发布。
