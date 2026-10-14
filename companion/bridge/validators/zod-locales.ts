// zod's locales for the validators' bundle (validators/build.ts): English only, the one zod configures itself with and
// the bridge's messages are in. zod's own index has every language (about 300 KB of the bundle); nothing reads them.
// @ts-ignore: zod's file, by path (its exports map doesn't name the locales one by one)
export { default as en } from '../node_modules/zod/v4/locales/en.js'
