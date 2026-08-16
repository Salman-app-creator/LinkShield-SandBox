package com.linkshield.sandbox.ui

// ─────────────────────────────────────────────────────────────────────────────
// This file intentionally contains NO composables.
//
// The active MainScreen is defined in:
//   com.linkshield.sandbox.MainActivity  (MainActivity.kt)
//
// Having two @Composable fun MainScreen() with identical signatures in
// the same compilation unit causes "Conflicting overloads" build error.
// Keeping this file empty (package declaration only) resolves the conflict
// while preserving the file so Git history and directory structure stay clean.
// ─────────────────────────────────────────────────────────────────────────────

