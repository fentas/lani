// node:os for the phone's bridge: the home is the app's <filesDir>/lani (the host's config), its tmp under it, the
// platform android.

const env = () => (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env ?? {}

export const homedir = () => env().HOME ?? '/'
export const tmpdir = () => env().TMPDIR ?? `${homedir()}/tmp`
export const platform = () => 'android'
export const type = () => 'Linux'
export const arch = () => 'arm64'
export const release = () => ''
export const hostname = () => 'phone'
export const EOL = '\n'
export const cpus = () => [] as unknown[]
export const totalmem = () => 0
export const freemem = () => 0
export const uptime = () => 0
export const userInfo = () => ({ username: 'lani', uid: 0, gid: 0, shell: null, homedir: homedir() })

export default { homedir, tmpdir, platform, type, arch, release, hostname, EOL, cpus, totalmem, freemem, uptime, userInfo }
