# Live schema baseline

The Supabase project `raisslpznoscphdxluwa` is the authority for this repository.
Its migration-history table was empty when this baseline was recorded on
2026-09-23, while the repository already contained historical scripts 01-12.
Those scripts were **not** replayed, repaired, or marked as applied.

`20260923_live_schema.md` is a checked, non-executable inventory of the live
schema immediately before Phase A1. `verify_live_baseline.sql` is a read-only
catalog query that regenerates structural fingerprints and the object inventory.
Together they establish the adoption boundary:

    live database -> recorded baseline -> incremental migrations

The baseline is evidence, not a bootstrap migration. Never apply it to a
database. Future changes must be new incremental migrations and must inspect
the live target before application.
