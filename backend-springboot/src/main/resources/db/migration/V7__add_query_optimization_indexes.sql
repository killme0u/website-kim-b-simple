-- V7__add_query_optimization_indexes.sql
-- Add indexes to optimize frequently used queries

-- Member lookups by email (password reset, username recovery)
CREATE INDEX idx_member_email ON member (email) WHERE status != 'DELETED';

-- Board lookups by slug (board page access)
CREATE INDEX idx_board_slug ON board (slug);

-- User's posts (my page, profile)
CREATE INDEX idx_post_member ON post (member_id, deleted_at DESC);

-- User's comments (comment history, profile)
CREATE INDEX idx_comment_member ON comment (member_id, deleted_at DESC);

-- Attachment lookups by stored_name (file download)
CREATE INDEX idx_attachment_stored_name ON attachment (stored_name);

-- View logs for analytics
CREATE INDEX idx_view_log_member ON post_view_log (member_id);

-- Post likes by member (like check)
CREATE INDEX idx_post_like_member ON post_like (member_id);
