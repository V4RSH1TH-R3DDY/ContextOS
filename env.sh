#!/bin/bash
# OpenClaw Environment Variables Setup Script
# Usage: source env.sh

# ── Gemini API Configuration ──────────────────────────────────────────────────
export OPENCLAW_API_ENDPOINT="https://generativelanguage.googleapis.com/v1beta/models"
export OPENCLAW_API_KEY="AIzaSyCBxXOpVd2nCcTVlmsp1TkDWH9bINouUIE"

# ── Gemini Models ─────────────────────────────────────────────────────────────
export OPENCLAW_REASONING_MODEL="gemini-2.0-flash"
export OPENCLAW_DRAFTING_MODEL="gemini-2.0-flash-lite"

# ── Feature Flags (set to true to enable real API calls) ─────────────────────
export OPENCLAW_ENABLE_REASONING=true
export OPENCLAW_ENABLE_DRAFTING=true

echo "✅ OpenClaw environment variables loaded. Run ./gradlew assembleDebug to build."
