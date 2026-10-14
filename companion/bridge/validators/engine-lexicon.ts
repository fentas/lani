// The bundle's entry for an engine with the dictionary's checks (validators/build.ts): the validators as globalThis.lani.
import { api } from '../src/validate-entry-lexicon'

;(globalThis as { lani?: typeof api }).lani = api
