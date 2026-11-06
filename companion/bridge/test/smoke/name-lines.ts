// Smoke checks: lines with the learner's name, from shared pieces (test/name-lines.ts, which runs on its own too), and the name
// check against the voice service's verdicts (test/name-check.ts, the same). Its bridge's port: LANI_SMOKE_NAMES_PORT (by default
// the harness's port + 9).
import nameCheck from '../name-check'
import nameLines from '../name-lines'
import { check, port as smokePort } from './harness'

export default async function names() {
  nameCheck(check)
  await nameLines(check, Number(process.env.LANI_SMOKE_NAMES_PORT ?? smokePort + 9))
}
