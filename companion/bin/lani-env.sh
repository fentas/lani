# Sourced by the bash scripts. The node's settings come from lani.env (below; the environment wins). A node set up
# before the project was renamed from Fluent to Lani (docs/migrate-from-fluent.md) keeps working: every LANI_* variable
# falls back to its old FLUENT_* name (LANI_* wins), and lani_config_file finds a file of ~/.config/lani, or of
# ~/.config/fluent while only the old directory has it.
for _v in ${!FLUENT_@}; do
  _n="LANI_${_v#FLUENT_}"
  [ -n "${!_n+x}" ] || export "$_n=${!_v}"
done
unset _v _n

# lani_config_file NAME: $LANI_CONFIG_DIR/NAME; else ~/.config/lani/NAME, or ~/.config/fluent/NAME while only that exists.
lani_config_file() {
  if [ -n "${LANI_CONFIG_DIR:-}" ]; then printf '%s\n' "$LANI_CONFIG_DIR/$1"; return; fi
  local new="$HOME/.config/lani/$1" old="$HOME/.config/fluent/$1"
  if [ ! -e "$new" ] && [ -e "$old" ]; then printf '%s\n' "$old"; else printf '%s\n' "$new"; fi
}

# lani_share_dir NAME: ~/.local/share/NAME (e.g. lani-voice), or the same named fluent-… while only that one exists.
lani_share_dir() {
  local new="$HOME/.local/share/$1" old="$HOME/.local/share/${1/lani/fluent}"
  if [ ! -e "$new" ] && [ -e "$old" ]; then printf '%s\n' "$old"; else printf '%s\n' "$new"; fi
}

# lani_env_file: the node's settings, $LANI_ENV_FILE (/dev/null: none), else lani.env in the config directory.
lani_env_file() {
  if [ -n "${LANI_ENV_FILE:-}" ]; then printf '%s\n' "$LANI_ENV_FILE"; else lani_config_file lani.env; fi
}

# lani.env's settings for this machine, exported where the environment doesn't set them (docs/setup.md, "lani.env"; the
# same rules as companion/bridge/src/env.ts and .claude/hooks/lani_paths.py). The default learner's settings (their
# data, voice cache, remote, …) are not exported, so a process of another learner never inherits them: the bridge and
# the hooks read them from the file themselves.
_lani_learner_keys=" LANI_DATA_DIR LANI_RESULTS_DIR LANI_VOICE_CACHE LANI_CULTURE LANI_LANDSCAPE LANI_BRIDGE_PORT LANI_PUBLIC_URL LANI_TOWN_URL LANI_DATA_AUTOCOMMIT LANI_DATA_REMOTE LANI_DATA_PUSH LANI_PROFILE LANI_CHILD "
_f="$(lani_env_file)"
if [ -f "$_f" ] && [ -r "$_f" ]; then
  while IFS= read -r _l || [ -n "$_l" ]; do
    _l="${_l%$'\r'}"
    [[ "$_l" =~ ^[[:space:]]*(export[[:space:]]+)?(LANI_[A-Z0-9_]*)[[:space:]]*=(.*)$ ]] || continue
    _k="${BASH_REMATCH[2]}"
    _v="${BASH_REMATCH[3]}"
    case "$_lani_learner_keys" in *" $_k "*) continue ;; esac
    [ -n "${!_k+x}" ] && continue
    _v="${_v#"${_v%%[![:space:]]*}"}"
    case "$_v" in
      \"*) _v="${_v#\"}"; _v="${_v%%\"*}" ;;
      \'*) _v="${_v#\'}"; _v="${_v%%\'*}" ;;
      \#*) _v="" ;;
      *) _v="${_v%%[[:space:]]#*}"; _v="${_v%"${_v##*[![:space:]]}"}" ;;
    esac
    case "$_v" in
      '~'|'~/'*) _v="$HOME${_v#\~}" ;;
      '$HOME'|'$HOME/'*) _v="$HOME${_v#\$HOME}" ;;
      '${HOME}'|'${HOME}/'*) _v="$HOME${_v#\$\{HOME\}}" ;;
    esac
    export "$_k=$_v"
  done < "$_f"
fi
unset _f _l _k _v
