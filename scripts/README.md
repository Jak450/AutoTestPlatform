# Secrets Management

This repo uses a **git filter** to keep real secrets out of commits while
letting you keep using real values during local development.

## How it works

```
worktree (real values)  ──clean──▶  index/repo (placeholders)
worktree (real values)  ◀─smudge──  index/repo (placeholders)
```

- `clean`  runs on `git add` / `git commit`: replaces real values with `<your KEY>`.
- `smudge` runs on `git checkout` / `git pull`: replaces `<your KEY>` with the real value.
- You see and edit real values; git only ever sees placeholders.

## First-time setup (per machine)

```powershell
# 1. Copy the template and fill in real values
Copy-Item .secrets.template .secrets.local
notepad .secrets.local

# 2. Activate the filter and pre-commit hook
pwsh ./scripts/secrets-install.ps1
```

`.secrets.local` is gitignored. Never commit it.

## Adding a new secret

1. Append to `.secrets.local`:
   ```
   NEW_SECRET_KEY=real-value-here
   ```
2. Use the **real** value in your code as usual.
3. (Optional) If the secret lives in a file type not yet covered, add a line to
   `.gitattributes`:
   ```
   path/to/your/file filter=secrets
   ```
4. Commit. The filter rewrites the value to `<your NEW_SECRET_KEY>` in the commit.

## Adding a new protected file path

Append a pattern to `.gitattributes`:

```
path/glob/**/*.ext filter=secrets
```

Then run `git add --renormalize .` once so the index gets re-cleaned.

## Verifying the filter works

```powershell
git show :path/to/file        # what git sees - should contain placeholders
Get-Content path/to/file      # what you see - real values
```

If they match, the filter is NOT active. Re-run `secrets-install.ps1`.

## Pre-commit hook

The hook in `scripts/pre-commit` runs `pre-commit-scan.ps1`, which scans
staged additions for known secret formats (ARK, OpenAI, AWS, GitHub PAT).
If a hit is **not** registered in `.secrets.local`, the commit is rejected.

This catches the case where you paste a fresh secret directly into code
without registering it first.

## Disaster recovery

If a secret leaks through:

1. **Rotate immediately** — the secret is compromised the moment it hits
   GitHub, regardless of whether you delete the commit later.
2. Rewrite history with `git filter-repo` and force-push (private repos only).
3. For a public repo, assume the secret is gone forever; rotate is the only fix.
