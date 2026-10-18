// bun:sqlite on the phone: none. The voice store (voice.ts) is a node's; on the phone the bridge has no voices
// (src/phone/voice.ts), and the voice profiles keep their table in a JSON file (src/phone/profiles-table.ts). A Database
// made anyway says why.

export class Database {
  constructor(path?: string) {
    throw new Error(`no SQLite on the phone (${path ?? 'a database'}): the voice store is a node's`)
  }
}

export default { Database }
