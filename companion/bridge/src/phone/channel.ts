// The phone's channel (docs/plans/05-built/phone-runtime.md): the bridge in the app has no Claude Code session to talk
// to; its tutor is the app's own (the in-app tutor), which hears the same events through the host (`host.channel`), with
// the content and meta Claude Code's channel would get (tool.ts channelEvent and withHint: the same tags made safe, the
// same reply line, the first conversation's event asking for the guide), and calls the same tools (entry.ts call).
import { host } from '../../phone/shims/host'
import { HttpError } from '../http'
import { channelEvent, withHint, type AppInbox, type ChannelPort } from '../tool'

export class PhoneChannel implements ChannelPort {
  /** Whether the tutor has had a conversation's event since it started (the first one also asks for the guide). */
  private heard = false
  /**
   * The in-app tutor's inbox while it is attached through the bridge's tutor session (features/tutor.ts, POST
   * /tutor/attach: the same contract as on a node): the events wait there instead of going to the host.
   */
  private app?: AppInbox

  /** The in-app tutor attached through /tutor/attach: the events go to [inbox] from now on. */
  divert = (inbox: AppInbox, log: (...a: unknown[]) => void): number => {
    this.app = inbox
    log("the app's tutor is attached through the tutor session: the channel's events go to its inbox")
    return 0
  }

  /** It detached: the events go to the host again, with what the inbox still held. */
  undivert = async (log: (...a: unknown[]) => void): Promise<number> => {
    const inbox = this.app
    if (!inbox) return 0
    this.app = undefined
    const back = inbox.takeAll()
    for (const q of back) await this.notify(q.content, { ...q.meta, queued_at: q.meta.queued_at ?? new Date(q.at).toISOString() })
    log(`the app's tutor detached: ${back.length} event(s) to the host again`)
    return back.length
  }

  /** A new tutor session (the in-app tutor started again, its context fresh): its first event asks for the guide again. */
  attach() {
    this.heard = false
  }

  notify = async (content: string, meta: Record<string, string>): Promise<'sent' | 'queued' | 'app'> => {
    const e = channelEvent(content, meta)
    if (this.app) {
      this.app.push(e.content, e.meta)
      return 'app'
    }
    host().channel(withHint(e.content, e.meta, this.heard), JSON.stringify(e.meta))
    if (e.meta.conversation_id) this.heard = true
    return 'sent'
  }

  /** The in-app tutor asks no tool approvals: there is no prompt to answer (as the service without a session: 503). */
  permission = async (_verdict: { request_id: string; behavior: 'allow' | 'deny' }): Promise<void> => {
    throw new HttpError(503, 'no tool approvals on the phone: the in-app tutor asks none')
  }
}
