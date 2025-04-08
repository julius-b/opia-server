CREATE OR REPLACE VIEW feed AS
SELECT
    id AS post_id,
    null AS event_membership_id,
    created_at
FROM Posts
UNION ALL
SELECT
    null AS post_id,
    id AS event_membership_id,
    created_at
FROM EventMemberships
ORDER BY created_at DESC;
