// The bundle's entry for an engine (validators/build.ts): the validators as globalThis.lani.
import { api } from '../src/validate-entry'

;(globalThis as { lani?: typeof api }).lani = api
