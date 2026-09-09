# 개발 메모

## 설정

### Flash-Mem MCP 설정

- "[사용자계정Home경로]/.ai/mcp/mcp.json"   (전역용)
- "[프로젝트Root경로]/.ai/mcp/mcp.json"   (프로젝트용)

`[프로젝트Root경로]\.ai\mcp\mcp.json` 파일 생성 => 예: `C:\\project\\website-kim-b-simple`

```json
{
  "mcpServers": {
    "flash-mem": {
      "type": "stdio",
      "command": "cmd",
      "args": [
        "/c", "flash-mem", "mcp",
        "[프로젝트Root경로]"
      ]
    }
  }
}
```

**전역 설정(Settings → Tools → AI Assistant → Model Context Protocol)에서는 `flash-mem` 항목을 제거**

### 전역 MCP 등록

```json
{
  "mcpServers": {
    "sequential-thinking": { "command": "cmd", "args": ["/c","npx","-y","@modelcontextprotocol/server-sequential-thinking"] },
    "context7":            { "command": "cmd", "args": ["/c","npx","-y","@upstash/context7-mcp"] },
    "playwright":          { "command": "cmd", "args": ["/c","npx","-y","@playwright/mcp@latest"] },
    "chrome-devtools":     { "command": "cmd", "args": ["/c","npx","-y","chrome-devtools-mcp@latest"] },
    "tavily":              { "command": "cmd", "args": ["/c","npx","-y","tavily-mcp@0.1.2"],
                             "env": { "TAVILY_API_KEY": "<TAVILY_API_KEY>" } },
    "serena":              { "command": "uvx", "args": ["--from","git+https://github.com/oraios/serena","serena","start-mcp-server","--context","ide-assistant","--enable-web-dashboard","false","--enable-gui-log-window","false"] }
  }
}

```

## 문제 해결