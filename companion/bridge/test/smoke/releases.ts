// Smoke checks: app self-update.
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { auth, base, check, dataDir } from './harness'

export default async function releases() {
  writeFileSync(join(dataDir, 'app/release/lani-99.apk'), 'apk')
  writeFileSync(join(dataDir, 'app/release/latest.json'), JSON.stringify({ versionCode: 99, versionName: '0.1.99', file: 'lani-99.apk', notes: 'test' }))
  await Bun.sleep(300)
  const backlog = await (await fetch(`${base}/events/backlog?since=0`, { headers: auth })).json()
  check('release announced as app_update', backlog.some((b: any) => b.event.type === 'app_update' && b.event.versionCode === 99), backlog.map((b: any) => b.event.type))
  const latest = await (await fetch(`${base}/app/latest`, { headers: auth })).json()
  check('GET /app/latest', latest.versionCode === 99)
  check('GET /app/apk serves the file', (await (await fetch(`${base}/app/apk`, { headers: auth })).text()) === 'apk')

  const latestPath = join(dataDir, 'app/release/latest.json')
  writeFileSync(latestPath, '{"versionCode": 100, "file": ')
  await Bun.sleep(300)
  check('a half-written latest.json neither crashes the bridge nor announces anything', (await fetch(`${base}/health`)).ok && (await fetch(`${base}/app/latest`, { headers: auth })).status === 404)
  writeFileSync(latestPath, JSON.stringify({ versionCode: 99, versionName: '0.1.99', file: '../game.json' }))
  check('GET /app/apk serves only an .apk next to latest.json', (await fetch(`${base}/app/apk`, { headers: auth })).status === 404)
  writeFileSync(latestPath, JSON.stringify({ versionCode: 99, versionName: '0.1.99', file: 'lani-99.apk', notes: 'test' }))
}
