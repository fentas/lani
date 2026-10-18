// node:fs/promises for the phone's bridge: fs.ts's promises (the sync calls, answered later).
import { promises } from './fs'

export const { readFile, writeFile, appendFile, mkdir, readdir, stat, lstat, rename, unlink, rm, rmdir, copyFile, cp, realpath, chmod, mkdtemp, access, open } = promises
export type FileHandle = Awaited<ReturnType<typeof promises.open>>
export default promises
