# wyapi.toubiec.cn 后端 API 逆向记录

## 范围与来源

本文档记录对 `https://wyapi.toubiec.cn/` 前端（Version 1.4.8，构建日期 2026-08-05）的真实浏览器请求观察结果。前端将正式 API 根地址设置为 `https://nextmusic.toubiec.cn`；设置中打开 Debug 模式时切换为 `http://localhost:3000`。

请求均为 `POST`，请求头为：

```http
Content-Type: application/json
```

前端先调用 `/api/ip` 缓存客户端 IP，然后为业务请求追加 `timestamp: Date.now()` 和 `ip`。实测业务请求至少需要 `ip`；缺少 `ip` 且仅发送 `id`、`timestamp` 时返回 `400 当前非法提交参数`。`timestamp` 在前端始终发送，但实测仅带 `id`、`ip` 的歌曲信息请求也能成功。

统一响应外壳：

```json
{"code":200,"data":{}}
```

失败响应仍使用 JSON，常见形式为 `{"code":400|301,"message":"...","data":null}`。HTTP 状态通常与 `code` 一致；歌单上游验证错误实测为 HTTP 400、业务码 `-462`。

## 接口总表

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/ip` | 返回客户端 IP、代理 IP、UUID 与服务端时间 |
| POST | `/api/search` | 网易云搜索 |
| POST | `/api/toplist` | 官方榜单目录 |
| POST | `/api/getSongInfo` | 单曲基础信息 |
| POST | `/api/getSongUrl` | 指定音质的播放/下载地址 |
| POST | `/api/getSongLyric` | LRC、翻译歌词及逐字歌词 |
| POST | `/api/getcomments` | 单曲评论分页 |
| POST | `/api/song/wiki` | 歌曲 Wiki/发行信息 |
| POST | `/api/getAlbum` | 专辑详情与曲目 |
| POST | `/api/playlist_trackall` | 歌单或榜单曲目 |
| POST | `/api/artist_songs` | 歌手作品分页 |
| POST | `/api/topen` | 后端请求统计与调度器状态 |

## 请求与响应

### `/api/ip`

请求体可为空对象或只带时间戳：

```json
{"timestamp":1770000000000}
```

成功 `data` 字段：

```json
{
  "ip":"222.93.81.245",
  "proxyIp":null,
  "uuid":"4556869633987709423",
  "timestamp":1786943786998
}
```

### `/api/search`

```json
{"keyword":"九万字","type":1,"limit":100,"offset":0}
```

`type` 沿用网易云搜索类型：`1` 单曲、`10` 专辑、`100` 歌手、`1000` 歌单。成功数据按类型返回 `songs`、`albums`、`artists` 或 `playlists`，并带对应的 `songCount`、`albumCount`、`artistCount` 或 `playlistCount`。单曲条目被后端规整为：

```json
{"id":1335942780,"name":"九万字","free":true,"album":"人间不值得","singer":"黄诗扶","picimg":"http://p2.music.126.net/...jpg","duration":"3:52","copyright":0,"time":"2026/08/17 13:15:44"}
```

### `/api/getSongInfo`

```json
{"id":"1335942780"}
```

`data` 字段：`id`、`name`、`free`、`album`、`singer`、`picimg`、`duration`、`copyright`、`time`。缺少 `id` 返回：

```json
{"code":400,"message":"Missing id parameter","data":null}
```

### `/api/getSongUrl`

```json
{"id":"1335942780","level":"standard"}
```

可选 `level`：`standard`、`exhigh`、`lossless`、`hires`、`jyeffect`、`sky`、`dolby`、`vivid`、`jymaster`。当 `level=sky` 时，前端还会传 `immerseType`：`c51`、`ste` 或 `aac`。

成功 `data` 字段：`id`、`url`、`br`、`level`、`size`、`md5`、`channelLayout`、`effects`、`cookie`、`time`。`url` 是网易云 CDN 地址，前端直接交给浏览器 `<audio>` 或下载器请求，不经过该 API 代理。请求的音质不可用时返回 `404 Song not found or invalid response`；实际返回的 `level` 以 `data.level` 为准，不能只信请求值。

### `/api/getSongLyric`

```json
{"id":"1335942780"}
```

`data` 字段：`lrc`、`tlyric`、`romalrc`、`klyric`、`yrc`、`yromalrc`、`ytlrc`、`time`。`lrc`/`tlyric` 是 JSON 行头加 LRC 文本；`yrc` 是逐字歌词格式，前端在存在 `yrc` 时启用 KTV 高亮。

### `/api/getcomments`

```json
{
  "id":"1335942780",
  "type":0,
  "sortType":2,
  "pageNo":1,
  "pageSize":10,
  "showInner":true,
  "fetchAll":false
}
```

`data` 字段：`id`、`type`、`threadId`、`sortType`、`total`、`pageNo`、`pageSize`、`cursor`、`hasMore`、`comments`、`hotComments`。评论条目包含 `id`、`userId`、`nickname`、`avatarUrl`、`vipType`、`content`、`likedCount`、`replyCount`、`time`、`timeText`、`ipLocation`、`liked`、`pendingReview`、`parentCommentId`。

### `/api/song/wiki`

```json
{"id":"1335942780"}
```

该接口用于下载元数据时读取 `publishTime`。实测歌曲 `1335942780` 与 `1901371647` 均返回：

```json
{"code":301,"message":"Failed to fetch song wiki","data":null}
```

因此调用方必须把 Wiki 视为可选增强信息，失败时保留当前年份或歌曲基础信息，不能阻断解析。

### `/api/getAlbum`

```json
{"id":"75228515"}
```

`data` 字段：`id`、`name`、`picUrl`、`publishTime`、`artist`、`description`、`songs`。`artist` 为 `{id,name}`；`songs` 使用与单曲信息相同的规整字段。

### `/api/playlist_trackall`

```json
{"id":"19723756","limit":500,"offset":0}
```

成功数据字段：`id`、`name`、`coverImage`、`songCount`、`playCount`、`description`、`tags`、`creator`、`songs`。`creator` 字段为 `{uid,avatar,name}`。

前端对歌单和榜单按 `limit=500`、`offset += songs.length` 循环请求，直到本页数量小于 `limit`。实测官方榜单 `19723756` 成功；普通 UGC 歌单 `6792103822` 返回：

```json
{"code":-462,"message":"请完成验证操作","data":null}
```

这是上游验证失败，不是分页字段缺失；调用方应停止分页并向用户报告失败。

### `/api/artist_songs`

```json
{
  "id":"12308369",
  "order":"hot",
  "workType":1,
  "withArtistDetail":true,
  "limit":500,
  "offset":0
}
```

`data` 字段：`id`、`order`、`offset`、`nextOffset`、`cursor`、`nextCursor`、`limit`、`workType`、`total`、`hasMore`、`fetched`、`artist`、`songs`。当前实测 `artist` 可能返回空的 `{name:"",avatar:""}`，前端因此回退到首曲的歌手名和封面。分页应优先根据 `hasMore`，同时防止 `nextOffset` 不前进造成死循环。

### `/api/toplist`

请求体 `{}`。成功 `data` 是榜单数组，每项字段：`name`、`id`、`coverImgUrl`、`updateFrequency`。前端点击榜单页时才调用，并缓存结果。

### `/api/topen`

请求体 `{}`。成功数据包含：`total`、`distinct`、`topCount`、`startedAt`、`endAt`、`endpoints`、`byEndpoint`、`scheduler`。`endpoints` 每项含 `endpoint`、`label`、`count`、`ratio`、`lastAt`、`lastAtText`；`scheduler` 含 `initialized`、`lastResetDate`、`lastResetAt`、`nextResetAt`、`nextResetInMs` 等字段。前端每 10 秒刷新一次统计页。

## 限流与错误处理

前端只对业务码 `429` 做专门处理，并读取 `data` 中的 `ip`、`limit`、`used`、`remaining`、`retryAfter`、`resetAt` 字段展示提示。非 429 错误统一显示接口地址和后端 `message`，Wiki 请求则在下载元数据流程中忽略失败。调用方应保留 HTTP 状态、业务码和 message，避免只按 HTTP 200 判断成功。

## 前端行为与复现要点

1. 首页加载调用 `/api/ip`，并把 IP 缓存在内存中；之后每次请求重新生成 `timestamp`。
2. 单曲解析顺序为 `getSongInfo -> getSongUrl -> getSongLyric -> getcomments(可选)`。
3. `/api/getSongUrl` 返回 CDN URL 后由浏览器直接请求音频；音频 URL 可能是 MP3、FLAC 或 MP4。
4. 专辑只请求一次 `getAlbum`；歌单/榜单和歌手使用循环分页。
5. Debug 开关只改变 API 根地址，不改变请求路径和 JSON 字段。

## 复现命令

PowerShell 中建议用文件传递 JSON，避免命令行转义改变请求体：

```powershell
Set-Content -NoNewline req.json '{"id":"1335942780","timestamp":1770000000000,"ip":"222.93.81.245"}'
curl.exe -s -X POST 'https://nextmusic.toubiec.cn/api/getSongInfo' `
  -H 'Content-Type: application/json' --data-binary '@req.json'
```

临时请求文件不属于项目运行时依赖，验证结束后应删除。
