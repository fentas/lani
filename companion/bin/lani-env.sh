# Sourced by the bash scripts (docs/migrate-from-fluent.md): a node set up before the project was renamed from Fluent
# to Lani keeps working. Every LANI_* variable falls back to its old FLUENT_* name (LANI_* wins), and
# lani_config_file finds a file of ~/.config/lani, or of ~/.config/fluent while only the old directory has it.
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
