// Content and data schema ids are "lani.<kind>/<version>" ("lani.pack/v0"). Before the project was renamed from Fluent
// they were "fluent.<kind>/<version>", and what was written then still loads: a curated file of an older checkout, a
// pack, scene or villager a tutor published into a learner's data. Every reader accepts both; writers write lani.*.
import { z } from 'zod'

/** The id [id] had before the rename: "fluent.pack/v0" for "lani.pack/v0". */
export const legacySchema = (id: string) => id.replace(/^lani\./, 'fluent.')

/** Whether [s] is the schema id [id], under its name of today or of before the rename. */
export const isSchema = (s: unknown, id: string) => s === id || s === legacySchema(id)

/** A spec's `schema` field: [id] or its name from before the rename, read as [id] (so a re-published spec says lani.*). */
export const schemaField = <T extends string>(id: T) =>
  z.union([z.literal(id), z.literal(legacySchema(id))]).transform((): T => id)
