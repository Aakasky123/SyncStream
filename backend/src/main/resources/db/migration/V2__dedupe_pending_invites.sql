DELETE FROM workspace_members newer
USING workspace_members older
WHERE newer.workspace_id = older.workspace_id
  AND lower(newer.email) = lower(older.email)
  AND newer.status = 'PENDING'
  AND older.status = 'PENDING'
  AND newer.user_id IS NULL
  AND older.user_id IS NULL
  AND newer.invitation_token IS NOT NULL
  AND older.invitation_token IS NOT NULL
  AND newer.id <> older.id
  AND (
      newer.created_at > older.created_at
      OR (newer.created_at = older.created_at AND newer.id::text > older.id::text)
  );

CREATE UNIQUE INDEX idx_workspace_members_pending_invite_email
ON workspace_members(workspace_id, lower(email))
WHERE status = 'PENDING'
  AND user_id IS NULL
  AND invitation_token IS NOT NULL;
