// The channel's parts that don't need the MCP SDK: the tool registry's shapes, the tools' answers, and what an event
// into the tutor's session looks like. channel.ts (the node's MCP server) and the phone's channel (src/phone/channel.ts:
// the bridge in the app, docs/plans/05-built/phone-runtime.md) both send events through [channelEvent], so the in-app
// tutor gets the content and meta Claude Code gets.
import { z } from 'zod'

export type ToolResult = { content: { type: 'text'; text: string }[]; isError?: boolean }

export type Tool = {
  name: string
  description: string
  /** JSON Schema shown to the session. */
  inputSchema: Record<string, unknown>
  handle: (args: Record<string, any>) => ToolResult | Promise<ToolResult>
}

export const ok = (text: string): ToolResult => ({ content: [{ type: 'text', text }] })
export const fail = (text: string): ToolResult => ({ content: [{ type: 'text', text }], isError: true })

/** The optional one-line `note` of the publish tools. */
export const noteIn = z.string().max(500).nullish()
export const badNote = () => fail('note must be a string of at most 500 characters; nothing published')

/** "</channel>" or "<channel …>" inside content → "&lt;/channel>": text, not a tag. */
export const neutralizeTags = (s: string) => s.replace(/<(\/?\s*channel)/gi, '&lt;$1')

/**
 * The line under an event that belongs to a conversation: the app never sees the terminal, and a session may have
 * the tools deferred or the instructions out of mind, so every such event says how to answer. The first event of a
 * bridge also asks for the guide (the instructions only hold the rules that fit Claude Code's limit).
 */
export function replyHint(conversationId: string | undefined, first: boolean): string {
  if (!conversationId) return ''
  const guide = first ? ' First event since the session started: call the channel_guide tool before answering.' : ''
  return `\n\n↩ The app doesn't see the terminal: anything for the learner goes through the lani reply tool (mcp__lani__reply, conversation_id "${conversationId}").${guide}`
}

/**
 * An event as it goes into the session: <channel source="lani" kind=… conversation_id=… msg_id=…>content</channel>.
 * The meta values become tag attributes, so they are limited to id characters; the content can't open or close a
 * <channel> tag of its own. [withHint] adds the reply line ([replyHint]) when it is delivered.
 */
export function channelEvent(content: string, meta: Record<string, string>): { content: string; meta: Record<string, string> } {
  const safeMeta = Object.fromEntries(Object.entries(meta).map(([k, v]) => [k, String(v).replace(/[^\w:.-]/g, '_').slice(0, 100)]))
  return { content: neutralizeTags(content), meta: safeMeta }
}

/** [content] as delivered: with the reply line of its conversation (the first such event also asks for the guide). */
export const withHint = (content: string, meta: Record<string, string>, heard: boolean) => content + replyHint(meta.conversation_id, !heard)

export type PermissionRequest = { request_id: string; tool_name: string; description: string; input_preview: string }

/** What the features use of the channel: events into the tutor's session, and the learner's answer to a tool approval. */
export type ChannelPort = {
  notify: (content: string, meta: Record<string, string>) => Promise<'sent' | 'queued'>
  permission: (verdict: { request_id: string; behavior: 'allow' | 'deny' }) => Promise<void>
}

/** The tools as the session sees them. */
export const listed = (tools: Tool[]) => tools.map(({ name, description, inputSchema }) => ({ name, description, inputSchema }))
