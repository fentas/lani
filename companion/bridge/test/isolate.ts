// The first import of the smoke tests (and the QA town): they never read this machine's lani.env (docs/setup.md), whose
// settings would point them at the learner's data, voice cache or releases. A section that tests lani.env names its own
// file (LANI_ENV_FILE).
process.env.LANI_ENV_FILE ||= '/dev/null'
