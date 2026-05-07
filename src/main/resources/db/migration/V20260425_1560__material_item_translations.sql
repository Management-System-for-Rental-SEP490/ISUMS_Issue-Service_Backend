-- Phase 5f i18n: per-locale translation map for material catalog item name.
ALTER TABLE material_items
    ADD COLUMN IF NOT EXISTS name_translations TEXT;

COMMENT ON COLUMN material_items.name_translations IS
    'JSON map of locale -> translated item name. Reserved keys: _source, _auto.';
