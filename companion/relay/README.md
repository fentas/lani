# lani-relay

Towns and the family page without a node, and the encrypted backups (plan 5, phase D). One Bun process, SQLite on disk,
configured by the environment. The API, the security model, the settings and how to host it:
[docs/plans/05-built/relay.md](../../docs/plans/05-built/relay.md).

```bash
bun install                 # type definitions only; nothing at run time but Bun
bun src/index.ts            # RELAY_PORT=8080, RELAY_DATA_DIR=./data
bun test                    # unit tests, in process
docker build -f companion/relay/Dockerfile -t lani-relay companion    # from the repository's root
```

The bridge uses it with `LANI_RELAY_URL` (companion/README.md, "Through the relay"). The end-to-end smoke with three
bridges: `cd companion/bridge && LANI_RELAY_SMOKE_PORT=<port> bun test/relay.ts`.
