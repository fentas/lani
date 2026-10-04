// The bridge service for the smoke's service section (test/smoke/service.ts): the real one, with tools of its own.
// smoke_slow holds a call open for `ms` (a stop waits for it), and with LANI_PROBE_NEW=1 there is smoke_new, as after a
// deploy that brings a new tool.
import { startBridge } from '../../src/bridge'
import { ok } from '../../src/channel'
import type { FeatureFactory } from '../../src/feature'

const probe: FeatureFactory = () => ({
  tools: [
    {
      name: 'smoke_slow',
      description: 'Waits `ms` milliseconds, then answers (the smoke).',
      inputSchema: { type: 'object', properties: { ms: { type: 'number' } } },
      handle: async args => {
        await Bun.sleep(Number(args.ms ?? 0))
        return ok(`slept ${args.ms}`)
      },
    },
    ...(process.env.LANI_PROBE_NEW === '1'
      ? [{ name: 'smoke_new', description: 'A tool a deploy brought (the smoke).', inputSchema: { type: 'object', properties: {} }, handle: () => ok('new') }]
      : []),
  ],
})

await startBridge({ mode: 'service', extraFeatures: [probe] })
